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
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

package studio.phaseshift.metatron.lang.obj.mgrph;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedEdge;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.Iterator;

import static studio.phaseshift.metatron.lang.obj.mgrph.mgrphInstSet.EDGE_TID;

public class MEdge extends MElement implements Obj, Edge, WrappedEdge<Edge> {

    protected MEdge(final Edge edge) {
        super(edge);
    }

    public static MEdge of(final Edge edge) {
        return edge instanceof MEdge ? (MEdge) edge : new MEdge(edge);
    }

    @Override
    public Edge value() {
        return (Edge) this.element;
    }

    @Override
    public Iterator<Vertex> vertices(final Direction direction) {
        return MVertex.makeVertices(this.getBaseEdge().vertices(direction));
    }

    @Override
    public <V> Property<V> property(final String key, final V value) {
        return MProperty.of(this.getBaseEdge().property(key, value));
    }

    @Override
    public <V> Iterator<Property<V>> properties(String... propertyKeys) {
        return MProperty.makeProperties(this.getBaseEdge().<V>properties(propertyKeys));
    }

    public static <E> Iterator<E> makeEdges(final Iterator<Edge> edges) {
        return (Iterator) IteratorUtil.map(edges, MEdge::of);
    }

    @Override
    public Edge getBaseEdge() {
        return this.value();
    }

    @Override
    public fURI tid() {
        return EDGE_TID;
        //return f(this.element.label());
    }

    @Override
    public fURI vid() {
        return ((MVertex) this.outVertex()).vid().extend("outE").extend(this.label()).extend(this.element.id().toString());
        //return this.graph().configuration().get(fURI.class, "pattern").retractPattern().extend("edge").extend(this.element.id().toString());
    }

}
