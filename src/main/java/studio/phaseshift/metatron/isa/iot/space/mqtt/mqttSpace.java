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

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedContext;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedContext;
import com.hivemq.client.mqtt.lifecycle.MqttClientReconnector;
import com.hivemq.client.mqtt.lifecycle.MqttDisconnectSource;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5RetainHandling;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.ui.console.StatusLine;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.iot.iotInstSet.IOT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;


public class mqttSpace extends AbstractSpace<Mqtt5Client> {

    public static fURI MQTT_SPACE_TID = IOT_ISA_TID.extend("space").extend("mqttspace");

    private static final String NATIVE_CONNACK = "native/connack";
    /**
     * the longest a connect, subscribe or publish may take before it fails
     */
    private static final long TIMEOUT_S = 10;
    /**
     * a lost broker is redialed after REDIAL_MIN_MS, the wait doubling per failed attempt up to REDIAL_MAX_MS
     */
    private static final long REDIAL_MIN_MS = 1_000;
    private static final long REDIAL_MAX_MS = 10_000;
    public static final Type MQTT_SPACE_TYPE =
            Type.Builder.build()
                    .tid(SPACE_TID)
                    .vid(MQTT_SPACE_TID)
                    .isaPredicate(rec(uri(HOST), URI_TYPE))
                    .constructor(arg -> mqttSpace.of(arg.asRec().apply().asRec(), arg.vid()))
                    .create();

    protected final fURI broker;
    protected final memSpace cache;
    /**
     * set by the first CONNACK: a dial that never connects fails the constructor, a connection lost after it is redialed
     */
    private final AtomicBoolean established = new AtomicBoolean(false);
    /**
     * set by close(): the client is hung up and never redialed
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);
    /**
     * the wait before the next redial, which close() cuts short
     */
    private final AtomicReference<CompletableFuture<Void>> redial = new AtomicReference<>(CompletableFuture.completedFuture(null));

    /**
     * The space's one client. It is never replaced: its listeners redial a lost broker, and the client restores the
     * space's subscription on the new session itself (see {@link #onDisconnected}).
     */
    private Mqtt5Client createClient(final Map<Obj, Obj> config) {
        return MqttClient.builder()
                .identifier(config.getOrDefault(uri(CLIENT), uri("mtron-" + CommonUtil.mintShortUUID(f(""), true))).uriValue().toString())
                .serverHost(this.broker.host())
                .serverPort(this.broker.portOrDefault(MqttClient.DEFAULT_SERVER_PORT))
                .useMqttVersion5()
                .addConnectedListener(this::onConnected)
                .addDisconnectedListener(this::onDisconnected)
                .build();
    }

    private void onConnected(final MqttClientConnectedContext context) {
        if (this.closed.get())
            this.sjvm.toAsync().disconnect(); // a dial that landed after close()
        else if (this.established.getAndSet(true))
            LOG.info("{{g}}reconnected{{X}} to mqtt broker %s", this.broker);
    }

    /**
     * Redial a lost broker until close(), the wait doubling from REDIAL_MIN_MS to REDIAL_MAX_MS. The client keeps the
     * subscription's callback through the outage and resubscribes when the new session starts without it
     * (resubscribeIfSessionExpired), so even a broker that restarted with no memory of this client is rejoined with the
     * space's topic filter intact — and the retained replay that follows resyncs the cache with what changed meanwhile.
     * A persistent broker session would do neither: it is lost by a broker that does not persist, and when it does
     * survive, the retained (qos 0) writes made during the outage are never replayed into it.
     */
    private void onDisconnected(final MqttClientDisconnectedContext context) {
        if (!this.established.get() || this.closed.get() || MqttDisconnectSource.USER == context.getSource())
            return; // the reconnector's default: do not reconnect
        final MqttClientReconnector reconnector = context.getReconnector();
        final long delay = Math.min(REDIAL_MAX_MS, REDIAL_MIN_MS << Math.min(reconnector.getAttempts(), 10));
        if (0 == reconnector.getAttempts())
            LOG.warn("mqtt broker %s lost (%s): redialing", this.broker, context.getCause().getMessage());
        else
            LOG.debug("mqtt broker %s unreachable (%s): redialing in %sms", this.broker, context.getCause().getMessage(), delay);
        final CompletableFuture<Void> wait = new CompletableFuture<Void>().completeOnTimeout(null, delay, TimeUnit.MILLISECONDS);
        this.redial.set(wait);
        if (this.closed.get()) // close() ran after the check above and may have missed this wait
            wait.complete(null);
        reconnector.resubscribeIfSessionExpired(true)
                .reconnectWhen(wait, (ignored, error) -> {
                    if (this.closed.get())
                        reconnector.reconnect(false);
                });
    }

