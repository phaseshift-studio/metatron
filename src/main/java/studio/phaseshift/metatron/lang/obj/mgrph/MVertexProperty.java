package studio.phaseshift.metatron.lang.obj.mgrph;

import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedVertexProperty;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.Iterator;

import static studio.phaseshift.metatron.lang.obj.mgrph.mgrphInstSet.VERTEX_PROPERTY_TID;


public class MVertexProperty<V> extends MProperty<V> implements VertexProperty<V>, WrappedVertexProperty<VertexProperty<V>> {

    public MVertexProperty(final VertexProperty<V> vertexProperty) {
        super(vertexProperty, VERTEX_PROPERTY_TID);
    }

    @Override
    public Vertex element() {
        return MVertex.of(this.getBaseVertexProperty().element());
    }

    public static <V> MVertexProperty<V> of(final VertexProperty<V> vertexProperty) {
        return new MVertexProperty<>(vertexProperty);
    }

    @Override
    public fURI vid() {
        return this.graph().configuration().get(fURI.class, "pattern").retractPattern().extend("vp").extend(this.getBaseVertexProperty().id().toString());
    }

    @Override
    public Object id() {
        return this.getBaseVertexProperty().id();
    }

    @Override
    public <V> Property<V> property(final String key, final V value) {
        return this.getBaseVertexProperty().property(key, value);
    }

    @Override
    public <U> Iterator<Property<U>> properties(final String... propertyKeys) {
        return MProperty.makeProperties(this.getBaseVertexProperty().<V>properties(propertyKeys));
    }

    public static <E, V> Iterator<E> makeVertexProperties(final Iterator<VertexProperty<V>> vertexProperties) {
        return (Iterator) IteratorUtil.map(vertexProperties, MVertexProperty::of);
    }

    @Override
    public VertexProperty<V> getBaseVertexProperty() {
        return (VertexProperty<V>) this.value;
    }
}
