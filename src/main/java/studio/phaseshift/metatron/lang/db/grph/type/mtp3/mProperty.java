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

package studio.phaseshift.metatron.lang.db.grph.type.mtp3;

import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedProperty;
import studio.phaseshift.metatron.isa.m.type.Rel;

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
        return this.property.second().jvm() instanceof Long ? (V) (Integer) ((Long) this.property.second().jvm()).intValue() : this.property.second().jvm();
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
