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

package studio.phaseshift.metatron.isa.m.web.space.ws;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.Analyzer;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.BasicAnalyzer;
import studio.phaseshift.metatron.isa.m.mach.type.network.Listener;
import studio.phaseshift.metatron.isa.m.mach.type.thread.CoreThread;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.CommonUtil;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.LinkedHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.ORIGIN;
import static studio.phaseshift.metatron.Tokens.ROUTE;
import static studio.phaseshift.metatron.Tokens.RUN;
import static studio.phaseshift.metatron.Tokens.SUBQ;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.sys.sysInstSet.SYS_ISA_TID;
import static studio.phaseshift.metatron.isa.m.web.space.ws.handler.mtron_wsHandler.WS_MTRON_HANDLER_TID;
import static studio.phaseshift.metatron.isa.m.web.space.ws.wsServer.WS_CLIENT_TID;
import static studio.phaseshift.metatron.isa.m.web.space.ws.wsServer.WS_HANDLER_TID;
import static studio.phaseshift.metatron.isa.m.web.type.mcpMetatronBuilder.MCP_MTRON_SERVER_TID;
import static studio.phaseshift.metatron.isa.m.web.webInstSet.WEB_ISA_TID;

/**
 * A WEBSOCKET SERVER IS A MACHINE'S PRESENCE ({@link wsServer}): a listener on a {@code ws://host:port} socket whose
 * routes are the routes of every machine sharing it. A connection takes the most specific route, and the machine whose
 * route it took evaluates the session and holds the inbound peer. Dials here are loopback, where the dialer records
 * the peer and the listener declines a twin ({@link Network#accept}), so the inbound peer is pinned through
 * {@link wsServer#admit} with a remote address.
 */
//@Isolated
//@Execution(ExecutionMode.SAME_THREAD)
public class wsServerTest extends AbstractMetatronTest {

    private fURI socket;

    /**
     * the web instruction set's types — ws_server::T, mtron_ws, mcp_server::T — are its import's, as they are a boot
     * profile's; a route is classified against them
     */
    @BeforeAll
    static void web() {
        InstSet.importInstSet(WEB_ISA_TID);
    }

    @BeforeEach
    void socket() {
        this.socket = f("ws://localhost:" + generatePort());
    }

