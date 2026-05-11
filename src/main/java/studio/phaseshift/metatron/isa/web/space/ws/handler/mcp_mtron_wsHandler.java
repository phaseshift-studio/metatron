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

package studio.phaseshift.metatron.isa.web.space.ws.handler;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.web.type.mMCPUtility;
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
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.*;

/*
 * MCP server pre-loaded with metatron-native tools, resources, and prompts.
 *
 * <p>Extends {@link mcp_wsHandler} (which provides the complete JSON-RPC 2.0 / MCP
 * protocol plumbing) and contributes the metatron-native capability layer on top:
 *
 * <ul>
 *   <li><b>tools</b>: {@code eval} — evaluate any metatron expression; the foundational
 *       tool from which an agent can build, query, and mutate the entire space.</li>
 *   <li><b>resources</b>: configurable at construction time via the {@code resource} key.</li>
 *   <li><b>prompts</b>: configurable at construction time via the {@code prompt} key.</li>
 * </ul>
 *
 * <p>Additional tools, resources, or prompts can be injected at construction time
 * by including them in the config rec passed to the wsSpace route table:
 * <pre>
 * wsspace::[host  => &lt;ws://localhost:8555&gt;,
 *           route => [/mcp => mcp_mtron_ws]]@/sys/space/web/ws
 * </pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mcp_mtron_wsHandler extends mcp_wsHandler {

    public static final fURI WS_MTRON_MCP_HANDLER_TID = WS_SPACE_TID.extend("mcp_mtron_ws");

    public static final Type WS_MTRON_MCP_HANDLER_TYPE = Type.Builder.build()
            .tid(WS_MCP_HANDLER_TID)
            .vid(WS_MTRON_MCP_HANDLER_TID)
            .isaPredicate(rec(
                    uri(TOOL).maybe().asUri(), rec(URI_TYPE, INST_TYPE),
                    uri(RESOURCE).maybe().asUri(), T(ALL),
                    uri(PROMPT).maybe().asUri(), T(ALL)))
            .constructor(instC(WS_MTRON_MCP_HANDLER_TID.extend(CTOR).dom(ALL.maybe()).rng(WS_MTRON_MCP_HANDLER_TID), lst(T(REC_TID)), (lhs, inst) -> {
                final Rec config = inst.arg(0).asRec();
                return new mcp_mtron_wsHandler(new LinkedHashMap<>(config.jvm()), config.vid());
            })).create();

    public mcp_mtron_wsHandler(final Map<Obj, Obj> jvm, final fURI vid) {
        // buildMetatronTools() pre-populates tools before super() sets up ON_MESSAGE
        super(buildJvm(jvm, vid), WS_MTRON_MCP_HANDLER_TID, vid);
    }

    private static Map<Obj, Obj> buildJvm(final Map<Obj, Obj> base, final fURI vid) {
        return mMCPUtility.buildMetatronTools(base, vid);
    }
}
