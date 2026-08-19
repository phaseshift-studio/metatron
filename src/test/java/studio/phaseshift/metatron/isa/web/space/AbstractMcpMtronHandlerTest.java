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

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.isa.web.type.mcpMetatronBuilder;
import studio.phaseshift.metatron.isa.web.type.mcpServer;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.MCP_SERVER_TID;

/**
 * Transport-agnostic base for the metatron-native MCP server.
 * <p>
 * Both {@code mcp_mtron_httpHandler} and {@code mcp_mtron_wsHandler} compose a
 * {@link mcpServer} whose tools/resources are populated by
 * {@link mcpMetatronBuilder#build(Map, fURI)}.  Because that protocol handler is
 * the same object regardless of HTTP vs WebSocket transport, the JSON-RPC
 * request/response behaviour is identical — so the direct-invocation tests live
 * here, once, and are inherited by every transport's test.
 * <p>
 * Subclasses supply nothing further for the direct-invocation suite; they only
 * add transport-specific type checks and live round-trip tests.
 */
public abstract class AbstractMcpMtronHandlerTest extends AbstractMcpHandlerTest {

    @Override
    protected fURI testSpacePattern() {
        return f("/test/#");
    }

    @Override
    protected mcpServer createMcpServer() {
        final fURI vid = createTestVid();
        return new mcpServer(mcpMetatronBuilder.build(
                new LinkedHashMap<>(Map.of(
                        uri(IN), uri(MIME.MIMEType.APPLICATION_JSON.value),
                        uri(OUT), uri(MIME.MIMEType.APPLICATION_JSON.value))),
                vid),
                MCP_SERVER_TID, vid);
    }

    // =========================================================
    // Built-in tools presence
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
                .stream().anyMatch(t -> t.isRec() && str("m_inst_list_space").equals(t.asRec().at(uri(NAME))));
        assertTrue(has, "tools/list should include 'list_space'");
    }

    @Test
    public void testToolsListContainsRouterInfo() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(3),
                uri("method"), uri("tools/list")));
        final boolean has = res.at(uri(RESULT)).asRec().at(uri("tools")).asLst().lstValue()
                .stream().anyMatch(t -> t.isRec() && str("m_inst_router_info").equals(t.asRec().at(uri(NAME))));
        assertTrue(has, "tools/list should include 'router_info'");
    }

    @Test
    public void testToolsListContainsFindInst() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(4),
                uri("method"), uri("tools/list")));
        final boolean has = res.at(uri(RESULT)).asRec().at(uri("tools")).asLst().lstValue()
                .stream().anyMatch(t -> t.isRec() && str("m_inst_find_inst").equals(t.asRec().at(uri(NAME))));
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
                uri("params"), rec(uri(NAME), str("m_inst_list_space"), uri("arguments"), rec())));
        assertFalse(res.at(uri(RESULT)).isNoObj(), "list_space should return a result");
        assertFalse(res.at(uri("error")).isRec(), "list_space should not error");
        assertTrue(res.at(uri(RESULT)).isRec(), "list_space result should be a Rec");
    }

    @Test
    public void testCallRouterInfo() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(11),
                uri("method"), uri("tools/call"),
                uri("params"), rec(uri(NAME), str("m_inst_router_info"), uri("arguments"), rec())));
        assertFalse(res.at(uri(RESULT)).isNoObj(), "router_info should return a result");
        final Obj contentList = res.at(uri(RESULT)).asRec().at(uri(CONTENT));
        assertFalse(contentList.isNoObj(), "result should have a content field");
        final String text = contentList.asLst().at(0).asRec().at(uri(TEXT)).toCleanString();
        assertTrue(text.contains("router_vid"), "router_info text should include router_vid");
        assertTrue(text.contains("space_count"), "router_info text should include space_count");
    }

    @Test
    public void testCallFindInst() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(12),
                uri("method"), uri("tools/call"),
                uri("params"), rec(uri(NAME), str("m_inst_find_inst"), uri("arguments"), rec(uri(PATTERN), uri("plus")))));
        assertFalse(res.at(uri(RESULT)).isNoObj(), "find_inst(pattern=>plus) should return a result");
        assertFalse(res.at(uri("error")).isRec(), "find_inst(pattern=>plus) should not error");
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
    // eval_mtron — regression tests for fail:: wrapping
    // =========================================================

    @Test
    public void testEvalMtronWithValidExpression() {
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(20),
                uri("method"), uri("tools/call"),
                uri("params"), rec(
                        uri(NAME), str("m_inst_eval_mtron"),
                        uri("arguments"), rec(uri("code"), str("\"hello\"")))));
        assertFalse(res.at(uri(RESULT)).isNoObj(), "eval_mtron('\"hello\"') should return a result");
        assertFalse(res.at(uri("error")).isRec(), "eval_mtron('\"hello\"') should not error");
    }

    @Test
    public void testEvalMtronWithNonMtronText() {
        // Plain text that is not valid mtron — should be returned as-is, not wrapped in fail::
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(21),
                uri("method"), uri("tools/call"),
                uri("params"), rec(
                        uri(NAME), str("m_inst_eval_mtron"),
                        uri("arguments"), rec(uri("code"), str("## Search Results\n\nmethod at line 79")))));
        assertFalse(res.at(uri(RESULT)).isNoObj(), "eval_mtron(non-mtron text) should return a result");
        assertFalse(res.at(uri("error")).isRec(), "eval_mtron(non-mtron text) should not error");
    }

    @Test
    public void testEvalMtronWithLiteralPercentS() {
        // Text containing literal %s — used to crash via String.format() in MTronException
        final Rec res = mcpRequest(rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(22),
                uri("method"), uri("tools/call"),
                uri("params"), rec(
                        uri(NAME), str("m_inst_eval_mtron"),
                        uri("arguments"), rec(uri("code"), str("text with literal %s placeholder")))));
        assertFalse(res.at(uri(RESULT)).isNoObj(), "eval_mtron(literal %s) should return a result");
        assertFalse(res.at(uri("error")).isRec(), "eval_mtron(literal %s) should not error");
    }
}
