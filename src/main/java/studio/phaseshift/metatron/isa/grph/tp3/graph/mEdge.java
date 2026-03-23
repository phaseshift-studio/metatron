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

package studio.phaseshift.metatron.isa.grph.tp3.graph;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.empty.EmptyProperty;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedEdge;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;

import java.util.Iterator;

import static studio.phaseshift.metatron.isa.grph.grphInstSet.IN;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.OUT;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mEdge extends mElement implements Edge, WrappedEdge<Rec> {

    public mEdge(final mGraph graph, final Rec edge) {
        super(graph, edge);
    }

    public static mEdge of(final mGraph graph, final Rec edge) {
        return new mEdge(graph, edge);
    }

    @Override
    public Iterator<Vertex> vertices(Direction direction) {
        return this.getBaseEdge().at(direction.name()).stream().map(v -> (Vertex) mVertex.of(this.graph, v.asRec())).iterator();
    }

    @Override
    public <V> Property<V> property(final String key, final V value) {
        if (key.equals(IN.uriValue().toString()) || key.equals(OUT.uriValue().toString()))
            return EmptyProperty.instance();
        final Rel rel = rel(uri(key), MObjFactory.of().createOrFail(value));
        this.getBaseEdge().at(rel.first(), rel.second(), MUTABLE);
        return mProperty.of(this, rel);
    }

    @Override
    public <V> Iterator<Property<V>> properties(String... propertyKeys) {
        return this.getBaseEdge().elements()
                .filter(p -> !p.first().equals(OUT) && !p.first().equals(IN))
                .filter(p -> ElementHelper.keyExists(p.first().toString(), propertyKeys))
                .map(p -> (Property<V>) mProperty.of(this, p)).iterator();
    }

    @Override
    public Rec getBaseEdge() {
        return this.base;
    }
}

