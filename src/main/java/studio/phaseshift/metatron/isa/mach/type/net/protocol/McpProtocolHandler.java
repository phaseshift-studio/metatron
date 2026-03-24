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

package studio.phaseshift.metatron.isa.mach.type.net.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpServerSession;
import org.java_websocket.WebSocket;
import studio.phaseshift.metatron.isa.mach.type.net.mcp.MetatronMcpServer;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP (Model Context Protocol) protocol handler.
 * Handles JSON-RPC 2.0 messages conforming to the MCP specification.
 * <p>
 * This handler enables AI assistants to interact with Metatron through
 * a standardized protocol, providing tools for code evaluation, system
 * introspection, and instruction discovery.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class McpProtocolHandler implements MServerProtocolHandler {

    private final MetatronMcpServer mcpServer;
    private final Map<WebSocket, McpServerSession> mcpSessions = new ConcurrentHashMap<>();
    private final ObjectMapper jsonMapper;
    private final GraphittyLogger LOG;

    public McpProtocolHandler(final GraphittyLogger LOG) {
        this.LOG = LOG;
        this.jsonMapper = new ObjectMapper();
        this.jsonMapper.findAndRegisterModules();
        this.mcpServer = new MetatronMcpServer();
        LOG.info("MCP protocol handler initialized");
    }

    @Override
    public String protocolName() {
        return "mcp";
    }

    @Override
    public int connections() {
        return this.mcpSessions.size();
    }

    @Override
    public boolean canHandle(final String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        final String trimmed = message.trim();
        // MCP messages are JSON objects with "jsonrpc": "2.0" field
        return trimmed.startsWith("{") && trimmed.contains("\"jsonrpc\"");
    }

    @Override
    public boolean canHandle(final ByteBuffer message) {
        // MCP uses text/JSON messages, not binary
        return false;
    }

    @Override
    public void handleMessage(final WebSocket conn, final String message) {
        try {
            // Get or create MCP session for this connection
            McpServerSession session = mcpSessions.get(conn);
            if (session == null) {
                // Create new session
                final String sessionId = conn.getAttachment() != null ?
                        conn.getAttachment().toString() :
                        "mcp-" + System.currentTimeMillis();
                session = mcpServer.getTransportProvider().createSession(conn, sessionId);
                mcpSessions.put(conn, session);
                LOG.info("Created new MCP session: %s", sessionId);
            }

            // Send the raw JSON string to the transport which will handle parsing
            mcpServer.getTransportProvider().sendMessageToSession(conn, message);

        } catch (final Exception e) {
            LOG.error("Failed to process MCP message: %s", e.getMessage());
            // Send error response back to client
            sendErrorResponse(conn, -32700, "Parse error", e.getMessage());
        }
    }

    @Override
    public void handleMessage(final WebSocket conn, final ByteBuffer message) {
        // MCP doesn't use binary messages - this shouldn't be called
        LOG.warn("MCP protocol received unexpected binary message from %s", conn.getAttachment());
    }

    @Override
    public void onConnectionOpen(final WebSocket conn) {
        LOG.debug("MCP protocol connection opened: %s", conn.getAttachment());
        // Session will be created on first message
    }

    @Override
    public void onConnectionClose(final WebSocket conn, final int code, final String reason) {
        // Clean up MCP session if exists
        final McpServerSession mcpSession = mcpSessions.remove(conn);
        if (mcpSession != null) {
            final String sessionId = conn.getAttachment() != null ?
                    conn.getAttachment().toString() :
                    "unknown";
            mcpServer.getTransportProvider().removeSession(sessionId);
            mcpServer.getTransportProvider().removeTransport(conn);
            LOG.info("closed MCP session %s with exit code %s [reason: %s]", sessionId, code, reason);
        }
    }

    @Override
    public void shutdown() {
        LOG.info("MCP protocol handler shutting down");
        if (mcpServer != null) {
            mcpServer.close();
        }
    }

    /**
     * Sends a JSON-RPC error response to the client.
     */
    private void sendErrorResponse(final WebSocket conn, final int code, final String message, final String data) {
        try {
            final Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("jsonrpc", "2.0");
            errorResponse.put("error", Map.of(
                    "code", code,
                    "message", message,
                    "data", data
            ));
            conn.send(jsonMapper.writeValueAsString(errorResponse));
        } catch (final Exception ex) {
            LOG.error("Failed to send error response: %s", ex.getMessage());
        }
    }

    public MetatronMcpServer getMcpServer() {
        return mcpServer;
    }
}
