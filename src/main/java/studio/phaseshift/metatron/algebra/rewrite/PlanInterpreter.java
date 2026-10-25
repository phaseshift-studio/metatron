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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.math.cat.Derivation;
import studio.phaseshift.metatron.isa.m.math.cat.Law;
import studio.phaseshift.metatron.isa.m.math.cat.LawTable;
import studio.phaseshift.metatron.isa.m.math.cat.Places;
import studio.phaseshift.metatron.isa.m.math.cat.Signatures;
import studio.phaseshift.metatron.isa.m.math.cat.TheoryHelper;
import studio.phaseshift.metatron.isa.m.parser.mFluent;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import static studio.phaseshift.metatron.Tokens.ADD;
import static studio.phaseshift.metatron.Tokens.AUTHORITY;
import static studio.phaseshift.metatron.Tokens.CODE_TID;
import static studio.phaseshift.metatron.Tokens.DERIVATION;
import static studio.phaseshift.metatron.Tokens.HOPS;
import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.ID;
import static studio.phaseshift.metatron.Tokens.INVERSE;
import static studio.phaseshift.metatron.Tokens.ZERO;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.cat.catLiveness.carriers;
import static studio.phaseshift.metatron.isa.m.math.cat.catLiveness.comparisonOf;
import static studio.phaseshift.metatron.isa.m.math.cat.catLiveness.declares;
import static studio.phaseshift.metatron.isa.m.math.cat.catLiveness.mapArgument;
import static studio.phaseshift.metatron.isa.m.math.cat.catLiveness.same;
import static studio.phaseshift.metatron.isa.m.math.cat.catLiveness.sameOp;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
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
        if (env.peers().isEmpty())
            return code;
        // THE HOP BUDGET: how many more levels of the tree may be planned from here. hops=0 is a leaf -- the fold runs
        // where it stands; the root's budget comes from the chain or the plan's tree route (see hops)
        final int hops = hops(plan, code);
        if (1 > hops)
            return code;

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
        final List<Inst> combiner = keyed == combined ? List.of(reducer) : combined.insts();

        final List<Obj> seed = insts.getFirst().arg(0).stream().toList();
        if (2 > seed.size())
            return code;

        // the placement that ships — the plan's distributing route — states what its targets must serve
        final Placement route = plan.where().stream().filter(placement -> placement.shape().distributes()).findFirst().orElse(null);
        final List<Obj> targets = null == route ? List.of() : shardTargets(env, route);
        if (targets.isEmpty())
            return code;
        final int k = Math.min(targets.size(), seed.size());

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
        // THE PEER RE-PLANS ITS PART. Applying a code compiles it (Machine.applyCode), so this rule runs again on the
        // slave where it lands: with budget left (start?hops=n) and dialed peers of its own, the peer is an interior
        // node of the tree -- it shards its part and recombines through the same combiner -- and at hops=0 it is a
        // leaf. Every level partitions its part exactly, so a cycle in the dial graph only bounces work, never
        // duplicates or drops it, and the budget bounds the bounce.
        final List<Obj> summands = new ArrayList<>();
        final List<Obj> routes = new ArrayList<>();
        final mFluent<?> master = merge_().addInsts(withoutHops(combiner)).addInsts(withoutHops(insts.subList(cutIndex + 1, insts.size())));
        for (int s = 0; s < k; s++) {
            final List<Obj> part = new ArrayList<>();
            for (int j = s; j < seed.size(); j += k)
                part.add(seed.get(j));
            final mFluent<?> slave = start_(objs(part)).q(HOPS, hops - 1);
            slave.addInsts(withoutHops(insts.subList(1, cutIndex)));
            slave.addInst(withoutHops(reducer));
            summands.add(slave);
            // THE ROUTE IS BOUND HERE, at plan time (Marko, 2026-10-24: the compiled code has the bindings): resolved
            // against the slave it will carry, so the emitted select holds the native eval with the target peer's own
            // body (Network.Helper.atPeer) — what runs where is in the listing, not decided when select applies it
            // (select keeps its projections raw, so nothing later would bind it). A route that does not resolve stays
            // the bare eval(<target>), resolved at run time as before.
            final Inst ship = eval_(targets.get(s)).tryToInst().asInst();
            final Inst bound = ship.resolve(slave);
            routes.add(null != bound && bound.hasf() ? bound : ship);
        }
        final Code emitted = start_(lst(summands)).select_(lst(routes)).addInsts(master.insts());
        return emitted;
    }

    /**
     * The hop budget of a fold, nearest statement first: a {@code ?hops} on any instruction of the chain — the seed
     * when it is written ({@code start?hops=3(…)}), the reducer under the {@code {…}} sugar ({@code sum?hops=3()}),
     * and on every slave this plan emits — else the plan's own tree route. A budget that is not a number plans nothing.
     */
    public static int hops(final Plan plan, final Code code) {
        for (final Inst inst : code.insts())
            if (inst.tid().hasQ(HOPS)) {
                try {
                    return Integer.parseInt(String.valueOf(inst.tid().qMap().get(HOPS)).trim());
                } catch (final NumberFormatException e) {
                    return 0;
                }
            }
        return plan.where().stream()
                .filter(placement -> Placement.Shape.TREE == placement.shape())
                .mapToInt(Placement::hops)
                .findFirst().orElse(0);
    }

    /**
     * The instruction without its {@code ?hops}: a budget is the plan's to restate at each level — on the slave's
     * seed — so no copied instruction may carry the level above's.
     */
    private static Inst withoutHops(final Inst inst) {
        return inst.tid().hasQ(HOPS) ? inst.tid(inst.tid().removeQ(HOPS)) : inst;
    }

    private static List<Inst> withoutHops(final List<Inst> insts) {
        return insts.stream().map(PlanInterpreter::withoutHops).toList();
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
     * The fan-out targets: the peers this machine DIALED, keyed by connection uri. Empty on a lone machine,
     * which gates the plan out of the default schedule there.
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
     */
    private static List<Obj> shardTargets(final Env env, final Placement route) {
        try {
            // A TARGET IS ONLINE AND SERVES THE ROUTE (Places.serves). AN OFFLINE PEER IS NO TARGET: a dialed peer
            // whose link is down stays in the map (it is being redialed), but a shard shipped down it answers `not
            // connected` and that fail poisons the reduction -- so an outage SHRINKS the fan-out instead, and the fold
            // completes on the peers that answer. A PEER THAT CANNOT `to` IS NO TARGET EITHER: the route ships each
            // slave as to(<target>), so a client whose inst declares no to has nothing to take it with. The seed bulk
            // is factored over whatever is left, so nothing is dropped and nothing is computed twice.
            // the route is the WIRE address the peer answers at -- its authority -- never its key, which is a local
            // path (where the member sits in the map) and would ship the shard nowhere
            return env.peers().values()
                    .filter(peer -> peer.isRec() && peer.asRec().at(uri(HOST)).isUri())
                    .filter(peer -> Places.serves(peer, route))
                    .map(peer -> (Obj) peer.asRec().atDirect(uri(AUTHORITY)))
                    .filter(Obj::isUri)
                    .toList();
        } catch (final Exception e) {
            return List.of();
        }
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
                    if (!unit.isNoObj() && unit.test(inst))
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
