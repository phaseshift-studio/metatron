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

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MObjs;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.*;

import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;

public class MClient extends WebSocketClient implements MConnection {

    protected final GraphittyLogger LOG = Graphitty.log(this);
    protected final ObjSerializer<?> serializer;
    protected final Map<UUID, FutureObj<Obj>> futures = new HashMap<>();
    private static final UUID UNITY_UUID = UUID.randomUUID();
    protected final fURI remoteHost;
    private FutureObj<Obj> TEMP_FUTURE = null;

    public MClient(final fURI remoteAuthority, final ObjSerializer<?> serializer, final Draft draft) {
        super(URI.create(remoteAuthority.toString()), draft);
        assert serializer != null;
        this.serializer = serializer;
        this.remoteHost = remoteAuthority;
        if (Router.loaded()) {
            LOG.info("connecting to {{b}}%s{{/b}}", this.remoteHost);
            Router.writeToSpace(Router.global().vid().extend("cluster"), objs(new ArrayList<>(List.of(this.remoteHost.toUri()))));
        }
    }

    public MClient(final fURI remoteAuthority, final ObjSerializer<?> serializer) {
        this(remoteAuthority, serializer, new Draft_6455());
    }

    public static MConnection of(final fURI clientAuthority, final ObjSerializer<?> defaultSerializer) {
        return Router.global()
                .server()
                .nodes()
                .values()
                .stream()
                .peek(c -> Router.global().logger().debug("reusing existing connection to {{b}}%s{{/b}}", c.remoteHost()))
                .findAny()
                .orElseGet(() -> {
                    final MClient client = new MClient(clientAuthority, defaultSerializer);
                    client.start();
                    return client;
                });
    }

   /* public void close() {
        try {
            super.closeBlocking();
        } catch (final InterruptedException e) {
            LOG.error(e);
        }
    }*/

    public fURI remoteHost() {
        return this.remoteHost;
    }

    public void start() {
        this.connect();
    }

    @Override
    public void close() {
        super.close(WebSocket.NORMAL_CLOSURE);
    }

    @Override
    public void onOpen(final ServerHandshake handshake) {
        LOG.debug("new connection opened: %s", handshake.getHttpStatusMessage());
    }

    @Override
    public void onClose(final int code, final String reason, final boolean remote) {
        LOG.trace("closed with exit code %d [reason:%s]", code, reason);
    }

    @Override
    public void onMessage(final String message) {
        LOG.debug("received string [length:%d]", message.length());
        this.onMessage(ByteBuffer.wrap(message.getBytes()));

    }

    @Override
    public void onMessage(final ByteBuffer message) {
        LOG.debug("received byte buffer [length:%d]", message.array().length);
        Router.global().stats().incrBytesRecv(message.array().length);
        final Obj obj = this.serializer.inputBytes(message);
        this.onObj(obj);
    }

    @Override
    public void onError(final Exception ex) {
        this.futures.clear();
        if (!this.isOpen()) {
            new Thread(() -> {
                try {
                    Thread.sleep(2500);
                    LOG.trace("retrying connection to {{b}}%s{{X}}", this.remoteHost);
                    this.reconnectBlocking();
                } catch (Exception e) {
                    LOG.error("an error occurred with {{b}}%s{{/b}}: %s", this.remoteHost, ex.getMessage().toLowerCase());
                    throw MTronException.of(e);
                }

            }).start();
        }
    }

    @Override
    public ObjSerializer<?> getSerializer() {
        return this.serializer;
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void sendObj(final Obj obj) {
        //LOG.trace("sendObj futures: %s", this.futures);
        final ByteBuffer buffer = this.serializer.outputBytes(obj);
        this.send(buffer);
        Router.global().stats().incrBytesSent(buffer.array().length);
    }


    @Override
    public <O extends Obj> FutureObj<O> sendRecvObj(final Obj obj) {
        Obj toSend = obj;// objs(obj.stream().map(x -> x.vid() == null ? x : x.vid(this.remoteHost().extend(x.vid().path()))));
        //toSend = toSend.vid(toSend.vid() == null ? f("temp?tag=abc") : toSend.vid().query("tag", "abc"));
        //LOG.trace("sending obj and awaiting future: %s", toSend);
        //final FutureObj<Obj> future = new FutureObj<>(UNITY_UUID);
        //this.futures.put(future.tag(), future);
        //LOG.trace("sendRecvObj futures: %s", this.futures);
        this.TEMP_FUTURE = new FutureObj<>(UNITY_UUID);
        this.sendObj(toSend);
        return (FutureObj<O>) this.TEMP_FUTURE;
    }

    public void onObj(final Obj obj) {
        LOG.trace("processing %s from {{b}}%s{{/b}}", obj, this.getRemoteSocketAddress());
        /*if (obj.vid() != null && obj.vid().hasQuery("tag")) {
            LOG.trace("processing tagged obj %s", obj.vid());
            final String tag = obj.vid().queryValue(f("tag"), String.class);
            Optional<FutureObj<?>> future = this.futures.stream().filter(f -> f.tag().equals(tag)).findAny();
            if (future.isPresent()) {
                final FutureObj<Obj> f = (FutureObj<Obj>) future.get();
                f.setObj(obj.vid(obj.vid().removeQ("tag")));
            }
        } else {*/
        LOG.trace("onObj futures: %s", this.futures);
        final FutureObj<Obj> future = this.TEMP_FUTURE;
        this.TEMP_FUTURE = null;
        if (null != future) {
            future.setObj(obj);
            LOG.trace("processing future obj %s", future);
        } else
            LOG.trace("no future obj found for %s", obj);
        //}
    }


}
