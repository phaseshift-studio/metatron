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
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.web.type.Content;
import studio.phaseshift.metatron.util.MTronException;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static studio.phaseshift.metatron.Tokens.IN;
import static studio.phaseshift.metatron.Tokens.OUT;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface WebSocketObj extends Closeable {

    record IO(Content.ContentType input, Content.ContentType output) {
        public static IO of(final Rec obj, final Content.ContentType defaultType) {
            final Content.ContentType input = obj.has(IN) ? Content.ContentType.of(obj.at(IN).uriValue().toString()) : defaultType;
            final Content.ContentType output = obj.has(OUT) ? Content.ContentType.of(obj.at(OUT).uriValue().toString()) : defaultType;
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
        return null == this.getWebSocket() ?
                NOOBJ_TID :
                this.getWebSocket().getAttachment();
    }

    default fURI getOtherVID() {
        return null == this.getWebSocket() ?
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
                throw MTronException.of("no websocket found for %s", this);
            }
            final Content.ContentType outType = this.getIO().output();
            final ByteBuffer bytes = outType.serializer().outputBytes(message);
            if (outType.isText()) {
                // send as a websocket text frame so clients using onText listeners receive it
                final String outgoing = new String(bytes.array(), StandardCharsets.UTF_8);
                Graphitty.log(this).info("sending %s to %s", outgoing, this.getWebSocket().getRemoteSocketAddress());
                this.getWebSocket().send(outgoing);
            } else
                this.getWebSocket().send(bytes);
        } catch (final Exception e) {
            Graphitty.log(this).error("error sending %s: %s", message, e);
        }
    }

}
