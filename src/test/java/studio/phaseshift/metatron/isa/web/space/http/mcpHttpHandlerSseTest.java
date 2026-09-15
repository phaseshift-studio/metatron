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

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.space.http.handler.mcp_httpHandler;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.QPROC;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/**
 * Network-level proof that {@link mcp_httpHandler#doGet} opens a real SSE stream and
 * drains the server's subscription outbox over the wire. Uses an ephemeral
 * {@link com.sun.net.httpserver.HttpServer} (port 0) and a real HTTP client.
 */
public class mcpHttpHandlerSseTest extends AbstractMetatronTest {

    private Space testSpace;
    private HttpServer server;

    @BeforeEach
    public void setupSseTest() throws Exception {
        InstSet.importInstSet(WEB_ISA_TID);
        // a space hosting the handler vid's outbox, with ?subq so the live-push registration works
        this.testSpace = memSpace.of(rec(
                        uri(PATTERN), uri("/test/sse/#"),
                        uri(QPROC), lst(QCollection.subq())),
                f("/sys/space/sse_test"));
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    @AfterEach
    public void teardownSseTest() {
        if (this.server != null) {
            this.server.stop(0);
            this.server = null;
        }
        if (this.testSpace != null) {
            Router.global().removeSpace(this.testSpace.vid());
            this.testSpace.close();
            this.testSpace = null;
        }
    }

    @Test
    public void testGetDrainsSubscriptionOutboxOverSse() throws Exception {
        // a handler whose vid lives under the test space, so its subscription outbox routes there
        final mcp_httpHandler handler = new mcp_httpHandler(
                new LinkedHashMap<>(), mcp_httpHandler.HTTP_MCP_HANDLER_TID, f("/test/sse/mcp"));
        // one notification already fired (between subscriptions/listen and this GET)
        Router.writeToSpace("/test/sse/mcp/subscriptions/30/0", rec(
                uri("method"), uri("notifications/resources/updated"),
                uri("params"), rec(uri("uri"), uri("/usr/demo/age")),
                uri("subscriptionId"), str("30")));

        this.server.createContext("/mcp", exchange -> handler.handle(exchange));
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        this.server.start();
        final int port = this.server.getAddress().getPort();

        final HttpClient client = HttpClient.newHttpClient();
        final HttpResponse<InputStream> resp = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
                        .header("Accept", "text/event-stream")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());

        assertEquals(200, resp.statusCode(), "the GET should open an SSE stream");
        assertTrue(resp.headers().firstValue("content-type").orElse("").contains("text/event-stream"),
                "the response should be text/event-stream");

        try (final InputStream in = resp.body()) {
            final byte[] buf = new byte[8192];
            final int n = in.read(buf);
            assertTrue(n > 0, "the stream should carry the drained notification");
            final String body = new String(buf, 0, n, StandardCharsets.UTF_8);
            assertTrue(body.contains("event: message"), "mcp notifications are named 'message': " + body);
            assertTrue(body.contains("notifications/resources/updated"),
                    "the drained notification should stream: " + body);
        }
    }
}
