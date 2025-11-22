package studio.phaseshift.metatron.lang.db.grph.type.mtp3;

import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedVertexProperty;
import studio.phaseshift.metatron.lang.core.m.type.Rel;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.Iterator;
import java.util.NoSuchElementException;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mVertexProperty<V> implements VertexProperty<V>, WrappedVertexProperty<Rel> {

    protected final Rel property;
    protected final mVertex host;

    protected mVertexProperty(final mVertex host, final Rel property) {
        this.property = property;
        this.host = host;
    }

    @Override
    public String toString() {
        return StringFactory.propertyString(this);
    }

    @Override
    public int hashCode() {
        return ElementHelper.hashCode((Property) this);
    }

    @Override
    public boolean equals(final Object other) {
        return ElementHelper.areEqual(this, other);
    }

    @Override
    public String key() {
        return this.property.first().uriValue().toString();
    }

    @Override
    public V value() throws NoSuchElementException {
        if (this.property.isNoObj() || this.property.second().isNoObj())
            throw new NoSuchElementException();
        return  this.property.second().jvm() instanceof Long ? (V)(Integer)((Long) this.property.second().jvm()).intValue() : this.property.second().jvm();

    }

    @Override
    public boolean isPresent() {
        return !this.property.isNoObj();
    }

    @Override
    public Vertex element() {
        return this.host;
    }

    @Override
    public void remove() {

    }

    @Override
    public <U> Iterator<Property<U>> properties(final String... propertyKeys) {
        return IteratorUtil.of();
    }

    @Override
    public Object id() {
        return this.host.getBaseVertex().vid().extend("vp").extend("" + this.property.hashCode());
    }

    @Override
    public <V> Property<V> property(String key, V value) {
        return Property.empty();
    }

    @Override
    public Rel getBaseVertexProperty() {
        return this.property;
    }
}
