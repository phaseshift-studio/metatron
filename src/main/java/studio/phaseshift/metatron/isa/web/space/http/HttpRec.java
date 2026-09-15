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

package studio.phaseshift.metatron.isa.web.space.http;

import com.sun.net.httpserver.HttpExchange;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.ui.console.StatusLine;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.util.MTronException;

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
    protected final ObjJSONSerializer JSON = ObjJSONSerializer.simple();

    // The current request's exchange — thread-local, not a shared instance field:
    // HttpRec instances are cached and SHARED across requests (see
    // httpSpace.createHandlerRoute's session cache) while dispatch happens on a
    // thread pool. A plain field gets clobbered when two requests land on the
    // same handler, cross-wiring responses: "headers already sent",
    // "stream closed", "insufficient bytes written", and orphaned sockets.
    private final ThreadLocal<HttpExchange> EXCHANGE = new ThreadLocal<>();

    /**
     * The current request's exchange for this thread; null during type-checking.
     */
    protected HttpExchange exchange() {
        return EXCHANGE.get();
    }

    /**
     * Bind the current request's exchange for the handling thread.
     */
    protected void exchange(final HttpExchange ex) {
        EXCHANGE.set(ex);
    }

    // The address this request is served from, when its mount resolved one outright — thread-local for the
    // same reason as EXCHANGE, and *only* the address travels this way: a templated mount resolves a different
    // address per request, so an address can never be baked into a shared handler's config. Null for a mount
    // that names a prefix root (then the handler still computes root + mount-relative path itself).
    private final ThreadLocal<fURI> ADDRESS = new ThreadLocal<>();

    /**
     * The address this request resolves to, or null when the handler must derive it from its own config.
     */
    protected fURI address() {
        return ADDRESS.get();
    }

    // The SSE stream this thread's request opened, if any. A streamed response owns the
    // exchange for its lifetime (it holds the chunked body open), so handle()'s finally must
    // not close the exchange out from under it — the stream closes itself.
    private final ThreadLocal<SseStream> STREAM = new ThreadLocal<>();

    /**
     * Handle a request whose mount already resolved its address — see {@link #address()}.
     */
    public void handle(final HttpExchange exchange, final fURI address) throws IOException {
        ADDRESS.set(address);
        try {
            this.handle(exchange);
        } finally {
            ADDRESS.remove();
        }
    }

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
        this.exchange(exchange);
        LOG.debug("handling %s %s [handler=%s]", exchange.getRequestMethod(),
                exchange.getRequestURI(), this.vidOrTid());
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
        } finally {
            // Whatever happened, this request must be *completed*. An error path that logs without writing a
            // response leaves the exchange open and the client waiting for its own timeout — observed as zero
            // bytes for 8s on a request whose uri failed to parse. Closing is a no-op after a response has been
            // written (every send closes its body stream) and is the difference between a fast failure and a hang.
            // An SSE stream owns the exchange for its lifetime; if the handler returned without ending it, close it
            // here so the client is never left waiting.
            final SseStream stream = STREAM.get();
            if (null != stream) {
                if (!stream.isClosed())
                    stream.close();
                STREAM.remove();
            }
            try {
                exchange.close();
            } catch (final Exception e) {
                LOG.debug("error closing exchange: %s", e.getMessage());
            }
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
            } catch (final IOException ex) {
                // never silent again: this is the path that used to leave a client waiting forever, and the only
                // trace of it was the log line above (handle's finally now closes the exchange either way)
                LOG.error("unable to send error response for %s: %s", exchange.getRequestURI(), ex.getMessage());
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
        // The mount's resolved address, when it resolved one outright (a templated route value): present means
        // "this is the address, nothing is appended to it" — the mount already consumed the request path.
        final fURI routed = this.address();
        if (null != routed)
            map.put(uri(WEB_ROOT), uri(routed));
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
        if (this.exchange() == null)  // type-checking guard
            return;
        final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        this.exchange().getResponseHeaders().set("Content-Type", "application/json");
        this.exchange().sendResponseHeaders(status, bytes.length);
        try (final OutputStream os = this.exchange().getResponseBody()) {
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
        if (this.exchange() == null) {
            // exchange is null during type-checking of the isaPredicate
            // (rhs.test(cinst.rng()) evaluates insts to verify result types)
            return;
        }
        try {
            final HttpIO io = getHttpIO();
            final byte[] bytes = io.output().serializer().outputBytes(message).array();
            this.exchange().getResponseHeaders().set(MIME.MIMEType.VALUE, io.output().value);
            this.exchange().sendResponseHeaders(200, bytes.length);
            try (final OutputStream os = this.exchange().getResponseBody()) {
                os.write(bytes);
            }
            StatusLine.message(str(message.toCleanString()));
        } catch (final Exception e) {
            LOG.status(ERROR, "error sending response: %s", e.getMessage());
            try {
                if (this.exchange() != null)
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
        if (this.exchange() == null) {
            // exchange is null during type-checking of the isaPredicate
            return;
        }
        try {
            final byte[] bytes = contentType.toBytes(message);
            this.exchange().getResponseHeaders().set(MIME.MIMEType.VALUE, contentType.value);
            // Revalidate before reuse. Responses carry no validator (no ETag, no Last-Modified), so a client that
            // cached a bad copy has nothing to check it against and can hold it indefinitely — which is how a
            // corrupted image stayed in a browser after the read that produced it was fixed. "no-cache" does not
            // forbid caching, it forbids reuse without asking, so assets stay fast and stale ones self-heal.
            this.exchange().getResponseHeaders().set("Cache-Control", "no-cache");
            this.exchange().sendResponseHeaders(200, bytes.length);
            try (final OutputStream os = this.exchange().getResponseBody()) {
                os.write(bytes);
            }
        } catch (final Exception e) {
            LOG.error("error sending response: %s", e.getMessage());
            try {
                if (this.exchange() != null)
                    sendError(500, "error sending response: " + e.getMessage());
            } catch (final IOException ignored) {
            }
        }
    }

    /**
     * Open a server-sent-events response on the current request. Sets the
     * {@code text/event-stream} content type and starts a chunked body (length 0 ⇒
     * unknown), returning the channel that owns it until {@link SseStream#close()}.
     * The handler must block until it closes the stream — the response completes
     * only when the stream ends.
     */
    public SseStream openSse() throws IOException {
        final HttpExchange ex = this.exchange();
        if (null == ex)
            throw MTronException.of("openSse requires an active http exchange");
        ex.getResponseHeaders().set(MIME.MIMEType.VALUE, MIME.MIMEType.TEXT_EVENT_STREAM.value);
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, 0);
        final SseStream stream = new SseStream(ex.getResponseBody());
        STREAM.set(stream);
        return stream;
    }

    /**
     * Close the underlying HttpExchange.
     */
    public void close() {
        final HttpExchange ex = this.exchange();
        if (ex != null) {
            try {
                ex.close();
            } catch (final Exception e) {
                LOG.error("error closing exchange: %s", e.getMessage());
            }
        }
        // drop the thread-local binding — the shared pool thread may next serve
        // a request for a different session; a stale exchange would leak it
        EXCHANGE.remove();
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
