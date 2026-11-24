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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.Training;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.m.web.space.ws.WebSocketRecClient;
import studio.phaseshift.metatron.isa.m.web.type.MIME;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.type.impl.BranchInst;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.CAT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.space.ws.wsServer.WS_CLIENT_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * THE TREE — {@code law_shard} planned at every level its hop budget allows. Peer 1 dials peers 2 and 3, so a budget
 * of two makes it an INTERIOR node: applying the lane it is handed compiles it, the rule fires again there, and the
 * peer re-plans its part over its own dialed peers and recombines their partials through the same combiner. This
 * machine holds all three: it dials peer 1 as a console dials, and parsing the dial literals peer 1 is sent builds a
 * client to peers 2 and 3 here too.
 */
public class TreeShardTransportTest extends AbstractMetatronTest {

    private static PeerCluster cluster;
    private static WebSocketRecClient client;

    @BeforeAll
    static void startTree() throws Exception {
        // the boot default: every type check on except code_resolve (Marko, 2026-11-18)
        InstSet.importInstSet(MATH_ISA_TID);
        InstSet.importInstSet(CAT_ISA_TID);
        Machine.current().compiler(CatTheoryCompiler.single());
        Typer.CHECKS.forEach(flag -> Typer.Helper.set(flag, Typer.defaultsOn(flag)));
        // the state belongs to the compiler stage, so it is set AFTER the compiler is installed
        // no peer mounts the category: law_shard is /m's, so a part re-plans wherever it lands on a bare peer
        cluster = PeerCluster.of(3).seed(PeerCluster.DEFAULT_SEED_MATRIX, "import(/m/math)").start();
        // THE MIDDLE OF THE TREE: peer 1 dials peers 2 and 3. A dial is construction, so it has to happen THERE. The
        // literal is parsed here, and this VM imports /m/web, so the parse builds (and dials) a client here too; what
        // ships is its data, ws_client::[host=>…], and peer 1 builds and dials its own from it.
        for (int i = 2; i <= cluster.size(); i++)
            cluster.transport(1).apply(ObjmtronSerializer.parse("ws_client::[host=><" + endpoint(i) + ">]"));
        // the construction registers its peer asynchronously -- wait (bounded) until peer 1 holds both
        for (int t = 0; t < 40 && !holdsChildren(); t++)
            Thread.sleep(250);
        // THE ROOT: this machine dials peer 1 as a console dials (the literals parsed above dialed 2 and 3 here too)
        final WebSocketRec wsRec = new WebSocketRec(mutableMap(
                uri(HOST), uri(endpoint(1)),
                uri(IN), uri(MIME.MIMEType.APPLICATION_MTRON.value),
                uri(OUT), uri(MIME.MIMEType.APPLICATION_MTRON.value)), WS_CLIENT_TID, null);
        client = new WebSocketRecClient(wsRec);
        Network.Helper.index(client);
    }

    @AfterAll
    static void stopTree() {
        if (null != client)
            client.close();
        if (null != cluster)
            cluster.close();
    }

    private static String endpoint(final int oneBased) {
        return "ws://localhost:" + cluster.port(oneBased) + PeerCluster.MTRON_ROUTE;
    }

    /**
     * whether peer 1's network holds a dialed peer for each of its two children
     */
    private static boolean holdsChildren() {
        final String network = cluster.transport(1).apply(ObjmtronSerializer.parse("*/network")).toString();
        return network.contains(String.valueOf(cluster.port(2))) && network.contains(String.valueOf(cluster.port(3)));
    }

    /**
     * The budget the root states travels with the part: the master ships one level fewer on the slave's HEAD (the seed
     * states the part alone), {@code ?hop} is not copied onto any other instruction, and the recombined value is the
     * undistributed one. Each row: the budget stated on the fold % the recombined value.
     */
    @Training.SkipTraining(reason = "the rows run over a tree of forked peers; the layout is the subject, not the value alone")
    @ParameterizedTest
    @CsvSource(value = {
            "2 % 44",
    }, delimiter = '%')
    void testTreeOverTransport(final int hop, final int expected) {
        final Code master = Machine.current().compiler().rewriter()
                .apply(ObjmtronSerializer.parse("{1,2,3,4,5,6,7,8}.plus(1).sum?hop=" + hop + "()")).asCode();
        final DistributedSweep.Layout layout = DistributedSweep.layout(master);
        assertEquals(List.of(BRANCH_INST_TID, SUM_INST_TID), layout.ops(),
                "the root recombines its child's partial: " + master);
        final DistributedSweep.Lane lane = layout.lane(0);
        checkEquality(LOG, uri(String.valueOf(hop - 1)), uri(lane.budget()), true);
        checkEquality(LOG, bool(true), bool(lane.code().insts().getFirst().tid().hasQ(HOP)), true);
        checkEquality(LOG, bool(false), bool(lane.code().insts()
                .subList(1, lane.code().insts().size()).stream().anyMatch(i -> i.tid().hasQ(HOP))), true);
        checkEquality(LOG, ObjmtronSerializer.parse(String.valueOf(expected)),
                TransportRewriteHarness.single(TransportRewriteHarness.evaluate(master)), true);
    }

