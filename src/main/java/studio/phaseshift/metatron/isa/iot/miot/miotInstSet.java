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
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.AbstractInstSet;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.isa.iot.haos.space.haosSpace.HAOS_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.iot.iotInstSet.IOT_ISA_TID;
import static studio.phaseshift.metatron.isa.iot.miot.type.soc.SoC.MIOT_SOC_TYPE;
import static studio.phaseshift.metatron.isa.iot.miot.type.soc.esp32.WemosD1Mini.WemosD1MiniType.WEMOS_D1_MINI_TYPE;
import static studio.phaseshift.metatron.isa.iot.space.mqtt.mqttSpace.MQTT_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
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
    /// /////////////////////// FURIS ///////////////////////////////////
    public static final fURI MIOT_THING_TID = MIOT_ISA_TID.extend("thing");
    public static final fURI MIOT_DEVICE_TID = MIOT_ISA_TID.extend("device");
    public static final fURI MIOT_ENTITY_TID = MIOT_ISA_TID.extend("entity");
    public static final fURI MIOT_GPIO_TID = MIOT_ISA_TID.extend("gpio");
    /// /////////////////////// TYPES //////////////////////////////////
    protected static final Set<Type> TYPES = new LinkedHashSet<>();
    protected static final Set<Inst> INSTS = new LinkedHashSet<>();
    public static final Type MIOT_DEVICE_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(MIOT_DEVICE_TID)
            .isaPredicate(rec(uri("status"), is_(or_(eq_(uri(ONLINE)), eq_(uri(OFFLINE)))).tryToInst()))
            .create(TYPES, INSTS);
    public static final Type MIOT_ENTITY_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(MIOT_ENTITY_TID)
            .isaPredicate(rec(uri(SUPER), MIOT_DEVICE_TYPE))
            .create(TYPES, INSTS);
    public static final Type MIOT_GPIO_TYPE = Type.Builder.build()
            .tid(MIOT_ENTITY_TID)
            .vid(MIOT_GPIO_TID)
            .isaPredicate(rec(INT_TYPE, INT_TYPE))
            .inst(MIOT_INST_TID.extend("toggle").dom(MIOT_GPIO_TID).rng(MIOT_GPIO_TID), lst(INT_TYPE),
                    (lhs, inst) -> {
                        final Uri key = inst.arg(0).as(URI_TYPE).as();
                        final long currentValue = lhs.asRec().at(key).orElse(jnt(0L)).intValue();
                        final Int newValue = 0 == currentValue ? jnt(1) : jnt(0);
                        if (lhs.vid() == null)
                            lhs.logger().warn("no vid associated with gpio", lhs);
                        else
                            Router.writeToSpace(lhs.vid().extend(key.uriValue()), newValue);
                        return lhs;
                    })
            .create(TYPES, INSTS);


    static {
        assert MIOT_DEVICE_TID_STRING.equals(MIOT_DEVICE_TID.toString());
        assert MIOT_ENTITY_TID_STRING.equals(MIOT_ENTITY_TID.toString());
    }

    public miotInstSet() {
        super(MIOT_ISA_TID, MIOT_ISA_TID);
    }

    @Override
    public Set<Type> types() {
        TYPES.addAll(List.of(
                MIOT_SOC_TYPE,
                WEMOS_D1_MINI_TYPE,
                MQTT_SPACE_TYPE,
                HAOS_SPACE_TYPE));
        return TYPES;
    }

    @Override
    public Set<Inst> insts() {
        INSTS.addAll(SoC.insts());
        return INSTS;
    }
}