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
import java.util.stream.Stream;

import static studio.phaseshift.metatron.isa.grph.grphInstSet.*;
import static studio.phaseshift.metatron.isa.grph.type.Vrtx.VrtxType.VRTX_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Edge extends Elmt {

    public static class EdgeType {

        public static final Type EDGE_TYPE = Type.Builder.build()
                .tid(ELMT_TID)
                .vid(EDGE_TID)
                .predicate(isa_(rec(
                        LABEL, URI_TYPE,
                        OUT, VRTX_TYPE,
                        IN, VRTX_TYPE))).create();

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(LABEL_INST_TID.dom(EDGE_TID).rng(URI_TID), lst(), (lhs, inst) -> lhs.asRec().at(LABEL)),
                    instC(INV_INST_TID.dom(EDGE_TID).rng(VRTX_TID), lst(), (lhs, inst) -> lhs.asRec().at(IN)),
                    instC(OUTV_INST_TID.dom(EDGE_TID).rng(VRTX_TID), lst(), (lhs, inst) -> lhs.asRec().at(OUT)),
                    instC(BOTHV_INST_TID.dom(EDGE_TID).rng(VRTX_TID), lst(), (lhs, inst) -> objs(Stream.concat(lhs.asRec().at(IN).stream(), lhs.asRec().at(OUT).stream())))));
        }
    }
}