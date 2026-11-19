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
 * <p><b>Every cut plans the run before it.</b> A chain may fold more than once: {@code a.r.b.s} has two cuts, {@code r}
 * and {@code s}, and each plans the floatable run before it without reaching back past the cut before. A run ships
 * through its own branch, and its cut's combiner brings the computation home, where the next run starts (the run
 * between two cuts, {@link Span.Kind#BETWEEN}). One application plans every cut.
 *
 * <p><b>The partitions are one funnel.</b> A distribution factors an index set over the roster: the seed's point
 * ({@code d()} instances, the initial value's members), a run's span range ({@code b()} instances, the unrolls
 * {@code a^[m,n] = Σ a^k}) and a run instruction's point ({@code d()} copies, {@code a{p} = a + … + a}) are index sets,
 * and a lane's program is the chain with its share projected onto it — {@code start(part)} projects the members,
 * {@code {p,q}step} the unrolls, {@code step{k}} the copies. A lane may carry more than one projection. v1 factors one
 * axis per run: the seed first, the run's span range or point when the seed is one obj. A fold answers one obj, so the
 * run after a cut factors its span range or point, with that obj copied to every lane.
 *
 * <p><b>The lane's budget is stated on its first instruction</b> ({@code ?hop}, {@code 0} at a leaf), so the metadata
 * is where a human and a re-planning peer read it. A copied instruction carries no budget of its own: both spellings
 * ({@code hop} and the deprecated {@code hops}) are stripped as the lane is written, or a peer handed a lane would
 * re-plan with the root's height.
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
     * snapshot — nothing inside reads the machine. The chain is walked cut by cut, and each cut's run is emitted
     * ({@link #emit}) or left as written.
     */
    public static Code apply(final Plan plan, final Code code, final Env env) {
        // A CHAIN THAT ALREADY BRANCHES BY PLACE IS A LAYOUT, NOT A FOLD TO PLAN: its keyed branch routes its lanes where
        // its keys point, and planning around it would wrap that branch in another. The fixpoint hands this rule every
        // layout it emitted, so this is also where the shard's own output stops: one application plans every cut, so a
        // layout holds nothing left to plan.
        if (code.insts().stream().anyMatch(inst -> BRANCH_INST_TID.equals(inst.tid().basePath()) && inst.args().isRec()))
            return code;
        // A LONE MACHINE DOES NOT FAN OUT -- unless its ROSTER declares a candidate (a declared machine is a
        // potential peer: the declared half of the liveness test, §4), or the decision states its own place set, whose
        // HOME lane is the lanes case (?place=[{4}local]: a peerless machine shards).
        final boolean stated = code.insts().stream().anyMatch(inst -> inst.tid().hasQ(PLACE));
        final boolean dialed = !env.peers().isEmpty();
        if (!dialed && env.machines().isEmpty() && !stated)
            return code;
        final List<Inst> insts = code.insts();
        // THE SEED IS THE INITIAL FORM (dom {0}) at the head of the chain, read off the contract the chain's own typing
        // resolved it to — never off its name.
        final List<Inst> contracts = 2 > insts.size() ? List.of() : contracts(code);
        if (contracts.isEmpty() || null == contracts.getFirst() || !contracts.getFirst().isInitial())
            return code;
        // the placement that ships — the plan's distributing route — states what its targets must serve
        final Placement route = plan.where().stream().filter(placement -> placement.shape().distributes()).findFirst().orElse(null);
        if (null == route)
            return code;
        // each instruction keyed at the carrier flowing into it, not the seed's
        final List<Type> carriers = carriers(code);
        final List<Inst> typed = IntStream.range(0, insts.size())
                .mapToObj(i -> 0 == i ? insts.getFirst() : retype(insts.get(i), carriers.get(i)))
                .toList();
        final Chain chain = new Chain(insts, typed, contracts, carriers, plan.cut().laws().toArray(Law[]::new), route, env, dialed);
        // EVERY CUT PLANS THE RUN BEFORE IT. A cut is a fold whose partials recombine through a fold declaring the cut's
        // laws: its run ships, and its combiner brings the computation home, where the next run starts. A run states its
        // own axes; one that states none keeps the axes the run before it resolved, else the plan's, so a budget or a
        // place set stated at the head governs the whole chain and a ?hop=0 on a later fold keeps only its own run home.
        // A run that declines stays as written. PAST THE LAST CUT (W) the rest is floatable runs and the home stretches
        // between them: each run ships on a branch of its own, dealing the bag the stretch before it hands on, so an
        // effect brings the members home and the run after it goes out again; once nothing floats, the tail stays home.
        final InstPattern recombines = InstPattern.law(Law.blocked).combinedBy(chain.laws());
        final InstPattern floats = InstPattern.law(Law.floatable);
        Axes axes = new Axes(plan.where().stream()
                .filter(placement -> Placement.Shape.TREE == placement.shape())
                .map(Placement::hopAxis)
                .findFirst().orElse(Hop.point()), route.split(), route.place(), false);
        final List<Inst> master = new ArrayList<>(insts.size());
        boolean planned = false;
        int from = 0;
        while (from < insts.size()) {
            final int start = Math.max(1, from);
            final int cut = IntStream.range(start, insts.size()).filter(i -> recombines.matches(typed.get(i))).findFirst().orElse(-1);
            final int run = 0 <= cut ? -1 : IntStream.range(start, insts.size()).filter(i -> floats.matches(typed.get(i))).findFirst().orElse(-1);
            if (0 > cut && 0 > run)
                break;
            // PROFILING IS AN END CAP, NOT A TAIL: when the instruction after the cut is profile(), each lane answers a
            // profiling::T, and the master's run is timed around its branch and nests the reports (emit), so the run
            // reaches the end
            final boolean profiling = 0 <= cut && insts.size() > cut + 1
                    && PROFILE_INST_TID.equals(insts.get(cut + 1).tid().basePath());
            final int end = 0 > cut ? runEnd(insts, typed, floats, run) : profiling ? insts.size() : cut + 1;
            final List<Inst> own = insts.subList(from, end);
            axes = new Axes(hopAxis(own, axes.hop()), split(own, axes.split()), place(own, axes.place()),
                    axes.placed() || own.stream().anyMatch(inst -> inst.tid().hasQ(PLACE)));
            final List<Inst> emitted = emit(chain, from, cut, end, axes, profiling);
            master.addAll(null == emitted ? own : emitted);
            planned |= null != emitted;
            from = end;
        }
        if (!planned)
            return code;
        master.addAll(insts.subList(from, insts.size()));
        return code(master);
    }

    /**
     * ONE CUT'S RUN, EMITTED: the instructions that replace {@code [from, end)} — the run's home prefix, the keyed branch
     * the run ships through, and the combiner that brings it home — or null when the run declines and stays as written.
     * A {@code cut} of -1 is a stretch past the last cut, {@code [from, end)}: the home instructions before one floatable
     * run, and the run itself, which ends at {@code end}. It ships as the cutless run partition (an index set at the
     * head) or dealt over places (W), with nothing to recombine but the branch's own merge.
     */
    private static List<Inst> emit(final Chain chain, final int from, final int cut, final int end, final Axes axes,
                                   final boolean profiling) {
        final List<Inst> insts = chain.insts();
        final List<Inst> typed = chain.typed();
        // THE HOP AXIS: how many more levels of the tree may be planned from here, and how many children one level may
        // fan out to. A height of zero is a leaf and an arity of zero is a point: the identity, byte for byte.
        final Hop hop = axes.hop();
        if (hop.isPoint() || hop.arity().isZero())
            return null;
        // THE ARITY CAPS THE FAN-OUT: at most `w` lanes leave one node; the data still clamps it (`k = min(shares, seed)`).
        final int fanout = (int) Math.min(null == hop.arity().max() ? Integer.MAX_VALUE : hop.arity().max(), Integer.MAX_VALUE);
        // THE PARTITION AXIS: what counts as one place's share. This chunk lowers STREAM and COPY; the other kinds
        // DECLINE TO THE IDENTITY -- the unchanged chain, never a different-but-plausible layout.
        final Partition split = axes.split();
        if (Partition.Kind.STREAM != split.kind() && Partition.Kind.COPY != split.kind()) {
            log.debug("?split={} does not lower in this chunk: the fold stays where it stands ({})", split, insts.subList(from, end));
            return null;
        }
        // THE RUN is the floatable stretch right before the cut (past the last cut, the stretch's first floatable
        // instruction up to end), never reaching back past its first instruction: the one after the seed at the head, the
        // one after the cut before it elsewhere. Whatever lies between that instruction and the run stays home.
        final InstPattern floats = InstPattern.law(Law.floatable);
        final int first = Math.max(1, from);
        int runIndex = 0 <= cut ? cut : IntStream.range(first, end).filter(i -> floats.matches(typed.get(i))).findFirst().orElse(end);
        while (runIndex > first && floats.matches(typed.get(runIndex - 1)))
            runIndex--;
        runIndex = expanded(chain, from, cut, runIndex);
        // a self-combining fold re-applies the chain's own instruction; a fold defined by derivation recombines through
        // the fold its definition names; a keyed fold through the chain its pointwise combiner computes
        final Call combined = 0 > cut ? null : Law.Helper.combiner(typed.get(cut), chain.laws());
        final List<Inst> combiner = null == combined ? List.of()
                : typed.get(cut) == combined ? List.of(insts.get(cut)) : combined.insts();
        // THE SEED: a literal at the head is dealt by the plan; anything else is the bag the branch takes when it runs.
        // ONE OBJ HAS NOTHING TO DEAL -- a literal of one member, or what a fold answers (a gather whose rng is one),
        // carried to the run by the instructions that stay home before it: its one multiplicity is a span range in the run.
        final Inst head = insts.getFirst();
        final boolean literal = 0 == from && 1 == runIndex && literal(head);
        final long seed = literal ? head.arg(0).stream().count() : Long.MAX_VALUE;
        final boolean one = literal ? 1 == seed : one(chain, runIndex);
        // THE RUN AXIS: a closed span range or a point in the run, factored when the seed is one obj. The unrolls a^[m,n]
        // are independent terms, so a share is a subrange of the exponent, not a part of the seed; a point a{p} is p copies
        // of one term, and points add across a branch, so a share is a count of copies. Every lane carries the whole seed
        // and a narrowed span or point. Under a cut the run is the stretch the cut planned. NO TYPED CUT IS NEEDED at a
        // head with no cut, where the run must start at the seed (a prefix that does not float would have to be
        // broadcast) and ends at its first instruction that does not float, the tail running at home over the branch's bag.
        final RunRange range = one ? runRange(insts, runIndex, 0 > cut ? end : cut) : null;
        // THE SEED AXIS PAST THE LAST CUT (W, embarrassingly parallel): a bag of many members and no fold, so the run is the
        // floatable stretch [runIndex, end), each lane takes its share of the bag (a literal seed dealt by the plan, any
        // other bag by the branch when it runs), and nothing recombines but the branch's own merge -- (a+b)f = af + bf,
        // right distributivity alone.
        final boolean cutless = 0 > cut && null == range && !one;
        final int runEnd = null != range ? (0 <= cut ? cut : runEnd(insts, typed, floats, range.index()))
                : cutless && runIndex < end ? end : -1;
        final boolean runAxis = null != range && (0 <= cut || (1 == runStart(typed, floats, range.index())
                && range.index() < runEnd && floats.matches(typed.get(range.index()))));
        if (!runAxis && (one || (0 > cut && !(cutless && runIndex < runEnd))))
            return null;
        // a copy has no fold to absorb the duplicates, so with no cut every member would come home once per lane
        if (!runAxis && Partition.Kind.COPY == split.kind() && (0 > cut || !idempotentCombiner(combined, chain.carriers().get(cut))))
            return null;
        final long members = runAxis ? range.count() : seed;
        if (2 > members)
            return null;
        // THE CANDIDATES: the run's place set, except that the peer enum with nothing dialed and nothing stated answers
        // the ROSTER's machines -- each named as an address (//host), so the resolver reads the row discovery filed and
        // admits it when its transport's scheme resolves (step 6's declared half of liveness). Planning never dials.
        final PlaceSet candidates = !chain.dialed() && !axes.placed()
                && axes.place().designators().stream().allMatch(designator -> PlaceSet.Form.PEER == designator.form())
                ? roster(chain.env()) : axes.place();
        // THE SHARES: the place set resolved against the snapshot, one entry per share, the HOME place's shares FIRST.
        // A literal seed clamps the shares; a computed bag's size is the branch's to know; the ARITY caps the window.
        final List<ResolvedPlace> shares = shares(resolve(chain.env(), chain.route(), candidates),
                (int) Math.min(members, Integer.MAX_VALUE), fanout);
        // NOTHING SHIPS: no place answered, or the single home lane — one home share is the code that already stands
        // here. Several home shares are the lanes case, a peerless machine sharding.
        if (1 >= shares.size() && shares.stream().allMatch(ResolvedPlace::isLocal))
            return null;
        // THE FORM IS RESTATED, NEVER THE NUMBER: the lane's budget is the same arity with the height decremented and
        // the unstated arity keeps its spelling; below a height of one the leaf states the bare point, 0.
        final Obj budget = 1 == hop.height() ? jnt(0) : Hop.of(hop.height() - 1, hop.arity()).toObj();
        // ONE KEY PER DISTINCT PLACE, in the lane order: the home place is <~>, a peer is its authority. Under the seed
        // axis the n shares one place takes are one key weighted ?split={n}part; under the run axis a key is one lane,
        // at most one per unroll or copy, its share chosen by WORK for a span (splitByCost) and evenly for a point.
        final Map<fURI, Integer> places = new LinkedHashMap<>();
        shares.forEach(share -> places.merge(share.isLocal() ? fURI.Singleton.f("~") : share.authority(), 1, Integer::sum));
        final List<Map.Entry<fURI, Integer>> keys = List.copyOf(places.entrySet());
        final int lanes = runAxis ? (int) Math.min(keys.size(), range.count()) : keys.size();
        if (runAxis && 2 > lanes)
            return null;
        final List<long[]> exponents = !runAxis ? List.of()
                : range.point() ? splitEvenly(range.count(), lanes) : splitByCost(range.min(), range.max(), lanes);
        // A LITERAL SEED IS DEALT BY THE BRANCH'S OWN RULE (BranchInst#deal), so a planned part is the part the branch
        // would have dealt; under the copy kind and the run axis every lane holds the whole seed.
        final boolean copy = Partition.Kind.COPY == split.kind();
        final List<List<Obj>> parts = literal && !runAxis
                ? BranchInst.deal(head.arg(0), keys.stream().mapToLong(key -> copy ? 0L : key.getValue()).toArray())
                : List.of();
        // emit [prefix·]branch(place => [start(part)·]run·fold, …)·combiner — BRANCH IS THE ONE DISTRIBUTED PRIMITIVE:
        // each lane runs the floatable run (its share of a span range projected onto it) and a local partial reduce where
        // its key points, so one partial per lane travels home, where the master recombines them through the combiner.
        final int stop = 0 > cut ? runEnd : cut;
        final Map<Obj, Obj> routes = new LinkedHashMap<>();
        for (int r = 0; r < lanes; r++) {
            final List<Inst> lane = new ArrayList<>();
            if (literal)
                lane.add(instB(START_INST_TID, lst(runAxis || copy ? head.arg(0) : objs(parts.get(r)))));
            for (int i = runIndex; i < stop; i++) {
                final Inst inst = raw(insts.get(i));
                // a NARROWED span or point is a fresh instruction, never a clone: Inst.tid(fURI) clones (and the clone
                // reads the vid, which recomputes a coefficient being rewritten out of it) — instB writes the tid.
                final long[] share = runAxis ? exponents.get(r) : null;
                lane.add(runAxis && i == range.index()
                        ? instB(range.point() ? inst.tid().d(cInt.of(share[1] - share[0] + 1))
                        : inst.tid().b(cInt.of(share[0], share[1])), inst.args())
                        : inst);
            }
            if (0 <= cut)
                lane.add(raw(insts.get(cut)));
            if (profiling && !runAxis)
                lane.add(instB(PROFILE_INST_TID, lst()));
            stampHop(lane, budget);
            final fURI place = keys.get(r).getKey();
            final int weight = keys.get(r).getValue();
            routes.put(uri(runAxis || copy || 1 == weight ? place : place.q(SPLIT, "{" + weight + "}" + PART)), code(lane));
        }
        // lanes seeded with a literal take nothing, so the branch is the initial form; otherwise the branch takes the bag
        // whole (a {*} dom over the carrier flowing into the run) and deals it by its keys -- or, under the run axis,
        // copies it to every lane, whose shares of the exponent are disjoint.
        final fURI carrier = chain.carriers().get(runIndex).typeId();
        final fURI dom = literal ? fURI.Singleton.NOOBJ : null == carrier ? ALL_STAR : carrier.maybeSome();
        final List<Inst> master = new ArrayList<>();
        if (!literal)
            master.addAll(insts.subList(from, runIndex));
        master.add(instB(literal && runAxis ? BRANCH_INST_TID.dom(dom)
                : BRANCH_INST_TID.dom(dom).q(SPLIT, runAxis || copy ? COPY : PART), rec(routes)));
        if (profiling && !runAxis) {
            // THE MASTER'S RUN IS TIMED AROUND ITS BRANCH: the lanes answer reports, not partials, so no combiner runs,
            // and a bare profile_collate() marks where the master's run ends. The profile_analysis rule, an analysis rule
            // and so applied after this one, captures everything before the marker (the master's whole run, the branch's
            // gather wait included), times it as profile() times a local run and nests the lanes' reports under it
            // (mInstSet.profileCollate). Whatever followed profile(), a >>format, follows the marker.
            master.add(instB(PROFILE_COLLATE_TID, lst()));
            master.addAll(insts.subList(cut + 2, end));
        } else {
            master.addAll(combiner);
            master.addAll(insts.subList(0 > cut ? runEnd : cut + 1, end));
        }
        return master;
    }

    /**
     * A LITERAL SEED: {@code start(x)} whose argument is a value, not a call the branch would have to run first.
     */
    private static boolean literal(final Inst head) {
        return START_INST_TID.equals(head.tid().basePath()) && !head.args().isEmpty() && !head.arg(0).isCall();
    }

    /**
     * ONE OBJ FLOWS INTO THE RUN. Walking back from the run, each instruction that stays home before it hands on at
     * most one obj for the one it takes — its contract maps one to one or to none ({@code print(_)}, a filter), and its
     * own coefficient states no copies and no unrolls — until a fold, which answers one, or the seed, a literal of one
     * member. So any number of home instructions may stand between a fold and the run after it. Anything else (a scatter,
     * a point or span range, an instruction no contract types) leaves a bag of unknown size, which the branch deals.
     */
    private static boolean one(final Chain chain, final int runIndex) {
        for (int i = runIndex - 1; 0 <= i; i--) {
            if (0 == i)
                return literal(chain.insts().getFirst()) && 1 == chain.insts().getFirst().arg(0).stream().count();
            final Inst contract = chain.contracts().get(i);
            if (null == contract)
                return false;
            if (contract.isReducing())
                return true;
            if (!handsOnOne(contract, chain.insts().get(i)))
                return false;
        }
        return false;
    }

    /**
     * A RUN THAT TURNS ONE OBJ INTO MANY STARTS AFTER THE TURN. With one obj flowing in and no index set to factor, the
     * run's leading flatmappers ({@code merge} unrolling the lst a fold answered) stay home and expand the obj into a
     * bag, which the rest of the run deals by member: {@code order(neg()).merge().plus(1).sum()} merges at home and
     * ships {@code plus(1).sum()}. Otherwise the run starts where it stood.
     */
    private static int expanded(final Chain chain, final int from, final int cut, final int runIndex) {
        if (0 > cut)
            return runIndex;
        final Inst head = chain.insts().getFirst();
        final boolean one = 0 == from && 1 == runIndex && literal(head)
                ? 1 == head.arg(0).stream().count()
                : one(chain, runIndex);
        if (!one || null != runRange(chain.insts(), runIndex, cut))
            return runIndex;
        int start = runIndex;
        while (start < cut && Inst.Form.flatmapper == Inst.Form.of(chain.typed().get(start)).subring())
            start++;
        return start;
    }

    private static boolean handsOnOne(final Inst contract, final Inst inst) {
        if (!contract.tid().hasDom() || !contract.tid().hasRng() || contract.isInitial())
            return false;
        final cInt point = inst.tid().d();
        final cInt span = inst.tid().b();
        return atMostOne(contract.dom().c()) && atMostOne(contract.rng().c())
                && (null == point || atMostOne(point) && !point.isZero())
                && (null == span || !span.isVariable() && null != span.min() && span.min().equals(span.max()));
    }

    private static boolean atMostOne(final cInt count) {
        return null != count && !count.isVariable() && null != count.max() && 1 >= count.max();
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
     * THE UNROLL INDEX, SPLIT BY WORK. A lane walks its runs from the whole seed, each step once, the runs sharing their
     * steps (Inst.Helper.runs, Marko 2026-11-15), so a subrange {@code [a, b]} costs the {@code b} steps it walks and
     * the {@code b - a + 1} runs it emits. No split shortens the top lane's walk to {@code max}, so the work balances in
     * the runs: equal costs {@code b + (b - a + 1)} halve a lane's share at each lane up, the shares from the top being
     * {@code w, 2w, 4w, …} of the {@code 2^lanes - 1} parts. {@code {0,4}} over two lanes is {@code [0,2]} and
     * {@code [3,4]}, costing 5 and 6. Every range is non-empty and contiguous (a lane's program is one narrowed span),
     * and the ranges cover {@code [min, max]} exactly once.
     */
    private static List<long[]> splitByCost(final long min, final long max, final int lanes) {
        final List<long[]> ranges = new ArrayList<>(lanes);
        long start = min;
        for (int lane = 0; lane < lanes - 1; lane++) {
            // of the lanes left, this one holds 2^(left-1) of the 2^left - 1 parts of the runs left, and leaves a run to
            // each lane above it
            final int left = lanes - lane;
            final double share = (max - start + 1) * Math.pow(2, left - 1) / (Math.pow(2, left) - 1);
            final long end = Math.max(start, Math.min(start + Math.round(share) - 1, max - (left - 1)));
            ranges.add(new long[]{start, end});
            start = end + 1;
        }
        ranges.add(new long[]{start, max});
        return ranges;
    }

    /**
     * THE COPIES OF A POINT, SPLIT EVENLY. A point {@code {p}} is p copies of one term, each costing the same, so the
     * shares are contiguous runs of the copies {@code [1, p]} whose sizes differ by at most one: {@code {5}} over two
     * lanes is three copies and two.
     */
    private static List<long[]> splitEvenly(final long count, final int lanes) {
        final List<long[]> ranges = new ArrayList<>(lanes);
        long start = 1;
        for (int lane = 0; lane < lanes; lane++) {
            final long size = count / lanes + (lane < count % lanes ? 1 : 0);
            ranges.add(new long[]{start, start + size - 1});
            start += size;
        }
        return ranges;
    }

    /**
     * THE FIRST INDEX SET of the run: an instruction whose span {@code b()} states both bounds with
     * {@code 0 <= min < max}, or whose point {@code d()} is exactly two or more. A point span ({@code {n}}) is a lone
     * compose chain, an open range ({@code {+}}, {@code {*}}) has no last unroll, and a point of one is one term: none
     * is a branch. Null when the run has neither.
     */
    private static RunRange runRange(final List<Inst> insts, final int from, final int to) {
        for (int i = Math.max(1, from); i < to; i++) {
            final cInt span = insts.get(i).tid().b();
            if (null != span && !span.isVariable() && null != span.min() && null != span.max()
                    && 0 <= span.min() && span.min() < span.max())
                return new RunRange(i, span.min(), span.max(), false);
            final cInt point = insts.get(i).tid().d();
            if (null != point && !point.isVariable() && null != point.min() && point.min().equals(point.max())
                    && 2 <= point.min())
                return new RunRange(i, 1, point.min(), true);
        }
        return null;
    }

    /**
     * An index set of the run at {@code index}: the unrolls {@code [min, max]} of a span range, or the copies
     * {@code [1, p]} of a {@code point}.
     */
    private record RunRange(int index, long min, long max, boolean point) {
        long count() {
            return this.max - this.min + 1;
        }
    }

    /**
     * THE CHAIN a walk plans over, read once: its instructions, each one typed at the carrier flowing into it, the
     * contract each resolved to, the carriers, the cut's laws, the plan's distributing route, and the snapshot.
     */
    private record Chain(List<Inst> insts, List<Inst> typed, List<Inst> contracts, List<Type> carriers, Law[] laws,
                         Placement route, Env env, boolean dialed) {
    }

    /**
     * THE THREE AXES a run is planned under (§2–§4) — how far and how wide, what counts as one share, and where — and
     * whether a place set was stated rather than read off the plan.
     */
    private record Axes(Hop hop, Partition split, PlaceSet place, boolean placed) {
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
     * THE BUDGET, STATED ON THE LANE'S HEAD. A leaf states {@code 0} and an interior lane the decremented form, on the
     * first instruction the lane carries ({@code start(part)?hop=n}) — the metadata a human and a re-planning peer read
     * in the same place, and where the received lane's own {@link #hopAxis} finds it. Every copied instruction is
     * stripped of both spellings first: the root has already read the level above's budget, and a slave handed to a
     * peer must not carry it (under either spelling, or the peer would re-plan with the root's height).
     */
    private static void stampHop(final List<Inst> chain, final Obj budget) {
        if (chain.isEmpty())
            return;
        for (int i = 0; i < chain.size(); i++) {
            final Inst inst = chain.get(i);
            final fURI tid = inst.tid().removeQ(HOP).removeQ(Placement.HOP_ALIAS);
            chain.set(i, inst.tid(0 == i ? tid.q(HOP, budget) : tid));
        }
    }

    /**
     * THE HOP AXIS of a run, resolved in chain order: the earliest {@code ?hop} (or its deprecated alias) among its
     * instructions wins, else the axis it {@code inherits}. A value the projection cannot read is REFUSED — {@code hop}
     * is the tree's height and a silent zero is the point topology.
     */
    private static Hop hopAxis(final List<Inst> insts, final Hop inherited) {
        for (final Inst inst : insts) {
            if (inst.tid().hasQ(HOP))
                return hopAxis(inst.tid(), HOP);
            if (inst.tid().hasQ(Placement.HOP_ALIAS))
                return hopAxis(inst.tid(), Placement.HOP_ALIAS);
        }
        return inherited;
    }

    @SuppressWarnings("unchecked")
    private static Hop hopAxis(final fURI tid, final String key) {
        // NOT THE STRICT READ: a zero coefficient is the hop's arity ({0}3 is the point, height 3), but a zero streams as
        // nothing, which the strict read refuses as an empty value. Hop.of refuses what it cannot read, noobj included
        final Obj value = tid.qValue(key, (Class<Obj>) (Class<?>) Lst.class);
        try {
            return Hop.of(value);
        } catch (final MTronException e) {
            throw MTronException.of("?%s=%s cannot be read as a hop: %s", key, tid.q(key), e.getMessage());
        }
    }

    /**
     * THE PARTITION AXIS of a run, resolved in chain order exactly as the hop axis is: a {@code ?split} on any of its
     * instructions — a branch's own split belongs to its keys, not to this fold — else the axis it {@code inherits}.
     */
    private static Partition split(final List<Inst> insts, final Partition inherited) {
        for (final Inst inst : insts)
            if (inst.tid().hasQ(SPLIT) && !BRANCH_INST_TID.equals(inst.tid().basePath()))
                return split(inst.tid(), SPLIT);
        return inherited;
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
     * THE PLACE SET of a run, resolved in chain order: a {@code ?place} on any of its instructions, else the set it
     * {@code inherits}. A set the projection cannot read is REFUSED, never silently ignored.
     */
    private static PlaceSet place(final List<Inst> insts, final PlaceSet inherited) {
        for (final Inst inst : insts)
            if (inst.tid().hasQ(PLACE))
                return place(inst.tid(), PLACE);
        return inherited;
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
     * THE ROSTER AS CANDIDATES — the places a peer enum names when nothing is dialed. Each roster machine is written as
     * an address ({@code //host}), which {@link PlaceResolver} reads through the roster and admits by the declared half
     * of liveness (its transport's scheme resolves in the ISA). No dial is attempted here: a candidate is planned, and
     * the run — the branch's own key — is what connects.
     */
    private static PlaceSet roster(final Env env) {
        final List<fURI> hosts = env.machines().elements()
                .map(Rel::first)
                .filter(Obj::isUri)
                .map(key -> key.uriValue())
                .map(host -> fURI.Singleton.f("//" + (null == host.host() ? host.toString() : host.host())))
                .distinct()
                .toList();
        return hosts.isEmpty() ? PlaceSet.none() : PlaceSet.of(hosts.toArray(fURI[]::new));
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
