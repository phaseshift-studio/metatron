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

import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Graph;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.mtron.MObj;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Iterator;

public abstract class MElement extends MObj implements Element, Obj {

    protected MElement(final Element element, final fURI tid, final fURI vid) {
        super(element, tid, vid);

    }

    @Override
    public Object id() {
        return this.vid().hashCode();
    }

    @Override
    public String label() {
        return this.value().label();
    }

    @Override
    public Graph graph() {
        return MGraph.of(this.value().graph());
    }

    @Override
    public void remove() {
        this.value().remove();
    }

    @Override
    public Obj take() {
        final MElement r = (MElement) this.clone();
        this.remove();
        return r;
    }

    /*@Override
    public <V> Iterator<? extends Property<V>> properties(String... propertyKeys) {
        return this.element.properties(propertyKeys);
    }*/

    @Override
    public Element value() {
        return (Element) this.value;
    }

    @Override
    public MElement clone(final Object newValue, final fURI newtid, final fURI newvid) {
        return (MElement) super.clone(newValue, newtid, newvid);
    }

    @Override
    public <V> Iterator<V> values(final String... propertyKeys) {
        return MProperty.makeValues((Iterator) this.properties(propertyKeys));
    }

    @Override
    public Obj clone() {
        try {
            return (Obj) super.clone();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }
}
