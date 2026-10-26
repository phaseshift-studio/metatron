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

package studio.phaseshift.metatron.isa.iot.space.mqtt;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.iot.iotInstSet.IOT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * MQTT_SESSION — one connected client, a {@code Rec} whose state lives in its {@code Map<Obj,Obj>}: the client id, its
 * keep-alive, and its will, plus a derived {@code status} and a {@code close}. It is addressable (its {@code vid}), so a
 * delivery pair {@code [pub, uri]} names it. Only the irreducible operational bits — the {@link Socket} and the reader
 * thread — are Java fields, exactly the split {@code WebSocketRecClient} makes. Deliveries are written by the server's
 * dispatch thread through {@link #deliver}, never by a subq virtual thread.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mqttSession extends MRec implements Closeable {

    public static final fURI MQTT_SESSION_TID = IOT_ISA_TID.extend("mqtt").extend("mqtt_session");

    public static final Type MQTT_SESSION_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(MQTT_SESSION_TID)
            .isaPredicate(rec(uri(CLIENT), STR_TYPE, uri(KEEP_ALIVE), INT_TYPE))
            .create();

    private final GraphittyLogger LOG = Graphitty.log(mqttSession.class);

    private final Socket socket;
    private final mqttServer server;
    private final Thread reader;
    private volatile boolean hungUp = false;

    public mqttSession(final Socket socket, final MqttPacket.Connect connect, final mqttServer server, final fURI vid) {
        super(mutableMap(
                        uri(CLIENT), str(connect.clientId()),
                        uri(KEEP_ALIVE), jnt(connect.keepAliveSeconds())),
                MQTT_SESSION_TID, vid);
        this.socket = socket;
        this.server = server;
        if (null != connect.willTopic())
            this.jvm().put(uri(WILL), rec(
                    uri(TOPIC), uri(connect.willTopic()),
                    uri(PAYLOAD), str(null == connect.willPayload() ? "" : new String(connect.willPayload(), StandardCharsets.UTF_8)),
                    uri(QOS), jnt(connect.willQos().value()),
                    uri(RETAIN), bool(connect.willRetain())));
        // the status — live while the socket is open and not hung up, derived on read and never stored
        this.jvm().put(uri(STATUS), Network.Helper.statusOf(() -> !this.hungUp && !this.socket.isClosed()));
        // close — the one explicit hang-up
        this.jvm().put(uri(CLOSE), instLambda((lhs, inst) -> {
            this.close();
            return noobj();
        }));
        // send — write a pub::[topic, payload] to this client, so anyone holding the session's vid can reach it
        this.jvm().put(uri(SEND), instLambda((lhs, inst) -> {
            final Obj pub = inst.arg(0);
            if (pub.isLst())
                this.deliver(pub.asLst());
            return noobj();
        }));
        this.reader = new Thread(this::readLoop, "mqtt-session-" + connect.clientId() + "-reader");
        this.reader.setDaemon(true);
    }

    /**
     * Begin the read loop. The server starts it only after the session is indexed, so a client that hung up the
     * instant it connected retires a session the server already holds rather than one it has not yet.
     */
    public void start() {
        this.reader.start();
    }

    private void readLoop() {
        while (!this.hungUp) {
            final MqttPacket packet;
            try {
                packet = MqttCodec.read(this.socket.getInputStream());
                this.server.packetIn();
            } catch (final EOFException e) {
                this.close();
                return;
            } catch (final IOException e) {
                if (!this.hungUp)
                    LOG.debug("mqtt session %s read failed: %s", this.at(uri(CLIENT)), e.getMessage());
                this.close();
                return;
            } catch (final Exception e) {
                LOG.warn("mqtt session %s: %s", this.at(uri(CLIENT)), e);
                continue;
            }
            if (packet instanceof MqttPacket.PingReq) {
                this.write(new MqttPacket.PingResp());
            } else if (packet instanceof MqttPacket.Publish publish) {
                this.publish(publish);
            } else if (packet instanceof MqttPacket.Subscribe subscribe) {
                this.subscribe(subscribe);
            } else if (packet instanceof MqttPacket.Disconnect) {
                this.close();
                return;
            } else {
                // UNSUBSCRIBE / the qos handshake land here until they are served
                LOG.debug("mqtt session %s: %s not served yet", this.at(uri(CLIENT)), packet.getClass().getSimpleName());
            }
        }
    }

    /**
     * The single socket write, serialized: the reader's responses and the server dispatch thread's deliveries both pass
     * through here.
     */
    public synchronized void write(final MqttPacket packet) {
        this.server.packetOut();
        try {
            MqttCodec.write(this.socket.getOutputStream(), packet);
        } catch (final IOException e) {
            LOG.debug("mqtt session %s write failed: %s", this.at(uri(CLIENT)), e.getMessage());
            this.close();
        }
    }

    /**
     * A delivery the server dispatch thread hands over: a {@code pub::[topic, payload]} serialized and written as its
     * PUBLISH packet.
     */
    public void deliver(final Lst pub) {
        final fURI topic = pub.at(0).uriValue();
        final Obj payload = pub.at(1);
        final byte[] bytes = this.server.serializer().outputBytes(payload).array();
        this.write(new MqttPacket.Publish(topic, bytes, MqttPacket.Qos.AT_MOST_ONCE, false, false, 0));
    }

    /**
     * PUBLISH — an mqtt payload is an {@code Obj} (this server's serializer), and the topic is a space address: the
     * write lands in whichever space claims it. Acknowledged per its qos.
     */
    private void publish(final MqttPacket.Publish publish) {
        try {
            final Obj obj = this.server.serializer().inputBytes(ByteBuffer.wrap(publish.payload()));
            this.server.publish(publish.topic(), obj);
            if (MqttPacket.Qos.AT_LEAST_ONCE == publish.qos())
                this.write(new MqttPacket.PubAck(publish.packetId()));
            else if (MqttPacket.Qos.EXACTLY_ONCE == publish.qos())
                this.write(new MqttPacket.PubRec(publish.packetId()));
            // at-most-once: no acknowledgement
        } catch (final Exception e) {
            LOG.warn("mqtt session %s publish failed: %s", this.at(uri(CLIENT)), e.getMessage());
        }
    }

    /**
     * SUBSCRIBE — each filter is registered (or refused) by the server, and the granted qos answered per filter.
     */
    private void subscribe(final MqttPacket.Subscribe subscribe) {
        final List<Integer> returnCodes = new ArrayList<>();
        for (final MqttPacket.Subscription sub : subscribe.subscriptions()) {
            returnCodes.add(this.server.subscribe(sub.topicFilter(), this));
        }
        this.write(new MqttPacket.SubAck(subscribe.packetId(), returnCodes));
    }

    public Socket socket() {
        return this.socket;
    }

    @Override
    public void close() {
        if (this.hungUp)
            return;
        this.hungUp = true;
        try {
            this.socket.close();
        } catch (final IOException ignored) {
            // already closing
        }
        this.reader.interrupt();
        this.server.retire(this);
    }
}
