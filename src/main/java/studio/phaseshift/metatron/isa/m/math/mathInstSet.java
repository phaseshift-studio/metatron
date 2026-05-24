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
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.Real;
import studio.phaseshift.metatron.isa.m.type.Type;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.as_;
import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@JREService(vid = "/m/math")
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
    public static final fURI MATH_BYTE_TID = MATH_ISA_TID.extend("bB");
    public static final fURI MATH_KBYTE_TID = MATH_ISA_TID.extend("kB");
    public static final fURI MATH_MBYTE_TID = MATH_ISA_TID.extend("mB");
    public static final fURI MATH_GBYTE_TID = MATH_ISA_TID.extend("gB");
    public static final fURI MATH_TBYTE_TID = MATH_ISA_TID.extend("tB");
    public static final fURI MATH_PBYTE_TID = MATH_ISA_TID.extend("pB");
    public static final fURI MATH_DATA_SIZE_TID = MATH_ISA_TID.extend("data_size");
    public static final String MATH_BYTE_STRING = "/m/math/bB";
    public static final String MATH_KBYTE_STRING = "/m/math/kB";
    public static final String MATH_MBYTE_STRING = "/m/math/mB";
    public static final String MATH_GBYTE_STRING = "/m/math/gB";
    public static final String MATH_TBYTE_STRING = "/m/math/tB";
    public static final String MATH_PBYTE_STRING = "/m/math/pB";
    /// ///////////////////////

    public static final fURI MATH_CURRENCY_TID = f("/m/math/currency");
    public static final fURI MATH_USD_TID = MATH_CURRENCY_TID.extend("usd");
    public static final fURI MATH_EURO_TID = MATH_CURRENCY_TID.extend("euro");
    public static final Type MATH_CURRENCY_TYPE = Type.Builder.build()
            .tid(REAL_TID)
            .vid(MATH_CURRENCY_TID)
            .create();

    static {
        assert MATH_BYTE_STRING.equals(MATH_BYTE_TID.toString());
        assert MATH_KBYTE_STRING.equals(MATH_KBYTE_TID.toString());
        assert MATH_MBYTE_STRING.equals(MATH_MBYTE_TID.toString());
        assert MATH_GBYTE_STRING.equals(MATH_GBYTE_TID.toString());
        assert MATH_TBYTE_STRING.equals(MATH_TBYTE_TID.toString());
        assert MATH_PBYTE_STRING.equals(MATH_PBYTE_TID.toString());
    }


    public mathInstSet() {
        super(mutableMap(uri(PATTERN), uri(MATH_ISA_TID.extend(HASH_FURI))), INSTSET_TID, MATH_ISA_TID);
    }

    public static final Type DATA_SIZE_TYPE = Type.Builder.build()
            .tid(REAL_TID)
            .vid(MATH_DATA_SIZE_TID)
            .create();

    public static final Type BYTE_TYPE = Type.Builder.build()
            .tid(MATH_DATA_SIZE_TID)
            .vid(MATH_BYTE_TID)
            .constructor(lhs -> {
                final Real arg = lhs.asReal();
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_KBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d);
                    case MATH_MBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d * 1024.0d);
                    case MATH_GBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d * 1024.0d * 1024.0d);
                    case MATH_TBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d * 1024.0d * 1024.0d * 1024.0d);
                    case MATH_PBYTE_STRING ->
                            arg.jvm(arg.asReal().jvm() * 1024.0d * 1024.0d * 1024.0d * 1024.0d * 1024.0d);
                    default -> arg;
                };
            }).create();

    public static final Type KBYTE_TYPE = Type.Builder.build()
            .tid(MATH_DATA_SIZE_TID)
            .vid(MATH_KBYTE_TID)
            .constructor(lhs -> {
                final Real arg = lhs.asReal();
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_BYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d);
                    case MATH_MBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d);
                    case MATH_GBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d * 1024.0d);
                    case MATH_TBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d * 1024.0d * 1024.0d);
                    case MATH_PBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d * 1024.0d * 1024.0d * 1024.0d);
                    default -> arg;
                };
            }).create();

    public static final Type MBYTE_TYPE = Type.Builder.build()
            .tid(MATH_DATA_SIZE_TID)
            .vid(MATH_MBYTE_TID)
            .constructor(lhs -> {
                final Real arg = lhs.asReal();
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_BYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d / 1024.0d);
                    case MATH_KBYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d);
                    case MATH_GBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d);
                    case MATH_TBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d * 1024.0d);
                    case MATH_PBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d * 1024.0d * 1024.0d);
                    default -> arg;
                };
            }).create();

    public static final Type GBYTE_TYPE = Type.Builder.build()
            .tid(MATH_DATA_SIZE_TID)
            .vid(MATH_GBYTE_TID)
            .constructor(lhs -> {
                final Real arg = lhs.asReal();
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_BYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d / 1024.0d / 1024.0d);
                    case MATH_KBYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d / 1024.0d);
                    case MATH_MBYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d);
                    case MATH_TBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d);
                    case MATH_PBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d * 1024.0d);
                    default -> arg;
                };
            }).create();

    public static final Type TBYTE_TYPE = Type.Builder.build()
            .tid(MATH_DATA_SIZE_TID)
            .vid(MATH_TBYTE_TID)
            .constructor(lhs -> {
                final Real arg = lhs.asReal();
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_BYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d / 1024.0d / 1024.0d / 1024.0d);
                    case MATH_KBYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d / 1024.0d / 1024.0d);
                    case MATH_MBYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d / 1024.0d);
                    case MATH_GBYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d);
                    case MATH_PBYTE_STRING -> arg.jvm(arg.asReal().jvm() * 1024.0d);
                    default -> arg;
                };
            }).create();

    public static final Type PBYTE_TYPE = Type.Builder.build()
            .tid(MATH_DATA_SIZE_TID)
            .vid(MATH_PBYTE_TID)
            .constructor(lhs -> {
                final Real arg = lhs.asReal();
                final String tid = arg.tid().toString();
                return switch (tid) {
                    case MATH_BYTE_STRING ->
                            arg.jvm(arg.asReal().jvm() / 1024.0d / 1024.0d / 1024.0d / 1024.0d / 1024.0d);
                    case MATH_KBYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d / 1024.0d / 1024.0d / 1024.0d);
                    case MATH_MBYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d / 1024.0d / 1024.0d);
                    case MATH_GBYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d / 1024.0d);
                    case MATH_TBYTE_STRING -> arg.jvm(arg.asReal().jvm() / 1024.0d);
                    default -> arg;
                };
            }).create();

    public void setup() {
        this.jvm().putAll(mutableMap(
                uri(PATTERN), uri(MATH_ISA_TID.extend(ALL)),
                uri(TYPE), lst(DATA_SIZE_TYPE,
                        docWrap(BYTE_TYPE, "a byte of data"),
                        docWrap(KBYTE_TYPE, "a kilobyte (1024 bytes) of data"),
                        docWrap(MBYTE_TYPE, "a megabyte (1024 kilobytes) of data"),
                        docWrap(GBYTE_TYPE, "a gigabyte (1024 megabytes) of data"),
                        docWrap(TBYTE_TYPE, "a terabyte (1024 gigabytes) of data"),
                        docWrap(PBYTE_TYPE, "a petabyte (1024 terabytes) of data"),
                        docWrap(MATH_CURRENCY_TYPE, "a currency amount"),
                        docWrap(Type.Builder.build().tid(MATH_CURRENCY_TID).vid(MATH_USD_TID).create(), "united states currency"),
                        docWrap(Type.Builder.build().tid(MATH_CURRENCY_TID).vid(MATH_EURO_TID).create(), "european union currency")),
                uri(INST), lst(
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
                        instC(MATH_ROUND_INST_TID.dom(ALL.maybe()).rng(INT_TID), lst(REAL_TYPE), (lhs, inst) -> jnt(Math.round(inst.arg(0).realValue())))),
                uri(CONST), lst(real(Math.E, REAL_TID, MATH_ISA_TID.extend("e").constant()), real(Math.PI, REAL_TID, MATH_ISA_TID.extend("pi").constant()))));
        docWrap(this, "the collection of mathematical instructions, algebraic and numeric data types, and associated constants");
        super.setup();
    }

}
