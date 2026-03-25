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
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import org.java_websocket.WebSocket;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transport provider that creates MCP transports for WebSocket connections.
 * Manages the lifecycle of MCP sessions over WebSocket connections.
 *
 * This provider acts as a factory, creating a new transport and session for each
 * WebSocket client connection. It maintains a registry of active sessions and
 * handles broadcasting notifications to all connected clients.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class McpWebSocketTransportProvider implements McpServerTransportProvider {

    protected final ObjectMapper objectMapper;
    protected final Map<String, McpServerSession> sessions = new ConcurrentHashMap<>();
    protected final Map<WebSocket, McpWebSocketTransport> transports = new ConcurrentHashMap<>();
    protected McpServerSession.Factory sessionFactory;
    protected JsonRpcToolDispatcher toolDispatcher;

    public McpWebSocketTransportProvider(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Sets the tool dispatcher for handling tools/call requests.
     * This is a workaround for the MCP SDK bug where tool handlers are not invoked.
     *
     * @param toolDispatcher The custom tool dispatcher
     */
    public void setToolDispatcher(final JsonRpcToolDispatcher toolDispatcher) {
        this.toolDispatcher = toolDispatcher;
    }

    @Override
    public void setSessionFactory(final McpServerSession.Factory sessionFactory) {
        this.sessionFactory = sessionFactory;
        System.out.println("DEBUG: Session factory set in transport provider");
    }

    /**
     * Creates a new MCP session for a WebSocket connection.
     * Called when a new WebSocket client connects and sends MCP protocol messages.
     *
     * @param webSocket The WebSocket connection
     * @param sessionId Unique identifier for this session
     * @return The created MCP session
     */
    public McpServerSession createSession(final WebSocket webSocket, final String sessionId) {
        System.out.println("DEBUG: createSession called, sessionFactory = " + sessionFactory);
        if (sessionFactory == null) {
            throw new IllegalStateException("Session factory not set. MCP server must be initialized first.");
        }

        // Create transport for this specific WebSocket connection
        // Pass the tool dispatcher to enable custom tool handling
        final McpWebSocketTransport transport = new McpWebSocketTransport(webSocket, objectMapper, toolDispatcher);

        // Create session using the factory provided by McpServer
        final McpServerSession session = sessionFactory.create(transport);
        // Link the session back to the transport so it can handle incoming messages
        transport.setSession(session);

        // Register session and transport for notifications and message routing
        sessions.put(sessionId, session);
        transports.put(webSocket, transport);

        return session;
    }

    /**
     * Removes a session when the WebSocket connection closes.
     *
     * @param sessionId The session identifier to remove
     */
    public void removeSession(final String sessionId) {
        sessions.remove(sessionId);
    }

    /**
     * Removes a transport when the WebSocket connection closes.
     *
     * @param webSocket The WebSocket connection
     */
    public void removeTransport(final WebSocket webSocket) {
        transports.remove(webSocket);
    }

    @Override
    public Mono<Void> notifyClients(final String method, final Object params) {
        return Mono.when(
            sessions.values().stream()
                .map(session -> session.sendNotification(method, params))
                .toList()
        );
    }

    @Override
    public Mono<Void> notifyClient(final String sessionId, final String method, final Object params) {
        final McpServerSession session = sessions.get(sessionId);
        if (session == null) {
            return Mono.empty();
        }
        return session.sendNotification(method, params);
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.when(
            sessions.values().stream()
                .map(McpServerSession::closeGracefully)
                .toList()
        ).then(Mono.fromRunnable(sessions::clear));
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * Sends a raw JSON message to a specific session for processing.
     * The message will be parsed and handled by the MCP session.
     *
     * @param webSocket The WebSocket connection
     * @param message The raw JSON-RPC message string
     */
    public void sendMessageToSession(final WebSocket webSocket, final String message) {
        // The MCP SDK doesn't provide a direct way to push messages into a session
        // Instead, we need to use the transport's message handling mechanism
        // For now, we'll store the message in the transport and let it be processed
        final McpWebSocketTransport transport = transports.get(webSocket);
        if (transport != null) {
            transport.handleIncomingMessage(message);
        }
    }

    /**
     * Gets the transport for a specific WebSocket connection.
     *
     * @param webSocket The WebSocket connection
     * @return The transport, or null if not found
     */
    public McpWebSocketTransport getTransport(final WebSocket webSocket) {
        return transports.get(webSocket);
    }
}
