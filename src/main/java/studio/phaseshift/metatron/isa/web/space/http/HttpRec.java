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
import studio.phaseshift.metatron.isa.web.type.MIME;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TID;
import static studio.phaseshift.metatron.isa.m.type.InstSet.A;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.http.httpSpace.HTTP_SPACE_TID;

/**
 * Base class for HTTP-based metatron objects — the HTTP analog of {@code WebSocketRec}.
 * <p>
 * Each {@code HttpRec} handles HTTP requests by delegating to mtron-level handlers
 * stored at keys {@code ON_GET}, {@code ON_POST}, {@code ON_PUT}, {@code ON_DELETE},
 * {@code ON_PATCH}, {@code ON_HEAD}, {@code ON_OPTIONS} in the rec map.
 * Subclasses (like {@code mcp_httpHandler}) may override the {@code doGet}/{@code doPost}
 * etc. methods for custom Java-level behavior.
 * <p>
 * Default {@code SEND} and {@code CLOSE} instC entries are registered in the constructor,
 * mirroring {@code WebSocketRec}'s pattern.
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
        // Default SEND — mirror WebSocketRec
        if (!map.containsKey(uri(SEND)))
            this.jvm().put(uri(SEND), instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(T(A.maybe())), (lhs, inst) -> {
                try {
                    this.send(inst.arg(0));
                    return noobj();
                } catch (final Exception e) {
                    LOG.error("error sending response: %s", e);
                    return fail(e);
                }
            }));
        // Default CLOSE — mirror WebSocketRec
        if (!map.containsKey(uri(CLOSE)))
            this.jvm().put(uri(CLOSE), instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(NOOBJ_TID), lst(), (lhs, inst) -> {
                this.logger().info("closing %s", this.vid());
                this.close();
                return noobj();
            }));
    }

    // ========================================
    // Entry point — dispatches by HTTP method
    // ========================================

    /**
     * Entry point for HTTP request handling. Dispatches by HTTP method to either
     * subclass overrides (OOP) or mtron-level handlers (ON_GET, ON_POST, etc.).
     */
    public void handle(final HttpExchange exchange) throws IOException {
        this.exchange = exchange;
        try {
            switch (exchange.getRequestMethod().toUpperCase()) {
                case "GET" -> doGet(exchange);
                case "POST" -> doPost(exchange);
                case "PUT" -> doPut(exchange);
                case "DELETE" -> doDelete(exchange);
                case "PATCH" -> doPatch(exchange);
                case "HEAD" -> doHead(exchange);
                case "OPTIONS" -> doOptions(exchange);
                default -> sendError(405, "Method Not Allowed");
            }
        } catch (final Exception e) {
            LOG.error("error handling %s %s: %s", exchange.getRequestMethod(), exchange.getRequestURI(),
                    e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            onError(exchange, e);
        }
    }

    // ========================================
    // Default HTTP method handlers — delegate to mtron
    // Subclasses may override for custom Java-level behavior
    // ========================================

    protected void doGet(final HttpExchange exchange) throws IOException {
        dispatchToMtron(ON_GET, exchange);
    }

    protected void doPost(final HttpExchange exchange) throws IOException {
        dispatchToMtron(ON_POST, exchange);
    }

    protected void doPut(final HttpExchange exchange) throws IOException {
        dispatchToMtron(ON_PUT, exchange);
    }

    protected void doDelete(final HttpExchange exchange) throws IOException {
        dispatchToMtron(ON_DELETE, exchange);
    }

    protected void doPatch(final HttpExchange exchange) throws IOException {
        dispatchToMtron(ON_PATCH, exchange);
    }

    protected void doHead(final HttpExchange exchange) throws IOException {
        dispatchToMtron(ON_HEAD, exchange);
    }

    protected void doOptions(final HttpExchange exchange) throws IOException {
        dispatchToMtron(ON_OPTIONS, exchange);
    }

    // ========================================
    // Mtron delegation
    // ========================================

    /**
     * Delegate an HTTP method to its mtron-level handler.
     * The mtron handler is responsible for calling {@link #send(Obj)} to respond.
     */
    protected void dispatchToMtron(final String methodKey, final HttpExchange exchange) throws IOException {
        final Obj handler = this.at(uri(methodKey));
        if (handler.isNoObj() || handler.isFail()) {
            sendError(405, exchange.getRequestMethod() + " not supported");
            return;
        }
        final Obj request = buildRequest(exchange);
        handler.apply(request);
    }

    /**
     * Handle errors via mtron delegation (ON_ERROR key).
     * Falls back to a plain 500 response if no ON_ERROR handler is registered.
     */
    protected void onError(final HttpExchange exchange, final Exception e) {
        LOG.error("error in %s: %s", this.vid(), e.getMessage());
        final Obj handler = this.at(uri(ON_ERROR));
        if (!handler.isNoObj() && !handler.isFail()) {
            try {
                handler.apply(fail(e));
            } catch (final Exception ex) {
                LOG.error("error in on_error handler: %s", ex.getMessage());
                try {
                    sendError(500, "Internal Server Error");
                } catch (final IOException ignored) {
                }
            }
        } else {
            try {
                sendError(500, e.getMessage() == null ? "Internal Server Error" : e.getMessage());
            } catch (final IOException ignored) {
            }
        }
    }

    /**
     * Build a request rec from the HttpExchange for mtron handler consumption.
     * Includes method, uri, headers, and body (for methods that carry one).
     */
    protected Obj buildRequest(final HttpExchange exchange) throws IOException {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(METHOD), str(exchange.getRequestMethod()));
        map.put(uri(URI), uri(exchange.getRequestURI().toString()));
        // Headers
        final Map<Obj, Obj> headerMap = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((k, v) ->
                headerMap.put(str(k), str(String.join(",", v))));
        map.put(uri(HEADERS), rec(headerMap));
        // Body for methods that carry one (not GET, HEAD, DELETE, OPTIONS)
        final String reqMethod = exchange.getRequestMethod().toUpperCase();
        if (!"GET".equals(reqMethod) && !"HEAD".equals(reqMethod)
                && !"DELETE".equals(reqMethod) && !"OPTIONS".equals(reqMethod)) {
            final String bodyStr = readBody(exchange);
            if (!bodyStr.isEmpty()) {
                final HttpIO io = getHttpIO();
                try {
                    map.put(uri(BODY), io.input().serializer().inputBytes(ByteBuffer.wrap(bodyStr.getBytes(StandardCharsets.UTF_8))));
                } catch (final Exception e) {
                    map.put(uri(BODY), str(bodyStr));
                }
            }
        }
        return rec(map);
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

    /**
     * Send a response via the current HttpExchange.
     * Serializes the message using the output ContentType from {@link #getHttpIO()}.
     * This is the HTTP analog of {@code WebSocketObj.send(Obj)}.
     */
    public void send(final Obj message) {
        if (this.exchange == null) {
            LOG.error("no exchange available to send response");
            return;
        }
        try {
            final HttpIO io = getHttpIO();
            final byte[] bytes = io.output().serializer().outputBytes(message).array();
            this.exchange.getResponseHeaders().set(MIME.MIMEType.VALUE, io.output().value);
            this.exchange.sendResponseHeaders(200, bytes.length);
            try (final OutputStream os = this.exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (final Exception e) {
            LOG.error("error sending response: %s", e.getMessage());
            try {
                if (this.exchange != null)
                    sendError(500, "error sending response: " + e.getMessage());
            } catch (final IOException ignored) {
            }
        }
    }

    /**
     * Send a response with an explicit content type.
     * This is used by web_httpHandler to serve files with dynamic content types
     * (text/html, text/css, application/json, etc.) rather than the configured OUT type.
     */
    public void send(final Obj message, final MIME.MIMEType contentType) {
        if (this.exchange == null) {
            LOG.error("no exchange available to send response");
            return;
        }
        try {
            final byte[] bytes = contentType.toBytes(message);
            this.exchange.getResponseHeaders().set(MIME.MIMEType.VALUE, contentType.value);
            this.exchange.sendResponseHeaders(200, bytes.length);
            try (final OutputStream os = this.exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (final Exception e) {
            LOG.error("error sending response: %s", e.getMessage());
            try {
                if (this.exchange != null)
                    sendError(500, "error sending response: " + e.getMessage());
            } catch (final IOException ignored) {
            }
        }
    }

    /**
     * Close the underlying HttpExchange.
     */
    public void close() {
        if (this.exchange != null) {
            try {
                this.exchange.close();
            } catch (final Exception e) {
                LOG.error("error closing exchange: %s", e.getMessage());
            }
        }
    }

    // ========================================
    // Serialization config
    // ========================================

    /**
     * Returns the serialization IO config (JSON in/out by default).
     */
    public record HttpIO(MIME.MIMEType input, MIME.MIMEType output) {
        public static HttpIO of(final Rec obj) {
            return new HttpIO(
                    MIME.MIMEType.of(obj.at(uri(IN)).orElse(uri(MIME.MIMEType.APPLICATION_JSON.value)).uriValue().toString()),
                    MIME.MIMEType.of(obj.at(uri(OUT)).orElse(uri(MIME.MIMEType.APPLICATION_JSON.value)).uriValue().toString()));
        }
    }

    public HttpIO getHttpIO() {
        return HttpIO.of(this);
    }

    @Override
    public HttpRec clone() {
        return this;
    }
}
