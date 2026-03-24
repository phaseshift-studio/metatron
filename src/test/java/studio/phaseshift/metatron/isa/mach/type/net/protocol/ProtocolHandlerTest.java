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

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.mach.io.type.ObjByteBufferSerializer;
import studio.phaseshift.metatron.isa.mach.type.net.MConnection;
import studio.phaseshift.metatron.furi.fURI;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for protocol handler implementations.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ProtocolHandlerTest extends AbstractMetatronTest {

    @Test
    public void testMcpProtocolDetection() {
        final McpProtocolHandler handler = new McpProtocolHandler(LOG);

        // Should detect MCP JSON-RPC messages
        assertTrue(handler.canHandle("{\"jsonrpc\":\"2.0\",\"method\":\"initialize\",\"id\":1}"));
        assertTrue(handler.canHandle("  {\"jsonrpc\":\"2.0\",\"method\":\"tools/list\"}  "));

        // Should not detect non-JSON messages
        assertFalse(handler.canHandle("plain text"));
        assertFalse(handler.canHandle(""));
        assertFalse(handler.canHandle((String) null));

        // Should not handle binary messages
        assertFalse(handler.canHandle(ByteBuffer.wrap(new byte[]{1, 2, 3})));

        assertEquals("mcp", handler.protocolName());
    }

    @Test
    public void testNativeProtocolDetection() {;
        final ObjByteBufferSerializer serializer = new ObjByteBufferSerializer();
        final NativeMetatronProtocolHandler handler = new NativeMetatronProtocolHandler(serializer, new HashMap<>(), LOG);

        // Should handle binary messages
        assertTrue(handler.canHandle(ByteBuffer.wrap(new byte[]{1, 2, 3})));

        // Should handle non-JSON text (will convert to binary)
        assertTrue(handler.canHandle("plain text"));
        assertTrue(handler.canHandle("some code"));

        // Should not handle JSON-RPC (that's MCP's job)
        assertFalse(handler.canHandle("{\"jsonrpc\":\"2.0\"}"));

        // Should not handle empty messages
        assertFalse(handler.canHandle(""));
        assertFalse(handler.canHandle((ByteBuffer) null));

        assertEquals("native", handler.protocolName());
    }

    @Test
    public void testProtocolPriority() {
        // Test that MCP takes priority over native for JSON-RPC messages
        final ObjByteBufferSerializer serializer = new ObjByteBufferSerializer();
        final NativeMetatronProtocolHandler nativeHandler = new NativeMetatronProtocolHandler(serializer, new HashMap<>(), LOG);
        final McpProtocolHandler mcpHandler = new McpProtocolHandler(LOG);

        final String mcpMessage = "{\"jsonrpc\":\"2.0\",\"method\":\"initialize\",\"id\":1}";

        // MCP should handle JSON-RPC
        assertTrue(mcpHandler.canHandle(mcpMessage));
        // Native should NOT handle JSON-RPC
        assertFalse(nativeHandler.canHandle(mcpMessage));

        final String nativeMessage = "some metatron code";

        // Native should handle non-JSON text
        assertTrue(nativeHandler.canHandle(nativeMessage));
        // MCP should NOT handle non-JSON text
        assertFalse(mcpHandler.canHandle(nativeMessage));
    }

    @Test
    public void testBinaryMessageRouting() {
        final ObjByteBufferSerializer serializer = new ObjByteBufferSerializer();
        final NativeMetatronProtocolHandler nativeHandler = new NativeMetatronProtocolHandler(serializer, new HashMap<>(), LOG);
        final McpProtocolHandler mcpHandler = new McpProtocolHandler(LOG);

        final ByteBuffer binaryMessage = ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5});

        // Native should handle binary
        assertTrue(nativeHandler.canHandle(binaryMessage));
        // MCP should NOT handle binary
        assertFalse(mcpHandler.canHandle(binaryMessage));
    }
}
