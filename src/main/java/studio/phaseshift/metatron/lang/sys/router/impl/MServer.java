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
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjByteBufferSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.lang.jre.ObjFieldReflection;
import studio.phaseshift.metatron.lang.sys.router.Cluster;
import studio.phaseshift.metatron.util.MTronException;

import java.io.Closeable;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.mach.type.router.BasicRouter.ROUTER_TID;

public class MServer extends WebSocketServer implements Cluster, Closeable, Obj {

    public static final fURI MSERVER_TID = ROUTER_TID.extend("server");

    protected final fURI host;
    protected final ObjSerializer<?> serializer;
    @ObjFieldReflection(tid = "cluster")
    protected final Map<fURI, MConnection> cluster = new HashMap<>();
    protected GraphittyLogger LOG;
    protected List<FutureObj<?>> futures = new ArrayList<>();
    final AtomicBoolean running = new AtomicBoolean(false);
    protected final List<fURI> peers;

    public MServer(final fURI host, final List<fURI> peers) {
        super(new InetSocketAddress(host.host(), host.port()));
        this.host = host;
        this.peers = peers;
        LOG = Graphitty.log(this);
        this.serializer = new ObjByteBufferSerializer();
    }

    public boolean isRunning() {
        return this.running.get();
    }

    @Override
    public fURI host() {
        return this.host;
    }

    @Override
    public void start() {
        if (Router.loaded()) {
            try {
                super.setReuseAddr(true);
                super.setDaemon(true);
                Runtime.getRuntime().addShutdownHook(new Thread(this::close));
                this.running.set(true);
                super.start();
                LOG.trace("server started: %s", this.getAddress());
                this.peers.forEach(
                        n -> {
                            try {
                                final MConnection client = MClient.of(n, this.serializer);
                                this.cluster.put(n, client);
                            } catch (final Exception e) {
                                LOG.error("unable to connect to cluster node {{b}}%s{{/b}}", n);
                            }
                        });
                Router.global().write(
                        Router.global().vid().extend(Tokens.CLUSTER),
                        lst((List) this.cluster.values().stream().map(x -> x.remoteHost().toUri()).toList()));
            } catch (final Exception e) {
                // do nothing
            }
        } else {
            throw MTronException.of("unable to start server as router not loaded");
        }
    }

    public <T> ObjSerializer<T> getSerializer() {
        return (ObjSerializer<T>) this.serializer;
    }

    @Override
    public void close() {
        LOG.info("closing %s node {{b}}%s{{/b}}", Graphitty.sillyPrint("mtron", true, true), this.host);
        try {
            this.cluster.values().stream().toList().forEach(MConnection::close);
            super.stop(1000, "server shutdown");
        } catch (final InterruptedException e) {
            LOG.info("%s interrupted successfully", this.host);
        } finally {
            this.running.set(false);
        }
    }

    @Override
    public void onOpen(final WebSocket ws, final ClientHandshake handshake) {
        ws.setAttachment(f("ws://" + ws.getRemoteSocketAddress()));
        LOG.info("new connection from %s", ws.getRemoteSocketAddress());
        this.running.set(true);
        // broadcast("new connection: " + handshake.getResourceDescriptor()); //This method sends a message to all clients connected
    }

    @Override
    public void onClose(final WebSocket conn, final int code, final String reason, final boolean remote) {
        LOG.info("closed %s with exit code %s [reason: %s]", this.cluster.remove(conn.<fURI>getAttachment()), code, reason);
    }

    @Override
    public void onMessage(final WebSocket conn, final String message) {
        LOG.debug("received from %s string [length:%d]", conn.getAttachment(), message.length());
        this.onMessage(conn, ByteBuffer.wrap(message.getBytes()));
    }

    @Override
    public void onMessage(final WebSocket conn, final ByteBuffer message) {
        LOG.debug("received from %s byte buffer [length:%d]", conn.getAttachment(), message.array().length);
        Router.global().stats().ioStats().incrBytesRecv(message.array().length);
        try {
            final Obj obj = this.serializer.inputBytes(message);// this.serializer.read(message);
            this.onObj(conn, obj);
        } catch (final Exception e) {
            this.onObj(conn, fail(e));
        }
    }


    public void onObj(final WebSocket conn, final Obj obj) {
        Obj result;
        try {
            LOG.trace("processing %s for {{b}}%s{{/b}}", obj, conn.getAttachment());
            result = obj.apply();
            result = objs(result.stream().map(x -> x.vid(null))); // x.vid(this.host().extend(x.vid()))));
            final ByteBuffer bytes = this.serializer.outputBytes(result);
            conn.send(bytes);
            Router.global().stats().ioStats().incrBytesSent(bytes.array().length);
            LOG.trace("sent %s for {{b}}%s{{/b}}", result, conn.getAttachment());
        } catch (final Exception e) {
            final ByteBuffer bytes = this.serializer.outputBytes(fail(e));
            conn.send(bytes);
            Router.global().stats().ioStats().incrBytesRecv(bytes.array().length);
        }
    }

    @Override
    public void onError(final WebSocket conn, final Exception ex) {
        LOG.error("an error occurred on connection %s: %s", null == conn ? "<none>" : conn.getAttachment(), ex);
        if (null == conn || ex instanceof BindException) {
            this.close();
        }
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
        return Router.loaded() ? Router.global().vid().extend("server") : null;
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