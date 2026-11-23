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

package studio.phaseshift.metatron.isa.m.web.space.ws;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.Analyzer;
import studio.phaseshift.metatron.isa.m.mach.type.network.Listener;
import studio.phaseshift.metatron.isa.m.sys.type.ThreadExecutor;
import studio.phaseshift.metatron.isa.m.sys.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.TypeBuilder;
import studio.phaseshift.metatron.isa.m.web.space.ws.handler.mcp_wsHandler;
import studio.phaseshift.metatron.isa.m.web.space.ws.handler.mtron_wsHandler;
import studio.phaseshift.metatron.isa.m.web.type.MIME;
import studio.phaseshift.metatron.isa.m.web.type.mcpServer;
import studio.phaseshift.metatron.isa.m.web.webHelper;
import studio.phaseshift.metatron.isa.m.web.webInstSet;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.web.space.ws.handler.mcp_wsHandler.WS_MCP_HANDLER_TID;
import static studio.phaseshift.metatron.isa.m.web.webInstSet.MCP_SERVER_TYPE;
import static studio.phaseshift.metatron.isa.m.web.webInstSet.WEB_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * WS_SERVER — a websocket server as a machine's PRESENCE: a {@link Listener} on a {@code ws://host:port} socket, serving
 * the routes of every machine that shares it.
 * <pre>
 *   ws_server::[origin=><ws://0.0.0.0:8555>,route=>[/mtron=>mtron_ws,/mcp=>mcp_mtron]]
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
    public static final fURI WS_WEBSOCKET_TID = WEB_ISA_TID.extend("ws").extend("web_socket");
    public static final fURI WS_HANDLER_TID = WEB_ISA_TID.extend("ws").extend("ws_handler");
    public static final fURI WS_CLIENT_TID = WEB_ISA_TID.extend("ws").extend("ws_client");

    private static final GraphittyLogger LOG = Graphitty.log(wsServer.class);

    public static final Type WS_WEBSOCKET_TYPE = TypeBuilder.build()
            .tid(REC_TID)
            .vid(WS_WEBSOCKET_TID)
            .isaPredicate(rec(
                    uri(IN).maybe().asUri(), isa_(webInstSet.MIME_OBJ_TYPE).orElse(uri(MIME.MIMEType.APPLICATION_MTRON.value)),
                    uri(OUT).maybe().asUri(), isa_(webInstSet.MIME_OBJ_TYPE).orElse(uri(MIME.MIMEType.APPLICATION_MTRON.value)),
                    uri(SEND).maybe().asUri(), INST_TYPE,
                    uri(SEND_RECV).maybe().asUri(), INST_TYPE,
                    // the mini-instset — {instruction tid => override}: what the network reaches this socket with; a
                    // spec that carries its own (a custom to(), say) keeps it, otherwise the socket declares eval/from/to
                    uri(INST).maybe().asUri(), REC_TYPE,
                    // true while the socket is open — optional here, installed by the constructor, derived on read
                    uri(STATUS).maybe().asUri(), BOOL_TYPE,
                    uri(ON_OPEN).maybe(), T(ALL),
                    uri(ON_ERROR).maybe(), T(ALL),
                    uri(ON_MESSAGE).maybe(), T(ALL),
                    uri(ON_CLOSE).maybe(), T(ALL))).create();


    public static final Type WS_HANDLER_TYPE = TypeBuilder.build()
            .tid(WS_WEBSOCKET_TID)
            .vid(WS_HANDLER_TID)
            .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(WS_WEBSOCKET_TID),
                    lst(T(REC_TID)), (_, inst) ->
                            new WebSocketRec(inst.arg(0).asRec().jvm(), inst.arg(0).vid()))).create();

    public static final Type WS_CLIENT_TYPE = TypeBuilder.build()
            .tid(WS_WEBSOCKET_TID)
            .vid(WS_CLIENT_TID)
            .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(WS_WEBSOCKET_TID),
                    lst(T(REC_TID)), (_, inst) -> {
                        try {
                            // THE IDIOMATIC DIAL: ws_client::[pattern=><ws://host:port/mtron>] —
                            // the claim derives the host (pattern retracted), the constructor connects, and
                            // on success the client indexes itself into the network as a space's constructor
                            // indexes into Memory. Indexing is a BACKGROUND behavior: it never fails the
                            // construction, and connect() remains sugar that converges on the same peer.
                            final Map<Obj, Obj> config =
                                    new LinkedHashMap<>(inst.arg(0).asRec().jvm());
                            final studio.phaseshift.metatron.isa.m.type.Obj pattern = config.get(uri(PATTERN));
                            if (null != pattern && pattern.isUri() && !config.containsKey(uri(HOST)))
                                config.put(uri(HOST), uri(pattern.uriValue().retractPattern()));
                            config.put(uri(HOST), uri(Machine.Helper.align(Machine.current(), config.get(uri(HOST)).uriValue())));
                            final WebSocketRec wsRec = new WebSocketRec(config, inst.arg(0).vid());
                            // A PROTOCOL HAT: protocol=>mtron_ws installs the mtron facade's full surface (apply-and-
                            // respond on_message, on_error, state) as guarded defaults, so the client can answer a
                            // server-initiated request. Explicit fields still win — the guards fill only what is absent.
                            if (wearsMtronHat(wsRec))
                                mtron_wsHandler.install(wsRec);
                            final WebSocketRecClient client = new WebSocketRecClient(wsRec);
                            // INDEXING IS UNCONDITIONAL. A dial whose far side is not up yet still declares a peer:
                            // the client is demoted to a bare rec inside its constructor and must be indexed so the
                            // supervisor redials it in place until the far side answers. Indexing is a background
                            // behavior that never fails the construction, and Network.index re-checks the inst.
                            Network.Helper.index(client);
                            return client;
                        } catch (final Exception e) {
                            throw MTronException.of(e);
                        }
                    })).create();

    /**
     * Is a {@code protocol} reference the mtron facade ({@code mtron_ws})? The reference resolves through
     * instruction memory — the same lookup the route table uses — so {@code protocol => mtron_ws} and its full
     * tid name the same facade. mtron is the only protocol surface today; more facades extend this dispatch.
     */
    private static boolean isMtronFacade(final Obj protocol) {
        final Obj resolved = protocol.isUri() ? Machine.read(protocol.uriValue()) : protocol;
        return resolved.isType() && mtron_wsHandler.WS_MTRON_HANDLER_TID.equals(resolved.asType().vid());
    }

    /**
     * Does a client rec wear the mtron protocol hat ({@code protocol => mtron_ws})? The ws_client constructor installs
     * the facade on a rec that does ({@link mtron_wsHandler#install}), and its client then answers the far side's
     * requests, so it reads every frame with full write semantics ({@code WebSocketRecClient.onMessage}).
     */
    static boolean wearsMtronHat(final Rec rec) {
        final Obj protocol = rec.jvm().get(uri(PROTOCOL));
        return null != protocol && !protocol.isNoObj() && isMtronFacade(protocol);
    }

    public static final Type WS_SERVER_TYPE = TypeBuilder.build()
            .tid(REC_TID)
            .vid(WS_SERVER_TID)
            .isaPredicate(rec(
                    // the socket to serve, scheme://host:port
                    uri(HOST).maybe().asUri(), URI_TYPE,
                    // this machine's routes on the socket: <path> => handler (a uri, a type, or an instruction)
                    uri(ROUTE).maybe().asUri(), rec(URI_TYPE, T(ALL)),
                    // the socket bound, and whether it is still served — the server's own fields once constructed
                    uri(ORIGIN).maybe().asUri(), URI_TYPE,
                    uri(STATUS).maybe().asUri(), BOOL_TYPE))
            .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(WS_SERVER_TID),
                    lst(T(REC_TID)), (_, inst) -> wsServer.of(inst.arg(0).asRec()))).create();

    /**
     * a connection's lot: the route it took, its session, the inbound peer it minted there (null when declined), and
     * the threads evaluating its frames (none between frames, and none once a hang-up has interrupted them)
     */
    private record Session(Route route, WebSocketRec rec, fURI peer, Set<Thread> evaluating) {
    }

    /**
     * The lane a ws route value resolves to — the handler ladder every connection walks:
     * <ol>
     *   <li>{@code MCP} — an {@code mcp_server} type (materialized into an instance) or an {@code mcpServer}
     *   instance, wrapped in {@code mcp_wsHandler};</li>
     *   <li>{@code CONSTRUCT} — a handler {@code Type} with a constructor, built per connection;</li>
     *   <li>{@code RETAG} — a handler {@code Type} without a constructor, re-tagged;</li>
     *   <li>{@code NONE} — anything else, which the session reports as {@code "websocket handler type required"}.</li>
     * </ol>
     */
    public record RouteLane(Kind kind, Obj target) {

        public enum Kind {MCP, CONSTRUCT, RETAG, NONE}

        static RouteLane of(final Kind kind, final Obj target) {
            return new RouteLane(kind, target);
        }

        /**
         * The lane a route value selects for the connection it will serve. A
         * websocket has no per-request uri, only a handshake, so the handshake uri is the lhs: a templated value
         * addresses per connection, and an untemplated one ({@code null} request) resolves as it stands. Resolved in
         * the machine the caller stands in, which is the machine whose route it is.
         */
        public static RouteLane resolve(final Obj routeValue, final fURI request) {
            return classify(webHelper.align(null, routeValue, request));
        }

        private static RouteLane classify(final Obj resolved) {
            Obj target = resolved.isUri() ? Machine.read(resolved.uriValue()) : resolved;
            // ── mcp_server type: materialize it so the transport wraps it ──
            if (target.isType() && target.asType().hasConstructor()
                    && Obj.Helper.specificType(target).test(MCP_SERVER_TYPE)) {
                final Obj mcp = target.asType().constructor().apply(rec(mutableMap())).as();
                if (!mcp.isFail())
                    target = mcp;
            }
            if (target instanceof mcpServer)
                return RouteLane.of(RouteLane.Kind.MCP, target);
            if (target.isType() && target.asType().hasConstructor())
                return RouteLane.of(RouteLane.Kind.CONSTRUCT, target);
            if (target.isType())
                return RouteLane.of(RouteLane.Kind.RETAG, target);
            return RouteLane.of(RouteLane.Kind.NONE, target);
        }
    }

    /**
     * The session a lane builds for one connection: an mcp server wrapped in its
     * websocket carrier, a handler type constructed with the request's {@code ?in}/{@code ?out} serializers, or a
     * constructor-less type re-tagged. Throws when the lane is no websocket handler or its construction fails.
     */
    public static WebSocketRec session(final RouteLane lane, final fURI request, final fURI vid) {
        if (RouteLane.Kind.NONE == lane.kind())
            throw MTronException.of("websocket handler type required: %s", lane.target());
        final Obj wsHandlerType = lane.target();
        final Rec config = rec(mutableMap(
                uri(IN), request.hasQ(IN) ? uri(request.q(IN)) : noobj(),
                uri(OUT), request.hasQ(OUT) ? uri(request.q(OUT)) : noobj()));
        final Obj handler;
        if (wsHandlerType instanceof mcpServer) {
            handler = new mcp_wsHandler(new LinkedHashMap<>(((mcpServer) wsHandlerType).jvm()), WS_MCP_HANDLER_TID, vid);
        } else if (wsHandlerType.asType().hasConstructor()) {
            handler = wsHandlerType.asType().constructor().apply(config).as();
            if (handler.isFail())
                throw MTronException.of("wsserver construction failed: {{y}}%s{{X}}", handler);
            handler.self(handler.jvm(), wsHandlerType.vid(), vid);
        } else {
            handler = config.tid(wsHandlerType.vid()).vid(vid);
        }
        if (handler.isNoObj() || handler.isFail())
            throw MTronException.of("wsserver construction failed: {{y}}%s{{X}}", handler);
        return handler.as();
    }

    private final Socket socket;
    /**
     * the live sessions, by their sockets — held here, never at an address
     */
    private final Map<WebSocket, Session> sessions = Collections.synchronizedMap(new IdentityHashMap<>());

    /**
     * The threads currently evaluating one of this socket's frames, across its sessions — the set a hang-up
     * interrupts. Each session evaluates its frames on threads of its own (a program runs on the system executor's
     * pooled platform thread), so a session in flight contributes exactly its own.
     */
    Set<Thread> evaluating() {
        final Set<Thread> threads = new LinkedHashSet<>();
        synchronized (this.sessions) {
            for (final Session session : this.sessions.values())
                threads.addAll(session.evaluating());
        }
        return threads;
    }

    private wsServer(final Map<Obj, Obj> jvm, final Network owner) {
        super(jvm, WS_SERVER_TID, owner);
        this.socket = new Socket(this.at(ORIGIN).uriValue());
    }

    @Override
    public fURI protocol() {
        return f("ws");
    }

    /**
     * A ws_server from its configuration — {@code ws_server::[origin=>...,route=>...]} in Java: this machine's routes,
     * served through the socket at {@code origin} ({@link #serve}).
     */
    public static Listener of(final Rec config) {
        final Obj origin = config.at(uri(ORIGIN));
        if (!origin.isUri())
            throw MTronException.of("a ws_server serves a socket: origin=><ws://origin:port> required, got %s", origin);
        final Obj routes = config.at(uri(ROUTE));
        return serve(origin.uriValue(), routes.isRec() ? routes.asRec() : rec());
    }

    /**
     * SERVE {@code routes} through the socket at {@code origin}, from the current machine: join the socket a machine at
     * or above this one already holds, or bind it here. Answers the server.
     */
    public static Listener serve(final fURI origin, final Rec routes) {
        final Network level = Machine.current().network();
        final Listener held = Network.Helper.listenerFor(origin);
        if (null != held) {
            Network.Helper.listen(level, held, routes);
            return held;
        }
        final Map<Obj, Obj> jvm = new LinkedHashMap<>();
        jvm.put(uri(ORIGIN), uri(origin));
        // a route table is ABSENT rather than empty while the machine serves nothing through it (listener::T)
        if (!routes.isEmpty())
            jvm.put(uri(ROUTE), routes);
        final wsServer server = new wsServer(jvm, level);
        try {
            Network.Helper.listen(level, server, routes);
        } catch (final RuntimeException e) {
            // a socket bound for routes that cannot be served is released with them
            server.close();
            throw e;
        }
        return server;
    }

    /**
     * THE CO-MOUNT — the inbound half of a dial, held by the machine whose route the connection took: its peer, keyed
     * by the remote socket and the route it dialed, with the session as
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
                LOG.debug("no route for %s on %s", request, this.origin());
                conn.close(4004, "no route for " + request);
            }
            return;
        }
        final Route taken = route.get();
        as(taken.machine(), () -> {
            final WebSocketRec session = session(RouteLane.resolve(taken.target(), request), request, null);
            session.setWebSocket(conn);
            // ADOPTED BY THE SOCKET THAT SERVED IT: the session's frames are counted in this server's analyzer, at the
            // wire address its route answers (the handshake path under the socket's origin)
            session.servedBy(this, request.origin(this.origin()).qLess());
            final Set<Thread> evaluating = ConcurrentHashMap.newKeySet();
            this.sessions.put(conn, new Session(taken, session, null, evaluating));
            session.onOpen(conn, handshake);
            final fURI peer = this.admit(taken, conn, session);
            if (null != peer)
                this.sessions.put(conn, new Session(taken, session, peer, evaluating));
            Analyzer.Helper.set(this.analyzer(), CONNECTIONS, this.sessions.size());
        });
    }

    private fURI admit(final Route route, final WebSocket conn, final WebSocketRec session) {
        try {
            final InetSocketAddress remote = conn.getRemoteSocketAddress();
            // the full connection uri: the route it dialed and its ?q, at the remote socket
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
        // A HANG-UP ENDS THE EVALUATION OF ITS FRAMES (2026-11-16): nothing else ends an endless evaluation (an open
        // span's walk asks its thread once a run), and their answers have no one to reach now. Every thread still
        // evaluating one, the socket's worker or a program's own thread, is interrupted under the lock evaluate() ends
        // under, so the interrupt reaches that frame and never the next one a worker takes. A connection closed by its
        // own evaluation interrupts nothing.
        synchronized (session.evaluating()) {
            for (final Thread evaluating : List.copyOf(session.evaluating()))
                if (Thread.currentThread() != evaluating) {
                    session.evaluating().remove(evaluating);
                    evaluating.interrupt();
                }
        }
        // A HANG-UP IS A DELETE: a write of noobj at the wire address the session served — the analq's write/delete
        // subset, counted and timed like any other write through the socket
        QProc.Layer.write(this, session.rec().wire(), noobj(), () -> noobj());
        Analyzer.Helper.set(this.analyzer(), CONNECTIONS, this.sessions.size());
        as(session.route().machine(), () -> {
            session.rec().onClose(conn, code, reason, remote);
            // the inbound peer dies with its socket: reach is a capability, retracted with the connection
            if (null != session.peer())
                session.route().level().drop(session.peer());
        });
    }

    private void message(final WebSocket conn, final String message) {
        final Session session = this.sessions.get(conn);
        if (null == session)
            return;
        // A PROGRAM IS EVALUATED OFF THE SOCKET'S WORKER (2026-11-21), on a thread of its own. On the worker, a frame
        // waiting on another machine held its connection and every frame queued behind it: three levels of a tree over
        // peers that dial each other send an interior lane and then a leaf down one connection, the leaf behind the lane
        // that, through the other connection, waits for it. A program answers under the tag its request carries
        // (WebSocketObj#sendRecv), so a connection's programs may answer in any order, and a console sends its next line
        // once the last is answered. The thread is one of the system executor's pooled platform threads, not a virtual
        // one: a program that computes without blocking keeps a virtual thread's carrier, so as many runaway programs as
        // there are cores would stall every virtual thread in the VM, where a platform thread is time-sliced. It goes
        // back to the pool warm (a per-thread cache outlives one frame) and is listed under /sys/thread/executor while it
        // runs. AN MCP REQUEST IS A PROGRAM TOO (2026-11-22): its tools/call evaluates mtron (eval_mtron) that waits on
        // other machines exactly as a frame does, the same tree deadlocked on the worker, and MCP answers each request
        // under its own id, so a connection's requests may answer in any order as well. Any other session's frames keep
        // their order, on the worker.
        if (session.rec() instanceof mtron_wsHandler program && program.evaluates()
                || session.rec() instanceof mcp_wsHandler)
            ThreadExecutor.instance().execute(() -> guard(conn, () -> this.evaluate(conn, session, message)));
        else
            this.evaluate(conn, session, message);
    }

    /**
     * One frame, evaluated as the machine whose route the connection took, on the thread that runs it.
     */
    private void evaluate(final WebSocket conn, final Session session, final String message) {
        session.evaluating().add(Thread.currentThread());
        try {
            // a hang-up between the lookup and here found no thread to interrupt, so the session is asked for again
            if (session == this.sessions.get(conn))
                as(session.route().machine(), () -> {
                    // THE WIRE COUNTS THE FRAME AS IT ARRIVES, before any of it runs: an mtron session evaluates its
                    // frame before on_message, and an mcp session answers it without on_message, so a count in the
                    // session's handler came after the program, or never. It counts on the wire of the machine whose
                    // route it took. The whole answer is then a READ at that route in this socket's analyzer: timed
                    // from here, and counted with its latency once the answer has been given.
                    // A REPLY TO AN OBSERVER'S ROUND TRIP over this connection is the observer's work, as a client's is
                    // (Analyzer.REPLY: this thread's alone, never carried into what the frame starts)
                    final boolean outer = Analyzer.REPLY.get();
                    Analyzer.REPLY.set(outer || WebSocketObj.observed(session.rec()));
                    try {
                        Analyzer.Helper.incr(Machine.current().network().analyzer(), Network.WIRE_RECV, 1L);
                        QProc.Layer.read(this, session.rec().wire(), () -> {
                            session.rec().onMessage(conn, message);
                            return noobj();
                        });
                    } finally {
                        Analyzer.REPLY.set(outer);
                    }
                });
        } finally {
            // THE HANG-UP'S INTERRUPT ENDS WITH THE EVALUATION: the worker serves the socket's other connections, and
            // Java-WebSocket's worker loop exits on an interrupt left set
            synchronized (session.evaluating()) {
                if (!session.evaluating().remove(Thread.currentThread()))
                    Thread.interrupted();
            }
        }
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
        conn.send(String.format("metatron ws_server at {{b}}%s{{X}}\n", this.origin()));
        conn.send("\t{{y}}available routes{{X}}:\n");
        for (final Network level : this.sharers()) {
            final Obj entry = level.listener(this.origin().pathEncoding());
            final Obj routes = entry.isRec() ? entry.asRec().at(uri(ROUTE)) : noobj();
            if (routes.isRec())
                conn.send(Highlighter.format(routes));
        }
        conn.closeConnection(1000, "end transmission");
    }

    private static InetSocketAddress address(final fURI origin) {
        if (null == origin.host() || origin.port() < 0)
            throw MTronException.of("a ws_server binds a origin and a port: %s", origin);
        return new InetSocketAddress(origin.host(), origin.port());
    }

    private static fURI request(final WebSocket conn) {
        final String descriptor = null == conn.getResourceDescriptor() ? "/" : conn.getResourceDescriptor();
        return f(descriptor.startsWith("/") ? descriptor : "/" + descriptor);
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

        private Socket(final fURI origin) {
            super(address(origin));
            if (!Machine.loaded())
                throw MTronException.of("unable to start %s: no machine is loaded", origin);
            this.setReuseAddr(true);
            this.setDaemon(true);
            this.start();
            this.awaitStart();
            LOG.info("ws_server listening: %s", origin);
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

        /**
         * A SPARE BUFFER PER CONNECTION (2026-11-16). The selector reads into a pooled buffer and the connection's
         * worker holds it until its callback returns, so a frame under evaluation holds one. The library pools one per
         * connection, so a lone connection's endless evaluation left the selector none to read the client's hang-up
         * with: the session never heard it, and the socket served no one. Two per connection, to the library's cap of
         * two per worker and one, always leave the selector one.
         */
        @Override
        protected void allocateBuffers(final WebSocket conn) throws InterruptedException {
            super.allocateBuffers(conn);
            super.allocateBuffers(conn);
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
