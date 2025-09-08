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

package studio.phaseshift.metatron.struct.mqtt;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5RetainHandling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.ui.Palette;
import studio.phaseshift.metatron.lang.obj.SObj;
import studio.phaseshift.metatron.lang.translate.JSONTranslator;
import studio.phaseshift.metatron.struct.Struct;
import studio.phaseshift.metatron.struct.mem.MemStruct;
import studio.phaseshift.metatron.ui.ObjSerializer;
import studio.phaseshift.metatron.ui.ObjStringSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static studio.phaseshift.metatron.lang.obj.BObj.URI_URI;

public class MqttStruct extends SObj.Obj implements Struct {

    public static fURI MQTT_TID = fURI.of("mqtt/broker");
    private static final Logger LOG = LoggerFactory.getLogger(MqttStruct.class);
    protected final fURI broker;
    protected final fURI pattern;
    Mqtt5Client client;
    Mqtt5BlockingClient.Mqtt5Publishes incomingMessages;

    MemStruct cache;

    private static final ObjSerializer<String> SERIALIZER = ObjStringSerializer.build()
            .simpleColon(false)
            .hideTypesMatching(Set.of())
            .palette(Palette.NO_COLOR)
            .create();
    final JSONTranslator jsonTranslator = new JSONTranslator();

    public MqttStruct(final Map<BObj.Uri, BObj.Obj> config, final fURI tid, final fURI vid) {
        super(config, tid, vid);
        this.broker = config
                .get(new SObj.Uri(fURI.of("broker"), URI_URI, null))
                .orElseThrow(new IllegalArgumentException("config must have a broker key")).uriValue();
        this.pattern = config
                .get(new SObj.Uri(fURI.of("pattern"), URI_URI, null))
                .orElseThrow(new IllegalArgumentException("config nust have a pattern key")).uriValue();
        this.cache = new MemStruct(this.pattern, fURI.NONE);
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
                    .connect()
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
                                    BObj.NoObj.of());
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
    public BObj.Obj read(final fURI addr) {
        return this.cache.read(addr);
    }

    @Override
    public BObj.Obj write(final fURI addr, final BObj.Obj obj) {
        try {
            this.client
                    .toAsync()
                    .publishWith()
                    .topic(addr.toString())
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
                                    BObj.NoObj.of());
                        }
                    }).get();
            return BObj.NoObj.of();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public void append(fURI addr, BObj.Obj... obj) {
        throw new RuntimeException("append currently not implemented");
    }

    @Override
    public long length() {
        return 0;
    }

    @Override
    public BObj.Obj get(final fURI key) {
        return this.read(key);
    }

    @Override
    public Iterator<BObj.Obj> iterator() {
        return this.cache.iterator();
    }
}
