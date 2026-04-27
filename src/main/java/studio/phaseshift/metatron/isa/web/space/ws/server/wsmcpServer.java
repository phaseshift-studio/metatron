/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.space.ws.WSServerRec;
import studio.phaseshift.metatron.isa.web.type.Content;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.DOCQ;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.Fail.FAIL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
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
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_SERVER_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_SPACE_TID;

/**
 * MCP (Model Context Protocol) server for wsSpace.
 * Handles JSON-RPC based communication for AI/LLM tool integration.
 * <p>
 * The MCP server type exposes three categories of capabilities:
 * <ul>
 *   <li><b>tool</b> — functions that LLMs can invoke (e.g. search, calculate)</li>
 *   <li><b>resource</b> — data/context that can be read (e.g. files, APIs)</li>
 *   <li><b>prompt</b> — templated messages/workflows for users</li>
 * </ul>
 * <p>
 * Users extend this server by defining a type in mtron:
 * <pre>
 * mymcp::T[?[
 *   tool=>[
 *     myTool=>myTool(a1,a2){ ... },
 *     ...],
 *   resource=>[
 *     myRes=>!*&lt;http://...&gt;,
 *     ...],
 *   prompt=>[
 *     myPrompt=>"template string",
 *     ...]
 * ]]@mymcp
 * </pre>
 * Then register it in the wsSpace route table and connect via WebSocket.
 */
public class wsmcpServer extends WSServerRec {

    public static final fURI WS_MCP_SERVER_TID = WS_SPACE_TID.extend("wsmcp");
    protected final GraphittyLogger LOG = Graphitty.log(this);

    public static final Type WS_MCP_SERVER_TYPE = Type.Builder.build()
            .tid(WS_SERVER_TID)
            .vid(WS_MCP_SERVER_TID)
            .isaPredicate(rec(
                    uri(TOOL).maybe().asUri(), lst(INST_TYPE),
                    uri(RESOURCE).maybe().asUri(), T(ALL),
                    uri(PROMPT).maybe().asUri(), T(ALL)))
            .constructor(instC(WS_MCP_SERVER_TID.extend(CTOR).dom(ALL.maybe()).rng(WS_MCP_SERVER_TID), lst(T(REC_TID)), (lhs, inst) ->
                    new wsmcpServer(new LinkedHashMap<>(inst.arg(0).asRec()
                            .at(uri(IN), uri(Content.ContentType.APPLICATION_JSON.value))
                            .at(uri(OUT), uri(Content.ContentType.APPLICATION_JSON.value)).jvm()), WS_MCP_SERVER_TID, inst.arg(0).vid()))).create();

    public wsmcpServer(final Rec recClone) {
        this(recClone.jvm(), recClone.tid(), recClone.vid());
    }
    
