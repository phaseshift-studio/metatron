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

package studio.phaseshift.metatron.lang.db.grph.type.tp;

import org.apache.commons.configuration2.Configuration;
import org.apache.tinkerpop.gremlin.process.computer.GraphComputer;
import org.apache.tinkerpop.gremlin.structure.*;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedGraph;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.db.grph.inst.grphInstSet;
import studio.phaseshift.metatron.lang.Space;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.MSpace;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

public class MGraph extends MSpace<Graph> implements Graph, WrappedGraph<Graph> {

    protected Graph graph;

    public MGraph(final Graph graph, final fURI pattern, final fURI vid) {
        super(graph, Map.of(uri("pattern"), uri(pattern)), pattern, grphInstSet.GRAPH_TID, vid);
        this.graph = graph;
        this.graph.configuration().addProperty("vid", vid);
        this.graph.configuration().addProperty("pattern", pattern);
    }

    public static MGraph of(final Graph graph, final fURI pattern, final fURI vid) {
        return new MGraph(graph, pattern, vid);
    }

    public static MGraph of(final Graph graph) {
        return new MGraph(graph, graph.configuration().get(fURI.class, "pattern"), graph.configuration().get(fURI.class, "vid"));
    }

    @Override
    public Vertex addVertex(final Object... keyValues) {
        return new MVertex(this.graph.addVertex(keyValues));
    }

    @Override
    public <C extends GraphComputer> C compute(Class<C> graphComputerClass) throws IllegalArgumentException {
        return null;
    }

    @Override
    public GraphComputer compute() throws IllegalArgumentException {
        return null;
    }

    @Override
    public Iterator<Vertex> vertices(final Object... vertexIds) {
        return MVertex.makeVertices(this.graph.vertices(vertexIds));
    }

    private Iterator<MVertex> mvertices(final fURI... vertexIds) {
        return (Iterator) this.vertices(Stream.of(vertexIds).map(id -> Long.valueOf(id.name())).toArray(Long[]::new));
    }

    @Override
    public Iterator<Edge> edges(final Object... edgeIds) {
        return MEdge.makeEdges(this.graph.edges(edgeIds));
    }

    private Iterator<MEdge> medges(final fURI... edgeIds) {
        return (Iterator) this.edges(Stream.of(edgeIds).map(id -> Long.valueOf(id.name())).toArray(Long[]::new));
    }


    @Override
    public Transaction tx() {
        return this.graph.tx();
    }

    @Override
    public Graph.Variables variables() {
        return this.graph.variables();
    }

    @Override
    public Configuration configuration() {
        return this.graph.configuration();
    }

    @Override
    public Obj read(final fURI vid) {
        return Space.Helper.resolveRead(this, vid, (key) -> {
            //if (key.tail(this.vid.extend("/vertex/#")))
            //    return IteratorUtil.stream(this.mvertices()).collect(Collectors.toMap(MVertex::vid, v -> v));
            //else {
            final Map<fURI, Obj> map = new HashMap<>();
            if (vid.bimatches(f("/+/vertex/+"))) {
                final String selector = vid.tail(1).asNode().asRelative().toString();
                if (selector.equals(fURI.ONE_WILD_STRING) || selector.equals(fURI.ALL_WILD_STRING)) {
                    IteratorUtil.stream(this.mvertices()).collect(Collectors.toMap(MVertex::vid, v -> v, Obj::append, () -> map));
                } else {
                    IteratorUtil.findFirst(this.mvertices(vid)).ifPresent(v -> map.put(v.vid(), v));
                }
            }
            if (vid.bimatches(f("/+/vertex/+/outE/+/+"))) {
                final List<String> selector = vid.select(f("/+/vertex/+/outE/+/+"));
                if (!f(selector.get(3)).hasPattern()) {
                    IteratorUtil.stream(this.edges(Long.parseLong(selector.get(3)))).map(e -> (MEdge) e).collect(Collectors.toMap(MEdge::vid, v -> v, (a, b) -> b, () -> map));
                } else if (selector.get(1).equals(fURI.ONE_WILD_STRING) || selector.get(1).equals(fURI.ALL_WILD_STRING)) {
                    if (selector.get(3).equals(fURI.ONE_WILD_STRING) || selector.get(3).equals(fURI.ALL_WILD_STRING)) {
                        IteratorUtil.stream(this.mvertices()).flatMap(v -> IteratorUtil.stream(v.edges(Direction.OUT))).map(e -> (MEdge) e).filter(e -> f(e.label()).matches(f(selector.get(2)))).collect(Collectors.toMap(MEdge::vid, v -> v, (a, b) -> b, () -> map));
                    }
                } else {
                    IteratorUtil.stream(this.mvertices(vid.head(3))).flatMap(v -> IteratorUtil.stream(v.edges(Direction.OUT))).map(e -> (MEdge) e).filter(e -> f(e.label()).matches(f(selector.get(2)))).collect(Collectors.toMap(MEdge::vid, v -> v, (a, b) -> b, () -> map));
                }
            }
            return map;
        });
    }

    @Override
    public Obj write(final fURI vid, Obj obj) {
        if (obj.is(MVertex.class)) {
            return MVertex.of(this.addVertex());
        } else if (obj.is(MEdge.class)) {
            return obj;
        } else {
            // write to space/configuration
        }
        return obj;
    }

    @Override
    public void close() {
        super.close();
        try {
            this.graph.close();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }


    @Override
    public Graph getBaseGraph() {
        return this.graph;
    }
}