    /**
     * THE TREE IS DRAWN BY ITS NODES (Marko, 2026-11-20): explain()>>ext/topology draws the root's fan, and a lane
     * that still carries a budget is explained by its own peer, whose compiler plans its subtree. Its flow comes back
     * as data and is drawn inside the lane, its merge at the peer's own place, running out to the root's join. The root
     * deals its eight over the three peers it knows, and peer 1, which dialed peers 2 and 3, re-plans its share over
     * them (two members or three, as the ports order the lanes). The root names peers 2 and 3 two and three, and peer 1
     * names them nothing, so the subtree reads the root's names: each name twice, once at the root and once inside
     * peer 1's lane. {@code has} keeps the drawing when the pattern is found in it, so each row is a pattern of the
     * drawing % 1 when the drawing holds it.
     */
    @Training.SkipTraining(reason = "the rows run over a tree of forked peers; the drawing is the subject")
    @ParameterizedTest
    @CsvSource(value = {
            "[{][23][}]──┤                                    % 1",
            "├─►//localhost:[0-9]+─[{]2[}]─[{]1[}]─+[┐┤┼┘]   % 1",
            "─►//local─[{]3[}]─[{]1[}]                       % 1",
            "//two─[^@]*//two─                                % 1",
            "//three─[^@]*//three─                            % 1",
    }, delimiter = '%')
    void testTheTreeIsDrawnByItsNodes(final String pattern, final int expected) {
        final Network network = Machine.current().network();
        network.registerPrefix(f("two"), f(endpoint(2)));
        network.registerPrefix(f("three"), f(endpoint(3)));
        try {
            checkCodeParseApply(LOG, "{1,2,3,4,5,6,7,8}.plus(1).sum?hop=2().explain()>>ext/topology/format.has('"
                    + pattern + "').count()", String.valueOf(expected));
        } finally {
            network.unregisterPrefix(f("two"));
            network.unregisterPrefix(f("three"));
        }
    }

    /**
     * A HAND-WRITTEN LANE IS EXPLAINED WHERE IT RUNS (the background reviews of 9918f5c44 through 876737ebc). Only
     * the planner's leaf form, a point on the head and no other {@code ?hop} or {@code ?hops}, is left alone; any other
     * lane is asked, and its machine answers what it plans, reading budgets run by run: a run's first budget and never
     * one it shadows, else the budget of the run before it, and the head's run, stating none, the shard plan's height
     * of one. A home lane is explained by this machine's compiler and network, as BranchInst#lane runs it: its four
     * are dealt over the three peers this machine holds (a branch whose one lane is home is that lane's code, compiled
     * here, so the row gives it a second lane). A lane at peer 1 is dealt over peers 2 and 3. A head budget that cannot
     * be read and a path that names no machine are drawn as their messages; a later budget the planner declines runs,
     * and is drawn, unplanned. Each row: the branch, P1 standing for peer 1's endpoint % a pattern of its drawing % 1
     * when the drawing holds it.
     */
    @Training.SkipTraining(reason = "the rows run over a tree of forked peers; the drawing is the subject")
    @ParameterizedTest
    @CsvSource(value = {
            "branch?<={0}(<~> => start?hop=1({1,2,3,4}).plus(1).sum(), </.> => start(5)) % ─►//local─[{]4[}]──┼ % 1",
            "branch?<={0}(<P1> => start({1,2,3,4,5,6,7,8}).plus(1).sum())       % [{]8[}]──┤                 % 1",
            "branch?<={0}(<P1> => start({1,2,3,4,5,6,7,8}).plus(1).sum?hop=1()) % [{]8[}]──┤                 % 1",
            "branch?<={0}(<P1> => start({1,2,3,4,5,6,7,8}).plus(1).sum().plus(1).sum?hop=0()) % [{]8[}]──┤ % 1",
            "branch?<={0}(<P1> => start?hop=0({1,2,3,4,5}).plus(1).sum().{0,3}plus?hop=1(1).sum()) % [{]1[}]──┤ % 1",
            "branch?<={0}(<P1> => start?hop=0({1,2,3,4,5}).plus(1).sum().{0,3}plus?hops=1(1).sum()) % [{]1[}]──┤ % 1",
            "branch?<={0}(<P1> => start({1,2,3,4,5,6,7,8}).plus?hop=1(1).sum?hop=-1()) % [{]8[}]──┤ % 1",
            "branch?<={0}(<P1> => start?hop=-1({1,2}).plus(1).sum())            % cannot be read as a hop    % 1",
            "branch?<={0}(<P1> => start({1,2}).plus(1).sum?hop=-1())            % cannot be read as a hop    % 0",
            "branch?<={0}(</nowhere> => start({1,2,3,4}).plus(1).sum(), <~> => start(5)) % found no machine % 1",
    }, delimiter = '%')
    void testAHandWrittenLaneIsExplainedWhereItRuns(final String branch, final String pattern, final int expected) {
        checkCodeParseApply(LOG, branch.replace("P1", endpoint(1)) + ".explain()>>ext/topology/format.has('"
                + pattern + "').count()", String.valueOf(expected));
    }

