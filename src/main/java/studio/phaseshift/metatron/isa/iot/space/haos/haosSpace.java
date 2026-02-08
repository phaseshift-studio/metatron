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

package studio.phaseshift.metatron.isa.iot.space.haos;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.iot.space.mqtt.mqttSpace;
import studio.phaseshift.metatron.isa.iot.type.Device;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.sys.type.Router;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.isa.iot.iotInstSet.IOT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class haosSpace extends mqttSpace {

    private DevicePublisher devicePublisher;
    private final Map<String, EntityHolder> entities = new HashMap<>();

    public static final fURI HAOS_SPACE_TID = IOT_ISA_TID.extend("space/haos");
    public static final Type HAOS_SPACE_TYPE = Type.Builder.build()
            .tid(MQTT_SPACE_TID)
            .vid(HAOS_SPACE_TID)
            .constructor(instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(HAOS_SPACE_TID),
                    lst(isa_(rec(uri(PATTERN), URI_TYPE)).tryToInst()), (lhs, inst) -> {
                        final Space space = haosSpace.of(inst.arg(0).asRec(), inst.arg(0).vid());
                        Router.global().addSpace(space);
                        return space;
                    })).create();

    public static mqttSpace of(final Rec config, final fURI vid) {
        final Mqtt5Client client = MqttClient.builder()
                .identifier(config.at(uri(CLIENT).orElse(uri("mtron-" + Math.abs(UUID.randomUUID().getMostSignificantBits())))).uriValue().toString())
                .serverHost(config.at(HOST).uriValue().host())
                .serverPort(config.at(HOST).uriValue().port())
                .useMqttVersion5()
                .build();
        return new haosSpace(client, config.jvm(), vid);
    }


    protected haosSpace(final Mqtt5Client client, final Map<Obj, Obj> config, final fURI vid) {
        super(client, config, HAOS_SPACE_TID, vid);
        this.tid = HAOS_SPACE_TID;
        // Initialize DevicePublisher with the vid name
        this.devicePublisher = new DevicePublisher(vid.name(), "homeassistant", new HashMap<>());
        this.devicePublisher.connect(client);
    }
    
    public void discovery(final Device device) {
        LOG.info("announcing device: %s", device);
    }

    public DevicePublisher getDevicePublisher() {
        return this.devicePublisher;
    }

    public void registerEntity(final String entityVid, final DevicePublisher.Entity entity,
                               final Function<Object, Object> readFunction,
                               final Function<Object, Object> writeFunction) {
        this.entities.put(entityVid, new EntityHolder(entity, readFunction, writeFunction));
    }

    public Map<String, EntityHolder> getEntities() {
        return this.entities;
    }

    public void logInfo(final String message, final Object... args) {
        LOG.info(message, args);
    }

    /**
     * Helper class to hold entity along with its read/write functions.
     * Mirrors the Python pattern: self.ha.entities[self.entity_vid] = [entity, self.read_f, self.write_f]
     */
    public static class EntityHolder {
        private final DevicePublisher.Entity entity;
        private final Function<Object, Object> readFunction;
        private final Function<Object, Object> writeFunction;

        public EntityHolder(final DevicePublisher.Entity entity,
                           final Function<Object, Object> readFunction,
                           final Function<Object, Object> writeFunction) {
            this.entity = entity;
            this.readFunction = readFunction;
            this.writeFunction = writeFunction;
        }

        public DevicePublisher.Entity getEntity() {
            return entity;
        }

        public Function<Object, Object> getReadFunction() {
            return readFunction;
        }

        public Function<Object, Object> getWriteFunction() {
            return writeFunction;
        }
    }

}
