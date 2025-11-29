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

import net.objecthunter.exp4j.ExpressionBuilder;
import studio.phaseshift.metatron.algebra.Ring;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.util.MathUtil;
import studio.phaseshift.metatron.lang.sys.router.Router;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.*;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;

public interface Real extends Mono, Ring.O<Real> {

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

    @Override
    default Real c(final cInt c) {
        return (Real) Mono.super.c(c);
    }

    @Override
    default Real zero() {
        return real(0.0d);
    }

    @Override
    default Real one() {
        return real(1.0d);
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

    public static final class RealType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(GT_INST_TID.dom(REAL_TID).rng(BOOL_TID), lst(T(REAL_TID)), (lhs, inst) -> bool(lhs.realValue() > inst.arg(0).realValue())),
                    instC(GTE_INST_TID.dom(REAL_TID).rng(BOOL_TID), lst(T(REAL_TID)), (lhs, inst) -> bool(lhs.realValue() >= inst.arg(0).realValue())),
                    instC(LT_INST_TID.dom(REAL_TID).rng(BOOL_TID), lst(T(REAL_TID)), (lhs, inst) -> bool(lhs.realValue() < inst.arg(0).realValue())),
                    instC(LTE_INST_TID.dom(REAL_TID).rng(BOOL_TID), lst(T(REAL_TID)), (lhs, inst) -> bool(lhs.realValue() <= inst.arg(0).realValue())),
                    instC(PLUS_INST_TID.dom(REAL_TID).rng(REAL_TID), lst(T(REAL_TID)), (lhs, inst) -> lhs.jvm(lhs.realValue() + inst.arg(0).realValue())),
                    instC(MULT_INST_TID.dom(REAL_TID).rng(REAL_TID), lst(T(REAL_TID)), (lhs, inst) -> lhs.jvm(lhs.realValue() * inst.arg(0).realValue())),
                    instC(SUM_INST_TID.dom(REAL_TID.maybeSome()).rng(REAL_TID), lst(), (lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> ((Real) a).plus((Real) b)).realValue()), real(0.0)),
                    instC(PROD_INST_TID.dom(REAL_TID.maybeSome()).rng(REAL_TID), lst(), (lhs, inst) -> lhs.stream().reduce(inst.seed(), (a, b) -> real(a.realValue() * (b.realValue() * b.c().max()))), real(1.0)),
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
                    })));
        }
    }

}