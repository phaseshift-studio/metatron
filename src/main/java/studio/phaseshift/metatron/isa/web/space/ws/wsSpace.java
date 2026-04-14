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
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.MTronException;

import java.net.InetSocketAddress;
import java.util.Map;
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
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

public class wsSpace extends AbstractSpace<WebSocketServer> {

    public static final fURI WS_SPACE_TID = WEB_ISA_TID.extend(SPACE).extend("ws");
    public static final fURI WS_ENDPOINT_TID = WEB_ISA_TID.extend("space/ws_endpoint");
    public static final Type WS_ENDPOINT_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(WS_ENDPOINT_TID)
            .isaPredicate(rec(
                    uri(WS), URI_TYPE,
                    uri(PATTERN), URI_TYPE,
                    uri(CLIENT).maybe(), lst(URI_TYPE),
                    uri(ON_OPEN).maybe(), T(ALL),
                    uri(ON_ERROR).maybe(), T(ALL),
                    uri(ON_MESSAGE).maybe(), T(ALL),
                    uri(ON_CLOSE).maybe(), T(ALL)))
            .constructor(instC(mInstSet.M_ISA_INST_TID.dom(ALL.maybe()).rng(WS_ENDPOINT_TID),
                    lst(T(REC_TID)), (lhs, inst) -> new wsEndPoint(inst.arg(0).asRec().jvm(), inst.arg(0).vid()))).create();

    public static final Type WS_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(WS_SPACE_TID)
            .isaPredicate(rec(uri(HOST), URI_TYPE,
                    uri(PATTERN), URI_TYPE,
                    uri(ROUTE), REC_TYPE))
            .constructor(instC(mInstSet.M_ISA_INST_TID.dom(ALL.maybe()).rng(WS_SPACE_TID),
                    lst(T(REC_TID, isa_(CONFIG))), (lhs, inst) -> wsSpace.of(inst.arg(0).recValue(), inst.arg(0).vid()))).create();

    protected wsSpace(final WebSocketServer server, final Map<Obj, Obj> config, final fURI vid) {
        super(server, config, WS_SPACE_TID, vid);
        if (null != vid)
            this.at(ROUTE, this.at(ROUTE).orElse(rec0()).plus(rec("mtron", new mtronEndpoint(vid.extend("mtron")))), MUTABLE);
    }

    public static wsSpace of(final Map<Obj, Obj> config, final fURI vid) {
        final mWebSocketServer server = new mWebSocketServer(config.get(uri(HOST)).uriValue().host(), config.get(uri(HOST)).uriValue().port());
        final wsSpace ws = new wsSpace(server, config, vid);
        server.setSpace(ws);
        ws.start();
        return ws;
    }

    public void start() {
        if (Router.loaded()) {
            try {
                this.sjvm().setReuseAddr(true);
                this.sjvm().setDaemon(true);
                Runtime.getRuntime().addShutdownHook(new Thread(this::close));
                // this.running.set(true);

                this.sjvm().start();
                LOG.trace("server started: %s", this.sjvm().getAddress());

            } catch (final Exception e) {
                // do nothing
            }
            // Native Metatron protocol (binary Obj serialization)
                /*nativeProtocolHandler = new NativeMetatronProtocolHandler(serializer, mutableMap(this.cluster), this.vid().extend("protocol/native"));
                protocolHandlers.add(nativeProtocolHandler);

                // MCP protocol (JSON-RPC 2.0)
                mcpProtocolHandler = new McpProtocolHandler(this.vid().extend("protocol/mcp"));
                protocolHandlers.add(mcpProtocolHandler);

                LOG.info("Initialized %d protocol handlers: %s",
                        protocolHandlers.size(),
                        protocolHandlers.stream().map(h -> h.tid().name()).toList());*/
        } else {
            throw MTronException.of("unable to start server as router not loaded");
        }
    }

