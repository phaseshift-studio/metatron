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

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.iot.space.mqtt.MqttPacket.*;
import static studio.phaseshift.metatron.isa.iot.space.mqtt.MqttPacket.Qos.*;

/**
 * MQTT 3.1.1 wire codec round trips and golden bytes. The codec is pure byte work — no VM — so this is a plain JUnit
 * test rather than an {@code AbstractMetatronTest}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MqttCodecTest {

    // ======================== golden bytes ========================

    @Test
    public void publishQos0GoldenBytes() {
        final Publish publish = new Publish(f("a"), new byte[0], AT_MOST_ONCE, false, false, 0);
        assertArrayEquals(new byte[]{0x30, 0x03, 0x00, 0x01, 'a'}, MqttCodec.encode(publish));
    }

    @Test
    public void connectGoldenBytes() {
        final Connect connect = new Connect("ab", true, 60, null, null, AT_MOST_ONCE, false, null, null);
        assertArrayEquals(
                new byte[]{0x10, 0x0E, 0x00, 0x04, 'M', 'Q', 'T', 'T', 0x04, 0x02, 0x00, 0x3C, 0x00, 0x02, 'a', 'b'},
                MqttCodec.encode(connect));
    }

    @Test
    public void subscribeGoldenBytes() {
        final Subscribe subscribe = new Subscribe(1, List.of(new Subscription(f("a/b"), AT_LEAST_ONCE)));
        assertArrayEquals(
                new byte[]{(byte) 0x82, 0x08, 0x00, 0x01, 0x00, 0x03, 'a', '/', 'b', 0x01},
                MqttCodec.encode(subscribe));
    }

    // ======================== round trips ========================

    @Test
    public void connectRoundTripWithWillAndCredentials() {
        final Connect connect = new Connect("sensor-1", true, 30, f("sensors/will"),
                new byte[]{0x0F, 0x10}, AT_LEAST_ONCE, true, "user", new byte[]{'p', 'w'});
        final Connect decoded = (Connect) MqttCodec.decode(MqttCodec.encode(connect));
        assertEquals(connect.clientId(), decoded.clientId());
        assertEquals(connect.cleanSession(), decoded.cleanSession());
        assertEquals(connect.keepAliveSeconds(), decoded.keepAliveSeconds());
        assertEquals(connect.willTopic(), decoded.willTopic());
        assertArrayEquals(connect.willPayload(), decoded.willPayload());
        assertEquals(connect.willQos(), decoded.willQos());
        assertEquals(connect.willRetain(), decoded.willRetain());
        assertEquals(connect.userName(), decoded.userName());
        assertArrayEquals(connect.password(), decoded.password());
    }

    @Test
    public void connectRoundTripBare() {
        final Connect connect = new Connect("", false, 0, null, null, AT_MOST_ONCE, false, null, null);
        final Connect decoded = (Connect) MqttCodec.decode(MqttCodec.encode(connect));
        assertEquals(connect.clientId(), decoded.clientId());
        assertEquals(connect.cleanSession(), decoded.cleanSession());
        assertEquals(connect.keepAliveSeconds(), decoded.keepAliveSeconds());
        assertNull(decoded.willTopic());
        assertNull(decoded.userName());
        assertNull(decoded.password());
    }

    @Test
    public void connAckRoundTrip() {
        final ConnAck connAck = new ConnAck(true, 0);
        assertEquals(connAck, MqttCodec.decode(MqttCodec.encode(connAck)));
    }

    @Test
    public void publishRoundTripAcrossQos() {
        for (final Qos qos : Qos.values()) {
            final Publish publish = new Publish(f("sensors/temp/1"), new byte[]{1, 2, 3}, qos, true, true, 42);
            final Publish decoded = (Publish) MqttCodec.decode(MqttCodec.encode(publish));
            assertEquals(publish.topic(), decoded.topic());
            assertArrayEquals(publish.payload(), decoded.payload());
            assertEquals(publish.qos(), decoded.qos());
            assertEquals(publish.retain(), decoded.retain());
            assertEquals(publish.dup(), decoded.dup());
            assertEquals(qos == AT_MOST_ONCE ? 0 : publish.packetId(), decoded.packetId());
        }
    }

    @Test
    public void ackRoundTrips() {
        assertEquals(new PubAck(7), MqttCodec.decode(MqttCodec.encode(new PubAck(7))));
        assertEquals(new PubRec(8), MqttCodec.decode(MqttCodec.encode(new PubRec(8))));
        assertEquals(new PubRel(9), MqttCodec.decode(MqttCodec.encode(new PubRel(9))));
        assertEquals(new PubComp(10), MqttCodec.decode(MqttCodec.encode(new PubComp(10))));
    }

    @Test
    public void subscribeRoundTripMultipleFilters() {
        final Subscribe subscribe = new Subscribe(3, List.of(
                new Subscription(f("a/+"), AT_LEAST_ONCE),
                new Subscription(f("b/#"), EXACTLY_ONCE),
                new Subscription(f("c"), AT_MOST_ONCE)));
        final Subscribe decoded = (Subscribe) MqttCodec.decode(MqttCodec.encode(subscribe));
        assertEquals(subscribe.packetId(), decoded.packetId());
        assertEquals(subscribe.subscriptions(), decoded.subscriptions());
    }

    @Test
    public void subAckRoundTrip() {
        final SubAck subAck = new SubAck(4, List.of(0, 1, 2, 0x80));
        assertEquals(subAck, MqttCodec.decode(MqttCodec.encode(subAck)));
    }

    @Test
    public void unsubscribeRoundTrip() {
        final Unsubscribe unsubscribe = new Unsubscribe(5, List.of(f("a/b"), f("c/#")));
        assertEquals(unsubscribe, MqttCodec.decode(MqttCodec.encode(unsubscribe)));
    }

    @Test
    public void unsubAckRoundTrip() {
        assertEquals(new UnsubAck(6), MqttCodec.decode(MqttCodec.encode(new UnsubAck(6))));
    }

    @Test
    public void pingRoundTrips() {
        assertEquals(new PingReq(), MqttCodec.decode(MqttCodec.encode(new PingReq())));
        assertEquals(new PingResp(), MqttCodec.decode(MqttCodec.encode(new PingResp())));
        assertEquals(new Disconnect(), MqttCodec.decode(MqttCodec.encode(new Disconnect())));
    }

    // ======================== variable byte integer boundaries ========================

    @Test
    public void remainingLengthBoundariesRoundTrip() {
        // 127, 128, 16383, 16384 cross the 1/2/3-byte varint boundaries; a payload that size exercises them
        for (final int size : new int[]{0, 127, 128, 16_383, 16_384}) {
            final byte[] payload = new byte[size];
            final Publish publish = new Publish(f("t"), payload, AT_MOST_ONCE, false, false, 0);
            final Publish decoded = (Publish) MqttCodec.decode(MqttCodec.encode(publish));
            assertArrayEquals(payload, decoded.payload());
        }
    }

    // ======================== malformed input ========================

    @Test
    public void truncatedPacketFails() {
        final byte[] connect = MqttCodec.encode(new Connect("ab", true, 60, null, null, AT_MOST_ONCE, false, null, null));
        final byte[] truncated = new byte[connect.length - 2];
        System.arraycopy(connect, 0, truncated, 0, truncated.length);
        assertThrows(MTronException.class, () -> MqttCodec.decode(truncated));
    }

    @Test
    public void badPublishQosFails() {
        // 0x36 = PUBLISH with qos 3 (invalid in 3.1.1)
        assertThrows(MTronException.class, () -> MqttCodec.decode(new byte[]{0x36, 0x03, 0x00, 0x01, 'a'}));
    }

    @Test
    public void badSubscribeFlagsFail() {
        // SUBSCRIBE (0x80) must carry flags 0x02; 0x00 is malformed
        assertThrows(MTronException.class, () -> MqttCodec.decode(new byte[]{(byte) 0x80, 0x02, 0x00, 0x01}));
    }

    @Test
    public void trailingBytesFail() {
        final byte[] publish = MqttCodec.encode(new Publish(f("a"), new byte[0], AT_MOST_ONCE, false, false, 0));
        final byte[] trailing = new byte[publish.length + 1];
        System.arraycopy(publish, 0, trailing, 0, publish.length);
        assertThrows(MTronException.class, () -> MqttCodec.decode(trailing));
    }
}
