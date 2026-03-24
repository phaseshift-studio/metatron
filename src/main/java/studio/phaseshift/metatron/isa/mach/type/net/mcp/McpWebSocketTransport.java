/*
 * metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.isa.mach.type.net.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import org.java_websocket.WebSocket;
import reactor.core.publisher.Mono;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

/**
 * WebSocket-based transport implementation for MCP server.
 * Handles JSON-RPC message exchange over a WebSocket connection.
 *
 * This transport bridges between MCP's reactive JSON-RPC protocol and
 * metatron's WebSocket infrastructure.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class McpWebSocketTransport implements McpServerTransport {

    protected static final GraphittyLogger LOG = Graphitty.log(McpWebSocketTransport.class);
    protected final WebSocket webSocket;
    protected final ObjectMapper objectMapper;
    protected final JsonRpcToolDispatcher toolDispatcher;
    protected volatile boolean closed = false;
    protected volatile McpServerSession session;

    public McpWebSocketTransport(final WebSocket webSocket, final ObjectMapper objectMapper, final JsonRpcToolDispatcher toolDispatcher) {
        this.webSocket = webSocket;
        this.objectMapper = objectMapper;
        this.toolDispatcher = toolDispatcher;
    }

    /**
     * Sets the session associated with this transport.
     * This is called after the session is created.
     *
     * @param session The MCP server session
     */
    public void setSession(final McpServerSession session) {
        this.session = session;
    }

    /**
     * Handles an incoming JSON message from the WebSocket.
     * Parses the message and passes it to the session for processing.
     *
     * WORKAROUND: Intercepts tools/call requests and handles them with our custom
     * dispatcher to bypass the MCP SDK bug where tool handlers are not invoked.
     *
     * @param message The raw JSON message string
     */
    public void handleIncomingMessage(final String message) {
        if (session != null) {
            try {
                LOG.debug("Handling incoming MCP message: %s", message);

                // WORKAROUND: Check if this is a tools/call request and handle it directly
                if (toolDispatcher != null && toolDispatcher.isToolCallRequest(message)) {
                    LOG.info("Intercepting tools/call request for custom dispatcher");
                    final String response = toolDispatcher.handleToolCall(message);
                    LOG.debug("Sending dispatcher response: %s", response);
                    webSocket.send(response);
                    return;
                }

                // For all other messages (initialize, tools/list, etc.), use the SDK
                // First parse as a generic map to inspect the message
                @SuppressWarnings("unchecked")
                final java.util.Map<String, Object> jsonMap = objectMapper.readValue(message, java.util.Map.class);

                // Determine the message type and create the appropriate JSONRPCMessage subclass
                final McpSchema.JSONRPCMessage jsonRpcMessage;

                if (jsonMap.containsKey("method")) {
                    // This is either a request or notification
                    if (jsonMap.containsKey("id")) {
                        // Request
                        LOG.debug("Parsed as JSONRPCRequest: method=%s, id=%s", jsonMap.get("method"), jsonMap.get("id"));
                        jsonRpcMessage = objectMapper.convertValue(jsonMap, McpSchema.JSONRPCRequest.class);
                    } else {
                        // Notification
                        LOG.debug("Parsed as JSONRPCNotification: method=%s", jsonMap.get("method"));
                        jsonRpcMessage = objectMapper.convertValue(jsonMap, McpSchema.JSONRPCNotification.class);
                    }
                } else if (jsonMap.containsKey("result") || jsonMap.containsKey("error")) {
                    // Response
                    LOG.debug("Parsed as JSONRPCResponse: id=%s", jsonMap.get("id"));
                    jsonRpcMessage = objectMapper.convertValue(jsonMap, McpSchema.JSONRPCResponse.class);
                } else {
                    throw new IllegalArgumentException("Invalid JSON-RPC message format");
                }

                // Now handle the properly typed message
                LOG.debug("Passing message to session.handle()");
                session.handle(jsonRpcMessage)
                    .doOnSuccess(v -> LOG.debug("Session handled message successfully"))
                    .doOnError(error -> {
                        LOG.error("Session error handling message: %s", error.toString());
                        sendErrorResponse(-32603, "Internal error", error.toString());
                    })
                    .subscribe();
            } catch (final Exception e) {
                // Failed to parse message - send parse error
                LOG.error("Failed to parse MCP message: %s", e.getMessage());
                sendErrorResponse(-32700, "Parse error", e.getMessage());
            }
        } else {
            LOG.warn("Received message but session is null");
        }
    }

    /**
     * Sends an error response to the client.
     */
    private void sendErrorResponse(final int code, final String message, final String data) {
        try {
            final java.util.Map<String, Object> errorResponse = new java.util.HashMap<>();
            errorResponse.put("jsonrpc", "2.0");
            errorResponse.put("error", java.util.Map.of(
                "code", code,
                "message", message,
                "data", data
            ));
            webSocket.send(objectMapper.writeValueAsString(errorResponse));
        } catch (final Exception ex) {
            // Failed to send error response - nothing we can do
        }
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.fromRunnable(() -> {
            if (!closed) {
                closed = true;
                webSocket.close();
            }
        });
    }

    @Override
    public Mono<Void> sendMessage(final McpSchema.JSONRPCMessage message) {
        return Mono.fromRunnable(() -> {
            if (closed) {
                throw new IllegalStateException("Transport is closed");
            }
            try {
                final String json = objectMapper.writeValueAsString(message);
                LOG.debug("Sending MCP message: %s", json);
                webSocket.send(json);
                LOG.debug("MCP message sent successfully");
            } catch (final Exception e) {
                LOG.error("Failed to send MCP message: %s", e.getMessage());
                throw new RuntimeException("Failed to send MCP message", e);
            }
        });
    }

    @Override
    public <T> T unmarshalFrom(final Object data, final TypeRef<T> typeRef) {
        try {
            return objectMapper.convertValue(data, objectMapper.constructType(typeRef.getType()));
        } catch (final Exception e) {
            throw new RuntimeException("Failed to unmarshal data", e);
        }
    }

    public boolean isClosed() {
        return closed;
    }
}
