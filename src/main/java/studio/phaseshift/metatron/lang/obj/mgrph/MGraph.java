package studio.phaseshift.metatron.lang.obj.mgrph;

import org.apache.commons.configuration2.Configuration;
import org.apache.tinkerpop.gremlin.process.computer.GraphComputer;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Transaction;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedGraph;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.space.mem.MSpace;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.Iterator;
import java.util.Map;

public class MGraph extends MSpace implements Graph, WrappedGraph<Graph> {

    protected Graph graph;

    public MGraph(final Graph graph, final fURI pattern, final fURI vid) {
        super(pattern, GrphInstSet.GRAPH_TID, vid);
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
    public Iterator<Vertex> vertices(Object... vertexIds) {
        return MVertex.makeVertices(this.graph.vertices(vertexIds));
    }

    @Override
    public Iterator<Edge> edges(Object... edgeIds) {
        return MEdge.makeEdges(this.graph.edges(edgeIds));
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
    public Map<Vertex, Iterator<Edge>> value() {
        return Map.of();
    }

    @Override
    public Obj read(final fURI vid) {
        return ObjUtil.oneNoneOrAll(MVertex.makeVertices(this.vertices()));
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
    public void append(fURI addr, Obj... obj) {

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

    @Override
    public String toString() {
        return ObjUtil.objToString(this);
    }

    @Override
    public int hashCode() {
        return ObjUtil.objHashCode(this);
    }

    @Override
    public boolean equals(final Object other) {
        return ObjUtil.objEquals(this, other);
    }
}
