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

package studio.phaseshift.metatron.isa.iot;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.iot.type.SoC;
import studio.phaseshift.metatron.isa.iot.type.device.GPIO;
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

import static studio.phaseshift.metatron.isa.iot.haos.space.haosSpace.HAOS_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.iot.space.mqtt.mqttSpace.MQTT_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.iot.type.Device.DEVICE_TYPE;
import static studio.phaseshift.metatron.isa.iot.type.SoC.SoCType.PIN_TYPE;
import static studio.phaseshift.metatron.isa.iot.type.SoC.SoCType.SOC_TYPE;
import static studio.phaseshift.metatron.isa.iot.type.device.GPIO.GPIO_TYPE;
import static studio.phaseshift.metatron.isa.iot.type.esp32.ESP32.ESP32_TYPE;
import static studio.phaseshift.metatron.isa.iot.type.esp32.WemosD1Mini.WemosD1MiniType.WEMOS_D1_MINI_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.MTRON_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@ServiceMetadata(tid = "/m/iot")
public class iotInstSet extends AbstractInstSet {

    public static final fURI IOT_ISA_TID = MTRON_TID.extend("iot");
    public static final fURI IOT_INST_TID = IOT_ISA_TID.extend("inst");

    public static final fURI SOC_TID = IOT_ISA_TID.extend("soc");
    public static final fURI DEVICE_TID = IOT_ISA_TID.extend("device");
    public static final fURI ENTITY_TID = IOT_ISA_TID.extend("entity");
    public static final fURI ESP32_TID = IOT_ISA_TID.extend("soc/esp32");
    public static final fURI PWM_INST_TID = IOT_INST_TID.extend("pwm");

    public static final String IOT_DEVICE_TID_STRING = "/m/iot/device";
    public static final String IOT_ENTITY_TID_STRING = "/m/iot/entity";

    static {
        assert IOT_DEVICE_TID_STRING.equals(DEVICE_TID.toString());
        assert IOT_ENTITY_TID_STRING.equals(ENTITY_TID.toString());
    }

    public iotInstSet() {
        super(IOT_ISA_TID, IOT_ISA_TID);
    }

    /*public iotInstSet(final fURI vid) {
        super(IOT_ISA_TID, vid);
    }*/

    @Override
    public Set<Type> types() {
        return Stream.of(
                PIN_TYPE,
                DEVICE_TYPE,
                SOC_TYPE,
                WEMOS_D1_MINI_TYPE,
                ESP32_TYPE,
                GPIO_TYPE,
                MQTT_SPACE_TYPE,
                HAOS_SPACE_TYPE).collect(Collectors.toSet());
    }

    @Override
    public Set<Inst> insts() {
        final List<Inst> insts = new ArrayList<>();
        insts.addAll(SoC.SoCType.insts());
        insts.addAll(GPIO.GPIOType.insts());
        return new LinkedHashSet<>(insts);
    }
}