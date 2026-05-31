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

package studio.phaseshift.metatron.isa.web.space.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.web.space.http.handler.mcp_mtron_httpHandler;
import studio.phaseshift.metatron.isa.web.type.mMCPUtility;
import studio.phaseshift.metatron.isa.web.type.mcpServer;
import studio.phaseshift.metatron.isa.web.type.MIME;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.http.handler.mcp_httpHandler.HTTP_MCP_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.space.http.handler.mcp_mtron_httpHandler.HTTP_MTRON_MCP_TID;
import static studio.phaseshift.metatron.isa.web.space.http.handler.mcp_mtron_httpHandler.HTTP_MTRON_MCP_TYPE;

/**
 * Integration tests for {@link mcp_mtron_httpHandler}.
 * Uses the {@link AbstractHTTPServerIntegrationTest} base class to start a real
 * httpSpace with a handler route, then sends Streamable HTTP requests via HttpClient.
 */
public class mcp_mtron_httpHandlerTest extends AbstractHTTPServerIntegrationTest {

    private Space testHoldingSpace;
    private mcpServer mcp;

    // ========================================
    // Integration test infrastructure
    // ========================================

    @Override
    protected httpSpace createHTTPSpace() {
        final fURI hostUri = f("http://localhost:" + generatePort());
        return httpSpace.of(rec(
                uri(HOST), uri(hostUri.toString()),
                uri(PATTERN), uri("http://#"),
                uri(ROUTE), rec(uri("/mcp"), uri(HTTP_MTRON_MCP_TID.toString()))
        ), f("/sys/space/http/mcp-mtron/test"));
    }

    @BeforeEach
    public void createHandler() {
        this.testHoldingSpace = memSpace.of(f("/test/#"), f("/sys/space/test/http-mcp-direct"));
        final fURI vid = f("/test/" + getClass().getSimpleName() + "/" + System.nanoTime());
        this.mcp = new mcpServer(mMCPUtility.buildMetatronTools(
                new LinkedHashMap<>(Map.of(
                        uri(IN), uri(MIME.MIMEType.APPLICATION_JSON.value),
                        uri(OUT), uri(MIME.MIMEType.APPLICATION_JSON.value))), vid),
                mcpServer.MCP_SERVER_TID, vid);
    }

    @AfterEach
    public void destroyHandler() {
        this.mcp = null;
        if (this.testHoldingSpace != null) {
            Router.global().removeSpace(this.testHoldingSpace.vid());
            this.testHoldingSpace.close();
            this.testHoldingSpace = null;
        }
    }

    // ========================================
    // Direct handler invocation helper
    // ========================================

    private Rec mcpRequest(final Rec request) {
        final Obj response = this.mcp.handleMessage(request);
        assertNotNull(response);
        assertFalse(response.isFail(), "response should not be a fail: " + response);
        assertTrue(response.isRec(), "response should be a Rec: " + response);
        return response.asRec();
    }

    // =========================================================
    // Type-level checks
    // =========================================================

    @Test
    public void testMcpHttpTIDNamespace() {
        assertTrue(HTTP_MTRON_MCP_TID.toString().contains("httpspace"));
        assertTrue(HTTP_MTRON_MCP_TID.toString().contains("mcp_mtron_http"));
    }

    @Test
    public void testMcpHttpTypeIsSubtypeOfMcpHttp() {
        assertEquals(HTTP_MCP_HANDLER_TID, HTTP_MTRON_MCP_TYPE.tid(),
                "mcp_mtron_http type should declare mcp_http as its parent type");
    }

    // =========================================================
    // Built-in tools presence (direct handler invocation)
    // =========================================================

