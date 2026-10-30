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

import net.objecthunter.exp4j.ExpressionBuilder;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.impl.MReal;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.util.MathUtil;

import java.util.*;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.REAL_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

public interface Real extends Mono {

    Real ZERO = real(0.0d);
    Real ONE = real(1.0d);

    // Real with coefficient {0}: noobj typed as a Real, for a method that answers a Real or nothing.
    Real REAL_NOOBJ = new MReal(null, REAL_TID.zero(), null);

    static Real real_noobj() {
        return REAL_NOOBJ;
    }

    public static Real real0() {
        return real(0.0, REAL_TID.zero(), null);
    }

    @Override
    Real clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    Double jvm();

    default Real jvm(final Double jvm) {
        return this.clone(jvm, this.tid(), this.vid());
    }

    default Real tid(final fURI tid) {
        return this.clone(this.jvm(), tid, this.vid());
    }

    default Real vid(final fURI vid) {
        return this.clone(this.jvm(), this.tid(), vid);
    }

    Real self(final Double jvm, final fURI tid, final fURI vid);

    @Override
    default Real c(final cInt c) {
        return (Real) Mono.super.c(c);
    }


    default Real zero() {
        return ZERO;
    }

    default Real one() {
        return ONE;
    }

    default Real inv() {
        return this.jvm(1.0d / this.realValue());
    }

    default Real div(final Real rhs) {
        return this.jvm(this.realValue() / rhs.realValue());
    }

    default Real plus(final Real rhs) {
        return this.jvm((this.realValue() * this.c().max()) + (rhs.realValue() * rhs.c().max())).c(cInt.ONE());
    }

    default Real minus(final Real rhs) {
        return this.jvm((this.realValue() * this.c().max()) - (rhs.realValue() * rhs.c().max())).c(cInt.ONE());
    }

    default Real mult(final Real rhs) {
        return this.jvm((this.realValue() * this.c().max()) * (rhs.realValue() * rhs.c().max())).c(cInt.ONE());
    }

    default Real neg() {
        return this.jvm(-1.0d * this.realValue());
    }

    /*
     * WHAT REMAINS HERE, and why: the ROW-LESS `as` conversion real->int (no law-table row, hence no claim and no
     * inverse) and the `math`/`order` bodies, which reach for MathUtil/ExpressionBuilder/Machine and
     * MLst.lst/Comparator. THE ROWED PAIR IS GONE: real->str is the SOUND direction (the printed decimal reads
     * back to the same double) and is declared WITH its inverse beside the other real operations in
     * mInsts.reals(); str->real is LOSSY and declares no inverse anywhere. The rest of real's operations live in
     * mInsts.reals(), `div` and `minus` included (their `derivation` is what lets the rows retire).
     */
    final class RealType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    docWrap(instC(AS_INST_TID.dom(REAL_TID).rng(Tokens.INT_TID), lst(T(Tokens.INT_TID)), (lhs, inst) -> jnt(lhs.realValue().longValue(), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid())), "a real", "the lhs real truncated to an int", Map.of(jnt(0), "the target type (int::T)"), "an int function: the lhs real truncated to an int"),
                    docWrap(instC(MATH_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(T(Tokens.STR_TID)), (lhs, inst) -> {
                                final String equation = inst.arg(0).strValue();
                                final Set<String> variables = MathUtil.getVariables(equation);
                                final double result = new ExpressionBuilder(equation)
                                        .variables(MathUtil.getVariables(equation))
                                        .build()
                                        .setVariables(variables.stream()
                                                .map(var -> List.of(var, Machine.read(var).<Number>jvm().doubleValue()))
                                                .collect(Collectors.toMap(
                                                        a -> a.get(0).toString(),
                                                        b -> (Double) b.get(1),
                                                        (a, b) -> b,
                                                        HashMap::new)))
                                        .evaluate();
                                return real(result);
                            }),
                            "a real", "the value of the arg math expression", Map.of(jnt(0), "the math expression to evaluate"), "a math function: evaluates the arg math expression over the lhs real, reading named variables from space"),
                    docWrap(instC(ORDER_INST_TID.dom(REAL_TID.maybeSome()).rng(Tokens.LST_TID), lst(), (lhs, inst) -> lst(lhs.stream().sorted(Comparator.comparing(a -> a.asReal().realValue())))), "a stream of reals", "the lhs reals sorted in ascending order", Map.of(), "an ordering function: sorts the lhs reals in natural (ascending) order")));
        }
    }

}
