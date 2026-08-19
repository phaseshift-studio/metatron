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

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.space.AbstractMcpMtronHandlerTest;
import studio.phaseshift.metatron.isa.web.space.ws.handler.mcp_mtron_wsHandler;
import studio.phaseshift.metatron.isa.web.space.ws.handler.mcp_wsHandler;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.util.CommonUtil;

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.handler.mcp_mtron_wsHandler.WS_MCP_MTRON_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.handler.mcp_mtron_wsHandler.WS_MCP_MTRON_HANDLER_TYPE;
import static studio.phaseshift.metatron.isa.web.space.ws.handler.mcp_wsHandler.WS_MCP_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.handler.mcp_wsHandler.WS_MCP_HANDLER_TYPE;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/**
 * Integration tests for {@link mcp_mtron_wsHandler}.
 * <p>
 * The direct-invocation (protocol-level) tests are inherited from
 * {@link AbstractMcpMtronHandlerTest}.  This class only adds the WS-specific
 * type checks, the caller-supplied-tools regression test, and the live
 * WebSocket round-trips against a real {@code wsSpace} (whose route table
 * registers the WS space so {@code list_space}/{@code router_info} resolve).
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class mcp_mtron_wsHandlerTest extends AbstractMcpMtronHandlerTest {

    private wsSpace space;
    private String wsHost;
    private int wsPort;
    private HttpClient httpClient;
    private WebSocket webSocket;

    /**
     * Direct-invocation WS handler reference (used by the WS type checks).
     */
    private mcp_mtron_wsHandler server;

    // ========================================
    // WebSocket transport infrastructure
    // ========================================

    private wsSpace createWSSpace() {
        final fURI hostUri = f("ws://localhost:" + generatePort());
        return wsSpace.of(rec(
                uri(HOST), uri(hostUri.toString()),
                uri(PATTERN), uri("ws://#"),
                uri(ROUTE), rec(uri("/mcp-mtron"), uri(WS_MCP_MTRON_HANDLER_TID.toString()))
        ).jvm(), f("/sys/space/ws/mcp-mtron/test"));
    }

    @BeforeAll
    public void setupWsSpace() {
        InstSet.importInstSet(WEB_ISA_TID);
        this.space = createWSSpace();
        assertNotNull(this.space, "wsSpace should be created");
        assertTrue(this.space.at(HOST).uriValue().test(this.space.pattern()), "the space host should match the space pattern");

        this.wsHost = this.space.at(HOST).uriValue().host();
        this.wsPort = this.space.at(HOST).uriValue().port();

        CommonUtil.sleepThread(10);

        this.httpClient = HttpClient.newHttpClient();
    }

    @AfterAll
    public void teardownWsSpace() {
        if (this.space != null) {
            Router.global().removeSpace(this.space.vid());
            Router.global().removeSpace(WEB_ISA_TID);
            this.space.close();
            this.space = null;
        }
        if (this.httpClient != null) {
            this.httpClient.close();
            this.httpClient = null;
        }
    }

    /**
     * Instantiate the WS handler for the type-conformance checks.  Runs after the
     * inherited {@code @BeforeEach setupTestSpace()} so the WEB types are registered.
     */
    @BeforeEach
    public void createWsServer() {
        this.server = new mcp_mtron_wsHandler(
                new LinkedHashMap<>(Map.of(
                        uri(IN), uri(MIME.MIMEType.APPLICATION_JSON.value),
                        uri(OUT), uri(MIME.MIMEType.APPLICATION_JSON.value))),
                createTestVid());
    }

    @AfterEach
    public void destroyWsServer() {
        this.server = null;
    }

    @AfterEach
    public void closeClientWebSocket() {
        if (this.webSocket != null) {
            try {
                this.webSocket.sendClose(10, "test complete");
            } catch (final Exception ignored) {
            }
            this.webSocket = null;
        }
    }

    private final AtomicReference<String> lastResponse = new AtomicReference<>(null);
    private volatile CountDownLatch responseLatch = new CountDownLatch(1);

    private WebSocket connectToServer(final String path) throws Exception {
        final java.net.URI wsUri = java.net.URI.create("ws://" + this.wsHost + ":" + this.wsPort + path);
        final CountDownLatch openLatch = new CountDownLatch(1);
        final AtomicReference<WebSocket> wsRef = new AtomicReference<>();
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();

        this.lastResponse.set(null);
        this.responseLatch = new CountDownLatch(1);

        this.httpClient.newWebSocketBuilder()
                .buildAsync(wsUri, new WebSocket.Listener() {
                    @Override
                    public void onOpen(final WebSocket webSocket) {
                        wsRef.set(webSocket);
                        webSocket.request(1);
                        openLatch.countDown();
                    }

                    @Override
                    public CompletionStage<?> onText(final WebSocket webSocket,
                                                     final CharSequence data,
                                                     final boolean last) {
                        lastResponse.set(data.toString());
                        responseLatch.countDown();
                        webSocket.request(1);
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }

                    @Override
                    public void onError(final WebSocket webSocket, final Throwable error) {
                        errorRef.set(error);
                        openLatch.countDown();
                        responseLatch.countDown();
                    }
                });

        final boolean opened = openLatch.await(getWsTimeoutSeconds(), TimeUnit.SECONDS);
        assertTrue(opened, "websocket connection should open within timeout");
        assertNull(errorRef.get(), "websocket connection should not error: " + errorRef.get());

        this.webSocket = wsRef.get();
        assertNotNull(this.webSocket, "websocket should be connected");
        return this.webSocket;
    }

    private String sendAndReceive(final String message) throws Exception {
        assertNotNull(this.webSocket, "websocket must be connected before sending");
        this.responseLatch = new CountDownLatch(1);
        this.lastResponse.set(null);
        this.webSocket.sendText(message, true);
        final boolean received = this.responseLatch.await(getWsTimeoutSeconds(), TimeUnit.SECONDS);
        if (!received) return null;
        return this.lastResponse.get();
    }

    private int getWsTimeoutSeconds() {
        return 5;
    }

    // =========================================================
    // Type-level checks
    // =========================================================

    @Test
    public void testMcpMtronTIDNamespace() {
        assertTrue(WS_MCP_MTRON_HANDLER_TID.toString().contains("wsspace"));
        assertTrue(WS_MCP_MTRON_HANDLER_TID.toString().contains("mcp_mtron_ws"));
    }

    // todo: test type, not tid
    @Test
    public void testMcpMtronTypeIsSubtypeOfMcpWs() {
        assertEquals(WS_MCP_HANDLER_TID, WS_MCP_MTRON_HANDLER_TYPE.tid(),
                "mcp_mtron type should declare mcp_ws as its parent type");
    }

    @Test
    public void testMcpWsTypeDeclaresMcpServerAsParent() {
        // mcp_ws declares WS_SERVER_TID as its tid.
        assertEquals(WS_HANDLER_TID, mcp_wsHandler.WS_MCP_HANDLER_TYPE.tid(),
                "mcp_ws type should declare ws_server as its parent type");
        assertTrue(this.server.test(WS_MCP_HANDLER_TYPE), "websocket server should be a wshandler::T");
        assertTrue(this.server.test(WS_MCP_MTRON_HANDLER_TYPE), "websocket server should be a mcp_mtron_ws::T");
    }

    // =========================================================
    // Caller-supplied tools override the defaults (direct invocation)
    // =========================================================

    @Test
    public void testCallerSuppliedToolsWin() {
        // If the caller provides their own tool rec, buildJvm() should not overwrite it.
        final fURI vid = f("/test/" + getClass().getSimpleName() + "/custom-" + System.nanoTime());
        final mcp_mtron_wsHandler customServer = new mcp_mtron_wsHandler(
                new LinkedHashMap<>(Map.of(
                        uri(IN), uri(MIME.MIMEType.APPLICATION_JSON.value),
                        uri(OUT), uri(MIME.MIMEType.APPLICATION_JSON.value),
                        uri(TOOL), rec(uri("custom_only"),
                                instC(
                                        f("custom_only")
                                                .dom(studio.phaseshift.metatron.furi.fURI.Singleton.ALL.maybe())
                                                .rng(studio.phaseshift.metatron.furi.fURI.Singleton.ALL.maybe()),
                                        lst(),
                                        (lhs, inst) -> str("custom"))))),
                vid);

        final Obj response = customServer.at(uri(ON_MESSAGE)).apply(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(1),
                uri("method"), uri("tools/list")));
        assertTrue(response.isRec());
        LOG.error(response.asRec());
        final boolean hasCustom = response.asRec().at(uri(RESULT)).asRec().at(uri("tools")).asLst().lstValue()
                .stream()
                .anyMatch(t -> t.isRec() && str("custom_only").equals(t.asRec().at(uri(NAME))));
        assertTrue(hasCustom, "caller-supplied 'custom_only' tool should be present");
        // Default tools should NOT be injected when caller provides own tool rec
        final boolean hasListSpace = response.asRec().at(uri(RESULT)).asRec().at(uri("tools")).asLst().lstValue()
                .stream()
                .anyMatch(t -> t.isRec() && str("m_inst_list_space").equals(t.asRec().at(uri(NAME))));
        assertFalse(hasListSpace, "default tools should not be injected when caller provides a tool rec");
    }

    // =========================================================
    // Live WebSocket round-trips
    // =========================================================

    @Test
    public void testInitializeRoundTrip() throws Exception {
        connectToServer("/mcp-mtron");
        final String req = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2025-03-26\","
                + "\"clientInfo\":{\"name\":\"test\",\"version\":\"0\"}}}";
        final String resp = sendAndReceive(req);
        assertNotNull(resp, "should receive a response");
        assertTrue(resp.contains("\"result\""), "initialize response should have result");
        assertTrue(resp.contains("\"capabilities\""), "initialize response should have capabilities");
    }

    @Test
    public void testToolsListRoundTrip() throws Exception {
        connectToServer("/mcp-mtron");
        final String resp = sendAndReceive("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
        assertNotNull(resp, "tools/list should return a response");
        assertTrue(resp.contains("\"result\""), "tools/list should have a result");
        // The metatron-native tools should appear under their tid-derived names
        assertTrue(
                resp.contains("m_inst_list_space") && resp.contains("m_inst_router_info") && resp.contains("m_inst_find_inst"),
                "tools/list should include the metatron-native tools");
    }

    @Test
    public void testCallListSpaceRoundTrip() throws Exception {
        connectToServer("/mcp-mtron");
        final String req = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"m_inst_list_space\",\"arguments\":{}}}";
        final String resp = sendAndReceive(req);
        assertNotNull(resp, "list_space call should return a response");
        assertFalse(resp.contains("\"error\""), "list_space should not error: " + resp);
        assertTrue(resp.contains("\"result\""), "list_space should return a result");
    }

    @Test
    public void testCallRouterInfoRoundTrip() throws Exception {
        connectToServer("/mcp-mtron");
        final String req = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"m_inst_router_info\",\"arguments\":{}}}";
        final String resp = sendAndReceive(req);
        assertNotNull(resp, "router_info call should return a response");
        assertFalse(resp.contains("\"error\""), "router_info should not error: " + resp);
        assertTrue(resp.contains("\"result\""), "router_info should return a result");
    }

    @Test
    public void testCallFindInstRoundTrip() throws Exception {
        connectToServer("/mcp-mtron");
        final String req = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"m_inst_find_inst\",\"arguments\":{\"pattern\":\"plus\"}}}";
        final String resp = sendAndReceive(req);
        assertNotNull(resp, "find_inst call should return a response");
        assertFalse(resp.contains("\"error\""), "find_inst should not error: " + resp);
        assertTrue(resp.contains("\"result\""), "find_inst should return a result");
    }

    @Test
    public void testPingRoundTrip() throws Exception {
        connectToServer("/mcp-mtron");
        final String resp = sendAndReceive("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"ping\"}");
        assertNotNull(resp);
        assertTrue(resp.contains("\"result\""), "ping should return result");
    }
}
