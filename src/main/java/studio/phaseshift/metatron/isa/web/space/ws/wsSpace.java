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
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.web.type.Content;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SPACE_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.http.httpSpace.CONFIG;
import static studio.phaseshift.metatron.isa.web.webInstSet.CONTENT_TYPE;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class wsSpace extends AbstractSpace<WebSocketServer> {

    public static final fURI WS_SPACE_TID = WEB_ISA_TID.extend(SPACE).extend("wsspace");
    public static final fURI WS_WEBSOCKET_TID = WS_SPACE_TID.extend("websocket");
    public static final fURI WS_SERVER_TID = WS_SPACE_TID.extend("wsserver");
    public static final fURI WS_CLIENT_TID = WS_SPACE_TID.extend("wsclient");

    public static final Type WS_WEBSOCKET_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(WS_WEBSOCKET_TID)
            .isaPredicate(rec(
                    uri(IN).maybe().asUri(), isa_(CONTENT_TYPE).orElse(uri(Content.ContentType.APPLICATION_MTRON.value)),
                    uri(OUT).maybe().asUri(), isa_(CONTENT_TYPE).orElse(uri(Content.ContentType.APPLICATION_MTRON.value)),
                    uri(SEND).maybe().asUri(), INST_TYPE,
                    uri(ON_OPEN).maybe(), T(ALL),
                    uri(ON_ERROR).maybe(), T(ALL),
                    uri(ON_MESSAGE).maybe(), T(ALL),
                    uri(ON_CLOSE).maybe(), T(ALL))).create();


    public static final Type WS_SERVER_TYPE = Type.Builder.build()
            .tid(WS_WEBSOCKET_TID)
            .vid(WS_SERVER_TID)
            .constructor(instC(WS_SERVER_TID.extend(CTOR).dom(ALL.maybe()).rng(WS_WEBSOCKET_TID),
                    lst(T(REC_TID)), (lhs, inst) ->
                            new WebSocketRec(inst.arg(0).asRec().jvm(), inst.arg(0).vid()))).create();

    public static final Type WS_CLIENT_TYPE = Type.Builder.build()
            .tid(WS_WEBSOCKET_TID)
            .vid(WS_CLIENT_TID)
            .constructor(instC(WS_CLIENT_TID.extend(CTOR).dom(ALL.maybe()).rng(WS_WEBSOCKET_TID),
                    lst(T(REC_TID)), (lhs, inst) -> {
                        final WebSocketRecClient client = new WebSocketRecClient(new WebSocketRec(inst.arg(0).asRec().jvm(), inst.arg(0).vid()));
                        return client;
                    })).create();

    public static final Type WS_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(WS_SPACE_TID)
            .isaPredicate(rec(
                    uri(HOST), URI_TYPE,
                    uri(PATTERN), URI_TYPE,
                    uri(ROUTE), REC_TYPE))
            .constructor(instC(WS_SPACE_TID.extend(CTOR).dom(WS_SPACE_TID).rng(WS_SPACE_TID),
                    lst(T(REC_TID, isa_(CONFIG))), (lhs, inst) -> wsSpace.of(inst.arg(0).recValue(), inst.arg(0).vid()))).create();

    private final memSpace cache;

    protected wsSpace(final WebSocketServer server, final Map<Obj, Obj> config, final fURI vid) {
        super(server, config, WS_SPACE_TID, vid);
        this.cache = memSpace.of(rec(uri(PATTERN), config.getOrDefault(uri(PATTERN), noobj())), null);
        /*if (null != vid)
            this.at(ROUTE, this.at(ROUTE).orElse(rec0())
                    .plus(rec(this.pattern.host(null).scheme(null).retractPattern().extend("wsmtron").toUri(), WS_MTRON_SERVER_TYPE))
                    .plus(rec(this.pattern.host(null).scheme(null).retractPattern().extend("wsmcp").toUri(), WS_MCP_SERVER_TYPE)), MUTABLE);*/
    }

    public static wsSpace of(final Map<Obj, Obj> config, final fURI vid) {
        try {
            final mWebSocketServer server = new mWebSocketServer(config.get(uri(HOST)).uriValue().host(), config.get(uri(HOST)).uriValue().port());
            final wsSpace ws = new wsSpace(server, config, vid);
            server.setSpace(ws);
            server.onStart();
            return ws;
        } catch (final Exception e) {
            throw MTronException.of("unable to start ws server: %s", e);
        }
    }

    @Override
    public void close() {
        LOG.debug("closing %s node {{b}}%s{{/b}}", Graphitty.sillyPrint("mtron", true, true), this);
        try {
            //this.cluster.values().stream().toList().forEach(MConnection::close);
            this.sjvm().stop(1000, "server shutdown");
        } catch (final InterruptedException e) {
            LOG.info("%s interrupted successfully", this);
        } finally {
            //  this.running.set(false);
        }
    }

    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        return this.cache.directReader();
    }

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return this.cache.directWriter();
    }


    public static class mWebSocketServer extends WebSocketServer {

        protected wsSpace space;
        protected final fURI baseURI;
        protected final AtomicInteger counter = new AtomicInteger(0);

        public mWebSocketServer(final String host, final int port) {
            super(new InetSocketAddress(host, port));
            this.setReuseAddr(true);
            this.setDaemon(true);
            this.baseURI = f("/").scheme("ws").host(host).port(port);
            if (Router.loaded()) {
                try {
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        try {
                            this.stop(1000, "server shutdown");
                        } catch (final Exception e) {
                            Graphitty.log(this).error(e);
                        }
                    }));
                    this.start();
                    Graphitty.log(this).info("server started: %s", this.getAddress());

                } catch (final Exception e) {
                    Graphitty.log(this).error(e);
                }
            } else {
                throw MTronException.of("unable to start server as router not loaded");
            }
        }

        public void setSpace(final wsSpace space) {
            this.space = space;
        }

        protected WebSocketObj createServer(final WebSocket conn) {
            try {
                if (null == conn)
                    return null;
                final fURI routePath = f(conn.getResourceDescriptor().startsWith("/")
                        ? conn.getResourceDescriptor()
                        : "/" + conn.getResourceDescriptor());
                final fURI wsServerTypeVID = Space.Helper.routeFromSpace(routePath.qLess(), this.space.routes());
                final Obj serverType = Router.global().read(wsServerTypeVID);
                if (!serverType.isType())
                    throw MTronException.of("ws server type required: %s at %s", serverType, wsServerTypeVID);
                this.space.LOG.info("starting session with ws server: %s", serverType);
                final fURI vid = this.baseURI.extend(routePath.qLess()).extend(this.counter.getAndIncrement() + "");

                // Delegate construction to the metatron type system:
                // rec(map, tid, vid) -> MObj.of() -> Obj.Helper.construct() which looks up
                // the Type at wsServerTypeVID in the Router and calls its constructor if present.
                // This allows user-defined wsserver::T subtypes to be instantiated correctly.
                // Pass an empty map — the type's constructor applies its own defaults for IN/OUT.
                final Obj server = rec(mutableMap(
                                uri(IN), routePath.hasQ(IN) ? uri(routePath.q(IN)) : noobj(),
                                uri(OUT), routePath.hasQ(OUT) ? uri(routePath.q(OUT)) : noobj()),
                        wsServerTypeVID, vid);
                if (server.isNoObj() || server.isFail()) {
                    conn.close(4000, "unable to construct server " + server);
                    throw MTronException.of("client {{b}}%s{{X}} wsserver construction failed: {{y}}%s{{X}}", conn.getRemoteSocketAddress(), server);
                }
                return (WebSocketRec) server;
            } catch (final Exception e) {
                throw MTronException.of("unable to create ws server for %s: %s", conn.getRemoteSocketAddress(), e);
            }
        }


        protected Optional<WebSocketObj> getSession(final WebSocket conn) {
            if (null == conn)
                return Optional.empty();
            final Obj session = this.space.cache.read(conn.<fURI>getAttachment());
            if (session.isNoObj()) {
                conn.closeConnection(1000, "no session found at " + session);
                return Optional.empty();
            }
            return Optional.of((WebSocketObj) session);
        }

        private boolean ignore(final WebSocket conn) {
            return null == conn || conn.isClosed() || conn.getAttachment() == null;
        }

        @Override
        public void onOpen(final WebSocket conn, final ClientHandshake handshake) {
            try {
                this.space.logger().info("creating new websocket server session w/ %s over %s", conn.getRemoteSocketAddress(), conn.getResourceDescriptor());
                if (conn.getResourceDescriptor().equals("/")) {
                    conn.send(String.format("metatron wsspace at %s\n", this.space.vid().toString()));
                    for (final String line : CommonUtil.getHeader(CommonUtil.HEADER_FILE, null, true).split("\n"))
                        conn.send(line);
                    conn.send("available servers:\n");
                    conn.send(this.space.at(ROUTE).toString());
                    conn.closeConnection(1000, "end transmission");
                } else {
                    final WebSocketObj server = this.createServer(conn);
                    if (null != server) {
                        server.setWebSocket(conn);
                        this.space.cache.write(server.getThisVID(), server);
                        server.onOpen(conn, handshake);
                    }
                }
            } catch (final Exception e) {
                this.space.LOG.error("error on new connection with %s: %s", conn.getRemoteSocketAddress(), e);
                conn.closeConnection(3000, "error on connection: " + e);
                // Do NOT re-throw — propagating an exception from an event handler
                // to the WebSocketWorker thread kills the entire server.
            }
        }


        @Override
        public void onClose(final WebSocket conn, final int code, final String reason, final boolean remote) {
            try {
                if (ignore(conn)) return;
                final WebSocketObj session = this.getSession(conn).orElseThrow(() -> MTronException.of("no session found for %s", conn));
                session.onClose(conn, code, reason, remote);
                this.space.cache.write(session.getThisVID(), noobj());
            } catch (final Exception e) {
                this.space.LOG.error(e);
            }
        }


        @Override
        public void onMessage(final WebSocket conn, final String message) {
            try {
                if (ignore(conn)) return;
                final WebSocketObj session = this.getSession(conn).orElseThrow(() -> MTronException.of("no session found for %s", conn));
                session.onMessage(conn, session.getIO().input().fromBytes(message));
            } catch (final Exception e) {
                this.space.LOG.error(e);
            }
        }

        @Override
        public void onMessage(final WebSocket conn, final ByteBuffer message) {
            try {
                if (ignore(conn)) return;
                final WebSocketObj session = this.getSession(conn).orElseThrow(() -> MTronException.of("no session found for %s", conn));
                session.onMessage(conn, session.getIO().input().fromBytes(message.array()));
            } catch (final Exception e) {
                this.space.LOG.error(e);
            }
        }


        @Override
        public void onError(final WebSocket conn, final Exception ex) {
            try {
                if (ignore(conn)) return;
                final WebSocketObj session = this.getSession(conn).orElseThrow(() -> MTronException.of("no session found for %s", conn));
                session.onError(conn, ex);
            } catch (final Exception e) {
                this.space.LOG.error(e);
            }
        }

        @Override
        public void onStart() {

        }
    }
}


