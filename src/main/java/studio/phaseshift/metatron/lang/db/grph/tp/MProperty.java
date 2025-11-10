/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC 
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

package studio.phaseshift.metatron.lang.db.grph.tp;

import org.apache.tinkerpop.gremlin.structure.*;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedProperty;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rel;
import studio.phaseshift.metatron.lang.core.m.type.impl.MObj;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.Iterator;

import static studio.phaseshift.metatron.lang.db.grph.grphInstSet.PROPERTY_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

public class MProperty<V> extends MObj implements Property<V>, WrappedProperty<Property<V>>, Rel {

    public MProperty(final Property<V> property, final fURI tid) {
        super(property, tid, fURI.NULL);
    }

    public static <V> MProperty<V> of(final Property<V> property) {
        return new MProperty<>(property, PROPERTY_TID);
    }

    public static <E, V> Iterator<E> makeProperties(final Iterator<Property<V>> properties) {
        return (Iterator) IteratorUtil.map(properties, MProperty::of);
    }

    public static <V> Iterator<V> makeValues(final Iterator<Property<V>> properties) {
        return IteratorUtil.map(MProperty.<Property<V>, V>makeProperties(properties), Property::value);
    }

    @Override
    public Tuple.Pair<Obj,Obj> jvm() {
        return Tuple.Pair.with(uri(this.getBaseProperty().key()), (Obj) this.value());
    }

    @Override
    public V value() {
        final V value = this.getBaseProperty().value();
        if (value instanceof Obj)
            return value;
        else if (value instanceof String)
            return (V) str((String) value);
        else if (value instanceof Long || value instanceof Integer)
            return (V) jnt(Long.valueOf(value.toString()));
        else if (value instanceof Float || value instanceof Double)
            return (V) real(Double.valueOf(value.toString()));
        else
            throw MTronException.of(new UnsupportedOperationException("value type not supported: " + value.getClass()));
    }

    @Override
    public String key() {
        return this.getBaseProperty().key();
    }

    @Override
    public boolean isPresent() {
        return this.getBaseProperty().isPresent();
    }

    @Override
    public Element element() {
        final Element e = this.getBaseProperty().element();
        return e instanceof Vertex ? MVertex.of((Vertex) e) : (e instanceof Edge ? MEdge.of((Edge) e) : MVertexProperty.of((VertexProperty) e));

    }

    @Override
    public void remove() {
        this.getBaseProperty().remove();
    }

    @Override
    public MProperty clone(Object jvm, fURI tid, fURI vid) {
        return (MProperty) super.clone();
    }

    @Override
    public Property<V> getBaseProperty() {
        return (Property<V>) this.jvm;
    }
}
