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
import studio.phaseshift.metatron.isa.grph.tp3.space.ElementMap;
import studio.phaseshift.metatron.isa.grph.tp3.space.VertexMap;
import studio.phaseshift.metatron.isa.grph.tp3.space.tp3Space;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.AbstractInstSet;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.*;
import static studio.phaseshift.metatron.isa.grph.tp3.space.schema.modernSchema.MODERN_SCHEMA_TYPE;
import static studio.phaseshift.metatron.isa.grph.tp3.space.tp3Space.TP3_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TID;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
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
    protected static final Set<Type> TYPES = new LinkedHashSet<>();
    protected static final Set<Inst> INSTS = new LinkedHashSet<>();

    public tp3InstSet() {
        super(TP3_ISA_TID, TP3_ISA_TID);
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private static BiFunction<Obj, Inst, Obj> V_E_FUNCTION(final Direction direction) {
        return (lhs, inst) -> objs(IteratorUtil.stream(VertexMap.recToVertex(lhs.asRec())
                        .edges(direction, inst.arg(0).stream().map(Obj::uriValue).map(fURI::toString).toArray(String[]::new)))
                .map(e -> EdgeMap.edgeToRec(e, lhs.asRec())));
    }

    private static BiFunction<Obj, Inst, Obj> V_V_FUNCTION(final Direction direction) {
        return (lhs, inst) -> objs(IteratorUtil.stream(VertexMap.recToVertex(lhs.asRec())
                        .vertices(direction, inst.arg(0).stream().map(Obj::uriValue).map(fURI::toString).toArray(String[]::new)))
                .map(v -> VertexMap.vertexToRec(v, lhs.asRec())));
    }

    /*BiFunction<Poly<?, ?>, Object, Poly<?, ?>> VERTEX_POLY_MUTABLE = (vertexPoly, vertexPolyJVM) -> {
        vertexPoly.<ElementMap>jvmAs().putAll((Map<Uri, Obj>) vertexPolyJVM);
        //Obj.Helper.objCheck(vertexPoly, vertexPolyJVM, vertexPoly.tid(), vertexPoly.vid());
        return vertexPoly;
    };*/
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public static final Type ELMT_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(ELMT_TID)
            .inst(LABEL_INST_TID.dom(ELMT_TID).rng(URI_TID), lst(), (lhs, inst) -> lhs.asRec().at(LABEL).orElse(uri(lhs.tid())))
            .inst(VALUES_INST_TID.dom(ELMT_TID).rng(ALL.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> lhs.asRec().at(inst.arg(0).isNoObj() ? uri("+") : inst.arg(0).asUri()))
            .create(TYPES, INSTS);
    public static final Type VRTX_TYPE = Type.Builder.build()
            .tid(ELMT_TID)
            .vid(VRTX_TID)
            .isaPredicate(rec(
                    IN.maybe().<Uri>as(), rec(URI_TYPE, T(EDGE_TID.maybeSome())),
                    OUT.maybe(), rec(URI_TYPE, T(EDGE_TID.maybeSome()))))
            .inst(OUT_INST_TID, lst(T(URI_TID.maybeSome())), V_V_FUNCTION(Direction.OUT))
            .inst(IN_INST_TID, lst(T(URI_TID.maybeSome())), V_V_FUNCTION(Direction.IN))
            .inst(BOTH_INST_TID, lst(T(URI_TID.maybeSome())), V_V_FUNCTION(Direction.BOTH))
            .inst(OUTE_INST_TID, lst(T(URI_TID.maybeSome())), V_E_FUNCTION(Direction.OUT))
            .inst(INE_INST_TID, lst(T(URI_TID.maybeSome())), V_E_FUNCTION(Direction.IN))
            .inst(BOTHE_INST_TID, lst(T(URI_TID.maybeSome())), V_E_FUNCTION(Direction.BOTH))
            .create(TYPES, INSTS);
    public static final Type EDGE_TYPE = Type.Builder.build()
            .tid(ELMT_TID)
            .vid(EDGE_TID)
            .isaPredicate(rec(IN, VRTX_TYPE, OUT, VRTX_TYPE))
            .inst(INV_INST_TID.dom(EDGE_TID).rng(VRTX_TID), lst(), (lhs, inst) -> lhs.asRec().at(IN))
            .inst(OUTV_INST_TID.dom(EDGE_TID).rng(VRTX_TID), lst(), (lhs, inst) -> lhs.asRec().at(OUT))
            .inst(BOTHV_INST_TID.dom(EDGE_TID).rng(VRTX_TID), lst(), (lhs, inst) -> objs(Stream.concat(lhs.asRec().at(IN).stream(), lhs.asRec().at(OUT).stream())))
            .create(TYPES, INSTS);

    @Override
    public Set<Obj> consts() {
        return new LinkedHashSet<>(List.of(new ObjTP3Serializer()));
    }

    @Override
    public Set<Type> types() {
        TYPES.addAll(List.of(
                TP3_SPACE_TYPE,
                MODERN_SCHEMA_TYPE));
        return TYPES;
    }

    @Override
    public Set<Inst> insts() {
        INSTS.add(instC(V_INST_TID.dom(URI_TID).rng(VRTX_TID.maybeSome()), lst(), (lhs, inst) -> Router.readFromSpace(lhs.uriValue().extend("V/+"))));
        INSTS.addAll(tp3Space.TP3SpaceType.insts());
        return INSTS;
    }

}

