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

package studio.phaseshift.metatron.lang.core.m.type.facade;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;

import java.util.Map;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FRec extends FObj<Rec> implements Rec {

    public FRec(final Rec base) {
        super(base);
    }

    @Override
    public Rec put(final Obj key, final Obj value) {
        return this.clone(this.base.put(key, value), this.tid(), this.vid());
    }

    @Override
    public Rec plus(final Rec rhs) {
        return this.clone(this.base.plus(rhs), this.tid(), this.vid());
    }

    @Override
    public Rec clone(final Object jvm, final fURI tid, final fURI vid) {
        return super.clone(jvm, tid, vid);
    }

    @Override
    public FRec self(final Object jvm, final fURI tid, final fURI vid) {
        return (FRec) super.self(jvm, tid, vid);
    }

    @Override
    public Map<Obj, Obj> jvm() {
        return this.base.jvm();
    }

    public Rec vid(final fURI vid) {
        return super.vid(vid).as();
    }

    public Rec tid(final fURI tid) {
        return super.tid(tid).as();
    }
}
