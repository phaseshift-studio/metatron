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

import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedElement;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.db.grph.type.RElement;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class mElement implements Element, WrappedElement<RElement> {

    protected final RElement base;
    protected final mGraph graph;


    protected Obj stringToUriLabels(final String[] labels) {
        final List<Obj> uris = new ArrayList<>();
        for (final String label : labels) {
            uris.add(uri(label));
        }
        return lst(uris);
    }

    /*protected String[] uriToStringLabels(final Uri[] labels) {
        final Uri[] uris = new Uri[labels.length];
        for (int i = 0; i < labels.length; i++) {
            uris[i] = uri(labels[i]);
        }
        return uris;
    }*/

    protected mElement(final mGraph graph, final RElement base) {
        this.base = base;
        this.graph = graph;
    }

    @Override
    public Object id() {
        return this.getBaseElement().vid();
    }

    @Override
    public String label() {
        return this.getBaseElement().label().toString();
    }

    @Override
    public Graph graph() {
        return this.graph;
    }

    @Override
    public void remove() {

    }

    //@Override
    // public <V> Iterator<? extends Property<V>> properties(final String... propertyKeys) {
    //  return this.getBaseElement().at(PROPS).elements().map(Obj::<Rel>as).filter(r -> ElementHelper.keyExists(r.first().toString(),propertyKeys)).map(r -> r.second()).map(Obj::<Rel>as).i;
    //}

    @Override
    public RElement getBaseElement() {
        return this.base;
    }
}
