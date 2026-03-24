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

import org.java_websocket.WebSocket;

import java.nio.ByteBuffer;

/**
 * Protocol handler interface for MServer.
 * Allows MServer to support multiple communication protocols (native Metatron, MCP, etc.)
 * by delegating message handling to protocol-specific implementations.
 *
 * Each protocol handler is responsible for:
 * - Detecting if a message belongs to its protocol
 * - Processing messages in its protocol format
 * - Managing protocol-specific session state
 * - Cleaning up when connections close
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface MServerProtocolHandler {

    /**
     * Returns the name of this protocol (e.g., "native", "mcp", "agent").
     */
    String protocolName();

    
    int connections();
    
    /**
     * Checks if a string message belongs to this protocol.
     * This is used for protocol detection when a text message arrives.
     *
     * @param message The incoming text message
     * @return true if this handler can process the message
     */
    boolean canHandle(final String message);

    /**
     * Checks if a binary message belongs to this protocol.
     * This is used for protocol detection when a binary message arrives.
     *
     * @param message The incoming binary message
     * @return true if this handler can process the message
     */
    boolean canHandle(final ByteBuffer message);

    /**
     * Handles an incoming text message.
     *
     * @param conn The WebSocket connection
     * @param message The text message
     */
    void handleMessage(final WebSocket conn, final String message);

    /**
     * Handles an incoming binary message.
     *
     * @param conn The WebSocket connection
     * @param message The binary message
     */
    void handleMessage(final WebSocket conn, final ByteBuffer message);

    /**
     * Called when a new WebSocket connection is opened.
     * Allows the protocol handler to initialize connection-specific state.
     *
     * @param conn The WebSocket connection
     */
    void onConnectionOpen(final WebSocket conn);

    /**
     * Called when a WebSocket connection is closed.
     * Allows the protocol handler to clean up connection-specific state.
     *
     * @param conn The WebSocket connection
     * @param code The close code
     * @param reason The close reason
     */
    void onConnectionClose(final WebSocket conn, final int code, final String reason);

    /**
     * Called when the server is shutting down.
     * Allows the protocol handler to perform cleanup.
     */
    void shutdown();
}
