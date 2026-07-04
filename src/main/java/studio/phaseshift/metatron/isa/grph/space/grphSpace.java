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

 package studio.phaseshift.metatron.isa.grph.space;

 import org.apache.commons.configuration2.BaseConfiguration;
 import org.apache.commons.configuration2.Configuration;
 import org.apache.commons.configuration2.ConfigurationMap;
 import org.apache.commons.configuration2.PropertiesConfiguration;
 import org.apache.tinkerpop.gremlin.driver.Cluster;
 import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection;
 import org.apache.tinkerpop.gremlin.process.remote.traversal.RemoteTraversal;
 import org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource;
 import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
 import org.apache.tinkerpop.gremlin.structure.*;
 import org.apache.tinkerpop.gremlin.structure.io.binary.TypeSerializerRegistry;
 import org.apache.tinkerpop.gremlin.structure.util.GraphFactory;
 import org.apache.tinkerpop.gremlin.structure.util.GraphFactoryClass;
 import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerFactory;
 import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
 import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerIoRegistryV3;
 import org.apache.tinkerpop.gremlin.util.ser.GraphBinaryMessageSerializerV1;
 import org.janusgraph.graphdb.tinkerpop.JanusGraphIoRegistry;
 import studio.phaseshift.metatron.furi.DataPath;
 import studio.phaseshift.metatron.furi.QProc;
 import studio.phaseshift.metatron.furi.fURI;
 import studio.phaseshift.metatron.isa.AbstractSpace;
 import studio.phaseshift.metatron.isa.SchemaSpace;
 import studio.phaseshift.metatron.isa.Space;
 import studio.phaseshift.metatron.isa.grph.grphInstSet;
 import studio.phaseshift.metatron.isa.grph.io.ObjTP3Serializer;
 import studio.phaseshift.metatron.isa.grph.space.schema.modernSchema;
 import studio.phaseshift.metatron.isa.m.type.*;
 import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
 import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
 import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
 import studio.phaseshift.metatron.isa.mach.type.Router;
 import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
 import studio.phaseshift.metatron.util.CommonUtil;
 import studio.phaseshift.metatron.util.IteratorUtil;
 import studio.phaseshift.metatron.util.MTronException;

 import java.util.*;
 import java.util.function.BiFunction;
 import java.util.function.Function;
 import java.util.stream.Stream;

 import static studio.phaseshift.metatron.Tokens.*;
 import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
 import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
 import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ_PATTERN;
 import static studio.phaseshift.metatron.isa.grph.grphInstSet.EDGE_TYPE;
 import static studio.phaseshift.metatron.isa.grph.grphInstSet.VRTX_TYPE;
 import static studio.phaseshift.metatron.isa.m.mInstSet.*;
 import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.failure_;
 import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
 import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
 import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
 import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
 import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
 import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
 import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
 import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
 import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
 import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

 /*
  * @author Marko A. Rodriguez (http://markorodriguez.com)
  */
 public class grphSpace extends AbstractSpace<GraphTraversalSource> implements SchemaSpace {

     public static final ObjSerializer<String> SERIALIZER = ObjmtronSerializer.singleNoClip();
     protected static ObjFactory FACTORY = null;

     protected static final String AUTO_TX = "auto_tx";
     /**
      * Config key for loading a pre-defined toy dataset (e.g. {@code /tinkerpop/modern}).
      */
     public static final String DATASET = "dataset";

     /**
      * Tracked for proper cleanup of the remote JanusGraph connection.
      */
     private transient DriverRemoteConnection remoteConnection;

     /**
      * Whether the underlying graph supports user-supplied IDs.
      * Embedded graphs ({@code gremlin.graph}) support them; remote backends don't.
      */
     private final boolean supportsUserSuppliedIds;
     private final Class<?> vertexIdClass;
     private final Class<?> edgeIdClass;

     public boolean supportsUserSuppliedIds() {
         return this.supportsUserSuppliedIds;
     }

     public Class<?> vertexIdClass() {
         return this.vertexIdClass;
     }

     public Class<?> edgeIdClass() {
         return this.edgeIdClass;
     }

     public static final fURI GRPH_SPACE_TID = grphInstSet.GRPH_ISA_TID.extend(SPACE).extend("grphspace");
     public static final Type GRPH_SPACE_TYPE = Type.Builder.build()
             .tid(SPACE_TID)
             .vid(GRPH_SPACE_TID)
             .isaPredicate(rec(
                     uri(SCHEMA).maybe().asUri(), INSTSET_TYPE,
                     uri(CONFIG).maybe(), REC_TYPE))
             .constructor(
                     instC(INST_CTOR_TID.dom(ALL.maybe()).rng(GRPH_SPACE_TID),
                             lst(REC_TYPE),
                             (lhs, inst) -> {
                                 if (inst.arg(0).isFail())
                                     throw inst.arg(0).asFail().asException();
                                 return grphSpace.of(inst.arg(0).asRec(), inst.arg(0).vid());
                             })).create();

     public static grphSpace of(final Rec grph, final fURI vid) {
         final Rec configBase = grph.at(CONFIG).orElse(rec());
         final Rec configWithHosts = grph.at(HOST).isNoObj()
                 ? configBase
                 : configBase
                 .at("hosts", lst(grph.at(HOST).elements().map(e -> (Obj) uri(e.uriValue().host())).toList()))
                 .at("ports", lst(grph.at(HOST).elements().map(e -> (Obj) jnt(e.uriValue().port())).toList()));
         final Configuration graphConfig = toApacheConfiguration(configWithHosts);
         final GraphTraversalSource graphTraversalSource;
         final DriverRemoteConnection driverRemoteConnection;
         if (!configBase.at(uri(Graph.GRAPH)).isNoObj()) {
             final Graph graph = GraphFactory.open(graphConfig);
             graphTraversalSource = graph.traversal();
             driverRemoteConnection = null;
         } else if (graphConfig.containsKey("clusterConfiguration") || graphConfig.containsKey("clusterConfigurationFile")) {
             TypeSerializerRegistry typeSerializerRegistry = TypeSerializerRegistry.build()
                     .addRegistry(JanusGraphIoRegistry.instance())
                     .create();
             Cluster cluster = Cluster.build()
                     .addContactPoints(grph.at(HOST).elements().map(e -> e.uriValue().host()).toList().toArray(new String[1]))
                     .port(grph.at(HOST).uriValue().port() == -1 ? 8182 : grph.at(HOST).uriValue().port())
                     .serializer(new GraphBinaryMessageSerializerV1(typeSerializerRegistry)).create();
             driverRemoteConnection = DriverRemoteConnection.using(cluster);
             graphTraversalSource = AnonymousTraversalSource.traversal().with(driverRemoteConnection);
         } else {
             driverRemoteConnection = null;
             graphTraversalSource = TinkerGraph.open().traversal();
         }
         final boolean supportsUserIds = driverRemoteConnection == null;
         final grphSpace space = new grphSpace(graphTraversalSource, grph.jvm(), vid, supportsUserIds);
         space.remoteConnection = driverRemoteConnection;

         // Load a pre-defined toy dataset if configured
         grph.at(uri(DATASET)).ifPresent(datasetUri -> {
             final java.util.function.Consumer<grphSpace> loader = GraphLoader.get(datasetUri.uriValue());
             if (loader != null) {
                 loader.accept(space);
             } else {
                 Graphitty.log(grphSpace.class).warn("unknown dataset key: %s", datasetUri);
             }
         });
         return space;
     }

     /**
      * Converts metatron rec configuration to Apache Commons Configuration.
      * Returns empty config when no connection properties are provided,
      *
      * @param config metatron configuration rec
      * @return Apache Commons Configuration for Cluster.open() or Settings.from()
      */
     private static Configuration toApacheConfiguration(final Rec config) {
         final BaseConfiguration apacheConfig = new BaseConfiguration();
         config.logger().info("processing configuration: %s", config);
         boolean isLocal = !config.at(uri(Graph.GRAPH)).isNoObj();
         if (!config.isEmpty()) {
             if (!isLocal)
                 apacheConfig.setProperty("clusterConfiguration", ".");
             config.elements().forEach(rel -> {
                 final String key = Str.Helper.cleanString(rel.first());
                 final Object value = rel.second().isLst() ?
                         rel.second().elements().map(Str.Helper::cleanString).toList() :
                         Str.Helper.cleanString(rel.second());
                 // Route cluster-connection properties into inline YAML so
                 // TinkerPop 3.8+ Cluster.open() parses them with correct types
                 // (hosts as list, port as int) rather than as flat strings.
                 if (!isLocal) {
                     config.logger().info("clusterConfiguration.%s => %s", key, value);
                     apacheConfig.setProperty("clusterConfiguration." + key, value);
                 }
                 apacheConfig.setProperty(key, value);
             });
         } else {
             config.logger().info("no remote config provided — defaulting to local TinkerGraph");
         }
         return apacheConfig;
     }

     /**
      * Loads sample datasets if supported by the graph implementation.
      * currently supports TinkerGraph datasets: modern, grateful, air_routes.
      *
      * @param graph  graph instance
      * @param config metatron configuration record
      */
     /**
      * Last-created grphSpace — used by {@link #from(Element)}.
      */
     private static grphSpace lastCreated;

     /**
      * Return the last created grphSpace. Used by the serializer.
      */
     public static grphSpace from(final Element element) {
         if (lastCreated == null)
             throw MTronException.of("No grphSpace created yet");
         return lastCreated;
     }

     protected fURI elementVID(final Element element) {
         return element instanceof Vertex ?
                 Space.Helper.routeToSpace(f("V/" + element.id().toString()), this.routes()) :
                 Space.Helper.routeToSpace(f("E/" + element.id().toString()), this.routes());
     }

     protected fURI schemaVID(final String label) {
         // Get the schema route value (e.g., /m/grph/schema/modern), not the route key (/g/S)
         return this.at(ROUTE).asRec().elements().filter(e -> e.first().uriValue().toString().endsWith("S")).findFirst().get().second().uriValue().extend(label);
     }

     protected ExistingGraphSchema existingGraphSchema;
     protected grphIncrQ grphIncrQ;

     protected grphSpace(final GraphTraversalSource graph, final Map<Obj, Obj> config, final fURI vid,
                         final boolean supportsUserSuppliedIds) {
         super(graph, config, GRPH_SPACE_TID, vid);
         this.supportsUserSuppliedIds = supportsUserSuppliedIds;
         if (supportsUserSuppliedIds) {
             final Optional<Object> vId = IteratorUtil.findFirst(graph.V()).map(v -> v.id());
             final Optional<Object> eId = IteratorUtil.findFirst(graph.E()).map(e -> e.id());
             this.vertexIdClass = vId.map(Object::getClass).orElse((Class) Integer.class);
             this.edgeIdClass = eId.map(Object::getClass).orElse((Class) Integer.class);
         } else {
             this.vertexIdClass = Long.class;
             this.edgeIdClass = Long.class;
         }
         lastCreated = this;
         //this.graphClass = MTronException.wrap(() -> (Class<GraphFactory>) Class.forName(Str.Helper.cleanString(this.at("config/gremlin/graph"))));
         //LOG.info("connected to %s", this.graphClass);
         // graph.configuration().setProperty(GRAPH_CONFIGURATION_KEY, vid.toString());
         // ── schema discovery ──
         this.existingGraphSchema = new ExistingGraphSchema(this);
         if (null == FACTORY) {
             LOG.warn("no obj factory specified. defaulting to an extended mobjfactory that assumes 64-bit long element ids");
             FACTORY = MObjFactory.of()
                     .addExtension(Vertex.class, v -> new VertexRec(v, this))
                     .addExtension(Edge.class, e -> new EdgeRec(e, this));
         }
         if (!this.at(f(CONFIG).extend(DATASET)).isNoObj()) {
             GraphLoader.get(this.at(f(CONFIG).extend(DATASET)).uriValue()).accept(this);
         }

         // ── schema discovery — only when no schema is provided in config ──
         // (avoids full-graph label scan on large existing databases)
         if (this.at(uri(SCHEMA)).isNoObj()) {
             this.existingGraphSchema.initialize(graph);
             this.existingGraphSchema.publishDiscoveredLabels();
         }

         // Snapshot + re-add QProcs through addQ() so the incrQ interceptor
         // can replace the default in-memory counter with grphIncrQ.
         // Avoids mutating the config's QProc list in place (immutable danger).
         final Lst qprocs = this.at(uri(QPROC)).orElse(lst()).asLst();
         if (!qprocs.isEmpty()) {
             final List<QProc> snapshot = new ArrayList<>(qprocs.<QProc>elements().toList());
             this.at(uri(QPROC), lst(), MUTABLE);
             for (final QProc q : snapshot)
                 this.addQ(q);
         }
         LOG.debug("initialized {{g}}auto-increment{{X}} support");

         // final Rec tp3Config = rec();
        /* new ConfigurationMap(sjvm.getGraph().configuration()).forEach((key, value) -> {
             try {
                 tp3Config.at(uri(key.toString()), MObjFactory.of().toObj(value), MUTABLE);
             } catch (final Exception e) {
                 LOG.warn("unable to encode %s:%s: %s", key, value, e);
             }
         });*/
         /*this.at(uri(NATIVE), rec(
                 uri("factory"), FACTORY,
                 uri(CONFIG), tp3Config,
                 uri("id"), rec(
                         uri(VERTEX), uri(IteratorUtil.findFirst(this.sjvm.V()).map(i -> i.id().getClass().getSimpleName()).orElse("unknown")),
                         uri(EDGE), uri(IteratorUtil.findFirst(this.sjvm.E()).map(i -> i.id().getClass().getSimpleName()).orElse("unknown")))), MUTABLE);*/
     }

     // =========================================================================
     //  I/O — readStream / writeStream (new API)
     // =========================================================================

     private static Object coerceId(final Object entry) {
         if (entry instanceof Number)
             return ((Number) entry).intValue();
         return CommonUtil.isInt(entry.toString()) ? Integer.parseInt(entry.toString()) : entry;
     }

     private Iterator<IdObj> readVertexTraversal(final DataPath dp) {
         final Iterator<Vertex> vertices = dp.entryIsWildcard()
                 ? this.sjvm.V() : this.sjvm.V(coerceId(dp.entry()));
         return IteratorUtil.stream(vertices).flatMap(v -> traverseVertex(v, dp)).iterator();
     }

     private Stream<IdObj> traverseVertex(final Vertex v, final DataPath dp) {
         final Direction dir = "OUT".equalsIgnoreCase(dp.field()) ? Direction.OUT : Direction.IN;
         final String firstExt = dp.extension() != null && dp.extension().segmentLength() > 0
                 ? dp.extension().segments().getFirst() : null;
         final boolean firstExtIsDirection = "IN".equalsIgnoreCase(firstExt) || "OUT".equalsIgnoreCase(firstExt);
         final boolean isExternal = firstExt != null && firstExt.indexOf(':') > 0;
         final boolean hasLabel = firstExt != null && !firstExt.equals("+") && !firstExt.equals("#") && !firstExtIsDirection && !isExternal;

         // ── external space traversal: scheme-prefixed label → Router ──
         if (isExternal) {
             final fURI externalBase = f(firstExt.indexOf("://") > 0 ? firstExt : "/" + firstExt.replace(':', '/'))
                     .extend(String.valueOf(dp.entry()));
             final List<String> remainder = dp.extension().segmentLength() > 1
                     ? dp.extension().segments().subList(1, dp.extension().segmentLength())
                     : List.of();
             // first remainder segment is the child key (if not a wildcard), skip in cascade
             final String childKey = remainder.isEmpty() || "+".equals(remainder.get(0)) || "#".equals(remainder.get(0))
                     ? "+" : remainder.get(0);
             final List<String> cascadeSegs = remainder.size() <= 1 || "+".equals(remainder.get(0)) || "#".equals(remainder.get(0))
                     ? List.of() : remainder.subList(1, remainder.size());
             final fURI exactPattern = externalBase.extend(childKey);
             final Obj readResult = Router.readFromSpace(exactPattern);
             final List<IdObj> readResults = readResult.isObjs()
                     ? IteratorUtil.stream(readResult.objsValue().iterator())
                     .map(o -> IdObj.of(o.vid() != null ? o.vid() : exactPattern, o)).toList()
                     : readResult.isNoObj() ? List.of()
                       : List.of(IdObj.of(exactPattern, readResult));
             return readResults.stream()
                     .flatMap(kv -> {
                         Obj result = kv.obj();
                         for (final String seg : cascadeSegs) {
                             if ("+".equals(seg)) continue;
                             if ("#".equals(seg)) break;
                             result = result.asRec().at(uri(seg));
                         }
                         return result.stream().map(o -> IdObj.of(kv.furi(), o));
                     });
         }

         // ── remote traversal ── (Vertex.edges() returns empty on DetachedVertex)
         final Iterator<Edge> edges;
         if (hasLabel) {
             edges = dir == Direction.OUT
                     ? this.sjvm.V(v.id()).outE(firstExt)
                     : this.sjvm.V(v.id()).inE(firstExt);
         } else {
             edges = dir == Direction.OUT
                     ? this.sjvm.V(v.id()).outE()
                     : this.sjvm.V(v.id()).inE();
         }
         final List<String> cascade = dp.extension() != null
                 ? dp.extension().segments().subList(hasLabel ? 1 : 0, dp.extension().segmentLength())
                 : List.of();
         return IteratorUtil.stream(edges).flatMap(e -> {
             Stream<Obj> stream = Stream.of(new EdgeRec(e, this));
             for (final String seg : cascade) {
                 if ("+".equals(seg)) continue;
                 stream = stream.flatMap(o -> o.asRec().at(uri(seg)).stream());
                 if ("#".equals(seg)) break;
             }
             return stream.map(o -> IdObj.of(this.elementVID(e), o));
         });
     }

     private Iterator<IdObj> readEdgeTraversal(final DataPath dp) {
         final Edge e = IteratorUtil.stream(this.sjvm.E(coerceId(dp.entry()))).findFirst().orElse(null);
         if (e == null) return IteratorUtil.of();
         final Vertex bare = "OUT".equalsIgnoreCase(dp.field()) ? e.outVertex() : e.inVertex();
         // Re-fetch full vertex — edge endpoints from remote traversals are ReferenceVertex refs
         final Vertex target = IteratorUtil.stream(this.sjvm.V(bare.id())).findFirst().orElse(bare);
         Stream<Obj> stream = Stream.of(new VertexRec(target, this));
         if (dp.hasExtension())
             for (final String seg : dp.extension().segments()) {
                 if ("+".equals(seg)) continue;
                 if ("#".equals(seg)) break;
                 stream = stream.flatMap(o -> o.asRec().at(uri(seg)).stream());
             }
         return stream.map(o -> IdObj.of(this.elementVID(target), o)).iterator();
     }


     private Iterator<IdObj> readCollection(final DataPath dp) {
         if (!dp.hasCollection()) return IteratorUtil.of();

         // -- shared SchemaSpace collection-level resolution --
         // Resolves exact label names (e.g. "person", "knows") against the
         // schema InstSet at SCHEMA/INSTSET.  For wildcards returns all types.
         final Iterator<IdObj> schemaResults =
                 resolveCollectionSchema(dp.collection());
         if (schemaResults.hasNext())
             return schemaResults;

         // -- V / E meta-collection fallback --
         // "V" and "E" aren't type names -- they're element-type categories.
         // Filter the schema InstSet by vertex / edge type refinement.
         final InstSet instSet = this.schema();
         if (instSet.types().isEmpty())
             return IteratorUtil.of();
         return readCollectionFromInstSet(instSet, dp);
     }

     /**
      * Filter an {@link InstSet} by element-type category ({@code V} / {@code E}).
      * Extracted so both the wired schema and Router-fallback paths share logic.
      */
     private Iterator<IdObj> readCollectionFromInstSet(final InstSet instSet, final DataPath dp) {
         final boolean wildcard = dp.collectionIsWildcard();
         if (wildcard) {
             return Stream.concat(
                             instSet.types().stream().filter(t -> t.isRefinementOf(VRTX_TYPE)),
                             instSet.types().stream().filter(t -> t.isRefinementOf(EDGE_TYPE)))
                     .map(t -> IdObj.of(t.vid(), t)).iterator();
         }
         if ("V".equals(dp.collection()))
             return instSet.types().stream().filter(t -> t.isRefinementOf(VRTX_TYPE))
                     .map(t -> IdObj.of(t.vid(), t)).iterator();
         if ("E".equals(dp.collection()))
             return instSet.types().stream().filter(t -> t.isRefinementOf(EDGE_TYPE))
                     .map(t -> IdObj.of(t.vid(), t)).iterator();
         return IteratorUtil.of();
     }

     /**
      * Returns {@code true} when the given collection name is a known vertex
      * label in the schema InstSet — i.e. a label-level collection like
      * {@code g:person} rather than the meta-collections {@code V} / {@code E}.
      */
     private boolean isVertexLabel(final String collectionName) {
         return this.schema().types().stream()
                 .anyMatch(t -> t.vid().name().equalsIgnoreCase(collectionName)
                         && t.isRefinementOf(VRTX_TYPE));
     }

     @Override
     public Space addQ(final QProc qProc) {
         // Intercept incrQ: replace the default in-memory counter with
         // grphIncrQ that lets JanusGraph auto-assign element IDs.
         final QProc toAdd = qProc.pattern().equals(INCRQ_PATTERN)
                 ? (this.grphIncrQ = new grphIncrQ(this)) : qProc;
         final Obj key = uri(QPROC);
         if (this.at(key).isNoObj())
             this.at(key, lst(), MUTABLE);
         this.at(key).asLst().add(toAdd, MUTABLE);
         return this;
     }

     @Override
     public Stream<IdObj> readStream(final fURI pattern) {
         return IteratorUtil.stream(directReader().apply(pattern));
     }

     @Override
     public Stream<IdObj> writeStream(final fURI pattern, final Obj obj) {
         directWriter().apply(pattern, obj);
         if (obj.isNoObj()) return Stream.empty();
         return Stream.of(IdObj.of(pattern, obj));
     }

     // =========================================================================
     //  I/O — directReader / directWriter (delegated by *Stream)
     // =========================================================================

     @Override
     public Function<fURI, Iterator<IdObj>> directReader() {
         return (pattern) -> {
             LOG.debug("looking for tp3 vid: %s", pattern);
             if (pattern.equals(ALL)) {
                 return Stream.concat(
                                 IteratorUtil.stream(this.sjvm().V()),
                                 IteratorUtil.stream(this.sjvm().E()))
                         .map(e -> IdObj.of(this.elementVID(e), e instanceof Vertex v ?
                                 new VertexRec(v, this) :
                                 new EdgeRec((Edge) e, this))).iterator();
             } else {
                 final fURI routed = Space.Helper.routeFromSpace(pattern, this.routes());

                 LOG.debug("reading tp3 vid: %s => %s", pattern, routed);
                 if (routed.hasScheme() && !routed.test(this.pattern())) {
                     return new IdObj(routed, Router.global().read(routed)).iterator();
                 }
                 final DataPath dp = routed.segments().get(0).equals(this.pattern().segments().get(0)) ? DataPath.of(routed) : DataPath.withoutDB(routed);
                 if (!dp.hasCollection()) return IteratorUtil.of();
                 if (!dp.hasEntry()) return readCollection(dp);
                 if ("V".equals(dp.collection())) {
                     // ── OUT/IN traversal — route through graph, not Rec ──
                     if (dp.hasField() && ("OUT".equalsIgnoreCase(dp.field()) || "IN".equalsIgnoreCase(dp.field()))) {
                         return readVertexTraversal(dp);
                     }
                     Iterator<Vertex> iterator;
                     if (!dp.entryIsWildcard())
                         iterator = this.sjvm.V(coerceId(dp.entry()));
                     else if (dp.entryIsWildcard())
                         iterator = this.sjvm.V();
                     else return readCollection(dp);
                     return IteratorUtil.stream(iterator)
                             .map(v -> IdObj.of(this.elementVID(v), new VertexRec(v, this)))
                             .map(idobj -> {
                                 if (dp.hasField()) {
                                     return IdObj.of(idobj.furi().extend(f(dp.field()).extend(dp.extension())), idobj.obj().asRec().at(f(dp.field()).extend(dp.extension())));
                                 } else {
                                     return idobj;
                                 }
                             }).iterator();
                 } else if ("E".equals(dp.collection())) {
                     // ── OUT/IN traversal on edge — route to endpoint vertex ──
                     if (dp.hasField() && ("OUT".equalsIgnoreCase(dp.field()) || "IN".equalsIgnoreCase(dp.field()))) {
                         return readEdgeTraversal(dp);
                     }
                     Iterator<Edge> iterator;
                     if (!dp.entryIsWildcard())
                         iterator = this.sjvm.E(coerceId(dp.entry()));
                     else if (dp.entryIsWildcard())
                         iterator = this.sjvm.E();
                     else return readCollection(dp);
                     return (Iterator) IteratorUtil.stream(iterator)
                             .map(e -> IdObj.of(this.elementVID(e), new EdgeRec(e, this)))
                             .map(idobj -> {
                                 if (dp.hasField()) {
                                     return IdObj.of(idobj.furi().extend(f(dp.field()).extend(dp.extension())), idobj.obj().asRec().at(f(dp.field()).extend(dp.extension())));
                                 } else {
                                     return idobj;
                                 }
                             })
                             .iterator();
                 } else {
                     // ── label-level collection (e.g. g:person, g:knows) ──
                     // Determine element type from the schema InstSet.
                     final Type labelType = this.schema().types().stream()
                             .filter(t -> t.vid().name().equalsIgnoreCase(dp.collection()))
                             .findFirst().orElse(null);
                     if (labelType != null && labelType.isRefinementOf(VRTX_TYPE)) {
                         if (dp.hasField() && ("OUT".equalsIgnoreCase(dp.field()) || "IN".equalsIgnoreCase(dp.field()))) {
                             return readVertexTraversal(dp);
                         }
                         final Iterator<Vertex> vIter = dp.entryIsWildcard()
                                 ? this.sjvm.V().hasLabel(dp.collection())
                                 : this.sjvm.V(coerceId(dp.entry()));
                         return IteratorUtil.stream(vIter)
                                 .map(v -> IdObj.of(this.elementVID(v), new VertexRec(v, this)))
                                 .map(idobj -> {
                                     if (dp.hasField()) {
                                         return IdObj.of(idobj.furi().extend(f(dp.field()).extend(dp.extension())),
                                                 idobj.obj().asRec().at(f(dp.field()).extend(dp.extension())));
                                     } else {
                                         return idobj;
                                     }
                                 }).iterator();
                     } else if (labelType != null && labelType.isRefinementOf(EDGE_TYPE)) {
                         if (dp.hasField() && ("OUT".equalsIgnoreCase(dp.field()) || "IN".equalsIgnoreCase(dp.field()))) {
                             return readEdgeTraversal(dp);
                         }
                         final Iterator<Edge> eIter = dp.entryIsWildcard()
                                 ? this.sjvm.E().hasLabel(dp.collection())
                                 : this.sjvm.E(coerceId(dp.entry()));
                         return (Iterator) IteratorUtil.stream(eIter)
                                 .map(e -> IdObj.of(this.elementVID(e), new EdgeRec(e, this)))
                                 .map(idobj -> {
                                     if (dp.hasField()) {
                                         return IdObj.of(idobj.furi().extend(f(dp.field()).extend(dp.extension())),
                                                 idobj.obj().asRec().at(f(dp.field()).extend(dp.extension())));
                                     } else {
                                         return idobj;
                                     }
                                 }).iterator();
                     }
                 }
                 LOG.debug("unknown tp3 vid: %s", pattern);
                 final fURI full = Space.Helper.routeFromSpace(pattern, this.routes());
                 if (full.equals(pattern)) return readCollection(dp);
                 return IdObj.of(full, Router.global().read(full)).iterator();
             }
         };
     }

     @Override
     public BiFunction<fURI, Obj, Obj> directWriter() {
         return (pattern, obj) -> {
             // DELETING A VERTEX OR EDGE
             if (obj.isNoObj()) {
                 this.read(pattern).stream().forEach(e -> {
                     LOG.debug("deleting element %s", e.vid());
                     if (e instanceof VertexRec vertexToDrop) {
                         this.sjvm.V(vertexToDrop.element().id()).drop().hasNext();
                     } else if (e instanceof EdgeRec edgeToDrop) {
                         this.sjvm.E(edgeToDrop.element().id()).drop().hasNext();
                     }
                 });
                 return noobj();
             }
             final fURI routed = Space.Helper.routeFromSpace(pattern, this.routes());
             LOG.debug("writing tp3 vid: %s => %s", pattern, routed);
             final DataPath dp = DataPath.withoutDB(routed);
             final boolean isVertexCollection = "V".equals(dp.collection())
                     || (dp.hasCollection() && isVertexLabel(dp.collection()));
             if (isVertexCollection && dp.hasEntry() && CommonUtil.isInt(dp.entry())) {
                 final Object id = CommonUtil.isInt(dp.entry()) ? Long.parseLong(dp.entry()) : dp.entry();
                 final String label = "V".equals(dp.collection())
                         ? (obj.isRec() && obj.asRec().jvm().containsKey(grphInstSet.LABEL)
                            ? obj.asRec().jvm().get(grphInstSet.LABEL).uriValue().toString()
                            : obj.tid().basePath().toString())
                         : dp.collection();
                 try {
                     final Vertex vertex = IteratorUtil.stream(this.sjvm.V(id)).findFirst().orElseGet(() ->
                             this.sjvm.addV(label).next());
                     LOG.debug("writing vertex %s => %s", vid, vertex);
                     // write properties from the Rec to the TinkerPop vertex
                     obj.asRec().jvm().entrySet().stream()
                             .filter(e -> !e.getKey().equals(grphInstSet.LABEL))
                             .forEach(e -> {
                                 final Obj value = e.getValue();
                                 if (value.isNoObj() || value.isNone()) {
                                     this.sjvm.V(vertex.id()).properties(e.getKey().uriValue().toString()).drop().hasNext();
                                 } else if (!value.isAuto()) {
                                     this.sjvm.V(vertex.id()).property(
                                             e.getKey().uriValue().toString(),
                                             ObjTP3Serializer.tp3Value(value)).next();
                                 }
                             });
                     // Track field types for schema update
                     if (obj.isRec()) {
                         final Map<String, Obj> existing =
                                 this.existingGraphSchema.getLogicalTypes()
                                         .get(label.toLowerCase());
                         final boolean isNewLabel = existing == null;
                         boolean hasNew = isNewLabel;
                         for (final Map.Entry<Obj, Obj> e : obj.asRec().jvm().entrySet()) {
                             if (e.getKey().equals(grphInstSet.LABEL)) continue;
                             final Obj value = e.getValue();
                             if (value.isNoObj() || value.isNone()
                                     || value.isAuto())
                                 continue;
                             final String propName = e.getKey().isUri()
                                     ? e.getKey().asUri().uriValue().name()
                                     : e.getKey().toString();
                             this.existingGraphSchema.trackPropertyType(
                                     label, propName, value);
                             if (!isNewLabel && !existing.containsKey(
                                     propName.toLowerCase()))
                                 hasNew = true;
                         }
                         if (hasNew)
                             this.existingGraphSchema.onLabelChanged(
                                     label, isNewLabel);
                     }
                     this.at(AUTO_TX).ifPresent(x -> {
                         if (x.boolValue()) {
                             this.sjvm.tx().commit();
                         }
                     });
                     return new VertexRec(vertex, this);
                 } catch (final Exception e) {
                     return fail(e);
                 }
             }
             return obj;
         };
     }

     @Override
     public void close() {
         try {
             this.sjvm().close();
         } catch (final Exception e) {
             LOG.error("error closing GraphTraversalSource", e);
         }
         try {
             if (this.remoteConnection != null) {
                 this.remoteConnection.close();
             }
         } catch (final Exception e) {
             LOG.error("error closing DriverRemoteConnection", e);
         }
         try {
             SchemaSpace.super.close();
         } finally {
             super.close();
         }
     }
 }
