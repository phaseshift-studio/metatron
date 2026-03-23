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

package studio.phaseshift.metatron.isa.grph.tp3.space;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.*;
import static studio.phaseshift.metatron.isa.grph.tp3.space.VertexMap.lazyVertexToRec;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class EdgeMap extends ElementMap {

    protected static final GraphittyLogger LOG = Graphitty.log(EdgeMap.class);

    public EdgeMap(final Edge base, final tp3Space space) {
        super(base, space);
    }

    @Override
    public boolean containsKey(final Object key) {
        return key.equals(IN) || key.equals(OUT) || key.equals(BOTH) || super.containsKey(key);
    }

    @Override
    public Edge getBase() {
        return (Edge) this.base;
    }

    @Override
    public Obj get(final Object key) {
        if (key.equals(IN) || key.equals(OUT) || key.equals(BOTH))
            return objs(IteratorUtil.stream(this.getBase().vertices(Direction.valueOf(((Uri) key).uriValue().toString())))
                    .map(v -> auto_from_(uri(this.space.elementVID(v)), lazyVertexToRec(v, this.space)).tryToInst()));
        return super.get(key);
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
        return edgeToRec(edge, tp3Space.from(edge));
    }

    public static Rec edgeToRec(final Edge edge, final Rec lhs) {
        return rec().self(new EdgeMap(edge, lhs.<ElementMap>jvmAs().space), f(edge.label()).c(lhs.c()), lhs.<ElementMap>jvmAs().space.elementVID(edge)).parent(lhs);
    }

    public static Rec edgeToRec(final Edge edge, final tp3Space lhs) {
        return new EdgeMap(edge, lhs).selfRec();
    }

    public static Edge recToEdge(final Rec rec) {
        return rec.<EdgeMap>jvmAs().getBase();
    }

    public static Inst lazyEdgeToRec(final Edge base, final tp3Space lhs) {
        return new LazyAutoElmnt(new EdgeMap(base, lhs));
    }

}
