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
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.iot.miot.type.Device;
import studio.phaseshift.metatron.isa.iot.miot.type.Entity;
import studio.phaseshift.metatron.isa.iot.miot.type.SoC;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.isa.iot.iotInstSet.IOT_ISA_TID;
import static studio.phaseshift.metatron.isa.iot.miot.space.miotSpace.MIOT_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.iot.space.mqtt.mqttSpace.MQTT_SPACE_TYPE;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(tid = "/m/iot/miot")
public class miotInstSet extends AbstractInstSet {

    public static final fURI MIOT_ISA_TID = IOT_ISA_TID.extend("miot");
    public static final fURI MIOT_INST_TID = MIOT_ISA_TID.extend("inst");
    /// /////////////////////// FURIS ///////////////////////////////////
    public static final fURI MIOT_SPACE_TID = MIOT_ISA_TID.extend("space/miot");
    public static final fURI MIOT_THING_TID = MIOT_ISA_TID.extend("thing");
    public static final fURI MIOT_DEVICE_TID = MIOT_ISA_TID.extend("device");
    public static final fURI MIOT_ENTITY_TID = MIOT_ISA_TID.extend("entity");
    public static final fURI MIOT_GPIO_TID = MIOT_ISA_TID.extend("gpio");
    public static final fURI MIOT_PWM_TID = MIOT_ISA_TID.extend("pwm");
    public static final fURI MIOT_SOC_TID = MIOT_ISA_TID.extend("soc");
    public static final fURI MIOT_ESP32_TID = MIOT_SOC_TID.extend("esp32");
    public static final fURI MIOT_WEMOS_D1_MINI_TID = MIOT_ESP32_TID.extend("d1_mini");
    /// /////////////////////// TYPES //////////////////////////////////
    protected static final Set<Type> TYPES = new LinkedHashSet<>();
    protected static final Set<Inst> INSTS = new LinkedHashSet<>();

    static {
        Device.installTypes(TYPES, INSTS);
        Entity.installTypes(TYPES, INSTS);
        SoC.installTypes(TYPES, INSTS);
        TYPES.addAll(List.of(
                MQTT_SPACE_TYPE,
                MIOT_SPACE_TYPE));
    }

    public miotInstSet() {
        super(MIOT_ISA_TID, MIOT_ISA_TID);
    }

    public static fURI deduceVID(final Obj obj, final fURI childVID) {
        if (obj.vid() != null)
            return obj.vid();
        if (obj.parent() != null) {
            if (obj.parent().vid() != null) {
                return obj.parent().vid().extend(childVID);
            } else {
                return deduceVID(obj.parent(), childVID.retract(1));
            }
        }
        return null;
    }

    @Override
    public Set<Type> types() {
        return TYPES;
    }

    @Override
    public Set<Inst> insts() {
        return INSTS;
    }
}