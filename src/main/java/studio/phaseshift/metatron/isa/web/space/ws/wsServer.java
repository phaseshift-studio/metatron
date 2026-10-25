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

package studio.phaseshift.metatron.isa.web.space.ws;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.network.Listener;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/**
 * WS_SERVER — a websocket server as a machine's PRESENCE: a {@link Listener} on a {@code ws://host:port} socket, serving
 * the routes of every machine that shares it.
 * <pre>
 *   ws_server::[host=><ws://0.0.0.0:8555>,route=>[/mtron=>mtron_ws,/mcp=>mcp_mtron]]
 * </pre>
 * THE CONSTRUCTOR SERVES. A socket no machine at or above this one holds is bound here, and this machine's network owns
 * it (its pop releases the port); a socket already held above is JOINED — this machine adds its own routes to it, and
 * its pop withdraws them. Either way the routes are the machine's own, in its {@code ~/network/listener/<key>}.
 * <p>
 * A CONNECTION BELONGS TO THE MACHINE WHOSE ROUTE IT TOOK. Per connection the most specific route across every sharer
 * answers ({@link Listener#route}), and that route's machine resolves the route's handler, owns the session — it
 * evaluates as that machine, where a socket thread would otherwise stand in the root — and holds the inbound peer in its
 * own {@code network/peer} ({@link Network#accept}), which the socket's close retracts.
 * <p>
 * A SESSION IS NOT AN ADDRESS. A session is held by its socket, here, and reached through the peer it mints: there is no
 * session cache and no {@code ws://host:port/...} session address.
 */
public final class wsServer extends Listener {

    public static final fURI WS_SERVER_TID = WEB_ISA_TID.extend("ws").extend("ws_server");

    private static final GraphittyLogger LOG = Graphitty.log(wsServer.class);

