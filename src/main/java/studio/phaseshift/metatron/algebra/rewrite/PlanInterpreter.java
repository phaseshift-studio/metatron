/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.algebra.rewrite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.algebra.rewrite.PlaceResolver.ResolvedPlace;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.Derivation;
import studio.phaseshift.metatron.isa.m.mach.type.thread.VirtualThread;
import studio.phaseshift.metatron.isa.m.math.cat.LawTable;
import studio.phaseshift.metatron.isa.m.math.cat.Places;
import studio.phaseshift.metatron.isa.m.math.cat.Signatures;
import studio.phaseshift.metatron.isa.m.math.cat.TheoryHelper;
import studio.phaseshift.metatron.isa.m.parser.mFluent;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.cat.catLiveness.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * PlanInterpreter — lowers a {@link Plan} to code: the engine the plan value is handed to — a categorical
 * match over {@link InstPattern}'s form/law alphabet in place of a hand-rolled form scan.
 *
 * <p>The one-cut distribution milestone (Thm 19, {@code (a+b)·c = (a·c+b·c)·c}): guard on the live peer
 * roster, match the chain shape {@code [seed, {*}floatable, blocked cut, {*}tail]}, locate the cut, then
 * emit the {@code start([slave...]).select([to(peer)...]).merge().<combiner>.<tail>} layout the processor runs.
 * The cut's law obligations
 * ({@code monoidic} + {@code commutative}) ride on the {@code blocked} slot's COMBINER — the fold the partials
 * recombine through, computed by {@link LawTable#combiner} — so {@link PlanBuilder#create()}'s shape-derived
 * check and the match-time check are the same Thm 19 hypothesis, read off the fold that is re-applied.
 *
 * <p>The chain instructions arrive pre-resolution (bare op addresses), so each body inst is first retyped
 * with its declared (dom/rng-qualified) tid via {@link LawTable#declared} — the step that makes
 * {@code Form.of} and {@code LawTable.declares} legible before resolution. The seed is matched by op address.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class PlanInterpreter {

    private static final Logger log = LoggerFactory.getLogger(PlanInterpreter.class);

    private PlanInterpreter() {
    }

    /**
     * Lower the plan onto {@code code} — the single application point. Every plan is {@code [name, cut, route]},
     * and what its cut names picks the transform ({@link #lowerCut}): a form cut distributes through
     * {@link #shard}, a theory role or a process law selects a local fold. A plan whose cut names nothing
     * known is the identity.
     */
    public static Code lower(final Plan plan, final Code code) {
        return apply(plan, code, Env.snapshot());
    }

    /**
     * The one body (§12.1): {@code apply(plan, env) = locate → resolve → emit → retype → re-lift}, over one immutable
     * {@link Env} snapshot — nothing inside reads the machine. {@link #lower} is the cut-plan convenience for the
     * rewriter; {@link #bind} is the cut-less sibling (Phase 4).
     */
    public static Code apply(final Plan plan, final Code code, final Env env) {
        if (null == plan.cut())
            return relocate(plan, code, env);
        return lowerCut(plan, code, env);
    }

    /**
     * The cut-less operator (§12.1): a distribution rule applied to code. Requires a plan with no cut; a cut plan is
     * {@link #lower}'s, so it is declined — the code passes through unchanged, the same identity a no-match plan is.
     * The relocation itself is emitted by {@link #relocate}; whether the code is placeable (has an origin) is the
     * lift's concern, not the operator's, so a machine that answers nowhere can still ship to a peer it dials.
     */
    public static Code bind(final Plan plan, final Code code) {
        if (null != plan.cut())
            return code;
        return apply(plan, code, Env.snapshot());
    }

    /**
     * The cut-less distribution rule (§12.1): the site is the decision's span, not a cut. A {@code move} decision
     * (a {@code chain}/{@code star} shape with a {@code target}) ships the whole code to that place — a hop is a
     * re-compilation in a new environment (§6.3), so the receiver re-plans the program on compile. The target's
     * scheme must resolve a dialer in the ISA ({@link Signatures#resolves}); other cut-less shapes, and a target
     * nothing can dial, are the identity: the code stands where it stood.
     */
    private static Code relocate(final Plan plan, final Code code, final Env env) {
        final Placement decision = plan.where().stream().findFirst().orElse(null);
        // an admissible move: the decision must name a target whose scheme resolves a dialer in the ISA — no dial to
        // decide otherwise. Declined as the identity, like every other cut-less shape without a target.
        if (null == decision || null == decision.target() || !Signatures.resolves(decision.target().scheme()))
            return code;
        // a move hop: append the to(<target>) terminal instruction, which ships the WHOLE code as a program — the
        // transport wraps start(code).to(<>) and the receiver re-plans it (§6.3) — never the code's value (the quine
        // rule: the payload is a program, not a callable result).
        final List<Inst> insts = new ArrayList<>(code.insts());
        insts.add(to_(decision.target()).tryToInst().asInst());
        return code(insts);
    }

    /**
     * The cut/route plan — the structural form: match the cut, isolate the span, route to the target. A
     * {@code none} target deletes the isolated span (the unit law's transform); a form-classified cut
     * distributes through {@link #shard}; a role/law-classified cut selects the local fold — the ring's own
     * operation, read off the theory rather than a transform name.
     */
    private static Code lowerCut(final Plan plan, final Code code, final Env env) {
        final Cut cut = plan.cut();
        // the unit law: a none target deletes the identity (and unwraps the single addend)
        for (final Placement placement : plan.where())
            if (Placement.Shape.NONE == placement.shape())
                return ID.equals(cut.role()) ? unitRemoval(code) : code;
        // the distribution plan: a form-classified cut (reducer/join/gather)
        if (null != cut.formOrNull())
            return shard(plan, code, env);
        // the role-classified folds: add (sum like terms), zero (unwrap the single addend), inverse (cancel),
        // map (unwrap the stream lift)
        final String role = cut.role();
        if (null != role)
            return ADD.equals(role) ? branchCollapse(code)
                    : ZERO.equals(role) ? branchInline(code)
                    : INVERSE.equals(role) ? inverseCancellation(code)
                    : MAP_INST_TID.name().equals(role) ? mapUnwrap(code)
                    : DERIVATION.equals(role) ? derivationContraction(code)
                    : code;
        // the law-classified folds: involution (drop), idempotent/monoidic (keep), poset (compose),
        // absorbing (swallow the prefix)
        if (cut.declares(Law.involution))
            return involutionRemoval(code);
        if (cut.declares(Law.idempotent))
            return idempotentRemoval(code);
        if (cut.declares(Law.monoidic))
            return monoidicRemoval(code);
        if (cut.declares(Law.poset))
            return posetRemoval(code);
        if (cut.declares(Law.absorbing))
            return absorbingRemoval(code);
        return code;
    }

    /**
     * The one-cut distribution (Thm 19): factor the seed bulk over the roster, replicate the floatable run, and
     * recombine the partials behind a barrier through the cut's COMBINER — computed, never declared
     * ({@link LawTable#combiner}). A self-combining fold re-applies itself ({@code sum}, {@code prod}); a fold defined
     * by its derivation recombines through the fold that defines it ({@code count} through {@code sum},
     * {@code order(s)} through {@code kmerge(s)}). The identity unless the chain matches the plan's one-cut shape and
     * the machine has live peers to fan out over.
     */
    private static Code shard(final Plan plan, final Code code, final Env env) {
        // A LONE MACHINE DOES NOT FAN OUT -- unless the decision states its own place set, whose HOME lane is the
        // lanes case (?place=[{4}local]: a peerless machine shards). Read before the hop so the budget's own refusal
        // stays exactly where it was on a machine with nothing dialed.
        final boolean stated = code.insts().stream().anyMatch(inst -> inst.tid().hasQ(PLACE));
        if (env.peers().isEmpty() && !stated)
            return code;
        // THE HOP AXIS: how many more levels of the tree may be planned from here, and how many children one level may
        // fan out to. A height of zero is a leaf -- the fold runs where it stands -- and an arity of zero is the same
        // point: with no branch and no level there is nothing to distribute, so the decision is the identity, the
        // original chain byte for byte (§9). The root's axis comes from the chain or from the plan's tree route.
        final Hop axis = hopAxis(plan, code);
        if (axis.isPoint() || axis.arity().isZero())
            return code;
        // THE ARITY CAPS THE FAN-OUT: at most `w` lanes leave one node (`{2}3` is a binary tree of height three), the
        // data still clamps it (`k = min(shares, seed)`), and a node with fewer live targets takes what there is. The
        // cap is the band's upper bound, so `{2,4}` fills what is live up to four (§11.2's v1).
        final long cap = null == axis.arity().max() ? Integer.MAX_VALUE : axis.arity().max();
        final int fanout = (int) Math.min(cap, Integer.MAX_VALUE);
        // THE PARTITION AXIS: what counts as one place's share. Read here, beside the hop, so an unreadable value is
        // refused on the same machine the hop's is (and with nothing dialed the decline is unchanged). STREAM and COPY
        // are the two kinds this chunk lowers; the other four DECLINE TO THE IDENTITY -- the unchanged chain, never a
        // different-but-plausible layout -- and each names the chunk that will lower it.
        final Partition split = split(plan, code);
        if (Partition.Kind.STREAM != split.kind() && Partition.Kind.COPY != split.kind()) {
            // block: contiguous runs need an ORDERED seed and everything law_shard sees is a bag (a stream has no
            // order), so lowering it would reorder the answer -- C7b adds the order requirement and its seeds.
            // key (G), owner (B), index (P): the bucketing read, the addressed seed and the generated range space are
            // their own chunks -- C7b -- and faking them here would compute an answer the listing does not say.
            log.debug("?split={} does not lower in this chunk: the fold stays where it stands ({})", split, code);
            return code;
        }

        final List<Inst> insts = code.insts();
        // the shape is [initial, {*}elementwise, reducer] — {*} = ZERO or more, so the minimum is
        // seed + reducer (2 insts); a bare reduction over the bulk is shardable.
        if (2 > insts.size() || !START_INST_TID.equals(insts.getFirst().tid().basePath()))
            return code;

        final Cut cut = plan.cut();
        final Law[] laws = cut.laws().toArray(Law[]::new);
        // each body instruction keyed at the carrier flowing into it, not the seed's
        final List<Type> carriers = carriers(code);
        final List<Inst> typed = IntStream.range(1, insts.size()).mapToObj(i -> retype(insts.get(i), carriers.get(i))).toList();

        // the one-cut shape: [ {*}floatable, blocked (recombined by a fold declaring the cut's laws), {*}tail ]
        final List<List<Inst>> captures = InstPattern.match(
                List.of(
                        InstPattern.law(Law.floatable).star(),
                        InstPattern.law(Law.blocked).combinedBy(laws),
                        InstPattern.any().star()),
                typed);
        if (null == captures)
            return code;

        final int cutIndex = 1 + captures.get(0).size();
        final Inst reducer = insts.get(cutIndex);
        // a self-combining fold re-applies the chain's own instruction (pre-resolution, exactly as the slaves carry
        // it); a fold defined by derivation recombines through the fold its definition names; a keyed fold through
        // the chain its pointwise combiner computes (the rec sum, then a per-key fold)
        final Inst keyed = typed.get(cutIndex - 1);
        final Call combined = LawTable.combiner(keyed, laws);
        // NO COMBINER, NO FAN-OUT: the combiner is COMPUTED, and the computation may answer null (a fold whose law
        // row does not resolve, a keyed spec whose per-key fold has none). Null is a DECLINE, not an error: the fold
        // stays where it stands, the unchanged chain, and the reason is logged rather than thrown.
        if (null == combined) {
            log.debug("no combiner for the cut {}: the fold stays where it stands ({})", keyed, code);
            return code;
        }
        final List<Inst> combiner = keyed == combined ? List.of(reducer) : combined.insts();
        // COPY'S PREMISE: replicating the seed onto every lane double-counts a fold that is not idempotent, so
        // `?split=copy` is legal only when the cut's COMBINER declares `idempotent`. Unsound is the IDENTITY -- the
        // chain that already stands here -- never a fan-out that quietly answers twice the sum.
        if (Partition.Kind.COPY == split.kind() && !idempotentCombiner(combined, carriers.get(cutIndex))) {
            log.debug("?split=copy needs an idempotent combiner: {} is not one, so the fold stays where it stands ({})",
                    combined, code);
            return code;
        }

        final List<Obj> seed = insts.getFirst().arg(0).stream().toList();
        if (2 > seed.size())
            return code;

        // the placement that ships — the plan's distributing route — states what its targets must serve
        final Placement route = plan.where().stream().filter(placement -> placement.shape().distributes()).findFirst().orElse(null);
        if (null == route)
            return code;
        // THE LANES: the place set — the route's own, overridden by a ?place on the chain — resolved against the
        // snapshot, one lane per share, the HOME place's shares first. The lane order is the emitter's (the design's
        // S order for [local,peer], and the same home-first rule for [peer,local], where the design's does not hold):
        // the resolver keeps the order the designators were written in, which is not the layout's. The arity caps the
        // window: the first `w` lanes in exactly that order, so the selection is the resolution order and nothing is
        // rotated or sorted per node.
        final List<ResolvedPlace> shares = shares(resolve(env, route, place(plan, code)), seed.size(), fanout);
        // NOTHING SHIPS: no place answered, or the single home lane — one home share is the code that already stands
        // here. SEVERAL home shares are NOT this: `?place=[{4}local]` is the lanes case, a peerless machine sharding.
        final boolean localOnly = 1 >= shares.size() && shares.stream().allMatch(ResolvedPlace::isLocal);
        if (localOnly)
            return code;
        final int k = shares.size();
        // THE FORM IS RESTATED, NEVER THE NUMBER: the slave's budget is the same arity with the height decremented
        // ({2}3 ⇒ {2}2 ⇒ {2}1, a band {2,4}3 ⇒ {2,4}2, the chain [n,1] ⇒ [n-1,1]), rendered through the one reader's
        // own writer so the pair form and the unstated arity keep their spellings. The reaching of the point is the one
        // place the arity drops out: below a height of zero nothing fans out, so the leaf states the bare point the
        // schedule has always emitted. The budget rides the slave's FOLD (the reducer), not its seed: the depth is the
        // fold's, and a peer re-plans from its own recombine.
        final Obj budget = 1 == axis.height() ? jnt(0) : Hop.of(axis.height() - 1, axis.arity()).toObj();

        // emit (a·r + b·r)·combiner(r) — the master gathers partials and recombines them at home; each slave
        // carries one factor of the bulk, the floatable run replicated, and a local partial reduce.
        //
        // A SLAVE SHIPS UNQUOTED. Nothing on this machine evaluates it: a multi-instruction chain is Code, never
        // an Inst, so neither the lst nor select's at() auto-resolves it; to(<peer>) takes it as its lhs, raw; and
        // the target's own to -- bound into the route below -- ships it as start(slave).to(<>) without computing it
        // here. The peer's evaluation of that start(...) is the one that runs the slave,
        // so what comes back is its value, 12@<tag>. A quote would now be peeled on the far side instead, and
        // whether the code it uncovers still runs is left to the peer's processor -- so there is none.
        //
        // THE PEER RE-PLANS ITS PART. Applying a code compiles it (Machine.evaluate), so this rule runs again on the
        // slave where it lands: with budget left and dialed peers of its own, the peer is an interior node of the tree
        // -- it shards its part and recombines through the same combiner -- and at a height of zero it is a leaf. Every
        // level partitions its part exactly, so a cycle in the dial graph only bounces work, never duplicates or drops
        // it, and the budget bounds the bounce.
        final List<Obj> summands = new ArrayList<>();
        final List<Obj> routes = new ArrayList<>();
        final List<Obj> gathers = new ArrayList<>();
        // PROFILING IS AN END CAP, NOT A TAIL. When the chain's instruction after the cut is profile(), the
        // fold's partials are not what comes back — each slave answers a profiling::T — so there is nothing
        // to recombine: the master's merge() is followed by COLLATION, which indexes the reports by the
        // machine each was made on. `merge().sum()` would try to add two reports together.
        final boolean profiling = insts.size() > cutIndex + 1
                && PROFILE_INST_TID.equals(insts.get(cutIndex + 1).tid().basePath());
        final mFluent<?> master = profiling
                ? merge_().addInst(profileCollate())
                : merge_().addInsts(withoutHop(combiner)).addInsts(withoutHop(insts.subList(cutIndex + 1, insts.size())));
        for (int s = 0; s < k; s++) {
            final List<Obj> part = new ArrayList<>();
            if (Partition.Kind.COPY == split.kind())
                part.addAll(seed);                    // one part, and it is the whole seed: the replicas are the lanes
            else
                for (int j = s; j < seed.size(); j += k)  // stride: the default stream partition, any exact division
                    part.add(seed.get(j));
            // the seed states the PART, never a budget: the depth rides the fold below
            final mFluent<?> slave = start_(objs(part));
            slave.addInsts(withoutHop(insts.subList(1, cutIndex)));
            // THE FOLD STATES THE RESTATED FORM: the copied reducer loses the level above's budget (whichever
            // spelling), then carries this level's — the same arity, one height down.
            final Inst fold = withoutHop(reducer);
            slave.addInst(fold.tid(fold.tid().q(HOP, budget)));
            // THE SLAVE'S END CAP: the peer profiles its OWN part. The marker ships, not a minted analysis
            // inst, because the analysis captures the code by closure on the machine that runs it — the peer
            // compiles the slave, so its profile_analysis sees the peer's chain and answers a profiling::T.
            if (profiling)
                slave.addInst(instB(PROFILE_INST_TID, lst()));
            summands.add(slave);
            // THE ROUTE IS BOUND HERE, at plan time (Marko, 2026-10-24: the compiled code has the bindings): resolved
            // against the slave it will carry, so the emitted select holds the native eval with the target peer's own
            // body (Network.Helper.atPeer) — what runs where is in the listing, not decided when select applies it
            // (select keeps its projections raw, so nothing later would bind it). A route that does not resolve stays
            // the bare eval(<target>), resolved at run time as before.
            // THE HOME LANE'S ROUTE IS THE ADDRESSLESS ONE: the same eval with its target elided, so the share is
            // applied where the code already stands -- the lane at distance zero, not a second mechanism. It is left
            // UNRESOLVED: there is no target body to bind (and binding would fill the declaration's arg types into a
            // route that must stay bare), and the bare eval() resolves where it runs, exactly as an unresolved peer
            // route does.
            final ResolvedPlace share = shares.get(s);
            final boolean home = share.isLocal();
            final Inst ship = home ? instB(EVAL_INST_TID, lst()) : eval_(share.authority()).tryToInst().asInst();
            final Inst bound = home ? ship : ship.resolve(slave);
            final Inst boundRoute = null != bound && bound.hasf() ? bound : ship;
            // THE PARALLEL FAN-OUT (Thm 5, entry D): wrap each ship in a virtual thread, so the first select submits
            // every round trip at once — none blocks the next, and each waits on its own thread. The gather select
            // below (>>rhs) reads each thread's future off its rhs slot; the deref's FutureObj passes back through
            // the processor's own FutureObj.resolveFuture, so the await boundary is the gather, not the fan-out.
            routes.add(VirtualThread.virtual(boundRoute));
            gathers.add(rshift_(uri(RHS)).tryToInst().asInst());
        }
        final Code emitted = start_(lst(summands)).select_(lst(routes)).select_(lst(gathers)).addInsts(master.insts());
        return emitted;
    }

    /**
     * THE HOP AXIS of a fold, resolved in chain order: a {@code ?hop} on any instruction of the chain — the seed
     * when it is written by the author ({@code start?hop=3(…)}), the reducer the {@code {…}} sugar states it on
     * ({@code sum?hop=3()}), and the FOLD of every slave this plan emits — else the plan's own tree route.
     * {@code ?hops} is the deprecated pre-singular spelling, read for one cycle: the scan walks the chain once and
     * answers the first instruction that states either spelling, so the earliest statement wins and the new one is
     * preferred at the same position.
     *
     * <p>The whole axis is read, not just the height: {@link Hop#of(Obj)} is the one reader of the value — a bare int
     * is today's spelling (the height stated, the arity the receiver's local out-degree), the leading coefficient the
     * arity ({@code {2}4}), and the pair {@code [height, arity]} the two arities the coefficient cannot render
     * ({@code [n,1]} the chain, {@code [n,0]} a point per level). A value the projection cannot read as a hop is
     * REFUSED — never answered {@code 0}, because {@code hop} is the tree's height and a silent zero is the point
     * topology.
     */
    public static Hop hopAxis(final Plan plan, final Code code) {
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

    /**
     * The hop budget of a fold — the height of {@link #hopAxis}, kept as the {@code int} the projection reads it as
     * ({@code catTypes} asks whether a fold is planned at all). The emitter reads the whole axis.
     */
    public static int hop(final Plan plan, final Code code) {
        return hopAxis(plan, code).height();
    }

    /**
     * THE PARTITION AXIS of a fold, resolved in chain order exactly as the hop axis is: a {@code ?split} on any
     * instruction of the chain — the seed, the reducer, and the fold of every slave this plan emits — else the plan's
     * own distributing route ({@code route.split()}, the stream partition for the plans in the tree today).
     *
     * <p><b>Which kinds lower in this chunk, and which decline.</b> The gate is the premise, and a partition that
     * cannot check its own premise is the identity (the unchanged chain), never a different-but-plausible layout:
     * <ul>
     *   <li>{@code stream} — stride division, the default: TODAY'S layout, byte for byte. A stated part count is not
     *       read here yet, because a share is already a lane and a second part count would be a second knob (the
     *       replicating {@code stream{2}} is C7b's);</li>
     *   <li>{@code copy} — the whole seed on every lane ({@code stream{1}}): lowered, and LEGAL only when the cut's
     *       combiner declares {@code idempotent}, since replicated data double-counts a fold that is not. Unsound is
     *       the identity;</li>
     *   <li>{@code block} — DECLINES: contiguous runs recombined in order need an ORDERED seed, and everything
     *       {@code law_shard} sees is a bag (a stream has no order), so lowering it would reorder the answer — C7b
     *       adds the order requirement and the seeds that meet it;</li>
     *   <li>{@code key} — DECLINES: the bucketing read (the chain's {@code group} spec) and a keyed route are C7b's;</li>
     *   <li>{@code owner} — DECLINES: one part per address owner needs an addressed seed ({@code from}, {@code *<uri>})
     *       and the owner partition is C7b's (menu row B);</li>
     *   <li>{@code index} — DECLINES: ranges of a generated index space ship no data at all (menu row P), C7b's.</li>
     * </ul>
     * An unreadable value is REFUSED loudly — a decision the author stated and the emitter dropped would compute
     * elsewhere than the listing says.
     */
    private static Partition split(final Plan plan, final Code code) {
        for (final Inst inst : code.insts())
            if (inst.tid().hasQ(SPLIT))
                return split(inst.tid(), SPLIT);
        return plan.where().stream()
                .filter(placement -> placement.shape().distributes())
                .map(Placement::split)
                .findFirst().orElse(Partition.stream());
    }

    /**
     * One stated partition: the WHOLE value the q key holds, through {@link Partition#of(Obj)} — a counted uri whose
     * name is the kind and whose coefficient the part count ({@code {2}block}). A value the projection cannot read as a
     * partition is refused naming the key and the text, never defaulted to the stream.
     */
    private static Partition split(final fURI tid, final String key) {
        final Obj value = strictQValue(tid, key);
        try {
            return Partition.of(value);
        } catch (final MTronException e) {
            throw MTronException.of("?%s=%s cannot be read as a partition: %s", key, tid.q(key), e.getMessage());
        }
    }

    /**
     * Whether the cut's COMBINER — the fold the partials recombine through, {@link LawTable#combiner computed} and
     * never declared — declares {@code idempotent}. Its TERMINAL instruction is the fold that recombines (a derivation
     * lowers to {@code … · fold}), and the law row is read the two ways the table already serves one: the
     * carrier-resolved read every other law gate in this class uses, and — when the carrier resolves no row — the
     * instruction's own {@code ?lawq} declaration ({@link LawTable#declares(fURI, Law)}, the route a fold declared
     * beside its body takes). Both are the table's own reads; the second is not a guess across types, it is the one
     * address the combiner states.
     */
    private static boolean idempotentCombiner(final Call combined, final Type carrier) {
        final List<Inst> folds = combined.insts();
        return !folds.isEmpty() && (declares(folds.getLast(), carrier, Law.idempotent)
                || LawTable.declares(folds.getLast().tid(), Law.idempotent));
    }

    /**
     * THE PLACE SET of a fold, resolved in chain order exactly as the hop budget is: a {@code ?place} on any
     * instruction of the chain — the seed when it is written ({@code start?place=[local,peer](…)}, the reducer under
     * the {@code {…}} sugar, and on every slave this plan emits) — else the plan's own distributing place set
     * ({@code route.place()}, the dialed set for the plans in the tree today). The scan walks the chain once and the
     * earliest statement wins, so a {@code ?place} anywhere in the fold overrides the plan, and a set the projection
     * cannot read as a place set is REFUSED, never silently ignored (a decision the author stated and the emitter
     * dropped would compute elsewhere than the listing says).
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

    /**
     * One stated place set: the whole value the q key holds — a uri is one designator, a lst a set, a rec the weighted
     * form ({@link PlaceSet#of(Obj)} is the one reader) — through the same strict projection {@link #strictQValue} uses, so
     * a value the grammar cannot parse, a {@code str} where a value was required, and an empty value are refused the
     * same way, and a set whose designators name nothing placeable is refused naming the key and the text.
     */
    private static PlaceSet place(final fURI tid, final String key) {
        final Obj value = strictQValue(tid, key);
        try {
            return PlaceSet.of(value);
        } catch (final MTronException e) {
            throw MTronException.of("?%s=%s cannot be read as a place set: %s", key, tid.q(key), e.getMessage());
        }
    }

    /**
     * One stated hop axis: the WHOLE value the q key holds, through {@link Hop#of(Obj)} — an int (the counted spelling
     * is the LEADING coefficient, {@code ?hop={2}4}, which the object grammar reads as the int 4 carrying {@code c = 2})
     * or the pair {@code [height, arity]} for the arities the coefficient cannot render. A trailing coefficient on a
     * number ({@code ?hop=4{2}}) is a parse error in mtron (a number takes none), so {@link #strictQValue} refuses it
     * rather than answering {@code 0}: {@code hop} is the tree's height and a silent zero is the point topology.
     */
    private static Hop hopAxis(final fURI tid, final String key) {
        final Obj value = strictQValue(tid, key);
        try {
            return Hop.of(value);
        } catch (final MTronException e) {
            throw MTronException.of("?%s=%s cannot be read as a hop: %s", key, tid.q(key), e.getMessage());
        }
    }

    /**
     * THE STRICT Q READ: the object a q key states, or a refusal naming the key and the text. {@code qValue}'s
     * {@code Class} argument is a DISPATCH HINT, not a type claim — its {@code Lst} and {@code Rec} branches are the
     * same {@code ObjmtronSerializer.parse} — so the answer is the parsed object whatever its shape ({@code int},
     * {@code uri}, {@code lst}, {@code fail}) and the cast only widens the generic return so a call site does not
     * narrow it back to a {@code Lst}. A {@code fail} (a spec the grammar cannot parse), a {@code str} (an unparsed
     * value) and an empty value are refused HERE, so a reader only checks the shape it needs.
     * <p>
     * THE WHOLE VALUE is what every reader takes, single-valued keys included: {@code [local,peer]} is one value
     * holding two designators, not two values, and a hop's pair form {@code [height, arity]} is the same one value.
     */
    @SuppressWarnings("unchecked")
    private static Obj strictQValue(final fURI tid, final String key) {
        // Lst selects the projection's parse branch; the erased return is the parsed obj, whatever its shape — the
        // cast widens the call's generic result to Obj so the call site does not narrow it back to a Lst
        final Obj value = tid.qValue(key, (Class<Obj>) (Class<?>) Lst.class);
        final List<Obj> members = value.stream().toList();
        if (members.stream().anyMatch(member -> member.isFail() || member.isStr()))
            throw MTronException.of("?%s=%s cannot be read", key, tid.q(key));
        if (members.isEmpty())
            throw MTronException.of("?%s=%s states no value", key, tid.q(key));
        return value;
    }

    /**
     * The instruction without its hop budget, whatever its spelling: a budget is the plan's to restate at each level
     * — on the slave's FOLD, the reducer that recombines the level below — so no copied instruction may carry the
     * level above's, and a slave handed to a peer must not carry it under the deprecated spelling either.
     */
    private static Inst withoutHop(final Inst inst) {
        final Inst unhopped = inst.tid().hasQ(HOP) ? inst.tid(inst.tid().removeQ(HOP)) : inst;
        return unhopped.tid().hasQ(Placement.HOP_ALIAS) ? unhopped.tid(unhopped.tid().removeQ(Placement.HOP_ALIAS)) : unhopped;
    }

    private static List<Inst> withoutHop(final List<Inst> insts) {
        return insts.stream().map(PlanInterpreter::withoutHop).toList();
    }

    /**
     * Retype a bare chain instruction with its declared (dom/rng-qualified) tid, so {@code Form.of} and
     * {@code LawTable.declares} read its coefficient shape and laws before resolution — and a keyed fold's spec folds
     * with it ({@link LawTable#keyed}). An undeclared op stays bare and thereby matches no floatable/blocked slot —
     * fail closed, as the hand-rolled scan did.
     */
    private static Inst retype(final Inst inst, final Type carrier) {
        return LawTable.keyed(inst, carrier);
    }

    /**
     * THE LANES: the places the route's {@link PlaceSet place axis} — {@code ?place}, when the chain states one —
     * resolves to against this application's {@link Env snapshot}, one place per share. Empty when nothing answered,
     * which is the emitter's decline.
     * <p>
     * THE RESOLUTION IS {@link PlaceResolver}'S (C4): the designators, the exclusions, the specificity, the wildcard's
     * reach and the liveness gate all live there, one place, so the emitter and the sweeps answer the same question
     * the same way. What stays here is the emitter's own clauses — the LANE ORDER (the home place's shares first) and
     * the DATA CLAMP (no place takes more lanes than the seed has parts), which the resolver does not hold the seed
     * for. A resolution that cannot answer (a designator naming nothing placeable) is the decline, not a failure of
     * the fold: the code then stands where it stood, exactly as it did when there were no targets.
     * <p>
     * A DIALED PEER, NOT EVERY PEER. {@code Network.accept} co-mounts every inbound socket as a peer too —
     * keyed by the dialer's ephemeral source port, with no {@code host} — and the far end of that socket is a
     * CLIENT. Whether a client evaluates a request the server sends it is invisible from here, and the default
     * {@code ws_client} only prints one, so a shard shipped down it never comes back: its round trip waits out
     * the deadline and the fail poisons the reduction (measured across two hosts: once one machine dialed the
     * other, the dialed machine's {@code sum()} answered {@code unable to convert mfail to int::T}). The loopback
     * case of the same twin is declined at accept; a cross-host dialer cannot be, so the planner chooses.
     * {@code host} is the field {@code peer::T} reserves for a peer the current machine can ship to — where to
     * dial it from here — so a target is a peer that carries one.
     * <p>
     * A TARGET IS ONLINE AND SERVES THE ROUTE ({@link Places#serves}). AN OFFLINE PEER IS NO TARGET: a dialed peer
     * whose link is down stays in the map (it is being redialed), but a shard shipped down it answers {@code not
     * connected} and that fail poisons the reduction — so an outage SHRINKS the fan-out instead, and the fold
     * completes on the peers that answer. A PEER THAT CANNOT {@code eval} IS NO TARGET EITHER: the route ships each
     * slave as {@code eval(<target>)}, and {@code eval} is exactly "apply this program and answer its value". The
     * seed bulk is factored over whatever is left, so nothing is dropped and nothing is computed twice.
     */
    private static List<ResolvedPlace> resolve(final Env env, final Placement route, final PlaceSet places) {
        try {
            return PlaceResolver.resolve(places, env, route.inst());
        } catch (final Exception e) {
            return List.of();
        }
    }

    /**
     * ONE LANE PER SHARE, in the layout's own order: the home place's shares FIRST (the emitter canonicalises the
     * lane order — the design's S order for {@code [local,peer]}, and the same home-first rule for {@code [peer,local]},
     * where the design's does not hold), then the peers in resolution order; a place with demand {@code n} takes
     * {@code n} consecutive lanes, and each place asks for at most as many lanes as the seed has parts.
     *
     * <p>THE ARITY'S WINDOW: the first {@code fanout} lanes of exactly that order, and no more — the hop axis's
     * {@code w} is how many children one level may fan out to, and the window is what makes the choice DETERMINISTIC
     * (the resolution order, never a per-node sort or rotation): {@code ?hop={2}3} over three peers takes the first
     * two of them at every node.
     */
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

    /**
     * The lanes one place asks for: the demand's upper bound (a band asks its most, and an open demand — the wildcard
     * read off a place — asks as much as the data allows), clamped by the seed. The absolute share is the design's:
     * {@code [{2}ginger,{1}booger]} wants two thirds of the parts AND two of the three lanes, one number.
     */
    private static int asked(final ResolvedPlace place, final int cap) {
        final cInt demand = place.demand();
        final Long asked = null == demand.max() ? demand.min() : demand.max();
        return null == asked ? cap : (int) Math.min(asked, cap);
    }

    /**
     * The unit law (local, no distribution): an operation applied to its own theory identity drops out, and a
     * bare identity operation is the identity morphism — remove every such instruction. The theory roles are
     * read from {@code object::T.law}, so the one rule covers every declared monoid/group/ring/…
     */
    private static Code unitRemoval(final Code code) {
        final List<Inst> insts = code.insts();
        if (insts.isEmpty())
            return code;
        final Map<Type, Rec> laws = new HashMap<>();
        // a lone instruction is a selector (payload), not a morphism to collapse — suppress the
        // bare-identity test for it. isUnit still fires on a sole unit such as [plus(0)]; if that
        // removal ever proves wrong, gate isUnit on !sole too (keep every single-instruction chain).
        final boolean sole = 1 == insts.size();
        // the identity morphism is read off the code ring once per pass, and only when a bare op could be it
        final fURI identity = insts.stream().anyMatch(i -> i.args().isEmpty())
                ? TheoryHelper.compositionIdentity(T(CODE_TID)) : null;
        final List<Inst> kept = insts.stream()
                .filter(i -> !isUnit(i, laws) && (!isIdentity(i, identity) || sole))
                .toList();
        return kept.size() == insts.size() ? code : code(kept);
    }

    /**
     * The unit test: an operation applied to its own theory identity — {@code op(id)} — for every theory
     * instance the operand's type models. The law blocks are memoized per rewrite pass by the caller's map,
     * since a type's theories are shared by every instruction over it.
     */
    private static boolean isUnit(final Inst inst, final Map<Type, Rec> laws) {
        if (inst.args().isEmpty())
            return false;
        final List<Type> operands = List.of(inst.dom(), inst.arg(0).type());
        for (final Type operand : operands)
            for (final Map.Entry<Obj, Obj> instance : laws.computeIfAbsent(operand, TheoryHelper::instances).jvm().entrySet()) {
                if (!instance.getValue().isRec())
                    continue;
                final Rec block = instance.getValue().asRec();
                for (final String[] roles : TheoryHelper.UNIT_ROLES) {
                    final Inst unit = TheoryHelper.unit(block, roles[0], roles[1]);
                    // THE IDENTITY IS A VALUE, SO THE OPERAND IS COMPARED. `unit.test(inst)` is a CONTRACT-level test
                    // (the pattern and the instruction share their dom/rng), so it accepts an instruction whose
                    // operand is a CODE as readily as the literal identity -- plus(0).test(plus(map(3).neg())) is
                    // true -- and that composition is exactly what derivation_contraction has to see and contract
                    // (plus·neg ↦ minus). Without the operand comparison the unit law deletes it first and a derived
                    // instruction's definition silently stops folding.
                    if (!unit.isNoObj() && unit.test(inst) && unit.args().equals(inst.args()))
                        return true;
                }
            }
        return false;
    }

    /**
     * The bare-identity test: an instruction with no operands that <em>is</em> the identity of chain composition
     * ({@link TheoryHelper#compositionIdentity}) — the code ring's {@code id}. Its occurrence in a chain is the
     * identity morphism, hence dead weight. An identity of another operation is not: the additive group's
     * {@code id} is end, composition's zero, and taking it for the identity turned {@code 5.end()} into {@code 5}.
     */
    private static boolean isIdentity(final Inst inst, final fURI identity) {
        if (null == identity || !inst.args().isEmpty())
            return false;
        if (!cInt.ONE().equals(inst.c()))
            return false;   // id{n} is a multiplicity, not a no-op
        return inst.tid().basePath().equals(identity);
    }

    /**
     * The involution law (local): an adjacent pair of the SAME self-inverse operation is the identity —
     * {@code f·f ↦ ε} for any operation declaring {@code involution} (or the carrier's declared inverse).
     */
    private static Code involutionRemoval(final Code code) {
        final List<Inst> insts = code.insts();
        if (2 > insts.size())
            return code;
        final List<Type> carriers = carriers(code);
        final List<Inst> collapsed = collapsePairs(insts, i -> same(insts.get(i), insts.get(i + 1)) && isInvolution(insts.get(i), carriers.get(i)));
        return collapsed.size() == insts.size() ? code : code(collapsed);
    }

    /**
     * The involution test: the operation's own declared {@code involution} law, or the carrier's declared
     * additive-group inverse.
     */
    private static boolean isInvolution(final Inst inst, final Type carrier) {
        if (declares(inst, carrier, Law.involution))
            return true;
        final Inst inv = TheoryHelper.invInst(carrier);
        return !inv.isNoObj() && inv.test(inst);
    }

    /**
     * Collapse adjacent pairs the predicate accepts to nothing — the equation {@code f·f ↦ ε}. The predicate is
     * handed the pair's first position, so it can read the carrier there.
     */
    private static List<Inst> collapsePairs(final List<Inst> insts, final IntPredicate pairAt) {
        final List<Inst> result = new ArrayList<>();
        int i = 0;
        while (i < insts.size()) {
            if (i + 1 < insts.size() && pairAt.test(i))
                i += 2; // f·f => ε — drop the pair
            else
                result.add(insts.get(i++));
        }
        return result;
    }

    /**
     * The idempotent law (local): an adjacent pair of the SAME idempotent operation is that operation —
     * {@code f·f ↦ f} for any operation declaring {@code idempotent}.
     */
    private static Code idempotentRemoval(final Code code) {
        final List<Inst> insts = code.insts();
        if (2 > insts.size())
            return code;
        final List<Type> carriers = carriers(code);
        final List<Inst> collapsed = collapseKeep(insts, i -> same(insts.get(i), insts.get(i + 1)) && declares(insts.get(i), carriers.get(i), Law.idempotent));
        return collapsed.size() == insts.size() ? code : code(collapsed);
    }

    /**
     * Collapse adjacent pairs the predicate accepts to a single instance — the equation {@code f·f ↦ f} shared
     * by the idempotent law and a monoidic fold (a reduction of one element is that element). The predicate is
     * handed the pair's first position, so it can read the carrier there.
     */
    private static List<Inst> collapseKeep(final List<Inst> insts, final IntPredicate pairAt) {
        final List<Inst> result = new ArrayList<>();
        int i = 0;
        while (i < insts.size()) {
            if (i + 1 < insts.size() && pairAt.test(i)) {
                result.add(insts.get(i)); // f·f => f — keep one
                i += 2;
            } else
                result.add(insts.get(i++));
        }
        return result;
    }

    /**
     * The monoidic law on a fold (local): an adjacent identical reducer/join collapses — {@code f·f ↦ f} for a
     * fold whose declared process laws include {@code monoidic}.
     */
    private static Code monoidicRemoval(final Code code) {
        final List<Inst> insts = code.insts();
        if (2 > insts.size())
            return code;
        final List<Type> carriers = carriers(code);
        final List<Inst> collapsed = collapseKeep(insts, i -> same(insts.get(i), insts.get(i + 1))
                && insts.get(i).c().equals(insts.get(i + 1).c()) && isFold(insts.get(i), carriers.get(i)));
        return collapsed.size() == insts.size() ? code : code(collapsed);
    }

    /**
     * The inverse law (local): an adjacent declared inverse pair with equal operands cancels to the identity —
     * {@code f·f⁻¹ ↦ ε} for the declared opposing edge.
     */
    private static Code inverseCancellation(final Code code) {
        final List<Inst> insts = code.insts();
        if (2 > insts.size())
            return code;
        final List<Type> carriers = carriers(code);
        final List<Inst> collapsed = collapsePairs(insts, i -> inversePair(insts.get(i), insts.get(i + 1), carriers.get(i)));
        return collapsed.size() == insts.size() ? code : code(collapsed);
    }

    /**
     * A fold: the declared coefficient shape gathers many to one ({@code reducer} or {@code join}) and the
     * operation is declared {@code monoidic} — the shape a repeated fold is idempotent on.
     */
    private static boolean isFold(final Inst inst, final Type carrier) {
        final LawTable.Declared declared = LawTable.declared(inst.tid().basePath(), carrier);
        if (null == declared)
            return false;
        final Inst.Form form = Inst.Form.of(inst.tid(declared.tid()));
        return (Inst.Form.reducer == form || Inst.Form.join == form) && declares(inst, carrier, Law.monoidic);
    }

    /**
     * Adjacent inverse pair with equal operands — {@code x.a().b() ↦ x} — read off {@code a}'s declared
     * {@code inverse}, in that direction only. A declared inverse is one-sided: {@code inverse(f) = g} claims
     * {@code x.f().g() = x} (f a section, g its retraction), never {@code x.g().f() = x} — on int,
     * {@code x.mult(2).div(2)} is {@code x} but {@code 5.div(2).mult(2)} is 4.
     */
    private static boolean inversePair(final Inst a, final Inst b, final Type carrier) {
        return isInverseOf(a, b, carrier);
    }

    /**
     * Whether {@code b} is the declared inverse of {@code a} with equal operands. Endpoints are unavailable
     * before resolution, so the operand type that selected the declaration is the type guard. A literal zero
     * operand never cancels: {@code mult(0)} has no inverse, and {@code plus(0)} is the unit law's anyway.
     */
    private static boolean isInverseOf(final Inst a, final Inst b, final Type carrier) {
        final LawTable.Declared declared = LawTable.declared(a.tid().basePath(), carrier);
        return null != declared && null != declared.entry().inverse()
                && b.tid().basePath().equals(declared.entry().inverse().basePath())
                && a.args().equals(b.args())
                && a.args().elements().noneMatch(PlanInterpreter::isZeroLiteral);
    }

    private static boolean isZeroLiteral(final Obj arg) {
        return (arg.isInt() && 0L == arg.asInt().intValue()) || (arg.isReal() && 0.0d == arg.asReal().realValue());
    }

    /**
     * The poset law (local): adjacent FILTERS on comparisons of the same ordered type compose to the filter on the
     * stricter bound — {@code is(gt(a))·is(gt(b)) ↦ is(gt(max(a,b)))}, since {@code x > a ∧ x > b ⇔ x > max(a,b)}.
     * Only filters compose: a bare comparison answers a bool, so {@code x.gt(a).gt(b)} compares that bool with
     * {@code b}, and reading it as {@code x.gt(max(a,b))} changed what it means.
     */
    private static Code posetRemoval(final Code code) {
        final List<Inst> insts = code.insts();
        if (2 > insts.size())
            return code;
        final List<Inst> out = new ArrayList<>(insts.size());
        boolean changed = false;
        // a filter hands on what it passes, so both comparisons test the first filter's carrier
        final List<Type> carriers = carriers(code);
        for (int i = 0; i < insts.size(); i++) {
            final Inst composed = (i + 1 < insts.size()) ? posetCompose(insts.get(i), insts.get(i + 1), carriers.get(i)) : null;
            if (null != composed) {
                out.add(composed);
                changed = true;
                i++;
            } else
                out.add(insts.get(i));
        }
        return changed ? code(out) : code;
    }

    /**
     * Compose two adjacent filters — the same filter ({@code is} or {@code filter}), each on the same comparison,
     * declared {@code poset} at their carrier — to one filter on the stricter bound, the poset law
     * {@code x > a ∧ x > b ⇔ x > max(a, b)}. Null when the pair does not compose.
     */
    private static Inst posetCompose(final Inst a, final Inst b, final Type carrier) {
        if (!sameOp(a, b))
            return null;
        final Inst first = comparisonOf(a);
        final Inst second = comparisonOf(b);
        if (null == first || null == second || !sameOp(first, second) || !declares(first, carrier, Law.poset)
                || 1 != first.args().count() || 1 != second.args().count())
            return null;
        final Obj bound = stricter(first, second);
        if (null == bound)
            return null;
        // the composed comparison goes back in the shape the filter held it: an instruction, or a one-instruction code
        final Inst comparison = first.args(lst(bound));
        return a.args(lst(a.arg(0).isInst() ? comparison : code(List.of(comparison))));
    }

    /**
     * The stricter of two bounds of the same comparison — the larger for {@code gt}/{@code gte}, the smaller for
     * {@code lt}/{@code lte} — or null when the bounds are not two ints, two reals, or two strs.
     */
    private static Obj stricter(final Inst first, final Inst second) {
        final String op = first.tid().basePath().name();
        final boolean upper = op.equals(GT_INST_TID.name()) || op.equals(GTE_INST_TID.name());
        final boolean lower = op.equals(LT_INST_TID.name()) || op.equals(LTE_INST_TID.name());
        if (!upper && !lower)
            return null;
        final Obj x = first.arg(0);
        final Obj y = second.arg(0);
        final Obj bound;
        if (x.isInt() && y.isInt())
            bound = jnt(upper ? Math.max(x.asInt().intValue(), y.asInt().intValue()) : Math.min(x.asInt().intValue(), y.asInt().intValue()));
        else if (x.isReal() && y.isReal())
            bound = real(upper ? Math.max(x.asReal().realValue(), y.asReal().realValue()) : Math.min(x.asReal().realValue(), y.asReal().realValue()));
        else if (x.isStr() && y.isStr()) {
            final int comparison = x.asStr().strValue().compareTo(y.asStr().strValue());
            bound = (upper ? 0 <= comparison : 0 >= comparison) ? x : y;
        } else
            return null;
        return bound;
    }

    /**
     * The stream lift (local): {@code map} is transparent on the value it wraps, so {@code map(f) ↦ f}, which
     * also collapses {@code map(map(f)) ↦ map(f)}.
     */
    private static Code mapUnwrap(final Code code) {
        final List<Inst> insts = code.insts();
        boolean changed = false;
        final List<Inst> out = new ArrayList<>(insts.size());
        final List<Type> carriers = carriers(code);
        for (int i = 0; i < insts.size(); i++) {
            final Obj wrapped = mapArgument(insts.get(i), carriers.get(i));
            if (null != wrapped) {
                out.add(wrapped.asInst());
                changed = true;
            } else
                out.add(insts.get(i));
        }
        return changed ? code(out) : code;
    }

    /**
     * The absorbing law (local): an absorbing op is a constant of the chain, so the run before it that cannot change
     * what it answers is dead code — {@code …·g·zero ↦ …·zero}. Each absorbing op swallows its predecessors back to
     * the first one that is not {@link #absorbable}, and never the seed.
     */
    private static Code absorbingRemoval(final Code code) {
        final List<Inst> insts = code.insts();
        final int seed = (!insts.isEmpty() && START_INST_TID.equals(insts.get(0).tid().basePath())) ? 1 : 0;
        final List<Type> carriers = carriers(code);
        final List<Integer> kept = new ArrayList<>(insts.size());   // positions, so each reads its own carrier
        for (int i = 0; i < insts.size(); i++) {
            final Inst inst = insts.get(i);
            final LawTable.Declared absorber = i < seed || !declares(inst, carriers.get(i), Law.absorbing)
                    ? null : LawTable.declared(inst.tid().basePath(), carriers.get(i));
            if (null != absorber)
                while (kept.size() > seed && absorbable(insts.get(kept.getLast()), absorber.tid(), carriers.get(kept.getLast())))
                    kept.removeLast();
            kept.add(i);
        }
        return kept.size() == insts.size() ? code : code(kept.stream().map(insts::get).toList());
    }

    /**
     * Whether an absorbing op may swallow the instruction before it: removing {@code prior} must not change what the
     * absorber answers. Read off declarations alone, so an undeclared op (no legible form) is never swallowed:
     * <ul>
     *   <li><b>pure</b> — {@code prior} declares {@link Law#pure}. An instruction that does not is effectful, so
     *       an undeclared op (side, print, to) stops the run.</li>
     *   <li><b>multiplicity</b> — a terminal absorber (rng {@code {0}}) answers noobj whatever reaches it, so any
     *       pure op goes. Any other absorber answers a constant PER OBJ, so {@code prior} must hand it one obj per
     *       obj: a coefficient-one {@code mapper}, or a {@code filter} when the absorber is non-strict — its
     *       {@code {?}} dom fires on the noobj the filter leaves, while a strict absorber never sees it
     *       ({@code {1,2,3}.is(gt(1)).zero()} is {@code {2}0}, and {@code {1,2,3}.sum().zero()} is {@code 0}).</li>
     *   <li><b>carrier</b> — the absorber's law was read for the chain's carrier, so {@code prior} must map that
     *       carrier to itself: {@code 5.as(str::T).zero()} is str's zero, {@code ''}, not int's.</li>
     * </ul>
     */
    private static boolean absorbable(final Inst prior, final fURI absorber, final Type carrier) {
        final LawTable.Declared declared = LawTable.declared(prior.tid().basePath(), carrier);
        if (null == declared || !declares(prior, carrier, Law.pure))
            return false;
        if (absorber.rng().c().isZero())
            return true;
        if (!cInt.ONE().equals(prior.c()) || !isEndo(declared.tid(), carrier))
            return false;
        final Inst.Form form = Inst.Form.of(prior.tid(declared.tid()));
        return Inst.Form.mapper == form || (Inst.Form.filter == form && !isStrict(absorber));
    }

    /**
     * Whether the declared op maps the chain's carrier to itself — dom and rng both the carrier's base type, or both
     * the one generic ({@code A ⇒ A}). Both ends, because the declaration was found by EITHER end matching the
     * carrier: {@code as?int<=str} answers an int carrier too.
     */
    private static boolean isEndo(final fURI declared, final Type carrier) {
        final fURI dom = declared.dom().basePath();
        final fURI rng = declared.rng().basePath();
        if (!dom.equals(rng))
            return false;
        return dom.isGeneric() || (null != carrier.vid() && dom.equals(carrier.vid().basePath()));
    }

    /**
     * Whether the declared op is strict — its dom excludes noobj ({@code {1}}, {@code {+}}), so the noobj a filter
     * leaves never fires it; a {@code {?}} or {@code {*}} dom fires on it.
     */
    private static boolean isStrict(final fURI declared) {
        final Long min = declared.dom().c().min();
        return null != min && 0L < min;
    }

    /**
     * Derivation contraction (local): fold a derived instruction's primitive composition back to the derived
     * operation — {@code plus·neg ↦ minus}, {@code mult·inv ↦ div}, from the type's declared {@code derivation}.
     */
    private static Code derivationContraction(final Code code) {
        final List<Derivation> derivations = LawTable.derivations();
        if (derivations.isEmpty())
            return code;
        List<Inst> out = code.insts();
        for (final Derivation d : derivations)
            out = contract(out, d);
        return code(out);
    }

    /**
     * Contract each occurrence of a derivation's primitive composition back to its derived instruction —
     * {@code mult·inv ↦ div}, {@code map(1)·sum ↦ count}. The composition is the rhs after its seed
     * ({@code start(A)}), matched as a contiguous WINDOW of the chain: a one-instruction rhs nests its composition in
     * that instruction's args ({@code plus(map(B).neg())}), a longer one spans several, and matching only the rhs's
     * last instruction would contract every {@code sum} to {@code count}.
     */
    private static List<Inst> contract(final List<Inst> insts, final Derivation d) {
        final List<Inst> rhs = afterSeed(d.rhs());
        final List<Inst> lhs = afterSeed(d.lhs());
        if (rhs.isEmpty() || lhs.isEmpty())
            return insts;
        final List<Inst> out = new ArrayList<>();
        int i = 0;
        while (i < insts.size()) {
            final Map<fURI, Obj> bindings = new HashMap<>();
            if (i + rhs.size() <= insts.size() && matchesWindow(rhs, insts.subList(i, i + rhs.size()), bindings)) {
                for (final Inst derived : lhs)
                    out.add((Inst) Inst.Helper.substituteGenerics(derived, bindings));
                i += rhs.size();
            } else
                out.add(insts.get(i++));
        }
        return out;
    }

    /**
     * A derivation side without its seed — the instructions a chain matches, {@code start(A)} excluded.
     */
    private static List<Inst> afterSeed(final Code side) {
        final List<Inst> insts = side.insts();
        return !insts.isEmpty() && START_INST_TID.equals(insts.getFirst().tid().basePath()) ? insts.subList(1, insts.size()) : insts;
    }

    /**
     * Whether a chain window matches a derivation side instruction-for-instruction, the generics binding across the
     * whole window.
     */
    private static boolean matchesWindow(final List<Inst> pattern, final List<Inst> window, final Map<fURI, Obj> bindings) {
        for (int j = 0; j < pattern.size(); j++)
            if (!Derivation.matchGenerics(pattern.get(j), window.get(j), bindings))
                return false;
        return true;
    }

    /**
     * The additive like-term law (local): equal members of any occurrence of the algebra's additive op collapse
     * to the member that first appeared, carrying the summed multiplicities — {@code S(x,x,…,x) ↦ S(x{n})}. The
     * witness is read off object[code].law at rewrite time (for the code ring: branch).
     */
    private static Code branchCollapse(final Code code) {
        return repeatedTerms(TheoryHelper.additiveWitness(T(CODE_TID)), T(CODE_TID), code);
    }

    /**
     * The one-term sum law (local): an occurrence of the algebra's additive op with a single member is that
     * member — {@code S(x) ↦ x}, the frame's own coefficient folded into it.
     */
    private static Code branchInline(final Code code) {
        return oneTerm(TheoryHelper.additiveWitness(T(CODE_TID)), T(CODE_TID), code);
    }

    /**
     * Whether the bare chain instruction is the witness op — a base-path match. The witness here is the
     * n-ary {@code branch} (the code ring's {@code Σ}), which is registered with a generic dom/rng, so the
     * space read's {@code admitted()} (which excludes generic rngs) would drop it; the bare address is unique
     * to the code ring, so the base-path check is exact. A concrete-rng op (like {@code plus}) would instead
     * resolve by space read for type inheritance ({@code nat} → {@code int}).
     */
    private static boolean matches(final fURI witness, final Inst inst, final Type carrier) {
        return witness.basePath().equals(inst.tid().basePath());
    }

    /**
     * The repeated-term normalizer, law-indexed: every occurrence of {@code op} — the algebra's additive op —
     * with two or more like terms consolidates them into the member that first appeared, carrying the summed
     * multiplicities as the coefficient. The body is inst-agnostic: for the code ring {@code op} is
     * {@code branch}, for the int ring the same body would point at {@code plus}. A null witness — the algebra
     * declares no additive op — is the identity.
     */
    private static Code repeatedTerms(final fURI op, final Type carrier, final Code code) {
        if (null == op)
            return code;
        boolean changed = false;
        final List<Inst> out = new ArrayList<>(code.insts().size());
        for (final Inst inst : code.insts()) {
            if (!matches(op, inst, carrier) || 2 > inst.args().count()) {
                out.add(inst);
                continue;
            }
            final List<Obj> reps = new ArrayList<>();
            final List<cInt> sums = new ArrayList<>();
            for (final Obj arg : inst.args().elements().toList()) {
                int slot = -1;
                for (int j = 0; j < reps.size(); j++) {
                    if (sameMember(reps.get(j), arg)) {
                        slot = j;
                        break;
                    }
                }
                if (-1 == slot) {
                    reps.add(arg);
                    sums.add(multiplicity(arg));
                } else
                    sums.set(slot, sums.get(slot).plus(multiplicity(arg)));
            }
            if (reps.size() < inst.args().count()) {
                changed = true;
                final List<Obj> merged = new ArrayList<>(reps.size());
                for (int i = 0; i < reps.size(); i++)
                    merged.add(cInt.ONE().equals(sums.get(i)) ? reps.get(i) : coeff(reps.get(i), sums.get(i)));
                out.add(inst.args(lst(merged)));
            } else
                out.add(inst);
        }
        return changed ? code(out) : code;
    }

    /**
     * The one-term normalizer, law-indexed: an occurrence of {@code op} — the algebra's additive op — whose
     * members have come to one is that member, added to nothing, so the frame unwraps and the frame's own
     * coefficient folds into the member ({@code branch{c}(b) |-> b{c}}); a call member splices in by its
     * insts, a non-call member lifts to the map that emits it — stream-transparent either way. A null witness
     * is the identity, as in {@link #repeatedTerms}.
     */
    private static Code oneTerm(final fURI op, final Type carrier, final Code code) {
        if (null == op)
            return code;
        boolean changed = false;
        final List<Inst> out = new ArrayList<>(code.insts().size());
        for (final Inst inst : code.insts()) {
            if (matches(op, inst, carrier) && 1 == inst.args().count()) {
                final Obj member = absorb(inst.arg(0), inst.c());
                changed = true;
                if (member.isCall())
                    out.addAll(member.asCall().insts());
                else
                    out.add((Inst) map_(member).tryToInst());
            } else
                out.add(inst);
        }
        return changed ? code(out) : code;
    }

    /**
     * The frame's own coefficient folded into its single member — an occurrence of coefficient c applied to a
     * single member multiplies that member's multiplicity by c, so the frame unwraps and the member comes out
     * carrying it. A code member anchors the coefficient at its head inst; an inst or a non-call member takes
     * the multiplication itself.
     */
    private static Obj absorb(final Obj member, final cInt c) {
        if (cInt.ONE().equals(c))
            return member;
        if (member.isCode()) {
            final List<Inst> insts = new ArrayList<>(member.asCode().insts());
            insts.set(0, (Inst) insts.get(0).c(o -> o.mult(c)));
            return code(insts);
        }
        return member.c(o -> o.mult(c));
    }

    /**
     * Two additive members being the same element — {@code same(Inst, Inst)} with the refined endpoints compared
     * too, minus the positional vid. A code member is the same when its instruction list is pairwise the same;
     * a non-call member is the same when it is coefficient-less equal (the coefficient is the multiplicity the
     * law sums, so it is deliberately OUT of the test).
     */
    private static boolean sameMember(final Obj a, final Obj b) {
        if (a.isInst() && b.isInst()) {
            final Inst ai = a.asInst();
            final Inst bi = b.asInst();
            return same(ai, bi)
                    && ai.dom().equals(bi.dom())
                    && ai.rng().equals(bi.rng())
                    && Objects.equals(ai.f(), bi.f());
        }
        if (a.isCode() && b.isCode()) {
            final List<Inst> ai = a.asCode().insts();
            final List<Inst> bi = b.asCode().insts();
            if (ai.size() != bi.size())
                return false;
            for (int i = 0; i < ai.size(); i++)
                if (!sameMember(ai.get(i), bi.get(i)))
                    return false;
            return true;
        }
        if (a instanceof Inst || b instanceof Inst || a instanceof Code || b instanceof Code)
            return false;
        return a.clessEquals(b);
    }

    /**
     * The multiplicity an additive member contributes — the coefficient the law sums. For an inst member it is
     * the inst's own coefficient; for a code member it rides the chain's first inst.
     */
    private static cInt multiplicity(final Obj member) {
        if (member.isCode() && !member.asCode().insts().isEmpty())
            return member.asCode().insts().getFirst().c();
        return member.c();
    }

    /**
     * The member carrying its summed coefficient, at the anchor {@link #multiplicity(Obj)} reads it from — the
     * inst's tid, or the head of the chain for a code.
     */
    private static Obj coeff(final Obj member, final cInt c) {
        if (member.isCode() && !member.asCode().insts().isEmpty()) {
            final List<Inst> insts = member.asCode().insts();
            final List<Inst> headed = new ArrayList<>(insts);
            headed.set(0, insts.getFirst().c(c));
            return code(headed);
        }
        return member.c(c);
    }
}
