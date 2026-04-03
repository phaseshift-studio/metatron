/*
 * metatron: a distributed virtual machine and language
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

package studio.phaseshift.metatron.isa.grph.tp3;

import org.apache.tinkerpop.gremlin.structure.*;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import org.apache.tinkerpop.gremlin.structure.util.empty.EmptyVertexProperty;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedVertex;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.impl.MObjFactory;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.IN;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.OUT;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mVertex extends mElement implements Vertex, WrappedVertex<Rec> {


    public mVertex(final mGraph graph, final Rec base) {
        super(graph, (Rec) base);
    }

    @Override
    public String toString() {
        return StringFactory.vertexString(this);
    }

    @Override
    public int hashCode() {
        return ElementHelper.hashCode(this);
    }

    @Override
    public boolean equals(final Object other) {
        return ElementHelper.areEqual(this, other);
    }

    public static mVertex of(final mGraph graph, final Rec r) {
        return new mVertex(graph, r);
    }

    @Override
    public Edge addEdge(final String label, final Vertex inVertex, final Object... keyValues) {
        ElementHelper.legalPropertyKeyValueArray(keyValues);
        final fURI vid = ElementHelper.getIdValue(keyValues).map(this.graph::makeEdgeID).orElseGet(() -> this.graph.makeEdgeID("" + this.graph.counter++));
        final fURI tid = f(ElementHelper.getLabelValue(keyValues).orElse(Edge.DEFAULT_LABEL));
        final Map<Obj, Obj> props = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i = i + 2) {
            if (keyValues[i] != T.id && keyValues[i] != T.label) {
                final Uri key = uri(keyValues[i].toString());
                final Obj value = MObjFactory.of().toObj(keyValues[i + 1]);
                props.put(key, value);
            }
        }
        return mEdge.of(this.graph, rec(props, tid, vid));
    }

    @Override
    public <V> VertexProperty<V> property(final VertexProperty.Cardinality cardinality, final String key, final V value, final Object... keyValues) {
        if (key.equals(IN.uriValue().toString()) || key.equals(OUT.uriValue().toString()))
            return EmptyVertexProperty.instance();
        final Uri uriKey = uri(key);
        Rel property = rel(uriKey, MObjFactory.of().createOrFail(value));
        Rec vertexRec = this.getBaseVertex();
        vertexRec.at(uriKey, MObjFactory.of().createOrFail(value), MUTABLE);
        return new mVertexProperty<>(this, property);
    }

    @Override
    public <V> Iterator<VertexProperty<V>> properties(final String... propertyKeys) {
        return this.getBaseVertex().elements()
                .filter(p -> !p.first().equals(OUT) && !p.first().equals(IN))
                .filter(r -> ElementHelper.keyExists(r.first().uriValue().toString(), propertyKeys))
                .map(r -> (VertexProperty<V>) new mVertexProperty<V>(this, r))
                .iterator();
    }

    @Override
    public Iterator<Edge> edges(final Direction direction, final String... edgeLabels) {
        return this.getBaseVertex().at(direction.equals(Direction.IN) ? "IN/+" : "OUT/+").stream().map(e -> (Edge) mEdge.of(this.graph, e.asRec())).iterator();
    }

    @Override
    public Iterator<Vertex> vertices(Direction direction, String... edgeLabels) {
        return this.getBaseVertex().at(direction.equals(Direction.IN) ? "IN/+/OUT" : "OUT/+/IN").stream().map(v -> (Vertex) mVertex.of(this.graph, v.asRec())).iterator();
    }

    @Override
    public Rec getBaseVertex() {
        return this.base;
    }
}
