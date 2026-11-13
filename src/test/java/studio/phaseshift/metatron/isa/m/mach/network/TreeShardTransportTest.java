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
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.m.web.space.ws.WebSocketRecClient;
import studio.phaseshift.metatron.isa.m.web.type.MIME;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.CAT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.space.ws.wsServer.WS_CLIENT_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * THE TREE — {@code law_shard} planned at every level its hop budget allows. This machine dials ONE peer, and that
 * peer dials the other two, so a budget of two makes the middle peer an INTERIOR node: applying the lane it is
 * handed compiles it, the rule fires again there, and the peer re-plans its part over its own dialed peers and
 * recombines their partials through the same combiner.
 */
public class TreeShardTransportTest extends AbstractMetatronTest {

    private static PeerCluster cluster;
    private static WebSocketRecClient client;

    @BeforeAll
    static void startTree() throws Exception {
        // typing is not solved across distributed executions yet — run type-check-free, as the peers do
        InstSet.importInstSet(MATH_ISA_TID);
        InstSet.importInstSet(CAT_ISA_TID);
        Machine.current().compiler(CatTheoryCompiler.single());
        Typer.CHECKS.forEach(flag -> Typer.Helper.set(flag, false));
        // the state belongs to the compiler stage, so it is set AFTER the compiler is installed
        // every peer mounts the category, so law_shard can fire wherever a part lands
        cluster = PeerCluster.of(3).seed(PeerCluster.DEFAULT_SEED_MATRIX, "import(/m/math)", "import(/m/math/cat)").start();
        // THE MIDDLE OF THE TREE: peer 1 dials peers 2 and 3. A dial is construction, so it has to happen THERE. The
        // literal is parsed here, and this VM imports /m/web, so the parse builds (and dials) a client here too; what
        // ships is its data, ws_client::[host=>…], and peer 1 builds and dials its own from it.
        for (int i = 2; i <= cluster.size(); i++)
            cluster.transport(1).apply(ObjmtronSerializer.parse("ws_client::[host=><" + endpoint(i) + ">]"));
        // the construction registers its peer asynchronously -- wait (bounded) until peer 1 holds both
        for (int t = 0; t < 40 && !holdsChildren(); t++)
            Thread.sleep(250);
        // THE ROOT: this machine dials peer 1 only, as a console dials
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
}
