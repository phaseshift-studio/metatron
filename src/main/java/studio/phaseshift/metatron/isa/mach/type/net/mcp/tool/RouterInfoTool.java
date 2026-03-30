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

import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.net.mcp.annotation.McpHandler;
import studio.phaseshift.metatron.isa.mach.type.net.mcp.annotation.McpTool;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Get information about the metatron system.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@McpTool(
        name = "mtron_router_info",
        description = "get information about the router's state including statistics and server information",
        category = "system"
)
public class RouterInfoTool {

    private static final GraphittyLogger LOG = Graphitty.log(RouterInfoTool.class);

    @McpHandler
    public Obj execute() {
        LOG.debug("getting system info");

        if (!Router.loaded()) {
            return str("router not loaded");
        }

        final Router router = Router.global();

        // Return structured data as an Obj record
        // Framework will convert to JSON automatically
        return rec(
                uri("router_vid"), uri(router.vid()),
                uri("router_tid"), uri(router.tid()),
                uri("server_host"), str(router.server().host().toString()),
                uri("server_running"), bool(router.server().isRunning()),
                uri("io_stats"), router.stats().ioStats());
    }
}