    public static final Type WS_SERVER_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(WS_SERVER_TID)
            .isaPredicate(rec(
                    // the socket to serve, scheme://host:port — wsspace's word for it, so a boot profile's ws block carries over
                    uri(HOST).maybe().asUri(), URI_TYPE,
                    // this machine's routes on the socket: <path> => handler (a uri, a type, or an instruction)
                    uri(ROUTE).maybe().asUri(), rec(URI_TYPE, T(ALL)),
                    // the socket bound, and whether it is still served — the server's own fields once constructed
                    uri(AUTHORITY).maybe().asUri(), URI_TYPE,
                    uri(STATUS).maybe().asUri(), BOOL_TYPE))
            .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(WS_SERVER_TID),
                    lst(T(REC_TID)), (_, inst) -> {
                        final Rec config = inst.arg(0).asRec();
                        final Obj host = config.at(uri(HOST)).autoResolve(noobj());
                        if (!host.isUri())
                            throw MTronException.of("a ws_server serves a socket: host=><ws://host:port> required, got %s", host);
                        final Obj routes = config.at(uri(ROUTE));
                        return wsServer.serve(host.uriValue(), routes.isRec() ? routes.asRec() : rec());
                    })).create();

    /**
     * a connection's lot: the route it took, its session, and the inbound peer it minted there (null when declined)
     */
    private record Session(Route route, WebSocketRec rec, fURI peer) {
    }

    private final Socket socket;
    /**
     * the live sessions, by their sockets — held here, never at an address
     */
    private final Map<WebSocket, Session> sessions = Collections.synchronizedMap(new IdentityHashMap<>());

    private wsServer(final fURI authority, final Network owner) {
        super(authority, WS_SERVER_TID, owner);
        this.socket = new Socket(authority);
    }

    /**
     * SERVE {@code routes} through the socket at {@code host}, from the current machine: join the socket a machine at
     * or above this one already holds, or bind it here. Answers the server.
     */
    public static Listener serve(final fURI host, final Rec routes) {
        final fURI authority = host.qLess().path(List.of());
        final Network level = Machine.current().network();
        final Listener held = Network.Helper.listenerFor(authority);
        if (null != held) {
            level.listen(held, routes);
            return held;
        }
        final wsServer server = new wsServer(authority, level);
        try {
            level.listen(server, routes);
        } catch (final RuntimeException e) {
            // a socket bound for routes that cannot be served is released with them
            server.close();
            throw e;
        }
        return server;
    }

    /**
     * THE CO-MOUNT — the inbound half of a dial, held by the machine whose route the connection took: its peer, keyed
     * by the remote socket and the route it dialed ({@code ?name} included, the dialer's identity), with the session as
     * its client. Answers the address the peer is retracted by, or null when the co-mount was declined.
     */
    static fURI admit(final Route route, final fURI dialed, final Obj session) {
        return route.level().accept(dialed, session).isNoObj() ? null : dialed;
    }

    @Override
    protected void unbind() {
        try {
            this.socket.stop(1000, "server shutdown");
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ──────────────────────────────────────────────
    // a connection's life, in the machine whose route it took
    // ──────────────────────────────────────────────

    private void open(final WebSocket conn, final ClientHandshake handshake) {
        final fURI request = request(conn);
        final Optional<Route> route = this.route(request);
        if (route.isEmpty()) {
            if ("/".equals(request.qLess().toString()))
                this.banner(conn);
            else {
                LOG.debug("no route for %s on %s", request, this.authority());
                conn.close(4004, "no route for " + request);
            }
            return;
        }
        final Route taken = route.get();
        as(taken.machine(), () -> {
            final WebSocketRec session = wsSpace.session(wsSpace.RouteLane.resolve(taken.target(), request), request, null);
            session.setWebSocket(conn);
            this.sessions.put(conn, new Session(taken, session, null));
            session.onOpen(conn, handshake);
            final fURI peer = this.admit(taken, conn, session);
            if (null != peer)
                this.sessions.put(conn, new Session(taken, session, peer));
        });
    }

    private fURI admit(final Route route, final WebSocket conn, final WebSocketRec session) {
        try {
            final InetSocketAddress remote = conn.getRemoteSocketAddress();
            // the full connection uri: the route it dialed and its ?q (the dialer's ?name), at the remote socket
            return null == remote ? null : admit(route, request(conn).scheme("ws").host(fURI.hostOf(remote)).port(remote.getPort()), session);
        } catch (final Exception e) {
            // never breaks the connection: a declined co-mount is a connection without a peer
            LOG.debug("inbound peer co-mount declined: %s", e);
            return null;
        }
    }

    private void closed(final WebSocket conn, final int code, final String reason, final boolean remote) {
        final Session session = this.sessions.remove(conn);
        if (null == session)
            return;
        as(session.route().machine(), () -> {
            session.rec().onClose(conn, code, reason, remote);
            // the inbound peer dies with its socket: reach is a capability, retracted with the connection
            if (null != session.peer())
                session.route().level().disconnect(session.peer());
        });
    }

    private void message(final WebSocket conn, final String message) {
        final Session session = this.sessions.get(conn);
        if (null != session)
            as(session.route().machine(), () -> session.rec().onMessage(conn, message));
    }

    private void error(final WebSocket conn, final Exception ex) {
        final Session session = this.sessions.get(conn);
        if (null != session)
            as(session.route().machine(), () -> session.rec().onError(conn, ex));
    }

    /**
     * the socket's root, when no machine routes it: who answers here, and the routes each machine serves
     */
    private void banner(final WebSocket conn) {
        for (final String line : CommonUtil.getHeader(CommonUtil.HEADER_FILE, null, true).split("\n"))
            conn.send(line);
        conn.send(String.format("metatron ws_server at {{b}}%s{{X}}\n", this.authority()));
        conn.send("\t{{y}}available routes{{X}}:\n");
        for (final Network level : this.sharers()) {
            final Obj entry = level.listenerAt(Network.Helper.keyOf(this.authority()));
            final Obj routes = entry.isRec() ? entry.asRec().at(uri(ROUTE)) : noobj();
            if (routes.isRec())
                conn.send(Highlighter.format(routes));
        }
        conn.closeConnection(1000, "end transmission");
    }

    private static InetSocketAddress address(final fURI authority) {
        if (null == authority.host() || authority.port() < 0)
            throw MTronException.of("a ws_server binds a host and a port: %s", authority);
        return new InetSocketAddress(authority.host(), authority.port());
    }

    private static fURI request(final WebSocket conn) {
        final String descriptor = null == conn.getResourceDescriptor() ? "/" : conn.getResourceDescriptor();
        return f(descriptor.startsWith("/") ? descriptor : "/" + descriptor);
    }

    /**
     * Run a callback as the machine whose route the connection took, then stand back where the thread stood: a socket
     * thread is in no machine, and {@code Machine.current()} there answers the root.
     */
    private static void as(final Machine machine, final Runnable body) {
        final Machine previous = Machine.CURRENT.get();
        Machine.current(machine);
        try {
            body.run();
        } finally {
            if (null == previous)
                Machine.CURRENT.remove();
            else
                Machine.CURRENT.set(previous);
        }
    }

    /**
     * Nothing may reach the socket's worker thread: a throwable that escapes a callback kills the whole server, so only
     * the connection it came from is dropped.
     */
    private static void guard(final WebSocket conn, final Runnable callback) {
        try {
            callback.run();
        } catch (final Throwable e) {
            LOG.warn("dropping %s: %s", null == conn ? "<no connection>" : conn.getRemoteSocketAddress(), e);
            if (null != conn) {
                try {
                    conn.closeConnection(3000, "error on connection: " + e.getClass().getSimpleName());
                } catch (final Throwable ignored) {
                    // already closing
                }
            }
        }
    }

    /**
     * The socket: Java-WebSocket's server, bound on construction (or the construction throws), each callback handed to
     * the session its connection belongs to.
     */
    private final class Socket extends WebSocketServer {

        /**
         * the bind's outcome. Java-WebSocket binds on its own selector thread: success calls {@link #onStart()}, and a
         * failed bind is reported only to {@code onError(null, ex)}, after which the server shuts itself down.
         */
        private final CompletableFuture<Void> started = new CompletableFuture<>();

        private Socket(final fURI authority) {
            super(address(authority));
            if (!Machine.loaded())
                throw MTronException.of("unable to start %s: no machine is loaded", authority);
            this.setReuseAddr(true);
            this.setDaemon(true);
            this.start();
            this.awaitStart();
            LOG.info("ws_server listening: %s", authority);
        }

        /**
         * return once the socket is listening, or throw why it is not
         */
        private void awaitStart() {
            try {
                this.started.get(5000, TimeUnit.MILLISECONDS);
            } catch (final ExecutionException e) {
                // the selector thread's fatal path has already shut the server down
                throw MTronException.of(e.getCause(), "unable to bind %s: %s", this.getAddress(), MTronException.translateMessage(e.getCause()));
            } catch (final TimeoutException | InterruptedException e) {
                // no outcome: shut the server down, or it could still bind later as a socket no listener holds
                try {
                    this.stop(1000, "server did not start");
                } catch (final InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                if (e instanceof InterruptedException)
                    Thread.currentThread().interrupt();
                throw MTronException.of(e, "%s did not start: %s", this.getAddress(), e.getClass().getSimpleName());
            }
        }

        @Override
        public void onOpen(final WebSocket conn, final ClientHandshake handshake) {
            guard(conn, () -> wsServer.this.open(conn, handshake));
        }

        @Override
        public void onClose(final WebSocket conn, final int code, final String reason, final boolean remote) {
            guard(conn, () -> wsServer.this.closed(conn, code, reason, remote));
        }

        @Override
        public void onMessage(final WebSocket conn, final String message) {
            guard(conn, () -> wsServer.this.message(conn, message));
        }

        @Override
        public void onMessage(final WebSocket conn, final ByteBuffer message) {
            guard(conn, () -> wsServer.this.message(conn, new String(message.array())));
        }

        @Override
        public void onError(final WebSocket conn, final Exception ex) {
            // no connection: the error is the socket's own, and before onStart() that means the bind failed
            if (null == conn)
                this.started.completeExceptionally(ex);
            else
                guard(conn, () -> wsServer.this.error(conn, ex));
        }

        @Override
        public void onStart() {
            this.started.complete(null);
        }
    }
}