    @Test
    public void testBuiltinToolsArePresent() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(1),
                uri("method"), uri("tools/list")));
        assertFalse(res.at(uri(RESULT)).isNoObj(), "result should be present");
        final Obj toolEntry = res.at(uri(RESULT)).asRec().at(uri("tools"));
        assertFalse(toolEntry.isNoObj(), "result should have a 'tools' key");
        assertFalse(toolEntry.asLst().lstValue().isEmpty(), "tool list should not be empty");
    }

    @Test
    public void testToolsListContainsListSpace() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(2),
                uri("method"), uri("tools/list")));
        final boolean has = res.at(uri(RESULT)).asRec().at(uri("tools")).asLst().lstValue()
                .stream().anyMatch(t -> t.isRec() && str("list_space").equals(t.asRec().at(uri(NAME))));
        assertTrue(has, "tools/list should include 'list_space'");
    }

    @Test
    public void testToolsListContainsRouterInfo() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(3),
                uri("method"), uri("tools/list")));
        final boolean has = res.at(uri(RESULT)).asRec().at(uri("tools")).asLst().lstValue()
                .stream().anyMatch(t -> t.isRec() && str("router_info").equals(t.asRec().at(uri(NAME))));
        assertTrue(has, "tools/list should include 'router_info'");
    }

    @Test
    public void testToolsListContainsListInst() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(4),
                uri("method"), uri("tools/list")));
        final boolean has = res.at(uri(RESULT)).asRec().at(uri("tools")).asLst().lstValue()
                .stream().anyMatch(t -> t.isRec() && str("find_inst").equals(t.asRec().at(uri(NAME))));
        assertTrue(has, "tools/list should include 'find_inst'");
    }

    // =========================================================
    // tools/call — individual tool execution
    // =========================================================

    @Test
    public void testCallListSpace() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(10),
                uri("method"), uri("tools/call"),
                uri("params"), rec(uri(NAME), str("list_space"), uri("arguments"), rec())));
        assertFalse(res.at(uri(RESULT)).isNoObj(), "list_space should return a result");
        assertFalse(res.at(uri("error")).isRec(), "list_space should not error");
    }

    @Test
    public void testCallRouterInfo() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(11),
                uri("method"), uri("tools/call"),
                uri("params"), rec(uri(NAME), str("router_info"), uri("arguments"), rec())));
        assertFalse(res.at(uri(RESULT)).isNoObj(), "router_info should return a result");
        final Obj contentList = res.at(uri(RESULT)).asRec().at(uri(CONTENT));
        assertFalse(contentList.isNoObj(), "result should have a content field");
        final String text = contentList.asLst().at(0).asRec().at(uri(TEXT)).toCleanString();
        assertTrue(text.contains("router_vid"), "router_info text should include router_vid");
    }

    @Test
    public void testCallFindInst() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(12),
                uri("method"), uri("tools/call"),
                uri("params"), rec(uri(NAME), str("find_inst"), uri("arguments"), rec(uri(PATTERN),uri("plus")))));
        assertFalse(res.at(uri(RESULT)).isNoObj(), "find_inst should return a result");
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
    // MCP protocol: initialize, ping, notifications
    // =========================================================

    @Test
    public void testPing() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(50),
                uri("method"), uri("ping")));
        assertFalse(res.at(uri(RESULT)).isNoObj(), "ping should return a result");
    }

    @Test
    public void testInitialize() {
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
        final Obj result = this.mcp.handleMessage(rec(
                uri(JSONRPC), str("2.0"),
                uri("method"), uri("notifications/initialized")));
        assertTrue(result.isNoObj(), "notification should return noobj");
    }

    // =========================================================
    // Live HTTP round-trips
    // =========================================================

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
        assertTrue(resp.body().contains("list_space") ||
                   resp.body().contains("router_info") ||
                   resp.body().contains("list_inst"),
                "tools/list should include at least one metatron-native tool");
    }

    @Test
    public void testHttpPostCallListSpace() throws Exception {
        final var resp = httpPost("/mcp",
                "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\"," +
                "\"params\":{\"name\":\"list_space\",\"arguments\":{}}}", null);
        assertEquals(200, resp.statusCode());
        assertFalse(resp.body().contains("\"error\""), "list_space should not error: " + resp.body());
    }

    @Test
    public void testHttpPostCallRouterInfo() throws Exception {
        final var resp = httpPost("/mcp",
                "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\"," +
                "\"params\":{\"name\":\"router_info\",\"arguments\":{}}}", null);
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
}
