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
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.isa.web.type.Content;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.http.httpSpace.HTTP_SPACE_TID;

/**
 * Base class for HTTP-based metatron objects — the HTTP analog of {@code WebSocketRec}.
 * <p>
 * Each {@code HttpRec} handles a single route in an {@link httpSpace}. Subclasses
 * override {@code doGet}, {@code doPost}, {@code doDelete} to implement specific
 * HTTP behavior. Session multiplexing is the subclass's responsibility.
 * <p>
 * The metatron type system constructs instances via the Type constructor when
 * a handler route is matched in the httpSpace route table.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class HttpRec extends MRec {

    public static final fURI HTTP_REC_TID = HTTP_SPACE_TID.extend("httprec");
    protected final GraphittyLogger LOG = Graphitty.log(this);
    protected final ObjJSONSerializer JSON = new ObjJSONSerializer();

    protected HttpExchange exchange;

    public HttpRec(final Map<Obj, Obj> map, final fURI tid, final fURI vid) {
        super(map, tid, vid);
    }

    /**
     * Entry point for HTTP request handling. Dispatches by HTTP method.
     */
    public void handle(final HttpExchange exchange) throws IOException {
        this.exchange = exchange;
        try {
            switch (exchange.getRequestMethod().toUpperCase()) {
                case "GET"    -> doGet(exchange);
                case "POST"   -> doPost(exchange);
                case "DELETE" -> doDelete(exchange);
                default -> sendError(405, "Method Not Allowed");
            }
        } catch (final Exception e) {
            LOG.error("error handling %s %s: %s", exchange.getRequestMethod(), exchange.getRequestURI(),
                    e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            try {
                sendError(500, "Internal Server Error");
            } catch (final IOException ignored) {
                // best effort
            }
        }
    }

    // ========================================
    // Subclass overrides
    // ========================================

    protected void doGet(final HttpExchange exchange) throws IOException {
        sendError(405, "GET not supported");
    }

    protected void doPost(final HttpExchange exchange) throws IOException {
        sendError(405, "POST not supported");
    }

    protected void doDelete(final HttpExchange exchange) throws IOException {
        sendError(405, "DELETE not supported");
    }

    // ========================================
    // I/O utilities for subclasses
    // ========================================

    /**
     * Read the request body as a UTF-8 string.
     */
    protected String readBody(final HttpExchange exchange) throws IOException {
        try (final InputStream is = exchange.getRequestBody();
             final BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().reduce("", (a, b) -> a + b);
        }
    }

    /**
     * Send a JSON response (serialized from a metatron Obj via ObjJSONSerializer).
     */
    protected void sendJson(final int status, final Obj obj) throws IOException {
        final String json = JSON.write(obj).toString();
        sendJsonString(status, json);
    }

    /**
     * Send a raw JSON string response.
     */
    protected void sendJsonString(final int status, final String json) throws IOException {
        final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (final OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Send a JSON error response.
     */
    protected void sendError(final int status, final String message) throws IOException {
        sendJsonString(status, "{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
    }

    // ========================================
    // Serialization config
    // ========================================

    /**
     * Returns the serialization IO config (JSON in/out by default).
     */
    public record HttpIO(Content.ContentType input, Content.ContentType output) {
        public static HttpIO of(final Rec obj) {
            return new HttpIO(
                    Content.ContentType.of(obj.at(uri(IN)).orElse(uri(Content.ContentType.APPLICATION_JSON.value)).uriValue().toString()),
                    Content.ContentType.of(obj.at(uri(OUT)).orElse(uri(Content.ContentType.APPLICATION_JSON.value)).uriValue().toString()));
        }
    }

    public HttpIO getHttpIO() {
        return HttpIO.of(this);
    }
}
