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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import java.util.*;
import java.util.function.BiPredicate;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.CAT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.math.cat.catLiveness.carrier;
import static studio.phaseshift.metatron.isa.m.math.cat.catLiveness.declares;
import static studio.phaseshift.metatron.isa.m.math.cat.catLiveness.mapArgument;
import static studio.phaseshift.metatron.isa.m.math.cat.catLiveness.sameOp;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.map_;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;


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
                                code -> {
                                    final List<Inst> insts = code.insts();
                                    if (0 == insts.size())
                                        return code;
                                    final Map<Type, Rec> laws = new HashMap<>();
                                    final List<Inst> kept = insts.stream()
                                            .filter(i -> !isUnit(i, laws) && !isIdentity(i, laws))
                                            .toList();
                                    return kept.size() == insts.size() ? code : code(kept);
                                }), "the unit law of every theory the operand's type models: an operation applied to its own identity drops out, \\(\\mathrm{op}(\\mathrm{id}) \\leadsto \\varepsilon\\), and a bare identity operation is the identity morphism. e.g. \\(x + 0 \\leadsto x\\), \\(x \\cdot 1 \\leadsto x\\), \\(x \\cdot \\langle\\rangle \\leadsto x\\), \\(x \\cdot \\mathrm{id} \\leadsto x\\), \\(x \\cdot . \\leadsto x\\)"),

                        // (1b) the ring-axiom warmup — additive like-term collapse: branch is the code ring's
                        // additive op (mInstSetLawTable), so equal members of a branch are additive repetitions of
                        // the same member, x + x = 2·x — and the pair collapses to the single member with the
                        // summed coefficients: branch(+2,+4,+2) |-> branch(+2{2},+4). Members are compared as
                        // instructions (sameOp plus operands and refinement — MInst equals minus the positional vid,
                        // parsed siblings sharing everything but their own address), and the survivors are the
                        // additive normal form the follow-on distributivity rewrite consumes.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("branch_collapse"),
                                code -> {
                                    final List<Inst> insts = code.insts();
                                    boolean changed = false;
                                    final List<Inst> out = new ArrayList<>(insts.size());
                                    for (final Inst inst : insts) {
                                        if (!BRANCH_INST_TID.equals(inst.tid().basePath()) || 2 > inst.args().count()) {
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
                                }), "the additive like-term law \\(x + x \\leadsto x\\{2\\}\\) on the branch, the code ring's additive op: equal members of a branch collapse to the single member with the summed coefficients -- \\(\\mathrm{branch}(+2, +4, +2) \\leadsto \\mathrm{branch}(+2\\{2\\}, +4)\\) -- the additive normal form the code ring's distributivity law consumes"),

                        // (1c) the single-addend law — a branch whose members have come to one is that member,
                        // added to nothing, so its frame unwraps: a.branch(b).c |-> a.b.c, the branch's own
                        // coefficient folded into the member as it goes (branch{c}(b) |-> b multiplied by c).
                        // It runs on its own (a lone hand-written branch, branch(+1), has no identical pair to
                        // collapse), and it runs on what branch_collapse left behind — a.collapsed to a single
                        // member is collapsed AND inlined: a.branch(b,b).c |-> a.b{2}.c. The member is spliced
                        // in by its insts (a call's list, its own singleton when it is an inst); a non-call
                        // member is the map that emits it, so the inlining is stream-transparent either way.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("branch_inline"),
                                code -> {
                                    final List<Inst> insts = code.insts();
                                    boolean changed = false;
                                    final List<Inst> out = new ArrayList<>(insts.size());
                                    for (final Inst inst : insts) {
                                        if (BRANCH_INST_TID.equals(inst.tid().basePath()) && 1 == inst.args().count()) {
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
                                }), "the single-addend law \\(\\mathrm{a} \\cdot \\mathrm{branch}(b) \\cdot \\mathrm{c} \\leadsto \\mathrm{a} \\cdot b \\cdot \\mathrm{c}\\): a branch of one member is that member added to nothing, so the frame unwraps, its own coefficient folded into the member -- after \\(\\mathrm{branch\\_collapse}\\), \\(\\mathrm{a} \\cdot \\mathrm{branch}(b, b) \\cdot \\mathrm{c} \\leadsto \\mathrm{a} \\cdot b\\{2\\} \\cdot \\mathrm{c}\\), and \\(\\mathrm{branch\\{c\\}}(b) \\leadsto b\"{c}\\) -- a non-call member unwraps as the map that emits it, \\(\\mathrm{a} \\cdot \\mathrm{branch}(x) \\cdot \\mathrm{c} \\leadsto \\mathrm{a} \\cdot \\mathrm{map}(x) \\cdot \\mathrm{c}\\)"),

                        // (2a) the involution law: an adjacent pair of the SAME self-inverse operation is the
                        // identity -- neg.neg, reverse.reverse, not.not, conj.conj, inv.inv.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("theory_involution"),
                                code -> {
                                    final List<Inst> insts = code.insts();
                                    if (2 > insts.size())
                                        return code;
                                    final Type carrier = carrier(code);
                                    final List<Inst> collapsed = collapsePairs(insts,
                                            (a, b) -> same(a, b) && isInvolution(a, carrier));
                                    return collapsed.size() == insts.size() ? code : code(collapsed);
                                }), "the involution law \\(f \\cdot f \\leadsto \\varepsilon\\) (period two) for any operation whose declared process laws include \\(\\mathrm{involution}\\) -- \\(\\mathrm{neg} \\cdot \\mathrm{neg} \\leadsto \\varepsilon\\), \\(\\mathrm{reverse} \\cdot \\mathrm{reverse} \\leadsto \\varepsilon\\), \\(\\mathrm{not} \\cdot \\mathrm{not} \\leadsto \\varepsilon\\)"),

                        // (2b) the idempotent law: an adjacent pair of the SAME idempotent operation is that
                        // operation -- ucase.ucase, zero.zero, one.one, an idempotent join (bool or/and).
                        docWrap(InstSet.Helper.rewriter(rewriteTID("law_idempotent"),
                                code -> {
                                    final List<Inst> insts = code.insts();
                                    if (2 > insts.size())
                                        return code;
                                    final Type carrier = carrier(code);
                                    final List<Inst> collapsed = collapseDuplicates(insts,
                                            (a, b) -> same(a, b) && declares(a, carrier, Law.idempotent));
                                    return collapsed.size() == insts.size() ? code : code(collapsed);
                                }), "the idempotent law \\(f \\cdot f \\leadsto f\\) for any operation whose declared process laws include \\(\\mathrm{idempotent}\\) -- \\(\\mathrm{ucase} \\cdot \\mathrm{ucase} \\leadsto \\mathrm{ucase}\\), \\(\\mathrm{zero} \\cdot \\mathrm{zero} \\leadsto \\mathrm{zero}\\)"),

                        // (2c) the absorbing law: an absorbing operation swallows the chain it is applied to
                        // (f . g = f), so every operation before it is dead code.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("law_absorbing"),
                                code -> {
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
                                }), "the absorbing law \\(f \\circ g = f\\): an operation whose declared process laws include \\(\\mathrm{absorbing}\\) is a constant of the chain, so everything before it is dead code -- \\(\\ldots \\cdot g \\cdot \\mathrm{zero} \\leadsto \\ldots \\cdot \\mathrm{zero}\\), \\(x + 3 \\cdot \\mathrm{zero} \\leadsto x \\cdot \\mathrm{zero}\\)"),

                        // (2d) the monoidic law on a fold: a fold of a fold is the fold (reducing one element is
                        // the identity of the reduction), so an adjacent identical reducer/join collapses.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("law_monoidic"),
                                code -> {
                                    final List<Inst> insts = code.insts();
                                    if (2 > insts.size())
                                        return code;
                                    final Type carrier = carrier(code);
                                    final List<Inst> collapsed = collapseDuplicates(insts,
                                            (a, b) -> same(a, b) && isFold(a, carrier) && declares(a, carrier, Law.monoidic));
                                    return collapsed.size() == insts.size() ? code : code(collapsed);
                                }), "the monoidic law on a fold (\\(\\mathrm{reducer}\\)/\\(\\mathrm{join}\\) form): a fold of a fold is the fold, \\(f \\cdot f \\leadsto f\\) -- \\(\\mathrm{sum} \\cdot \\mathrm{sum} \\leadsto \\mathrm{sum}\\), \\(\\mathrm{merge} \\cdot \\mathrm{merge} \\leadsto \\mathrm{merge}\\)"),


                        // (2f) the poset law: compose adjacent comparisons of the same order -- the stricter
                        // bound wins, so the two filters are one filter.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("law_poset"),
                                code -> {
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
                                }), "the poset law \\(x > a \\wedge x > b \\iff x > \\max(a, b)\\): adjacent comparisons of the same ordered type compose to the stricter bound -- \\(\\mathrm{gt}(a) \\cdot \\mathrm{gt}(b) \\leadsto \\mathrm{gt}(\\max(a, b))\\), \\(\\mathrm{lt}(a) \\cdot \\mathrm{lt}(b) \\leadsto \\mathrm{lt}(\\min(a, b))\\)"),

                        // (3a) inverse cancellation: an adjacent declared inverse pair with equal operands
                        // cancels to the identity -- the group law g . g^-1 = 1, read off Entry.inverse.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("inverse_cancellation"),
                                code -> {
                                    final List<Inst> insts = code.insts();
                                    if (2 > insts.size())
                                        return code;
                                    final List<Inst> out = new ArrayList<>(insts.size());
                                    boolean changed = false;
                                    final Type carrier = carrier(code);
                                    for (int i = 0; i < insts.size(); i++) {
                                        if (i + 1 < insts.size() && inversePair(insts.get(i), insts.get(i + 1), carrier)) {
                                            changed = true;
                                            i++;
                                        } else
                                            out.add(insts.get(i));
                                    }
                                    return changed ? code(out) : code;
                                }), "the inverse law \\(f \\cdot f^{-1} \\leadsto \\varepsilon\\) on the declared opposing edge: an adjacent inverse pair with equal operands cancels -- \\(x + 3 - 3 \\leadsto x\\), \\(x \\cdot 2 \\div 2 \\leadsto x\\)"),

                        // (5) the stream lift: `map` is transparent on the value it wraps, so map(f) |-> f.
                        // This stays WITH map (its shape {?} -> {?} also matches `is`, so a shape-only
                        // generalization would unwrap a predicate); one rule covers both map(f) |-> f and the
                        // nesting map(map(f)) |-> map(f) (the outer unwrap exposes the inner map, next pass).
                        docWrap(InstSet.Helper.rewriter(rewriteTID("form_map_unwrap"),
                                code -> {
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
                                }), "the stream lift \\(\\mathrm{map}\\) is the identity on a scalar, so a mapping of an instruction unwraps: \\(\\mathrm{map}(f) \\leadsto f\\), which also collapses \\(\\mathrm{map}(\\mathrm{map}(f)) \\leadsto \\mathrm{map}(f)\\)"),

                        // (3b) derivation contraction: fold a derived instruction's primitive composition back
                        // to the derived operation -- plus . neg |-> minus, mult . inv |-> div.
                        docWrap(InstSet.Helper.rewriter(rewriteTID("derivation_contraction"),
                                code -> {
                                    final List<Derivation> derivations = LawTable.derivations();
                                    if (derivations.isEmpty())
                                        return code;
                                    List<Inst> out = code.insts();
                                    for (final Derivation d : derivations)
                                        out = contract(out, d);
                                    return code(out);
                                }), "folds a derived instruction's primitive composition back to the derived operation, from the type's declared \\(\\mathrm{derivation}\\): \\(\\mathrm{plus} \\cdot \\mathrm{neg} \\leadsto \\mathrm{minus}\\), \\(\\mathrm{mult} \\cdot \\mathrm{inv} \\leadsto \\mathrm{div}\\)"),

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
                                catShard::shard), "the distribution kernel \\((a+b) \\cdot c = (a \\cdot c + b \\cdot c) \\cdot c\\) (Thm 19): factor the seed bulk over the roster, replicate the floatable run per factor with a local partial reduce, reapply the reducer behind a barrier -- \\(\\{1,2,3\\}.f.\\Sigma \\leadsto [\\mathrm{barrier}.\\Sigma,\\ \\{1,3\\}.f.\\Sigma,\\ \\{2\\}.f.\\Sigma] == [\\mathrm{to}(),\\ \\mathrm{to}(a),\\ \\mathrm{to}(b)]\\)")
        );
    }

    /**
     * The rewrite's address — {@code /m/math/cat/inst/rewrite/<name>}.
     */
    private static fURI rewriteTID(final String name) {
        return CAT_ISA_TID.extend(INST).extend(REWRITE).extend(name);
    }

    /**
     * Two chain elements are the same operation: same op address and same operands. This is what makes a
     * law-driven fold sound — {@code neg·neg} collapses, {@code neg·inv} does not.
     */
    private static boolean same(final Inst a, final Inst b) {
        return sameOp(a, b) && a.args().equals(b.args());
    }

    /**
     * The branch's own coefficient folded into its single member — a branch of coefficient c applied to a
     * single member multiplies that member's multiplicity by c, so the frame unwraps and the member comes out
     * carrying it. A code member anchors the coefficient at its head inst (the one place the resolution
     * pipeline preserves it); an inst or a non-call member takes the multiplication itself.
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
     * Two branch members being the same additive element — {@link #same(Inst, Inst)} with the refined endpoints
     * compared too, minus the positional vid (parsed siblings share everything but their own address, which is why
     * a raw equals cannot group them). A code member is the same when its instruction list is pairwise the same.
     * A non-call member is the same when it is coefficient-less equal ({@code 10} ≡ {@code 10{2}} as additive
     * elements — they are the same term at different multiplicities). The coefficient is deliberately OUT of the
     * test everywhere in the test — it is the multiplicity the law sums, and comparing it would leave the like
     * terms uncollapsed (the {@code {3}10} + {@code {2}10} case must sum to {@code {5}10}, not refuse to match).
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
     * The multiplicity an additive member contributes — the coefficient the law sums. For an inst member it
     * is the inst's own coefficient ({@code plus{2}(2)}); for a code member it rides the chain's first inst
     * ({@code map{2}(2).neg()}), the form the language's own coefficient-mark produces — the code's own tid is
     * re-forged in resolution, so a coefficient there would be dropped.
     */
    private static cInt multiplicity(final Obj member) {
        if (member.isCode() && !member.asCode().insts().isEmpty())
            return member.asCode().insts().getFirst().c();
        return member.c();
    }

    /**
     * The member carrying its summed coefficient, at the anchor {@link #multiplicity(Obj)} reads it from —
     * the inst's tid, or the head of the chain for a code (the first step's coefficient propagates the stream
     * multiplicity, so the collapse is stream-transparent).
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

    /**
     * Collapse adjacent pairs the predicate accepts to nothing — the equation {@code f·f ↦ ε} shared by the
     * involution, idempotent and monoidic-fold laws.
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
     * Collapse adjacent duplicate pairs to a single instance — the equation {@code f·f ↦ f} shared by the
     * idempotent law and by a monoidic fold (a reduction of one element is that element).
     */
    private static List<Inst> collapseDuplicates(final List<Inst> insts, final BiPredicate<Inst, Inst> match) {
        final List<Inst> result = new ArrayList<>();
        int i = 0;
        while (i < insts.size()) {
            if (i + 1 < insts.size() && match.test(insts.get(i), insts.get(i + 1))) {
                result.add(insts.get(i));
                i += 2; // f·f => f — keep one
            } else
                result.add(insts.get(i++));
        }
        return result;
    }

    /**
     * The involution test: the operation's own declared {@code involution} law, or the carrier's declared
     * additive-group inverse (the role path that keeps a type whose ops are not in the process-law table —
     * e.g. {@code cmplx}'s {@code neg} — collapsing as before).
     */
    private static boolean isInvolution(final Inst inst, final Type carrier) {
        if (declares(inst, carrier, Law.involution))
            return true;
        final Inst inv = TheoryHelper.invInst(carrier);
        return !inv.isNoObj() && inv.test(inst);
    }

    /**
     * The unit test: an operation applied to its own theory identity — {@code op(id)} — for every theory
     * instance the operand's type models. The theory roles are read from {@code object::T.law}, so this one
     * rule covers {@code x + 0}, {@code x · 1}, {@code x · <>} (uri), {@code x · .} (machine), {@code x · id}
     * (code) and every user-declared monoid/group/ring/… The law blocks are memoized per rewrite pass by the
     * caller's map, since a type's theories are shared by every instruction over it.
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
     * A fold: the declared n-tid coefficient shape gathers many to one ({@code reducer} or {@code join}) and
     * the operation is declared {@code monoidic} — the shape a repeated fold is idempotent on. The chain
     * instruction is retyped with its declared address so the shape is legible before resolution.
     */
    private static boolean isFold(final Inst inst, final Type carrier) {
        final LawTable.Declared declared = LawTable.declared(inst.tid().basePath(), carrier);
        if (null == declared)
            return false;
        final Inst.Form form = Inst.Form.of(inst.tid(declared.tid()));
        return (Inst.Form.reducer == form || Inst.Form.join == form) && declares(declared, Law.monoidic);
    }

    /**
     * Compose two adjacent comparisons of the same ordered type to the stricter single bound — the poset law
     * {@code x > a ∧ x > b ⇔ x > max(a, b)}. Null when the pair is not composable (not declared
     * {@code poset}, different ops, or non-literal operands), so the caller keeps the original pair.
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
     * Adjacent inverse pair with equal operands — the group law {@code f·f⁻¹ = 1}. The pairing is the edge's
     * declared {@code inverse} (either direction), so it holds for plus/minus, mult/div, and every declared
     * inverse pair, and only when the operands cancel exactly.
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
        // source.test(pattern) here would let a scalar pass a nested-composition pattern (e.g. the `2`
        // of plus(2) "matching" the map(B).neg() arg of the minus derivation)
        if (pattern.isInst() || pattern.isCode() || source.isInst() || source.isCode())
            return false;
        return source.test(pattern);
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
            } else {
                out.add(inst);
            }
        }
        return out;
    }
}
