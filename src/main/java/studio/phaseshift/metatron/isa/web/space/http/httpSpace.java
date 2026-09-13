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
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.sys.type.ThreadExecutor;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.isa.web.type.mcpServer;
import studio.phaseshift.metatron.isa.web.webHelper;
import studio.phaseshift.metatron.isa.web.webInstSet;
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
import static studio.phaseshift.metatron.furi.q.QCollection.MIMEQ_PATTERN;
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
import static studio.phaseshift.metatron.isa.web.space.http.handler.mcp_httpHandler.HTTP_MCP_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.space.http.handler.web_httpHandler.WEB_HTTP_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.MCP_SERVER_TYPE;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class httpSpace extends AbstractSpace<HttpServer> {

    public static final fURI HTTP_SPACE_TID = WEB_ISA_TID.extend("space").extend("httpspace");
    public static final fURI HTTP_SOCKET_TID = WEB_ISA_TID.extend("http").extend("http_socket");
    public static final fURI HTTP_HANDLER_TID = WEB_ISA_TID.extend("http").extend("http_handler");
    public static final fURI HTTP_CLIENT_TID = WEB_ISA_TID.extend("http").extend("http_client");

    public static final Rec CONFIG = rec(uri(PATTERN), T(URI_TID), uri(HOST), T(URI_TID), uri(ROUTE), T(REC_TID));
    public static final Type HTTP_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(HTTP_SPACE_TID)
            .constructor(instC(HTTP_SPACE_TID.extend(CTOR).dom(ALL.maybe()).rng(HTTP_SPACE_TID),
                    lst(T(REC_TID, isa_(CONFIG))), (lhs, inst) -> httpSpace.of(inst.arg(0).asRec(), inst.arg(0).vid()))).create();

    private final memSpace cache;
    private static final ObjJSONSerializer JSON_TRANSLATOR = ObjJSONSerializer.simple();

    public static final Type HTTP_HANDLER_TYPE = Type.Builder.build()
            .tid(HTTP_SOCKET_TID)
            .vid(HTTP_HANDLER_TID)
            .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(HTTP_SOCKET_TID),
                    lst(T(REC_TID)), (lhs, inst) ->
                            new HttpRec(inst.arg(0).asRec().jvm(), inst.arg(0).tid(), inst.arg(0).vid()))).create();

    public static final Type HTTP_SOCKET_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(HTTP_SOCKET_TID)
            .isaPredicate(rec(
                    uri(IN).maybe().asUri(), isa_(webInstSet.MIME_OBJ_TYPE).orElse(uri(MIME.MIMEType.APPLICATION_MTRON.value)),
                    uri(OUT).maybe().asUri(), isa_(webInstSet.MIME_OBJ_TYPE).orElse(uri(MIME.MIMEType.APPLICATION_MTRON.value)),
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
            this.validateRoutes();
            this.at(ROUTE).orElse(rec0()).elements().forEach(r -> {
                final boolean hostRoute = r.first().uriValue().toString().startsWith(this.at(HOST).uriValue().toString());
                final fURI left = hostRoute ? f(r.first().uriValue().toString().replaceFirst(this.at(HOST).uriValue().toString(), "")) : r.first().uriValue();
                if (!hostRoute && r.first().uriValue().hasHost())
                    return;
                // the value is *rendered*, not uri-converted: a value that cannot convert must not be able to
                // take the space down before it has served anything (see resolveRoute)
                final Obj routeValue = r.second();
                try {
                    // A templated value has no boot-time target at all — its expressions need a request to bind
                    // to — so it is resolved per request. Every other value is resolved once, here, exactly as
                    // before: a code route value which materializes its target must not re-materialize it on
                    // every request.
                    final RouteLane fixed = webHelper.isTemplated(routeValue) ? null : this.resolveRoute(routeValue);
                    if (null == fixed)
                        LOG.info("mounting templated http route: %s => %s (addressed per request)", left.toString(), routeValue.toShortString());
                    else
                        LOG.info("processing http route: %s => %s => %s [%s]", r.first().uriValue().toString(), routeValue.toShortString(), left.toString(), fixed.kind());
                    if (null != fixed && RouteLane.Kind.NONE == fixed.kind())
                        LOG.warn("no lane for route %s => %s", left, routeValue.toShortString());
                    else
                        server.createContext(left.toString(), exchange -> this.handleRequest(exchange, left, routeValue, fixed));
                } catch (final Exception e) {
                    // one bad mount must not stop the space from serving its other mounts
                    LOG.error("unable to mount %s => %s: %s", left, routeValue.toShortString(), e.getMessage());
                }
            });
            LOG.info("starting web server at %s", this.at(HOST).uriValue().scheme(HTTP).toUri());
            server.setExecutor(ThreadExecutor.instance());
            Runtime.getRuntime().addShutdownHook(new Thread(this::close));
            LOG.debug("available routes: %s", this.at(ROUTE));
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
     * Serve one request from a mount: resolve the route for <em>this</em> request, then get-or-create the
     * handler that serves it.
     * <p>
     * The resolution is per request because the request uri is the route value's lhs — that is what makes a
     * templated mount mean "the address this request asks for" ({@code Space.Helper.resolveApply} applies a
     * value with no lhs, so a template would otherwise have nothing to bind to). The handler is emphatically
     * <em>not</em> per address: it is a protocol engine which serves whatever address the request resolves to,
     * so it stays cached per (mount, client, resolved target) and one handler serves every address a mount can
     * produce. That is also why an {@code mcp_server} mount needs no change here at all — it was already
     * address-agnostic, which is how one handler serves http, ws and mcp alike.
     */
    private void handleRequest(final HttpExchange exchange, final fURI mount, final Obj routeValue,
                              final RouteLane fixed) throws IOException {
        final RouteLane lane = null != fixed ? fixed
                : this.resolveRoute(routeValue, f(exchange.getRequestURI().toString()));
        if (RouteLane.Kind.NONE == lane.kind()) {
            LOG.warn("no lane for %s => %s", exchange.getRequestURI(), routeValue.toShortString());
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
            return;
        }
        final String sid = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
        final fURI sessionVid = this.handlerVid(mount, sid, lane);
        try {
            Obj handler = this.cache.read(sessionVid);
            if (handler.isNoObj()) {
                LOG.debug("creating handler: typeVID=%s sessionVid=%s", lane.handlerType(), sessionVid);
                final Map<Obj, Obj> config = mutableMap(
                        uri(IN), uri(MIME.MIMEType.APPLICATION_JSON.value),
                        uri(OUT), uri(MIME.MIMEType.APPLICATION_JSON.value)
                );
                if (null != lane.handlerConfig())
                    config.putAll(lane.handlerConfig());
                // a prefix mount bakes its root; a templated one bakes nothing and supplies the address per
                // request instead (HttpRec.address()), because that address is not knowable here and now
                if (null != lane.webRoot())
                    config.put(uri(WEB_ROOT), uri(lane.webRoot()));
                // Use the type constructor directly — rec()->construct()->big()
                // may redirect to a different URI where the type has no constructor,
                // falling through to a plain MRec (same root cause as the wsSpace
                // ClassCastException fix).
                final Obj type = Router.global().read(lane.handlerType());
                if (type.isType() && type.asType().hasConstructor()) {
                    handler = type.asType().constructor().apply(rec(config)).as();
                    if (!handler.isFail())
                        handler.self(handler.jvm(), lane.handlerType(), sessionVid);
                    else
                        handler = rec(config, lane.handlerType(), sessionVid);
                } else {
                    handler = rec(config, lane.handlerType(), sessionVid);
                }
                this.cache.write(sessionVid, handler);
            }
            if (handler instanceof HttpRec hr) {
                hr.handle(exchange, lane.address());
            } else {
                LOG.error("handler at %s is not an httprec::T: %s", sessionVid, handler.getClass().getName());
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
            } catch (final IOException ignored) {
            }
        }
    }

    /**
     * The handler cache key: (mount, client, resolved target).
     * <p>
     * The resolved <b>address</b> is deliberately absent. A templated mount resolves a different address per
     * request while the handler serving it is the same protocol engine, so keying by address would grow one
     * handler per address ever visited and put per-request data into a long-lived object — the opposite of what
     * a cache is for ({@code docs/design/webspace.md} §8.1.2).
     */
    private fURI handlerVid(final fURI mount, final String sid, final RouteLane lane) {
        return this.vid()
                .extend(segment(mount.toString()))
                .extend(segment(null != sid ? sid : "default"))
                .extend(segment(lane.identity()));
    }

    /**
     * A single cache-key segment: separators and metatron wildcards neutralized.
     * <p>
     * Three reasons, each of which bit. The whole mount path is used rather than {@code mount.name()} because
     * {@code /a/docs} and {@code /b/docs} share a name and would share a handler. Wildcards must go because a
     * mount key may be a pattern ({@code /people/#}), and a {@code #} in a vid turns a cache <em>read</em> into a
     * pattern read that returns an {@code Objs} — which then fails an {@code instanceof HttpRec} check far from
     * the cause. The session id is sanitized because it arrives in a client-controlled header
     * ({@code Mcp-Session-Id}) and must not be able to escape the key or inject a pattern.
     */
    private static String segment(final String value) {
        return value.replace("/", "_").replace("#", "_").replace("+", "_");
    }

    /**
     * Report route-table problems at boot — the shape and signature of each route value — instead of meeting
     * them later as a dead server or a request-time 500. Warning only: a value the ladder cannot resolve is
     * skipped by the mount loop, not fatal to the space.
     */
    private void validateRoutes() {
        final Obj routes = this.at(ROUTE);
        if (routes.isNoObj() || !routes.isRec())
            return;
        final Obj problems = webHelper.routeProblems(routes.asRec());
        if (!problems.isNoObj())
            LOG.warn("%s route table problems: %s", this.vid(), problems);
    }

    // ──────────────────────────────────────────────
    // Route resolution — the ladder, named
    // ──────────────────────────────────────────────

    /**
     * The lane a route value resolves to, with everything the mount needs to honour it.
     *
     * @param kind          which lane the value took
     * @param handlerType   the handler type to construct (HANDLER and, since it is auto-created, WEB)
     * @param handlerConfig extra handler config — a materialized {@code mcp_server}'s state (HANDLER lane)
     * @param identity      what distinguishes two handlers of the same type, i.e. a materialized instance's vid;
     *                      never the request address, see {@link #handlerVid}
     * @param address       the address this request is served from, when the route resolved one outright — a
     *                      templated value has already consumed the request path, so nothing is appended to it
     * @param webRoot       the prefix root a non-templated uri target names, extended by the request's path
     */
    public record RouteLane(Kind kind, fURI handlerType, Map<Obj, Obj> handlerConfig, String identity,
                            fURI address, fURI webRoot) {

        public enum Kind {HANDLER, WEB, NONE}

        static RouteLane handler(final fURI handlerType, final Map<Obj, Obj> handlerConfig, final String identity) {
            return new RouteLane(Kind.HANDLER, handlerType, handlerConfig, identity, null, null);
        }

        /**
         * A templated value resolved this request's address itself — the mount consumed the request path.
         */
        static RouteLane addressed(final fURI address) {
            return new RouteLane(Kind.WEB, WEB_HTTP_TID, null, WEB_HTTP_TID.name(), address, null);
        }

        /**
         * A uri target names a root to serve a whole namespace from.
         */
        static RouteLane web(final fURI webRoot) {
            return new RouteLane(Kind.WEB, WEB_HTTP_TID, null, WEB_HTTP_TID.name(), null, webRoot);
        }

        static RouteLane none() {
            return new RouteLane(Kind.NONE, null, null, null, null, null);
        }
    }

    /**
     * Resolve a route value with no request — the boot-time resolution of a value that is not templated.
     */
    public RouteLane resolveRoute(final Obj value) {
        return this.classify(webHelper.align(this, value, null), false);
    }

    /**
     * Resolve a route value against the request it serves: a templated value's expressions see the request uri,
     * so {@code ${name()}} is the tail segment and {@code ${as(rec::T).>>path/2}} the third. Anything else is
     * resolved exactly as {@link #resolveRoute(Obj)} does, since an untemplated value cannot see a request.
     */
    public RouteLane resolveRoute(final Obj value, final fURI requestUri) {
        final boolean templated = webHelper.isTemplated(value);
        return this.classify(webHelper.align(this, value, requestUri), templated);
    }

    /**
     * The ladder itself — this used to be inlined in the constructor. Named, so it can be characterized
     * directly, and separated from alignment, so a value it cannot resolve is reported where it is found
     * instead of taking the whole space down:
     * <ol>
     *   <li>a {@code Type} target — an {@code mcp_server} type is first materialized into an instance;</li>
     *   <li>an {@code mcpServer} instance — wrapped in the mcp transport handler;</li>
     *   <li>a uri target — served as a web root, or as the exact address when the value was templated;</li>
     *   <li>no lane — reported as a problem, no longer as a crash.</li>
     * </ol>
     * Note the web lane tests the <em>routed</em> uri, not the read result: a uri whose read yields nothing (or
     * a document) is still a mount, and it is exactly the "resolves to nothing, so serve 404" case.
     */
    private RouteLane classify(final Obj routed, final boolean templated) {
        Obj target = routed.isUri() ? Router.global().read(routed.uriValue()) : routed;
        // ── mcp_server type: materialize it so the transport wraps it ──
        if (target.isType() && target.asType().hasConstructor()
                && Obj.Helper.specificType(target).test(MCP_SERVER_TYPE)) {
            final Obj mcp = target.asType().constructor().apply(rec0()).as();
            if (!mcp.isFail())
                target = mcp;
        }
        if (target.isType())
            return RouteLane.handler(target.vid(), null, identityOf(target));
        if (target instanceof mcpServer)
            return RouteLane.handler(HTTP_MCP_HANDLER_TID, ((mcpServer) target).jvm(), identityOf(target));
        if (routed.isUri())
            return templated ? RouteLane.addressed(routed.uriValue()) : RouteLane.web(routed.uriValue());
        return RouteLane.none();
    }

    /**
     * What distinguishes two handlers of the same type: a materialized instance's vid when it has one, else its
     * type. Never the request address — see {@link #handlerVid}.
     */
    private static String identityOf(final Obj target) {
        return null != target.vid() ? target.vid().toString() : target.tid().toString();
    }

    // ──────────────────────────────────────────────
    // Factory, lifecycle
    // ──────────────────────────────────────────────

    public static httpSpace of(final Rec config, final fURI vid) {
        try {
            final HttpServer server = HttpServer.create(
                    new InetSocketAddress(config.at(HOST).uriValue().host(),
                            config.at(HOST).uriValue().port()), 0);
            server.setExecutor(ThreadExecutor.instance());
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
            // Normalize bare host URLs (http://host → http://host/) so route
            // matching and Jsoup fetch behave identically with or without trailing slash.
            final fURI pat = (pattern.segmentLength() == 0) && pattern.hasHost() ? pattern.asBranch() : pattern;
            // 1 — Try local route table first
            try {
                final fURI route = Space.Helper.routeFromSpace(pat.scheme(null).host(null), this.routes());
                if (route != null && !route.toString().isEmpty()) {
                    final Iterator<IdObj> local = this.cache.directReader().apply(route);
                    if (local.hasNext())
                        return local;
                }
            } catch (final Exception ignored) {
            }

            // 2 — Remote web fetch via Jsoup
            try {
                fURI runningPattern = pat;
                int steps = 0;
                while (true) {
                    final Connection.Response response = Jsoup.connect(runningPattern.toString()).ignoreContentType(true).ignoreHttpErrors(true).execute();
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
                        // MIME resolution (mirrors fsSpace.readFileAsObj):
                        //   default → typed string (e.g. html::"<html>...</html>")
                        //     where the MIME's corresponding type TID predicates the content
                        //   ?mimeq=... is handled by QCollection.mimeQ() postRead QProc
                        final Obj docObj;
                        if (null != contentType) {
                            final fURI contentTid = contentType.toTid();
                            docObj = null != contentTid
                                    ? str(response.body(), contentTid, /*vid*/ null)
                                    : str(response.body());
                        } else {
                            docObj = str(response.body());
                        }
                        LOG.debug("fetched %s [status=%d, contentType=%s, objTid=%s]",
                                runningPattern, response.statusCode(), contentType, docObj.tid());
                        final Uri key = uri(pat.scheme(null).host(null).tail(steps).asRelative());
                        if (key.uriValue().toString().trim().isEmpty())
                            return docObj.isNoObj() ? IteratorUtil.of() : IteratorUtil.of(IdObj.of(pat, docObj));
                        if (docObj.isRec()) {
                            final Obj subDocObj = docObj.asRec().at(key);
                            return subDocObj.isNoObj() ? IteratorUtil.of() : IteratorUtil.of(IdObj.of(pat, subDocObj));
                        }
                        // Non-rec result (e.g. typed str like html::"...") — return as-is
                        // for the root pattern, empty for sub-path lookups
                        if (docObj.isStr())
                            return steps == 0 ? IteratorUtil.of(IdObj.of(pat, docObj)) : IteratorUtil.of();
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

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return this.directWriter().apply(vid, obj);
    }

    /**
     * Write to the http:// address space.
     * POSTs to the remote server via HttpClient.
     */
    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (pattern, obj) -> {
            // 1 — Try local route
           /* if (pattern.test(this.pattern)) {
                final fURI location = Space.Helper.routeFromSpace(pattern.scheme(null).host(null), this.routes());
                if (location != null && !location.toString().isEmpty()) {
                    return Router.global().write(location, obj);
                }
            }*/

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
                    LOG.debug(request);
                    response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                }
                final Optional<String> contentType = pattern.hasQ(MIMEQ_PATTERN) ? Optional.of(pattern.q(MIMEQ_PATTERN)) : response.headers().firstValue(MIME.MIMEType.VALUE);
                if (contentType.isPresent())
                    return MIME.MIMEType.of(contentType.get(), MIME.MIMEType.TEXT_PLAIN).serializer().inputBytes(response.body());
                return jnt(response.statusCode());
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        };
    }
}
