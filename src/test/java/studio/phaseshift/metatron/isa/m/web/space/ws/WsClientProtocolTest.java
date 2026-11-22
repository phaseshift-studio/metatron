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

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.network.Listener;
import studio.phaseshift.metatron.util.CommonUtil;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.PRINT_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.space.ws.handler.mtron_wsHandler.WS_MTRON_HANDLER_TID;
import static studio.phaseshift.metatron.isa.m.web.webInstSet.WEB_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A {@code ws_client} with a protocol hat: {@code protocol => mtron_ws} installs the mtron facade's
 * apply-and-respond {@code on_message} (instead of the {@code print} default), so the client can answer a
 * server-initiated request over the established socket — the client half of a symmetric peer.
 */
//@Isolated
//@Execution(ExecutionMode.SAME_THREAD)
public class WsClientProtocolTest extends AbstractMetatronTest {

    private Listener space;
    private int port;

    @AfterEach
    void tearDown() {
        if (null != this.space) {
            Machine.current().network().drop(f("ws://localhost:" + this.port));
            this.space.close();
            this.space = null;
        }
    }

    /**
     * Boot a local mtron server and dial it with the given {@code ws_client} expression, answering the client.
     */
    private Obj dial(final String clientExpr) {
        InstSet.importInstSet(WEB_ISA_TID);
        this.port = generatePort();
        this.space = wsServer.of(rec(mutableMap(
                uri(ORIGIN), uri("ws://localhost:" + this.port),
                uri(ROUTE), rec(uri("/mtron"), uri(WS_MTRON_HANDLER_TID.toString())))));
        CommonUtil.sleepThread(500);
        return ObjmtronSerializer.parse(clientExpr.replace("{{port}}", String.valueOf(this.port))).apply();
    }

    @Test
    void testProtocolMtronWsInstallsApplyAndRespondHandler() {
        final Obj client = this.dial("ws_client::[host=><ws://localhost:{{port}}/mtron>,protocol=>mtron_ws]@self");
        final java.util.Map<Obj, Obj> jvm = client.asRec().jvm();
        // the full mtron surface, not just the on_message lambda
        assertNotEquals(PRINT_INST_TID, jvm.get(uri(ON_MESSAGE)).tid().basePath(),
                "protocol=>mtron_ws must replace the print default on_message, got tid " + jvm.get(uri(ON_MESSAGE)).tid());
        assertTrue(jvm.containsKey(uri(ON_ERROR)), "protocol=>mtron_ws must install the on_error callback");
        assertTrue(jvm.containsKey(uri(STATE)), "protocol=>mtron_ws must install the state callback");
    }

    @Test
    void testNoProtocolKeepsPrintDefault() {
        final Obj client = this.dial("ws_client::[host=><ws://localhost:{{port}}/mtron>]@self");
        final Obj onMessage = client.asRec().jvm().get(uri(ON_MESSAGE));
        assertEquals(PRINT_INST_TID, onMessage.tid().basePath(),
                "a protocol-less ws_client must keep the print default on_message");
    }

    /**
     * A PROTOCOL CLIENT KEEPS WHAT A REQUEST WRITES. The far side asks the client to evaluate {@code 5.vid(</sys/hat/x>)},
     * framed the way a session's {@code send_recv} frames it ({@code reply(…, <tag>)}). The client evaluates a request
     * as it reads the frame, and it read every frame with {@code Obj.Helper.SELF_VID} bound, which stores nothing: it
     * answered {@code 5@</sys/hat/x/0x…>} and kept nothing at {@code /sys/hat/x}. The far side here is a bare socket
     * that evaluates nothing, so whatever is stored at {@code /sys/hat/x} the client stored.
     */
    @Test
    void testAProtocolClientKeepsWhatARequestWrites() throws Exception {
        InstSet.importInstSet(WEB_ISA_TID);
        final int farPort = generatePort();
        final String tag = "0x5e1f0001";
        final CompletableFuture<Void> started = new CompletableFuture<>();
        final CompletableFuture<WebSocket> session = new CompletableFuture<>();
        final CompletableFuture<String> answer = new CompletableFuture<>();
        final WebSocketServer far = new WebSocketServer(new InetSocketAddress("localhost", farPort)) {
            @Override
            public void onStart() {
                started.complete(null);
            }

            @Override
            public void onOpen(final WebSocket conn, final ClientHandshake handshake) {
                session.complete(conn);
            }

            @Override
            public void onMessage(final WebSocket conn, final String message) {
                if (message.contains(tag))
                    answer.complete(message);
            }

            @Override
            public void onClose(final WebSocket conn, final int code, final String reason, final boolean remote) {
                // the test hangs up
            }

            @Override
            public void onError(final WebSocket conn, final Exception ex) {
                started.completeExceptionally(ex);
            }
        };
        far.setReuseAddr(true);
        far.start();
        final fURI x = f("/sys/hat/x");
        try {
            started.get(5, TimeUnit.SECONDS);
            ObjmtronSerializer.parse("ws_client::[host=><ws://localhost:" + farPort + "/mtron>,protocol=>mtron_ws]@self").apply();
            session.get(10, TimeUnit.SECONDS).send("reply(5.vid(</sys/hat/x>),<" + tag + ">)");
            final String reply = answer.get(10, TimeUnit.SECONDS);
            LOG.debug("the protocol client answered the request: %s", reply);
            assertTrue(reply.contains("/sys/hat/x"), "the request must be answered, or this test proves nothing: " + reply);
            checkEquality(LOG, jnt(5).selfVID(x), Machine.read(x), true);
        } finally {
            Machine.current().network().drop(f("ws://localhost:" + farPort));
            Machine.write(x, noobj());
            far.stop(1000);
        }
    }
}
