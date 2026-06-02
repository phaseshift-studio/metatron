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

package studio.phaseshift.metatron.isa.grph.space;

import org.apache.tinkerpop.gremlin.structure.*;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.reference.ReferenceVertexProperty;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.Iterator;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RefVertex implements Vertex {

    protected final Obj obj;

    public RefVertex(final Obj obj) {
        this.obj = obj;
    }

    @Override
    public Object id() {
        return this.obj.vid();
    }

    @Override
    public String label() {
        return this.obj.tid().toString();
    }

    @Override
    public Graph graph() {
        return null;
    }

    @Override
    public Edge addEdge(final String label, final Vertex inVertex, final Object... keyValues) {
        return null;
    }

    @Override
    public <V> VertexProperty<V> property(final String key) {
        return new ReferenceVertexProperty<>(null, key, this.obj.autoResolve(noobj()).asRec().at(key).jvm());
    }

    @Override
    public void remove() {
        if(this.obj.vid() != null)
            Router.global().write(this.obj.vid(),noobj());
        this.obj.selfTID(this.obj.tid().zero());
    }

    @Override
    public <V> VertexProperty<V> property(VertexProperty.Cardinality cardinality, String key, V value, Object... keyValues) {
        return null;
    }

    @Override
    public Iterator<Edge> edges(Direction direction, String... edgeLabels) {
        return IteratorUtil.of();
    }

    @Override
    public Iterator<Vertex> vertices(final Direction direction, final String... edgeLabels) {
        return obj.autoResolve(noobj()).asRec().elements().filter(e -> ElementHelper.keyExists(e.first().uriValue().toString(), edgeLabels)).filter(e -> e.second().isRec()).map(e -> (Vertex) new RefVertex(e.second())).iterator();
    }

    @Override
    public <V> Iterator<VertexProperty<V>> properties(final String... propertyKeys) {
        return obj.autoResolve(noobj()).asRec().elements().filter(e -> ElementHelper.keyExists(e.first().toString(), propertyKeys)).filter(e -> !e.second().isRec()).map(e -> (VertexProperty<V>) new ReferenceVertexProperty<>(null, e.first().uriValue().toString(), (V) e.second().jvm())).iterator();
    }

}