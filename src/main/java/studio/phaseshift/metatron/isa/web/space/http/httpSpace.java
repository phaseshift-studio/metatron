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

import com.google.gson.JsonElement;
import com.sun.net.httpserver.HttpServer;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.http.handler.web_httpHandler.WEB_HTTP_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.CONTENT_TYPE;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class httpSpace extends AbstractSpace<HttpServer> {

    public static final fURI HTTP_SPACE_TID = WEB_ISA_TID.extend("space/httpspace");
    public static final fURI HTTP_SOCKET_TID = HTTP_SPACE_TID.extend("socket");
    public static final fURI HTTP_HANDLER_TID = HTTP_SPACE_TID.extend("http_handler");
    public static final fURI HTTP_CLIENT_TID = HTTP_SPACE_TID.extend("http_client");

    public static final Rec CONFIG = rec(uri(PATTERN), T(URI_TID), uri(HOST), T(URI_TID), uri(ROUTE), T(REC_TID));
    public static final Type HTTP_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(HTTP_SPACE_TID)
            .constructor(instC(HTTP_SPACE_TID.extend(CTOR).dom(ALL.maybe()).rng(HTTP_SPACE_TID),
                    lst(T(REC_TID, isa_(CONFIG))), (lhs, inst) -> httpSpace.of(inst.arg(0).asRec(), inst.arg(0).vid()))).create();

    private final memSpace cache;
    private static final ObjJSONSerializer JSON_TRANSLATOR = new ObjJSONSerializer();

    public static final Type HTTP_HANDLER_TYPE = Type.Builder.build()
            .tid(HTTP_SOCKET_TID)
            .vid(HTTP_HANDLER_TID)
            .constructor(instC(HTTP_HANDLER_TID.extend(CTOR).dom(ALL.maybe()).rng(HTTP_SOCKET_TID),
                    lst(T(REC_TID)), (lhs, inst) ->
                            new HttpRec(inst.arg(0).asRec().jvm(), inst.arg(0).tid(), inst.arg(0).vid()))).create();

    public static final Type HTTP_SOCKET_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(HTTP_SOCKET_TID)
            .isaPredicate(rec(
                    uri(IN).maybe().asUri(), isa_(CONTENT_TYPE).orElse(uri(MIME.MIMEType.APPLICATION_MTRON.value)),
                    uri(OUT).maybe().asUri(), isa_(CONTENT_TYPE).orElse(uri(MIME.MIMEType.APPLICATION_MTRON.value)),
                    uri(SEND).maybe().asUri(), INST_TYPE,
                    uri(ON_GET).maybe(), T(ALL),
                    uri(ON_POST).maybe(), T(ALL),
                    uri(ON_PUT).maybe(), T(ALL),
                    uri(ON_DELETE).maybe(), T(ALL),
                    uri(ON_PATCH).maybe(), T(ALL),
                    uri(ON_HEAD).maybe(), T(ALL),
                    uri(ON_OPTIONS).maybe(), T(ALL),
                    uri(ON_ERROR).maybe(), T(ALL),
                    uri(ON_CLOSE).maybe(), T(ALL))).create();

    public static final Type HTTP_CLIENT_TYPE = Type.Builder.build()
            .tid(HTTP_SOCKET_TID)
            .vid(HTTP_CLIENT_TID)
            .constructor(instC(HTTP_CLIENT_TID.extend(CTOR).dom(ALL.maybe()).rng(HTTP_SOCKET_TID),
                    lst(T(REC_TID)), (lhs, inst) -> {
                        throw MTronException.of("http client not implemented");
                    })).create();


    protected httpSpace(final HttpServer server, final Map<Obj, Obj> config, final fURI vid) {
        super(server, config, HTTP_SPACE_TID, vid);
        this.cache = memSpace.of(rec(uri(PATTERN), config.getOrDefault(uri(PATTERN), noobj())), null);
        try {
            this.at(ROUTE).orElse(rec0()).elements().forEach(r -> {
                final boolean hostRoute = r.first().uriValue().toString().startsWith(this.at(HOST).uriValue().toString());
                final fURI left = hostRoute ? f(r.first().uriValue().toString().replaceFirst(this.at(HOST).uriValue().toString(), "")) : r.first().uriValue();
                if (!hostRoute && r.first().uriValue().hasHost())
                    return;
                LOG.info("processing http route: %s => %s => %s", r.first().uriValue().toString(), r.second().uriValue().toString(), left.toString());

                // ── All routes go through handler type construction ──
                final fURI targetVID = r.second().uriValue();
                if (!targetVID.toString().isEmpty()) {
                    final Obj targetObj = Router.global().read(targetVID);
                    if (targetObj.isType()) {
                        // Type route: construct handler via type system (MCP, mtron, web_http, etc.)
                        LOG.info("handling as handler route: %s => %s", left, targetVID);
                        createHandlerRoute(server, left, targetVID);
                    } else {
                        // Non-type route: treat as web root — auto-create a web_httpHandler
                        LOG.info("handling as web route: %s => %s (WEB_ROOT=%s)", left, WEB_HTTP_TID, targetVID);
                        createWebHandlerRoute(server, left, targetVID);
                    }
                }
            });
            LOG.info("starting web server at %s", this.at(HOST).uriValue().scheme(HTTP).toUri());
            server.setExecutor(BootLoader.getExecutor());
            Runtime.getRuntime().addShutdownHook(new Thread(this::close));
            LOG.info("available routes: %s", this.at(ROUTE));
            server.start();
        } catch (final Exception e) {
            LOG.error(MTronException.of(e));
            LOG.warn("%s server not started: %s", this, e.getMessage());
        }
    }

    // ──────────────────────────────────────────────
    // Route creation — all routes create handler instances via the type system
    // ──────────────────────────────────────────────

    /**
     * Create a handler route for a Type-based route target.
     * Constructs the handler via {@code rec(map, typeVID, sessionVid)} which invokes
     * the type's constructor. Sessions are cached in the local memSpace.
     */
    private void createHandlerRoute(final HttpServer server, final fURI path, final fURI typeVID) {
        createHandlerRoute(server, path, typeVID, mutableMap());
    }

    /**
     * Create a handler route with additional handler config keys (e.g. WEB_ROOT).
     */
    private void createHandlerRoute(final HttpServer server, final fURI path, final fURI typeVID,
                                    final Map<Obj, Obj> handlerConfig) {
        server.createContext(path.toString(), exchange -> {
            final String sid = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
            final fURI sessionVid = this.vid().extend(path.name()).extend(sid != null ? sid : "default");
            try {
                Obj handler = cache.read(sessionVid);
                if (handler.isNoObj()) {
                    final Map<Obj, Obj> config = mutableMap(
                            uri(IN), uri(MIME.MIMEType.APPLICATION_JSON.value),
                            uri(OUT), uri(MIME.MIMEType.APPLICATION_JSON.value)
                    );
                    config.putAll(handlerConfig);
                    handler = rec(config, typeVID, sessionVid);
                    cache.write(sessionVid, handler);
                }
                if (handler instanceof HttpRec hr) {
                    hr.handle(exchange);
                } else {
                    LOG.error("handler at %s is not an HttpRec: %s", sessionVid, handler.getClass().getName());
                    exchange.sendResponseHeaders(500, 0);
                    exchange.close();
                }
            } catch (final IOException e) {
                throw e;
            } catch (final Exception e) {
                LOG.error("error in handler route %s: %s", sessionVid, e.getMessage());
                try {
                    exchange.sendResponseHeaders(500, 0);
                    exchange.close();
                } catch (final IOException ignored) {}
            }
        });
    }

    /**
     * Create a web handler route: the target value is treated as the WEB_ROOT
     * and a {@link web_httpHandler} is constructed to serve content from it.
     */
    private void createWebHandlerRoute(final HttpServer server, final fURI path, final fURI webRoot) {
        createHandlerRoute(server, path, WEB_HTTP_TID, mutableMap(uri(WEB_ROOT), uri(webRoot)));
    }

    // ──────────────────────────────────────────────
    // Factory, lifecycle
    // ──────────────────────────────────────────────

    public static httpSpace of(final Rec config, final fURI vid) {
        try {
            final HttpServer server = HttpServer.create(
                    new InetSocketAddress(config.at(HOST).uriValue().host(),
                            config.at(HOST).uriValue().port()), 0);
            server.setExecutor(BootLoader.getExecutor());
            return new httpSpace(server, config.jvm(), vid);
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public void close() {
        this.sjvm().stop(0);
        super.close();
    }

    @Override
    public httpSpace tid(final fURI tid) {
        return (httpSpace) super.tid(tid);
    }

    // ──────────────────────────────────────────────
    // Router I/O — local routes first, then remote web
    // ──────────────────────────────────────────────

    /**
     * Read from the http:// address space.
     * First tries the local route table; if no match, fetches from the remote web via Jsoup.
     * Supports nested path resolution: a 404 walks up the path to find a containing resource,
     * then navigates into it (e.g. {@code http://host/page/section} → fetch {@code /page}
     * and extract {@code section} from the result).
     */
    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        return (pattern) -> {
            // 1 — Try local route table first
            try {
                final fURI route = Space.Helper.routeFromSpace(pattern.scheme(null).host(null), this.routes());
                if (route != null && !route.toString().isEmpty()) {
                    final Iterator<IdObj> local = this.cache.directReader().apply(route);
                    if (local.hasNext())
                        return local;
                }
            } catch (final Exception ignored) {}

            // 2 — Remote web fetch via Jsoup
            try {
                fURI runningPattern = pattern;
                int steps = 0;
                while (true) {
                    final Connection.Response response = Jsoup.connect(runningPattern.toString())
                            .ignoreContentType(true).ignoreHttpErrors(true).execute();
                    if (response.statusCode() == 404) {
                        if (runningPattern.segmentLength() == 0)
                            return IteratorUtil.of();
                        steps++;
                        runningPattern = runningPattern.asRelativeNode().retract(1).asAbsolute();
                    } else {
                        // Use file extension as a hint when the server sends text/plain
                        // (e.g. .json files served without application/json content type)
                        MIME.MIMEType contentType = MIME.MIMEType.of(response.contentType());
                        if (null == contentType || contentType == MIME.MIMEType.TEXT_PLAIN) {
                            contentType = MIME.MIMEType.fromExtension(runningPattern.name(), contentType);
                        }
                        final Obj docObj = null != contentType ? contentType.fromBytes(response.body()) : str(response.body());
                        final Uri key = uri(pattern.scheme(null).host(null).tail(steps).asRelative());
                        if (key.uriValue().toString().trim().isEmpty())
                            return docObj.isNoObj() ? IteratorUtil.of() : IteratorUtil.of(IdObj.of(pattern, docObj));
                        if (docObj.isRec()) {
                            final Obj subDocObj = docObj.asRec().at(key);
                            return subDocObj.isNoObj() ? IteratorUtil.of() : IteratorUtil.of(IdObj.of(pattern, subDocObj));
                        }
                        return IteratorUtil.of();
                    }
                }
            } catch (final Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("no bytes"))
                    return IteratorUtil.of();
                throw MTronException.of(e);
            }
        };
    }

    /**
     * Write to the http:// address space.
     * If the URI matches this space's pattern, routes to a local space.
     * Otherwise POSTs to the remote server via HttpClient.
     */
    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (pattern, obj) -> {
            // 1 — Try local route
            if (pattern.test(this.pattern)) {
                final fURI location = Space.Helper.routeFromSpace(pattern.scheme(null).host(null), this.routes());
                if (location != null && !location.toString().isEmpty()) {
                    return Router.global().write(location, obj);
                }
            }

            // 2 — Remote POST via HttpClient
            try {
                final JsonElement json = JSON_TRANSLATOR.write(obj);
                final HttpRequest request = HttpRequest.newBuilder()
                        .header(MIME.MIMEType.VALUE, MIME.MIMEType.APPLICATION_JSON.value)
                        .uri(java.net.URI.create(pattern.toString()))
                        .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                        .build();
                final HttpResponse<byte[]> response;
                try (final HttpClient client = HttpClient.newHttpClient()) {
                    response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                }
                final Optional<String> contentType = response.headers().firstValue(MIME.MIMEType.VALUE);
                if (contentType.isPresent())
                    return MIME.MIMEType.of(contentType.get()).fromBytes(response.body());
                return jnt(response.statusCode());
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        };
    }
}
