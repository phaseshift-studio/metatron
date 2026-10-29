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
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 *
 */
public interface Int extends Mono {

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


    default Int zero() {
        return ZERO;
    }


    default Int one() {
        return ONE;
    }


    default Int plus(final Int rhs) {
        return this.jvm((this.intValue() * this.c().max()) + (rhs.intValue() * rhs.c().max())).c(cInt.ONE());
    }

    default Int mult(final Int rhs) {
        return this.jvm((this.intValue() * this.c().max()) * (rhs.intValue() * rhs.c().max())).c(cInt.ONE());
    }
    
    default Int neg() {
        return this.jvm(-1 * this.intValue());
    }

    Int self(final Long jvm, final fURI tid, final fURI vid);

    /*
     * WHAT REMAINS HERE, and why: the ROW-LESS `as` conversions. int->bool, int->bytes, int->real and int->uri
     * carry no law-table row -- hence no claim and no inverse -- so there is nothing for a slice to preserve yet;
     * they move to mInsts.ints() when their bodies' imports travel with them. THE ROWED PAIR IS GONE: int->str is
     * the SOUND direction (print an int, read the printed str back, and the value returns) and is declared WITH
     * its inverse beside the other int operations in mInsts.ints(), while str->int is LOSSY ("007" -> 7 -> "7")
     * and declares no inverse anywhere. `plus` below states its own contract and laws through InstBuilder; the
     * rest of int's operations live in mInsts.ints(), `mult` and `minus` included (their `inverse` and
     * `derivation` are what let the rows retire).
     */
    final class IntType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(AS_INST_TID.dom(INT_TID).rng(BOOL_TID), lst(BOOL_TYPE), (lhs, inst) -> bool(lhs.intValue() > 0, inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid())),
                    instC(AS_INST_TID.dom(INT_TID).rng(BYTES_TID), lst(BYTES_TYPE), (lhs, inst) -> bytes(ByteBuffer.allocate(8).putLong(lhs.intValue()), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid())),
                    instC(AS_INST_TID.dom(INT_TID).rng(REAL_TID), lst(T(REAL_TID)), (lhs, inst) -> real(lhs.intValue().doubleValue(), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid())),
                    instC(AS_INST_TID.dom(INT_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(f(lhs.intValue().toString()), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid())),
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