    public wsmcpServer(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.outContentType = Content.ContentType.APPLICATION_JSON;
        this.inContentType = Content.ContentType.APPLICATION_JSON;
        this.jvm().put(uri(ON_OPEN), instC(vid.extend(ON_OPEN), lst(URI_TYPE), (lhs, inst) -> {
            LOG.info("mcp server opened w/ serializers: [in=>%s,out=>%s]", this.inContentType.name(), this.outContentType.name());
            return noobj();
        }));
        this.jvm().put(uri(ON_MESSAGE), instC(vid.extend(ON_MESSAGE).dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> {
            try {
                LOG.info("incoming mcp message for %s: %s", this.vid(), lhs);
                // MCP JSON-RPC message handling
                // If the input is not a Rec (e.g. a plain string), pass it through
                if (!lhs.isRec()) {
                    return lhs;
                }
                final Rec json = lhs.asRec();
                // Extract JSON-RPC fields: method, id, params
                final String method = json.at(uri("method")).isNoObj() ? "" : json.at(uri("method")).uriValue().toString();
                final Obj id = json.at(uri(ID));
                final Rec params = json.at(uri("params")).isNoObj() ? rec() : json.at(uri("params")).asRec();

                LOG.debug("mcp request: method=%s, id=%s, params=%s", method, id, params);

                final Obj result;
                switch (method) {
                    // ========================================
                    // Tools
                    // ========================================
                    case "tools/list" -> {
                        // Return the list of available tools with docq-derived JSON Schema
                        final Rec tools = this.at(TOOL).isNoObj() ? rec() : this.at(TOOL).asRec();
                        result = mcpResponse(id, rec(
                                uri(TOOL), lst(tools.elements()
                                        .map(kv -> (Obj) rec(
                                                uri(NAME), str(kv.first().uriValue().toString()),
                                                uri(DESC), str(toolDescription(kv.second())),
                                                uri("inputSchema"), buildInputSchema(kv.second())))
                                        .toList())));
                    }
                    case "tools/call" -> {
                        // Invoke a specific tool by name with the given arguments.
                        // Arguments arrive as a mtron Rec from the JSON deserializer.
                        // The optional "lhs" key carries the left-hand side input; all other
                        // keys are bound as named inst args via inst.args(argsRec).apply(lhs).
                        final String toolName = params.at(uri(NAME)).isNoObj() ? "" : params.at(uri(NAME)).toCleanString();
                        final Rec arguments = params.at(uri("arguments")).isNoObj() ? rec() : params.at(uri("arguments")).asRec();
                        final Obj toolEntry = this.at(TOOL).isNoObj() ? noobj() : this.at(TOOL).asRec().at(uri(toolName));
                        if (toolEntry.isNoObj()) {
                            result = mcpError(id, jnt(-32601), str("tool not found: " + toolName));
                        } else {
                            // Extract lhs if caller supplied it; remaining keys become inst args.
                            // Use a distinct name ("toolLhs") to avoid shadowing the outer lambda's lhs.
                            final Obj toolLhs = arguments.at(uri(LHS)).orElse(noobj());
                            final Obj toolResult = toolEntry.asInst().args(arguments).apply(toolLhs);
                            result = mcpResponse(id, rec(
                                    uri(CONTENT), lst(
                                            (Obj) rec(
                                                    uri(TYPE), str("text"),
                                                    uri(TEXT), str(toolResult.toCleanString())))));
                        }
                    }

                    // ========================================
                    // Resources
                    // ========================================
                    case "resources/list" -> {
                        // Return the list of available resources
                        final Rec resources = this.at(RESOURCE).isNoObj() ? rec() : this.at(RESOURCE).asRec();
                        result = mcpResponse(id, rec(
                                uri(RESOURCE), lst(resources.elements()
                                        .map(kv -> (Obj) rec(
                                                uri(URI), uri(kv.first().uriValue().toString()),
                                                uri(NAME), str(kv.first().uriValue().toString()),
                                                uri(DESC), str(kv.second().toShortString())))
                                        .toList())));
                    }
                    case "resources/read" -> {
                        // Read a specific resource by URI
                        final String resourceUri = params.at(uri(URI)).isNoObj() ? "" : params.at(uri(URI)).toCleanString();
                        final Obj resourceEntry = this.at(RESOURCE).isNoObj() ? noobj() : this.at(RESOURCE).asRec().at(uri(resourceUri));
                        if (resourceEntry.isNoObj()) {
                            result = mcpError(id, jnt(-32602), str("resource not found: " + resourceUri));
                        } else {
                            // Resolve the resource value (could be a URI reference or inline content)
                            final Obj resolved = resourceEntry.resolve(noobj());
                            result = mcpResponse(id, rec(
                                    uri(CONTENT), lst(
                                            (Obj) rec(
                                                    uri(URI), uri(resourceUri),
                                                    uri(TEXT), str(resolved.toCleanString()),
                                                    uri("mimeType"), str("text/plain")))));
                        }
                    }

                    // ========================================
                    // Prompts
                    // ========================================
                    case "prompts/list" -> {
                        // Return the list of available prompts
                        final Rec prompts = this.at(PROMPT).isNoObj() ? rec() : this.at(PROMPT).asRec();
                        result = mcpResponse(id, rec(
                                uri(PROMPT), lst(prompts.elements()
                                        .map(kv -> (Obj) rec(
                                                uri(NAME), str(kv.first().uriValue().toString()),
                                                uri(DESC), str(kv.second().toShortString())))
                                        .toList())));
                    }
                    case "prompts/get" -> {
                        // Get a specific prompt by name
                        final String promptName = params.at(uri(NAME)).isNoObj() ? "" : params.at(uri(NAME)).toCleanString();
                        final Obj promptEntry = this.at(PROMPT).isNoObj() ? noobj() : this.at(PROMPT).asRec().at(uri(promptName));
                        if (promptEntry.isNoObj()) {
                            result = mcpError(id, jnt(-32602), str("prompt not found: " + promptName));
                        } else {
                            final Obj resolved = promptEntry.resolve(noobj());
                            result = mcpResponse(id, rec(
                                    uri("messages"), lst(
                                            (Obj) rec(
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
                        result = mcpResponse(id, rec(
                                uri("protocolVersion"), str("2025-03-26"),
                                uri("capabilities"), rec(
                                        uri(TOOL), rec(),
                                        uri(RESOURCE), rec(),
                                        uri(PROMPT), rec()),
                                uri("serverInfo"), rec(
                                        uri(NAME), str("metatron-mcp"),
                                        uri("version"), str("0.1.0"))));
                    }
                    case "ping" -> {
                        result = mcpResponse(id, rec());
                    }
                    case "notifications/initialized", "notifications/cancelled" -> {
                        // Notifications have no id — return noobj to avoid sending a response
                        result = noobj();
                    }

                    // ========================================
                    // Unknown method
                    // ========================================
                    default -> {
                        LOG.warn("unknown mcp method: %s", method);
                        result = mcpError(id, jnt(-32601), str("method not found: " + method));
                    }
                }
                return result;
            } catch (final Exception e) {
                LOG.error("error processing mcp message: %s -- %s", lhs, e.getMessage() == null ? e.getClass().getName() : e.getMessage());
                for (var ste : e.getStackTrace()) {
                    LOG.error("  at %s.%s(%s:%d)", ste.getClassName(), ste.getMethodName(), ste.getFileName(), ste.getLineNumber());
                }
                return fail(e);
            }
        }));
        this.jvm().put(uri(ON_CLOSE), instC(vid.extend(ON_CLOSE), rec(uri(CODE), INT_TYPE, uri(REASON), STR_TYPE), (lhs, inst) -> {
            LOG.info("closing mcp endpoint w/ %s: code={{y}}%s{{X}}, reason={{y}}%s{{X}}", this.socket.getRemoteSocketAddress(), inst.arg(CODE), inst.arg(REASON));
            return noobj();
        }));
        this.jvm().put(uri(ON_ERROR), instC(vid.extend(ON_ERROR), lst(FAIL_TYPE), (lhs, inst) -> {
            LOG.error("error occurred w/ %s: %s", this.socket.getRemoteSocketAddress(), inst.arg(0));
            return noobj();
        }));
    }

    // ========================================
    // JSON-RPC Helpers
    // ========================================

    /**
     * Build a JSON-RPC 2.0 success response.
     *
     * @param id     the request id (may be noobj for notifications)
     * @param result the result payload
     * @return a JSON-RPC response record
     */
    private static Obj mcpResponse(final Obj id, final Rec result) {
        final Rec response = rec(
                uri(JSONRPC), str("2.0"),
                uri(RESULT), result);
        if (!id.isNoObj()) {
            response.at(uri(ID), id, Rec.MUTABLE);
        }
        return response;
    }

    /**
     * Build a JSON-RPC 2.0 error response.
     *
     * @param id      the request id (may be noobj for notifications)
     * @param code    the error code
     * @param message the error message
     * @return a JSON-RPC error response record
     */
    private static Obj mcpError(final Obj id, final Obj code, final Obj message) {
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

    /**
     * Extract a human-readable description for a tool entry.
     * If the entry is an Inst, reads its {@code ?docq} description from the Router.
     * Falls back to the inst's TID name if no documentation is available.
     */
    private static String toolDescription(final Obj toolEntry) {
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

    /**
     * Build a JSON Schema {@code inputSchema} record for a tool entry using
     * the inst's type signature ({@code dom}, named/positional {@code args}) and
     * any {@code ?docq} argument descriptions attached to the inst in the Router.
     * <p>
     * Schema shape: {@code {type:"object", properties:{...}, required:[...]}}
     */
    private static Rec buildInputSchema(final Obj toolEntry) {
        if (!toolEntry.isObjInst())
            return rec(uri(TYPE), str(OBJECT), uri("properties"), rec());

        final Inst inst = toolEntry.asInst();
        // Read docq for arg descriptions
        final Obj docObj = Router.global().read(inst.tid().q(DOCQ, null));
        final Rec docArgs = docObj.isRec() && !QCollection.isNoDocs(docObj)
                ? new QCollection.Docs(docObj.asRec()).args().orElse(rec()).asRec()
                : rec();

        final Rec properties = rec();
        final java.util.List<Obj> required = new java.util.ArrayList<>();

        // dom (lhs) — exposed as "lhs" parameter if non-trivial
        if (!inst.dom().isNoObj() && !inst.dom().c().isZeroable()) {
            final String argDesc = docArgs.at(uri(Inst.DOM)).isNoObj()
                    ? "" : docArgs.at(uri(Inst.DOM)).toCleanString();
            properties.at(uri(LHS),
                    rec(uri(TYPE), str(objTypeToJsonSchema(inst.dom())),
                            uri(DESC), str(argDesc)),
                    Rec.MUTABLE);
            required.add(str(LHS));
        }

        // named/positional inst args
        final Poly<?, ?> args = inst.args().orElse(rec());
        if (!args.isNoObj() && !args.isEmpty()) {
            if (args.isRec()) {
                args.asRec().elements().forEach(rel -> {
                    final String argName = rel.first().uriValue().toString();
                    final String argDesc = docArgs.at(rel.first()).isNoObj()
                            ? "" : docArgs.at(rel.first()).toCleanString();
                    properties.at(uri(argName),
                            rec(uri(TYPE), str(objTypeToJsonSchema(rel.second())),
                                    uri(DESC), str(argDesc)),
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
                                    uri(DESC), str(argDesc)),
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

    /**
     * Map a metatron type/obj to a JSON Schema primitive type string.
     */
    private static String objTypeToJsonSchema(final Obj obj) {
        if (obj.isStr() || obj.isUri()) return "string";
        if (obj.isInt()) return "integer";
        if (obj.isReal()) return "number";
        if (obj.isBool()) return "boolean";
        if (obj.isLst()) return "array";
        if (obj.isRec()) return OBJECT;
        // For Type predicates, inspect the vid to guess the base type
        if (obj.isType()) {
            final String tidName = obj.asType().vid().basePath().toString();
            if (tidName.contains("str") || tidName.contains("uri")) return "string";
            if (tidName.contains("int")) return "integer";
            if (tidName.contains("real")) return "number";
            if (tidName.contains("bool")) return "boolean";
            if (tidName.contains("lst")) return "array";
            if (tidName.contains("rec")) return OBJECT;
        }
        return "string"; // safe fallback
    }
}
