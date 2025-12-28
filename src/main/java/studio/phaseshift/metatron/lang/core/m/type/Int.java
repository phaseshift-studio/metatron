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

package studio.phaseshift.metatron.lang.core.m.type;

import studio.phaseshift.metatron.algebra.Ring;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.util.MTronException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.*;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

public interface Int extends Mono, Ring.O<Int> {

    @Override
    Int clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    Long jvm();

    default Int jvm(final Long jvm) {
        return this.clone(jvm, this.tid(), this.vid());
    }

    default Int tid(final fURI tid) {
        return this.clone(this.jvm(), tid, this.vid());
    }

    default Int vid(final fURI vid) {
        return this.clone(this.jvm(), this.tid(), vid);
    }

    @Override
    default Int c(cInt c) {
        return (Int) Mono.super.c(c);
    }

    @Override
    default Int zero() {
        return jnt(0);
    }

    @Override
    default Int one() {
        return jnt(1);
    }

    @Override
    default Int plus(final Int rhs) {
        return this.jvm((this.intValue() * this.c().max()) + (rhs.intValue() * rhs.c().max())).c(cInt.ONE());
    }

    @Override
    default Int mult(final Int rhs) {
        return this.jvm((this.intValue() * this.c().max()) * (rhs.intValue() * rhs.c().max())).c(cInt.ONE());
    }

    @Override
    default Int neg() {
        return this.jvm(-1 * this.intValue());
    }

    public static final class IntType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(AS_INST_TID.dom(INT_TID).rng(A), lst(T(A)), (lhs, inst) -> {
                        if (inst.arg(0).matches(T(INT_TID)))
                            return lhs;
                        if (inst.arg(0).matches(T(REAL_TID)))
                            return real(lhs.intValue().doubleValue(), inst.arg(0).tid(), lhs.vid());
                        else if (inst.arg(0).matches(T(STR_TID)))
                            return str(lhs.intValue().toString(), inst.arg(0).tid(), lhs.vid());
                        else if (inst.arg(0).matches(T(URI_TID)))
                            return uri(f(lhs.intValue().toString()), inst.arg(0).tid(), lhs.vid());
                        else throw MTronException.of("no defined conversion for %s => %s", lhs.tid(), inst.arg(0).tid());
                    }),
                    instC(MULT_INST_TID.dom(INT_TID).rng(INT_TID), lst(T(INT_TID)), (lhs, inst) -> lhs.jvm(lhs.intValue() * inst.arg(0).intValue())),
                    instC(MINUS_INST_TID.dom(INT_TID).rng(INT_TID), lst(T(INT_TID)), (lhs, inst) -> lhs.jvm(lhs.intValue() - inst.arg(0).intValue())),
                    instC(PLUS_INST_TID.dom(INT_TID).rng(INT_TID), lst(T(INT_TID)), (lhs, inst) -> lhs.jvm(lhs.intValue() + inst.arg(0).intValue())),
                    instC(PLUS_INST_TID.dom(INT_TID.some()).rng(INT_TID.some()), lst(T(INT_TID)), (lhs, inst) -> objs(lhs.elements().map(i -> i.jvm(i.intValue() + inst.arg(0).intValue())))),
                    instC(GT_INST_TID.dom(INT_TID).rng(BOOL_TID), lst(T(INT_TID)), (lhs, inst) -> bool(lhs.intValue() > inst.arg(0).intValue())),
                    instC(GTE_INST_TID.dom(INT_TID).rng(BOOL_TID), lst(T(INT_TID)), (lhs, inst) -> bool(lhs.intValue() >= inst.arg(0).intValue())),
                    instC(LT_INST_TID.dom(INT_TID).rng(BOOL_TID), lst(T(INT_TID)), (lhs, inst) -> bool(lhs.intValue() < inst.arg(0).intValue())),
                    instC(LTE_INST_TID.dom(INT_TID).rng(BOOL_TID), lst(T(INT_TID)), (lhs, inst) -> bool(lhs.intValue() <= inst.arg(0).intValue())),
                    instC(SUM_INST_TID.dom(INT_TID.maybeSome()).rng(INT_TID), lst(), (lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> ((Int) a).plus((Int) b)).intValue()), jnt(0)),
                    instC(PROD_INST_TID.dom(INT_TID.maybeSome()).rng(INT_TID), lst(), (lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> jnt(a.intValue() * (b.intValue() * b.c().max()))).intValue()/* * inst.c().max()*/), jnt(1)),
                    instC(POW_INST_TID.dom(INT_TID).rng(INT_TID), lst(T(INT_TID)), (lhs, inst) -> jnt((long) Math.pow(lhs.intValue(), inst.arg(0).intValue())))
            ));
        }

    }
}
