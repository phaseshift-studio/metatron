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
import org.java_websocket.handshake.ClientHandshake;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.web.type.Content;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface WSServer extends Obj {

    void onOpen(final WebSocket conn, final ClientHandshake handshake);

    void onClose(final WebSocket conn, final int code, final String reason, final boolean remote);

    void onMessage(final WebSocket conn, final String message);

    void onError(final WebSocket conn, final Exception ex);

    public WebSocket getWebSocket();

    public Tuple.Pair<Content.ContentType, Content.ContentType> getIOSerializers();

    default void send(final Obj message) {
        if (null == this.getWebSocket())
            throw MTronException.of("no websocket found for %s", this);
        this.getWebSocket().send(this.getIOSerializers().get1().serializer().outputBytes(message));
    }
}
