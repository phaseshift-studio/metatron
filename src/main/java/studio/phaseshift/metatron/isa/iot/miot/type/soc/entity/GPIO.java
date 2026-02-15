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

package studio.phaseshift.metatron.isa.iot.miot.type.soc.entity;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.isa.iot.iotInstSet.IOT_INST_TID;
import static studio.phaseshift.metatron.isa.iot.iotInstSet.IOT_ISA_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.AS_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REL_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface GPIO {

    fURI GPIO_TID = IOT_ISA_TID.extend("gpio");
    Type GPIO_TYPE = Type.Builder.build().tid(REL_TID).vid(GPIO_TID).predicate(isa_(rel(URI_TYPE, INT_TYPE)).tryToInst()).create();

    final class GPIOType {
        private static final GraphittyLogger LOG = Graphitty.log(GPIO.class);

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(AS_INST_TID.dom(REL_TID).rng(GPIO_TID), lst(GPIO_TYPE), (lhs, inst) -> lhs.asRel().tid(GPIO_TID)),
                    instC(IOT_INST_TID.extend("toggle").dom(GPIO_TID).rng(GPIO_TID), lst(), (lhs, inst) ->
                            lhs.asRel().second().equals(jnt(0L)) ? lhs.asRel().second(jnt(1L)) : lhs.asRel().second(jnt(0L)))
            ));


        }
    }


}
