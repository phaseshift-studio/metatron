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

package studio.phaseshift.metatron.isa.m.math;

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.ServiceMetadata;
import studio.phaseshift.metatron.isa.m.type.impl.AbstractInstSet;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.as_;
import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@ServiceMetadata(tid = "/m/math")
public class mathInstSet extends AbstractInstSet {

    public static final fURI MATH_ISA_TID = M_ISA_TID.extend("math");
    public static final fURI MATH_INST_TID = MATH_ISA_TID.extend("inst");
    public static final fURI MATH_COS_INST_TID = MATH_INST_TID.extend("cos");
    public static final fURI MATH_SIN_INST_TID = MATH_INST_TID.extend("sin");
    public static final fURI MATH_TAN_INST_TID = MATH_INST_TID.extend("tan");
    public static final fURI MATH_SQRT_INST_TID = MATH_INST_TID.extend("sqrt");
    public static final fURI MATH_ATAN_INST_TID = MATH_INST_TID.extend("atan");
    public static final fURI MATH_ATAN2_INST_TID = MATH_INST_TID.extend("atan2");
    public static final fURI MATH_LOG_INST_TID = MATH_INST_TID.extend("log");
    public static final fURI MATH_LOG10_INST_TID = MATH_INST_TID.extend("log10");
    public static final fURI MATH_EXP_INST_TID = MATH_INST_TID.extend("exp");
    public static final fURI MATH_ABS_INST_TID = MATH_INST_TID.extend("abs");
    public static final fURI MATH_CEIL_INST_TID = MATH_INST_TID.extend("ceil");
    public static final fURI MATH_FLOOR_INST_TID = MATH_INST_TID.extend("floor");
    public static final fURI MATH_ROUND_INST_TID = MATH_INST_TID.extend("round");
    public static final fURI MATH_POW_INST_TID = MATH_INST_TID.extend("pow");

    public mathInstSet() {
        super(MATH_ISA_TID, MATH_ISA_TID);
    }


    public Set<Inst> insts() {
        return new LinkedHashSet<>(List.of(
                instC(MATH_COS_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(as_(REAL_TYPE).tryToInst()), (lhs, inst) -> real(Math.cos(inst.arg(0).realValue()))),
                instC(MATH_SIN_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.sin(inst.arg(0).realValue()))),
                instC(MATH_TAN_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.tan(inst.arg(0).realValue()))),
                instC(MATH_SQRT_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.sqrt(inst.arg(0).realValue()))),
                instC(MATH_POW_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.pow(lhs.realValue(), inst.arg(0).realValue()))),
                instC(MATH_ATAN_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.atan(inst.arg(0).realValue()))),
                instC(MATH_ATAN2_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE.c(cInt.of(2))), (lhs, inst) -> real(Math.atan2(inst.arg(0).take(cInt.ONE()).get0().realValue(), inst.arg(0).take(cInt.ONE()).get0().realValue()))),
                instC(MATH_LOG_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.log(inst.arg(0).realValue()))),
                instC(MATH_LOG10_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.log10(inst.arg(0).realValue()))),
                instC(MATH_EXP_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.exp(inst.arg(0).realValue()))),
                instC(MATH_ABS_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.abs(inst.arg(0).realValue()))),
                instC(MATH_CEIL_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.ceil(inst.arg(0).realValue()))),
                instC(MATH_FLOOR_INST_TID.dom(ALL.maybe()).rng(REAL_TID), lst(REAL_TYPE), (lhs, inst) -> real(Math.floor(inst.arg(0).realValue()))),
                instC(MATH_ROUND_INST_TID.dom(ALL.maybe()).rng(INT_TID), lst(REAL_TYPE), (lhs, inst) -> jnt(Math.round(inst.arg(0).realValue())))
        ));
    }

    public Set<Obj> consts() {
        return new LinkedHashSet<>(List.of(
                real(Math.E, REAL_TID, MATH_ISA_TID.extend("e")),
                real(Math.PI, REAL_TID, MATH_ISA_TID.extend("pi"))
        ));
    }
}
