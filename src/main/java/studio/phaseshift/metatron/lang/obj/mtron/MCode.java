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
import studio.phaseshift.metatron.lang.obj.base.Bool;
import studio.phaseshift.metatron.lang.obj.base.Code;
import studio.phaseshift.metatron.lang.obj.base.Inst;

import java.util.List;

public class MCode extends MObj implements Code {

    public MCode(final List<Inst> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    public MCode(final List<Inst> value) {
        this(value, Code.TID, fURI.NONE);
    }

    @Override
    public Code clone(final Object value, final fURI tid, final fURI vid) {
        return new MCode((List<Inst>) value, tid, vid);
    }

    @Override
    public List<Inst> value() {
        return (List<Inst>) this.value;
    }
}