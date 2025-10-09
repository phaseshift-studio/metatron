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

package studio.phaseshift.metatron.lang.obj;

import studio.phaseshift.metatron.algebra.Ring;
import studio.phaseshift.metatron.lang.obj.mtron.MCode;
import studio.phaseshift.metatron.lang.obj.mtron.MInst;
import studio.phaseshift.metatron.lang.obj.mtron.c.cInt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;
import static studio.phaseshift.metatron.lang.obj.mtron.MObjs.objs;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronFluent.StartLess.split;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.ID_TID;

public interface Call extends Obj, Ring<Call> {

    static Call from(final List<Inst> insts) {
        if (insts.isEmpty())
            return NoObj.single();
        else if (insts.size() == 1)
            return insts.get(0);
        else
            return MCode.of(insts);
    }

    default Call singleOrSequence() {
        if (this.isCode()) {
            if (this.codeValue().isEmpty())
                return NoObj.single();
            else if (this.codeValue().size() == 1)
                return this.codeValue().get(0);
        }
        return this;
    }

    default List<Inst> insts() {
        return this.isCode() ? this.codeValue() : List.of(this.as());
    }

    @Override
    Call resolve(final Obj start);

    default <C extends Call> C dom(final Type domain) {
        return (C) this.tid(this.tid().dom(domain.tid()));
    }

    default <C extends Call> C rng(final Type range) {
        return (C) this.tid(this.tid().rng(range.tid()));
    }

    @Override
    default Call neg() {
        return this.c(cInt::neg).as();
    }

    @Override
    default Obj append(final Obj obj) {
        return obj.isCall() ? this.plus((Call) obj) : objs(List.of(this, obj));
    }

    @Override
    default Call one() {
        return MInst.instB(ID_TID, lst());
    }

    @Override
    default boolean isOne() {
        return this.equals(this.one());
    }

    @Override
    default boolean isZero() {
        return this.isNoObj();
    }

    @Override
    default Call c(final Function<cInt, cInt> func) {
        return (Call) Obj.super.c(func);
    }

    @Override
    default Call plus(final Call rhs) {
        if (rhs.isZero()) return this;
        if (this.isZero()) return rhs;
        if (this.clessEquals(rhs))
            return this.c(c -> c.plus(rhs.c()));
        return split(objs(this.singleOrSequence(), rhs.singleOrSequence())).singleOrSequence();
    }

    @Override
    default Call mult(final Call rhs) {
        if (rhs.isZero() || this.isZero())
            return NoObj.single();
        if (rhs.isOne()) return this;
        if (this.isOne()) return rhs;
        final List<Inst> insts = new ArrayList<>(this.insts());
        insts.addAll(rhs.insts());
        return MCode.of(insts).singleOrSequence().c(c -> this.c().mult(rhs.c()));
    }

    @Override
    default Call zero() {
        return NoObj.single();
    }
}
