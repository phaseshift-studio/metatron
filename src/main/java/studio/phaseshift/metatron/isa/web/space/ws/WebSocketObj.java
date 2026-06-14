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
import org.java_websocket.handshake.Handshakedata;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Real;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.util.MTronException;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface WebSocketObj extends Rec, Closeable {

    record IO(MIME.MIMEType input, MIME.MIMEType output) {
        public static IO of(final Rec obj, final MIME.MIMEType defaultType) {
            final MIME.MIMEType input = obj.has(IN) ? MIME.MIMEType.of(obj.at(IN).uriValue().toString()) : defaultType;
            final MIME.MIMEType output = obj.has(OUT) ? MIME.MIMEType.of(obj.at(OUT).uriValue().toString()) : defaultType;
            return new IO(input, output);
        }
    }

    void onOpen(final WebSocket conn, final Handshakedata handshake);

    void onClose(final WebSocket conn, final int code, final String reason, final boolean remote);

    void onMessage(final WebSocket conn, final Obj message);

    void onError(final WebSocket conn, final Exception ex);

    WebSocket getWebSocket();

    void setWebSocket(final WebSocket socket);

    default fURI getThisVID() {
        return null == this.getWebSocket() || this.getWebSocket().isClosed() ?
                NOOBJ_TID :
                this.getWebSocket().getAttachment();
    }

    default fURI getOtherVID() {
        
        return null == this.getWebSocket() || this.getWebSocket().isClosed() ?
                NOOBJ_TID :
                f(this.getWebSocket().getRemoteSocketAddress().toString());
    }

    IO getIO();

    @Override
    default void close() {
        try {
            if (null != this.getWebSocket() && !this.getWebSocket().isClosed()) {
                Graphitty.log(this).info("closing %s", this.getThisVID());
                this.getWebSocket().close();
            } else
                throw MTronException.of("websocket already closed for %s", this.getThisVID());
        } catch (final Exception e) {
            Graphitty.log(this).error("error closing websocket: %s", this.getThisVID(), e);
        }
    }

    default void send(final Obj message) {
        try {
            if (null == this.getWebSocket()) {
                if (BootLoader.TESTING)
                    return;
                return;
            }
            final MIME.MIMEType outType = this.getIO().output();
            final ByteBuffer bytes = outType.serializer().outputBytes(message);
            if (outType.isText()) {
                // send as a websocket text frame so clients using onText listeners receive it
                final String outgoing = new String(bytes.array(), StandardCharsets.UTF_8);
                Graphitty.log(this).debug("sending %s to %s", outgoing, this.getWebSocket().getRemoteSocketAddress());
                this.getWebSocket().send(outgoing);
            } else
                this.getWebSocket().send(bytes);
        } catch (final Exception e) {
            Graphitty.log(this).error("error sending %s: %s", message, e);
        }
    }

    /**
     * Send a message and block until a response is received, then return it.
     * <p>
     * Temporarily installs a one-shot {@code ON_MESSAGE} handler that captures
     * the next inbound frame, sends {@code message}, waits indefinitely for the
     * response, restores the previous {@code ON_MESSAGE} handler, and returns
     * the captured response.
     *
     * @param message the Obj to send
     * @return the response Obj (or {@code noobj} if interrupted)
     */
    default Obj sendRecv(final Obj message) {
        return this.sendRecv(message, 0L);
    }

    /**
     * Send a message and block for up to {@code timeoutMs} waiting for a
     * response.
     * <p>
     * Temporarily installs a one-shot {@code ON_MESSAGE} handler that captures
     * the next inbound frame, sends {@code message}, waits up to
     * {@code timeoutMs}, restores the previous {@code ON_MESSAGE} handler, and
     * returns the captured response (or {@code noobj} if the timeout expired
     * or the thread was interrupted).
     *
     * @param message   the Obj to send
     * @param timeoutMs max wait in milliseconds (<= 0 means wait indefinitely)
     * @return the response Obj (or {@code noobj} on timeout / interrupt)
     */
    default Obj sendRecv(final Obj message, final long timeoutMs) {
        final AtomicReference<Obj> incoming = new AtomicReference<>(noobj());
        final CountDownLatch latch = new CountDownLatch(1);
        final Obj previousOnMessage = this.at(uri(ON_MESSAGE));

        // Install a one-shot handler that captures the next message
        this.at(uri(ON_MESSAGE), instLambda((lhs, inst) -> {
            incoming.set(lhs);
            latch.countDown();
            return lhs;
        }), MUTABLE);

        try {
            this.send(message);
            if (timeoutMs <= 0L) {
                latch.await();
            } else {
                latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            this.at(uri(ON_MESSAGE), previousOnMessage, MUTABLE);
        }

        return incoming.get();
    }
}
