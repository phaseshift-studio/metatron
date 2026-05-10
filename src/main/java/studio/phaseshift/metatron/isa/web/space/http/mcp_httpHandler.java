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

import com.sun.net.httpserver.HttpExchange;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.web.type.mcp_Server;
import studio.phaseshift.metatron.isa.web.type.Content;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * Streamable HTTP MCP transport handler. Composes a {@link mcp_Server} for JSON-RPC
 * protocol dispatch and implements the MCP Streamable HTTP transport over
 * {@link com.sun.net.httpserver.HttpServer}.
 * <p>
 * Session management via {@code Mcp-Session-Id} header — a new session is created
 * on the first {@code initialize} request and used for subsequent requests.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mcp_httpHandler extends HttpRec {

    public static final fURI MCP_HTTP_TID = HTTP_SPACE_TID.extend("mcp_http");

    public static final Type MCP_HTTP_TYPE = Type.Builder.build()
            .tid(HTTP_REC_TID)
            .vid(MCP_HTTP_TID)
            .isaPredicate(rec(
                    uri(TOOL).maybe().asUri(), rec(URI_TYPE, INST_TYPE).maybe(),
                    uri(RESOURCE).maybe().asUri(), T(ALL),
                    uri(PROMPT).maybe().asUri(), T(ALL)))
            .constructor(instC(MCP_HTTP_TID.extend(CTOR).dom(ALL.maybe()).rng(MCP_HTTP_TID),
                    lst(T(REC_TID)),
                    (lhs, inst) -> new mcp_httpHandler(
                            new LinkedHashMap<>(inst.arg(0).asRec().jvm()),
                            MCP_HTTP_TID, inst.arg(0).vid())))
            .create();

    // Transport-agnostic protocol handler (composition)
    private final mcp_Server mcp;

    // Session registry: sessionId → session metadata
    private final Map<String, fURI> sessions = new ConcurrentHashMap<>();
    private String sessionId;

    public mcp_httpHandler(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.mcp = new mcp_Server(jvm, tid, vid);
    }

    // ========================================
    // Streamable HTTP: POST (JSON-RPC)
    // ========================================

    @Override
    protected void doPost(final HttpExchange exchange) throws IOException {
        final String body = readBody(exchange);
        LOG.info("mcp http POST %s: %s", exchange.getRequestURI(), body.length() > 200 ? body.substring(0, 200) + "..." : body);

        // Parse incoming JSON-RPC
        final Obj request;
        try {
            request = Content.ContentType.APPLICATION_JSON.fromBytes(body);
        } catch (final Exception e) {
            LOG.warn("failed to parse JSON-RPC body: %s", e.getMessage());
            sendError(400, "Invalid JSON-RPC body");
            return;
        }

        if (!request.isRec()) {
            sendError(400, "JSON-RPC request must be an object");
            return;
        }

        // Check for existing session
        this.sessionId = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");

        // Detect initialize to create a new session
        final Rec json = request.asRec();
        final String method = json.at(uri("method")).isNoObj() ? "" : json.at(uri("method")).uriValue().toString();
        if ("initialize".equals(method)) {
            this.sessionId = java.util.UUID.randomUUID().toString();
            sessions.put(this.sessionId, this.vid());
            LOG.info("created mcp session: %s", this.sessionId);
        }

        // Dispatch to protocol handler
        final Obj result = this.mcp.handleMessage(request);

        if (result.isNoObj()) {
            // Notification — 202 Accepted, no body
            exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId != null ? sessionId : "default");
            exchange.sendResponseHeaders(202, -1);
        } else {
            // Response
            final String jsonStr = JSON.write(result).toString();
            final byte[] bytes = jsonStr.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (sessionId != null) {
                exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId);
            }
            exchange.sendResponseHeaders(200, bytes.length);
            try (final OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // ========================================
    // Streamable HTTP: GET (SSE stub)
    // ========================================

    @Override
    protected void doGet(final HttpExchange exchange) throws IOException {
        // SSE streaming for server→client notifications (not yet needed —
        // notifications are handled at the mtron level per user confirmation)
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        sendError(501, "SSE streaming not yet implemented");
    }

    // ========================================
    // Streamable HTTP: DELETE (session teardown)
    // ========================================

    @Override
    protected void doDelete(final HttpExchange exchange) throws IOException {
        this.sessionId = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
        if (this.sessionId != null) {
            sessions.remove(this.sessionId);
            LOG.info("removed mcp session: %s", this.sessionId);
        }
        exchange.sendResponseHeaders(204, -1);
    }
}
