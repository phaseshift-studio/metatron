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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.CommonUtil;

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.ROUTE;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/**
 * Abstract base for integration-testing WSServer implementations against a
 * real wsSpace and live WebSocket connection.
 * <p>
 * Subclasses implement exactly one method:
 * <pre>
 *   protected wsSpace createWSSpace();
 * </pre>
 * Everything — host, port, routes, vid — is derived from the returned space:
 * <ul>
 *   <li>{@code space.at(HOST).uriValue()} → host and port</li>
 *   <li>{@code space.at(ROUTE)} → route table (path → type TID)</li>
 * </ul>
 * The first entry in the route table is used as the primary connection path
 * for the base-class tests.
 */
public abstract class AbstractWebSocketServerIntegrationTest extends AbstractMetatronTest {

    protected wsSpace space;
    protected String wsHost;
    protected int wsPort;
    protected HttpClient httpClient;
    protected WebSocket webSocket;

    // ========================================
    // Single abstract method
    // ========================================

    /**
     * Create and return the fully configured, started wsSpace under test.
     * The space must already be bound to its host/port when returned.
     * Use {@link #generatePort()} inside this method to pick a free port.
     */
    protected abstract wsSpace createWSSpace();

    // ========================================
    // Derived helpers — no overrides needed
    // ========================================

    /**
     * The primary WebSocket connection path for this test, derived from the
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

    // ========================================
    // Lifecycle
    // ========================================
    @BeforeEach
    public void setupWsSpace() {
        InstSet.importInstSet(WEB_ISA_TID);
        this.space = createWSSpace();
        assertNotNull(this.space, "wsSpace should be created");

        // Derive host and port from the space
        this.wsHost = this.space.at(HOST).uriValue().host();
        this.wsPort = this.space.at(HOST).uriValue().port();

        // Give the server a moment to start
        CommonUtil.sleepThread(500);

        // InstSet.importInstSet(WEB_ISA_TID) above already registers all web types
        // (WS_MCP_HANDLER_TYPE at WS_MCP_SERVER_TID, etc.) in the Router.
        // createServer() reads from the TID directly via routeFromSpace(), so no
        // additional per-host URI registration is needed here.

        this.httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    public void teardownWsSpace() {
        if (this.webSocket != null) {
            try {
                this.webSocket.sendClose(1000, "test complete");
            } catch (final Exception ignored) {
            }
            this.webSocket = null;
        }
        if (this.space != null) {
            Router.global().removeSpace(this.space.vid());
            Router.global().removeSpace(WEB_ISA_TID);
            this.space.close();
            this.space = null;
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
                });

        final boolean opened = openLatch.await(getWsTimeoutSeconds(), TimeUnit.SECONDS);
        assertTrue(opened, "WebSocket connection should open within timeout");
        assertNull(errorRef.get(), "WebSocket connection should not error: " + errorRef.get());

        this.webSocket = wsRef.get();
        assertNotNull(this.webSocket, "WebSocket should be connected");
        return this.webSocket;
    }

    protected String sendAndReceive(final String message) throws Exception {
        assertNotNull(this.webSocket, "WebSocket must be connected before sending");
        this.responseLatch = new CountDownLatch(1);
        this.lastResponse.set(null);
        this.webSocket.sendText(message, true);
        final boolean received = responseLatch.await(getWsTimeoutSeconds(), TimeUnit.SECONDS);
        if (!received) return null;
        return this.lastResponse.get();
    }

    protected ByteBuffer sendAndReceiveBinary(final ByteBuffer message) throws Exception {
        assertNotNull(this.webSocket, "WebSocket must be connected before sending");
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
        final Obj routes = space.at(ROUTE);
        assertFalse(routes.isNoObj(), "wsSpace should have a route table");
        routes.asRec().elements().forEach(r -> {
            final Obj type = Router.global().read(r.second().uriValue());
            assertFalse(type.isNoObj(),
                    "Type should be registered in Router at " + r.second().uriValue());
            assertTrue(type.isType(),
                    "Registered object should be a Type, got: " + type);
        });
    }

    @Test
    public void testWebSocketConnection() throws Exception {
        final WebSocket ws = connectToServer(primaryRoutePath());
        assertNotNull(ws, "WebSocket should be connected");
        assertFalse(ws.isOutputClosed(), "WebSocket output should be open");
        assertFalse(ws.isInputClosed(), "WebSocket input should be open");
    }

    @Test
    public void testWebSocketConnectionAndClosure() throws Exception {
        final WebSocket ws = connectToServer(primaryRoutePath());
        assertNotNull(ws, "WebSocket should connect");
        final CompletableFuture<WebSocket> closeFuture = ws.sendClose(1000, "test done");
        closeFuture.get(getWsTimeoutSeconds(), TimeUnit.SECONDS);
    }
}
