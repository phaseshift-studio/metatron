/*
 * metatron: a distributed virtual machine and language
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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.LOOP;
import static studio.phaseshift.metatron.Tokens.LOOPBACK;
import static studio.phaseshift.metatron.Tokens.MONAD;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst0;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MONAD_TID;

public interface PCMonad extends Monad<Lst> {

    @Override
    PCMonad clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    Lst jvm();

   /* @Override
    default PCMonad neg() {
        return this.c(cInt::neg);
    }

    @Override
    default PCMonad mult(final PCMonad rhs) {
        return (PCMonad)this.apply(rhs).c(c -> c.mult(rhs.c()));
    }

    @Override
    default PCMonad one() {
        return this.c(cInt.ONE());
    }

    @Override
    default PCMonad zero() {
        return this.c(cInt.ZERO());
    }*/

    default boolean halted() {
        return this.inst().isNoObj();
    }

    default boolean dead() {
        return this.obj().isNoObj();
    }

    default boolean zombie() {
        return this.dead() && !this.halted();
    }

    default PCMonad nextInst() {
        return this.jvm(lst(CommonUtil.arrayList(this.obj(), this.code().nextInst(this.inst()), this.state(), this.code())));
    }

    default PCMonad next(final Obj obj) {
        return this.jvm(lst(CommonUtil.arrayList(obj, this.code().nextInst(this.inst()), this.state(), this.code())));
    }

    default Lst loopStack() {
        return this.state().at(LOOP).orElse(lst0()).asLst();
    }

    default Int loop() {
        final List<Obj> stack = this.loopStack().jvm();
        return stack.isEmpty() ? jnt(0) : jnt(stack.get(stack.size() - 1).intValue());
    }

    default PCMonad incrLoop(final int incr) {
        final List<Obj> stack = new ArrayList<>(this.loopStack().jvm());
        if (stack.isEmpty())
            stack.add(jnt(incr));
        else
            stack.set(stack.size() - 1, jnt(stack.get(stack.size() - 1).intValue() + incr));
        return this.state(this.state().at(uri(LOOP), lst(stack)).as());
    }

    default PCMonad pushLoop() {
        final List<Obj> stack = new ArrayList<>(this.loopStack().jvm());
        stack.add(jnt(0));
        return this.state(this.state().at(uri(LOOP), lst(stack)).as());
    }

    /**
     * Pop the loop counter stack — called when a repeat invocation exits so a
     * chained repeat starts its counter at 0.
     */
    default PCMonad popLoop() {
        final List<Obj> stack = new ArrayList<>(this.loopStack().jvm());
        if (!stack.isEmpty())
            stack.remove(stack.size() - 1);
        return this.state(this.state().at(uri(LOOP), lst(stack)).as());
    }

    default boolean isLoopback() {
        return this.state().at(uri(LOOPBACK)).orElse(BOOL_FALSE).boolValue();
    }

    default PCMonad loopback(final boolean loopback) {
        return this.state(this.state().at(uri(LOOPBACK), bool(loopback)).as());
    }

    default Rec state() {
        return this.jvm().asLst().jvm().get(2).orElse(rec());
    }

    default Inst inst() {
        return this.jvm().asLst().jvm().get(1).c(c -> c.mult(this.c())).as();
    }

    default Obj obj() {
        final Obj inner = this.jvm().asLst().jvm().getFirst();
        return inner.isObjs() ? inner : inner.c(c -> c.mult(this.c()));
    }

    default Code code() {
        return this.jvm().asLst().jvm().get(3).orElse(MCode.code(List.of(noobj()))).c(c -> c.mult(this.c())).as();
    }


    @Override
    PCMonad tid(final fURI tid);

    @Override
    default PCMonad c(final cInt c) {
        return (PCMonad) Monad.super.c(c);
    }

    @Override
    default PCMonad c(final Function<cInt, cInt> func) {
        return (PCMonad) Monad.super.c(func);
    }

    @Override
    default PCMonad c(final Long exact) {
        return this.c(cInt.of(exact));
    }

    default PCMonad obj(final Obj obj) {
        return this.clone(lst(obj, this.inst(), this.state(), this.code()), this.tid(), this.vid());
    }

    default PCMonad inst(final Inst inst) {
        return this.clone(lst(this.obj(), inst, this.state(), this.code()), this.tid(), this.vid());
    }

    default PCMonad state(final Rec state) {
        return this.clone(lst(this.obj(), this.inst(), state, this.code()), this.tid(), this.vid());
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
    PCMonad clone();

    @Override
    default Obj apply() {
        if (this.halted())
            return this;
        final boolean monadicInst = this.inst().tid().hasQ(MONAD);
        final Obj nextObj = this.inst().apply(monadicInst ?
                this :        // don't unwrap monad (lhs)
                this.obj());
        return monadicInst ? nextObj : this.next(nextObj);
        //return this.next(monadicInst ? nextObj.asMonad().obj(): nextObj); // wrap monad (rhs)
    }

    class Helpers {
        public static String monadToString(final PCMonad monad) {
            return "%s::[%s<=o==M==i=>%s]".formatted(monad.tid(), monad.obj(), monad.inst());
        }

        public static int monadHashCode(final PCMonad monad) {
            return Objects.hash(monad.tid().one(), monad.jvm());
        }

        public static boolean monadEquals(final PCMonad monad, final Object other) {
            return other instanceof PCMonad && Obj.Helper.objEquals(monad, other);
        }

        public static boolean monadcLessEquals(final PCMonad monad, final Object other) {
            return other instanceof PCMonad && Obj.Helper.objcLessEquals(monad, other);
        }
    }

}