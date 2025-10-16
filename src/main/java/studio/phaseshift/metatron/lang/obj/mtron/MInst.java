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
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Poly;
import studio.phaseshift.metatron.lang.obj.mtron.c.cInt;

import java.util.Objects;
import java.util.function.BiFunction;

import static studio.phaseshift.metatron.util.Tuple.Triplet;

public class MInst extends MObj implements Inst {
    public MInst(final Triplet<Poly, Inst.f, Obj> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }


    @Override
    public Inst clone(final Object value, final fURI tid, final fURI vid) {
        return (Inst) super.clone(value, tid, vid);
    }

    @Override
    public Triplet<Poly, Inst.f, Obj> value() {
        return (Triplet<Poly, Inst.f, Obj>) this.value;
    }

    @Override
    public Inst c(final cInt c) {
        return (Inst) super.c(c);
    }

    public static Inst instA(final fURI tid) {
        return new MInst(Triplet.with(MLst.of(), null, NoObj.single()), tid, fURI.NULL);
    }

    public static Inst instB(final fURI tid, final Poly args) {
        return new MInst(Triplet.with(args, null, NoObj.single()), tid, fURI.NULL);
    }

    public static Inst instC(final fURI tid, final Poly args, final BiFunction<Obj, Inst, Obj> f) {
        return new MInst(Triplet.with(args, Inst.f.of(f), NoObj.single()), tid, fURI.NULL);
    }

    public static Inst instC(final fURI tid, final Poly args, final BiFunction<Obj, Inst, Obj> f, final Obj seed) {
        return new MInst(Triplet.with(args, Inst.f.of(f), seed), tid, fURI.NULL);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.tid, this.vid);
    }


    @Override
    public boolean equals(final Object other) {
        return (other instanceof Inst) &&
                Objects.equals(this.tid, ((Obj) other).tid()) &&
                Objects.equals(this.args(), ((Inst) other).args()) &&
                Objects.equals(this.vid, ((Obj) other).vid());
        /*Objects.equals(this.value,((Obj) other).value())*/
    }
}