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
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.CommonUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.ROUTE;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/**
 * Abstract base for integration-testing HttpRec implementations against a
 * real httpSpace and live HTTP connection.
 * <p>
 * Subclasses implement exactly one method:
 * <pre>
 *   protected httpSpace createHTTPSpace();
 * </pre>
 * Everything — host, port, routes, vid — is derived from the returned space.
 */
public abstract class AbstractHTTPServerIntegrationTest extends AbstractMetatronTest {

    protected httpSpace space;
    protected String httpHost;
    protected int httpPort;
    protected HttpClient httpClient;

    // ========================================
    // Single abstract method
    // ========================================

    /**
     * Create and return the fully configured, started httpSpace under test.
     * The space must already be bound to its host/port when returned.
     * Use {@link #generatePort()} inside this method to pick a free port.
     */
    protected abstract httpSpace createHTTPSpace();

    // ========================================
    // Derived helpers
    // ========================================

    /**
     * The primary HTTP route path for this test, derived from the
     * first entry in {@code space.at(ROUTE)}.
     */
    protected String primaryRoutePath() {
        final Obj routes = space.at(ROUTE);
        if (routes.isNoObj()) return "/";
        return routes.asRec().elements()
                .map(r -> r.first().uriValue().toString())
                .findFirst()
                .orElse("/");
    }

    protected String baseUrl() {
        return "http://" + httpHost + ":" + httpPort;
    }

    // ========================================
    // Lifecycle
    // ========================================
    @BeforeEach
    public void setupHTTPSpace() {
        InstSet.importInstSet(WEB_ISA_TID);
        this.space = createHTTPSpace();
        assertNotNull(this.space, "httpSpace should be created");

        this.httpHost = this.space.at(HOST).uriValue().host();
        this.httpPort = this.space.at(HOST).uriValue().port();

        CommonUtil.sleepThread(500);

        this.httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
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

    // ========================================
    // HTTP client helpers
    // ========================================

    protected HttpResponse<String> httpGet(final String path) throws IOException, InterruptedException {
        final HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .GET()
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpPost(final String path, final String body, final String sessionId)
            throws IOException, InterruptedException {
        final HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpDelete(final String path, final String sessionId)
            throws IOException, InterruptedException {
        final HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .DELETE();
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ========================================
    // Base integration tests
    // ========================================

    @Test
    public void testHandlerTypeIsRegisteredInRouter() {
        final Obj routes = space.at(ROUTE);
        assertFalse(routes.isNoObj(), "httpSpace should have a route table");
        routes.asRec().elements().forEach(r -> {
            final Obj type = Router.global().read(r.second().uriValue());
            assertFalse(type.isNoObj(),
                    "Type should be registered in Router at " + r.second().uriValue());
            assertTrue(type.isType(),
                    "Registered object should be a Type, got: " + type);
        });
    }

    @Test
    public void testHttpConnection() throws Exception {
        final HttpResponse<String> resp = httpGet(primaryRoutePath());
        assertNotNull(resp, "HTTP response should not be null");
        // Handler routes may return 405/501 for unsupported methods, but should respond
        assertTrue(resp.statusCode() >= 200, "HTTP should respond");
    }

    @Test
    public void testPostReturnsResponse() throws Exception {
        final HttpResponse<String> resp = httpPost(primaryRoutePath(),
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}", null);
        assertNotNull(resp, "POST should return a response");
        assertTrue(resp.statusCode() >= 200 && resp.statusCode() < 600,
                "POST should return a valid HTTP status");
    }
}
