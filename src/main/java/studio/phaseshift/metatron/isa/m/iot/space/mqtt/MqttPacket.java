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

package studio.phaseshift.metatron.isa.m.iot.space.mqtt;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;

/**
 * MQTT 3.1.1 control packets — the wire model a {@link MqttCodec} encodes and decodes. This is the transport layer
 * under {@code mqtt_server::T}, deliberately plain Java (records) rather than an {@code Obj}: a packet is bytes on a
 * socket, not an addressable value.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface MqttPacket {

    /**
     * The three MQTT delivery guarantees.
     */
    enum Qos {
        AT_MOST_ONCE(0), AT_LEAST_ONCE(1), EXACTLY_ONCE(2);

        private final int value;

        Qos(final int value) {
            this.value = value;
        }

        public int value() {
            return this.value;
        }

        public static Qos of(final int value) {
            return switch (value) {
                case 0 -> AT_MOST_ONCE;
                case 1 -> AT_LEAST_ONCE;
                case 2 -> EXACTLY_ONCE;
                default -> throw MTronException.of("mqtt qos is 0, 1, or 2, got %s", value);
            };
        }
    }

    /**
     * One subscription in a SUBSCRIBE payload: a topic filter and the requested qos.
     */
    record Subscription(fURI topicFilter, Qos qos) {
    }

    /**
     * CONNECT — a client establishing (or resuming) a session. {@code null} will topic, user name or password
     * suppresses the corresponding flag.
     */
    record Connect(String clientId, boolean cleanSession, int keepAliveSeconds,
                   fURI willTopic, byte[] willPayload, Qos willQos, boolean willRetain,
                   String userName, byte[] password) implements MqttPacket {
    }

    /**
     * CONNACK — the broker's acceptance or refusal of a CONNECT.
     */
    record ConnAck(boolean sessionPresent, int returnCode) implements MqttPacket {
    }

    /**
     * PUBLISH — an application message on a topic, either direction. {@code packetId} is present only above qos 0.
     */
    record Publish(fURI topic, byte[] payload, Qos qos, boolean retain, boolean dup,
                   int packetId) implements MqttPacket {
    }

    record PubAck(int packetId) implements MqttPacket {
    }

    record PubRec(int packetId) implements MqttPacket {
    }

    record PubRel(int packetId) implements MqttPacket {
    }

    record PubComp(int packetId) implements MqttPacket {
    }

    record Subscribe(int packetId, List<Subscription> subscriptions) implements MqttPacket {
    }

    record SubAck(int packetId, List<Integer> returnCodes) implements MqttPacket {
    }

    record Unsubscribe(int packetId, List<fURI> topicFilters) implements MqttPacket {
    }

    record UnsubAck(int packetId) implements MqttPacket {
    }

    record PingReq() implements MqttPacket {
    }

    record PingResp() implements MqttPacket {
    }

    record Disconnect() implements MqttPacket {
    }
}
