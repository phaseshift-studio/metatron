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
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.Uri;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import static studio.phaseshift.metatron.isa.grph.grphInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Vrtx {

    public static class VrtxType {

        public static final Type VRTX_TYPE = T(VRTX_TID, isa_(rec(
                OUT, T(EDGE_TID.maybeSome()),
                IN, T(EDGE_TID.maybeSome()))));

        private static BiFunction<Obj, Inst, Obj> V_E_FUNCTION(final Uri direction) {
            return (lhs, inst) -> objs(lhs.asRec().at(direction).asRec().elements().filter(r -> r.first().uriValue().matches(inst.arg(0).uriValue())).flatMap(r -> r.second().stream()));
        }

        private static BiFunction<Obj, Inst, Obj> V_V_FUNCTION(final Uri d1, final Uri d2) {
            return (lhs, inst) -> objs(V_E_FUNCTION(d1).apply(lhs, inst).stream().flatMap(e -> e.asRec().at(d2).stream()));
        }

        public static Set<Inst> insts() {

            return new LinkedHashSet<>(List.of(
                    instC(OUTE_INST_TID.dom(VRTX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_E_FUNCTION(OUT)),
                    instC(INE_INST_TID.dom(VRTX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_E_FUNCTION(IN)),
                    instC(BOTHE_INST_TID.dom(VRTX_TID).rng(EDGE_TID.maybeSome()), lst(), (lhs, inst) -> objs(
                            V_E_FUNCTION(OUT).apply(lhs, inst),
                            V_E_FUNCTION(IN).apply(lhs, inst))),
                    instC(OUT_INST_TID.dom(VRTX_TID).rng(VRTX_TID.maybeSome()), lst(), V_V_FUNCTION(OUT, IN)),
                    instC(IN_INST_TID.dom(VRTX_TID).rng(VRTX_TID.maybeSome()), lst(), V_V_FUNCTION(IN, OUT)),
                    instC(BOTH_INST_TID.dom(VRTX_TID).rng(VRTX_TID.maybeSome()), lst(), (lhs, inst) -> objs(
                            V_V_FUNCTION(OUT, IN).apply(lhs, inst),
                            V_V_FUNCTION(IN, OUT).apply(lhs, inst)))
            ));
        }
    }
}
