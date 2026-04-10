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

package studio.phaseshift.metatron.isa.mach.type.net.protocol;

import org.java_websocket.WebSocket;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.nio.ByteBuffer;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.IN;
import static studio.phaseshift.metatron.Tokens.OUT;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.mach.type.net.MServer.MSERVER_TID;
import static studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty.sillyPrint;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * Native Metatron protocol handler.
 * Handles binary Obj-based messages using Metatron's native serialization format.
 * <p>
 * This is the original protocol that MServer used before multi-protocol support.
 * It serializes/deserializes Obj instances and executes them through the Router.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class NativeMetatronProtocolHandler extends MRec implements MServerProtocolHandler {

    public static final String MACH_SERVER_NATIVE_PROTOCOL_TID = MSERVER_TID.extend("protocol").extend("native").toString();

    private final ObjSerializer<?> serializer;
    private final Map<fURI, WebSocket> cluster;
    private final GraphittyLogger LOG;

    public NativeMetatronProtocolHandler(
            final ObjSerializer<?> serializer,
            final Map<fURI, WebSocket> cluster,
            final fURI vid) {
        super(mutableMap(cluster), f(MACH_SERVER_NATIVE_PROTOCOL_TID), vid);
        this.serializer = serializer;
        this.cluster = cluster;
        this.LOG = this.logger();
    }

    @Override
    public int connections() {
        return cluster.size();
    }

    @Override
    public boolean canHandle(final String message) {
        // Native protocol doesn't use text messages (only binary)
        // If we receive text that's not JSON-RPC, convert it to binary
        return message != null && !message.trim().isEmpty() && !message.trim().contains(Tokens.JSONRPC);
    }

    @Override
    public boolean canHandle(final ByteBuffer message) {
        // Native protocol uses binary ByteBuffer messages
        // This is the default/fallback protocol for binary data
        return message != null && message.hasRemaining();
    }

    @Override
    public void handleMessage(final WebSocket conn, final String message) {
        LOG.debug("received from %s string [length:%d] - converting to binary", conn, message.length());
        // Convert string to binary and handle as ByteBuffer
        handleMessage(conn, ByteBuffer.wrap(message.getBytes()));
    }

    @Override
    public void handleMessage(final WebSocket conn, final ByteBuffer message) {
        LOG.debug("received from %s byte buffer [length:%d]", conn, message.array().length);
        Router.global().stats().ioStats().incrBytesRecv(message.array().length);
        try {
            if (!Router.global().read(conn.<fURI>getAttachment().extend(IN)).isNoObj())
                Router.global().write(conn.<fURI>getAttachment().extend(IN), str(new String(message.array())));
            final Obj obj = this.serializer.inputBytes(message);
            processObj(conn, obj);
        } catch (final Exception e) {
            processObj(conn, fail(e));
        }
    }

    /**
     * Processes a deserialized Obj by applying it and sending the result back.
     */
    private void processObj(final WebSocket conn, final Obj obj) {
        Obj result;
        try {
            LOG.debug("processing %s for {{b}}%s{{/b}}", obj, conn);
            result = obj.apply();
            if (!Router.global().read(conn.<fURI>getAttachment().extend(OUT)).isNoObj())
                Router.global().write(conn.<fURI>getAttachment().extend(OUT), result);
            final ByteBuffer bytes = result.isNoObj() ?
                    (ByteBuffer) this.serializer.writeNoObj(noobj()) :
                    this.serializer.outputBytes(result);
            conn.send(bytes);
            Router.global().stats().ioStats().incrBytesSent(bytes.array().length);
            LOG.debug("sent %s for {{b}}%s{{/b}}", result, conn);
        } catch (final Exception e) {
            final ByteBuffer bytes = this.serializer.outputBytes(fail(e));
            conn.send(bytes);
            Router.global().stats().ioStats().incrBytesSent(bytes.array().length);
        }
    }

    @Override
    public void onConnectionOpen(final WebSocket conn) {
        // Native protocol doesn't need special initialization
        LOG.debug("native protocol connection opened: %s", conn);
        this.cluster.put(f(conn.getRemoteSocketAddress().getHostString()), conn);
    }

    @Override
    public void onConnectionClose(final WebSocket conn, final int code, final String reason) {
        final WebSocket removed = cluster.remove(f(conn.getRemoteSocketAddress().getHostString()));
        if (removed != null) {
            LOG.debug("closed native connection %s with exit code %s [reason: %s]", removed, code, reason);
        }
    }

    @Override
    public void shutdown() {
        LOG.info("%s protocol handler shutting down", sillyPrint("native", true, true));
        // No special cleanup needed
    }
}
