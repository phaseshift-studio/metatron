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

import org.javatuples.Triplet;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.base.Inst;
import studio.phaseshift.metatron.lang.obj.base.NoObj;
import studio.phaseshift.metatron.lang.obj.base.Obj;
import studio.phaseshift.metatron.lang.obj.base.Poly;

public class MInst extends MObj implements Inst {
    public MInst(final Triplet<Poly, Inst.f, Obj> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }


    @Override
    public Inst clone(final Object value, final fURI tid, final fURI vid) {
        return new MInst((Triplet<Poly, Inst.f, Obj>) value, tid, vid);
    }

    @Override
    public Triplet<Poly, Inst.f, Obj> value() {
        return (Triplet<Poly, Inst.f, Obj>) this.value;
    }

    public boolean resolved() {
        return null != this.value().getValue1();
    }

    public static Inst instB(final Poly args, final fURI tid) {
        return new MInst(Triplet.with(args, null, NoObj.single()), tid, fURI.NONE);
    }

    public static Inst instA(final fURI tid) {
        return new MInst(Triplet.with(MLst.of(), null, NoObj.single()), tid, fURI.NONE);
    }
}