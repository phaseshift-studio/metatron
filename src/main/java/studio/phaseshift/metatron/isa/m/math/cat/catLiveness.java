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

package studio.phaseshift.metatron.isa.m.math.cat;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.MAP_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;


/**
 * The a-priori liveness abstraction — the single-pass {@link Profile} over a code chain (or over an
 * {@code abstraction::T}'s morphisms) that decides, from the category's declarations alone, whether a
 * rule is a live candidate on this chain: {@code live(Profile, Inst)} is the necessary half of each
 * rule's guard, so {@code false} proves the rule is the identity while {@code true} only licenses the
 * match. Also the shared rule-guard vocabulary ({@code sameOp}, {@code mapArgument}, {@code declares})
 * the other slices of the law-driven rewrite family use.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class catLiveness {

    /**
     * The carrier type of a code chain — the {@link #carrierOf carrier} of the seed (the {@code start(x)} wrapper's
     * arg). Argless ops ({@code neg()}) have no arg to derive it from, so the rewrite reads it off the seed.
     */
    public static Type carrier(final Code code) {
        final List<Inst> insts = code.insts();
        if (!insts.isEmpty() && START_INST_TID.equals(insts.get(0).tid().basePath()))
            return carrierOf(insts.get(0).arg(0));
        return T(ALL);
    }

    /**
     * The carrier a seed fixes — the type of ONE obj of its stream. A bulk seed types as its stream: the member type
     * with the bulk's multiplicity as its coefficient ({@code {1,2,3}} is {@code int{3}::T}). A multiplicity is not an
     * object of the category — no theory is declared over {@code int{3}}, and {@code object::T} (dom {@code {?}})
     * refuses it — so it is dropped and the member type ({@code int::T}) carries the chain. The rules and the
     * {@code Profile} both read the carrier here, so the pruner sees exactly the carrier the rule it prunes would.
     */
    public static Type carrierOf(final Obj seed) {
        final Type type = seed.type();
        final fURI id = type.typeId();
        return null == id || id.c().isOne() || id.c().isZero() ? type : T(id.one());
    }

    /**
     * Two chain elements are the same operation, whatever their operands — the guard for a law that
     * relates an operation to itself (poset comparison composition).
     */
    public static boolean sameOp(final Inst a, final Inst b) {
        return a.tid().basePath().equals(b.tid().basePath());
    }

    /**
     * Two chain elements are the same operation AND the same operands — the guard for a law that relates an
     * operation to itself (involution, idempotent, monoidic fold).
     */
    public static boolean same(final Inst a, final Inst b) {
        return sameOp(a, b) && a.args().equals(b.args());
    }

    /**
     * The stream lift, {@code map} — kept keyed on the operation itself: its {@code {?} → {?}} coefficient
     * shape is shared by other instructions ({@code is} is also {@code {?} → {?}}), so a shape-only test
     * would unwrap a predicate. The chain instruction carries no endpoints, so the op <em>address</em> is
     * what identifies it.
     */
    static boolean isMap(final Inst inst) {
        return inst.tid().basePath().equals(MAP_INST_TID);
    }

    /**
     * The instruction a {@code map} wraps, or null when it wraps no instruction. Before resolution the
     * wrapped operand arrives as a uri (the op symbol), so it is read back as an instruction.
     */
    public static Obj mapArgument(final Inst inst, final Type carrier) {
        if (1 != inst.args().count() || !isMap(inst))
            return null;
        final Obj arg = inst.arg(0);
        if (arg.isObjInst())
            return arg;
        if (!arg.isUri())
            return null;
        final fURI op = arg.uriValue().isAbsolute() ? arg.uriValue() : M_ISA_TID.extend(INST).extend(arg.uriValue().name());
        final Inst wrapped = instB(op, lst());
        // keep the chain's pre-resolution form (the op address), but only when the address is a real op
        if (!Machine.read(op).isNoObj())
            return wrapped;
        return null;
    }

    /**
     * Whether an instruction declares a process law for the carrier's type. The chain instruction carries no
     * endpoints (the rewriter runs before resolution), so the declaration is resolved by op address,
     * disambiguated by the operand type — never guessed across types.
     */
    public static boolean declares(final Inst inst, final Type carrier, final Law law) {
        final LawTable.Declared declared = LawTable.declared(inst.tid().basePath(), carrier);
        return null != declared && declares(declared, law);
    }

    static boolean declares(final LawTable.Declared declared, final Law law) {
        return declared.entry().laws().lstValue().stream()
                .anyMatch(l -> l.uriValue().name().equals(law.name()));
    }

    /**
     * The liveness test, decided endogenously: the profile is read off an {@code abstraction::T}'s own
     * morphisms (their {@code law}, {@code form} and {@code name} fields, with each morphism's carried
     * {@code obj} instruction supplying operands), so the vertex prunes its out-edges from data it already
     * contains rather than from a table outside the graph.
     */
    public static boolean maybeNotIdentity(final List<Obj> morphisms, final Inst rewrite) {
        if (morphisms.isEmpty())
            return false; // every rewrite is the identity on the empty word
        try {
            return live(Profile.of(morphisms), rewrite);
        } catch (final Exception e) {
            return true; // the profile failed to compute — cannot prove identity, so keep the edge (never prune when unsure)
        }
    }

    /**
     * The rule dispatch shared by both profiles — each case is the necessary half of that rule's own guard.
     */
    static boolean live(final Profile profile, final Inst rewrite) {
        return switch (rewrite.tid().basePath().name()) {
            case "theory_unit_removal" -> profile.unit;
            case "theory_involution" -> profile.adjInvolution;
            case "law_idempotent" -> profile.adjIdempotent;
            case "law_absorbing" -> profile.absorbing;
            case "law_monoidic" -> profile.adjMonoidFold;
            case "law_poset" -> profile.adjPoset;
            case "inverse_cancellation" -> profile.adjInverse;
            case "form_map_unwrap" -> profile.mapWrap;
            case "derivation_contraction" -> LawTable.derivations().stream()
                    .anyMatch(d -> profile.ops.contains(d.rhs().insts().getLast().tid().basePath().toString()));
            case "law_shard" ->
                    profile.shard;   // a seed + a reducer whose partials recombine (the one-cut shard)
            default -> true; // an undeclared rule proves nothing -- keep the edge
        };
    }

    /**
     * The declaration profile of one code chain, computed in a single pass over its instructions: which
     * law-bearing adjacency, theory-modeling operands and forms the chain puts on the table. One field
     * per rule family; every field over-approximates its rule's guard (op adjacency stands in for
     * {@code same()}'s operand equality, operand-models-a-theory stands in for the unit test), never under.
     */
    private static final class Profile {

        private final Set<String> ops = new HashSet<>();  // the op addresses present -- derivation heads test membership
        private boolean unit;            // an op over a theory-modeling operand, or a bare argless op (a candidate identity morphism)
        private boolean adjInvolution;   // an adjacent same-op pair declaring involution, or any adjacent same-op pair under a carrier with a declared inverse role
        private boolean adjIdempotent;   // an adjacent same-op pair declaring idempotent
        private boolean adjMonoidFold;   // an adjacent same-op reducer/join pair declaring monoidic
        private boolean adjPoset;        // an adjacent same-op pair declaring poset
        private boolean adjInverse;      // an adjacent pair on a declared opposing edge
        private boolean absorbing;       // an absorbing op with live code before it
        private boolean mapWrap;         // a map(f) wrapping an unwrappable instruction
        private boolean shard;           // a seed + a reducer with a combiner (law_shard's anchor)

        /**
         * The profile read off an abstraction's morphisms: {@code law}, {@code form} and {@code name} are
         * endogenous fields of each {@code morphism::T}, and each morphism's carried {@code obj} instruction
         * supplies the operands (args, dom) for the unit, map and inverse tests. An {@code initial} morphism
         * is the seed: it fixes the carrier and takes part in no adjacency.
         */
        private static Profile of(final List<Obj> morphisms) {
            final Profile profile = new Profile();
            final Map<Type, Boolean> modeled = new HashMap<>();
            Type carrier = T(ALL);
            boolean carrierInverse = false;
            String priorName = null;
            fURI priorInverse = null;
            boolean pastSeed = false;
            boolean hasSeed = false;
            for (final Obj m : morphisms) {
                if (!m.isRec())
                    continue;
                final Rec morphism = m.asRec();
                final Inst keyed = morphism.has(uri(OBJ)) ? morphism.at(OBJ).asInst() : null;
                final String name = morphism.has(uri(NAME)) ? morphism.at(NAME).uriValue().name()
                        : (null == keyed ? null : keyed.tid().basePath().name());
                final String form = morphism.has(uri(FORM)) ? morphism.at(FORM).uriValue().name() : null;
                final List<String> laws = morphism.has(uri(LAW))
                        ? morphism.at(LAW).asLst().lstValue().stream().map(l -> l.uriValue().name()).toList()
                        : List.of();
                if (Inst.Form.initial.name().equals(form) || (null != keyed && START_INST_TID.equals(keyed.tid().basePath()))) {   // the seed: start (op address) or initial form — the bare start reads as mapper pre-resolution, so the op address is the reliable seed marker
                    hasSeed = true;
                    if (null != keyed && 0 < keyed.args().count()) {
                        carrier = carrierOf(keyed.arg(0));
                        carrierInverse = !TheoryHelper.invInst(carrier).isNoObj();
                    }
                    continue;
                }
                if (hasSeed && Inst.Form.reducer.name().equals(form) && combines(keyed, carrier))
                    profile.shard = true;   // law_shard's anchor: a reducer whose partials recombine behind an order-free barrier
                if (null != keyed) {
                    profile.ops.add(keyed.tid().basePath().toString());
                    if (keyed.args().isEmpty())
                        profile.unit = true;  // a bare op may be a theory's identity morphism
                    else if (models(keyed.dom(), modeled) || models(keyed.arg(0).type(), modeled))
                        profile.unit = true;  // op(id) needs an operand whose type models a theory
                    if (null != mapArgument(keyed, carrier))
                        profile.mapWrap = true;
                }
                if (pastSeed && laws.contains(Law.absorbing.name()))
                    profile.absorbing = true;
                if (pastSeed && null != name && name.equals(priorName)) {
                    if (carrierInverse || laws.contains(Law.involution.name()))
                        profile.adjInvolution = true;
                    if (laws.contains(Law.idempotent.name()))
                        profile.adjIdempotent = true;
                    if (laws.contains(Law.poset.name()))
                        profile.adjPoset = true;
                    if ((Inst.Form.reducer.name().equals(form) || Inst.Form.join.name().equals(form)) && laws.contains(Law.monoidic.name()))
                        profile.adjMonoidFold = true;
                }
                final fURI inverse = null == keyed ? null : inverseOf(keyed);
                if (pastSeed
                        && ((null != inverse && null != priorName && priorName.equals(inverse.name()))
                        || (null != priorInverse && priorInverse.name().equals(name))))
                    profile.adjInverse = true;
                priorName = name;
                priorInverse = inverse;
                pastSeed = true;
            }
            return profile;
        }

        /**
         * Whether the chain instruction's partials recombine — it has a computed {@link LawTable#combiner} once keyed
         * with its declared n-tid, exactly as the shard plan keys it. The morphism carries the ORIGINAL instruction,
         * so the key is re-derived from the carrier here.
         */
        private static boolean combines(final Inst chain, final Type carrier) {
            if (null == chain)
                return false;
            final LawTable.Declared declared = LawTable.declared(chain.tid().basePath(), carrier);
            return null != declared && null != LawTable.combiner(chain.tid(declared.tid()));
        }

        /**
         * The declared opposing edge of the keyed instruction, read from its own entry; null when none. The
         * one residue the morphism does not yet surface eagerly (it sits in the lazy {@code analysis} block),
         * so it is read off the carried n-tid rather than forcing the analysis.
         */
        private static fURI inverseOf(final Inst keyed) {
            final LawTable.Entry entry = LawTable.lawsOf(keyed.tid());
            return null == entry ? null : entry.inverse();
        }

        /**
         * Whether the operand's type models any algebraic theory -- memoized per type, since a chain's
         * instructions overwhelmingly share their operand types.
         */
        private static boolean models(final Type operand, final Map<Type, Boolean> cache) {
            return cache.computeIfAbsent(operand, t -> !LawTable.typeLawsOf(t).jvm().isEmpty());
        }

        /**
         * Whether the declaration's opposing edge is the given op -- one side of an adjacent inverse pair.
         */
        private static boolean opposes(final LawTable.Declared declared, final fURI op) {
            return null != declared && null != declared.entry().inverse() && declared.entry().inverse().basePath().equals(op);
        }
    }
}
