/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang.obj;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.util.MTronException;

import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.*;

public interface ObjFactory {

    <O extends Obj> O create(final Object value, final fURI tid, final fURI vid, final Class<O> objClass);

    Obj create(final Object value);

    default <O extends Obj> O create(final Object value, final fURI tid, final Class<O> objClass) {
        return this.create(value, tid, fURI.NULL, objClass);
    }

    default <O extends Obj> O create(final Object value, final Class<O> objClass) {
        fURI tid;
        if (Bool.class.isAssignableFrom(objClass))
            tid = BOOL_TID;
        else if (Int.class.isAssignableFrom(objClass))
            tid = INT_TID;
        else if (Real.class.isAssignableFrom(objClass))
            tid = REAL_TID;
        else if (Str.class.isAssignableFrom(objClass))
            tid = STR_TID;
        else if (Uri.class.isAssignableFrom(objClass))
            tid = URI_TID;
        else if (Lst.class.isAssignableFrom(objClass))
            tid = LST_TID;
        else if (Rel.class.isAssignableFrom(objClass))
            tid = REL_TID;
        else if (Rec.class.isAssignableFrom(objClass))
            tid = REC_TID;
        else if (Inst.class.isAssignableFrom(objClass))
            tid = INST_TID;
        else if (Code.class.isAssignableFrom(objClass))
            tid = CODE_TID;
        else if (Objs.class.isAssignableFrom(objClass))
            tid = OBJS_TID;
        else if (Type.class.isAssignableFrom(objClass))
            tid = TYPE_TID;
        else if (NoObj.class.isAssignableFrom(objClass))
            tid = fURI.NONE;
        else
            throw MTronException.of("unable to convert to requested obj class: %s", objClass);
        return this.create(value, tid, fURI.NULL, objClass);
    }

}
