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
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.grph.grphInstSet;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.Map;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.grph.space.VertexMap.lazyVertexToRec;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class EdgeMap extends ElementMap {

    protected static final GraphittyLogger LOG = Graphitty.log(EdgeMap.class);

    public EdgeMap(final Edge base, final graphSpace space) {
        super(base, space);
    }

    @Override
    public boolean containsKey(final Object key) {
        return key.equals(grphInstSet.IN) || key.equals(grphInstSet.OUT) || key.equals(grphInstSet.BOTH) || super.containsKey(key);
    }

    @Override
    public Edge getBase() {
        return (Edge) this.base;
    }

    @Override
    public Obj get(final Object key) {
        final fURI keyF = ((Uri) key).uriValue();
        final fURI keyHead = f(keyF.segments(0, null));
        final boolean hasPattern = keyHead.hasPattern();
        if (!hasPattern) {
            if (f(grphInstSet.OUT.toString()).equals(keyHead) || f(grphInstSet.IN.toString()).equals(keyHead) || f(grphInstSet.BOTH.toString()).equals(keyHead)) {
                Stream<Obj> prefix = IteratorUtil.stream(this.getBase().vertices(Direction.valueOf(keyF.segments().getFirst())))
                        .map(e -> auto_from_(uri(this.space.elementVID(e)), lazyVertexToRec(e, this.space)).tryToInst());
                return keyF.segmentLength() < 2 ? objs(prefix) : objs(prefix.map(o -> o.asRec().at(keyF.pretract(1))));
            } else {
                return super.get(key);
            }
        } else {
            Stream<Obj> prefixStream = null;
            if (grphInstSet.OUT_FURI.test(keyHead) || grphInstSet.BOTH_FURI.test(keyHead))
                prefixStream = IteratorUtil.stream(this.getBase().vertices(Direction.OUT)).map(v -> auto_from_(uri(this.space.elementVID(v)), lazyVertexToRec(v, this.space)).tryToInst());
            if (grphInstSet.IN_FURI.test(keyHead) || grphInstSet.BOTH_FURI.test(keyHead))
                prefixStream = Stream.concat(null != prefixStream ? prefixStream : Stream.empty(), IteratorUtil.stream(this.getBase().vertices(Direction.IN)).map(v -> auto_from_(uri(this.space.elementVID(v)), lazyVertexToRec(v, this.space)).tryToInst()));
            if (null != prefixStream && keyF.segmentLength() > 2)
                prefixStream = prefixStream.map(o -> o.asRec().at(keyF.pretract(2)));
            return null == prefixStream ? super.get(keyF) : objs(prefixStream).append(super.get(keyHead));
        }
    }

   /* @Override
    public Set<Entry<Uri, Obj>> entrySet() {
        final Set<Entry<Uri, Obj>> entries = new LinkedHashSet<>(super.entrySet().stream().collect(Collectors.toSet()));
        entries.add(new SimpleEntry<Uri,Obj>(OUT, auto_(()->this.get(OUT)).tryToInst()));
        entries.add(new SimpleEntry<Uri,Obj>(IN, auto_(()->this.get(IN)).tryToInst()));
        return entries;
    }*/

    @Override
    public Rec asRec() {
        return rec((Map) this, f(this.getBase().label()), null);
    }

    @Override
    public Rec selfRec() {
        return rec((Map) this, f(this.getBase().label()), null).selfVID(this.space.elementVID(this.base)).asRec();
    }

    public static Rec edgeToRec(final Edge edge) {
        return edgeToRec(edge, graphSpace.from(edge));
    }

    public static Rec edgeToRec(final Edge edge, final Rec lhs) {
        return rec().self(new EdgeMap(edge, lhs.<ElementMap>jvmAs().space), f(edge.label()).c(lhs.c()), lhs.<ElementMap>jvmAs().space.elementVID(edge)).parent(lhs);
    }

    public static Rec edgeToRec(final Edge edge, final graphSpace lhs) {
        return new EdgeMap(edge, lhs).selfRec();
    }

    public static Edge recToEdge(final Rec rec) {
        return rec.<EdgeMap>jvmAs().getBase();
    }

    public static Inst lazyEdgeToRec(final Edge base, final graphSpace lhs) {
        return new LazyAutoElmnt(new EdgeMap(base, lhs));
    }

}
