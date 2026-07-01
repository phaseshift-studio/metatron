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
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.*;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.algebra.rewrite.CommonRewrites;
import studio.phaseshift.metatron.algebra.rewrite.Rewriter;
import studio.phaseshift.metatron.furi.DataPath;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.grph.io.ObjTP3Serializer;
import studio.phaseshift.metatron.isa.grph.space.EdgeRec;
import studio.phaseshift.metatron.isa.grph.space.ElementRec;
import studio.phaseshift.metatron.isa.grph.space.VertexRec;
import studio.phaseshift.metatron.isa.grph.space.grphSpace;
import studio.phaseshift.metatron.isa.grph.space.schema.GremlinRewriteUtils;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.grph.space.grphSpace.*;
import static studio.phaseshift.metatron.isa.grph.space.grphSpace.SERIALIZER;
import static studio.phaseshift.metatron.isa.grph.space.schema.modernSchema.MODERN_SCHEMA_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(vid = "/m/grph")
public class grphInstSet extends AbstractInstSet {

    public static final fURI GRPH_ISA_TID = M_ISA_TID.extend("grph");
    public static final fURI EDGE_TID = GRPH_ISA_TID.extend("edge");
    public static final fURI VRTX_TID = GRPH_ISA_TID.extend("vrtx");
    public static final fURI ELMT_TID = GRPH_ISA_TID.extend("elmt");
    public static final fURI GRPH_INST_TID = GRPH_ISA_TID.extend("inst");
    public static final fURI GRPH_REWRITE_TID = GRPH_INST_TID.extend("rewrite");
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
    public static final String AUTO_ROUTE_STRING = "!route";
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

    public static Type VRTX_TYPE;
    public static Type EDGE_TYPE;

