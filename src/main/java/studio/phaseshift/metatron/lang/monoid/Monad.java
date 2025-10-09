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

package studio.phaseshift.metatron.lang.monoid;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.monoid.mtron.MMonad;
import studio.phaseshift.metatron.lang.obj.*;
import studio.phaseshift.metatron.lang.obj.mtron.c.cInt;
import studio.phaseshift.metatron.ui.Graphitty;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static studio.phaseshift.metatron.lang.obj.mtron.MObjs.objs;
import static studio.phaseshift.metatron.lang.obj.mtron.MType.T;
import static studio.phaseshift.metatron.util.Tuple.Triplet;

public interface Monad extends Obj { //, Ring<Monad> {

    class Helpers {
        public static String monadToString(final Monad monad) {
            return Graphitty.string("{{b}}%s{{g}}::[%s{{g}}<--{{/g}}{{c}}M{{g}}-->{{c}}%s{{g}}]{{X}}", monad.tid(), monad.obj(), monad.inst());
        }

        public static int monadHashCode(final Monad monad) {
            return Objects.hash(monad.tid(), monad.vid(), monad.value());
        }

        public static boolean monadEquals(final Monad monad, final Object other) {
            return other instanceof Monad && Objects.equals(((Monad) other).tid(), monad.tid()) && Objects.equals(((Monad) other).vid(), monad.vid()) && Objects.equals(((Monad) other).value(), monad.value());
        }
    }

    @Override
    Monad clone(final Object value, final fURI tid, final fURI vid);

    @Override
    Triplet<Obj, Inst, Rec> value();

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
        return this.value().get2();
    }

    default Inst inst() {
        return this.value().get1();
    }

    default Obj obj() {
        return this.value().get0();
    }

    @Override
    Monad tid(final fURI furi);

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

    default boolean merges(final Monad other) {
        return Objects.equals(this.value(), other.value()) && Objects.equals(this.tid().basePath(), other.tid().basePath());
    }

    default Obj plus(final Monad other) {
        return this.merges(other) ? this.tid(this.tid().plus(other.tid())) : objs(List.of(this, other));
    }

    default Monad obj(final Obj obj) {
        return this.clone(Triplet.with(obj, this.inst(), this.state()), this.tid(), this.vid());
    }

    default Monad inst(final Inst inst) {
        return this.clone(Triplet.with(this.obj(), inst, this.state()), this.tid(), this.vid());
    }

    @Override
    default Type dom() {
        return T(MMonad.MMONAD_TID);
    } // TODO: is this what we need?

    @Override
    default Type rng() {
        return T(MMonad.MMONAD_TID);
    }

    @Override
    default Monad apply(final Obj inst) {
        if (this.halted())
            return this;
        return this.obj(this.inst().apply(this.obj())).inst(inst.as());
    }

}