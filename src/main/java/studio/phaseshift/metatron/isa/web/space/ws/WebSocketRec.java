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
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Real;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.web.type.MIME;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.EVAL_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.FROM_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.TO_INST_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.from_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.start_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
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
        // THE RAW WIRE INSTRUCTION — send a message and await its reply: a user's instruction (and the wire trace's
        // round-trip kind), not the network's reach; the network reaches this connection through its mini-instset
        final BiFunction<Obj, Inst, Obj> shipAndAwait = (lhs1, inst1) -> {
            try {
                final Obj toSend = inst1.arg(0);
                final Real timeoutMs = inst1.arg(1).orElse(real(-1.0));
                return timeoutMs.realValue() == -1.0
                        ? this.sendRecv(toSend)
                        : this.sendRecv(toSend, timeoutMs.realValue().longValue());
            } catch (final Exception e) {
                LOG.error("error in sendRecv: %s", e);
                return fail(e);
            }
        };
        if (!map.containsKey(uri(SEND_RECV)))
            this.jvm().put(uri(SEND_RECV), instLambda(shipAndAwait));
        // THE MINI-INSTSET — the instructions you talk to this connection with, each on its native signature with this
        // client's own body (Network.Helper.override), so the network reaches each by swapping f() alone:
        //   eval(code)    ships the code: the network calls eval?block(code), so arg(0) is the code, raw;
        //   from(<uri>)   ships the localized read, from(</path>);
        //   obj.to(<uri>) ships the localized write, start(obj).to(</path>) — the obj is the lhs, as the native's.
        // The far side evaluates what it is sent (the wire protocol), so a read answers its value and a shipped unquoted
        // program runs there. A compiled from/to whose address names this peer binds to these bodies
        // (Network.Helper.atPeer). A caller that brings its OWN inst — a custom to(), say — keeps it. With no instruction
        // memory to read the natives' signatures from, the client declares nothing.
        if (!map.containsKey(uri(INST))) {
            final Rec overrides = rec();
            final Obj eval = Network.Helper.override(EVAL_INST_TID, shipAndAwait);
            final Obj from = Network.Helper.override(FROM_INST_TID, (lhs, inst) ->
                    this.ship(() -> from_(inst.arg(0).uriValue().localize().toUri()).tryToInst()));
            final Obj to = Network.Helper.override(TO_INST_TID, (lhs, inst) ->
                    this.ship(() -> start_(lhs).to_(inst.arg(0).uriValue().localize().toUri()).tryToInst()));
            if (eval.isObjInst())
                overrides.jvm().put(uri(EVAL_INST_TID), eval);
            if (from.isObjInst())
                overrides.jvm().put(uri(FROM_INST_TID), from);
            if (to.isObjInst())
                overrides.jvm().put(uri(TO_INST_TID), to);
            if (!overrides.isEmpty())
                this.jvm().put(uri(INST), overrides);
        }
        // THE STATUS — live while its socket is open, derived on read: a socket that closed (or has not opened yet)
        // reads false at once, and a redial that reopens it reads true again, with no field to update
        if (!map.containsKey(uri(STATUS)))
            this.jvm().put(uri(STATUS), Network.Helper.statusOf(() -> {
                final WebSocket socket = this.getWebSocket();
                return null != socket && socket.isOpen();
            }));
        if (!map.containsKey(uri(CLOSE)))
            this.jvm().put(uri(CLOSE), instLambda((lhs, inst) -> {
                LOG.debug("closing %s", this.vid());
                this.close();
                return noobj();
            }));
    }

    /**
     * Ship the message a call builds and await the far side's answer — a fail when the message cannot be built or the
     * round trip throws: an override answers with an obj, never an exception out of the processor.
     */
    private Obj ship(final Supplier<Obj> message) {
        try {
            return this.sendRecv(message.get());
        } catch (final Exception e) {
            LOG.error("error in sendRecv: %s", e);
            return fail(e);
        }
    }

    @Override
    public IO getIO() {
        return IO.of(this, MIME.MIMEType.APPLICATION_MTRON);
    }

    public boolean state() {
        return this.state(this.getWebSocket());
    }

    @Override
    public void onOpen(final WebSocket conn, final Handshakedata handshake) {
        try {
            this.logger().status(DEBUG, "{{y}}%s {{g}}<=> {{y}}%s{{X}} opened w/ serializers: [{{c}}in{{y}}=>{{X}}%s,{{c}}out{{y}}=>{{X}}%s]", this.vid(), this.getOtherVID(), this.getIO().input().value, this.getIO().output().value);
            if (handshake instanceof ClientHandshake)
                this.at(uri(ON_OPEN)).apply(uri(((ClientHandshake) handshake).getResourceDescriptor()));
            else
                this.at(uri(ON_OPEN)).apply(uri(((ServerHandshake) handshake).getHttpStatusMessage()));
        } catch (final Exception e) {
            LOG.error("error processing handshake %s: %e", handshake, e);
        }
    }

    @Override
    public void onClose(final WebSocket conn, final int code, final String reason, final boolean remote) {
        try {
            LOG.status(DEBUG, "{{y}}%s {{g}}<=> {{y}}%s{{X}} closed: code={{y}}%s{{X}}, reason={{y}}%s{{X}}", this.vid(), this.getOtherVID() == null ? "{{r}}noobj" : this.getOtherVID(), code, reason);
            this.at(uri(ON_CLOSE)).apply(rec(uri(CODE), jnt(code), uri(REASON), str(reason)));
            // A LINK THAT FAILED HAS NOT ENDED. A dialed connection its owner did not hang up is being supervised
            // back, so it is not closed here: the round trips in flight on it are answered at once (they would
            // otherwise wait out their whole deadline for a reply that cannot come), and its router stays installed,
            // so the redialed connection keeps the one router rather than wrapping a second around it.
            if (this.socket instanceof Network.Link link && !link.hungUp())
                this.abandon("closed with code " + code + (null == reason || reason.isBlank() ? "" : ": " + reason));
            else
                this.close();
        } catch (final Exception e) {
            LOG.error("error processing close: %s", e);
        }
    }

    @Override
    public void onMessage(final WebSocket conn, final Obj message) {
        try {
            // the single inbound funnel: a client's onMessage delegates into this same method, so counting here
            // counts every frame that arrived on any socket, handled or not
            WireStats.received(this.peerName(), message.vid());
            this.at(uri(ON_MESSAGE)).apply(message);
        } catch (final Exception e) {
            LOG.error("error processing message: %s", e);
        }
    }

    @Override
    public void onError(final WebSocket conn, final Exception ex) {
        try {
            LOG.error("{{y}}%s {{g}}<=> {{y}}%s{{X}} errored: %s", this.vid(), this.getOtherVID() == null ? "{{r}}noobj" : this.getOtherVID(), ex);
            this.at(uri(ON_ERROR)).apply(fail(ex));
        } catch (final Exception e) {
            LOG.error("error processing error: %s", e);
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
