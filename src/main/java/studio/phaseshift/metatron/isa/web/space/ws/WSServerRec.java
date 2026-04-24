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
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.web.type.Content;
import studio.phaseshift.metatron.util.Tuple;

import java.nio.ByteBuffer;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class WSServerRec extends MRec implements WSServer {

    protected final Content.ContentType inContentType;
    protected final Content.ContentType outContentType;
    protected WebSocket socket = null;

    public WSServerRec(final Map<Obj, Obj> map, final fURI vid) {
        this(map, wsSpace.WS_SERVER_TID, vid);
    }

    public WSServerRec(final Map<Obj, Obj> map, final fURI tid, final fURI vid) {
        super(map, tid, vid);
        this.outContentType = Content.ContentType.APPLICATION_MTRON;
        this.inContentType = Content.ContentType.APPLICATION_MTRON;
    }


    public Tuple.Pair<Content.ContentType, Content.ContentType> getIOSerializers() {
        return Tuple.Pair.with(this.inContentType, this.outContentType);
    }

    public void onOpen(final WebSocket conn, final ClientHandshake handshake) {
        this.at(uri(ON_OPEN)).apply(str(handshake.getResourceDescriptor()));
    }


    public void onClose(final WebSocket conn, final int code, final String reason, final boolean remote) {
        this.at(uri(ON_CLOSE)).apply(rec(uri(CODE), jnt(code), uri(REASON), str(reason)));
    }


    public void onMessage(final WebSocket conn, final String message) {
        final Obj result = this.at(uri(ON_MESSAGE)).apply(this.inContentType.serializer().inputBytes(ByteBuffer.wrap(message.getBytes())));
        this.send(result);
    }


    public void onError(final WebSocket conn, final Exception ex) {
        this.at(uri(ON_ERROR)).apply(fail(ex));
    }

    @Override
    public WebSocket getWebSocket() {
        return this.socket;
    }
}