    @AfterEach
    void release() throws Exception {
        this.resetToRoot();
        final Network network = Machine.current().network();
        network.drop(this.socket);
        network.unlisten(this.socket);
        // a hang-up reaches the far side asynchronously: let the reach settle so each test starts with none
        final long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline && network.peers().elements().count() > 0)
            Thread.sleep(50);
    }

    private static Rec mtron(final String route) {
        return rec(uri(route), uri(WS_MTRON_HANDLER_TID.toString()));
    }

    @Test
    void testServesItsRoutesThroughTheSocket() {
        final Obj server = wsServer.WS_SERVER_TYPE.constructor().apply(rec(uri(ORIGIN), uri(this.socket), uri(ROUTE), mtron("/mtron")));
        assertInstanceOf(wsServer.class, server, "the constructor answers the server: " + server);
        assertTrue(Network.Helper.status(server), "a server is live while it holds its socket");
        assertSame(server, Network.Helper.listenerFor(this.socket), "the socket is this machine's presence");
        Machine.write(f("/t/ws_server/x"), str("served"));
        Network.Helper.connect(Machine.current().network(), this.socket.extend("mtron"), WS_CLIENT_TID);
        checkEquality(LOG, Machine.read(this.socket.extend("t").extend("ws_server").extend("x")), str("served"), true);
    }

    @Test
    void testAConnectionEvaluatesAsTheMachineWhoseRouteItTook() {
        final Machine root = Machine.current();
        final Listener server = wsServer.serve(this.socket, mtron("/mtron"));
        Machine.write(f("/probe/where"), str("root"));
        final Machine child = root.push(f("ws_server_child"));
        assertSame(server, wsServer.serve(this.socket, mtron("/child")), "a child joins the socket its parent holds");
        Machine.write(f("~/probe/where"), str("child"));
        // the root dials both routes: each connection evaluates as the machine whose route it took
        Machine.current(root);
        final Obj code = ObjmtronSerializer.parse("*<~/probe/where>");
        Network.Helper.connect(root.network(), this.socket.extend("mtron"), WS_CLIENT_TID);
        checkEquality(LOG, root.network().submit(this.socket, code), str("root"), true);
        root.network().drop(this.socket);
        Network.Helper.connect(root.network(), this.socket.extend("child"), WS_CLIENT_TID);
        checkEquality(LOG, root.network().submit(this.socket, code), str("child"), true);
        root.network().drop(this.socket);
        // pop() pops the thread's current machine, so stand in the child again to pop it
        Machine.current(child);
        child.pop();
        assertTrue(server.route(f("/child")).isEmpty(), "the child's route left with the child");
        assertFalse(server.closed(), "a child's pop never releases the socket its parent holds");
    }

    @Test
    void testTheOwnersReleaseStopsTheSocket() {
        final Listener server = wsServer.serve(this.socket, mtron("/mtron"));
        Machine.current().network().unlisten(this.socket);
        assertFalse(Network.Helper.status(server), "a released server is not live");
        assertTrue(server.closed());
        // the port is free again: the same socket binds anew
        final Listener again = wsServer.serve(this.socket, mtron("/mtron"));
        assertNotSame(server, again);
        assertTrue(Network.Helper.status(again));
    }

    @Test
    void testAnUnroutedPathIsRefused() throws Exception {
        wsServer.serve(this.socket, mtron("/mtron"));
        final CompletableFuture<Integer> closed = new CompletableFuture<>();
        final WebSocketClient probe = new WebSocketClient(URI.create(this.socket + "/nowhere")) {
            @Override
            public void onOpen(final ServerHandshake handshake) {
            }

            @Override
            public void onMessage(final String message) {
            }

            @Override
            public void onClose(final int code, final String reason, final boolean remote) {
                closed.complete(code);
            }

            @Override
            public void onError(final Exception ex) {
            }
        };
        probe.connectBlocking(2, TimeUnit.SECONDS);
        assertEquals(4004, closed.get(2, TimeUnit.SECONDS), "a path no machine routes is closed, not served");
    }

    /**
     * A raw client on one of this socket's routes, outside metatron's own: it sends text frames, and queues the frames it
     * is answered with.
     */
    private WebSocketClient client(final String route, final BlockingQueue<String> replies) {
        return new WebSocketClient(URI.create(this.socket + route)) {
            @Override
            public void onOpen(final ServerHandshake handshake) {
            }

            @Override
            public void onMessage(final String message) {
                replies.add(message);
            }

            @Override
            public void onClose(final int code, final String reason, final boolean remote) {
            }

            @Override
            public void onError(final Exception ex) {
            }
        };
    }

    /**
     * A HANG-UP ENDS THE EVALUATION OF ITS MESSAGE (2026-11-16), ON THE THREAD THAT RUNS IT. An MCP tool call is a
     * program (2026-11-22), so it runs on a pooled thread of the system's executor rather than on the socket's worker:
     * eval_mtron's {@code 1.{*}plus(0)} never leaves plus's domain, so its walk loops on, as the expression says. The
     * client hangs up without a closing handshake: the session's close interrupts every thread still evaluating one of
     * its frames, and the walk, which asks its thread once a run, answers a fail to no one. The thread goes back to the
     * pool, so it must outlive the interrupt: a flag left set would end the next task it takes.
     */
    @Test
    void testAHangUpEndsTheEvaluationOfItsMessage() throws Exception {
        final wsServer server = (wsServer) wsServer.serve(this.socket, rec(uri("/mcp"), uri(MCP_MTRON_SERVER_TID.toString())));
        final BlockingQueue<String> replies = new LinkedBlockingQueue<>();
        final WebSocketClient client = this.client("/mcp", replies);
        assertTrue(client.connectBlocking(2, TimeUnit.SECONDS), "the client dials the route");
        client.send("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\","
                + "\"clientInfo\":{\"name\":\"wsServerTest\",\"version\":\"0\"}}}");
        // each request runs on a thread of its own, so the tool call waits for initialize's answer: the one thread left
        // evaluating is then the tool call's
        assertNotNull(replies.poll(5, TimeUnit.SECONDS), "the server answers initialize");
        client.send("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"m_web_mcp_mcp_mtron_eval_mtron\","
                + "\"arguments\":{\"code\":\"1.{*}plus(0)\"}}}");
        // the thread evaluating the request is this socket's own: the tool call's frame is the only one in flight, so
        // the socket's evaluating set is scoped to this connection and names the request's thread alone
        final long found = System.currentTimeMillis() + 5000;
        Thread worker = null;
        while (null == worker && System.currentTimeMillis() < found) {
            Thread.sleep(50);
            worker = server.evaluating().stream().findFirst().orElse(null);
        }
        assertNotNull(worker, "the server evaluates the tool call");
        Thread.sleep(250);
        assertTrue(server.evaluating().contains(worker), "the walk is still walking before the hang-up");
        client.closeConnection(CloseFrame.ABNORMAL_CLOSE, "hung up");
        // the thread leaves the evaluation and goes back to its pool
        final long ended = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < ended && worker.isAlive() && server.evaluating().contains(worker))
            Thread.sleep(50);
        assertFalse(server.evaluating().contains(worker),
                "the hang-up ends the evaluation within five seconds, not on " + worker);
        assertTrue(worker.isAlive(), "the thread outlives the interrupt: it goes back to the pool");
    }

    /**
     * A HANG-UP ENDS EVERY PROGRAM ITS SESSION IS RUNNING (2026-11-21). Each program a session is sent runs on a pooled
     * thread of the system's executor, so two walks that never end are two more threads running under
     * {@code /sys/thread/executor}, time-sliced as any platform thread is. The client hangs up without a closing
     * handshake: the session's close interrupts both, each walk answers a fail to no one, and both threads go back to the
     * pool. The socket serves the next connection.
     */
    @Test
    void testAHangUpEndsEveryProgramItsSessionRuns() throws Exception {
        wsServer.serve(this.socket, mtron("/mtron"));
        // the pooled platform threads alone: the executor also lists the virtual threads that come and go beside the
        // walks on their own schedule (a frame's network analyzer publishes a step later, on one of its own)
        final Supplier<Obj> running = () -> jnt(listedThreads(RUN).stream()
                .filter(thread -> Machine.read(thread) instanceof CoreThread)
                .count());
        final long idle = running.get().intValue();
        final WebSocketClient client = this.client("/mtron", new LinkedBlockingQueue<>());
        assertTrue(client.connectBlocking(2, TimeUnit.SECONDS), "the client dials the route");
        client.send("1.{*}plus(0)");
        client.send("1.{*}plus(0)");
        for (int waited = 0; waited < 5_000 && !jnt(idle + 2).equals(running.get()); waited += 50)
            Thread.sleep(50);
        checkEquality(LOG, jnt(idle + 2), running.get(), true);
        client.closeConnection(CloseFrame.ABNORMAL_CLOSE, "hung up");
        for (int waited = 0; waited < 5_000 && !jnt(idle).equals(running.get()); waited += 50)
            Thread.sleep(50);
        checkEquality(LOG, jnt(idle), running.get(), true);
        final BlockingQueue<String> replies = new LinkedBlockingQueue<>();
        final WebSocketClient next = this.client("/mtron", replies);
        assertTrue(next.connectBlocking(2, TimeUnit.SECONDS), "the next client dials the route");
        try {
            next.send("1.plus(1)");
            checkEquality(LOG, jnt(2), ObjmtronSerializer.parse(String.valueOf(replies.poll(5, TimeUnit.SECONDS))), true);
        } finally {
            next.closeConnection(CloseFrame.NORMAL, "done");
        }
    }

    /**
     * A PROGRAM THAT WAITS HOLDS NO FRAME BEHIND IT (2026-11-21). Each program a session is sent is evaluated on a thread
     * of its own, so a walk that never ends leaves its connection free: the next frame on the same connection is answered
     * while the first walks on. On the socket's worker it waited behind the walk for good, the queue a tree three levels
     * deep over peers that dial each other deadlocked on (DistributedComputingTest, row E). Programs answer under their
     * requests' tags, so they may answer out of order.
     */
    @Test
    void testAProgramThatWaitsHoldsNoFrameBehindIt() throws Exception {
        wsServer.serve(this.socket, mtron("/mtron"));
        final BlockingQueue<String> replies = new LinkedBlockingQueue<>();
        final WebSocketClient client = this.client("/mtron", replies);
        assertTrue(client.connectBlocking(2, TimeUnit.SECONDS), "the client dials the route");
        try {
            client.send("1.{*}plus(0)");
            client.send("1.plus(1)");
            checkEquality(LOG, jnt(2), ObjmtronSerializer.parse(String.valueOf(replies.poll(5, TimeUnit.SECONDS))), true);
        } finally {
            client.closeConnection(CloseFrame.ABNORMAL_CLOSE, "hung up");
        }
    }

    @Test
    void testTheInboundPeerIsHeldByTheRoutesMachine() {
        final Machine root = Machine.current();
        final Machine child = root.push(f("ws_server_child"));
        try {
            final Listener.Route route = new Listener.Route(child.network(), f("/drstynx"), uri(WS_MTRON_HANDLER_TID.toString()));
            final WebSocketRec session = new WebSocketRec(new LinkedHashMap<>(), WS_HANDLER_TID, null);
            final fURI dialed = f("ws://10.0.0.7:41234/drstynx?clientq=lab");
            assertEquals(dialed, wsServer.admit(route, dialed, session));
            final fURI key = dialed.pathEncoding();
            final Obj peer = child.network().peer(key);
            assertTrue(peer.isRec(), "the peer joins the machine whose route the connection took");
            checkEquality(LOG, root.network().peer(key), noobj(), true);
            child.network().drop(dialed);
            checkEquality(LOG, child.network().peer(key), noobj(), true);
        } finally {
            child.pop();
        }
    }

    /**
     * A SERVER'S METRICS ARE ITS OWN. Every socket is an addressable source on its own address
     * ({@code <origin>/#}), instrumented at birth: a frame it handles is a READ there, the reply it sends is a
     * WRITE, connections is the one gauge, and a hang-up is a write of noobj (a delete) — all in the analyzer the
     * socket's own {@code ?analq} holds, never the network's shared one.
     */
    @Test
    void testEachSocketAnswersItsOwnAnalysis() throws Exception {
        final Listener server = wsServer.serve(this.socket, mtron("/mtron"));
        assertEquals(f("ws"), server.protocol(), "a ws server says ws");
        assertNotNull(server.analyzer(), "a socket carries an access analyzer from birth");
        assertNotSame(Machine.current().network().analyzer(), server.analyzer(),
                "and it is the socket's own, not the network's shared one");
        final fURI analysisAddress = this.socket.extend(ALL).addQ("analq");
        assertTrue(Machine.read(analysisAddress).isRec(), "the socket answers its analysis at its own address");

        // one round trip through the socket: the server reads the request and writes the answer
        final Network network = Machine.current().network();
        Network.Helper.connect(network, this.socket.extend("mtron"), WS_CLIENT_TID);
        final Obj answer = network.submit(this.socket, ObjmtronSerializer.parse("1.plus(1)"));
        assertFalse(answer.isFail(), "the socket answered: " + answer);

        // the request is a read in the socket's analysis, once its answer has been given
        assertTrue(this.reads(1L).at(uri("latency")).asRec().at(uri("count")).intValue() >= 1L,
                "and the read is timed where the handler ran");
        final Rec analysis = Machine.read(analysisAddress).asRec();
        assertTrue(analysis.at(uri("write")).asRec().at(uri("count")).intValue() >= 1L,
                "the reply is a write at the route it served");
        assertEquals(1.0d, analysis.at(uri("connections")).realValue(), "connections is a gauge, one live session");

        // INDEPENDENT OF THE NETWORK'S: the wire meters are the network's, the accesses are the socket's
        final Rec wireAnalysis = Machine.current().network().analyzer().analysis();
        assertTrue(wireAnalysis.at(uri("read")).isNoObj(), "the network records wire frames, not the socket's reads");

        // A HANG-UP IS A DELETE: the socket records it as a write of noobj, whose subset is write/delete
        network.drop(this.socket);
        for (int waited = 0; waited < 5_000 && Machine.read(analysisAddress).asRec()
                .at(uri("write")).asRec().at(uri("delete")).isNoObj(); waited += 50)
            CommonUtil.sleepThread(50);
        final Rec after = Machine.read(analysisAddress).asRec();
        assertEquals(1L, after.at(uri("write")).asRec().at(uri("delete")).asRec().at(uri("count")).intValue(),
                "the session's hang-up is a delete: " + after);
        assertEquals(0.0d, after.at(uri("connections")).realValue(), "and the gauge falls with it");
    }

    /**
     * A FRAME IS COUNTED AS IT ARRIVES, before its program runs: the program a frame carries reads the wire with its own
     * arrival already counted. Counted after its evaluation, a frame never saw itself, and the first read of a quiet
     * machine's network analysis answered an empty analysis.
     */
    @Test
    void testAFrameIsCountedBeforeItsProgramRuns() {
        wsServer.serve(this.socket, mtron("/mtron"));
        final Network network = Machine.current().network();
        Network.Helper.connect(network, this.socket.extend("mtron"), WS_CLIENT_TID);
        // one round trip first: whatever the connection exchanges as it opens has arrived before the count is taken
        final Obj warm = network.submit(this.socket, ObjmtronSerializer.parse("1.plus(1)"));
        assertFalse(warm.isFail(), "the socket answers: " + warm);
        // client and server share this machine's network, so its wire counts the server's arrivals and the client's
        final long before = Machine.read(f("/network/analyzer/analysis/wire/recv")).orElse(jnt(0)).intValue();
        final Obj seen = network.submit(this.socket, ObjmtronSerializer.parse("*/network/analyzer/analysis/wire/recv"));
        assertFalse(seen.isFail(), "the program answered: " + seen);
        assertEquals(before + 1L, seen.orElse(jnt(0)).intValue(),
                "the program read the wire with its own frame already counted: " + seen);
    }

    /**
     * AN MCP REQUEST IS COUNTED, as every frame is: on the wire of the machine whose route it took as it arrives, and as
     * a read in the socket's own analysis once it is answered. An mcp session answers without on_message, which is where
     * a frame used to be counted, so its requests were counted nowhere.
     */
    @Test
    void testAnMcpRequestIsCountedAsItArrives() throws Exception {
        wsServer.serve(this.socket, rec(uri("/mcp"), uri(MCP_MTRON_SERVER_TID.toString())));
        final BlockingQueue<String> replies = new LinkedBlockingQueue<>();
        final WebSocketClient client = this.client("/mcp", replies);
        try {
            assertTrue(client.connectBlocking(2, TimeUnit.SECONDS), "the client dials the route");
            // the raw client's own frames are no machine's: this machine's wire counts only what the server takes
            final long before = Machine.read(f("/network/analyzer/analysis/wire/recv")).orElse(jnt(0)).intValue();
            client.send("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}");
            assertNotNull(replies.poll(5, TimeUnit.SECONDS), "the server answers the ping");
            assertEquals(before + 1L, Machine.read(f("/network/analyzer/analysis/wire/recv")).orElse(jnt(0)).intValue(),
                    "the request crossed the wire into this machine");
            assertEquals(1L, this.reads(1L).at(uri("count")).intValue(), "and is a read in the socket's own analysis");
        } finally {
            client.closeBlocking();
        }
    }

    /**
     * A FRAME'S READ IS TIMED OVER ITS WHOLE ANSWER: the socket's read/latency spans the program the frame carries, not
     * only the on_message that sends the answer after it.
     */
    @Test
    void testAReadIsTimedOverItsProgram() {
        InstSet.importInstSet(SYS_ISA_TID);
        wsServer.serve(this.socket, mtron("/mtron"));
        final Network network = Machine.current().network();
        Network.Helper.connect(network, this.socket.extend("mtron"), WS_CLIENT_TID);
        final Obj answer = network.submit(this.socket, ObjmtronSerializer.parse("1.sleep(millis::200.0)"));
        assertFalse(answer.isFail(), "the socket answered: " + answer);
        final Rec latency = this.reads(1L).at(uri("latency")).asRec();
        assertTrue(latency.at(uri("max")).intValue() >= 200_000_000L, "the read spans the program's 200ms: " + latency);
    }

    /**
     * this socket's read meters once {@code count} reads have landed. A read is recorded when its answer has been given
     * (QProc.Layer records it after the source returns, and the source is what sends the answer), so a client holding
     * the answer can look before the read lands.
     */
    private Rec reads(final long count) {
        final fURI analysis = this.socket.extend(ALL).addQ("analq");
        Obj reads = Machine.read(analysis).asRec().at(uri("read"));
        for (int waited = 0; waited < 5_000 && (reads.isNoObj() || reads.asRec().at(uri("count")).intValue() < count); waited += 50) {
            CommonUtil.sleepThread(50);
            reads = Machine.read(analysis).asRec().at(uri("read"));
        }
        assertFalse(reads.isNoObj(), "a read lands in the socket's analysis within five seconds");
        return reads.asRec();
    }

    /**
     * A PEER SHOWS IN THE NETWORK'S ANALYSIS AS SOON AS IT IS HELD: {@code peer} is a gauge of the peers the level
     * holds, sampled when the analysis is read, so a bare connect counts before any frame is sent, and a drop takes it
     * away again.
     */
    @Test
    void testAPeerShowsInTheNetworkAnalysis() throws Exception {
        wsServer.serve(this.socket, mtron("/mtron"));
        final Network network = Machine.current().network();
        Network.Helper.connect(network, this.socket.extend("mtron"), WS_CLIENT_TID);
        final Obj held = Machine.read(f("/network/analyzer/analysis/peer"));
        assertFalse(held.isNoObj(), "a connect shows in the analysis: " + Machine.read(f("/network/analyzer/analysis")));
        // at least the dialed peer: a loopback socket also admits the inbound end as a peer, on the server's own thread,
        // so whether it is there yet is a race this test does not pin
        assertTrue(held.realValue() >= 1.0d, "the peer this level holds is counted: " + held);
        network.drop(this.socket);
        final long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && network.peers().elements().count() > 0)
            Thread.sleep(50);
        assertEquals(0.0d, Machine.read(f("/network/analyzer/analysis/peer")).realValue(), 0.0d,
                "and a drop takes it away, with no event to tell the gauge");
    }

    /**
     * A NETWORK SWITCHED OFF COUNTS NO FRAME: noobj written at its analyzer removes it, so the wire's taps are handed
     * nothing to record into, while the frames themselves still flow. A rec written there switches it back on, with new
     * meters that count from zero and the peer gauge registered with them.
     */
    @Test
    void testANetworkSwitchedOffCountsNoFrame() {
        wsServer.serve(this.socket, mtron("/mtron"));
        final Network network = Machine.current().network();
        Network.Helper.connect(network, this.socket.extend("mtron"), WS_CLIENT_TID);
        checkEquality(LOG, jnt(2), network.submit(this.socket, ObjmtronSerializer.parse("1.plus(1)")), true);
        final Analyzer before = network.analyzer();
        Machine.write(f("/network/analyzer"), noobj());
        try {
            assertNull(network.analyzer(), "noobj removes the network's analyzer");
            final long received = before.analysis().at(uri("wire/recv")).orElse(jnt(0)).intValue();
            final long sent = before.analysis().at(uri("wire/sent")).orElse(jnt(0)).intValue();
            // a network switched off still carries frames: the round trip answers
            checkEquality(LOG, jnt(3), network.submit(this.socket, ObjmtronSerializer.parse("1.plus(2)")), true);
            assertEquals(received, before.analysis().at(uri("wire/recv")).orElse(jnt(0)).intValue(), "and counts none it receives");
            assertEquals(sent, before.analysis().at(uri("wire/sent")).orElse(jnt(0)).intValue(), "and none it sends");
        } finally {
            Machine.write(f("/network/analyzer"), rec());
        }
        final Analyzer after = network.analyzer();
        assertNotSame(before, after, "a rec written at it switches it back on, with meters of its own");
        assertTrue(after.analysis().at(uri("wire")).isNoObj(), "which count from zero: " + after.analysis());
        assertTrue(Machine.read(f("/network/analyzer/analysis/peer")).realValue() >= 1.0d, "the peer gauge registered with them");
        checkEquality(LOG, jnt(4), network.submit(this.socket, ObjmtronSerializer.parse("1.plus(3)")), true);
        assertTrue(after.analysis().at(uri("wire/sent")).orElse(jnt(0)).intValue() > 0, "and the frames counted again");
    }

    /**
     * AN OBSERVER'S ROUND TRIP ANNOUNCES NOTHING: a subscriber to the network's analysis that asks a peer for something
     * on each delivery sends as the observer, and the reply, which arrives on the socket library's own thread, is
     * counted as the observer's work too, so the network goes quiet after the change that started it. The route taken
     * is the child's, so the request the server receives counts on the child's wire, never on the dialer's.
     */
    @Test
    void testAnObserversRoundTripAnnouncesNothing() {
        final Machine root = Machine.current();
        wsServer.serve(this.socket, mtron("/mtron"));
        final Machine child = root.push(f("ws_server_observer"));
        wsServer.serve(this.socket, mtron("/child"));
        Machine.current(root);
        final Network network = root.network();
        Network.Helper.connect(network, this.socket.extend("child"), WS_CLIENT_TID);
        final AtomicInteger heard = new AtomicInteger();
        final List<Obj> answers = Collections.synchronizedList(new ArrayList<>());
        try {
            checkEquality(LOG, jnt(2), network.submit(this.socket, ObjmtronSerializer.parse("1.plus(1)")), true);
            Machine.write(f("/network/analyzer/analysis").addQ(SUBQ), instLambda((pub, inst) -> {
                heard.incrementAndGet();
                answers.add(network.submit(this.socket, ObjmtronSerializer.parse("1.plus(2)")));
                return noobj();
            }));
            Analyzer.Helper.incr(network.analyzer(), f("observer/kick"), 1L);
            // QUIET IS A COUNT THAT HOLDS FOR A WHOLE WINDOW once the trip was answered: a late delivery only moves it,
            // and a reply that announced its own trip would never let it hold
            final long deadline = System.currentTimeMillis() + 10_000L;
            int settled = -1;
            while (-1 == settled && System.currentTimeMillis() < deadline) {
                final int seen = heard.get();
                CommonUtil.sleepThread(4 * BasicAnalyzer.DEFAULT_STEP);
                if (!answers.isEmpty() && seen == heard.get())
                    settled = seen;
            }
            assertTrue(settled >= 1, "the network went quiet after the subscriber's round trip: heard " + heard.get() + ", answers " + answers);
            checkEquality(LOG, jnt(3), answers.getFirst(), true);
        } finally {
            Machine.write(f("/network/analyzer/analysis").addQ(SUBQ), noobj());
            network.drop(this.socket);
            Machine.current(child);
            child.pop();
        }
    }

    /**
     * A LEVEL GAUGES ONLY ITS OWN PEERS: a connection belongs to the machine that dialed it, never inherited by a pushed
     * frame (NetworkUnion), so the frame's network counts none while its parent holds one.
     */
    @Test
    void testAPushedLevelGaugesOnlyItsOwnPeers() {
        wsServer.serve(this.socket, mtron("/mtron"));
        final Machine root = Machine.current();
        Network.Helper.connect(root.network(), this.socket.extend("mtron"), WS_CLIENT_TID);
        assertTrue(Machine.read(f("~/network/analyzer/analysis/peer")).realValue() >= 1.0d, "the dialing level counts its peer");
        final Machine child = root.push(f("ws_server_peer_gauge"));
        try {
            assertEquals(0.0d, Machine.read(f("~/network/analyzer/analysis/peer")).realValue(), 0.0d,
                    "a pushed frame holds no peer of its own, and inherits none");
        } finally {
            Machine.current(child);
            child.pop();
        }
    }
}
