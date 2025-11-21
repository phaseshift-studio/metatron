package studio.phaseshift.metatron.lang.db.grph.type.mtp3;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedVertex;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.impl.MObjs;
import studio.phaseshift.metatron.lang.db.grph.type.REdge;
import studio.phaseshift.metatron.lang.db.grph.type.RVertex;
import studio.phaseshift.metatron.lang.db.grph.type.TP3Translator;
import studio.phaseshift.metatron.lang.db.grph.type.tp.MVertexProperty;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.Iterator;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mVertex extends mElement implements Vertex, WrappedVertex<RVertex> {

    public mVertex(final RVertex base) {
        super(base);
    }

    public static mVertex of(final RVertex r) {
        return new mVertex(r);
    }

    @Override
    public Edge addEdge(String label, Vertex inVertex, Object... keyValues) {
        final mEdge edge = mEdge.of(REdge.of(label, this.base.vid(), ((mVertex) inVertex).base.vid(), keyValues));
        this.getBaseVertex().jvm().getOrDefault(uri(Direction.OUT.name()), rec()).<Rec>as().jvm().getOrDefault(uri(label), MObjs.empty()).append((Obj) edge);
        return edge;
    }

    @Override
    public <V> VertexProperty<V> property(final VertexProperty.Cardinality cardinality, final String key, final V value, final Object... keyValues) {
        return VertexProperty.empty();
    }

    @Override
    public <V> Iterator<VertexProperty<V>> properties(final String... propertyKeys) {
        return IteratorUtil.of();//this.getBaseVertex().<V>properties(stringToUriLabels(propertyKeys)).iterator();
    }

    @Override
    public Iterator<Edge> edges(final Direction direction, final String... edgeLabels) {
        return this.getBaseVertex().edges(direction, stringToUriLabels(edgeLabels).as()).map(mEdge::of).map(m -> (Edge) m).iterator();
    }

    @Override
    public Iterator<Vertex> vertices(Direction direction, String... edgeLabels) {
        return this.getBaseVertex().vertices(direction, stringToUriLabels(edgeLabels).as()).map(mVertex::of).map(m -> (Vertex) m).iterator();
    }

    @Override
    public RVertex getBaseVertex() {
        return (RVertex) this.base;
    }
}
