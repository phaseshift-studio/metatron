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

package studio.phaseshift.metatron.isa.mach.type.net.mcp.tool.annotated;

import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.net.mcp.annotation.McpHandler;
import studio.phaseshift.metatron.isa.mach.type.net.mcp.annotation.McpParameter;
import studio.phaseshift.metatron.isa.mach.type.net.mcp.annotation.McpTool;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

/**
 * List currently accessible /m-developed instructions.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@McpTool(
        name = "list_inst",
        description = """
                Get a list of the currently accessible /m-developed instructions.

                An inst can be called with the following syntax:

                    op(arg1,...,argN)

                If a more specific domain or range is needed:

                    op?rng<=dom(arg1,...,argN)

                The list of currently loaded /m-developed instructions is provided below.
                A {<j>} means the instruction's code body is written in Java with no obvious means of introspection.

                If documentation is accessed, note that not all insts have written documentation or it may be incomplete.
                """,
        category = "introspection"
)
public class AnnotatedListInstTool {

    private static final GraphittyLogger LOG = Graphitty.log(AnnotatedListInstTool.class);

    @McpParameter(
            name = "doc",
            description = "return documentation associated with each inst",
            required = false,
            defaultValue = "false"
    )
    private boolean doc;

    @McpHandler
    public Obj execute() {
        LOG.debug("curating inst list (doc=%b)", doc);

        // Return the list of instructions as an Obj
        // Framework will convert to structured JSON
        return lst(Router.global().read(doc ? "/m/inst/#?doc" : "/m/inst/+"));
    }
}