    @Override
    public void close() {
        LOG.debug("closing %s node {{b}}%s{{/b}}", Graphitty.sillyPrint("mtron", true, true), this);
        try {
            // Shutdown all protocol handlers
            // protocolHandlers.forEach(MServerProtocolHandler::shutdown);

            //this.cluster.values().stream().toList().forEach(MConnection::close);
            this.sjvm().stop(1000, "server shutdown");
        } catch (final InterruptedException e) {
            LOG.info("%s interrupted successfully", this);
        } finally {
            //  this.running.set(false);
        }
    }

    public static class wsEndPoint extends MRec {

        public wsEndPoint(final Map<Obj, Obj> map, final fURI vid) {
            this(map, WS_ENDPOINT_TID, vid);
        }

        public wsEndPoint(final Map<Obj, Obj> map, final fURI tid, final fURI vid) {
            super(map, tid, vid);
        }


        public void onOpen(final WebSocket conn, final ClientHandshake handshake) {
            this.jvm().getOrDefault(uri(ON_OPEN), noobj()).asInst().apply();
        }


        public void onClose(final WebSocket conn, final int code, final String reason, final boolean remote) {
            this.jvm().getOrDefault(uri(ON_CLOSE), noobj()).asInst().apply(rec(uri(CODE), jnt(code), uri(REASON), str(reason)));
        }


        public void onMessage(final WebSocket conn, final String message) {
            this.jvm().getOrDefault(uri(ON_MESSAGE), noobj()).asInst().apply(ObjmtronSerializer.parse(message));
        }


        public void onError(final WebSocket conn, final Exception ex) {
            this.jvm().getOrDefault(uri(ON_ERROR), noobj()).asInst().apply(fail(ex));
        }


        public void onStart() {

        }
    }

    public static class mWebSocketServer extends WebSocketServer {

        protected wsSpace space;
        protected final AtomicInteger counter = new AtomicInteger(0);

        public mWebSocketServer(final String host, final int port) {
            super(InetSocketAddress.createUnresolved(host, port));
        }

        public void setSpace(final wsSpace space) {
            this.space = space;
        }

        protected Rec getEndPoint(final WebSocket conn) {
            if (null == conn)
                return rec0();
            final fURI endpoint = f(conn.getResourceDescriptor());
            final Obj ep = this.space.read(endpoint);
            return ep.isNoObj() ? rec0() : ep.asRec();
        }

        @Override
        public void onOpen(final WebSocket conn, final ClientHandshake handshake) {
            this.space.logger().info("opening ws endpoint w/ %s", conn.getResourceDescriptor());
            final Rec ep = this.getEndPoint(conn);
            if (ep instanceof wsEndPoint)
                ((wsEndPoint) ep).onOpen(conn, handshake);
            else ep.at(uri(ON_OPEN)).apply();
        }


        @Override
        public void onClose(final WebSocket conn, final int code, final String reason, final boolean remote) {
            final Rec ep = this.getEndPoint(conn);
            if (ep instanceof wsEndPoint)
                ((wsEndPoint) ep).onClose(conn, code, reason, remote);
            else ep.at(uri(ON_CLOSE)).apply(rec(uri(CODE), jnt(code), uri(REASON), str(reason)));
        }


        @Override
        public void onMessage(final WebSocket conn, final String message) {
            final Rec ep = this.getEndPoint(conn);
            if (ep instanceof wsEndPoint)
                ((wsEndPoint) ep).onMessage(conn, message);
            else ep.at(uri(ON_MESSAGE)).apply(ObjmtronSerializer.parse(message));
        }


        @Override
        public void onError(final WebSocket conn, final Exception ex) {
            final Rec ep = this.getEndPoint(conn);
            if (ep instanceof wsEndPoint)
                ((wsEndPoint) ep).onError(conn, ex);
            else ep.at(uri(ON_ERROR)).apply(fail(ex));
        }

        @Override
        public void onStart() {
        }
    }
}


