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

import com.google.gson.JsonParser;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.SObj;
import studio.phaseshift.metatron.lang.translator.JSONTranslator;
import studio.phaseshift.metatron.struct.Struct;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class MqttStruct extends SObj.Rec implements Struct {

    public static fURI MQTT_TID = fURI.of("mqtt/broker");

    private static final Logger LOG = LoggerFactory.getLogger(MqttStruct.class);
    protected final fURI broker;
    protected final fURI pattern;
    Mqtt5Client client;
    Mqtt5BlockingClient.Mqtt5Publishes incomingMessages;

    public MqttStruct(final BObj.Rec config, final fURI vid) {
        super(config.value(), MQTT_TID, vid);
        this.broker = config.<BObj.Uri>get(new SObj.Uri("broker")).orElseThrow(new IllegalArgumentException("supplied config has not broker key")).uriValue();
        this.pattern = config.<BObj.Uri>get(new SObj.Uri("pattern")).orElseThrow(new IllegalArgumentException("supplied config has not broker key")).uriValue();
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
                    .whenComplete((a, b) -> System.out.println("connected " + a))
                    .get();
            this.incomingMessages = this.client.toBlocking().publishes(MqttGlobalPublishFilter.ALL);
           /* client.toAsync().subscribeWith()
                    .topicFilter("fhatos/#")
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .callback(a -> LOG.info(Graphitty.parse("!r---!!%s".formatted(ObjParser.parse(a.getPayload().map(b -> StandardCharsets.UTF_8.decode(b).toString()).orElse("noobj")).toString()))))
                    .send()
                    .whenComplete((a, b) -> System.out.println("subscribed " + a))
                    .get();*/
           /* client.toAsync().subscribeWith()
                    .topicFilter("fhatos/#")
                    .topicFilter("homeassistant/#")
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .callback(a -> LOG.info(a.getPayload().map(b -> StandardCharsets.UTF_8.decode(b).toString()).orElse("noobj")))
                    .send()
                    .whenComplete((a, b) -> System.out.println("subscribed " + a))
                    .get();
            client.toAsync().publishWith()
                    .topic("fhatos")
                    .contentType("mtron")
                    .retain(true)
                    .payload("1.plus(2)".getBytes())
                    .send()
                    .whenComplete((a, b) -> System.out.println("published " + a))
                    .get();*/
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(final String[] args) {
        new MqttStruct(new SObj.Rec(Map.of(
                SObj.Uri.of("broker"),
                SObj.Uri.of("ip://192.168.66.2:1883"),
                SObj.Uri.of("pattern"),
                SObj.Uri.of("homeassistant/#"))), fURI.of("/mnt/mqtt"));
    }


    @Override
    public fURI pattern() {
        return this.value().get(new SObj.Uri("pattern")).uriValue();
    }

    @Override
    public BObj.Obj read(final fURI addr) {
        final Set<BObj.Obj> results = new HashSet<>();
        this.client.toBlocking().unsubscribeWith().topicFilter(addr.toString()).send();
        LOG.info("unsubscribed from %s\n".formatted(SObj.Uri.of(addr)));
        this.client
                .toBlocking()
                .subscribeWith()
                .topicFilter(addr.toString())
                .send();
        try {
            final JSONTranslator jsonTranslator = new JSONTranslator();
            final long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 1000) {
                Optional<Mqtt5Publish> o = this.incomingMessages.receive(1000, TimeUnit.MILLISECONDS);
                o.filter(p -> MqttTopicFilter.of(addr.toString()).matches(p.getTopic().filter()))
                        .filter(p -> p.getPayload().isPresent())
                        .ifPresent(p -> results.add(
                                jsonTranslator.translate(JsonParser.parseString(StandardCharsets.UTF_8.decode(p.getPayload().get()).toString()))));

                //ObjParser.parse("'" + p.getPayload()
                //.map(b -> StandardCharsets.UTF_8.decode(b).toString().replaceAll("'", "") + "'").orElse("noobj"))));
                // TODO: convert JSON to records
            }
        } catch (InterruptedException e) {
            LOG.error(e.getMessage());
        }

        if (results.isEmpty())
            return BObj.NoObj.of();
        else if (results.size() == 1)
            return results.iterator().next();
        else
            return new SObj.Objs(results);
    }

    @Override
    public BObj.Obj write(fURI addr, BObj.Obj obj) {
        try {
            this.client
                    .toAsync()
                    .publishWith()
                    .topic(addr.toString())
                    .payload(obj.toString().getBytes())
                    .send()
                    .get();
            return BObj.NoObj.of();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void append(fURI addr, BObj.Obj... obj) {

    }
}
