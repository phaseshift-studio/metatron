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

import com.google.gson.JsonParser;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import studio.phaseshift.metatron.isa.mach.io.type.ObjByteBufferSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.net.mcp.JsonRpcToolDispatcher;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.Tuple;

import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class GetSystemInfoTool {

    private static final GraphittyLogger LOG = Graphitty.log(GetSystemInfoTool.class);

    public static String getName() {
        return "get_system_info";
    }

    public static String getDescription() {
        return "Get information about the metatron system including router state, server information, and system statistics.";
    }

    public static String getJsonSchema() {
        return JsonParser.parseString(
                """
                {
                  "type": "object",
                  "additionalProperties": false
                }
                """).getAsJsonObject().toString();
    }

    public static Tuple.Pair<McpSchema.Tool, JsonRpcToolDispatcher.ToolHandler> create() {
        return Tuple.Pair.with(
                McpSchema.Tool.builder()
                        .name(getName())
                        .description(getDescription())
                        .inputSchema(McpJsonDefaults.getMapper(), getJsonSchema())
                        .build(),
                args -> {
                    try {
                        StringBuilder info = new StringBuilder();
                        info.append("=== metratron system information ===\n\n");
                        if (Router.loaded()) {
                            Router router = Router.global();
                            info.append("router vid: ").append(router.vid()).append("\n");
                            info.append("router tid: ").append(router.tid()).append("\n");
                            info.append("\n");
                            if (router.server() != null) {
                                info.append("mserver host: ").append(router.server().host()).append("\n");
                                info.append("mserver running: ").append(router.server().isRunning()).append("\n");
                                info.append("\n");
                            }

                            if (router.stats() != null) {
                                info.append("\nstatistics:\n");
                                info.append("  io: ").append(new String(new ObjByteBufferSerializer().writeRec(router.stats().ioStats()).array())).append("\n");
                                info.append("\n");
                            }
                        }
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(info.toString())))
                                .isError(false)
                                .build();
                    } catch (Exception e) {
                        LOG.error("error getting system info: %s", e.getMessage());
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(e.getMessage())))
                                .isError(true)
                                .build();
                    }
                });
    }
}
