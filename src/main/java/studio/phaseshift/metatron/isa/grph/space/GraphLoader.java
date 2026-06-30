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

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerFactory;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.LinkedHashMap;
import java.util.Map;
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

    private GraphLoader() {}

    /** Dataset key URIs — fully-qualified {@link org.apache.tinkerpop.gremlin.LoadGraphWith.GraphData} enum names. */
    public static final fURI MODERN   = f("org.apache.tinkerpop.gremlin.LoadGraphWith.GraphData.MODERN");
    public static final fURI GRATEFUL = f("org.apache.tinkerpop.gremlin.LoadGraphWith.GraphData.GRATEFUL");
    public static final fURI CLASSIC  = f("org.apache.tinkerpop.gremlin.LoadGraphWith.GraphData.CLASSIC");
    public static final fURI CREW     = f("org.apache.tinkerpop.gremlin.LoadGraphWith.GraphData.CREW");

    /**
     * Registered dataset loaders.
     * <p>
     * Each {@link Consumer} receives the {@link grphSpace} and is responsible
     * for wiping existing data, seeding vertices/edges with {@code mtron_id}
     * properties, and committing if the graph supports transactions.
     */
    public static final Map<fURI, Consumer<grphSpace>> LOADERS = new LinkedHashMap<>();

    static {
        LOADERS.put(MODERN,  loadFromTinkerGraph(TinkerFactory.createModern()));
        LOADERS.put(GRATEFUL, loadFromTinkerGraph(TinkerFactory.createGratefulDead()));
        LOADERS.put(CLASSIC, loadFromTinkerGraph(TinkerFactory.createClassic()));
        LOADERS.put(CREW, loadFromTinkerGraph(TinkerFactory.createTheCrew()));
        
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
            final org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource g = space.sjvm();

            if (g.getGraph().features().graph().supportsTransactions())
                g.tx().open();

            // Wipe existing data
            g.V().drop().hasNext();
            g.E().drop().hasNext();

            // Seed vertices
            template.traversal().V().forEachRemaining(v -> {
                g.addV(v.label()).property(MTRON_ID, v.id()).next();
                g.V().has(MTRON_ID, v.id()).forEachRemaining(v1 -> {
                    v.properties().forEachRemaining(p ->
                            g.V(v1).property(p.key(), p.value()).next());
                });
            });

            // Seed edges
            template.traversal().E().forEachRemaining(e -> {
                final Vertex outV = g.V().has(MTRON_ID, e.outVertex().id()).next();
                final Vertex inV = g.V().has(MTRON_ID, e.inVertex().id()).next();
                final Edge edge = g.V(outV.id()).addE(e.label()).to(__.V(inV.id())).next();
                g.E(edge.id()).property(MTRON_ID, e.id()).next();
                e.properties().forEachRemaining(p -> {
                    if (!"id".equals(p.key())) {
                        Object val = p.value();
                        if (val instanceof Float f) val = f.doubleValue();
                        g.E(edge.id()).property(p.key(), val).next();
                    }
                });
            });

            if (g.getGraph().features().graph().supportsTransactions())
                g.tx().commit();

            LOG.info("loaded %s dataset: %d vertices, %d edges",
                    template, g.V().count().next(), g.E().count().next());
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
