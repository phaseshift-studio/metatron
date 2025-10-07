/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.lang.obj.mtron;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Type;


public class MType extends MObj implements Type {

    public static Type T(final fURI tid) {
        return MType.of(tid);
    }

    public static Type T(final Obj obj) {
        return obj.isType() ? obj.as() : MType.of(obj, obj.tid());
    }

    public MType(final Obj value, final fURI tid) {
        super(value, tid, tid);
    }

    @Override
    public Type clone(final Object value, final fURI tid, final fURI vid) {
        // if (!tid.equals(vid))
        //     throw MTronException.of("a tid and vid of a type must be the same: %s != %s", tid, vid);
        return new MType((Obj) value, tid);
    }

    @Override
    public Obj value() {
        return (Obj) this.value;
    }

    public static MType of(final Obj value, final fURI tid) {
        return null == value || value.isNoObj() ? MType.of(tid) : new MType(value, tid);
    }

    public static MType of(final fURI tid) {
        return new MType(null, tid);
    }
}
