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
import org.apache.tinkerpop.gremlin.structure.Vertex;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.List;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A Rec backed by a live TinkerPop {@link Vertex}.
 * Provides structural graph access (edges, adjacent vertices) through
 * the live vertex reference while carrying a materialized property map.
 */
public class VertexRec extends ElementRec<Vertex> {

    public VertexRec(final Vertex vertex, final grphSpace space) {
        super(vertex, space, f(vertex.label()), space.elementVID(vertex));
    }

    public Vertex vertex() {
        return this.element;
    }

    /**
     * Edge lookup by direction with label filter — traverses live graph.
     */
    public Stream<Obj> edges(final Direction direction, final String... labels) {
        return IteratorUtil.stream(this.element.edges(direction, labels))
                .map(e -> new EdgeRec(e, this.space));
    }

    /**
     * Adjacent vertex lookup by direction with label filter.
     */
    public Stream<Obj> vertices(final Direction direction, final String... labels) {
        return IteratorUtil.stream(this.element.vertices(direction, labels))
                .map(v -> new VertexRec(v, this.space));
    }

    /**
     * Intercept OUT/IN key access — handles both single-segment keys and
     * multi-segment cascade paths like /OUT/+/IN/name by decomposing the
     * first segment as a structural direction, then cascading the remainder.
     */
    @Override
    public Obj at(final Obj key) {
        if (!(key instanceof Uri u)) return super.at(key);
        final fURI f = u.uriValue();
        final List<String> segs = f.segments();
        if (segs.isEmpty()) return super.at(key);
        final String first = segs.get(0);
        final boolean isOut = "OUT".equalsIgnoreCase(first);
        final boolean isIn = "IN".equalsIgnoreCase(first);
        if (!isOut && !isIn && !"BOTH".equalsIgnoreCase(first))
            return super.at(key);

        final Direction dir = isIn ? Direction.IN : isOut ? Direction.OUT : null;
        Stream<Obj> stream = dir != null
                ? edges(dir)
                : Stream.concat(edges(Direction.OUT), edges(Direction.IN));

        // Cascade remaining segments through the edges
        for (int i = 1; i < segs.size(); i++) {
            final String seg = segs.get(i);
            if ("+".equals(seg)) continue;
            if ("#".equals(seg)) break;
            stream = stream.flatMap(o -> o.asRec().at(uri(seg)).stream());
        }
        return objs(stream);
    }
}
