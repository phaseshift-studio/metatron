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

import org.apache.tinkerpop.gremlin.structure.Direction;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.grph.space.tp3.EdgeMap;
import studio.phaseshift.metatron.isa.grph.space.tp3.VertexMap;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import static studio.phaseshift.metatron.isa.grph.grphInstSet.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Vrtx extends Elmt {

    public static class VrtxType {

        public static final Type VRTX_TYPE = Type.Builder.build()
                .tid(ELMT_TID)
                .vid(VRTX_TID)
                /*.constructor(
                        instC(INST_TID.dom(ALL.maybe()).rng(VRTX_TID), lst(T(REC_TID)), (lhs, inst) -> {
                            final Obj obj = null == inst.arg(0).vid() ? noobj() : Router.readFromSpace(inst.arg(0).vid());
                            return obj.isNoObj() ? inst.arg(0) : obj;
                        }))*/
                .create();//.predicate(isa_(rec(

        // OUT, T(EDGE_TID.maybeSome()),
        // IN, T(EDGE_TID.maybeSome())))).create();
        
        private static BiFunction<Obj, Inst, Obj> V_E_FUNCTION(final Direction direction) {
            return (lhs, inst) -> objs(IteratorUtil.stream(VertexMap.rVertex(lhs.asRec())
                            .edges(direction, inst.arg(0).stream().map(Obj::uriValue).map(fURI::toString).toArray(String[]::new)))
                    .map(EdgeMap::edgeRec));

        }

        private static BiFunction<Obj, Inst, Obj> V_V_FUNCTION(final Direction direction) {
            return (lhs, inst) -> objs(IteratorUtil.stream(VertexMap.rVertex(lhs.asRec())
                            .edges(direction, inst.arg(0).stream().map(Obj::uriValue).map(fURI::toString).toArray(String[]::new)))
                    .map(v -> VertexMap.vrtxRec(v.vertices(direction.opposite()).next())));

        }

        public static Set<Inst> insts() {

            return new LinkedHashSet<>(List.of(
                    instC(OUTE_INST_TID.dom(VRTX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_E_FUNCTION(Direction.OUT)),
                    instC(INE_INST_TID.dom(VRTX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_E_FUNCTION(Direction.IN)),
                    instC(BOTHE_INST_TID.dom(VRTX_TID).rng(EDGE_TID.maybeSome()), lst(), (lhs, inst) -> objs(
                            V_E_FUNCTION(Direction.OUT).apply(lhs, inst),
                            V_E_FUNCTION(Direction.IN).apply(lhs, inst))),
                    instC(OUT_INST_TID.dom(VRTX_TID).rng(VRTX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_V_FUNCTION(Direction.OUT)),
                    instC(IN_INST_TID.dom(VRTX_TID).rng(VRTX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_V_FUNCTION(Direction.IN)),
                    instC(BOTH_INST_TID.dom(VRTX_TID).rng(VRTX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(
                            V_V_FUNCTION(Direction.OUT).apply(lhs, inst),
                            V_V_FUNCTION(Direction.IN).apply(lhs, inst)))
            ));
        }
    }
}
