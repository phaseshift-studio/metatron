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

package studio.phaseshift.metatron.isa.m.web.space.ws;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ServerHandshake;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.m.web.type.MIME;
import studio.phaseshift.metatron.util.MTronException;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.print_;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.web.space.ws.wsServer.WS_CLIENT_TID;

/**
 * The ws protocol client — and a {@link Network.Link}: the network supervises it for as long as it is indexed, so a
 * connection that fails is redialed IN PLACE (this object, its config, its handlers and its round-trip router all
 * survive the outage), and only the owner's {@link #close()} — the client closed or deleted where it is held, the
 * peer dropped, the frame popped — ends it for good and takes its peer out of the map.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class WebSocketRecClient extends WebSocketClient implements Rec, Closeable, Network.Link {

    /**
     * how long a dial — the construction's, and every redial's — may take to open. The TCP connect is bounded by
     * it too, so an unreachable host costs this and no more.
     */
    public static final int CONNECT_TIMEOUT_MS = 5_000;

    private final WebSocketRec wsclient;
    protected final GraphittyLogger LOG = Graphitty.log(this);
    /**
     * the OWNER closed this client: the one explicit shutdown, after which it is never redialed
     */
    private volatile boolean hungUp = false;
    /**
     * set from a redial until the connection opens again: an attempt that is refused is the supervisor's to
     * reschedule, not a close or an error for the client's handlers — once per attempt, for as long as the far
     * side is down
     */
    private volatile boolean redialing = false;
    /**
     * the thread inside {@link #redial()}: the reconnect's own teardown closes the dead connection through
     * {@link #close()}, and that close is the redial's, not the owner's
     */
    private volatile Thread redialer = null;
    private final Object redialLock = new Object();

    public WebSocketRecClient(final WebSocketRec wsclient) {
        super(java.net.URI.create(wsclient.at(HOST).uriValue().toString()), new Draft_6455(), null, CONNECT_TIMEOUT_MS);
        this.wsclient = wsclient;
        this.wsclient.socket = this;
        this.wsclient.selfTID(WS_CLIENT_TID);
        this.wsclient.socket.setAttachment(this.wsclient.vid());
        if (this.wsclient.at(ON_MESSAGE).isNoObj()) {
            this.wsclient.at(ON_MESSAGE, /*mtron_wsHandler.mtron_on_message(wsclient), MUTABLE);*/ print_(str("received ${_}")).tryToInst(), MUTABLE);
        }
        try {
            if (this.connectBlocking(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                LOG.debug("{{y}}%s {{g}}<=> {{y}}%s{{X}} opened", this.wsclient.getThisVID(), this.wsclient.getOtherVID());
                return;
            }
        } catch (final Exception e) {
            LOG.error(MTronException.of(e));
        }
        // a dial that did not take is not a ws_client yet -- and not a lost cause either: once indexed it is a
        // supervised link, redialed until the far side answers, and the first redial that opens restores the type
        this.selfTID(REC_TID);
        this.wsclient.selfTID(REC_TID);
    }

    @Override
    public void close() {
        // NOTE: this override is the real close path for a client -- CommonUtil.close(client) lands here, not
        // on WebSocketObj.close(), since this class implements Rec/Closeable rather than WebSocketObj.
        // THE REDIAL'S OWN TEARDOWN IS NOT A HANG-UP: reconnect() closes the dead connection through this method
        // before it dials again. A close from anywhere else is the owner's.
        if (Thread.currentThread() == this.redialer) {
            this.closeConnectionOnly();
            return;
        }
        final boolean first = !this.hungUp;
        this.hungUp = true;
        LOG.debug("closing client ready=%s", this.getReadyState());
        this.closeConnectionOnly();
        // DESTRUCTION ON DELETION: the owner's close takes the peer out of every level that indexed it -- once,
        // since retracting disconnects, and disconnecting closes this client again
        if (first)
            Network.Helper.retract(this);
    }

    private void closeConnectionOnly() {
        try {
            super.close();
        } catch (final Exception e) {
            LOG.error(MTronException.of(e));
        }
    }

    // ======================== the link ========================

    @Override
    public boolean connected() {
        return this.isOpen();
    }

    @Override
    public boolean hungUp() {
        return this.hungUp;
    }

    /**
     * Reconnect IN PLACE: the same client, the same uri, the same handlers. Bounded by {@link #CONNECT_TIMEOUT_MS}
     * and serialized, so the supervisor and a request that needs the link now can both ask — the second finds the
     * first's connection rather than dialing a socket of its own.
     */
    @Override
    public boolean redial() {
        synchronized (this.redialLock) {
            if (this.hungUp)
                return false;
            if (this.isOpen())
                return true;
            this.redialing = true;
            this.redialer = Thread.currentThread();
            try {
                // reconnect(), not reconnectBlocking(): the blocking form waits on the handshake without a bound, and
                // a host that accepts the socket but never upgrades it would hold this attempt forever
                this.reconnect();
                final long deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS;
                while (!this.isOpen() && !this.isClosed() && System.currentTimeMillis() < deadline)
                    Thread.sleep(10L);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final Exception e) {
                LOG.debug("redial of %s failed: %s", this.wsclient.peerName(), e);
            } finally {
                this.redialer = null;
            }
            if (!this.isOpen())
                return false;
            // the owner hung up while the dial was in flight: the connection is not theirs to get back
            if (this.hungUp) {
                this.closeConnectionOnly();
                return false;
            }
            // A REDIAL IS A NEW CONNECTION UNDER THE SAME CLIENT: the attachment is per connection, and a client whose
            // first dial never took was demoted to a bare rec -- it is a ws_client now
            this.setAttachment(this.wsclient.vid());
            this.wsclient.selfTID(WS_CLIENT_TID);
            LOG.debug("{{y}}%s {{g}}<=> {{y}}%s{{X}} reopened", this.wsclient.getThisVID(), this.wsclient.getOtherVID());
            return true;
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
        return WebSocketObj.IO.of(this.wsclient, MIME.MIMEType.APPLICATION_MTRON);
    }

    @Override
    public void onOpen(final ServerHandshake handshake) {
        // a redial that opens is an open like the first: the client's on_open runs again for the new connection
        this.redialing = false;
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

    public boolean state() {
        return this.wsclient.state(this);
    }

    public void send(final Obj message) {
        this.wsclient.send(message);
    }

    public Obj sendRecv(final Obj message) {
        return this.wsclient.sendRecv(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        // A REDIAL THAT NEVER OPENED CLOSED NOTHING: the owner already saw the close that took the link down
        if (this.redialing && CloseFrame.NEVER_CONNECTED == code) {
            LOG.debug("redial of %s did not open: %s", this.wsclient.peerName(), reason);
            return;
        }
        this.wsclient.onClose(this, code, reason, remote);
    }

    @Override
    public void onError(Exception ex) {
        // a refused redial is expected while the far side is down -- the supervisor reschedules it
        if (this.redialing) {
            LOG.debug("redial of %s errored: %s", this.wsclient.peerName(), ex.getMessage());
            return;
        }
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
