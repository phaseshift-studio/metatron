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

package studio.phaseshift.metatron.isa.mach.type;

import studio.phaseshift.metatron.algebra.Ring;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.MONAD;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MONAD_TID;

public interface Monad extends Obj, Ring<Monad> {

    @Override
    Monad clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    List<Obj> jvm();

    @Override
    default Monad neg() {
        return this.c(cInt::neg);
    }

    @Override
    default Monad mult(final Monad rhs) {
        return this.apply(rhs).c(c -> c.mult(rhs.c()));
    }

    @Override
    default Monad one() {
        return this.c(cInt.ONE());
    }

    @Override
    default Monad zero() {
        return this.c(cInt.ZERO());
    }

    default boolean halted() {
        return this.inst().isNoObj();
    }

    default boolean dead() {
        return this.obj().isNoObj();
    }

    default boolean zombie() {
        return this.dead() && !this.halted();
    }

    default Rec state() {
        return null == this.jvm().get(2) || this.jvm().get(2).isNoObj() ? rec0() : (Rec) this.jvm().get(2);
    }

    default Inst inst() {
        return (Inst) this.jvm().get(1);
    }

    default Obj obj() {
        return this.jvm().getFirst();
    }

    @Override
    Monad tid(final fURI tid);

    @Override
    default Monad c(final cInt c) {
        return (Monad) Obj.super.c(c);
    }

    @Override
    default Monad c(final Function<cInt, cInt> func) {
        return (Monad) Obj.super.c(func);
    }

    @Override
    default Monad c(final Long exact) {
        return this.c(cInt.of(exact));
    }

    default Monad obj(final Obj obj) {
        return this.clone(List.of(obj, this.inst(), this.state()), this.tid(), this.vid());
    }

    default Monad inst(final Inst inst) {
        return this.clone(List.of(this.obj(), inst, this.state()), this.tid(), this.vid());
    }

    @Override
    default Type dom() {
        return T(MACH_MONAD_TID);
    } // TODO: is this what we need?

    @Override
    default Type rng() {
        return T(MACH_MONAD_TID);
    }

    @Override
    Monad clone();

    @Override
    default Monad apply(final Obj inst) {
        if (this.halted())
            return this;
        return this.obj(this.inst().apply(this.inst().tid().hasQuery(MONAD) ? this : this.obj())).inst(inst.as());
    }

    class Helpers {
        public static String monadToString(final Monad monad) {
            return "%s::[%s<=o==M==i=>%s]".formatted(monad.tid(), monad.obj(), monad.inst());
        }

        public static int monadHashCode(final Monad monad) {
            return Objects.hash(monad.tid().cLess(), monad.jvm());
        }

        public static boolean monadEquals(final Monad monad, final Object other) {
            return other instanceof Monad && Obj.Helper.objEquals(monad, other);
        }

        public static boolean monadcLessEquals(final Monad monad, final Object other) {
            return other instanceof Monad && Obj.Helper.objcLessEquals(monad, other);
        }
    }

}