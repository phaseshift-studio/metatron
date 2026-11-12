/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.algebra.rewrite.plan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.algebra.rewrite.*;
import studio.phaseshift.metatron.algebra.rewrite.PlaceResolver.ResolvedPlace;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.BranchInst;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.stream.IntStream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.algebra.Law.Helper.declares;
import static studio.phaseshift.metatron.isa.m.mach.type.compiler.Rewriter.Helper.carriers;
import static studio.phaseshift.metatron.isa.m.mach.type.compiler.Rewriter.Helper.contracts;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Shard — the distribution rule's emitter, the one body behind {@code law_shard}. It lowers a plan onto a chain over one
 * immutable {@link Env} snapshot: guard on the live roster, match the chain shape, locate the multiplicity to factor,
 * emit the keyed {@code branch} layout, and leave everything else where it stood.
 *
 * <p><b>The two partitions are one funnel.</b> A distribution factors an index set over the roster: the seed's point
 * ({@code d()} instances, the initial value's members) and a run's span range ({@code b()} instances, the unrolls
 * {@code a^[m,n] = Σ a^k}) are both index sets, and a lane's program is the chain with its share projected onto it —
 * {@code start(part)} projects the members, {@code {p,q}step} projects the unrolls. A lane may carry both projections.
 * v1 factors one axis per application: the seed first (today's layout), the span when the seed is a singleton.
 *
 * <p><b>The lane's budget is stated on its first instruction</b> ({@code ?hop}, {@code 0} at a leaf), so the metadata
 * is where a human and a re-planning peer look for it. A copied instruction is not stripped of a budget it carries:
 * the first statement wins ({@link #hopAxis}), so the head is authoritative and an inner statement is inert.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class Shard {

    private static final Logger log = LoggerFactory.getLogger(Shard.class);

    private Shard() {
        // do nothing
    }

    /**
     * The one body (§12.1): {@code apply(plan, env) = locate → resolve → emit}, over one immutable {@link Env}
     * snapshot — nothing inside reads the machine.
     */
    public static Code apply(final Plan plan, final Code code, final Env env) {
        // A CHAIN THAT ALREADY BRANCHES BY PLACE IS A LAYOUT, NOT A FOLD TO PLAN: its keyed branch routes its lanes where
        // its keys point, and planning around it would wrap that branch in another. The fixpoint hands this rule every
        // layout it emitted, so this is also where the shard's own output stops.
        if (code.insts().stream().anyMatch(inst -> BRANCH_INST_TID.equals(inst.tid().basePath()) && inst.args().isRec()))
            return code;
        // A LONE MACHINE DOES NOT FAN OUT -- unless the decision states its own place set, whose HOME lane is the
        // lanes case (?place=[{4}local]: a peerless machine shards). Read before the hop so the budget's own refusal
        // stays exactly where it was on a machine with nothing dialed.
        final boolean stated = code.insts().stream().anyMatch(inst -> inst.tid().hasQ(PLACE));
        if (env.peers().isEmpty() && !stated)
            return code;
        // THE HOP AXIS: how many more levels of the tree may be planned from here, and how many children one level may
        // fan out to. A height of zero is a leaf and an arity of zero is a point: the identity, byte for byte.
        final Hop axis = hopAxis(plan, code);
        if (axis.isPoint() || axis.arity().isZero())
            return code;
        // THE ARITY CAPS THE FAN-OUT: at most `w` lanes leave one node; the data still clamps it (`k = min(shares, seed)`).
        final long cap = null == axis.arity().max() ? Integer.MAX_VALUE : axis.arity().max();
        final int fanout = (int) Math.min(cap, Integer.MAX_VALUE);
        // THE PARTITION AXIS: what counts as one place's share. This chunk lowers STREAM and COPY; the other kinds
        // DECLINE TO THE IDENTITY -- the unchanged chain, never a different-but-plausible layout.
        final Partition split = split(plan, code);
        if (Partition.Kind.STREAM != split.kind() && Partition.Kind.COPY != split.kind()) {
            log.debug("?split={} does not lower in this chunk: the fold stays where it stands ({})", split, code);
            return code;
        }

        final List<Inst> insts = code.insts();
        // THE SEED IS THE INITIAL FORM (dom {0}) at the head of the chain, read off the contract the chain's own typing
        // resolved it to — never off its name.
        final Inst initial = 2 > insts.size() ? null : contracts(code).getFirst();
        if (null == initial || !initial.isInitial())
            return code;

        final Cut cut = plan.cut();
        final Law[] laws = cut.laws().toArray(Law[]::new);
        // each instruction keyed at the carrier flowing into it, not the seed's
        final List<Type> carriers = carriers(code);
        final List<Inst> typed = IntStream.range(0, insts.size())
                .mapToObj(i -> 0 == i ? insts.getFirst() : retype(insts.get(i), carriers.get(i)))
                .toList();
        // THE CUT is the first fold whose partials recombine through a fold declaring the cut's laws. THE RUN is the
        // floatable stretch right before it (or the whole rest of the chain when there is no cut). Everything before
        // the run stays home.
        final InstPattern recombines = InstPattern.law(Law.blocked).combinedBy(laws);
        final InstPattern floats = InstPattern.law(Law.floatable);
        final int cutIndex = IntStream.range(1, insts.size()).filter(i -> recombines.matches(typed.get(i))).findFirst().orElse(-1);
        final int runIndex = 0 > cutIndex ? 1
                : 1 + IntStream.range(1, cutIndex).map(i -> cutIndex - i)
                .filter(i -> !floats.matches(typed.get(i)))
                .findFirst().orElse(0);
        final Inst reducer = 0 > cutIndex ? null : insts.get(cutIndex);
        final Inst keyed = 0 > cutIndex ? null : typed.get(cutIndex);
        // a self-combining fold re-applies the chain's own instruction; a fold defined by derivation recombines through
        // the fold its definition names; a keyed fold through the chain its pointwise combiner computes
        final Call combined = 0 > cutIndex ? null : Law.Helper.combiner(keyed, laws);
        final List<Inst> combiner = 0 > cutIndex ? List.of()
                : keyed == combined ? List.of(reducer) : combined.insts();
        final Inst head = insts.getFirst();
        final boolean literal = START_INST_TID.equals(head.tid().basePath()) && !head.args().isEmpty()
                && !head.arg(0).isCall() && 1 == runIndex;
        final int seed = literal ? (int) head.arg(0).stream().count() : Integer.MAX_VALUE;
        // THE RUN AXIS: a closed span range in the run, factored when the seed is a singleton. The unrolls a^[m,n] are
        // independent terms, so a share is a subrange of the exponent, not a part of the seed: every lane carries the
        // whole seed and a narrowed span, and the chain's tail — the first non-floatable instruction after the run, a
        // reducer included — runs at home over the branch's bag. NO TYPED CUT IS NEEDED: the branch recombines the
        // terms and the tail folds them, so a span the typing probe could not thread is still plannable. The run must
        // start at the seed (a prefix that does not float would have to be broadcast, the next chunk's copy case).
        final RunRange range = literal && 1 == seed ? runRange(insts, 1, insts.size()) : null;
        final int runEnd = null == range ? -1 : runEnd(insts, typed, floats, range.index());
        final boolean runAxis = null != range && 2 <= range.count()
                && 1 == runStart(typed, floats, range.index())
                && range.index() < runEnd
                && floats.matches(typed.get(range.index()));
        if (0 > cutIndex && !runAxis)
            return code;
        if (!runAxis && Partition.Kind.COPY == split.kind()
                && !idempotentCombiner(combined, carriers.get(cutIndex)))
            return code;
        final int members = runAxis ? (int) Math.min(range.count(), Integer.MAX_VALUE) : seed;
        if (2 > members)
            return code;

        // the placement that ships — the plan's distributing route — states what its targets must serve
        final Placement route = plan.where().stream().filter(placement -> placement.shape().distributes()).findFirst().orElse(null);
        if (null == route)
            return code;
        // THE SHARES: the place set resolved against the snapshot, one entry per share, the HOME place's shares FIRST.
        // A literal seed clamps the shares; a computed bag's size is the branch's to know; the ARITY caps the window.
        final List<ResolvedPlace> shares = shares(resolve(env, route, place(plan, code)), members, fanout);
        // NOTHING SHIPS: no place answered, or the single home lane — one home share is the code that already stands
        // here. Several home shares are the lanes case, a peerless machine sharding.
        final boolean localOnly = 1 >= shares.size() && shares.stream().allMatch(ResolvedPlace::isLocal);
        if (localOnly)
            return code;
        // THE FORM IS RESTATED, NEVER THE NUMBER: the lane's budget is the same arity with the height decremented and
        // the unstated arity keeps its spelling; below a height of one the leaf states the bare point, 0.
        final Obj budget = 1 == axis.height() ? jnt(0) : Hop.of(axis.height() - 1, axis.arity()).toObj();

        // PROFILING IS AN END CAP, NOT A TAIL: when the chain's instruction after the cut is profile(), each lane
        // answers a profiling::T, so the master indexes the reports by the machine each was made on.
        final boolean profiling = 0 <= cutIndex && insts.size() > cutIndex + 1
                && PROFILE_INST_TID.equals(insts.get(cutIndex + 1).tid().basePath());

        if (runAxis)
            return unroll(code, insts, runEnd, head, range, shares, budget);

        // emit [prefix·]branch(place => [start(part)·]run·fold, …)·combiner(r) — BRANCH IS THE ONE DISTRIBUTED
        // PRIMITIVE: each lane runs the floatable run replicated and a local partial reduce where its key points, and
        // the lanes' partials come back as one bag, which the master recombines at home through the combiner.
        final boolean copy = Partition.Kind.COPY == split.kind();
        // the floatable run replicated, then the FOLD; the lane's first instruction states the restated budget.
        final List<Inst> program = new ArrayList<>(insts.subList(runIndex, cutIndex).stream().map(Shard::raw).toList());
        program.add(raw(reducer));
        if (profiling)
            program.add(instB(PROFILE_INST_TID, lst()));
        // ONE KEY PER DISTINCT PLACE, in the lane order: the home place is <~>, a peer is its authority, and the n
        // shares one place takes are one key weighted ?split={n}part.
        final Map<fURI, Integer> places = new LinkedHashMap<>();
        shares.forEach(share -> places.merge(share.isLocal() ? fURI.Singleton.f("~") : share.authority(), 1, Integer::sum));
        // A LITERAL SEED IS DEALT BY THE BRANCH'S OWN RULE (BranchInst#deal), so a planned part is the part the branch
        // would have dealt; under the copy kind every lane holds the whole seed.
        final List<List<Obj>> parts = literal
                ? BranchInst.deal(head.arg(0), places.values().stream().mapToLong(weight -> copy ? 0L : weight).toArray())
                : List.of();
        final List<Map.Entry<fURI, Integer>> keys = List.copyOf(places.entrySet());
        final Map<Obj, Obj> lanes = new LinkedHashMap<>();
        for (int r = 0; r < keys.size(); r++) {
            final List<Inst> lane = new ArrayList<>();
            if (literal)
                lane.add(instB(START_INST_TID, lst(copy ? head.arg(0) : objs(parts.get(r)))));
            lane.addAll(program);
            stampHop(lane, budget);
            final fURI place = keys.get(r).getKey();
            final int weight = keys.get(r).getValue();
            lanes.put(uri(copy || 1 == weight ? place : place.q(SPLIT, "{" + weight + "}" + PART)), code(lane));
        }
        // the branch takes the computed bag whole (a {*} dom over the bag's carrier) and parts it by its keys. When
        // every lane carries its own seed no start stands here, so the branch takes nothing: it is the initial form.
        final fURI carrier = carriers.get(runIndex).typeId();
        final fURI dom = literal ? fURI.Singleton.NOOBJ : null == carrier ? ALL_STAR : carrier.maybeSome();
        final List<Inst> master = new ArrayList<>();
        if (!literal)
            master.addAll(insts.subList(0, runIndex));
        master.add(instB(BRANCH_INST_TID.dom(dom).q(SPLIT, copy ? COPY : PART), rec(lanes)));
        if (profiling)
            master.add(profileCollate());
        else {
            master.addAll(combiner);
            master.addAll(insts.subList(cutIndex + 1, insts.size()));
        }
        return code(master);
    }

    /**
     * THE RUN PARTITION: factor a span range of the run over the roster. One lane per distinct place, each carrying the
     * whole seed and the run with its span narrowed to that lane's share of the unroll index — a contiguous subrange
     * chosen by WORK, because unroll {@code k} costs {@code k} applications and an index-even split leaves the last lane
     * with almost all of it. The branch recombines the terms; the chain's tail (from {@code runEnd}) runs at home over
     * the branch's bag, so a reducer after the run folds the union exactly as it folds the serial unroll.
     */
    private static Code unroll(final Code code, final List<Inst> insts, final int runEnd, final Inst head,
                               final RunRange range, final List<ResolvedPlace> shares, final Obj budget) {
        final List<fURI> places = shares.stream()
                .map(share -> share.isLocal() ? fURI.Singleton.f("~") : share.authority())
                .distinct().toList();
        final int lanes = (int) Math.min(Math.min(places.size(), range.count()), Integer.MAX_VALUE);
        if (2 > lanes)
            return code;
        final List<long[]> ranges = splitByCost(range.min(), range.max(), lanes);
        final Map<Obj, Obj> routes = new LinkedHashMap<>();
        for (int r = 0; r < lanes; r++) {
            final long[] share = ranges.get(r);
            final List<Inst> lane = new ArrayList<>();
            // THE WHOLE SEED on every lane: the unrolls are taken from the same input, and the ranges are disjoint.
            lane.add(instB(START_INST_TID, lst(head.arg(0))));
            for (int i = 1; i < runEnd; i++) {
                Inst inst = raw(insts.get(i));
                // a NARROWED span is a fresh instruction, never a clone: Inst.tid(fURI) clones (and the clone reads
                // the vid, which recomputes a coefficient the span is being rewritten out of) — instB writes the tid.
                if (i == range.index())
                    inst = instB(inst.tid().b(cInt.of(share[0], share[1])), inst.args());
                lane.add(inst);
            }
            stampHop(lane, budget);
            routes.put(uri(places.get(r)), code(lane));
        }
        final List<Inst> master = new ArrayList<>();
        // seeded lanes alone need no lhs: the branch is the initial form, with the dom the seed declares
        master.add(instB(BRANCH_INST_TID.dom(fURI.Singleton.NOOBJ), rec(routes)));
        master.addAll(insts.subList(runEnd, insts.size()));
        return code(master);
    }

    /**
     * THE RUN'S START: the first instruction after the seed of the maximal floatable stretch holding the span. A
     * non-floatable instruction before the span keeps the run from the seed, and this run partition declines — its
     * prefix would have to be broadcast (the next chunk's copy case).
     */
    private static int runStart(final List<Inst> typed, final InstPattern floats, final int spanIndex) {
        int start = spanIndex;
        while (start > 1 && floats.matches(typed.get(start - 1)))
            start--;
        return start;
    }

    /**
     * THE RUN'S END: the first instruction after the span that does not float. From there the chain runs at home over
     * the branch's bag — a reducer folds the union in the lanes' order, which is the serial unroll's order.
     */
    private static int runEnd(final List<Inst> insts, final List<Inst> typed, final InstPattern floats, final int spanIndex) {
        int end = spanIndex + 1;
        while (end < insts.size() && floats.matches(typed.get(end)))
            end++;
        return end;
    }

    /**
     * THE UNROLL INDEX, SPLIT BY WORK. Unroll {@code k} applies the step {@code k} times, so a subrange's cost is
     * {@code Σ k}; the boundaries solve {@code T(p) = (lane / lanes) · T(n)} on the triangular numbers, which places
     * them at the square root of the work rather than at even indices. Every range is non-empty and contiguous (a lane's
     * program is one narrowed span), and the ranges cover {@code [min, max]} exactly once.
     */
    private static List<long[]> splitByCost(final long min, final long max, final int lanes) {
        final List<long[]> ranges = new ArrayList<>(lanes);
        if (1 >= lanes) {
            ranges.add(new long[]{min, max});
            return ranges;
        }
        final double below = min <= 0 ? 0.0 : ((double) (min - 1) * min) / 2.0;
        final double total = ((double) max * (max + 1)) / 2.0 - below;
        long start = min;
        for (int lane = 0; lane < lanes - 1; lane++) {
            final double target = ((double) (lane + 1) * total) / lanes;
            final long bound = Math.min(max - (lanes - 1 - lane), max);
            final long at = Math.round((Math.sqrt(1.0 + 8.0 * (below + target)) - 1.0) / 2.0);
            final long end = Math.max(start, Math.min(at, bound));
            ranges.add(new long[]{start, end});
            start = end + 1;
        }
        ranges.add(new long[]{start, max});
        return ranges;
    }

    /**
     * THE FIRST CLOSED SPAN RANGE of the run: an instruction whose span {@code b()} states both bounds with
     * {@code 0 <= min < max}. A point span ({@code {n}}) is a lone compose chain, and an open range ({@code {+}},
     * {@code {*}}) has no last unroll: neither is a branch. Null when the run has no range.
     */
    private static RunRange runRange(final List<Inst> insts, final int from, final int to) {
        for (int i = Math.max(1, from); i < to; i++) {
            final cInt span = insts.get(i).tid().b();
            if (null == span || span.isVariable() || null == span.min() || null == span.max())
                continue;
            if (0 <= span.min() && span.min() < span.max())
                return new RunRange(i, span.min(), span.max());
        }
        return null;
    }

    private record RunRange(int index, long min, long max) {
        long count() {
            return this.max - this.min + 1;
        }
    }

    /**
     * THE INSTRUCTION AS WRITTEN. A lane is compiled where it runs, so the program it carries holds nothing this
     * machine bound: not the resolved signature, not a bound function and not a position vid. What stays is the op
     * address, the caller's own query, the coefficient and the arguments.
     */
    private static Inst raw(final Inst inst) {
        final Poly args = inst.args().isRec()
                ? rec(inst.args().asRec().elements().map(kv -> rel(kv.first(), rawArg(kv.second()))))
                : lst(inst.args().elements().map(Shard::rawArg).toList());
        return instB(inst.tid().removeQ(DOM).removeQ(RNG).removeQ(ARGS), args);
    }

    private static Obj rawArg(final Obj arg) {
        return arg.isObjInst() ? raw(arg.asInst())
                : arg.isCode() ? code(arg.asCode().insts().stream().map(Shard::raw).toList())
                : arg;
    }

    /**
     * THE BUDGET, STATED ON THE LANE'S FIRST INSTRUCTION. A leaf states {@code 0} and an interior lane the decremented
     * form; no copied instruction is stripped of a budget it carries, because the first statement wins ({@link
     * #hopAxis}) and the head is the one a human and a peer read. The head is normalized to the current spelling
     * ({@code ?hop}); the deprecated alias it may carry is superseded there.
     */
    private static void stampHop(final List<Inst> chain, final Obj budget) {
        if (chain.isEmpty())
            return;
        final Inst head = chain.getFirst();
        chain.set(0, head.tid(head.tid().removeQ(HOP).removeQ(Placement.HOP_ALIAS).q(HOP, budget)));
    }

    /**
     * THE HOP AXIS of a fold, resolved in chain order: the earliest {@code ?hop} (or its deprecated alias) wins, else
     * the plan's tree route. A value the projection cannot read is REFUSED — {@code hop} is the tree's height and a
     * silent zero is the point topology.
     */
    static Hop hopAxis(final Plan plan, final Code code) {
        for (final Inst inst : code.insts()) {
            if (inst.tid().hasQ(HOP))
                return hopAxis(inst.tid(), HOP);
            if (inst.tid().hasQ(Placement.HOP_ALIAS))
                return hopAxis(inst.tid(), Placement.HOP_ALIAS);
        }
        return plan.where().stream()
                .filter(placement -> Placement.Shape.TREE == placement.shape())
                .map(Placement::hopAxis)
                .findFirst().orElse(Hop.point());
    }

    private static Hop hopAxis(final fURI tid, final String key) {
        final Obj value = strictQValue(tid, key);
        try {
            return Hop.of(value);
        } catch (final MTronException e) {
            throw MTronException.of("?%s=%s cannot be read as a hop: %s", key, tid.q(key), e.getMessage());
        }
    }

    /**
     * THE PARTITION AXIS, resolved in chain order exactly as the hop axis is: a {@code ?split} on any instruction of
     * the chain — a branch's own split belongs to its keys, not to this fold — else the plan's distributing route.
     */
    private static Partition split(final Plan plan, final Code code) {
        for (final Inst inst : code.insts())
            if (inst.tid().hasQ(SPLIT) && !BRANCH_INST_TID.equals(inst.tid().basePath()))
                return split(inst.tid(), SPLIT);
        return plan.where().stream()
                .filter(placement -> placement.shape().distributes())
                .map(Placement::split)
                .findFirst().orElse(Partition.stream());
    }

    private static Partition split(final fURI tid, final String key) {
        final Obj value = strictQValue(tid, key);
        try {
            return Partition.of(value);
        } catch (final MTronException e) {
            throw MTronException.of("?%s=%s cannot be read as a partition: %s", key, tid.q(key), e.getMessage());
        }
    }

    /**
     * THE PLACE SET of a fold, resolved in chain order: a {@code ?place} on any instruction, else the plan's own
     * distributing place set. A set the projection cannot read is REFUSED, never silently ignored.
     */
    private static PlaceSet place(final Plan plan, final Code code) {
        for (final Inst inst : code.insts())
            if (inst.tid().hasQ(PLACE))
                return place(inst.tid(), PLACE);
        return plan.where().stream()
                .filter(placement -> placement.shape().distributes())
                .map(Placement::place)
                .findFirst().orElse(PlaceSet.none());
    }

    private static PlaceSet place(final fURI tid, final String key) {
        final Obj value = strictQValue(tid, key);
        try {
            return PlaceSet.of(value);
        } catch (final MTronException e) {
            throw MTronException.of("?%s=%s cannot be read as a place set: %s", key, tid.q(key), e.getMessage());
        }
    }

    /**
     * THE STRICT Q READ: the object a q key states, or a refusal naming the key and the text. The whole value is taken
     * — {@code [local,peer]} is one value holding two designators — and a {@code fail}, a {@code str} and an empty value
     * are refused here, so a reader only checks the shape it needs.
     */
    @SuppressWarnings("unchecked")
    private static Obj strictQValue(final fURI tid, final String key) {
        final Obj value = tid.qValue(key, (Class<Obj>) (Class<?>) Lst.class);
        final List<Obj> members = value.stream().toList();
        if (members.stream().anyMatch(member -> member.isFail() || member.isStr()))
            throw MTronException.of("?%s=%s cannot be read", key, tid.q(key));
        if (members.isEmpty())
            throw MTronException.of("?%s=%s states no value", key, tid.q(key));
        return value;
    }

    /**
     * Whether the cut's COMBINER declares {@code idempotent}: its terminal instruction is the fold that recombines,
     * read through the carrier-resolved claim and the instruction's own {@code ?decq} declaration.
     */
    private static boolean idempotentCombiner(final Call combined, final Type carrier) {
        final List<Inst> folds = combined.insts();
        return !folds.isEmpty() && (declares(folds.getLast(), carrier, Law.idempotent)
                || Law.Helper.declares(folds.getLast().tid(), Law.idempotent));
    }

    /**
     * Retype a bare chain instruction with its declared (dom/rng-qualified) tid, so {@code Form.of} and
     * {@code Law.Helper.declares} read its coefficient shape and laws before resolution.
     */
    private static Inst retype(final Inst inst, final Type carrier) {
        return Law.Helper.keyed(inst, carrier);
    }

    /**
     * THE LANES: the places the route's place axis resolves to against this application's snapshot, one place per
     * share; empty when nothing answered. The home place's shares come first, the resolver's order is kept, the data
     * clamps each place's demand and the arity caps the window.
     */
    private static List<ResolvedPlace> resolve(final Env env, final Placement route, final PlaceSet places) {
        try {
            return PlaceResolver.resolve(places, env, route.inst());
        } catch (final Exception e) {
            return List.of();
        }
    }

    private static List<ResolvedPlace> shares(final List<ResolvedPlace> resolved, final int cap, final int fanout) {
        final List<ResolvedPlace> ordered = new ArrayList<>(resolved.size());
        resolved.stream().filter(ResolvedPlace::isLocal).forEach(ordered::add);
        resolved.stream().filter(place -> !place.isLocal()).forEach(ordered::add);
        final List<ResolvedPlace> shares = new ArrayList<>();
        for (final ResolvedPlace place : ordered)
            for (int share = 0; share < asked(place, cap); share++)
                shares.add(place);
        return shares.size() > fanout ? List.copyOf(shares.subList(0, fanout)) : shares;
    }

    private static int asked(final ResolvedPlace place, final int cap) {
        final cInt demand = place.demand();
        final Long asked = null == demand.max() ? demand.min() : demand.max();
        if (null == asked)
            return Integer.MAX_VALUE == cap ? 1 : cap;
        return (int) Math.min(asked, cap);
    }
}
