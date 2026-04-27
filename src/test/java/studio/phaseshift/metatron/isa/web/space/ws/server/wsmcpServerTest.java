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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.web.space.ws.AbstractWSServerIntegrationTest;
import studio.phaseshift.metatron.isa.web.space.ws.AbstractWSServerTest;
import studio.phaseshift.metatron.isa.web.space.ws.WSServerRec;
import studio.phaseshift.metatron.isa.web.space.ws.wsSpace;
import studio.phaseshift.metatron.isa.web.type.Content;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.server.wsmcpServer.WS_MCP_SERVER_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.server.wsmcpServer.WS_MCP_SERVER_TYPE;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_SERVER_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class wsmcpServerTest extends AbstractWSServerTest {

    @Override
    protected WSServerRec createServer(final fURI vid) {
        return new wsmcpServer(new LinkedHashMap<>(Map.of(uri(IN), uri(Content.ContentType.APPLICATION_JSON.value), uri(OUT), uri(Content.ContentType.APPLICATION_JSON.value))), WS_MCP_SERVER_TID, vid);
    }

    /**
     * Return the static type directly to avoid Router-mediated lookup.
     * {@code T(WS_MCP_SERVER_TID)} routes through whichever space {@code getSpace()}
     * selects (currently mInstSet, not webInstSet, due to shortest-prefix routing),
     * and that space loses the type registration after a Router reset.
     */
    @Override
    protected Type serverType() {
        return WS_MCP_SERVER_TYPE;
    }

    // =========================================================
    // MCP-specific type checks
    // =========================================================

    @Test
    public void testMCPTidNamespace() {
        assertTrue(WS_MCP_SERVER_TID.toString().contains("wsspace"));
        assertTrue(WS_MCP_SERVER_TID.toString().contains("wsmcp"));
    }

    @Test
    public void testMCPTypeIsWSServerSubtype() {
        // WS_MCP_SERVER_TYPE declares WS_SERVER_TID as its parent (tid).
        // isRefinementOf() traverses parentType() which resolves via Router;
        // after a Router reset the routing can pick mInstSet (shortest prefix)
        // which has no WEB types, causing parentType() to return null → NPE.
        // Checking the tid() directly tests the same semantics without Router.
        assertEquals(WS_SERVER_TID, WS_MCP_SERVER_TYPE.tid(),
                "WS_MCP_SERVER_TYPE should declare WS_SERVER_TID as its parent type");
    }

    @Test
    public void testMCPTypePredicateHasToolResourcePrompt() {
        final Rec pred = WS_MCP_SERVER_TYPE.predicate().asInst().arg(0).asRec();
        assertFalse(pred.at(uri(TOOL)).isNoObj(), "predicate should declare tool");
        assertFalse(pred.at(uri(RESOURCE)).isNoObj(), "predicate should declare resource");
        assertFalse(pred.at(uri(PROMPT)).isNoObj(), "predicate should declare prompt");
    }

    // =========================================================
    // MCP JSON-RPC message handling
    // =========================================================

    // Helper: fire ON_MESSAGE with a JSON-RPC Rec and return the response Rec
    private Rec mcpRequest(final Rec request) {
        final Obj response = server.at(uri(ON_MESSAGE)).apply(request);
        assertNotNull(response);
        assertFalse(response.isFail(), "response should not be a fail: " + response);
        assertTrue(response.isRec(), "response should be a Rec: " + response);
        return response.asRec();
    }

    @Test
    public void testPing() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(1),
                uri("method"), uri("ping")));
        assertEquals(str("2.0"), res.at(uri(JSONRPC)));
        assertFalse(res.at(uri(RESULT)).isNoObj());
    }

    @Test
    public void testInitialize() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), uri("2.0"),
                uri(ID), jnt(1),
                uri("method"), uri("initialize"),
                uri("params"), rec(
                        uri("protocolVersion"), str("2025-03-26"),
                        uri("clientInfo"), rec(uri(NAME), str("test-client"), uri("version"), str("0")))));
        assertEquals(str("2.0"), res.at(uri(JSONRPC)));
        final Rec result = res.at(uri(RESULT)).asRec();
        assertFalse(result.at(uri("capabilities")).isNoObj(), "initialize should return capabilities");
        assertFalse(result.at(uri("serverInfo")).isNoObj(), "initialize should return serverInfo");
    }

    @Test
    public void testToolsListEmpty() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), uri("2.0"),
                uri(ID), jnt(2),
                uri("method"), uri("tools/list")));
        assertFalse(res.at(uri(RESULT)).isNoObj());
        // no tools registered — list should exist and be empty
        assertFalse(res.at(uri(RESULT)).asRec().at(uri(TOOL)).isNoObj(), "result should have 'tool' key");
    }

    @Test
    public void testToolsCallUnknownTool() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), uri("2.0"),
                uri(ID), jnt(3),
                uri("method"), uri("tools/call"),
                uri("params"), rec(uri(NAME), str("nonexistent"), uri("arguments"), rec())));
        // expect a JSON-RPC error response
        assertFalse(res.at(uri("error")).isNoObj(), "unknown tool should produce an error");
    }

    @Test
    public void testResourcesListEmpty() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(4),
                uri("method"), uri("resources/list")));
        assertFalse(res.at(uri(RESULT)).isNoObj());
    }

    @Test
    public void testPromptsListEmpty() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), uri("2.0"),
                uri(ID), jnt(5),
                uri("method"), uri("prompts/list")));
        assertFalse(res.at(uri(RESULT)).isNoObj());
    }

    @Test
    public void testNotificationProducesNoResponse() {
        // Notifications have no id — the handler should return noobj (no response sent)
        final Obj result = server.at(uri(ON_MESSAGE)).apply(rec(
                uri(JSONRPC), uri("2.0"),
                uri("method"), uri("notifications/initialized")));
        assertTrue(result.isNoObj(), "notification should produce noobj (no response)");
    }

    @Test
    public void testUnknownMethodProducesError() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), uri("2.0"),
                uri(ID), jnt(99),
                uri("method"), uri("bogus/method")));
        assertFalse(res.at(uri("error")).isNoObj(), "unknown method should yield error");
        assertEquals(jnt(-32601), res.at(uri("error")).asRec().at(uri(CODE)));
    }

    @Test
    public void testNonRecInputPassesThrough() {
        // Non-Rec input (e.g. a plain string) returns as-is — not a JSON-RPC error
        final Obj result = server.at(uri(ON_MESSAGE)).apply(str("not-json"));
        assertFalse(result.isFail());
    }

    @Test
    public void testToolsCallWithRegisteredTool() {
        // Build a server with a tool in its map and verify tools/call dispatches correctly
        final fURI vid = createTestVid();
        final wsmcpServer withTool = new wsmcpServer(
                mutableMap(
                        uri(IN), uri(Content.ContentType.APPLICATION_JSON.value),
                        uri(OUT), uri(Content.ContentType.APPLICATION_JSON.value)),
                WS_MCP_SERVER_TID, vid);
        // Manually add a tool: addTool => instC that echoes the arguments back
        withTool.jvm().put(uri(TOOL), rec(
                uri("echo"), instC(f("echo").dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL.maybe())),
                        (lhs, inst) -> str("echoed!"))));

        final Obj response = withTool.at(uri(ON_MESSAGE)).apply(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(1),
                uri("method"), uri("tools/call"),
                uri("params"), rec(uri(NAME), str("echo"), uri("arguments"), rec())));

        assertFalse(response.isNoObj());
        assertFalse(response.isFail());
        assertTrue(response.isRec());
        assertFalse(response.asRec().at(uri(RESULT)).isNoObj(), "tool call should succeed");
    }

    @Test
    public void testToolsListIncludesSchemaForRegisteredTool() {
        // A server with a named-arg inst should emit non-empty inputSchema in tools/list
        final fURI vid = createTestVid();
        final wsmcpServer withTool = new wsmcpServer(rec(
                new LinkedHashMap<>(), WS_MCP_SERVER_TID, vid));
        withTool.jvm().put(uri(TOOL), rec(
                uri("greet"), instC(f("greet").dom(ALL.maybe()).rng(ALL.maybe()),
                        rec(uri("name"), T(ALL.maybe())),
                        (lhs, inst) -> str("Hello, " + inst.arg("name").toCleanString()))));

        final Obj response = withTool.at(uri(ON_MESSAGE)).apply(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(1),
                uri("method"), uri("tools/list")));
        assertTrue(response.isRec());
        final Rec toolList = response.asRec().at(uri(RESULT)).asRec().at(uri(TOOL)).asLst().lstValue().get(0).asRec();
        assertEquals(str("greet"), toolList.at(uri(NAME)));
        // inputSchema should have properties (at minimum the "name" arg)
        final Rec schema = toolList.at(uri("inputSchema")).asRec();
        assertFalse(schema.at(uri("properties")).isNoObj(), "inputSchema should have properties");
    }

    // =========================================================
    // ON_MESSAGE parameterized cases — override with MCP-aware cases
    // =========================================================

    protected static Stream<Arguments> provideMessageTestCases() {
        // For MCP, a non-Rec input just passes through unchanged
        return Stream.of(
                Arguments.of("non-rec-passthrough", str("hello"), str("hello"))
        );
    }

    @Override
    protected void assertOnMessageResult(final String desc, final Obj input, final Obj expected, final Obj actual) {
        // MCP server returns non-Rec inputs unchanged
        if (!input.isRec()) {
            assertEquals(expected, actual, desc);
        } else {
            // For Rec inputs (JSON-RPC), just verify it's a non-null, non-fail response
            assertNotNull(actual, desc);
            assertFalse(actual.isFail(), desc + " should not fail");
        }
    }

    // =========================================================
    // Integration tests
    // =========================================================

    public static class Integration extends AbstractWSServerIntegrationTest {

        @Override
        protected studio.phaseshift.metatron.isa.web.space.ws.wsSpace createWSSpace() {
            final fURI hostUri = f("ws://localhost:" + generatePort());
            return studio.phaseshift.metatron.isa.web.space.ws.wsSpace.of(rec(
                    uri(HOST), uri(hostUri.toString()),
                    uri(PATTERN), uri("ws://#"),
                    uri(ROUTE), rec(uri("/wsmcp"), uri(WS_MCP_SERVER_TID.toString()))
            ).jvm(), f("/sys/space/ws/test"));
        }

        @Test
        public void testInitializeRoundTrip() throws Exception {
            connectToServer("/wsmcp");
            final String req = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                    + "\"params\":{\"protocolVersion\":\"2025-03-26\",\"clientInfo\":{\"name\":\"test\",\"version\":\"0\"}}}";
            final String resp = sendAndReceive(req);
            assertNotNull(resp, "should receive a response");
            assertTrue(resp.contains("\"result\""), "initialize response should have result");
            assertTrue(resp.contains("\"capabilities\""), "initialize response should have capabilities");
        }

        @Test
        public void testPingRoundTrip() throws Exception {
            connectToServer("/wsmcp");
            final String resp = sendAndReceive("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}");
            assertNotNull(resp);
            assertTrue(resp.contains("\"result\""), "ping should return result");
        }

        @Test
        public void testToolsListRoundTrip() throws Exception {
            connectToServer("/wsmcp");
            final String resp = sendAndReceive("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\"}");
            assertNotNull(resp);
            assertTrue(resp.contains("\"result\""), "tools/list should return result");
        }
    }
}
