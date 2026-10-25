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
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsServer;
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
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.sys.type.ThreadExecutor;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.isa.web.type.mcpServer;
import studio.phaseshift.metatron.isa.web.webHelper;
import studio.phaseshift.metatron.isa.web.webInstSet;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.MIMEQ_PATTERN;
import static studio.phaseshift.metatron.isa.m.mInstSet.FROM_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.TO_INST_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
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
            .constructor(space -> httpSpace.of(space.asRec(), space.vid()))
            .create();

    private final memSpace cache;
    /**
     * the https:// half of this space's client, when its pattern is http:// — see {@link #companion()}
     */
    private final httpSpace companion;

    public static final Type HTTP_HANDLER_TYPE = Type.Builder.build()
            .tid(HTTP_SOCKET_TID)
            .vid(HTTP_HANDLER_TID)
            .constructor(handler -> new HttpRec(handler.asRec().jvm(), handler.tid(), handler.vid()))
            .create();

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
                    uri(ON_CLOSE).maybe(), T(ALL)))
            .create();

    /**
     * how long an http client's status may wait for the server to answer its HEAD — the connect and the response
     * both — before it reads not live
     */
    private static final java.time.Duration STATUS_TIMEOUT = java.time.Duration.ofSeconds(2);

    public static final Type HTTP_CLIENT_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(HTTP_CLIENT_TID)
            .constructor(socket -> {
                // THE HTTP CLIENT — a connectionless reader and writer. It overrides `from` with an HTTP GET and `to`
                // with an HTTP POST, and declares NO eval: it is reached only through those two overrides in the
                // read/write ladders. Each override is the native itself with an http body, so the call arrives as it
                // was written — the FULL address (with authority) in arg(0), sent verbatim.
                // GET: the response is reified by its content type.
                final Obj from = Network.Helper.override(FROM_INST_TID, (lhs, inst) -> {
                    final fURI uri = inst.arg(0).uriValue();
                    try {
                        final HttpRequest request = HttpRequest.newBuilder()
                                .uri(java.net.URI.create(uri.toString()))
                                .GET()
                                .build();
                        final HttpResponse<String> response;
                        try (final HttpClient client = HttpClient.newHttpClient()) {
                            response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        }
                        // mirror httpSpace's read path: a typed string keyed by the content type's tid (html::"..."),
                        // never a serialized rec — the body is TEXT, not data.
                        final String contentTypeHeader = response.headers().firstValue(MIME.MIMEType.VALUE).orElse(null);
                        final MIME.MIMEType contentType = null == contentTypeHeader ? null : MIME.MIMEType.of(contentTypeHeader);
                        final fURI contentTid = null == contentType ? null : contentType.toTid();
                        return null != contentTid ? str(response.body(), contentTid, null) : str(response.body());
                    } catch (final Exception e) {
                        return fail(e);
                    }
                });
                // POST: obj.to(<uri>) writes the obj — the lhs, as the native's — as httpSpace's directWriter sends it:
                // the mime is the address's ?mimeq or json, the body that mime's bytes. The answer is the native to's:
                // the obj written (its range is the lhs's — the type system checks it), so a reply the far side sends
                // back is not the write's answer; a refusal (no 2xx) is a fail. A POST WRITES and nothing more: the far
                // side stores what it is sent and evaluates nothing, so this client serves no eval and is no place to
                // ship a program (Placement.Shape.inst requires both to and eval of a shard target).
                final Obj to = Network.Helper.override(TO_INST_TID, (lhs, inst) -> {
                    final fURI uri = inst.arg(0).uriValue();
                    try {
                        final MIME.MIMEType mime = Optional.ofNullable(MIME.MIMEType.of(uri.q(MIMEQ_PATTERN))).orElse(MIME.MIMEType.APPLICATION_JSON);
                        final HttpRequest request = HttpRequest.newBuilder()
                                .header(MIME.MIMEType.VALUE, mime.value)
                                .uri(java.net.URI.create(uri.removeQ(MIMEQ_PATTERN).toString()))
                                .POST(HttpRequest.BodyPublishers.ofByteArray(mime.toBytes(lhs)))
                                .build();
                        final HttpResponse<byte[]> response;
                        try (final HttpClient client = HttpClient.newHttpClient()) {
                            response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                        }
                        return 2 == response.statusCode() / 100
                                ? lhs
                                : fail("POST %s answered %s", uri, response.statusCode());
                    } catch (final Exception e) {
                        return fail(e);
                    }
                });
                // THE STATUS — a connectionless client holds no socket to ask, so it asks the server, each time it is
                // read: a bounded HEAD to the host it was dialed at. ANY response is a live server (a 404 still
                // answers); a refused or timed-out connection is not.
                final Obj host = socket.asRec().at(uri(HOST));
                final Obj status = Network.Helper.statusOf(() -> {
                    if (!host.isUri())
                        return false;
                    try (final HttpClient client = HttpClient.newBuilder().connectTimeout(STATUS_TIMEOUT).build()) {
                        client.send(HttpRequest.newBuilder()
                                        .uri(java.net.URI.create(host.uriValue().toString()))
                                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                                        .timeout(STATUS_TIMEOUT)
                                        .build(),
                                HttpResponse.BodyHandlers.discarding());
                        return true;
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    } catch (final Exception e) {
                        return false;
                    }
                });
                final Obj client = rec(
                        uri(HOST), host,
                        uri(INST), rec(uri(FROM_INST_TID), from, uri(TO_INST_TID), to),
                        uri(STATUS), status);
                Network.Helper.index(client);
                return client;
            }).create();


    protected httpSpace(final HttpServer server, final Map<Obj, Obj> config, final fURI vid) {
        super(server, config, HTTP_SPACE_TID, vid);
        // the session cache is this space's own, reached through this field and never by address: registered, it
        // claimed this space's pattern in memory beside the space, and outlived it there once the space was closed
        this.cache = memSpace.unregistered(rec(uri(PATTERN), config.getOrDefault(uri(PATTERN), noobj())), null);
        if (null == server) {
            // a client half — the https:// companion of an http:// space: no socket, so no routes to mount on one
            this.companion = null;
            return;
        }
        // no catch-all around the table: one that cannot be processed fails the construction. Swallowed, it left the
        // space mounted around a bound socket that was never started, which accepts connections and answers none.
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
        // mounted before the server starts and inside this construction, so a companion that cannot mount fails it
        this.companion = this.companion();
        LOG.info("starting web server at %s", this.at(HOST).uriValue().scheme(server instanceof HttpsServer ? HTTPS : HTTP).toUri());
        server.setExecutor(ThreadExecutor.instance());
        LOG.debug("available routes: %s", this.at(ROUTE));
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
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
        // the FULL connection uri — scheme + the server's own authority + the dialed path and its
        // ?q processors (?name=<name> is the dialer's identity) — defined once at the request
        // boundary so the co-mount and the handlers see what the client actually dialed — https when it came over tls.
        final fURI connection = f(exchange.getRequestURI().toString()).scheme(exchange instanceof HttpsExchange ? HTTPS : HTTP)
                .host(fURI.hostOf(exchange.getLocalAddress())).port(exchange.getLocalAddress().getPort());
        final RouteLane lane = null != fixed ? fixed
                : this.resolveRoute(routeValue, connection.localize());
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
                final Obj type = Machine.current().memory().read(lane.handlerType());
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
        Obj target = routed.isUri() ? Machine.current().memory().read(routed.uriValue()) : routed;
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
            final fURI host = config.at(HOST).uriValue();
            // an https host's certificate is loaded before anything binds, so a keystore that cannot serve never holds
            // the port it would have served on
            final SSLContext tls = HTTPS.equals(host.scheme()) ? httpSpace.tls(config) : null;
            // HttpServer.create binds here, synchronously, so a lost bind throws before the space (which registers
            // itself in memory as it is constructed) exists
            final InetSocketAddress address = new InetSocketAddress(host.host(), host.port());
            final HttpServer server;
            if (null == tls)
                server = HttpServer.create(address, 0);
            else {
                final HttpsServer https = HttpsServer.create(address, 0);
                https.setHttpsConfigurator(new HttpsConfigurator(tls));
                server = https;
            }
            try {
                server.setExecutor(ThreadExecutor.instance());
                return Space.Helper.construct(() -> new httpSpace(server, config.jvm(), vid));
            } catch (final RuntimeException e) {
                // the port this factory bound is released with the space that never came to serve it. stop() alone
                // does not release a server that never started: its channel is registered with the server's
                // selector, so the close completes only on the next select, and only the dispatcher selects.
                try {
                    server.start();
                } catch (final IllegalStateException alreadyStarted) {
                    // the construction failed after starting it
                }
                server.stop(0);
                throw e;
            }
        } catch (final Exception e) {
            throw MTronException.of(e, "unable to start http server at %s: %s", config.at(HOST), MTronException.translateMessage(e));
        }
    }

    /**
     * The certificate an https host serves. {@code keystore} is a PKCS12 (or JKS) key store — a path to one, or its
     * bytes ({@code keystore => *<mfs:conf/server.p12>}) — and {@code pass} opens both the store and the key in it.
     * Both are applied first, as a space's pattern is, so either may be an expression. Every way that can be wrong
     * fails the construction here, at boot, rather than each handshake later.
     */
    static SSLContext tls(final Rec config) {
        final Obj keystore = config.at(uri(KEYSTORE)).apply();
        final Obj pass = config.at(uri(PASS)).apply();
        if (keystore.isNoObj())
            throw MTronException.of("an %s host requires a %s", HTTPS, KEYSTORE);
        if (!pass.isStr())
            throw MTronException.of("an %s host requires the %s that opens its %s", HTTPS, PASS, KEYSTORE);
        final char[] secret = pass.strValue().toCharArray();
        try (final InputStream in = httpSpace.open(keystore)) {
            final KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(in, secret);
            if (!httpSpace.holdsKey(store))
                throw MTronException.of("%s holds no private key to serve", keystore);
            final KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(store, secret);
            final SSLContext context = SSLContext.getInstance("TLS");
            context.init(keys.getKeyManagers(), null, null);
            return context;
        } catch (final MTronException e) {
            throw e;
        } catch (final Exception e) {
            throw MTronException.of(e, "unable to load %s %s: %s", KEYSTORE, keystore, MTronException.translateMessage(e));
        }
    }

    private static InputStream open(final Obj keystore) throws IOException {
        if (keystore.isBytes())
            return new ByteArrayInputStream(keystore.bytesValue().array());
        if (!keystore.isUri() && !keystore.isStr())
            throw MTronException.of("a %s is a path or bytes: %s", KEYSTORE, keystore);
        final String path = keystore.isUri() ? keystore.uriValue().toString() : keystore.strValue();
        return Files.newInputStream(Path.of(path.startsWith("~") ? System.getProperty(USER_HOME) + path.substring(1) : path));
    }

    private static boolean holdsKey(final KeyStore store) throws KeyStoreException {
        for (final String alias : Collections.list(store.aliases()))
            if (store.isKeyEntry(alias))
                return true;
        return false;
    }

    /**
     * The https:// half of an http:// space's client. A space claims exactly one pattern, so an {@code http://#}
     * space leaves every {@code https://} address unrouted, though Jsoup and HttpClient both speak tls. This mounts
     * the twin pattern as a second httpspace with no server: the same client (local routes, then the web) and the
     * same q-procs, nothing bound and nothing announced, under this space's vid and closed with it. A pattern of any
     * other scheme has no twin, and gets none.
     */
    private httpSpace companion() {
        if (null == this.pattern() || !HTTP.equals(this.pattern().scheme()))
            return null;
        final Map<Obj, Obj> config = mutableMap(uri(PATTERN), uri(this.pattern().scheme(HTTPS)));
        final Obj qprocs = this.at(uri(QPROC));
        if (!qprocs.isNoObj())
            config.put(uri(QPROC), qprocs);
        final httpSpace companion = new httpSpace(null, config, null == this.vid() ? null : this.vid().extend(HTTPS));
        LOG.info("mounting %s client at %s [pattern: %s]", HTTPS, companion.vid(), companion.pattern());
        return companion;
    }

    /**
     * the server IS this space: its host is the socket it bound, so it is an address this machine answers on. A
     * companion binds nothing, so it answers on nothing.
     */
    @Override
    public boolean listens() {
        return null != this.sjvm();
    }

    @Override
    public void close() {
        // the companion is this space's client for the other scheme: it goes when this space goes
        if (null != this.companion)
            this.companion.close();
        if (null != this.sjvm())
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
     * Read from the web address space this space claims: http:// or https://.
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
     * Write to the web address space this space claims: http:// or https://.
     * POSTs to the remote server via HttpClient.
     */
    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (pattern, obj) -> {
            // 1 — Try local route
          /* if (pattern.test(this.pattern)) {
                final fURI location = Space.Helper.routeFromSpace(pattern.scheme(null).host(null), this.routes());
                if (location != null && !location.toString().isEmpty()) {
                    return Machine.current().memory().write(location, obj);
                }
            }*/

            // 2 — Remote POST via HttpClient
            try {
                final MIME.MIMEType mime = Optional.ofNullable(MIME.MIMEType.of(pattern.q(MIMEQ_PATTERN))).orElse(MIME.MIMEType.APPLICATION_JSON);
                // the mime's serializer already produced the body, so sending those bytes is the whole job.
                // re-parsing them here is what broke this write: a rec serializes to a JsonObject, and
                // JsonElement.getAsString() throws on a JsonObject -- so every json POST failed locally,
                // before the client ever connected (a dead host reproduces it).
                final HttpRequest request = HttpRequest.newBuilder()
                        .header(MIME.MIMEType.VALUE, mime.value)
                        .uri(java.net.URI.create(pattern.removeQ(MIMEQ_PATTERN).toString()))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(mime.toBytes(obj)))
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
