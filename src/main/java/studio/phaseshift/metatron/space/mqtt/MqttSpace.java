/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.space.mqtt;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5RetainHandling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Uri;
import studio.phaseshift.metatron.lang.obj.mtron.MObj;
import studio.phaseshift.metatron.lang.obj.mtron.MUri;
import studio.phaseshift.metatron.ui.Palette;
import studio.phaseshift.metatron.lang.translate.JSONTranslator;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.space.mem.MemSpace;
import studio.phaseshift.metatron.ui.ObjSerializer;
import studio.phaseshift.metatron.ui.ObjStringSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;


public class MqttSpace extends MObj implements Space {

    public static fURI MQTT_TID = fURI.of("mqtt/broker");
    private static final Logger LOG = LoggerFactory.getLogger(MqttSpace.class);
    protected final fURI broker;
    protected final fURI pattern;
    Mqtt5Client client;
    Mqtt5BlockingClient.Mqtt5Publishes incomingMessages;

    MemSpace cache;

    private static final ObjSerializer<String> SERIALIZER = ObjStringSerializer.build()
            .simpleColon(false)
            .hideTypesMatching(Set.of())
            .palette(Palette.NO_COLOR)
            .create();
    final JSONTranslator jsonTranslator = new JSONTranslator();

    public MqttSpace(final Map<Uri, Obj> config, final fURI tid, final fURI vid) {
        super(config, tid, vid);
        this.broker = config
                .get(new MUri(fURI.of("broker")))
                .orElseThrow(new IllegalArgumentException("config must have a broker key")).uriValue();
        this.pattern = config
                .get(new MUri(fURI.of("pattern")))
                .orElseThrow(new IllegalArgumentException("config nust have a pattern key")).uriValue();
        this.cache = new MemSpace(this.pattern, fURI.NULL);
        this.init();
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
                    .whenComplete((a, b) -> LOG.info("connected {}", a))
                    .get();
            this.client.toAsync()
                    .subscribeWith()
                    .topicFilter(this.pattern.toString())
                    .retainHandling(Mqtt5RetainHandling.SEND)
                    .callback(p -> {
                        LOG.info("received {}", p);
                        if (p.getPayload().isPresent()) {
                            final String json = StandardCharsets.UTF_8.decode(p.getPayload().get()).toString();
                            this.cache.write(
                                    fURI.of(p.getTopic().toString()),
                                    this.jsonTranslator.translateString(json));
                        } else {
                          this.cache.write(
                                    fURI.of(p.getTopic().toString()),
                                    NoObj.single());
                        }
                    })
                    .send()
                    .get();
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public fURI pattern() {
        return this.pattern;
    }

    @Override
    public Obj read(final fURI vid) {
        return this.cache.read(vid);
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        try {
            this.client
                    .toAsync()
                    .publishWith()
                    .topic(vid.toString())
                    .payload(obj.isNoObj() ? new byte[0] : this.jsonTranslator.translate(obj).toString().getBytes())
                    .retain(true)
                    .send()
                    .whenComplete((p, t) -> {
                        LOG.info("caching {}", p.getPublish());
                        if (p.getPublish().getPayload().isPresent()) {
                            final String json = StandardCharsets.UTF_8.decode(p.getPublish().getPayload().get()).toString();
                            this.cache.write(
                                    fURI.of(p.getPublish().getTopic().toString()),
                                    this.jsonTranslator.translateString(json));
                        } else {
                            this.cache.write(
                                    fURI.of(p.getPublish().getTopic().toString()),
                                    NoObj.single());
                        }
                    }).get();
            return NoObj.single();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public void append(fURI addr, Obj... obj) {
        throw new RuntimeException("append currently not implemented");
    }

   /* @Override
    public long length() {
        return 0;
    }

    @Override
    public Obj get(final fURI key) {
        return this.read(key);
    }*/

    @Override
    public Iterator<Obj> iterator() {
        return this.cache.iterator();
    }
}