    /**
     * THE KEYED TREE: a keyed fold planned at every level its budget allows. Peer 1 re-plans its part over its own
     * peers and recombines their partial recs through the pointwise combiner, and the root recombines peer 1's the same
     * way — the rec sum merges by key, then each key folds through its value's combiner. Each row: the source, its
     * budget on the group % the recombined value.
     */
    @Training.SkipTraining(reason = "the rows run over a tree of forked peers; the layout is the subject, not the value alone")
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4,5,6,7,8}.plus(1).group?hop=2([mod(3)=>sum()])     % [2=>15,0=>18,1=>11]",
            "{1,2,3,4,5,6,7,8}.group?hop=2([mod(4)=>count()])           % [1=>2,2=>2,3=>2,0=>2]",
    }, delimiter = '%')
    void testKeyedTreeOverTransport(final String source, final String expected) {
        final Code master = Machine.current().compiler().rewriter().apply(ObjmtronSerializer.parse(source)).asCode();
        final DistributedSweep.Layout layout = DistributedSweep.layout(master);
        assertEquals(List.of(BRANCH_INST_TID, SUM_INST_TID, SELECT_INST_TID), layout.ops(),
                "the root merges its child's partial rec by key, then folds each key: " + master);
        checkEquality(LOG, uri("1"), uri(layout.lane(0).budget()), true);
        checkEquality(LOG, ObjmtronSerializer.parse(expected),
                TransportRewriteHarness.single(TransportRewriteHarness.evaluate(master)), true);
    }

    /**
     * A PROFILE NESTS LIKE THE TREE (U over E's budget): peer 1, handed a lane with a level left, re-plans it over peers 2
     * and 3, times its own run around its branch and answers a collated report of its own, which crosses the wire whole
     * and nests under the root's. The root holds all three peers (the {@code ws_client} literals parsed here dial them
     * here too), so its level is three places, peer 1 the one interior node. The figures fold bottom-up: the root counts
     * peer 1's leaves as peer 1 folded them, without walking below it. Each row: the source % the nest's shape -- depth,
     * the leaves (how many, and the objs their runs carried) and the places per level.
     */
    @Training.SkipTraining(reason = "the rows run over a tree of forked peers; the nest's shape is the subject, its timings vary run to run")
    @ParameterizedTest
    @CsvSource(value = {
            "{1,2,3,4,5,6,7,8}.plus(1).sum?hop=2().profile()   % [depth=>2,leaf=>[count=>4,c_sum=>8],level=>[3,2]]",
    }, delimiter = '%')
    void testProfileNestsLikeTheTree(final String source, final String expected) {
        final Rec report = Machine.evaluate(Machine.parse(source).asCode()).asRec();
        checkEquality(LOG, ObjmtronSerializer.parse(expected), rec(
                uri(DEPTH), report.at(uri(DEPTH)),
                uri(LEAF), rec(uri(COUNT), report.at(uri(f(LEAF).extend(COUNT))), uri(C_SUM), report.at(uri(f(LEAF).extend(C_SUM)))),
                uri(LEVEL), lst(report.at(uri(LEVEL)).elements().map(level -> (Obj) level.asRec().at(uri(COUNT))).toList())), true);
        // ONE INTERIOR NODE: the one place that collated places of its own, one level deep over two leaves
        final List<Rec> interiors = report.at(uri(PLACE)).asRec().<Rel>elements().map(place -> place.second().asRec())
                .filter(place -> !place.at(uri(PLACE)).isNoObj()).toList();
        checkEquality(LOG, jnt(1), jnt(interiors.size()), true);
        checkEquality(LOG, jnt(1), interiors.getFirst().at(uri(DEPTH)), true);
        checkEquality(LOG, jnt(2), interiors.getFirst().at(uri(f(LEAF).extend(COUNT))), true);
        // FOUR MACHINES: the root and its three peers, peers 2 and 3 at both levels
        final List<Obj> machines = Stream.concat(Stream.of(report.at(uri(MACHINE))), Stream.concat(
                report.at(uri(PLACE)).asRec().<Rel>elements().map(place -> (Obj) place.second().asRec().at(uri(MACHINE))),
                interiors.getFirst().at(uri(PLACE)).asRec().<Rel>elements().map(place -> (Obj) place.second().asRec().at(uri(MACHINE))))).toList();
        checkEquality(LOG, jnt(4), jnt(machines.stream().distinct().count()), true);
        // the user's >>format names every place at both levels
        final Obj format = Machine.evaluate(Machine.parse(source + ">>format").asCode());
        LOG.debug("profile of %s: %s", source, format);
        checkEquality(LOG, bool(true), bool(format.isStr()), true);
        // EVERY PLACE IS NAMED AS THE TOPOLOGY NAMES ITS LANE (Marko, 2026-11-22), by the machine that dealt it: the
        // root's three peers and peer 1's two, //localhost:<port> each, and the format shows every one
        final java.util.Set<String> peers = Stream.of(1, 2, 3).map(i -> "//localhost:" + cluster.port(i))
                .collect(java.util.stream.Collectors.toSet());
        checkEquality(LOG, bool(true), bool(peers.equals(report.at(uri(PLACE)).asRec().<Rel>elements()
                .map(place -> place.first().toString()).collect(java.util.stream.Collectors.toSet()))), true);
        Stream.concat(report.at(uri(PLACE)).asRec().<Rel>elements().map(Rel::first),
                        interiors.getFirst().at(uri(PLACE)).asRec().<Rel>elements().map(Rel::first))
                .forEach(key -> checkEquality(LOG, bool(true), bool(format.strValue().contains(key.toString())), true));
    }

    /**
     * THE INTERIOR NODE: peer 1, handed a lane with budget left, plans it over its own dialed peers — its compiled
     * code is one branch keyed by peers 2 and 3, each lane stating its one remaining level on its FOLD
     * ({@code sum?hop=0()}), and recombines through {@code sum}. The parts are the branch's to deal when it runs, so the
     * plan names no slice. Asked of peer 1 itself, so it is that machine's compiler, roster and rule set that decide.
     */
    @Test
    void testInteriorNodeReplans() {
        final String explain = cluster.transport(1)
                .apply(ObjmtronSerializer.parse("start?hop=1({1,2,3,4,5,6,7,8}).plus(1).sum().explain()>>format")).toString();
        LOG.debug("interior node plan: %s", explain);
        assertTrue(explain.contains("branch"), "the interior node plans one branch: " + explain);
        assertTrue(explain.contains("?hop=0"), "each lane's fold states the last level: " + explain);
        assertTrue(explain.contains(":" + cluster.port(2)) && explain.contains(":" + cluster.port(3)),
                "keyed by peers 2 and 3: " + explain);
    }

    /**
     * A TREE OF SUPERSTEPS (Marko, 2026-11-22: tree BSP unites two patterns, E and N). A whole-frontier repeat whose
     * budget is two deals each pass over the three peers this machine holds, each lane the code with a level left
     * ({@code plus?hop=1(1)}). The repeat stays home and judges until; peer 1, handed {@code start(part).plus?hop=1(1)},
     * plans its part over peers 2 and 3 (law_shard's cutless run), so every pass partitions the frontier down the tree
     * and merges it back up before the next. The answer is the flat deal's: four passes of plus(1) over 1..8.
     */
    @Training.SkipTraining(reason = "the program runs over a tree of forked peers; its layout is the subject")
    @Test
    void testATreeOfSuperstepsOverTransport() {
        final String source = "{1,2,3,4,5,6,7,8}.repeat?<=int{*}&hop=2(code=>plus(1),until=>loop()?>3)";
        final Code master = Machine.current().compiler().rewriter().apply(ObjmtronSerializer.parse(source)).asCode();
        final Inst repeat = master.insts().stream()
                .filter(inst -> REPEAT_INST_TID.equals(inst.tid().basePath())).findFirst().orElseThrow();
        final Inst branch = repeat.arg(f(CODE), 1).asCall().insts().getFirst();
        assertEquals(BRANCH_INST_TID, branch.tid().basePath(), "the pass is a branch: " + master);
        final List<BranchInst.Lane> lanes = BranchInst.lanes(branch);
        assertEquals(cluster.size(), lanes.size(), "one lane per peer this machine holds: " + master);
        // a lane is the cut its move compiles to, its budget on the cut's target (Marko, 2026-11-24)
        lanes.forEach(lane -> assertEquals("1", lane.place().q(HOP), "each lane carries a level left: " + lane));
        // what a lane does at peer 1: handed its part with a level left, it plans the part over peers 2 and 3 (an
        // even fan of two, its four members dealt two and two)
        checkCodeParseApply(LOG, "branch?<={0}(<" + endpoint(1) + "> => start({1,2,3,4}).plus?hop=1(1))"
                + ".explain()>>ext/topology/format.has('[{]4[}]──┤').count()", "1");
        checkCodeRewrite(LOG, source, "start().repeat(branch(cut(),cut(),cut()),loop().is())",
                "{5,6,7,8,9,10,11,12}", true);
    }

    /**
     * COEFFICIENTS ARE CONSERVED DOWN THE TREE (Marko, 2026-11-22): every tree answers what the same program answers at
     * home, coefficients included. Each seed repeats members, so the frontier bulks, and peer 1, which re-plans its part
     * over peers 2 and 3, hands counted members on a level further and reads them back. The program runs as written,
     * which must distribute, and its home twin states ?hop=0 where the program's budget is read. Each row: the program %
     * its home twin.
     */
    @Training.SkipTraining(reason = "the rows run over a tree of forked peers; the conservation is the subject")
    @ParameterizedTest
    @CsvSource(value = {
            "start?hop=2({1,1,2,2,3,3,4,4}).plus(1)                          % start?hop=0({1,1,2,2,3,3,4,4}).plus(1)",
            "{1,1,2,2,3,3,4,4}.plus(1).sum?hop=2()                           % start?hop=0({1,1,2,2,3,3,4,4}).plus(1).sum()",
            "{1,1,2,2,3}.group?hop=2([mod(2)=>count()])                      % start?hop=0({1,1,2,2,3}).group([mod(2)=>count()])",
            "{1,1,2,2}.repeat?<=int{*}&hop=2(code=>plus(1),until=>loop()?>3) % {1,1,2,2}.repeat?<=int{*}&hop=0(code=>plus(1),until=>loop()?>3)",
    }, delimiter = '%')
    void testCoefficientsAreConservedDownTheTree(final String program, final String home) {
        final Code planned = Machine.current().compiler().rewriter().apply(ObjmtronSerializer.parse(program.trim())).asCode();
        assertTrue(distributes(planned.insts()), "the program must distribute, or the row tests nothing: " + planned);
        checkEquality(LOG, Machine.evaluate(Machine.parse(home.trim()).asCode()),
                Machine.evaluate(Machine.parse(program.trim()).asCode()), true);
    }

    /**
     * Whether a chain fans out: a branch, or a branch inside a repeat's code (law_bsp's superstep).
     */
    private static boolean distributes(final List<Inst> insts) {
        return insts.stream().anyMatch(inst -> BRANCH_INST_TID.equals(inst.tid().basePath())
                || (REPEAT_INST_TID.equals(inst.tid().basePath()) && inst.arg(f(CODE), 1).isCall()
                && distributes(inst.arg(f(CODE), 1).asCall().insts())));
    }
}
