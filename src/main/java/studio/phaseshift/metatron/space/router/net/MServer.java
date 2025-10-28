/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC 
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

package studio.phaseshift.metatron.space.router.net;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.Fail;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.space.router.FutureObj;
import studio.phaseshift.metatron.space.router.ObjByteBufferSerializer;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.ui.ObjSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MFail.fail;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MLst.lst;
import static studio.phaseshift.metatron.space.router.MRouter.ROUTER_TID;

public class MServer extends WebSocketServer implements Closeable, Obj {

    public static final fURI MSERVER_TID = ROUTER_TID.extend("server");

    protected final fURI authority;
    protected final ObjSerializer<ByteBuffer> serializer;
    protected final Map<fURI, MConnection> cluster = new HashMap<>();
    protected GraphittyLogger LOG;
    protected Thread serverThread;
    protected List<FutureObj<?>> futures = new ArrayList<>();

    public MServer(final fURI authority) {
        super(new InetSocketAddress(authority.host(), authority.port()));
        this.authority = authority;
        LOG = Graphitty.log(this);
        this.serializer = new ObjByteBufferSerializer();
    }

    public fURI authority() {
        return this.authority;
    }

    public void start() {
        LOG = Router.global().logger();
        final Runnable r = () -> {
            try (this) {
                this.run();
            } catch (final Exception e) {
                if (!(e.getCause() instanceof InterruptedException)) {
                    throw MTronException.of(e);
                }
            }
        };
        try {
            this.serverThread = new Thread(r);
            this.serverThread.start();
            LOG.trace("server started: %s", this.getAddress());
            BootLoader.GLOBAL.at("cluster").elementStream().filter(o -> !o.isNoObj()).forEach(n -> {
                final MConnection client = MClient.of(n.uriValue());
                this.cluster.put(n.uriValue(), client);
            });
            Router.global().write(
                    Router.global().vid().extend("cluster"),
                    lst((List) this.cluster.values().stream().map(x -> x.authority().toUri()).toList()));
        } catch (final Exception e) {
            // do nothing
        }

    }

    public Stream<MConnection> cluster(final fURI select) {
        return this.cluster.entrySet().stream().filter(kv -> select.matches(kv.getKey())).map(Map.Entry::getValue);
    }

    @Override
    public void close() {
        LOG.info("closing %s node {{b}}%s{{/b}}", Graphitty.sillyPrint("mtron", true, true), this.authority);
        this.stop();
    }

    @Override
    public void stop() {
        try {
            this.cluster.values().stream().toList().forEach(MConnection::close);
            super.stop(1000);
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
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
        LOG.trace("received from %s string [length:%d]", conn, message.length());
        final Obj obj = this.serializer.read(ByteBuffer.wrap(message.getBytes()));
        this.onObj(conn, obj);
    }

    @Override
    public void onMessage(final WebSocket conn, final ByteBuffer message) {
        LOG.trace("received from %s byte buffer [length:%d]", conn, message.array().length);
        final Obj obj = this.serializer.read(message);
        this.onObj(conn, obj);
    }

    public void sendObj(final WebSocket conn, final Obj obj) {
        conn.send(this.serializer.write(obj));
    }

    public void onObj(final WebSocket conn, final Obj obj) {
        try {
            LOG.trace("processing %s for {{b}}%s{{/b}}", obj, conn.getRemoteSocketAddress());
            final Obj result = obj.apply().vid(null);
            // final String tag = obj.vid() != null ? obj.vid().queryValue(f("tag"), String.class, null) : null;
            //if (tag != null) {
            //    fURI rvid = result.vid() == null ? f("/usr/temp?tag=" + tag) : result.vid().query("tag", tag);
            //    result = result.vid(rvid);
            //     LOG.info("obj tagged: %s", result);
            // }
            this.sendObj(conn, result);
            if (result.isFail())
                this.onError(conn, result.<Fail>as().jvmAs());
        } catch (final Exception e) {
            this.sendObj(conn, fail(e));
            this.onError(conn, e);
        }
    }

    @Override
    public void onError(final WebSocket conn, final Exception ex) {
        LOG.error("an error occurred on connection %s: %s", null == conn ? "<not connected>" : conn.getRemoteSocketAddress(), ex);
    }

    @Override
    public void onStart() {
        LOG.info("starting %s node {{b}}%s{{/b}}", Graphitty.sillyPrint("mtron", true, true), this.authority);
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
    public <O extends Obj> O clone(Object jvm, fURI tid, fURI vid) {
        return null;
    }

    @Override
    public Obj clone() {
        return null;
    }

    public class MServerClient implements MConnection {
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
        public fURI authority() {
            return this.ws.getAttachment();
        }
    }
}