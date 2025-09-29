package studio.phaseshift.metatron.lang.obj.mgrph;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedVertex;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.Iterator;

public class MVertex extends MElement implements Obj, Vertex, WrappedVertex<Vertex> {

    protected MVertex(final Vertex vertex) {
        super(vertex);
    }

    public static MVertex of(final Vertex vertex) {
        return vertex instanceof MVertex ? (MVertex) vertex : new MVertex(vertex);
    }

    @Override
    public Vertex value() {
        return (Vertex) this.element;
    }

    @Override
    public Edge addEdge(String label, Vertex inVertex, Object... keyValues) {
        Router.global().write(this.vid().extend("outE"), new MVertex(inVertex));
        return null;
    }

    @Override
    public <V> VertexProperty<V> property(String key) {
        return Vertex.super.property(key);
    }

    @Override
    public <V> VertexProperty<V> property(String key, V value, Object... keyValues) {
        return Vertex.super.property(key, value, keyValues);
    }

    @Override
    public <V> VertexProperty<V> property(VertexProperty.Cardinality cardinality, String key, V value, Object... keyValues) {
        return null;
    }

    @Override
    public Iterator<Edge> edges(Direction direction, String... edgeLabels) {
        return null;
    }

    @Override
    public Iterator<Vertex> vertices(Direction direction, String... edgeLabels) {
        return MVertex.makeVertices(this.getBaseVertex().vertices(direction, edgeLabels));
    }

    @Override
    public <V> Iterator<VertexProperty<V>> properties(String... propertyKeys) {
        return null;
    }

    public static <E> Iterator<E> makeVertices(final Iterator<Vertex> vertices) {
        return (Iterator) IteratorUtil.map(vertices, MVertex::of);
    }

    @Override
    public Vertex getBaseVertex() {
        return this.value();
    }
}
