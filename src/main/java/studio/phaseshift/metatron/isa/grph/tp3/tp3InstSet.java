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

package studio.phaseshift.metatron.isa.grph.tp3;

import org.apache.tinkerpop.gremlin.structure.Direction;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.grph.tp3.parser.ObjTP3Serializer;
import studio.phaseshift.metatron.isa.grph.tp3.space.EdgeMap;
import studio.phaseshift.metatron.isa.grph.tp3.space.VertexMap;
import studio.phaseshift.metatron.isa.grph.tp3.space.tp3Space;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.ServiceMetadata;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.AbstractInstSet;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.*;
import static studio.phaseshift.metatron.isa.grph.tp3.space.schema.modernSchema.MODERN_SCHEMA_TYPE;
import static studio.phaseshift.metatron.isa.grph.tp3.space.tp3Space.TP3_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@ServiceMetadata(tid = "/m/grph/tp3")
public class tp3InstSet extends AbstractInstSet {

    public static final fURI TP3_ISA_TID = GRPH_ISA_TID.extend("tp3");

    public tp3InstSet() {
        super(TP3_ISA_TID, TP3_ISA_TID);
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public static final Type ELMT_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(ELMT_TID).create();
    public static final Type VRTX_TYPE = Type.Builder.build()
            .tid(ELMT_TID)
            .vid(VRTX_TID)
            /*.constructor(
                    instC(INST_TID.dom(ALL.maybe()).rng(VRTX_TID), lst(T(REC_TID)), (lhs, inst) -> {
                        final Obj obj = null == inst.arg(0).vid() ? noobj() : Router.readFromSpace(inst.arg(0).vid());
                        return obj.isNoObj() ? inst.arg(0) : obj;
                    }))*/
            .create();//.predicate(isa_(rec(
    public static final Type EDGE_TYPE = Type.Builder.build()
            .tid(ELMT_TID)
            .vid(EDGE_TID)
            .create();
    // .predicate(isa_(rec())).create();
    // LABEL, URI_TYPE,
    // OUT, VRTX_TYPE,
    //IN, VRTX_TYPE))).create();

    @Override
    public Set<Obj> consts() {
        return new LinkedHashSet<>(List.of(new ObjTP3Serializer()));
    }


    @Override
    public Set<Type> types() {
        return new HashSet<>(List.of(
                TP3_SPACE_TYPE,
                MODERN_SCHEMA_TYPE,
                ELMT_TYPE,
                VRTX_TYPE,
                EDGE_TYPE));
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static BiFunction<Obj, Inst, Obj> V_E_FUNCTION(final Direction direction) {
        return (lhs, inst) -> objs(IteratorUtil.stream(VertexMap.recToVertex(lhs.asRec())
                        .edges(direction, inst.arg(0).stream().map(Obj::uriValue).map(fURI::toString).toArray(String[]::new)))
                .map(e -> EdgeMap.edgeToRec(e, ((EdgeMap) lhs.jvm()).space)));
    }

    private static BiFunction<Obj, Inst, Obj> V_V_FUNCTION(final Direction direction) {
        return (lhs, inst) -> objs(IteratorUtil.stream(VertexMap.recToVertex(lhs.asRec())
                        .edges(direction, inst.arg(0).stream().map(Obj::uriValue).map(fURI::toString).toArray(String[]::new)))
                .map(v -> VertexMap.vertexToRec(v.vertices(direction.opposite()).next(), ((VertexMap) lhs.jvm()).space)));
    }

    @Override
    public Set<Inst> insts() {
        final List<Inst> insts = new ArrayList<>();
        insts.addAll(List.of(instC(V_INST_TID.dom(URI_TID).rng(VRTX_TID.maybeSome()), lst(), (lhs, inst) -> Router.readFromSpace(lhs.uriValue().extend("V/+")))));
        // elmnt inst set
        insts.addAll(List.of(
                instC(LABEL_INST_TID.dom(ELMT_TID).rng(URI_TID), lst(), (lhs, inst) -> lhs.asRec().at(LABEL).orElse(uri(lhs.tid()))),
                instC(VALUES_INST_TID.dom(ELMT_TID).rng(ALL.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> lhs.asRec().at(inst.arg(0).isNoObj() ? uri("+") : inst.arg(0).asUri()))
                // instC(VALUES_INST_TID.dom(ELMT_TID).rng(ALL.maybeSome()), lst(T(URI_TID.maybeSome())), PROPERTY_FUNCTION(uri("+")))
        ));
        // vrtx inst set
        insts.addAll(List.of(
                instC(OUTE_INST_TID.dom(VRTX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_E_FUNCTION(Direction.OUT)),
                instC(INE_INST_TID.dom(VRTX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_E_FUNCTION(Direction.IN)),
                instC(BOTHE_INST_TID.dom(VRTX_TID).rng(EDGE_TID.maybeSome()), lst(), (lhs, inst) -> objs(
                        V_E_FUNCTION(Direction.OUT).apply(lhs, inst),
                        V_E_FUNCTION(Direction.IN).apply(lhs, inst))),
                instC(OUT_INST_TID.dom(VRTX_TID).rng(VRTX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_V_FUNCTION(Direction.OUT)),
                instC(IN_INST_TID.dom(VRTX_TID).rng(VRTX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_V_FUNCTION(Direction.IN)),
                instC(BOTH_INST_TID.dom(VRTX_TID).rng(VRTX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> objs(
                        V_V_FUNCTION(Direction.OUT).apply(lhs, inst),
                        V_V_FUNCTION(Direction.IN).apply(lhs, inst)))));
        // edge inst set
        insts.addAll(List.of(
                instC(INV_INST_TID.dom(EDGE_TID).rng(VRTX_TID), lst(), (lhs, inst) -> lhs.asRec().at(IN)),
                instC(OUTV_INST_TID.dom(EDGE_TID).rng(VRTX_TID), lst(), (lhs, inst) -> lhs.asRec().at(OUT)),
                instC(BOTHV_INST_TID.dom(EDGE_TID).rng(VRTX_TID), lst(), (lhs, inst) -> objs(Stream.concat(lhs.asRec().at(IN).stream(), lhs.asRec().at(OUT).stream())))));
        // tp3 space inst set
        insts.addAll(tp3Space.TP3SpaceType.insts());
        return new LinkedHashSet<>(insts);
    }

}

