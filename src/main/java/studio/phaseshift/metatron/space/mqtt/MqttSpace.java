/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.space.mqtt;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5RetainHandling;
import studio.phaseshift.metatron.furi.Q;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.NoObj;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.Uri;
import studio.phaseshift.metatron.lang.mweb.JSONTranslator;
import studio.phaseshift.metatron.furi.Qs;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.space.kv.KVSpace;
import studio.phaseshift.metatron.space.MSpace;
import studio.phaseshift.metatron.furi.q.PubSubQ;
import studio.phaseshift.metatron.ui.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.MTRON_SPACE_TID;


public class MqttSpace extends MSpace<Map<Uri, Obj>> implements Space {

    private static final ObjSerializer<String> SERIALIZER = ObjStringSerializer
            .build()
            .simpleColon(true)
            .palette(Palette.NO_COLOR)
            .prettyPrint(false)
            .ignoreRewrites(true)
            .create();
    public static fURI MQTT_TID = MTRON_SPACE_TID.extend("mqtt");
    protected final fURI broker;
    protected final fURI prefix;
    final JSONTranslator jsonTranslator = new JSONTranslator(SERIALIZER);
    final Qs qs;
    private final GraphittyLogger LOG = Graphitty.log(this);
    Mqtt5Client client;
    Mqtt5BlockingClient.Mqtt5Publishes incomingMessages;
    KVSpace cache;

    public MqttSpace(final fURI pattern, final fURI vid) {
        this(Map.of(
                uri("pattern"), pattern.basePath().toUri(),
                uri("broker"), pattern.queryValue(f("broker"), fURI.class).basePath().toUri(),
                uri("prefix"), pattern.queryValue(f("prefix"), fURI.class).basePath().toUri()), vid);
    }

