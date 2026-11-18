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

package studio.phaseshift.metatron.isa.m.mach.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.algebra.rewrite.plan.Env;
import studio.phaseshift.metatron.algebra.rewrite.plan.PlanInterpreter;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.HOP;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.CAT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;

/**
 * {@code ?place} AT THE EMITTER (C5 of {@code distributed-qparams-implementation.md}): the place set a fold states —
 * the plan's distributing set, overridden by a {@code ?place} on any instruction of the chain — resolved into
 * <em>lanes</em> of one branch, one per distinct place, the home place first.
 *
 * <p><b>The oracle is the law, not the layout.</b> Every row that emits also asserts the value, because a place axis
 * is result-preserving (Thm 19): {@code distribute(c) ≡ c}. The lane <em>structure</em> is the other half — the home
 * place's key is {@code <~>} (the lane at distance zero), a peer's is its authority, and a place with demand n is one
 * key weighted {@code ?split={n}part}.
 *
 * <p><b>Hermetic where it can be.</b> The {@code local} rows resolve against a synthetic {@link Env} — the seam C4
 * opened — because a test VM has no listening authority, so {@code Places.self()} is {@code noobj} unless the env
 * states one. The rows that need a real shipped lane dial a {@code loop://} peer (the
 * {@link PlacementEquivalenceTest} pattern: no forked process, no new cluster) and take the live peer map into the
 * same synthetic env.
 *
 * <p>The counts are the design's: a share is a weight, so {@code [local,peer]} is two lanes and
 * {@code [local,local,local,local]} is ONE home lane weighted four — the lanes case, a peerless machine sharding,
 * keyed by the one place it has.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class PlaceLaneTest extends AbstractMetatronTest {

    /**
     * A stated home authority — what makes {@code local} a place in a synthetic env.
     */
    private static final fURI MINE = f("loop://mantis:8555");
    /**
     * The two loop peers the duplicate-designator row names by address.
     */
    private static final fURI GINGER = f("loop://ginger:8555");
    private static final fURI BOOGER = f("loop://booger:8555");
    /**
     * The one loop peer the default and S rows fan out over.
     */
    private static final fURI PEER = f("loop://localhost:8555");

    @BeforeEach
    void setup() {
        // the boot default: every type check on except code_resolve (Marko, 2026-11-18)
        InstSet.importInstSet(MATH_ISA_TID);
        InstSet.importInstSet(CAT_ISA_TID);
        Machine.current().compiler(CatTheoryCompiler.single());
        Typer.CHECKS.forEach(flag -> Typer.Helper.set(flag, Typer.defaultsOn(flag)));
    }

    @AfterEach
    void teardown() {
        Machine.current().network().drop(PEER);
        Machine.current().network().drop(GINGER);
        Machine.current().network().drop(BOOGER);
    }

    /**
     * S — {@code ?place=[local,peer]}: the master is a place at distance zero. Each row: the fold % its value. The
     * layout is the same mechanism with one more lane: the master's chain is still {@code branch·<combiner>},
     * lane 0 is the home key {@code <~>} and lane 1 travels to the dialed peer. The value is the local fold's, because
     * a share is a share wherever it runs.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "plus(1).sum() % 27",
            "count()       % 6",
    }, delimiter = '%')
    void testTheMasterIsAPlaceAtDistanceZero(final String tail, final int expected) {
        Network.Helper.connect(Machine.current().network(), PEER);
        final Code emitted = lower(homeAndPeer(), "start?place=[local,peer]({1,2,3,4,5,6})." + tail);
        final DistributedSweep.Layout layout = DistributedSweep.layout(emitted);
        assertEquals(2, layout.lanes().size(), "one lane per share: " + emitted);
        assertTrue(layout.lane(0).local(), "the home place's share comes first: " + emitted);
        assertEquals(PEER, layout.lane(1).authority(), "then the peers in resolution order: " + emitted);
        assertEquals(List.of(BRANCH_INST_TID, SUM_INST_TID),
                layout.ops(), "the master's chain is unchanged by the extra lane: " + emitted);
        assertEquals("0", layout.lane(0).budget(), "every lane is one level down: " + emitted);
        checkEquality(LOG, jnt(expected),
                evaluate(emitted).clone().selfVID(null), true);
    }

    /**
     * THE LANES — {@code ?place=[local,local,local,local]} on a <b>peerless</b> machine: four home shares, keyed by the
     * one place they share, so ONE home lane weighted {@code {4}part} takes every member of the seed, recombined at home
     * through the same combiner. The four shares no longer run as four concurrent lanes: a branch has one lane per key,
     * and a place is one key. The value is the local fold's. Each row: the fold % its value.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "plus(1).sum() % 44",
            "count()       % 8",
    }, delimiter = '%')
    void testFourHomeLanesOnAPeerlessMachine(final String tail, final int expected) {
        final Code emitted = lower(alone(), "start?place=[local,local,local,local]({1,2,3,4,5,6,7,8})." + tail);
        final DistributedSweep.Layout layout = DistributedSweep.layout(emitted);
        assertEquals(1, layout.lanes().size(), "a place with demand four is one key: " + emitted);
        assertTrue(layout.lane(0).local(), "a peerless machine's lane stays home: " + emitted);
        assertEquals("{4}part", layout.lane(0).split(), "the key is weighted by its four shares: " + emitted);
        assertEquals(8, layout.lane(0).part().size(), "the one weighted key is dealt the whole seed: " + emitted);
        checkEquality(LOG, jnt(expected),
                evaluate(emitted).clone().selfVID(null), true);
    }

    /**
     * DUPLICATES SUM — {@code ?place=[//ginger,//ginger,//booger]} over two loop peers: two lanes, ginger's weighted
     * two shares. The brace-free spelling of the same 2:1 weight {@code [{2}//ginger,//booger]} states, so the second
     * ginger is a share, not a starved duplicate. Each row: the fold % its value.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "plus(1).sum() % 27",
    }, delimiter = '%')
    void testDuplicateDesignatorsSumTheirShare(final String tail, final int expected) {
        Network.Helper.connect(Machine.current().network(), GINGER);
        Network.Helper.connect(Machine.current().network(), BOOGER);
        final Code emitted = PlanInterpreter.lower(PlanInterpreter.SHARD_PLAN,
                ObjmtronSerializer.parse("start?place=[//ginger,//ginger,//booger]({1,2,3,4,5,6})." + tail).asCode());
        final DistributedSweep.Layout layout = DistributedSweep.layout(emitted);
        assertEquals(List.of(GINGER, BOOGER), layout.routes(), "one lane per place: " + emitted);
        assertEquals(List.of("{2}part", "part"), layout.lanes().stream().map(DistributedSweep.Lane::split).toList(),
                "ginger takes two shares and booger one: " + emitted);
        checkEquality(LOG, jnt(expected),
                evaluate(emitted).clone().selfVID(null), true);
    }

    /**
     * THE DEFAULT — no {@code ?place}: the plan's own distributing set (the dialed peers), one lane per peer, no home
     * lane, and the master chain every literal seed emits ({@code branch·<combiner>}). This is C0's pin, re-read where
     * the place axis lands, so a change to the default would be a diff here and not only in the suites that pin it too.
     */
    @Test
    void testTheDefaultEmitsTheLayoutItAlwaysDid() {
        Network.Helper.connect(Machine.current().network(), PEER);
        final Code emitted = PlanInterpreter.lower(PlanInterpreter.SHARD_PLAN,
                ObjmtronSerializer.parse("start({1,2,3,4,5,6}).plus(1).sum()").asCode());
        final DistributedSweep.Layout layout = DistributedSweep.layout(emitted);
        assertEquals(List.of(PEER), layout.routes(), "the dialed set, and no home lane: " + emitted);
        assertEquals(List.of(BRANCH_INST_TID, SUM_INST_TID),
                layout.ops(), "the default layout is the branch: " + emitted);
        checkEquality(LOG, jnt(27),
                evaluate(emitted).clone().selfVID(null), true);
    }

    /**
     * A PLACE SET THAT CANNOT BE READ IS REFUSED, NOT IGNORED — a decision the author stated and the emitter dropped
     * would compute somewhere other than the listing says. These are the shapes the object grammar leaves behind: a
     * bare non-designator ({@code abc}), a value the parser cannot read ({@code \x}), and a quoted {@code str} where a
     * value was required ({@code "ab"}). Each row: the stated value % the key the refusal must name.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "abc   % ?place=abc",
            "\\x   % ?place=\\x",
            "\"ab\" % ?place=\"ab\"",
    }, delimiter = '%')
    void testAnUnreadablePlaceSetIsRefused(final String stated, final String expected) {
        final Code code = ObjmtronSerializer.parse("start?place=" + stated + "({1,2,3,4}).plus(1).sum()").asCode();
        final MTronException refused = assertThrows(MTronException.class,
                () -> PlanInterpreter.apply(PlanInterpreter.SHARD_PLAN, code, alone()),
                "a place set that cannot be read must be refused, not ignored: " + stated);
        assertTrue(refused.getMessage().contains(expected),
                "the refusal names the key and the text it could not read: " + refused.getMessage());
    }

    /// /////////////////////////////////////////////////////////////////////////////////////
    /// THE HOP AXIS — height{arity}
    /// /////////////////////////////////////////////////////////////////////////////////////

    /**
     * THE ARITY CAPS THE FAN-OUT, AND THE FORM IS RESTATED, NEVER THE NUMBER (C6 of the plan). Over three loop peers
     * an uncapped budget takes three children; {@code {2}3} takes two, and the slave states {@code {2}2} — the same
     * arity with the height decremented — on its FOLD. A band restates as a band ({@code {2,4}3} ⇒ {@code {2,4}2}),
     * and a bare or {@code {*}} budget is the schedule that has always been: three children, and a leaf at zero. Each
     * row: the fold % the lanes one node takes % the budget the lane's fold states % the value.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "start({1,2,3,4,5,6,7,8}).plus(1).sum()            % 3 % 0       % 44",
            "start?hop=1({1,2,3,4,5,6,7,8}).plus(1).sum()      % 3 % 0       % 44",
            "start?hop={*}1({1,2,3,4,5,6,7,8}).plus(1).sum()   % 3 % 0       % 44",
            "start?hop={2}3({1,2,3,4,5,6,7,8}).plus(1).sum()   % 2 % {2}2    % 44",
            "start?hop={2,4}3({1,2,3,4,5,6,7,8}).plus(1).sum() % 3 % {2,4}2  % 44",
    }, delimiter = '%')
    void testTheArityCapsTheFanOutAndTheFormIsRestated(final String source, final int lanes, final String budget, final int expected) {
        connectAll();
        final Code emitted = lower(homeAndPeer(), source);
        final DistributedSweep.Layout layout = DistributedSweep.layout(emitted);
        assertEquals(lanes, layout.lanes().size(), "one lane per child, capped by the arity: " + emitted);
        assertEquals(budget, layout.lane(0).budget(), "the slave restates the same arity one height down: " + emitted);
        checkEquality(LOG, jnt(expected), evaluate(emitted).clone().selfVID(null), true);
    }

    /**
     * EVERY NODE TAKES AT MOST {@code w} CHILDREN — the cap is the axis, not the root's arithmetic: re-planning the
     * lane the root emitted answers two children again, with the form restated one height further down.
     */
    @Test
    void testTheArityCapsEveryNodeNotOnlyTheRoot() {
        connectAll();
        final DistributedSweep.Lane root = DistributedSweep.layout(lower(homeAndPeer(),
                "start?hop={2}3({1,2,3,4,5,6,7,8}).plus(1).sum()")).lane(0);
        assertEquals("{2}2", root.budget(), "the root states the form one height down: " + root.code());
        final Code replanned = Machine.current().compiler().rewriter().apply(root.code()).asCode();
        final DistributedSweep.Layout interior = DistributedSweep.layout(replanned);
        assertEquals(2, interior.lanes().size(), "an interior node takes two children too: " + replanned);
        assertEquals("{2}1", interior.lane(0).budget(), "and restates the form once more: " + replanned);
    }

    /**
     * THE BUDGET IS STATED ON THE LANE'S FIRST INSTRUCTION — the code that ships and re-plans says where its metadata
     * lives, so a human and a peer read the same place, and a leaf says {@code ?hop=0} instead of hiding the budget.
     * A copied instruction is not stripped of a budget it carries (the first statement wins); the authored budget here
     * is on the seed, which the lane rebuilds, so the head is the only statement. The master recombines with none of
     * its own.
     */
    @Test
    void testTheBudgetIsStatedOnTheLanesFirstInstruction() {
        connectAll();
        final Code emitted = lower(homeAndPeer(), "start?hop={2}3({1,2,3,4,5,6,7,8}).plus(1).sum()");
        final DistributedSweep.Lane lane = DistributedSweep.layout(emitted).lane(0);
        assertTrue(lane.code().insts().getFirst().tid().hasQ(HOP), "the head states the budget: " + lane.code());
        assertEquals("{2}2", lane.budget(), "the head states the restated form: " + lane.code());
        assertFalse(lane.code().insts().subList(1, lane.code().insts().size()).stream()
                        .anyMatch(inst -> inst.tid().hasQ(HOP)),
                "no copied instruction carries a budget: " + lane.code());
        assertFalse(emitted.insts().stream().anyMatch(inst -> inst.tid().hasQ(HOP)),
                "the master recombines with no budget of its own: " + emitted);
    }

    /**
     * THE IDENTITY — a height of zero and an arity of zero are the same point: no level and no branch means
     * {@code D(c) = c}, the original chain byte for byte (§9), and nothing ships. Each row: the whole expression.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "start?hop=0({1,2,3,4}).plus(1).sum()",
            "start?hop={0}3({1,2,3,4}).plus(1).sum()",
            "start?hop=[3,0]({1,2,3,4}).plus(1).sum()",
            "start?hop=0({1,2,3,4,5}).plus(1).sum().{0,3}plus(1).sum()",
    }, delimiter = '%')
    void testNoLevelOrNoBranchIsTheIdentity(final String source) {
        connectAll();
        final Code original = ObjmtronSerializer.parse(source).asCode();
        final Code emitted = PlanInterpreter.apply(PlanInterpreter.SHARD_PLAN, original, homeAndPeer());
        assertEquals(original.toString(), emitted.toString(), "the point is the original chain, byte for byte: " + emitted);
        assertTrue(DistributedSweep.layout(emitted).lanes().isEmpty(), "and nothing ships: " + emitted);
    }

    /**
     * THE CHAIN — arity one, the shape that did not exist before C6: each node hands its WHOLE part to exactly one
     * child, so the tree is a line as long as the height. Re-planning the one lane answers one lane again, one height
     * down ({@code [3,1] ⇒ [2,1] ⇒ [1,1]}), and the value is the undistributed fold's to the last link.
     */
    @Test
    void testAChainHandsOffToOneChildAtEachNode() {
        connectAll();
        final Code emitted = lower(homeAndPeer(), "start?hop=[3,1]({1,2,3,4,5,6,7,8}).plus(1).sum()");
        final DistributedSweep.Layout root = DistributedSweep.layout(emitted);
        assertEquals(1, root.lanes().size(), "arity one is a single child: " + emitted);
        assertEquals(8, root.lane(0).part().size(), "and that child takes the whole part: " + emitted);
        assertEquals("[2,1]", root.lane(0).budget(), "the chain restates its own form, one height down: " + emitted);
        final Code second = Machine.current().compiler().rewriter().apply(root.lane(0).code()).asCode();
        final DistributedSweep.Layout next = DistributedSweep.layout(second);
        assertEquals(1, next.lanes().size(), "the next node is a link of the same line: " + second);
        assertEquals("[1,1]", next.lane(0).budget(), "the line restates again: " + second);
        checkEquality(LOG, jnt(44), evaluate(emitted).clone().selfVID(null), true);
    }

    /// /////////////////////////////////////////////////////////////////////////////////////
    /// THE RUN PARTITION — a span range is an index set too
    /// /////////////////////////////////////////////////////////////////////////////////////

    /**
     * THE RUN PARTITION — a span range on an instruction is an index set, and with a singleton seed it is the only
     * multiplicity there is: each lane carries the whole seed and the run with its span narrowed to a WORK-weighted
     * subrange of the unroll index ({@code a^[m,n] = Σ a^k}), folds its unrolls through the cut so one partial travels
     * home, the branch recombines through the cut's combiner, and the value is the serial one. Each row: the chain % its
     * value.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "start?place=[local,peer](0).{0,4}plus(1).sum()          % 10",    // 0+1+2+3+4
            "start?place=[local,peer](0).{1,4}plus(1).sum()          % 10",    // 1+2+3+4
            "start?place=[local,peer](0).{0,4}plus(1).mult(10).sum() % 100",   // 10·(0+…+4)
    }, delimiter = '%')
    void testTheRunPartitionFactorsTheUnrollIndex(final String source, final int expected) {
        Network.Helper.connect(Machine.current().network(), PEER);
        final Code emitted = lower(homeAndPeer(), source);
        final DistributedSweep.Layout layout = DistributedSweep.layout(emitted);
        assertEquals(2, layout.lanes().size(), "the unrolls are factored over the two places: " + emitted);
        assertEquals(List.of(BRANCH_INST_TID, SUM_INST_TID), layout.ops(),
                "the branch recombines through the cut's combiner: " + emitted);
        assertTrue(layout.lanes().stream().allMatch(lane -> SUM_INST_TID.equals(lane.code().insts().getLast().tid().basePath())),
                "each lane folds its unrolls through the cut: " + emitted);
        assertEquals("0", layout.lane(0).budget(), "a leaf states 0 on its first instruction: " + emitted);
        checkEquality(LOG, jnt(expected), evaluate(emitted).clone().selfVID(null), true);
    }

    /**
     * THE RUN PARTITION SPLITS BY THE WORK OF A WALK (Marko, 2026-11-15): each lane walks its runs from the whole seed,
     * each step once, so a share {@code {a,b}} costs the {@code b} steps it walks and the {@code b - a + 1} runs it
     * emits, and the lane nearer the top takes fewer runs. Each row: the chain % the lanes' narrowed spans, home first.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "start?place=[local,peer](0).{0,4}plus(1).sum()   % 0..2|3..4",
            "start?place=[local,peer](0).{1,4}plus(1).sum()   % 1..3|4..4",
            "start?place=[local,peer](0).{0,9}plus(1).sum()   % 0..6|7..9",
    }, delimiter = '%')
    void testTheRunPartitionSplitsByTheWorkOfAWalk(final String source, final String expected) {
        Network.Helper.connect(Machine.current().network(), PEER);
        final DistributedSweep.Layout layout = DistributedSweep.layout(lower(homeAndPeer(), source));
        assertEquals(expected, spans(layout), "the shares of the unroll index: " + layout.emitted());
    }

    /**
     * THE CUTLESS RUN PARTITION — the branch IS the recombination: no combiner, the lanes' narrowed spans are the
     * additive terms, and the value is the unroll's formal sum.
     */
    @Test
    void testTheCutlessRunPartitionIsTheBranchItself() {
        Network.Helper.connect(Machine.current().network(), PEER);
        final Code emitted = lower(homeAndPeer(), "start?place=[local,peer](0).{0,4}plus(1)");
        final DistributedSweep.Layout layout = DistributedSweep.layout(emitted);
        assertEquals(2, layout.lanes().size(), "the unrolls are factored over the two places: " + emitted);
        assertEquals(List.of(BRANCH_INST_TID), layout.ops(), "the branch is the sum: " + emitted);
        checkEquality(LOG, ObjmtronSerializer.parse("{0,1,2,3,4}"),
                TransportRewriteHarness.evaluate(emitted).clone().selfVID(null), true);
    }

    /**
     * THE SEED AXIS WINS WHEN THE SEED HAS MEMBERS — the two multiplicities are one funnel, but v1 factors one axis per
     * application: a seed of two members is dealt two ways and each lane runs the whole span, so the value is
     * {@code 2·(0+…+4)}.
     */
    @Test
    void testTheSeedAxisIsFactoredBeforeTheRunAxis() {
        Network.Helper.connect(Machine.current().network(), PEER);
        final Code emitted = lower(homeAndPeer(), "start?place=[local,peer]({0,0}).{0,4}plus(1).sum()");
        final DistributedSweep.Layout layout = DistributedSweep.layout(emitted);
        assertEquals(2, layout.lanes().size(), "the two seed members are two lanes: " + emitted);
        assertEquals(List.of(BRANCH_INST_TID, SUM_INST_TID), layout.ops(), "the fold recombines at home: " + emitted);
        checkEquality(LOG, jnt(20), evaluate(emitted).clone().selfVID(null), true);
    }

    /**
     * WHERE THE RUN PARTITION STANDS STILL: a point span ({@code {3}} is a lone compose chain, not an index set) and an
     * open range ({@code +}: no last unroll to plan). Each row: the chain, unchanged.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "start?place=[local,peer](0).{3}plus(1).sum()",
            "start?place=[local,peer](0).{+}plus(1).sum()",
    }, delimiter = '%')
    void testTheRunPartitionLeavesAPointOrOpenSpanWhereItStands(final String source) {
        Network.Helper.connect(Machine.current().network(), PEER);
        final Code original = ObjmtronSerializer.parse(source).asCode();
        final Code emitted = lower(homeAndPeer(), source);
        checkEquality(LOG, original.tryToInst(), emitted.tryToInst(), true);
    }

    /**
     * A RUN PARTITION NEEDS A SECOND PLACE: one home lane is the code that already stands here, so a singleton place
     * set declines even when the span is a range.
     */
    @Test
    void testTheRunPartitionNeedsASecondPlace() {
        final Code original = ObjmtronSerializer.parse("start?place=[local](0).{0,4}plus(1).sum()").asCode();
        final Code emitted = PlanInterpreter.apply(PlanInterpreter.SHARD_PLAN, original, alone());
        checkEquality(LOG, original.tryToInst(), emitted.tryToInst(), true);
    }

    /// /////////////////////////////////////////////////////////////////////////////////////
    /// EVERY CUT PLANS THE RUN BEFORE IT
    /// /////////////////////////////////////////////////////////////////////////////////////

    /**
     * EVERY CUT SHIPS ITS RUN — a chain that folds more than once is planned cut by cut: each run ships through its own
     * branch, and its cut's combiner brings the computation home, where the next run starts. A fold answers one obj, so
     * the run after it ships the span range it carries. Over the three loop peers, the dialed set being the default
     * place. Each row: the chain % the master's ops % its value.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "start({1,2,3,4,5}).plus(1).sum().{0,3}plus(1).sum()                    % branch,sum,branch,sum            % 86",     // 20+21+22+23
            "start({1,2,3,4,5}).plus(1).sum().{0,3}plus(1).prod()                   % branch,sum,branch,prod           % 212520", // 20·21·22·23
            "start({1,2,3,4,5}).plus(1).sum().{0,3}plus(1).count()                  % branch,sum,branch,sum            % 4",
            "start({1,2,3,4,5}).plus(1).count().{0,3}mult(2).sum()                  % branch,sum,branch,sum            % 75",     // 5+10+20+40
            "start({1,2,3,4}).sum().{0,3}plus(1).sum()                              % branch,sum,branch,sum            % 46",     // 10+11+12+13
            "start(0).{0,4}plus(1).sum().{0,3}plus(1).sum()                         % branch,sum,branch,sum            % 46",     // 10, then 10+…+13
            "start({1,2,3,4,5}).plus(1).sum().{0,3}plus(1).sum().{0,1}plus(1).sum() % branch,sum,branch,sum,branch,sum % 173",    // 86+87
    }, delimiter = '%')
    void testEveryCutShipsItsRun(final String source, final String ops, final int expected) {
        connectAll();
        final Code emitted = lower(homeAndPeer(), source);
        assertEquals(List.of(ops.trim().split(",")), TransportRewriteHarness.ops(emitted).stream().map(fURI::name).toList(),
                "each cut's run ships through its own branch: " + emitted);
        checkEquality(LOG, jnt(expected), evaluate(emitted).clone().selfVID(null), true);
    }

    /**
     * THE RUN AFTER A CUT COPIES ITS ONE OBJ TO EVERY LANE — there is no seed to deal, so the branch takes the obj whole
     * ({@code ?split=copy}) and every lane walks its WORK-weighted share of the span range and folds it through the cut:
     * over the three loop peers, {@code {0,3}} is {@code {0,1}}, {@code {2,2}} and {@code {3,3}}. No lane holds a part of
     * its own, and each states the leaf's budget on its first instruction.
     */
    @Test
    void testTheRunAfterACutCopiesItsOneObjToEveryLane() {
        connectAll();
        final Code emitted = lower(homeAndPeer(), "start({1,2,3,4,5}).plus(1).sum().{0,3}plus(1).sum()");
        final DistributedSweep.Layout second = layoutAt(emitted, 1);
        assertEquals(3, second.lanes().size(), "one lane per peer: " + emitted);
        assertTrue(second.lanes().stream().allMatch(lane -> "copy".equals(lane.split())),
                "the branch copies the one obj to every lane: " + emitted);
        assertTrue(second.lanes().stream().allMatch(lane -> lane.part().isEmpty()),
                "no lane holds a part of its own: " + emitted);
        assertEquals("0..1|2..2|3..3", spans(second), "the shares of the unroll index: " + emitted);
        assertTrue(second.lanes().stream().allMatch(lane -> SUM_INST_TID.equals(lane.code().insts().getLast().tid().basePath())),
                "each lane folds its share through the cut: " + emitted);
        assertEquals("0", second.lane(0).budget(), "a leaf states 0 on its first instruction: " + emitted);
    }

    /**
     * ONE OBJ AND NO INDEX SET STAYS HOME — the run after a fold has one obj and, with no span range and no point,
     * nothing to factor, so dealing it would hand the whole run to one lane: a round trip and no parallelism. A point
     * span ({@code {3}}, one compose chain) is no index set either. The run before still ships. Each row: the chain %
     * its value.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "start({1,2,3,4,5}).plus(1).sum().plus(10).prod()  % 30",
            "start({1,2,3,4,5}).plus(1).sum().{3}plus(1).sum() % 23",
    }, delimiter = '%')
    void testARunWithOneObjAndNoIndexSetStaysHome(final String source, final int expected) {
        connectAll();
        final Code emitted = lower(homeAndPeer(), source);
        assertEquals(1, branches(emitted).size(), "only the first run ships: " + emitted);
        assertEquals(BRANCH_INST_TID, emitted.insts().getFirst().tid().basePath(), "and it is the first: " + emitted);
        checkEquality(LOG, jnt(expected), evaluate(emitted).clone().selfVID(null), true);
    }

    /**
     * A RUN STATES ITS OWN AXES, AND ONE THAT STATES NONE KEEPS THOSE OF THE RUN BEFORE IT — a statement governs from
     * where it stands onward. {@code ?hop=0} on the last fold keeps only that run home, though its span range or its
     * computed prefix would otherwise ship it; at the head, or on the first fold, it reaches every run after it, so
     * nothing ships. The first row's fold states its barrier ({@code sum?int<=int{*}}) only as a WORKAROUND, until the
     * tail-fold bug is fixed (Marko, 2026-11-18): behind a keyed branch, a reducer after a span range that stays home
     * folds each obj alone and answers 20 here, typer checks on or off. Each row: the chain % how many runs ship % its
     * value.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "start({1,2,3,4,5}).plus(1).sum().{0,3}plus(1).sum?int<=int{*}&hop=0()                            % 1 % 86",
            "start({1,2,3}).plus(1).sum().repeat(code=>+1,until=>loop().?>20,emit=>true).plus(10).sum?hop=0() % 1 % 649",
            "start?hop=0({1,2,3,4,5}).plus(1).sum().{0,3}plus(1).sum()                                       % 0 % 86",
            "start({1,2,3,4,5}).plus(1).sum?hop=0().{0,3}plus(1).sum()                                       % 0 % 86",
    }, delimiter = '%')
    void testARunStatesItsOwnAxesOrKeepsThoseBeforeIt(final String source, final int shipped, final int expected) {
        connectAll();
        final Code emitted = lower(homeAndPeer(), source);
        assertEquals(shipped, branches(emitted).size(), "the runs that ship: " + emitted);
        checkEquality(LOG, jnt(expected), evaluate(emitted).clone().selfVID(null), true);
    }

    /**
     * A POINT IS SPLIT ACROSS THE LANES — {@code a{p}} is p copies of one term and points add across a branch, so with one
     * obj to start from the copies are the index set: every lane takes the whole obj and an even share of the point
     * ({@code {5}} over the three loop peers is 2, 2 and 1), folds its copies through the cut, and the master recombines.
     * At the head the lanes are seeded; after a fold the branch copies the obj. Each row: the chain % the lanes' points %
     * its value.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "start({1,2,3,4,5}).plus(1).sum().id{5}().plus(10).prod()  % 2|2|1 % 24300000", // 30^5
            "start({1,2,3,4,5}).plus(1).sum().id{5}().plus(10).count() % 2|2|1 % 5",
            "start(20).id{5}().plus(10).prod()                         % 2|2|1 % 24300000",
            "start(20).plus{5}(1).prod()                               % 2|2|1 % 4084101",  // 21^5
    }, delimiter = '%')
    void testAPointIsSplitAcrossTheLanes(final String source, final String points, final int expected) {
        connectAll();
        final Code emitted = lower(homeAndPeer(), source);
        final DistributedSweep.Layout last = layoutAt(emitted, branches(emitted).size() - 1);
        assertEquals(points.trim(), last.lanes().stream()
                .map(lane -> lane.code().insts().stream().map(inst -> inst.tid().d()).filter(point -> !point.isOne())
                        .map(point -> String.valueOf(point.min())).findFirst().orElse("1"))
                .collect(Collectors.joining("|")), "the shares of the point: " + emitted);
        checkEquality(LOG, jnt(expected), evaluate(emitted).clone().selfVID(null), true);
    }

    /**
     * A PLACE SET STATED AT THE HEAD GOVERNS EVERY RUN — over one loop peer, {@code ?place=[local,peer]} gives the second
     * run its home lane too, where the dialed set alone would be one place and the run partition would decline.
     */
    @Test
    void testAPlaceSetStatedAtTheHeadGovernsEveryRun() {
        Network.Helper.connect(Machine.current().network(), PEER);
        final Code emitted = lower(homeAndPeer(), "start?place=[local,peer]({1,2,3,4,5}).plus(1).sum().{0,3}plus(1).sum()");
        final DistributedSweep.Layout second = layoutAt(emitted, 1);
        assertEquals(2, second.lanes().size(), "the inherited place set is two places: " + emitted);
        assertTrue(second.lane(0).local(), "the home place's share comes first: " + emitted);
        assertEquals(PEER, second.lane(1).authority(), "then the peer: " + emitted);
        assertEquals("0..2|3..3", spans(second), "the shares of the unroll index: " + emitted);
        checkEquality(LOG, jnt(86), evaluate(emitted).clone().selfVID(null), true);
    }

    /// /////////////////////////////////////////////////////////////////////////////////////
    /// THE ENVIRONMENTS AND THE ONE LOWERING
    /// /////////////////////////////////////////////////////////////////////////////////////

    /**
     * The {@link Env} the {@code ?place} emitter is a function of: the live peer map (so a {@code loop://} lane is a
     * real shipped lane), the live roster, and one synthetic authority — a test VM listens on nothing, and a machine
     * that answers nowhere has no place to be the home lane's.
     */
    private static Env homeAndPeer() {
        final Machine machine = Machine.current();
        return Env.of(machine.network().peers(), machine.network().machines(), Set.of(MINE));
    }

    /**
     * A peerless machine that still answers: the lanes case, with no dialed set at all.
     */
    private static Env alone() {
        return Env.of(rec(), rec(), Set.of(MINE));
    }

    /**
     * The three loop peers the hop rows fan out over — more than a {@code {2}} cap takes, so the cap is visible, and
     * none of them a forked process: the local transport, exactly as the place rows dial one.
     */
    private static void connectAll() {
        Network.Helper.connect(Machine.current().network(), PEER);
        Network.Helper.connect(Machine.current().network(), GINGER);
        Network.Helper.connect(Machine.current().network(), BOOGER);
    }

    /**
     * Lower a chain through the law shard plan against {@code env} — the emitter as a function of
     * {@code (plan, code, env)}, with no rewriter dispatch and no dial.
     */
    private static Code lower(final Env env, final String source) {
        return PlanInterpreter.apply(PlanInterpreter.SHARD_PLAN,
                ObjmtronSerializer.parse(source).asCode(), env);
    }

    /**
     * The value of a code, as the harness reads it (a distributed computation's answer).
     */
    private static Obj evaluate(final Code code) {
        return TransportRewriteHarness.single(TransportRewriteHarness.evaluate(code));
    }

    /**
     * The indices of the keyed branches an emitted chain holds — one per run that shipped.
     */
    private static List<Integer> branches(final Code emitted) {
        return IntStream.range(0, emitted.insts().size())
                .filter(i -> BRANCH_INST_TID.equals(emitted.insts().get(i).tid().basePath()) && emitted.insts().get(i).args().isRec())
                .boxed().toList();
    }

    /**
     * The layout of the {@code k}-th keyed branch: the reader peels the first one it meets, so it reads the chain from
     * that branch on.
     */
    private static DistributedSweep.Layout layoutAt(final Code emitted, final int k) {
        return DistributedSweep.layout(code(emitted.insts().subList(branches(emitted).get(k), emitted.insts().size())));
    }

    /**
     * Each lane's narrowed span, {@code min..max}, joined in lane order.
     */
    private static String spans(final DistributedSweep.Layout layout) {
        return layout.lanes().stream()
                .map(lane -> lane.code().insts().stream().map(inst -> inst.tid().b()).filter(span -> !span.isOne())
                        .map(span -> span.min() + ".." + span.max()).findFirst().orElse("no span"))
                .collect(Collectors.joining("|"));
    }
}
