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

import studio.phaseshift.metatron.algebra.TheoryHelper;
import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Resolver;
import studio.phaseshift.metatron.isa.m.type.*;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.algebra.Law.Helper.declares;
import static studio.phaseshift.metatron.algebra.rewrite.plan.PlanInterpreter.comparisonOf;
import static studio.phaseshift.metatron.algebra.rewrite.plan.PlanInterpreter.mapArgument;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.mach.type.compiler.Rewriter.Helper.carriers;
import static studio.phaseshift.metatron.isa.m.mach.type.compiler.Rewriter.Helper.contracts;
import static studio.phaseshift.metatron.isa.m.type.Inst.Helper.sameOp;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;


/**
 * The a-priori liveness abstraction — the single-pass {@link Profile} over a code chain (or over an
 * {@code abstraction::T}'s morphisms) that decides, from the category's declarations alone, whether a
 * rule is a live candidate on this chain: {@code live(Profile, Inst)} is the necessary half of each
 * rule's guard, so {@code false} proves the rule is the identity while {@code true} only licenses the
 * match. The rule-guard vocabulary it reads lives with its owners: the chain typing in
 * {@code Rewriter.Helper}, the law reads in {@code Law.Helper}, the instruction equalities in {@code Inst.Helper}, and the
 * rules' own guards in {@code PlanInterpreter}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class catLiveness {

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
            return live(profileOf(morphisms), rewrite);
        } catch (final Exception e) {
            return true; // the profile failed to compute — cannot prove identity, so keep the edge (never prune when unsure)
        }
    }

    /**
     * The profile of an abstraction's morphisms, computed once per lift: a path asks every rule of the same morphism
     * list in turn, so the last list profiled on this thread (by identity) answers again.
     */
    private static Profile profileOf(final List<Obj> morphisms) {
        final Map.Entry<List<Obj>, Profile> last = LAST_PROFILE.get();
        if (null != last && last.getKey() == morphisms)
            return last.getValue();
        final Profile profile = Profile.of(morphisms);
        LAST_PROFILE.set(Map.entry(morphisms, profile));
        return profile;
    }

    private static final ThreadLocal<Map.Entry<List<Obj>, Profile>> LAST_PROFILE = new ThreadLocal<>();

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
            case "branch_collapse" -> profile.repeatedTerms;
            case "branch_inline" -> profile.oneTerm;
            case "derivation_contraction" -> Law.Helper.derivations().values().stream()
                    .anyMatch(d -> profile.ops.contains(d.atDirect(uri(RHS)).asCode().insts().getLast().tid().basePath().toString()));
            case "law_shard" -> profile.shard;   // a seed + a reducer whose partials recombine (the one-cut shard)
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
        private boolean adjPoset;        // an adjacent pair of the same filter on the same poset comparison
        private boolean adjInverse;      // an adjacent pair on a declared opposing edge
        private boolean absorbing;       // an absorbing op with live code before it
        private boolean mapWrap;         // a map(f) wrapping an unwrappable instruction
        private boolean repeatedTerms;   // an occurrence of the additive witness with two or more members (like terms need a pair)
        private boolean oneTerm;         // an occurrence of the additive witness with exactly one member
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
            // the additive normalizers' op, read off the code ring's algebra exactly as their rules read it -- a ring
            // declaring no additive structure has no witness, and neither normalizer is then ever live
            final fURI additive = TheoryHelper.additiveWitness(T(CODE_TID));
            // the carrier at each position, exactly as the rules read it (carriers): the chain is the morphisms' carried
            // instructions, and a morphism carrying none leaves every position unknown
            final List<Rec> blocks = morphisms.stream().filter(Obj::isRec).map(Obj::asRec).toList();
            final List<Inst> chain = blocks.stream().filter(b -> b.has(uri(OBJ))).map(b -> b.at(OBJ).asInst()).toList();
            // one typing of the chain answers both: the carrier at each position and the contract each resolved to
            final Code typed = chain.size() == blocks.size() ? code(chain) : null;
            final List<Type> carriers = null == typed ? null : carriers(typed);
            final List<Inst> contracts = null == typed ? null : contracts(typed);
            final Map<Type, Boolean> inverses = new HashMap<>();
            boolean priorCarrierInverse = false;
            Inst priorComparison = null;
            Type priorCarrier = T(ALL);
            String priorName = null;
            fURI priorInverse = null;
            boolean pastSeed = false;
            boolean hasSeed = false;
            for (int i = 0; i < blocks.size(); i++) {
                final Rec morphism = blocks.get(i);
                final Type carrier = null == carriers ? T(ALL) : carriers.get(i);
                final boolean carrierInverse = inverses.computeIfAbsent(carrier, t -> !TheoryHelper.invInst(t).isNoObj());
                final Inst keyed = morphism.has(uri(OBJ)) ? morphism.at(OBJ).asInst() : null;
                final String name = morphism.has(uri(NAME)) ? morphism.at(NAME).uriValue().name()
                        : (null == keyed ? null : keyed.tid().basePath().name());
                final String form = morphism.has(uri(FORM)) ? morphism.at(FORM).uriValue().name() : null;
                final List<String> laws = morphism.has(uri(LAW))
                        ? morphism.at(LAW).asLst().lstValue().stream().map(l -> l.uriValue().name()).toList()
                        : List.of();
                if (Inst.Form.initial.name().equals(form) || (null != contracts && null != contracts.get(i) && contracts.get(i).isInitial())) {   // the seed: an initial form — the morphism's own, or the contract the chain's typing resolved it to ({0} => A), exactly as law_shard reads it
                    hasSeed = true;
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
                    // the additive normalizers read the members' count; whether two members are like terms is the
                    // value residue, so a pair only licenses the collapse while a lone member inlines -- unless it is
                    // keyed to a remote place, which runs there. A keyed pair never collapses: its rec already summed
                    // equal lanes under one key, and lanes under different keys run at different places.
                    if (null != additive && additive.basePath().equals(keyed.tid().basePath())) {
                        if (1 == keyed.args().count() && !Inst.Helper.hasRemoteLane(keyed))
                            profile.oneTerm = true;
                        else if (1 < keyed.args().count() && !keyed.args().isRec())
                            profile.repeatedTerms = true;
                    }
                }
                if (pastSeed && laws.contains(Law.absorbing.name()))
                    profile.absorbing = true;
                if (pastSeed && null != name && name.equals(priorName)) {
                    // the pair's carrier is its first member's, as the rule reads it
                    if (priorCarrierInverse || laws.contains(Law.involution.name()))
                        profile.adjInvolution = true;
                    if (laws.contains(Law.idempotent.name()))
                        profile.adjIdempotent = true;
                    // two of the same filter on the same comparison, poset-declared at the first one's carrier
                    final Inst comparison = null == keyed ? null : comparisonOf(keyed);
                    if (null != comparison && null != priorComparison && sameOp(comparison, priorComparison)
                            && declares(priorComparison, priorCarrier, Law.poset))
                        profile.adjPoset = true;
                    if ((Inst.Form.reducer.name().equals(form) || Inst.Form.join.name().equals(form)) && laws.contains(Law.monoidic.name()))
                        profile.adjMonoidFold = true;
                }
                // the declared opposing edge, read off the carried n-tid rather than forcing the morphism's lazy analysis
                final fURI inverse = null == keyed ? null : TheoryHelper.roleAddress(Law.Helper.lawsOf(keyed.tid()), INVERSE);
                // directional, as the rule is: the current op must be the PRIOR op's declared inverse (x.f().f⁻¹)
                if (pastSeed && null != priorInverse && priorInverse.name().equals(name))
                    profile.adjInverse = true;
                priorName = name;
                priorInverse = inverse;
                priorCarrierInverse = carrierInverse;
                priorComparison = null == keyed ? null : comparisonOf(keyed);
                priorCarrier = carrier;
                pastSeed = true;
            }
            return profile;
        }

        /**
         * Whether the chain instruction's partials recombine — it has a computed {@link Law.Helper#combiner} once keyed
         * with its declared n-tid, exactly as the shard plan keys it ({@link Law.Helper#keyed}, a keyed fold's spec folds
         * included). The morphism carries the ORIGINAL instruction, so the key is re-derived from the carrier here.
         */
        private static boolean combines(final Inst chain, final Type carrier) {
            return null != chain && null != Law.Helper.combiner(Law.Helper.keyed(chain, carrier));
        }

        /**
         * Whether the operand's type models any algebraic theory -- memoized per type, since a chain's
         * instructions overwhelmingly share their operand types.
         */
        private static boolean models(final Type operand, final Map<Type, Boolean> cache) {
            return cache.computeIfAbsent(operand, t -> !TheoryHelper.instances(t).jvm().isEmpty());
        }
    }
}
