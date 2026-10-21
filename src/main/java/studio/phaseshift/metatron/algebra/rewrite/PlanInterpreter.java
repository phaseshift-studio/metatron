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
import studio.phaseshift.metatron.isa.m.math.cat.TheoryHelper;
import studio.phaseshift.metatron.isa.m.parser.mFluent;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

import static studio.phaseshift.metatron.Tokens.ADD;
import static studio.phaseshift.metatron.Tokens.CODE_TID;
import static studio.phaseshift.metatron.Tokens.DERIVATION;
import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.ID;
import static studio.phaseshift.metatron.Tokens.INVERSE;
import static studio.phaseshift.metatron.Tokens.OP;
import static studio.phaseshift.metatron.Tokens.ZERO;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.cat.catLiveness.carrier;
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
 * emit the {@code [chain...]==[to()...]} layout the SwarmProcessor consumes. The cut's law obligations
 * ({@code monoidic} + {@code commutative}) ride on the {@code blocked} slot, so
 * {@link PlanBuilder#create()}'s shape-derived check and the match-time check are the same Thm 19 hypothesis.
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
     * Lower the plan onto {@code code} — the single application point. A distribution plan (no {@code law})
     * lowers through {@link #shard}; a law-driven plan dispatches on its declared {@code law}. An unknown plan
     * is the identity.
     */
    public static Code lower(final Plan plan, final Code code) {
        return lowerCut(plan, code);
    }

    /**
     * The cut/route plan — the structural form: match the cut, isolate the span, route to the target. A
     * {@code none} target deletes the isolated span (the unit law's transform); a form-classified cut
     * distributes through {@link #shard}; a role/law-classified cut selects the local fold — the ring's own
     * operation, read off the theory rather than a transform name.
     */
    private static Code lowerCut(final Plan plan, final Code code) {
        final Cut cut = plan.cut();
        // the unit law: a none target deletes the identity (and unwraps the single addend)
        for (final Route route : plan.routes())
            if (Route.Target.NONE == route.target())
                return ID.equals(cut.role()) ? unitRemoval(code) : code;
        // the distribution plan: a form-classified cut (reducer/join/gather)
        if (null != cut.formOrNull())
            return shard(plan, code);
        // the role-classified folds: add (sum like terms), zero (unwrap the single addend), inverse (cancel),
        // map (unwrap the stream lift)
        final String role = cut.role();
        if (null != role)
            return ADD.equals(role) ? branchCollapse(code)
                    : ZERO.equals(role) ? branchInline(code)
                    : INVERSE.equals(role) ? inverseCancellation(code)
                    : MAP_INST_TID.name().equals(role) ? mapUnwrap(code)
                    : ORDER_INST_TID.name().equals(role) ? orderShard(plan, code)
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
     * The one-cut distribution (Thm 19): factor the seed bulk over the roster, replicate the floatable run,
     * reapply the reducer behind a barrier. The identity unless the chain matches the plan's one-cut shape and
     * the machine has live peers to fan out over.
     */
    private static Code shard(final Plan plan, final Code code) {
        if (Machine.current().network().peers().isEmpty())
            return code;
        if (code.insts().stream().anyMatch(i -> i.tid().hasQ("no_shard")))
            return code;

        final List<Inst> insts = code.insts();
        // the shape is [initial, {*}elementwise, reducer] — {*} = ZERO or more, so the minimum is
        // seed + reducer (2 insts); a bare reduction over the bulk is shardable.
        if (2 > insts.size() || !START_INST_TID.equals(insts.getFirst().tid().basePath()))
            return code;

        final Cut cut = plan.cut();
        final Type carrier = insts.getFirst().arg(0).type();
        final List<Inst> body = insts.subList(1, insts.size());
        final List<Inst> typed = body.stream().map(i -> retype(i, carrier)).toList();

        // the one-cut shape: [ {*}floatable, blocked (declaring the cut's laws), {*}tail ]
        final List<List<Inst>> captures = InstPattern.match(
                List.of(
                        InstPattern.law(Law.floatable).star(),
                        InstPattern.law(Law.blocked).and(cut.laws().toArray(Law[]::new)),
                        InstPattern.any().star()),
                typed);
        if (null == captures)
            return code;

        final int cutIndex = 1 + captures.get(0).size();
        final Inst reducer = insts.get(cutIndex);

        final List<Obj> seed = insts.getFirst().arg(0).stream().toList();
        if (2 > seed.size())
            return code;

        final List<Obj> targets = shardTargets();
        if (targets.isEmpty())
            return code;
        final int k = Math.min(targets.size(), seed.size());

        // emit (a·c + b·c)·c — the master gathers partials and re-applies the reducer at home; each slave
        // carries one factor of the bulk, the floatable run replicated, and a local partial reduce.
        //
        // A SLAVE SHIPS UNQUOTED. Nothing on this machine evaluates it: a multi-instruction chain is Code, never
        // an Inst, so neither the lst nor select's at() auto-resolves it; to(<peer>) takes it as its lhs, raw; and
        // the transport ships Network.write's message (start(slave).to(<>)) without computing it here (see
        // Network.Helper.asTransport). The peer's evaluation of that start(...) is the one that runs the slave,
        // so what comes back is its value, 12@<tag>. A quote would now be peeled on the far side instead, and
        // whether the code it uncovers still runs is left to the peer's processor -- so there is none.
        final List<Obj> summands = new ArrayList<>();
        final List<Obj> routes = new ArrayList<>();
        final mFluent<?> master = merge_().addInst(reducer).addInsts(insts.subList(cutIndex + 1, insts.size()));
        for (int s = 0; s < k; s++) {
            final List<Obj> part = new ArrayList<>();
            for (int j = s; j < seed.size(); j += k)
                part.add(seed.get(j));
            final mFluent<?> slave = start_(objs(part)).q("no_shard");
            slave.addInsts(insts.subList(1, cutIndex));
            slave.addInst(reducer);
            summands.add(slave);
            routes.add(to_(targets.get(s)).tryToInst());
        }
        final Code emitted = start_(lst(summands)).select_(lst(routes)).addInsts(master.insts());
        return emitted;
    }

    /**
     * The one-cut sort distribution: factor the seed bulk over the roster, replicate the floatable run,
     * each peer orders its shard locally, and the master k-merges the sorted shards behind the barrier.
     * The identity unless the chain matches {@code [start, {*}floatable, order, {*}tail]} and the machine
     * has live peers to fan out over. The combine is {@code kmerge} (the sorted merge), not the monoidic
     * reapply of {@link #shard}.
     */
    private static Code orderShard(final Plan plan, final Code code) {
        if (Machine.current().network().peers().isEmpty())
            return code;
        if (code.insts().stream().anyMatch(i -> i.tid().hasQ("no_shard")))
            return code;

        final List<Inst> insts = code.insts();
        if (2 > insts.size() || !START_INST_TID.equals(insts.getFirst().tid().basePath()))
            return code;

        int cutIndex = -1;
        for (int i = 1; i < insts.size(); i++)
            if (ORDER_INST_TID.equals(insts.get(i).tid().basePath())) {
                cutIndex = i;
                break;
            }
        if (-1 == cutIndex)
            return code;

        final Inst order = insts.get(cutIndex);
        final Obj selector = order.arg(0);

        final List<Obj> seed = insts.getFirst().arg(0).stream().toList();
        if (2 > seed.size())
            return code;

        final List<Obj> targets = shardTargets();
        if (targets.isEmpty())
            return code;
        final int k = Math.min(targets.size(), seed.size());

        // emit — the master k-merges the sorted shards; each slave carries one factor of the bulk, the
        // floatable run replicated, and a local order. The slave ships unquoted, exactly as in shard.
        final List<Obj> summands = new ArrayList<>();
        final List<Obj> routes = new ArrayList<>();
        final mFluent<?> master = merge_().addInsts(kmerge_(selector).insts()).addInsts(insts.subList(cutIndex + 1, insts.size()));
        for (int s = 0; s < k; s++) {
            final List<Obj> part = new ArrayList<>();
            for (int j = s; j < seed.size(); j += k)
                part.add(seed.get(j));
            final mFluent<?> slave = start_(objs(part)).q("no_shard");
            slave.addInsts(insts.subList(1, cutIndex));
            slave.addInsts(order_(selector).insts());
            summands.add(slave);
            routes.add(to_(targets.get(s)).tryToInst());
        }
        final Code emitted = start_(lst(summands)).select_(lst(routes)).addInsts(master.insts());
        return emitted;
    }

    /**
     * Retype a bare chain instruction with its declared (dom/rng-qualified) tid, so {@code Form.of} and
     * {@code LawTable.declares} read its coefficient shape and laws before resolution. An undeclared op
     * stays bare and thereby matches no floatable/blocked slot — fail closed, as the hand-rolled scan did.
     */
    private static Inst retype(final Inst inst, final Type carrier) {
        final LawTable.Declared declared = LawTable.declared(inst.tid().basePath(), carrier);
        return null == declared ? inst : inst.tid(declared.tid());
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
    private static List<Obj> shardTargets() {
        try {
            return Machine.current().network().peers().elements()
                    .filter(peer -> peer.second().isRec() && peer.second().asRec().at(uri(HOST)).isUri())
                    .map(Rel::first)
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
        final List<Inst> kept = insts.stream()
                .filter(i -> !isUnit(i, laws) && (!isIdentity(i, laws) || sole))
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
     * The bare-identity test: an instruction with no operands that <em>is</em> a theory's identity operation
     * (the role holds an instruction, not a literal) — the code ring's {@code id}. Its occurrence in a chain
     * is the identity morphism, hence dead weight.
     */
    private static boolean isIdentity(final Inst inst, final Map<Type, Rec> laws) {
        if (!inst.args().isEmpty())
            return false;
        if (!cInt.ONE().equals(inst.c()))
            return false;   // id{n} is a multiplicity, not a no-op
        for (final Map.Entry<Obj, Obj> instance : laws.computeIfAbsent(T(CODE_TID), TheoryHelper::instances).jvm().entrySet()) {
            if (!instance.getValue().isRec())
                continue;
            final Rec block = instance.getValue().asRec();
            for (final String role : TheoryHelper.IDENTITY_ROLES) {
                final fURI identity = TheoryHelper.identityAddress(block, role);
                if (null != identity && inst.tid().basePath().equals(identity))
                    return true;
            }
        }
        return false;
    }

    /**
     * The involution law (local): an adjacent pair of the SAME self-inverse operation is the identity —
     * {@code f·f ↦ ε} for any operation declaring {@code involution} (or the carrier's declared inverse).
     */
    private static Code involutionRemoval(final Code code) {
        final List<Inst> insts = code.insts();
        if (2 > insts.size())
            return code;
        final Type carrier = carrier(code);
        final List<Inst> collapsed = collapsePairs(insts, (a, b) -> same(a, b) && isInvolution(a, carrier));
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
     * Collapse adjacent pairs the predicate accepts to nothing — the equation {@code f·f ↦ ε}.
     */
    private static List<Inst> collapsePairs(final List<Inst> insts, final BiPredicate<Inst, Inst> match) {
        final List<Inst> result = new ArrayList<>();
        int i = 0;
        while (i < insts.size()) {
            if (i + 1 < insts.size() && match.test(insts.get(i), insts.get(i + 1)))
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
        final Type carrier = carrier(code);
        final List<Inst> collapsed = collapseKeep(insts, (a, b) -> same(a, b) && declares(a, carrier, Law.idempotent));
        return collapsed.size() == insts.size() ? code : code(collapsed);
    }

    /**
     * Collapse adjacent pairs the predicate accepts to a single instance — the equation {@code f·f ↦ f} shared
     * by the idempotent law and a monoidic fold (a reduction of one element is that element).
     */
    private static List<Inst> collapseKeep(final List<Inst> insts, final BiPredicate<Inst, Inst> match) {
        final List<Inst> result = new ArrayList<>();
        int i = 0;
        while (i < insts.size()) {
            if (i + 1 < insts.size() && match.test(insts.get(i), insts.get(i + 1))) {
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
        final Type carrier = carrier(code);
        final List<Inst> collapsed = collapseKeep(insts, (a, b) -> same(a, b) && a.c().equals(b.c()) && isFold(a, carrier));
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
        final Type carrier = carrier(code);
        final List<Inst> collapsed = collapsePairs(insts, (a, b) -> inversePair(a, b, carrier));
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
     * Adjacent inverse pair with equal operands — the group law {@code f·f⁻¹ = 1}, read off the edge's declared
     * {@code inverse} (either direction).
     */
    private static boolean inversePair(final Inst a, final Inst b, final Type carrier) {
        return isInverseOf(a, b, carrier) || isInverseOf(b, a, carrier);
    }

    /**
     * Whether {@code b} is the declared inverse of {@code a} with equal operands. Endpoints are unavailable
     * before resolution, so the operand type that selected the declaration is the type guard.
     */
    private static boolean isInverseOf(final Inst a, final Inst b, final Type carrier) {
        final LawTable.Declared declared = LawTable.declared(a.tid().basePath(), carrier);
        return null != declared && null != declared.entry().inverse()
                && b.tid().basePath().equals(declared.entry().inverse().basePath())
                && a.args().equals(b.args());
    }

    /**
     * The poset law (local): compose adjacent comparisons of the same ordered type to the stricter single
     * bound — {@code gt(a)·gt(b) ↦ gt(max(a,b))}.
     */
    private static Code posetRemoval(final Code code) {
        final List<Inst> insts = code.insts();
        if (2 > insts.size())
            return code;
        final List<Inst> out = new ArrayList<>(insts.size());
        boolean changed = false;
        final Type carrier = carrier(code);
        for (int i = 0; i < insts.size(); i++) {
            final Inst composed = (i + 1 < insts.size()) ? posetCompose(insts.get(i), insts.get(i + 1), carrier) : null;
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
     * Compose two adjacent comparisons of the same ordered type to the stricter single bound — the poset law
     * {@code x > a ∧ x > b ⇔ x > max(a, b)}. Null when the pair is not composable.
     */
    private static Inst posetCompose(final Inst a, final Inst b, final Type carrier) {
        if (!sameOp(a, b) || !declares(a, carrier, Law.poset) || 1 != a.args().count() || 1 != b.args().count())
            return null;
        final String op = a.tid().basePath().name();
        final boolean upper = op.equals(GT_INST_TID.name()) || op.equals(GTE_INST_TID.name());
        final boolean lower = op.equals(LT_INST_TID.name()) || op.equals(LTE_INST_TID.name());
        if (!upper && !lower)
            return null;
        final Obj x = a.arg(0);
        final Obj y = b.arg(0);
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
        return a.args(lst(bound));
    }

    /**
     * The stream lift (local): {@code map} is transparent on the value it wraps, so {@code map(f) ↦ f}, which
     * also collapses {@code map(map(f)) ↦ map(f)}.
     */
    private static Code mapUnwrap(final Code code) {
        final List<Inst> insts = code.insts();
        boolean changed = false;
        final List<Inst> out = new ArrayList<>(insts.size());
        final Type carrier = carrier(code);
        for (final Inst inst : insts) {
            final Obj wrapped = mapArgument(inst, carrier);
            if (null != wrapped) {
                out.add(wrapped.asInst());
                changed = true;
            } else
                out.add(inst);
        }
        return changed ? code(out) : code;
    }

    /**
     * The absorbing law (local): an absorbing op swallows the chain before it, so everything from the seed to
     * the first absorbing op is dead code — {@code …·g·zero ↦ …·zero}.
     */
    private static Code absorbingRemoval(final Code code) {
        final List<Inst> insts = code.insts();
        final int seed = (!insts.isEmpty() && START_INST_TID.equals(insts.get(0).tid().basePath())) ? 1 : 0;
        final Type carrier = carrier(code);
        for (int i = seed; i < insts.size(); i++)
            if (declares(insts.get(i), carrier, Law.absorbing)) {
                if (i <= seed)
                    return code;
                final List<Inst> out = new ArrayList<>(insts.subList(0, seed));
                out.addAll(insts.subList(i, insts.size()));
                return code(out);
            }
        return code;
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
     * Contract the primitive composition of a derivation back to its derived instruction — {@code mult·inv ↦ div}.
     */
    private static List<Inst> contract(final List<Inst> insts, final Derivation d) {
        final List<Inst> out = new ArrayList<>();
        for (final Inst inst : insts) {
            final Map<fURI, Obj> bindings = new HashMap<>();
            final Inst rhs = d.rhs().insts().getLast();
            if (matchGenerics(rhs, inst, bindings)) {
                final Inst lhs = d.lhs().insts().getLast();
                out.add((Inst) Inst.Helper.substituteGenerics(lhs, bindings));
            } else
                out.add(inst);
        }
        return out;
    }

    /**
     * Match a generic pattern against a source, binding the generic fURIs ({@code A}, {@code B}, …) as they are
     * met. A generic uri matches anything (binding it); an inst matches by tid and recurses into its args.
     */
    private static boolean matchGenerics(final Obj pattern, final Obj source, final Map<fURI, Obj> bindings) {
        if (pattern.isUri() && pattern.uriValue().isGeneric()) {
            final fURI key = pattern.uriValue();
            final Obj prior = bindings.get(key);
            if (null == prior) {
                bindings.put(key, source);
                return true;
            }
            return prior.equals(source);
        }
        if (pattern.isInst() && source.isInst()) {
            final Inst p = pattern.asInst();
            final Inst s = source.asInst();
            if (!s.tid().test(p.tid()))
                return false;
            if (p.args().count() != s.args().count())
                return false;
            for (int i = 0; i < p.args().count(); i++)
                if (!matchGenerics(p.arg(i), s.arg(i), bindings))
                    return false;
            return true;
        }
        if (pattern.isCode() && source.isCode()) {
            final List<Inst> ps = pattern.asCode().insts();
            final List<Inst> ss = source.asCode().insts();
            if (ps.size() != ss.size())
                return false;
            for (int i = 0; i < ps.size(); i++)
                if (!matchGenerics(ps.get(i), ss.get(i), bindings))
                    return false;
            return true;
        }
        // a structural pattern (inst/code) never matches a non-structural source — falling through to
        // source.test(pattern) here would let a scalar pass a nested-composition pattern
        if (pattern.isInst() || pattern.isCode() || source.isInst() || source.isCode())
            return false;
        return source.test(pattern);
    }

    /**
     * The additive like-term law (local): equal members of any occurrence of the algebra's additive op collapse
     * to the member that first appeared, carrying the summed multiplicities — {@code S(x,x,…,x) ↦ S(x{n})}. The
     * witness is read off object[code].law at rewrite time (for the code ring: branch).
     */
    private static Code branchCollapse(final Code code) {
        return repeatedTerms(additiveWitness(T(CODE_TID)), T(CODE_TID), code);
    }

    /**
     * The one-term sum law (local): an occurrence of the algebra's additive op with a single member is that
     * member — {@code S(x) ↦ x}, the frame's own coefficient folded into it.
     */
    private static Code branchInline(final Code code) {
        return oneTerm(additiveWitness(T(CODE_TID)), T(CODE_TID), code);
    }

    /**
     * The op address of a type's declared additive structure — the law-indexed witness: the first theory
     * instance of {@code object[T].law} that carries an additive role — the {@code add} role of a ring/field/rig,
     * or the {@code op} role of an {@code add_*} monoid/group. The role is read with {@code atDirect} (no call
     * evaluation; {@code rec.has} reports false on law keys that still resolve, so the noobj answer is the
     * presence test), and the auto pointer is unwrapped with {@code getPointer} without resolving it. For the
     * code ring this answers the branch address, for the int ring the plus address; a type declaring no additive
     * structure answers null, which degrades the normalizers to the identity.
     */
    private static fURI additiveWitness(final Type type) {
        fURI add = null;
        for (final Map.Entry<Obj, Obj> instance : TheoryHelper.instances(type).jvm().entrySet()) {
            if (!instance.getValue().isRec())
                continue;
            final Rec block = instance.getValue().asRec();
            final String name = instance.getKey().isUri() ? instance.getKey().uriValue().name() : null;
            if (null != name && name.startsWith("add_")) {
                final fURI op = roleAddress(block, OP);
                if (null != op)
                    return op;  // the additive group's n-ary op (branch) — the form the normalizers fold
            } else if (null == add)
                add = roleAddress(block, ADD);
        }
        return add;
    }

    /**
     * The op address a law block's role binds, or null when the role is absent or binds a literal: the value
     * arrives as a {@code !*} auto pointer naming its target in its own argument, so the address is read
     * without resolving — the target may be an as-yet-unresolved registration.
     */
    private static fURI roleAddress(final Rec block, final String role) {
        final Obj holder = block.atDirect(uri(role));
        if (holder.isNoObj())
            return null;
        if (holder.isUri())
            return holder.uriValue();
        return Obj.Helper.getPointer(holder).orElse(null);
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
