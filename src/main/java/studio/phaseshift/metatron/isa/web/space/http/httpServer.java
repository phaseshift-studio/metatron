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
import com.sun.net.httpserver.HttpsServer;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.network.Listener;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.sys.type.ThreadExecutor;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.isa.web.type.mcpServer;
import studio.phaseshift.metatron.isa.web.webHelper;
import studio.phaseshift.metatron.isa.web.webInstSet;
import studio.phaseshift.metatron.util.MTronException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.LISTENER_TID;
import static studio.phaseshift.metatron.isa.web.space.http.handler.mcp_httpHandler.HTTP_MCP_HANDLER_TID;
import static studio.phaseshift.metatron.isa.web.space.http.handler.web_httpHandler.WEB_HTTP_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.MCP_SERVER_TYPE;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * HTTP_SERVER — an http(s) server as a machine's PRESENCE: a {@link Listener} on an {@code http://host:port} (or
 * {@code https://host:port}) socket, serving the routes of every machine that shares it.
 * <pre>
 *   http_server::[host=><http://0.0.0.0:8777>,route=>[/=><metatronfs:docs/website>,/mcp=>mcp_mtron]]
 *   http_server::[host=><https://0.0.0.0:8443>,keystore=><conf/server.p12>,pass=>'changeit',route=>[...]]
 * </pre>
 * THE CONSTRUCTOR SERVES, as {@code ws_server}'s does. A socket no machine at or above this one holds is bound here, and
 * this machine's network owns it (its pop releases the port); a socket already held above is JOINED — this machine adds
 * its own routes to it, and its pop withdraws them. Either way the routes are the machine's own, in its
 * {@code ~/network/listener/<key>}.
 * <p>
 * A REQUEST BELONGS TO THE MACHINE WHOSE ROUTE IT TOOK. The socket mounts one root context and routes every request
 * itself ({@link Listener#route}): the most specific key across every sharer's table answers — a wildcard key matches
 * the paths it describes — and the request is served as that route's machine.
 * <p>
 * A ROUTE IS RESOLVED ONCE. Its value is resolved on the first request it serves and kept until the value changes, so a
 * route that builds its target ({@code *dr.as(skill::T).as(mcp_server::T)}) is not rebuilt per request — and, resolved
 * late, it may name a target constructed after the server. Only a templated value is resolved per request.
 * <p>
 * TLS IS THE SOCKET'S. An {@code https://} host serves with the certificate in its {@code keystore}, opened by its
 * {@code pass}: both are read once, at the bind, and neither enters the listener entry, which any reader of
 * {@code ~/network/listener} sees.
 * <p>
 * A HANDLER IS NOT AN ADDRESS. The protocol engines that answer requests are held here, by (machine, mount, session,
 * target), and never written to an address.
 */
public final class httpServer extends Listener {

    public static final fURI HTTP_SERVER_TID = WEB_ISA_TID.extend("http").extend("http_server");
    public static final fURI HTTP_SOCKET_TID = WEB_ISA_TID.extend("http").extend("http_socket");
    public static final fURI HTTP_HANDLER_TID = WEB_ISA_TID.extend("http").extend("http_handler");

    private static final GraphittyLogger LOG = Graphitty.log(httpServer.class);

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

    public static final Type HTTP_SERVER_TYPE = Type.Builder.build()
            .tid(LISTENER_TID)
            .vid(HTTP_SERVER_TID)
            .isaPredicate(rec(
                    // an https host's certificate: its key store (a path or bytes) and the pass that opens it, read at
                    // the bind and kept by no rec
                    uri(KEYSTORE).maybe().asUri(), T(ALL),
                    uri(PASS).maybe().asUri(), T(ALL),
                    uri(STATUS).maybe().asUri(), BOOL_TYPE))
            .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(HTTP_SERVER_TID),
                    lst(T(REC_TID)), (_, inst) -> httpServer.of(inst.arg(0).asRec()))).create();

    /**
     * a route value's lane, kept per (machine, mount, value): the value's text names it, so a replaced value is resolved
     * anew and an unchanged one never is
     */
    private record LaneKey(fURI machine, fURI mount, String value) {
    }

    /**
     * a protocol engine's place: the machine and mount it serves, the client session it holds ({@code Mcp-Session-Id}),
     * and the target it serves — never the request's address, which a templated mount resolves anew per request
     */
    private record HandlerKey(fURI machine, fURI mount, String session, String identity) {
    }

    private final HttpServer socket;
    private final Map<LaneKey, RouteLane> lanes = new ConcurrentHashMap<>();
    private final Map<HandlerKey, HttpRec> handlers = new ConcurrentHashMap<>();

    private httpServer(final fURI origin, final Network owner, final SSLContext tls) {
        super(origin, HTTP_SERVER_TID, owner);
        this.socket = bind(origin, tls, this);
        LOG.info("http_server listening: %s", origin);
    }

    /**
     * An http_server from its configuration — {@code http_server::[host=>...,route=>...]} in Java: this machine's
     * routes, served through the socket at {@code host} ({@link #serve}).
     */
    public static Listener of(final Rec config) {
        final Obj origin = config.at(uri(ORIGIN));
        if (!origin.isUri())
            throw MTronException.of("an http_server serves a socket: origin=><http://host:port> required, got %s", origin);
        final Obj routes = config.at(uri(ROUTE));
        return serve(origin.uriValue(), routes.isRec() ? routes.asRec() : rec(), config);
    }

    /**
     * SERVE {@code routes} through the socket at {@code host}, from the current machine: join the socket a machine at
     * or above this one already holds, or bind it here. Answers the server.
     */
    public static Listener serve(final fURI host, final Rec routes) {
        return serve(host, routes, rec());
    }

    private static Listener serve(final fURI host, final Rec routes, final Rec config) {
        final fURI origin = host.qLess().path(List.of());
        final Network level = Machine.current().network();
        // a table it cannot serve is reported here, at the mount, rather than met later as a 500
        final Obj problems = webHelper.routeProblems(routes);
        if (!problems.isNoObj())
            LOG.warn("%s route table problems: %s", origin, problems);
        final Listener held = Network.Helper.listenerFor(origin);
        if (null != held) {
            level.listen(held, routes);
            return held;
        }
        // the certificate is loaded before anything binds, so a keystore that cannot serve never holds the port
        final SSLContext tls = HTTPS.equals(origin.scheme()) ? tls(config) : null;
        final httpServer server = new httpServer(origin, level, tls);
        try {
            level.listen(server, routes);
        } catch (final RuntimeException e) {
            // a socket bound for routes that cannot be served is released with them
            server.close();
            throw e;
        }
        return server;
    }

    @Override
    protected void unbind() {
        this.socket.stop(0);
    }

    /**
     * A level that stops serving takes what its routes resolved with it: their lanes and the handlers they built.
     */
    @Override
    public void unshare(final Network level) {
        super.unshare(level);
        final Machine machine = level.machine();
        if (null == machine)
            return;
        this.lanes.keySet().removeIf(key -> key.machine().equals(machine.vid()));
        this.handlers.keySet().removeIf(key -> key.machine().equals(machine.vid()));
    }

    // ──────────────────────────────────────────────
    // a request's life, in the machine whose route it took
    // ──────────────────────────────────────────────

    private void handle(final HttpExchange exchange) {
        try {
            final fURI request = request(exchange);
            final Optional<Route> route = this.route(request);
            if (route.isEmpty()) {
                LOG.debug("no route for %s on %s", request, this.origin());
                respond(exchange, 404, "no route for " + request.qLess());
                return;
            }
            final Route taken = route.get();
            as(taken.machine(), () -> this.serve(exchange, taken, request));
        } catch (final Throwable e) {
            // nothing may reach the dispatcher: a throwable that escapes the handler is answered here
            LOG.warn("dropping %s %s: %s", exchange.getRequestMethod(), exchange.getRequestURI(), e);
            respond(exchange, 500, "error on request: " + e.getClass().getSimpleName());
        }
    }

    private void serve(final HttpExchange exchange, final Route route, final fURI request) {
        final RouteLane lane = this.lane(route, request);
        if (RouteLane.Kind.NONE == lane.kind()) {
            LOG.warn("no lane for %s => %s", request, route.target().toShortString());
            respond(exchange, 500, "no handler for " + route.key());
            return;
        }
        final HttpRec handler = this.handler(route, exchange.getRequestHeaders().getFirst("Mcp-Session-Id"), lane);
        try {
            handler.handle(exchange, mountOf(route.key(), request), lane.address());
        } catch (final IOException e) {
            // the handler completes its own exchange; an i/o failure here is the client gone
            LOG.debug("unable to answer %s: %s", request, e.getMessage());
        }
    }

    /**
     * The lane a request's route resolves to: per request for a templated value (its expressions see the request),
     * otherwise the lane resolved the first time the route served, kept until its value changes. A value that resolves
     * to no lane is not kept — what it names may yet be constructed.
     */
    private RouteLane lane(final Route route, final fURI request) {
        final Obj value = route.target();
        if (webHelper.isTemplated(value))
            return RouteLane.resolve(value, request);
        final LaneKey key = new LaneKey(route.machine().vid(), route.key(), value.toString());
        final RouteLane kept = this.lanes.get(key);
        if (null != kept)
            return kept;
        final RouteLane resolved = RouteLane.resolve(value, null);
        if (RouteLane.Kind.NONE == resolved.kind())
            return resolved;
        final RouteLane raced = this.lanes.putIfAbsent(key, resolved);
        return null == raced ? resolved : raced;
    }

    /**
     * The handler a lane's requests are served by, per (machine, mount, session, target): built from the lane's handler
     * type with its config, and kept. A handler is a protocol engine that serves every address its mount resolves, so
     * the request's own address is never part of the key.
     */
    private HttpRec handler(final Route route, final String session, final RouteLane lane) {
        final HandlerKey key = new HandlerKey(route.machine().vid(), route.key(), null == session ? "default" : session, lane.identity());
        final HttpRec kept = this.handlers.get(key);
        if (null != kept)
            return kept;
        final HttpRec built = build(lane);
        final HttpRec raced = this.handlers.putIfAbsent(key, built);
        return null == raced ? built : raced;
    }

    private static HttpRec build(final RouteLane lane) {
        final Map<Obj, Obj> config = mutableMap(
                uri(IN), uri(MIME.MIMEType.APPLICATION_JSON.value),
                uri(OUT), uri(MIME.MIMEType.APPLICATION_JSON.value));
        if (null != lane.handlerConfig())
            config.putAll(lane.handlerConfig());
        // a prefix mount bakes its root; a templated one bakes nothing and supplies the address per request instead
        // (HttpRec.address()), because that address is not knowable here and now
        if (null != lane.webRoot())
            config.put(uri(WEB_ROOT), uri(lane.webRoot()));
        // the type's constructor directly: a rec built and cast could redirect to an address where the type has none
        final Obj type = Machine.current().memory().read(lane.handlerType());
        if (!type.isType() || !type.asType().hasConstructor())
            throw MTronException.of("no http handler type at %s", lane.handlerType());
        final Obj handler = type.asType().constructor().apply(rec(config)).as();
        if (!(handler instanceof HttpRec httpRec))
            throw MTronException.of("handler %s is not an httprec::T: %s", lane.handlerType(), handler);
        httpRec.self(httpRec.jvm(), lane.handlerType(), null);
        return httpRec;
    }

    /**
     * The request path a route's key matched — what the handler strips to find the path beneath the mount. A literal key
     * is that path itself; a wildcard key matched the request's own segments in its places, up to a trailing {@code #},
     * which took the rest.
     */
    static fURI mountOf(final fURI key, final fURI request) {
        if (!key.hasPattern())
            return key;
        final fURI path = request.qLess();
        final List<String> matched = new ArrayList<>();
        for (int i = 0; i < key.path().size() && i < path.path().size(); i++) {
            if ("#".equals(key.path().get(i)))
                break;
            matched.add(path.path().get(i));
        }
        return path.path(matched);
    }

    private static fURI request(final HttpExchange exchange) {
        return fURI.Singleton.f(exchange.getRequestURI().toString());
    }

    private static void respond(final HttpExchange exchange, final int status, final String message) {
        try {
            final byte[] body = message.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(MIME.MIMEType.VALUE, MIME.MIMEType.TEXT_PLAIN.value);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        } catch (final IOException e) {
            LOG.debug("unable to answer %s with %s: %s", exchange.getRequestURI(), status, e.getMessage());
        } finally {
            exchange.close();
        }
    }

    // ──────────────────────────────────────────────
    // the socket
    // ──────────────────────────────────────────────

    /**
     * Bind the socket — synchronously, so a lost bind throws before the server exists — mount the one root context that
     * routes every request, and start it.
     */
    private static HttpServer bind(final fURI origin, final SSLContext tls, final httpServer server) {
        if (null == origin.host() || origin.port() < 0)
            throw MTronException.of("an http_server binds a host and a port: %s", origin);
        try {
            final InetSocketAddress address = new InetSocketAddress(origin.host(), origin.port());
            final HttpServer socket;
            if (null == tls)
                socket = HttpServer.create(address, 0);
            else {
                final HttpsServer https = HttpsServer.create(address, 0);
                https.setHttpsConfigurator(new HttpsConfigurator(tls));
                socket = https;
            }
            socket.createContext("/", server::handle);
            socket.setExecutor(ThreadExecutor.instance());
            socket.start();
            return socket;
        } catch (final IOException e) {
            throw MTronException.of(e, "unable to bind %s: %s", origin, MTronException.translateMessage(e));
        }
    }

    /**
     * The certificate an https host serves. {@code keystore} is a PKCS12 (or JKS) key store — a path to one, or its
     * bytes ({@code keystore => *<mfs:conf/server.p12>}) — and {@code pass} opens both the store and the key in it.
     * Both are applied first, so either may be an expression. Every way that can be wrong fails the construction here,
     * at boot, rather than each handshake later.
     */
    static SSLContext tls(final Rec config) {
        final Obj keystore = config.at(uri(KEYSTORE)).apply();
        final Obj pass = config.at(uri(PASS)).apply();
        if (keystore.isNoObj())
            throw MTronException.of("an %s host requires a %s", HTTPS, KEYSTORE);
        if (!pass.isStr())
            throw MTronException.of("an %s host requires the %s that opens its %s", HTTPS, PASS, KEYSTORE);
        final char[] secret = pass.strValue().toCharArray();
        try (final InputStream in = open(keystore)) {
            final KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(in, secret);
            if (!holdsKey(store))
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

    // ──────────────────────────────────────────────
    // route resolution — the ladder, named
    // ──────────────────────────────────────────────

    /**
     * The lane a route value resolves to, with everything the mount needs to honour it.
     *
     * @param kind          which lane the value took
     * @param handlerType   the handler type to construct (HANDLER and, since it is auto-created, WEB)
     * @param handlerConfig extra handler config — a materialized {@code mcp_server}'s state (HANDLER lane)
     * @param identity      what distinguishes two handlers of the same type, i.e. a materialized instance's vid;
     *                      never the request address
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

        /**
         * Resolve a route value for the request it serves ({@code null} for no request). A templated value's
         * expressions see the request uri, so {@code ${name()}} is the tail segment and {@code ${as(rec::T).>>path/2}}
         * the third; anything else resolves as it stands, since an untemplated value cannot see a request. Resolved in
         * the machine the caller stands in, which is the machine whose route it is.
         */
        public static RouteLane resolve(final Obj value, final fURI request) {
            final boolean templated = null != request && webHelper.isTemplated(value);
            return classify(webHelper.align(null, value, request), templated);
        }

        /**
         * The ladder itself:
         * <ol>
         *   <li>a {@code Type} target — an {@code mcp_server} type is first materialized into an instance;</li>
         *   <li>an {@code mcpServer} instance — wrapped in the mcp transport handler;</li>
         *   <li>a uri target — served as a web root, or as the exact address when the value was templated;</li>
         *   <li>no lane — reported as a problem, not a crash.</li>
         * </ol>
         * The web lane tests the <em>routed</em> uri, not the read result: a uri whose read yields nothing (or a
         * document) is still a mount, and it is exactly the "resolves to nothing, so serve 404" case.
         */
        private static RouteLane classify(final Obj routed, final boolean templated) {
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
         * type. Never the request address.
         */
        private static String identityOf(final Obj target) {
            return null != target.vid() ? target.vid().toString() : target.tid().toString();
        }
    }
}
