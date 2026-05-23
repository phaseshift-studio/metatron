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

package studio.phaseshift.metatron.isa.web.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRecClient;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.Tokens.DOC;
import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.ON_MESSAGE;
import static studio.phaseshift.metatron.Tokens.RESOURCE;
import static studio.phaseshift.metatron.Tokens.TOOL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_CLIENT_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_HANDLER_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class mMCPUtility {

    private mMCPUtility() {
        // do nothing
    }

    // ========================================
    // Shared metatron-native tool definitions
    // ========================================

    /**
     * Build the metatron-native MCP tool definitions and merge them into the
     * supplied jvm map.  Caller-supplied entries always win — this method
     * never overwrites existing keys.
     * <p>
     * Shared by {@code mcp_wsHandler} and {@code mcp_mtron_httpHandler}.
     *
     * @param base the caller-supplied jvm map (may contain tools/resources/prompts)
     * @param vid  the type VID for tool TID namespacing
     * @return a new map with metatron-native tools merged in
     */
    public static Map<Obj, Obj> buildMetatronTools(final Map<Obj, Obj> base, final fURI vid) {
        final Map<Obj, Obj> jvm = new LinkedHashMap<>(base);

        // ── tools ────────────────────────────────────────────────────────────
        if (!jvm.containsKey(uri(TOOL))) {
            final Rec tools = rec(mutableMap());
            tools.at(uri("write_memory"), docWrap(instC(vid.extend("write_memory").dom(NOOBJ_TID.zero()).rng(ALL.maybeSome()),
                            rec(uri("current_memory"), ALL_TYPE,
                                    uri("previous_memory").maybe().asUri(), URI_TYPE), (lhs, inst) -> {
                                final Obj previousMemory = Router.readFromSpace(inst.arg(f("previous_memory"), 1).uriValue());
                                final fURI memoryBasePath = previousMemory.vid().retract(1).basePath();
                                final Obj currentMemory = inst.arg(f("current_memory"), 0).vid(CommonUtil.mintShortUUID(memoryBasePath, true));
                                return rel(previousMemory, currentMemory, REL_TID, CommonUtil.mintShortUUID(memoryBasePath, true));
                            }), "noobj lhs", "an memory chain relation (previous => current)@<vid> w/ id for future lookup",
                    Map.of(uri("current_memory"), "the memory to remember -- a str::T, a markdown::T, etc.",
                            uri("previous_memory"), "a previous memory vid to chain current memory to"),
                    "(experimental) returns a memory relation of the form(current@<vid> => previous@<vid>)@<vid>"), MUTABLE);
            tools.at(uri("read_memory"), docWrap(instC(vid.extend("read_memory").dom(NOOBJ_TID.zero()).rng(ALL.maybeSome()),
                            rec(uri("memory_vid").maybe().asUri(), URI_TYPE), (lhs, inst) -> {
                                final Obj memId = inst.arg(f("memory_vid"), 0);
                                if (!memId.isNoObj())
                                    return Router.readFromSpace(memId.uriValue());
                                else
                                    return noobj();
                            }), "noobj lhs", "the memory fragment by vid which can then be walked with >>",
                    Map.of(uri("memory_vid"), "the vid of the memory to read"),
                    "(experimental) returns the result of reading the provided memory"), MUTABLE);
            // eval_mtron — the foundational tool: evaluate metatron expressions
            tools.at(uri("eval_mtron"), docWrap(instC(
                            vid.extend("eval_mtron").dom(NOOBJ_TID.zero()).rng(ALL.maybeSome()),
                            rec(uri("code"), STR_TYPE), (lhs, inst) -> {
                                final Obj codeArg = inst.arg(f("code"), 0);
                                return ObjmtronSerializer.parse(codeArg.toCleanString()).apply();
                            }), "noobj lhs", "the result of the code evaluation",
                    Map.of(uri(CODE), "mtron code to evaluate"), "returns the result of evaluating the provided mtron expression"), MUTABLE);
            // list_space — return an index of currently accessible spaces
            tools.at(uri("list_space"), docWrap(instC(
                            vid.extend("list_space").dom(NOOBJ_TID.zero()).rng(ALL.maybe()),
                            lst(), (lhs, inst) -> {
                                final Map<Obj, Obj> spaces = new LinkedHashMap<>();
                                Router.global().spaces().jvm().entrySet().forEach(kv -> {
                                    spaces.put(kv.getKey(), uri(kv.getValue().<Space>as().pattern()));
                                });
                                return rec(spaces);
                            }), "noobj lhs", "a rec index of currently accessible spaces",
                    Map.of(), "returns a rec identifying all active metatron spaces"), MUTABLE);

            // router_info — router vid, tid, and space count
            tools.at(uri("router_info"), instC(
                    vid.extend("router_info").dom(NOOBJ_TID.zero()).rng(ALL.maybe()),
                    lst(), (lhs, inst) -> {
                        if (!Router.loaded()) return str("router not loaded");
                        final Router router = Router.global();
                        return rec(
                                uri("router_vid"), uri(router.vid()),
                                uri("router_tid"), uri(router.tid()),
                                uri("space_count"), jnt(router.spaces().jvm().size()),
                                uri("io_stats"), router.stats().ioStats());
                    }), MUTABLE);

            // find_inst — gets lst of loaded /m instructions and documentation
            tools.at(uri("find_inst"), docWrap(instC(
                            vid.extend("find_inst").dom(NOOBJ_TID.zero()).rng(ALL.maybe()),
                            rec(uri(PATTERN), URI_TYPE,
                                    uri(DOM).maybe(), URI_TYPE,
                                    uri(RNG).maybe(), URI_TYPE), (lhs, inst) -> {
                                fURI pattern = inst.arg(f(PATTERN), 0).uriValue();
                                if (inst.args().has(DOM))
                                    pattern = pattern.dom(inst.arg(f(DOM), 1).uriValue());
                                if (inst.args().has(RNG))
                                    pattern = pattern.rng(inst.arg(f(RNG), 2).uriValue());
                                return lst(Router.global().read(pattern.addQ(DOCQ))
                                        .stream()
                                        .map(Obj::asRec)
                                        .filter(o -> o.at(OBJ).isInst())
                                        .map(o -> o));
                            }), "maybe an obj", "lst of inst matching arg specification",
                    Map.of(uri(PATTERN), "the inst tid to match",
                            uri(DOM), "the dom of inst to match (can be added to pattern arg)",
                            uri(RNG), "the rng of inst to match (can be added to pattern arg"),
                    "returns a lst of all instruction pattern matches w/ documentation",
                    "find_inst(plus,int,int)",
                    "find_inst(plus?int<=int)"), MUTABLE);
            // spawn_wsclient — create a websocket client
            tools.at(uri("spawn_wsclient"), docWrap(instC(
                            vid.extend("spawn_wsclient").dom(NOOBJ_TID.zero()).rng(WS_CLIENT_TID),
                            rec(uri(HOST), URI_TYPE, uri(ON_MESSAGE), INST_TYPE), (lhs, inst) -> new WebSocketRecClient(
                                    new WebSocketRec(
                                            new LinkedHashMap<>(inst.args().jvm()),
                                            vid.extend("wsclient"), CommonUtil.mintShortUUID(vid, true)))),
                    "noobj lhs",
                    "the created websocket client",
                    Map.of(uri(HOST), "the full ws:// uri of the websocket server to connect to",
                            uri(ON_MESSAGE), "the function to evaluate on every received message"),
                    "create a websocket client with provided on_message behavior"), MUTABLE);

            // spawn_wshandler — create a websocket handler
            tools.at(uri("spawn_wshandler"), docWrap(instC(
                            vid.extend("spawn_wshandler").dom(NOOBJ_TID.zero()).rng(WS_HANDLER_TID),
                            rec(uri(HOST), URI_TYPE, uri(ON_MESSAGE), INST_TYPE), (lhs, inst) -> {
                                final WebSocketRec server = new WebSocketRec(
                                        new LinkedHashMap<>(inst.args().jvm()),
                                        vid.extend("wsserver"), CommonUtil.mintShortUUID(vid, true));
                                Router.writeToSpace(server);
                                return server;
                            }),
                    "noobj lhs",
                    "the created websocket handler",
                    Map.of(uri(HOST), "the full ws:// uri of the websocket handler to expose",
                            uri(ON_MESSAGE), "the function to evaluate on every received message"),
                    "create a websocket handler with provided on_message behavior"), MUTABLE);

            jvm.put(uri(TOOL), tools);
        }

        // ── resources ──────────────────────────────────────────────────────────
        if (false && !jvm.containsKey(uri(RESOURCE))) {
            final fURI prefix = f("mtronfs:skills/mtron/");
            final Rec resources = rec(mutableMap());
            resources.jvm().put(uri("writing-mtron-expressions.md"), auto_(auto_from_(prefix.extend("references/writing-mtron-expressions.md")).as_(STR_TYPE).asCode()).tryToInst());
            jvm.put(uri(RESOURCE), resources);
        }

        return jvm;
    }
}
