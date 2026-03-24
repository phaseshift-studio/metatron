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
import studio.phaseshift.metatron.isa.mach.type.net.mcp.annotation.McpTool;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;

/**
 * Get an index of currently accessible mtron spaces.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@McpTool(
        name = "list_space",
        description = """
                Get an index of the currently accessible mtron spaces.
                The result template is: vid=>space@vid.

                All objs in the space have furis that match the space's pattern.
                The space itself as an obj is accessible via it's @vid.
                """,
        category = "introspection"
)
public class AnnotatedListSpaceTool {

    private static final GraphittyLogger LOG = Graphitty.log(AnnotatedListSpaceTool.class);

    @McpHandler
    public Obj execute() {
        LOG.debug("curating space list");

        // Return spaces as a record
        // Framework will convert to structured JSON
        final Map<Obj, Obj> spaces = new LinkedHashMap<>();
        Router.global().spaces().jvm().forEach(spaces::put);

        return rec(spaces);
    }
}
