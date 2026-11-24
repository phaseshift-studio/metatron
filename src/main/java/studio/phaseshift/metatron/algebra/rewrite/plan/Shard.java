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
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.algebra.Law.Helper.declares;
import static studio.phaseshift.metatron.isa.m.mach.type.compiler.Rewriter.Helper.carriers;
import static studio.phaseshift.metatron.isa.m.mach.type.compiler.Rewriter.Helper.contracts;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.mach.machInstSet.CUT_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Shard — the distribution rule's emitter, the one body behind {@code law_shard}. It lowers a plan onto a chain over one
 * immutable {@link Env} snapshot: guard on the dialed peers, match the chain shape, locate the multiplicity to factor,
 * emit the keyed {@code branch} layout, and leave everything else where it stood.
 *
 * <p><b>Every cut plans the run before it.</b> A chain may fold more than once: {@code a.r.b.s} has two cuts, {@code r}
 * and {@code s}, and each plans the floatable run before it without reaching back past the cut before. A run ships
 * through its own branch, and its cut's combiner brings the computation home, where the next run starts (the run
 * between two cuts, {@link Span.Kind#BETWEEN}). One application plans every cut.
 *
 * <p><b>The partitions are one funnel.</b> A distribution factors an index set over its places: the seed's point
 * ({@code d()} instances, the initial value's members), a run's span range ({@code b()} instances, the unrolls
 * {@code a^[m,n] = Σ a^k}) and a run instruction's point ({@code d()} copies, {@code a{p} = a + … + a}) are index sets,
 * and a lane's program is the chain with its share projected onto it — {@code start(part)} projects the members,
 * {@code {p,q}step} the unrolls, {@code step{k}} the copies. A lane may carry more than one projection. v1 factors one
 * axis per run: the seed first, the run's span range or point when the seed is one obj. A fold answers one obj, so the
 * run after a cut factors its span range or point, with that obj copied to every lane.
 *
 * <p><b>A prefix the combiner respects joins the cut.</b> A select keeping the first k positions right after a fold
 * whose combiner is {@link Law#causal} ships with the fold: {@code order(s)==[{k}_]} runs in every lane and
 * {@code kmerge(s)==[{k}_]} at home, so each lane sends at most k members. The combiner of the longer cut is the
 * combiner's own law at work, {@code combiner(r·t) = combiner(r)·t}, and no instruction declares it (menu entry K).
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
     * A LONE MACHINE DOES NOT FAN OUT — unless the decision states its own place set, whose HOME lane is the lanes case
     * ({@code ?place=[{4}local]}: a peerless machine shards), or names a machine it knows ({@code //host}). PEERS ARE
     * EXPLICIT (Marko, 2026-11-23): the roster is knowledge, not reach, so a machine nothing dialed is never a
     * candidate on its own; a program that wants peers dials them. One rule, read by {@link #apply} off its snapshot
     * and by the pool off the peer map before any snapshot is taken (mInstSet's law_shard).
     */
    public static boolean lone(final Rec peers, final Code code) {
        return peers.isEmpty() && code.insts().stream().noneMatch(inst -> inst.tid().hasQ(PLACE));
    }

    /**
     * WHERE THIS MACHINE'S PLAN ENDS: the first instruction that changes the current machine ({@link Law#nomadic}:
     * move, push, pop, cut), or -1. The nomadic extractor leaves a cut last, or before the explain() or profile() that
     * analyze the chain at home (Marko, 2026-11-24), so a cut can stand inside a run this rule would ship, and copied
     * into lanes its machine change would run at every peer.
     */
    public static int departure(final List<Inst> insts) {
        final List<fURI> nomadic = Law.Helper.claimants(Law.nomadic).stream().map(term -> term.tid().basePath()).toList();
        for (int i = 0; i < insts.size(); i++)
            if (nomadic.contains(insts.get(i).tid().basePath()))
                return i;
        return -1;
    }

    /**
     * The one body (§12.1): {@code apply(plan, env) = locate → resolve → emit}, over one immutable {@link Env}
     * snapshot — nothing inside reads the machine. The chain is walked cut by cut, and each cut's run is emitted
     * ({@link #emit}) or left as written.
     */
    public static Code apply(final Plan plan, final Code code, final Env env) {
        // A CHAIN THAT ALREADY BRANCHES BY PLACE IS A LAYOUT, NOT A FOLD TO PLAN: its branch routes its lanes where their
        // cuts carry them (BranchInst#isLayout), and planning around it would wrap that branch in another. The fixpoint
        // hands this rule every layout it emitted, so this is also where the shard's own output stops: one application
        // plans every cut, so a layout holds nothing left to plan.
        if (code.insts().stream().anyMatch(BranchInst::isLayout))
            return code;
        // A MACHINE CHANGE ENDS WHAT IS PLANNED HERE (departure): the tail is the cut's argument, compiled where it
        // lands, and the cut and the explain() or profile() after it stay as written. This machine plans the stretch
        // before it
        final int departs = departure(code.insts());
        if (0 == departs)
            return code;
        if (0 < departs) {
            final Code here = apply(plan, code(new ArrayList<>(code.insts().subList(0, departs))), env);
            if (here.insts().equals(code.insts().subList(0, departs)))
                return code;
            final List<Inst> relocated = new ArrayList<>(here.insts());
            relocated.addAll(code.insts().subList(departs, code.insts().size()));
            return code(relocated);
        }
        // a lone machine does not fan out (lone)
        if (lone(env.peers(), code))
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
        final Chain chain = new Chain(insts, typed, contracts, carriers, plan.cut().laws().toArray(Law[]::new), route, env);
        // EVERY CUT PLANS THE RUN BEFORE IT. A cut is a fold whose partials recombine through a fold declaring the cut's
        // laws: its run ships, and its combiner brings the computation home, where the next run starts. A run states its
        // own axes; one that states none keeps the axes the run before it resolved, else the plan's, so a budget or a
        // place set stated at the head governs the whole chain, and a ?hop=0 on a later fold keeps its run home and
        // every run after it that states none, never a run before it.
        // A run that declines stays as written. PAST THE LAST CUT (W) the rest is floatable runs and the home stretches
        // between them: each run ships on a branch of its own, dealing the bag the stretch before it hands on, so an
        // effect brings the members home and the run after it goes out again; once nothing floats, the tail stays home.
        final InstPattern recombines = InstPattern.law(Law.blocked).combinedBy(chain.laws());
        final InstPattern floats = InstPattern.law(Law.floatable);
        Axes axes = new Axes(plan.where().stream()
                .filter(placement -> Placement.Shape.TREE == placement.shape())
                .map(Placement::hopAxis)
                .findFirst().orElse(Hop.point()), route.split(), route.place());
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
            // A PREFIX THE COMBINER RESPECTS JOINS THE CUT (K, top-k): a select keeping the first k positions, right after
            // a fold whose combiner is causal, ships with the fold. Each lane keeps its own first k and home keeps the
            // first k of the merge, so combiner(r·t) = combiner(r)·t -- computed from the combiner's law, never declared
            final boolean absorbs = 0 <= cut && !profiling && insts.size() > cut + 1
                    && keepsPrefix(insts.get(cut + 1)) && causalCombiner(typed.get(cut), chain.laws());
            final int end = 0 > cut ? runEnd(insts, typed, floats, run) : profiling ? insts.size() : absorbs ? cut + 2 : cut + 1;
            final List<Inst> own = insts.subList(from, end);
            axes = new Axes(hopAxis(own, axes.hop()), split(own, axes.split()), place(own, axes.place()));
            final List<Inst> emitted = emit(chain, from, cut, end, axes, profiling, absorbs);
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
     * head) or dealt over places (W), with nothing to recombine but the branch's own merge. When the cut {@code absorbs}
     * the select right after it (a prefix kept after a causal combiner, K), every lane ends in that select too, and the
     * master's combiner is followed by it, as the stretch {@code [from, end)} already holds it.
     */
    private static List<Inst> emit(final Chain chain, final int from, final int cut, final int end, final Axes axes,
                                   final boolean profiling, final boolean absorbs) {
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
        // the fold its definition names; a keyed fold through the chain its pointwise combiner computes. A FOLD THAT
        // RECOMBINES THROUGH ITSELF AFTER A LIFT (J, mean ↦ ℓ·mean) has every lane run the lift in its place, so a lane
        // answers a summary of its part, and home runs the fold as its equation states it, over the summaries
        // A POINT OR A SPAN ON A KEYED FOLD'S VALUE (group([k=>mean{2}()]), group([k=>{2}count()])) is written over when
        // the spec is keyed, so neither a lane nor the per-key combiner carries it: such a group stays home and answers
        // as home does (a point multiplies each key's answer; a span, home's group drops, applying the value once). A span on the cut itself ({2}count(), count twice) is the cut run
        // again over its own answer, which no lane can run on a part: it stays home too
        if (0 <= cut && (pointedValues(insts.get(cut)) || !insts.get(cut).tid().b().isOne()))
            return null;
        final Call combined = 0 > cut ? null : Law.Helper.combiner(typed.get(cut), chain.laws());
        final Law.Helper.Lift lifted = 0 > cut || null != combined ? null : Law.Helper.lifted(typed.get(cut));
        final List<Inst> combiner = null != lifted ? pointed(lifted.home(), insts.get(cut))
                : null == combined ? List.of()
                : typed.get(cut) == combined ? List.of(insts.get(cut)) : pointed(combined.insts(), insts.get(cut));
        // THE SEED: a literal at the head is dealt by the plan; anything else is the bag the branch takes when it runs.
        // ONE OBJ HAS NOTHING TO DEAL -- a literal of one member, or what a fold answers (a gather whose rng is one, or a declared reducer whose rng is at most one: sup, inf),
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
        final boolean copy = Partition.Kind.COPY == split.kind();
        // a lifted cut has no combiner of its own to declare idempotent (its fold recombines summaries), so it declines
        if (!runAxis && copy && (0 > cut || null == combined || !idempotentCombiner(combined, chain.carriers().get(cut))))
            return null;
        final long members = runAxis ? range.count() : seed;
        if (2 > members)
            return null;
        // THE SHARES: the place set resolved against the snapshot, one entry per share, the HOME place's shares FIRST.
        // A literal seed clamps the shares; a computed bag's size is the branch's to know; the ARITY caps the window.
        // NO PEER WITHOUT A MEMBER (Marko, 2026-11-20: "don't use all peers if we don't need to"): a literal seed of n
        // members dealt in parts fills at most n shares, so its window is the arity or the seed, whichever is smaller.
        // The run axis and a copy hand every lane the whole seed and take one lane per place, so their window stays the
        // arity
        final int cap = (int) Math.min(members, Integer.MAX_VALUE);
        // THE CANDIDATES are the run's place set, and nothing else: the peer enum is the dialed set, and a machine the
        // roster knows is a candidate only when the place set names it (//host). Planning never dials.
        final List<ResolvedPlace> shares = shares(resolve(chain.env(), chain.route(), axes.place()), cap,
                runAxis || copy ? fanout : Math.min(fanout, cap));
        // NOTHING SHIPS: no place answered, or the single home lane — one home share is the code that already stands
        // here. Several home shares are the lanes case, a peerless machine sharding.
        if (1 >= shares.size() && shares.stream().allMatch(ResolvedPlace::isLocal))
            return null;
        // THE FORM IS RESTATED, NEVER THE NUMBER: the lane's budget is the same arity with the height decremented and
        // the unstated arity keeps its spelling; below a height of one the leaf states the bare point, 0.
        final Obj budget = 1 == hop.height() ? jnt(0) : Hop.of(hop.height() - 1, hop.arity()).toObj();
        // ONE LANE PER DISTINCT PLACE, in the lane order: the home place is <~>, a peer is its authority. Under the seed
        // axis the n shares a peer takes are one lane weighted n, the point on its move, while home's n shares are n
        // home lanes, as a home lane has no move to weigh; under the run axis and a copy a place is one lane, at most
        // one per unroll or copy, its share chosen by WORK for a span (splitByCost) and evenly for a point.
        final Map<fURI, Integer> places = new LinkedHashMap<>();
        shares.forEach(share -> places.merge(share.isLocal() ? fURI.Singleton.f("~") : share.authority(), 1, Integer::sum));
        final List<Map.Entry<fURI, Integer>> keys = places.entrySet().stream()
                .flatMap(key -> runAxis || copy || !BranchInst.home(key.getKey()) ? Stream.of(key)
                        : Stream.generate(() -> Map.entry(key.getKey(), 1)).limit(key.getValue()))
                .toList();
        final int lanes = runAxis ? (int) Math.min(keys.size(), range.count()) : keys.size();
        if (runAxis && 2 > lanes)
            return null;
        final List<long[]> exponents = !runAxis ? List.of()
                : range.point() ? splitEvenly(range.count(), lanes) : splitByCost(range.min(), range.max(), lanes);
        // A LITERAL SEED IS DEALT BY THE BRANCH'S OWN RULE (BranchInst#deal), so a planned part is the part the branch
        // would have dealt; under the copy kind and the run axis every lane holds the whole seed.
        final List<List<Obj>> parts = literal && !runAxis
                ? BranchInst.deal(head.arg(0), keys.stream().mapToLong(key -> copy ? 0L : key.getValue()).toArray())
                : List.of();
        // emit [prefix·]branch(cut(<place>, [start(part)·]run·fold), …)·combiner — BRANCH IS THE ONE DISTRIBUTED
        // PRIMITIVE: each lane runs the floatable run (its share of a span range projected onto it) and a local partial
        // reduce where its cut carries it, so one partial per lane travels home, where the master recombines them
        // through the combiner. A LANE IS A MOVE (Law.nomadic, stage 2), emitted as the cut it compiles to, so a
        // profiled lane's profile() stays the place's: a remote lane's budget rides on its cut's target and its weight
        // is the cut's point (Marko, 2026-11-24: "move() point coefficient is branch weight"); a home lane gets no move,
        // so no weight, and its budget goes on its head
        final int stop = 0 > cut ? runEnd : cut;
        final List<Obj> routes = new ArrayList<>();
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
                lane.addAll(null == lifted ? List.of(unpointed(raw(insts.get(cut)))) : lifted.lane().stream().map(Shard::stated).toList());
            if (absorbs)
                lane.add(raw(insts.get(cut + 1)));
            if (profiling)
                lane.add(instB(PROFILE_INST_TID, lst()));
            final fURI place = keys.get(r).getKey();
            final int weight = keys.get(r).getValue();
            final cInt share = runAxis || copy || 1 == weight ? null : cInt.of(weight);
            final boolean home = BranchInst.home(place);
            stampHop(lane, home ? budget : null);
            routes.add(home ? code(lane).tryToInst() : instB(null == share ? CUT_INST_TID : CUT_INST_TID.d(share),
                    lst(uri(place.q(HOP, budget)), code(lane).tryToInst())));
        }
        // lanes seeded with a literal take nothing, so the branch is the initial form; otherwise the branch takes the bag
        // whole (a {*} dom over the carrier flowing into the run) and deals it by its keys -- or, under the run axis,
        // copies it to every lane, whose shares of the exponent are disjoint.
        // THE BRANCH STATES ITS TYPES IN GENERICS, NEVER #{*} (Marko, 2026-11-20), so compilation carries real types
        // past it. Its dom is the carrier flowing into the run, or A{*} when the probe could not type it (#). Its rng
        // is what each lane's last instruction answers, once per lane: the branch's own B{*} is bound by nothing and
        // resolves to #{*}, which the combiner after it cannot bind (kmerge's lst[{*}A{*}]{*} fell back to # => #, a
        // lst{1} read as {*}). Profiling lanes answer reports, so they state none.
        final fURI carrier = chain.carriers().get(runIndex).typeId();
        final fURI dom = literal ? fURI.Singleton.NOOBJ
                : null == carrier || fURI.Singleton.ALL.basePath().equals(carrier.basePath()) ? InstSet.A.maybeSome()
                : carrier.maybeSome();
        // an absorbed select keeps a prefix of the cut's lst, so the cut's contract states what those lanes answer too;
        // a lst select declares no element type, and its own contract would state a bare lst the combiner cannot bind
        final Inst answers = profiling ? null : chain.contracts().get(0 <= cut ? cut : runEnd - 1);
        final fURI lane = null == answers || !answers.tid().hasRng() ? null : answers.rng().typeId();
        final fURI branch = literal && runAxis ? BRANCH_INST_TID.dom(dom)
                : BRANCH_INST_TID.dom(dom).q(SPLIT, runAxis || copy ? COPY : PART);
        final List<Inst> master = new ArrayList<>();
        if (!literal)
            master.addAll(insts.subList(from, runIndex));
        master.add(instB(null == lane || fURI.Singleton.ALL.basePath().equals(lane.basePath()) ? branch : branch.rng(lane.maybeSome()), lst(routes)));
        if (profiling) {
            // THE MASTER'S RUN IS TIMED AROUND ITS BRANCH, whichever axis the run factored (a seed's members, a span's
            // unrolls, a point's copies): the lanes answer reports, not partials, so no combiner runs, and a bare
            // profile_collate() marks where the master's run ends. The profile_analysis rule, whose ?zindex runs it
            // after this one, captures everything before the marker (the master's whole run, the branch's gather
            // wait included), times it as profile() times a local run and nests the lanes' reports under it
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
     * A POINT ON THE CUT IS A MULTIPLICITY OF ITS ANSWER: {@code r{d}} answers {@code {d}} copies of what {@code r} does,
     * so {@code (a+b)·r{d} = {d}((a·r + b·r)·C)}. No lane carries it ({@link #unpointed}; {@link #raw} keeps it, and only
     * a remote lane's text once dropped it, so a home lane folded with it twice), and home's combiner does. A
     * self-combining fold re-applies the cut as written, point and all; a derived or a lifted combiner is another
     * instruction, so the point moves onto its last: {@code count{2}()} recombines through {@code sum{2}()}, and
     * {@code mean{2}()} through {@code mean{2}()} over the lanes' summaries.
     */
    private static List<Inst> pointed(final List<Inst> combiner, final Inst cut) {
        final cInt point = cut.tid().d();
        if (combiner.isEmpty() || null == point || point.isOne())
            return combiner;
        final List<Inst> out = new ArrayList<>(combiner);
        out.set(out.size() - 1, instB(out.getLast().tid().d(point), out.getLast().args()));
        return out;
    }

    /**
     * Whether a keyed fold's spec holds a value with a point ({@code group([k=>count{2}()])}), at any depth of nested
     * groups.
     */
    private static boolean pointedValues(final Inst cut) {
        return GROUP_INST_TID.equals(cut.tid().basePath()) && 1 == cut.args().count() && cut.arg(0).isRec()
                && cut.arg(0).asRec().elements().anyMatch(kv -> {
            final Obj value = kv.secondDirect();
            final List<Inst> folds = value.isObjInst() ? List.of(value.asInst()) : value.isCode() ? value.asCode().insts() : List.of();
            return folds.stream().anyMatch(fold -> (null != fold.tid().d() && !fold.tid().d().isOne())
                    || !fold.tid().b().isOne() || pointedValues(fold));
        });
    }

    /**
     * THE CUT A LANE RUNS: its point dropped, as the point multiplies the whole answer and home's combiner carries it
     * ({@link #pointed}).
     */
    private static Inst unpointed(final Inst cut) {
        return null == cut.tid().d() || cut.tid().d().isOne() ? cut : instB(cut.tid().d(cInt.ONE()), cut.args());
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
            // a fold answers one obj, at most: so does a contract of any op whose declared form is reducer (InstBuilder
            // records one form per op, its first contract's), sup and inf among them, whose range admits none (A{?})
            if (contract.isReducing() || Inst.Form.reducer == Inst.Form.of(contract))
                return true;
            if (!handsOnOne(contract, chain.insts().get(i)))
                return false;
        }
        return false;
    }

    /**
     * A RUN THAT TURNS ONE OBJ INTO MANY STARTS AFTER THE TURN. With one obj flowing in and no index set to factor, the
     * run's flatmappers ({@code merge} unrolling the lst a fold answered) stay home and expand the obj into a bag, which
     * the rest of the run deals by member: {@code order(neg()).merge().plus(1).sum()} merges at home and ships
     * {@code plus(1).sum()}. What hands on one obj for one before the turn stays home with it, as one obj has nothing
     * to deal: {@code ==[{4}mult(-1)]>-.order(neg())} maps the lst and merges it at home and ships the sort (Marko,
     * 2026-11-20, two top-ks in one job). With no turn, the run starts where it stood.
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
        int turn = runIndex;
        while (turn < cut && Inst.Form.flatmapper != Inst.Form.of(chain.typed().get(turn)).subring()
                && null != chain.contracts().get(turn) && handsOnOne(chain.contracts().get(turn), chain.insts().get(turn)))
            turn++;
        int start = turn;
        while (start < cut && Inst.Form.flatmapper == Inst.Form.of(chain.typed().get(start)).subring())
            start++;
        return turn == start ? runIndex : start;
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
                         Placement route, Env env) {
    }

    /**
     * THE THREE AXES a run is planned under (§2–§4) — how far and how wide, what counts as one share, and where.
     */
    private record Axes(Hop hop, Partition split, PlaceSet place) {
    }

    /**
     * THE INSTRUCTION AS WRITTEN. A lane is compiled where it runs, so the program it carries holds nothing this
     * machine bound: not the resolved signature, not a bound function and not a position vid. What stays is the op
     * address, the caller's own query, the coefficient and the arguments.
     */
    static Inst raw(final Inst inst) {
        final Poly args = inst.args().isRec()
                ? rec(inst.args().asRec().elements().map(kv -> rel(kv.first(), rawArg(kv.second()))))
                : lst(inst.args().elements().map(Shard::rawArg).toList());
        return instB(inst.tid().removeQ(DOM).removeQ(RNG).removeQ(ARGS), args);
    }

    /**
     * THE INSTRUCTION AS ITS EQUATION STATES IT: a lift read off a declared derivation (J) keeps the domain the equation
     * states, which is part of what it means and nothing this machine bound: {@code split?<=int{*}} gathers the lane's
     * whole part, where a bare {@code split} would take one member at a time. All else is {@link #raw}. A keyed lift's
     * group holds the lift inside its rec, which raw leaves as it is, so {@code split?<=int{*}} rides inside each key's
     * value untouched.
     */
    static Inst stated(final Inst inst) {
        final Inst raw = raw(inst);
        return inst.tid().hasDom() ? instB(raw.tid().dom(inst.tid().dom()), raw.args()) : raw;
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
     * peer must not carry it (under either spelling, or the peer would re-plan with the root's height). A REMOTE LANE'S
     * BUDGET RIDES ON ITS CUT'S TARGET (Marko, 2026-11-24), which states it on the head of what it ships: its chain is
     * only stripped, a {@code null} budget.
     */
    static void stampHop(final List<Inst> chain, final Obj budget) {
        if (chain.isEmpty())
            return;
        for (int i = 0; i < chain.size(); i++) {
            final Inst inst = chain.get(i);
            final fURI tid = inst.tid().removeQ(HOP).removeQ(Placement.HOP_ALIAS);
            chain.set(i, inst.tid(0 == i && null != budget ? tid.q(HOP, budget) : tid));
        }
    }

    /**
     * THE HOP AXIS of a run, resolved in chain order: the earliest {@code ?hop} (or its deprecated alias) among its
     * instructions wins, else the axis it {@code inherits}. A value the projection cannot read is REFUSED — {@code hop}
     * is the tree's height and a silent zero is the point topology.
     */
    static Hop hopAxis(final List<Inst> insts, final Hop inherited) {
        for (final Inst inst : insts) {
            if (inst.tid().hasQ(HOP))
                return hopAxis(inst.tid(), HOP);
            if (inst.tid().hasQ(Placement.HOP_ALIAS))
                return hopAxis(inst.tid(), Placement.HOP_ALIAS);
        }
        return inherited;
    }

    @SuppressWarnings("unchecked")
    static Hop hopAxis(final fURI tid, final String key) {
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
     * A SELECT THAT KEEPS A PREFIX: one positional pattern whose every slot is a bare {@code id()} -- {@code ==[{k}_]},
     * the span laid along the select's bracket as k identity slots. A positional select keeps only the positions its
     * pattern covers, so this one keeps the first k and leaves each as it was: a projection. A slot that maps
     * ({@code ==[{2}+1]}) is none -- pushed into the lanes it would map twice -- a point ({@code id{2}()}) copies, and a
     * keyed pattern ({@code ==[?<2=>_]}) keeps every position, as a hole where its key fails. An empty pattern, a zero
     * span laid ({@code ==[{0}_]}), stays home for now: shipping it is sound at k = 0, but empty partials through the
     * branch and the merge are untested.
     */
    private static boolean keepsPrefix(final Inst inst) {
        if (!SELECT_INST_TID.equals(inst.tid().basePath()) || 1 != inst.args().count() || !inst.arg(0).isLst())
            return false;
        final List<Obj> slots = inst.arg(0).asLst().<Obj>elements().toList();
        return !slots.isEmpty() && slots.stream().allMatch(slot -> {
            final Obj step = slot.isCode() && 1 == slot.asCode().insts().size() ? slot.asCode().insts().getFirst() : slot;
            // a laid slot states its point as one: no point and a point of one are the same single copy
            return step.isObjInst() && ID_INST_TID.equals(step.tid().basePath())
                    && step.asInst().args().isEmpty() && (null == step.tid().d() || step.tid().d().isOne());
        });
    }

    /**
     * THE CUT'S COMBINER IS CAUSAL: the first n of its output depend only on the first n of each input, so a prefix kept
     * after it may be kept in every lane first. Read where {@link Law.Helper#combiner} reads the combiner's own laws: a
     * self-combining fold at its declared n-tid, a derived one ({@code order ↦ kmerge}) at the cut's range. A chain
     * combiner, a keyed fold's pointwise merge, is no one fold and declares nothing.
     */
    private static boolean causalCombiner(final Inst keyed, final Law[] laws) {
        final Call combined = Law.Helper.combiner(keyed, laws);
        if (null == combined)
            return false;
        if (keyed == combined)
            return Law.Helper.declares(keyed.tid(), Law.causal);
        final List<Inst> folds = combined.insts();
        return 1 == folds.size() && Law.Helper.declares(folds.getFirst(), keyed.rng(), Law.causal);
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
     * clamps each place's demand (and a literal seed dealt in parts, the window), and the arity caps the window.
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
