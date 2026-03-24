/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.isa.mach.type.net;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TestCategory;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjByteBufferSerializer;
import studio.phaseshift.metatron.util.CommonUtil;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

/**
 * Comprehensive test suite for MServer covering both protocol entry points:
 * 1. Native metatron protocol using ObjByteBufferSerializer
 * 2. MCP (Model Context Protocol) using JSON-RPC
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MServerTest extends AbstractMetatronTest {

    protected static MServer server;
    protected static fURI serverHost;
    protected static int serverPort;
    protected static final ObjByteBufferSerializer serializer = new ObjByteBufferSerializer();
    protected static final ObjectMapper jsonMapper = new ObjectMapper();

    @BeforeAll
    public static void setupServer() throws Exception {
        // Generate random port for test server
        serverPort = generatePort();
        serverHost = f("ws://localhost:" + serverPort);

        STATIC_LOG.info("Starting test MServer on %s", serverHost);

        // Create and start server
        server = new MServer(serverHost, List.of());
        server.start();

        // Wait for server to be ready
        int attempts = 0;
        while (!server.isRunning() && attempts < 50) {
            Thread.sleep(100);
            attempts++;
        }

        assertTrue(server.isRunning(), "Server should be running after startup");
        STATIC_LOG.info("MServer started successfully on %s", serverHost);

        // Configure JSON mapper
        jsonMapper.findAndRegisterModules();
    }

    @AfterAll
    public static void teardownServer() {
        if (server != null) {
            STATIC_LOG.info("Shutting down test MServer");
            server.close();

            // Wait for server to stop
            int attempts = 0;
            while (server.isRunning() && attempts < 50) {
                CommonUtil.sleepThread(100);
                attempts++;
            }

            assertFalse(server.isRunning(), "Server should be stopped after close");
            STATIC_LOG.info("MServer stopped successfully");
        }
    }

    // ========================================
    // Server Lifecycle Tests
    // ========================================

    @Test
    @Order(1)
    @TestCategory.Crud
    public void testServerStartup() {
        assertNotNull(server, "Server should be initialized");
        assertTrue(server.isRunning(), "Server should be running");
        assertEquals(serverHost, server.host(), "Server host should match");
        assertNotNull(server.getSerializer(), "Server should have serializer");
        assertInstanceOf(ObjByteBufferSerializer.class, server.getSerializer(),
                "Server should use ObjByteBufferSerializer");
    }

    @Test
    @Order(2)
    @TestCategory.Type
    public void testServerProperties() {
        assertEquals(MServer.MSERVER_TID, server.tid(), "server tid should match");
        assertNotNull(server.vid(), "server vid should not be null");
        assertTrue(server.vid().toString().contains("server"), "Server vid should contain 'server'");
        assertNotNull(server.nodes(), "server nodes map should not be null");
    }

    // ========================================
    // Native Protocol Tests (ObjByteBufferSerializer)
    // ========================================

    /**
     * Test basic connection and disconnection with native protocol.
     */
    @Test
    @Order(10)
    @TestCategory.Crud
    public void testNativeProtocolConnection() throws Exception {
        final CountDownLatch openLatch = new CountDownLatch(1);
        final CountDownLatch closeLatch = new CountDownLatch(1);

        final WebSocketClient client = new WebSocketClient(URI.create(serverHost.toString())) {
            @Override
            public void onOpen(final ServerHandshake handshake) {
                LOG.debug("Native client connected");
                openLatch.countDown();
            }

            @Override
            public void onMessage(final String message) {
                LOG.debug("Received string message: %s", message);
            }

            @Override
            public void onMessage(final ByteBuffer bytes) {
                LOG.debug("Received byte buffer: %d bytes", bytes.array().length);
            }

            @Override
            public void onClose(final int code, final String reason, final boolean remote) {
                LOG.debug("Native client closed: %d - %s", code, reason);
                closeLatch.countDown();
            }

            @Override
            public void onError(final Exception ex) {
                LOG.error("Native client error: %s", ex.getMessage());
            }
        };

        client.connect();
        assertTrue(openLatch.await(5, TimeUnit.SECONDS), "Client should connect within 5 seconds");

        client.close();
        assertTrue(closeLatch.await(5, TimeUnit.SECONDS), "Client should close within 5 seconds");
    }

    /**
     * Test sending and receiving metatron objects via native protocol.
     */
    @Test
    @Order(11)
    @TestCategory.Crud
    @TestCategory.Type
    @Disabled
    public void testNativeProtocolSendReceive() throws Exception {
        final CountDownLatch openLatch = new CountDownLatch(1);
        final CountDownLatch responseLatch = new CountDownLatch(1);
        final AtomicReference<Obj> receivedObj = new AtomicReference<>();

        final WebSocketClient client = new WebSocketClient(URI.create(serverHost.toString())) {
            @Override
            public void onOpen(final ServerHandshake handshake) {
                LOG.debug("Connected, sending test object");
                openLatch.countDown();
                // Send a simple expression to evaluate
                final Obj testObj = mParser.parse("1.plus(2)");
                final ByteBuffer buffer = serializer.outputBytes(testObj);
                send(buffer);
            }

            @Override
            public void onMessage(final ByteBuffer bytes) {
                LOG.debug("Received response: %d bytes", bytes.array().length);
                final Obj obj = serializer.inputBytes(bytes);
                receivedObj.set(obj);
                responseLatch.countDown();
            }

            @Override
            public void onMessage(final String message) {
                // Should not receive string messages for native protocol
                LOG.warn("Unexpected string message: %s", message);
            }

            @Override
            public void onClose(final int code, final String reason, final boolean remote) {
                LOG.debug("Connection closed");
            }

            @Override
            public void onError(final Exception ex) {
                LOG.error("Error: %s", ex.getMessage());
            }
        };

        client.connectBlocking();
        assertTrue(openLatch.await(5, TimeUnit.SECONDS), "Should connect within 5 seconds");
        assertTrue(responseLatch.await(10, TimeUnit.SECONDS), "Should receive response within 10 seconds");

        assertNotNull(receivedObj.get(), "Should receive an object");
        LOG.info("Received object: %s", receivedObj.get());

        // The result should contain the evaluated result (3)
        assertTrue(receivedObj.get().stream().anyMatch(o -> o.equals(jnt(3))),
                "Response should contain result of 1+2=3");

        client.closeBlocking();
    }

    /**
     * Parameterized test for various metatron expressions via native protocol.
     */
    @ParameterizedTest
    @Order(12)
    @TestCategory.Type
    @CsvSource(value = {
            "1.plus(2)                       | 3",
            "10.mult(5)                      | 50",
            "\"hello\".to(test)              | str::\"hello\"",
            "[1,2,3].>-.count()              | 3",
            "[1,2,3].count()                 | 1",
            "[a=>1,b=>2]>>a                  | 1",
            "true.and(false)                 | false",
            "5.gt(3)                         | true"
    }, delimiter = '|')
    public void testNativeProtocolExpressions(final String expression, final String expectedResult) throws Exception {
        final CountDownLatch openLatch = new CountDownLatch(1);
        final CountDownLatch responseLatch = new CountDownLatch(1);
        final AtomicReference<Obj> receivedObj = new AtomicReference<>();

        final WebSocketClient client = new WebSocketClient(URI.create(serverHost.toString())) {
            @Override
            public void onOpen(final ServerHandshake handshake) {
                openLatch.countDown();
                final Obj testObj = mParser.parse(expression.trim());
                final ByteBuffer buffer = serializer.outputBytes(testObj);
                send(buffer);
            }

            @Override
            public void onMessage(final ByteBuffer bytes) {
                final Obj obj = serializer.inputBytes(bytes);
                receivedObj.set(obj);
                responseLatch.countDown();
            }

            @Override
            public void onMessage(final String message) {
            }

            @Override
            public void onClose(final int code, final String reason, final boolean remote) {
            }

            @Override
            public void onError(final Exception ex) {
                LOG.error("Error in test: %s", ex.getMessage());
            }
        };

        client.connectBlocking();
        assertTrue(openLatch.await(5, TimeUnit.SECONDS), "Should connect for: " + expression);
        assertTrue(responseLatch.await(10, TimeUnit.SECONDS),
                "Should receive response for: " + expression);

        assertNotNull(receivedObj.get(), "Should receive response object");

        final Obj expected = mParser.parse(expectedResult.trim());
        assertTrue(receivedObj.get().stream().anyMatch(o -> o.equals(expected)),
                String.format("Response should contain %s for expression %s, but got %s",
                        expected, expression, receivedObj.get()));

        client.closeBlocking();
    }

    /**
     * Test serialization round-trip through server.
     */
    @ParameterizedTest
    @Order(13)
    @TestCategory.Type
    @ValueSource(strings = {
           // "noobj",
            "1",
            "3.14",
            "true",
            "false",
            "\"test string\"",
            "<http://example.com>",
            "[1,2,3]",
            "[a=>1,b=>2]",
            //"{1,2,3}",
            "[a=>[b=>1,c=>2],d=>3]"
    })
    public void testNativeProtocolSerialization(final String objString) throws Exception {
        final CountDownLatch openLatch = new CountDownLatch(1);
        final CountDownLatch responseLatch = new CountDownLatch(1);
        final AtomicReference<Obj> receivedObj = new AtomicReference<>();

        final Obj originalObj = mParser.parse(objString);

        final WebSocketClient client = new WebSocketClient(URI.create(serverHost.toString())) {
            @Override
            public void onOpen(final ServerHandshake handshake) {
                openLatch.countDown();
                final Obj testObj = mParser.parse(objString);
                final ByteBuffer buffer = serializer.outputBytes(testObj);
                send(buffer);
            }

            @Override
            public void onMessage(final ByteBuffer bytes) {
                final Obj obj = serializer.inputBytes(bytes);
                receivedObj.set(obj);
                responseLatch.countDown();
            }

            @Override
            public void onMessage(final String message) {
            }

            @Override
            public void onClose(final int code, final String reason, final boolean remote) {
            }

            @Override
            public void onError(final Exception ex) {
                LOG.error("Serialization test error: %s", ex.getMessage());
            }
        };

        client.connectBlocking();
        assertTrue(openLatch.await(5, TimeUnit.SECONDS), "Should connect for: " + objString);
        assertTrue(responseLatch.await(10, TimeUnit.SECONDS),
                "Should receive response for: " + objString);

        assertNotNull(receivedObj.get(), "Should receive response");

        // Check that the original object is in the response
        assertTrue(receivedObj.get().stream().anyMatch(o -> o.equals(originalObj)),
                String.format("Serialization round-trip failed for %s", objString));

        client.closeBlocking();
    }

    // ========================================
    // MCP Protocol Tests (JSON-RPC)
    // ========================================

    /**
     * Test MCP protocol detection and routing.
     */
    @Test
    @Order(20)
    @TestCategory.Crud
    public void testMcpProtocolDetection() throws Exception {
        final CountDownLatch openLatch = new CountDownLatch(1);
        final CountDownLatch responseLatch = new CountDownLatch(1);
        final AtomicReference<String> receivedMessage = new AtomicReference<>();

        final WebSocketClient client = new WebSocketClient(URI.create(serverHost.toString())) {
            @Override
            public void onOpen(final ServerHandshake handshake) {
                openLatch.countDown();
                LOG.debug("MCP client connected, sending initialize request");

                // Send MCP initialize request
                final Map<String, Object> request = new HashMap<>();
                request.put("jsonrpc", "2.0");
                request.put("id", 1);
                request.put("method", "initialize");
                request.put("params", Map.of(
                        "protocolVersion", "2024-11-05",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of(
                                "name", "test-client",
                                "version", "1.0.0"
                        )
                ));

                try {
                    final String json = jsonMapper.writeValueAsString(request);
                    LOG.debug("Sending MCP request: %s", json);
                    send(json);
                } catch (final Exception e) {
                    LOG.error("Failed to send MCP request: %s", e.getMessage());
                }
            }

            @Override
            public void onMessage(final String message) {
                LOG.debug("Received MCP response: %s", message);
                receivedMessage.set(message);
                responseLatch.countDown();
            }

            @Override
            public void onMessage(final ByteBuffer bytes) {
                LOG.warn("Unexpected byte buffer in MCP test");
            }

            @Override
            public void onClose(final int code, final String reason, final boolean remote) {
                LOG.debug("MCP client closed");
            }

            @Override
            public void onError(final Exception ex) {
                LOG.error("MCP client error: %s", ex.getMessage());
            }
        };

        client.connectBlocking();
        assertTrue(openLatch.await(5, TimeUnit.SECONDS), "Should connect for MCP test");
        assertTrue(responseLatch.await(10, TimeUnit.SECONDS),
                "Should receive MCP response within 10 seconds");

        assertNotNull(receivedMessage.get(), "Should receive MCP response");

        // Parse response and verify it's valid JSON-RPC
        final Map<String, Object> response = jsonMapper.readValue(
                receivedMessage.get(), Map.class);
        assertEquals("2.0", response.get("jsonrpc"), "Should be JSON-RPC 2.0");
        assertTrue(response.containsKey("result") || response.containsKey("error"),
                "Response should have result or error");

        client.closeBlocking();
    }

    /**
 

    // ========================================
    // Concurrent Connection Tests
    // ========================================

    /**
     * Test multiple simultaneous native protocol connections.
     */
    @Test
    @Order(30)
    @TestCategory.Concurrent
    public void testMultipleNativeConnections() throws Exception {
        final int clientCount = 5;
        final CountDownLatch allOpenLatch = new CountDownLatch(clientCount);
        final CountDownLatch allResponsesLatch = new CountDownLatch(clientCount);
        final List<WebSocketClient> clients = new ArrayList<>();
        final List<Obj> responses = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < clientCount; i++) {
            final int clientId = i;
            final WebSocketClient client = new WebSocketClient(URI.create(serverHost.toString())) {
                @Override
                public void onOpen(final ServerHandshake handshake) {
                    allOpenLatch.countDown();
                    // Each client sends a different expression
                    final Obj testObj = mParser.parse(clientId + ".plus(10)");
                    final ByteBuffer buffer = serializer.outputBytes(testObj);
                    send(buffer);
                }

                @Override
                public void onMessage(final ByteBuffer bytes) {
                    final Obj obj = serializer.inputBytes(bytes);
                    responses.add(obj);
                    allResponsesLatch.countDown();
                }

                @Override
                public void onMessage(final String message) {
                }

                @Override
                public void onClose(final int code, final String reason, final boolean remote) {
                }

                @Override
                public void onError(final Exception ex) {
                    LOG.error("Client %d error: %s", clientId, ex.getMessage());
                }
            };

            clients.add(client);
            client.connect();
        }

        assertTrue(allOpenLatch.await(10, TimeUnit.SECONDS), "All clients should connect");
        assertTrue(allResponsesLatch.await(15, TimeUnit.SECONDS),
                "All clients should receive responses");

        assertEquals(clientCount, responses.size(), "Should receive all responses");

        // Verify each response contains the expected result
        for (int i = 0; i < clientCount; i++) {
            final int expected = i + 10;
            final int finalI = i;
            assertTrue(responses.stream().anyMatch(r ->
                            r.stream().anyMatch(o -> o.equals(jnt(expected)))),
                    "Should have response for client " + finalI);
        }

        // Close all clients
        for (final WebSocketClient client : clients) {
            client.closeBlocking();
        }
    }

    /**
     * Test mixed protocol connections (native + MCP simultaneously).
     */
    @Test
    @Order(31)
    @TestCategory.Concurrent
    public void testMixedProtocolConnections() throws Exception {
        final CountDownLatch nativeOpenLatch = new CountDownLatch(1);
        final CountDownLatch mcpOpenLatch = new CountDownLatch(1);
        final CountDownLatch nativeLatch = new CountDownLatch(1);
        final CountDownLatch mcpLatch = new CountDownLatch(1);
        final AtomicReference<Obj> nativeResponse = new AtomicReference<>();
        final AtomicReference<String> mcpResponse = new AtomicReference<>();

        // Native protocol client
        final WebSocketClient nativeClient = new WebSocketClient(URI.create(serverHost.toString())) {
            @Override
            public void onOpen(final ServerHandshake handshake) {
                nativeOpenLatch.countDown();
                final Obj testObj = mParser.parse("100.plus(200)");
                send(serializer.outputBytes(testObj));
            }

            @Override
            public void onMessage(final ByteBuffer bytes) {
                nativeResponse.set(serializer.inputBytes(bytes));
                nativeLatch.countDown();
            }

            @Override
            public void onMessage(final String message) {
            }

            @Override
            public void onClose(final int code, final String reason, final boolean remote) {
            }

            @Override
            public void onError(final Exception ex) {
            }
        };

        // MCP protocol client
        final WebSocketClient mcpClient = new WebSocketClient(URI.create(serverHost.toString())) {
            @Override
            public void onOpen(final ServerHandshake handshake) {
                mcpOpenLatch.countDown();
                final Map<String, Object> request = new HashMap<>();
                request.put("jsonrpc", "2.0");
                request.put("id", 1);
                request.put("method", "initialize");
                request.put("params", Map.of(
                        "protocolVersion", "2024-11-05",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "test-mcp", "version", "1.0.0")
                ));

                try {
                    send(jsonMapper.writeValueAsString(request));
                } catch (final Exception e) {
                    LOG.error("MCP send error: %s", e.getMessage());
                }
            }

            @Override
            public void onMessage(final String message) {
                mcpResponse.set(message);
                mcpLatch.countDown();
            }

            @Override
            public void onMessage(final ByteBuffer bytes) {
            }

            @Override
            public void onClose(final int code, final String reason, final boolean remote) {
            }

            @Override
            public void onError(final Exception ex) {
            }
        };

        // Connect both clients simultaneously
        nativeClient.connect();
        mcpClient.connect();

        assertTrue(nativeOpenLatch.await(5, TimeUnit.SECONDS), "Native client should connect");
        assertTrue(mcpOpenLatch.await(5, TimeUnit.SECONDS), "MCP client should connect");
        assertTrue(nativeLatch.await(10, TimeUnit.SECONDS),
                "Native client should receive response");
        assertTrue(mcpLatch.await(10, TimeUnit.SECONDS),
                "MCP client should receive response");

        // Verify native response
        assertNotNull(nativeResponse.get(), "Native response should not be null");
        assertTrue(nativeResponse.get().stream().anyMatch(o -> o.equals(jnt(300))),
                "Native response should contain 300");

        // Verify MCP response
        assertNotNull(mcpResponse.get(), "MCP response should not be null");
        final Map<String, Object> mcpResult = jsonMapper.readValue(mcpResponse.get(), Map.class);
        assertEquals("2.0", mcpResult.get("jsonrpc"), "MCP response should be JSON-RPC 2.0");

        nativeClient.closeBlocking();
        mcpClient.closeBlocking();
    }

    // ========================================
    // Error Handling Tests
    // ========================================

    /**
     * Test native protocol error handling for invalid objects.
     */
    @Test
    @Order(40)
    @Disabled
    @TestCategory.Boundary
    public void testNativeProtocolErrorHandling() throws Exception {
        final CountDownLatch openLatch = new CountDownLatch(1);
        final CountDownLatch responseLatch = new CountDownLatch(1);
        final AtomicReference<Obj> receivedObj = new AtomicReference<>();

        final WebSocketClient client = new WebSocketClient(URI.create(serverHost.toString())) {
            @Override
            public void onOpen(final ServerHandshake handshake) {
                openLatch.countDown();
                // Send invalid bytecode that will cause parsing error
                final ByteBuffer invalidBuffer = ByteBuffer.wrap("invalid metatron code @#$d3".getBytes());
                send(invalidBuffer);
            }

            @Override
            public void onMessage(final ByteBuffer bytes) {
                final Obj obj = serializer.inputBytes(bytes);
                receivedObj.set(obj);
                responseLatch.countDown();
            }

            @Override
            public void onMessage(final String message) {
            }

            @Override
            public void onClose(final int code, final String reason, final boolean remote) {
            }

            @Override
            public void onError(final Exception ex) {
            }
        };

        client.connectBlocking();
        assertTrue(openLatch.await(5, TimeUnit.SECONDS), "Should connect for error test");
        assertTrue(responseLatch.await(10, TimeUnit.SECONDS), "Should receive error response");

        assertNotNull(receivedObj.get(), "Should receive response");
        // The response should contain a Fail object
        assertTrue(receivedObj.get().stream().anyMatch(Obj::isFail),
                "Response should contain a Fail object for invalid input");

        client.closeBlocking();
    }

    /**
     * Test MCP protocol error handling for invalid JSON.
     */
    @Test
    @Order(41)
    @TestCategory.Boundary
    public void testMcpProtocolErrorHandling() throws Exception {
        final CountDownLatch openLatch = new CountDownLatch(1);
        final CountDownLatch responseLatch = new CountDownLatch(1);
        final AtomicReference<String> receivedMessage = new AtomicReference<>();

        final WebSocketClient client = new WebSocketClient(URI.create(serverHost.toString())) {
            @Override
            public void onOpen(final ServerHandshake handshake) {
                openLatch.countDown();
                // Send invalid JSON
                send("{\"jsonrpc\": \"2.0\", invalid json here}");
            }

            @Override
            public void onMessage(final String message) {
                receivedMessage.set(message);
                responseLatch.countDown();
            }

            @Override
            public void onMessage(final ByteBuffer bytes) {
            }

            @Override
            public void onClose(final int code, final String reason, final boolean remote) {
            }

            @Override
            public void onError(final Exception ex) {
            }
        };

        client.connectBlocking();
        assertTrue(openLatch.await(5, TimeUnit.SECONDS), "Should connect for MCP error test");
        assertTrue(responseLatch.await(10, TimeUnit.SECONDS), "Should receive error response");

        assertNotNull(receivedMessage.get(), "Should receive error message");

        // Parse and verify it's an error response
        final Map<String, Object> response = jsonMapper.readValue(receivedMessage.get(), Map.class);
        assertTrue(response.containsKey("error"), "Response should contain error");

        final Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertEquals(-32700, error.get("code"), "Should be parse error code");

        client.closeBlocking();
    }

    // ========================================
    // Performance Tests
    // ========================================

    /**
     * Test throughput with rapid sequential requests.
     */
    @Test
    @Order(50)
    @TestCategory.Concurrent
    public void testNativeProtocolThroughput() throws Exception {
        final int requestCount = 20;
        final CountDownLatch openLatch = new CountDownLatch(1);
        final CountDownLatch responseLatch = new CountDownLatch(requestCount);
        final List<Obj> responses = Collections.synchronizedList(new ArrayList<>());

        final WebSocketClient client = new WebSocketClient(URI.create(serverHost.toString())) {
            protected int sentCount = 0;

            @Override
            public void onOpen(final ServerHandshake handshake) {
                openLatch.countDown();
                // Send multiple requests rapidly
                for (int i = 0; i < requestCount; i++) {
                    final Obj testObj = mParser.parse(i + ".plus(1)");
                    send(serializer.outputBytes(testObj));
                    sentCount++;
                }
                LOG.debug("Sent %d requests", sentCount);
            }

            @Override
            public void onMessage(final ByteBuffer bytes) {
                final Obj obj = serializer.inputBytes(bytes);
                responses.add(obj);
                responseLatch.countDown();
            }

            @Override
            public void onMessage(final String message) {
            }

            @Override
            public void onClose(final int code, final String reason, final boolean remote) {
            }

            @Override
            public void onError(final Exception ex) {
                LOG.error("Throughput test error: %s", ex.getMessage());
            }
        };

        final long startTime = System.currentTimeMillis();
        client.connectBlocking();
        assertTrue(openLatch.await(5, TimeUnit.SECONDS), "Should connect for throughput test");

        assertTrue(responseLatch.await(30, TimeUnit.SECONDS),
                "Should receive all responses within 30 seconds");

        final long duration = System.currentTimeMillis() - startTime;

        assertEquals(requestCount, responses.size(), "Should receive all responses");

        LOG.info("Processed %d requests in %d ms (%.2f req/sec)",
                requestCount, duration, (requestCount * 1000.0) / duration);

        client.closeBlocking();
    }
}
