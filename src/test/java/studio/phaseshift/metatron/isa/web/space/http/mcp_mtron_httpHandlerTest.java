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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.space.AbstractMcpMtronHandlerTest;
import studio.phaseshift.metatron.util.CommonUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.ROUTE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.http.handler.mcp_httpHandler.HTTP_MCP_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.space.http.handler.mcp_mtron_httpHandler.HTTP_MCP_MTRON_TID;
import static studio.phaseshift.metatron.isa.web.space.http.handler.mcp_mtron_httpHandler.HTTP_MCP_MTRON_TYPE;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/**
 * Integration tests for {@link studio.phaseshift.metatron.isa.web.space.http.handler.mcp_mtron_httpHandler}.
 * <p>
 * Direct-invocation (protocol-level) tests are inherited from
 * {@link AbstractMcpMtronHandlerTest}.  This class only adds the HTTP-specific
 * type checks and the live Streamable-HTTP round-trips against a real httpSpace.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class mcp_mtron_httpHandlerTest extends AbstractMcpMtronHandlerTest {

    private httpSpace space;
    private String httpHost;
    private int httpPort;
    private HttpClient httpClient;

    // ========================================
    // HTTP transport infrastructure
    // ========================================

    private httpSpace createHTTPSpace() {
        final fURI hostUri = f("http://localhost:" + generatePort());
        return httpSpace.of(rec(
                uri(HOST), uri(hostUri.toString()),
                uri(PATTERN), uri("http://#"),
                uri(ROUTE), rec(uri("/mcp"), uri(HTTP_MCP_MTRON_TID.toString()))
        ), f("/sys/space/http/mcp-mtron/test"));
    }

    @BeforeAll
    public void setupHTTPSpace() {
        InstSet.importInstSet(WEB_ISA_TID);
        this.space = createHTTPSpace();
        assertNotNull(this.space, "httpSpace should be created");

        this.httpHost = this.space.at(HOST).uriValue().host();
        this.httpPort = this.space.at(HOST).uriValue().port();

        CommonUtil.sleepThread(100);

        this.httpClient = HttpClient.newHttpClient();
    }

    @AfterAll
    public void teardownHTTPSpace() {
        if (this.httpClient != null) {
            this.httpClient.close();
            this.httpClient = null;
        }
        if (this.space != null) {
            Router.global().removeSpace(this.space.vid());
            Router.global().removeSpace(WEB_ISA_TID);
            this.space.close();
            this.space = null;
        }
    }

    private String baseUrl() {
        return "http://" + this.httpHost + ":" + this.httpPort;
    }

    private HttpResponse<String> httpPost(final String path, final String body, final String sessionId)
            throws IOException, InterruptedException {
        final HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
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
                .uri(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(2))
                .DELETE();
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return this.httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // =========================================================
    // Type-level checks
    // =========================================================

    @Test
    public void testMcpHttpTIDNamespace() {
        assertTrue(HTTP_MCP_MTRON_TID.toString().contains("httpspace"));
        assertTrue(HTTP_MCP_MTRON_TID.toString().contains("mcp_mtron_http"));
    }

    @Test
    public void testMcpHttpTypeIsSubtypeOfMcpHttp() {
        assertEquals(HTTP_MCP_HANDLER_TID, HTTP_MCP_MTRON_TYPE.tid(),
                "mcp_mtron_http type should declare mcp_http as its parent type");
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
}
