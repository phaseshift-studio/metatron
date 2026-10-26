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
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.ROUTE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.iot.space.mqtt.MqttPacket.Qos.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The mqtt_server, one component per test: a live socket against a live server, each test exercising a single
 * behavior — CONNECT/CONNACK, PINGREQ/PINGRESP, DISCONNECT retiring the session, a PUBLISH landing in the space
 * graph, the acknowledgment a qos-bearing PUBLISH earns, a SUBSCRIBE granted or refused, and the full delivery
 * loop (a subscriber's filter hearing a publish and the PUBLISH it earns in return).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mqttServerTest extends AbstractMetatronTest {

    private static final int WINDOW_MS = 5_000;

    /**
     * the subq-bearing space the server projects onto — mqtt topics are its addresses
     */
    private static final fURI FOUNDRY = f("/foundry");
    /**
     * a plain space without subq — a filter over it must be refused, not mutated
     */
    private static final fURI PLAIN = f("/plain");

    public mqttServerTest() {
        final memSpace foundry = memSpace.of(FOUNDRY.extend(ALL), f("~/sys/space/mqtt/foundry"));
        foundry.addQ(QCollection.subQ());
        memSpace.of(PLAIN.extend(ALL), f("~/sys/space/mqtt/plain"));
    }

    // ======================== one exchange at a time ========================

    @Test
    public void connectAnswersConnAck() throws Exception {
        try (final Live live = serve()) {
            try (final Socket client = open(live.port())
            ) {
                MqttCodec.write(client.getOutputStream(), connect("kiln-gauge"));
                final MqttPacket connAck = MqttCodec.read(client.getInputStream());
                assertInstanceOf(MqttPacket.ConnAck.class, connAck);
                assertEquals(0, ((MqttPacket.ConnAck) connAck).returnCode());
            }
        }
    }

    @Test
    public void pingReqAnswersPingResp() throws Exception {
        try (final Live live = serve()) {
            try (final Socket client = open(live.port())
            ) {
                MqttCodec.write(client.getOutputStream(), connect("kiln-gauge"));
                readConnAck(client);
                MqttCodec.write(client.getOutputStream(), new MqttPacket.PingReq());
                assertInstanceOf(MqttPacket.PingResp.class, MqttCodec.read(client.getInputStream()));
            }
        }
    }

    @Test
    public void disconnectRetiresTheSession() throws Exception {
        try (final Live live = serve()) {
            final Socket client = open(live.port());
            MqttCodec.write(client.getOutputStream(), connect("kiln-gauge"));
            readConnAck(client);
            assertTrue(awaitsCount(live.server(), 1), "session should be live after CONNECT");
            MqttCodec.write(client.getOutputStream(), new MqttPacket.Disconnect());
            assertTrue(awaitsCount(live.server(), 0), "session should retire after DISCONNECT");
            client.close();
        }
    }

    // ======================== the write half: a publish is a space write ========================

    @Test
    public void publishLandsInTheSpaceGraph() throws Exception {
        final fURI topic = FOUNDRY.extend("press/1/temp");
        try (final Live live = serve()) {
            try (final Socket client = open(live.port())
            ) {
                MqttCodec.write(client.getOutputStream(), connect("press-arm"));
                readConnAck(client);
                final byte[] payload = "{\"temp\":907}".getBytes(StandardCharsets.UTF_8);
                MqttCodec.write(client.getOutputStream(), new MqttPacket.Publish(topic, payload, AT_MOST_ONCE, false, false, 0));
                final Obj value = await(topic);
                assertEquals(907, value.asRec().at(uri("temp")).intValue());
            }
        }
    }

    @Test
    public void qos1PublishEarnsAPubAck() throws Exception {
        final fURI topic = FOUNDRY.extend("press/1/temp");
        try (final Live live = serve()) {
            try (final Socket client = open(live.port())
            ) {
                MqttCodec.write(client.getOutputStream(), connect("press-arm"));
                readConnAck(client);
                final byte[] payload = "{\"temp\":907}".getBytes(StandardCharsets.UTF_8);
                MqttCodec.write(client.getOutputStream(), new MqttPacket.Publish(topic, payload, AT_LEAST_ONCE, false, false, 41));
                final MqttPacket reply = MqttCodec.read(client.getInputStream());
                assertInstanceOf(MqttPacket.PubAck.class, reply);
                assertEquals(41, ((MqttPacket.PubAck) reply).packetId());
            }
        }
    }

    @Test
    public void qos2PublishEarnsAPubRec() throws Exception {
        final fURI topic = FOUNDRY.extend("press/1/temp");
        try (final Live live = serve()) {
            try (final Socket client = open(live.port())
            ) {
                MqttCodec.write(client.getOutputStream(), connect("press-arm"));
                readConnAck(client);
                final byte[] payload = "{\"temp\":907}".getBytes(StandardCharsets.UTF_8);
                MqttCodec.write(client.getOutputStream(), new MqttPacket.Publish(topic, payload, EXACTLY_ONCE, false, false, 42));
                final MqttPacket reply = MqttCodec.read(client.getInputStream());
                assertInstanceOf(MqttPacket.PubRec.class, reply);
                assertEquals(42, ((MqttPacket.PubRec) reply).packetId());
            }
        }
    }

    // ======================== the read half: a subscribe, granted or refused ========================

    @Test
    public void subscribeIsGrantedOverASubqSpace() throws Exception {
        try (final Live live = serve()) {
            try (final Socket client = open(live.port())
            ) {
                MqttCodec.write(client.getOutputStream(), connect("melter"));
                readConnAck(client);
                MqttCodec.write(client.getOutputStream(), new MqttPacket.Subscribe(7,
                        List.of(new MqttPacket.Subscription(FOUNDRY.extend("press/#"), AT_MOST_ONCE))));
                readSubAck(client, 7, List.of(0));
            }
        }
    }

    @Test
    public void subscribeIsRefusedOverAPlainSpace() throws Exception {
        try (final Live live = serve()) {
            try (final Socket client = open(live.port())
            ) {
                MqttCodec.write(client.getOutputStream(), connect("melter"));
                readConnAck(client);
                MqttCodec.write(client.getOutputStream(), new MqttPacket.Subscribe(8,
                        List.of(new MqttPacket.Subscription(PLAIN.extend("press/#"), AT_MOST_ONCE))));
                // 0x80 — the address is not subscribable, not a silent no-op
                readSubAck(client, 8, List.of(0x80));
            }
        }
    }

    // ======================== the whole loop: subscribe + publish = delivery ========================

    @Test
    public void subscriberHearsThePublishItsFilterCovers() throws Exception {
        final fURI topic = FOUNDRY.extend("press/1/temp");
        try (final Live live = serve()) {
            try (final Socket client = open(live.port())) {
                MqttCodec.write(client.getOutputStream(), connect("melter"));
                readConnAck(client);
                MqttCodec.write(client.getOutputStream(), new MqttPacket.Subscribe(9,
                        List.of(new MqttPacket.Subscription(FOUNDRY.extend("press/#"), AT_MOST_ONCE))));
                readSubAck(client, 9, List.of(0));
                final byte[] payload = "{\"temp\":907}".getBytes(StandardCharsets.UTF_8);
                MqttCodec.write(client.getOutputStream(), new MqttPacket.Publish(topic, payload, AT_MOST_ONCE, false, false, 0));
                // the delivery rides the subq virtual thread, the queue and the dispatch thread — poll for it
                final MqttPacket delivered = readWithin(client, WINDOW_MS);
                assertInstanceOf(MqttPacket.Publish.class, delivered);
                final MqttPacket.Publish pub = (MqttPacket.Publish) delivered;
                assertEquals(topic, pub.topic());
                final Obj value = ObjJSONSerializer.simple().inputBytes(pub.payload());
                assertEquals(907, value.asRec().at(uri("temp")).intValue());
            }
        }
    }

    // ======================== the broker's own namespace: $SYS ========================

    @Test
    public void sysServesTheBrokerItself() throws Exception {
        try (final Live live = serve()) {
            try (final Socket client = open(live.port())) {
                MqttCodec.write(client.getOutputStream(), connect("dial-thermometer"));
                readConnAck(client);
                MqttCodec.write(client.getOutputStream(), new MqttPacket.Subscribe(21,
                        List.of(new MqttPacket.Subscription(f("$SYS/broker/#"), AT_MOST_ONCE))));
                // the broker's own leaves ride the dispatch thread and race the SubAck on the same socket —
                // read the whole burst and judge the tree and the ack from it, in whatever order the wire chose
                final List<MqttPacket> burst = readBurst(client, 9);
                final MqttPacket subAck = burst.stream().filter(MqttPacket.SubAck.class::isInstance).findFirst()
                        .orElseThrow(() -> new AssertionError("no SubAck in the burst: " + burst));
                assertEquals(21, ((MqttPacket.SubAck) subAck).packetId());
                assertEquals(List.of(0), ((MqttPacket.SubAck) subAck).returnCodes());
                final Map<String, Obj> tree = new LinkedHashMap<>();
                burst.stream().filter(MqttPacket.Publish.class::isInstance).map(MqttPacket.Publish.class::cast)
                        .forEach(pub -> tree.put(pub.topic().toString(), ObjJSONSerializer.simple().inputBytes(pub.payload())));
                assertTrue(tree.values().stream().anyMatch(value -> "mqtt 5.0 / metatron".equals(value.strValue())),
                        "the version leaf serves the broker's own text: " + tree);
                assertTrue(tree.values().stream().anyMatch(value -> value.isInt() && value.intValue() >= 1),
                        "the client count serves at least this very watcher: " + tree);
            }
        }
    }

    @Test
    public void sysClientsClimbAsASecondClientJoins() throws Exception {
        try (final Live live = serve()) {
            final Socket watcher = open(live.port());
            final Socket joiner = open(live.port());
            MqttCodec.write(watcher.getOutputStream(), connect("tally-keeper"));
            readConnAck(watcher);
            MqttCodec.write(watcher.getOutputStream(), new MqttPacket.Subscribe(22,
                    List.of(new MqttPacket.Subscription(f("$SYS/broker/load/clients"), AT_MOST_ONCE))));
            readSubAck(watcher, 22, List.of(0));
            final MqttPacket first = readWithin(watcher, WINDOW_MS);
            assertInstanceOf(MqttPacket.Publish.class, first);
            assertEquals(1, ObjJSONSerializer.simple().inputBytes(((MqttPacket.Publish) first).payload()).intValue());
            MqttCodec.write(joiner.getOutputStream(), connect("second-hand"));
            readConnAck(joiner);
            // the join is the moment that earns the watcher its push — the new count, derived on read
            final MqttPacket pushed = readWithin(watcher, WINDOW_MS);
            assertInstanceOf(MqttPacket.Publish.class, pushed);
            assertEquals(2, ObjJSONSerializer.simple().inputBytes(((MqttPacket.Publish) pushed).payload()).intValue());
            joiner.close();
            watcher.close();
        }
    }

    @Test
    public void aForgedSysPublishSpendsNothing() throws Exception {
        try (final Live live = serve()) {
            try (final Socket client = open(live.port())) {
                MqttCodec.write(client.getOutputStream(), connect("forged-entry"));
                readConnAck(client);
                MqttCodec.write(client.getOutputStream(), new MqttPacket.Subscribe(23,
                        List.of(new MqttPacket.Subscription(f("$SYS/broker/version"), AT_MOST_ONCE))));
                readSubAck(client, 23, List.of(0));
                readWithin(client, WINDOW_MS);
                // the forged publish is dropped, never written — so the broker's own value answers a fresh read
                MqttCodec.write(client.getOutputStream(), new MqttPacket.Publish(f("$SYS/broker/version"),
                        "a forged entry".getBytes(StandardCharsets.UTF_8), AT_MOST_ONCE, false, false, 0));
                MqttCodec.write(client.getOutputStream(), new MqttPacket.Subscribe(24,
                        List.of(new MqttPacket.Subscription(f("$SYS/broker/version"), AT_MOST_ONCE))));
                readSubAck(client, 24, List.of(0));
                final MqttPacket still = readWithin(client, WINDOW_MS);
                assertInstanceOf(MqttPacket.Publish.class, still);
                assertEquals("mqtt 5.0 / metatron",
                        ObjJSONSerializer.simple().inputBytes(((MqttPacket.Publish) still).payload()).strValue());
            }
        }
    }

    @Test
    public void theRouteRemapsSysOntoTheServer() throws Exception {
        try (final Live live = serve()) {
            final Obj table = live.server().atDirect(uri(ROUTE));
            assertInstanceOf(Rec.class, table);
            assertTrue(table.asRec().keys().anyMatch(key -> key.isUri()
                            && "$SYS".equals(key.uriValue().path().stream().filter(s -> !s.isEmpty()).findFirst().orElse(""))),
                    "the broker's own $SYS route is in its table: " + table);
            assertTrue(live.server().sys().atDirect(uri("broker")).isRec(), "the SYS tree hangs off the broker itself");
        }
    }

    // ======================== plumbing ========================

    /**
     * A served server plus the port it listens on — closed on exit.
     */
    private static final class Live implements AutoCloseable {
        private final mqttServer server;
        private final int port;

        private Live(final mqttServer server, final int port) {
            this.server = server;
            this.port = port;
        }

        mqttServer server() {
            return this.server;
        }

        int port() {
            return this.port;
        }

        @Override
        public void close() {
            this.server.close();
        }
    }

    /**
     * Serve a fresh server on a probe-verified free port — the origin built with the fURI API, never string
     * concatenation.
     */
    private Live serve() {
        final int port = generatePort();
        final fURI origin = f("mqtt://127.0.0.1").port(port);
        return new Live((mqttServer) mqttServer.serve(origin), port);
    }

    private static Socket open(final int port) throws IOException {
        return new Socket("127.0.0.1", port);
    }

    private static MqttPacket.Connect connect(final String clientId) {
        return new MqttPacket.Connect(clientId, true, 30, null, null, AT_MOST_ONCE, false, null, null);
    }

    private static void readConnAck(final Socket client) throws IOException {
        assertInstanceOf(MqttPacket.ConnAck.class, MqttCodec.read(client.getInputStream()));
    }

    private static void readSubAck(final Socket client, final int packetId, final List<Integer> codes) throws IOException {
        final MqttPacket reply = MqttCodec.read(client.getInputStream());
        assertInstanceOf(MqttPacket.SubAck.class, reply);
        final MqttPacket.SubAck subAck = (MqttPacket.SubAck) reply;
        assertEquals(packetId, subAck.packetId());
        assertEquals(codes, subAck.returnCodes());
    }

    private static MqttPacket readWithin(final Socket client, final int windowMs) throws IOException {
        final long deadline = System.currentTimeMillis() + windowMs;
        while (true) {
            final long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0)
                throw new AssertionError("no packet within " + windowMs + " ms");
            client.setSoTimeout((int) remaining);
            try {
                return MqttCodec.read(client.getInputStream());
            } catch (final SocketTimeoutException e) {
                // keep polling — the delivery may still be on its way
            }
        }
    }

    /**
     * Read packets off the wire until enough arrive or the window closes — a $SYS burst is ack-and-leaves
     * in whatever order the dispatch thread and the reader thread choose.
     */
    private static List<MqttPacket> readBurst(final Socket client, final int count) throws IOException {
        final List<MqttPacket> burst = new ArrayList<>();
        final long deadline = System.currentTimeMillis() + WINDOW_MS;
        while (burst.size() < count) {
            final long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0)
                break;
            client.setSoTimeout((int) remaining);
            try {
                burst.add(MqttCodec.read(client.getInputStream()));
            } catch (final SocketTimeoutException e) {
                break; // the window closed; whatever arrived stands
            }
        }
        return burst;
    }

    /**
     * Poll an address until it answers, or noobj once the deadline passes.
     */
    private static Obj await(final fURI address) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + WINDOW_MS;
        while (System.currentTimeMillis() < deadline) {
            final Obj value = Machine.read(address);
            if (!value.isNoObj())
                return value;
            Thread.sleep(20);
        }
        return noobj();
    }

    private static boolean awaitsCount(final mqttServer server, final int count) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + WINDOW_MS;
        while (System.currentTimeMillis() < deadline) {
            if (server.sessionCount() == count)
                return true;
            Thread.sleep(20);
        }
        return false;
    }
}
