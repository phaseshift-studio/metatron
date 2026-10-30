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

package studio.phaseshift.metatron.isa.web.space.ws;

import org.junit.jupiter.api.*;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.network.Listener;

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.ROUTE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/**
 * Abstract base for integration-testing websocket handlers against a real
 * ws_server and a live WebSocket connection.
 * <p>
 * Subclasses implement exactly one method:
 * <pre>
 *   protected Listener createWSServer();
 * </pre>
 * Everything — host, port, routes — is derived from the returned server:
 * <ul>
 *   <li>{@code server.origin()} → host and port</li>
 *   <li>{@link #routes()} → this machine's route table on the socket (path → type TID)</li>
 * </ul>
 * The first entry in the route table is used as the primary connection path
 * for the base-class tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractWebSocketServerIntegrationTest extends AbstractMetatronTest {

    protected Listener server;
    protected String wsHost;
    protected int wsPort;
    protected HttpClient httpClient;
    protected WebSocket webSocket;

    // ========================================
    // Single abstract method
    // ========================================

    /**
     * Create and return the fully configured, serving ws_server under test ({@code wsServer.of(...)}).
     * The server is bound to its host/port when returned.
     * Use {@link #generatePort()} inside this method to pick a free port.
     */
    protected abstract Listener createWSServer();

    // ========================================
    // Derived helpers — no overrides needed
    // ========================================

    /**
     * This machine's routes on the server's socket, as its listener entry keeps them.
     */
    protected Obj routes() {
        final Obj entry = Machine.current().network().listener(this.server.origin().pathEncoding());
        return entry.isRec() ? entry.asRec().at(uri(ROUTE)) : noobj();
    }

    /**
     * The primary WebSocket connection path for this test, derived from the
     * first entry in {@link #routes()}.
     */
    protected String primaryRoutePath() {
        final Obj routes = this.routes();
        if (routes.isNoObj()) return "/";
        return routes.asRec().elements()
                .map(r -> r.first().uriValue().toString())
                .findFirst()
                .orElse("/");
    }

    // ========================================
    // Lifecycle
    // ========================================
    @BeforeAll
    public void setupWsServer() {
        InstSet.importInstSet(WEB_ISA_TID);
        this.server = createWSServer();
        assertNotNull(this.server, "the ws_server should be created");

        // Derive host and port from the server's socket
        this.wsHost = this.server.origin().host();
        this.wsPort = this.server.origin().port();

        // a constructed ws_server is already listening (its construction awaits the bind)

        // InstSet.importInstSet(WEB_ISA_TID) above registers every web type (WS_MCP_HANDLER_TYPE at
        // WS_MCP_SERVER_TID, etc.), which is what a route's handler resolves against per connection.

        this.httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    public void closeClientWebSocket() {
        if (this.webSocket != null) {
            try {
                this.webSocket.sendClose(1000, "test complete");
            } catch (final Exception ignored) {
            }
            this.webSocket = null;
        }
    }

    @AfterAll
    public void teardownWsServer() {
        if (this.server != null) {
            // the inverse of the construction: this machine stops serving, and the socket it opened is released
            Machine.current().network().unlisten(this.server.origin());
            Machine.current().memory().removeSpace(WEB_ISA_TID);
            this.server = null;
        }
        if (this.httpClient != null) {
            this.httpClient.close();
            this.httpClient = null;
        }
    }

    // ========================================
    // WebSocket client helpers
    // ========================================

    private final AtomicReference<String> lastResponse = new AtomicReference<>(null);
    private volatile CountDownLatch responseLatch = new CountDownLatch(1);

    protected WebSocket connectToServer(final String path) throws Exception {
        final java.net.URI wsUri = java.net.URI.create("ws://" + wsHost + ":" + wsPort + path);
        final CountDownLatch openLatch = new CountDownLatch(1);
        final AtomicReference<WebSocket> wsRef = new AtomicReference<>();
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();

        this.lastResponse.set(null);
        this.responseLatch = new CountDownLatch(1);

        this.httpClient.newWebSocketBuilder()
                .buildAsync(wsUri, new WebSocket.Listener() {
                    @Override
                    public void onOpen(final WebSocket webSocket) {
                        wsRef.set(webSocket);
                        webSocket.request(1);
                        openLatch.countDown();
                    }

                    @Override
                    public CompletionStage<?> onText(final WebSocket webSocket,
                                                     final CharSequence data,
                                                     final boolean last) {
                        lastResponse.set(data.toString());
                        responseLatch.countDown();
                        webSocket.request(1);
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }

                    @Override
                    public void onError(final WebSocket webSocket, final Throwable error) {
                        errorRef.set(error);
                        openLatch.countDown();
                        responseLatch.countDown();
                    }
                })
                // a refused connection or a failed handshake fails this future and never reaches the listener: without
                // it, such a failure could only time out and say nothing of why
                .whenComplete((socket, failure) -> {
                    if (null != failure) {
                        errorRef.set(failure);
                        openLatch.countDown();
                    }
                });

        final boolean opened = openLatch.await(getWsTimeoutSeconds(), TimeUnit.SECONDS);
        assertTrue(opened, "websocket connection should open within timeout");
        assertNull(errorRef.get(), "websocket connection should not error: " + errorRef.get());

        this.webSocket = wsRef.get();
        assertNotNull(this.webSocket, "websocket should be connected");
        return this.webSocket;
    }

    protected String sendAndReceive(final String message) throws Exception {
        assertNotNull(this.webSocket, "websocket must be connected before sending");
        this.responseLatch = new CountDownLatch(1);
        this.lastResponse.set(null);
        this.webSocket.sendText(message, true);
        this.responseLatch.await(getWsTimeoutSeconds(), TimeUnit.SECONDS);
        return this.lastResponse.get();
    }

    protected ByteBuffer sendAndReceiveBinary(final ByteBuffer message) throws Exception {
        assertNotNull(this.webSocket, "websocket must be connected before sending");
        final CountDownLatch binaryLatch = new CountDownLatch(1);
        final AtomicReference<ByteBuffer> responseRef = new AtomicReference<>();
        this.webSocket.sendBinary(message, true);
        final boolean received = binaryLatch.await(getWsTimeoutSeconds(), TimeUnit.SECONDS);
        return received ? responseRef.get() : null;
    }

    protected int getWsTimeoutSeconds() {
        return 5;
    }

    // ========================================
    // Base integration tests
    // ========================================

    @Test
    public void testServerTypeIsRegisteredInRouter() {
        final Obj routes = this.routes();
        assertFalse(routes.isNoObj(), "the ws_server should keep a route table");
        routes.asRec().elements().forEach(r -> {
            final Obj type = Machine.current().memory().read(r.second().uriValue());
            assertFalse(type.isNoObj(),
                    "Type should be registered in Router at " + r.second().uriValue());
            assertTrue(type.isType(),
                    "Registered object should be a Type, got: " + type);
        });
    }

    @Test
    public void testWebSocketConnection() throws Exception {
        final WebSocket ws = connectToServer(primaryRoutePath());
        assertNotNull(ws, "websocket should be connected");
        assertFalse(ws.isOutputClosed(), "websocket output should be open");
        assertFalse(ws.isInputClosed(), "websocket input should be open");
    }

    @Test
    public void testWebSocketConnectionAndClosure() throws Exception {
        final WebSocket ws = connectToServer(primaryRoutePath());
        assertNotNull(ws, "websocket should connect");
        final CompletableFuture<WebSocket> closeFuture = ws.sendClose(1000, "test done");
        closeFuture.get(getWsTimeoutSeconds(), TimeUnit.SECONDS);
    }
}
