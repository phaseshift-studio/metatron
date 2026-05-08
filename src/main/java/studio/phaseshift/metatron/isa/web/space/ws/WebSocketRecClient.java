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

import kotlin.internal.InlineOnly;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshake;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.type.Content;
import studio.phaseshift.metatron.util.MTronException;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.Tokens.ON_MESSAGE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.ALL_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.print_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_CLIENT_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class WebSocketRecClient extends WebSocketClient implements Rec, Closeable {

    private final WebSocketRec wsclient;
    protected final GraphittyLogger LOG = Graphitty.log(this);

    public WebSocketRecClient(final WebSocketRec wsclient) {
        super(java.net.URI.create(wsclient.at(HOST).uriValue().toString()));
        this.wsclient = wsclient;
        this.wsclient.socket = this;
        this.wsclient.selfTID(WS_CLIENT_TID);
        this.wsclient.socket.setAttachment(this.wsclient.vid());
        if (this.wsclient.at(ON_MESSAGE).isNoObj()) {
            this.wsclient.at(ON_MESSAGE, instC(f(ON_MESSAGE), lst(ALL_TYPE), (lhs, inst) -> print_(str("recieved ${_}")).apply(lhs)), MUTABLE);
        }
        try {
            this.connectBlocking(5000, TimeUnit.MILLISECONDS);
        } catch (final Exception e) {
            LOG.error(MTronException.of(e));
            return;
        }
        LOG.info("{{y}}%s {{g}}<=> {{y}}%s{{X}} opened", this.wsclient.getThisVID(), this.wsclient.getOtherVID());
    }

    @Override
    public void close() {
        try {
            super.close();
        } catch (final Exception e) {
            LOG.error(MTronException.of(e));
        }
    }

    @Override
    public String toString() {
        return this.wsclient.toString();
    }

    @Override
    public boolean equals(final Object o) {
        return this.wsclient.equals(o);
    }

    @Override
    public int hashCode() {
        return this.wsclient.hashCode();
    }


    public WebSocketObj.IO getIO() {
        return WebSocketObj.IO.of(this.wsclient, Content.ContentType.APPLICATION_MTRON);
    }

    @Override
    public void onOpen(final ServerHandshake handshake) {
        this.wsclient.onOpen(this, handshake);
    }

    @Override
    public void onMessage(final String message) {
        this.wsclient.onMessage(this, this.getIO().input().serializer().inputBytes(ByteBuffer.wrap(message.getBytes())));
    }

    @Override
    public void onMessage(final ByteBuffer message) {
        this.wsclient.onMessage(this, this.getIO().input().serializer().inputBytes(message));
    }

    public void send(final Obj message) {
        this.wsclient.send(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        this.wsclient.onClose(this, code, reason, remote);
    }

    @Override
    public void onError(Exception ex) {
        this.wsclient.onError(this, ex);
    }

    @Override
    public Map<Obj, Obj> jvm() {
        return this.wsclient.jvm();
    }

    @Override
    public fURI tid() {
        return this.wsclient.tid();
    }

    @Override
    public fURI vid() {
        return this.wsclient.vid();
    }

    @Override
    public Rec self(final Object jvm, fURI tid, final fURI vid) {
        return this;
    }

    @Override
    public Rec clone(final Object jvm, final fURI tid, final fURI vid) {
        return this;
    }

    @Override
    public Obj clone() {
        return this;
    }
}
