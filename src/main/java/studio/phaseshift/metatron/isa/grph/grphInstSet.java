/*
 * metatron: a distributed virtual machine and language
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

package studio.phaseshift.metatron.isa.grph;

import org.apache.tinkerpop.gremlin.jsr223.DefaultGremlinScriptEngineManager;
import org.apache.tinkerpop.gremlin.jsr223.GremlinLangScriptEngineFactory;
import org.apache.tinkerpop.gremlin.jsr223.GremlinScriptEngine;
import org.apache.tinkerpop.gremlin.structure.*;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.grph.io.ObjTP3Serializer;
import studio.phaseshift.metatron.isa.grph.space.EdgeMap;
import studio.phaseshift.metatron.isa.grph.space.VertexMap;
import studio.phaseshift.metatron.isa.grph.space.graphSpace;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.JREService;
import static studio.phaseshift.metatron.isa.grph.space.ElementMap.Helper.mtronKV;
import static studio.phaseshift.metatron.isa.grph.space.graphSpace.*;
import static studio.phaseshift.metatron.isa.grph.space.graphSpace.SERIALIZER;
import static studio.phaseshift.metatron.isa.grph.space.schema.modernSchema.MODERN_SCHEMA_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@JREService(vid = "/m/grph")
public class grphInstSet extends AbstractInstSet {

    public static final fURI GRPH_ISA_TID = M_ISA_TID.extend("grph");
    public static final fURI EDGE_TID = GRPH_ISA_TID.extend("edge");
    public static final fURI VRTX_TID = GRPH_ISA_TID.extend("vrtx");
    public static final fURI ELMT_TID = GRPH_ISA_TID.extend("elmt");
    public static final fURI GRPH_INST_TID = GRPH_ISA_TID.extend("inst");
    //
    public static final fURI GREMLIN_INST_TID = GRPH_INST_TID.extend("gremlin");
    public static final fURI ADDE_INST_TID = GRPH_INST_TID.extend("addE");
    public static final fURI PROPERTIES_INST_TID = GRPH_INST_TID.extend("properties");
    public static final fURI LABEL_INST_TID = GRPH_INST_TID.extend("label");
    public static final fURI VALUES_INST_TID = GRPH_INST_TID.extend("values");
    public static final fURI BOTHV_INST_TID = GRPH_INST_TID.extend("bothV");
    public static final fURI INV_INST_TID = GRPH_INST_TID.extend("inV");
    public static final fURI OUTV_INST_TID = GRPH_INST_TID.extend("outV");
    public static final fURI BOTHE_INST_TID = GRPH_INST_TID.extend("bothE");
    public static final fURI BOTH_INST_TID = GRPH_INST_TID.extend("both");
    public static final fURI INE_INST_TID = GRPH_INST_TID.extend("inE");
    public static final fURI OUTE_INST_TID = GRPH_INST_TID.extend("outE");
    public static final fURI IN_INST_TID = GRPH_INST_TID.extend("in");
    public static final fURI OUT_INST_TID = GRPH_INST_TID.extend("out");
    public static final String REDIRECT_STRING = ":redirect";
    public static final fURI REDIRECT_FURI = f(REDIRECT_STRING);
    public static final Uri BOTH = uri(Direction.BOTH.name());
    public static final Uri ID = uri("ID");
    public static final fURI IN_FURI = f(Direction.IN.name());
    public static final fURI OUT_FURI = f(Direction.OUT.name());
    public static final fURI BOTH_FURI = f(Direction.BOTH.name());
    public static final fURI LABEL_FURI = f("LABEL");
    public static final fURI ID_FURI = f("ID");
    public static final Uri LABEL = uri("LABEL");
    public static final Uri IN = uri(Direction.IN.name());
    public static final Uri OUT = uri(Direction.OUT.name());

    public grphInstSet() {
        super(mutableMap(uri(PATTERN), uri(GRPH_ISA_TID.extend(ALL))), GRPH_ISA_TID, GRPH_ISA_TID);
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

    @Override
    public void setup() {
        this.jvm().putAll(mutableMap(
                uri(PATTERN), uri(GRPH_ISA_TID.extend(ALL)),
                uri(CONST), lst(ObjTP3Serializer.single()),
                uri(TYPE), lst(
                        docWrap(Type.Builder.build()
                                .tid(REC_TID)
                                .vid(ELMT_TID)
                                .create(), "a key/value attributed element that is refined by vrtx::T and edge::T"),
                        docWrap(Type.Builder.build()
                                .tid(ELMT_TID)
                                .vid(VRTX_TID)
                                .isaPredicate(rec(
                                        IN.maybe().<Uri>as(), rec(URI_TYPE, T(EDGE_TID.maybeSome())),
                                        OUT.maybe(), rec(URI_TYPE, T(EDGE_TID.maybeSome()))))
                                .create(), "a key/value attributed vertex"),
                        docWrap(Type.Builder.build()
                                .tid(ELMT_TID)
                                .vid(EDGE_TID)
                                .isaPredicate(rec(IN, T(VRTX_TID), OUT, T(VRTX_TID)))
                                .create(), "an directed key/value attributed binary edge"),
                        GRAPH_SPACE_TYPE,
                        MODERN_SCHEMA_TYPE,
                        docWrap(Type.Builder.build()
                                        .tid(SPACE_TID)
                                        .vid(GRAPH_SPACE_TID)
                                        .create(),
                                "a graph space", "the graph space", Map.of(), "a space for graph traversal")
                ),
                uri(INST), lst(
                        docWrap(instC(grphInstSet.GREMLIN_INST_TID.dom(GRAPH_SPACE_TID).rng(ALL.maybeSome()), lst(STR_TYPE), (lhs, inst) -> {
                            try {
                                final GremlinLangScriptEngineFactory factory = new GremlinLangScriptEngineFactory();
                                //factory.setCustomizerManager(new CachedGremlinScriptEngineManager());
                                factory.setCustomizerManager(new DefaultGremlinScriptEngineManager());
                                final GremlinScriptEngine engine = factory.getScriptEngine();
                                engine.put("g", ((graphSpace) lhs).sjvm().traversal());
                                final Object object = engine.eval(inst.arg(0).strValue());
                                return MObjFactory.of().toObj(object);
                            } catch (Exception e) {
                                return fail(e);
                            }
                        }), "execute a gremlin traversal", "the gremlin expression", Map.of(), "executes the gremlin expression on the graph space"),
                        docWrap(instC(LABEL_INST_TID.dom(ELMT_TID).rng(URI_TID), lst(), (lhs, inst) -> lhs.asRec().at(LABEL).orElse(uri(lhs.tid()))),
                                "an element", "the element label", Map.of(), "returns the lhs element label (the tid)"),
                        docWrap(instC(VALUES_INST_TID.dom(ELMT_TID).rng(ALL.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) -> lhs.asRec().at(inst.arg(0).isNoObj() ? uri("+") : inst.arg(0).asUri())),
                                "an element", "the element values", Map.of(jnt(0), "zero or more element property labels"), "returns the lhs element arg-labeled values"),
                        docWrap(instC(INV_INST_TID.dom(EDGE_TID).rng(VRTX_TID), lst(), (lhs, inst) -> lhs.asRec().at(IN)),
                                "an edge", "the incoming vertex", Map.of(), "returns the lhs edge head vertex"),
                        docWrap(instC(OUTV_INST_TID.dom(EDGE_TID).rng(VRTX_TID), lst(), (lhs, inst) -> lhs.asRec().at(OUT)),
                                "an edge", "the outgoing vertex", Map.of(), "returns the lhs edge tail vertex"),
                        docWrap(instC(BOTHV_INST_TID.dom(EDGE_TID).rng(VRTX_TID), lst(), (lhs, inst) -> objs(Stream.concat(lhs.asRec().at(IN).stream(), lhs.asRec().at(OUT).stream()))),
                                "an edge", "both vertices", Map.of(), "returns the lhs edge's head and tail vertices"),
                        docWrap(instC(GRPH_INST_TID.extend("graph").dom(ALL.maybe()).rng(GRAPH_SPACE_TID),
                                        lst(GRAPH_CONFIG),
                                        (lhs, inst) -> graphSpace.of(inst.arg(0).asRec(), lhs.vid())),
                                "a graph space", "the graph space", Map.of(jnt(0), "the graph configuration"), "a space for graph traversal"),
                        docWrap(instC(OUT_INST_TID.dom(VRTX_TID).rng(VRTX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_V_FUNCTION(Direction.OUT)),
                                "a vertex", "out adjacent vertices", Map.of(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent outgoing vertices"),
                        docWrap(instC(IN_INST_TID.dom(VRTX_TID).rng(VRTX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_V_FUNCTION(Direction.IN)),
                                "a vertex", "in adjacent vertices", Map.of(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent incoming vertices"),
                        docWrap(instC(BOTH_INST_TID.dom(VRTX_TID).rng(VRTX_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_V_FUNCTION(Direction.BOTH)),
                                "a vertex", "both adjacent vertices", Map.of(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent incoming and outgoing vertices"),
                        docWrap(instC(OUTE_INST_TID.dom(VRTX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_E_FUNCTION(Direction.OUT)),
                                "a vertex", "out adjacent edges", Map.of(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent outgoing edges"),
                        docWrap(instC(INE_INST_TID.dom(VRTX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_E_FUNCTION(Direction.IN)),
                                "a vertex", "in adjacent edges", Map.of(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent incoming edges"),
                        docWrap(instC(BOTHE_INST_TID.dom(VRTX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_E_FUNCTION(Direction.BOTH)),
                                "a vertex", "both adjacent edges", Map.of(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent incoming and outgoing edges"),
                        instC(ADDE_INST_TID.dom(VRTX_TID).rng(EDGE_TID), lst(T(ALL), URI_TYPE, T(REC_TID.maybe())), (lhs, inst) -> {
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
                        }))));
        docWrap(this, "from vertex to vertex, the edge of the metatron is traversed");
        super.setup();
    }
}
