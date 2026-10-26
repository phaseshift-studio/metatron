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
import studio.phaseshift.metatron.util.MTronException;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.iot.space.mqtt.MqttPacket.*;

/**
 * The MQTT 3.1.1 wire codec — a packet's fixed header (type, flags, remaining length) and body, both directions. This
 * is the byte layer under {@code mqtt_server::T}, not an {@code Obj}: a packet is bytes on a socket, transient, and is
 * never addressed. The session that holds the socket is the {@code Rec} (see {@code mqttSession}); this class just
 * turns those bytes into records and back.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class MqttCodec {

    private static final String PROTOCOL_NAME = "MQTT";
    private static final int PROTOCOL_LEVEL = 4; // 3.1.1
    private static final int MAX_KEEP_ALIVE = 0xFFFF;

    private MqttCodec() {
        // static only
    }

    // ======================== encode ========================

    public static byte[] encode(final MqttPacket packet) {
        final byte[] body = encodeBody(packet);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(typeAndFlags(packet));
        writeVariableByteInteger(out, body.length);
        out.write(body, 0, body.length);
        return out.toByteArray();
    }

    private static int typeAndFlags(final MqttPacket packet) {
        if (packet instanceof Connect)
            return 0x10;
        if (packet instanceof ConnAck)
            return 0x20;
        if (packet instanceof Publish publish)
            return 0x30 | (publish.dup() ? 0x08 : 0) | (publish.qos().value() << 1) | (publish.retain() ? 0x01 : 0);
        if (packet instanceof PubAck)
            return 0x40;
        if (packet instanceof PubRec)
            return 0x50;
        if (packet instanceof PubRel)
            return 0x62;
        if (packet instanceof PubComp)
            return 0x70;
        if (packet instanceof Subscribe)
            return 0x82;
        if (packet instanceof SubAck)
            return 0x90;
        if (packet instanceof Unsubscribe)
            return 0xA2;
        if (packet instanceof UnsubAck)
            return 0xB0;
        if (packet instanceof PingReq)
            return 0xC0;
        if (packet instanceof PingResp)
            return 0xD0;
        if (packet instanceof Disconnect)
            return 0xE0;
        throw MTronException.of("unknown mqtt packet: %s", packet);
    }

    private static byte[] encodeBody(final MqttPacket packet) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (packet instanceof Connect connect) {
            writeUtf8(out, PROTOCOL_NAME);
            out.write(PROTOCOL_LEVEL);
            final boolean will = null != connect.willTopic();
            final int flags = (null != connect.userName() ? 0x80 : 0)
                    | (null != connect.password() ? 0x40 : 0)
                    | (will && connect.willRetain() ? 0x20 : 0)
                    | (will ? connect.willQos().value() << 3 : 0)
                    | (will ? 0x04 : 0)
                    | (connect.cleanSession() ? 0x02 : 0);
            out.write(flags);
            writeUnsignedShort(out, connect.keepAliveSeconds());
            writeUtf8(out, connect.clientId());
            if (will) {
                writeUtf8(out, connect.willTopic().toString());
                writeBinary(out, connect.willPayload());
            }
            if (null != connect.userName())
                writeUtf8(out, connect.userName());
            if (null != connect.password())
                writeBinary(out, connect.password());
        } else if (packet instanceof ConnAck connAck) {
            out.write(connAck.sessionPresent() ? 1 : 0);
            out.write(connAck.returnCode());
        } else if (packet instanceof Publish publish) {
            writeUtf8(out, publish.topic().toString());
            if (Qos.AT_MOST_ONCE != publish.qos())
                writeUnsignedShort(out, publish.packetId());
            final byte[] payload = null == publish.payload() ? new byte[0] : publish.payload();
            out.write(payload, 0, payload.length);
        } else if (packet instanceof PubAck pubAck) {
            writeUnsignedShort(out, pubAck.packetId());
        } else if (packet instanceof PubRec pubRec) {
            writeUnsignedShort(out, pubRec.packetId());
        } else if (packet instanceof PubRel pubRel) {
            writeUnsignedShort(out, pubRel.packetId());
        } else if (packet instanceof PubComp pubComp) {
            writeUnsignedShort(out, pubComp.packetId());
        } else if (packet instanceof Subscribe subscribe) {
            writeUnsignedShort(out, subscribe.packetId());
            // each subscription is a filter immediately followed by its requested qos byte
            for (final Subscription subscription : subscribe.subscriptions()) {
                writeUtf8(out, subscription.topicFilter().toString());
                out.write(subscription.qos().value());
            }
        } else if (packet instanceof SubAck subAck) {
            writeUnsignedShort(out, subAck.packetId());
            for (final int returnCode : subAck.returnCodes())
                out.write(returnCode);
        } else if (packet instanceof Unsubscribe unsubscribe) {
            writeUnsignedShort(out, unsubscribe.packetId());
            for (final fURI filter : unsubscribe.topicFilters())
                writeUtf8(out, filter.toString());
        } else if (packet instanceof UnsubAck unsubAck) {
            writeUnsignedShort(out, unsubAck.packetId());
        } else if (packet instanceof PingReq || packet instanceof PingResp || packet instanceof Disconnect) {
            // no body
        } else {
            throw MTronException.of("unknown mqtt packet: %s", packet);
        }
        return out.toByteArray();
    }

    // ======================== decode ========================

    public static MqttPacket decode(final byte[] bytes) {
        try {
            final ByteBuffer in = ByteBuffer.wrap(bytes);
            final int first = in.get() & 0xFF;
            final int remainingLength = readVariableByteInteger(in);
            if (in.remaining() != remainingLength)
                throw MTronException.of("malformed mqtt packet: remaining length %s but %s bytes follow", remainingLength, in.remaining());
            final byte[] body = new byte[remainingLength];
            in.get(body);
            return decode(first, body);
        } catch (final BufferUnderflowException e) {
            throw MTronException.of("truncated mqtt packet");
        }
    }

    /**
     * Decode from the fixed-header first byte and the body (variable header and payload), for a caller that has
     * already split the two off a stream.
     */
    public static MqttPacket decode(final int firstByte, final byte[] body) {
        try {
            final int type = (firstByte & 0xFF) >>> 4;
            final int flags = firstByte & 0x0F;
            final ByteBuffer bodyBuf = ByteBuffer.wrap(body);
            final MqttPacket packet = decodeBody(type, flags, bodyBuf);
            if (bodyBuf.hasRemaining())
                throw MTronException.of("malformed mqtt packet type %s: %s trailing bytes", type, bodyBuf.remaining());
            return packet;
        } catch (final BufferUnderflowException e) {
            throw MTronException.of("truncated mqtt packet");
        }
    }

    /**
     * Read one packet off a stream: the fixed header, then the remaining-length body.
     */
    public static MqttPacket read(final InputStream in) throws IOException {
        final int first = in.read();
        if (first < 0)
            throw new EOFException("mqtt stream ended before a packet");
        final int remainingLength = readVariableByteInteger(in);
        final byte[] body = new byte[remainingLength];
        readFully(in, body);
        return decode(first, body);
    }

    /**
     * Write a packet to a stream.
     */
    public static void write(final OutputStream out, final MqttPacket packet) throws IOException {
        out.write(encode(packet));
    }

    private static MqttPacket decodeBody(final int type, final int flags, final ByteBuffer in) {
        switch (type) {
            case 1: return decodeConnect(flags, in);
            case 2: return decodeConnAck(flags, in);
            case 3: return decodePublish(flags, in);
            case 4: return decodePubAck(flags, in);
            case 5: return decodePubRec(flags, in);
            case 6: return decodePubRel(flags, in);
            case 7: return decodePubComp(flags, in);
            case 8: return decodeSubscribe(flags, in);
            case 9: return decodeSubAck(flags, in);
            case 10: return decodeUnsubscribe(flags, in);
            case 11: return decodeUnsubAck(flags, in);
            case 12: return decodePingReq(flags);
            case 13: return decodePingResp(flags);
            case 14: return decodeDisconnect(flags);
            default: throw MTronException.of("unknown mqtt packet type: %s", type);
        }
    }

    private static Connect decodeConnect(final int flags, final ByteBuffer in) {
        requireFlags(flags, 0x00, 1);
        final String protocolName = readUtf8(in);
        if (!PROTOCOL_NAME.equals(protocolName))
            throw MTronException.of("unsupported mqtt protocol name: %s", protocolName);
        final int level = in.get() & 0xFF;
        if (PROTOCOL_LEVEL != level)
            throw MTronException.of("unsupported mqtt protocol level: %s", level);
        final int connectFlags = in.get() & 0xFF;
        if (0 != (connectFlags & 0x01))
            throw MTronException.of("malformed mqtt connect: reserved flag bit set");
        final boolean cleanSession = 0 != (connectFlags & 0x02);
        final boolean willFlag = 0 != (connectFlags & 0x04);
        final Qos willQos = Qos.of((connectFlags >>> 3) & 0x03);
        final boolean willRetain = 0 != (connectFlags & 0x20);
        final boolean passwordFlag = 0 != (connectFlags & 0x40);
        final boolean userNameFlag = 0 != (connectFlags & 0x80);
        final int keepAlive = readUnsignedShort(in);
        final String clientId = readUtf8(in);
        final fURI willTopic = willFlag ? f(readUtf8(in)) : null;
        final byte[] willPayload = willFlag ? readBinary(in) : null;
        final String userName = userNameFlag ? readUtf8(in) : null;
        final byte[] password = passwordFlag ? readBinary(in) : null;
        return new Connect(clientId, cleanSession, keepAlive, willTopic, willPayload, willQos, willRetain, userName, password);
    }

    private static ConnAck decodeConnAck(final int flags, final ByteBuffer in) {
        requireFlags(flags, 0x00, 2);
        final boolean sessionPresent = 0 != (in.get() & 0x01);
        final int returnCode = in.get() & 0xFF;
        return new ConnAck(sessionPresent, returnCode);
    }

    private static Publish decodePublish(final int flags, final ByteBuffer in) {
        final boolean dup = 0 != (flags & 0x08);
        final int qosValue = (flags >>> 1) & 0x03;
        if (qosValue > 2)
            throw MTronException.of("malformed mqtt publish: qos %s", qosValue);
        final Qos qos = Qos.of(qosValue);
        final boolean retain = 0 != (flags & 0x01);
        final fURI topic = f(readUtf8(in));
        final int packetId = Qos.AT_MOST_ONCE == qos ? 0 : readUnsignedShort(in);
        final byte[] payload = new byte[in.remaining()];
        in.get(payload);
        return new Publish(topic, payload, qos, retain, dup, packetId);
    }

    private static PubAck decodePubAck(final int flags, final ByteBuffer in) {
        requireFlags(flags, 0x00, 4);
        return new PubAck(readUnsignedShort(in));
    }

    private static PubRec decodePubRec(final int flags, final ByteBuffer in) {
        requireFlags(flags, 0x00, 5);
        return new PubRec(readUnsignedShort(in));
    }

    private static PubRel decodePubRel(final int flags, final ByteBuffer in) {
        requireFlags(flags, 0x02, 6);
        return new PubRel(readUnsignedShort(in));
    }

    private static PubComp decodePubComp(final int flags, final ByteBuffer in) {
        requireFlags(flags, 0x00, 7);
        return new PubComp(readUnsignedShort(in));
    }

    private static Subscribe decodeSubscribe(final int flags, final ByteBuffer in) {
        requireFlags(flags, 0x02, 8);
        final int packetId = readUnsignedShort(in);
        final List<Subscription> subscriptions = new ArrayList<>();
        while (in.hasRemaining()) {
            final fURI filter = f(readUtf8(in));
            final int options = in.get() & 0xFF;
            if (0 != (options & 0xFC))
                throw MTronException.of("malformed mqtt subscribe: reserved bits set in 0x%x", options);
            subscriptions.add(new Subscription(filter, Qos.of(options & 0x03)));
        }
        return new Subscribe(packetId, subscriptions);
    }

    private static SubAck decodeSubAck(final int flags, final ByteBuffer in) {
        requireFlags(flags, 0x00, 9);
        final int packetId = readUnsignedShort(in);
        final List<Integer> returnCodes = new ArrayList<>();
        while (in.hasRemaining())
            returnCodes.add(in.get() & 0xFF);
        return new SubAck(packetId, returnCodes);
    }

    private static Unsubscribe decodeUnsubscribe(final int flags, final ByteBuffer in) {
        requireFlags(flags, 0x02, 10);
        final int packetId = readUnsignedShort(in);
        final List<fURI> filters = new ArrayList<>();
        while (in.hasRemaining())
            filters.add(f(readUtf8(in)));
        return new Unsubscribe(packetId, filters);
    }

    private static UnsubAck decodeUnsubAck(final int flags, final ByteBuffer in) {
        requireFlags(flags, 0x00, 11);
        return new UnsubAck(readUnsignedShort(in));
    }

    private static PingReq decodePingReq(final int flags) {
        requireFlags(flags, 0x00, 12);
        return new PingReq();
    }

    private static PingResp decodePingResp(final int flags) {
        requireFlags(flags, 0x00, 13);
        return new PingResp();
    }

    private static Disconnect decodeDisconnect(final int flags) {
        requireFlags(flags, 0x00, 14);
        return new Disconnect();
    }

    // ======================== primitives ========================

    private static void writeVariableByteInteger(final ByteArrayOutputStream out, final int value) {
        int remaining = value;
        do {
            int digit = remaining % 128;
            remaining /= 128;
            if (remaining > 0)
                digit |= 0x80;
            out.write(digit);
        } while (remaining > 0);
    }

    private static int readVariableByteInteger(final ByteBuffer in) {
        int value = 0;
        int multiplier = 1;
        for (int i = 0; i < 4; i++) {
            final int digit = in.get() & 0xFF;
            value += (digit & 0x7F) * multiplier;
            if (0 == (digit & 0x80))
                return value;
            multiplier *= 128;
        }
        throw MTronException.of("malformed mqtt variable byte integer");
    }

    private static int readVariableByteInteger(final InputStream in) throws IOException {
        int value = 0;
        int multiplier = 1;
        for (int i = 0; i < 4; i++) {
            final int digit = in.read();
            if (digit < 0)
                throw new EOFException("mqtt stream ended in a variable byte integer");
            value += (digit & 0x7F) * multiplier;
            if (0 == (digit & 0x80))
                return value;
            multiplier *= 128;
        }
        throw MTronException.of("malformed mqtt variable byte integer");
    }

    private static void readFully(final InputStream in, final byte[] bytes) throws IOException {
        int offset = 0;
        while (offset < bytes.length) {
            final int read = in.read(bytes, offset, bytes.length - offset);
            if (read < 0)
                throw new EOFException("mqtt stream ended mid-packet");
            offset += read;
        }
    }

    private static void writeUnsignedShort(final ByteArrayOutputStream out, final int value) {
        if (value < 0 || value > MAX_KEEP_ALIVE)
            throw MTronException.of("mqtt unsigned short out of range: %s", value);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static int readUnsignedShort(final ByteBuffer in) {
        return ((in.get() & 0xFF) << 8) | (in.get() & 0xFF);
    }

    private static void writeUtf8(final ByteArrayOutputStream out, final String string) {
        final byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        writeUnsignedShort(out, bytes.length);
        out.write(bytes, 0, bytes.length);
    }

    private static String readUtf8(final ByteBuffer in) {
        final int length = readUnsignedShort(in);
        if (in.remaining() < length)
            throw MTronException.of("malformed mqtt utf-8 string: %s bytes declared, %s available", length, in.remaining());
        final byte[] bytes = new byte[length];
        in.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeBinary(final ByteArrayOutputStream out, final byte[] bytes) {
        final byte[] data = null == bytes ? new byte[0] : bytes;
        writeUnsignedShort(out, data.length);
        out.write(data, 0, data.length);
    }

    private static byte[] readBinary(final ByteBuffer in) {
        final int length = readUnsignedShort(in);
        if (in.remaining() < length)
            throw MTronException.of("malformed mqtt binary: %s bytes declared, %s available", length, in.remaining());
        final byte[] bytes = new byte[length];
        in.get(bytes);
        return bytes;
    }

    private static void requireFlags(final int flags, final int expected, final int type) {
        if (flags != expected)
            throw MTronException.of("malformed mqtt packet type %s: flags 0x%x, expected 0x%x", type, flags, expected);
    }
}
