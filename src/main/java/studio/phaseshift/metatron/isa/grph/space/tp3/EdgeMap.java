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

package studio.phaseshift.metatron.isa.grph.space.tp3;

import org.apache.tinkerpop.gremlin.structure.Edge;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Uri;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.grph.grphInstSet.*;
import static studio.phaseshift.metatron.isa.grph.space.tp3.VertexMap.vRec;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class EdgeMap extends ElementMap {

    public EdgeMap(final Edge base) {
        super(base);
    }

    @Override
    public Edge getBase() {
        return (Edge) this.base;
    }

    @Override
    public Obj get(final Object key) {
        if (key.equals(IN))
            return auto_from_(uri("/g/V/" + this.getBase().inVertex().id().toString()), vRec(this.getBase().inVertex())).tryToInst();
        if (key.equals(OUT))
            return auto_from_(uri("/g/V/" + this.getBase().outVertex().id().toString()), vRec(this.getBase().outVertex())).tryToInst();
        if(key.equals(LABEL))
            return uri(this.getBase().label());
        else
            return super.get(key);
    }

   /* @Override
    public Set<Entry<Uri, Obj>> entrySet() {
        final Set<Entry<Uri, Obj>> entries = new LinkedHashSet<>(super.entrySet().stream().collect(Collectors.toSet()));
        entries.add(new SimpleEntry<>(IN, this.get(IN)));
        entries.add(new SimpleEntry<>(OUT, this.get(OUT)));
        return entries;
    }*/

    @Override
    public Rec asRec() {
        return rec((Map) this, EDGE_TID, null);
    }

    @Override
    public Rec selfRec() {
        return rec((Map) this, EDGE_TID,null).self(this, EDGE_TID, f("/g/E/" + this.getBase().id().toString()));
    }

    public static Inst eRec(final Edge base) {
        return new EdgeMap.LazyAutoInst(new EdgeMap(base));
        //return (Inst) auto_(instC(INST_TID.dom(ALL.maybe()).rng(EDGE_TID), lst(), (lhs, inst) -> new EdgeMap(base).asRec())).tryToInst();
    }

}
