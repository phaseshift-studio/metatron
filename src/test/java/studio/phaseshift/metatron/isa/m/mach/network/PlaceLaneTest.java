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
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.algebra.rewrite.Env;
import studio.phaseshift.metatron.algebra.rewrite.PlanInterpreter;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.math.cat.catRewrite;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.HOP;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.MERGE_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SELECT_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.START_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SUM_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import studio.phaseshift.metatron.isa.m.mach.type.Network;

/**
 * {@code ?place} AT THE EMITTER (C5 of {@code distributed-qparams-implementation.md}): the place set a fold states —
 * the plan's distributing set, overridden by a {@code ?place} on any instruction of the chain — resolved into
 * <em>lanes</em>, one per share, the home place's shares first.
 *
 * <p><b>The oracle is the law, not the layout.</b> Every row that emits also asserts the value, because a place axis
 * is result-preserving (Thm 19): {@code distribute(c) ≡ c}. The lane <em>structure</em> is the other half — the home
 * place's route is the addressless {@code eval()} (lane at distance zero), a peer's is {@code eval(<authority>)}, and
 * a place with demand n takes n consecutive lanes.
 *
 * <p><b>Hermetic where it can be.</b> The {@code local} rows resolve against a synthetic {@link Env} — the seam C4
 * opened — because a test VM has no listening authority, so {@code Places.self()} is {@code noobj} unless the env
 * states one. The rows that need a real shipped lane dial a {@code loop://} peer (the
 * {@link PlacementEquivalenceTest} pattern: no forked process, no new cluster) and take the live peer map into the
 * same synthetic env.
 *
 * <p>The counts are the design's: a share is a lane, so {@code [local,peer]} is two lanes and
 * {@code [local,local,local,local]} is four home lanes — the lanes case, a peerless machine sharding.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class PlaceLaneTest extends AbstractMetatronTest {

    /** A stated home authority — what makes {@code local} a place in a synthetic env. */
    private static final fURI MINE = f("loop://mantis:8555");
    /** The two loop peers the duplicate-designator row names by address. */
    private static final fURI GINGER = f("loop://ginger:8555");
    private static final fURI BOOGER = f("loop://booger:8555");
    /** The one loop peer the default and S rows fan out over. */
    private static final fURI PEER = f("loop://localhost:8555");

    @BeforeEach
    void setup() {
        // typing is not solved across distributed executions yet — run type-check-free, as the forked-peer suite does
        TypeCheck.disable(TypeCheck.values());
        InstSet.importInstSet(f("/m/math"));
        InstSet.importInstSet(f("/m/math/cat"));
        Machine.current().compiler(CatTheoryCompiler.single());
    }

    @AfterEach
    void teardown() {
        Machine.current().network().drop(PEER);
        Machine.current().network().drop(GINGER);
        Machine.current().network().drop(BOOGER);
    }

    /**
     * S — {@code ?place=[local,peer]}: the master is a place at distance zero. Each row: the fold % its value. The
     * layout is the same mechanism with one more lane: the master's chain is still
     * {@code start·select·select·merge·<combiner>}, lane 0 is the addressless {@code eval()} (the home lane) and lane 1
     * travels to the dialed peer. The value is the local fold's, because a share is a share wherever it runs.
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
        assertEquals(List.of(START_INST_TID, SELECT_INST_TID, SELECT_INST_TID, MERGE_INST_TID, SUM_INST_TID),
                layout.ops(), "the master's chain is unchanged by the extra lane: " + emitted);
        assertEquals("0", layout.lane(0).budget(), "every lane is one level down: " + emitted);
        checkEquality(LOG, jnt(expected),
                evaluate(emitted).clone().selfVID(null), true);
    }

    /**
     * THE LANES — {@code ?place=[local,local,local,local]} on a <b>peerless</b> machine: four home lanes, each with its
     * own share of the seed, recombined at home through the same combiner. A peerless machine shards because the place
     * set says it does; the value is the local fold's. Each row: the fold % its value.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "plus(1).sum() % 44",
            "count()       % 8",
    }, delimiter = '%')
    void testFourHomeLanesOnAPeerlessMachine(final String tail, final int expected) {
        final Code emitted = lower(alone(), "start?place=[local,local,local,local]({1,2,3,4,5,6,7,8})." + tail);
        final DistributedSweep.Layout layout = DistributedSweep.layout(emitted);
        assertEquals(4, layout.lanes().size(), "a place with demand four takes four lanes: " + emitted);
        assertTrue(layout.lanes().stream().allMatch(DistributedSweep.Lane::local),
                "a peerless machine's lanes all stay home: " + emitted);
        assertEquals(List.of(2, 2, 2, 2), layout.lanes().stream().map(lane -> lane.part().size()).toList(),
                "the seed strides over the four shares: " + emitted);
        checkEquality(LOG, jnt(expected),
                evaluate(emitted).clone().selfVID(null), true);
    }

    /**
     * DUPLICATES SUM — {@code ?place=[//ginger,//ginger,//booger]} over two loop peers: three lanes, two of them
     * ginger's. The brace-free spelling of the same 2:1 weight {@code [{2}//ginger,//booger]} states, so the second
     * ginger is a share, not a starved duplicate. Each row: the fold % its value.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "plus(1).sum() % 27",
    }, delimiter = '%')
    void testDuplicateDesignatorsSumTheirShare(final String tail, final int expected) {
        Network.Helper.connect(Machine.current().network(), GINGER);
        Network.Helper.connect(Machine.current().network(), BOOGER);
        final Code emitted = PlanInterpreter.lower(catRewrite.planOf("law_shard"),
                ObjmtronSerializer.parse("start?place=[//ginger,//ginger,//booger]({1,2,3,4,5,6})." + tail).asCode());
        final DistributedSweep.Layout layout = DistributedSweep.layout(emitted);
        assertEquals(List.of(GINGER, GINGER, BOOGER), layout.routes(),
                "ginger takes two consecutive lanes and booger one: " + emitted);
        checkEquality(LOG, jnt(expected),
                evaluate(emitted).clone().selfVID(null), true);
    }

    /**
     * THE DEFAULT IS UNCHANGED — no {@code ?place}: the plan's own distributing set (the dialed peers), one lane per
     * peer, no home lane, and the same master chain the pre-C5 emitter emitted. This is C0's pin, re-read where the
     * place axis lands, so a change to the default would be a diff here and not only in the suites that pin it too.
     */
    @Test
    void testTheDefaultEmitsTheLayoutItAlwaysDid() {
        Network.Helper.connect(Machine.current().network(), PEER);
        final Code emitted = PlanInterpreter.lower(catRewrite.planOf("law_shard"),
                ObjmtronSerializer.parse("start({1,2,3,4,5,6}).plus(1).sum()").asCode());
        final DistributedSweep.Layout layout = DistributedSweep.layout(emitted);
        assertEquals(List.of(PEER), layout.routes(), "the dialed set, and no home lane: " + emitted);
        assertEquals(List.of(START_INST_TID, SELECT_INST_TID, SELECT_INST_TID, MERGE_INST_TID, SUM_INST_TID),
                layout.ops(), "the default layout is the one it always was: " + emitted);
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
                () -> PlanInterpreter.apply(catRewrite.planOf("law_shard"), code, alone()),
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
     * THE BUDGET RIDES THE FOLD — the seed of an emitted lane states its part and nothing else, the fold (the reducer
     * the lane recombines through) states the restated form, and no COPIED instruction carries the level above's
     * budget. The master, which gathers and recombines at home, carries none at all.
     */
    @Test
    void testTheBudgetRidesTheFoldAndNoCopiedInstructionCarriesIt() {
        connectAll();
        final Code emitted = lower(homeAndPeer(), "start?hop={2}3({1,2,3,4,5,6,7,8}).plus(1).sum()");
        final DistributedSweep.Lane lane = DistributedSweep.layout(emitted).lane(0);
        assertFalse(lane.code().insts().getFirst().tid().hasQ(HOP), "the seed states the part, never a budget: " + lane.code());
        assertEquals("{2}2", lane.budget(), "the fold states the restated form: " + lane.code());
        assertFalse(lane.code().insts().subList(1, lane.code().insts().size() - 1).stream()
                        .anyMatch(inst -> inst.tid().hasQ(HOP)),
                "no copied instruction carries the level above's budget: " + lane.code());
        assertFalse(emitted.insts().stream().skip(3).anyMatch(inst -> inst.tid().hasQ(HOP)),
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
    }, delimiter = '%')
    void testNoLevelOrNoBranchIsTheIdentity(final String source) {
        connectAll();
        final Code original = ObjmtronSerializer.parse(source).asCode();
        final Code emitted = PlanInterpreter.apply(catRewrite.planOf("law_shard"), original, homeAndPeer());
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

    /** A peerless machine that still answers: the lanes case, with no dialed set at all. */
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
        return PlanInterpreter.apply(catRewrite.planOf("law_shard"),
                ObjmtronSerializer.parse(source).asCode(), env);
    }

    /** The value of a code, as the harness reads it (a distributed computation's answer). */
    private static Obj evaluate(final Code code) {
        return TransportRewriteHarness.single(TransportRewriteHarness.evaluate(code));
    }
}
