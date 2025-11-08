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

package studio.phaseshift.metatron.lang.msys.impl.net;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.msys.Router;
import studio.phaseshift.metatron.lang.msys.impl.FutureObj;
import studio.phaseshift.metatron.lang.msys.impl.ObjByteBufferSerializer;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.impl.MObjs;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.ui.ObjSerializer;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

public class MClient extends WebSocketClient implements MConnection {

    protected final GraphittyLogger LOG;
    protected final ObjSerializer<ByteBuffer> serializer;
    protected final Queue<FutureObj<Obj>> futures = new LinkedList<>();
    protected final fURI authority;

    public MClient(final fURI authority, final Draft draft) {
        super(URI.create(authority.toString()), draft);
        LOG = Router.global().logger();
        this.serializer = new ObjByteBufferSerializer();
        this.authority = authority;
        LOG.info("connecting to {{b}}%s{{/b}}", this.authority);
        Router.writeToSpace(Router.global().vid().extend("cluster"), new MObjs(this.authority.toUri()));
    }

    public MClient(final fURI authority) {
        this(authority, new Draft_6455());
    }

    public static MConnection of(final fURI clientAuthority) {
        return Router.global()
                .server()
                .cluster(clientAuthority)
                .peek(c -> Router.global().logger().debug("reusing existing connection to {{b}}%s{{/b}}", c.authority()))
                .findAny()
                .orElseGet(() -> {
                    final MClient client = new MClient(clientAuthority);
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

    public fURI authority() {
        return this.authority;
    }

    public void start() {
        this.connect();
    }

    @Override
    public void onOpen(final ServerHandshake handshake) {
        LOG.debug("new connection opened: %s", handshake.getHttpStatusMessage());
    }

    @Override
    public void onClose(final int code, final String reason, final boolean remote) {
        LOG.debug("closed with exit code %d [reason:%s]", code, reason);
    }

    @Override
    public void onMessage(final String message) {
        LOG.trace("received string [length:%d]", message.length());
        final Obj obj = this.serializer.read(ByteBuffer.wrap(message.getBytes()));
        this.onObj(obj);

    }

    @Override
    public void onMessage(final ByteBuffer message) {
        LOG.trace("received byte buffer [length:%d]", message.array().length);
        final Obj obj = this.serializer.read(message);
        this.onObj(obj);

    }

    @Override
    public void onError(final Exception ex) {
        LOG.error("an error occurred with {{b}}%s{{/b}}: %s", this.authority, ex.getMessage().toLowerCase());
        this.futures.clear();
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void sendObj(final Obj obj) {
        final ByteBuffer buffer = this.serializer.write(obj);
        this.send(buffer);
    }


    @Override
    public <O extends Obj> FutureObj<O> sendRecvObj(final Obj obj) {
        final Obj toSend = obj;
        //final Obj toSend = obj.vid(obj.vid() == null ? f("temp?tag=abc") : obj.vid().query("tag", "abc"));
        LOG.trace("sending obj and awaiting future: %s", toSend);
        final FutureObj<Obj> future = new FutureObj<>("abc");
        this.futures.add(future);
        this.sendObj(toSend);
        return (FutureObj<O>) future;
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
        final FutureObj<Obj> future = this.futures.poll();
        LOG.trace("processing future obj %s", future);
        if (null != future) {
            future.setObj(obj);
        }
        //}
    }


}
