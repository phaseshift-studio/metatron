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

package studio.phaseshift.metatron.isa.iot.haos.space;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.iot.space.mqtt.mqttSpace;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.Map;
import java.util.UUID;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.isa.iot.haos.haosInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.BASE_TYPES;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.NOOBJ_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class haosSpace extends mqttSpace {

    public enum EntityType {
        SENSOR(HAOS_SENSOR_TYPE),
        SWITCH(HAOS_SWITCH_TYPE),
        BUTTON(HAOS_BUTTON_TYPE),
        NUMBER(HAOS_NUMBER_TYPE),
        LIGHT(HAOS_LIGHT_TYPE),
        //AUTOMATION(HAOS_AUTOMATION_TYPE),
        //SELECT(HAOS_SELECT_TYPE),
        NONE(NOOBJ_TYPE);

        final Type type;

        EntityType(final Type type) {
            this.type = type;
        }

        public static EntityType of(final fURI tid) {
            if (null == tid || tid.basePath().equals(NOOBJ_TID) || tid.isZero()) return NONE;
            for (final EntityType type : values())
                if (type.type.vid().equals(tid)) return type;
            return NONE;
        }

        public static EntityType inferFrom(final fURI id) {
            final String path = id.path();
            if (path.startsWith("sensor")) return SENSOR;
            else if (path.startsWith("switch")) return SWITCH;
            else if (path.startsWith("button")) return BUTTON;
            else if (path.startsWith("number")) return NUMBER;
            else if (path.startsWith("light")) return LIGHT;
            else return NONE;
        }

    }

    public static final fURI HAOS_SPACE_TID = HAOS_ISA_TID.extend("space").extend("haos");
    public static final Type HAOS_SPACE_TYPE = Type.Builder.build()
            .tid(MQTT_SPACE_TID)
            .vid(HAOS_SPACE_TID)
            .constructor(instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(HAOS_SPACE_TID),
                    lst(isa_(rec(uri(PATTERN), URI_TYPE)).tryToInst()), (lhs, inst) -> {
                        final Space space = haosSpace.of(inst.arg(0).asRec(), inst.arg(0).vid());
                        Router.global().addSpace(space);
                        return space;
                    })).create();

    public static haosSpace of(final Rec config, final fURI vid) {
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
    }


    public Obj read(final fURI vid) {
        final Obj result = super.read(vid);
        if (result.isNoObj())
            return result;
        if (!vid.hasPattern() && EntityType.inferFrom(vid) == EntityType.NONE)
            return result;
        return objs(result.stream().map(x -> {
            final fURI valueId = vid.isBranch() ? x.asRel().first().uriValue() : vid;
            final Obj value = vid.isBranch() ? x.asRel().second() : x;
            
            final EntityType entityType = EntityType.inferFrom(valueId);
            if (entityType == EntityType.NONE || !value.isRec() || !BASE_TYPES.contains(value.tid().basePath()) || !value.matches(entityType.type))
                return x;
            LOG.info("converting {{b}}%s{{X}} to {{y}}%s{{X}}", valueId, entityType.type.namedType());
            return vid.isBranch() ? x.asRel().second(value.tid(entityType.type.vid())/*.selfVID(valueId)*/) : value.tid(entityType.type.vid())/*.selfVID(valueId)*/;
        }));
    }
    
    /// /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    
}
