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

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.*;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.llm.type.mSkill;
import studio.phaseshift.metatron.isa.llm.type.mTool;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.web.space.ws.handler.mcp_wsHandler;

import java.util.Map;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.MCP_SERVER_TID;
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
public class mcpServer extends MRec {

    protected final GraphittyLogger LOG = Graphitty.log(this);
    private static final String DESCRIPTION = "description";

    public mcpServer(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * Reduce a {@link mSkill} to an MCP server: the skill's tools become the
     * server's {@code tool} rec (keyed by derived tool name) and its resources
     * become the server's {@code resource} rec (keyed by uri).  Prompts are
     * absent (skills don't carry prompts).
     *
     * @param skill the skill to expose as an MCP server
     * @return an {@code mcp_server::T} wrapping the skill's tools/resources
     */
    public static mcpServer of(final mSkill skill) {
        final Map<Obj, Obj> jvm = mutableMap();
        if (skill.has(TOOL)) {
            final Rec tools = rec(mutableMap());
            skill.at(TOOL).asLst().elements().forEach(inst -> {
                tools.at(uri(inst.asInst().tid().basePath().toString().replaceAll("^/+", "").replace("/", "_")), inst, Rec.MUTABLE);
            });
            jvm.put(uri(TOOL), tools);
        }
        if (skill.has(RESOURCE)) {
            final Rec resources = rec(mutableMap());
            skill.at(RESOURCE).asLst().elements().forEach(r -> {
                resources.jvm().put(r.asRec().at(uri(URI)), r);
            });
            jvm.put(uri(RESOURCE), resources);
        }
        return new mcpServer(jvm, MCP_SERVER_TID, null);
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

            return switch (method) {
                case "tools/list" -> handleToolsList(id, params);
                case "tools/call" -> handleToolsCall(id, params);
                case "resources/list" -> handleResourcesList(id, params);
                case "resources/read" -> handleResourcesRead(id, params);
                case "resources/templates/list" -> handleResourcesTemplatesList(id, params);
                case "prompts/list" -> handlePromptsList(id, params);
                case "prompts/get" -> handlePromptsGet(id, params);
                case "initialize" -> handleInitialize(id, params);
                case "ping" -> handlePing(id, params);
                case "notifications/initialized", "notifications/cancelled" -> handleNotifications(id, method);
                default -> handleUnknownMethod(id, method);
            };
        } catch (final Exception e) {
            LOG.error("error processing mcp message: %s -- %s", message, e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            for (var ste : e.getStackTrace()) {
                LOG.error("  at %s.%s(%s:%d)", ste.getClassName(), ste.getMethodName(), ste.getFileName(), ste.getLineNumber());
            }
            return fail(e);
        }
    }

    // ========================================
    // MCP Protocol Handlers (overridable by subclasses)
    // ========================================

    /**
     * Handle a {@code tools/list} request.
     * Returns the list of tools registered in this server's {@code tool} rec.
     */
    protected Obj handleToolsList(final Obj id, final Rec params) {
        return mcpResponse(id, rec(
                uri("tools"), lst(this.at(TOOL).orElse(rec0()).elements()
                        .map(kv -> {
                            final Obj toolEntry = kv.second();
                            if (!toolEntry.isObjInst())
                                return (Obj) rec(uri(NAME), str(kv.first().uriValue().toString()),
                                        uri(DESCRIPTION), str(toolEntry.toShortString()),
                                        uri("inputSchema"), rec(uri(TYPE), str(OBJECT), uri("properties"), rec()));
                            final ToolSpecification spec = mTool.mtronInstToolSpecification(mTool.mtronInstToTool(toolEntry.asInst())).get0();
                            return (Obj) rec(uri(NAME), str(kv.first().uriValue().toString()),
                                    uri(DESCRIPTION), str(null == spec.description() ? "<no description>" : spec.description()),
                                    uri("inputSchema"), jsonSchemaToRec(spec.parameters()));
                        })
                        .toList())));
    }

    /**
     * Handle a {@code tools/call} request.
     * Looks up the named tool and applies it with the supplied arguments.
     */
    protected Obj handleToolsCall(final Obj id, final Rec params) {
        final String toolName = params.at(uri(NAME)).isNoObj() ? "" : params.at(uri(NAME)).toCleanString();
        final Rec arguments = params.at(uri("arguments")).isNoObj() ? rec() : params.at(uri("arguments")).asRec();
        // ── wire-key alias: "arguments" → "args" for metatron convention ──
        if (!arguments.jvm().containsKey(uri(ARGS)) && arguments.jvm().containsKey(uri("arguments")))
            arguments.jvm().put(uri(ARGS), arguments.jvm().get(uri("arguments")));
        final Obj toolEntry = this.at(TOOL).orElse(rec0()).at(uri(toolName));
        if (toolEntry.isNoObj()) {
            return mcpError(id, jnt(-32601), str("tool not found: " + toolName));
        } else {
            // parse raw JSON args into typed mtron objs (mirrors the LLM mTool executor)
            final Inst toolInst = toolEntry.asInst();
            final Map<Obj, Obj> argMap = arguments.jvm();
            final Poly<?, ?> args = toolInst.args().isNoObj() ? lst() : (toolInst.args().isLst() ?
                    lst(argMap.entrySet().stream().filter(e -> !e.getKey().equals(uri(LHS))).map(e -> ObjmtronSerializer.<Obj>parse(e.getValue().toString())).collect(Collectors.toList())) :
                    rec(argMap.entrySet().stream().filter(e -> !e.getKey().equals(uri(LHS))).collect(Collectors.toMap(e -> uri(e.getKey().toString()), e -> ObjmtronSerializer.parse(e.getValue().toString())))));
            final Obj toolLhs = argMap.containsKey(uri(LHS)) ? ObjmtronSerializer.compact().read(argMap.get(uri(LHS)).toString()) : noobj();
            final Obj toolResult = toolInst.args(args).apply(toolLhs);
            if (toolResult.isFail())
                return mcpError(id, jnt(-32603), str(toolResult.asFail().message()));
            return mcpResponse(id, rec(uri(CONTENT), lst(rec(
                    uri(TYPE), str("text"),
                    uri(TEXT), str(toolResult.toCleanString())))));
        }
    }

    /**
     * Handle a {@code resources/list} request.
     * Returns the list of resources registered in this server's {@code resource} rec.
     */
    protected Obj handleResourcesList(final Obj id, final Rec params) {
        return mcpResponse(id, rec(uri("resources"), lst(this.at(RESOURCE)
                .orElse(rec0())
                .jvm()
                .values()
                .stream()
                .map(r -> {
                    final Map<Obj, Obj> m = r.asRec().jvm();
                    final Rec item = rec(
                            uri(URI), str(m.get(uri(URI)).uriValue().toString()),
                            uri(NAME), m.get(uri(NAME)),
                            uri(DESCRIPTION), m.get(uri(DESC)));
                    if (m.containsKey(uri(REFERENCE)))
                        item.at(uri(REFERENCE), m.get(uri(REFERENCE)), MUTABLE);
                    return (Obj) item;
                })
                .toList())));
    }

    /**
     * Handle a {@code resources/read} request.
     * Resolves the named resource and returns its contents.
     */
    protected Obj handleResourcesRead(final Obj id, final Rec params) {
        final String resourceUri = params.at(uri(URI)).isNoObj() ? "" : params.at(uri(URI)).toCleanString();
        final Obj entry = this.at(RESOURCE).orElse(rec0()).jvm().get(uri(resourceUri));
        if (null == entry || entry.isNoObj()) {
            return mcpError(id, jnt(-32602), str("resource not found: " + resourceUri));
        }
        final Map<Obj, Obj> m = entry.asRec().jvm();
        // large resource: emit the reference path AS the text, not a non-standard field
        final Obj content = m.containsKey(uri(REFERENCE)) ? m.get(uri(REFERENCE)) : m.get(uri(TEXT));
        return mcpResponse(id, rec(uri("contents"), lst(rec(
                        uri(URI), str(resourceUri),
                        uri(TEXT), content,
                        uri("mimeType"), str(MIME.MIMEType.fromExtension(resourceUri, MIME.MIMEType.TEXT_PLAIN).value)))));
    }

    /**
     * Handle a {@code resources/templates/list} request.
     * Resolves the named resource and returns its contents.
     */
    protected Obj handleResourcesTemplatesList(final Obj id, final Rec params) {
        return mcpResponse(id, rec(uri("resourceTemplates"), lst()));
    }

    /**
     * Handle a {@code prompts/list} request.
     * Returns the list of prompts registered in this server's {@code prompt} rec.
     */
    protected Obj handlePromptsList(final Obj id, final Rec params) {
        return mcpResponse(id, rec(
                uri("prompts"), lst(this.at(PROMPT).orElse(rec0()).elements()
                        .map(kv -> (Obj) rec(
                                uri(NAME), str(kv.first().uriValue().toString()),
                                uri(DESCRIPTION), str(kv.second().toShortString())))
                        .toList())));
    }

    /**
     * Handle a {@code prompts/get} request.
     * Resolves the named prompt and returns its rendered messages.
     */
    protected Obj handlePromptsGet(final Obj id, final Rec params) {
        final String promptName = params.at(uri(NAME)).isNoObj() ? "" : params.at(uri(NAME)).toCleanString();
        final Obj promptEntry = this.at(PROMPT).orElse(rec0()).at(uri(promptName));
        if (promptEntry.isNoObj()) {
            return mcpError(id, jnt(-32602), str("prompt not found: " + promptName));
        } else {
            final Obj resolved = promptEntry.resolve(noobj());
            return mcpResponse(id, rec(uri("messages"), lst(rec(
                    uri("role"), str("user"),
                    uri(CONTENT), rec(
                            uri(TYPE), str("text"),
                            uri(TEXT), str(resolved.toCleanString()))))));
        }
    }

    /**
     * Handle an {@code initialize} request.
     * Returns server capabilities and info based on the presence of tools/resources/prompts.
     */
    protected Obj handleInitialize(final Obj id, final Rec params) {
        final boolean hasTools = !this.at(TOOL).isNoObj();
        final boolean hasResources = !this.at(RESOURCE).isNoObj();
        final boolean hasPrompts = !this.at(PROMPT).isNoObj();
        final Rec caps = rec();
        if (hasTools) caps.at(uri("tools"), rec(), Rec.MUTABLE);
        if (hasResources) caps.at(uri("resources"), rec(), Rec.MUTABLE);
        if (hasPrompts) caps.at(uri("prompts"), rec(), Rec.MUTABLE);
        return mcpResponse(id, rec(
                uri("protocolVersion"), str("2025-03-26"),
                uri("capabilities"), caps,
                uri("serverInfo"), rec(
                        uri(NAME), str("metatron-mcp"),
                        uri("version"), str("0.1.0"))));
    }

    /**
     * Handle a {@code ping} request.
     * Returns an empty success response.
     */
    protected Obj handlePing(final Obj id, final Rec params) {
        return mcpResponse(id, rec());
    }

    /**
     * Handle notification messages ({@code notifications/initialized},
     * {@code notifications/cancelled}). Notifications require no response.
     */
    protected Obj handleNotifications(final Obj id, final String method) {
        return noobj();
    }

    /**
     * Handle an unknown or unrecognized JSON-RPC method.
     * Returns a method-not-found error (code -32601).
     */
    protected Obj handleUnknownMethod(final Obj id, final String method) {
        if (method.isBlank())
            return noobj();
        LOG.error("unknown mcp method: %s", method);
        return mcpError(id, jnt(-32601), str("method not found: " + method));
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
    // JSON Schema helper
    // ========================================

    /**
     * Serialize a LangChain4j {@link JsonSchemaElement} to the MCP wire format
     * (a mtron rec with {@code type}/{@code properties}/{@code required}).
     */
    protected static Rec jsonSchemaToRec(final JsonSchemaElement element) {
        if (null == element)
            return rec(uri(TYPE), str(OBJECT), uri("properties"), rec());
        if (element instanceof JsonObjectSchema obj) {
            final Rec properties = rec();
            obj.properties().forEach((name, sub) -> properties.at(uri(name), jsonSchemaToRec(sub), Rec.MUTABLE));
            return rec(uri(TYPE), str(OBJECT),
                    uri("properties"), properties,
                    uri(REQUIRED), lst(obj.required().stream().map(s -> (Obj) str(s)).toList()));
        } else if (element instanceof JsonArraySchema arr) {
            return rec(uri(TYPE), str("array"), uri("items"), jsonSchemaToRec(arr.items()));
        } else if (element instanceof JsonBooleanSchema) {
            return rec(uri(TYPE), str("boolean"));
        } else if (element instanceof JsonIntegerSchema) {
            return rec(uri(TYPE), str("integer"));
        } else if (element instanceof JsonNumberSchema) {
            return rec(uri(TYPE), str("number"));
        } else if (element instanceof JsonStringSchema) {
            return rec(uri(TYPE), str("string"));
        } else if (element instanceof JsonEnumSchema en) {
            return rec(uri(TYPE), str("string"), uri("enum"), lst(en.enumValues().stream().map(s -> (Obj) str(s)).toList()));
        } else if (element instanceof JsonReferenceSchema ref) {
            return rec(uri("$ref"), str(ref.reference()));
        } else if (element instanceof JsonAnyOfSchema anyOf) {
            return rec(uri("anyOf"), lst(anyOf.anyOf().stream().map(e -> (Obj) jsonSchemaToRec(e)).toList()));
        } else {
            return rec(uri(TYPE), str("string"));
        }
    }

    // ========================================
    // Public API for transport wrappers
    // ========================================

    /**
     * Returns the serialization IO config for MCP (JSON in/out).
     */
    public WebSocketRec.IO getIO() {
        return new WebSocketRec.IO(
                MIME.MIMEType.of(MIME.MIMEType.APPLICATION_JSON.value),
                MIME.MIMEType.of(MIME.MIMEType.APPLICATION_JSON.value));
    }

}
