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

package studio.phaseshift.metatron.lang.net.iot;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5RetainHandling;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.Q;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.MSpace;
import studio.phaseshift.metatron.lang.Space;
import studio.phaseshift.metatron.lang.core.m.inst.mInstSet;
import studio.phaseshift.metatron.lang.core.m.obj.NoObj;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Rel;
import studio.phaseshift.metatron.lang.core.m.type.Type;
import studio.phaseshift.metatron.lang.db.kv.kvSpace;
import studio.phaseshift.metatron.lang.net.web.JSONTranslator;
import studio.phaseshift.metatron.lang.sys.router.Router;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.*;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.net.iot.iotInstSet.IOT_INSTSET_TID;
import static studio.phaseshift.metatron.util.Common.mutableMap;


public class mqttSpace extends MSpace<Mqtt5Client> {

    public static fURI MQTT_TID = IOT_INSTSET_TID.extend("space").extend("mqtt");
    public static final Type MQTT_TYPE = T(MQTT_TID, null,
            instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(MQTT_TID),
                    lst(T(REC_TID, isa_(rec(
                            uri(PATTERN), T(URI_TID),
                            uri(Tokens.HOST), T(URI_TID),
                            uri(Tokens.PREFIX), T(URI_TID),
                            uri(Tokens.Q).c(cInt::maybe), isa_(T(LST_TID)))))), (lhs, inst) -> {
                        final fURI pattern = inst.arg(0).<Rec>as().at(PATTERN).uriValue();
                        final fURI host = inst.arg(0).<Rec>as().at(Tokens.HOST).uriValue();
                        final fURI prefix = inst.arg(0).<Rec>as().at(Tokens.PREFIX).uriValue();
                        // final Rec route = inst.arg(0).<Rec>as().at(ROUTE);
                        final mqttSpace space = mqttSpace.of(mutableMap(uri(PATTERN), uri(pattern), uri(Tokens.HOST), uri(host), uri(Tokens.PREFIX), uri(prefix)), inst.arg(0).vid());
                        Router.global().addSpace(space);
                        return space;
                    }));
    protected final fURI broker;
    protected final fURI prefix;
    protected final JSONTranslator jsonTranslator = new JSONTranslator();
    protected final Mqtt5Client client;
    protected final kvSpace cache;

    public mqttSpace(final Mqtt5Client client, final Map<Obj, Obj> config, final fURI vid) {
        super(client, config, config.get(uri(PATTERN)).uriValue(), MQTT_TID, vid);
        this.prefix = config.containsKey(uri(Tokens.PREFIX)) ? config.get(uri(Tokens.PREFIX)).uriValue() : null;
        LOG.info("{{y}}mtron{{g}}<=>{{y}}mqtt{{X}} mapping established: %s {{g}}<=> ({{b}}%s {{g}}<=>{{X}} %s{{g}}){{X}}", this.pattern().toUri(), this.prefix, uri(Space.Helper.toNativeSpace(this.pattern(), this.prefix)));
        this.cache = new kvSpace(this.pattern(), this.vid.extend("cache"));
        this.put(uri(Tokens.Q), lst(List.of(new MqttPubSubQ(this))), IMMUTABLE);
        this.broker = config.get(uri(Tokens.HOST)).orThrow(new IllegalArgumentException("config must have a host key")).uriValue();
        try {
            this.client = MqttClient.builder()
                    .identifier(UUID.randomUUID().toString())
                    .serverHost(this.broker.host())
                    .serverPort(this.broker.port() == -1 ? 1833 : this.broker.port())
                    .useMqttVersion5()
                    .build();
            this.client.toAsync()
                    .connectWith()
                    .cleanStart(false)
                    .send()
                    .whenComplete((a, b) -> LOG.info("{{g}}connected{{X}} %s", a))
                    .get();
            this.client.toAsync()
                    .subscribeWith()
                    .topicFilter(Space.Helper.toNativeSpace(this.pattern, this.prefix))
                    .retainHandling(Mqtt5RetainHandling.SEND)
                    .callback(p -> {
                        try {
                            LOG.debug("received %s", p);
                            Router.global().server().stats().incrTotalBytesRecv(p.getPayload().isPresent() ? p.getPayloadAsBytes().length : 0);
                            if (p.getPayload().isPresent()) {
                                final String json = StandardCharsets.UTF_8.decode(p.getPayload().get()).toString();
                                this.cache.write(
                                        Space.Helper.fromNativeSpace(p.getTopic().toString(), this.prefix),
                                        this.jsonTranslator.translateString(json));
                            } else {
                                this.cache.write(
                                        Space.Helper.fromNativeSpace(p.getTopic().toString(), this.prefix),
                                        NoObj.noobj());
                            }
                        } catch (final Exception e) {
                            LOG.error(e);
                            e.printStackTrace();
                        }
                    })
                    .send()
                    .whenComplete((a, b) -> {
                        if (null != b)
                            LOG.error(b);
                        else
                            LOG.info("synchronized with mqtt topic: %s", uri(Space.Helper.toNativeSpace(this.pattern, this.prefix)));
                    })
                    .get();
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static mqttSpace of(final Map<Obj, Obj> config, final fURI vid) {
        final Mqtt5Client client = MqttClient.builder()
                .identifier(UUID.randomUUID().toString())
                .serverHost(config.get(uri(Tokens.HOST)).uriValue().host())
                .serverPort(config.get(uri(Tokens.HOST)).uriValue().port())
                .useMqttVersion5()
                .build();
        return new mqttSpace(client, config, vid);
    }

    @Override
    public Obj read(final fURI vid) {
        final Obj ret = Q.Helper.processPreRead(this.qs(), vid, vid).orElse(null);
        if (null != ret)
            return ret;
        final Obj result = this.cache.read(vid.qLess());
        return Q.Helper.processPostRead(this.qs(), vid, vid, result).orElse(result);
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        final Obj ret = Q.Helper.processPreWrite(this.qs(), vid, vid, obj).orElse(null);
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
        return Q.Helper.processPostWrite(this.qs(), vid, vid, obj).orElse(Q.Helper.processQlessWrite(this.qs(), vid, vid, obj.c(cInt.ONE())).orElse(obj));
    }

    private void send(final fURI vid, final Obj obj) {
        try {
            final byte[] payload = obj.isNoObj() ? new byte[0] : this.jsonTranslator.translate(obj).toString().getBytes();
            if (vid.hasQuery(Tokens.SUB))
                return;
            this.client
                    .toAsync()
                    .publishWith()
                    .topic(Space.Helper.toNativeSpace(vid, this.prefix))
                    .payload(payload)
                    .retain(true)
                    .send()
                    .whenComplete((p, t) -> {
                        Router.global().server().stats().incrTotalBytesSent(payload.length);
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
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public void close() {
        this.client.toBlocking().disconnect();
        this.cache.close();
        super.close();
    }
}