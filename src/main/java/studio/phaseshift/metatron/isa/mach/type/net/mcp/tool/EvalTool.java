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

import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.net.mcp.annotation.McpHandler;
import studio.phaseshift.metatron.isa.mach.type.net.mcp.annotation.McpParameter;
import studio.phaseshift.metatron.isa.mach.type.net.mcp.annotation.McpTool;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

/**
 * Example of annotation-based MCP tool definition.
 * <p>
 * This is equivalent to EvaluateCodeTool but uses annotations instead of
 * static methods for registration.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@McpTool(name = "mtron_eval",
        description = "evaluate mtron code and return the result",
        category = "execution")
public class EvalTool {

    private static final GraphittyLogger LOG = Graphitty.log(EvalTool.class);

    @McpParameter(
            name = "code",
            description = "mtron code to evaluate",
            required = true
    )
    private String code;

    @McpHandler
    public Obj execute() {
        LOG.debug("evaluating code: %s", this.code);
        return mParser.eval(this.code);
    }
}
