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
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.InstBuilder;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.m.type.impl.MStr;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.Tuple;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.Str.Helper.REGEX_CACHE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
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
 * ({@code ?lawq}, staged and persisted by the set's drain). A type's algebra is declared at the type
 * ({@link mTypes}); this class declares what its OPERATIONS do.
 *
 * <p>THE MIGRATION RULE: an instruction moves here when its law-table row carried LAWS only — or, now that the
 * builder states them, an {@code inverse} (int's {@code mult}→{@code div} retraction) or a {@code derivation}
 * (int's {@code minus}, real's {@code minus}/{@code div}). A row survives while it carries a claim the builder
 * cannot express: the {@code as} pairs, whose direction IS the claim, and a body that needs a helper its slice
 * cannot import. Those retire as {@code InstBuilder} grows the remaining facets; the rows then delete with them.
 * <p>BECAUSE A DECLARATION IS KEYED BY THE INSTRUCTION'S N-TID ({@code tid.dom(A).rng(B)}), a migrated entry must
 * sit at the ROW'S OWN address ({@code tid(op).dom(A).rng(B)}): that is how the declaration is served in place of
 * the row, and how {@link studio.phaseshift.metatron.isa.m.math.cat.LawTable#derivations()} dedupes the two
 * forms into one rule.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class mInsts {

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
                        .law(Law.involution, Law.pure)
                        .create(),
                InstBuilder.build().tid(DIV_INST_TID, "divide the lhs int by the argument int")
                        .dom(INT_TID, "the dividend int").rng(INT_TID, "the truncated quotient")
                        .argLst(INT_TYPE, "the divisor int")
                        .body((lhs, inst) -> jnt(Double.valueOf(lhs.intValue().doubleValue() / inst.arg(0).intValue().doubleValue()).longValue(), lhs.tid(), lhs.vid()))
                        .law(Law.action, Law.pure)
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
                        .rng(REAL_TID, "the mean of the lhs int stream").argLst()
                        .body((lhs, inst) -> real(lhs.stream().mapToLong(Obj::intValue).average().orElse(0L)))
                        .examples("{1.0,2.0,3.0}.mean() [-- 2.0 --]")
                        .law(Law.magmadic, Law.commutative, Law.pure)
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
                        .examples("{1,2,3,4}.prod() [-- 24 --]")
                        .law(Law.monoidic, Law.commutative, Law.semi_right_distributive, Law.pure)
                        .create(),
                InstBuilder.build().tid(ORDER_INST_TID, "order the lhs int stream in ascending order")
                        .dom(INT_TID.maybeSome(), "a stream of ints")
                        .rng(LST_TID.poly(INT_TID.maybeSome()), "the sorted lst of ints").argLst()
                        .body((lhs, inst) -> lst(lhs.stream().sorted(Comparator.comparing(a -> a.asInt().intValue()))))
                        .create(),
                InstBuilder.build().tid(SUM_INST_TID, "add the lhs int stream into a single int")
                        .dom(INT_TID.maybeSome(), "zero or more ints").rng(INT_TID, "a single plus reduced int")
                        .argLst().seed(jnt(0))
                        .body((lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> ((studio.phaseshift.metatron.isa.m.type.Int) a).plus((studio.phaseshift.metatron.isa.m.type.Int) b)).intValue()))
                        .examples("{1,2,3,4}.sum() [-- 10 --]")
                        .law(Law.monoidic, Law.commutative, Law.semi_right_distributive, Law.pure)
                        .create(),
                // MULT AND MINUS DECLARE THE TWO RELATIONS A ROW STILL CARRIED: mult's one-way retraction to div
                // (int models a mult_monoid, not a group — 5.div(2).mult(2) is 4) and minus's defining equation
                // (minus ≡ plus ∘ neg), which is what lets int's add_group derive the pair instead of declaring it
                InstBuilder.build().tid(MULT_INST_TID, "multiply the lhs int by the argument int")
                        .dom(INT_TID, "the lhs int").rng(INT_TID, "the result of the multiplication")
                        .argLst(INT_TYPE, "the int to multiply the lhs by")
                        .body((lhs, inst) -> lhs.jvm(lhs.intValue() * inst.arg(0).intValue()))
                        .law(Law.commutative, Law.right_distributive, Law.action, Law.pure)
                        .inverse(DIV_INST_TID.dom(INT_TID).rng(INT_TID))
                        .create(),
                InstBuilder.build().tid(MINUS_INST_TID, "subtract the argument int from the lhs int")
                        .dom(INT_TID, "the lhs int").rng(INT_TID, "the result of the subtraction")
                        .argLst(INT_TYPE, "the int to subtract from the lhs")
                        .body((lhs, inst) -> lhs.jvm(lhs.intValue() - inst.arg(0).intValue()))
                        .derivation(new Derivation(start_(uri(InstSet.A)).minus_(uri(InstSet.B)), start_(uri(InstSet.A)).plus_(map_(uri(InstSet.B)).neg_())))
                        .law(Law.action, Law.pure)
                        .create(),
                // THE as PAIR, DECLARED BESIDE ITS INSTRUCTIONS (the human's ruling): int->str is the SOUND
                // direction -- print an int, read the printed str back, and the value returns (42 -> "42" -> 42) --
                // so it declares the opposing edge. str->int is LOSSY: every int renders, but only SOME strs read
                // back ("007" -> 7 -> "7"), so it declares NO inverse. The pair's law-table rows are deleted; the
                // one claim that survives is the sound one, stated here.
                InstBuilder.build().tid(AS_INST_TID, "print the lhs int into a str")
                        .dom(INT_TID, "the int to convert").rng(STR_TID, "the lhs int as a str")
                        .argLst(STR_TYPE, "the str type that receives the value")
                        .body((lhs, inst) -> str(lhs.intValue().toString(), inst.arg(0).vidOrTid().c(c -> c.mult(lhs.c())), lhs.vid()))
                        .law(Law.pure)
                        .inverse(AS_INST_TID.dom(STR_TID).rng(INT_TID))
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
                InstBuilder.build().tid(NEG_INST_TID, "a negation function \\(f(x) \\nearrow -x\\): the additive inverse of the lhs real")
                        .dom(REAL_TID, "a real").rng(REAL_TID, "the negated lhs real")
                        .argLst()
                        .body((lhs, inst) -> lhs.asReal().neg())
                        .law(Law.involution, Law.pure)
                        .create(),
                InstBuilder.build().tid(PLUS_INST_TID, "a sum function \\(f(x, y) \\nearrow x + y\\): adds the rhs real to the lhs real")
                        .dom(REAL_TID, "a real").rng(REAL_TID, "the sum")
                        .argLst(T(REAL_TID), "the rhs real")
                        .body((lhs, inst) -> lhs.asReal().plus(Inst.Helper.alignRHSType(lhs, inst.arg(0)).asReal()))
                        .law(Law.commutative, Law.right_distributive, Law.action, Law.pure)
                        .create(),
                InstBuilder.build().tid(MULT_INST_TID, "a product function \\(f(x, y) \\nearrow xy\\): multiplies the lhs real by the rhs real")
                        .dom(REAL_TID, "a real").rng(REAL_TID, "the product")
                        .argLst(T(REAL_TID), "the rhs real")
                        .body((lhs, inst) -> lhs.asReal().mult(Inst.Helper.alignRHSType(lhs, inst.arg(0)).asReal()))
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
                InstBuilder.build().tid(INV_INST_TID, "an inverse function \\(f(x) \\nearrow 1/x\\): the multiplicative inverse of the lhs real")
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
                        .dom(REAL_TID.maybeSome(), "a stream of reals").rng(REAL_TID, "the mean of the lhs real stream")
                        .argLst()
                        .body((lhs, inst) -> real(lhs.stream().mapToDouble(Obj::realValue).average().orElse(0.0)))
                        .examples("{1.0,2.0,3.0}.mean() [-- 2.0 --]")
                        .law(Law.magmadic, Law.commutative, Law.pure)
                        .create(),
                InstBuilder.build().tid(POW_INST_TID, "a power function \\(f(x, n) \\nearrow x^n\\): the lhs real raised to the arg real")
                        .dom(REAL_TID, "a real").rng(REAL_TID, "the lhs real raised to the arg real")
                        .argLst(T(REAL_TID), "the exponent real")
                        .body((lhs, inst) -> real(Math.pow(lhs.realValue(), inst.arg(0).realValue())))
                        .law(Law.pure)
                        .create(),
                // DIV AND MINUS DECLARE THEIR DEFINING EQUATIONS, which is what lets real's field (and its
                // add_group) derive the inverse pairs instead of a row declaring them: div ≡ mult ∘ inv,
                // minus ≡ plus ∘ neg. The carrier states the group; the equation states which instruction it is.
                InstBuilder.build().tid(DIV_INST_TID, "a quotient function \\(f(x, y) \\nearrow x/y\\): the lhs real divided by the rhs real")
                        .dom(REAL_TID, "a real").rng(REAL_TID, "the quotient")
                        .argLst(T(REAL_TID), "the rhs real")
                        .body((lhs, inst) -> lhs.asReal().div(Inst.Helper.alignRHSType(lhs, inst.arg(0)).asReal()))
                        .derivation(new Derivation(start_(uri(InstSet.A)).div_(uri(InstSet.B)), start_(uri(InstSet.A)).mult_(map_(uri(InstSet.B)).inv_())))
                        .law(Law.right_distributive, Law.action, Law.pure)
                        .create(),
                InstBuilder.build().tid(MINUS_INST_TID, "a difference function \\(f(x, y) \\nearrow x - y\\): the rhs real subtracted from the lhs real")
                        .dom(REAL_TID, "a real").rng(REAL_TID, "the difference")
                        .argLst(T(REAL_TID), "the rhs real")
                        .body((lhs, inst) -> lhs.asReal().minus(Inst.Helper.alignRHSType(lhs, inst.arg(0)).asReal()))
                        .derivation(new Derivation(start_(uri(InstSet.A)).minus_(uri(InstSet.B)), start_(uri(InstSet.A)).plus_(map_(uri(InstSet.B)).neg_())))
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
                        .argLst(T(STR_TID)).argDocs(Map.of(jnt(0), "a postfix str"))
                        .body((lhs, inst) -> lhs.jvm(lhs.strValue() + inst.arg(0).strValue()))
                        .examples("\"a\" + \"b\" [-- \"ab\" --]")
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
                // uri), so they declare no inverse at all. Every row that used to claim otherwise is deleted.
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
                // laws are the ones their rows carried: split is pure, merge is the monoid's own op and pure.
                InstBuilder.build().tid(SPLIT_INST_TID, "split the lhs string according to the token arg and insert components into an ordered lst")
                        .dom(STR_TID, "a str to split").rng(LST_TID, "lst encoded components of the split lhs str")
                        .argLst(STR_TYPE, "a token to split on")
                        .body((lhs, inst) -> Arrays.stream(CommonUtil.splitSafely(REGEX_CACHE.computeIfAbsent(inst.arg(0).strValue(), CommonUtil::guardPattern), lhs.strValue())).map(MStr::str).collect(new CommonUtil.LstCollector()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(MERGE_INST_TID, "join the barrier given the str arg")
                        .dom(STR_TID.maybeSome(), "an str barrier").rng(STR_TID, "the join of the str barrier")
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
                InstBuilder.build().tid(REVERSE_INST_TID, "reverse the path of the lhs uri")
                        .dom(URI_TID, "the uri to reverse").rng(URI_TID, "the uri with its path reversed")
                        .argLst()
                        .body((lhs, inst) -> lhs.jvm(lhs.uriValue().path(lhs.asUri().uriValue().path().reversed())))
                        .law(Law.involution, Law.pure)
                        .create(),
                InstBuilder.build().tid(MERGE_INST_TID, "merge a uri stream into a single uri")
                        .dom(URI_TID.maybeSome(), "zero or more uris").rng(URI_TID, "a single merged uri")
                        .argLst(T(URI_TID), "the uri placed between each merge")
                        .body((lhs, inst) -> uri(lhs.stream().map(Obj::uriValue).reduce((a, b) -> a.extend(inst.arg(0).uriValue()).extend(b)).orElse(f("noobj"))))
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
                        .docDesc("a branch function \\(f(x, y) \\nearrow x \\sqcup y\\): the union of the lhs and rhs uri paths")
                        .law(Law.commutative, Law.pure)
                        .create(),
                InstBuilder.build().tid(MULT_INST_TID, "concatenate the path of the argument uri onto the path of the lhs uri")
                        .dom(URI_TID, "a uri").rng(URI_TID.maybe(), "the path extension")
                        .argLst(T(URI_TID.maybe()), "the rhs uri")
                        .body((lhs, inst) -> lhs.asUri().mult(inst.arg(0).asUri()))
                        .docDesc("a product function \\(f(x, y) \\nearrow xy\\): concatenates the rhs uri path onto the lhs uri path")
                        .law(Law.monoidic, Law.pure)
                        .create(),
                InstBuilder.build().tid(ONE_INST_TID, "the one of the uri monoid")
                        .dom(URI_TID, "a uri").rng(URI_TID, "the uri one")
                        .argLst()
                        .body((lhs, inst) -> lhs.asUri().one())
                        .docDesc("a one function: the uri one (<>)")
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(SUM_INST_TID, "add a uri stream into a single uri")
                        .dom(URI_TID.maybeSome(), "zero or more uris").rng(URI_TID, "a single plus reduced uri")
                        .argLst()
                        .seed(uri(NOOBJ))
                        .body((lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> ((Uri) a).plus((Uri) b)).uriValue()))
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
                InstBuilder.build().tid(POW_INST_TID, "concatenate the path of the lhs uri onto itself the argument number of times")
                        .dom(URI_TID, "the base uri").rng(URI_TID, "the repeated uri")
                        .argLst(T(INT_TID), "the number of repetitions")
                        .body((lhs, inst) -> {
                            final int pow = inst.arg(0).intValue().intValue();
                            if (0 == pow) return lhs.jvm(f(""));
                            fURI u = null;
                            for (int i = 0; i < pow; i++) {
                                u = null == u ? lhs.uriValue() : u.mult(lhs.uriValue());
                            }
                            return lhs.jvm(u);
                        })
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
                        .body((lhs, inst) -> lhs.uriValue().hasHost() ? uri(lhs.uriValue().host()) : noobj())
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
     * {@code lst::T}'s operations: the slot monoid, the selectors, and the folds.
     */
    public static Set<Inst> lsts_insts() {
        return new LinkedHashSet<>(List.of(
                InstBuilder.build().tid(REVERSE_INST_TID, "reverse the slots of the lhs lst")
                        .dom(LST_TID, "the lst to reverse").rng(LST_TID, "the lst with its slots reversed")
                        .argLst()
                        .body((lhs, inst) -> lhs.jvm(lhs.asLst().jvm().reversed()))
                        .law(Law.involution, Law.pure)
                        .create(),
                InstBuilder.build().tid(ZERO_INST_TID, "the zero of the lst monoid")
                        .dom(LST_TID, "the lhs lst").rng(LST_TID, "the empty lst")
                        .argLst()
                        .body((lhs, inst) -> lhs.asLst().zero())
                        .law(Law.unit, Law.idempotent, Law.pure)
                        .create(),
                InstBuilder.build().tid(PLUS_INST_TID, "append the slots of the argument lst onto the slots of the lhs lst")
                        .dom(LST_TID, "the lhs lst").rng(LST_TID, "the concatenated lst")
                        .argLst(LST_TYPE, "the lst to append")
                        .body((lhs, inst) -> lhs.jvm(Stream.concat(lhs.elements(), inst.arg(0).elements()).map(o -> ((Obj) o).clone().as()).toList()))
                        .law(Law.monoidic, Law.pure)
                        .create(),
                InstBuilder.build().tid(MINUS_INST_TID, "remove the slots of the argument lst from the lhs lst")
                        .dom(LST_TID, "the lhs lst").rng(LST_TID, "the lst with the argument slots removed")
                        .argLst(LST_TYPE, "the lst of slots to remove")
                        .body((lhs, inst) -> {
                            final List<Obj> values = lhs.lstValue();
                            values.removeAll(inst.arg(0).lstValue());
                            return lhs.jvm(values);
                        })
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(MULT_INST_TID, "pair every slot of the lhs lst with every slot of the argument lst")
                        .dom(LST_TID, "the lhs lst").rng(LST_TID, "the lst of slot pairs")
                        .argLst(LST_TYPE, "the lst to pair with")
                        .body((lhs, inst) -> lhs.jvm(lhs.elements().flatMap(a -> inst.arg(0).elements().map(b -> rel(a, b))).toList()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(SPLIT_INST_TID, "split the lhs into the slots of the lst the argument inst builds")
                        .dom(A, "the obj to split").rng(LST_TID, "the lst of split objs")
                        .argLst(LST_TYPE, "the list of instructions, each applied to the lhs")
                        .body((lhs, inst) -> inst.arg(0).jvm(inst.arg(0).elements().map(e -> e.apply(lhs)).toList()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(MERGE_INST_TID, "merge the slots of the lhs lst into an obj stream")
                        .dom(LST_TID, "the lst to merge").rng(A.maybeSome(), "the merged objs")
                        .argLst()
                        .body((lhs, inst) -> objs(lhs.elements()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(MERGE_INST_TID, "merge the slots of the lhs lst into one uri")
                        .dom(LST_TID, "the lst to merge").rng(URI_TID, "the merged uri")
                        .argLst(URI_TYPE, "the uri placed between each merge")
                        .body((lhs, inst) -> uri(lhs.elements().map(e -> e.uriValue().toString()).reduce("", (a, b) -> a + inst.arg(0).uriValue().toString() + b).substring(1)))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(MERGE_INST_TID, "merge the slots of the lhs lst into one str")
                        .dom(LST_TID, "the lst to merge").rng(STR_TID, "the merged str")
                        .argLst(STR_TYPE, "the str placed between each merge")
                        .body((lhs, inst) -> str(lhs.elements().map(Obj::strValue).reduce("", (a, b) -> a + inst.arg(0).strValue() + b).substring(1)))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(HAS_INST_TID, "check whether any slot of the lhs lst tests true against an argument")
                        .dom(LST_TID, "the lst to check").rng(LST_TID.maybe(), "the lhs lst when a slot test holds")
                        // no per-argument docs: the builder keys argDocs by the arg TYPE, and the second and
                        // third args are both ALL.maybe() — a duplicate key, which Map.of rejects at build time
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
                InstBuilder.build().tid(SUM_INST_TID, "append a lst stream into a single lst")
                        .dom(LST_TID.maybeSome(), "zero or more lsts").rng(LST_TID, "a single plus reduced lst")
                        .argLst()
                        .seed(lst())
                        .body((lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> ((Lst) a).plus((Lst) b)).lstValue()))
                        .law(Law.monoidic, Law.pure)
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
                            if (lhs.isLst() && inst.arg(0).intValue() < lhs.lstValue().size()) {
                                final List<Obj> newList = new ArrayList<>(lhs.lstValue());
                                final Obj result = newList.remove(inst.arg(0).intValue().intValue());
                                lhs.jvm(newList);
                                return result;
                            }
                            return noobj();
                        })
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(POW_INST_TID, "pair every slot of the lhs lst with every slot of the argument lst the argument number of times")
                        .dom(LST_TID, "the base lst").rng(LST_TID, "the repeated lst")
                        .argLst(INT_TYPE, "the number of repetitions")
                        .body((lhs, inst) -> {
                            int pow = inst.arg(0).intValue().intValue();
                            Lst l = lhs.clone(lhs.jvm(), lhs.tid(), null);
                            for (int i = 0; i < pow; i++) {
                                l = l.jvm(l.elements().flatMap(a -> inst.arg(0).elements().map(b -> rel(a, b))).toList());
                            }
                            return lhs.jvm(l);
                        })
                        .law(Law.pure)
                        .create(),
                // lst<->rec IS NOT PRESERVING IN EITHER DIRECTION -- a pair maps to a rel and a lst of rels maps to
                // a rec, but the round trip reorders and re-keys what it carries -- so neither direction declares
                // an inverse. The lossy claim its row made is deleted with the row.
                InstBuilder.build().tid(AS_INST_TID, "convert the lhs lst into the rec the argument rec type constructs")
                        .dom(LST_TID, "the lst to convert").rng(REC_TID, "the lhs lst as the argument's rec type")
                        .argLst(REC_TYPE, "the rec type whose constructor is applied to the lhs")
                        .body((lhs, inst) -> Poly.Helper.transformLstToRec(lhs.asLst(), inst.arg(0).vidOrTid(), null))
                        .law(Law.pure)
                        .create()
        ));
    }

    /**
     * {@code rec::T}'s operations: the record's merge, its entries, the reads, and the folds.
     */
    public static Set<Inst> rec_insts() {
        return new LinkedHashSet<>(List.of(
                InstBuilder.build().tid(AS_INST_TID, "convert an inst into the rec the argument rec type constructs")
                        .dom(INST_TID, "the inst to convert").rng(REC_TID, "the lhs inst as the argument's rec type")
                        .argLst(REC_TYPE, "the rec type whose constructor is applied to the lhs")
                        .body((lhs, inst) -> inst.arg(0).asType().constructor().apply(lhs).tid(inst.arg(0).vidOrTid()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(AS_INST_TID, "convert an int into the rec the argument rec type constructs")
                        .dom(INT_TID, "the int to convert").rng(REC_TID, "the lhs int as the argument's rec type")
                        .argLst(REC_TYPE, "the rec type whose constructor is applied to the lhs")
                        .body((lhs, inst) -> inst.arg(0).asType().constructor().apply(lhs).tid(inst.arg(0).vidOrTid()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(AS_INST_TID, "convert a str into the rec the argument rec type constructs")
                        .dom(STR_TID, "the str to convert").rng(REC_TID, "the lhs str as the argument's rec type")
                        .argLst(REC_TYPE, "the rec type whose constructor is applied to the lhs")
                        .body((lhs, inst) -> inst.arg(0).asType().constructor().apply(lhs).tid(inst.arg(0).vidOrTid()))
                        .law(Law.pure)
                        .create(),
                // rec<->lst IS NOT PRESERVING IN EITHER DIRECTION (the round trip re-keys and reorders), and
                // rec->uri is LOSSY TOO (only the component entries a uri can hold survive; any other rec does not
                // read back). Both declare NO inverse. uri->rec, the sound side of the uri pair, keeps its row
                // until its body can leave Uri.UriType (it calls Uri.Helper.parseQ, package-private there).
                InstBuilder.build().tid(AS_INST_TID, "convert the lhs rec into the lst the argument lst type constructs")
                        .dom(REC_TID, "the rec to convert").rng(LST_TID, "the lhs rec as the argument's lst type")
                        .argLst(LST_TYPE, "the lst type whose constructor is applied to the lhs")
                        .body((lhs, inst) -> Poly.Helper.transformRecToLst(lhs.asRec(), inst.arg(0).vidOrTid(), null))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(AS_INST_TID, "convert the lhs rec into the uri its component entries describe")
                        .dom(REC_TID, "the rec to convert").rng(URI_TID, "the lhs rec as a uri")
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
                                furi = furi.path(lhsRec.at(PATH).asLst().elements().map(Obj::uriValue).map(fURI::toString).filter(s -> !s.isEmpty()).collect(Collectors.joining("/")));
                            if (lhsRec.has(COEFF))
                                furi = furi.c(cInt.of(cInt.of(lhsRec.at("c/min").asInt().intValue(), lhsRec.at("c/max").asInt().intValue()).toString()));
                            if (lhsRec.has(QPROC))
                                furi = furi.q(lhsRec.at(QPROC).asRec().elements().map(e -> Tuple.Pair.with(e.first().toCleanString(), e.second().isNothing() ? "" : e.second().toCleanString())).collect(Collectors.toMap(Tuple.Pair::get0, Tuple.Pair::get1)));
                            return uri(furi, inst.arg(0).vidOrTid(), null);
                        })
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(ZERO_INST_TID, "the zero of the rec monoid")
                        .dom(REC_TID, "the lhs rec").rng(REC_TID, "the empty rec").argLst()
                        .body((lhs, inst) -> lhs.asRec().zero())
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
                        .dom(REC_TID, "the rec to merge").rng(REL_TID.maybeSome(), "the rec's entries as rels").argLst()
                        .body((lhs, inst) -> objs(lhs.elements()))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(DOM_INST_TID, "the keys of the lhs rec")
                        .dom(REC_TID, "the lhs rec").rng(A.maybeSome(), "the keys of the rec").argLst()
                        .body((lhs, inst) -> objs(lhs.asRec().elements().map(Rel::first)))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(RNG_INST_TID, "the values of the lhs rec")
                        .dom(REC_TID, "the lhs rec").rng(A.maybeSome(), "the values of the rec").argLst()
                        .body((lhs, inst) -> objs(lhs.asRec().elements().map(Rel::second)))
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(LSHIFT_INST_TID, "the parent of the lhs rec")
                        .dom(REC_TID, "the lhs rec").rng(A.maybeSome(), "the parent obj").argLst()
                        .body((lhs, inst) -> lhs.parent())
                        .law(Law.pure)
                        .create(),
                InstBuilder.build().tid(PLUS_INST_TID, "deep merge the argument rec into the lhs rec")
                        .dom(REC_TID, "the lhs rec").rng(REC_TID, "the deep merge of the two recs")
                        .argLst(T(REC_TID.maybeMaybe()), "the rec to merge into the lhs")
                        .body((lhs, inst) -> lhs.asRec().plus(inst.arg(0).asRec()))
                        .law(Law.monoidic, Law.pure)
                        .create(),
                InstBuilder.build().tid(MINUS_INST_TID, "remove the argument rec's matching entries from the lhs rec")
                        .dom(REC_TID, "the lhs rec").rng(REC_TID, "the lhs rec without the removed entries")
                        .argLst(REC_TYPE, "the rec whose matching entries are removed")
                        .body((lhs, inst) -> {
                            final Map<Obj, Obj> values = lhs.recValue();
                            inst.arg(0).asRec().elements().forEach(rkv -> {
                                final Set<Obj> keys = lhs.asRec().elements().filter(lkv -> lkv.first().test(rkv.first()) && lkv.second().test(rkv.second())).map(Rel::first).collect(Collectors.toSet());
                                keys.forEach(values::remove);
                            });
                            return lhs.jvm(values);
                        })
                        .law(Law.pure)
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
                        .dom(REC_TID.maybeSome(), "zero or more recs").rng(REC_TID, "the deep merge of the stream").argLst()
                        .body((lhs, inst) -> lhs.stream().map(Obj::asRec).reduce(rec(), Rec::plus))
                        .law(Law.monoidic, Law.commutative, Law.pure)
                        .create()
        ));
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
                        .law(Law.involution, Law.pure, Law.unit)
                        .create(),
                InstBuilder.build().tid(PLUS_INST_TID, "branch two codes in parallel, the results as their coefficient sum")
                        .dom(CODE_TID, "the lhs code").rng(CODE_TID, "the branch of the two codes")
                        .argLst(CODE_TYPE, "the code branched alongside the lhs")
                        .body((lhs, inst) -> {
                            if (lhs.equals(inst.arg(0)))
                                return lhs.c(c -> c.plus(inst.arg(0).c()));
                            else {
                                List<Obj> branches = new ArrayList<>();
                                if (lhs.asCode().insts().size() == 1 && lhs.asCode().insts().getFirst().tid().basePath().equals(BRANCH_INST_TID)) {
                                    branches.addAll(lhs.asCode().insts().getFirst().args().elements().toList());
                                }
                                if (inst.arg(0).asCode().insts().size() == 1 && inst.arg(0).asCode().insts().getFirst().tid().basePath().equals(BRANCH_INST_TID)) {
                                    branches.addAll(inst.arg(0).asCode().insts().getFirst().args().elements().toList());
                                }
                                return branch_(branches.toArray(new Obj[0])).as();
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
                        .docDesc("""
                                a profiling::T analysis of prior instruction in the code — 
                                every monad the run produced up to the marker is aggregated into one compressed report (stages, per-instruction times, rewrite rules, flow, cache)""")
                        .examples("*<http://markorodriguez.com>.as(rec::T)>>html/head/title.profile()",
                                "*<http://markorodriguez.com>.as(rec::T)>>html/head/title.profile()>>format")
                        .create()));
    }
}
