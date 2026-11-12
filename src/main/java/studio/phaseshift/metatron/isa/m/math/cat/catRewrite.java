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
import static studio.phaseshift.metatron.algebra.rewrite.plan.PlanInterpreter.SHARD_PLAN;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.CAT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;

/**
 * The category's {@code /rewrite} registrations: law_shard, the distribution rule, and the design note for the
 * graph-driven family the category may own next. Every law rewrite is /m's.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class catRewrite {

    /**
     * The {@code /rewrite} registrations — the category's rules, each its
     * {@code docWrap(rewriter(rewriteTID(name)), doc)}, plus the design note for the deferred graph-driven family.
     */
    public static Lst rules() {
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

                // EVERY LAW REWRITE THE CATEGORY ONCE REGISTERED IS /m's (2026-11-12): the unit, involution, idempotent,
                // monoidic, absorbing, poset, inverse and derivation laws, the map unwrap, and the code ring's normal forms
                // (branch_factor, branch_collapse, branch_inline). The theories are /m's, so the rules run on the base path,
                // the category imported or not; their bodies are Law.Helper's. The category registers only law_shard.

                // the distribution kernel -- Thm 19 of Stream Ring Theory, registered as a rewrite:
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
