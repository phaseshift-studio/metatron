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

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerFactory;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static studio.phaseshift.metatron.Tokens.MTRON_ID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * Loads TinkerPop toy datasets into a {@link grphSpace} via the
 * {@code GraphTraversalSource} API.
 * <p>
 * Each dataset is identified by a {@code fURI} key whose path
 * mirrors the dataset's class name (e.g. {@code /tinkerpop/modern}
 * for the "modern" graph).  The loader wipes any existing data
 * before seeding.
 * <p>
 * To add a new dataset, register a new entry in {@link #LOADERS}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class GraphLoader {

    private static final GraphittyLogger LOG = Graphitty.log(GraphLoader.class);

    private GraphLoader() {
    }

    /**
     * Dataset key URIs — fully-qualified {@link org.apache.tinkerpop.gremlin.LoadGraphWith.GraphData} enum names.
     */
    public static final fURI MODERN = f("org.apache.tinkerpop.gremlin.LoadGraphWith.GraphData.MODERN");
    public static final fURI GRATEFUL = f("org.apache.tinkerpop.gremlin.LoadGraphWith.GraphData.GRATEFUL");
    public static final fURI CLASSIC = f("org.apache.tinkerpop.gremlin.LoadGraphWith.GraphData.CLASSIC");
    public static final fURI CREW = f("org.apache.tinkerpop.gremlin.LoadGraphWith.GraphData.CREW");

    /**
     * Registered dataset loaders.
     * <p>
     * Each {@link Consumer} receives the {@link grphSpace} and is responsible
     * for wiping existing data, seeding vertices/edges with {@code mtron_id}
     * properties, and committing if the graph supports transactions.
     */
    public static final Map<fURI, Consumer<grphSpace>> LOADERS = new LinkedHashMap<>();

    static {
        LOADERS.put(MODERN, loadFromTinkerGraph(TinkerFactory.createModern()));
        LOADERS.put(GRATEFUL, loadFromTinkerGraph(TinkerFactory.createGratefulDead()));
        LOADERS.put(CLASSIC, loadFromTinkerGraph(TinkerFactory.createClassic()));
        LOADERS.put(CREW, loadFromTinkerGraph(TinkerFactory.createTheCrew()));

    }

    private static Object coerceId(final Object entry) {
        if (entry instanceof Number)
            return ((Number) entry).intValue();
        return CommonUtil.isInt(entry.toString()) ? Integer.parseInt(entry.toString()) : entry;
    }

    // ========================================================================
    //  Individual dataset loaders
    // ========================================================================


    /**
     * Build a loader from any in-memory {@link TinkerGraph}.
     * The provided graph is used as a template — its vertices and edges
     * are copied into the target space via the standard pattern:
     * {@code addV(label).property(MTRON_ID, id)} for vertices and
     * {@code addE(label).to(__.V(inId)).property(MTRON_ID, id)} for edges.
     */
    public static Consumer<grphSpace> loadFromTinkerGraph(final TinkerGraph template) {
        return space -> {
            try {
                final GraphTraversalSource g = space.sjvm();
                final boolean supportsUserIds = space.supportsUserSuppliedIds();
                LOG.info("[{{c}}*{{X}}] loading example graph data: %s [user_ids: %s]", template, supportsUserIds);
                if (g.getGraph().features().graph().supportsTransactions())
                    g.tx().open();
                // Wipe existing data
                final boolean hasV = g.V().drop().hasNext();
                final boolean hasE = g.E().drop().hasNext();
                LOG.info("[{{c}}*{{X}}] dropping current graph [hasV: %s, hasE: %s]", hasV, hasE);
                final Object V_ID_KEY = supportsUserIds ? T.id : MTRON_ID;
                final Object E_ID_KEY = supportsUserIds ? T.id : MTRON_ID;
                LOG.info("[{{c}}*{{X}}] using id keys [idV: %s, idE: %s]", V_ID_KEY, E_ID_KEY);
                // Seed vertices — use T.id for embedded graphs, MTRON_ID for remote
                final AtomicInteger counter = new AtomicInteger(0);
                template.traversal().V().forEachRemaining(v -> {
                    counter.incrementAndGet();
                    g.addV(v.label()).property(V_ID_KEY, coerceId(v.id())).next();
                    (supportsUserIds ? g.V().hasId(v.id()) : g.V().has(MTRON_ID, v.id())).forEachRemaining(v1 -> {
                        v.properties().forEachRemaining(p ->
                                g.V(v1).property(p.key(), p.value()).next());
                    });
                });
                LOG.info("[{{c}}*{{X}}] loaded vertices [count: %d]", counter.getAndSet(0));
                // Seed edges
                template.traversal().E().forEachRemaining(e -> {
                    counter.incrementAndGet();
                    final Vertex outV = (supportsUserIds ? g.V().hasId(e.outVertex().id()) : g.V().has(MTRON_ID, e.outVertex().id())).next();
                    final Vertex inV = (supportsUserIds ? g.V().hasId(e.inVertex().id()) : g.V().has(MTRON_ID, e.inVertex().id())).next();
                    final Edge edge = g.addE(e.label()).from(__.V(outV.id())).to(__.V(inV.id())).property(E_ID_KEY, coerceId(e.id())).next();
                    e.properties().forEachRemaining(p -> {
                        if (!T.id.getAccessor().equals(p.key())) {
                            Object val = p.value();
                            if (val instanceof Float f) val = f.doubleValue();
                            g.E(edge.id()).property(p.key(), val).next();
                        }
                    });
                });
                LOG.info("[{{c}}*{{X}}] loaded edges [count: %d]", counter.getAndSet(0));
                if (g.getGraph().features().graph().supportsTransactions())
                    g.tx().commit();
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        };
    }

    // ========================================================================
    //  Lookup
    // ========================================================================

    /**
     * Return the loader for the given dataset key, or {@code null} if not found.
     */
    public static Consumer<grphSpace> get(final fURI key) {
        return LOADERS.get(key);
    }
}
