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

import studio.phaseshift.metatron.algebra.rewrite.plan.PlanInterpreter;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Lst;

import static studio.phaseshift.metatron.Tokens.INST;
import static studio.phaseshift.metatron.Tokens.REWRITE;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mach.type.compiler.rewriter.PlanRewriter.*;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.CAT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;

/**
 * The law-driven rewrite machinery of the category — the {@code /rewrite} rules and the equation
 * tests and helpers each rule's guard is built from.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class catRewrite {

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

                // (1) the unit law is /m's theory_unit_removal: the theories are /m's, so it runs on the base path
                // too, the category imported or not.

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
                        "the additive like-term law, law-indexed: equal members of any occurrence of the algebra's additive op collapse to the single member with the summed coefficients -- on the code ring (S read from \\(\\mathrm{object}[\\mathrm{code}].\\mathrm{law}\\), \\(S = \\mathrm{branch}\\)) \\(\\mathrm{branch}(+2, +4, +2) \\leadsto \\mathrm{branch}(+2\\{2\\}, +4)\\) -- the additive normal form the code ring's distributivity law consumes"),

                docWrap(InstSet.Helper.rewriter(rewriteTID("branch_inline"),
                                code -> PlanInterpreter.lower(BRANCH_INLINE_PLAN, code)),
                        "the one-term sum law, law-indexed: an occurrence of the algebra's additive op with a single member is that member added to nothing -- \\(\\mathrm{a} \\cdot \\mathrm{branch}(b) \\cdot \\mathrm{c} \\leadsto \\mathrm{a} \\cdot b \\cdot \\mathrm{c}\\), the frame's own coefficient folded into the member, \\(\\mathrm{branch}\\{c\\}(b) \\leadsto b\\{c\\}\\) -- a non-call member unwraps as the map that emits it, \\(\\mathrm{branch}(x) \\leadsto \\mathrm{map}(x)\\)"),

                // (2) the involution, idempotent and monoidic laws are /m's: one rewrite per rule (law_involution,
                // law_idempotent, law_monoidic), each carrying its rules as its own argument.

                // (2c) the absorbing law is /m's: its rewrite, law_absorbing, states {+}#·op ⇝ op, a spanned run of
                // instructions before an absorbing op, where the op's gate admits it (pure, one obj per obj, the carrier kept).

                // (2f) the poset law is /m's: its rewrite, law_poset, states is(op(a))·is(op(b)) ⇝ is(op(b)) where the
                // comparison orders b past a, stated both ways round and for filter too.

                // (3) inverse cancellation and derivation contraction are /m's: their rules are already data
                // (group_theory's rules, each instruction's derivation), so /m applies them on the base path too.

                // (5) the stream lift is /m's too: its rewrite, form_map_unwrap, unwraps a mapping of a mapper to the mapper
                // (map(#::T@f) ⇝ #::T@f, where f's operation declares the mapper form), with map_nest beside it.

                // (6) the distribution kernel -- Thm 19 of Stream Ring Theory, registered as a rewrite:
                // (a+b)·r = (a·r + b·r)·combiner(r) for a reducer whose partials recombine through a
                // monoidic, commutative fold -- r itself (sum, prod), the fold its derivation names
                // (count -> sum, order(s) -> kmerge(s)), or pointwise for a keyed group. The seed bulk is
                // factored over the machine's dialed peers, the elementwise (floatable) run is replicated per
                // factor with a local partial reduce, and the combiner is applied once more at home:
                //
                //   {1,2,3}.plus(2).sum()
                //     |->  branch(<a> => start({1,3}).plus(2).sum?hop=0(), <b> => start({2}).plus(2).sum?hop=0()).sum()
                //
                // branch keys each part by the place it runs (<~> home, a peer's authority); a remote lane ships its
                // program uncompiled to its place's eval, every lane runs on its own virtual thread, and the branch
                // gathers the partials for the combiner. A peer with hop budget left re-plans its part over its own
                // peers (the tree).
                //
                // POLICY: the guard requires dialed peers and a hop budget (the plan's tree placement, or ?hop=n
                // on the chain; ?hop=0 opts a fold out), so on a lone machine the rule is the identity and the
                // default schedule is unchanged. Whether a NETWORKED machine should auto-distribute every
                // eligible chain at compile time, or only via an explicit >>law_shard edge, is policy (cf.
                // derivation_expansion above).
                docWrap(InstSet.Helper.rewriter(rewriteTID("law_shard"),
                        code -> PlanInterpreter.lower(SHARD_PLAN, code)), "the distribution kernel \\((a+b) \\cdot r = (a \\cdot r + b \\cdot r) \\cdot \\mathrm{combiner}(r)\\) (Thm 19): factor the seed bulk over the roster, replicate the floatable run per factor with a local partial reduce, recombine the partials behind a barrier through the reducer's combiner -- computed, never declared: \\(\\Sigma\\) and \\(\\Pi\\) recombine through themselves, \\(\\mathrm{count}\\) through \\(\\Sigma\\), \\(\\mathrm{order}(s)\\) through \\(\\mathrm{kmerge}(s)\\), \\(\\mathrm{group}([k \\Rightarrow h])\\) pointwise through \\(\\Sigma\\) then \\(\\mathrm{combiner}(h)\\) per key -- \\(\\{1,2,3\\}.f.\\mathrm{count} \\leadsto \\mathrm{branch}(a \\Rightarrow \\{1,3\\}.f.\\mathrm{count},\\ b \\Rightarrow \\{2\\}.f.\\mathrm{count}).\\Sigma\\)")
        );
    }

    /**
     * The rewrite's address — {@code /m/math/cat/inst/rewrite/<name>}.
     */
    private static fURI rewriteTID(final String name) {
        return CAT_ISA_TID.extend(INST).extend(REWRITE).extend(name);
    }
}
