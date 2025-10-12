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

package studio.phaseshift.metatron.lang.obj.mgrph.tp;

import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedVertexProperty;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.Iterator;

import static studio.phaseshift.metatron.lang.obj.mgrph.tp.mgrphInstSet.VERTEX_PROPERTY_TID;


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
