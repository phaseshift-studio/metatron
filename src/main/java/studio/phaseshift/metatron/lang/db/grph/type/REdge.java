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

package studio.phaseshift.metatron.lang.db.grph.type;

import org.apache.tinkerpop.gremlin.structure.Direction;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Objs;
import studio.phaseshift.metatron.lang.core.m.type.Rec;

import java.util.Map;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.auto;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.db.grph.inst.grphInstSet.EDGE_TID;
import static studio.phaseshift.metatron.lang.translator.TP3Translator.LABEL;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class REdge extends RElement {

    protected REdge(final Map<Obj, Obj> edge) {
        super(edge, EDGE_TID);
    }

    public static REdge of(final Rec edge) {
        return edge instanceof REdge ? (REdge) edge : new REdge(edge.jvm()).vid(edge.vid()).as();
    }

    public static REdge of(final String label, final fURI outVertex, final fURI inVertex, final Object... keyValues) {
        return REdge.of(rec(uri(LABEL), uri(label), uri(Direction.OUT.name()), auto(outVertex), uri(Direction.IN.name()), auto(inVertex)));
    }

    public static Stream<REdge> of(final Obj edges) {
        return edges instanceof Objs ? edges.elements().map(Obj::<Rec>as).map(REdge::of) : Stream.of(REdge.of((Rec) edges));
    }

    public Stream<RVertex> vertices(final Direction direction) {
        final Stream<RVertex> out = direction.equals(Direction.OUT) || direction.equals(Direction.BOTH) ?
                this.at(Direction.OUT.name()).stream().map(Obj::<Rec>as).map(RVertex::of) : Stream.empty();
        final Stream<RVertex> in = direction.equals(Direction.IN) || direction.equals(Direction.BOTH) ?
                this.at(Direction.IN.name()).stream().map(Obj::<Rec>as).map(RVertex::of) : Stream.empty();
        return Stream.concat(out, in);
    }

    public String toString() {
        return "{{b}}e{{g}}[{{y}}" + this.vid() + "{{g}}]" + (this.tid().cV().isOne() ? "" : ("{{{y}}" + this.tid().c() + "{{g}}}")) + "[{{b}}" + this.jvm().get(uri(Direction.OUT.name())) + "{{g}}={{b}}" + this.label() + "{{g}}=>" + this.jvm().get(uri(Direction.IN.name())) + "{{g}}]{{X}}";
    }

    @Override
    public REdge clone() {
        return (REdge) super.clone();
    }


}