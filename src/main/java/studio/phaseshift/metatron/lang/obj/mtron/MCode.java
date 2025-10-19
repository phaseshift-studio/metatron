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
import studio.phaseshift.metatron.lang.obj.Code;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.Obj;

import java.util.List;

import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.CODE_TID;

public class MCode extends MObj implements Code {

    public MCode(final List<Inst> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public MCode(final List<Inst> jvm) {
        this(jvm, CODE_TID, fURI.NULL);
    }

    @Override
    public Code clone(final Object jvm, final fURI tid, final fURI vid) {
        return (Code) super.clone(jvm, tid, vid);
    }

    @Override
    public Obj append(final Obj obj) { // TODO: is this plus()? and should this actually be split and not concat?
        if (obj.isCall())
            return this.plus(obj.as()).as();
        else
            return super.append(obj);
    }

    @Override
    public List<Inst> jvm() {
        return (List<Inst>) this.jvm;
    }

    public static Code of(final List<Inst> insts) {
        return new MCode(insts, CODE_TID, fURI.NULL);
    }
}