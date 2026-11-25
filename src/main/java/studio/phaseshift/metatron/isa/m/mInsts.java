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

package studio.phaseshift.metatron.isa.m;

import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.Analyzer;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.mach.type.processor.monad.BasicStatefulMonad;
import studio.phaseshift.metatron.isa.m.mach.type.processor.monad.StatefulMonad;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.*;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.*;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.Str.Helper.REGEX_CACHE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE /m INSTRUCTIONS, DECLARED — one slice per carrier, in the shape {@code catInstSet}'s supporting classes use.
 * Every entry states all four facets in ONE chain, documentation colocated with what it documents: the CONTRACT
 * ({@code tid(tid, desc)}, {@code dom(dom, desc)}, {@code rng(rng, desc)}, {@code argLst(type, desc)}, {@code seed}),
 * the IMPLEMENTATION ({@code body}), the DOCS ({@code ?docq}, written by the builder) and the process LAWS
 * ({@code ?decq}, staged by the builder and published on first read). A type's algebra is declared at the type
 * ({@link mTypes}); this class declares what its OPERATIONS do.
 *
 * <p>WHAT STAYS IN A TYPE'S OWN CLASS: the retired LawTable's rows moved here as the builder learned to state their
 * laws, an {@code inverse} (the sound {@code as} directions) and a {@code derivation} (int's {@code minus}, real's
 * {@code minus}/{@code div}). An entry that remains in its type's class ({@code Int.IntType}, …) declares no law,
 * inverse or derivation, or has a body that needs a helper this slice cannot import; each of those classes says
 * which, and why.
 * <p>A DECLARATION IS KEYED BY THE INSTRUCTION'S N-TID ({@code tid(op).dom(A).rng(B)}): the builder stages it at that
 * address's {@code ?decq}, which is where {@link studio.phaseshift.metatron.algebra.Law.Helper} reads it, and
 * {@link studio.phaseshift.metatron.algebra.Law.Helper#derivations()} keeps one defining equation per address.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class mInsts {

    /**
     * repeat()'s probes in a profiled run: each pass, and the phases a pass is made of
     */
    private static final fURI REPEAT_LOOP_PROBE = f(REPEAT).extend(LOOP);
    private static final fURI REPEAT_STATE_PROBE = f(REPEAT).extend(STATE);
    private static final fURI REPEAT_ESC_PROBE = f(REPEAT).extend(ESC);
    private static final fURI REPEAT_EMIT_PROBE = f(REPEAT).extend(EMIT);
    private static final fURI REPEAT_UNTIL_PROBE = f(REPEAT).extend(UNTIL);
    private static final fURI REPEAT_CODE_PROBE = f(REPEAT).extend(CODE);

    private mInsts() {
    }

    /**
     * {@code int::T}'s operations: the ring's own, the comparisons, and the folds.
     */
    public static Set<Inst> int_insts() {
        return new LinkedHashSet<>(List.of(
                InstBuilder.build().tid(ZERO_INST_TID, "the zero of the int ring")
                        .dom(INT_TID, "the lhs int").rng(INT_TID, "zero").argLst()
                        .body((lhs, inst) -> lhs.asInt().zero())
                        .law(Law.absorbing, Law.idempotent, Law.pure)
                        .create(),
                InstBuilder.build().tid(ONE_INST_TID, "the one of the int ring")
                        .dom(INT_TID, "the lhs int").rng(INT_TID, "one").argLst()
                        .body((lhs, inst) -> lhs.asInt().one())
                        .law(Law.idempotent, Law.pure)
                        .create(),
                InstBuilder.build().tid(NEG_INST_TID, "the additive inverse of the int")
                        .dom(INT_TID, "the int to negate").rng(INT_TID, "the negated int").argLst()
                        .body((lhs, inst) -> lhs.asInt().neg())
                        .converse(NEG_INST_TID.dom(INT_TID).rng(INT_TID))
                        .law(Law.involution, Law.pure)
                        .create(),
                // NOT mult's inverse. Truncating division is not injective (7.div(2).mult(2) is 6, not 7), so no
                // left inverse exists; the declared relation is PARTIAL (undefined at 0) and the honest law is
                // `partial`, not `action` -- int models a mul_monoid, never a group.
                InstBuilder.build().tid(DIV_INST_TID, "divide the lhs int by the argument int")
                        .dom(INT_TID, "the dividend int").rng(INT_TID, "the truncated quotient")
                        .argLst(INT_TYPE, "the divisor int")
                        .body((lhs, inst) -> {
                            final long divisor = inst.arg(0).intValue();
                            if (0L == divisor)
                                return noobj();   // division by zero has no int answer: the claim is partial
                            return jnt(lhs.intValue() / divisor, lhs.tid(), lhs.vid());
                        })
                        // DERIVATION SUSPENDED (bisect: catInstSetTest)
                        .law(Law.partial, Law.pure)
                        .create(),
                InstBuilder.build().tid(POW_INST_TID, "raise the lhs int to the argument power")
                        .dom(INT_TID, "the base int").rng(INT_TID, "the power")
                        .argLst(T(INT_TID), "the exponent int")
                        .body((lhs, inst) -> jnt((long) Math.pow(lhs.intValue(), inst.arg(0).intValue())))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(MOD_INST_TID, "the remainder of the lhs int over the argument int")
                        .dom(INT_TID, "the dividend int").rng(INT_TID, "the remainder")
                        .argLst(INT_TYPE, "the modulus int")
                        .body((lhs, inst) -> jnt(lhs.intValue() % inst.arg(0).intValue()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(MEAN_INST_TID, "the mean of an int stream")
                        .dom(INT_TID.maybeSome(), "a stream of int")
                        .rng(REAL_TID.maybe(), "the mean of the lhs int stream, or noobj for none").argLst()
                        .form(Inst.Form.reducer)
                        .body((lhs, inst) -> mean(lhs, member -> member.intValue()))
                        .example("{1.0,2.0,3.0}.mean() [-- 2.0 --]")
                        .law(Law.pure)
                        .derivation(mean_(INT_TID), liftedMean(INT_TID))
                        .create(),
                InstBuilder.build().tid(GT_INST_TID, "check whether the lhs int is greater than the argument int")
                        .dom(INT_TID, "the lhs int").rng(BOOL_TID, "whether the lhs is greater than the rhs")
                        .argLst(INT_TYPE, "the int to compare against the lhs")
                        .body((lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.intValue() > inst.arg(0).intValue()).isPresent()))
                        .law(Law.poset, Law.pure)
                        .create(),
                InstBuilder.build().tid(GTE_INST_TID, "check whether the lhs int is greater than or equal to the argument int")
                        .dom(INT_TID, "the lhs int").rng(BOOL_TID, "whether the lhs is at least the rhs")
                        .argLst(INT_TYPE, "the int to compare against the lhs")
                        .body((lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.intValue() >= inst.arg(0).intValue()).isPresent()))
                        .law(Law.poset, Law.pure)
                        .create(),
                InstBuilder.build().tid(LT_INST_TID, "check whether the lhs int is less than the argument int")
                        .dom(INT_TID, "the lhs int").rng(BOOL_TID, "whether the lhs is less than the rhs")
                        .argLst(INT_TYPE, "the int to compare against the lhs")
                        .body((lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.intValue() < inst.arg(0).intValue()).isPresent()))
                        .law(Law.poset, Law.pure)
                        .create(),
                InstBuilder.build().tid(LTE_INST_TID, "check whether the lhs int is less than or equal to the argument int")
                        .dom(INT_TID, "the lhs int").rng(BOOL_TID, "whether the lhs is at most the rhs")
                        .argLst(INT_TYPE, "the int to compare against the lhs")
                        .body((lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.intValue() <= inst.arg(0).intValue()).isPresent()))
                        .law(Law.poset, Law.pure)
                        .create(),
                InstBuilder.build().tid(PROD_INST_TID, "multiply the lhs int stream into a single int")
                        .dom(INT_TID.maybeSome(), "zero or more ints").rng(INT_TID, "a single product reduced int")
                        .argLst().seed(jnt(1))
                        .body((lhs, inst) -> inst.seed().jvm(lhs.stream().map(l -> l.c().isOne() ? l.intValue() : Math.powExact(l.intValue(), l.c().max().intValue())).reduce(inst.seed().intValue(), (a, b) -> a * b)))
                        .example("{1,2,3,4}.prod() [-- 24 --]")
                        .law(Law.monoidic, Law.commutative, Law.semi_right_distributive, Law.pure)
                        .create(),
                /*InstBuilder.build().tid(ORDER_INST_TID, "order the lhs int stream in ascending order")
                        .dom(INT_TID.maybeSome(), "a stream of ints")
                        .rng(LST_TID.poly(INT_TID.maybeSome()), "the sorted lst of ints").argLst()
                        .body((lhs, inst) -> lst(lhs.stream().sorted(Comparator.comparing(a -> a.asInt().intValue()))))
                        .law(Law.pure)
                        .create(),*/
                InstBuilder.build().tid(SUM_INST_TID, "add the lhs int stream into a single int")
                        .dom(INT_TID.maybeSome(), "zero or more ints").rng(INT_TID, "a single plus reduced int")
                        .argLst().seed(jnt(0))
                        .body((lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> ((studio.phaseshift.metatron.isa.m.type.Int) a).plus((studio.phaseshift.metatron.isa.m.type.Int) b)).intValue()))
                        .example("{1,2,3,4}.sum() [-- 10 --]")
                        .law(Law.monoidic, Law.commutative, Law.semi_right_distributive, Law.pure)
                        .create(),
                // MULT DECLARES NO INVERSE. int is a mul_monoid, not a group: 5.div(2).mult(2) is 4, so div is a
                // one-way retraction at best and never the two-sided inverse `?decq inverse` would claim. MINUS
                // keeps its defining equation (minus ≡ plus ∘ neg), which is what lets int's add_group derive it
                InstBuilder.build().tid(MULT_INST_TID, "multiply the lhs int by the argument int")
                        .dom(INT_TID, "the lhs int").rng(INT_TID, "the result of the multiplication")
                        .argLst(INT_TYPE, "the int to multiply the lhs by")
                        .body((lhs, inst) -> lhs.jvm(lhs.intValue() * inst.arg(0).intValue()))
                        .law(Law.commutative, Law.right_distributive, Law.action, Law.pure)
                        .create(),
                InstBuilder.build().tid(MINUS_INST_TID, "subtract the argument int from the lhs int")
                        .dom(INT_TID, "the lhs int").rng(INT_TID, "the result of the subtraction")
                        .argLst(INT_TYPE, "the int to subtract from the lhs")
                        .body((lhs, inst) -> lhs.jvm(lhs.intValue() - inst.arg(0).intValue()))
                        .converse(PLUS_INST_TID.dom(INT_TID).rng(INT_TID).args(INT_TID))
                        .derivation(minus_(T(ALL, f("b"), null, null)), plus_(map_(T(ALL, f("b"), null, null)).neg_()))
                        .law(Law.action, Law.pure)
                        .create(),
                // THE as PAIR, DECLARED BESIDE ITS INSTRUCTIONS (the human's ruling): int->str is the SOUND
                // direction -- print an int, read the printed str back, and the value returns (42 -> "42" -> 42) --
                // so it declares the opposing edge. str->int is LOSSY: every int renders, but only SOME strs read
                // back ("007" -> 7 -> "7"), so it declares NO inverse. Only the sound direction declares one, and
                // it is stated here.
                InstBuilder.build().tid(AS_INST_TID, "print the lhs int into a str")
                        .dom(INT_TID, "the int to convert").rng(STR_TID, "the lhs int as a str")
                        .argLst(STR_TYPE, "the str type that receives the value")
                        .body((lhs, inst) -> str(lhs.intValue().toString(), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid()))
                        .law(Law.pure)
                        .inverse(AS_INST_TID.dom(STR_TID).rng(INT_TID))
                        .create(),
                InstBuilder.build().tid(PLUS_INST_TID, "add the argument int to the lhs int")
                        .dom(INT_TID, "the lhs int")
                        .rng(INT_TID, "the result of the addition")
                        .argLst(INT_TYPE, "the int to add to the lhs")
                        .body((lhs, inst) -> lhs.jvm(lhs.intValue() + inst.arg(0).intValue()))
                        .converse(MINUS_INST_TID.dom(INT_TID).rng(INT_TID).args(INT_TID))
                        .example("1.plus(2)          [-- 3                    --]",
                                "1+2                [-- 3 sugar'd form       --]",
                                "1.plus(plus(1))    [-- 3 nested application --]",
                                "1+(+1)             [-- 3 sugar nested form  --]")
                        .law(Law.commutative, Law.right_distributive, Law.action, Law.pure)
                        .form(Inst.Form.mapper)
                        //.inverse(MINUS_INST_TID.dom(INT_TID).rng(INT_TID))
                        .create()
        ));
    }

    /**
     * {@code real::T}'s operations: the field's own, the comparisons, and the folds.
     */
    public static Set<Inst> real_insts() {
        return new LinkedHashSet<>(List.of(
                InstBuilder.build().tid(GT_INST_TID, "check whether the lhs real is greater than the argument real")
                        .dom(REAL_TID, "a real").rng(BOOL_TID, "true if the lhs real is greater than the arg real")
                        .argLst(T(REAL_TID), "the rhs real")
                        .body((lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.realValue() > inst.arg(0).realValue()).isPresent()))
                        .law(Law.poset, Law.pure)
                        .create(),
                InstBuilder.build().tid(GTE_INST_TID, "check whether the lhs real is greater than or equal to the argument real")
                        .dom(REAL_TID, "a real").rng(BOOL_TID, "true if the lhs real is greater than or equal to the arg real")
                        .argLst(T(REAL_TID), "the rhs real")
                        .body((lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.realValue() >= inst.arg(0).realValue()).isPresent()))
                        .law(Law.poset, Law.pure)
                        .create(),
                InstBuilder.build().tid(LT_INST_TID, "check whether the lhs real is less than the argument real")
                        .dom(REAL_TID, "a real").rng(BOOL_TID, "true if the lhs real is less than the arg real")
                        .argLst(T(REAL_TID), "the rhs real")
                        .body((lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.realValue() < inst.arg(0).realValue()).isPresent()))
                        .law(Law.poset, Law.pure)
                        .create(),
                InstBuilder.build().tid(LTE_INST_TID, "check whether the lhs real is less than or equal to the argument real")
                        .dom(REAL_TID, "a real").rng(BOOL_TID, "true if the lhs real is less than or equal to the arg real")
                        .argLst(T(REAL_TID), "the rhs real")
                        .body((lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.realValue() <= inst.arg(0).realValue()).isPresent()))
                        .law(Law.poset, Law.pure)
                        .create(),
                InstBuilder.build().tid(NEG_INST_TID, "a negation function \\(f(x) \\mapsto -x\\): the additive inverse of the lhs real")
                        .dom(REAL_TID, "a real").rng(REAL_TID, "the negated lhs real")
                        .argLst()
                        .body((lhs, inst) -> lhs.asReal().neg())
                        .converse(NEG_INST_TID.dom(REAL_TID).rng(REAL_TID))
                        .law(Law.involution, Law.pure)
                        .create(),
                InstBuilder.build().tid(PLUS_INST_TID, "a sum function \\(f(x, y) \\mapsto x + y\\): adds the rhs real to the lhs real")
                        .dom(REAL_TID, "a real").rng(REAL_TID, "the sum")
                        .argLst(T(REAL_TID), "the rhs real")
                        .body((lhs, inst) -> lhs.asReal().plus(Inst.Helper.alignRHSType(lhs, inst.arg(0)).asReal()))
                        .converse(MINUS_INST_TID.dom(REAL_TID).rng(REAL_TID).args(REAL_TID))
                        .law(Law.commutative, Law.right_distributive, Law.action, Law.pure)
                        .create(),
                InstBuilder.build().tid(MULT_INST_TID, "a product function \\(f(x, y) \\mapsto xy\\): multiplies the lhs real by the rhs real")
                        .dom(REAL_TID, "a real").rng(REAL_TID, "the product")
                        .argLst(T(REAL_TID), "the rhs real")
                        .body((lhs, inst) -> lhs.asReal().mult(Inst.Helper.alignRHSType(lhs, inst.arg(0)).asReal()))
                        .converse(DIV_INST_TID.dom(REAL_TID).rng(REAL_TID).args(REAL_TID))
                        .law(Law.commutative, Law.right_distributive, Law.action, Law.pure)
                        .create(),
                InstBuilder.build().tid(ZERO_INST_TID, "a zero function: the real zero (0.0)")
                        .dom(REAL_TID, "a real").rng(REAL_TID, "the real zero")
                        .argLst()
                        .body((lhs, inst) -> lhs.asReal().zero())
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(ONE_INST_TID, "a one function: the real one (1.0)")
                        .dom(REAL_TID, "a real").rng(REAL_TID, "the real one")
                        .argLst()
                        .body((lhs, inst) -> lhs.asReal().one())
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(INV_INST_TID, "an inverse function \\(f(x) \\mapsto 1/x\\): the multiplicative inverse of the lhs real")
                        .dom(REAL_TID, "a real").rng(REAL_TID, "the multiplicative inverse")
                        .argLst()
                        .body((lhs, inst) -> lhs.asReal().inv())
                        .law(Law.involution, Law.pure)
                        .create(),
                InstBuilder.build().tid(SUM_INST_TID, "a sum function: the additive reduction of the lhs reals")
                        .dom(REAL_TID.maybeSome(), "a stream of reals").rng(REAL_TID, "the sum of the lhs reals")
                        .argLst().seed(real(0.0))
                        .body((lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> real(a.realValue() + (b.realValue() * b.c().max().doubleValue()))).realValue()))
                        .law(Law.monoidic, Law.commutative, Law.semi_right_distributive, Law.pure)
                        .create(),
                InstBuilder.build().tid(PROD_INST_TID, "a product function: the multiplicative reduction of the lhs reals")
                        .dom(REAL_TID.maybeSome(), "a stream of reals").rng(REAL_TID, "the product of the lhs reals")
                        .argLst().seed(real(1.0))
                        .body((lhs, inst) -> inst.seed().jvm(lhs.stream().map(l -> l.c().isOne() ? l.realValue() : Math.pow(l.realValue(), l.c().max())).reduce(inst.seed().realValue(), (a, b) -> a * b)))
                        .law(Law.monoidic, Law.commutative, Law.semi_right_distributive, Law.pure)
                        .create(),
                InstBuilder.build().tid(MEAN_INST_TID, "the mean of a stream of reals")
                        .dom(REAL_TID.maybeSome(), "a stream of reals").rng(REAL_TID.maybe(), "the mean of the lhs real stream, or noobj for none")
                        .argLst()
                        .form(Inst.Form.reducer)
                        .body((lhs, inst) -> mean(lhs, Obj::realValue))
                        .example("{1.0,2.0,3.0}.mean() [-- 2.0 --]")
                        .law(Law.pure)
                        .derivation(mean_(REAL_TID), liftedMean(REAL_TID))
                        .create(),
                InstBuilder.build().tid(POW_INST_TID, "a power function \\(f(x, n) \\mapsto x^n\\): the lhs real raised to the arg real")
                        .dom(REAL_TID, "a real").rng(REAL_TID, "the lhs real raised to the arg real")
                        .argLst(T(REAL_TID), "the exponent real")
                        .body((lhs, inst) -> real(Math.pow(lhs.realValue(), inst.arg(0).realValue())))
                        .law(Law.pure)
                        .create(),
                // DIV AND MINUS DECLARE THEIR DEFINING EQUATIONS, which is what lets real's field (and its
                // add_group) derive the inverse pairs instead of an `inverse` declaring them: div ≡ mult ∘ inv,
                // minus ≡ plus ∘ neg. The carrier states the group; the equation states which instruction it is.
                InstBuilder.build().tid(DIV_INST_TID, "a quotient function \\(f(x, y) \\mapsto x/y\\): the lhs real divided by the rhs real")
                        .dom(REAL_TID, "a real").rng(REAL_TID, "the quotient")
                        .argLst(T(REAL_TID), "the rhs real")
                        .body((lhs, inst) -> lhs.asReal().div(Inst.Helper.alignRHSType(lhs, inst.arg(0)).asReal()))
                        .converse(MULT_INST_TID.dom(REAL_TID).rng(REAL_TID).args(REAL_TID))
                        .derivation(div_(T(ALL, f("b"), null, null)), mult_(map_(T(ALL, f("b"), null, null)).inv_()))
                        .law(Law.right_distributive, Law.action, Law.pure)
                        .create(),
                InstBuilder.build().tid(MINUS_INST_TID, "a difference function \\(f(x, y) \\mapsto x - y\\): the rhs real subtracted from the lhs real")
                        .dom(REAL_TID, "a real").rng(REAL_TID, "the difference")
                        .argLst(T(REAL_TID), "the rhs real")
                        .body((lhs, inst) -> lhs.asReal().minus(Inst.Helper.alignRHSType(lhs, inst.arg(0)).asReal()))
                        .converse(PLUS_INST_TID.dom(REAL_TID).rng(REAL_TID).args(REAL_TID))
                        .derivation(minus_(T(ALL, f("b"), null, null)), plus_(map_(T(ALL, f("b"), null, null)).neg_()))
                        .law(Law.action, Law.pure)
                        .create(),
                // real->str is SOUND (the printed decimal reads back to the same double), so it declares the
                // opposing edge; str->real is LOSSY (real's printing is not injective over every str it accepts,
                // and only some strs are reals at all), so it declares none.
                InstBuilder.build().tid(AS_INST_TID, "print the lhs real into a str")
                        .dom(REAL_TID, "the real to convert").rng(STR_TID, "the lhs real as a str")
                        .argLst(STR_TYPE, "the str type that receives the value")
                        .body((lhs, inst) -> str(String.valueOf(lhs.realValue()), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid()))
                        .law(Law.pure)
                        .inverse(AS_INST_TID.dom(STR_TID).rng(REAL_TID))
                        .create()
        ));
    }

    /**
     * {@code bool::T}'s operations: the Boolean algebra's join, meet and complement, with the two bounds as the
     * monoid identities the type's algebra already states ({@code zero => false}, {@code one => true}).
     */
    public static Set<Inst> bool_insts() {
        return new LinkedHashSet<>(List.of(
                InstBuilder.build().tid(AS_INST_TID, "convert the lhs bool type to a reified rec")
                        .dom(BOOL_TID, "the bool type to convert")
                        .rng(REC_TID, "the bool type deconstructed to its component parts")
                        .argLst(REC_TYPE, "the rec type")
                        .body((lhs, inst) -> Rec.Helper.typeToRec(lhs, () -> fail("a bool value has no rec mapping")))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(ZERO_INST_TID, "the zero of the Boolean algebra — false, the join identity")
                        .dom(BOOL_TID, "the lhs bool").rng(BOOL_TID, "false").argLst()
                        .body((lhs, inst) -> lhs.asBool().zero())
                        .law(Law.unit, Law.idempotent, Law.pure)
                        .create(),
                InstBuilder.build().tid(ONE_INST_TID, "the one of the Boolean algebra — true, the meet identity")
                        .dom(BOOL_TID, "the lhs bool").rng(BOOL_TID, "true").argLst()
                        .body((lhs, inst) -> lhs.asBool().one())
                        .law(Law.unit, Law.idempotent, Law.pure)
                        .create(),
                InstBuilder.build().tid(PLUS_INST_TID, "the disjunction (or) of the lhs bool and the argument bool")
                        .dom(BOOL_TID, "the lhs bool").rng(BOOL_TID, "whether either bool is true")
                        .argLst(T(BOOL_TID), "the bool to or with the lhs")
                        .body((lhs, inst) -> lhs.jvm(lhs.boolValue() || inst.arg(0).boolValue()))
                        .law(Law.commutative, Law.monoidic, Law.idempotent, Law.pure)
                        .create(),
                InstBuilder.build().tid(MULT_INST_TID, "the conjunction (and) of the lhs bool and the argument bool")
                        .dom(BOOL_TID, "the lhs bool").rng(BOOL_TID, "whether both bools are true")
                        .argLst(T(BOOL_TID), "the bool to and with the lhs")
                        .body((lhs, inst) -> lhs.jvm(lhs.boolValue() && inst.arg(0).boolValue()))
                        .law(Law.commutative, Law.monoidic, Law.idempotent, Law.pure)
                        .create(),
                InstBuilder.build().tid(NOT_INST_TID, "the complement (not) of the lhs bool")
                        .dom(BOOL_TID, "the lhs bool").rng(BOOL_TID, "the complemented bool")
                        .argLst()
                        .body((lhs, inst) -> lhs.jvm(!lhs.boolValue()))
                        .law(Law.involution, Law.pure)
                        .create(),
                // bool->str is SOUND ("true"/"false" reads back to the same bool), so it declares the opposing
                // edge; str->bool is LOSSY (it answers false for every str that does not spell true), so it
                // declares none.
                InstBuilder.build().tid(AS_INST_TID, "print the lhs bool into a str")
                        .dom(BOOL_TID, "the bool to convert").rng(STR_TID, "the lhs bool as a str")
                        .argLst(STR_TYPE, "the str type that receives the value")
                        .body((lhs, inst) -> str(lhs.boolValue() ? "true" : "false", inst.arg(0).vidOrTid(), lhs.vid()))
                        .law(Law.pure)
                        .inverse(AS_INST_TID.dom(STR_TID).rng(BOOL_TID))
                        .create()
        ));
    }

    /**
     * {@code bytes::T}'s operations: the monoid's identity and its append.
     */
    public static Set<Inst> bytes_insts() {
        return new LinkedHashSet<>(List.of(
                InstBuilder.build().tid(AS_INST_TID, "convert the lhs bytes type to a reified rec")
                        .dom(BYTES_TID, "the bytes type to convert")
                        .rng(REC_TID, "the bytes type deconstructed to its component parts")
                        .argLst(REC_TYPE, "the rec type")
                        .body((lhs, inst) -> Rec.Helper.typeToRec(lhs, () -> fail("a bytes value has no rec mapping")))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(ZERO_INST_TID, "the zero of the bytes monoid — the empty byte sequence")
                        .dom(BYTES_TID, "the lhs bytes").rng(BYTES_TID, "the empty bytes").argLst()
                        .body((lhs, inst) -> lhs.asBytes().zero())
                        .law(Law.unit, Law.idempotent, Law.pure)
                        .create(),
                InstBuilder.build().tid(PLUS_INST_TID, "append the argument bytes to the lhs bytes")
                        .dom(BYTES_TID, "the lhs bytes").rng(BYTES_TID, "the concatenated bytes")
                        .argLst(T(BYTES_TID), "the bytes to append to the lhs")
                        .body((lhs, inst) -> lhs.<studio.phaseshift.metatron.isa.m.type.Bytes>as().plus(inst.arg(0).as()))
                        .law(Law.monoidic, Law.pure)
                        .create(),
                // str->bytes is SOUND (the bytes of a str are a str again: the UTF-8 round trip holds for every
                // str), so str's side of the pair declares the opposing edge; bytes->str is LOSSY (arbitrary
                // bytes need not be UTF-8 text), so this side declares none.
                InstBuilder.build().tid(AS_INST_TID, "read the lhs bytes as a utf-8 str")
                        .dom(BYTES_TID, "the bytes to convert").rng(STR_TID, "the lhs bytes as a str")
                        .argLst(STR_TYPE, "the str type that receives the value")
                        .body((lhs, inst) -> str(new String(lhs.bytesValue().array(), StandardCharsets.UTF_8), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid()))
                        .law(Law.pure)
                        .create()
        ));
    }

    /**
     * {@code str::T}'s operations: the monoid's identity and concatenation, the lexicographic comparisons, reverse, and case.
     */
    public static Set<Inst> str_insts() {
        return new LinkedHashSet<>(List.of(
                InstBuilder.build().tid(AS_INST_TID, "convert the lhs str type to a reified rec")
                        .dom(STR_TID, "the str type to convert")
                        .rng(REC_TID, "the str type deconstructed to its component parts")
                        .argLst(REC_TYPE, "the rec type")
                        .body((lhs, inst) -> Rec.Helper.typeToRec(lhs, () -> fail("a str value has no rec mapping")))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(REVERSE_INST_TID, "reverse the characters of the lhs str")
                        .dom(STR_TID, "the str to reverse").rng(STR_TID, "the reversed str").argLst()
                        .body((lhs, inst) -> lhs.jvm(new StringBuilder(lhs.strValue()).reverse().toString()))
                        .law(Law.involution, Law.pure)
                        .create(),
                InstBuilder.build().tid(ZERO_INST_TID, "the zero of the str monoid — the empty str")
                        .dom(STR_TID, "the lhs str").rng(STR_TID, "the empty str").argLst()
                        .body((lhs, inst) -> lhs.asStr().zero())
                        .law(Law.unit, Law.idempotent, Law.pure)
                        .create(),
                InstBuilder.build().tid(GT_INST_TID, "check whether the lhs str sorts after the argument str")
                        .dom(STR_TID, "the lhs str").rng(BOOL_TID, "whether the lhs sorts after the rhs")
                        .argLst(T(STR_TID), "the str to compare against the lhs")
                        .body((lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.strValue().compareTo(inst.arg(0).strValue()) > 0).isPresent()))
                        .law(Law.poset, Law.pure)
                        .create(),
                InstBuilder.build().tid(GTE_INST_TID, "check whether the lhs str sorts at or after the argument str")
                        .dom(STR_TID, "the lhs str").rng(BOOL_TID, "whether the lhs sorts at or after the rhs")
                        .argLst(T(STR_TID), "the str to compare against the lhs")
                        .body((lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.strValue().compareTo(inst.arg(0).strValue()) >= 0).isPresent()))
                        .law(Law.poset, Law.pure)
                        .create(),
                InstBuilder.build().tid(LT_INST_TID, "check whether the lhs str sorts before the argument str")
                        .dom(STR_TID, "the lhs str").rng(BOOL_TID, "whether the lhs sorts before the rhs")
                        .argLst(T(STR_TID), "the str to compare against the lhs")
                        .body((lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.strValue().compareTo(inst.arg(0).strValue()) < 0).isPresent()))
                        .law(Law.poset, Law.pure)
                        .create(),
                InstBuilder.build().tid(LTE_INST_TID, "check whether the lhs str sorts at or before the argument str")
                        .dom(STR_TID, "the lhs str").rng(BOOL_TID, "whether the lhs sorts at or before the rhs")
                        .argLst(T(STR_TID), "the str to compare against the lhs")
                        .body((lhs, inst) -> bool(Inst.Helper.alignLHSType(lhs, inst.arg(0)).filter(l -> l.strValue().compareTo(inst.arg(0).strValue()) <= 0).isPresent()))
                        .law(Law.poset, Law.pure)
                        .create(),
                InstBuilder.build().tid(PLUS_INST_TID, "concatenate two str::T values together")
                        .dom(STR_TID, "a prefix str").rng(STR_TID, "the concatenated str")
                        .argLst(T(STR_TID), "a postfix str")
                        .body((lhs, inst) -> lhs.jvm(lhs.strValue() + inst.arg(0).strValue()))
                        .example("\"a\" + \"b\" [-- \"ab\" --]")
                        .law(Law.monoidic, Law.pure)
                        .create(),
                InstBuilder.build().tid(SUM_INST_TID, "concatenate a str stream into a single str")
                        .dom(STR_TID.maybeSome(), "zero or more strs").rng(STR_TID, "a single concatenated str")
                        .argLst(T(STR_TID.maybe()), "the initial str of the fold, empty when omitted")
                        .body((lhs, inst) -> str(lhs.stream().map(Obj::strValue).reduce(inst.arg(0).orElse(str("")).strValue(), (a, b) -> a + b)))
                        .law(Law.monoidic, Law.pure)
                        .create(),
                InstBuilder.build().tid(UCASE_INST_TID, "upper-case the lhs str")
                        .dom(STR_TID, "the lhs str").rng(STR_TID, "the upper-cased str").argLst()
                        .body((lhs, inst) -> lhs.jvm(lhs.strValue().toUpperCase()))
                        .law(Law.idempotent, Law.pure)
                        .create(),
                InstBuilder.build().tid(LCASE_INST_TID, "lower-case the lhs str")
                        .dom(STR_TID, "the lhs str").rng(STR_TID, "the lower-cased str").argLst()
                        .body((lhs, inst) -> lhs.jvm(lhs.strValue().toLowerCase()))
                        .law(Law.idempotent, Law.pure)
                        .create(),
                // THE FIVE as PAIRS STR IS ON, DECLARED BESIDE THEIR INSTRUCTIONS (the human's ruling). str is the
                // SOUND side of exactly one: str->bytes, whose bytes read back as the same str -- that direction
                // declares the opposing edge. Its other four are LOSSY (a str is not the type it names: only SOME
                // strs parse as an int/real, only the two spellings read back as a bool, and only a legal uri is a
                // uri), so they declare no inverse at all.
                InstBuilder.build().tid(AS_INST_TID, "read the bytes of the lhs str")
                        .dom(STR_TID, "the str to convert").rng(BYTES_TID, "the lhs str as bytes")
                        .argLst(BYTES_TYPE, "the bytes type that receives the value")
                        .body((lhs, inst) -> bytes(ByteBuffer.wrap(lhs.strValue().getBytes()), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid()))
                        .law(Law.pure)
                        .inverse(AS_INST_TID.dom(BYTES_TID).rng(STR_TID))
                        .create(),
                InstBuilder.build().tid(AS_INST_TID, "read the lhs str as a bool — true when it spells true")
                        .dom(STR_TID, "the str to convert").rng(BOOL_TID, "the lhs str as a bool")
                        .argLst(BOOL_TYPE, "the bool type that receives the value")
                        .body((lhs, inst) -> bool(lhs.strValue().equalsIgnoreCase("true"), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(AS_INST_TID, "read the lhs str as an int")
                        .dom(STR_TID, "the str to convert").rng(INT_TID, "the lhs str as an int")
                        .argLst(INT_TYPE, "the int type that receives the value")
                        .body((lhs, inst) -> {
                            final String lhsString = lhs.toCleanString().trim();
                            try {
                                return jnt(Long.parseLong(lhsString), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid());
                            } catch (final NumberFormatException e) {
                                return ObjmtronSerializer.parse(lhsString);
                            }
                        })
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(AS_INST_TID, "read the lhs str as a real")
                        .dom(STR_TID, "the str to convert").rng(REAL_TID, "the lhs str as a real")
                        .argLst(REAL_TYPE, "the real type that receives the value")
                        .body((lhs, inst) -> real(Double.parseDouble(lhs.strValue()), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(AS_INST_TID, "read the lhs str as a uri")
                        .dom(STR_TID, "the str to convert").rng(URI_TID, "the lhs str as a uri")
                        .argLst(URI_TYPE, "the uri type that receives the value")
                        .body((lhs, inst) -> uri(f(lhs.strValue()), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid()))
                        .law(Law.pure)
                        .create(),
                // SPLIT AND MERGE ARE NOT PRESERVING IN EITHER DIRECTION -- an empty segment (a trailing token, an
                // empty field) does not survive the round trip -- so neither direction declares an inverse. Their
                // laws: split is pure, merge is the monoid's own op and pure.
                InstBuilder.build().tid(SPLIT_INST_TID, "split the lhs string according to the token arg and insert components into an ordered lst")
                        .dom(STR_TID, "a str to split").rng(LST_TID, "lst encoded components of the split lhs str")
                        .argLst(STR_TYPE, "a token to split on")
                        .body((lhs, inst) -> Arrays.stream(CommonUtil.splitSafely(REGEX_CACHE.computeIfAbsent(inst.arg(0).strValue(), CommonUtil::guardPattern), lhs.strValue())).map(MStr::str).collect(new CommonUtil.LstCollector()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(MERGE_INST_TID, "join the barrier given the str arg")
                        .dom(STR_TID.maybeSome(), "an str barrier")
                        .rng(STR_TID, "the join of the str barrier")
                        .argLst(STR_TYPE, "the join token")
                        .body((lhs, inst) -> str(lhs.stream().map(Obj::<String>jvmAs).reduce((a, b) -> a + inst.arg(0).strValue() + b).orElse("")))
                        .law(Law.monoidic, Law.pure)
                        .create()
        ));
    }

    /**
     * {@code uri::T}'s operations: the path ring, the component accessors, and the folds.
     */
    public static Set<Inst> uri_insts() {
        return new LinkedHashSet<>(List.of(
                InstBuilder.build().tid(AS_INST_TID).dom(URI_TID).rng(REC_TID)
                        .argLst(REC_TYPE)
                        .body((lhs, _) -> Rec.Helper.typeToRec(lhs, () -> Uri.Helper.uriToRec(lhs.asUri())))
                        .law(Law.pure)
                        .inverse(AS_INST_TID.dom(REC_TID).rng(URI_TID))
                        .create(),
                InstBuilder.build().tid(MERGE_INST_TID, "merge a uri stream into a single uri")
                        .dom(URI_TID.maybeSome(), "zero or more uris")
                        .rng(URI_TID, "a single merged uri")
                        .argLst(T(URI_TID), "the uri placed between each merge")
                        .body((lhs, inst) -> uri(lhs.stream().map(Obj::uriValue).reduce((a, b) -> a.extend(inst.arg(0).uriValue()).extend(b)).orElse(f("noobj"))))
                        .law(Law.pure, Law.monoidic, Law.commutative)
                        .create(),
                InstBuilder.build().tid(SPLIT_INST_TID, "split a uri into its component branches")
                        .dom(URI_TID, "a uri with one or more branches")
                        .rng(URI_TID.maybeSome(), "the individual branches of the uri")
                        .argLst()
                        .body((lhs, inst) -> objs(lhs.uriValue().branches().map(MUri::uri)))
                        // AN UNFOLD IS NOT A MONOID: monoidic is about a binary op or a fold (A{*} => A), and split is
                        // the dual (A => A{*}) -- the fold it pairs with is merge, which declares monoidic. Split is pure.
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(NAME_INST_TID, "the name of the lhs uri")
                        .dom(URI_TID, "the uri to name").rng(URI_TID, "the name of the lhs uri")
                        .argLst()
                        .body((lhs, inst) -> uri(lhs.uriValue().name()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(MINUS_INST_TID, "remove the argument uri text from the lhs uri")
                        .dom(URI_TID, "the lhs uri").rng(URI_TID, "the uri with the argument text removed")
                        .argLst(T(URI_TID), "the uri text to remove")
                        .body((lhs, inst) -> uri(lhs.uriValue().toString().replace(inst.arg(0).uriValue().toString(), "")))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(PLUS_INST_TID, "union the path of the lhs uri with the path of the argument uri")
                        .dom(URI_TID, "a uri").rng(URI_TID.maybe(), "the branch union")
                        .argLst(T(URI_TID.maybe()), "the rhs uri")
                        .body((lhs, inst) -> lhs.asUri().plus(inst.arg(0).asUri()))
                        .desc("a branch function \\(f(x, y) \\mapsto x \\sqcup y\\): the union of the lhs and rhs uri paths")
                        .law(Law.commutative, Law.pure)
                        .create(),
                InstBuilder.build().tid(MULT_INST_TID, "concatenate the path of the argument uri onto the path of the lhs uri")
                        .dom(URI_TID, "a uri").rng(URI_TID.maybe(), "the path extension")
                        .argLst(T(URI_TID.maybe()), "the rhs uri")
                        .body((lhs, inst) -> lhs.asUri().mult(inst.arg(0).asUri()))
                        .desc("a product function \\(f(x, y) \\mapsto xy\\): concatenates the rhs uri path onto the lhs uri path")
                        .law(Law.monoidic, Law.pure)
                        .create(),
                InstBuilder.build().tid(ONE_INST_TID, "the one of the uri monoid")
                        .dom(URI_TID, "a uri").rng(URI_TID, "the uri one")
                        .argLst()
                        .body((lhs, inst) -> lhs.asUri().one())
                        .desc("a one function: the uri one (<>)")
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(SUM_INST_TID, "add a uri stream into a single uri")
                        .dom(URI_TID.maybeSome(), "zero or more uris").rng(URI_TID, "a single plus reduced uri")
                        .argLst()
                        .seed(uri(NOOBJ))
                        // one uri whose value counts every member by its own coefficient, summed at once: a fold from the
                        // seed floats the seed's zero over the whole sum (and re-typed the seed, so main's answered noobj)
                        .body((lhs, inst) -> uri(fURI.Helper.sum(lhs.stream().map(o -> o.uriValue().d(o.uriValue().d().mult(o.c()))))))
                        .law(Law.monoidic, Law.pure)
                        .create(),
                InstBuilder.build().tid(PROD_INST_TID, "multiply a uri stream into a single uri")
                        .dom(URI_TID.maybeSome(), "zero or more uris").rng(URI_TID, "a single mult reduced uri")
                        .argLst()
                        .seed(uri(""))
                        .body((lhs, inst) -> lhs.stream().reduce(inst.seed(), (a, b) -> uri(a.uriValue().mult(b.uriValue()))))
                        .law(Law.monoidic, Law.pure)
                        .create(),
                InstBuilder.build().tid(PATH_TID, "the path of the lhs uri")
                        .dom(URI_TID, "the uri to read").rng(URI_TID, "the path of the lhs uri")
                        .argLst(T(URI_TID), "the uri argument")
                        .body((lhs, inst) -> uri(lhs.uriValue().pathString()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(Q_INST_TID, "set the query string of the lhs uri")
                        .dom(URI_TID, "the uri to write").rng(URI_TID, "the uri with its query set")
                        .argLst(REC_TYPE, "the query parameters as a rec")
                        .body((lhs, inst) -> lhs.jvm(lhs.uriValue().q((Map<String, String>) inst.arg(0).asRec().elements().collect(Collectors.toMap(kv -> kv.first().uriValue().toString(), kv -> "" + kv.second().jvm(), (a, b) -> b, LinkedHashMap::new)))))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(URI_C_TID, "the coefficient bounds of the lhs uri")
                        .dom(URI_TID, "the uri to read").rng(LST_TID, "the lst of the coefficient's min and max")
                        .argLst(T(URI_TID), "the uri argument")
                        .body((lhs, inst) -> lst(jnt((Long) lhs.uriValue().c().min()), jnt((Long) lhs.uriValue().c().max())))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(POW_INST_TID, "concatenate the path of the lhs uri onto itself the argument number of times, its reversed path for a negative number (the span <u>{n})")
                        .dom(URI_TID, "the base uri").rng(URI_TID, "the repeated uri")
                        .argLst(T(INT_TID), "the number of repetitions: zero is <>, and a negative number repeats the converse, the path reversed")
                        .body((lhs, inst) -> lhs.jvm(fURI.Helper.power(lhs.uriValue(), Typer.Helper.unfolds(Typer.UNFOLD_URI, inst.arg(0).intValue()))))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(SCHEME_INST_TID, "the scheme of the lhs uri")
                        .dom(URI_TID, "the uri to read").rng(URI_TID.maybe(), "the scheme of the lhs uri")
                        .argLst()
                        .body((lhs, inst) -> lhs.uriValue().hasScheme() ? uri(lhs.uriValue().scheme()) : noobj())
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(SCHEME_INST_TID, "set the scheme of the lhs uri")
                        .dom(URI_TID, "the uri to write").rng(URI_TID, "the uri with its scheme set")
                        .argLst(T(URI_TID), "the new scheme")
                        .body((lhs, inst) -> uri(lhs.uriValue().scheme(inst.arg(0).uriValue().toString().isEmpty() ? null : inst.arg(0).uriValue().toString())))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(HOST_INST_TID, "the host of the lhs uri")
                        .dom(URI_TID, "the uri to read").rng(URI_TID.maybe(), "the host of the lhs uri")
                        .argLst()
                        .body((lhs, inst) -> null != lhs.uriValue().host() ? uri(lhs.uriValue().host()) : noobj())
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(HOST_INST_TID, "set the host of the lhs uri")
                        .dom(URI_TID, "the uri to write").rng(URI_TID, "the uri with its host set")
                        .argLst(T(URI_TID), "the new host")
                        .body((lhs, inst) -> uri(lhs.uriValue().host(inst.arg(0).uriValue().toString().isEmpty() ? null : inst.arg(0).uriValue().toString())))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(PORT_INST_TID, "the port of the lhs uri")
                        .dom(URI_TID, "the uri to read").rng(INT_TID.maybe(), "the port of the lhs uri")
                        .argLst()
                        .body((lhs, inst) -> lhs.uriValue().hasPort() ? jnt(lhs.uriValue().port()) : noobj())
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(PORT_INST_TID, "set the port of the lhs uri")
                        .dom(URI_TID, "the uri to write").rng(URI_TID, "the uri with its port set")
                        .argLst(T(INT_TID).predicate(is_(gte_(jnt(-1))).tryToInst()), "the new port")
                        .body((lhs, inst) -> uri(lhs.uriValue().port(inst.arg(0).intValue().intValue())))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(SELECT_INST_TID, "project the lhs uri through the argument rec")
                        .dom(URI_TID, "the uri to project").rng(URI_TID, "the projected uri")
                        .argLst(REC_TYPE, "the projection rules")
                        .body((lhs, inst) -> {
                            final Uri.Helper.UriProjected projected = Uri.Helper.project(lhs.uriValue(), inst.arg(0).orElse(rec0()));
                            return uri(fURI.of(
                                    lhs.uriValue().b(),
                                    projected.scheme(),
                                    projected.host(),
                                    projected.port(),
                                    projected.path(),
                                    lhs.uriValue().c(),
                                    lhs.uriValue().poly(),
                                    projected.query(),
                                    List.of()
                            ), lhs.tid(), lhs.vid());
                        })
                        .law(Law.pure)
                        .create(),
                // uri->str is SOUND (a uri prints and the same uri parses back), so it declares the opposing edge;
                // str->uri is LOSSY (only a legal uri is one; any other str is merely a str), so str's side
                // declares none.
                InstBuilder.build().tid(AS_INST_TID, "print the lhs uri into a str")
                        .dom(URI_TID, "the uri to convert").rng(STR_TID, "the lhs uri as a str")
                        .argLst(STR_TYPE, "the str type that receives the value")
                        .body((lhs, inst) -> str(lhs.uriValue().toString(), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid()))
                        .law(Law.pure)
                        .inverse(AS_INST_TID.dom(STR_TID).rng(URI_TID))
                        .create()
        ));
    }

    /**
     * {@code lst::T}'s operations: the word ring, the selectors, and the folds.
     */
    public static Set<Inst> lsts_insts() {
        return new LinkedHashSet<>(List.of(
               /* InstBuilder.build().tid(MAP_INST_TID, "apply arg to every element of lhs lst")
                        .dom(LST_TID, "the lhs lst containing elements to map")
                        .rng(LST_TID, "the lhs lst with mapped elements")
                        .argLst(ALL_TYPE, "a lst element mapping to obj")
                        .body((lhs, inst) -> lhs.jvm(lhs.lstValue().stream().map(e -> inst.arg(0).apply(e)).collect(CommonUtil.ListCollector.single())))
                        .law(Law.pure)
                        .form(Inst.Form.mapper)
                        .create(), */
                InstBuilder.build().tid(ZERO_INST_TID, "the zero of the lst word ring: [noobj], the lst of a hole, what a sum of cancelling lsts answers")
                        .dom(LST_TID, "the lhs lst").rng(LST_TID, "the zero, [noobj]")
                        .argLst()
                        .body((lhs, _) -> lhs.asLst().zero())
                        .law(Law.unit, Law.idempotent, Law.pure)
                        .create(),
                InstBuilder.build().tid(ONE_INST_TID, "the unity of the lst word ring: the empty lst [,], the identity of concatenation")
                        .dom(LST_TID, "the lhs lst").rng(LST_TID, "the empty lst")
                        .argLst()
                        .body((lhs, _) -> lhs.asLst().one())
                        .law(Law.idempotent, Law.pure)
                        .create(),
                // THE WORD RING'S SUM (Marko, 2026-11-25): the argument counts by its point, as a uri's does, so the 11-17
                // refusal of a bulked argument ("one lst appends one lst") is retired with the append it guarded
                InstBuilder.build().tid(PLUS_INST_TID, "add the argument lst to the lhs lst in the word ring: one lst, their shared prefix and suffix around a brace of what differs")
                        .dom(LST_TID, "the lhs lst").rng(LST_TID, "the sum, the zero [noobj] when its words cancel")
                        .argLst(T(LST_TID.c(cInt.ANY())), "the lst to add, counted by its point")
                        .body((lhs, inst) -> lhs.asLst().plus(inst.arg(0).asLst()))
                        .desc("a branch function \\(f(x, y) \\mapsto x + y\\): two timelines as one lst, \\([a,b,c] + [a,d,c] = [a,\\{b,d\\},c]\\)")
                        .law(Law.monoidic, Law.commutative, Law.pure)
                        .create(),
                InstBuilder.build().tid(NEG_INST_TID, "the additive inverse of the lhs lst: -1 on its first letter, a lst being multilinear in its letters")
                        .dom(LST_TID, "a lst").rng(LST_TID, "the additive inverse")
                        .argLst()
                        .body((lhs, _) -> lhs.asLst().neg())
                        .law(Law.involution, Law.pure)
                        .create(),
                InstBuilder.build().tid(MINUS_INST_TID, "subtract the argument lst from the lhs lst in the word ring: the lhs plus the argument's additive inverse")
                        .dom(LST_TID, "the lhs lst").rng(LST_TID, "the difference, the zero [noobj] when its words cancel")
                        .argLst(T(LST_TID.c(cInt.ANY())), "the lst to subtract, counted by its point")
                        .body((lhs, inst) -> lhs.asLst().minus(inst.arg(0).asLst()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(MULT_INST_TID, "concatenate the argument lst onto the lhs lst: the word ring's product, with the unity [,]")
                        .dom(LST_TID, "the lhs lst").rng(LST_TID.maybe(), "the concatenation, the zero [noobj] when either is the zero, noobj when either is noobj")
                        .argLst(T(LST_TID.c(cInt.ANY())), "the lst to follow the lhs lst, its point on the product's first letter")
                        .body((lhs, inst) -> lhs.asLst().mult(inst.arg(0).asLst()))
                        .desc("a product function \\(f(x, y) \\mapsto xy\\): one timeline after another, \\([1,2] \\cdot [3] = [1,2,3]\\)")
                        .law(Law.monoidic, Law.pure)
                        .create(),
                InstBuilder.build().tid(SPLIT_INST_TID, "split the lhs into the slots of the lst the argument inst builds")
                        .dom(A, "the obj to split")
                        .rng(LST_TID, "the lst of split objs")
                        .argLst(LST_TYPE, "the list of instructions, each applied to the lhs")
                        .body((lhs, inst) -> inst.arg(0).jvm(inst.arg(0).elements().map(e -> e.apply(lhs)).toList()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(MERGE_INST_TID, "merge the slots of the lhs lst into an obj stream")
                        // merge?A{*}<=lst[{**}A{*}](): the A the lst's indices hold is the A it hands on (Marko,
                        // 2026-11-18), so order(…).merge() over ints is an int stream and what follows it is typed. An
                        // index holds ANY mass, a negative point too (Marko, 2026-11-24): [{2}5,{-2}5,7]>- is 7, the 5s
                        // canceling, where {*}, zero to unbounded, refused the anti-member. A bracket reads its entries
                        // as values, {point}value{span} (Type#lstIndices), so the mass is the uri's leading b: {**}A{*}
                        // is A.maybeSome().b(ANY). THE RANGE STAYS A{*} (Marko, 2026-11-24, domain only): the binder
                        // binds a reducer's A{*} dom only from a coefficient within {*}, so under an A{**} range the sum
                        // after a merge was left unbound and folded per obj ([1,2,3]>-.sum() was {1,2,3}). A lst that
                        // unrolls to a negative total ([{-2}5]>-) is a range fail until stream doms take {**}
                        .dom(LST_TID.poly(A.maybeSome().b(cInt.ANY())), "the lst to merge")
                        .rng(A.maybeSome(), "the merged objs")
                        .argLst()
                        .body((lhs, inst) -> objs(lhs.elements()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(MERGE_INST_TID, "merge the slots of the lhs lst into one uri")
                        .dom(LST_TID, "the lst to merge")
                        .rng(URI_TID, "the merged uri")
                        .argLst(URI_TYPE, "the uri placed between each merge")
                        .body((lhs, inst) -> uri(lhs.elements().map(e -> e.uriValue().toString()).collect(Collectors.joining(inst.arg(0).uriValue().toString()))))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(MERGE_INST_TID, "merge the slots of the lhs lst into one str")
                        .dom(LST_TID, "the lst to merge")
                        .rng(STR_TID, "the merged str")
                        .argLst(STR_TYPE, "the str placed between each merge")
                        .body((lhs, inst) -> str(lhs.elements().map(Obj::strValue).collect(Collectors.joining(inst.arg(0).strValue()))))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(HAS_INST_TID, "check whether any slot of the lhs lst tests true against an argument")
                        .dom(LST_TID, "the lst to check").rng(LST_TID.maybe(), "the lhs lst when a slot test holds")
                        // no per-argument docs: the docs are POSITIONAL, so documenting the second of two
                        // ALL.maybe() slots would leave the first and third positions blank prose — the
                        // three slots are named by the description instead
                        .argLst(T(ALL), T(ALL).maybe(), T(ALL).maybe())
                        .body((lhs, inst) -> lhs.<Lst>as().elements().anyMatch(r -> r.test(inst.arg(0)) || r.test(inst.arg(1)) || r.test(inst.arg(2))) ? lhs : noobj())
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(WITHIN_INST_TID, "apply the argument inst within each slot of the lhs lst")
                        .dom(LST_TID, "the lst to work within").rng(LST_TID, "the lst of results")
                        .argLst(T(ALL_STAR), "the inst applied within the slots")
                        .body((lhs, inst) -> lst(inst.arg(0).apply(objs(lhs.elements().flatMap(Obj::elements))).stream().toList()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(SUM_INST_TID, "add a lst stream into a single lst in the word ring")
                        .dom(LST_TID.maybeSome(), "zero or more lsts").rng(LST_TID, "a single plus reduced lst, the zero [noobj] when its words cancel or the stream is empty")
                        .argLst()
                        .seed(lst(noobj()))
                        // the stream summed at once, its words expanded once (Lst.Helper.sum), as a uri stream is
                        .body((lhs, inst) -> Lst.Helper.sum(lhs.stream().map(Obj::asLst)))
                        .law(Law.monoidic, Law.commutative, Law.pure)
                        .create(),
                // the product of a lst stream is their concatenation, as a uri stream's is its extension, so it declares
                // no commutative law. A stream bulks equal lsts, so it holds them in the order it first meets each, a
                // bulked lst counting as many copies as its point, as {2,{2}3}.prod() is 2*3*3: {[1],[2],[1]}.prod() is
                // [1,1,2]. A point there is a count, never a span, so one that counts no copy is refused, and the copies one
                // counts are the lst's power, bounded as a span's are (Typer.UNFOLD_LST): {101}[a].prod() is refused as
                // [a]{101} is, while {{50}[a],{51}[b]}.prod() answers, as [a]{50} * [b]{51} does
                InstBuilder.build().tid(PROD_INST_TID, "concatenate a lst stream into a single lst: the word ring's product")
                        .dom(LST_TID.maybeSome(), "zero or more lsts").rng(LST_TID, "a single mult reduced lst")
                        .argLst()
                        .seed(lst())
                        .body((lhs, inst) -> lhs.stream().map(Obj::asLst).reduce(lst(), (a, b) -> {
                            final Long count = b.tid().d().isExact() ? b.tid().d().min() : null;
                            if (null == count || count < 1L)
                                throw MTronException.of("the product of a lst stream counts %s copies of %s: a point in a stream counts copies, never a span", b.tid().d(), b.d(cInt.ONE()));
                            return a.mult(Lst.Helper.power(b.d(cInt.ONE()).asLst(), b.tid().d()));
                        }))
                        .law(Law.monoidic, Law.pure)
                        .create(),
                InstBuilder.build().tid(ZIP_INST_TID, "interleave lst argument with lhs lst")
                        .dom(LST_TID, "elements are lhs of zip").rng(LST_TID, "lst of zip pairs")
                        .argLst(T(A.some()), "elements are the rhs of zip")
                        .body((lhs, inst) -> {
                            final List<Obj> zipList = new ArrayList<>();
                            final List<Obj> lhsList = lhs.lstValue();
                            final List<Obj> argList = inst.arg(0).lstValue();
                            final int lhsMaxIndex = lhsList.size();
                            final int argMaxIndex = argList.size();
                            final int index = Math.min(lhsMaxIndex, argMaxIndex);
                            for (int i = 0; i < index; i++) {
                                zipList.add(lst(lhsList.get(i).c(c -> c.mult(lhs.c())), argList.get(i).c(c -> c.mult(inst.args().c()))));
                            }
                            return lst(zipList);
                        })
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(SELECT_INST_TID, "select the slots of the lhs lst at the argument keys")
                        .dom(LST_TID, "the lst to select from").rng(B.maybeSome(), "the selected slots")
                        .argLst(T(A.some()), "the keys to select")
                        .body((lhs, inst) -> objs(inst.arg(0).stream().map(s -> lhs.asLst().at(s))))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(SELECT_INST_TID, "select the slots of the lhs lst through the argument lst of rules")
                        .dom(LST_TID, "the lst to select from").rng(LST_TID.maybe(), "the selected slots")
                        .argLst(LST_TYPE, "the lst of selection rules")
                        .body((lhs, inst) -> Poly.Helper.selectLstLstRecursion(lhs.asLst(), inst.arg(0).asLst(), false))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(SELECT_INST_TID, "select the slots of the lhs lst through the argument rec of rules")
                        .dom(LST_TID, "the lst to select from").rng(LST_TID, "the selected slots")
                        .argLst(REC_TYPE, "the rec of selection rules")
                        .body((lhs, inst) -> Poly.Helper.selectLstRecRecursion(lhs.asLst(), inst.arg(0).asRec(), false))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(REMOVE_INST_TID, "remove the slot at the argument index from the lhs lst")
                        .dom(LST_TID, "the lst to remove from").rng(A.maybeSome(), "the removed slot")
                        .argLst(INT_TYPE, "the index of the slot to remove")
                        .body((lhs, inst) -> {
                            final long index = inst.arg(0).intValue();
                            return index >= 0L && index < lhs.lstValue().size() ? lhs.lstValue().get((int) index) : noobj();
                        })
                        .law(Law.pure)
                        .create(),
                // THE SPAN AT RUN TIME (Marko, 2026-11-25): pow(n) is the power a literal's [l]{n} realizes, so pow(-1) is
                // the converse of a computed lst, which reverse() was
                InstBuilder.build().tid(POW_INST_TID, "concatenate the lhs lst onto itself the argument number of times, its converse for a negative number (the span [l]{n})")
                        .dom(LST_TID, "the base lst").rng(LST_TID, "the lst's power")
                        .argLst(T(INT_TID), "the power: zero is [,], and a negative number repeats the converse, the slots reversed")
                        .body((lhs, inst) -> Lst.Helper.power(lhs.asLst(), cInt.of(inst.arg(0).intValue())))
                        .law(Law.pure)
                        .create(),
                // THE PATH A LST OF ATOMS SPELLS, the word ring's embedding in the uri ring ([a,b,c].as(uri) is a/b/c):
                // the uri sum of its words, each word its letters' segments with its coefficient for the point, so the
                // sum a lst factors is the sum a uri factors, [a,{b,d},c] spelling <a/{b,d}/c>
                InstBuilder.build().tid(AS_INST_TID, "spell the lhs lst as a uri: the uri sum of its words, each word the path of its letters")
                        .dom(LST_TID, "the lst to spell").rng(URI_TID, "the uri sum of the lst's words")
                        .argLst(T(URI_TID), "the uri type")
                        .body((lhs, inst) -> uri(fURI.Helper.sum(Lst.Helper.words(lhs.asLst().lstValue(), cInt.ONE()).entrySet().stream()
                                .filter(word -> !word.getValue().isZero())
                                .map(word -> word.getKey().stream().map(Obj::uriValue).reduce(fURI::extend).orElse(f("")).d(word.getValue())))))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(AS_INST_TID, "read the lhs lst as the argument lst type, its generics bound by the lhs")
                        .dom(LST_TID, "the lst to read").rng(LST_TID, "the lhs lst as the argument's lst type")
                        .argLst(LST_TYPE, "the lst type the lhs is read as")
                        .body((lhs, inst) -> lhs.tid(Lst.LstType.boundOrDeclared(lhs.asLst(), inst.arg(0).vidOrTid())).c(c -> c.mult(lhs.c())))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(AS_INST_TID, "read the lhs lst as code: each instruction it holds a step, and any other obj a map to it")
                        .dom(LST_TID, "the lst to read").rng(CODE_TID, "the code the lst's slots make")
                        .argLst(T(CODE_TID), "the code type")
                        .body((lhs, inst) -> MCode.code(lhs.asLst().jvm().stream().flatMap(o -> o.isCall() ? o.asCall().insts().stream() : map_(o).tryToInst().insts().stream()).toList()).c(c -> c.mult(lhs.c())))
                        .law(Law.pure)
                        .create(),
                // lst<->rec IS NOT PRESERVING IN EITHER DIRECTION -- a pair maps to a rel and a lst of rels maps to
                // a rec, but the round trip reorders and re-keys what it carries -- so neither direction declares
                // an inverse.
                InstBuilder.build().tid(AS_INST_TID, "convert the lhs lst into the rec the argument rec type constructs")
                        .dom(LST_TID, "the lst to convert").rng(REC_TID, "the lhs lst as the argument's rec type")
                        .argLst(REC_TYPE, "the rec type whose constructor is applied to the lhs")
                        .body((lhs, inst) -> Rec.Helper.typeToRec(lhs, () -> Poly.Helper.transformLstToRec(lhs.asLst(), inst.arg(0).vidOrTid(), null)))
                        .law(Law.pure)
                        .create()
        ));
    }

    /**
     * {@code rec::T}'s operations: the record's merge, its entries, the reads, and the folds.
     */
    public static Set<Inst> rec_insts() {
        return new LinkedHashSet<>(List.of(
              /*  InstBuilder.build().tid(MAP_INST_TID, "apply arg to every rel of lhs lst. \\(\\mathsf{map} : \\big((k \\mapsto v) \\to (k' \\mapsto v')\\big) \\to \\mathrm{rec}(K,V) \\to \\mathrm{rec}(K',V')\\)")
                        .dom(REC_TID, "the lhs rec containing rel fields to map")
                        .rng(REC_TID, "the lhs rec with mapped rel fields")
                        .argLst(ALL_TYPE, "a rel field mapping to rel")
                        .body((lhs, inst) -> lhs.jvm(lhs.asRec().elements().map(e -> inst.arg(0).apply(e).asRel()).collect(CommonUtil.MapCollector.single())))
                        .law(Law.pure)
                        .form(Inst.Form.mapper)
                        .create(),*/
               /* InstBuilder.build().tid(AS_INST_TID, "convert the lhs rec type to a reified rec")
                        .dom(REC_TID, "the rec type to convert")
                        .rng(REC_TID, "the rec type deconstructed to its component parts")
                        .argLst(REC_TYPE, "the rec type")
                        .body((lhs, inst) -> Rec.Helper.typeToRec(lhs, () -> lhs.as(inst.arg(0).asType())))
                        .law(Law.pure)
                        .create(),*/
                InstBuilder.build().tid(AS_INST_TID, "convert an int into the rec the argument rec type constructs")
                        .dom(INT_TID, "the int to convert")
                        .rng(REC_TID, "the lhs int as the argument's rec type")
                        .argLst(REC_TYPE, "the rec type whose constructor is applied to the lhs")
                        .body((lhs, inst) -> Rec.Helper.typeToRec(lhs, () -> inst.arg(0).asType().constructor().apply(lhs).tid(inst.arg(0).vidOrTid())))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(AS_INST_TID, "convert a str into the rec the argument rec type constructs")
                        .dom(STR_TID, "the str to convert")
                        .rng(REC_TID, "the lhs str as the argument's rec type")
                        .argLst(REC_TYPE, "the rec type whose constructor is applied to the lhs")
                        .body((lhs, inst) -> Rec.Helper.typeToRec(lhs, () -> inst.arg(0).asType().constructor().apply(lhs).tid(inst.arg(0).vidOrTid())))
                        .law(Law.pure)
                        .create(),

                // rec<->lst IS NOT PRESERVING IN EITHER DIRECTION (the round trip re-keys and reorders), and
                // rec->uri is LOSSY TOO (only the component entries a uri can hold survive; any other rec does not
                // read back). Both declare NO inverse. uri->rec, the sound side of the uri pair,
                // declares its claim beside its body in Uri.UriType (it calls Uri.Helper.parseQ, package-private there).
                InstBuilder.build().tid(AS_INST_TID, "convert the lhs rec into the lst the argument lst type constructs")
                        .dom(REC_TID, "the rec to convert").rng(LST_TID, "the lhs rec as the argument's lst type")
                        .argLst(LST_TYPE, "the lst type whose constructor is applied to the lhs")
                        .body((lhs, inst) -> Poly.Helper.transformRecToLst(lhs.asRec(), inst.arg(0).vidOrTid(), null))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(AS_INST_TID, "convert the lhs rec into the uri its component entries describe")
                        .dom(REC_TID, "the rec to convert")
                        .rng(URI_TID, "the lhs rec as a uri")
                        .argLst(URI_TYPE, "the uri type that receives the value")
                        .body((lhs, inst) -> {
                            final Rec lhsRec = lhs.asRec();
                            fURI furi = fURI.Singleton.empty();
                            if (lhsRec.has(SCHEME))
                                furi = furi.scheme(lhsRec.at(SCHEME).asUri().uriValue().toString());
                            if (lhsRec.has(f(SUB)))
                                return uri(lhsRec.at(SUB).uriValue().subdomain());
                            if (lhsRec.has(HOST))
                                furi = furi.host(lhsRec.at(HOST).asUri().uriValue().toString());
                            if (lhsRec.has(PORT))
                                furi = furi.port(lhsRec.at(PORT).asInt().intValue().intValue());
                            if (lhsRec.has(PATH))
                                furi = furi.path(lhsRec.at(PATH).asLst().elements().map(Obj::uriValue).map(fURI::toString).collect(Collectors.joining("/")));
                            if (lhsRec.has(COEFF))
                                furi = furi.c(cInt.of(cInt.of(lhsRec.at("c/min").asInt().intValue(), lhsRec.at("c/max").asInt().intValue()).toString()));
                            if (lhsRec.has(QPROC))
                                furi = furi.q(lhsRec.at(QPROC).asRec().elements().map(e -> Tuple.Pair.with(e.first().toCleanString(), e.second().isNothing() ? "" : e.second().toCleanString())).collect(Collectors.toMap(Tuple.Pair::get0, Tuple.Pair::get1)));
                            return uri(furi, inst.arg(0).vidOrTid(), null);
                        })
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(ZERO_INST_TID, "the zero of the rec ring, the empty rec: no walk to join")
                        .dom(REC_TID, "the lhs rec").rng(REC_TID, "the empty rec").argLst()
                        .body((lhs, inst) -> lhs.asRec().zero())
                        .law(Law.unit, Law.idempotent, Law.pure)
                        .create(),
                InstBuilder.build().tid(ONE_INST_TID, "the unity of the rec ring, [_=>_]: the identity arrow, the walk of length zero at every object")
                        .dom(REC_TID, "the lhs rec").rng(REC_TID, "the rec of the identity arrow").argLst()
                        .body((lhs, inst) -> lhs.asRec().one())
                        .law(Law.unit, Law.idempotent, Law.pure)
                        .create(),
                InstBuilder.build().tid(REVERSE_INST_TID, "reverse the order of the lhs rec's entries")
                        .dom(REC_TID, "the rec to reverse").rng(REC_TID, "the rec with its entries reversed").argLst()
                        .body((lhs, inst) -> new ArrayList<Rel>(lhs.asRec().elements().toList()).reversed().stream().collect(new CommonUtil.RecCollector(lhs.tid(), lhs.vid())))
                        .law(Law.involution, Law.pure)
                        .create(),
                InstBuilder.build().tid(HAS_INST_TID, "check whether the lhs rec holds the argument key, applying the argument code to its value")
                        .dom(REC_TID, "the lhs rec").rng(REC_TID.maybe(), "the lhs rec when the key is held and the check passes, otherwise noobj")
                        .argLst(T(ALL), T(ALL))
                        .body((lhs, inst) -> {
                            final Obj result = inst.arg(1).apply(lhs.asRec().at(inst.arg(0)));
                            return result.isFail() ? result : (result.booleanCheck() ? lhs : noobj());
                        })
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(HAS_INST_TID, "check whether the lhs rec holds the argument key or key-value rel")
                        .dom(REC_TID, "the lhs rec").rng(REC_TID.maybe(), "the lhs rec when the key or rel is held, otherwise noobj")
                        .argLst(T(ALL), "the key or key-value rel to look for")
                        .body((lhs, inst) -> inst.arg(0).isRel() ?
                                (lhs.<Rec>as().elements().anyMatch(r -> r.test(inst.arg(0))) ? lhs : noobj()) :
                                (lhs.<Rec>as().elements().map(Rel::first).anyMatch(r -> r.test(inst.arg(0))) ? lhs : noobj()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(GET_INST_TID, "read the value at the argument uri from each rec in the stream")
                        .dom(REC_TID, "the lhs rec").rng(A.maybeSome(), "the value at the uri for each rec")
                        .argLst(URI_TYPE, "the uri to read from each rec")
                        .body((lhs, inst) -> objs(lhs.stream().map(r -> r.<Rec>as().at(inst.arg(0)))))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(SPLIT_INST_TID, "split the lhs through the argument rec's key and value code")
                        .dom(A, "any obj").rng(REC_TID, "the rec the split builds")
                        .argLst(REC_TYPE, "the rec whose key and value code is applied to the lhs")
                        .body((lhs, inst) -> inst.arg(0).asRec().elements().map(e -> rel(e.first().apply(lhs), e.second().apply(lhs))).collect(new CommonUtil.RecCollector(inst.arg(0).tid(), inst.arg(0).vid())))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(MERGE_INST_TID, "merge the lhs rec's entries into rels")
                        .dom(REC_TID, "the rec to merge")
                        .rng(REL_TID.maybeSome(), "the rec's entries as rels").argLst()
                        .body((lhs, inst) -> objs(lhs.elements()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(DOM_INST_TID, "the keys of the lhs rec")
                        .dom(REC_TID, "the lhs rec")
                        .rng(A.maybeSome(), "the keys of the rec").argLst()
                        .body((lhs, inst) -> objs(lhs.asRec().elements().map(Rel::first)))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(RNG_INST_TID, "the values of the lhs rec")
                        .dom(REC_TID, "the lhs rec")
                        .rng(A.maybeSome(), "the values of the rec").argLst()
                        .body((lhs, inst) -> objs(lhs.asRec().elements().map(Rel::second)))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(PLUS_INST_TID, "deep merge the argument rec into the lhs rec")
                        .dom(REC_TID, "the lhs rec")
                        .rng(REC_TID, "the deep merge of the two recs")
                        .argLst(T(REC_TID.maybeMaybe()), "the rec to merge into the lhs")
                        .body((lhs, inst) -> lhs.asRec().plus(inst.arg(0).asRec()))
                        .law(Law.monoidic, Law.commutative, Law.pure)
                        .create(),
                InstBuilder.build().tid(MINUS_INST_TID, "deep merge the inverted argument rec into the lhs rec")
                        .dom(REC_TID, "the lhs rec")
                        .rng(REC_TID, "the deep merge of the two opposing sign recs")
                        .argLst(REC_TYPE, "the rec whose matching entries are inverted and merged")
                        .body((lhs, inst) -> lhs.asRec().minus(inst.arg(0).asRec()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(NEG_INST_TID, "a negation function \\(f(x) \\mapsto -x\\): the additive inverse of the lhs rec, -1 on each value")
                        .dom(REC_TID, "a rec")
                        .rng(REC_TID, "the additive inverse")
                        .argLst()
                        .body((lhs, _) -> lhs.asRec().neg())
                        .law(Law.involution, Law.pure)
                        .create(),
                InstBuilder.build().tid(MULT_INST_TID, "join the lhs rec's walks to the argument rec's arrows, keeping the middle: [a=>b]·[b=>c] is [a=>(b=>c)]")
                        .dom(REC_TID, "the lhs rec")
                        .rng(REC_TID, "the walks the join builds")
                        .argLst(REC_TYPE, "the rec whose arrows extend the lhs rec's walks where they end")
                        .body((lhs, inst) -> lhs.asRec().mult(inst.arg(0).asRec()))
                        .law(Law.monoidic, Law.pure)
                        .create(),
                InstBuilder.build().tid(MPLUS_INST_TID, "set the lhs rec's entries from the argument rec, one rel at a time")
                        .dom(REC_TID, "the lhs rec").rng(REC_TID, "the lhs rec with the argument's entries written over it")
                        .argLst(REC_TYPE, "the rec whose rels are written into the lhs")
                        .body((lhs, inst) -> inst.arg(0).<Rec>as().elements().map(Obj::<Obj>as).reduce(lhs.<Rec>as(), (a, b) -> a.<Rec>as().at(((Rel) b).first(), ((Rel) b).second(), MUTABLE)))
                        .law(Law.monoidic, Law.pure)
                        .create(),
                InstBuilder.build().tid(SELECT_INST_TID, "read the value at each argument key from the lhs rec")
                        .dom(REC_TID, "the lhs rec").rng(B.maybeSome(), "the value at each key")
                        .argLst(T(A.some()), "the keys to read")
                        .body((lhs, inst) -> objs(inst.arg(0).stream().map(s -> lhs.asRec().at(s))))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(SELECT_INST_TID, "select the lhs rec's entries matched by the argument rec's pattern")
                        .dom(REC_TID, "the lhs rec").rng(REC_TID.maybe(), "the selected entries as a rec")
                        .argLst(REC_TYPE, "the pattern rec that selects the lhs's entries")
                        .body((lhs, inst) -> Poly.Helper.selectPolyRecursion(lhs.asRec(), inst.arg(0).asRec(), false))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(WITHIN_INST_TID, "apply the argument code to each entry of the lhs rec")
                        .dom(REC_TID, "the lhs rec").rng(REC_TID, "the rec of transformed entries")
                        .argLst(T(ALL_STAR), "the code applied to each entry")
                        .body((lhs, inst) -> rec(lhs.elements().map(r -> inst.arg(0).apply(r).<Rel>as())))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(SUM_INST_TID, "deep merge the lhs rec stream into one rec")
                        .dom(REC_TID.maybeSome(), "zero or more recs").rng(REC_TID, "the deep merge of the stream, the zero [=>] when it is empty").argLst()
                        .seed(rec())
                        .body((lhs, inst) -> lhs.stream().map(Obj::asRec).reduce(rec(), Rec::plus))
                        .law(Law.monoidic, Law.commutative, Law.pure)
                        .create()
        ));
    }

    public static Set<Inst> inst_insts() {
        return new LinkedHashSet<>(List.of(
                InstBuilder.build().tid(AS_INST_TID, "convert an inst into a rec by decomposing its components into keyed values of the rec")
                        .dom(INST_TID, "the inst to convert")
                        .rng(REC_TID, "the inst deconstructed into its args, body, and tid further deconstructed")
                        .argLst(REC_TYPE, "the rec type")
                        .body((lhs, _) -> {
                            Inst lhsInst = lhs.asInst();
                            return rec(uri(TID), Uri.Helper.uriToRec(uri(lhsInst.tid())),
                                    uri(ARGS), lhsInst.args(),
                                    uri(BODY), lhsInst.f().isLambda() ? uri(lhsInst.f().toString()) : (Obj) lhsInst.f().func());
                        })
                        .law(Law.pure)
                        .form(Inst.Form.mapper)
                        .create()));
    }

    /**
     * {@code code::T}'s operations: composition, the branch, and the two conversions.
     */
    public static Set<Inst> code_insts() {
        return new LinkedHashSet<>(List.of(
                InstBuilder.build().tid(AS_INST_TID, "convert a code into the lst of its insts")
                        .dom(CODE_TID, "the code to convert").rng(LST_TID, "the code's insts as a lst")
                        .argLst(LST_TYPE, "the lst type that receives the insts")
                        .body((lhs, _) -> lst(lhs.asCode().codeValue().stream().map(Obj::<Obj>as).toList()).c(c -> c.mult(lhs.c())))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(AS_INST_TID, "convert a code into the single inst that applies it")
                        .dom(CODE_TID, "the code to convert").rng(INST_TID, "the code as one inst")
                        .argLst(INST_TYPE, "the inst type that receives the code")
                        .body((lhs, _) -> lhs.asCode().codeValue().size() == 1 ? lhs.asCode().codeValue().getFirst() : instC(M_ISA_INST_TID.dom(lhs.asCode().insts().getFirst().dom().tid()).rng(lhs.asCode().insts().getLast().rng().tid()), lst(), lhs.asCode()).c(lhs.c()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(COMPOSE_INST_TID, "compose two codes in series, the lhs's insts then the argument's")
                        .dom(CODE_TID, "the code applied first").rng(CODE_TID, "the serial composition of the two codes")
                        .argLst(CODE_TYPE, "the code applied after the lhs")
                        .body((lhs, inst) -> {
                            final List<Inst> insts = new ArrayList<>();
                            insts.addAll(lhs.asCode().codeValue());
                            insts.addAll(inst.arg(0).asCode().codeValue());
                            return MCode.code(insts);
                        })
                        .law(Law.monoidic, Law.pure)
                        .create(),
                // str::T's update over instructions (Call.Helper.update): a rule's key is a code pattern, its value what
                // each run the pattern matches becomes
                InstBuilder.build().tid(UPDATE_INST_TID, "change the runs of the lhs code that the rules' patterns match")
                        .dom(CODE_TID, "the code to update").rng(CODE_TID, "the code with each matched run changed")
                        .argLst(REC_TYPE, "the rules, pattern => change: a code pattern (inst names as uri patterns, args as the types they must be) => the code that replaces each run it matches, or a rec of rules applied inside the run")
                        .body((lhs, inst) -> lhs.jvm(Call.Helper.update(lhs.asCode().insts(), inst.arg(0))).asCall().tryToInst())  // TODO: this is going to bite us one day -- until then, let us live like kings.
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(END_INST_TID, "the terminal instruction yielding noobj")
                        .dom(CODE_TID, "the lhs code").rng(CODE_TID.zero())
                        .body((lhs, inst) -> {
                            final List<Inst> insts = new ArrayList<>(lhs.codeValue());
                            insts.add(end_().tryToInst().asInst());
                            return lhs.jvm(insts);

                        })
                        .law(Law.absorbing, Law.pure, Law.unit)
                        .create(),
                InstBuilder.build().tid(ID_INST_TID, "the identity instruction")
                        .dom(CODE_TID, "the lhs code").rng(CODE_TID)
                        .body((lhs, inst) -> {
                            final List<Inst> insts = new ArrayList<>(lhs.codeValue());
                            insts.add(id_().tryToInst().asInst());
                            return lhs.jvm(insts);
                        })
                        .law(Law.idempotent, Law.pure, Law.unit)
                        .create(),
                InstBuilder.build().tid(NEG_INST_TID, "the negation of code inverts its coefficient")
                        .dom(CODE_TID, "the lhs code").rng(CODE_TID)
                        .body((lhs, _) -> lhs.c(cInt::neg))
                        .law(Law.involution, Law.pure)
                        .create(),
                InstBuilder.build().tid(PLUS_INST_TID, "branch two codes in parallel, the results as their coefficient sum")
                        .dom(CODE_TID, "the lhs code").rng(CODE_TID, "the branch of the two codes")
                        .argLst(CODE_TYPE, "the code branched alongside the lhs")
                        .body((lhs, inst) -> {
                            if (lhs.equals(inst.arg(0)))
                                return lhs.c(c -> c.plus(inst.arg(0).c()));
                            else {
                                // ONLY a LOCAL branch flattens: a layout is a route table (BranchInst#isLayout), and
                                // splicing its members into another branch would drop its keys or its deal, so it
                                // joins whole, as one member, as any other code does
                                return branch_(Stream.of(lhs, inst.arg(0)).flatMap(side -> 1 == side.asCode().insts().size()
                                        && BRANCH_INST_TID.equals(side.asCode().insts().getFirst().tid().basePath())
                                        && !BranchInst.isLayout(side.asCode().insts().getFirst())
                                        ? side.asCode().insts().getFirst().args().<Obj>elements() : Stream.of(side))
                                        .toArray(Obj[]::new)).as();
                            }
                        })
                        .law(Law.commutative, Law.monoidic, Law.pure)
                        .create()
        ));
    }

    /**
     * {@code code::T}'s operations: composition, the branch, and the two conversions.
     */
    public static Set<Inst> objs_insts() {
        return new LinkedHashSet<>(List.of(
                InstBuilder.build().tid(START_INST_TID)
                        .dom(fURI.Singleton.NOOBJ, "noobj")
                        .rng(A.any(), "initial objs")
                        .argLst(T(A.any()), "initial objs")
                        .desc("the initial function f()->x")
                        .body((lhs, inst) -> objs(inst.args().values()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build()
                        .tid(NOT_INST_TID)
                        .dom(ALL, "any obj")
                        .rng(BOOL_TID, "the negated truth value of the arg")
                        .argLst(T(ALL.maybe()), "the obj to negate")
                        .desc("a negation function \\(f(x) \\mapsto x'\\): true if the arg obj is falsy")
                        .body((lhs, inst) -> bool(!inst.arg(0).booleanCheck()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build()
                        .tid(EQ_INST_TID)
                        .dom(A, "any objs")
                        .rng(BOOL_TID, "true if lhs equals rhs")
                        .argLst(T(A), "the rhs obj")
                        .desc("an equality function \\[ f(\\mathtt{lhs}) = \\left\\{ \\begin{aligned} \\mathtt{true} & \\quad \\text{if } \\mathtt{lhs} = \\mathtt{arg}_0 \\\\ \\mathtt{false} & \\quad \\text{otherwise.} \\end{aligned} \\right. \\]")
                        .body((lhs, inst) -> Inst.Helper.alignLHSType(lhs, inst.arg(0)).map(l -> Objects.equals(l, inst.arg(0))).map(MBool::bool).orElse(BOOL_FALSE))
                        .law(Law.pure, Law.commutative)
                        .create(),
                InstBuilder.build()
                        .tid(NEQ_INST_TID)
                        .dom(A, "any objs")
                        .rng(BOOL_TID, "true if lhs does not equal rhs")
                        .argLst(T(A), "the rhs obj")
                        .desc("an inequality function \\[ f(\\mathtt{lhs}) = \\left\\{ \\begin{aligned} \\mathtt{true} & \\quad \\text{if } \\mathtt{lhs} \\neq \\mathtt{arg}_0 \\\\ \\mathtt{false} & \\quad \\text{otherwise.} \\end{aligned} \\right. \\]")
                        .body((lhs, inst) -> Inst.Helper.alignLHSType(lhs, inst.arg(0)).map(l -> !Objects.equals(l, inst.arg(0))).map(MBool::bool).orElse(BOOL_TRUE))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build()
                        .tid(ID_INST_TID)
                        .dom(A, "the lhs obj to pass to the rhs")
                        .rng(A, "an rhs obj is the lhs obj")
                        .argLst()
                        .desc("the obj identity function \\(f(x) \\mapsto x\\)")
                        .body((lhs, _) -> lhs)
                        .law(Law.pure, Law.idempotent, Law.involution, Law.unit, Law.commutative)
                        .create(),
                // TODO: do we gut this and rely fully on the non-barried form?
                InstBuilder.build()
                        .tid(ID_INST_TID)
                        .dom(A.maybeSome(), "the lhs objs to pass to the rhs")
                        .rng(A.maybeSome(), "the rhs objs are the lhs objs")
                        .argLst()
                        .desc("a objs barrier identity function \\(f(X) \\mapsto X\\)")
                        .body((lhs, _) -> lhs)
                        .law(Law.pure, Law.idempotent, Law.involution, Law.unit, Law.commutative)
                        .create(),
                // SUP AND INF (menu entry H, redundant shards): the join and meet of the order a stream's members compare
                // in, the folds that are idempotent as well as a commutative monoid's, x ⊕ x = x. Self-combining, so
                // law_shard recombines their partials through themselves (A, E); idempotent, so ?split=copy may ship the
                // whole seed to every lane and fold every reply (H): each copy answers the same bound, and the bound of
                // copies is itself. A bulked member weighs nothing, as the sup of {3}5 is 5. The empty stream's bound is
                // noobj: not the order's bottom (sup ∅) or top (inf ∅), but the stream ring's zero, which an empty lane adds
                // to the merge as nothing, (noobj + x)·sup = x·sup (no seed: nothing reads one). So the range is A{?}, and
                // the reducer form is declared, which the range alone no longer shows. The argument is the comparator, as
                // order()'s is: a projection of each member compares the members, so a mixed stream answers by a key
                // ({1,'a',2.5}.sup(tid().name())), and the bare form compares the members themselves. They are
                // sup and inf, never max and min: an instruction's name shadows an unbound name of the same spelling,
                // and an optional {?}max read as *max (dsh's read_events) would have read the instruction.
                InstBuilder.build()
                        .tid(SUP_INST_TID)
                        .dom(A.maybeSome(), "maybe some A to compare")
                        .rng(A.maybe(), "the greatest A, or noobj for none")
                        .argLst(T(ALL.maybe()), "a projection of A to compare, as order's")
                        .desc("the supremum function \\(f(X) \\mapsto \\sup X\\): the greatest member of the lhs in the order a sort compares it (by the arg's projection, when given, as order's), whatever its mass; noobj for the empty stream. A join: commutative, associative and idempotent, so its partials recombine through itself, even when the parts overlap")
                        .example("{3,1,4,1,5}.sup()          [-- 5 --]")
                        .example("{{3}2,7}.sup()             [-- 7: a member's mass does not weigh in --]")
                        .example("{'b','a','c'}.sup()        [-- 'c' --]")
                        .example("{[a=>3],[a=>1]}.sup(_>>a)  [-- [a=>3]: the argument is the comparator, as order's --]")
                        .form(Inst.Form.reducer)
                        .body((lhs, inst) -> lhs.stream().max(bound(inst)).map(m -> m.c(cInt::one)).orElse(noobj()))
                        .law(Law.monoidic, Law.commutative, Law.idempotent, Law.semi_right_distributive, Law.pure)
                        .create(),
                InstBuilder.build()
                        .tid(INF_INST_TID)
                        .dom(A.maybeSome(), "maybe some A to compare")
                        .rng(A.maybe(), "the least A, or noobj for none")
                        .argLst(T(ALL.maybe()), "a projection of A to compare, as order's")
                        .desc("the infimum function \\(f(X) \\mapsto \\inf X\\): the least member of the lhs in the order a sort compares it (by the arg's projection, when given, as order's), whatever its mass; noobj for the empty stream. A meet: commutative, associative and idempotent, so its partials recombine through itself, even when the parts overlap")
                        .example("{3,1,4,1,5}.inf()          [-- 1 --]")
                        .example("{'b','a','c'}.inf()        [-- 'a' --]")
                        .form(Inst.Form.reducer)
                        .body((lhs, inst) -> lhs.stream().min(bound(inst)).map(m -> m.c(cInt::one)).orElse(noobj()))
                        .law(Law.monoidic, Law.commutative, Law.idempotent, Law.semi_right_distributive, Law.pure)
                        .create(),
                // THE COEFFICIENT, READ AND SET (moved from Obj, Marko 2026-11-22). The reader answers mass, which no
                // floatable function sees (Thm 6 carries the coefficient past it), so a key holding cc() compares mass and
                // law_shard keeps its sort home (Law.Helper#massBlind). Both are reducers by their coefficient shape, so
                // neither floats in a shipped run.
                InstBuilder.build()
                        .tid(CC_INST_TID)
                        .dom(A.maybeSome(), "an obj, or a stream read as one obj")
                        .rng(INT_TID, "the mass of the lhs")
                        .argLst()
                        .desc("the coefficient function \\(f(\\{c\\}x) \\mapsto c\\): the mass of the lhs as an int, and of a stream, the sum of its members' masses. It reads what a floatable function carries untouched (Theorem 6), so a sort keyed by it compares mass, and law_shard keeps such a sort home, where every value's mass is whole")
                        .example("{3}5.cc()                    [-- 3 --]")
                        .example("{1,1,2}.cc()                 [-- 3, the mass of the stream --]")
                        .example("{1,1,1,2,3,3}.order(cc())    [-- [2,{2}3,{3}1], sorted by mass --]")
                        .body((lhs, inst) -> jnt(lhs.c().max()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build()
                        .tid(CC_INST_TID)
                        .dom(A, "an obj, one copy at a time")
                        .rng(A.any(), "the lhs with the coefficient the arg states")
                        .argLst(T(INT_TID), "the coefficient of one copy")
                        .desc("the coefficient-setting function \\(f(x, c) \\mapsto \\{c\\}x\\): one copy of the lhs takes the coefficient c, so each copy of a bulked obj takes it, \\(\\{n\\}x \\mapsto \\{nc\\}x\\)")
                        .example("5.cc(3)                                                [-- {3}5 --]")
                        .example("{2}5.cc(3)                                             [-- {6}5: each of the two copies takes three --]")
                        .example("{1,1,2}.split?<=int{*}([mean(),count()]).cc(_>>1)>>0   [-- {3}1.333…: a part's mean weighted by its count --]")
                        .body((lhs, inst) -> lhs.c(inst.arg(0).intValue()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build()
                        .tid(MAP_INST_TID)
                        .dom(A.maybe(), "maybe some obj")
                        .rng(B.maybeSome(), "the lhs obj applied to the arg obj")
                        .argLst(T(B.maybeSome()), "any obj")
                        .desc("applies the lhs obj to the arg obj to yield the rhs obj")
                        .body((lhs, inst) -> inst.arg(0).apply(lhs))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build()
                        .tid(BRANCH_INST_TID)
                        // THE DOM IS THE SHIPMENT GRAIN, but the INSTRUCTION'S OWN DEFAULT IS LOCAL: {?} feeds one obj
                        // at a time. {n} pages it; {*} gathers the whole lhs once (a barrier). A DISTRIBUTION REWRITE
                        // states the barrier on the branch it emits — this is an optimization of the plan, never a
                        // property of the instruction.
                        .dom(A.maybe(), "maybe an obj: one obj at a time (`{n}` pages it, `{*}` gathers the whole lhs)")
                        .rng(B.maybeSome(), "the branch results as objs")
                        // REC ARGS ARE ROUTES — `place => program`. The key is a TYPE (uri::T), not a name: the
                        // declaration states the SHAPE of the caller's keys, so the caller supplies them all and
                        // the resolver keeps every one (a rec is open-world: it may carry more than its contract
                        // names). An ordered call is still the local branch, so one definition serves both.
                        .args(rec(T(URI_TID.b(cInt.MAYBESOME())), T(B.b(cInt.MAYBESOME()).some())))
                        .argDocs(Map.of(T(URI_TID.b(cInt.MAYBESOME())), "the individual branches key'd by evaluation location"))
                        .desc("""
                                a branching function: the lhs applied to each argument.
                                the dom coefficient is the SHIPMENT GRAIN: `{?}` (the default) feeds one obj per call;
                                `{n}` pages it (a buffer of at most n per call); `{*}` gathers the whole lhs once and
                                passes it to every member — the barrier a DISTRIBUTION REWRITE states on the branch it
                                emits.
                                an ordered call is the local branch: each member applied at current machine, from the
                                machine the branch stands in, and the merge returns there.
                                a LANE is a member headed by a move, move(<place>).program, which compiles to the cut
                                cut(<place>, program): a path names a local machine (<..> its parent, </.> the root,
                                </abc>, <abc> a child), whose compiler compiles the lane and whose processor runs it; a
                                host names a peer, and the path past the surface it was dialed at names the machine in it
                                the lane runs at; a host with no dialed peer is unreachable. a program that is one
                                from(</path>) or one to(</path>) of one obj, at the peer itself, needs only that verb
                                there, so a client that reads and writes but runs no program serves it. every lane runs on
                                its own virtual::T, so the lanes run concurrently. a member with no move is a home lane.
                                a keyed call is sugar for its lanes, and compiles to them: `place => program` is the lane
                                cut(<place>, program), whole at its place, <~> or <.> a home lane, a key's ?timeout its
                                cut's, and the keys' splits the branch's (mixed copy and part keys are refused). a
                                designator key, <peer> or <#>, is a lane at each place it names when the branch runs, as
                                ?place resolves it: no place, no lane.
                                branch?split=part deals the lhs evenly over the lanes. branch?split=[{2}//a,//b] deals
                                it by the bias that place set states: each lane, per lane, the demand of the designator
                                naming its place (local a home lane, peer any host, # any lane), one share where none
                                does. a key's ?split={w}part is its share, and an unmarked key (or ?split=copy) gets the
                                whole lhs. without a split every lane gets the whole lhs. a lane's point is copies of
                                what it answers, never a share. ?timeout=n is a cut's: move?timeout=n(<place>) waits n seconds for its
                                lane, and branch?timeout=n is every lane's that states none (a key's own overrides it).
                                arguments are blocked, so a call is not computed here.
                                """)
                        // THE BODY LIVES ON ITS CLASS: the definition is minted a BranchInst, and a call bound from it is
                        // a clone of it, so the bound call routes its own lhs
                        .inst(BranchInst::new)
                        .body((lhs, inst) -> ((BranchInst) inst).route(lhs))
                        // THE BRANCH'S OWN LAW — the additive operation: associative with noobj as its identity
                        // (monoidic) and pure. NOT commutative: its keys route, its split deals in order, and its
                        // lanes may have effects. The lanes' laws never promote the branch's; the commutativity a
                        // shard needs is the CUT's combiner.
                        .law(Law.monoidic, Law.pure)
                        .create(),
                InstBuilder.build()
                        .tid(REPEAT_INST_TID.q(MONAD_IN, "+").q(MONAD_OUT, "+"))
                        .dom(A, "any obj")
                        .rng(A.maybeSome(), "the repeated application results")
                        .args(rec(
                                uri(ESC).maybe().asUri(), T(A.maybeSome()),
                                uri(CODE), T(A.maybeSome()),
                                uri(UNTIL).maybe(), BOOL_TYPE,
                                uri(EMIT).maybe(), BOOL_TYPE))
                        .argDocs(Map.of(
                                uri(ESC).maybe(), "the escape predicate",
                                uri(CODE), "the code to repeat",
                                uri(UNTIL).maybe(), "the exit predicate (true stops the loop)",
                                uri(EMIT).maybe(), "the emit predicate"))
                        .desc("a repetition function: loopback application of the code with escape/exit/emit predicates. "
                                + "With an unbounded dom (repeat?<=A{*}) it is the BSP form: the barrier hands over the whole frontier "
                                + "and each pass applies the code to all of it at once (a dealing branch in the code parts it over the "
                                + "peers), loop() counts passes and is shared by every member, esc/emit/until are judged per member, "
                                + "and a member that becomes a fail leaves the loop as its answer")
                        .body((lhs, inst) -> {
                            // REPEAT?<=A{*} IS THE BSP FORM: the barrier hands repeat the whole frontier, a bare stream
                            // rather than a monad, once (repeatFrontier loops the passes itself)
                            if (inst.isGather())
                                return repeatFrontier(lhs, inst);
                            // a PROFILED run measures the loop's phases: the escape/emit/until predicate applies,
                            // the repeated code's apply, and the loop-frame bookkeeping (the monad state churn) --
                            // repeat/esc, emit, until, code and state -- and counts each pass (repeat/loop)
                            final Analyzer profile = Analyzer.PROFILE.get();
                            if (null != profile)
                                profile.incr(REPEAT_LOOP_PROBE, 1L);
                            final long book0 = null == profile ? 0L : System.nanoTime();
                            final Obj escPredicate = inst.arg(f(ESC), 0);
                            final Obj repeatedApply = inst.arg(f(CODE), 1);
                            final Obj untilPredicate = inst.arg(f(UNTIL), 2);
                            final Obj emitPredicate = inst.arg(f(EMIT), 3);
                            final List<Obj> toEmit = new ArrayList<>();
                            final StatefulMonad monad = StatefulMonad.of(lhs);
                            if (null != profile)
                                profile.record(REPEAT_STATE_PROBE, System.nanoTime() - book0);
                            final long esc0 = null == profile ? 0L : System.nanoTime();
                            // esc through the same lens as until and emit: applied bare, a one-instruction predicate
                            // (esc=>is(gt(3))) received the whole monad and never fired (Marko, 2026-11-22)
                            final Obj escCode = escPredicate.isCall() ? escPredicate.<Call>as().asCode() : escPredicate;
                            final boolean esc = escCode.apply(monad).booleanCheck();
                            if (null != profile)
                                profile.record(REPEAT_ESC_PROBE, System.nanoTime() - esc0);
                            if (esc) {
                                toEmit.add(monad.loopback(false).nextInst());
                            } else {
                                if (monad.isNoObj() || monad.obj().isNoObj()) {
                                    toEmit.add(monad.loopback(false).nextInst());
                                } else {
                                    // fresh entry pushes a loop frame; a loopback continuation does not
                                    final long book1 = null == profile ? 0L : System.nanoTime();
                                    final boolean loopback = monad.isLoopback();
                                    final StatefulMonad m = monad.loopback(false);
                                    final StatefulMonad repeatMonad = loopback ? m : m.pushLoop();
                                    if (null != profile)
                                        profile.record(REPEAT_STATE_PROBE, System.nanoTime() - book1);
                                    final Obj emitCode = emitPredicate.isCall() ? emitPredicate.<Call>as().asCode() : emitPredicate;
                                    final long emit0 = null == profile ? 0L : System.nanoTime();
                                    final boolean emit = emitCode.apply(repeatMonad).booleanCheck();
                                    if (null != profile)
                                        profile.record(REPEAT_EMIT_PROBE, System.nanoTime() - emit0);
                                    if (emit)
                                        toEmit.add(repeatMonad.popLoop().nextInst());
                                    // wrap the predicate in code so a single-inst until (is(gt(10))) goes
                                    // through the monad lens like the multi-inst form (loop().is(gt(3))) —
                                    // applied bare, is(gt(10)) receives the whole monad and never terminates.
                                    final Obj untilCode = untilPredicate.isCall() ? untilPredicate.<Call>as().asCode() : untilPredicate;
                                    final long until0 = null == profile ? 0L : System.nanoTime();
                                    final boolean until = untilCode.apply(repeatMonad).booleanCheck();
                                    if (null != profile)
                                        profile.record(REPEAT_UNTIL_PROBE, System.nanoTime() - until0);
                                    if (until) {
                                        if (!emit)
                                            toEmit.add(repeatMonad.popLoop().nextInst());
                                    } else {
                                        final long code0 = null == profile ? 0L : System.nanoTime();
                                        final Obj codeResult = repeatedApply.apply(repeatMonad.obj());
                                        if (null != profile)
                                            profile.record(REPEAT_CODE_PROBE, System.nanoTime() - code0);
                                        final long book2 = null == profile ? 0L : System.nanoTime();
                                        final StatefulMonad monadX = repeatMonad.obj(codeResult).incrLoop(1).loopback(true);
                                        if (null != profile)
                                            profile.record(REPEAT_STATE_PROBE, System.nanoTime() - book2);
                                        toEmit.add(monadX);
                                    }
                                }
                            }
                            return objs(toEmit);
                        })
                        // REPEAT IS PURE (Marko, 2026-11-22): purity is the instruction's own law, not its arguments', and
                        // repeat itself does nothing that is not functionally pure.
                        // NOTE, OPEN (Marko will address it): Inst.isPure, the test a planner reads before it moves a call
                        // (law_shard's floatable run, law_bsp's dealt code, law_absorbing), is the instruction's law AND
                        // every argument's (Inst.Helper.isPure), and since 8506da5fc a keyed argument (a Rel: repeat's
                        // code=>, a branch's //0=>) counts too. So repeat(code=>plus(1).print(_)) reads impure and stays
                        // home, though repeat is pure. The two questions are conflated: what an instruction does, and
                        // whether a call may move with what it is handed. The same holds for branch, which never inspects
                        // its lanes for its own law (monoidic, pure), yet a branch call with an effectful lane now reads
                        // impure. Kept as is for now (InstTest's purity rows pin today's behavior).
                        .law(Law.pure)
                        .create(),
                InstBuilder.build()
                        .tid(ORDER_INST_TID, "a sorting function \\(f(X) \\mapsto X'\\): the distinct members of the lhs, each once with its whole mass, sorted by the arg's projection of them. A projection that reads mass (cc()) sorts by it; such a sort ships no lane, since a shard parts each value's mass across its lanes, so the members come home and sort where every mass is whole")
                        .dom(A.maybeSome(), "maybe some A to sort")
                        // equal objs sort to one index as a point ({1.1,1.1}.order() is [{2}1.1]), so its indices take
                        // a mass of zero or more: lst[{*}A{*}], the left coefficient a bracket entry's mass (Marko,
                        // 2026-11-18). Not merge's {**}: a negative member is not ruled on here
                        .rng(LST_TID.poly(A.maybeSome().b(cInt.MAYBESOME())), "a sorted lst of the lhs A{*}")
                        .argLst(T(ALL.maybe()), "a projection of A to compare")
                        // BULK, THEN SORT (Marko, 2026-11-20): a stream bulks, and it has no nested streams
                        // ({1,2,{5,3},5,{0,1,1}} is {{3}1,2,{2}5,3,0}). objs() bulks as it builds, so the sort meets each
                        // distinct obj once with its whole mass (equals that arrive apart, {1,2,3}.mult(0), sort as one
                        // entry, not three), and equals share one index even where a projection's ties would sort them apart
                        .body((lhs, inst) -> {
                            final Comparator<Obj> cmp = inst.arg(0).isNoObj() ? CommonUtil.ObjComparator.single() : CommonUtil.ObjProjectionComparator.of(inst.arg(0));
                            return lst(objs(lhs.stream()).stream().sorted(cmp).toList());
                        })
                        .example("{3,1,2}.order()                            [-- [1,2,3] --]")
                        .example("{3,1,2}.order(neg())                       [-- [3,2,1] --]")
                        .example("{1,1,1,2,3,3}.order(cc())                  [-- [2,{2}3,{3}1]: by mass, each value's whole mass --]")
                        .example("{1,1,1,2,3,3}.order(cc().neg())==[{2}_]    [-- [{3}1,{2}3]: the two heaviest --]")
                        .law(Law.pure)
                        .derivation(order_(T(ALL.maybe(), f("b"), null, null)), split_(lst(id_().tryToInst())).kmerge_(T(ALL.maybe(), f("b"), null, null)))
                        .create(),
                // GROUP (moved from Obj, Marko 2026-11-22). BULK, THEN KEY, as order bulks before it sorts: the objs it
                // builds meet each distinct member once with its whole mass, so a key that reads mass (cc()) reads all of
                // it. It once keyed the stream as it flowed, so {1,2,3,4}.mod(2).group([cc()=>count()]) keyed every member
                // by 1 while {1,1,2}.group([cc()=>count()]) keyed {2}1 by 2: one bag, two answers. A fold that reads
                // copies (count, sum, mean) answers as before; a key that reads mass now reads all of it, and a value fold
                // that reads order (take, reduce) meets each group as the bag it is, a bulked member arriving whole.
                InstBuilder.build()
                        .tid(GROUP_INST_TID)
                        .dom(A.maybeSome(), "any objs")
                        .rng(REC_TID, "a rec of the grouped results")
                        .argLst(T(REC_TID), "the grouping key rec, the value inst computing each group")
                        .desc("a grouping function \\(f(X) \\mapsto x'\\): groups the distinct members of the lhs, each with its whole mass, by the key rec and computes each group (sugar'd %==)")
                        .example("{1,1,1,2,3,3}.group([mod(2)=>count()])        [-- [1=>5,0=>1] --]")
                        .example("{1,1,1,2,3,3}.group([mod(2)=>mean()])         [-- [1=>1.8,0=>2.0], each group's mean weighed by its copies --]")
                        .example("{1,2,3,4}.mod(2).group([cc()=>count()])       [-- [2=>4], each member keyed by its whole mass --]")
                        .body((lhs, inst) -> {
                            final Map<Obj, Obj> result = new LinkedHashMap<>();
                            objs(lhs.stream()).stream().forEach(e -> inst.arg(0).asRec().elements().forEach(kv -> {
                                final Obj kk = kv.first().isObjCall()
                                        ? kv.first().apply(e)
                                        : (e.isRec() ? e.asRec().at(kv.first()) : e);
                                if (!kk.isNoObj()) // TODO: if the group value is not a barrier, then process immediately.
                                    // a key is an identity, not a multiplicity: a bulked member's coefficient is counted by
                                    // the group it joins ({2}x keys as x), never carried on the key
                                    result.compute(kk.c(cInt::one), (k, v) -> (v == null) ? lst(kv.second(), e) : v.asLst().at(jnt(1), v.asLst().at(jnt(1)).append(e), MUTABLE));
                            }));
                            return result.entrySet().stream()
                                    .map(kv -> rel(
                                            kv.getKey(),  // key
                                            kv.getValue().asLst().at(0).apply(kv.getValue().asLst().at(jnt(1)))))  // compute barriered value
                                    .collect(new CommonUtil.RecCollector());
                        })
                        .law(Law.pure)
                        .create(),
                InstBuilder.build() // SWAP — swap(lhs, g) = g(lhs): the lhs becomes the inst's lhs, the inst's lhs becomes its arg
                        .tid(SWAP_INST_TID)
                        .dom(A, "the lhs to swap for the swap arg")
                        .rng(A, "the result of applying the swap arg to the lhs")
                        .argLst(T(B), "a obj to make the lhs")
                        .desc("""
                                takes the swap argument and makes it the lhs obj.
                                if the argument is an inst, then the original lhs becomes the inst arg and the inst arg becomes the lhs.
                                if an inst arg is wanted for the swap application, then it must be blocked (|) so as to delay its evaluation.""")
                        // if an inst arg is double blocked, then the inst arg is treated as a value and applied to the original lhs obj.
                        .example("abc.mult(xyz)             [-- abc/xyz --]")
                        .example("abc.swap(|mult(xyz))      [-- xyz/abc --]")
                        .example("|-<[_,_].swap(6)          [-- [6,6]   --]")
                        // AN INVOLUTION ON CODE: swap(swap(c)) = c, so the operation is its own inverse (its `inverse`
                        // is itself) and it is pure — a code value in, a code value out, nothing written.
                        .law(Law.involution, Law.pure)
                        .inverse(SWAP_INST_TID)
                        .body((lhs, inst) -> {
                            final boolean swapInstArg = inst.arg(0).isCall() && !inst.arg(0).asCall().insts().getFirst().tid().basePath().equals(BLOCK_INST_TID);
                            final Obj newLHS = swapInstArg ? inst.arg(0).asCall().insts().getFirst().arg(0) : inst.arg(0);
                            return swapInstArg ? inst.arg(0).asCall().insts().getFirst().args(lst(lhs)).apply(newLHS) : lhs.apply(newLHS);
                        })
                        .create(),
                docWrap(InstBuilder.build()
                                .tid(FROM_INST_TID)
                                .dom(ALL.maybe())
                                .rng(B.maybeSome())
                                .args(lst(URI_TYPE)).body((lhs, inst) -> {
                                    final Obj readObj = Machine.read(inst.arg(0).isInt() ? f(inst.arg(0).intValue().intValue()) : inst.arg(0).uriValue());
                                    // A COMPONENT IS ITS LOCUS, so a dereference hands it back AS ITSELF. Types, machines
                                    // and SPACES are exempt from the detaching copy: everything else may be copied and
                                    // have its address stripped (a dereferenced value is a value, not a place), but a
                                    // component read through an address is the ONE object stored there. Copying one made
                                    // every dereference of `*~/instset` a different object from `*</instset>` — two
                                    // reads of one slot that could not be compared — and no identity-stable read of a
                                    // memory, an ISA or a mounted space was possible at all. A NotDetachable obj (a thread)
                                    // is one too: a copy shared the thread's Java thread but not its rec, so mtron's stop
                                    // wrote its state into the copy, and pause and resume reached no thread at all.
                                    return readObj.isType() || readObj.isMachine() || readObj.isAddressable()
                                            || readObj instanceof NotDetachable
                                            ? readObj
                                            : readObj.clone().selfVID(null);
                                })
                                .law(Law.monoidic)
                                .create(), // TODO: only resolves when explicit mono args (not code args)
                        "any obj", "the obj referred to by the arg uri", Map.of(jnt(0), "the uri to dereference"), "dereferences a uri to an obj (sugar'd *)",
                        "*abc        [-- obj at abc                            --]",
                        "abc.*_      [-- obj at abc via dynamic arg generation --]",
                        "c.*ab${_}   [-- obj at abc via uri template parameter --]",
                        "from(abc)   [-- obj at abc non-sugar form             --]"),
                InstBuilder.build()
                        .tid(PROFILE_INST_TID, "generates a profiling::T analysis of the current expression")
                        .dom(ALL.maybe(), "the expression being profiled")
                        .rng(PROFILING_TID, "a profiling::T analysis of the expression")
                        // SINGLE MACHINE OR DISTRIBUTED, profile() IS A REDUCER: it is the barrier the
                        // monads stream into, and ONE compressed report comes out the other side. The form
                        // is DECLARED, not derived — the dom is `maybe` (a bulk dom made the monad loop
                        // apply this inst *and* flush it through the barrier, answering two reports), and
                        // Form.of reads a maybe-to-one dom as a mapper. The inst this marker MINTS
                        // (machInstSet.profileAnalysis) carries the same form and the same three laws, so
                        // the profile() a writer types and the profile_analysis() that runs cannot disagree
                        // about the act: monoidic (the partials regroup across shards), commutative (a
                        // collation is order-free) and pure (it observes, it does not mutate).
                        .form(Inst.Form.reducer)
                        .law(Law.monoidic, Law.commutative, Law.pure)
                        // the profile_analysis rewrite does the work; bare profile() is a no-op
                        .body((lhs, inst) -> lhs)
                        .desc("""
                                a profiling::T analysis of prior instruction in the code — 
                                every monad the run produced up to the marker is aggregated into one compressed report (stages, per-instruction times, rewrite rules, flow, cache)""")
                        .example("*<http://markorodriguez.com>.as(rec::T)>>html/head/title.profile()",
                                "*<http://markorodriguez.com>.as(rec::T)>>html/head/title.profile()>>format")
                        .create()));
    }

    /**
     * REPEAT OVER THE WHOLE FRONTIER ({@code repeat?<=A{*}}, Marko 2026-11-22), the BSP form. The swarm's barrier
     * hands repeat everything that reached it, once, as one stream, and each pass applies the code to the whole
     * frontier at once, so a pass is a superstep: a dealing branch in the code
     * ({@code branch?<=A{*}&split=part(...)}) parts the frontier over the peers and returns only when every lane
     * has, and the next pass starts after it. The barrier fires once and is not re-armed, so the passes loop here
     * rather than back through the processor the way the per-monad form's loopback monads do.
     * <p>
     * {@code loop()} counts the passes and is the same for every member. esc, emit and until are judged per member
     * against it, each on a monad sitting at repeat with the pass on its loop stack, through the same code lens the
     * per-monad form uses: emit keeps a copy and the member goes on, until (or esc) lets it leave. A member that
     * becomes a fail leaves too, as its answer. The members that stay are the next frontier, bulked by the code's own
     * stream, so the traversers at one vertex merge between passes. The loop ends when the frontier is empty, or when
     * its thread is interrupted.
     */
    private static Obj repeatFrontier(final Obj frontier, final Inst inst) {
        final UnaryOperator<Obj> lens = p -> p.isCall() ? p.<Call>as().asCode() : p;
        final Obj esc = lens.apply(inst.arg(f(ESC), 0));
        // the code runs over the frontier as a batching branch's member runs over its bag: start(bag).code, so the
        // start streams the bag and a map meets one obj at a time while a gather (a dealing branch) meets it whole
        final Obj codeArg = inst.arg(f(CODE), 1);
        final List<Inst> code = codeArg.isCall() ? codeArg.asCall().insts()
                : List.of((Inst) map_(codeArg).tryToInst());
        final Obj until = lens.apply(inst.arg(f(UNTIL), 2));
        final Obj emit = lens.apply(inst.arg(f(EMIT), 3));
        final List<Obj> left = new ArrayList<>();
        List<Obj> members = frontier.stream().filter(m -> !m.isNoObj()).toList();
        for (int pass = 0; !members.isEmpty(); pass++) {
            // the swarm checks its STATE between steps, and this loop is one step: an interrupt (a stopped threaded
            // computer, a caller's timeout) ends it here, the flag left set for whoever waits on the thread
            if (Thread.currentThread().isInterrupted())
                throw MTronException.of("repeat?<=A{*} interrupted after %s passes", pass);
            final List<Obj> next = new ArrayList<>();
            for (final Obj member : members) {
                // A FAIL LEAVES AS ITS MEMBER'S ANSWER: it passes through every predicate (booleanCheck() is false on
                // a fail), so kept in the frontier it came back every pass and the loop never ended
                if (member.isFail() || member.isCaughtFail()) {
                    left.add(member);
                    continue;
                }
                final StatefulMonad at = BasicStatefulMonad
                        .statefulMonad(member, inst, rec(), MCode.code(List.of(noobj())))
                        .pushLoop().incrLoop(pass);
                if (esc.apply(at).booleanCheck()) {
                    left.add(member);
                    continue;
                }
                final boolean emitted = emit.apply(at).booleanCheck();
                if (emitted)
                    left.add(member);
                if (until.apply(at).booleanCheck()) {
                    if (!emitted)
                        left.add(member);
                } else
                    next.add(member);
            }
            members = next.isEmpty() ? List.of()
                    : start_(objs(next)).addInsts(code).apply(noobj())
                    .stream().filter(m -> !m.isNoObj()).toList();
        }
        return objs(left);
    }

    /**
     * THE MEAN OF A BAG: each member weighs its coefficient, as count() counts it (Marko, 2026-11-22: "that's bad").
     * {1,1,2} is {{2}1,2}, whose mean is 4/3, never the 1.5 of its two distinct members. An empty bag's mean is noobj
     * (Marko, 2026-11-22: mean has no seed, so nothing has no mean): a sharded mean's emptied lane runs mean() inside
     * its lift and must vanish in the merge, where a zero-division fail would fail the branch. So the range is real{?},
     * and the reducer form is declared, which the range alone no longer shows.
     */
    private static Obj mean(final Obj lhs, final java.util.function.ToDoubleFunction<Obj> value) {
        double sum = 0.0;
        long copies = 0L;
        for (final Obj member : lhs.stream().toList()) {
            final Long c = member.c().max();
            final long weight = null == c ? 1L : c;
            sum += weight * value.applyAsDouble(member);
            copies += weight;
        }
        return 0L == copies ? noobj() : real(sum / copies);
    }

    /**
     * THE ORDER SUP AND INF BOUND IN: the arg's projection when given, as order's, else the members themselves, with
     * ties broken by each member's written form. A tie must not fall to whichever member came first, as a bag has no
     * first and both folds declare commutative: {@code {[a=>1,b=>1],[a=>1,b=>2]}.sup(_>>a)} answers the same member
     * written either way round.
     */
    private static Comparator<Obj> bound(final Inst inst) {
        final Comparator<Obj> cmp = inst.arg(0).isNoObj() ? CommonUtil.ObjComparator.single() : CommonUtil.ObjProjectionComparator.of(inst.arg(0));
        return cmp.thenComparing(Obj::toString);
    }

    /**
     * The mean of a carrier's stream as a chain, {@code mean?real{?}<=carrier{*}()}: the lhs of that overload's equation.
     * It states its domain and range because a keyed mean binds an equation's lhs as a value tests a type, and a bare
     * {@code mean()} declares {@code #} for both, which neither a stream nor a range that admits none fits.
     */
    private static Code mean_(final fURI carrier) {
        return inst_(MInst.instB(MEAN_INST_TID.dom(carrier.maybeSome()).rng(REAL_TID.maybe()), lst()));
    }

    /**
     * MEAN, LIFTED (menu entry J, algebraic aggregates): {@code ℓ·mean}, where the lift {@code ℓ} summarizes a part as
     * its mean weighted by its count, {@code {n}m}. The split gathers the whole part ({@code ?<=carrier{*}}), the count
     * becomes the coefficient, and the mean is what the projection keeps. The weighted mean of the summaries is the mean
     * of the whole, and the lift is itself a self-combining fold (two summaries lift to the summary of their union), so
     * mean declares {@code mean ↦ ℓ·mean} and a sharded mean ships the summaries home (Law.Helper#lifted).
     */
    private static Code liftedMean(final fURI carrier) {
        return inst_(MInst.instB(SPLIT_INST_TID.dom(carrier.maybeSome()), lst(lst(MInst.instB(MEAN_INST_TID, lst()), count_().tryToInst()))))
                .cc_(id_().rshift_(jnt(1)))
                .rshift_(jnt(0))
                .addInst(MInst.instB(MEAN_INST_TID, lst()));
    }
}
