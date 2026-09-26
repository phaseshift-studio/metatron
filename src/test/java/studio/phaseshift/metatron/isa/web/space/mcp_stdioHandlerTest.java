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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.isa.web.space.stdio.handler.mcp_stdioHandler;
import studio.phaseshift.metatron.isa.web.type.mcpServer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.MCP_SERVER_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The stdio MCP carrier: the framing, the tool-collection reshape, and the invariants that keep a stdio
 * session usable.
 * <p>
 * Streams are injected, so the production protocol path is driven with no process, no pipe and no JVM of its
 * own.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mcp_stdioHandlerTest extends AbstractMcpHandlerTest {

    /**
     * {@code createTestVid()} addresses the server under {@code /test/…}, so the test space has to serve that
     * whole subtree — the base class's default pattern covers only {@code /test/emulator/#}.
     */
    @Override
    protected fURI testSpacePattern() {
        return f("/test/#");
    }

    /**
     * The minimal server: one tool, written as a collection of instruction references rather than a
     * hand-keyed rec, because the key is derived from the instruction.
     */
    @Override
    protected mcpServer createMcpServer() {
        return new mcpServer(mutableMap(uri(TOOL), lst(Router.readFromSpace(f("eval")))),
                MCP_SERVER_TID, createTestVid());
    }

    private static String json(final Rec request) {
        return ObjJSONSerializer.simple().write(request).toString();
    }

    private Obj dispatch(final Rec request) {
        return this.mcp.handleMessage(json(request));
    }

    private static Rec call(final String name, final String arg) {
        return rec(uri(JSONRPC), str("2.0"), uri(ID), str("1"), uri("method"), str("tools/call"),
                uri("params"), rec(uri(NAME), str(name), uri("arguments"), rec(uri("0"), str(arg))));
    }

    private mcp_stdioHandler carrier(final ByteArrayOutputStream sink) {
        return (mcp_stdioHandler) mcp_stdioHandler.of(
                rec(mutableMap(uri(SERVER), this.mcp)),
                new ByteArrayInputStream(new byte[0]),
                new PrintStream(sink, true, StandardCharsets.UTF_8), false);
    }

    /**
     * A tool written as a collection is keyed by the instruction's own name — which is why the constructor
     * reshapes before {@code super(...)} type-checks the obj: a list that reached the predicate unreshaped
     * could not be constructed at all.
     */
    @Test
    void testToolCollectionIsReshapedAndNamedByItsInstruction() {
        final Rec tools = this.mcp.at(uri(TOOL)).asRec();
        assertEquals(1, tools.jvm().size(), "the single tool in the collection is the single entry: " + tools);
        assertFalse(tools.at(uri("m_inst_eval")).isNoObj(),
                "the entry is keyed by the instruction's flattened tid, got: " + tools);
    }

    /**
     * Everything {@code tools/list} advertises must be callable by that same name — the invariant a
     * hand-written rec key violated silently (listed as one name, looked up as another).
     */
    @Test
    void testEverythingListedIsCallable() {
        final Rec listed = dispatch(rec(uri(JSONRPC), str("2.0"), uri(ID), str("list"),
                uri("method"), str("tools/list"))).asRec();
        final Obj tools = listed.at(uri(RESULT)).asRec().at(uri("tools"));
        assertFalse(tools.isNoObj(), "tools/list should warn of tools: " + listed);
        tools.asLst().lstValue().forEach(tool -> {
            final String name = tool.asRec().at(uri(NAME)).toCleanString();
            final Rec called = dispatch(call(name, "1+2")).asRec();
            assertTrue(called.has(uri(RESULT)),
                    "a listed tool must be callable by its advertised name: " + name + " -> " + called);
        });
    }

    /**
     * The wire carries one JSON object per line and nothing else.  A stdio session lives or dies on this: a
     * banner, a log line or a pretty-printed response would desynchronize the client.
     */
    @Test
    void testSendWritesExactlyOneJsonLine() {
        final ByteArrayOutputStream sink = new ByteArrayOutputStream();
        carrier(sink).send(dispatch(rec(uri(JSONRPC), str("2.0"), uri(ID), str("ping"),
                uri("method"), str("ping"))));
        final String wire = sink.toString(StandardCharsets.UTF_8);
        assertTrue(wire.endsWith("\n"), "a frame is newline terminated");
        assertEquals(1, wire.lines().count(), "a frame is one line, got: " + wire);
        assertTrue(ObjJSONSerializer.simple().inputBytes(wire.trim()).isRec(),
                "the frame parses as one JSON object, got: " + wire);
    }

    /**
     * The carrier's state lives in its own jvm — not in Java fields — so a running transport is
     * introspectable from mtron like any other object.
     */
    @Test
    void testCarrierStateIsData() {
        final mcp_stdioHandler carrier = carrier(new ByteArrayOutputStream());
        assertFalse(carrier.at(uri(STATUS)).isNoObj(), "a serving carrier reports status as data: " + carrier);
        assertFalse(carrier.at(uri(SERVER)).isNoObj(), "the server it carries is visible as data: " + carrier);
        carrier.close();
        assertFalse(carrier.at(uri(STATUS)).boolValue(), "a closed carrier reports not-serving");
    }

    /**
     * A {@code code::T} argument is evaluated on the way in — the reason {@code eval} is the foundational
     * tool rather than an identity function over a string.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "1+2                       % 3",
            "[1,2,3]>-.sum()           % 6",
            "10.as(real::T).mult(2.5)  % 25.0",
    }, delimiter = '%')
    void testEvalToolEvaluatesCodeOverTheWire(final String code, final String expected) {
        final ByteArrayOutputStream sink = new ByteArrayOutputStream();
        final mcp_stdioHandler carrier = carrier(sink);
        final Obj response = carrier.handle(json(call("m_inst_eval", code)));
        assertFalse(response.isNoObj(), "a request must be answered");
        carrier.send(response);
        assertTrue(sink.toString(StandardCharsets.UTF_8).contains("\"text\":\"" + expected + "\""),
                "expected " + expected + " over the wire, got: " + sink);
    }
}
