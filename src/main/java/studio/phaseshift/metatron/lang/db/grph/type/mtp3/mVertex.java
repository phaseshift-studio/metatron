package studio.phaseshift.metatron.lang.db.grph.type.mtp3;

import org.apache.tinkerpop.gremlin.structure.*;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedVertex;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.*;
import studio.phaseshift.metatron.lang.core.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.lang.core.m.type.impl.MObjs;
import studio.phaseshift.metatron.lang.core.m.type.impl.MType;
import studio.phaseshift.metatron.lang.db.grph.type.REdge;
import studio.phaseshift.metatron.lang.db.grph.type.RVertex;
import studio.phaseshift.metatron.lang.db.grph.type.TP3Translator;
import studio.phaseshift.metatron.lang.db.grph.type.tp.MVertexProperty;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.Iterator;

import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.start_;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.REC_TID;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.db.grph.type.mtp3.mGraph.PROPS;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mVertex extends mElement implements Vertex, WrappedVertex<RVertex> {

    public mVertex(final mGraph graph, final RVertex base) {
        super(graph, base);
    }

    @Override
    public String toString() {
        //  return StringFactory.vertexString(this);
        return this.getBaseVertex().base().toString();
    }

    @Override
    public int hashCode() {
        return ElementHelper.hashCode(this);
    }

    @Override
    public boolean equals(final Object other) {
        return ElementHelper.areEqual(this, other);
    }

    public static mVertex of(final mGraph graph, final RVertex r) {
        return new mVertex(graph, r);
    }

    @Override
    public Edge addEdge(final String label, final Vertex inVertex, final Object... keyValues) {
        final fURI edgeId = this.graph.makeEdgeID(ElementHelper.getIdValue(keyValues).orElseGet(() -> "e" + this.graph.counter++));
        final REdge re = REdge.of(label,
                this.getBaseVertex().vid(),
                ((mVertex) inVertex).getBaseVertex().vid(),
                keyValues).vid(edgeId).as();
        final Rec directedEdges = this.getBaseVertex().jvm().getOrDefault(uri(Direction.OUT.name()), rec()).as();
        final Obj labeledEdges = directedEdges.jvm().getOrDefault(uri(label), MObjs.empty()).as();
        this.getBaseVertex().jvm().put(uri(Direction.OUT.name()), directedEdges.put(uri(label), labeledEdges.append(re)));
        Router.writeToSpace(this.getBaseVertex());

        RVertex vertex2 = ((mVertex) inVertex).getBaseVertex();
        final Rec directedEdges2 = vertex2.jvm().getOrDefault(uri(Direction.IN.name()), rec()).as();
        final Obj labeledEdges2 = directedEdges2.jvm().getOrDefault(uri(label), MObjs.empty()).as();
        vertex2.jvm().put(uri(Direction.IN.name()), directedEdges2.put(uri(label), labeledEdges2.append(re.clone())));
        Router.writeToSpace(vertex2);

        return mEdge.of(this.graph, re);
    }

    @Override
    public <V> VertexProperty<V> property(final VertexProperty.Cardinality cardinality, final String key, final V value, final Object... keyValues) {
        final Uri uriKey = uri(key);
        Rel property = rel(uriKey, MObjFactory.of().create(value));
        Rec props = this.getBaseVertex().at(PROPS).orElse(rec());
        props.jvm().put(uriKey, MObjFactory.of().create(value));
        this.getBaseVertex().jvm().put(uri(PROPS), props);

        this.getBaseVertex().logger().info("HERE %s => %s ====> %s", key, value, Router.readFromSpace(this.getBaseVertex().vid()).<RVertex>as().properties(noobj()).toList());
        Router.writeToSpace(this.getBaseVertex());
        this.getBaseVertex().logger().info("XXX: %s", IteratorUtil.stream(this.properties()).toList());
        return new mVertexProperty<>(this, property);
    }

    @Override
    public <V> Iterator<VertexProperty<V>> properties(final String... propertyKeys) {
        return this.getBaseVertex().at(PROPS).elements().map(Obj::<Rel>as)
                .filter(r -> ElementHelper.keyExists(r.first().uriValue().toString(), propertyKeys))
                .map(r -> new mVertexProperty<V>(this, r))
                .map(vp -> (VertexProperty<V>) vp).iterator();
    }

    @Override
    public Iterator<Edge> edges(final Direction direction, final String... edgeLabels) {
        return this.getBaseVertex().edges(direction, stringToUriLabels(edgeLabels).as()).map(e -> mEdge.of(this.graph, e)).map(m -> (Edge) m).iterator();
    }

    @Override
    public Iterator<Vertex> vertices(Direction direction, String... edgeLabels) {
        return this.getBaseVertex().vertices(direction, stringToUriLabels(edgeLabels).as()).map(r -> mVertex.of(this.graph, r)).map(m -> (Vertex) m).iterator();
    }

    @Override
    public RVertex getBaseVertex() {
        return (RVertex) this.base;
    }
}
