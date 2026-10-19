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

import studio.phaseshift.metatron.algebra.rewrite.Cut;
import studio.phaseshift.metatron.algebra.rewrite.Plan;
import studio.phaseshift.metatron.algebra.rewrite.PlanBuilder;
import studio.phaseshift.metatron.algebra.rewrite.PlanInterpreter;
import studio.phaseshift.metatron.algebra.rewrite.Route;
import studio.phaseshift.metatron.algebra.rewrite.Span;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import java.util.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.CAT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.PLAN_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.map_;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;


/**
 * The law-driven rewrite machinery of the category — the {@code /rewrite} rules and the equation
 * tests and helpers each rule's guard is built from.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class catRewrite {

    /**
     * The one-cut distribution plan — {@code law_shard}, Thm 19 {@code (a+b)·c = (a·c+b·c)·c}: the anchor is a
     * blocked reducer declaring {@code monoidic} (partials regroup) and {@code commutative} (order-free merge); the
     * master re-applies it behind the barrier and runs the tail at home, each peer takes one factor of the seed bulk
     * with the floatable run replicated and a local partial reduce. {@link PlanInterpreter#lower} consumes the cut.
     */
    private static final Cut SHARD_CUT = Cut.reducer(Law.monoidic, Law.commutative);
    private static final Span SHARD_PRE = Span.before().floatable();
    private static final Span SHARD_AT = Span.at();
    private static final Span SHARD_POST = Span.after();
    private static final Plan SHARD_PLAN = PlanBuilder.plan("law_shard")
            .cut(SHARD_CUT)
            .route(Route.local(SHARD_AT, SHARD_POST))
            .route(Route.peers(Span.seed().partition(), SHARD_PRE.withoutSeed(), SHARD_AT))
            .create();

    /**
     * The one-cut sort distribution — {@code order_shard}: the anchor is the {@code order} op (a role cut, not
     * a form — the sort is order-preserving, not a monoidic/commutative fold); the {@code shuffle} route factors
     * the seed over the roster, each peer orders its shard, and the master {@code kmerge}s behind the barrier.
     */
    private static final Cut ORDER_CUT = Cut.role(ORDER_INST_TID.name());
    private static final Plan ORDER_PLAN = PlanBuilder.plan("order_shard")
            .cut(ORDER_CUT)
            .route(Route.local(SHARD_AT, SHARD_POST))
            .route(Route.shuffle(Span.seed().partition(), SHARD_PRE.withoutSeed(), SHARD_AT))
            .create();

    /**
     * The local unit-law plan — cut the identity (the {@code id} role) and route it to {@code none}: the span
     * is deleted, the unit law's transform. No {@code law} selector — the theory names the role.
     */
    private static final Cut UNIT_CUT = Cut.role(ID);
    private static final Plan UNIT_PLAN = PlanBuilder.plan("theory_unit_removal")
            .cut(UNIT_CUT)
            .route(Route.none(Span.at()))
            .create();

    /**
     * The involution plan — cut the self-inverse op ({@code law => [involution]}): the f·f ↦ ε fold.
     */
    private static final Cut INVOLUTION_CUT = Cut.law(Law.involution);
    private static final Plan INVOLUTION_PLAN = PlanBuilder.plan("theory_involution")
            .cut(INVOLUTION_CUT)
            .route(Route.local(Span.at()))
            .create();

    /**
     * The idempotent plan — cut the idempotent op ({@code law => [idempotent]}): the f·f ↦ f fold.
     */
    private static final Cut IDEMPOTENT_CUT = Cut.law(Law.idempotent);
    private static final Plan IDEMPOTENT_PLAN = PlanBuilder.plan("law_idempotent")
            .cut(IDEMPOTENT_CUT)
            .route(Route.local(Span.at()))
            .create();

    /**
     * The monoidic plan — cut a monoidic fold ({@code law => [monoidic]}): the f·f ↦ f fold on a reducer/join.
     */
    private static final Cut MONOIDIC_CUT = Cut.law(Law.monoidic);
    private static final Plan MONOIDIC_PLAN = PlanBuilder.plan("law_monoidic")
            .cut(MONOIDIC_CUT)
            .route(Route.local(Span.at()))
            .create();

    /**
     * The inverse-cancellation plan — cut a declared inverse ({@code role => inverse}): the f·f⁻¹ ↦ ε fold on
     * the declared opposing edge.
     */
    private static final Cut INVERSE_CUT = Cut.role(INVERSE);
    private static final Plan INVERSE_PLAN = PlanBuilder.plan("inverse_cancellation")
            .cut(INVERSE_CUT)
            .route(Route.local(Span.at()))
            .create();

    /**
     * The like-term plan — cut the additive op ({@code role => add}): the route-as-function form, where the
     * transform IS the ring's own addition (sum like terms).
     */
    private static final Cut BRANCH_CUT = Cut.role(ADD);
    private static final Plan BRANCH_COLLAPSE_PLAN = PlanBuilder.plan("branch_collapse")
            .cut(BRANCH_CUT)
            .route(Route.local(Span.at()))
            .create();

    /**
     * The one-term plan — cut the additive identity ({@code role => zero}): the single addend is that addend
     * (the implicit zero drops out).
     */
    private static final Cut BRANCH_INLINE_CUT = Cut.role(ZERO);
    private static final Plan BRANCH_INLINE_PLAN = PlanBuilder.plan("branch_inline")
            .cut(BRANCH_INLINE_CUT)
            .route(Route.local(Span.at()))
            .create();

    /**
     * The poset plan — cut a poset comparison ({@code law => [poset]}): adjacent comparisons compose to the
     * stricter bound.
     */
    private static final Cut POSET_CUT = Cut.law(Law.poset);
    private static final Plan POSET_PLAN = PlanBuilder.plan("law_poset")
            .cut(POSET_CUT)
            .route(Route.local(Span.at()))
            .create();

    /**
     * The map-unwrap plan — cut the stream lift ({@code role => map}): the transparent map unwraps its value.
     */
    private static final Cut MAP_CUT = Cut.role(MAP_INST_TID.name());
    private static final Plan MAP_UNWRAP_PLAN = PlanBuilder.plan("form_map_unwrap")
            .cut(MAP_CUT)
            .route(Route.local(Span.at()))
            .create();

    /**
     * The absorbing plan — cut an absorbing op ({@code law => [absorbing]}): everything before it is dead code.
     */
    private static final Cut ABSORBING_CUT = Cut.law(Law.absorbing);
    private static final Plan ABSORBING_PLAN = PlanBuilder.plan("law_absorbing")
            .cut(ABSORBING_CUT)
            .route(Route.local(Span.at()))
            .create();

    /**
     * The derivation plan — cut a derived op ({@code role => derivation}): fold its primitive composition back
     * to the derived operation.
     */
    private static final Cut DERIVATION_CUT = Cut.role(DERIVATION);
    private static final Plan DERIVATION_PLAN = PlanBuilder.plan("derivation_contraction")
            .cut(DERIVATION_CUT)
            .route(Route.local(Span.at()))
            .create();

    /**
     * The rewrite-name → plan registry — the bridge the path's out-edges look up when a rewrite rule has a
     * declarative plan ({@code law} or {@code cut}/{@code route}) rather than a bare lambda. A Plan is now the {@code plan::T} rec
     * itself, so there is no serialization step: the value is handed out as-is.
     */
    private static final Map<String, Plan> PLANS = new LinkedHashMap<>();

    static {
        PLANS.put(SHARD_PLAN.name(), SHARD_PLAN);
        PLANS.put(ORDER_PLAN.name(), ORDER_PLAN);
        PLANS.put(UNIT_PLAN.name(), UNIT_PLAN);
        PLANS.put(INVOLUTION_PLAN.name(), INVOLUTION_PLAN);
        PLANS.put(IDEMPOTENT_PLAN.name(), IDEMPOTENT_PLAN);
        PLANS.put(MONOIDIC_PLAN.name(), MONOIDIC_PLAN);
        PLANS.put(INVERSE_PLAN.name(), INVERSE_PLAN);
        PLANS.put(BRANCH_COLLAPSE_PLAN.name(), BRANCH_COLLAPSE_PLAN);
        PLANS.put(BRANCH_INLINE_PLAN.name(), BRANCH_INLINE_PLAN);
        PLANS.put(POSET_PLAN.name(), POSET_PLAN);
        PLANS.put(MAP_UNWRAP_PLAN.name(), MAP_UNWRAP_PLAN);
        PLANS.put(ABSORBING_PLAN.name(), ABSORBING_PLAN);
        PLANS.put(DERIVATION_PLAN.name(), DERIVATION_PLAN);
    }

    /**
     * The plan registered under a rewrite rule's name, or null when the rule is still a bare lambda.
     */
    public static Plan planOf(final String name) {
        return PLANS.get(name);
    }

