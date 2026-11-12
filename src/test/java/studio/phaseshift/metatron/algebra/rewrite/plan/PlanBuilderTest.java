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

package studio.phaseshift.metatron.algebra.rewrite.plan;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.mach.machInstSet;
import studio.phaseshift.metatron.isa.m.math.cat.catInstSet;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * The one-rule contract ({@code plan::T = [name, cut?, where]}): a plan with a {@code cut} is a semantic rule, a
 * plan with no {@code cut} is a distribution rule. {@link PlanBuilder#create()} accepts a cut-less plan with exactly
 * one {@link Placement} (the distribution rule), keeps {@code cut.laws ⊇ obligations} when a cut is present, and reads
 * a decision back from its axes. The plan vocabulary alone: what law_shard does with its plan, and what every other
 * rule does, is mInstSetRewriteTest's.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class PlanBuilderTest extends AbstractInstSetTest {

    public PlanBuilderTest() {
        super(catInstSet::new);
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
        assertEquals(1, readBack.where().get(1).hop());
    }

    /**
     * A plan rec written before the singular spelling carries {@code hops} in its decision; {@link Placement#of}
     * migrates it to {@code hop} on read — the same compat read the old {@code target/url} keys get — so an old plan
     * still states its budget.
     */
    @Test
    public void testLegacyHopsMigratedOnRead() {
        final Plan legacy = Plan.of(ObjmtronSerializer.parse("[name=><law_shard>,where=>[[shape=><tree>,hops=>2]]]").asRec());
        assertEquals(2, legacy.where().get(0).hop(),
                "a pre-singular decision still answers its hop budget");
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
        assertTrue(shard.test(machInstSet.MACH_PLAN_TYPE), "a cut plan is a plan::T: " + shard);
        final Plan move = PlanBuilder.plan("move_star").where(Placement.tree(1, Span.at())).create();
        assertTrue(move.test(machInstSet.MACH_PLAN_TYPE), "a cut-less plan is a plan::T: " + move);
    }

    /**
     * {@code bind} is the cut-less operator: a plan with a cut is {@code lower}'s, so {@code bind} declines it — the
     * code passes through unchanged.
     */
    @Test
    public void testBindDeclinesCutPlan() {
        final Plan shard = PlanInterpreter.SHARD_PLAN;
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

    /**
     * THE DECISION STATED IN THE AXES — {@link PlanBuilder#place}, {@link PlanBuilder#hop} and {@link PlanBuilder#split}
     * bind to the {@code where} that follows them, and the assembled plan is still a {@code plan::T} that reads back:
     * the tail placement keeps its own decision (the home place alone), while the fan-out states where its shares go,
     * how deep the tree may run, and what counts as one share.
     */
    @Test
    public void testTheDecisionInTheAxes() {
        final Plan plan = PlanBuilder.plan("axes_shard")
                .cut(Cut.reducer(Law.monoidic, Law.commutative))
                .where(Placement.local(Span.at(), Span.after()))
                .place(PlaceSet.peer()).hop(1, cInt.ANY()).split(Partition.stream())
                .where(Placement.tree(1, Span.seed().partition(), Span.before().floatable().withoutSeed(), Span.at()))
                .create();
        assertTrue(plan.test(machInstSet.MACH_PLAN_TYPE), "an axes-declared decision is a plan::T: " + plan);
        assertEquals(2, plan.where().size(), "the two decisions are kept apart");
        // the origin's tail: the place set is local alone, so nothing ships and no law is demanded
        final Placement tail = plan.where().get(0);
        assertEquals(Placement.Shape.LOCAL, tail.shape());
        assertEquals(List.of("local"), tail.place().names());
        assertEquals(Set.of(), tail.obligations());
        // the fan-out: the dialed set, one level, strided
        final Placement fanOut = plan.where().get(1);
        assertEquals(List.of("peer"), fanOut.place().names());
        assertEquals(1, fanOut.hopAxis().height());
        assertEquals(cInt.ANY(), fanOut.hopAxis().arity());
        assertEquals(Partition.stream(), fanOut.split());
        assertEquals(Placement.Shape.TREE, fanOut.shape(), "one level over the dialed set is the tree's radius one");
        assertEquals(Set.of(Law.monoidic, Law.commutative), fanOut.obligations());
        assertNull(fanOut.target(), "the dialed set names no single address");
    }

    /**
     * THE AXES ARE THE AUTHORITY THE VALIDATION READS — {@link PlanBuilder#create()} derives the decision's law
     * obligations from them, so the same cut is refused for an off-machine recombine and admitted where the axes say
     * the merge is sequential (a one-child hop), bucketed (a keyed split), or never regrouped at all (the home place).
     * A fan-out's placement is the one that carries a SEED span, which is what the bind expects.
     */
    @Test
    public void testTheAxesAreTheAuthorityTheValidationReads() {
        assertThrows(MTronException.class, () -> PlanBuilder.plan("shard").cut(Cut.reducer(Law.monoidic))
                        .place(PlaceSet.peer()).hop(1, cInt.ANY()).split(Partition.stream())
                        .where(Placement.peers(Span.seed().partition(), Span.at())).create(),
                "an off-machine recombine demands commutativity too");
        assertNotNull(PlanBuilder.plan("shuffle").cut(Cut.reducer(Law.monoidic))
                        .place(PlaceSet.peer()).hop(1, cInt.ANY()).split(Partition.key())
                        .where(Placement.peers(Span.seed().partition(), Span.at())).create(),
                "a keyed partition folds per bucket, so monoidic alone is enough");
        assertNotNull(PlanBuilder.plan("chain").cut(Cut.reducer(Law.monoidic))
                        .place(PlaceSet.peer()).hop(2, cInt.ONE())
                        .where(Placement.peers(Span.seed().partition(), Span.at())).create(),
                "a one-child hop is a sequential forward: order is preserved, commutativity is not demanded");
        assertNotNull(PlanBuilder.plan("home").cut(Cut.reducer())
                        .place(PlaceSet.local()).hop(0, cInt.ANY()).split(Partition.stream())
                        .where(Placement.peers(Span.seed().partition(), Span.at())).create(),
                "a single home place regroups nothing, so the cut demands no law");
        assertThrows(MTronException.class, () -> PlanBuilder.plan("dangling").cut(Cut.reducer())
                        .place(PlaceSet.peer()).create(),
                "an axis with no placement to bind is refused, never silently dropped");
    }

    /**
     * AN {@code index} PARTITION IS DERIVED LIKE A FAN-OUT — it parts no <em>data</em> out, but the code still ships to
     * the range holders and their partials still recombine at home through the same combiner, so the gate demands the
     * off-machine merge's two laws (it fails closed). The same cut is admitted once the plan declares them.
     */
    @Test
    public void testTheIndexPartitionIsDerivedLikeAFanOut() {
        assertThrows(MTronException.class, () -> PlanBuilder.plan("indices").cut(Cut.reducer(Law.monoidic))
                        .place(PlaceSet.peer()).hop(1, cInt.ANY())
                        .split(Partition.of(Partition.Kind.INDEX, cInt.ANY()))
                        .where(Placement.peers(Span.seed().partition(), Span.at())).create(),
                "an index split still recombines the range holders' partials at home, so monoidic is not enough");
        assertNotNull(PlanBuilder.plan("indices").cut(Cut.reducer(Law.monoidic, Law.commutative))
                        .place(PlaceSet.peer()).hop(1, cInt.ANY())
                        .split(Partition.of(Partition.Kind.INDEX, cInt.ANY()))
                        .where(Placement.peers(Span.seed().partition(), Span.at())).create(),
                "the index split is a fan-out's recombine, so the two laws admit it");
    }

    /**
     * A REGROUPING PLACE SET BINDS TO THE FAN-OUT, NEVER TO THE GATHER — {@link PlanBuilder#where} refuses to hand the
     * origin's tail ({@code AT}/{@code POST}, no {@code SEED} span) a place set that regroups, because that would
     * silently turn the plan's gather into a lane: {@code law_shard} would re-apply its cut off-machine and never
     * recombine. The same axes bound to the placement that carries the SEED span are admitted.
     */
    @Test
    public void testARegroupingPlaceSetBindsToAFanOut() {
        assertThrows(MTronException.class, () -> PlanBuilder.plan("tail_as_lane").cut(Cut.reducer(Law.monoidic, Law.commutative))
                        .place(PlaceSet.peer()).hop(1, cInt.ANY()).split(Partition.stream())
                        .where(Placement.local(Span.at(), Span.after())).create(),
                "a peer place set bound to the tail turns the gather into a fan-out: refused");
        assertThrows(MTronException.class, () -> PlanBuilder.plan("tail_as_lanes").cut(Cut.reducer(Law.monoidic, Law.commutative))
                        .place(PlaceSet.of(f("local"), cInt.of(4L))).hop(1, cInt.ANY())
                        .where(Placement.local(Span.at(), Span.after())).create(),
                "four home lanes are no gather either: refused");
        assertNotNull(PlanBuilder.plan("fan_out").cut(Cut.reducer(Law.monoidic, Law.commutative))
                        .where(Placement.local(Span.at(), Span.after()))
                        .place(PlaceSet.peer()).hop(1, cInt.ANY()).split(Partition.stream())
                        .where(Placement.tree(1, Span.seed().partition(), Span.at())).create(),
                "the tail keeps its own decision and the fan-out takes the axes");
    }

    /**
     * LAW_SHARD SPEAKS THE AXES ALONE (C9) — the assembled rec carries no derived {@code shape} and no {@code target}:
     * the shape is a consequence of the axes and the target is the place set's sole address, so the plan states three
     * axes per decision and nothing they could contradict. The tail stays a TAIL (the home place alone, no SEED span),
     * and the fan-out reads back as exactly the decision the emitter has always seen — which is why every emitted
     * layout is byte-identical: the emitter is a function of these axes.
     */
    @Test
    public void testLawShardSpeaksTheAxesAlone() {
        final Plan shard = PlanInterpreter.SHARD_PLAN;
        assertNotNull(shard, "no plan registered under law_shard");
        assertTrue(shard.test(machInstSet.MACH_PLAN_TYPE), "a plan stated in the axes is a plan::T: " + shard);
        final String rec = shard.toString();
        assertFalse(rec.contains("shape="), "no derived shape token in the assembled rec: " + rec);
        assertFalse(rec.contains("target="), "no derived target in the assembled rec: " + rec);
        // the origin's tail: a tail, not a lane
        final Placement tail = shard.where().get(0);
        assertEquals(Placement.Shape.LOCAL, tail.shape(), "the tail is the home place: " + tail);
        assertEquals(List.of("local"), tail.place().names(), "and only the home place: " + tail);
        assertEquals(Set.of(), tail.obligations(), "nothing is regrouped, so the cut demands no law");
        assertTrue(tail.spans().stream().noneMatch(span -> Span.Kind.SEED == span.kind()),
                "the tail carries no SEED span: " + tail);
        // the fan-out: the decision the emitter has always read
        final Placement fanOut = shard.where().get(1);
        assertEquals(Placement.Shape.TREE, fanOut.shape(), "one level over the dialed set is the tree: " + fanOut);
        assertEquals(PlaceSet.peer(), fanOut.place(), "the dialed set: " + fanOut);
        assertEquals(Hop.tree(1), fanOut.hopAxis(), "one level, the local out-degree: " + fanOut);
        assertEquals(Partition.stream(), fanOut.split(), "the stride partition: " + fanOut);
        assertEquals(Set.of(Law.monoidic, Law.commutative), fanOut.obligations(), "an off-machine recombine: " + fanOut);
    }

    /**
     * AN AXES-ONLY DECISION IS A {@code plan::T} — the derived {@code shape} token is optional now (C9), because a rec
     * that states {@code place}/{@code hop}/{@code split} states the decision itself. The legacy encoding is still
     * admitted for one cycle, which is the read side {@link Placement#of(studio.phaseshift.metatron.isa.m.type.Rec)}
     * migrates.
     */
    @Test
    public void testAnAxesOnlyDecisionIsAPlanT() {
        assertTrue(ObjmtronSerializer.parse(
                                "[name=><axes_only>,where=>[[place=>[peer],hop=>1,split=>stream,span=>[[span=><at>]]]]]")
                        .test(machInstSet.MACH_PLAN_TYPE),
                "a decision stated in the axes alone is a plan::T");
        assertTrue(ObjmtronSerializer.parse(
                                "[name=><legacy_only>,where=>[[shape=><tree>,span=>[[span=><at>]]]]]")
                        .test(machInstSet.MACH_PLAN_TYPE),
                "and the legacy encoding is still read for one cycle");
    }

    /**
     * {@code bind} declines a cut-less plan whose target's scheme resolves no dialer — the identity, symmetric with
     * {@code lower}'s no-match.
     */
    @Test
    public void testBindDeclinesUnresolvableScheme() {
        final Plan move = PlanBuilder.plan("move").where(Placement.chain(f("foo://nowhere"), Span.at())).create();
        assertNull(move.cut());
        final Code code = ObjmtronSerializer.parse("start(5).plus(1)").asCode();
        assertEquals(code, PlanInterpreter.bind(move, code), "bind declines a target whose scheme no dialer resolves");
    }
}
