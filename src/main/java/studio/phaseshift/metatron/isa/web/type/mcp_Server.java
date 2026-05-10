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
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRecClient;
import studio.phaseshift.metatron.isa.web.space.ws.server.mcp_wsHandler;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.DOCQ;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.*;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * Transport-agnostic MCP (Model Context Protocol) JSON-RPC protocol handler.
 * <p>
 * This class handles the complete MCP JSON-RPC dispatch (tools, resources, prompts,
 * initialize, ping, notifications) and is designed to be wrapped by transport layers
 * such as {@link mcp_wsHandler} (WebSocket) or {@code mcp_httpHandler} (HTTP).
 * <p>
 * Transport wrappers compose this class and call {@link #handleMessage(Obj)} on
 * each incoming JSON-RPC message, then deliver the returned response via their
 * own transport mechanism.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mcp_Server extends MRec {

    public static final fURI MCP_SERVER_TID = WS_SPACE_TID.extend("mcp_server");
    protected final GraphittyLogger LOG = Graphitty.log(this);
    private static final String DESCRIPTION = "description";

    public static final Type MCP_SERVER_TYPE = Type.Builder.build()
            .tid(MCP_SERVER_TID)
            .vid(MCP_SERVER_TID.extend("base"))
            .isaPredicate(rec(
                    uri(TOOL).maybe().asUri(), rec(URI_TYPE, INST_TYPE).maybe(),
                    uri(RESOURCE).maybe().asUri(), T(ALL),
                    uri(PROMPT).maybe().asUri(), T(ALL)))
            .constructor(instC(MCP_SERVER_TID.extend(CTOR).dom(ALL.maybe()).rng(MCP_SERVER_TID), lst(T(REC_TID)), (lhs, inst) ->
                    new mcp_Server(new LinkedHashMap<>(inst.arg(0).asRec().jvm()), MCP_SERVER_TID, inst.arg(0).vid()))).create();

    public mcp_Server(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * Handle a JSON-RPC message and return the response.
     * Transport layers call this method, then deliver the returned result.
     *
     * @param message the incoming parsed JSON-RPC message (as a Rec)
     * @return the JSON-RPC response to send (noobj for notifications/errors)
     */
    public Obj handleMessage(final Obj message) {
        try {
            // If the input is not a Rec (e.g. a plain string), pass it through
            if (!message.isRec()) {
                return message;
            }
            final Rec json = message.asRec();
            final String method = json.at(uri("method")).isNoObj() ? "" : json.at(uri("method")).uriValue().toString();
            final Obj id = json.at(uri(ID));
            final Rec params = json.at(uri("params")).isNoObj() ? rec() : json.at(uri("params")).asRec();

            LOG.debug("mcp request: method=%s, id=%s, params=%s", method, id, params);

            final Obj result = switch (method) {
                // ========================================
                // Tools
                // ========================================
                case "tools/list" -> {
                    yield mcpResponse(id, rec(
                            uri("tools"), lst(this.at(TOOL).orElse(rec0()).elements()
                                    .map(kv -> (Obj) rec(
                                            uri(NAME), str(kv.first().uriValue().toString()),
                                            uri(DESCRIPTION), str(toolDescription(kv.second())),
                                            uri("inputSchema"), buildInputSchema(kv.second())))
                                    .toList())));
                }
                case "tools/call" -> {
                    final String toolName = params.at(uri(NAME)).isNoObj() ? "" : params.at(uri(NAME)).toCleanString();
                    final Rec arguments = params.at(uri("arguments")).isNoObj() ? rec() : params.at(uri("arguments")).asRec();
                    final Obj toolEntry = this.at(TOOL).orElse(rec0()).at(uri(toolName));
                    if (toolEntry.isNoObj()) {
                        yield mcpError(id, jnt(-32601), str("tool not found: " + toolName));
                    } else {
                        final Obj toolLhs = arguments.at(uri(LHS)).orElse(noobj());
                        final Obj toolResult = toolEntry.asInst().args(arguments).apply(toolLhs);
                        yield mcpResponse(id, rec(uri(CONTENT), lst(rec(
                                uri(TYPE), str("text"),
                                uri(TEXT), str(toolResult.toCleanString())))));
                    }
                }

                // ========================================
                // Resources
                // ========================================
                case "resources/list" -> {
                    yield mcpResponse(id, rec(
                            uri("resources"), lst(this.at(RESOURCE).orElse(rec0()).elements()
                                    .map(kv -> (Obj) rec(
                                            uri(URI), uri(kv.first().uriValue().toString()),
                                            uri(NAME), str(kv.first().uriValue().toString()),
                                            uri(DESCRIPTION), str(kv.second().toShortString())))
                                    .toList())));
                }
                case "resources/read" -> {
                    final String resourceUri = params.at(uri(URI)).isNoObj() ? "" : params.at(uri(URI)).toCleanString();
                    final Obj resourceEntry = this.at(RESOURCE).orElse(rec0()).at(uri(resourceUri));
                    if (resourceEntry.isNoObj()) {
                        yield mcpError(id, jnt(-32602), str("resource not found: " + resourceUri));
                    } else {
                        final Obj resolved = resourceEntry.resolve(noobj());
                        yield mcpResponse(id, rec(uri("contents"), lst(rec(
                                uri(URI), uri(resourceUri),
                                uri(TEXT), str(resolved.toCleanString()),
                                uri("mimeType"), str("text/plain")))));
                    }
                }

                // ========================================
                // Prompts
                // ========================================
                case "prompts/list" -> {
                    yield mcpResponse(id, rec(
                            uri("prompts"), lst(this.at(PROMPT).orElse(rec0()).elements()
                                    .map(kv -> (Obj) rec(
                                            uri(NAME), str(kv.first().uriValue().toString()),
                                            uri(DESCRIPTION), str(kv.second().toShortString())))
                                    .toList())));
                }
                case "prompts/get" -> {
                    final String promptName = params.at(uri(NAME)).isNoObj() ? "" : params.at(uri(NAME)).toCleanString();
                    final Obj promptEntry = this.at(PROMPT).orElse(rec0()).at(uri(promptName));
                    if (promptEntry.isNoObj()) {
                        yield mcpError(id, jnt(-32602), str("prompt not found: " + promptName));
                    } else {
                        final Obj resolved = promptEntry.resolve(noobj());
                        yield mcpResponse(id, rec(uri("messages"), lst(rec(
                                uri("role"), str("user"),
                                uri(CONTENT), rec(
                                        uri(TYPE), str("text"),
                                        uri(TEXT), str(resolved.toCleanString()))))));
                    }
                }

                // ========================================
                // Initialize / Ping / Notifications
                // ========================================
                case "initialize" -> {
                    final boolean hasTools = !this.at(TOOL).isNoObj();
                    final boolean hasResources = !this.at(RESOURCE).isNoObj();
                    final boolean hasPrompts = !this.at(PROMPT).isNoObj();
                    final Rec caps = rec();
                    if (hasTools) caps.at(uri("tools"), rec(), Rec.MUTABLE);
                    if (hasResources) caps.at(uri("resources"), rec(), Rec.MUTABLE);
                    if (hasPrompts) caps.at(uri("prompts"), rec(), Rec.MUTABLE);
                    yield mcpResponse(id, rec(
                            uri("protocolVersion"), str("2025-03-26"),
                            uri("capabilities"), caps,
                            uri("serverInfo"), rec(
                                    uri(NAME), str("metatron-mcp"),
                                    uri("version"), str("0.1.0"))));
                }
                case "ping" -> {
                    yield mcpResponse(id, rec());
                }
                case "notifications/initialized", "notifications/cancelled" -> {
                    yield noobj(); // no response for notifications
                }

                // ========================================
                // Unknown method
                // ========================================
                default -> {
                    LOG.warn("unknown mcp method: %s", method);
                    yield mcpError(id, jnt(-32601), str("method not found: " + method));
                }
            };
            return result;
        } catch (final Exception e) {
            LOG.error("error processing mcp message: %s -- %s", message, e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            for (var ste : e.getStackTrace()) {
                LOG.error("  at %s.%s(%s:%d)", ste.getClassName(), ste.getMethodName(), ste.getFileName(), ste.getLineNumber());
            }
            return fail(e);
        }
    }

    // ========================================
    // JSON-RPC Helpers
    // ========================================

    protected static Obj mcpResponse(final Obj id, final Rec result) {
        final Rec response = rec(
                uri(JSONRPC), str("2.0"),
                uri(RESULT), result);
        if (!id.isNoObj()) {
            response.at(uri(ID), id, Rec.MUTABLE);
        }
        return response;
    }

    protected static Obj mcpError(final Obj id, final Obj code, final Obj message) {
        final Rec response = rec(
                uri(JSONRPC), str("2.0"),
                uri("error"), rec(
                        uri(CODE), code,
                        uri(MESSAGE), message));
        if (!id.isNoObj()) {
            response.at(uri(ID), id, Rec.MUTABLE);
        }
        return response;
    }

    // ========================================
    // JSON Schema helpers (docq integration)
    // ========================================

    protected static String toolDescription(final Obj toolEntry) {
        if (!toolEntry.isObjInst())
            return toolEntry.toShortString();
        final Inst inst = toolEntry.asInst();
        final Obj docObj = Router.global().read(inst.tid().q(DOCQ, null));
        if (docObj.isRec() && !QCollection.isNoDocs(docObj)) {
            final QCollection.Docs doc = new QCollection.Docs(docObj.asRec());
            final String desc = doc.description();
            if (desc != null && !desc.isEmpty())
                return desc;
        }
        return inst.tid().name();
    }

    protected static Rec buildInputSchema(final Obj toolEntry) {
        if (!toolEntry.isObjInst())
            return rec(uri(TYPE), str(OBJECT), uri("properties"), rec());

        final Inst inst = toolEntry.asInst();
        final Obj docObj = Router.global().read(inst.tid().q(DOCQ, null));
        final Rec docArgs = docObj.isRec() && !QCollection.isNoDocs(docObj)
                ? new QCollection.Docs(docObj.asRec()).args().orElse(rec()).asRec()
                : rec();

        final Rec properties = rec();
        final java.util.List<Obj> required = new java.util.ArrayList<>();

        if (!inst.dom().isNoObj() && !inst.dom().c().isZeroable()) {
            final String argDesc = docArgs.at(uri(Inst.DOM)).isNoObj()
                    ? "" : docArgs.at(uri(Inst.DOM)).toCleanString();
            properties.at(uri(LHS),
                    rec(uri(TYPE), str(objTypeToJsonSchema(inst.dom())),
                            uri(DESCRIPTION), str(argDesc)),
                    Rec.MUTABLE);
            required.add(str(LHS));
        }

        final Poly<?, ?> args = inst.args().orElse(rec());
        if (!args.isNoObj() && !args.isEmpty()) {
            if (args.isRec()) {
                args.asRec().elements().forEach(rel -> {
                    final String argName = rel.first().uriValue().toString();
                    final String argDesc = docArgs.at(rel.first()).isNoObj()
                            ? "" : docArgs.at(rel.first()).toCleanString();
                    properties.at(uri(argName),
                            rec(uri(TYPE), str(objTypeToJsonSchema(rel.second())),
                                    uri(DESCRIPTION), str(argDesc)),
                            Rec.MUTABLE);
                    if (!rel.second().c().isZeroable())
                        required.add(str(argName));
                });
            } else if (args.isLst()) {
                args.asLst().indexedStream().forEach(indexedArg -> {
                    final String argName = String.valueOf(indexedArg.first().intValue().intValue());
                    final Obj argType = indexedArg.second();
                    final Obj docEntry = docArgs.at(indexedArg.first());
                    final String argDesc = docEntry.isNoObj() ? "" : docEntry.toCleanString();
                    properties.at(uri(argName),
                            rec(uri(TYPE), str(objTypeToJsonSchema(argType)),
                                    uri(DESCRIPTION), str(argDesc)),
                            Rec.MUTABLE);
                    if (!argType.c().isZeroable())
                        required.add(str(argName));
                });
            }
        }

        return rec(
                uri(TYPE), str(OBJECT),
                uri("properties"), properties,
                uri(REQUIRED), lst(required));
    }

    protected static String objTypeToJsonSchema(final Obj obj) {
        if (obj.isStr() || obj.isUri()) return "string";
        if (obj.isInt()) return "integer";
        if (obj.isReal()) return "number";
        if (obj.isBool()) return "boolean";
        if (obj.isLst()) return "array";
        if (obj.isRec()) return OBJECT;
        if (obj.isType()) {
            final String tidName = obj.asType().vid().basePath().toString();
            if (tidName.contains("str") || tidName.contains("uri")) return "string";
            if (tidName.contains("int")) return "integer";
            if (tidName.contains("real")) return "number";
            if (tidName.contains("bool")) return "boolean";
            if (tidName.contains("lst")) return "array";
            if (tidName.contains("rec")) return OBJECT;
        }
        return "string";
    }

    // ========================================
    // Public API for transport wrappers
    // ========================================

    /**
     * Returns the serialization IO config for MCP (JSON in/out).
     */
    public WebSocketRec.IO getIO() {
        return new WebSocketRec.IO(
                Content.ContentType.of(Content.ContentType.APPLICATION_JSON.value),
                Content.ContentType.of(Content.ContentType.APPLICATION_JSON.value));
    }

    /**
     * Returns an empty tool list — subclasses override to provide tools.
     */
    public Rec getToolList() {
        return rec();
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
                                final Map<Obj, Obj> spaces = new LinkedHashMap<>(Router.global().spaces().jvm());
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

            // list_inst — list loaded /m instructions
            tools.at(uri("list_inst"), instC(
                    vid.extend("list_inst").dom(NOOBJ_TID.zero()).rng(ALL.maybe()),
                    rec(uri(DOC), BOOL_TYPE.maybe()), (lhs, inst) -> {
                        final Obj docArg = inst.arg(f(DOC), 0);
                        final boolean withDoc = docArg.isBool() && docArg.boolValue()
                                || !docArg.isNoObj() && docArg.toCleanString().equalsIgnoreCase("true");
                        return lst(Router.global().read(withDoc ? "/m/inst/#?doc" : "/m/inst/+"));
                    }), MUTABLE);

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
                            vid.extend("spawn_wshandler").dom(NOOBJ_TID.zero()).rng(WS_SERVER_TID),
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
