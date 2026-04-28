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

package studio.phaseshift.metatron.isa.web.space.ws.server;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.EVAL_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Fail.FAIL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_SPACE_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * MCP server pre-loaded with metatron-native tools, resources, and prompts.
 *
 * <p>Extends {@link mcp_wsServer} (which provides the complete JSON-RPC 2.0 / MCP
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
public class mcp_mtron_wsServer extends mcp_wsServer {

    public static final fURI MCP_MTRON_WS_TID = WS_SPACE_TID.extend("mcp_mtron_ws");

    public static final Type WS_MCP_MTRON_SERVER_TYPE = Type.Builder.build()
            .tid(MCP_WS_TID)
            .vid(MCP_MTRON_WS_TID)
            .isaPredicate(rec(
                    uri(TOOL).maybe().asUri(), rec(URI_TYPE, INST_TYPE),
                    uri(RESOURCE).maybe().asUri(), T(ALL),
                    uri(PROMPT).maybe().asUri(), T(ALL)))
            .constructor(instC(MCP_MTRON_WS_TID.extend(CTOR).dom(ALL.maybe()).rng(MCP_MTRON_WS_TID), lst(T(REC_TID)), (lhs, inst) -> {
                final Rec config = inst.arg(0).asRec();
                return new mcp_mtron_wsServer(new LinkedHashMap<>(config.jvm()), config.vid());
            })).create();

    public mcp_mtron_wsServer(final Map<Obj, Obj> jvm, final fURI vid) {
        // buildJvm() pre-populates the eval tool (and any caller-supplied tools/resources/prompts)
        // BEFORE super() sets up ON_MESSAGE, so the inherited JSON-RPC dispatch sees everything.
        super(buildJvm(jvm), MCP_MTRON_WS_TID, vid);
        this.jvm().put(uri(ON_OPEN), instC(vid.extend(ON_OPEN), lst(URI_TYPE), (lhs, inst) -> {
            LOG.info("mcp_mtron session opened: [in=>%s, out=>%s]", this.inContentType.name(), this.outContentType.name());
            return noobj();
        }));
        this.jvm().put(uri(ON_CLOSE), instC(vid.extend(ON_CLOSE), rec(uri(CODE), INT_TYPE, uri(REASON), STR_TYPE), (lhs, inst) -> {
            LOG.info("mcp_mtron session closed: code=%s, reason=%s", inst.arg(CODE), inst.arg(REASON));
            return noobj();
        }));
        this.jvm().put(uri(ON_ERROR), instC(vid.extend(ON_ERROR), lst(FAIL_TYPE), (lhs, inst) -> {
            LOG.error("error in mcp_mtron session %s: %s", this.socket.getRemoteSocketAddress(), inst.arg(0));
            return noobj();
        }));
    }

    /**
     * Augment the caller's jvm map with the metatron-native MCP capabilities.
     * Called before {@code super()} so that the parent's {@code ON_MESSAGE} handler
     * (the JSON-RPC dispatch) sees the pre-populated {@code tool}, {@code resource},
     * and {@code prompt} entries.
     *
     * <p>Caller-supplied entries always win — this method never overwrites existing keys.
     */
    private static Map<Obj, Obj> buildJvm(final Map<Obj, Obj> base) {
        final Map<Obj, Obj> jvm = new LinkedHashMap<>(base);

        // ── tools ────────────────────────────────────────────────────────────
        // Populate default metatron-native tools only if the caller has not
        // supplied their own tool rec.
        if (!jvm.containsKey(uri(TOOL))) {
            final Rec tools = rec(mutableMap());

            // eval — the foundational tool: an agent with eval can build, query,
            // and mutate the entire metatron space.
            final Obj evalInst = Router.global().read(EVAL_INST_TID);
            if (!evalInst.isNoObj() && evalInst.isObjInst())
                tools.at(uri(EVAL_INST_TID.name()), evalInst, Rec.MUTABLE);

            // mtron_list_space — return an index of currently accessible spaces.
            // Result shape: vid => space@vid (each space accessible via its vid).
            tools.at(uri("mtron_list_space"), instC(
                    MCP_MTRON_WS_TID.extend("list_space").dom(ALL.maybe()).rng(ALL.maybe()),
                    lst(), (lhs, inst) -> {
                        final Map<Obj, Obj> spaces = new LinkedHashMap<>(Router.global().spaces().jvm());
                        return rec(spaces);
                    }), Rec.MUTABLE);

            // mtron_router_info — router vid, tid, and space count.
            tools.at(uri("mtron_router_info"), instC(
                    MCP_MTRON_WS_TID.extend("router_info").dom(ALL.maybe()).rng(ALL.maybe()),
                    lst(), (lhs, inst) -> {
                        if (!Router.loaded()) return str("router not loaded");
                        final Router router = Router.global();
                        return rec(
                                uri("router_vid"), uri(router.vid()),
                                uri("router_tid"), uri(router.tid()),
                                uri("space_count"), jnt(router.spaces().jvm().size()),
                                uri("io_stats"), router.stats().ioStats());
                    }), Rec.MUTABLE);

            // mtron_list_inst — list loaded /m instructions.
            // Optional arg: doc (bool or uri "true") — include docq metadata per inst.
            // Note: ObjSimpleJSONSerializer is URI-biased, so JSON "true" arrives
            // as uri("true"), not bool(true).  We check both forms.
            tools.at(uri("mtron_list_inst"), instC(
                    MCP_MTRON_WS_TID.extend("list_inst").dom(ALL.maybe()).rng(ALL.maybe()),
                    rec(uri("doc"), BOOL_TYPE.maybe()), (lhs, inst) -> {
                        final Obj docArg = inst.arg("doc");
                        final boolean withDoc = docArg.isBool() && docArg.boolValue()
                                || !docArg.isNoObj() && docArg.toCleanString().equalsIgnoreCase("true");
                        return lst(Router.global().read(withDoc ? "/m/inst/#?doc" : "/m/inst/+"));
                    }), Rec.MUTABLE);

            jvm.put(uri(TOOL), tools);
        }

        return jvm;
    }
}
