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
import studio.phaseshift.metatron.algebra.MultGroup;
import studio.phaseshift.metatron.algebra.Ring;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.util.MathUtil;

import java.util.*;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.REAL_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

public interface Real extends Mono, Ring.O<Real>, MultGroup.O<Real> {

    Real ZERO = real(0.0d);
    Real ONE = real(1.0d);

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

    @Override
    default Real zero() {
        return ZERO;
    }

    @Override
    default Real one() {
        return ONE;
    }

    @Override
    default Real inv() {
        return this.jvm(1.0d / this.realValue());
    }

    @Override
    default Real div(final Real rhs) {
        return this.jvm(this.realValue() / rhs.realValue());
    }

    @Override
    default Real plus(final Real rhs) {
        return this.jvm((this.realValue() * this.c().max()) + (rhs.realValue() * rhs.c().max())).c(cInt.ONE());
    }

    @Override
    default Real mult(final Real rhs) {
        return this.jvm((this.realValue() * this.c().max()) * (rhs.realValue() * rhs.c().max())).c(cInt.ONE());
    }

    @Override
    default Real neg() {
        return this.jvm(-1.0d * this.realValue());
    }

    final class RealType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    docWrap(instC(AS_INST_TID.dom(REAL_TID).rng(Tokens.INT_TID), lst(T(Tokens.INT_TID)), (lhs, inst) -> jnt(lhs.realValue().longValue(), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid())), "a real", "the lhs real truncated to an int", Map.of(jnt(0), "the target type (int::T)"), "an int function: the lhs real truncated to an int"),
                    docWrap(instC(AS_INST_TID.dom(REAL_TID).rng(Tokens.STR_TID), lst(T(Tokens.STR_TID)), (lhs, inst) -> str(String.valueOf(lhs.realValue()), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid())), "a real", "the lhs real as a str", Map.of(jnt(0), "the target type (str::T)"), "a str function: the lhs real rendered as a str"),
                    docWrap(instC(GT_INST_TID.dom(REAL_TID).rng(Tokens.BOOL_TID), lst(T(REAL_TID)), (lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.realValue() > inst.arg(0).realValue()).isPresent())), "a real", "true if the lhs real is greater than the arg real", Map.of(jnt(0), "the rhs real"), "a comparison function: true if the lhs real is greater than the rhs real"),
                    docWrap(instC(GTE_INST_TID.dom(REAL_TID).rng(Tokens.BOOL_TID), lst(T(REAL_TID)), (lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.realValue() >= inst.arg(0).realValue()).isPresent())), "a real", "true if the lhs real is greater than or equal to the arg real", Map.of(jnt(0), "the rhs real"), "a comparison function: true if the lhs real is greater than or equal to the rhs real"),
                    docWrap(instC(LT_INST_TID.dom(REAL_TID).rng(Tokens.BOOL_TID), lst(T(REAL_TID)), (lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.realValue() < inst.arg(0).realValue()).isPresent())), "a real", "true if the lhs real is less than the arg real", Map.of(jnt(0), "the rhs real"), "a comparison function: true if the lhs real is less than the rhs real"),
                    docWrap(instC(LTE_INST_TID.dom(REAL_TID).rng(Tokens.BOOL_TID), lst(T(REAL_TID)), (lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.realValue() <= inst.arg(0).realValue()).isPresent())), "a real", "true if the lhs real is less than or equal to the arg real", Map.of(jnt(0), "the rhs real"), "a comparison function: true if the lhs real is less than or equal to the rhs real"),
                    docWrap(instC(NEG_INST_TID.dom(REAL_TID).rng(REAL_TID), lst(), (lhs, inst) -> lhs.asReal().neg()), "a real", "the negated lhs real", Map.of(), "a negation function \\(f(x) \nearrow -x\\): the additive inverse of the lhs real"),
                    docWrap(instC(PLUS_INST_TID.dom(REAL_TID).rng(REAL_TID), lst(T(REAL_TID)), (lhs, inst) -> lhs.asReal().plus(Inst.Helper.alignRHSType(lhs, inst.arg(0)).asReal())), "a real", "the sum", Map.of(jnt(0), "the rhs real"), "a sum function \\(f(x, y) \nearrow x + y\\): adds the rhs real to the lhs real"),
                    docWrap(instC(MULT_INST_TID.dom(REAL_TID).rng(REAL_TID), lst(T(REAL_TID)), (lhs, inst) -> lhs.asReal().mult(Inst.Helper.alignRHSType(lhs, inst.arg(0)).asReal())), "a real", "the product", Map.of(jnt(0), "the rhs real"), "a product function \\(f(x, y) \nearrow xy\\): multiplies the lhs real by the rhs real"),
                    docWrap(instC(DIV_INST_TID.dom(REAL_TID).rng(REAL_TID), lst(T(REAL_TID)), (lhs, inst) -> lhs.asReal().div(Inst.Helper.alignRHSType(lhs, inst.arg(0)).asReal())), "a real", "the quotient", Map.of(jnt(0), "the rhs real"), "a quotient function \\(f(x, y) \nearrow x/y\\): the lhs real divided by the rhs real"),
                    docWrap(instC(ZERO_INST_TID.dom(REAL_TID).rng(REAL_TID), lst(), (lhs, inst) -> lhs.asReal().zero()), "a real", "the real zero", Map.of(), "a zero function: the real zero (0.0)"),
                    docWrap(instC(ONE_INST_TID.dom(REAL_TID).rng(REAL_TID), lst(), (lhs, inst) -> lhs.asReal().one()), "a real", "the real one", Map.of(), "a one function: the real one (1.0)"),
                    docWrap(instC(INV_INST_TID.dom(REAL_TID).rng(REAL_TID), lst(), (lhs, inst) -> lhs.asReal().inv()), "a real", "the multiplicative inverse", Map.of(), "an inverse function \\(f(x) \nearrow 1/x\\): the multiplicative inverse of the lhs real"),
                    docWrap(instC(MINUS_INST_TID.dom(REAL_TID).rng(REAL_TID), lst(T(REAL_TID)), (lhs, inst) -> lhs.asReal().minus(Inst.Helper.alignRHSType(lhs, inst.arg(0)).asReal())), "a real", "the difference", Map.of(jnt(0), "the rhs real"), "a difference function \\(f(x, y) \nearrow x - y\\): the rhs real subtracted from the lhs real"),
                    docWrap(instC(SUM_INST_TID.dom(REAL_TID.maybeSome()).rng(REAL_TID), lst(), (lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> real(a.realValue() + (b.realValue() * b.c().max().doubleValue()))).realValue()), real(0.0)), "a stream of reals", "the sum of the lhs reals", Map.of(), "a sum function: the additive reduction of the lhs reals"),
                    docWrap(instC(PROD_INST_TID.dom(REAL_TID.maybeSome()).rng(REAL_TID), lst(), (lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> ((Real) a).mult(b.asReal())).realValue()), real(1.0)), "a stream of reals", "the product of the lhs reals", Map.of(), "a product function: the multiplicative reduction of the lhs reals"),
                    docWrap(instC(MEAN_INST_TID.dom(REAL_TID.maybeSome()).rng(REAL_TID), lst(), (lhs, inst) -> real(lhs.stream().mapToDouble(Obj::realValue).average().orElse(0.0))),  // TODO: don't we need c involved?
                            "a stream of reals",
                            "the mean of the lhs real stream",
                            Map.of(), "the mean of a stream of reals", "{1.0,2.0,3.0}.mean() [-- 2.0 --]"),
                    docWrap(instC(POW_INST_TID.dom(REAL_TID).rng(REAL_TID), lst(T(REAL_TID)), (lhs, inst) -> real(Math.pow(lhs.realValue(), inst.arg(0).realValue()))), "a real", "the lhs real raised to the arg real", Map.of(jnt(0), "the exponent real"), "a power function \\(f(x, n) \nearrow x^n\\): the lhs real raised to the arg real"),
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