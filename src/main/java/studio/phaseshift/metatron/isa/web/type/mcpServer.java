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
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.web.space.ws.handler.mcp_wsHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;
import static studio.phaseshift.metatron.furi.q.QCollection.SUBQ_SUB_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.LST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
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

    /** The MCP protocol version this server advertises in {@code initialize}. */
    public static final String PROTOCOL_VERSION = "2025-03-26";

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
                tools.at(uri(mTool.toolName(inst.asInst().tid())), inst, Rec.MUTABLE);
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
            final String method = json.at(uri("method")).isNoObj() ? "" : json.at(uri("method")).toCleanString();
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
                case "server/discover" -> handleServerDiscover(id, params);
                case "subscriptions/listen" -> handleSubscriptionsListen(id, params);
                case "ping" -> handlePing(id, params);
                case "notifications/initialized", "notifications/cancelled" -> handleNotifications(id, method);
                default -> handleUnknownMethod(id, method);
            };
        } catch (final Throwable e) {
            // Throwable (not Exception) — an Error such as a
            // StackOverflowError from a pathological regex in a tool
            // call must become a named fail payload, not escape to
            // the worker thread and drop the session
            LOG.error("error processing mcp message: %s -- %s", message, e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            for (var ste : e.getStackTrace()) {
                LOG.error("  at %s.%s(%s:%d)", ste.getClassName(), ste.getMethodName(), ste.getFileName(), ste.getLineNumber());
            }
            return fail(e);
        }
    }

    /**
     * Transport entry point that parses a raw JSON-RPC body with schema awareness.
     * <p>
     * A single schema-blind pass ({@code MIME.APPLICATION_JSON.fromBytes}) mangles
     * string arguments — a {@code code::T} argument gets mtron-parsed or
     * quote-stripped before the tool ever sees it.  This does a two-phase parse: a
     * blind pass to resolve the tool, then a schema-aware pass (via a
     * {@link ObjJSONSerializer} parameterized with the tool's typed args) that
     * disambiguates each {@code arguments} value to its declared inst type — so the
     * tool never has to massage a JSON string back into its own type.
     *
     * @param rawJson the raw JSON-RPC body
     * @return the JSON-RPC response (noobj for notifications)
     */
    public Obj handleMessage(final String rawJson) {
        return this.handleMessage(ObjJSONSerializer.parse(rawJson), rawJson);
    }

    /**
     * Schema-aware dispatch given an already-blind-parsed message plus the raw JSON.
     * Reusing the caller's blind parse avoids re-parsing the body a second time
     * (the HTTP handler already parsed it for session detection).
     */
    public Obj handleMessage(final Obj blind, final String rawJson) {
        if (!blind.isRec()) return this.handleMessage(blind);
        final Rec json = blind.asRec();
        final String method = json.at(uri("method")).isNoObj() ? "" : json.at(uri("method")).toCleanString();
        if (!"tools/call".equals(method)) return this.handleMessage(blind);
        final Obj params = json.at(uri("params"));
        if (params.isNoObj() || !params.isRec()) return this.handleMessage(blind);
        final String toolName = params.asRec().at(uri(NAME)).isNoObj() ? "" : params.asRec().at(uri(NAME)).toCleanString();
        final Obj toolEntry = this.at(TOOL).orElse(rec0()).at(uri(toolName));
        if (toolEntry.isNoObj() || !toolEntry.isObjInst() || !toolEntry.asInst().args().isRec()) return this.handleMessage(blind);
        try {
            final Obj schemaAware = new ObjJSONSerializer()
                    .schema(toolEntry.asInst().args().asRec())
                    .readString(rawJson);
            return this.handleMessage(schemaAware);
        } catch (final Exception e) {
            // a schema'd argument failed to parse (e.g. malformed code::T) —
            // surface it as a JSON-RPC error rather than a transport-level 400
            return mcpError(json.at(uri(ID)), jnt(-32603), str("invalid arguments: " + e.getMessage()));
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
                                return rec(uri(NAME), str(kv.first().uriValue().toString()),
                                        uri(DESCRIPTION), str(toolEntry.toShortString()),
                                        uri("inputSchema"), rec(uri(TYPE), str(OBJECT), uri("properties"), rec()));
                            final ToolSpecification spec = mTool.mtronInstToolSpecification(mTool.mtronInstToDocs(toolEntry.asInst())).get0();
                            return (Obj) rec(uri(NAME), str(spec.name()),
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
        try {
            final String toolName = params.at(uri(NAME)).isNoObj() ? "" : params.at(uri(NAME)).toCleanString();
            final Rec arguments = params.at(uri("arguments")).isNoObj() ? rec() : params.at(uri("arguments")).asRec();
            final Obj toolEntry = this.at(TOOL).orElse(rec0()).at(uri(toolName));
            if (toolEntry.isNoObj()) {
                return mcpError(id, jnt(-32601), str("tool not found: " + toolName));
            } else {
                final Inst toolInst = toolEntry.asInst();
                final Obj toolResult = mTool.applyArguments(toolInst, arguments);
                if (toolResult.isFail()) {
                    return mcpError(id, jnt(-32603), str(toolResult.asFail().message()));
                }
                // Result is serialized to plain (TRANSPARENT) JSON — no OPAQUE
                // _tid/_bid envelope, so stock MCP clients can consume it.
                // NOTE: TRANSPARENT flattens objs/rel/type to a JSON array, which
                // the client currently reads back as lst::T (not objs/rel/type).
                // Recovering those faithfully needs the client to know the tool's
                // rng (an outputSchema in tools/list) — deferred.
                return mcpResponse(id, rec(uri(CONTENT), lst(rec(
                        uri(TYPE), str(TEXT),
                        uri(TEXT), str(ObjJSONSerializer.simple().write(toolResult).toString())))));
            }
        } catch (final Exception e) {
            return mcpError(id, jnt(-32603), str("error: %s".formatted(e)));
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
                uri("protocolVersion"), str(PROTOCOL_VERSION),
                uri("capabilities"), caps,
                uri("serverInfo"), rec(
                        uri(NAME), str("metatron-mcp"),
                        uri("version"), str("0.1.0"))));
    }

    /**
     * Handle a {@code server/discover} request (2026-07-28 spec "Discovery").
     * Lets a client query supported protocol versions, capabilities, and identity
     * before any other request.  We only advertise the version we actually speak.
     */
    protected Obj handleServerDiscover(final Obj id, final Rec params) {
        final boolean hasTools = !this.at(TOOL).isNoObj();
        final boolean hasResources = !this.at(RESOURCE).isNoObj();
        final boolean hasPrompts = !this.at(PROMPT).isNoObj();
        final Rec caps = rec();
        if (hasTools) caps.at(uri("tools"), rec(), Rec.MUTABLE);
        if (hasResources) caps.at(uri("resources"), rec(), Rec.MUTABLE);
        if (hasPrompts) caps.at(uri("prompts"), rec(), Rec.MUTABLE);
        return mcpResponse(id, rec(
                uri("resultType"), str("complete"),
                uri("supportedVersions"), lst(str(PROTOCOL_VERSION)),
                uri("capabilities"), caps,
                uri("serverInfo"), rec(
                        uri(NAME), str("metatron-mcp"),
                        uri("version"), str("0.1.0"))));
    }

    /**
     * Handle a {@code subscriptions/listen} request (2026-07-28 spec "Subscriptions").
     * <p>
     * Registers a metatron {@code ?subq} pub/sub subscription for each URI in
     * {@code params.notifications.resourceSubscriptions}, then acknowledges the
     * subset it agreed to honor. When a subscribed resource mutates, the sub's code
     * fires and captures a {@code notifications/resources/updated} rec into a
     * per-server outbox ({@code <server>subscriptions/<id>?incrq}).
     * <p>
     * NOTE: {@code subscriptions/listen} is a long-lived (SSE) stream in the spec —
     * the acknowledgment and pushed notifications normally travel out-of-band on
     * that stream. This server is request/response, so delivery is deferred: we
     * register the subscription and return the acknowledgment as the JSON-RPC
     * response, while fired notifications accumulate in the outbox for a future
     * transport to drain.
     */
    protected Obj handleSubscriptionsListen(final Obj id, final Rec params) {
        final Obj notifications = params.at(uri("notifications"));
        final Rec acked = rec();
        if (!notifications.isNoObj() && notifications.isRec()) {
            final Rec notif = notifications.asRec();
            final Obj resourceSubs = notif.at(uri("resourceSubscriptions"));
            if (!resourceSubs.isNoObj() && resourceSubs.isLst()) {
                final List<Obj> ackedUris = new ArrayList<>();
                for (final Obj u : resourceSubs.asLst().lstValue()) {
                    final fURI target = f(u.toCleanString());
                    if (this.subscribeResource(id, target))
                        ackedUris.add(uri(target));
                }
                acked.at(uri("resourceSubscriptions"), lst(ackedUris), MUTABLE);
            }
            // echo the list-changed booleans the server agrees to honor (we accept them
            // but do not yet emit list_changed notifications — see the deferral note).
            for (final String flag : List.of("toolsListChanged", "promptsListChanged", "resourcesListChanged")) {
                final Obj v = notif.at(uri(flag));
                if (!v.isNoObj()) acked.at(uri(flag), v, MUTABLE);
            }
        }
        return mcpResponse(id, rec(uri("notifications"), acked));
    }

    /**
     * Register a {@code ?subq} subscription for {@code target}. The sub's code fires
     * with {@code lhs = pub::T = lst([changed_uri, new_obj])} whenever {@code target}
     * mutates, and captures a {@code notifications/resources/updated} rec to the
     * server's subscription outbox. Returns false (and skips registration) when the
     * target is not backed by any registered space.
     */
    private boolean subscribeResource(final Obj id, final fURI target) {
        if (!Router.global().hasSpaceFor(target)) {
            LOG.warn("no space for resource subscription target %s — skipping", target);
            return false;
        }
        final fURI outbox = this.subscriptionOutbox(id);
        Router.global().write(target.addQ(SUBQ), rec(mutableMap(
                uri(TARGET), uri(target),
                uri(CODE), instC(f("mcp_resource_updated").dom(LST_TID).rng(NOOBJ_TID.zero()), lst(), (lhs, inst) -> {
                    final Obj changed = lhs.asLst().at(0);
                    final Obj value = lhs.asLst().at(1);
                    Router.global().write(outbox, rec(mutableMap(
                            uri(METHOD), uri("notifications/resources/updated"),
                            uri("params"), rec(uri(URI), changed, uri(VALUE), value),
                            uri("subscriptionId"), id)));
                    return noobj();
                })), SUBQ_SUB_TID, null));
        return true;
    }

    /**
     * The location where fired subscription notifications are captured, keyed by the
     * {@code subscriptions/listen} request id so concurrent subscriptions don't collide.
     * Falls back to a fixed region when the server has no vid (bare test servers).
     */
    private fURI subscriptionOutbox(final Obj id) {
        final fURI base = null == this.vid()
                ? f("/m/web/mcp/subscriptions")
                : this.vid().extend("subscriptions");
        return base.extend(id.toCleanString()).addQ(INCRQ);
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
        final Rec base;
        if (element instanceof JsonObjectSchema obj) {
            final Rec properties = rec();
            obj.properties().forEach((name, sub) -> properties.at(uri(name), jsonSchemaToRec(sub), Rec.MUTABLE));
            base = rec(uri(TYPE), str(OBJECT),
                    uri("properties"), properties,
                    uri(REQUIRED), lst(obj.required().stream().map(s -> (Obj) str(s)).toList()));
        } else if (element instanceof JsonArraySchema arr) {
            base = rec(uri(TYPE), str("array"), uri("items"), jsonSchemaToRec(arr.items()));
        } else if (element instanceof JsonBooleanSchema) {
            base = rec(uri(TYPE), str("boolean"));
        } else if (element instanceof JsonIntegerSchema) {
            base = rec(uri(TYPE), str("integer"));
        } else if (element instanceof JsonNumberSchema) {
            base = rec(uri(TYPE), str("number"));
        } else if (element instanceof JsonStringSchema) {
            base = rec(uri(TYPE), str("string"));
        } else if (element instanceof JsonEnumSchema en) {
            base = rec(uri(TYPE), str("string"), uri("enum"), lst(en.enumValues().stream().map(s -> (Obj) str(s)).toList()));
        } else if (element instanceof JsonReferenceSchema ref) {
            base = rec(uri("$ref"), str(ref.reference()));
        } else if (element instanceof JsonAnyOfSchema anyOf) {
            base = rec(uri("anyOf"), lst(anyOf.anyOf().stream().map(e -> (Obj) jsonSchemaToRec(e)).toList()));
        } else {
            base = rec(uri(TYPE), str("string"));
        }
        final String description = element.description();
        if (null != description && !description.isBlank())
            base.at(uri(DESCRIPTION), str(description), Rec.MUTABLE);
        return base;
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
