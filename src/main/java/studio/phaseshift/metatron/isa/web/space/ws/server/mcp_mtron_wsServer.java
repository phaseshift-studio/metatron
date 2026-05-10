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

import org.java_websocket.client.WebSocketClient;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRecClient;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MCode.code;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.*;
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
        super(buildJvm(jvm, vid), MCP_MTRON_WS_TID, vid);
    }

    /**
     * Augment the caller's jvm map with the metatron-native MCP capabilities.
     * Called before {@code super()} so that the parent's {@code ON_MESSAGE} handler
     * (the JSON-RPC dispatch) sees the pre-populated {@code tool}, {@code resource},
     * and {@code prompt} entries.
     *
     * <p>Caller-supplied entries always win — this method never overwrites existing keys.
     */
    private static Map<Obj, Obj> buildJvm(final Map<Obj, Obj> base, final fURI vid) {
        final Map<Obj, Obj> jvm = new LinkedHashMap<>(base);
        // ── tools ────────────────────────────────────────────────────────────
        // Populate default metatron-native tools only if the caller has not
        // supplied their own tool rec.
        if (!jvm.containsKey(uri(TOOL))) {
            final Rec tools = rec(mutableMap());
            // eval — the foundational tool: an agent with eval can build, query,
            // and mutate the entire metatron space.
            // The MCP client sends arguments as {"code": "..."} (named) or {"0": "..."} (positional).
            // ObjSimpleJSONSerializer is URI-based so string args arrive as URIs — use toCleanString().
            tools.at(uri("eval_mtron"), docWrap(instC(
                            MCP_MTRON_WS_TID.extend("eval_mtron").dom(NOOBJ_TID.zero()).rng(ALL.maybeSome()),
                            rec(uri("code"), STR_TYPE), (lhs, inst) -> {
                                final Obj codeArg = inst.arg(f("code"),0);
                                return ObjmtronSerializer.parse(codeArg.toCleanString()).apply();
                            }), "noobj lhs", "the result of the code evaluation",
                    Map.of(uri(CODE), "mtron code to evaluate"), "returns the result of evaluating the provided mtron expression"), MUTABLE);
            // list_space — return an index of currently accessible spaces.
            // Result shape: vid => space@vid (each space accessible via its vid).
            tools.at(uri("list_space"), docWrap(instC(
                            MCP_MTRON_WS_TID.extend("list_space").dom(NOOBJ_TID.zero()).rng(ALL.maybe()),
                            lst(), (lhs, inst) -> {
                                final Map<Obj, Obj> spaces = new LinkedHashMap<>(Router.global().spaces().jvm());
                                return rec(spaces);
                            }), "noobj lhs", "a rec index of currently accessible spaces",
                    Map.of(), "returns a rec identifying all active metatron spaces"), MUTABLE);

            // router_info — router vid, tid, and space count.
            tools.at(uri("router_info"), instC(
                    MCP_MTRON_WS_TID.extend("router_info").dom(NOOBJ_TID.zero()).rng(ALL.maybe()),
                    lst(), (lhs, inst) -> {
                        if (!Router.loaded()) return str("router not loaded");
                        final Router router = Router.global();
                        return rec(
                                uri("router_vid"), uri(router.vid()),
                                uri("router_tid"), uri(router.tid()),
                                uri("space_count"), jnt(router.spaces().jvm().size()),
                                uri("io_stats"), router.stats().ioStats());
                    }), MUTABLE);

            // list_inst — list loaded /m instructions.
            // Optional arg: doc (bool or uri "true") — include docq metadata per inst.
            // Note: ObjSimpleJSONSerializer is URI-biased, so JSON "true" arrives
            // as uri("true"), not bool(true).  We check both forms.
            tools.at(uri("list_inst"), instC(
                    MCP_MTRON_WS_TID.extend("list_inst").dom(NOOBJ_TID.zero()).rng(ALL.maybe()),
                    rec(uri(DOC), BOOL_TYPE.maybe()), (lhs, inst) -> {
                        final Obj docArg = inst.arg(f(DOC),0);
                        final boolean withDoc = docArg.isBool() && docArg.boolValue()
                                || !docArg.isNoObj() && docArg.toCleanString().equalsIgnoreCase("true");
                        return lst(Router.global().read(withDoc ? "/m/inst/#?doc" : "/m/inst/+"));
                    }), MUTABLE);

            // spawn_wsclient -- create a websocket client with provide on_message behavior
            tools.at(uri("spawn_wsclient"), docWrap(instC(
                            MCP_MTRON_WS_TID.extend("spawn_wsclient").dom(NOOBJ_TID.zero()).rng(WS_CLIENT_TID),
                            rec(uri(HOST), URI_TYPE, uri(ON_MESSAGE), INST_TYPE), (lhs, inst) -> new WebSocketRecClient(
                                    new WebSocketRec(
                                            new LinkedHashMap<>(inst.args().jvm()),
                                            MCP_MTRON_WS_TID.extend("wsclient"), CommonUtil.mintShortUUID(vid, true)))),
                    "noobj lhs",
                    "the created websocket client",
                    Map.of(uri(HOST), "the full ws:// uri of the the websocket server to connect to",
                            uri(ON_MESSAGE), "the function to evaluate on every received message"),
                    "create a websocket client with provide on_message behavior"), MUTABLE);

            // spawn_wsserver -- create a websocket server at provided binding with provide on_message behavior
            tools.at(uri("spawn_wsserver"), docWrap(instC(
                            MCP_MTRON_WS_TID.extend("spawn_wsserver").dom(NOOBJ_TID.zero()).rng(WS_SERVER_TID),
                            rec(uri(HOST), URI_TYPE, uri(ON_MESSAGE), INST_TYPE), (lhs, inst) -> {
                                final WebSocketRec server = new WebSocketRec(
                                        new LinkedHashMap<>(inst.args().jvm()),
                                        MCP_MTRON_WS_TID.extend("wsserver"), CommonUtil.mintShortUUID(vid, true));
                                Router.writeToSpace(server);
                                return server;
                            }),
                    "noobj lhs",
                    "the created websocket server",
                    Map.of(uri(HOST), "the full ws:// uri of the the websocket server expose",
                            uri(ON_MESSAGE), "the function to evaluate on every received message"),
                    "create a websocket server with provide on_message behavior"), MUTABLE);
            jvm.put(uri(TOOL), tools);
        }

        // ── resources ──────────────────────────────────────────────────────────
        // Skill reference files served from the mtronfs: space (boot.mtron line 61).
        // auto_from_ entries are auto-resolved by Rec.at() when resources/read
        // accesses them, so content is always live from disk — no need to update
        // Java code when reference docs change.  .jvm().put() bypasses Rec.at()
        // path-decomposition so keys stay flat (no nesting).
        if (false && !jvm.containsKey(uri(RESOURCE))) {
            final fURI prefix = f("mtronfs:skills/mtron/");
            final Rec resources = rec(mutableMap());
          //  resources.jvm().put(uri("SKILL.md"), auto_(auto_from_(prefix.extend("SKILL.md")).as_(STR_TYPE).asCode()).tryToInst());
            resources.jvm().put(uri("writing-mtron-expressions.md"), auto_(auto_from_(prefix.extend("references/writing-mtron-expressions.md")).as_(STR_TYPE).asCode()).tryToInst());
          //  resources.jvm().put(uri("connecting-datasources.md"), auto_(auto_from_(prefix.extend("references/connecting-datasources.md")).as_(STR_TYPE).asCode()).tryToInst());
          //  resources.jvm().put(uri("providing-data-statistics.md"), auto_(auto_from_(prefix.extend("references/providing-data-statistics.md")).as_(STR_TYPE).asCode()).tryToInst());
           // resources.jvm().put(uri("mcp-server-architecture.md"), auto_(auto_from_(prefix.extend("references/mcp-server-architecture.md")).as_(STR_TYPE).asCode()).tryToInst());
           // resources.jvm().put(uri("mcp-server-notifications.md"), auto_(auto_from_(prefix.extend("references/mcp-server-notifications.md")).as_(STR_TYPE).asCode()).tryToInst());
           // resources.jvm().put(uri("http-page-fetching.md"), auto_(auto_from_(prefix.extend("references/http-page-fetching.md")).as_(STR_TYPE).asCode()).tryToInst());
           // resources.jvm().put(uri("answer-questions.md"), auto_(auto_from_(prefix.extend("references/answer-questions.md")).as_(STR_TYPE).asCode()).tryToInst());
            jvm.put(uri(RESOURCE), resources);
        }

        return jvm;
    }
}
