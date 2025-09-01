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
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import studio.phaseshift.metatron.lang.parse.ObjParser;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MqttStruct {

    public MqttStruct() {
        this.init();
        ;
    }

    public void init() {
        try {
            Mqtt5Client client = MqttClient.builder()
                    .identifier(UUID.randomUUID().toString())
                    .serverHost("192.168.66.2")
                    .useMqttVersion5()
                    .build();
            client.toAsync()
                    .connect()
                    .whenComplete((a, b) -> System.out.println("connected " + a))
                    .get();
            client.toAsync().subscribeWith()
                    .topicFilter("fhatos/#")
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .callback(a -> System.out.println("received " + ObjParser.parse(a.getPayload().map(b -> StandardCharsets.UTF_8.decode(b).toString()).orElse("noobj")).toString()))
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
                    .get();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(final String[] args) {
        new MqttStruct();
    }

}
