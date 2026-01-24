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

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedEdge;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;
import studio.phaseshift.metatron.lang.db.grph.type.REdge;

import java.util.Iterator;

import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mEdge extends mElement implements Edge, WrappedEdge<REdge> {

    public mEdge(final mGraph graph, final REdge edge) {
        super(graph, edge);
    }

    public static mEdge of(final mGraph graph, final REdge edge) {
        return new mEdge(graph, edge);
    }

    @Override
    public Iterator<Vertex> vertices(Direction direction) {
        return this.getBaseEdge().vertices(direction).map(v -> mVertex.of(this.graph, v)).map(v -> (Vertex) v).iterator();
    }

    @Override
    public <V> Property<V> property(final String key, final V value) {
        this.getBaseEdge().property(fURI.f(key), MObjFactory.of().create(value));
        return mProperty.of(this, rel(uri(key), MObjFactory.of().create(value)));
    }

    @Override
    public <V> Iterator<Property<V>> properties(String... propertyKeys) {
        return this.getBaseEdge().<V>properties(stringToUriLabels(propertyKeys)).map(p -> mProperty.of(this, p)).map(p -> (Property<V>) p).iterator();
    }

    @Override
    public REdge getBaseEdge() {
        return (REdge) this.base;
    }
}

