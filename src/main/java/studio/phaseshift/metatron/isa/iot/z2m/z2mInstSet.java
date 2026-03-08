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

package studio.phaseshift.metatron.isa.iot.z2m;

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
public class z2mInstSet extends AbstractInstSet {

    public static final fURI Z2M_ISA_TID = IOT_ISA_TID.extend("z2m");
    public static final fURI Z2M_INST_TID = Z2M_ISA_TID.extend("inst");
    /// /////////////////////// FURIS ///////////////////////////////////
    public static final fURI Z2M_SPACE_TID = Z2M_ISA_TID.extend("space/z2m");
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

    public z2mInstSet() {
        super(Z2M_ISA_TID, Z2M_ISA_TID);
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
