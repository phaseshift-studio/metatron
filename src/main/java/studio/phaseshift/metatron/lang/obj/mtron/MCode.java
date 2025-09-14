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
import studio.phaseshift.metatron.lang.obj.Code;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.Obj;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.*;

public class MCode extends MObj implements Code {

    public MCode(final List<Inst> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    public MCode(final List<Inst> value) {
        this(value, CODE_TID, fURI.NULL);
    }

    @Override
    public Code clone(final Object value, final fURI tid, final fURI vid) {
        return new MCode((List<Inst>) value, tid, vid);
    }

    @Override
    public List<Inst> value() {
        return (List<Inst>) this.value;
    }

    public static Code of(final List<Inst> insts) {
        return new MCode(insts, CODE_TID, fURI.NULL);
    }

    @Override
    public Code resolve(final Obj start) { // support callbacks on resolution so monoids can generate appropriate monads on the first scan of the code
        Obj running_obj = start;
        final List<Inst> resolvedCode = new ArrayList<>();
        for (final Inst inst : this.value()) {
                final Inst instB = inst.resolve(Inst.Resolve.B, running_obj);
                running_obj = (instB.f().form().isInitial() ||
                        instB.tid().queryless().equals(START_TID) || // once the forms are working, gut these direct references
                        instB.tid().queryless().equals(FROM_TID)) ?
                     instB.arg(0) : instB.rng();
                resolvedCode.add(instB);
        }
       return MCode.of(resolvedCode);
    }

    /*@Override
    public Obj apply(final Obj lhs) {
        return lhs;
    }*/
}