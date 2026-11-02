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

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientState;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.iot.MoquetteServer;
import studio.phaseshift.metatron.isa.m.iot.haos.space.haosSpace;
import studio.phaseshift.metatron.isa.m.iot.miot.space.miotSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.iot.haos.haosInstSet.HAOS_ISA_TID;
import static studio.phaseshift.metatron.isa.m.iot.iotInstSet.IOT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.iot.miot.miotInstSet.MIOT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A broker restart is survived. The space keeps its one client through the outage: the client redials the broker and
 * restores the space's subscription on the new session, a write into the outage fails rather than building a second
 * client, and a space closed mid-outage stops its client for good. Every case stops the suite's broker, so they live
 * apart from {@code mqttSpaceTest}, whose broker serves the whole AbstractSpaceTest run.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mqttSpaceReconnectTest extends AbstractMetatronTest {

    private static final int PORT = generatePort();
    /**
     * the longest any case waits on the broker or the client: well past the redial's first doublings (1s, 2s, 4s)
     */
    private static final long AWAIT_MS = 20_000;
    private static boolean brokerUp = false;

    private mqttSpace space;

    @BeforeAll
    public static void setupAll() {
        InstSet.importInstSet(IOT_ISA_TID);
        InstSet.importInstSet(MIOT_ISA_TID);
        InstSet.importInstSet(HAOS_ISA_TID);
        startBroker();
    }

    @AfterAll
    public static void stopAll() {
        stopBroker();
    }

    @AfterEach
    public void closeSpace() {
        if (null != this.space)
            this.space.close();
        this.space = null;
        if (!brokerUp) // a case that failed mid-outage must not leave the next one without a broker
            startBroker();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "mqtt % /t/kitchen    % 'whistling'",
            "miot % /t/kiln       % 1280",
            "haos % /t/greenhouse % 21.5"}, delimiter = '%')
    public void testBrokerRestart(final String kind, final String root, final String reading) {
        this.space = space(kind, root);
        final Mqtt5Client client = this.space.sjvm();
        final String id = clientId(client);
        // a subq on what a device reports: it fires only when the space's own subscription delivers the report
        ObjmtronSerializer.parse(root + "/probe?subq -> sub::[code=>>>1.to(" + root + "/echo)]").apply();
        checkCodeEvaluate(LOG, root + "/before -> " + reading, "*" + root + "/before", reading);

        stopBroker();
        await(() -> !client.getState().isConnected(), "the client notices the outage");
        // a write into the outage fails at once, on the outage itself, and leaves the one client in place
        final Obj outage = ObjmtronSerializer.parse(root + "/outage -> " + reading).apply();
        assertTrue(outage.stream().anyMatch(o -> o.isFail() && causes(o.asFail().jvm()).contains("unreachable")),
                "a write into the outage must fail on it: " + outage);
        assertSame(client, this.space.sjvm());

        startBroker();
        await(() -> client.getState().isConnected(), "the client redials the restarted broker");
        assertSame(client, this.space.sjvm());
        // the restarted broker remembers nothing, so only a restored subscription carries the device's report
        publish(root + "/probe", reading);
        await(() -> !Machine.read(f(root + "/echo")).isNoObj(), "the subq hears the device after the restart");
        checkCodeParseApply(LOG, "*" + root + "/probe", reading);
        checkCodeParseApply(LOG, "*" + root + "/echo", reading);
        checkCodeEvaluate(LOG, root + "/after -> " + reading, "*" + root + "/after", reading);
        // and the broker holds one connection: the space's (the device hung up, no stray client redialed)
        await(() -> List.of(id).equals(MoquetteServer.clients()), "the broker holds the space's connection alone");
    }

    @Test
    public void testCloseDuringOutage() {
        this.space = space("mqtt", "/t/attic");
        final Mqtt5Client client = this.space.sjvm();
        stopBroker();
        await(() -> MqttClientState.DISCONNECTED_RECONNECT == client.getState(), "the client schedules a redial");
        this.space.close();
        this.space = null;
        await(() -> MqttClientState.DISCONNECTED == client.getState(), "close() calls the redial off");
        startBroker();
        CommonUtil.sleepThread(3_500); // past the 1s and 2s points a live redial would have dialed at
        assertEquals(MqttClientState.DISCONNECTED, client.getState(), "a closed space's client never redials");
        assertEquals(List.of(), MoquetteServer.clients(), "no connection outlives its space");
    }

    @Test
    public void testFirstDial() {
        assumeFalse(listening(MqttClient.DEFAULT_SERVER_PORT), "a broker answers on 1883 here, so a refusal can not be provoked");
        final long start = System.currentTimeMillis();
        // a host without a port dials MQTT's registered port, and a first dial that fails fails the space at once:
        // there is no connection yet to redial, so the dial is not retried into the connect timeout
        final MTronException refused = assertThrows(MTronException.class, () -> mqttSpace.of(rec(
                uri(HOST), uri("mqtt://127.0.0.1"),
                uri(PATTERN), uri("/t/cellar/#")), f("/sys/space/t/cellar")));
        assertTrue(causes(refused).contains("127.0.0.1:1883"), "the dial must go to 1883: " + causes(refused));
        assertTrue(System.currentTimeMillis() - start < 5_000, "a refused first dial must fail at once");
    }

    /// /////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static mqttSpace space(final String kind, final String root) {
        final Rec config = rec(
                uri(QPROC), lst(QCollection.subQ()),
                uri(HOST), uri("mqtt://127.0.0.1:" + PORT),
                uri(PATTERN), uri(root + "/#"),
                uri(SERIALIZER), ObjmtronSerializer.single());
        final fURI vid = f("/sys/space" + root);
        return switch (kind) {
            case "miot" -> miotSpace.of(config, vid);
            case "haos" -> haosSpace.of(config, vid);
            default -> mqttSpace.of(config, vid);
        };
    }

    private static void startBroker() {
        MoquetteServer.run(PORT);
        brokerUp = true;
    }

    private static void stopBroker() {
        MoquetteServer.stop();
        brokerUp = false;
    }

    /**
     * A device on the broker other than the space, reporting a retained reading: it reaches the space only through
     * the space's subscription, and retained, it is delivered even if it lands before the restored subscription does.
     */
    private static void publish(final String topic, final String mtron) {
        final Mqtt5BlockingClient device = MqttClient.builder()
                .identifier("greenhouse-probe")
                .serverHost("127.0.0.1")
                .serverPort(PORT)
                .useMqttVersion5()
                .buildBlocking();
        device.connect();
        try {
            device.publishWith().topic(topic).payload(mtron.getBytes(StandardCharsets.UTF_8)).retain(true).send();
        } finally {
            device.disconnect();
        }
    }

    private static String clientId(final Mqtt5Client client) {
        return client.getConfig().getClientIdentifier().map(Object::toString).orElseThrow();
    }

    private static void await(final BooleanSupplier condition, final String what) {
        final long deadline = System.currentTimeMillis() + AWAIT_MS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline)
                fail("timed out waiting until " + what);
            CommonUtil.sleepThread(25);
        }
    }

    private static boolean listening(final int port) {
        try (final Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
            return true;
        } catch (final IOException e) {
            return false;
        }
    }

    private static String causes(final Throwable throwable) {
        final StringBuilder chain = new StringBuilder();
        for (Throwable cause = throwable; null != cause; cause = cause.getCause())
            chain.append(cause).append(" <= ");
        return chain.toString();
    }
}
