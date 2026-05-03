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

package studio.phaseshift.metatron.isa.web.space.ws.server;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.space.ws.AbstractWSServerIntegrationTest;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.web.type.Content;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.server.mcp_mtron_wsServer.MCP_MTRON_WS_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.server.mcp_mtron_wsServer.WS_MCP_MTRON_SERVER_TYPE;
import static studio.phaseshift.metatron.isa.web.space.ws.server.mcp_wsServer.MCP_WS_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_SERVER_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_WEBSOCKET_TID;

/**
 * Integration tests for {@link mcp_mtron_wsServer}.
 * <p>
 * All tests require a live WebSocket environment because {@code mcp_mtron_wsServer}'s
 * built-in tools ({@code mtron_list_space}, {@code mtron_router_info}, {@code mtron_list_inst})
 * query the Router for registered spaces at invocation time.  Running against an empty-route
 * {@code wsSpace} (as {@code AbstractWSServerTest} provides) causes those look-ups to fail.
 * <p>
 * The {@link AbstractWSServerIntegrationTest} base class starts a real {@code wsSpace} with
 * the correct route table, ensuring the Router has a properly registered WS space available
 * for every test — both for direct handler invocations and live WebSocket round-trips.
 */
public class mcp_mtron_wsServerTest extends AbstractWSServerIntegrationTest {

    /**
     * Lightweight in-memory holding space for the direct-invocation server vid.
     * The integration wsSpace uses pattern {@code ws://#}; the server vid uses
     * {@code /test/#}.  A {@link memSpace} registered at {@code /test/#} bridges
     * the gap without spinning up a second network server.
     */
    private Space testHoldingSpace;

    /** Direct-invocation server reference (no WebSocket required for handler calls). */
    private WebSocketRec server;

    // ========================================
    // Integration test infrastructure
    // ========================================

    @Override
    protected studio.phaseshift.metatron.isa.web.space.ws.wsSpace createWSSpace() {
        final fURI hostUri = f("ws://localhost:" + generatePort());
        return studio.phaseshift.metatron.isa.web.space.ws.wsSpace.of(rec(
                uri(HOST), uri(hostUri.toString()),
                uri(PATTERN), uri("ws://#"),
                uri(ROUTE), rec(uri("/mcp-mtron"), uri(MCP_MTRON_WS_TID.toString()))
        ).jvm(), f("/sys/space/ws/mcp-mtron/test"));
    }

    /**
     * Creates a memSpace with pattern {@code /test/#} so the server vid is
     * resolvable in the Router, then instantiates the direct-invocation server.
     * Runs after {@link AbstractWSServerIntegrationTest#setupWsSpace()} so the
     * integration wsSpace (and its registered types) are already in the Router.
     */
    @BeforeEach
    public void createServer() {
        this.testHoldingSpace = memSpace.of(f("/test/#"), f("/sys/space/test/mcp-mtron-direct"));
        final fURI vid = f("/test/" + getClass().getSimpleName() + "/" + System.nanoTime());
        this.server = new mcp_mtron_wsServer(
                new LinkedHashMap<>(Map.of(
                        uri(IN), uri(Content.ContentType.APPLICATION_JSON.value),
                        uri(OUT), uri(Content.ContentType.APPLICATION_JSON.value))),
                vid);
    }

    @AfterEach
    public void destroyServer() {
        this.server = null;
        if (this.testHoldingSpace != null) {
            Router.global().removeSpace(this.testHoldingSpace.vid());
            this.testHoldingSpace.close();
            this.testHoldingSpace = null;
        }
    }

    // =========================================================
    // Type-level checks
    // =========================================================

    @Test
    public void testMcpMtronTidNamespace() {
        assertTrue(MCP_MTRON_WS_TID.toString().contains("wsspace"));
        assertTrue(MCP_MTRON_WS_TID.toString().contains("mcp_mtron_ws"));
    }

    @Test
    public void testMcpMtronTypeIsSubtypeOfMcpWs() {
        assertEquals(MCP_WS_TID, WS_MCP_MTRON_SERVER_TYPE.tid(),
                "mcp_mtron type should declare mcp_ws as its parent type");
    }

    @Test
    public void testMcpWsTypeDeclaresMcpServerAsParent() {
        // mcp_ws declares WS_SERVER_TID as its tid.
        assertEquals(WS_SERVER_TID, mcp_wsServer.WS_MCP_SERVER_TYPE.tid(),
                "mcp_ws type should declare ws_server as its parent type");
    }

    // =========================================================
    // Built-in tools presence (direct handler invocation)
    // =========================================================

    /** Fire ON_MESSAGE with a JSON-RPC Rec and return the response Rec. */
    private Rec mcpRequest(final Rec request) {
        final Obj response = server.at(uri(ON_MESSAGE)).apply(request);
        assertNotNull(response);
        assertFalse(response.isFail(), "response should not be a fail: " + response);
        assertTrue(response.isRec(), "response should be a Rec: " + response);
        return response.asRec();
    }

