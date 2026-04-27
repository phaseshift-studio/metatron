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

package studio.phaseshift.metatron.isa.mach.type.net;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.reflect.JRecElement;
import studio.phaseshift.metatron.isa.mach.io.type.ObjByteBufferSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.net.protocol.MServerProtocolHandler;
import studio.phaseshift.metatron.isa.mach.type.net.protocol.McpProtocolHandler;
import studio.phaseshift.metatron.isa.mach.type.net.protocol.NativeMetatronProtocolHandler;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
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
import java.util.concurrent.atomic.AtomicInteger;

import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public class MServer extends WebSocketServer implements Cluster, Closeable, Rec {

    public static final fURI MSERVER_TID = MACH_ISA_TID.extend("server");

    protected static final AtomicInteger sessionCounter = new AtomicInteger(0);
    protected final fURI host;
    protected final ObjSerializer<?> serializer;
    @JRecElement(key = "cluster", rng = "/m/lst[/m/mach/net/connection]")
    protected final Map<fURI, MConnection> cluster = new HashMap<>();
    protected GraphittyLogger LOG = Graphitty.log(this);
    final AtomicBoolean running = new AtomicBoolean(false);
    protected final List<fURI> peers;

    // protocol handlers for multi-protocol support (TODO: route message more intelligently)
    protected final List<MServerProtocolHandler> protocolHandlers = new ArrayList<>();
    protected MServerProtocolHandler nativeProtocolHandler;
    protected MServerProtocolHandler mcpProtocolHandler;

    public MServer(final fURI host, final List<fURI> peers) {
        super(new InetSocketAddress(host.host(), host.port()));
        this.host = host;
        this.peers = peers;
        this.serializer = new ObjByteBufferSerializer();
        Obj.Helper.objCheckAndSave(this);
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
            // Native Metatron protocol (binary Obj serialization)
            this.nativeProtocolHandler = new NativeMetatronProtocolHandler(serializer, mutableMap(this.cluster), this.vid().extend("protocol/native"));
            this.protocolHandlers.add(this.nativeProtocolHandler);

            // MCP protocol (JSON-RPC 2.0)
            this.mcpProtocolHandler = new McpProtocolHandler(this.vid().extend("protocol/mcp"));
            this.protocolHandlers.add(this.mcpProtocolHandler);

            LOG.info("Initialized %d protocol handlers: %s",
                    this.protocolHandlers.size(),
                    this.protocolHandlers.stream().map(h -> h.tid().name()).toList());
        } else {
            throw MTronException.of("unable to start server as router not loaded");
        }
    }

    public <T> ObjSerializer<T> getSerializer() {
        return (ObjSerializer<T>) this.serializer;
    }

    @Override
    public void close() {
        LOG.debug("closing %s node {{b}}%s{{/b}}", Graphitty.sillyPrint("mtron", true, true), this.host);
        try {
            // Shutdown all protocol handlers
            protocolHandlers.forEach(MServerProtocolHandler::shutdown);

            this.cluster.values().stream().toList().forEach(MConnection::close);
            super.stop(1000, "server shutdown");
        } catch (final InterruptedException e) {
            LOG.info("%s interrupted successfully", this.host);
        } finally {
            this.running.set(false);
        }
    }

    @Override
    public void onOpen(final WebSocket conn, final ClientHandshake handshake) {
        LOG.info("new connection from %s for %s", conn.getRemoteSocketAddress(), handshake.getResourceDescriptor());
        conn.setAttachment(this.vid().extend("ws").extend(sessionCounter.incrementAndGet() + ""));
        this.protocolHandlers.forEach(handler -> handler.onConnectionOpen(conn));
        Router.global().stats().ioStats().setConnections(this.protocolHandlers.stream().mapToInt(MServerProtocolHandler::connections).sum());
    }

    @Override
    public void onClose(final WebSocket conn, final int code, final String reason, final boolean remote) {
        this.protocolHandlers.forEach(handler -> handler.onConnectionClose(conn, code, reason));
        Router.global().stats().ioStats().setConnections(this.protocolHandlers.stream().mapToInt(MServerProtocolHandler::connections).sum());

    }

    @Override
    public void onMessage(final WebSocket conn, final String message) {
        for (final MServerProtocolHandler handler : this.protocolHandlers) {
            if (handler.canHandle(message)) {
                Router.global().stats().ioStats().setLastMessage(message);
                LOG.debug("Routing message to %s protocol handler", handler.tid().name());
                handler.handleMessage(conn, message);
                return;
            }
        }
        LOG.warn("no protocol handler found for message from %s", conn.<fURI>getAttachment());
    }

    @Override
    public void onMessage(final WebSocket conn, final ByteBuffer message) {
        for (final MServerProtocolHandler handler : protocolHandlers) {
            if (handler.canHandle(message)) {
                Router.global().stats().ioStats().setLastMessage(new String(message.array()));
                LOG.debug("Routing message to %s protocol handler", handler.tid().name());
                handler.handleMessage(conn, message);
                return;
            }
        }
        LOG.warn("no protocol handler found for binary message from %s", conn.<fURI>getAttachment());
    }

    @Override
    public void onError(final WebSocket conn, final Exception ex) {
        LOG.error("an error occurred on connection %s: %s", null == conn ? "<none>" : conn.getAttachment(), ex);
        if (null != conn && ex instanceof BindException) {
            conn.close();
        }
    }

    @Override
    public void onStart() {
        LOG.info("starting %s node {{b}}%s{{/b}}", Graphitty.sillyPrint("mtron", true, true), this.host);
    }

    @Override
    public Map<Obj, Obj> jvm() {
        return mutableMap();
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
    
    public List<MServerProtocolHandler> getProtocolHandlers() {
        return new ArrayList<>(protocolHandlers);
    }
    
    public MServerProtocolHandler getProtocolHandler(final String protocolName) {
        return protocolHandlers.stream()
                .filter(h -> h.tid().name().equals(protocolName))
                .findFirst()
                .orElse(null);
    }
}