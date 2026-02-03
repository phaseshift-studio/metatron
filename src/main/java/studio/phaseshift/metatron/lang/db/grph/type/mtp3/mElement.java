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
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedElement;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.grph.type.Elmt;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.sys.type.Router;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class mElement implements Element, WrappedElement<Elmt> {

    protected final Elmt base;
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

    protected mElement(final mGraph graph, final Elmt base) {
        this.base = base;
        this.graph = graph;
    }

    @Override
    public fURI id() {
        return null;
        // return this.getBaseElement().id();
    }

    @Override
    public String label() {
        return null;
        //   return this.getBaseElement().label().toString();
    }

    @Override
    public Graph graph() {
        return this.graph;
    }

    @Override
    public void remove() {
      
        //  this.getBaseElement().jvm().clear();
        Router.global().write(this.id(), noobj());
    }

    @Override
    public boolean equals(final Object other) {
        return ElementHelper.areEqual(this, other);
    }

    @Override
    public int hashCode() {
        return ElementHelper.hashCode(this);
    }

    //@Override
    // public <V> Iterator<? extends Property<V>> properties(final String... propertyKeys) {
    //  return this.getBaseElement().at(PROPS).elements().map(Obj::<Rel>as).filter(r -> ElementHelper.keyExists(r.first().toString(),propertyKeys)).map(r -> r.second()).map(Obj::<Rel>as).i;
    //}

    @Override
    public Elmt getBaseElement() {
        return this.base;
    }
}
