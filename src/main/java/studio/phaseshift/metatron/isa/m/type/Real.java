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

import net.objecthunter.exp4j.ExpressionBuilder;
import studio.phaseshift.metatron.algebra.MultGroup;
import studio.phaseshift.metatron.algebra.Ring;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.MathUtil;

import java.util.*;
import java.util.stream.Collectors;

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
    Type REAL_TYPE = Type.Builder.build().tid(REAL_TID).vid(REAL_TID).create();

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

    final class TypeObj {

        Type REAL_TYPE = T(REAL_TID);

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(AS_INST_TID.dom(REAL_TID).rng(INT_TID), lst(T(INT_TID)), (lhs, inst) -> jnt(lhs.realValue().longValue(), inst.arg(0).tid(), lhs.vid())),
                    instC(AS_INST_TID.dom(REAL_TID).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> str(String.valueOf(lhs.realValue()), inst.arg(0).tid(), lhs.vid())),
                    instC(GT_INST_TID.dom(REAL_TID).rng(BOOL_TID), lst(T(REAL_TID)), (lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.realValue() > inst.arg(0).realValue()).isPresent())),
                    instC(GTE_INST_TID.dom(REAL_TID).rng(BOOL_TID), lst(T(REAL_TID)), (lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.realValue() >= inst.arg(0).realValue()).isPresent())),
                    instC(LT_INST_TID.dom(REAL_TID).rng(BOOL_TID), lst(T(REAL_TID)), (lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.realValue() < inst.arg(0).realValue()).isPresent())),
                    instC(LTE_INST_TID.dom(REAL_TID).rng(BOOL_TID), lst(T(REAL_TID)), (lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.realValue() <= inst.arg(0).realValue()).isPresent())),
                    instC(NEG_INST_TID.dom(REAL_TID).rng(REAL_TID), lst(), (lhs, inst) -> lhs.asReal().neg()),
                    instC(PLUS_INST_TID.dom(REAL_TID).rng(REAL_TID), lst(T(REAL_TID)), (lhs, inst) -> lhs.asReal().plus(Inst.Helper.alignRHSType(lhs, inst.arg(0)).asReal())),
                    instC(MULT_INST_TID.dom(REAL_TID).rng(REAL_TID), lst(T(REAL_TID)), (lhs, inst) -> lhs.asReal().mult(Inst.Helper.alignRHSType(lhs, inst.arg(0)).asReal())),
                    instC(DIV_INST_TID.dom(REAL_TID).rng(REAL_TID), lst(T(REAL_TID)), (lhs, inst) -> lhs.asReal().div(Inst.Helper.alignRHSType(lhs, inst.arg(0)).asReal())),
                    instC(ZERO_INST_TID.dom(REAL_TID).rng(REAL_TID), lst(), (lhs, inst) -> lhs.asReal().zero()),
                    instC(ONE_INST_TID.dom(REAL_TID).rng(REAL_TID), lst(), (lhs, inst) -> lhs.asReal().one()),
                    instC(INV_INST_TID.dom(REAL_TID).rng(REAL_TID), lst(), (lhs, inst) -> lhs.asReal().inv()),
                    instC(MINUS_INST_TID.dom(REAL_TID).rng(REAL_TID), lst(T(REAL_TID)), (lhs, inst) -> lhs.asReal().minus(Inst.Helper.alignRHSType(lhs, inst.arg(0)).asReal())),
                    instC(SUM_INST_TID.dom(REAL_TID.maybeSome()).rng(REAL_TID), lst(), (lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> ((Real) a).plus((Real) b)).realValue()), real(0.0)),
                    instC(PROD_INST_TID.dom(REAL_TID.maybeSome()).rng(REAL_TID), lst(), (lhs, inst) -> lhs.stream().reduce(inst.seed(), (a, b) -> real(a.realValue() * (b.realValue() * b.c().max()))), real(1.0)),
                    docWrap(instC(MEAN_INST_TID.dom(REAL_TID.maybeSome()).rng(REAL_TID), lst(), (lhs, inst) -> real(lhs.stream().mapToDouble(Obj::realValue).average().orElse(0.0))),
                            "a stream of reals", 
                            "the mean of the lhs real stream", 
                            Map.of(), "the mean of a stream of reals","{1.0,2.0,3.0}.mean() [-- 2.0 --]"),
                    instC(POW_INST_TID.dom(REAL_TID).rng(REAL_TID), lst(T(REAL_TID)), (lhs, inst) -> real(Math.pow(lhs.realValue(), inst.arg(0).realValue()))),
                    instC(MATH_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(T(STR_TID)), (lhs, inst) -> {
                        final String equation = inst.arg(0).strValue();
                        final Set<String> variables = MathUtil.getVariables(equation);
                        final double result = new ExpressionBuilder(equation)
                                .variables(MathUtil.getVariables(equation))
                                .build()
                                .setVariables(variables.stream()
                                        .map(var -> List.of(var, Router.readFromSpace(var).<Number>jvm().doubleValue()))
                                        .collect(Collectors.toMap(
                                                a -> a.get(0).toString(),
                                                b -> (Double) b.get(1),
                                                (a, b) -> b,
                                                HashMap::new)))
                                .evaluate();
                        return real(result);
                    }),
                    instC(ORDER_INST_TID.dom(REAL_TID.maybeSome()).rng(LST_TID), lst(), (lhs, inst) -> lst(lhs.stream().sorted(Comparator.comparing(a -> a.asReal().realValue()))))));
        }
    }

}