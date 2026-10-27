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

package studio.phaseshift.metatron.isa.m.type;

import studio.phaseshift.metatron.algebra.Ring;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.Law;
import studio.phaseshift.metatron.isa.m.type.impl.InstBuilder;
import studio.phaseshift.metatron.isa.m.type.impl.MInt;

import java.nio.ByteBuffer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 *
 */
public interface Int extends Mono, Ring.O<Int> {

    Int ZERO = jnt(0L);
    Int ONE = jnt(1L);

    // Int with coefficient {0}: noobj typed as an Int, for a method that answers an Int or nothing.
    // the coefficient says absent, so the payload is never read: null
    Int INT_NOOBJ = new MInt(null, INT_TID.zero(), null);

    static Int int_noobj() {
        return INT_NOOBJ;
    }

    public static Int int0() {
        return jnt(0, INT_TID.zero(), null);
    }

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

    /*
     * WHAT REMAINS HERE, and why: an entry whose law-table row carries a RELATION the builder cannot express yet.
     * The `as` conversions declare an opposing edge (a pair, and the direction is the claim), `mult` declares the
     * one-way retraction to `div`, `minus` declares its defining equation (a Derivation), and `prod`'s body needs a
     * helper this slice cannot import yet. Their law rows stay in mInstSetLawTable; everything the builder CAN say
     * lives in mInsts.ints() -- and these retire as InstBuilder grows `inverse`/`derivation`.
     */
    final class IntType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(AS_INST_TID.dom(INT_TID).rng(BOOL_TID), lst(BOOL_TYPE), (lhs, inst) -> bool(lhs.intValue() > 0, inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid())),
                    instC(AS_INST_TID.dom(INT_TID).rng(BYTES_TID), lst(BYTES_TYPE), (lhs, inst) -> bytes(ByteBuffer.allocate(8).putLong(lhs.intValue()), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid())),
                    instC(AS_INST_TID.dom(INT_TID).rng(REAL_TID), lst(T(REAL_TID)), (lhs, inst) -> real(lhs.intValue().doubleValue(), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid())),
                    instC(AS_INST_TID.dom(INT_TID).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> str(lhs.intValue().toString(), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid())),
                    instC(AS_INST_TID.dom(INT_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(f(lhs.intValue().toString()), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid())),
                    docWrap(instC(MULT_INST_TID.dom(INT_TID).rng(INT_TID), lst(T(INT_TID)), (lhs, inst) -> lhs.jvm(lhs.intValue() * inst.arg(0).intValue())), "the lhs int", "the result of the multiplication", Map.of(INT_TYPE, "the int to multiply the lhs by"), "multiply the lhs int by the argument int"),
                    docWrap(instC(MINUS_INST_TID.dom(INT_TID).rng(INT_TID), lst(T(INT_TID)), (lhs, inst) -> lhs.jvm(lhs.intValue() - inst.arg(0).intValue())), "the lhs int", "the result of the subtraction", Map.of(INT_TYPE, "the int to subtract from the lhs"), "subtract the argument int from the lhs int"),
                    instC(PROD_INST_TID.dom(INT_TID.maybeSome()).rng(INT_TID), lst(), (lhs, inst) -> inst.seed().jvm(lhs.stream().map(l -> l.c().isOne() ? l.intValue() : Math.powExact(l.intValue(), l.c().max().intValue())).reduce(inst.seed().intValue(), (a, b) -> a * b)), jnt(1)),
                    InstBuilder.build().tid(PLUS_INST_TID).dom(INT_TID).rng(INT_TID)
                            .argLst(INT_TYPE)
                            .body((lhs, inst) -> lhs.jvm(lhs.intValue() + inst.arg(0).intValue()))
                            .docDom("the lhs int")
                            .docRng("the result of the addition")
                            .argDocs(Map.of(INT_TYPE, "the int to add to the lhs"))
                            .docDesc("add the argument int to the lhs int")
                            .examples("1.plus(2)          [-- 3                    --]",
                                    "1+2                [-- 3 sugar'd form       --]",
                                    "1.plus(plus(1))    [-- 3 nested application --]",
                                    "1+(+1)             [-- 3 sugar nested form  --]")
                            .law(Law.commutative, Law.right_distributive, Law.action, Law.pure)
                            .create()
            ));
        }
    }
}