    @Test
    public void testBuiltinToolsArePresent() {
        // tools/list returns the tools rec — verify the 3 always-present tools exist.
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(1),
                uri("method"), uri("tools/list")));
        assertFalse(res.at(uri(RESULT)).isNoObj(), "result should be present");

        final Obj toolEntry = res.at(uri(RESULT)).asRec().at(uri("tools"));
        assertFalse(toolEntry.isNoObj(), "result should have a 'tools' key");
        // The tool list should not be empty — at minimum list_space, router_info, list_inst
        assertFalse(toolEntry.asLst().lstValue().isEmpty(), "tool list should not be empty");
    }

    @Test
    public void testToolsListContainsListSpace() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(2),
                uri("method"), uri("tools/list")));
        final boolean hasListSpace = res.at(uri(RESULT)).asRec().at(uri("tools")).asLst().lstValue()
                .stream()
                .anyMatch(t -> t.isRec() && str("mtron_list_space").equals(t.asRec().at(uri(NAME))));
        assertTrue(hasListSpace, "tools/list should include 'mtron_list_space'");
    }

    @Test
    public void testToolsListContainsRouterInfo() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(3),
                uri("method"), uri("tools/list")));
        final boolean hasRouterInfo = res.at(uri(RESULT)).asRec().at(uri("tools")).asLst().lstValue()
                .stream()
                .anyMatch(t -> t.isRec() && str("mtron_router_info").equals(t.asRec().at(uri(NAME))));
        assertTrue(hasRouterInfo, "tools/list should include 'mtron_router_info'");
    }

    @Test
    public void testToolsListContainsListInst() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(4),
                uri("method"), uri("tools/list")));
        final boolean hasListInst = res.at(uri(RESULT)).asRec().at(uri("tools")).asLst().lstValue()
                .stream()
                .anyMatch(t -> t.isRec() && str("mtron_list_inst").equals(t.asRec().at(uri(NAME))));
        assertTrue(hasListInst, "tools/list should include 'mtron_list_inst'");
    }

    // =========================================================
    // tools/call — individual tool execution (direct invocation)
    // =========================================================

    @Test
    public void testCallListSpace() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(10),
                uri("method"), uri("tools/call"),
                uri("params"), rec(
                        uri(NAME), str("mtron_list_space"),
                        uri("arguments"), rec())));
        assertFalse(res.at(uri(RESULT)).isNoObj(), "mtron_list_space should return a result");
        assertFalse(res.at(uri("error")).isRec(), "mtron_list_space should not error");
        // Result content is a Rec mapping space vids → space objects
        assertTrue(res.at(uri(RESULT)).isRec(), "mtron_list_space result should be a Rec");
    }

    @Test
    public void testCallRouterInfo() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(11),
                uri("method"), uri("tools/call"),
                uri("params"), rec(
                        uri(NAME), str("mtron_router_info"),
                        uri("arguments"), rec())));
        assertFalse(res.at(uri(RESULT)).isNoObj(), "mtron_router_info should return a result");
        assertFalse(res.at(uri("error")).isRec(), "mtron_router_info should not error");
        // tools/call wraps the output in {content:[{type:"text",text:"...serialized..."}]}
        final Obj contentList = res.at(uri(RESULT)).asRec().at(uri(CONTENT));
        assertFalse(contentList.isNoObj(), "result should have a content field");
        final String text = contentList.asLst().at(0).asRec().at(uri(TEXT)).toCleanString();
        assertTrue(text.contains("router_vid"), "router_info text should include router_vid");
        assertTrue(text.contains("space_count"), "router_info text should include space_count");
    }

    @Test
    public void testCallListInstNoDoc() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(12),
                uri("method"), uri("tools/call"),
                uri("params"), rec(
                        uri(NAME), str("mtron_list_inst"),
                        uri("arguments"), rec())));
        assertFalse(res.at(uri(RESULT)).isNoObj(), "mtron_list_inst should return a result");
        assertFalse(res.at(uri("error")).isRec(), "mtron_list_inst should not error");
    }

    @Test
    public void testCallListInstWithDocTrue() {
        // ObjSimpleJSONSerializer is URI-biased, so "true" → uri("true").
        // The list_inst tool accepts both bool(true) and uri("true").
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(13),
                uri("method"), uri("tools/call"),
                uri("params"), rec(
                        uri(NAME), str("mtron_list_inst"),
                        uri("arguments"), rec(uri("doc"), uri("true")))));
        assertFalse(res.at(uri(RESULT)).isNoObj(), "mtron_list_inst(doc=true) should return a result");
        assertFalse(res.at(uri("error")).isRec(), "mtron_list_inst(doc=true) should not error");
    }

    @Test
    public void testCallUnknownToolProducesError() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(99),
                uri("method"), uri("tools/call"),
                uri("params"), rec(uri(NAME), str("nonexistent_tool"), uri("arguments"), rec())));
        assertFalse(res.at(uri("error")).isNoObj(), "unknown tool should produce a JSON-RPC error");
    }

    // =========================================================
    // Caller-supplied tools override the defaults (direct invocation)
    // =========================================================

    @Test
    public void testCallerSuppliedToolsWin() {
        // If the caller provides their own tool rec, buildJvm() should not overwrite it.
        final fURI vid = f("/test/" + getClass().getSimpleName() + "/custom-" + System.nanoTime());
        final mcp_mtron_wsServer customServer = new mcp_mtron_wsServer(
                new LinkedHashMap<>(Map.of(
                        uri(IN), uri(Content.ContentType.APPLICATION_JSON.value),
                        uri(OUT), uri(Content.ContentType.APPLICATION_JSON.value),
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
                .anyMatch(t -> t.isRec() && str("mtron_list_space").equals(t.asRec().at(uri(NAME))));
        assertFalse(hasListSpace, "default tools should not be injected when caller provides a tool rec");
    }

    // =========================================================
    // Inherited MCP protocol round-trips (direct invocation)
    // =========================================================

    @Test
    public void testPingStillWorks() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(50),
                uri("method"), uri("ping")));
        assertFalse(res.at(uri(RESULT)).isNoObj(), "ping should return a result");
    }

    @Test
    public void testInitializeStillWorks() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(51),
                uri("method"), uri("initialize"),
                uri("params"), rec(
                        uri("protocolVersion"), str("2025-03-26"),
                        uri("clientInfo"), rec(uri(NAME), str("test"), uri("version"), str("0")))));
        final Rec result = res.at(uri(RESULT)).asRec();
        assertFalse(result.at(uri("capabilities")).isNoObj(), "initialize should return capabilities");
        assertFalse(result.at(uri("serverInfo")).isNoObj(), "initialize should return serverInfo");
    }

    @Test
    public void testUnknownMethodProducesError() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(99),
                uri("method"), uri("bogus/method")));
        assertFalse(res.at(uri("error")).isNoObj(), "unknown method should yield a JSON-RPC error");
        assertEquals(jnt(-32601), res.at(uri("error")).asRec().at(uri(CODE)));
    }

    @Test
    public void testNotificationProducesNoResponse() {
        final Obj result = server.at(uri(ON_MESSAGE)).apply(rec(
                uri(JSONRPC), uri("2.0"),
                uri("method"), uri("notifications/initialized")));
        assertTrue(result.isNoObj(), "notification should return noobj (no response sent)");
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
        // At least one of the metatron-native tools should appear in the JSON
        assertTrue(
                resp.contains("mtron_list_space") || resp.contains("mtron_router_info") || resp.contains("mtron_list_inst"),
                "tools/list should include at least one metatron-native tool");
    }

    @Test
    public void testCallListSpaceRoundTrip() throws Exception {
        connectToServer("/mcp-mtron");
        final String req = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"mtron_list_space\",\"arguments\":{}}}";
        final String resp = sendAndReceive(req);
        assertNotNull(resp, "mtron_list_space call should return a response");
        assertFalse(resp.contains("\"error\""), "mtron_list_space should not error: " + resp);
        assertTrue(resp.contains("\"result\""), "mtron_list_space should return a result");
    }

    @Test
    public void testCallRouterInfoRoundTrip() throws Exception {
        connectToServer("/mcp-mtron");
        final String req = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"mtron_router_info\",\"arguments\":{}}}";
        final String resp = sendAndReceive(req);
        assertNotNull(resp, "mtron_router_info call should return a response");
        assertFalse(resp.contains("\"error\""), "mtron_router_info should not error: " + resp);
        assertTrue(resp.contains("\"result\""), "mtron_router_info should return a result");
    }

    @Test
    public void testCallListInstRoundTrip() throws Exception {
        connectToServer("/mcp-mtron");
        final String req = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"mtron_list_inst\",\"arguments\":{}}}";
        final String resp = sendAndReceive(req);
        assertNotNull(resp, "mtron_list_inst call should return a response");
        assertFalse(resp.contains("\"error\""), "mtron_list_inst should not error: " + resp);
        assertTrue(resp.contains("\"result\""), "mtron_list_inst should return a result");
    }

    @Test
    public void testPingRoundTrip() throws Exception {
        connectToServer("/mcp-mtron");
        final String resp = sendAndReceive("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"ping\"}");
        assertNotNull(resp);
        assertTrue(resp.contains("\"result\""), "ping should return result");
    }
}
