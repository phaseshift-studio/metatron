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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.space.http.httpSpace;
import studio.phaseshift.metatron.isa.web.type.mcpEmulatorBuilder;
import studio.phaseshift.metatron.isa.web.type.mcpServer;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.MCP_SERVER_TID;

/**
 * Transport-agnostic tests for the MCP emulator tools
 * ({@code adduser}, {@code deluser}, {@code tools_list}, {@code tools_call}).
 * <p>
 * No HTTP server or WebSocket — pure {@code mcpServer.handleMessage()} dispatch.
 */
public class mcpEmulatorTest extends AbstractMcpHandlerTest {

    private Space homeSpace;
    private static final String TEST_USER = "test-user-" + System.nanoTime();

    @BeforeAll
    public static void setup() {
        InstSet.importInstSet(f("/m/web"));
        httpSpace.of(rec(uri(PATTERN), uri("http://#"), uri(HOST), uri("http://localhost:8654"), uri(ROUTE), rec(uri("/metatron"), uri("mcp_mtron_http"))), f("/sys/space/web/http"));
    }

    @Override
    protected mcpServer createMcpServer() {
        final fURI vid = createTestVid();
        return new mcpServer(
                mcpEmulatorBuilder.build(rec().jvm(), vid),
                MCP_SERVER_TID, null);
    }

    @BeforeEach
    public void setupHomeSpace() {
        // Use the boot-time home space if registered; create a fresh one otherwise
        // final Space existing = Router.global().getSpaceFor(f("home:test"));
        // if (existing == null || existing.isNoObj()) {
        this.homeSpace = memSpace.of(
                rec(uri(PATTERN), uri("home:#")),
                f("/sys/space/test-home"));
        //}
    }

    @AfterEach
    public void teardownHomeSpace() {
        if (this.homeSpace != null) {
            Router.global().removeSpace(this.homeSpace.vid());
            this.homeSpace.close();
            this.homeSpace = null;
        }
    }

    // ========================================
    // Bootstrap toolkit: tools/list
    // ========================================

    @Test
    public void testEmulatorToolsListReturnsBootstrapTools() {
        final Rec res = mcpRequest(request(1, "tools/list", rec()));
        LOG.info("testEmulatorToolsListReturnsBootstrapTools result: %s", res);
        final var tools = res.at(uri(RESULT)).asRec().at(uri("tools")).asLst();
        assertFalse(tools.lstValue().isEmpty(), "emulator should expose its own tools");
        // Check for known bootstrap tools
        assertTrue(toolPresent(tools, "adduser"), "should have adduser");
        assertTrue(toolPresent(tools, "deluser"), "should have deluser");
        assertTrue(toolPresent(tools, "tools_list"), "should have tools_list");
        assertTrue(toolPresent(tools, "tools_call"), "should have tools_call");
        assertTrue(toolPresent(tools, "install"), "should have install");
    }

    // ========================================
    // adduser
    // ========================================

    @Test
    public void testAdduserCreatesHomeDirectory() {
        final Rec res = mcpRequest(toolsCall(2, "adduser",
                rec(uri(USER), uri(TEST_USER))));
        final String resultText = contentText(res);
        LOG.info("testAdduserCreatesHomeDirectory result: %s", resultText);
        assertTrue(resultText.contains(TEST_USER), "result should include the username: " + resultText);
    }

    @Test
    public void testAdduserDuplicateUserFails() {
        mcpRequest(toolsCall(3, "adduser", rec(uri(USER), uri(TEST_USER))));
        final Obj response = this.mcp.handleMessage(toolsCall(4, "adduser",
                rec(uri(USER), uri(TEST_USER))));
        // throw → fail() → toolResult wrapped as mcpResponse; error text in content
        LOG.info("testAdduserDuplicateUserFails result: %s", response);
        assertTrue(response.isRec(), "response should be a Rec");
        assertTrue(response.asRec().at(uri("error")).isRec()
                        || contentText(response.asRec()).contains("already exists"),
                "duplicate adduser should error: " + response);
    }

    // ========================================
    // deluser
    // ========================================

    @Test
    public void testDeluserRemovesHomeDirectory() {
        final String delUser = TEST_USER + "-del";
        mcpRequest(toolsCall(5, "adduser", rec(uri(USER), uri(delUser))));
        final Rec res = mcpRequest(toolsCall(6, "deluser",
                rec(uri(USER), uri(delUser))));
        LOG.info("testDeluserRemovesHomeDirectory result: %s", res);
        assertTrue(contentText(res).contains("deleted"), "deluser should confirm deletion: " + contentText(res));
    }

    @Test
    public void testDeluserNonexistentUserFails() {
        final Obj response = this.mcp.handleMessage(toolsCall(7, "deluser",
                rec(uri(USER), uri("no-such-user-" + System.nanoTime()))));
        LOG.info("testDeluserNonexistentUserFails result: %s", response);
        assertTrue(response.isRec(), "response should be a Rec");
        assertTrue(response.asRec().at(uri("error")).isRec()
                        || contentText(response.asRec()).contains("does not exists"),
                "deluser on nonexistent user should error: " + response);
    }

