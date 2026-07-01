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

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.util.detached.DetachedVertex;
import org.apache.tinkerpop.gremlin.structure.util.reference.ReferenceVertex;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.grph.grphInstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.IteratorUtil;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A Rec backed by a live TinkerPop {@link Edge}.
 * Provides structural access to the edge's endpoint vertices through
 * the live edge reference while carrying a materialized property map.
 */
public class EdgeRec extends ElementRec<Edge> {

    public EdgeRec(final Edge edge, final grphSpace space) {
        super(edge, space, f(edge.label()), space.elementVID(edge));
    }

    public Edge edge() {
        return this.element;
    }

    /**
     * Return the endpoint vertex, auto-resolving through {@code :redirect}
     * if the vertex is a reference to an object in another space.
     * <p>
     * Re-fetches the vertex via the traversal source because edge endpoints
     * from remote traversals can be bare {@code ReferenceVertex} references
     * whose {@code properties()} iterator is empty.
     */
    private Obj resolveEndpoint(final org.apache.tinkerpop.gremlin.structure.Vertex v) {
        final Vertex fullVertex = v instanceof ReferenceVertex || v instanceof DetachedVertex ? IteratorUtil.stream(this.space.sjvm().V(v.id())).findFirst().orElse(v) : v;

        final VertexProperty<?> redirectProp = fullVertex.property(grphInstSet.AUTO_ROUTE_STRING);
        if (redirectProp.isPresent()) {
            try {
                final String redirectStr = redirectProp.value().toString();
                this.logger().debug("auto resolution of %s", redirectStr);
                return ObjmtronSerializer.parse(redirectStr).apply().parent(new VertexRec(fullVertex, this.space));
            } catch (final Exception e) {
                this.logger().warn("an auto routing error occured: %s", e);
            }
        }
        return new VertexRec(fullVertex, this.space);
    }

    public Obj inVertex() {
        return resolveEndpoint(this.element.inVertex());
    }

    public Obj outVertex() {
        return resolveEndpoint(this.element.outVertex());
    }

    public Obj vertex(final Direction direction) {
        return direction == Direction.IN ? inVertex() : outVertex();
    }

    /**
     * Intercept IN/OUT key access — handles both single-segment keys and
     * multi-segment cascade paths like /IN/name by decomposing the first
     * segment as a structural direction, then cascading the remainder.
     */
    @Override
    public Obj at(final Obj key) {
        if (!(key instanceof Uri u)) return super.at(key);
        final fURI f = u.uriValue();
        final java.util.List<String> segs = f.segments();
        if (segs.isEmpty()) return super.at(key);
        final String first = segs.get(0);
        final Direction dir = "IN".equalsIgnoreCase(first) ? Direction.IN
                : "OUT".equalsIgnoreCase(first) ? Direction.OUT : null;
        if (dir == null && !"BOTH".equalsIgnoreCase(first))
            return super.at(key);

        java.util.stream.Stream<Obj> stream = dir != null
                ? java.util.stream.Stream.of(resolveEndpoint(
                dir == Direction.IN ? this.element.inVertex() : this.element.outVertex()))
                : java.util.stream.Stream.concat(inVertex().stream(), outVertex().stream());

        for (int i = 1; i < segs.size(); i++) {
            final String seg = segs.get(i);
            if ("+".equals(seg)) continue;
            if ("#".equals(seg)) break;
            stream = stream.flatMap(o -> o.asRec().at(uri(seg)).stream());
        }
        return objs(stream);
    }
}
