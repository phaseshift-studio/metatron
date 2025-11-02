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

package studio.phaseshift.metatron.lang.mgrph.tp;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedVertex;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.msys.Router;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.Iterator;

import static studio.phaseshift.metatron.lang.mgrph.mgrphInstSet.VERTEX_TID;

public class MVertex extends MElement implements Obj, Vertex, WrappedVertex<Vertex> {

    protected MVertex(final Vertex vertex) {
        super(vertex, VERTEX_TID, fURI.NULL);
    }

    public static MVertex of(final Vertex vertex) {
        return vertex instanceof MVertex ? (MVertex) vertex : new MVertex(vertex);
    }

    @Override
    public Vertex jvm() {
        return (Vertex) this.jvm;
    }

    @Override
    public Edge addEdge(String label, Vertex inVertex, Object... keyValues) {
        Router.global().write(this.vid().extend("outE"), new MVertex(inVertex));
        return null;
    }

    @Override
    public <V> VertexProperty<V> property(final String key) {
        return MVertexProperty.of(Vertex.super.property(key));
    }

   /* @Override
    public <V> VertexProperty<V> property(final String key, final V value, final Object... keyValues) {
        return MVertexProperty.of(Vertex.super.property(key, value, keyValues));
    }*/

    @Override
    public <V> VertexProperty<V> property(VertexProperty.Cardinality cardinality, String key, V value, Object... keyValues) {
        return MVertexProperty.of(this.getBaseVertex().property(cardinality, key, value, keyValues));
    }

    @Override
    public fURI vid() {
        return fURI.NULL; //this.graph().configuration().get(fURI.class, "pattern").retractPattern().extend("vertex").extend(this.value().id().toString());
    }

    @Override
    public Iterator<Edge> edges(final Direction direction, final String... edgeLabels) {
        return MEdge.makeEdges(this.getBaseVertex().edges(direction, edgeLabels));
    }

    @Override
    public Iterator<Vertex> vertices(final Direction direction, final String... edgeLabels) {
        return (Iterator) IteratorUtil.map(MVertex.makeVertices(this.getBaseVertex().vertices(direction, edgeLabels)),v->((MVertex)v).c(this.c()));
    }

    @Override
    public <V> Iterator<VertexProperty<V>> properties(final String... propertyKeys) {
        return MVertexProperty.makeVertexProperties(this.getBaseVertex().<V>properties(propertyKeys));
    }

    public static <E> Iterator<E> makeVertices(final Iterator<Vertex> vertices) {
        return (Iterator) IteratorUtil.map(vertices, MVertex::of);
    }

    @Override
    public Vertex getBaseVertex() {
        return this.jvm();
    }
}
