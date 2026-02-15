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

package studio.phaseshift.metatron.isa.iot.miot;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.iot.miot.type.soc.SoC;
import studio.phaseshift.metatron.isa.iot.miot.type.soc.entity.GPIO;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.ServiceMetadata;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.AbstractInstSet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.isa.iot.haos.space.haosSpace.HAOS_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.iot.iotInstSet.IOT_ISA_TID;
import static studio.phaseshift.metatron.isa.iot.miot.type.Device.MIOT_DEVICE_TYPE;
import static studio.phaseshift.metatron.isa.iot.miot.type.Entity.MIOT_ENTITY_TYPE;
import static studio.phaseshift.metatron.isa.iot.miot.type.soc.SoC.MIOT_SOC_TYPE;
import static studio.phaseshift.metatron.isa.iot.miot.type.soc.esp32.WemosD1Mini.WemosD1MiniType.WEMOS_D1_MINI_TYPE;
import static studio.phaseshift.metatron.isa.iot.space.mqtt.mqttSpace.MQTT_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@ServiceMetadata(tid = "/m/iot/miot")
public class miotInstSet extends AbstractInstSet {

    public static final fURI MIOT_ISA_TID = IOT_ISA_TID.extend("miot");
    public static final fURI MIOT_INST_TID = MIOT_ISA_TID.extend("inst");
    

    public static final String MIOT_DEVICE_TID_STRING = "/m/iot/miot/device";
    public static final String MIOT_ENTITY_TID_STRING = "/m/iot/miot/entity";

    public static final fURI MIOT_THING_TID = MIOT_ISA_TID.extend("thing");
    public static final fURI MIOT_DEVICE_TID = MIOT_ISA_TID.extend("device");
    public static final fURI MIOT_ENTITY_TID = MIOT_ISA_TID.extend("entity");


    public static final Type MIOT_THING_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(MIOT_THING_TID)
            .predicate(isa_(rec(
                    uri("name"), STR_TYPE,
                    uri("usage"), rec(URI_TYPE, T(ALL)),
                    uri("entity"), rec(URI_TYPE, T(MIOT_ENTITY_TID))))).create();


    static {
        assert MIOT_DEVICE_TID_STRING.equals(MIOT_DEVICE_TID.toString());
        assert MIOT_ENTITY_TID_STRING.equals(MIOT_ENTITY_TID.toString());
    }

    public miotInstSet() {
        super(MIOT_ISA_TID, MIOT_ISA_TID);
    }

    @Override
    public Set<Type> types() {
        return Stream.of(
                MIOT_THING_TYPE,
                MIOT_DEVICE_TYPE,
                MIOT_ENTITY_TYPE,
                MIOT_SOC_TYPE,
                WEMOS_D1_MINI_TYPE,
                MQTT_SPACE_TYPE,
                HAOS_SPACE_TYPE).collect(Collectors.toSet());
    }

    @Override
    public Set<Inst> insts() {
        final List<Inst> insts = new ArrayList<>();
        insts.addAll(SoC.insts());
        insts.addAll(GPIO.GPIOType.insts());
        return new LinkedHashSet<>(insts);
    }
}