    public grphInstSet() {
        super(mutableMap(uri(PATTERN), uri(GRPH_ISA_TID.extend(ALL))), INSTSET_TID, GRPH_ISA_TID);
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Resolve the element VID: prefer Obj VID, check ElementRec fields, unwrap Objs.
     */
    private static fURI resolveVid(final Obj lhs) {
        fURI base = lhs.vid();
        if (base == null && lhs instanceof ElementRec<?> er)
            base = er.elementVID();
        // lhs could be an Objs wrapping multiple vertices — search inside for ElementRec
        if (base == null && lhs.isObjs()) {
            base = lhs.stream()
                    .filter(o -> o instanceof ElementRec<?>)
                    .map(o -> ((ElementRec<?>) o).elementVID())
                    .findFirst().orElse(null);
        }
        return base;
    }

    private static Obj routeEdgeTraversal(final Obj lhs, final Inst inst, final Direction direction) {
        final fURI base = resolveVid(lhs);
        if (base == null) return noobj();
        fURI path = base.extend(direction.name());
        if (!inst.arg(0).isNoObj())
            path = path.extend(inst.arg(0).uriValue().toString());
        return Router.readFromSpace(path);
    }

    private static Obj routeVertexTraversal(final Obj lhs, final Inst inst, final Direction direction) {
        final fURI base = resolveVid(lhs);
        if (base == null) return noobj();
        fURI path = base.extend(direction.name());
        if (!inst.arg(0).isNoObj())
            path = path.extend(inst.arg(0).uriValue().toString());
        path = path.extend(direction.opposite().name());
        return Router.readFromSpace(path);
    }

    private static Obj routeBothTraversal(final Obj lhs, final Inst inst) {
        final fURI base = resolveVid(lhs);
        if (base == null) return noobj();
        final fURI outPath = inst.arg(0).isNoObj()
                ? base.extend("OUT").extend("+")
                : base.extend("OUT").extend(inst.arg(0).uriValue().toString()).extend(Tokens.IN);
        final fURI inPath = inst.arg(0).isNoObj()
                ? base.extend("IN").extend("+")
                : base.extend("IN").extend(inst.arg(0).uriValue().toString()).extend(Tokens.OUT);
        return objs(Stream.concat(
                Router.readFromSpace(outPath).stream(),
                Router.readFromSpace(inPath).stream()));
    }

    private static Obj routeBothETraversal(final Obj lhs, final Inst inst) {
        final fURI base = resolveVid(lhs);
        if (base == null) return noobj();
        final fURI outPath = inst.arg(0).isNoObj()
                ? base.extend("OUT")
                : base.extend("OUT").extend(inst.arg(0).uriValue().toString());
        final fURI inPath = inst.arg(0).isNoObj()
                ? base.extend("IN")
                : base.extend("IN").extend(inst.arg(0).uriValue().toString());
        final Obj outResult = Router.readFromSpace(outPath);
        final Obj inResult = Router.readFromSpace(inPath);
        if (outResult.isFail()) return outResult;
        if (inResult.isFail()) return inResult;
        return objs(Stream.concat(outResult.stream(), inResult.stream()));
    }

    protected static BiFunction<Obj, Inst, Obj> V_E_FUNCTION(final Direction direction) {
        return direction == Direction.BOTH
                ? (lhs, inst) -> routeBothETraversal(lhs, inst)
                : (lhs, inst) -> routeEdgeTraversal(lhs, inst, direction);
    }

    public static BiFunction<Obj, Inst, Obj> V_V_FUNCTION(final Direction direction) {
        final String dir = direction.name();
        return direction == Direction.BOTH
                ? grphInstSet::routeBothTraversal
                : (lhs, inst) -> routeVertexTraversal(lhs, inst, direction);
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
                uri(CONSTQ), lst(ObjTP3Serializer.single()),
                uri(TYPE), lst(
                        docWrap(Type.Builder.build()
                                .tid(REC_TID)
                                .vid(ELMT_TID)
                                .create(), "a key/value attributed element that is refined by vrtx::T and edge::T"),
                        VRTX_TYPE = docWrap(Type.Builder.build()
                                .tid(ELMT_TID)
                                .vid(VRTX_TID)
                                /*.isaPredicate(rec(
                                        OUT.maybe().asUri(), rec(URI_TYPE, T(EDGE_TID.maybeSome())),
                                        IN.maybe(), rec(URI_TYPE, T(EDGE_TID.maybeSome()))))*/
                                .create(), "a key/value attributed vertex"),
                        EDGE_TYPE = docWrap(Type.Builder.build()
                                .tid(ELMT_TID)
                                .vid(EDGE_TID)
                                /*   .isaPredicate(rec(
                                           OUT, rec(URI_TYPE, T(VRTX_TID)),
                                           IN, rec(URI_TYPE, T(VRTX_TID))))*/
                                .create(), "a directed key/value attributed binary edge"),
                        docWrap(GRPH_SPACE_TYPE, "a space for graph traversal"),
                        docWrap(MODERN_SCHEMA_TYPE, "a schema for the modern graph dataset")
                ),
                uri(INST), lst(
                        docWrap(instC(GREMLIN_INST_TID.dom(GRPH_SPACE_TID).rng(ALL.maybeSome()), lst(STR_TYPE), (lhs, inst) -> {
                            try {
                                final GremlinLangScriptEngineFactory factory = new GremlinLangScriptEngineFactory();
                                //factory.setCustomizerManager(new CachedGremlinScriptEngineManager());
                                factory.setCustomizerManager(new DefaultGremlinScriptEngineManager());
                                final GremlinScriptEngine engine = factory.getScriptEngine();
                                engine.put("g", ((grphSpace) lhs).sjvm());
                                final Object object = engine.eval(inst.arg(0).strValue());
                                return MObjFactory.of().toObj(object);
                            } catch (Exception e) {
                                return fail(e);
                            }
                        }), "execute a gremlin traversal", "the gremlin expression", Map.of(), "executes the gremlin expression on the graph space"),
                        docWrap(instC(LABEL_INST_TID.dom(REC_TID).rng(URI_TID), lst(), (lhs, inst) -> lhs.asRec().at(LABEL).orElse(uri(lhs.tid()))),
                                "an element", "the element label", Map.of(), "returns the lhs element label (the tid)"),
                        docWrap(instC(VALUES_INST_TID.dom(REC_TID).rng(ALL.maybeSome()), lst(T(URI_TID.maybeSome())), (lhs, inst) ->
                                        inst.arg(0).isNoObj() ? lhs.asRec().at(uri("+")) : objs(inst.args().valueElements().map(key -> lhs.asRec().at(key)))),
                                "an element", "the element values", mutableMap(jnt(0), "zero or more element property labels"), "returns the lhs element arg-labeled values"),
                        docWrap(instC(INV_INST_TID.dom(EDGE_TID).rng(VRTX_TID), lst(), (lhs, inst) -> {
                                    final fURI vid = resolveVid(lhs);
                                    return vid != null ? Router.readFromSpace(vid.extend("IN")) : lhs.asRec().at(IN);
                                }),
                                "an edge", "the incoming vertex", Map.of(), "returns the lhs edge head vertex"),
                        docWrap(instC(OUTV_INST_TID.dom(EDGE_TID).rng(VRTX_TID), lst(), (lhs, inst) -> {
                                    final fURI vid = resolveVid(lhs);
                                    return vid != null ? Router.readFromSpace(vid.extend("OUT")) : lhs.asRec().at(OUT);
                                }),
                                "an edge", "the outgoing vertex", Map.of(), "returns the lhs edge tail vertex"),
                        docWrap(instC(BOTHV_INST_TID.dom(EDGE_TID).rng(VRTX_TID.c(cInt.of(2))), lst(), (lhs, inst) -> {
                                    final fURI vid = resolveVid(lhs);
                                    if (vid != null)
                                        return objs(Stream.concat(
                                                Router.readFromSpace(vid.extend("IN")).stream(),
                                                Router.readFromSpace(vid.extend("OUT")).stream()));
                                    return objs(Stream.concat(lhs.asRec().at(IN).stream(), lhs.asRec().at(OUT).stream()));
                                }),
                                "an edge", "both vertices", Map.of(), "returns the lhs edge's head and tail vertices"),
                    /*    docWrap(instC(GRPH_INST_TID.extend("graph").dom(ALL.maybe()).rng(GRAPH_SPACE_TID),
                                        lst(GRAPH_CONFIG),
                                        (lhs, inst) -> grphSpace.of(inst.arg(0).asRec(), lhs.vid())),
                                "a graph space", "the graph space", Map.of(jnt(0), "the graph configuration"), "a space for graph traversal"),*/
                        docWrap(instC(OUT_INST_TID.dom(VRTX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_V_FUNCTION(Direction.OUT)),
                                "a vertex", "out adjacent vertices", mutableMap(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent outgoing vertices"),
                        docWrap(instC(IN_INST_TID.dom(VRTX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_V_FUNCTION(Direction.IN)),
                                "a vertex", "in adjacent vertices", mutableMap(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent incoming vertices"),
                        docWrap(instC(BOTH_INST_TID.dom(VRTX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_V_FUNCTION(Direction.BOTH)),
                                "a vertex", "both adjacent vertices", mutableMap(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent incoming and outgoing vertices"),
                        docWrap(instC(OUTE_INST_TID.dom(VRTX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_E_FUNCTION(Direction.OUT)),
                                "a vertex", "out adjacent edges", mutableMap(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent outgoing edges"),
                        docWrap(instC(INE_INST_TID.dom(VRTX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_E_FUNCTION(Direction.IN)),
                                "a vertex", "in adjacent edges", mutableMap(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent incoming edges"),
                        docWrap(instC(BOTHE_INST_TID.dom(VRTX_TID).rng(EDGE_TID.maybeSome()), lst(T(URI_TID.maybeSome())), V_E_FUNCTION(Direction.BOTH)),
                                "a vertex", "both adjacent edges", mutableMap(jnt(0), "zero or more edge labels"), "returns the lhs vertex arg-adjacent incoming and outgoing edges"),
                        instC(ADDE_INST_TID.dom(VRTX_TID).rng(EDGE_TID), lst(URI_TYPE, T(REC_TID.some()), T(REC_TID.maybe())), (lhs, inst) -> {
                            if (!(lhs instanceof VertexRec vr)) return noobj();
                            final Vertex outVertex = vr.vertex();
                            final grphSpace graphSpace = vr.space();
                            final GraphTraversalSource g = graphSpace.sjvm();
                            final fURI edgeLabel = inst.arg(0).uriValue().big();
                            final String label = edgeLabel.small().toString();
                            return objs(inst.arg(1).stream().map(otherV -> {
                                final Vertex inVertex;
                                if (otherV instanceof VertexRec otherVRec) {
                                    inVertex = otherVRec.vertex();
                                } else {
                                    final Optional<fURI> pointer = Obj.Helper.getPointer(otherV);
                                    if (pointer.isPresent()) {
                                        inVertex = g.addV(otherV.apply(noobj()).tid().big().toString())
                                                .property(AUTO_ROUTE_STRING, SERIALIZER.write(auto_from_(pointer.get())))
                                                .next();
                                    } else {
                                        throw MTronException.of("invalid edge vertex: %s", otherV);
                                    }
                                }
                                final Edge edge = g.V(outVertex.id()).addE(label).to(inVertex).next();
                                if (inst.arg(2).isRec()) {
                                    inst.arg(2).asRec().jvm().forEach((key, value) -> edge.property(key.uriValue().toString(), value.jvm()));
                                }
                                return new EdgeRec(edge, graphSpace).tid(edgeLabel);
                            }));
                        })),
                uri(REWRITE), lst(
                        docWrap(
                                InstSet.Helper.rewriter(
                                        GRPH_REWRITE_TID.extend("gremlin_count"),
                                        code -> code.selfJVM(
                                                Rewriter.search(code.insts())
                                                        .match(List.of(instB(FROM_INST_TID, lst()), instB(COUNT_INST_TID, lst())))
                                                        .rewrite(map -> {
                                                            final java.util.List<Inst> matchList = new java.util.ArrayList<>(map.values());
                                                            final Inst fromInst = matchList.get(0);
                                                            final Obj ref = fromInst.arg(0);
                                                            // guard: only V/E collections, no traversals (no field/extensions)
                                                            if (!ref.isUri())
                                                                return matchList.stream().map(Obj::asInst).toList();
                                                            final DataPath dp = DataPath.withoutDB(ref.uriValue());
                                                            if (!("V".equals(dp.collection()) || "E".equals(dp.collection()))
                                                                    || !dp.entryIsWildcard()
                                                                    || dp.hasField() || dp.hasExtension())
                                                                return matchList.stream().map(Obj::asInst).toList();
                                                            final fURI furi = ref.uriValue();
                                                            final studio.phaseshift.metatron.isa.Space space = Router.global().getSpaceFor(furi);
                                                            if (!(space instanceof grphSpace gs))
                                                                return matchList.stream().map(Obj::asInst).toList();
                                                            final long count = "V".equals(dp.collection())
                                                                    ? gs.sjvm().V().count().next()
                                                                    : gs.sjvm().E().count().next();
                                                            return List.of(instC(
                                                                    GRPH_REWRITE_TID.extend("graph_count").dom(ALL_STAR).rng(INT_TID),
                                                                    lst(uri(furi)),
                                                                    (lhs, inst) -> jnt(count)));
                                                        })
                                        ).asCode()),
                                "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's count()-reducer for vrtx/edge collections"),
                        docWrap(CommonRewrites.limitRewrite(
                                grphSpace.class,
                                GRPH_REWRITE_TID.extend("gremlin_limit"),
                                (space, dp, limit) -> {
                                    final Iterator<? extends Element> elements = "V".equals(dp.collection())
                                            ? space.sjvm().V().limit(limit)
                                            : space.sjvm().E().limit(limit);
                                    return objs(IteratorUtil.stream(elements).map(e -> (e instanceof Vertex v) ? new VertexRec(v, space) : new EdgeRec((Edge) e, space)));
                                }
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's limit()-barrier for vrtx/edge collections"),

                        // Optimize: from(V/+).where([field=>value]) → g.V().has(field, predicate)
                        docWrap(CommonRewrites.whereRewrite(
                                grphSpace.class,
                                GRPH_REWRITE_TID.extend("gremlin_where").dom(GRPH_SPACE_TID).rng(VRTX_TID.maybeSome()),
                                (space, dp, filterClause) -> {
                                    if (!"V".equals(dp.collection()))
                                        throw MTronException.of("where-rewrite only supports the vertex (V) collection: %s", dp.collection());
                                    final org.apache.tinkerpop.gremlin.process.traversal.P<?> pred = parseGremlinPredicate(filterClause);
                                    final String field = extractPredicateField(filterClause);
                                    if (pred == null || field == null)
                                        throw MTronException.of("unable to parse where-predicate: %s", filterClause);
                                    return objs(IteratorUtil.stream(space.sjvm().V().has(field, pred)).map(v -> new VertexRec(v, space)));
                                }, GremlinRewriteUtils.PREDICATE_JOINER,
                                GremlinRewriteUtils.CONDITION_FORMATTER
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's has()-filtering for vrtx collections"),

                        // Optimize: gremlin_where.count() → g.V().has(field, pred).count().next()
                        // Uses direct rewriter to match by TID substring (bypasses ?dom/?rng query-param mismatch)
                        docWrap(
                                InstSet.Helper.rewriter(
                                        GRPH_REWRITE_TID.extend("gremlin_where_count"),
                                        code -> code.selfJVM(
                                                Rewriter.search(code.insts())
                                                        .match(List.of(instB(ALL, lst()), instB(COUNT_INST_TID, lst())))
                                                        .rewrite(map -> {
                                                            final java.util.List<Inst> matchList = new java.util.ArrayList<>(map.values());
                                                            final Inst whereInst = matchList.get(0);
                                                            // only compose if the first instruction is a gremlin_where
                                                            if (!whereInst.tid().toString().contains("gremlin_where"))
                                                                return matchList.stream().map(Obj::asInst).toList();
                                                            final Obj args = whereInst.args();
                                                            if (!args.isLst() || args.asLst().count() < 2)
                                                                return matchList.stream().map(Obj::asInst).toList();
                                                            final fURI furi = args.asLst().at(0).asUri().uriValue();
                                                            final String filterClause = args.asLst().at(1).asStr().jvm();
                                                            final studio.phaseshift.metatron.isa.Space space = Router.global().getSpaceFor(furi);
                                                            if (!(space instanceof grphSpace gs))
                                                                return matchList.stream().map(Obj::asInst).toList();
                                                            //final DataPath dp = DataPath.withoutDB(furi);
                                                            final org.apache.tinkerpop.gremlin.process.traversal.P<?> pred = parseGremlinPredicate(filterClause);
                                                            final String field = extractPredicateField(filterClause);
                                                            if (pred == null || field == null)
                                                                return matchList.stream().map(Obj::asInst).toList();
                                                            final long count = gs.sjvm().V().has(field, pred).count().next();
                                                            return List.of(instC(
                                                                    GRPH_REWRITE_TID.extend("gremlin_where_count").dom(ALL_STAR).rng(INT_TID),
                                                                    lst(uri(furi), studio.phaseshift.metatron.isa.m.type.impl.MStr.str(filterClause)),
                                                                    (lhs, inst) -> jnt(count)));
                                                        })
                                        ).asCode()),
                                "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's has().count() filtering-reduction for vrtx collections"),

                        // Optimize: gremlin_where.take(n) → g.V().has(field, pred).limit(n)
                        docWrap(
                                InstSet.Helper.rewriter(
                                        GRPH_REWRITE_TID.extend("gremlin_where_limit"),
                                        code -> code.selfJVM(
                                                Rewriter.search(code.insts())
                                                        .match(List.of(instB(ALL, lst()), instB(TAKE_INST_TID, lst())))
                                                        .rewrite(map -> {
                                                            final java.util.List<Inst> matchList = new java.util.ArrayList<>(map.values());
                                                            final Inst whereInst = matchList.get(0);
                                                            final Inst takeInst = matchList.get(1);
                                                            if (!whereInst.tid().toString().contains("gremlin_where"))
                                                                return matchList.stream().map(Obj::asInst).toList();
                                                            final Obj args = whereInst.args();
                                                            if (!args.isLst() || args.asLst().count() < 2)
                                                                return matchList.stream().map(Obj::asInst).toList();
                                                            final fURI furi = args.asLst().at(0).asUri().uriValue();
                                                            final String filterClause = args.asLst().at(1).asStr().jvm();
                                                            final long limit = takeInst.arg(0).asInt().jvm();
                                                            final studio.phaseshift.metatron.isa.Space space = Router.global().getSpaceFor(furi);
                                                            if (!(space instanceof grphSpace gs))
                                                                return matchList.stream().map(Obj::asInst).toList();
                                                            final DataPath dp = DataPath.withoutDB(furi);
                                                            final org.apache.tinkerpop.gremlin.process.traversal.P<?> pred = parseGremlinPredicate(filterClause);
                                                            final String field = extractPredicateField(filterClause);
                                                            if (pred == null || field == null)
                                                                return matchList.stream().map(Obj::asInst).toList();
                                                            return List.of(instC(
                                                                    GRPH_REWRITE_TID.extend("gremlin_where_limit").dom(ALL_STAR).rng(ALL_STAR),
                                                                    lst(uri(furi), studio.phaseshift.metatron.isa.m.type.impl.MStr.str(filterClause), jnt(limit)),
                                                                    (lhs, inst) -> objs(IteratorUtil.stream(
                                                                            gs.sjvm().V().has(field, pred).limit(limit)).map(v -> new VertexRec((Vertex) v, gs)))));
                                                        })
                                        ).asCode()),
                                "pre-rewrite code", "post-rewrite code", Map.of(), "leverages gremlin's has().limit() filtering-barrier for vrtx collections"),
                        InstSet.Helper.rewriter(GRPH_REWRITE_TID.extend("out_incident_adjacent"), code -> code.selfJVM(
                                Rewriter.search(code.insts())
                                        .match(List.of(instA(OUTE_INST_TID), instA(INV_INST_TID)))
                                        .rewrite(map -> List.of(instB(OUT_INST_TID, map.entrySet().iterator().next().getValue().args())))).asCode()),
                        InstSet.Helper.rewriter(GRPH_REWRITE_TID.extend("in_incident_adjacent"), code -> code.selfJVM(
                                Rewriter.search(code.insts())
                                        .match(List.of(instA(INE_INST_TID), instA(OUTV_INST_TID)))
                                        .rewrite(map -> List.of(instB(IN_INST_TID, map.entrySet().iterator().next().getValue().args())))).asCode()))));
        docWrap(this, "from vertex to vertex, the edge of the metatron is traversed");
        super.setup();
    }

    /**
     * Parse a SQL-like WHERE clause into a Gremlin P predicate.
     * Handles: "field > value", "field = value", "field < value",
     * "field >= value", "field <= value", "field <> value".
     */
    private static org.apache.tinkerpop.gremlin.process.traversal.P<?> parseGremlinPredicate(final String whereClause) {
        if (whereClause == null || whereClause.isBlank()) return null;
        final String[] ops = {">=", "<=", "<>", ">", "<", "="};
        for (final String op : ops) {
            final int idx = whereClause.indexOf(op);
            if (idx > 0) {
                final String valueStr = whereClause.substring(idx + op.length()).trim();
                final Object value = parsePredicateValue(valueStr);
                return switch (op) {
                    case "=" -> org.apache.tinkerpop.gremlin.process.traversal.P.eq(value);
                    case ">" -> org.apache.tinkerpop.gremlin.process.traversal.P.gt((Comparable) value);
                    case "<" -> org.apache.tinkerpop.gremlin.process.traversal.P.lt((Comparable) value);
                    case ">=" -> org.apache.tinkerpop.gremlin.process.traversal.P.gte((Comparable) value);
                    case "<=" -> org.apache.tinkerpop.gremlin.process.traversal.P.lte((Comparable) value);
                    case "<>" -> org.apache.tinkerpop.gremlin.process.traversal.P.neq(value);
                    default -> null;
                };
            }
        }
        return null;
    }

    /**
     * Extract the field name from a SQL WHERE condition like "value > 5"
     */
    private static String extractPredicateField(final String whereClause) {
        if (whereClause == null || whereClause.isBlank()) return null;
        final String[] ops = {">=", "<=", "<>", ">", "<", "=", " IS NOT NULL"};
        for (final String op : ops) {
            final int idx = whereClause.indexOf(op);
            if (idx > 0) return whereClause.substring(0, idx).trim();
        }
        return null;
    }

    /**
     * Parse a value string: handle quoted strings, booleans, numbers
     */
    private static Object parsePredicateValue(final String valueStr) {
        if (valueStr.startsWith("'") && valueStr.endsWith("'"))
            return valueStr.substring(1, valueStr.length() - 1).replace("''", "'");
        if ("TRUE".equalsIgnoreCase(valueStr)) return true;
        if ("FALSE".equalsIgnoreCase(valueStr)) return false;
        try {
            return Integer.parseInt(valueStr);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Double.parseDouble(valueStr);
        } catch (NumberFormatException ignored) {
        }
        return valueStr;
    }
}
