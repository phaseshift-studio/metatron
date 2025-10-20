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

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class MServer extends WebSocketServer implements AutoCloseable {

    protected final fURI host;
    protected final GraphittyLogger LOG;
    protected final ObjSerializer<ByteBuffer> serializer;
    protected Thread serverThread;

    public MServer(final fURI host) {
        super(new InetSocketAddress(host.host(), host.port()));
        this.host = host;
        LOG = Graphitty.log(this);
        this.serializer = new ObjByteBufferSerializer();
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
        } catch (final Exception e) {
            // do nothing
        }

    }

    @Override
    public void close() {
        this.stop();
    }

    @Override
    public void stop() {
        try {
            LOG.info("{{g}}stopping{{/g}} %s node: %s", Graphitty.sillyPrint("mtron", true, true), this.host.toUri());
            super.stop();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public void onOpen(final WebSocket conn, final ClientHandshake handshake) {
        LOG.debug("new connection from %s", conn.getRemoteSocketAddress());
        // conn.send("Welcome to the server!"); //This method sends a message to the new client
        // broadcast("new connection: " + handshake.getResourceDescriptor()); //This method sends a message to all clients connected
    }

    @Override
    public void onClose(final WebSocket conn, final int code, final String reason, final boolean remote) {
        LOG.debug("closed %s with exit code %s [reason: %s]", conn.getRemoteSocketAddress(), code, reason);
    }

    @Override
    public void onMessage(final WebSocket conn, final String message) {
        LOG.trace("parsing raw message: %s", message);
        Obj obj = this.serializer.read(ByteBuffer.wrap(message.getBytes()));
        LOG.trace("%s received from %s [raw string:%s]", obj, conn.getRemoteSocketAddress(), message);
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        Obj obj = this.serializer.read(message);
        LOG.trace("%s received from %s [raw bytes:%s]", obj, conn.getRemoteSocketAddress(), message);
    }

    @Override
    public void onError(final WebSocket conn, final Exception ex) {
        LOG.error("an error occurred on connection %s: %s", conn.getRemoteSocketAddress(), ex);
    }

    @Override
    public void onStart() {
        LOG.info("{{g}}starting{{/g}} %s node: %s", Graphitty.sillyPrint("mtron", true, true), this.getAddress());
    }
}