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

package studio.phaseshift.metatron.io.net;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.ui.ObjSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.lang.fURI.f;

public class MServer extends WebSocketServer implements Closeable {

    protected final fURI authority;
    protected final GraphittyLogger LOG;
    protected final ObjSerializer<ByteBuffer> serializer;
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
        } catch (final Exception e) {
            // do nothing
        }

    }

    public List<MServerClient> getRouters(final fURI pattern) {
        return this.getConnections().stream().map(MServerClient::new).filter(msc -> msc.authority().matches(pattern)).toList();
    }

    @Override
    public void close() {
        this.stop();
    }

    @Override
    public void stop() {
        try {
            super.stop(1000);
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public void onOpen(final WebSocket ws, final ClientHandshake handshake) {
        ws.setAttachment(f("ws://" + ws.getRemoteSocketAddress()));
        LOG.debug("new connection from %s", ws.getRemoteSocketAddress());
        // conn.send("Welcome to the server!"); //This method sends a message to the new client
        // broadcast("new connection: " + handshake.getResourceDescriptor()); //This method sends a message to all clients connected
    }

    @Override
    public void onClose(final WebSocket conn, final int code, final String reason, final boolean remote) {
        LOG.debug("closed %s with exit code %s [reason: %s]", conn.getRemoteSocketAddress(), code, reason);
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

    public void onObj(final WebSocket conn, final Obj obj) {
        try {
            LOG.trace("processing %s for %s", obj, conn);
            Obj result = obj.apply().vid(null);
           // final String tag = obj.vid() != null ? obj.vid().queryValue(f("tag"), String.class, null) : null;
            //if (tag != null) {
            //    fURI rvid = result.vid() == null ? f("/usr/temp?tag=" + tag) : result.vid().query("tag", tag);
            //    result = result.vid(rvid);
           //     LOG.info("obj tagged: %s", result);
           // }
            conn.send(this.serializer.write(result));
        } catch (final Exception e) {
            this.onError(conn, e);
        }
    }

    @Override
    public void onError(final WebSocket conn, final Exception ex) {
        LOG.error("an error occurred on connection %s: %s", conn.getRemoteSocketAddress(), ex);
    }

    @Override
    public void onStart() {
        LOG.info("{{g}}starting{{/g}} %s node: %s", Graphitty.sillyPrint("mtron", true, true), this.getAddress());
    }

    public class MServerClient {
        private final WebSocket ws;

        public MServerClient(final WebSocket ws) {
            this.ws = ws;
        }

        public void sendObj(final Obj obj) {
            this.ws.send(serializer.write(obj));
        }

        public <O extends Obj> FutureObj<O> sendRecvObj(final Obj obj) {
            final FutureObj<O> future = new FutureObj<>("abc");
            futures.add(future);
            this.ws.send(serializer.write(obj));
            return future;
        }

        public fURI authority() {
            return this.ws.getAttachment();
        }
    }
}