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

package studio.phaseshift.metatron.isa.m.type;

import studio.phaseshift.metatron.algebra.Ring;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;

import java.nio.ByteBuffer;
import java.util.*;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.DocQ.Doc.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Bytes.BYTES_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 *
 */
public interface Int extends Mono, Ring.O<Int> {

    
    Type INT_TYPE = Type.Builder.build().tid(INT_TID).vid(INT_TID).create();
    Int ZERO = jnt(0L);
    Int ONE = jnt(1L);
    
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
        return ZERO;
    }

    @Override
    default Int one() {
        return ONE;
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

    Int self(final Long jvm, final fURI tid, final fURI vid);
    
    public static final class IntType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(AS_INST_TID.dom(INT_TID).rng(BOOL_TID), lst(BOOL_TYPE), (lhs, inst) -> bool(lhs.intValue() > 0, inst.arg(0).tid(), lhs.vid())),
                    instC(AS_INST_TID.dom(INT_TID).rng(BYTES_TID), lst(BYTES_TYPE), (lhs, inst) -> bytes(ByteBuffer.allocate(8).putLong(lhs.intValue()), inst.arg(0).tid(), lhs.vid())),
                    instC(AS_INST_TID.dom(INT_TID).rng(REAL_TID), lst(T(REAL_TID)), (lhs, inst) -> real(lhs.intValue().doubleValue(), inst.arg(0).tid(), lhs.vid())),
                    instC(AS_INST_TID.dom(INT_TID).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> str(lhs.intValue().toString(), inst.arg(0).tid(), lhs.vid())),
                    instC(AS_INST_TID.dom(INT_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(f(lhs.intValue().toString()), inst.arg(0).tid(), lhs.vid())),
                    instC(ZERO_INST_TID.dom(INT_TID).rng(INT_TID), lst(), (lhs, _) -> lhs.asInt().zero()),
                    instC(ONE_INST_TID.dom(INT_TID).rng(INT_TID), lst(), (lhs, _) -> lhs.asInt().one()),
                    instC(NEG_INST_TID.dom(INT_TID).rng(INT_TID), lst(), (lhs, _) -> lhs.asInt().neg()),
                    docWrap(instC(MULT_INST_TID.dom(INT_TID).rng(INT_TID), lst(T(INT_TID)), (lhs, inst) -> lhs.jvm(lhs.intValue() * inst.arg(0).intValue())), "the lhs int", "the result of the multiplication", Map.of(INT_TYPE, "the int to multiply the lhs by"), "multiply the lhs int by the argument int"),
                    docWrap(instC(MINUS_INST_TID.dom(INT_TID).rng(INT_TID), lst(T(INT_TID)), (lhs, inst) -> lhs.jvm(lhs.intValue() - inst.arg(0).intValue())), "the lhs int", "the result of the subtraction", Map.of(INT_TYPE, "the int to subtract from the lhs"), "subtract the argument int from the lhs int"),
                    docWrap(instC(PLUS_INST_TID.dom(INT_TID).rng(INT_TID), lst(INT_TYPE), (lhs, inst) -> lhs.jvm(lhs.intValue() + inst.arg(0).intValue())), "the lhs int", "the result of the addition", Map.of(INT_TYPE, "the int to add to the lhs"), "add the argument int to the lhs int"),
                    docWrap(instC(PLUS_INST_TID.dom(INT_TID.some()).rng(INT_TID.some()), lst(T(INT_TID)), (lhs, inst) -> objs(lhs.elements().map(i -> i.jvm(i.intValue() + inst.arg(0).intValue())))), "the lhs int list", "the result of the addition", Map.of(INT_TYPE, "the int to add to each element of the lhs list"), "add the argument int to each element of the lhs int list"),
                    docWrap(instC(GT_INST_TID.dom(INT_TID).rng(BOOL_TID), lst(T(INT_TID)), (lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.intValue() > inst.arg(0).intValue()).isPresent())), "the lhs int", "whether the lhs is greater than the rhs", Map.of(INT_TYPE, "the int to compare against the lhs"), "check whether the lhs int is greater than the argument int"),
                    docWrap(instC(GTE_INST_TID.dom(INT_TID).rng(BOOL_TID), lst(T(INT_TID)), (lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.intValue() >= inst.arg(0).intValue()).isPresent())), "the lhs int", "whether the lhs is greater than or equal to the rhs", Map.of(INT_TYPE, "the int to compare against the lhs"), "check whether the lhs int is greater than or equal to the argument int"),
                    docWrap(instC(LT_INST_TID.dom(INT_TID).rng(BOOL_TID), lst(T(INT_TID)), (lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.intValue() < inst.arg(0).intValue()).isPresent())), "the lhs int", "whether the lhs is less than the rhs", Map.of(INT_TYPE, "the int to compare against the lhs"), "check whether the lhs int is less than the argument int"),
                    docWrap(instC(LTE_INST_TID.dom(INT_TID).rng(BOOL_TID), lst(T(INT_TID)), (lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.intValue() <= inst.arg(0).intValue()).isPresent())), "the lhs int", "whether the lhs is less than or equal to the rhs", Map.of(INT_TYPE, "the int to compare against the lhs"), "check whether the lhs int is less than or equal to the argument int"),
                    instC(SUM_INST_TID.dom(INT_TID.maybeSome()).rng(INT_TID), lst(), (lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> ((Int) a).plus((Int) b)).intValue()), jnt(0)),
                    instC(PROD_INST_TID.dom(INT_TID.maybeSome()).rng(INT_TID), lst(), (lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> jnt(a.intValue() * (b.intValue() * b.c().max()))).intValue()/* * inst.c().max()*/), jnt(1)),
                    instC(POW_INST_TID.dom(INT_TID).rng(INT_TID), lst(T(INT_TID)), (lhs, inst) -> jnt((long) Math.pow(lhs.intValue(), inst.arg(0).intValue()))),
                    instC(MOD_INST_TID.dom(INT_TID).rng(INT_TID), lst(INT_TYPE), (lhs,inst) -> jnt(lhs.intValue() % inst.arg(0).intValue())),
                    instC(ORDER_INST_TID.dom(INT_TID.maybeSome()).rng(LST_TID), lst(), (lhs, inst) -> lst(lhs.stream().sorted(Comparator.comparing(a -> a.asInt().intValue()))))
            ));
        }

    }
}
