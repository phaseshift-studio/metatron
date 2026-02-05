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

package studio.phaseshift.metatron.isa.grph.space.tp3;

import org.apache.commons.configuration2.ConfigurationMap;
import org.apache.tinkerpop.gremlin.jsr223.DefaultGremlinScriptEngineManager;
import org.apache.tinkerpop.gremlin.jsr223.GremlinLangScriptEngineFactory;
import org.apache.tinkerpop.gremlin.jsr223.GremlinScriptEngine;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerFactory;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.grph.space.grphSpace;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.sys.type.Router;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.LOAD;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.GREMLIN_INST_TID;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.GRPH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.failure_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
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

    protected static final ObjFactory FACTORY = MObjFactory.of()
            .addExtension(Vertex.class, v -> VertexMap.vrtxRec(v))
            .addExtension(Edge.class, e -> EdgeMap.edgeRec(e));

    public static final fURI TP3_SPACE_TID = GRPH_ISA_TID.extend("space").extend("tp3");
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


    //  protected final Tuple.Pair<String, String> rewrite;
    protected final String vertexPrefix;
    protected final String edgePrefix;

    public static tp3Space of(final Rec config, final fURI vid) {
        Router.global().logger().debug("tp3 space config: %s", config);
        final TinkerGraph graph = TinkerGraph.open();
        if (config.has(LOAD)) {
            final fURI dataset = config.at(LOAD).uriValue();
            Graphitty.log(tp3Space.class).info("translating %s into grph space", config.at(LOAD));
            if (dataset.equals(f("modern")))
                TinkerFactory.generateModern(graph);
            else if (dataset.equals(f("grateful")))
                TinkerFactory.generateGratefulDead(graph);
            else if (dataset.equals(f("airroutes")))
                TinkerFactory.generateAirRoutes(graph);
            else
                throw MTronException.of("unknown dataset: %s", config.at(LOAD));
        }
        return new tp3Space(graph, config.jvm(), vid);
    }


    protected tp3Space(final Graph graph, final Map<Obj, Obj> config, final fURI vid) {
        super(graph, config, TP3_SPACE_TID, vid);
        LOG.debug("tp3 space: %s", this);
        final Rec tp3Config = rec();
        new ConfigurationMap(sjvm.configuration()).forEach((key, value) -> {
            try {
                tp3Config.put(uri(key.toString()), MObjFactory.of().create(value), MUTABLE);
            } catch (final Exception e) {
                LOG.warn("unable to encode %s:%s: %s", key, value, e);
            }
        });
        this.put(uri("native/config"), tp3Config, MUTABLE);
        this.put(uri("native/id"), rec(
                uri("vertex"), uri(this.sjvm.vertices().next().id().getClass().getSimpleName()),
                uri("edge"), uri(this.sjvm.edges().next().id().getClass().getSimpleName())), MUTABLE);
        this.vertexPrefix = this.pattern.retractPattern().extend("V/").toString();
        this.edgePrefix = this.pattern.retractPattern().extend("E/").toString();
        LOG.debug("tp3 prefixes: %s %s", this.vertexPrefix, this.edgePrefix);
    }

    @Override
    public Obj read(final fURI vid) {
        final String vidString = vid.toString();
        if (vidString.startsWith(this.vertexPrefix)) {
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
    }

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
                final Long id = Long.valueOf(suffix);
                try {
                    final Vertex vertex = IteratorUtil.stream(this.sjvm.vertices(id)).findFirst().orElseGet(() -> this.sjvm.addVertex(T.label, obj.tid().toString(), T.id, id));
                    LOG.info("writing vertex %s => %s", vid, vertex);
                    obj.asRec().elements().forEach(e -> vertex.property(e.jvm().get0().uriValue().toString(), MObjFactory.of().create(e.jvm().get1()).jvm()));
                    return VertexMap.vrtxRec(vertex);
                } catch (final Exception e) {
                    return obj;
                }
            } else {
                throw MTronException.of("unknown tp3 vid: %s", vid);
            }
        }
    }

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
                    return MObjFactory.of().create(object);
                } catch (Exception e) {
                    return fail(e);
                }
            }))));

        }
    }
}
