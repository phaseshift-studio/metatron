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
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5RetainHandling;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.iot.iotInstSet.IOT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SPACE_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.io.ioInstSet.OBJ_SERIAL_TYPE;


public class mqttSpace extends AbstractSpace<Mqtt5Client> {

    public static fURI MQTT_SPACE_TID = IOT_ISA_TID.extend("space").extend("mqtt");

    private static final String NATIVE_CONNACK = "native/connack";
    public static final Rec MQTT_SPACE_CONFIG = rec(
            uri(PATTERN), URI_TYPE,
            uri(HOST), URI_TYPE,
            uri(SERIALIZER).maybe(), OBJ_SERIAL_TYPE,
            //uri(CLIENT).maybe(), T(URI_TID).maybe(),
            uri(ROUTE), REC_TYPE,
            uri(Tokens.QPROC).c(cInt::maybe), isa_(LST_TYPE));
    public static final Type MQTT_SPACE_TYPE = Type.Builder.build().tid(SPACE_TID).vid(MQTT_SPACE_TID).constructor(
            instC(mInstSet.M_ISA_INST_TID.dom(ALL.maybe()).rng(MQTT_SPACE_TID),
                    lst(T(REC_TID, isa_(MQTT_SPACE_CONFIG))), (lhs, inst) ->
                            mqttSpace.of(Poly.Helper.applyObjRecursion(inst.arg(0).asRec(), MQTT_SPACE_CONFIG).asRec(), inst.arg(0).vid()))).create();

    protected final fURI broker;
    protected final memSpace cache;

    protected Mqtt5Client createConnection(final Map<Obj, Obj> config, final boolean cleanStart) {
        try {
            final Mqtt5Client client = MqttClient.builder()
                    .identifier(config.getOrDefault(uri(CLIENT), uri("mtron-" + CommonUtil.mintShortUUID(f(""), true))).uriValue().toString())
                    .serverHost(this.broker.host())
                    .serverPort(this.broker.port() == -1 ? 1833 : this.broker.port())
                    .useMqttVersion5()
                    .build();
            client.toAsync()
                    .connectWith()
                    .cleanStart(cleanStart)
                    .send()
                    .whenComplete((a, b) -> {
                        if (b != null) {
                            throw MTronException.of(b);
                        } else {
                            final Rec conn = MObjFactory.of().toObj(a).asRec();
                            LOG.debug("{{g}}connected{{X}} %s", conn);
                            this.at(uri(NATIVE_CONNACK), conn, MUTABLE);
                        }
                    })
                    .get(10, TimeUnit.SECONDS);
            return client;
        } catch (final Exception e) {
            throw MTronException.of(e);
        }

    }

    @Override
    public Space addQ(final QProc q) {
        if (q.tid().basePath().equals(QCollection.SUBQ_TID) && !(q instanceof MqttPubSubQ)) {
            this.at(uri(QPROC)).asLst().add(new MqttPubSubQ(this), MUTABLE);
            return this;
        }
        return super.addQ(q);
    }

    protected mqttSpace(final Mqtt5Client client, final Map<Obj, Obj> config, final fURI tid, final fURI vid) {
        super(client, config, null == tid ? MQTT_SPACE_TID : tid, vid);
        LOG.info("{{y}}mtron{{g}}<=>{{y}}mqtt{{X}} route established: %s {{g}}<=> ({{b}}%s {{g}}<=>{{X}} %s{{g}}){{X}}", this.pattern().toUri(), config.getOrDefault(uri(ROUTE), rec()), uri(this.redirect(this.pattern(), false)));
        this.cache = memSpace.of(this.pattern(), null);
        if(this.at(SERIALIZER).isNoObj())
            this.at(uri(SERIALIZER),new ObjSimpleJSONSerializer(), MUTABLE);
        LOG.info("%s serializer loaded: %s", this.tid(), this.at(SERIALIZER));
        this.broker = this.at(uri(HOST)).orThrow(new IllegalArgumentException("config must have a host key")).uriValue();
        try {
            this.sjvm = this.createConnection(config, false);
            this.sjvm.toAsync()
                    .subscribeWith()
                    .topicFilter(this.redirect(this.pattern, true).toString())
                    .retainHandling(Mqtt5RetainHandling.SEND)
                    .callback(p -> {
                        try {
                            LOG.debug("received %s", p);
                            Router.global().stats().ioStats().incrBytesRecv(p.getPayload().isPresent() ? p.getPayloadAsBytes().length : 0);
                            if (p.getPayload().isPresent()) {
                                final String json = StandardCharsets.UTF_8.decode(p.getPayload().get()).toString();
                                this.cache.write(
                                        this.redirect(f(p.getTopic().toString()), false),
                                        this.at(SERIALIZER).<ObjSerializer<?>>as().inputBytes(ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8))));
                            } else {
                                this.cache.write(
                                        this.redirect(f(p.getTopic().toString()), false),
                                        noobj());
                            }
                        } catch (final Exception e) {
                            LOG.error(e);
                            // e.printStackTrace();
                        }
                    })
                    .send()
                    .whenComplete((a, b) -> {
                        if (null != b)
                            LOG.error(b);
                        else
                            LOG.info("synchronized with mqtt topic: %s", uri(this.redirect(this.pattern, false)));
                    })
                    .get(10, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    public static mqttSpace of(final Rec config, final fURI vid) {
        final Mqtt5Client client = MqttClient.builder()
                .identifier(config.at(uri(CLIENT).orElse(uri("mtron-" + Math.abs(UUID.randomUUID().getMostSignificantBits())))).uriValue().toString())
                .serverHost(config.at(HOST).uriValue().host())
                .serverPort(config.at(HOST).uriValue().port())
                .useMqttVersion5()
                .build();
        return new mqttSpace(client, config.jvm(), MQTT_SPACE_TID, vid);
    }

    @Override
    public Obj read(final fURI pattern) {
        return QProc.Helper.processPreRead(this.qs(), pattern).orElseGet(() -> {
            final Obj result = this.cache.read(pattern.one());
            return QProc.Helper.processPostRead(this.qs(), pattern, result).orElse(result);
        });
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
        return QProc.Helper.processPostWrite(this.qs(), pattern, obj)
                .orElse(QProc.Helper.processQlessWrite(this.qs(), pattern, obj.c(cInt.ONE())).orElse(obj));
    }

    private void send(final fURI pattern, final Obj obj) {
        try {
            final byte[] payload = obj.isNoObj() ? new byte[0] : this.at(SERIALIZER).<ObjSerializer<?>>as().outputBytes(obj).array();
            if (pattern.hasQ(Tokens.SUB))
                return;
            this.sjvm
                    .toAsync()
                    .publishWith()
                    .topic(this.redirect(pattern, true).toString())
                    .payload(payload)
                    .retain(true)
                    .send()
                    .whenComplete((p, t) -> {
                        if (null != t) {
                            LOG.error("mqtt client error (reconnecting)", t);
                            this.sjvm = this.createConnection(this.jvm(), false);
                        } else
                            Router.global().stats().ioStats().incrBytesSent(payload.length);
                    }).get();
            // Write directly to cache so local readers see the data immediately,
            // without waiting for the MQTT subscriber callback to fire on a Netty thread.
            this.cache.write(pattern, obj);
        } catch (final InterruptedException | ExecutionException e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public void close() {
        try {
            if (null != this.cache)
                this.cache.close();
            this.at(uri(NATIVE_CONNACK), noobj());
            if (this.sjvm != null) {
                this.sjvm.toAsync().disconnect();
            }
        } catch (final Exception e) {
            throw MTronException.of(e);
        } finally {
            super.close();
        }

    }
}