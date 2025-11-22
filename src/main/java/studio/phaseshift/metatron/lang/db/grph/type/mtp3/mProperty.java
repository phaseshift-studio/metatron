package studio.phaseshift.metatron.lang.db.grph.type.mtp3;

import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedProperty;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Rel;

import java.util.NoSuchElementException;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mProperty<V> implements Property<V>, WrappedProperty<Rel> {

    protected mElement host;
    protected Rel property;

    public mProperty(final mElement host, final Rel property) {
        this.property = property;
        this.host = host;
    }

    public static <V> mProperty<V> of(final mElement host, final Rel rel) {
        return new mProperty<>(host, rel);
    }

    @Override
    public String key() {
        return this.property.first().uriValue().toString();
    }

    @Override
    public V value() throws NoSuchElementException {
        return this.property.second().jvm() instanceof Long ? (V)(Integer)((Long) this.property.second().jvm()).intValue() : this.property.second().jvm();
    }

    @Override
    public boolean isPresent() {
        return null != this.property && !property.isNoObj();
    }

    @Override
    public mElement element() {
        return this.host;
    }

    @Override
    public void remove() {

    }

    @Override
    public Rel getBaseProperty() {
        return this.property;
    }
}
