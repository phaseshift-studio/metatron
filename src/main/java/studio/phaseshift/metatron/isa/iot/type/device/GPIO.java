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

package studio.phaseshift.metatron.isa.iot.type.device;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.iot.type.Device;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.*;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.fnull;
import static studio.phaseshift.metatron.isa.iot.iotInstSet.IOT_INST_TID;
import static studio.phaseshift.metatron.isa.iot.iotInstSet.IOT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.INT_TID;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class GPIO extends MRec implements Device {

    private static final GraphittyLogger LOG = Graphitty.log(GPIO.class);
    public static final fURI GPIO_TID = IOT_ISA_TID.extend("device/gpio");
    public static final Type GPIO_TYPE = T(GPIO_TID, null,
            instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(GPIO_TID),
                    lst(REC_TYPE),
                    (lhs, inst) -> {
                        final Map<Obj, Obj> result = new LinkedHashMap<>();
                        inst.arg(0).asRec().elements().forEach(e -> {
                            if (e.first().isUri()) {
                                final String name = e.first().uriValue().name();
                                if (CommonUtil.isInt(name)) {
                                    result.put(uri(name), e.second());
                                } else {
                                    LOG.warn("ignoring invalid pin: %s", name);
                                }
                            } else {
                                LOG.warn("ignoring invalid pin: %s", e.first());
                            }
                        });
                        return new GPIO(result, fnull);
                    }));

    public GPIO(final Map<Obj, Obj> pins, final fURI vid) {
        super(pins, GPIO_TID, vid);
        /*Router.global().write(
                this.vid().extend("+").query(SUB),
                instC(GPIO_INST_TID.extend("pin").dom(SUBSCRIPTION_TID).rng(INT_TID), lst(), (lhs, inst) -> {
                    final Int pin = jnt(Integer.valueOf(lhs.asLst().at(jnt(0)).asUri().uriValue().name()));
                    final Int value = lhs.asLst().at(jnt(1)).as();
                    this.set(pin, value, false);
                    return value;
                }));*/
    }

    /* public GPIO set(final Int pin, final Int value, final boolean publish) {
         this.at(uri(String.valueOf(pin.intValue())), value);
         if (publish)
             Router.global().write(this.vid().extend(String.valueOf(value.intValue())), value);
         return this;
     }
 
     public Int get(final Int pin) {
         return this.at(uri(String.valueOf(pin.intValue())));
     }
 */
    public static final class GPIOType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(IOT_INST_TID.extend("read").dom(GPIO_TID).rng(INT_TID), lst(INT_TYPE), (lhs, inst) -> lhs.asRec().at(uri(String.valueOf(inst.arg(0).intValue())))),
                    instC(IOT_INST_TID.extend("write").dom(GPIO_TID).rng(GPIO_TID), lst(INT_TYPE, INT_TYPE), (lhs, inst) -> lhs.asRec().at(uri(String.valueOf(inst.arg(0).intValue())), inst.arg(1), MUTABLE))
            ));


        }
    }


}
