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
public class ListInstTool {

    private static final GraphittyLogger LOG = Graphitty.log(ListInstTool.class);

    public static String getName() {
        return "list_inst";
    }

    public static String getDescription() {
        return """
               Get a list of the currently accessible /m-developed instructions.
               
               An inst can be called with the following syntax:
               
                   op(arg1,...,argN)
               
               If a more specific domain or range is needed:
               
                   op?rng<=dom(arg1,...,argN)
               
               The list of currently loaded /m-developed instructions is provided below.
               A {<j>} means the instruction's code body is written in Java with no obvious means of introspection.
               
               If documentation is accessed, note that not all insts have written documentation or it may be incomplete.
               """;
    }

    public static String getJsonSchema() {
        return JsonParser.parseString("""
                                      {
                                          "type": "object",
                                          "icons": ["https://metatron.phaseshift.studio/images/icons/space/oltp-icon.svg"],
                                          "properties": {
                                            "doc": {
                                              "type": "boolean",
                                              "description": "return documentation associated with each inst"
                                            }
                                          }
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
                        LOG.debug("curating inst list");
                        /// ////////////////////////////////////
                        final boolean doc = args.containsKey("doc") && (boolean) args.get("doc");
                        final String result =
                                Router.global().read(doc ? "/m/inst/#?doc" : "/m/inst/+")
                                        .stream()
                                        .map(Obj::toString)
                                        .map(Graphitty::strip)
                                        .reduce("", (a, b) -> a + "\n==>" + b)
                                        .trim();
                        /// ////////////////////////////////////
                        LOG.debug("curated inst list: %s", result);
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(result)))
                                .isError(false)
                                .build();
                    } catch (final Exception e) {
                        LOG.debug("error curating inst list: %s", e.getMessage());
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(e.getMessage())))
                                .isError(true)
                                .build();
                    }
                });
    }
}
