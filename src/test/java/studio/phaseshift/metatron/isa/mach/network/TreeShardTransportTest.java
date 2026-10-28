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

package studio.phaseshift.metatron.isa.mach.network;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.Training;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.isa.m.math.cat.CatTheoryCompiler;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRecClient;
import studio.phaseshift.metatron.isa.web.type.MIME;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.cat.catInstSet.CAT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.wsServer.WS_CLIENT_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * THE TREE — {@code law_shard} planned at every level its hop budget allows. This machine dials ONE peer, and that
 * peer dials the other two, so a budget of two makes the middle peer an INTERIOR node: applying the slave it is
 * handed compiles it, the rule fires again there, and the peer re-plans its part over its own dialed peers and
 * recombines their partials through the same combiner.
 */
public class TreeShardTransportTest extends AbstractMetatronTest {

    private static PeerCluster cluster;
    private static WebSocketRecClient client;

    @BeforeAll
    static void startTree() throws Exception {
        // typing is not solved across distributed executions yet — run type-check-free, as the peers do
        TypeCheck.disable(TypeCheck.values());
        InstSet.importInstSet(MATH_ISA_TID);
        InstSet.importInstSet(CAT_ISA_TID);
        Machine.current().compiler(CatTheoryCompiler.single());
        // every peer mounts the category, so law_shard can fire wherever a part lands
        cluster = PeerCluster.of(3).seed(PeerCluster.DEFAULT_SEED_MATRIX, "import(/m/math)", "import(/m/math/cat)").start();
        // THE MIDDLE OF THE TREE: peer 1 dials peers 2 and 3. A dial is construction, so it has to happen THERE: the
        // ws_client literal is parsed here (a parse constructs nothing) and evaluated on peer 1, which constructs it.
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
     * The budget the root states travels with the part: the master ships one level fewer on the slave's seed,
     * {@code ?hops} is not copied onto any instruction below it, and the recombined value is the undistributed one.
     * Each row: the budget stated on the fold % the recombined value.
     */
    @Training.SkipTraining(reason = "the rows run over a tree of forked peers; the layout is the subject, not the value alone")
    @ParameterizedTest
    @CsvSource(value = {
            "2 % 44",
    }, delimiter = '%')
    void testTreeOverTransport(final int hops, final int expected) {
        final Code master = Machine.current().compiler().rewriter()
                .apply(ObjmtronSerializer.parse("{1,2,3,4,5,6,7,8}.plus(1).sum?hops=" + hops + "()")).asCode();
        final DistributedSweep.Layout layout = DistributedSweep.layout(master);
        assertEquals(List.of(START_INST_TID, SELECT_INST_TID, SELECT_INST_TID, MERGE_INST_TID, SUM_INST_TID), layout.ops(),
                "the root recombines its child's partial: " + master);
        final DistributedSweep.Lane lane = layout.lane(0);
        checkEquality(LOG, uri(String.valueOf(hops - 1)), uri(lane.budget()), true);
        checkEquality(LOG, bool(false), bool(lane.code().insts().stream().skip(1).anyMatch(i -> i.tid().hasQ(HOPS))), true);
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
            "{1,2,3,4,5,6,7,8}.plus(1).group?hops=2([mod(3)=>sum()])     % [2=>15,0=>18,1=>11]",
            "{1,2,3,4,5,6,7,8}.group?hops=2([mod(4)=>count()])           % [1=>2,2=>2,3=>2,0=>2]",
    }, delimiter = '%')
    void testKeyedTreeOverTransport(final String source, final String expected) {
        final Code master = Machine.current().compiler().rewriter().apply(ObjmtronSerializer.parse(source)).asCode();
        final DistributedSweep.Layout layout = DistributedSweep.layout(master);
        assertEquals(List.of(START_INST_TID, SELECT_INST_TID, SELECT_INST_TID, MERGE_INST_TID, SUM_INST_TID, SELECT_INST_TID), layout.ops(),
                "the root merges its child's partial rec by key, then folds each key: " + master);
        checkEquality(LOG, uri("1"), uri(layout.lane(0).budget()), true);
        checkEquality(LOG, ObjmtronSerializer.parse(expected),
                TransportRewriteHarness.single(TransportRewriteHarness.evaluate(master)), true);
    }

    /**
     * THE INTERIOR NODE: peer 1, handed a slave with budget left, plans it over its own dialed peers — its compiled
     * code splits its part into one leaf ({@code hops=0}) per child, routed to peers 2 and 3, and recombines through
     * {@code sum}. Asked of peer 1 itself, so it is that machine's compiler, roster and rule set that decide.
     */
    @Test
    void testInteriorNodeReplans() {
        final String explain = cluster.transport(1)
                .apply(ObjmtronSerializer.parse("start?hops=1({1,2,3,4,5,6,7,8}).plus(1).sum().explain()>>format")).toString();
        LOG.debug("interior node plan: %s", explain);
        checkEquality(LOG, bool(true), bool(explain.contains("start?hops=0({1,3,5,7})") && explain.contains("start?hops=0({2,4,6,8})")), true);
        checkEquality(LOG, bool(true), bool(explain.contains(":" + cluster.port(2) + ")") && explain.contains(":" + cluster.port(3) + ")")), true);
    }
}
