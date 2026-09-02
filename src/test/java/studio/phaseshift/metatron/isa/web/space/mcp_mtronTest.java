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

package studio.phaseshift.metatron.isa.web.space;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.space.http.httpSpace;
import studio.phaseshift.metatron.isa.web.space.ws.wsSpace;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.isa.web.type.mcpMetatronBuilder;
import studio.phaseshift.metatron.isa.web.type.mcpServer;
import studio.phaseshift.metatron.util.CommonUtil;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
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
import static studio.phaseshift.metatron.isa.web.type.mcpMetatronBuilder.MCP_MTRON_SERVER_TID;
import static studio.phaseshift.metatron.isa.web.type.mcpMetatronBuilder.MCP_MTRON_SERVER_TYPE;
import static studio.phaseshift.metatron.isa.web.webInstSet.*;

/**
 * Integration tests for the transport-agnostic {@code mcp_mtron} server,
 * exercised over both transports — Streamable HTTP (via {@link httpSpace}) and
 * WebSocket (via {@link wsSpace}).
 * <p>
 * The direct-invocation (protocol-level) tests are inherited from
 * {@link AbstractMcpMtronHandlerTest}.  This class adds the type-level checks
 * and the live round-trips proving that both spaces wrap the same {@code
 * mcp_server} in their respective transport handler.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class mcp_mtronTest extends AbstractMcpMtronHandlerTest {

    private httpSpace httpSpace;
    private String httpHost;
    private int httpPort;

    private wsSpace wsSpace;
    private String wsHost;
    private int wsPort;

    private HttpClient httpClient;
    private WebSocket webSocket;

    // ========================================
    // Transport infrastructure
    // ========================================

    private httpSpace createHTTPSpace() {
        final fURI hostUri = f("http://localhost:" + generatePort());
        return httpSpace.of(rec(
                uri(HOST), uri(hostUri.toString()),
                uri(PATTERN), uri("http://#"),
                uri(ROUTE), rec(uri("/mcp"), uri(MCP_MTRON_SERVER_TID.toString()))
        ), f("/sys/space/http/mcp-mtron/test"));
    }

    private wsSpace createWSSpace() {
        final fURI hostUri = f("ws://localhost:" + generatePort());
        return wsSpace.of(rec(
                uri(HOST), uri(hostUri.toString()),
                uri(PATTERN), uri("ws://#"),
                uri(ROUTE), rec(uri("/mcp-mtron"), uri(MCP_MTRON_SERVER_TID.toString()))
        ).jvm(), f("/sys/space/ws/mcp-mtron/test"));
    }

    @BeforeAll
    public void setupSpaces() {
        InstSet.importInstSet(WEB_ISA_TID);
        this.httpSpace = createHTTPSpace();
        assertNotNull(this.httpSpace, "httpSpace should be created");
        this.httpHost = this.httpSpace.at(HOST).uriValue().host();
        this.httpPort = this.httpSpace.at(HOST).uriValue().port();

        this.wsSpace = createWSSpace();
        assertNotNull(this.wsSpace, "wsSpace should be created");
        assertTrue(this.wsSpace.at(HOST).uriValue().test(this.wsSpace.pattern()), "the space host should match the space pattern");
        this.wsHost = this.wsSpace.at(HOST).uriValue().host();
        this.wsPort = this.wsSpace.at(HOST).uriValue().port();

        CommonUtil.sleepThread(100);
        this.httpClient = HttpClient.newHttpClient();
    }

    @AfterAll
    public void teardownSpaces() {
        if (this.httpSpace != null) {
            Router.global().removeSpace(this.httpSpace.vid());
            this.httpSpace.close();
            this.httpSpace = null;
        }
        if (this.wsSpace != null) {
            Router.global().removeSpace(this.wsSpace.vid());
            this.wsSpace.close();
            this.wsSpace = null;
        }
        Router.global().removeSpace(WEB_ISA_TID);
        if (this.httpClient != null) {
            this.httpClient.close();
            this.httpClient = null;
        }
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

    // ========================================
    // Type-level checks
    // ========================================

    @Test
    public void testMcpMtronTypeIsAMcpServer() {
        assertEquals(MCP_SERVER_TID, MCP_MTRON_SERVER_TYPE.tid(),
                "mcp_mtron type should declare mcp_server as its parent type");
    }

    @Test
    public void testServerIsAMcpMtron() {
        final fURI vid = createTestVid();
        final mcpServer server = new mcpServer(mcpMetatronBuilder.build(
                new LinkedHashMap<>(Map.of(
                        uri(IN), uri(MIME.MIMEType.APPLICATION_JSON.value),
                        uri(OUT), uri(MIME.MIMEType.APPLICATION_JSON.value))),
                vid), MCP_SERVER_TID, vid);
        assertTrue(server.test(MCP_MTRON_SERVER_TYPE), "server should be a mcp_mtron::T");
        assertTrue(server.test(MCP_SERVER_TYPE), "server should be a mcp_server::T");
    }

    // ========================================
    // Caller-supplied tools override the defaults (direct invocation)
    // ========================================

    @Test
    public void testCallerSuppliedToolsWin() {
        // If the caller provides their own tool rec, build() should not overwrite it.
        final fURI vid = f("/test/" + getClass().getSimpleName() + "/custom-" + System.nanoTime());
        final mcpServer customServer = new mcpServer(mcpMetatronBuilder.build(
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
                vid), MCP_SERVER_TID, vid);

        final Obj response = customServer.handleMessage(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(1),
                uri("method"), uri("tools/list")));
        assertTrue(response.isRec());
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

    // ========================================
    // Live Streamable-HTTP round-trips
    // ========================================

    private String baseUrl() {
        return "http://" + this.httpHost + ":" + this.httpPort;
    }

    private HttpResponse<String> httpPost(final String path, final String body, final String sessionId)
            throws IOException, InterruptedException {
        final HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(java.net.URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(2));
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return this.httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> httpDelete(final String path, final String sessionId)
            throws IOException, InterruptedException {
        final HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(java.net.URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(2))
                .DELETE();
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return this.httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    public void testHttpPostInitialize() throws Exception {
        final var resp = httpPost("/mcp",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"," +
                        "\"params\":{\"protocolVersion\":\"2025-03-26\"," +
                        "\"clientInfo\":{\"name\":\"test\",\"version\":\"0\"}}}", null);
        assertEquals(200, resp.statusCode(), "initialize should return 200");
        assertTrue(resp.body().contains("\"result\""), "should have a result");
        assertTrue(resp.body().contains("\"capabilities\""), "should have capabilities");
    }

    @Test
    public void testHttpPostToolsList() throws Exception {
        final var resp = httpPost("/mcp",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}", null);
        assertEquals(200, resp.statusCode(), "tools/list should return 200");
        assertTrue(resp.body().contains("\"result\""), "should have a result");
        assertTrue(resp.body().contains("m_inst_list_space") &&
                        resp.body().contains("m_inst_router_info") &&
                        resp.body().contains("m_inst_find_inst"),
                "tools/list should include the metatron-native tools");
    }

    @Test
    public void testHttpPostCallListSpace() throws Exception {
        final var resp = httpPost("/mcp",
                "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\"," +
                        "\"params\":{\"name\":\"m_inst_list_space\",\"arguments\":{}}}", null);
        assertEquals(200, resp.statusCode());
        assertFalse(resp.body().contains("\"error\""), "list_space should not error: " + resp.body());
    }

    @Test
    public void testHttpPostCallRouterInfo() throws Exception {
        final var resp = httpPost("/mcp",
                "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\"," +
                        "\"params\":{\"name\":\"m_inst_router_info\",\"arguments\":{}}}", null);
        assertEquals(200, resp.statusCode());
        assertFalse(resp.body().contains("\"error\""), "router_info should not error");
    }

    @Test
    public void testHttpPostPing() throws Exception {
        final var resp = httpPost("/mcp",
                "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"ping\"}", null);
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"result\""), "ping should return result");
    }

    @Test
    public void testHttpPostUnknownMethod() throws Exception {
        final var resp = httpPost("/mcp",
                "{\"jsonrpc\":\"2.0\",\"id\":99,\"method\":\"bogus/method\"}", null);
        assertTrue(resp.body().contains("\"error\""), "unknown method should produce an error");
    }

    @Test
    public void testHttpDeleteSession() throws Exception {
        final var resp = httpDelete("/mcp", "test-session");
        assertEquals(204, resp.statusCode(), "DELETE should return 204 No Content");
    }

    // ========================================
    // Live WebSocket round-trips
    // ========================================

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
