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

package studio.phaseshift.metatron.lang.mtron.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.Call;
import studio.phaseshift.metatron.lang.mtron.type.Type;
import studio.phaseshift.metatron.lang.msys.Router;


public class MType extends MObj implements Type {

    public MType(final Call value, final fURI tid) {
        super(value, tid, tid);
    }

    public static Type T(final fURI tid) {
        return (tid.hasPattern() ||
                null == Router.global() ||
                Router.global().read(tid).isNoObj() ||
                Router.global().read(tid).isObjs() ||
                Router.global().read(tid).isCall()) ?
                MType.of(tid) : Router.global().read(tid).tid(tid).as();
    }

    public static Type T(final Call obj) {
        return MType.of(obj, obj.tid());
    }

    public static Type T(final fURI tid, final Call predicate) {
        return new MType(predicate.tryToInst(), tid);
    }

    public static MType of(final Call value, final fURI tid) {
        return null == value || value.isNoObj() ? MType.of(tid) : new MType(value, tid);
    }

    public static MType of(final fURI tid) {
        return new MType(null, tid);
    }

    /*@Override
    public Obj apply(final Obj lhs) {
        if (null == this.value)
            this.value = Router.global().read(this.vid).value();
        return null != this.value ? this.value().apply(lhs) : lhs;
    }*/

    @Override
    public Type clone(final Object jvm, final fURI tid, final fURI vid) {
        // if (!tid.equals(vid))
        //     throw MTronException.of("a tid and vid of a type must be the same: %s != %s", tid, vid);
        return new MType((Call) jvm, tid);
    }

    @Override
    public Call jvm() {
        return (Call) this.jvm;
    }
}
