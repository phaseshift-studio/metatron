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

package studio.phaseshift.metatron.isa.m.web.space.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.web.space.http.handler.mcp_httpHandler;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.web.webInstSet.WEB_ISA_TID;

/**
 * The streamable-http half of the notification contract: a client's notification is accepted with {@code 202}
 * and no body, while a request is still answered. Driven over a real {@link HttpServer} (port 0) and a real
 * client, because "no response" is a wire fact — the handler answers {@code 202} for a noobj result, and a
 * notification that reached the unknown-method arm answered {@code 200} with a JSON-RPC error instead.
 */
public class mcpHttpHandlerTest extends AbstractMetatronTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    public void setupHttpTest() throws Exception {
        InstSet.importInstSet(WEB_ISA_TID);
        final mcp_httpHandler handler = new mcp_httpHandler(
                new LinkedHashMap<>(), mcp_httpHandler.HTTP_MCP_HANDLER_TID, f("/test/http/mcp"));
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/mcp", handler::handle);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        this.server.start();
        this.port = this.server.getAddress().getPort();
    }

    @AfterEach
    public void teardownHttpTest() {
        if (null != this.server) {
            this.server.stop(0);
            this.server = null;
        }
    }

    private HttpResponse<String> post(final String json) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.port + "/mcp"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '%', value = {
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/roots/list_changed\"}",
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}",
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/unknown\"}"})
    public void testNotificationIsAcceptedWithoutBody(final String notification) throws Exception {
        final HttpResponse<String> response = this.post(notification);
        assertEquals(202, response.statusCode(), "a notification is accepted, not answered: " + response.body());
        assertEquals("", response.body(), "a notification must carry no response body");
    }

    @Test
    public void testRequestIsStillAnswered() throws Exception {
        final HttpResponse<String> response = this.post("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"ping\"}");
        assertEquals(200, response.statusCode(), "a request is answered: " + response.body());
        assertTrue(response.body().contains("\"result\""), "a ping should still answer with a result: " + response.body());
    }
}
