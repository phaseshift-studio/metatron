/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.lang.sys.router.impl;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Fail;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.sys.router.Cluster;
import studio.phaseshift.metatron.lang.sys.router.IOStat;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.util.serial.ObjByteBufferSerializer;
import studio.phaseshift.metatron.lang.util.serial.ObjSerializer;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Threadable;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.sys.router.impl.MRouter.ROUTER_TID;

public class MServer extends WebSocketServer implements Cluster, Closeable, Obj, Threadable {

    public static final fURI MSERVER_TID = ROUTER_TID.extend("server");

    protected final fURI host;
    protected final ObjSerializer<?> serializer;
    protected final Map<fURI, MConnection> cluster = new HashMap<>();
    protected GraphittyLogger LOG;
    protected Thread thread;
    protected List<FutureObj<?>> futures = new ArrayList<>();
    private IOStat ioStat = new IOStat();

    public MServer(final fURI host) {
        super(new InetSocketAddress(host.host(), host.port()));
        this.host = host;
        LOG = Graphitty.log(this);
        this.serializer = MRouter.SERIALIZERS.get(ObjByteBufferSerializer.OBJ_BYTE_BUFFER_SERIALIZER_TID);
    }

    @Override
    public fURI host() {
        return this.host;
    }

    public void start() {
        LOG = Router.global().logger();
        try {
            this.thread = new Thread(this);
            this.thread.start();
            LOG.trace("server started: %s", this.getAddress());
            BootLoader.ARGS.at(Tokens.CLUSTER).elements().filter(o -> !o.isNoObj()).forEach(n -> {
                try {
                    final MConnection client = MClient.of(n.uriValue(), this.serializer);
                    this.cluster.put(n.uriValue(), client);
                } catch (final Exception e) {
                    LOG.error("unable to connect to cluster node {{b}}%s{{/b}}", n.uriValue());
                }
            });
            Router.global().write(
                    Router.global().vid().extend(Tokens.CLUSTER),
                    lst((List) this.cluster.values().stream().map(x -> x.remoteHost().toUri()).toList()));
        } catch (final Exception e) {
            // do nothing
        }

    }

    public <T> ObjSerializer<T> getSerializer() {
        return (ObjSerializer<T>) this.serializer;
    }

    @Override
    public Thread getThread() {
        return this.thread;
    }

    @Override
    public void close() {
        LOG.info("closing %s node {{b}}%s{{/b}}", Graphitty.sillyPrint("mtron", true, true), this.host);
        try {
            this.cluster.values().stream().toList().forEach(MConnection::close);
            super.stop(1000);
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    public IOStat stats() {
        return this.ioStat;
    }

    @Override
    public void onOpen(final WebSocket ws, final ClientHandshake handshake) {
        ws.setAttachment(f("ws://" + ws.getRemoteSocketAddress()));
        LOG.debug("new connection from %s", ws.getRemoteSocketAddress());
        // broadcast("new connection: " + handshake.getResourceDescriptor()); //This method sends a message to all clients connected
    }

    @Override
    public void onClose(final WebSocket conn, final int code, final String reason, final boolean remote) {
        LOG.debug("closed %s with exit code %s [reason: %s]", this.cluster.remove(conn.<fURI>getAttachment()), code, reason);
    }

    @Override
    public void onMessage(final WebSocket conn, final String message) {
        LOG.trace("received from %s string [length:%d]", conn.getAttachment(), message.length());
        this.onMessage(conn, ByteBuffer.wrap(message.getBytes()));
    }

    @Override
    public void onMessage(final WebSocket conn, final ByteBuffer message) {
        LOG.trace("received from %s byte buffer [length:%d]", conn.getAttachment(), message.array().length);
        this.ioStat.incrTotalByteReceived(message.array().length);
        try {
            final Obj obj = this.serializer.readBytes(message);// this.serializer.read(message);
            this.onObj(conn, obj);
        } catch (final Exception e) {
            this.onObj(conn, fail(e));
        }

    }


    public void onObj(final WebSocket conn, final Obj obj) {
        try {
            LOG.trace("processing %s for {{b}}%s{{/b}}", obj, conn.getAttachment());
            Obj result = obj.apply();
            result = objs(result.stream().map(x -> x.vid() == null ? x : x.vid(this.host().extend(x.vid()))));
            // final String tag = obj.vid() != null ? obj.vid().queryValue(f("tag"), String.class, null) : null;
            //if (tag != null) {
            //    fURI rvid = result.vid() == null ? f("/usr/temp?tag=" + tag) : result.vid().query("tag", tag);
            //    result = result.vid(rvid);
            //     LOG.info("obj tagged: %s", result);
            // }
            final ByteBuffer bytes = this.serializer.writeBytes(result);
            conn.send(bytes);
            this.ioStat.incrTotalByteSent(bytes.array().length);
            //this.sendObj(conn, result);
            if (result.isFail())
                this.onError(conn, result.<Fail>as().jvmAs());

        } catch (final Exception e) {
            final ByteBuffer bytes = this.serializer.writeBytes(fail(e));
            conn.send(bytes);
            this.ioStat.incrTotalByteReceived(bytes.array().length);
            this.onError(conn, e);
        }
    }

    @Override
    public void onError(final WebSocket conn, final Exception ex) {
        LOG.error("an error occurred on connection %s: %s", null == conn ? "<not connected>" : conn.getAttachment(), ex);
    }

    @Override
    public void onStart() {
        LOG.info("starting %s node {{b}}%s{{/b}}", Graphitty.sillyPrint("mtron", true, true), this.host);
    }

    @Override
    public WebSocketServer jvm() {
        return this;
    }

    @Override
    public fURI tid() {
        return MSERVER_TID;
    }

    @Override
    public fURI vid() {
        return Router.global().vid().extend("server");
    }

    @Override
    public MServer clone(final Object jvm, final fURI tid, final fURI vid) {
        return this;
    }

    @Override
    public Obj clone() {
        return this;
    }

    @Override
    public MServer self(final Object jvm, final fURI tid, final fURI vid) {
        return this;
    }

    @Override
    public Map<fURI, MConnection> nodes() {
        return this.cluster;
    }

   /* public class MServerClient implements MConnection {
        private final WebSocket ws;

        public MServerClient(final WebSocket ws) {
            this.ws = ws;
        }

        @Override
        public void sendObj(final Obj obj) {
            this.ws.send(serializer.write(obj));
        }

        @Override
        public <O extends Obj> FutureObj<O> sendRecvObj(final Obj obj) {
            final FutureObj<O> future = new FutureObj<>("abc");
            futures.add(future);
            this.ws.send(serializer.write(obj));
            return future;
        }

        @Override
        public void close() {
            this.ws.close();
        }

        @Override
        public fURI remoteHost() {
            return this.ws.getAttachment();
        }
    }*/
}