    public MqttSpace(final Map<Uri, Obj> config, final fURI vid) {
        super(config, config.containsKey(uri("prefix")) ?
                config.get(uri("prefix")).uriValue().extend(config
                        .get(uri("pattern"))
                        .orElseThrow(new IllegalArgumentException("config must have a pattern key")).uriValue()) :
                config.get(uri("pattern"))
                        .orElseThrow(new IllegalArgumentException("config must have a pattern key")).uriValue(), MQTT_TID, vid);
        this.prefix = config.containsKey(uri("prefix")) ? config.get(uri("prefix")).uriValue() : null;
        LOG.info("{{y}}mtron{{g}}<=>{{y}}mqtt{{X}} mapping established: {{b}}%s {{g}}<=> ({{b}}%s {{g}}<=> {{b}}%s{{g}}){{X}}", this.pattern(), this.prefix, this.toMqttTopic(this.pattern()));
        this.cache = new KVSpace(this.pattern(), this.vid.extend("cache"));
        this.cache.qs().clear();
        this.qs = new Qs(this.vid);
        this.qs.register(new PubSubQ(this) {
            @Override
            public Optional<Q.OnWrite> onWrite() {
                return Optional.of(new PubSubQ.OnWrite() {
                    @Override
                    public Optional<Obj> qlessWrite(final fURI source, final fURI vid, final Obj obj) {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<Obj> preWrite(final fURI source, final fURI vid, final Obj obj) {
                        LOG.trace("evaluating {{y}}prewrite{{/y}}: %s => %s", obj, vid);
                        if (vid.hasQuery("sub")) {
                            if (obj.isNoObj()) {
                                client.toAsync()
                                        .unsubscribeWith()
                                        .topicFilter(toMqttTopic(vid.basePath()))
                                        .send().
                                        whenComplete((m, e) -> {
                                            if (null != e)
                                                LOG.error(e);
                                            else
                                                LOG.debug("unsubscribed from %s", m);
                                        });
                            } else {
                                client.toAsync()
                                        .subscribeWith()
                                        .topicFilter(toMqttTopic(vid.basePath()))
                                        .callback(p -> {
                                            LOG.debug("received %s", p);
                                            if (p.getPayload().isPresent()) {
                                                final String json = StandardCharsets.UTF_8.decode(p.getPayload().get()).toString();
                                                final Obj o = jsonTranslator.translateString(json);
                                                cache.write(toMtronVid(p.getTopic().toString()), o);
                                                final Obj result = obj.apply(o);
                                                LOG.trace("subscription evaluation of %s => %s yielded %s", o, obj, result);
                                            } else {
                                                cache.write(toMtronVid(p.getTopic().toString()), NoObj.single());
                                                final Obj result = obj.apply();
                                                LOG.trace("subscription evaluation of %s => %s yielded %s", NoObj.single(), obj, result);
                                            }
                                        })
                                        .send()
                                        .whenComplete((m, e) -> {
                                            if (null != e)
                                                LOG.error(e);
                                            else
                                                LOG.debug("subscribed to %s", m);
                                        });
                            }
                            return super.preWrite(source, vid, obj);
                        }
                        return Optional.empty();
                    }
                });
            }
        });
        this.broker = config.get(uri("broker")).orElseThrow(new IllegalArgumentException("config must have a broker key")).uriValue();
        this.init();
    }

    public static MqttSpace of(final fURI pattern, final fURI vid) {
        return new MqttSpace(pattern, vid);
    }

    public MqttSpace clone(final Object jvm, final fURI tid, final fURI vid) {
        return this;
    }

    @Override
    public Qs qs() {
        return this.qs;
    }

    private String toMqttTopic(final fURI vid) {
        return null == this.prefix ? vid.toString() : vid.removePrefix(this.prefix).toString();
    }

    private fURI toMtronVid(final String topic) {
        return null == this.prefix ? f(topic) : this.prefix.extend(topic);
    }

    public void init() {
        try {
            this.client = MqttClient.builder()
                    .identifier(UUID.randomUUID().toString())
                    .serverHost(this.broker.host())
                    .serverPort(this.broker.port(1883))
                    .useMqttVersion5()
                    .build();
            this.client.toAsync()
                    .connectWith()
                    .cleanStart(false)
                    .send()
                    .whenComplete((a, b) -> LOG.info("connected %s", a))
                    .get();
            this.client.toAsync()
                    .subscribeWith()
                    .topicFilter(toMqttTopic(this.pattern))
                    .retainHandling(Mqtt5RetainHandling.SEND)
                    .callback(p -> {
                        try {
                            LOG.debug("received %s", p);
                            if (p.getPayload().isPresent()) {
                                final String json = StandardCharsets.UTF_8.decode(p.getPayload().get()).toString();
                                this.cache.write(
                                        toMtronVid(p.getTopic().toString()),
                                        this.jsonTranslator.translateString(json));
                            } else {
                                this.cache.write(
                                        toMtronVid(p.getTopic().toString()),
                                        NoObj.single());
                            }
                        } catch (final Exception e) {
                            LOG.error(e);
                        }
                    })
                    .send()
                    .get();
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Obj read(final fURI vid) {
        return this.cache.read(vid);
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        final Obj ret = this.qs().processPreWrite(vid, vid, obj).orElse(null);
        if (null != ret)
            return ret;
        Helper.resolveWrite(this, vid.basePath(), obj, (key, value) -> {
            this.send(vid, value);
        }, this.cache.directReader());
        return obj;
    }

    private void send(final fURI vid, final Obj obj) {
        try {
            this.client
                    .toAsync()
                    .publishWith()
                    .topic(toMqttTopic(vid))
                    .payload(obj.isNoObj() ? new byte[0] : this.jsonTranslator.translate(obj).toString().getBytes())
                    .retain(true)
                    .send()
                    .whenComplete((p, t) -> {
                        LOG.info("caching %s", p.getPublish());
                        if (p.getPublish().getPayload().isPresent()) {
                            final String json = StandardCharsets.UTF_8.decode(p.getPublish().getPayload().get()).toString();
                            this.cache.write(
                                    toMtronVid(p.getPublish().getTopic().toString()),
                                    this.jsonTranslator.translateString(json));
                        } else {
                            this.cache.write(
                                    toMtronVid(p.getPublish().getTopic().toString()),
                                    NoObj.single());
                        }
                    }).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Stream<Obj> stream() {
        return this.cache.stream();
    }

    @Override
    public void close() {
        LOG.debug("closing %s", this);
        this.client.toBlocking().disconnect();
        this.cache.close();
    }
}
