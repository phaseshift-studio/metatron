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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.math.cat.Law;
import studio.phaseshift.metatron.isa.m.math.cat.catInstSet;
import studio.phaseshift.metatron.isa.m.math.cat.catRewrite;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.util.MTronException;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * The one-rule contract ({@code plan::T = [name, cut?, where]}): a plan with a {@code cut} is a semantic rule, a
 * plan with no {@code cut} is a distribution rule. {@link PlanBuilder#create()} accepts a cut-less plan with exactly
 * one {@link Placement} (the distribution rule), keeps {@code cut.laws ⊇ obligations} when a cut is present, and the
 * {@code route → where} rename is inert — the emitted code is byte-for-byte the code the pre-rename plans lowered to.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class PlanBuilderTest extends AbstractInstSetTest {

    public PlanBuilderTest() {
        super(catInstSet::new);
    }

    @BeforeAll
    public static void importAllInstSets() {
        InstSet.importInstSet(f("#"));
    }

    @BeforeEach
    public void installCategoryCompiler() {
        Machine.current().compiler(CatTheoryCompiler.single());
    }

    /**
     * A cut shipped to a non-local target's {@code AT} span and re-applied at home must declare {@code monoidic} and
     * {@code commutative} (Thm 19) — {@link PlanBuilder#create()} refuses it when it does not, and admits the same
     * non-commutative cut where no order-free merge is demanded (local).
     */
    @Test
    public void testDistributedCutRefusedWithoutLaws() {
        assertThrows(MTronException.class, () ->
                PlanBuilder.plan("shard").cut(Cut.reducer()).where(Placement.tree(1, Span.at())).create());
        assertThrows(MTronException.class, () ->
                PlanBuilder.plan("shard").cut(Cut.reducer(Law.monoidic)).where(Placement.tree(1, Span.at())).create());
        // the same non-commutative cut is accepted when it stays local — no regrouping, no order-free merge demanded
        assertNotNull(PlanBuilder.plan("local").cut(Cut.reducer(Law.monoidic)).where(Placement.local(Span.at())).create());
    }

    /**
     * A cut-less plan is the distribution rule: accepted, with a null cut and exactly one relocation decision.
     */
    @Test
    public void testCutLessDistributionAccepted() {
        final Plan plan = PlanBuilder.plan("move_star").where(Placement.tree(1, Span.at())).create();
        assertNull(plan.cut());
        assertEquals(1, plan.where().size());
    }

    /**
     * A distribution rule (no cut) has exactly one decision — zero and two are both refused.
     */
    @Test
    public void testCutLessNeedsExactlyOnePlacement() {
        assertThrows(MTronException.class, () -> PlanBuilder.plan("move_star").create());
        assertThrows(MTronException.class, () -> PlanBuilder.plan("move_star")
                .where(Placement.local(Span.at()))
                .where(Placement.local(Span.after()))
                .create());
    }

    /**
     * The {@code where} list round-trips through the plan rec.
     */
    @Test
    public void testWhereListPreserved() {
        final Plan plan = PlanBuilder.plan("law_shard")
                .cut(Cut.reducer(Law.monoidic, Law.commutative))
                .where(Placement.local(Span.at(), Span.after()))
                .where(Placement.tree(1, Span.seed().partition(), Span.before().floatable().withoutSeed(), Span.at()))
                .create();
        final Plan readBack = Plan.of(plan);
        assertEquals(2, readBack.where().size());
        assertEquals(Placement.Shape.LOCAL, readBack.where().get(0).shape());
        assertEquals(Placement.Shape.TREE, readBack.where().get(1).shape());
        assertEquals(1, readBack.where().get(1).hops());
    }

    /**
     * A plan — with or without a cut — is a valid {@code plan::T}: the rec predicate admits the {@code where} key.
     */
    @Test
    public void testPlanIsAPlanT() {
        final Plan shard = PlanBuilder.plan("law_shard")
                .cut(Cut.reducer(Law.monoidic, Law.commutative))
                .where(Placement.local(Span.at()))
                .where(Placement.tree(1, Span.at()))
                .create();
        assertTrue(shard.test(catInstSet.PLAN_TYPE), "a cut plan is a plan::T: " + shard);
        final Plan move = PlanBuilder.plan("move_star").where(Placement.tree(1, Span.at())).create();
        assertTrue(move.test(catInstSet.PLAN_TYPE), "a cut-less plan is a plan::T: " + move);
    }

    /**
     * THE EMITTED-CODE SNAPSHOT — the rename is inert. Each row lowers one of {@code catRewrite}'s migrated plans
     * (built through the new {@code where}/{@code Placement} vocabulary) over a representative chain and asserts the
     * emitted code is byte-for-byte the code the pre-rename plan lowered to. {@code law_shard} is the peer-gated
     * exception, covered by {@code DistributedRewriteTransportTest}/{@code TreeShardTransportTest}; the two branch
     * normalizers reach their snapshot at fixpoint in {@code catInstSetTest}. Each row: the rule % the chain % the
     * lowered chain.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "theory_unit_removal     % 5.plus(0)                          % start(5)",
            "theory_involution       % 5.neg().neg()                       % start(5)",
            "law_idempotent          % \"ab\".ucase().ucase()                % start(\"ab\").ucase()",
            "law_absorbing           % 5.plus(3).zero()                    % start(5).zero()",
            "law_monoidic            % {1,2,3}.sum().sum()                 % start({1,2,3}).sum()",
            "inverse_cancellation    % 5.plus(3).minus(3)                  % start(5)",
            "form_map_unwrap         % 5.map(neg)                          % start(5).neg()",
            "derivation_contraction  % 6.plus(3.neg())                     % start(6).minus(3)",
            "law_poset               % {1,2,3,4,5}.is(gt(1)).is(gt(3))     % start({1,2,3,4,5}).is(gt(3))",
            "branch_inline           % 1.branch(+2)                        % start(1).plus(2)",
            "branch_collapse         % 1.branch(+2,+2)                     % start(1).branch(plus{2}(2))",
    }, delimiter = '%')
    public void testEmittedCodeIsUnchangedByRename(final String rule, final String source, final String expected) {
        final Plan plan = catRewrite.planOf(rule);
        assertNotNull(plan, "no plan registered under " + rule);
        final Code rewritten = PlanInterpreter.lower(plan, ObjmtronSerializer.parse(source).asCode());
        assertEquals(ObjmtronSerializer.parse(expected).asCode(), rewritten,
                "the rename changed the emitted code of " + rule);
    }

    /**
     * {@code bind} is the cut-less operator: a plan with a cut is {@code lower}'s, so {@code bind} declines it — the
     * code passes through unchanged.
     */
    @Test
    public void testBindDeclinesCutPlan() {
        final Plan shard = catRewrite.planOf("law_shard");
        assertNotNull(shard);
        final Code code = ObjmtronSerializer.parse("start({1,2,3,4}).plus(1).sum()").asCode();
        assertEquals(code, PlanInterpreter.bind(shard, code), "bind declines a plan with a cut");
    }

    /**
     * {@code bind} applies a cut-less plan: until Phase 6 (move) ships the chain/star emission, the distribution rule
     * is the identity — the code stands where it stood.
     */
    @Test
    public void testBindAppliesCutLessPlan() {
        final Plan move = PlanBuilder.plan("move_star").where(Placement.local(Span.at())).create();
        assertNull(move.cut());
        final Code code = ObjmtronSerializer.parse("start(5).plus(1)").asCode();
        assertEquals(code, PlanInterpreter.bind(move, code), "a cut-less plan is the identity until Phase 6");
    }
}
