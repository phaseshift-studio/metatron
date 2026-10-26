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

package studio.phaseshift.metatron.isa.web.space.ws;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.network.Listener;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.NAME;
import static studio.phaseshift.metatron.Tokens.ORIGIN;
import static studio.phaseshift.metatron.Tokens.ROUTE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.handler.mtron_wsHandler.WS_MTRON_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.wsServer.WS_CLIENT_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.wsServer.WS_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

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
        network.disconnect(this.socket);
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
        Machine.current().network().connect(this.socket.extend("mtron"), WS_CLIENT_TID);
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
        root.network().connect(this.socket.extend("mtron"), WS_CLIENT_TID);
        checkEquality(LOG, root.network().submit(this.socket, code), str("root"), true);
        root.network().disconnect(this.socket);
        root.network().connect(this.socket.extend("child"), WS_CLIENT_TID);
        checkEquality(LOG, root.network().submit(this.socket, code), str("child"), true);
        root.network().disconnect(this.socket);
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

    @Test
    void testTheInboundPeerIsHeldByTheRoutesMachine() {
        final Machine root = Machine.current();
        final Machine child = root.push(f("ws_server_child"));
        try {
            final Listener.Route route = new Listener.Route(child.network(), f("/drstynx"), uri(WS_MTRON_HANDLER_TID.toString()));
            final WebSocketRec session = new WebSocketRec(new LinkedHashMap<>(), WS_HANDLER_TID, null);
            final fURI dialed = f("ws://10.0.0.7:41234/drstynx?name=lab");
            assertEquals(dialed, wsServer.admit(route, dialed, session));
            final fURI key = Network.Helper.keyOf(dialed);
            final Obj peer = child.network().peerAt(key);
            assertTrue(peer.isRec(), "the peer joins the machine whose route the connection took");
            checkEquality(LOG, peer.asRec().at(uri(NAME)), str("lab"), true);
            checkEquality(LOG, root.network().peerAt(key), noobj(), true);
            child.network().disconnect(dialed);
            checkEquality(LOG, child.network().peerAt(key), noobj(), true);
        } finally {
            child.pop();
        }
    }
}
