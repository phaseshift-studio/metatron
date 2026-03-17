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

package studio.phaseshift.metatron.isa.grph.tp3.space;

import org.apache.commons.configuration2.ConfigurationMap;
import org.apache.tinkerpop.gremlin.jsr223.DefaultGremlinScriptEngineManager;
import org.apache.tinkerpop.gremlin.jsr223.GremlinLangScriptEngineFactory;
import org.apache.tinkerpop.gremlin.jsr223.GremlinScriptEngine;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerFactory;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.grph.space.grphSpace;
import studio.phaseshift.metatron.isa.grph.tp3.space.schema.modernSchema;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class tp3Space extends grphSpace<Graph> {

    public static final Uri NATIVE_LOAD = uri(f(NATIVE).extend(LOAD));
    public static final String TP3_GRAPH_CONFIGURATION_KEY = "mtron.grph.vid";

    protected static ObjFactory FACTORY = null;

    public static final fURI TP3_SPACE_TID = GRPH_ISA_TID.extend(SPACE).extend("tp3");
    public static final Type TP3_SPACE_TYPE = Type.Builder.build()
            .tid(GRPH_SPACE_TID)
            .vid(TP3_SPACE_TID)
            .constructor(
                    instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(TP3_SPACE_TID),
                            lst(isa_(GRPH_CONFIG).else_(failure_(str("malformed tp3 config"))).tryToInst()),
                            (lhs, inst) -> {
                                if (inst.arg(0).isFail())
                                    throw inst.arg(0).asFail().asException();
                                return tp3Space.of(inst.arg(0).asRec(), inst.arg(0).vid());
                            })).create();

    protected final String vertexPrefix;
    protected final String edgePrefix;
    protected final String schemaPrefix;
    protected final Obj schema;

    public static tp3Space of(final Rec config, final fURI vid) {
        Router.global().logger().debug("tp3 space config: %s", config);
        final TinkerGraph graph = TinkerGraph.open();
        if (config.has(NATIVE)) {
            final fURI dataset = config.at(NATIVE).asRec().at(LOAD).uriValue();
            Graphitty.log(tp3Space.class).info("translating %s into grph space", config.at(NATIVE_LOAD));
            if (dataset.equals(f("modern"))) {
                config.at(uri(SCHEMA), new modernSchema(config.at(PATTERN).uriValue().head(1).extend("S").extend("modern")), MUTABLE);
                TinkerFactory.generateModern(graph);
            } else if (dataset.equals(f("grateful")))
                TinkerFactory.generateGratefulDead(graph);
            else if (dataset.equals(f("air_routes")))
                TinkerFactory.generateAirRoutes(graph);
            else
                throw MTronException.of("unknown dataset: %s", config.at(NATIVE_LOAD));
        }
        return new tp3Space(graph, config.jvm(), vid);
    }

    public static tp3Space from(final Element element) {
        return (tp3Space) Router.readFromSpace(f(element.graph().configuration().get(String.class, tp3Space.TP3_GRAPH_CONFIGURATION_KEY)));
    }

    public fURI elementVID(final Element element) {
        return element instanceof Vertex ?
                f(this.vertexPrefix + "/" + element.id().toString()) :
                f(this.edgePrefix + "/" + element.id().toString());
    }

    protected tp3Space(final Graph graph, final Map<Obj, Obj> config, final fURI vid) {
        super(graph, config, TP3_SPACE_TID, vid);
        LOG.debug("tp3 space: %s", this);
        graph.configuration().setProperty(TP3_GRAPH_CONFIGURATION_KEY, vid.toString());
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
        // this.put(uri("native/factory"), FACTORY, MUTABLE);
        this.at(uri(NATIVE), rec(
                uri("factory"), FACTORY,
                uri(CONFIG), tp3Config,
                uri("id"), rec(
                        uri(VERTEX), uri(IteratorUtil.findFirst(this.sjvm.vertices()).map(i -> i.id().getClass().getSimpleName()).orElse("unknown")),
                        uri(EDGE), uri(IteratorUtil.findFirst(this.sjvm.edges()).map(i -> i.id().getClass().getSimpleName()).orElse("unknown")))), MUTABLE);
        this.vertexPrefix = this.pattern.retractPattern().extend("V").toString();
        this.edgePrefix = this.pattern.retractPattern().extend("E").toString();
        this.schemaPrefix = this.pattern.retractPattern().extend("S").toString();
        this.at(uri(ROUTE), rec(
                uri(VERTEX), uri(this.vertexPrefix),
                uri(EDGE), uri(this.edgePrefix),
                uri(SCHEMA), uri(this.schemaPrefix)), MUTABLE);
        LOG.debug("tp3 prefixes: %s %s %s", this.vertexPrefix, this.edgePrefix, this.schemaPrefix);
        this.schema = this.at(uri(SCHEMA));
    }

    @Override
    public Obj read(final fURI vid) {
        return studio.phaseshift.metatron.furi.Q.Helper.processPreRead(this.qs(), vid, vid).orElseGet(() -> {
            final Obj result = vid.hasPostfix("V/+") || vid.hasPostfix("V/#") ?
                    objs(IteratorUtil.stream(this.sjvm.vertices()).map(v -> VertexMap.vertexToRec(v, this))) :
                    Space.Helper.resolveRead(this, vid.basePath(), directReader());
            return studio.phaseshift.metatron.furi.Q.Helper.processPostRead(this.qs(), vid, vid, result).orElse(result);
        });
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return studio.phaseshift.metatron.furi.Q.Helper.processPreWrite(this.qs(), vid, vid, obj).orElseGet(() -> {
            if (obj.jvm() instanceof ElementMap) // underlying store has already updated the element accordingly
                return obj;
            Space.Helper.resolveWrite(LOG, this, vid.basePath(), obj, this.directWriter(), this.directReader());
            //return obj;
            return studio.phaseshift.metatron.furi.Q.Helper.processPostWrite(this.qs(), vid, vid, obj)
                    .orElse(studio.phaseshift.metatron.furi.Q.Helper.processQlessWrite(this.qs(), vid, vid, obj).orElse(obj));
        });
    }

    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        return (pattern) -> {
            LOG.debug("looking for tp3 vid: %s", pattern);
            if (pattern.equals(ALL)) {
                throw MTronException.of("cannot read all tp3 space");
            } else {
                if (f(this.schemaPrefix).test(pattern)) {
                    return IdObj.of(f(this.schemaPrefix), this.at(SCHEMA)).iterator();
                } else if (pattern.hasPrefix(this.schemaPrefix)) {
                    return IteratorUtil.of();
                } else if (pattern.segmentLength() < 3) {
                    return IteratorUtil.of();
                } else if (pattern.equals(f(this.vertexPrefix).extend("#"))) {
                    return (Iterator) IteratorUtil.stream(this.sjvm.vertices()).map(v -> IdObj.of(f(this.vertexPrefix).extend(v.id().toString()), VertexMap.vertexToRec(v, this))).iterator();
                } else if (pattern.test(f(this.vertexPrefix).extend("+"))) {
                    final String suffix = pattern.name();
                    LOG.info("reading vertices %s => %s", vid, suffix);
                    Iterator<Vertex> vertices = (suffix.equals("+") || suffix.equals("#")) ? this.sjvm.vertices() : this.sjvm.vertices(Integer.parseInt(suffix));
                    return IteratorUtil.map(vertices, v -> IdObj.of(f(this.vertexPrefix).extend(v.id().toString()), VertexMap.vertexToRec(v, this)));
                } else if (pattern.test(f(this.edgePrefix).extend("+"))) {
                    final String suffix = pattern.name();
                    LOG.info("reading edges %s => %s", vid, suffix);
                    Iterator<Edge> edges = (suffix.equals("+") || suffix.equals("#")) ? this.sjvm.edges() : this.sjvm.edges(Integer.parseInt(suffix));
                    return IteratorUtil.map(edges, e -> IdObj.of(f(this.edgePrefix).extend(e.id().toString()), EdgeMap.edgeToRec(e, this)));
                } else {
                    LOG.warn("unknown tp3 vid: %s", pattern);
                    return IteratorUtil.of();
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
                final String vidString = pattern.toString();
                if (vidString.startsWith(this.vertexPrefix)) {
                    final String suffix = vidString.replaceFirst(this.vertexPrefix + "/", "");
                    final Integer id = Integer.parseInt(suffix);
                    try { //  a newly created vertex from a rec
                        final Vertex vertex = IteratorUtil.stream(this.sjvm.vertices(id)).findFirst().orElseGet(() -> this.sjvm.addVertex(org.apache.tinkerpop.gremlin.structure.T.label, obj.tid().basePath().toString(), org.apache.tinkerpop.gremlin.structure.T.id, id));
                        LOG.info("writing vertex %s => %s", vid, vertex);
                        obj.asRec().elements()
                                .filter(e -> !e.first().equals(IN) && !e.first().equals(OUT))
                                .forEach(e -> {
                                    LOG.info("writing vertex property %s =%s=> %s", vertex, e.first(), e.second());
                                    vertex.property(e.jvm().get0().uriValue().toString(), FACTORY.toObj(e.jvm().get1()).jvm());
                                });
                        obj.asRec().elements().filter(e -> e.first().equals(OUT))
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

    public static class TP3SpaceType {
        public static Set<Inst> insts() {
            return new HashSet<>(List.of((instC(GREMLIN_INST_TID.dom(TP3_SPACE_TID).rng(ALL.maybeSome()), lst(STR_TYPE), (lhs, inst) -> {
                try {
                    final GremlinLangScriptEngineFactory factory = new GremlinLangScriptEngineFactory();
                    //factory.setCustomizerManager(new CachedGremlinScriptEngineManager());
                    factory.setCustomizerManager(new DefaultGremlinScriptEngineManager());
                    final GremlinScriptEngine engine = factory.getScriptEngine();
                    engine.put("g", ((tp3Space) lhs).sjvm().traversal());
                    final Object object = engine.eval(inst.arg(0).strValue());
                    return MObjFactory.of().toObj(object);
                } catch (Exception e) {
                    return fail(e);
                }
            }))));

        }
    }
}
