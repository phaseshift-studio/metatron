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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.type.mcpServer;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/**
 * Transport-agnostic base for testing MCP handler implementations
 * via direct {@link mcpServer#handleMessage(Obj)} dispatch.
 * <p>
 * No HTTP server, no WebSocket — pure protocol-level testing.
 * Subclasses implement {@link #createMcpServer()} to supply their handler.
 */
public abstract class AbstractMcpHandlerTest extends AbstractMetatronTest {

    protected Space testSpace;
    protected mcpServer mcp;

    // ========================================
    // Subclass contract
    // ========================================

    /**
     * Produce the mcpServer instance under test.
     */
    protected abstract mcpServer createMcpServer();

    /**
     * Optional: space pattern to host test data (e.g. home: directories).
     * Default: {@code /test/emulator/#}
     */
    protected fURI testSpacePattern() {
        return f("/test/emulator/#");
    }

    protected fURI createTestVid() {
        return f("/test/" + getClass().getSimpleName() + "/" + System.nanoTime());
    }

    // ========================================
    // Lifecycle
    // ========================================

    @BeforeEach
    public void setupTestSpace() {
        InstSet.importInstSet(WEB_ISA_TID);
        this.testSpace = memSpace.of(
                rec(uri(PATTERN), uri(testSpacePattern())),
                f("/sys/space/test"));
        this.mcp = createMcpServer();
    }

    @AfterEach
    public void teardownTestSpace() {
        this.mcp = null;
        if (this.testSpace != null) {
            Router.global().removeSpace(this.testSpace.vid());
            this.testSpace.close();
            this.testSpace = null;
        }
    }

    // ========================================
    // Helpers
    // ========================================

    /**
     * Dispatch a JSON-RPC request through the mcpServer and assert it
     * returns a valid, non-failing Rec response.
     */
    protected Rec mcpRequest(final Rec request) {
        final Obj response = this.mcp.handleMessage(request);
        assertNotNull(response, "mcp response should not be null");
        assertFalse(response.isFail(), "response should not be a fail: " + response);
        assertTrue(response.isRec(), "response should be a Rec, got: " + response.getClass().getSimpleName());
        return response.asRec();
    }

    /**
     * Dispatch a JSON-RPC notification and assert it returns noobj (no response).
     */
    protected void mcpNotification(final Rec notification) {
        final Obj response = this.mcp.handleMessage(notification);
        assertTrue(response.isNoObj(), "notification should return noobj, got: " + response);
    }

    /**
     * Build a standard JSON-RPC request rec with an auto-incrementing id.
     */
    protected static Rec request(final long id, final String method, final Rec params) {
        return rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(id),
                uri("method"), uri(method.isEmpty() ? "tools/list" : method),
                uri("params"), params);
    }

    /**
     * Convenience: request with no params (for ping, etc.).
     */
    protected static Rec request(final long id, final String method) {
        return rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(id),
                uri("method"), uri(method));
    }

    // ========================================
    // Common MCP protocol tests
    // ========================================

    @Test
    public void testPing() {
        final Rec res = mcpRequest(request(1, "ping"));
        assertFalse(res.at(uri(RESULT)).isNoObj(), "ping should return a result");
    }

    @Test
    public void testInitialize() {
        final Rec res = mcpRequest(request(2, "initialize", rec(
                uri("protocolVersion"), str("2025-03-26"),
                uri("clientInfo"), rec(uri(NAME), str("test"), uri("version"), str("0")))));
        final Rec result = res.at(uri(RESULT)).asRec();
        assertFalse(result.at(uri("protocolVersion")).isNoObj(), "should have protocolVersion");
        assertFalse(result.at(uri("capabilities")).isNoObj(), "should have capabilities");
        assertFalse(result.at(uri("serverInfo")).isNoObj(), "should have serverInfo");
    }

    @Test
    public void testToolsListReturnsWellFormedResponse() {
        final Rec res = mcpRequest(request(3, "tools/list", rec()));
        final Obj tools = res.at(uri(RESULT)).asRec().at(uri("tools"));
        assertFalse(tools.isNoObj(), "result should have 'tools' key");
        assertTrue(tools.isLst(), "tools should be a list");
    }

    @Test
    public void testUnknownMethodProducesError() {
        final Rec res = mcpRequest(request(99, "bogus/method"));
        final Obj error = res.at(uri("error"));
        assertFalse(error.isNoObj(), "unknown method should yield a JSON-RPC error");
        assertEquals(jnt(-32601), error.asRec().at(uri(CODE)));
    }

    @Test
    public void testNotificationProducesNoResponse() {
        mcpNotification(rec(
                uri(JSONRPC), str("2.0"),
                uri("method"), uri("notifications/initialized")));
    }

    @Test
    public void testToolsListHasIdField() {
        final Rec res = mcpRequest(request(7, "tools/list", rec()));
        assertFalse(res.at(uri(ID)).isNoObj(), "response should echo the id");
        assertEquals(jnt(7), res.at(uri(ID)), "response id should match request id");
    }
}
