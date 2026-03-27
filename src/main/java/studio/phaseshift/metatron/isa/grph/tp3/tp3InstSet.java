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

import org.apache.tinkerpop.gremlin.structure.*;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.grph.tp3.parser.ObjTP3Serializer;
import studio.phaseshift.metatron.isa.grph.tp3.space.EdgeMap;
import studio.phaseshift.metatron.isa.grph.tp3.space.VertexMap;
import studio.phaseshift.metatron.isa.grph.tp3.space.tp3Space;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.*;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.INV_INST_TID;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.JREService;
import static studio.phaseshift.metatron.isa.grph.tp3.space.ElementMap.Helper.mtronKV;
import static studio.phaseshift.metatron.isa.grph.tp3.space.schema.modernSchema.MODERN_SCHEMA_TYPE;
import static studio.phaseshift.metatron.isa.grph.tp3.space.tp3Space.SERIALIZER;
import static studio.phaseshift.metatron.isa.grph.tp3.space.tp3Space.TP3_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@JREService(tid = "/m/grph/tp3")
public class tp3InstSet extends AbstractInstSet {

    public static final fURI TP3_ISA_TID = GRPH_ISA_TID.extend("tp3");
    public static final fURI GRPH_TID = TP3_ISA_TID.extend("grph");
    public static final String REDIRECT_STRING = ":redirect";
    public static final fURI REDIRECT_FURI = f(REDIRECT_STRING);

    protected static final Set<Type> TYPES = new LinkedHashSet<>();
    protected static final Set<Inst> INSTS = new LinkedHashSet<>();