/**
     * The {@code /rewrite} registrations — the law-driven rules the category ships, each its
     * {@code docWrap(rewriter(rewriteTID(name)), doc)}, plus the design note for the deferred
     * graph-driven family below the unit law.
     */
    public static Lst rules() {
        // The additive witness of the code ring is read off its algebra (object[code].law) inside each rule's
        // lambda — at rewrite time, when the object::T machinery is live — never named in the rule body:
        // that is the thin law-indexing. A null witness (the type declares no additive structure) degrades a
        // normalizer to the identity.
        return lst(
                        /* =====================================================================================
                         * (4) THE GRAPH/RELATION-DRIVEN FAMILY — next slice, deliberately NOT wired yet.
                         *
                         * Every rewrite below reads a single instruction's own declaration (its theory roles
                         * and process laws). The family after it reads an instruction's RELATION to the other
                         * types in the category — the metadata catInstSet already computes but the rewriter
                         * does not consume yet:
                         *
                         *   analysis.inverse   the declared opposing edge f^-1  (f . f^-1 = 1)
                         *   class              endo / iso / auto / mono / epi / section / retraction
                         *   position           duplicate / ambiguous / incomparable / coupling / isochain / retract
                         *   orbit              the reversible-core component of the endpoint
                         *   the cast subgraph  the as-edges, their refinements, and `implicit()`'s synthesized casts
                         *
                         * Candidate rules, each sound only under the named class/position guard:
                         *
                         *   inverse_pair_elision  x.as(B).as(A) |-> x        when as?B<=A . as?A<=B is `coupling`
                         *                                                    (a true two-sided inverse -- NOT when the
                         *                                                    round trip is lossy: `retract`/`isochain`)
                         *   cast_fusion           x.as(A).as(B) |-> x.as(B)  when B.refines(A) (the intermediate
                         *                                                    re-tag is the identity; `reaches`/`refines`
                         *                                                    already decide it)
                         *   endo_cast_drop        x.as(X::T) |-> x           when x is already X::T (an endo edge)
                         *   section_cancellation  r . s |-> e                when `clazz` says r is a `retraction`
                         *                                                    and s a `section` (a split mono/epi pair)
                         *   derived_cancellation  div(mult(a,b), b) |-> a    composed from `derivation` + `inverse`
                         *
                         * Two things gate this family, and they are why it is a separate slice:
                         *
                         *   1. it needs the CAST SUBGRAPH (`graph()`, `check()`, `implicit()`), not one inst. The
                         *      guard is a whole-graph query, so the rule is not a local list filter like the ones
                         *      below -- it has to be driven from `catAudit.check()`/`Finding`s, not from
                         *      `code.insts()`.
                         *   2. the dual direction -- `derivation_expansion` (minus |-> plus . neg,
                         *      div |-> mult . inv) -- must NOT be registered beside `derivation_contraction`.
                         *      The two alternate forever, so no length-stable rewriter stage can settle on a
                         *      form (the shipped `fixpoint_rewriter` would never close its stable window), and
                         *      which form wins becomes nondeterministic.
                         *      Expansion is a LOWERING policy (for a target that lacks the primitive), so it
                         *      belongs behind an explicit lowering flag/plan, never in the default rewrite set.
                         * ===================================================================================== */

                        // (1) the unit law, for EVERY theory instance the operand's type declares: op(id) |-> e,
                        // plus a bare identity operation (code's id) which is the identity morphism.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("theory_unit_removal"),
                                code -> PlanInterpreter.lower(UNIT_PLAN, code)), "the unit law of every theory the operand's type models: an operation applied to its own identity drops out, \\(\\mathrm{op}(\\mathrm{id}) \\leadsto \\varepsilon\\), and a bare identity operation is the identity morphism. e.g. \\(x + 0 \\leadsto x\\), \\(x \\cdot 1 \\leadsto x\\), \\(x \\cdot \\langle\\rangle \\leadsto x\\), \\(x \\cdot \\mathrm{id} \\leadsto x\\), \\(x \\cdot . \\leadsto x\\)"),

                        // ============ (1b/1c) THE BRANCH-SPECIFIC WARM-UP — SUPERSEDED, KEPT IN COMMENT FOR THE SIDE-BY-SIDE ============
                        // The two bodies below have moved into the law-indexed normalizers repeatedTerms / oneTerm
                        // (inst-agnostic: the additive op is the witness read off the algebra — object[code].law,
                        // ring.add / add_group.op, resolved inside each rule by additiveWitness at rewrite time).
                        // The registrations that follow are the same two rules at the same rule addresses;
                        // re-enable these to compare against the specific version (with the generic pair
                        // registered, that would double-fire the same passes).
                        // docWrap(InstSet.Helper.rewriter(rewriteTID("branch_collapse"),
                        //         code -> {
                        //             final List<Inst> insts = code.insts();
                        //             boolean changed = false;
                        //             final List<Inst> out = new ArrayList<>(insts.size());
                        //             for (final Inst inst : insts) {
                        //                 if (!BRANCH_INST_TID.equals(inst.tid().basePath()) || 2 > inst.args().count()) {
                        //                     out.add(inst);
                        //                     continue;
                        //                 }
                        //                 final List<Obj> reps = new ArrayList<>();
                        //                 final List<cInt> sums = new ArrayList<>();
                        //                 for (final Obj arg : inst.args().elements().toList()) {
                        //                     int slot = -1;
                        //                     for (int j = 0; j < reps.size(); j++) {
                        //                         if (sameMember(reps.get(j), arg)) {
                        //                             slot = j;
                        //                             break;
                        //                         }
                        //                     }
                        //                     if (-1 == slot) {
                        //                         reps.add(arg);
                        //                         sums.add(multiplicity(arg));
                        //                     } else
                        //                         sums.set(slot, sums.get(slot).plus(multiplicity(arg)));
                        //                 }
                        //                 if (reps.size() < inst.args().count()) {
                        //                     changed = true;
                        //                     final List<Obj> merged = new ArrayList<>(reps.size());
                        //                     for (int i = 0; i < reps.size(); i++)
                        //                         merged.add(cInt.ONE().equals(sums.get(i)) ? reps.get(i) : coeff(reps.get(i), sums.get(i)));
                        //                     out.add(inst.args(lst(merged)));
                        //                 } else
                        //                     out.add(inst);
                        //             }
                        //             return changed ? code(out) : code;
                        //         }), "the additive like-term law ... (see repeatedTerms)"),
                        //
                        // docWrap(InstSet.Helper.rewriter(rewriteTID("branch_inline"),
                        //         code -> {
                        //             final List<Inst> insts = code.insts();
                        //             boolean changed = false;
                        //             final List<Inst> out = new ArrayList<>(insts.size());
                        //             for (final Inst inst : insts) {
                        //                 if (BRANCH_INST_TID.equals(inst.tid().basePath()) && 1 == inst.args().count()) {
                        //                     final Obj member = absorb(inst.arg(0), inst.c());
                        //                     changed = true;
                        //                     if (member.isCall())
                        //                         out.addAll(member.asCall().insts());
                        //                     else
                        //                         out.add((Inst) map_(member).tryToInst());
                        //                 } else
                        //                     out.add(inst);
                        //             }
                        //             return changed ? code(out) : code;
                        //         }), "the single-addend law ... (see oneTerm)"),

                        // (1b) the additive normalizer, law-indexed — the thin generalization of the warm-up above:
                        // the moves are statements about the LAW, not about an inst. For the additive op S of the
                        // algebra being rewritten,
                        //
                        //     like terms      S(x, x, ..., x)  ->  S(x{n})    (equality coefficient-blind: the confluence)
                        //
                        // S is the witness read off object[code].law — ring.add / add_group.op, which here resolves
                        // to branch — so the body (repeatedTerms / oneTerm) knows nothing of branch; the same body
                        // pointed at object[int].law would run on plus with no body change.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("branch_collapse"),
                                code -> PlanInterpreter.lower(BRANCH_COLLAPSE_PLAN, code)),
                                "the additive like-term law, law-indexed: equal members of any occurrence of the algebra's additive op collapse to the single member with the summed coefficients -- on the code ring (S read from \\(\\mathrm{object}[code].law\\), \\(S = branch\\)) \\(\\mathrm{branch}(+2, +4, +2) \\leadsto \\mathrm{branch}(+2\\{2\\}, +4)\\) -- the additive normal form the code ring's distributivity law consumes"),

                        docWrap(InstSet.Helper.rewriter(rewriteTID("branch_inline"),
                                code -> PlanInterpreter.lower(BRANCH_INLINE_PLAN, code)),
                                "the one-term sum law, law-indexed: an occurrence of the algebra's additive op with a single member is that member added to nothing -- \\(\\mathrm{a} \\cdot \\mathrm{branch}(b) \\cdot \\mathrm{c} \\leadsto \\mathrm{a} \\cdot b \\cdot \\mathrm{c}\\), the frame's own coefficient folded into the member, \\(\\mathrm{branch}\\{c\\}(b) \\leadsto b\\{c\\}\\) -- a non-call member unwraps as the map that emits it, \\(\\mathrm{branch}(x) \\leadsto \\mathrm{map}(x)\\)"),

                        // (2a) the involution law: an adjacent pair of the SAME self-inverse operation is the
                        // identity -- neg.neg, reverse.reverse, not.not, conj.conj, inv.inv.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("theory_involution"),
                                code -> PlanInterpreter.lower(INVOLUTION_PLAN, code)), "the involution law \\(f \\cdot f \\leadsto \\varepsilon\\) (period two) for any operation whose declared process laws include \\(\\mathrm{involution}\\) -- \\(\\mathrm{neg} \\cdot \\mathrm{neg} \\leadsto \\varepsilon\\), \\(\\mathrm{reverse} \\cdot \\mathrm{reverse} \\leadsto \\varepsilon\\), \\(\\mathrm{not} \\cdot \\mathrm{not} \\leadsto \\varepsilon\\)"),

                        // (2b) the idempotent law: an adjacent pair of the SAME idempotent operation is that
                        // operation -- ucase.ucase, zero.zero, one.one, an idempotent join (bool or/and).
                        docWrap(InstSet.Helper.rewriter(rewriteTID("law_idempotent"),
                                code -> PlanInterpreter.lower(IDEMPOTENT_PLAN, code)), "the idempotent law \\(f \\cdot f \\leadsto f\\) for any operation whose declared process laws include \\(\\mathrm{idempotent}\\) -- \\(\\mathrm{ucase} \\cdot \\mathrm{ucase} \\leadsto \\mathrm{ucase}\\), \\(\\mathrm{zero} \\cdot \\mathrm{zero} \\leadsto \\mathrm{zero}\\)"),

                        // (2c) the absorbing law: an absorbing operation swallows the chain it is applied to
                        // (f . g = f), so every operation before it is dead code.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("law_absorbing"),
                                code -> PlanInterpreter.lower(ABSORBING_PLAN, code)), "the absorbing law \\(f \\circ g = f\\): an operation whose declared process laws include \\(\\mathrm{absorbing}\\) is a constant of the chain, so everything before it is dead code -- \\(\\ldots \\cdot g \\cdot \\mathrm{zero} \\leadsto \\ldots \\cdot \\mathrm{zero}\\), \\(x + 3 \\cdot \\mathrm{zero} \\leadsto x \\cdot \\mathrm{zero}\\)"),

                        // (2d) the monoidic law on a fold: a fold of a fold is the fold (reducing one element is
                        // the identity of the reduction), so an adjacent identical reducer/join collapses.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("law_monoidic"),
                                code -> PlanInterpreter.lower(MONOIDIC_PLAN, code)), "the monoidic law on a fold (\\(\\mathrm{reducer}\\)/\\(\\mathrm{join}\\) form): a fold of a fold is the fold, \\(f \\cdot f \\leadsto f\\) -- \\(\\mathrm{sum} \\cdot \\mathrm{sum} \\leadsto \\mathrm{sum}\\), \\(\\mathrm{merge} \\cdot \\mathrm{merge} \\leadsto \\mathrm{merge}\\)"),


                        // (2f) the poset law: compose adjacent comparisons of the same order -- the stricter
                        // bound wins, so the two filters are one filter.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("law_poset"),
                                code -> PlanInterpreter.lower(POSET_PLAN, code)), "the poset law \\(x > a \\wedge x > b \\iff x > \\max(a, b)\\): adjacent comparisons of the same ordered type compose to the stricter bound -- \\(\\mathrm{gt}(a) \\cdot \\mathrm{gt}(b) \\leadsto \\mathrm{gt}(\\max(a, b))\\), \\(\\mathrm{lt}(a) \\cdot \\mathrm{lt}(b) \\leadsto \\mathrm{lt}(\\min(a, b))\\)"),

                        // (3a) inverse cancellation: an adjacent declared inverse pair with equal operands
                        // cancels to the identity -- the group law g . g^-1 = 1, read off Entry.inverse.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("inverse_cancellation"),
                                code -> PlanInterpreter.lower(INVERSE_PLAN, code)), "the inverse law \\(f \\cdot f^{-1} \\leadsto \\varepsilon\\) on the declared opposing edge: an adjacent inverse pair with equal operands cancels -- \\(x + 3 - 3 \\leadsto x\\), \\(x \\cdot 2 \\div 2 \\leadsto x\\)"),

                        // (5) the stream lift: `map` is transparent on the value it wraps, so map(f) |-> f.
                        // This stays WITH map (its shape {?} -> {?} also matches `is`, so a shape-only
                        // generalization would unwrap a predicate); one rule covers both map(f) |-> f and the
                        // nesting map(map(f)) |-> map(f) (the outer unwrap exposes the inner map, next pass).
                        docWrap(InstSet.Helper.rewriter(rewriteTID("form_map_unwrap"),
                                code -> PlanInterpreter.lower(MAP_UNWRAP_PLAN, code)), "the stream lift \\(\\mathrm{map}\\) is the identity on a scalar, so a mapping of an instruction unwraps: \\(\\mathrm{map}(f) \\leadsto f\\), which also collapses \\(\\mathrm{map}(\\mathrm{map}(f)) \\leadsto \\mathrm{map}(f)\\)"),

                        // (3b) derivation contraction: fold a derived instruction's primitive composition back
                        // to the derived operation -- plus . neg |-> minus, mult . inv |-> div.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("derivation_contraction"),
                                code -> PlanInterpreter.lower(DERIVATION_PLAN, code)), "folds a derived instruction's primitive composition back to the derived operation, from the type's declared \\(\\mathrm{derivation}\\): \\(\\mathrm{plus} \\cdot \\mathrm{neg} \\leadsto \\mathrm{minus}\\), \\(\\mathrm{mult} \\cdot \\mathrm{inv} \\leadsto \\mathrm{div}\\)"),

                        // (6) the distribution kernel -- Thm 19 of Stream Ring Theory, registered as a rewrite:
                        // (a+b)·c = (a·c + b·c)·c for a monoidic, commutative reducer. The seed bulk is factored
                        // over the machine's peers, the elementwise (floatable) run is replicated per factor with
                        // a local partial reduce, and the reducer is reapplied once more behind a barrier at home:
                        //
                        //   {1,2,3}.plus(2).sum()
                        //     |->  [barrier(2).sum(), {1,3}.plus(2).sum(), {2}.plus(2).sum()]==[to(), to(<a>), to(<b>)]
                        //
                        // to() with no arg is "stay"; to(<uri>) ships its summand to that peer. barrier(k) declares
                        // its dom coefficient (the partial count) -- the SwarmProcessor side fills in both.
                        //
                        // POLICY: the guard requires live remote peers, so on a lone machine the rule is the
                        // identity and the default schedule is unchanged. Whether a NETWORKED machine should
                        // auto-distribute every eligible chain at compile time, or only via an explicit
                        // >>law_shard edge, is policy (cf. derivation_expansion above) -- gate shardTargets()
                        // behind a plan flag / q-param to pull it out of the default fixpoint schedule.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("law_shard"),
                                code -> PlanInterpreter.lower(SHARD_PLAN, code)), "the distribution kernel \\((a+b) \\cdot c = (a \\cdot c + b \\cdot c) \\cdot c\\) (Thm 19): factor the seed bulk over the roster, replicate the floatable run per factor with a local partial reduce, reapply the reducer behind a barrier -- \\(\\{1,2,3\\}.f.\\Sigma \\leadsto [\\mathrm{barrier}.\\Sigma,\\ \\{1,3\\}.f.\\Sigma,\\ \\{2\\}.f.\\Sigma] == [\\mathrm{to}(),\\ \\mathrm{to}(a),\\ \\mathrm{to}(b)]\\)"),
                        docWrap(InstSet.Helper.rewriter(rewriteTID("order_shard"),
                                code -> PlanInterpreter.lower(ORDER_PLAN, code)), "the sort distribution: factor the seed bulk over the roster, each peer orders its shard locally, and the master k-merges the sorted shards behind the barrier")
        );
    }

    /**
     * The rewrite's address — {@code /m/math/cat/inst/rewrite/<name>}.
     */
    private static fURI rewriteTID(final String name) {
        return CAT_ISA_TID.extend(INST).extend(REWRITE).extend(name);
    }
}
