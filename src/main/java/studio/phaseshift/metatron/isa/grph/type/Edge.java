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

package studio.phaseshift.metatron.isa.grph.type;

import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Type;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.isa.grph.grphInstSet.*;
import static studio.phaseshift.metatron.isa.grph.type.Vrtx.VrtxType.VRTX_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Edge {

    public static class EdgeType {

        public static final Type EDGE_TYPE = T(EDGE_TID, isa_(rec(
                OUT, VRTX_TYPE,
                IN, VRTX_TYPE)));

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(INV_INST_TID.dom(EDGE_TID).rng(VRTX_TID), lst(), (lhs, inst) -> lhs.asRec().at(IN)),
                    instC(OUTV_INST_TID.dom(EDGE_TID).rng(VRTX_TID), lst(), (lhs, inst) -> lhs.asRec().at(OUT)),
                    instC(BOTHV_INST_TID.dom(EDGE_TID).rng(VRTX_TID), lst(), (lhs, inst) -> objs(lhs.asRec().at(IN), lhs.asRec().at(OUT)))));
        }
    }
}