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
 import org.apache.tinkerpop.gremlin.jsr223.DefaultGremlinScriptEngineManager;
 import org.apache.tinkerpop.gremlin.jsr223.GremlinLangScriptEngineFactory;
 import org.apache.tinkerpop.gremlin.jsr223.GremlinScriptEngine;
 import org.apache.tinkerpop.gremlin.structure.Edge;
 import org.apache.tinkerpop.gremlin.structure.Element;
 import org.apache.tinkerpop.gremlin.structure.Graph;
 import org.apache.tinkerpop.gremlin.structure.Vertex;
 import org.apache.tinkerpop.gremlin.structure.util.GraphFactory;
 import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerFactory;
 import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
 import studio.phaseshift.metatron.furi.fURI;
 import studio.phaseshift.metatron.isa.AbstractSpace;
 import studio.phaseshift.metatron.isa.Space;
 import studio.phaseshift.metatron.isa.grph.grphInstSet;
 import studio.phaseshift.metatron.isa.grph.space.schema.modernSchema;
 import studio.phaseshift.metatron.isa.m.mInstSet;
 import studio.phaseshift.metatron.isa.m.type.*;
 import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
 import studio.phaseshift.metatron.isa.mach.io.type.ObjCleanStringSerializer;
 import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
 import studio.phaseshift.metatron.isa.mach.type.Router;
 import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
 import studio.phaseshift.metatron.util.CommonUtil;
 import studio.phaseshift.metatron.util.IteratorUtil;
 import studio.phaseshift.metatron.util.MTronException;

 import java.util.*;
 import java.util.function.BiFunction;
 import java.util.function.Function;

 import static studio.phaseshift.metatron.Tokens.*;
 import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
 import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
 import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
 import static studio.phaseshift.metatron.isa.m.mInstSet.SPACE_TID;
 import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.failure_;
 import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
 import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
 import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
 import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
 import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
 import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
 import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
 import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
 import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

 /*
  * @author Marko A. Rodriguez (http://markorodriguez.com)
  */
 public class graphSpace extends AbstractSpace<Graph> {
     
     public static final String GRAPH_CONFIGURATION_KEY = "mtron.grph.vid";
     public static final ObjSerializer<String> SERIALIZER = new ObjCleanStringSerializer();
     public static final Rec GRAPH_CONFIG = rec(uri(GRAPH), URI_TYPE);

     protected static ObjFactory FACTORY = null;
     private static final fURI V_SOME = f("V/+");
     public static final fURI GRAPH_SPACE_TID = grphInstSet.GRPH_ISA_TID.extend(SPACE).extend("graph");
     public static final Type GRAPH_SPACE_TYPE = Type.Builder.build()
             .tid(SPACE_TID)
             .vid(GRAPH_SPACE_TID)
             .constructor(
                     instC(mInstSet.M_ISA_INST_TID.dom(ALL.maybe()).rng(GRAPH_SPACE_TID),
                             lst(isa_(GRAPH_CONFIG).else_(failure_(str("malformed tp3 config"))).tryToInst()),
                             (lhs, inst) -> {
                                 if (inst.arg(0).isFail())
                                     throw inst.arg(0).asFail().asException();
                                 return graphSpace.of(inst.arg(0).asRec(), inst.arg(0).vid());
                             })).create();

     public static graphSpace of(final Rec config, final fURI vid) {
         Router.global().logger().debug("tp3 space config: %s", config);
         final Configuration graphConfig = toApacheConfiguration(config);
         final Graph graph = GraphFactory.open(graphConfig);
         loadDatasetIfSpecified(graph, config); // only loads if supported and specified
         return new graphSpace(graph, config.jvm(), vid);
     }

     /**
      * Converts metatron rec configuration to Apache Commons Configuration.
      * Defaults to using TinkerGraph.
      *
      * @param config metatron configuration rec
      * @return Apache Commons Configuration for GraphFactory
      */
     private static Configuration toApacheConfiguration(final Rec config) {
         final BaseConfiguration apacheConfig = new BaseConfiguration();

         // Check for new-style GRAPH config (supports any TinkerPop3-compliant graph)
         if (config.has(uri(GRAPH))) {
             final Rec graphRec = config.at(uri(GRAPH)).asRec();
             graphRec.jvm().forEach((key, value) -> {
                 final String configKey = key.uriValue().toString();
                 final Object configValue = value.jvm();
                 apacheConfig.setProperty(configKey, configValue);
             });
         } else {
             // Legacy format - default to TinkerGraph for backward compatibility
             apacheConfig.setProperty(Graph.GRAPH, TinkerGraph.class.getCanonicalName());
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
     private static void loadDatasetIfSpecified(final Graph graph, final Rec config) {
         if (!config.has(NATIVE)) {
             return;
         }

         final Obj dataset = config.at(NATIVE).asRec().at(LOAD);
         if (dataset.isNoObj()) {
             return;
         }

         // Only TinkerGraph has the TinkerFactory datasets
         if (graph instanceof TinkerGraph) {
             final TinkerGraph tinkerGraph = (TinkerGraph) graph;
             final String datasetName = dataset.uriValue().toString();

             Graphitty.log(graphSpace.class).info("loading dataset %s into TinkerGraph", datasetName);

             switch (datasetName) {
                 case "modern" -> {
                     TinkerFactory.generateModern(tinkerGraph);
                     config.at(uri(SCHEMA),
                             new modernSchema(config.at(PATTERN).uriValue().head(1).extend("S").extend("modern")),
                             MUTABLE);
                 }
                 case "grateful" -> TinkerFactory.generateGratefulDead(tinkerGraph);
                 case "air_routes" -> TinkerFactory.generateAirRoutes(tinkerGraph);
                 default -> throw MTronException.of("unknown TinkerGraph dataset: %s", datasetName);
             }
         } else {
             Graphitty.log(graphSpace.class).warn(
                     "dataset loading requested but graph type %s does not support TinkerFactory datasets",
                     graph.getClass().getSimpleName());
         }
     }

     public static graphSpace from(final Element element) {
         return (graphSpace) Router.readFromSpace(f(element.graph().configuration().get(String.class, graphSpace.GRAPH_CONFIGURATION_KEY)));
     }

     protected fURI elementVID(final Element element) {
         return element instanceof Vertex ?
                 Space.Helper.routeToSpace(f("V/" + element.id().toString()), this.routes()) :
                 Space.Helper.routeToSpace(f("E/" + element.id().toString()), this.routes());
     }

     protected fURI schemaVID(final String label) {
         return this.at(ROUTE).asRec().elements().filter(e -> e.first().uriValue().toString().endsWith("S")).findFirst().get().first().uriValue().extend(label);
     }

     protected graphSpace(final Graph graph, final Map<Obj, Obj> config, final fURI vid) {
         super(graph, config, GRAPH_SPACE_TID, vid);
         LOG.debug("tp3 space: %s", this);
         graph.configuration().setProperty(GRAPH_CONFIGURATION_KEY, vid.toString());
         if (null == FACTORY)
             FACTORY = MObjFactory.of()
                     .addExtension(Vertex.class, v -> VertexMap.vertexToRec(v, this))
                     .addExtension(Edge.class, e -> EdgeMap.edgeToRec(e, this));
         final Rec tp3Config = rec();
         new ConfigurationMap(sjvm.configuration()).forEach((key, value) -> {
             try {
                 tp3Config.at(uri(key.toString()), MObjFactory.of().toObj(value), MUTABLE);
             } catch (final Exception e) {
                 LOG.warn("unable to encode %s:%s: %s", key, value, e);
             }
         });
         this.at(uri(NATIVE), rec(
                 uri("factory"), FACTORY,
                 uri(CONFIG), tp3Config,
                 uri("id"), rec(
                         uri(VERTEX), uri(IteratorUtil.findFirst(this.sjvm.vertices()).map(i -> i.id().getClass().getSimpleName()).orElse("unknown")),
                         uri(EDGE), uri(IteratorUtil.findFirst(this.sjvm.edges()).map(i -> i.id().getClass().getSimpleName()).orElse("unknown")))), MUTABLE);
     }

     @Override
     public Function<fURI, Iterator<IdObj>> directReader() {
         return (pattern) -> {
             LOG.debug("looking for tp3 vid: %s", pattern);
             if (pattern.equals(ALL)) {
                 throw MTronException.of("cannot read all tp3 space");
             } else {
                 final fURI routed = Space.Helper.routeFromSpace(pattern, this.routes()).asRelative();
                 LOG.info("reading tp3 vid: %s => %s", pattern, routed);
                 if (routed.hasScheme()) {
                     return new IdObj(routed, Router.global().read(routed)).iterator();
                 }
                 final String first = routed.segments(0, null);
                 if (null == first) return IteratorUtil.of();
                 final String second = routed.segments(1, null);
                 // LOG.info("parts: %s / %s",first,second);
                 final boolean all = "#".equals(second) || "+".equals(second);
                 ////////////////////////////////////////////////////////////////////
                 if (first.equals("V")) {
                     if (routed.segmentLength() > 2)
                         return IteratorUtil.of();
                     Iterator<Vertex> iterator;
                     if (CommonUtil.isInt(second)) iterator = this.sjvm.vertices(Integer.parseInt(second));
                     else if (all) iterator = this.sjvm.vertices();
                     else iterator = IteratorUtil.of();
                     return (Iterator) IteratorUtil.stream(iterator).map(v -> IdObj.of(this.elementVID(v), VertexMap.vertexToRec(v, this))).iterator();
                 } else if (first.equals("E")) {
                     if (routed.segmentLength() > 2)
                         return IteratorUtil.of();
                     Iterator<Edge> iterator;
                     if (CommonUtil.isInt(second)) iterator = this.sjvm.edges(Integer.parseInt(second));
                     else if (all) iterator = this.sjvm.edges();
                     else iterator = IteratorUtil.of();
                     return (Iterator) IteratorUtil.stream(iterator).map(e -> IdObj.of(this.elementVID(e), EdgeMap.edgeToRec(e, this))).iterator();
                 } else {
                     LOG.debug("unknown tp3 vid: %s", pattern);
                     final fURI full = Space.Helper.routeFromSpace(pattern, this.routes());
                     if (full.equals(pattern)) return IteratorUtil.of();
                     return IdObj.of(full, Router.global().read(full)).iterator();
                 }
             }
         };
     }

     @Override
     public BiFunction<fURI, Obj, Obj> directWriter() {
         return (pattern, obj) -> {
             if (obj.isNoObj()) {
                 this.read(pattern).stream().forEach(e -> {
                     LOG.info("deleting element %s", e.vid());
                     ((ElementMap) e.jvm()).getBase().remove();
                 });
                 return noobj();
             } else {
                 if (obj.jvm() instanceof ElementMap) // vertex already exists, all updates already occurred, no need to write it again
                     return obj;
                 final fURI routed = Space.Helper.routeFromSpace(pattern, this.routes());
                 LOG.info("writing tp3 vid: %s => %s", pattern, routed);
                 if (routed.test(V_SOME)) {
                     final Integer id = Integer.parseInt(routed.name());
                     try { //  a newly created vertex from a rec
                         final Vertex vertex = IteratorUtil.stream(this.sjvm.vertices(id)).findFirst().orElseGet(() ->
                                 this.sjvm.addVertex(
                                         org.apache.tinkerpop.gremlin.structure.T.label, obj.tid().basePath().toString(),
                                         org.apache.tinkerpop.gremlin.structure.T.id, id));
                         LOG.info("writing vertex %s => %s", vid, vertex);
                         /// SET VERTEX PROPERTIES
                         obj.asRec().jvm().entrySet().stream()
                                 .filter(e -> !e.getKey().equals(grphInstSet.IN) && !e.getValue().equals(grphInstSet.OUT))
                                 .forEach(e -> {
                                     LOG.info("writing vertex property %s =%s=> %s", vertex, e.getKey(), e.getValue());
                                     ElementMap.Helper.tp3KeyValue kv = new ElementMap.Helper.tp3KeyValue(e.getKey(), e.getValue());
                                     vertex.property((String) kv.key()).remove();
                                     if (!e.getValue().equals(uri("/noobj")) && !e.getValue().isNoObj())
                                         vertex.property((String) kv.key(), kv.value());
                                 });
                         /// SET VERTEX OUT EDGES
                         obj.asRec().elements().filter(e -> e.first().equals(grphInstSet.OUT))
                                 .forEach(label -> label.jvm().get1().asRec()
                                         .elements()
                                         .map(Rel::second)
                                         .forEach(e -> {
                                             LOG.info("writing edge %s =%s=> %s", vertex, label, e);
                                             try {
                                                 LOG.info("reading edge target %s", e);
                                                 final Edge edge = vertex.addEdge(label.jvm().get0().uriValue().toString(), ((VertexMap) this.read(e.uriValue()).jvm()).getBase());
                                                 LOG.info("writing edge %s", edge);
                                             } catch (final Exception ex) {
                                                 LOG.warn("unable to write edge %s =%s=> %s: %s", vertex, label, e, ex);
                                             }
                                         }));
                         return VertexMap.vertexToRec(vertex, this);
                     } catch (final Exception e) {
                         return fail(e);
                     }
                 } else {
                     return obj;
                     //   throw MTronException.of("unknown tp3 vid: %s", vid);
                 }
             }
         };
     }

  /*  @Override
    public Obj read(final fURI vid) {
        final String vidString = vid.toString();
        if (vidString.startsWith(this.schemaPrefix))
            return vid.isNode() ? this.schema : rel(uri(this.schemaPrefix), this.schema);
        else if (vidString.startsWith(this.vertexPrefix)) {
            final String suffix = vidString.replaceFirst(this.vertexPrefix, "");
            LOG.info("reading vertices %s => %s", vid, suffix);
            if (suffix.equals("+") || suffix.equals("#"))
                return objs(IteratorUtil.stream(this.sjvm.vertices()).map(VertexMap::vrtxRec));
            final Integer id = Integer.valueOf(vidString.replaceFirst(this.vertexPrefix, ""));
            LOG.debug("reading vertex %s => %s", vid, id);
            return objs(IteratorUtil.stream(this.sjvm.vertices(id)).map(VertexMap::vrtxRec));
        } else if (vidString.startsWith(this.edgePrefix)) {
            final String suffix = vidString.replaceFirst(this.edgePrefix, "");
            LOG.info("reading edges %s => %s", vid, suffix);
            if (suffix.equals("+") || suffix.equals("#"))
                return objs(IteratorUtil.stream(this.sjvm.edges()).map(EdgeMap::edgeRec));
            final Long id = Long.valueOf(vidString.replaceFirst(this.edgePrefix, ""));
            LOG.debug("reading edge %s => %s", vid, id);
            return objs(IteratorUtil.stream(this.sjvm.edges(id)).map(EdgeMap::edgeRec));
        } else {
            throw MTronException.of("unknown tp3 vid: %s", vid);
        }
    }*/
/*
    @Override
    public Obj write(final fURI vid, final Obj obj) {
        if (obj.isNoObj()) {
            this.read(vid).stream().forEach(e -> {
                LOG.info("deleting vertex %s", e.vid());
                ((ElementMap) e.jvm()).getBase().remove();
            });
            return noobj();
        } else {
            final String vidString = vid.toString();
            if (vidString.startsWith(this.vertexPrefix)) {
                final String suffix = vidString.replaceFirst(this.vertexPrefix, "");
                final long id = Long.parseLong(suffix);
                try {
                    final Vertex vertex = IteratorUtil.stream(this.sjvm.vertices(id)).findFirst().orElseGet(() -> this.sjvm.addVertex(T.label, obj.tid().basePath().toString(), T.id, id));
                    LOG.info("writing vertex %s => %s", vid, vertex);
                    obj.asRec().elements().forEach(e -> vertex.property(e.jvm().get0().uriValue().toString(), FACTORY.toObj(e.jvm().get1()).jvm()));
                    return VertexMap.vrtxRec(vertex);
                } catch (final Exception e) {
                    return obj;
                }
            } else {
                throw MTronException.of("unknown tp3 vid: %s", vid);
            }
        }
    }*/
 }
