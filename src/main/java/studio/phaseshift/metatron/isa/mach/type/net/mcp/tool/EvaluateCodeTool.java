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
import studio.phaseshift.metatron.isa.mach.type.net.mcp.JsonRpcToolDispatcher;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.Tuple;

import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class EvaluateCodeTool {

    private static final GraphittyLogger LOG = Graphitty.log(EvaluateCodeTool.class);

    public static String getName() {
        return "evaluate_code";
    }
    
    public static String getDescription() {
        return "Evaluate metatron code and return the result.";
    }
    
    public static String getJsonSchema() {
        return JsonParser.parseString(
                """
                {
                  "type": "object",
                  "properties": {
                    "code": {
                      "type": "string",
                      "description": "mtron code to evaluate"
                    }
                  },
                  "required": ["code"]
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
                        String code = args.get("code").toString();
                        LOG.debug("evaluating code: %s", code);
                        /// ////////////////////////////////////
                        final Obj codeObj = mParser.parse(code);
                        final Obj result = codeObj.apply();
                        /// ////////////////////////////////////
                        final String resultStr = result.toString();
                        LOG.debug("evaluation result: %s", resultStr);
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(resultStr)))
                                .isError(false)
                                .build();
                    } catch (final Exception e) {
                        LOG.debug("error evaluating code: %s", e.getMessage());
                        return McpSchema.CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent(e.getMessage())))
                                .isError(true)
                                .build();
                    }
                });
    }
}
