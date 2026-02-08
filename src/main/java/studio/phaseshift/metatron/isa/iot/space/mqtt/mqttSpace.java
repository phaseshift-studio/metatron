/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

import com.hivemq.client.internal.mqtt.message.connect.connack.MqttConnAck;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5RetainHandling;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.sys.type.Router;
import studio.phaseshift.metatron.isa.web.parser.JSONTranslator;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.isa.iot.iotInstSet.IOT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Rel.REL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;


public class mqttSpace extends AbstractSpace<Mqtt5Client> {

    public static fURI MQTT_SPACE_TID = IOT_ISA_TID.extend("space").extend("mqtt");
    public static final Type MQTT_SPACE_TYPE = Type.Builder.build().tid(SPACE_TID).vid(MQTT_SPACE_TID).constructor(
            instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(MQTT_SPACE_TID),
                    lst(T(REC_TID, isa_(rec(
                            uri(PATTERN), URI_TYPE,
                            uri(HOST), URI_TYPE,
                            //uri(CLIENT).maybe(), T(URI_TID).maybe(),
                            uri(REWRITE), REL_TYPE,
                            uri(Tokens.Q).c(cInt::maybe), isa_(LST_TYPE))))), (lhs, inst) -> mqttSpace.of(inst.arg(0).asRec(), inst.arg(0).vid()))).create();
    protected final fURI broker;
    protected final Tuple.Pair<String, String> rewrite;
    protected final JSONTranslator jsonTranslator = new JSONTranslator();
    protected final memSpace cache;

    protected mqttSpace(final Mqtt5Client client, final Map<Obj, Obj> config, final fURI tid, final fURI vid) {
        super(client, config, null == tid ? MQTT_SPACE_TID : tid, vid);
        MqttConnAck connAck = null;
        this.rewrite = Space.Helper.extractRewrite(config);
        LOG.info("{{y}}mtron{{g}}<=>{{y}}mqtt{{X}} mapping established: %s {{g}}<=> ({{b}}%s {{g}}<=>{{X}} %s{{g}}){{X}}", this.pattern().toUri(), this.rewrite, uri(Space.Helper.toNativeSpace(this.pattern(), this.rewrite)));
        this.cache = memSpace.of(this.pattern(), fURI.fnull);
        this.put(uri(Tokens.Q), lst(List.of(new MqttPubSubQ(this))), MUTABLE);
        this.broker = this.at(uri(HOST)).orThrow(new IllegalArgumentException("config must have a host key")).uriValue();
        try {
            this.sjvm = MqttClient.builder()
                    .identifier(config.getOrDefault(uri(CLIENT), uri("mtron-" + Math.abs(UUID.randomUUID().getMostSignificantBits()))).uriValue().toString())
                    .serverHost(this.broker.host())
                    .serverPort(this.broker.port() == -1 ? 1833 : this.broker.port())
                    .useMqttVersion5()
                    .build();
            this.sjvm.toAsync()
                    .connectWith()
                    .cleanStart(false)
                    .send()
                    .whenComplete((a, b) -> {
                        if (b != null) {
                            throw MTronException.of(b);
                        } else {
                            final Rec conn = MObjFactory.of().toObj(a).asRec();
                            LOG.debug("{{g}}connected{{X}} %s", conn);
                            this.put(uri("native/connack"), conn, MUTABLE);
                        }
                    })
                    .get(10, TimeUnit.SECONDS);
            this.sjvm.toAsync()
                    .subscribeWith()
                    .topicFilter(Space.Helper.toNativeSpace(this.pattern, this.rewrite))
                    .retainHandling(Mqtt5RetainHandling.SEND)
                    .callback(p -> {
                        try {
                            LOG.debug("received %s", p);
                            Router.global().stats().incrBytesRecv(p.getPayload().isPresent() ? p.getPayloadAsBytes().length : 0);
                            if (p.getPayload().isPresent()) {
                                final String json = StandardCharsets.UTF_8.decode(p.getPayload().get()).toString();
                                this.cache.write(
                                        Space.Helper.fromNativeSpace(p.getTopic().toString(), this.rewrite),
                                        this.jsonTranslator.parse(json));
                            } else {
                                this.cache.write(
                                        Space.Helper.fromNativeSpace(p.getTopic().toString(), this.rewrite),
                                        NoObj.noobj());
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
                            LOG.info("synchronized with mqtt topic: %s", uri(Space.Helper.toNativeSpace(this.pattern, this.rewrite)));
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
    public Obj read(final fURI vid) {
        return studio.phaseshift.metatron.furi.Q.Helper.processPreRead(this.qs(), vid, vid).orElseGet(() -> {
            final Obj result = this.cache.read(vid.qLess());
            return studio.phaseshift.metatron.furi.Q.Helper.processPostRead(this.qs(), vid, vid, result).orElse(result);
        });
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        final Obj ret = studio.phaseshift.metatron.furi.Q.Helper.processPreWrite(this.qs(), vid, vid, obj).orElse(null);
        if (null != ret)
            return ret;
        if (vid.hasPattern()) {
            this.read(vid.asBranch()).stream().map(r -> r.<Rel>as().first()).forEach(u -> this.write(u.uriValue(), obj));
        } else
            this.send(vid, obj);
       /* final Obj result = Space.Helper.resolveWrite(this, vid.basePath(), obj, (key, value) -> {
            this.send(vid.qLess(), value.c(cInt.ONE()));
            return value;
        }, this.cache.directReader());*/
        return studio.phaseshift.metatron.furi.Q.Helper.processPostWrite(this.qs(), vid, vid, obj)
                .orElse(studio.phaseshift.metatron.furi.Q.Helper.processQlessWrite(this.qs(), vid, vid, obj.c(cInt.ONE())).orElse(obj));
    }

    private void send(final fURI vid, final Obj obj) {
        try {
            final byte[] payload = obj.isNoObj() ? new byte[0] : this.jsonTranslator.translate(obj).toString().getBytes();
            if (vid.hasQuery(Tokens.SUB))
                return;
            this.sjvm
                    .toAsync()
                    .publishWith()
                    .topic(Space.Helper.toNativeSpace(vid, this.rewrite))
                    .payload(payload)
                    .retain(true)
                    .send()
                    .whenComplete((p, t) -> {
                        Router.global().stats().incrBytesSent(payload.length);
                        /*Router.global().server().stats().incrTotalBytesRecv(p.getPublish().getPayload().isPresent() ? p.getPublish().getPayloadAsBytes().length : 0);
                        LOG.trace("caching %s[%s]", p.getPublish(), new String(p.getPublish().getPayloadAsBytes()));
                        if (p.getPublish().getPayload().isPresent()) {
                            final String json = StandardCharsets.UTF_8.decode(p.getPublish().getPayload().get()).toString();
                            this.cache.write(
                                    toMtronVid(p.getPublish().getTopic().toString()),
                                    this.jsonTranslator.translateString(json));
                        } else {
                            this.cache.write(
                                    toMtronVid(p.getPublish().getTopic().toString()),
                                    NoObj.noobj());
                        }*/
                    }).get();
        } catch (final InterruptedException | ExecutionException e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public void close() {
        try {
            this.cache.close();
            if (this.sjvm != null)
                this.sjvm.toAsync().disconnect();
        } catch (final Exception e) {
            throw MTronException.of(e);
        } finally {
            super.close();
        }

    }
}