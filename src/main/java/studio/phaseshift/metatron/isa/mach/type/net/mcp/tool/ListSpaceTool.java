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
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.net.mcp.JsonRpcToolDispatcher;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.Tuple;

import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ListSpaceTool {

    private static final GraphittyLogger LOG = Graphitty.log(ListSpaceTool.class);

    public static String getName() {
        return "list_space";
    }

    public static String getDescription() {
        return """
    Get an index of the currently accessible mtron spaces.
    The result template is: vid=>space@vid.
    
    All objs in the space have furis that match the space's pattern.
    The space itself as an obj is accessible via it's @vid.
    """;
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
                        LOG.debug("curating space list");
                        /// ////////////////////////////////////
                        final StringBuilder sb = new StringBuilder();
                        Router.global().spaces().jvm().forEach((k, v) -> sb.append(k).append("=>").append(v).append("\n\n"));
                        /// ////////////////////////////////////
                        final String result = sb.toString();
                        LOG.debug("curated space list: %s", result);
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(result)))
                                .isError(false)
                                .build();
                    } catch (final Exception e) {
                        LOG.debug("error curating space list: %s", e.getMessage());
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(e.getMessage())))
                                .isError(true)
                                .build();
                    }
                });
    }
}
