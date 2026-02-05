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

import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerFactory;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.grph.space.grphSpace;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.isa.sys.type.Router;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.GRPH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.failure_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class tp3Space extends grphSpace<Graph> {

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
        final Graph graph = TinkerFactory.createModern();
        return new tp3Space(graph, config.jvm(), vid);
    }

    protected tp3Space(final Graph graph, final Map<Obj, Obj> config, final fURI vid) {
        super(graph, config, TP3_SPACE_TID, vid);
        LOG.debug("tp3 space: %s", this);
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
            final Long id = Long.valueOf(vidString.replaceFirst(this.vertexPrefix, ""));
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
                    Router.global().logger().info("writing vertex %s => %s", vid, vertex);
                    obj.asRec().elements().forEach(e -> vertex.property(e.jvm().get0().uriValue().toString(), MObjFactory.of().create(e.jvm().get1()).jvm()));
                    return new VertexMap(vertex).selfRec();
                } catch (final Exception e) {
                    return obj;
                }
            } else {
                throw MTronException.of("unknown tp3 vid: %s", vid);
            }
        }
    }

/*
 if (this.has(LOAD)) {
            final fURI dataset = this.at(LOAD).uriValue();
            LOG.info("translating %s into grph space", this.at(LOAD));
            final TP3Translator t = TP3Translator.Builder.of(this.pattern.retractPattern()).create();
            if (dataset.equals(f("modern")))
                t.translate(TinkerFactory.createModern());
            else if (dataset.equals(f("grateful")))
                t.translate(TinkerFactory.createGratefulDead());
            else if (dataset.equals(f("airroutes")))
                t.translate(TinkerFactory.createAirRoutes());
            else
                throw MTronException.of("unknown dataset: %s", this.at(LOAD));
        }
 */
}
