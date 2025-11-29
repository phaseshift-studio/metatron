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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.Rel;
import studio.phaseshift.metatron.lang.core.m.type.facade.FRec;
import studio.phaseshift.metatron.lang.core.m.type.impl.MRec;

import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.db.grph.inst.grphInstSet.EDGE_TID;
import static studio.phaseshift.metatron.lang.db.grph.type.TP3Translator.LABEL;
import static studio.phaseshift.metatron.lang.db.grph.type.TP3Translator.PROPS;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RElement extends FRec {

    public RElement(final Obj element) {
        super((Rec) element);
    }

    public static RElement of(final Rec obj) {
        return (obj.tid().basePath().equals(EDGE_TID)) ? REdge.of(obj) : RVertex.of(obj);
    }

    public RElement property(final fURI key, final Obj value) {
        this.put(uri(PROPS), this.at(uri(PROPS)).orSupply(MRec::rec).put(uri(key), value), MUTABLE);
        return this;
    }

    public Stream<Rel> properties(final Obj keys) {
        boolean emptyKeys = keys.elements().noneMatch(e -> !e.isNoObj());
        return this.has(PROPS) ?
                this.at(PROPS).<Rec>as()
                        .elements()
                        .filter(o -> emptyKeys || keys.elements().anyMatch(u -> o.<Rel>as().first().uriValue().matches(u.uriValue()))) :
                Stream.empty();
    }

    public fURI label() {
        return this.at(LABEL).uriValue();
    }

    public Object id() {
        return this.vid();
    }

    public Stream<Obj> values(final Obj keys) {
        return this.properties(keys).map(Rel::second);
    }

    // abstract public void drop();

    @Override
    public RElement clone() {
        return (RElement) super.clone();
    }
}
