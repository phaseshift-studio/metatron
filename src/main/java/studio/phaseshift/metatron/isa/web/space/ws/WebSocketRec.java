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
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshake;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Real;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.type.MIME;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TID;
import static studio.phaseshift.metatron.isa.m.type.InstSet.A;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class WebSocketRec extends MRec implements WebSocketObj {

    protected WebSocket socket = null;
    protected final GraphittyLogger LOG = Graphitty.log(this);

    public WebSocketRec(final Map<Obj, Obj> map, final fURI vid) {
        this(map, wsSpace.WS_WEBSOCKET_TID, vid);
    }

    public WebSocketRec(final Map<Obj, Obj> map, final fURI tid, final fURI vid) {
        super(map, tid, vid);
        if (!map.containsKey(uri(SEND)))
            this.jvm().put(uri(SEND), instLambda((lhs, inst) -> {
                try {
                    this.send(inst.arg(0));
                    return noobj();
                } catch (final Exception e) {
                    LOG.error("error sending message: %s", e);
                    return fail(e);
                }
            }));
        if (!map.containsKey(uri(SEND_RECV))) {
            this.jvm().put(uri(SEND_RECV), instLambda((lhs1, inst1) -> {
                final AtomicReference<Obj> incoming = new AtomicReference<>(noobj());
                final CountDownLatch latch = new CountDownLatch(1);
                final Obj previousOnMessage = this.at(ON_MESSAGE);
                this.at(ON_MESSAGE, instLambda((lhs, inst) -> {
                    incoming.set(lhs);
                    latch.countDown();
                    return lhs;
                }), MUTABLE);
                try {
                    final Obj toSend = inst1.arg(0);
                    final Real timeoutMs = inst1.arg(1).orElse(real(-1.0));
                    if (timeoutMs.realValue() == -1.0) {
                        this.send(toSend);
                        latch.await();
                    } else {
                        this.send(toSend);
                        latch.await(timeoutMs.realValue().longValue(), TimeUnit.MILLISECONDS);
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    this.at(ON_MESSAGE, previousOnMessage, MUTABLE);
                }
                return incoming.get();
            }));
        }
        if (!map.containsKey(uri(CLOSE)))
            this.jvm().put(uri(CLOSE), instLambda((lhs, inst) -> {
                this.logger().info("closing %s", this.vid());
                this.close();
                return noobj();
            }));
    }

    @Override
    public IO getIO() {
        return IO.of(this, MIME.MIMEType.APPLICATION_MTRON);
    }

    @Override
    public void onOpen(final WebSocket conn, final Handshakedata handshake) {
        try {
            this.logger().info("{{y}}%s {{g}}<=> {{y}}%s{{X}} opened w/ serializers: [{{c}}in{{y}}=>{{X}}%s,{{c}}out{{y}}=>{{X}}%s]", this.vid(), this.getOtherVID(), this.getIO().input().value, this.getIO().output().value);
            if (handshake instanceof ClientHandshake)
                this.at(uri(ON_OPEN)).apply(uri(((ClientHandshake) handshake).getResourceDescriptor()));
            else
                this.at(uri(ON_OPEN)).apply(uri(((ServerHandshake) handshake).getHttpStatusMessage()));
        } catch (final Exception e) {
            LOG.error("error processing handshake: %s", handshake, e);
        }
    }

    @Override
    public void onClose(final WebSocket conn, final int code, final String reason, final boolean remote) {
        try {
            this.logger().info("{{y}}%s {{g}}<=> {{y}}%s{{X}} closed: code={{y}}%s{{X}}, reason={{y}}%s{{X}}", this.vid(), this.getOtherVID(), code, reason);
            this.at(uri(ON_CLOSE)).apply(rec(uri(CODE), jnt(code), uri(REASON), str(reason)));
            Router.global().write(this.vid(), noobj());
        } catch (final Exception e) {
            LOG.error("error processing close: %s", this.vid(), e);
        }
    }

    @Override
    public void onMessage(final WebSocket conn, final Obj message) {
        try {
            this.at(uri(ON_MESSAGE)).apply(message);
        } catch (final Exception e) {
            LOG.error("error processing message: %s", this.vid(), e);
        }
    }

    @Override
    public void onError(final WebSocket conn, final Exception ex) {
        try {
            this.logger().error("{{y}}%s {{g}}<=> {{y}}%s{{X}} errored: %s", this.vid(), this.getOtherVID(), ex);
            this.at(uri(ON_ERROR)).apply(fail(ex));
        } catch (final Exception e) {
            LOG.error("error processing error: %s", this.vid(), e);
        }
    }

    @Override
    public WebSocket getWebSocket() {
        return this.socket;
    }

    @Override
    public void setWebSocket(final WebSocket socket) {
        this.socket = socket;
        this.socket.setAttachment(this.vid());
    }

    @Override
    public WebSocketRec clone() {
        return this;
    }
}
