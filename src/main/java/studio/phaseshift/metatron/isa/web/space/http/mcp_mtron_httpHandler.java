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

package studio.phaseshift.metatron.isa.web.space.http;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.web.type.mcp_Server;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.http.httpSpace.HTTP_SPACE_TID;

/**
 * MCP HTTP handler pre-populated with metatron-native tools.
 * Analogous to {@code mcp_wsHandler} but for HTTP transport.
 * <p>
 * Tools include: eval_mtron, list_space, router_info, list_inst,
 * spawn_wsclient, spawn_wsserver.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mcp_mtron_httpHandler extends mcp_httpHandler {

    public static final fURI MCP_MTRON_HTTP_TID = HTTP_SPACE_TID.extend("mcp_mtron_http");

    public static final Type MCP_MTRON_HTTP_TYPE = Type.Builder.build()
            .tid(MCP_HTTP_TID)
            .vid(MCP_MTRON_HTTP_TID)
            .isaPredicate(rec(
                    uri(TOOL).maybe().asUri(), rec(URI_TYPE, INST_TYPE),
                    uri(RESOURCE).maybe().asUri(), T(ALL),
                    uri(PROMPT).maybe().asUri(), T(ALL)))
            .constructor(instC(MCP_MTRON_HTTP_TID.extend(CTOR).dom(ALL.maybe()).rng(MCP_MTRON_HTTP_TID),
                    lst(T(REC_TID)),
                    (lhs, inst) -> {
                        final Rec config = inst.arg(0).asRec();
                        return new mcp_mtron_httpHandler(
                                new LinkedHashMap<>(config.jvm()),
                                config.vid());
                    }))
            .create();

    public mcp_mtron_httpHandler(final Map<Obj, Obj> jvm, final fURI vid) {
        super(mcp_Server.buildMetatronTools(jvm, vid), MCP_MTRON_HTTP_TID, vid);
    }
}
