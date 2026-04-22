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
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SPACE_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.http.httpSpace.CONFIG;
import static studio.phaseshift.metatron.isa.web.space.ws.server.wsmtronServer.WS_MTRON_SERVER_TYPE;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class wsSpace extends AbstractSpace<WebSocketServer> {

    public static final fURI WS_SPACE_TID = WEB_ISA_TID.extend(SPACE).extend("wsspace");
    public static final fURI WS_SERVER_TID = WS_SPACE_TID.extend("wsserver");
    public static final Type WS_SERVER_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(WS_SERVER_TID)
            .isaPredicate(rec(
                    uri(HOST), URI_TYPE,
                    uri(PATTERN), URI_TYPE,
                    uri(ON_OPEN).maybe(), T(ALL),
                    uri(ON_ERROR).maybe(), T(ALL),
                    uri(ON_MESSAGE).maybe(), T(ALL),
                    uri(ON_CLOSE).maybe(), T(ALL)))
            .constructor(instC(mInstSet.M_ISA_INST_TID.dom(ALL.maybe()).rng(WS_SERVER_TID),
                    lst(T(REC_TID)), (lhs, inst) -> new WSServerRec(inst.arg(0).asRec().jvm(), inst.arg(0).vid()))).create();

    public static final Type WS_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(WS_SPACE_TID)
            .isaPredicate(rec(uri(HOST), URI_TYPE,
                    uri(PATTERN), URI_TYPE,
                    uri(ROUTE), REC_TYPE))
            .constructor(instC(mInstSet.M_ISA_INST_TID.dom(WS_SPACE_TID).rng(WS_SPACE_TID),
                    lst(T(REC_TID, isa_(CONFIG))), (lhs, inst) -> wsSpace.of(inst.arg(0).recValue(), inst.arg(0).vid()))).create();

    protected wsSpace(final WebSocketServer server, final Map<Obj, Obj> config, final fURI vid) {
        super(server, config, WS_SPACE_TID, vid);
        if (null != vid)
            this.at(ROUTE, this.at(ROUTE).orElse(rec0()).plus(rec(this.pattern.host(null).scheme(null).retractPattern().extend("mtron").toUri(), WS_MTRON_SERVER_TYPE)), MUTABLE);
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

    public static class mWebSocketServer extends WebSocketServer {

        protected wsSpace space;
        protected final AtomicInteger counter = new AtomicInteger(0);
        protected final Map<WebSocket, fURI> socketToSession = new ConcurrentHashMap<>();

        public mWebSocketServer(final String host, final int port) {
            super(new InetSocketAddress(host, port));
            this.setReuseAddr(true);
            this.setDaemon(true);
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

        protected Rec createServer(final WebSocket conn) {
            if (null == conn)
                return rec0();
            final Obj serverType = this.space.at(ROUTE).orElse(rec0()).at(conn.getResourceDescriptor().substring(1));
            if (!serverType.isType())
                throw MTronException.of("unknown ws server structure: %s", serverType);
            this.space.LOG.info("found ws server: %s", serverType);
            final Obj server = serverType.asType().constructor().apply(rec(
                    uri(IN), uri(f(conn.getResourceDescriptor()).q("in")),
                    uri(OUT), uri(f(conn.getResourceDescriptor()).q("out")))
                    .vid(this.space.vid().extend(this.counter.getAndIncrement() + "")));
            ;
            if (server.isNoObj()) {
                conn.close(1000, "no server found at " + server);
                throw MTronException.of("client {{b}}%s{{X}} requested non-existent server at {{y}}%s{{X}}", conn.getRemoteSocketAddress(), server);
            }
            return server.asRec();
        }

        protected Rec getSession(final WebSocket conn) {
            if (null == conn)
                return rec0();
            final fURI session = this.socketToSession.get(conn);
            if (null == session) {
                conn.close(1000, "no session found for " + conn);
                return rec0();
            }
            final Obj sessionObj = Router.global().read(session);
            if (sessionObj.isNoObj()) {
                conn.close(1000, "no session found at " + session);
                Router.global().write(session, noobj());
                return rec0();
            }
            return sessionObj.asRec();
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
                    conn.close(1000, "end transmission");
                } else {
                    final Rec server = this.createServer(conn);
                    this.socketToSession.put(conn, server.vid());
                    if (server instanceof WSServerRec)
                        ((WSServerRec) server).onOpen(conn, handshake);
                    else server.at(uri(ON_OPEN)).apply();
                }
            } catch (final Exception e) {
                this.space.LOG.error("error on new connection with %s: %s", conn.getRemoteSocketAddress(), e);
                conn.close(3000, "error on connection: " + e);
                throw e;
            }
        }


        @Override
        public void onClose(final WebSocket conn, final int code, final String reason, final boolean remote) {
            final Rec session = this.getSession(conn);
            if (session instanceof WSServerRec)
                ((WSServerRec) session).onClose(conn, code, reason, remote);
            else session.at(uri(ON_CLOSE)).apply(rec(uri(CODE), jnt(code), uri(REASON), str(reason)));
            this.socketToSession.remove(conn);
        }


        @Override
        public void onMessage(final WebSocket conn, final String message) {
            final Rec session = this.getSession(conn);
            if (session instanceof WSServerRec)
                ((WSServerRec) session).onMessage(conn, message);
            else session.asRec().at(uri(ON_MESSAGE)).apply(ObjmtronSerializer.parse(message));
        }


        @Override
        public void onError(final WebSocket conn, final Exception ex) {
            final Rec session = this.createServer(conn);
            if (session instanceof WSServerRec)
                ((WSServerRec) session).onError(conn, ex);
            else session.at(uri(ON_ERROR)).apply(fail(ex));
        }

        @Override
        public void onStart() {

        }
    }
}