    public tp3InstSet() {
        super(TP3_ISA_TID, TP3_ISA_TID);
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    protected static BiFunction<Obj, Inst, Obj> V_E_FUNCTION(final Direction direction) {
        return (lhs, inst) -> {
            final Rec lhsRec = lhs.asRec();
            final String[] labels = inst.arg(0).isNoObj() ? EMPTY_STRING_ARRAY : inst.arg(0).stream().map(Obj::uriValue).map(fURI::toString).toArray(String[]::new);
            return objs(IteratorUtil.map(VertexMap.recToVertex(lhsRec).edges(direction, labels), e -> EdgeMap.edgeToRec(e, lhsRec)));
        };
    }

    public static BiFunction<Obj, Inst, Obj> V_V_FUNCTION(final Direction direction) {
        return (lhs, inst) -> {
            final Rec lhsRec = lhs.asRec();
            final String[] labels = inst.arg(0).isNoObj() ? EMPTY_STRING_ARRAY : inst.arg(0).stream().map(Obj::uriValue).map(fURI::toString).toArray(String[]::new);
            return objs(IteratorUtil.map(VertexMap.recToVertex(lhs.asRec()).vertices(direction, labels), v -> {
                final Property<?> redirect = v.property(REDIRECT_STRING);
                return redirect.isPresent() ? (Obj) mtronKV(redirect).value() : VertexMap.vertexToRec(v, lhsRec);
            }));
        };
    }

    /*BiFunction<Poly<?, ?>, Object, Poly<?, ?>> VERTEX_POLY_MUTABLE = (vertexPoly, vertexPolyJVM) -> {
        vertexPoly.<ElementMap>jvmAs().putAll((Map<Uri, Obj>) vertexPolyJVM);
        //Obj.Helper.objCheck(vertexPoly, vertexPolyJVM, vertexPoly.tid(), vertexPoly.vid());
        return vertexPoly;
    };*/
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public static final Type GRPH_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(GRPH_TID)
            .create(TYPES, INSTS);
    public static final Type ELMT_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(ELMT_TID)
            .inst(LABEL_INST_TID.dom(ELMT_TID).rng(URI_TID), lst(), (lhs, inst) -> lhs.asRec().at(LABEL).orElse(uri(lhs.tid())))
            .doc("an element", "the element label", Map.of(), "returns the lhs element label (the tid)")
            .inst(VALUES_INST_TID.dom(ELMT_TID).rng(ALL.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> lhs.asRec().at(inst.arg(0).isNoObj() ? uri("+") : inst.arg(0).asUri()))
            .doc("an element", "the element values", Map.of(jnt(0), "zero or more element property labels"), "returns the lhs element arg-labeled values")
            .create(TYPES, INSTS);
    public static final Type VRTX_TYPE = Type.Builder.build()
            .tid(ELMT_TID)
            .vid(VRTX_TID)
            .isaPredicate(rec(
                    IN.maybe().<Uri>as(), rec(URI_TYPE, T(EDGE_TID.maybeSome())),
                    OUT.maybe(), rec(URI_TYPE, T(EDGE_TID.maybeSome()))))
            .inst(OUT_INST_TID.dom(VRTX_TID).rng(VRTX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_V_FUNCTION(Direction.OUT))
            .doc("a vertex", "out adjacent vertices", Map.of(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent outgoing vertices")
            .inst(IN_INST_TID.dom(VRTX_TID).rng(VRTX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_V_FUNCTION(Direction.IN))
            .doc("a vertex", "in adjacent vertices", Map.of(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent incoming vertices")
            .inst(BOTH_INST_TID.dom(VRTX_TID).rng(VRTX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_V_FUNCTION(Direction.BOTH))
            .doc("a vertex", "both adjacent vertices", Map.of(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent incoming and outgoing vertices")
            .inst(OUTE_INST_TID.dom(VRTX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_E_FUNCTION(Direction.OUT))
            .doc("a vertex", "out adjacent edges", Map.of(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent outgoing edges")
            .inst(INE_INST_TID.dom(VRTX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_E_FUNCTION(Direction.IN))
            .doc("a vertex", "in adjacent edges", Map.of(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent incoming edges")
            .inst(BOTHE_INST_TID.dom(VRTX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_E_FUNCTION(Direction.BOTH))
            .doc("a vertex", "both adjacent edges", Map.of(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent incoming and outgoing edges")
            /*  .inst(ADDE_INST_TID.dom(VRTX_TID).rng(EDGE_TID), lst(URI_TYPE, T(VRTX_TID), T(REC_TID.maybe())), (lhs, inst) -> objs(inst.arg(1).stream().map(e -> {
                  //if (e.test(T(VRTX_TID))) {
                  final VertexMap lhsMap = lhs.jvm();
                  final fURI edgeLabel = inst.arg(0).uriValue().big();
                  //    final Object rhsObj = inst.arg(1).test(VRTX_TYPE) ? ((VertexMap)e.jvm()).getBase() ? inst.arg(1).tid().test(AUTO_FROM_INST_TID) : 
                  // TODO:support 
  
                  final Edge edge = ((VertexMap) lhs.jvm()).getBase().addEdge(inst.arg(0).uriValue().big().toString(),
                          ((VertexMap) e.jvm()).getBase());
                  if (inst.arg(2).isRec()) {
                      inst.arg(2).asRec().elements().forEach(kv -> {
                          edge.property(kv.first().uriValue().toString(), kv.second().jvm());
                      });
                  }
                  return EdgeMap.edgeToRec(edge, lhs.asRec()).tid(inst.arg(0).uriValue().big());
                  //} else {
                  //    throw MTronException.of("invalid edge vertex: %s", e);
                  //}
              })))*/
            .inst(ADDE_INST_TID.dom(VRTX_TID).rng(EDGE_TID), lst(T(ALL), URI_TYPE, T(REC_TID.maybe())), (lhs, inst) -> {
                final Vertex outVertex = ((VertexMap) lhs.jvm()).getBase();
                final fURI edgeLabel = inst.arg(0).uriValue().big();
                final Graph graph = outVertex.graph();
                return objs(inst.arg(1).stream().map(e -> {
                    final Vertex inVertex;
                    final Edge edge;
                    if (e.isUri()) {
                        inVertex = graph.addVertex(
                                org.apache.tinkerpop.gremlin.structure.T.label, e.tid().equals(URI_TID) ? VRTX_TID.toString() : e.tid().toString(), 
                                REDIRECT_STRING, SERIALIZER.write(auto_from_(e.asUri()).tryToInst()));
                    } else {
                        inVertex = e.asRec().<VertexMap>jvmAs().getBase();
                    }
                    edge = outVertex.addEdge(edgeLabel.toString(), inVertex);
                    if (inst.arg(2).isRec()) {
                        inst.arg(2).asRec().jvm().forEach((key, value) -> edge.property(key.uriValue().toString(), value.jvm()));
                    }
                    return EdgeMap.edgeToRec(edge, lhs.<VertexMap>jvmAs().space).tid(edgeLabel);
                }));
            })
            .create(TYPES, INSTS);

    public static final Type EDGE_TYPE = Type.Builder.build()
            .tid(ELMT_TID)
            .vid(EDGE_TID)
            .isaPredicate(rec(IN, VRTX_TYPE, OUT, VRTX_TYPE))
            .inst(INV_INST_TID.dom(EDGE_TID).rng(VRTX_TID), lst(), (lhs, inst) -> lhs.asRec().at(IN))
            .doc("an edge", "the incoming vertex", Map.of(), "returns the lhs edge head vertex")
            .inst(OUTV_INST_TID.dom(EDGE_TID).rng(VRTX_TID), lst(), (lhs, inst) -> lhs.asRec().at(OUT))
            .doc("an edge", "the outgoing vertex", Map.of(), "returns the lhs edge tail vertex")
            .inst(BOTHV_INST_TID.dom(EDGE_TID).rng(VRTX_TID), lst(), (lhs, inst) -> objs(Stream.concat(lhs.asRec().at(IN).stream(), lhs.asRec().at(OUT).stream())))
            .doc("an edge", "both vertices", Map.of(), "returns the lhs edge's head and tail vertices")
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

