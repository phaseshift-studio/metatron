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

package studio.phaseshift.metatron.lang.obj.mtron;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.base.Obj;
import studio.phaseshift.metatron.lang.obj.base.Objs;

public class MObjs extends MObj implements Objs {

    public MObjs(final Iterable<Obj> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    public MObjs(final Iterable<Obj> value) {
        this(value, Objs.TID, fURI.NONE);
    }

    @Override
    public Objs clone(final Object value, final fURI tid, final fURI vid) {
        return new MObjs((Iterable<Obj>) value, tid, vid);
    }

    @Override
    public Iterable<Obj> value() {
        return (Iterable<Obj>) this.value;
    }
}