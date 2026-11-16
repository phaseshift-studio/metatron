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

 package studio.phaseshift.metatron.isa.m.db.grph.space;

 import org.apache.commons.configuration2.BaseConfiguration;
 import org.apache.commons.configuration2.Configuration;
 import org.apache.tinkerpop.gremlin.driver.Cluster;
 import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection;
 import org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource;
 import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
 import org.apache.tinkerpop.gremlin.structure.*;
 import org.apache.tinkerpop.gremlin.structure.io.binary.TypeSerializerRegistry;
 import org.apache.tinkerpop.gremlin.structure.util.GraphFactory;
 import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
 import org.apache.tinkerpop.gremlin.util.ser.GraphBinaryMessageSerializerV1;
 import org.janusgraph.graphdb.tinkerpop.JanusGraphIoRegistry;
 import studio.phaseshift.metatron.furi.DataPath;
 import studio.phaseshift.metatron.furi.QProc;
 import studio.phaseshift.metatron.furi.fURI;
 import studio.phaseshift.metatron.isa.AbstractDataPathSpace;
 import studio.phaseshift.metatron.isa.SchemaSpace;
 import studio.phaseshift.metatron.isa.Space;
 import studio.phaseshift.metatron.isa.m.db.grph.grphInstSet;
 import studio.phaseshift.metatron.isa.m.db.grph.io.ObjTP3Serializer;
 import studio.phaseshift.metatron.isa.m.type.*;
 import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
 import studio.phaseshift.metatron.isa.m.web.type.ObjSerializer;
 import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
 import studio.phaseshift.metatron.isa.m.mach.type.Machine;
 import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
 import studio.phaseshift.metatron.util.CommonUtil;
 import studio.phaseshift.metatron.util.IteratorUtil;
 import studio.phaseshift.metatron.util.MTronException;
 import studio.phaseshift.metatron.isa.m.type.impl.TypeBuilder;

 import java.util.*;
 import java.util.function.BiFunction;
 import java.util.function.Function;
 import java.util.stream.Stream;

 import static studio.phaseshift.metatron.Tokens.*;
 import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
 import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
 import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ_PATTERN;
 import static studio.phaseshift.metatron.isa.m.mInstSet.INSTSET_TYPE;
 import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TYPE;
 import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
 import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
 import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
 import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
 import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

 /*
  * @author Marko A. Rodriguez (http://markorodriguez.com)
  */
 public class grphSpace extends AbstractDataPathSpace<GraphTraversalSource> implements SchemaSpace {

     public static final ObjSerializer<String> SERIALIZER = ObjmtronSerializer.single();
     protected static ObjFactory FACTORY = null;

     protected static final String AUTO_TX = "auto_tx";
     /**
      * Config key for loading a pre-defined toy dataset (e.g. {@code /tinkerpop/modern}).
      */
     public static final String DATASET = "dataset";

     /**
      * Reserved vertex label for the flat key-value namespace, mirroring
      * tbleSpace/dcmntSpace's {@code kv_store}.  URIs whose first segment is this
      * label are stored as {@code kv_store}-labeled vertices with a {@code key}
      * (full relative path) and a serialized {@code value} property.
      */
     public static final String KV_STORE = "kv_store";
     // Property names on the flat kv_store vertex. Namespaced to avoid JanusGraph's
     // protected namespace ("key"/"value") and to keep the serialized-string value
     // from colliding with a graph vertex's own "value" field (type-enforced globally).
     public static final String KV_KEY = "mtron_key";
     public static final String KV_VALUE = "mtron_value";

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
     public static final Type GRPH_SPACE_TYPE = TypeBuilder.build()
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
         try {
             // schema discovery and the dataset load run after the space has mounted: a failure must not leave it there
             return Space.Helper.construct(() -> {
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
                 SchemaSpace.Helper.mount(space);
                 return space;
             });
         } catch (final RuntimeException e) {
             try {
                 CommonUtil.close(graphTraversalSource);
                 CommonUtil.close(driverRemoteConnection);
             } catch (final RuntimeException closing) {
                 e.addSuppressed(closing);
             }
             throw e;
         }
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

     /**
      * THE TID AN ELEMENT CARRIES: its label's declared type, else the bare label. An inferred label's type is not the
      * element's own: >>= re-tags an element with its tid, and an inferred type there would refuse the very write that
      * widens it (see {@link SchemaSpace.Helper}).
      */
     protected fURI elementTypeVid(final String label) {
         final Type declared = SchemaSpace.Helper.declared(this, label);
         return null == declared ? f(label) : declared.vid();
     }

     /**
      * Whether a label names vertices, asked of the graph itself.
      */
     boolean isVertexLabel(final String label) {
         return this.sjvm.V().hasLabel(label).hasNext();
     }

     /**
      * Whether a label names edges, asked of the graph itself.
      */
     boolean isEdgeLabel(final String label) {
         return this.sjvm.E().hasLabel(label).hasNext();
     }

     @Override
     public Stream<String> collections() {
         return Stream.concat(IteratorUtil.stream(this.sjvm.V().label().dedup()), IteratorUtil.stream(this.sjvm.E().label().dedup()))
                 .filter(label -> !KV_STORE.equals(label))
                 .distinct();
     }

     /**
      * The properties of every element of a label, folded: the vertices that carry it, else the edges. A poly property
      * is stored under an {@code mtron:} key ({@link ElementMap}), and is the field its name says.
      */
     @Override
     public Rec fields(final String label) {
         final Iterator<? extends Element> elements = this.isVertexLabel(label)
                 ? this.sjvm.V().hasLabel(label) : this.sjvm.E().hasLabel(label);
         return SchemaSpace.Helper.fields(IteratorUtil.stream(elements).map(element -> {
             final Map<Obj, Obj> properties = new LinkedHashMap<>();
             new ElementMap<>(element).forEach((key, value) -> properties.put(uri(key.uriValue().name()), value));
             return rec(properties);
         }));
     }

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
         if (null == FACTORY) {
             LOG.warn("no obj factory specified. defaulting to an extended mobjfactory that assumes 64-bit long element ids");
             FACTORY = MObjFactory.of()
                     .addExtension(Vertex.class, v -> new VertexRec(v, this))
                     .addExtension(Edge.class, e -> new EdgeRec(e, this));
         }
         if (!this.at(f(CONFIG).extend(DATASET)).isNoObj()) {
             GraphLoader.get(this.at(f(CONFIG).extend(DATASET)).uriValue()).accept(this);
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

     /**
      * THE ID AN ENTRY NAMES, as reads and writes look a vertex up alike: the modern graph loads Integer ids, so a number
      * that fits an int is one, and a larger one (JanusGraph's) stays a Long. A Long lookup misses an Integer id, and the
      * write path made one: an update of {@code /g/V/1} added a vertex and left vertex 1 as it was.
      */
     private static Object coerceId(final Object entry) {
         if (!(entry instanceof Number) && !CommonUtil.isInt(entry.toString()))
             return entry;
         final long id = entry instanceof Number number ? number.longValue() : Long.parseLong(entry.toString());
         return id == (int) id ? (Object) Integer.valueOf((int) id) : (Object) Long.valueOf(id);
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
             final Obj readResult = Machine.read(exactPattern);
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

         // -- wildcard: the V / E meta-collections, not the label types --
         if (dp.collectionIsWildcard())
             return Stream.of(f("V"), f("E")).map(vid -> T(REC_TID, vid, null, null)).map(t -> IdObj.of(t.vid(), t)).iterator();

         // -- element-kind qualified collection: V:<label> / E:<label> --
         // e.g. /g/V:person resolves the person vertex label type; /g/E:knows the knows edge label type.
         final String collection = dp.collection();
         final int colon = collection.indexOf(':');
         if (colon > 0)
             return this.resolveCollectionSchema(collection.substring(colon + 1));

         // -- V / E meta-collections: the types of the vertex labels, of the edge labels --
         if ("V".equals(collection) || "E".equals(collection))
             return SchemaSpace.Helper.types(this)
                     .filter(t -> "V".equals(collection) ? this.isVertexLabel(t.vid().name()) : this.isEdgeLabel(t.vid().name()))
                     .map(t -> IdObj.of(t.vid(), t)).iterator();

         // -- a label: its declared or inferred type --
         return this.resolveCollectionSchema(collection);
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
                     return new IdObj(routed, Machine.current().memory().read(routed)).iterator();
                 }
                 // Flat key-value namespace (reserved kv_store label, or unknown collection).
                 // Only for paths under this space's prefix; absolute cross-space paths
                 // (e.g. /g/S → /m/db/grph/schema/... route) fall through to the fallback.
                 if (!routed.isAbsolute() || routed.hasPrefix(this.pattern().retractPattern())) {
                     final fURI rel = this.relative(routed);
                     if (this.isFlatRead(rel))
                         return this.readFlat(rel, pattern);
                 }
                 final DataPath dp = routed.segments().get(0).equals(this.pattern().segments().get(0)) ? DataPath.of(routed) : DataPath.withoutDB(routed);
                 // the field a read reaches into, with its extension when it has one: extend(null) threw, so */g/V/1/name failed
                 final fURI field = !dp.hasField() ? null : dp.hasExtension() ? f(dp.field()).extend(dp.extension()) : f(dp.field());
                 if (!dp.hasCollection()) return IteratorUtil.of();
                 if (!dp.hasEntry()) return readCollection(dp);
                 // /g/+/+ → every label type under the configured schema route
                 // (person, software, knows, created), unlike /g/+ which returns
                 // just the V/E meta-collections.
                 if (dp.collectionIsWildcard() && dp.entryIsWildcard()) {
                     final fURI schemaBase = SchemaSpace.Helper.schemaRoute(this);
                     if (schemaBase != null) {
                         final Space schemaSpace = Machine.current().memory().getSpaceFor(schemaBase);
                         if (schemaSpace instanceof InstSet isa)
                             return isa.types().stream()
                                     // only the schema's own label types, not the
                                     // schema type itself or instset/space infra types
                                     .filter(t -> t.vid() != null && t.vid().hasPrefix(schemaBase) && !t.vid().equals(schemaBase))
                                     .map(t -> IdObj.of(t.vid(), t)).iterator();
                     }
                     return IteratorUtil.of();
                 }
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
                                     return IdObj.of(idobj.furi().extend(field), idobj.obj().asRec().at(field));
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
                                     return IdObj.of(idobj.furi().extend(field), idobj.obj().asRec().at(field));
                                 } else {
                                     return idobj;
                                 }
                             })
                             .iterator();
                 } else {
                     // ── label-level collection (e.g. g:person, g:knows): the graph says whose label it is ──
                     if (this.isVertexLabel(dp.collection())) {
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
                                         return IdObj.of(idobj.furi().extend(field),
                                                 idobj.obj().asRec().at(field));
                                     } else {
                                         return idobj;
                                     }
                                 }).iterator();
                     } else if (this.isEdgeLabel(dp.collection())) {
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
                                         return IdObj.of(idobj.furi().extend(field),
                                                 idobj.obj().asRec().at(field));
                                     } else {
                                         return idobj;
                                     }
                                 }).iterator();
                     }
                 }
                 LOG.debug("unknown tp3 vid: %s", pattern);
                 final fURI full = Space.Helper.routeFromSpace(pattern, this.routes());
                 if (full.equals(pattern)) return readCollection(dp);
                 return IdObj.of(full, Machine.current().memory().read(full)).iterator();
             }
         };
     }

     @Override
     public BiFunction<fURI, Obj, Obj> directWriter() {
         return (pattern, obj) -> {
             // DELETING A VERTEX, EDGE, OR FLAT ENTRY
             if (obj.isNoObj()) {
                 final fURI rel = this.relative(Space.Helper.routeFromSpace(pattern, this.routes()));
                 if (this.isFlatRead(rel)) {
                     this.writeFlat(rel, noobj());
                     return noobj();
                 }
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
             final fURI rel = this.relative(routed);
             // a type written to a label's path declares the label (SchemaSpace.Helper)
             if (obj.isType() && 1 == rel.segments().size()) {
                 Machine.write(this.schema().vid().extend(rel.name()), obj);
                 return obj;
             }
             // Flat key-value namespace (reserved kv_store label, or deduced).
             if (this.isFlatWrite(rel, obj)) {
                 this.writeFlat(rel, obj);
                 return obj;
             }
             this.writeStructured(rel, obj);
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

     // =======================================================================
     //  AbstractDataPathSpace hooks — flat kv_store namespace + structured write
     // =======================================================================

     @Override
     protected boolean isReservedFlatName(final String firstSegment) {
         return KV_STORE.equalsIgnoreCase(firstSegment);
     }

     @Override
     protected boolean isStructuredCollection(final fURI relativePath) {
         final List<String> segs = relativePath.segments();
         if (segs == null || segs.isEmpty())
             return false;
         final String c = segs.getFirst();
         if ("V".equalsIgnoreCase(c) || "E".equalsIgnoreCase(c))
             return true;
         if ("+".equals(c) || "#".equals(c))
             return true; // wildcard collection → schema resolution (readCollection)
         // element-kind qualified collection: V:<label> / E:<label> → schema type read
         final int colon = c.indexOf(':');
         if (colon > 0) {
             final String kind = c.substring(0, colon);
             return "V".equalsIgnoreCase(kind) || "E".equalsIgnoreCase(kind);
         }
         return this.isVertexLabel(c) || this.isEdgeLabel(c);
     }

     /**
      * Normalize a routed path to the space-relative form.  {@code routeFromSpace}
      * strips the route key for mapped collections ({@code /g/V} → {@code V/1})
      * but leaves unmapped paths absolute ({@code /g/kv_store/x}); strip the
      * {@code /g/} prefix here so the flat namespace sees a consistent relative key.
      */
     private fURI relative(final fURI routed) {
         final fURI prefix = this.pattern().retractPattern();
         if (!routed.hasPrefix(prefix)) return routed;
         // Keep the leading '/' so a ':' in the first remaining segment (e.g.
         // V:person) is NOT re-parsed as a scheme separator.  removePrefix's
         // string form drops it, turning "V:person" into scheme "V" + "person".
         return f(routed.toString().substring(prefix.toString().length()));
     }

     @Override
     protected void writeFlat(final fURI relativePath, final Obj obj) {
         final String key = relativePath.toString();
         if (obj.isNoObj()) {
             this.sjvm.V().hasLabel(KV_STORE).has(KV_KEY, key).drop().toList();
             return;
         }
         final Vertex existing = IteratorUtil.stream(
                 this.sjvm.V().hasLabel(KV_STORE).has(KV_KEY, key)).findFirst().orElse(null);
         if (existing != null) {
             this.sjvm.V(existing.id()).property(KV_VALUE, SERIALIZER.write(obj)).toList();
         } else {
             this.sjvm.addV(KV_STORE).property(KV_KEY, key).property(KV_VALUE, SERIALIZER.write(obj)).next();
         }
     }

     @Override
     protected Iterator<IdObj> readFlat(final fURI relativePath, final fURI pattern) {
         final fURI nodePattern = pattern.asNode();
         if (relativePath.hasPattern()) {
             return IteratorUtil.stream(this.sjvm.V().hasLabel(KV_STORE)).flatMap(v -> {
                 final String key = v.value(KV_KEY).toString();
                 final fURI vid = this.pattern().retractPattern().extend(f(key));
                 final Obj val = SERIALIZER.read(v.value(KV_VALUE).toString());
                 final List<IdObj> out = new ArrayList<>();
                 if (vid.test(nodePattern))
                     out.add(IdObj.of(vid, val));
                 else if (val.isPoly())
                     out.addAll(Space.Helper.unrollPoly(vid, val.as(), nodePattern));
                 return out.stream();
             }).iterator();
         }
         final Vertex v = IteratorUtil.stream(
                 this.sjvm.V().hasLabel(KV_STORE).has(KV_KEY, relativePath.toString())).findFirst().orElse(null);
         if (v == null)
             return IteratorUtil.of();
         return IteratorUtil.of(IdObj.of(nodePattern, SERIALIZER.read(v.value(KV_VALUE).toString())));
     }

     @Override
     protected void writeStructured(final fURI relativePath, final Obj obj) {
         final DataPath dp = DataPath.withoutDB(relativePath);
         if (dp.collection() == null || dp.entry() == null)
             return;
         // Edges are created via addE (Phase 3); skip edge-label collections.
         if ("E".equals(dp.collection()) || this.isEdgeLabel(dp.collection()))
             return;
         if (!CommonUtil.isInt(dp.entry())) {
             // A rec at a non-integer entry is not a vertex id — park it in the
             // flat namespace so the >> spine walk can still descend into it.
             this.writeFlat(relativePath, obj);
             return;
         }
         if (!obj.isRec())
             throw MTronException.of("graph vertex requires a Rec; cannot promote %s", obj.type());
         final Vertex existing = IteratorUtil.stream(this.sjvm.V(coerceId(dp.entry()))).findFirst().orElse(null);
         // a vertex keeps its label; a new one takes its collection's, else the rec's LABEL, else its type's name
         final String label = null != existing ? existing.label()
                 : !"V".equals(dp.collection()) ? dp.collection()
                 : obj.asRec().jvm().containsKey(grphInstSet.LABEL) ? obj.asRec().jvm().get(grphInstSet.LABEL).uriValue().toString()
                 : obj.tid().name();
         // A WRITE MERGES INTO THE VERTEX: what its label's type is asked to hold is the vertex as the write leaves it
         final Map<Obj, Obj> record = new LinkedHashMap<>();
         if (null != existing)
             new ElementMap<>(existing).forEach((key, value) -> record.put(uri(key.uriValue().name()), value));
         obj.asRec().jvm().forEach((key, value) -> {
             if (value.isNoObj() || value.isNone())
                 record.remove(key);
             else if (!key.equals(grphInstSet.LABEL) && !value.isAuto())
                 record.put(key, value);
         });
         SchemaSpace.Helper.admit(this, label, rec(record));
         try {
             // Promote parked flat entries before the first vertex of a new label.
             if (null == existing && !this.isVertexLabel(label))
                 this.migrateFlatToStructured(label);
             final Vertex vertex = null != existing ? existing : this.sjvm.addV(label).next();
             obj.asRec().jvm().entrySet().stream()
                     .filter(e -> !e.getKey().equals(grphInstSet.LABEL))
                     .forEach(e -> {
                         final Obj value = e.getValue();
                         if (value.isNoObj() || value.isNone()) {
                             this.sjvm.V(vertex.id()).properties(e.getKey().uriValue().toString()).drop().toList();
                         } else if (!value.isAuto()) {
                             this.sjvm.V(vertex.id()).property(
                                     e.getKey().uriValue().toString(), ObjTP3Serializer.tp3Value(value)).next();
                         }
                     });
             this.at(AUTO_TX).ifPresent(x -> {
                 if (x.boolValue())
                     this.sjvm.tx().commit();
             });
         } catch (final Exception e) {
             throw MTronException.of(e);
         }
     }

     @Override
     protected Stream<FlatEntry> scanFlat(final String collectionPrefix) {
         final String prefix = collectionPrefix + "/";
         return IteratorUtil.stream(this.sjvm.V().hasLabel(KV_STORE)
                         .has(KV_KEY, org.apache.tinkerpop.gremlin.process.traversal.TextP.startingWith(prefix)))
                 .map(v -> new FlatEntry(v.value(KV_KEY).toString(),
                         SERIALIZER.read(v.value(KV_VALUE).toString())));
     }

     @Override
     protected void removeFlat(final String fullKey) {
         this.sjvm.V().hasLabel(KV_STORE).has(KV_KEY, fullKey).drop().toList();
     }
 }
