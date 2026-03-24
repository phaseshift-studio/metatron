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

package studio.phaseshift.metatron.isa.mach.type.net.mcp.tool;

import io.modelcontextprotocol.spec.McpSchema;
import studio.phaseshift.metatron.isa.mach.type.net.mcp.JsonRpcToolDispatcher;
import studio.phaseshift.metatron.util.Tuple;

/**
 * Service provider interface for MCP tools.
 *
 * Implementations of this interface can be discovered via Java's ServiceLoader mechanism.
 * This allows tools to be registered without hardcoding them in MetatronMcpServer.
 *
 * To register a tool provider:
 * 1. Implement this interface
 * 2. Create META-INF/services/studio.phaseshift.metatron.isa.mach.type.net.mcp.tool.McpToolProvider
 * 3. Add the fully qualified class name of your implementation to that file
 *
 * Example:
 * <pre>
 * public class MyToolProvider implements McpToolProvider {
 *     public Tuple.Pair<McpSchema.Tool, JsonRpcToolDispatcher.ToolHandler> provideTool() {
 *         return McpToolRegistry.register(MyAnnotatedTool.class);
 *     }
 * }
 * </pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface McpToolProvider {

    /**
     * Provide a tool registration pair.
     *
     * @return Tuple of Tool definition and handler
     */
    Tuple.Pair<McpSchema.Tool, JsonRpcToolDispatcher.ToolHandler> provideTool();
}