    /**
     * Stop the client for good: cut a pending redial short and disconnect a live connection. The disconnect is not
     * waited on, so a hang-up never stalls the thread closing the space.
     */
    private void hangUp() {
        this.closed.set(true);
        this.redial.get().complete(null);
        if (null != this.sjvm && this.sjvm.getState().isConnected())
            this.sjvm.toAsync().disconnect();
    }

    @Override
    public Space addQ(final QProc q) {
        if (q.tid().basePath().equals(QCollection.SUBQ_TID) && !(q instanceof mqttSpaceSubQ)) {
            this.at(uri(QPROC)).asLst().add(new mqttSpaceSubQ(this), MUTABLE);
            return this;
        }
        return super.addQ(q);
    }

    protected mqttSpace(final Map<Obj, Obj> config, final fURI tid, final fURI vid) {
        super(null, config, null == tid ? MQTT_SPACE_TID : tid, vid);
        LOG.info("{{y}}mtron{{g}}<=>{{y}}mqtt{{X}} route established: %s {{g}}<=> ({{b}}%s {{g}}<=>{{X}} %s{{g}}){{X}}", this.pattern().toUri(), config.getOrDefault(uri(ROUTE), rec()), uri(this.redirect(this.pattern(), false)));
        // An internal mirror, reached only through this field — it must NOT self-register into the
        // machine's space index. Registered, it carries the same pattern as the space itself, so
        // Memory.findSpace can resolve /t/... addresses to this empty cache instead of the space:
        // the mqttSpace.write() override is then bypassed and any qproc write fails checkQProcs
        // ("no subq query processor attached to memspace [/t/#]").
        this.cache = memSpace.unregistered(this.pattern(), null);
        if (this.at(SERIALIZER).isNoObj())
            this.at(uri(SERIALIZER), ObjJSONSerializer.simple(), MUTABLE);
        LOG.info("%s serializer loaded: %s", this.tid(), this.at(SERIALIZER));
        this.broker = this.at(uri(HOST)).orThrow(new IllegalArgumentException("config must have a host key")).uriValue();
        // assigned before the dial: a dial that lands after a failed constructor is hung up through it (onConnected)
        this.sjvm = this.createClient(config);
        try {
            final Mqtt5ConnAck connAck = this.sjvm.toAsync()
                    .connectWith()
                    .cleanStart(false)
                    .send()
                    .get(TIMEOUT_S, TimeUnit.SECONDS);
            final Rec conn = MObjFactory.of().toObj(connAck).asRec();
            LOG.debug("{{g}}connected{{X}} %s", conn);
            this.at(uri(NATIVE_CONNACK), conn, MUTABLE);
            this.sjvm.toAsync()
                    .subscribeWith()
                    .topicFilter(this.redirect(this.pattern, true).toString())
                    .retainHandling(Mqtt5RetainHandling.SEND)
                    .callback(p -> {
                        if (this.closed.get())
                            return; // a dial that landed after close(), its hang-up in flight
                        final fURI topic = this.redirect(f(p.getTopic().toString()), false);
                        try {
                            LOG.debug("received %s => %s", p.getTopic(), topic);
                            StatusLine.message(str("%s => %s".formatted(topic, new String(p.getPayloadAsBytes()))));
                            Machine.current().memory().stats().ioStats().incrBytesRecv(p.getPayload().isPresent() ? p.getPayloadAsBytes().length : 0);
                            final Obj obj;
                            if (p.getPayload().isPresent()) {
                                final String json = StandardCharsets.UTF_8.decode(p.getPayload().get()).toString();
                                obj = this.at(SERIALIZER).<ObjSerializer<?>>as().inputBytes(ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)));
                            } else {
                                obj = noobj();
                            }
                            this.cache.write(topic, obj);
                            // Fire SubQ and other QProc callbacks for external writes.
                            // Use routeToSpace to convert the MQTT topic back to the internal
                            // URI format (e.g. z2m/office/knob → z2m:office/knob) so it matches
                            // the subscription TARGET stored by preWrite.
                            QProc.Helper.processQlessWrite(this.qs(), topic, obj);
                        } catch (final Exception e) {
                            LOG.error("unable to process %s: %s", topic, e);
                        }
                    })
                    .send()
                    .whenComplete((a, b) -> {
                        if (null != b)
                            LOG.error(b);
                        else
                            LOG.info("synchronized with mqtt topic: %s", uri(this.redirect(this.pattern, false)));
                    })
                    .get(TIMEOUT_S, TimeUnit.SECONDS);
        } catch (final Exception e) {
            this.hangUp();
            throw MTronException.of(e);
        }
    }

    public static mqttSpace of(final Rec config, final fURI vid) {
        // the constructor dials the broker after the space has mounted: a broker that is down must not leave it there
        return Space.Helper.construct(() -> new mqttSpace(config.jvm(), MQTT_SPACE_TID, vid));
    }

    @Override
    public Obj read(final fURI pattern) {
        return QProc.Helper.processPreRead(this.qs(), pattern).orElseGet(() -> {
            final Obj result = this.cache.read(pattern.one());
            return QProc.Helper.processPostRead(this.qs(), pattern, result);
        });
    }

    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        // The reference-graph walk (>>) and wildcard reads go through directReader, not
        // read(); without this override they hit the empty Space default and yield nothing.
        return this.cache.directReader();
    }

    @Override
    public Obj write(final fURI pattern, final Obj obj) {
        final Obj ret = QProc.Helper.processPreWrite(this.qs(), pattern, obj).orElse(null);
        if (null != ret)
            return ret;
        if (pattern.hasPattern()) {
            this.read(pattern.asBranch()).stream().map(r -> r.<Rel>as().first()).forEach(u -> this.write(u.uriValue(), obj));
        } else
            this.send(pattern, obj);
       /* final Obj result = Space.Helper.resolveWrite(this, vid.basePath(), obj, (key, value) -> {
            this.send(vid.qLess(), value.c(cInt.ONE()));
            return value;
        }, this.cache.directReader());*/
        // Fire SubQ callbacks for internal writes.  MQTT clients do NOT receive
        // their own publishes, so the constructor-level MQTT callback only fires for
        // external messages.  We use the MQTT-format topic here so the URI matches
        // the subscription TARGET (which preWrite normalizes to MQTT format).
        final fURI mqttPattern = this.redirect(pattern, true);
        return QProc.Helper.processPostWrite(this.qs(), pattern, obj)
                .orElse(QProc.Helper.processQlessWrite(this.qs(), mqttPattern, obj.c(cInt.ONE())).orElse(obj));
    }

    private void send(final fURI pattern, final Obj obj) {
        try {
            final byte[] payload = obj.isNoObj() ? new byte[0] : this.at(SERIALIZER).<ObjSerializer<?>>as().outputBytes(obj).array();
            if (pattern.hasQ(Tokens.SUB))
                return;
            // the client redials a lost broker by itself (onDisconnected), and a publish made meanwhile would queue
            // behind that redial with the writer waiting out the outage — so the write fails now instead
            if (!this.sjvm.getState().isConnected())
                throw MTronException.of("mqtt broker %s unreachable: client %s", this.broker, this.sjvm.getState().name().toLowerCase());
            this.sjvm
                    .toAsync()
                    .publishWith()
                    .topic(this.redirect(pattern, true).toString())
                    .payload(payload)
                    .retain(true)
                    .send()
                    .get(TIMEOUT_S, TimeUnit.SECONDS);
            Machine.current().memory().stats().ioStats().incrBytesSent(payload.length);
            // Write directly to cache so local readers see the data immediately,
            // without waiting for the MQTT subscriber callback to fire on a Netty thread.
            this.cache.write(pattern, obj);
        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public void close() {
        try {
            // first, so no message lands in the cache as it closes
            this.hangUp();
            if (null != this.cache)
                this.cache.close();
            this.at(uri(NATIVE_CONNACK), noobj());
        } catch (final Exception e) {
            throw MTronException.of(e);
        } finally {
            super.close();
        }

    }
}