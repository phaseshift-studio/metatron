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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.math.cat.Law;
import studio.phaseshift.metatron.isa.m.math.cat.LawTable;
import studio.phaseshift.metatron.isa.m.math.cat.TheoryHelper;
import studio.phaseshift.metatron.isa.m.parser.mFluent;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import static studio.phaseshift.metatron.Tokens.CODE_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.math.cat.catLiveness.carrier;
import static studio.phaseshift.metatron.isa.m.math.cat.catLiveness.declares;
import static studio.phaseshift.metatron.isa.m.math.cat.catLiveness.same;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

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
        final Law law = plan.law();
        return null == law ? shard(plan, code) : switch (law) {
            case unit -> unitRemoval(code);
            case involution -> involutionRemoval(code);
            default -> code;
        };
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

        final Cut cut = plan.cuts().getFirst();
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
            summands.add(block_(block_(block_(slave))));
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
     * The fan-out targets: this machine's own reachable peers, keyed by connection uri. Empty on a lone
     * machine, which gates the plan out of the default schedule there.
     */
    private static List<Obj> shardTargets() {
        try {
            return new ArrayList<>(Machine.current().network().peers().jvm().keySet());
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
        final List<Inst> kept = insts.stream()
                .filter(i -> !isUnit(i, laws) && !isIdentity(i, laws))
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
}