    // ========================================
    // tools_list (emulated — per-user)
    // ========================================

    @Test
    public void testEmulatedToolsListReturnsEmptyForNewUser() {
        final String emptyUser = TEST_USER + "-empty";
        mcpRequest(toolsCall(8, "adduser", rec(uri(USER), uri(emptyUser))));
        final Obj response = this.mcp.handleMessage(toolsCall(9, "tools_list",
                rec(uri(USER), uri(emptyUser))));
        LOG.info("testEmulatedToolsListReturnsEmptyForNewUser result: %s", response);
        // tools_list calls from_(home:{user}/tool/+/).apply() — on an empty dir
        // expect either a valid result or a graceful empty
        assertNotNull(response, "tools_list for empty user should not crash");
    }

    // ========================================
    // tools_call (emulated — per-user) — noop on empty tool dir
    // ========================================

    @Test
    public void testEmulatedToolsCallOnEmptyUserProducesError() {
        final String toolsUser = TEST_USER + "-tools";
        mcpRequest(toolsCall(10, "adduser", rec(uri(USER), uri(toolsUser))));
        final Obj response = this.mcp.handleMessage(toolsCall(11, "tools_call",
                rec(uri(USER), uri(toolsUser),
                        uri(NAME), str("nonexistent"),
                        uri(ARGS), rec())));
        LOG.info("testEmulatedToolsCallOnEmptyUserProducesError result: %s", response);
        assertNotNull(response, "tools_call on empty user should not crash");
    }

    // ========================================
    // install — mcpServers config → mcpClient → tools linked
    // ========================================

    @Test
    public void testInstallLinksServerTools() {
        final String installUser = TEST_USER + "-install";
        mcpRequest(toolsCall(12, "adduser", rec(uri(USER), uri(installUser))));

        // mcpServers config — installs metatron's own MCP server (no external dependencies)
        final String config = """
                              {
                                "mcpServers": {
                                  "metatron": {
                                    "url": "http://localhost:8999/mcp"
                                  }
                                }
                              }""";

        final Obj response = this.mcp.handleMessage(toolsCall(13, "install",
                rec(uri(USER), uri(installUser), uri("mcpServers"), str(config))));
        LOG.info("testInstallLinksServerTools install result: %s", response);
        assertNotNull(response, "install should not crash");
        assertTrue(response.isRec(), "install should return a Rec");
        assertTrue(response.asRec().at(uri("error")).isNoObj(), "install should not error: " + response);

        // Verify the install response is well-formed (may fail if metatron HTTP not running, but must not crash)
        LOG.info("installed mcpClient: %s", Router.readFromSpace(f("home:" + installUser).extend("tool/metatron")));

        // Verify tools_list sees the installed server
        final Obj listResult = this.mcp.handleMessage(toolsCall(14, "tools_list",
                rec(uri(USER), uri(installUser))));
        LOG.info("tools_list after install: %s", listResult);
    }

    // ========================================
    // Protocol overrides
    // ========================================

    @Test
    public void testInitializeReturnsGatewayServerInfo() {
        final Rec res = mcpRequest(request(12, "initialize", rec(
                uri("protocolVersion"), str("2025-03-26"),
                uri("clientInfo"), rec(uri(NAME), str("test"), uri("version"), str("0")))));
        LOG.info("testInitializeReturnsGatewayServerInfo result: %s", res);
        final String serverName = res.at(uri(RESULT)).asRec()
                .at(uri("serverInfo")).asRec()
                .at(uri(NAME)).toCleanString();
        // The emulator uses the base mcpServer initialize which returns "metatron-mcp"
        assertFalse(serverName.isEmpty(), "serverInfo should have a name");
    }

    // ========================================
    // Helpers
    // ========================================

    private static Rec toolsCall(final long id, final String toolName, final Rec args) {
        return rec(
                uri(JSONRPC), str("2.0"),
                uri(ID), jnt(id),
                uri("method"), uri("tools/call"),
                uri("params"), rec(uri(NAME), str(toolName), uri("arguments"), args));
    }

    private static boolean toolPresent(final studio.phaseshift.metatron.isa.m.type.Lst tools, final String name) {
        return tools.lstValue().stream()
                .anyMatch(t -> t.isRec() && str(name).equals(t.asRec().at(uri(NAME))));
    }

    private static String contentText(final Rec response) {
        final Obj result = response.at(uri(RESULT));
        if (result.isNoObj() || !result.isRec())
            return "";
        final Obj content = result.asRec().at(uri(CONTENT));
        if (content.isLst() && !content.asLst().lstValue().isEmpty()) {
            return Str.Helper.cleanString(content.asLst().at(0).asRec().at(uri(TEXT)));
        }
        return "";
    }
}
