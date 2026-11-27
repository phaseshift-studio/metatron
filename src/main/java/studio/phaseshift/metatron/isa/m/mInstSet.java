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

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.algebra.TheoryHelper;
import studio.phaseshift.metatron.algebra.rewrite.RewriterBuilder;
import studio.phaseshift.metatron.algebra.rewrite.plan.Bsp;
import studio.phaseshift.metatron.algebra.rewrite.plan.PlanInterpreter;
import studio.phaseshift.metatron.algebra.rewrite.plan.Shard;
import studio.phaseshift.metatron.algebra.rewrite.plan.Topology;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.Sugar;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.MachinePerspective;
import studio.phaseshift.metatron.isa.m.mach.type.Network;
import studio.phaseshift.metatron.isa.m.mach.type.Processor;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.Analysis;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.Analyzer;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.BasicAnalyzer;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Rewriter;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.BranchInst;
import studio.phaseshift.metatron.isa.m.type.impl.InstBuilder;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.m.type.impl.TypeBuilder;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronUISerializer;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.algebra.Law.LAW_TID;
import static studio.phaseshift.metatron.furi.QProc.QPROC_TID;
import static studio.phaseshift.metatron.furi.QProc.QPROC_TYPE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MILLIS_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.Fail.FAIL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

@InstSet.JREService(vid = "/m")
public class mInstSet extends AbstractInstSet {

    // the TYPE DECLARATIONS live in mTypes (the sibling of mInsts, mirroring catTypes); re-exported by name
    public static final Type BOOL_TYPE = mTypes.BOOL_TYPE;

    public static final Type BYTES_TYPE = mTypes.BYTES_TYPE;

    // ---------- the FOUNDATIONAL ALGEBRA VOCABULARY: the theories a type models ----------
    // These live with the instruction set, not with the category: the category is OPTIONAL (mounted only by
    // importing /m/math/cat), while a type's algebra is a fact about the type. The category renders them
    // (object::T>>theory, morphism::T.law); it does not own them.
    public static final fURI THEORY_TID = M_ISA_TID.extend("theory");
    public static final fURI RING_THEORY_TID = THEORY_TID.extend("ring_theory");
    public static final fURI GROUP_THEORY_TID = THEORY_TID.extend("group_theory");
    public static final fURI MONOID_THEORY_TID = THEORY_TID.extend("monoid_theory");
    public static final fURI COMMUTATIVE_MONOID_THEORY_TID = THEORY_TID.extend("commutative_monoid_theory");
    public static final fURI FIELD_THEORY_TID = THEORY_TID.extend("field_theory");
    public static final fURI RIG_THEORY_TID = THEORY_TID.extend("rig_theory");
    public static final fURI BOOLEAN_THEORY_TID = THEORY_TID.extend("boolean_theory");
    public static final fURI LATTICE_THEORY_TID = THEORY_TID.extend("lattice_theory");
    public static final fURI SEMILATTICE_THEORY_TID = THEORY_TID.extend("semilattice_theory");
    public static final fURI NEAR_RING_THEORY_TID = THEORY_TID.extend("near_ring_theory");
    public static final fURI THEORY_FORM_TID = M_ISA_TID.extend("theory_form");

    public static final Type INT_TYPE = mTypes.INT_TYPE;

    public static final Type REAL_TYPE = mTypes.REAL_TYPE;

    public static final Type STR_TYPE = mTypes.STR_TYPE;

    public static final Type URI_TYPE = mTypes.URI_TYPE;

    public static final Type REL_TYPE = mTypes.REL_TYPE;

    public static final Type LST_TYPE = mTypes.LST_TYPE;

    public static final Type REC_TYPE = mTypes.REC_TYPE;

    /**
     * Cache for fully resolved instructions when ALL arguments are literals (non-call objects).
     * Key: "lhsType|instBasePath|args" - includes lhs type, instruction name, and literal args
     * Value: The fully resolved instruction (safe to reuse since literal args don't depend on lhs)
     * Call args (inst, code, type) are NOT cached because they depend on the lhs value.
     */
    // Map<String, Inst> RESOLUTION_CACHE = new ConcurrentHashMap<>();
    // ThreadLocal<Boolean> REWRITE_MODE = ThreadLocal.withInitial(() -> false);

    // Uri ARGS_URI = uri(ARGS_FURI);
    public static final Type INST_TYPE = mTypes.INST_TYPE;

    public static final Type CODE_TYPE = mTypes.CODE_TYPE;
    public static final Type NOOBJ_TYPE = mTypes.NOOBJ_TYPE;

    public static final fURI CAUSE_INST_TID = M_ISA_INST_TID.extend("cause");
    public static final fURI NATIVE_INST_TID = M_ISA_INST_TID.extend("native");
    public static final fURI SERIALIZE_INST_TID = M_ISA_INST_TID.extend("serialize");
    public static final fURI ID_INST_TID = M_ISA_INST_TID.extend("id");
    public static final fURI DEDUP_INST_TID = M_ISA_INST_TID.extend("dedup");
    public static final fURI EXPLAIN_INST_TID = M_ISA_INST_TID.extend("explain");
    public static final fURI PROFILE_INST_TID = M_ISA_INST_TID.extend("profile");
    public static final fURI HAS_INST_TID = M_ISA_INST_TID.extend("has");
    public static final fURI EVAL_INST_TID = M_ISA_INST_TID.extend("eval");
    public static final fURI PARSE_INST_TID = M_ISA_INST_TID.extend("parse");
    public static final fURI FORK_INST_TID = M_ISA_INST_TID.extend("fork");
    public static final fURI CATCH_INST_TID = M_ISA_INST_TID.extend("catch");
    public static final fURI APPLY_INST_TID = M_ISA_INST_TID.extend("apply");
    public static final fURI START_INST_TID = M_ISA_INST_TID.extend("start");
    public static final fURI COUNT_INST_TID = M_ISA_INST_TID.extend("count");
    public static final fURI SUM_INST_TID = M_ISA_INST_TID.extend("sum");
    public static final fURI CC_INST_TID = M_ISA_INST_TID.extend("cc");
    public static final fURI PROD_INST_TID = M_ISA_INST_TID.extend("prod");
    public static final fURI MEAN_INST_TID = M_ISA_INST_TID.extend("mean");
    public static final fURI SUP_INST_TID = M_ISA_INST_TID.extend("sup");
    public static final fURI INF_INST_TID = M_ISA_INST_TID.extend("inf");
    public static final fURI POW_INST_TID = M_ISA_INST_TID.extend("pow");
    public static final fURI MOD_INST_TID = M_ISA_INST_TID.extend("mod");
    public static final fURI REDUCE_INST_TID = M_ISA_INST_TID.extend("reduce");
    public static final fURI NEG_INST_TID = M_ISA_INST_TID.extend("neg");
    public static final fURI MULT_INST_TID = M_ISA_INST_TID.extend("mult");
    public static final fURI DIV_INST_TID = M_ISA_INST_TID.extend("div");
    public static final fURI INV_INST_TID = M_ISA_INST_TID.extend("inv");
    public static final fURI ZERO_INST_TID = M_ISA_INST_TID.extend("zero");
    public static final fURI ONE_INST_TID = M_ISA_INST_TID.extend("one");
    public static final fURI PLUS_INST_TID = M_ISA_INST_TID.extend("plus");
    public static final fURI MPLUS_INST_TID = M_ISA_INST_TID.extend("mplus");
    public static final fURI MINUS_INST_TID = M_ISA_INST_TID.extend("minus");
    public static final fURI NAME_INST_TID = M_ISA_INST_TID.extend("name");
    public static final fURI MAP_INST_TID = M_ISA_INST_TID.extend("map");
    public static final fURI PARENT_INST_TID = M_ISA_INST_TID.extend("parent");
    public static final fURI FILTER_INST_TID = M_ISA_INST_TID.extend("filter");
    public static final fURI SIDE_INST_TID = M_ISA_INST_TID.extend("side");
    public static final fURI TO_INST_TID = M_ISA_INST_TID.extend("to");
    public static final fURI FROM_INST_TID = M_ISA_INST_TID.extend("from");
    public static final fURI REF_INST_TID = M_ISA_INST_TID.extend("ref");
    public static final fURI BRANCH_INST_TID = M_ISA_INST_TID.extend("branch"); // -<[]>-
    public static final fURI PARTITION_INST_TID = M_ISA_INST_TID.extend("partition");
    public static final fURI SPLIT_INST_TID = M_ISA_INST_TID.extend("split"); // -<
    public static final fURI CHOOSE_INST_TID = M_ISA_INST_TID.extend("choose"); // -<|
    public static final fURI MERGE_INST_TID = M_ISA_INST_TID.extend("merge");
    public static final fURI COMPOSE_INST_TID = M_ISA_INST_TID.extend("compose"); // a·b — serial composition (a then b)
    public static final fURI FILL_TID = M_ISA_INST_TID.extend("fill");
    public static final fURI FIND_TID = M_ISA_INST_TID.extend("find");
    public static final fURI RANGE_INST_TID = M_ISA_INST_TID.extend("range");
    public static final fURI WITHIN_INST_TID = M_ISA_INST_TID.extend("within");
    public static final fURI AUTO_INST_TID = M_ISA_INST_TID.extend("auto");
    public static final fURI AUTO_FROM_INST_TID = M_ISA_INST_TID.extend("auto_from");
    public static final fURI AUTO_TO_INST_TID = M_ISA_INST_TID.extend("auto_to");
    public static final fURI AUTO_AT_INST_TID = M_ISA_INST_TID.extend("auto_at");
    public static final fURI BLOCK_INST_TID = M_ISA_INST_TID.extend("block");
    public static final fURI RNG_INST_TID = M_ISA_INST_TID.extend("rng");
    public static final fURI DOM_INST_TID = M_ISA_INST_TID.extend("dom");
    public static final fURI TID_INST_TID = M_ISA_INST_TID.extend("tid");
    public static final fURI VID_INST_TID = M_ISA_INST_TID.extend("vid");
    public static final fURI TYPE_INST_TID = M_ISA_INST_TID.extend("type");
    public static final fURI GET_INST_TID = M_ISA_INST_TID.extend("get");
    public static final fURI THROW_INST_TID = M_ISA_INST_TID.extend("throw");
    public static final fURI AS_INST_TID = M_ISA_INST_TID.extend("as");
    public static final fURI REVERSE_INST_TID = M_ISA_INST_TID.extend("reverse");
    public static final fURI CLOSE_INST_TID = M_ISA_INST_TID.extend("close");
    public static final fURI REPEAT_INST_TID = M_ISA_INST_TID.extend("repeat");
    public static final fURI LOOP_INST_TID = M_ISA_INST_TID.extend("loop");
    public static final fURI PATH_INST_TID = M_ISA_INST_TID.extend("path");
    public static final fURI AT_INST_TID = M_ISA_INST_TID.extend("at");
    public static final fURI IS_INST_TID = M_ISA_INST_TID.extend("is");
    public static final fURI ISA_INST_TID = M_ISA_INST_TID.extend("isa");
    public static final fURI SORTA_INST_TID = M_ISA_INST_TID.extend("sorta");
    public static final fURI OR_INST_TID = M_ISA_INST_TID.extend("or");
    public static final fURI AND_INST_TID = M_ISA_INST_TID.extend("and");
    public static final fURI EQ_INST_TID = M_ISA_INST_TID.extend("eq");
    public static final fURI NEQ_INST_TID = M_ISA_INST_TID.extend("neq");
    public static final fURI GT_INST_TID = M_ISA_INST_TID.extend("gt");
    public static final fURI ORDER_INST_TID = M_ISA_INST_TID.extend("order");
    public static final fURI KMERGE_INST_TID = M_ISA_INST_TID.extend("kmerge");
    public static final fURI LT_INST_TID = M_ISA_INST_TID.extend("lt");
    public static final fURI GTE_INST_TID = M_ISA_INST_TID.extend("gte");
    public static final fURI ARGS_INST_TID = M_ISA_INST_TID.extend("args");
    public static final fURI LTE_INST_TID = M_ISA_INST_TID.extend("lte");
    public static final fURI NOT_INST_TID = M_ISA_INST_TID.extend("not");
    public static final fURI TAKE_INST_TID = M_ISA_INST_TID.extend("take");
    public static final fURI SKIP_INST_TID = M_ISA_INST_TID.extend("skip");
    public static final fURI BARRIER_INST_TID = M_ISA_INST_TID.extend("barrier");
    public static final fURI REIFY_INST_TID = M_ISA_INST_TID.extend("reify");
    public static final fURI UNION_INST_TID = M_ISA_INST_TID.extend("union");
    public static final fURI SELECT_INST_TID = M_ISA_INST_TID.extend("select");
    public static final fURI ZIP_INST_TID = M_ISA_INST_TID.extend("zip");
    public static final fURI CASCADE_INST_TID = M_ISA_INST_TID.extend("cascade");
    public static final fURI REMOVE_INST_TID = M_ISA_INST_TID.extend("remove");
    public static final fURI UPDATE_INST_TID = M_ISA_INST_TID.extend("update");
    public static final fURI ACTIVE_INST_TID = M_ISA_INST_TID.extend(ACTIVE);
    public static final fURI INACTIVE_INST_TID = M_ISA_INST_TID.extend(INACTIVE);
    public static final fURI GROUP_INST_TID = M_ISA_INST_TID.extend("group");
    public static final fURI ELSE_INST_TID = M_ISA_INST_TID.extend("else");
    public static final fURI END_INST_TID = M_ISA_INST_TID.extend("end");
    public static final fURI IMPORT_INST_TID = M_ISA_INST_TID.extend("import");
    public static final fURI SOURCE_INST_TID = M_ISA_INST_TID.extend("source");
    public static final fURI SWAP_INST_TID = M_ISA_INST_TID.extend("swap");
    public static final fURI PRINT_INST_TID = M_ISA_INST_TID.extend("print");
    public static final fURI PRINTLN_INST_TID = M_ISA_INST_TID.extend("println");
    public static final fURI LSHIFT_INST_TID = M_ISA_INST_TID.extend("lshift");
    public static final fURI RSHIFT_INST_TID = M_ISA_INST_TID.extend("rshift");
    public static final fURI MATH_INST_TID = M_ISA_INST_TID.extend("math");
    public static final fURI LIMIT_INST_TID = M_ISA_INST_TID.extend("limit");
    public static final fURI PATH_TID = M_ISA_INST_TID.extend("path");
    public static final fURI Q_INST_TID = M_ISA_INST_TID.extend("q");
    public static final fURI URI_C_TID = M_ISA_INST_TID.extend("uri:c");
    public static final fURI LCASE_INST_TID = M_ISA_INST_TID.extend("lcase");
    public static final fURI UCASE_INST_TID = M_ISA_INST_TID.extend("ucase");
    public static final fURI SCHEME_INST_TID = M_ISA_INST_TID.extend("scheme");
    public static final fURI AUTHORITY_INST_TID = M_ISA_INST_TID.extend("authority");
    public static final fURI HOST_INST_TID = M_ISA_INST_TID.extend("host");
    public static final fURI PORT_INST_TID = M_ISA_INST_TID.extend("port");
    public static final fURI REWRITER_TYPE_TID = f("/m/sys/rewriter");
    /// ////////////
    /// ////////////
    public static final fURI POLY_TID = M_ISA_TID.extend("poly");
    public static final fURI MONO_TID = M_ISA_TID.extend("mono");
    public static final fURI NUM_TID = M_ISA_TID.extend("num");

    public static final fURI MEM_SPACE_TID = M_ISA_TID.extend("space").extend("memspace");
    public static Type MEM_SPACE_TYPE;
    public static Type REGEX_TYPE;
    public static final fURI REGEX_TID = STR_TID.extend("rx");

    public static final Type ALL_TYPE = TypeBuilder.build().tid(ALL).vid(ALL).create();
    public static final Type ALL_MAYBE_TYPE = TypeBuilder.build().tid(ALL.maybe()).vid(ALL.maybe()).create();
    /**
     * {@code law::T} — the process-law vocabulary, MOUNTED WITH {@code /m}: the law values live at
     * {@code /m/law/<label>}, so the type that admits them belongs to this set's jurisdiction (and is available before
     * the optional category renders it). A UNION OF ITS LABELS (Marko, 2026-11-08):
     * {@code uri::T[union(absorbing, …, cyclic)]@/m/law}, one uri per {@link Law} constant, so the type says what it
     * admits. Users assert these labels and declare no new ones (ruling 2026-10-21), so the union is closed. It is
     * declared here, past {@link #ISA_INST_TID} and {@link #UNION_INST_TID}: {@link mTypes} initializes while this set
     * does and would read those constants at their defaults.
     */
    public static final Type LAW_TYPE = TypeBuilder.build()
            .tid(URI_TID)
            .vid(LAW_TID)
            // the union IS the predicate, a filter: it keeps a label and drops anything else, and a type refuses what its
            // predicate drops. isa(union(…)) would not: isa tests the lhs against the union as a type, and the noobj a
            // misfit yields passes the union's A{?} range
            .predicate(union_(Arrays.stream(Law.values()).map(law -> (Obj) uri(law.name())).toList()).tryToInst())
            .create();
    /**
     * THE ALGEBRAIC THEORIES ARE /m's (Marko, 2026-11-08): the core types declare their algebras over them (int's ring,
     * add_group, …), and every instruction set inherits them for its own types; the category instruction set keeps
     * category theory (adjunctions, pullbacks, products, …). A theory is a named tuple of roles (role => op or element),
     * and {@code theory::T} is their nominal super-type.
     * <p>
     * A THEORY'S LAWS AND RULES ARE ITS OWN DATA (Marko, 2026-11-08). {@code ?decq} holds what is declared ABOUT a type
     * (int's algebras, an instruction's claims), but a theory is itself data about types, so what it uses to do its job
     * sits in its definition beside its roles: the roles are its fields (templates), its {@code rewrite} rules its methods
     * (what to do with those templates), and its {@code law} battery the process laws an instance of it entails. Both
     * are the theory's, never an instance's, so they sit under optional keys no instance carries ({@code MObj} checks a
     * theory instance built at runtime against its theory). The batteries, each defensible on its own terms:
     * <ul>
     *   <li>MONOID — an associative operation with an identity: {@code monoidic} + {@code unit}; its identity drops out,
     *       {@code op(id) ⇝ ε}.</li>
     *   <li>COMMUTATIVE_MONOID — the monoid's laws plus {@code commutative}: lst under concatenation is not one (append is
     *       ordered), rec under deep merge is (merge is keyed).</li>
     *   <li>GROUP — a monoid with inverses: those two + {@code involution} ({@code a·a⁻¹ = id} for the operation,
     *       {@code inv(inv(a)) = a} for the inverse).</li>
     *   <li>RING — an abelian additive group and a multiplicative monoid distributing over it. The battery is FLAT — a law
     *       is not attributed to a role — so a type-level analysis answers {@code commutative}, the +'s law,
     *       uncheckable on the operation a ring binds as its mul (rec's walk join), while an operation another instance
     *       binds as its op (code's compose, under mul_monoid) is held to all of it.</li>
     *   <li>FIELD — a commutative ring whose non-zero elements are a multiplicative group: the ring's laws, the extra
     *       structure being the {@code inv} role.</li>
     *   <li>RIG — monoids with distributivity and NO additive inverse: no {@code involution}, and no {@code idempotent}
     *       (nat is a rig and is not idempotent).</li>
     *   <li>BOOLEAN — a complemented distributive lattice: commutative idempotent monoids, distributivity,
     *       {@code involution} for the complement and {@code complement} for {@code b∧¬b = ⊥}, {@code b∨¬b = ⊤}.</li>
     *   <li>LATTICE / SEMILATTICE — join and meet are each an idempotent commutative monoid.</li>
     *   <li>NEAR_RING — one-sided distributivity only; its signature declares no identity and no inverse, and
     *       {@code semi_right_distributive} is an instruction claim about reducers (Thm 19), not an axiom (Thm 18).</li>
     * </ul>
     */
    public static final Type THEORY_TYPE = TypeBuilder.build()
            .tid(REC_TID)
            .vid(THEORY_TID, "the nominal super-type of the algebraic theories: a theory names its operations by role, e.g. \\(\\mathrm{ring} \\mapsto \\{\\mathrm{add}, \\mathrm{mul}, \\mathrm{zero}, \\mathrm{one}\\}\\)")
            .isaPredicate(rec(uri(LAW).maybe().asUri(), T(LST_TID.poly(LAW_TID.b(cInt.C_SOME)))))
            .create();
    // A THEORY'S LAW BATTERY AND ITS REWRITE RULES ARE THE THEORY'S OWN DATA: an instance rec carries the theory's
    // roles (e.g. add/mul/zero/one) and never its `law` or `rewrite` slots, so those keys are OPTIONAL in the shape an
    // instance is held to. The battery is read from the type (TheoryHelper.stated), never matched against the value.
    public static final Type RING_THEORY_TYPE = TypeBuilder.build()
            .tid(THEORY_TID)
            .vid(RING_THEORY_TID, "the theory of rings \\(\\langle R, +, \\cdot, 0, 1 \\rangle\\): \\(+\\) an abelian group, \\(\\cdot\\) a monoid, distributive on both sides: \\(a \\cdot (b + c) = a \\cdot b + a \\cdot c\\) and \\((a + b) \\cdot c = a \\cdot c + b \\cdot c\\)")
            .isaPredicate(rec(
                    uri(ADD), INST_TYPE,
                    uri(MUL), INST_TYPE,
                    uri(ZERO), ALL_TYPE,
                    uri(ONE), ALL_TYPE,
                    uri(LAW).maybe(), laws(Law.monoidic, Law.unit, Law.commutative, Law.involution, Law.left_distributive, Law.right_distributive)))
            .create();
    public static final Type GROUP_THEORY_TYPE = TypeBuilder.build()
            .tid(THEORY_TID)
            .vid(GROUP_THEORY_TID, "the theory of groups \\(\\langle G, \\cdot, 1, {}^{-1} \\rangle\\): \\(g \\cdot g^{-1} = g^{-1} \\cdot g = 1\\)")
            .isaPredicate(rec(
                    uri(OP), INST_TYPE,
                    uri(ID), ALL_TYPE,
                    uri(INV), INST_TYPE,
                    uri(LAW).maybe(), laws(Law.monoidic, Law.unit, Law.involution),
                    // an element and its inverse cancel on either side, a·a⁻¹ = 1 and a⁻¹·a = 1, in the theory's own
                    // role names over a named operand: op(inv(a)) binds the instruction DEFINED that way (minus ≡
                    // plus∘neg, div ≡ mult∘inv), so int's add_group gives plus(a)·minus(a) and minus(a)·plus(a)
                    uri(REWRITE).maybe(), lst(
                            rec(MCode.code(List.of(instB(f(OP), lst(T(ALL, f("a"), null, null))),
                                            instB(f(OP), lst(instB(f(INV), lst(T(ALL, f("a"), null, null))))))),
                                    MCode.code(List.<Inst>of())),
                            rec(MCode.code(List.of(instB(f(OP), lst(instB(f(INV), lst(T(ALL, f("a"), null, null))))),
                                            instB(f(OP), lst(T(ALL, f("a"), null, null))))),
                                    MCode.code(List.<Inst>of())))))
            .create();
    public static final Type MONOID_THEORY_TYPE = TypeBuilder.build()
            .tid(THEORY_TID)
            .vid(MONOID_THEORY_TID, "the theory of monoids \\(\\langle M, \\cdot, 1 \\rangle\\): \\(m \\cdot 1 = 1 \\cdot m = m\\) and \\((m \\cdot n) \\cdot p = m \\cdot (n \\cdot p)\\)")
            .isaPredicate(rec(
                    uri(OP), INST_TYPE,
                    uri(ID), ALL_TYPE,
                    uri(LAW).maybe(), laws(Law.monoidic, Law.unit),
                    // the monoid's identity drops out, op(id) ⇝ ε, in the theory's own role names: every monoid
                    // instance binds op and id (Law.Helper.bind)
                    uri(REWRITE).maybe(), lst(rec(MCode.code(List.of(instB(f(OP), lst(uri(ID))))), MCode.code(List.<Inst>of())))))
            .create();
    public static final Type COMMUTATIVE_MONOID_THEORY_TYPE = TypeBuilder.build()
            .tid(THEORY_TID)
            .vid(COMMUTATIVE_MONOID_THEORY_TID, "the theory of commutative monoids \\(\\langle M, \\cdot, 1 \\rangle\\): a monoid whose operation commutes, \\(m \\cdot n = n \\cdot m\\)")
            .isaPredicate(rec(
                    uri(OP), INST_TYPE,
                    uri(ID), ALL_TYPE,
                    uri(LAW).maybe(), laws(Law.monoidic, Law.unit, Law.commutative)))
            .create();
    public static final Type FIELD_THEORY_TYPE = TypeBuilder.build()
            .tid(THEORY_TID)
            .vid(FIELD_THEORY_TID, "the theory of fields \\(\\langle F, +, \\cdot, 0, 1, {}^{-1} \\rangle\\): a commutative ring where every non-\\(0\\) element has a multiplicative inverse, \\(x \\cdot x^{-1} = 1\\)")
            .isaPredicate(rec(
                    uri(ADD), INST_TYPE,
                    uri(MUL), INST_TYPE,
                    uri(ZERO), ALL_TYPE,
                    uri(ONE), ALL_TYPE,
                    uri(INV), INST_TYPE,
                    uri(LAW).maybe(), laws(Law.monoidic, Law.unit, Law.commutative, Law.involution, Law.left_distributive, Law.right_distributive)))
            .create();
    public static final Type RIG_THEORY_TYPE = TypeBuilder.build()
            .tid(THEORY_TID)
            .vid(RIG_THEORY_TID, "the theory of rigs (semirings) \\(\\langle S, +, \\cdot, 0, 1 \\rangle\\): an additive and a multiplicative monoid with \\(\\cdot\\) distributive over \\(+\\), and no additive inverses")
            .isaPredicate(rec(
                    uri(ADD), INST_TYPE,
                    uri(MUL), INST_TYPE,
                    uri(ZERO), ALL_TYPE,
                    uri(ONE), ALL_TYPE,
                    uri(LAW).maybe(), laws(Law.monoidic, Law.unit, Law.commutative, Law.left_distributive, Law.right_distributive)))
            .create();
    public static final Type BOOLEAN_THEORY_TYPE = TypeBuilder.build()
            .tid(THEORY_TID)
            .vid(BOOLEAN_THEORY_TID, "the theory of Boolean algebras \\(\\langle B, \\lor, \\land, \\lnot, 0, 1 \\rangle\\): a complemented distributive lattice, \\(b \\lor \\lnot b = 1\\) and \\(b \\land \\lnot b = 0\\)")
            .isaPredicate(rec(
                    uri(OR), INST_TYPE,
                    uri(AND), INST_TYPE,
                    uri(NOT), INST_TYPE,
                    uri(ZERO), ALL_TYPE,
                    uri(ONE), ALL_TYPE,
                    uri(LAW).maybe(), laws(Law.monoidic, Law.unit, Law.commutative, Law.idempotent, Law.involution, Law.complement, Law.left_distributive, Law.right_distributive)))
            .create();
    public static final Type LATTICE_THEORY_TYPE = TypeBuilder.build()
            .tid(THEORY_TID)
            .vid(LATTICE_THEORY_TID, "the theory of lattices \\(\\langle L, \\sqcap, \\sqcup, \\bot, \\top \\rangle\\): every pair of elements has a meet and a join, bounded by \\(\\bot\\) and \\(\\top\\)")
            .isaPredicate(rec(
                    uri(MEET), INST_TYPE,
                    uri(JOIN), INST_TYPE,
                    uri(BOTTOM), ALL_TYPE,
                    uri(TOP), ALL_TYPE,
                    uri(LAW).maybe(), laws(Law.monoidic, Law.unit, Law.commutative, Law.idempotent)))
            .create();
    public static final Type SEMILATTICE_THEORY_TYPE = TypeBuilder.build()
            .tid(THEORY_TID)
            .vid(SEMILATTICE_THEORY_TID, "the theory of semilattices \\(\\langle S, \\sqcup, 0 \\rangle\\): an idempotent commutative monoid (a single meet or join side of a lattice)")
            .isaPredicate(rec(
                    uri(OP), INST_TYPE,
                    uri(ID), ALL_TYPE,
                    uri(LAW).maybe(), laws(Law.monoidic, Law.unit, Law.commutative, Law.idempotent)))
            .create();
    public static final Type NEAR_RING_THEORY_TYPE = TypeBuilder.build()
            .tid(THEORY_TID)
            .vid(NEAR_RING_THEORY_TID, "the theory of near-rings \\(\\langle N, +, \\cdot, 0 \\rangle\\): \\(+\\) a group, \\(\\cdot\\) a semigroup, one-sided distributivity, no multiplicative \\(1\\) (reserved for barrier types)")
            .isaPredicate(rec(
                    uri(ADD), INST_TYPE,
                    uri(MUL), INST_TYPE,
                    uri(ZERO), ALL_TYPE,
                    uri(LAW).maybe(), laws(Law.monoidic, Law.left_distributive)))
            .create();

    /*
     * /m's REWRITES, ONE PER RULE (Marko, 2026-11-10) — a rewrite is a simple, semantically specific alteration, turned
     * on and off by adding it to or removing it from the machine's rewrite pool, so the pool is the table: each rule is
     * its own rewrite, and path::T draws one edge per rule. A rule rewrite's declared ARGUMENT is its rules alone
     * (Marko, 2026-11-13: no key): a value, not a type, so it resolves with its rules already bound and no type
     * resolution takes place (InstSet.Helper.rewriter). Its BODY is where they hold, the gate it hands Law.Helper.rewrite
     * with them, written beside the rule rather than picked by a key:
     * - a LAW's rule is stated in the name op, the instruction whose claim states the law, and the law's claimants bind
     *   op (Law.Helper.claimed); the gate reads the law at the chain's carrier;
     * - a FORM's rule is stated over a named hole, and the gate asks that the operation of what the hole captured declares
     *   the form;
     * - a PLAIN rule applies as it stands, and a ${...} template in its change computes an argument from the names the run
     *   bound.
     */

    /**
     * {@code map_nest} — a PLAIN rule: a map of a map is the map, whatever it maps. map hands each obj to its argument one
     * at a time, so the outer map adds nothing: {@code map(map(+2)) ⇝ map(+2)}, and {@code form_map_unwrap} unwraps the
     * rest. A pattern's map states map's own endpoints, maybe in ({@code #{?}}) and any number out ({@code #{*}}, Marko,
     * 2026-11-17: {@code map?B{*}<=A{?}}), so a RESOLVED map ({@code map?/m/int{*}<=...}) tests it too: an endpoint a
     * pattern leaves out reads as {@code #}, which an {@code int{*}} is not, and a pattern stating the old {@code #{?}}
     * out held no map at all once map's range was {@code {*}}. It is its own rule, not the mapper's: map's form is
     * {@code standard}, its {@code {?} ⇒ {*}} shape no mapper's, since what it hands on is what its argument does
     * ({@code map(is(gt(1)))} filters).
     */
    public static final Rec MAP_NEST = rec(MCode.code(List.of(instB(MAP_INST_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), lst(instB(MAP_INST_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), lst(T(ALL, f("f"), null, null))))))),
            MCode.code(List.of(instB(MAP_INST_TID, lst(T(ALL, f("f"), null, null))))));

    /**
     * {@code form_map_unwrap} — a FORM rule (Marko, 2026-11-09: mapper only): a mapping of a mapper is the mapper. Its hole
     * ranges over the form, so {@code map(f)} unwraps where the operation of f, an instruction or the op symbol a chain
     * carries before resolution ({@code map(neg)}), declares the mapper form. {@code map(neg) ⇝ neg()}, while
     * {@code map(sum())} stays.
     * FOR ANY FUNCTION, not just mapper forms, it is the same rule applied as it stands, with no gate, or in Java
     * {@code rec(MCode.code(List.of(instB(MAP_INST_TID, lst(T(ALL, f("f"), null, null))))), T(ALL, f("f"), null, null))}.
     * That is the Java fold the mapper rule replaced, and it is unsound for a function that consumes the stream:
     * {@code {1,2,3}.map(sum())} sums each element alone, {@code {1,2,3}}, while {@code {1,2,3}.sum()} is 6.
     */
    public static final Rec FORM_MAP_UNWRAP = rec(MCode.code(List.of(instB(MAP_INST_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), lst(T(ALL, f("f"), null, null))))),
            T(ALL, f("f"), null, null));

    /**
     * {@code law_involution} — an involution pair drops out, {@code op·op ⇝ ε}, where the operation is an involution at the
     * chain's carrier, or the carrier's additive-group inverse.
     */
    public static final Rec LAW_INVOLUTION = rec(MCode.code(List.of(instB(f(OP), lst()), instB(f(OP), lst()))), MCode.code(List.<Inst>of()));

    /**
     * {@code law_idempotent} — an idempotent pair is one, {@code op·op ⇝ op}, where the operation is declared idempotent at
     * the chain's carrier. The claimant's term pins the pair's operands equal.
     */
    public static final Rec LAW_IDEMPOTENT = rec(MCode.code(List.of(instB(f(OP), lst()), instB(f(OP), lst()))), MCode.code(List.of(instB(f(OP), lst()))));

    /**
     * {@code law_monoidic} — a fold of a fold is the fold, {@code op·op ⇝ op}, for a reducer or join declared monoidic at
     * the chain's carrier. Its rule is idempotent's, {@code op·op ⇝ op}: what tells the two apart is the law whose claimants
     * bind {@code op} and the gate that reads it.
     */
    public static final Rec LAW_MONOIDIC = rec(MCode.code(List.of(instB(f(OP), lst()), instB(f(OP), lst()))), MCode.code(List.of(instB(f(OP), lst()))));

    /**
     * {@code law_poset} — two filters on one comparison are the filter on the stricter bound,
     * {@code x > a ∧ x > b ⇔ x > max(a, b)}. The comparison decides which bound that is, not its name, so the gate
     * evaluates it (law_poset's gate): a rule holds where the bound it keeps, b, is ordered past a or equals it.
     * The pair is stated both ways round, so the bound kept is the second (b second) or the first (b first). Only filters
     * compose: a bare comparison answers a bool, and {@code x.gt(a).gt(b)} compares that bool with b.
     */
    public static final Rec LAW_POSET = rec(
            MCode.code(List.of(instB(IS_INST_TID, lst(instB(f(OP), lst(T(ALL, f("a"), null, null))))), instB(IS_INST_TID, lst(instB(f(OP), lst(T(ALL, f("b"), null, null))))))),
            MCode.code(List.of(instB(IS_INST_TID, lst(instB(f(OP), lst(T(ALL, f("b"), null, null))))))),
            MCode.code(List.of(instB(IS_INST_TID, lst(instB(f(OP), lst(T(ALL, f("b"), null, null))))), instB(IS_INST_TID, lst(instB(f(OP), lst(T(ALL, f("a"), null, null))))))),
            MCode.code(List.of(instB(IS_INST_TID, lst(instB(f(OP), lst(T(ALL, f("b"), null, null))))))),
            MCode.code(List.of(instB(FILTER_INST_TID, lst(instB(f(OP), lst(T(ALL, f("a"), null, null))))), instB(FILTER_INST_TID, lst(instB(f(OP), lst(T(ALL, f("b"), null, null))))))),
            MCode.code(List.of(instB(FILTER_INST_TID, lst(instB(f(OP), lst(T(ALL, f("b"), null, null))))))),
            MCode.code(List.of(instB(FILTER_INST_TID, lst(instB(f(OP), lst(T(ALL, f("b"), null, null))))), instB(FILTER_INST_TID, lst(instB(f(OP), lst(T(ALL, f("a"), null, null))))))),
            MCode.code(List.of(instB(FILTER_INST_TID, lst(instB(f(OP), lst(T(ALL, f("b"), null, null))))))));

    /**
     * {@code law_absorbing} — an absorbing op is a constant of the chain, so a run before it that cannot change what it
     * answers is dead code, {@code …·g·zero ⇝ …·zero}. The run is a SPANNED element (Marko, 2026-11-09:
     * {@code {span}inst{point}}), {@code {+}#{*}<=#{*}({*}#::T)}: one or more instructions of any name, endpoints and
     * arguments, laid over the chain as a lst type's entries are laid over its indices. The longest run is tried first,
     * and one the gate refuses (the seed, an effectful instruction, a filter before a strict op) gives way to a shorter
     * one (law_absorbing's gate).
     */
    public static final Rec LAW_ABSORBING = rec(MCode.code(List.of(instB(ALL.b(cInt.SOME()).dom(ALL.maybeSome()).rng(ALL.maybeSome()), lst(T(ALL.b(cInt.MAYBESOME()), null, null, null))), instB(f(OP), lst()))),
            MCode.code(List.of(instB(f(OP), lst()))));

    /**
     * {@code else_after_count} — a PLAIN rule: no claimant to bind and no gate. count is total, so an else after it is dead
     * code.
     */
    public static final Rec ELSE_AFTER_COUNT = rec(MCode.code(List.of(instB(COUNT_INST_TID, lst()), instB(ELSE_INST_TID, lst(T(ALL))))),
            MCode.code(List.of(instB(COUNT_INST_TID, lst()))));

    /**
     * {@code range_skip_take} — a PLAIN rule: a window is a skip and a take, {@code range(i, n) ⇝ skip(i)·take(n)}, its
     * offset skip's count and its length take's (Marko, 2026-11-26). The change read n as an end ({@code take(n - i)}) where
     * range's body reads a length, so a literal range and a computed one answered different windows. Only literal bounds
     * match ({@code int::T} refuses an instruction), so a computed range stays range, and its body answers the same.
     */
    public static final Rec RANGE_SKIP_TAKE = rec(MCode.code(List.of(instB(RANGE_INST_TID, lst(T(INT_TID, f("i"), null, null), T(INT_TID, f("n"), null, null))))),
            MCode.code(List.of(instB(SKIP_INST_TID, lst(T(ALL, f("i"), null, null))), instB(TAKE_INST_TID, lst(T(ALL, f("n"), null, null))))));

    /**
     * A theory's law battery as its type states it: the labels, a lst of {@code law::T}.
     */
    private static Lst laws(final Law... laws) {
        return lst(Arrays.stream(laws).map(law -> (Obj) uri(law.name())).toList());
    }

    /**
     * {@code theory_form::T} — THE KEYS OF A TYPE'S THEORY BLOCK (Marko, 2026-11-08). A theory with several op roles is
     * keyed by its own name ({@code ring}); a theory with one {@code op} role by the instruction family that op binds,
     * {@code add} for {@code plus} and {@code mul} for {@code mult} ({@code add_group}, {@code mul_monoid}). The key is
     * the only thing that tells two instances of one theory apart (int's {@code add_monoid} and {@code mul_monoid}), so
     * it is stated in the theory's vocabulary, never in what an instruction does on a carrier. {@code free_groupoid} is
     * the machine's frame groupoid, until the category instruction set has a groupoid theory. The union IS the
     * predicate, as it is {@code law::T}'s, and a {@code ?decq} write keyed by anything else is refused.
     */
    public static final Type THEORY_FORM_TYPE = TypeBuilder.build()
            .tid(URI_TID)
            .vid(THEORY_FORM_TID)
            .predicate(union_(Stream.of("ring", "field", "rig", "boolean", "lattice", "near_ring",
                            "add_group", "mul_group", "add_monoid", "mul_monoid", "add_commutative_monoid",
                            "mul_commutative_monoid", "add_semilattice", "mul_semilattice", "free_groupoid")
                    .map(form -> (Obj) uri(form)).toList()).tryToInst())
            .create();
    public static final Type SPACE_TYPE = TypeBuilder.build()
            .tid(REC_TID)
            .vid(SPACE_TID)
            .isaPredicate(rec(
                    uri(PATTERN), URI_TYPE,
                    uri(QPROC).maybe(), lst(QPROC_TYPE.maybe().asType()),
                    uri(ROUTE).maybe(), rec(T(URI_TID.maybe()), URI_TYPE),
                    uri(SCHEMA).maybe(), T(INSTSET_TID), // nominal-only: avoids structural recursion in InstSet
                    uri(ON_CLOSE).maybe(), ALL_TYPE))
            .slots(Map.of(
                    uri(PATTERN), "the uri address region the space will manage",
                    uri(QPROC).maybe(), "query processors (qproc) that augment space capabilities",
                    uri(ROUTE).maybe(), "a secondary router after global router for inter and intra-space redirects",
                    uri(SCHEME).maybe(), "an instset defining the type structure of the space objs",
                    uri(ON_CLOSE).maybe(), "an optional shutdown down hook"))
            .desc("storage systems structured as uri addressed objs")
            .example("memspace::[pattern=>/usr/marko/#,q=>[mimeq::[=>]]]@~/memory/space/usr/marko")
            .create();

    public static final fURI EXPLANATION_TID = EXPLAIN_INST_TID.extend("explanation");
    public static final Type EXPLANATION_TYPE = TypeBuilder.build()
            .tid(REC_TID)
            .vid(EXPLANATION_TID)
            .isaPredicate(rec(
                    uri(FORMAT), STR_TYPE,
                    // dom/rng/rewrite/f are optional keys: the rec builder drops a noobj field (and cleanMap
                    // drops the whole key with it), and every one of these is legitimately noobj — `dom` for any
                    // expression starting at a literal, `rewrite` when the submitted code is the last stage, `f`
                    // for an inst with no function. a mandatory key made explain() fail its own type_pred check.
                    uri(DESC), rec(
                            uri(DOM).maybe().asUri(), URI_TYPE,
                            uri(RNG).maybe().asUri(), URI_TYPE,
                            uri(INSTS), INT_TYPE,
                            uri(REWRITE).maybe().asUri(), T(EXPLANATION_TID.maybe())),
                    uri(PER_INST), lst(rec(
                            uri(OP), URI_TYPE,
                            uri(DOM).maybe().asUri(), URI_TYPE,
                            uri(RNG).maybe().asUri(), URI_TYPE,
                            uri(ARGS), union_(LST_TYPE, REC_TYPE).tryToInst(),
                            uri(Tokens.F).maybe().asUri(), ALL_TYPE,
                            uri(FORM), URI_TYPE,
                            // omitted when the coefficient is unbounded below (cc's A.any()): such a range has no least count
                            uri(C_DOM).maybe().asUri(), INT_TYPE,
                            uri(C_RNG).maybe().asUri(), INT_TYPE)),
                    uri(EXT).maybe().asUri(), REC_TYPE), Map.of(
                    uri(FORMAT), "lazily constructed pretty-print format (the current text table) — a lazy inst, materialized interactively",
                    uri(DESC), "expression-level metadata of the COMPILED chain: whole-expression dom/rng, inst count, and the rewrite chain — desc.rewrite is the SUBMITTED expression's explanation ({?}), which terminates the chain (noobj)",
                    uri(PER_INST), "lst of per-instruction stage recs (op/dom/rng/args/f/form/c_dom/c_rng) — args are the real objs, not their string renderings; f is the wrapped function ('<j>' = java/opaque)",
                    uri(EXT), "the explain extensions (Marko, 2026-11-20): rewrites beside explain_analysis, each writing its rec at ext/<name> (ext/topology) and appending that rec's format to this one's"))
            .desc("a structured explanation of a resolved expression produced by explain()")
            .example("1.plus(2).explain()>>desc>>rng     [-- int — the type flowing out of the expression --]")
            .example("1.plus(2).explain()>>per_inst      [-- per-inst recs: [op=>start,args=>[1],form=>initial,c_dom=>0,c_rng=>1], [op=>plus,dom=>int,rng=>int,args=>[2],f=>'<j>',form=>mapper,c_dom=>1,c_rng=>1] --]")
            .example("1.plus(2).explain()>>format         [-- the str::T text table --]")
            .example("6.plus(0).explain()>>desc>>insts    [-- 1 — the reported chain is the COMPILED one, so the unit law already dropped plus(0) --]")
            .example("6.plus(0).explain()>>desc>>rewrite  [-- the SUBMITTED rec: >>insts is 2 (start, plus), the expression as written --]")
            .create();
    public static final fURI PROFILING_TID = PROFILE_INST_TID.extend("profiling");

    /**
     * THE ADDRESSABLE FORM OF A URI — {@code fURI#pathEncoding} stated as a type, so a machine's origin can be
     * a rec KEY: {@code ws/localhost/1112/mtron} is one addressable segment, where {@code ws://localhost:1112/mtron}
     * is not a key at all. This is what makes a collated profile an INDEX (a rec keyed by where each report was
     * made) rather than a list, and therefore walkable and nestable: a star's flat fan-out, a tree's hops and a
     * divide-and-conquer plan all describe themselves the same way, each level holding the reports below it.
     */
    public static final fURI PATH_ENCODING_TID = URI_TID.extend("path_encoding");
    public static final Type PROFILING_TYPE = TypeBuilder.build()
            .tid(REC_TID)
            .vid(PROFILING_TID)
            .isaPredicate(rec(
                    uri(FORMAT), STR_TYPE,
                    uri(STAGE), rec(
                            uri(REWRITE), rec(uri(MIN), MILLIS_TYPE, uri(MAX), MILLIS_TYPE),
                            uri(RESOLVE), rec(uri(MIN), MILLIS_TYPE, uri(MAX), MILLIS_TYPE),
                            uri(APPLY), rec(uri(MIN), MILLIS_TYPE, uri(MAX), MILLIS_TYPE)),
                    uri(INSTS), INT_TYPE,
                    uri(RESOLVE), rec(
                            uri(INST_RESOLVE), MILLIS_TYPE,
                            uri(GENERIC_BINDING), MILLIS_TYPE,
                            uri(INST_COMPOSITION), MILLIS_TYPE),
                    uri(REWRITE), rec(
                            uri(RULES), lst(rec(uri(NAME), URI_TYPE, uri(IN), INT_TYPE, uri(OUT), INT_TYPE, uri(TIME), MILLIS_TYPE)),
                            uri(TOTAL), rec(uri(IN), INT_TYPE, uri(OUT), INT_TYPE, uri(TIME), MILLIS_TYPE)),
                    uri(APPLY), rec(
                            uri(SPLIT), MILLIS_TYPE,
                            uri(APPLY), MILLIS_TYPE,
                            uri(NEXT), MILLIS_TYPE),
                    uri(PER_INST), lst(rec(
                            uri(NAME), URI_TYPE,
                            uri(MONAD_IN), INT_TYPE, uri(C_IN), INT_TYPE,
                            uri(MONAD_OUT), INT_TYPE, uri(C_OUT), INT_TYPE,
                            uri(TIME), MILLIS_TYPE)),
                    uri(FLOW), rec(
                            uri(MONADS), INT_TYPE, uri(C_SUM), INT_TYPE,
                            uri(COMPRESSION), REAL_TYPE, uri(PROCESSORS), INT_TYPE),
                    uri(CACHE), rec(
                            uri(HITS), INT_TYPE, uri(MISSES), INT_TYPE, uri(HIT_RATE), REAL_TYPE),
                    // RECURSIVE -- A COLLATED REPORT IS AN INDEX OF REPORTS: <path-encoded machine uri> =>
                    // profiling::T. T(PROFILING_TID.maybe()) is a FORWARD REFERENCE to this very type, which is
                    // why the entry is written T(PROFILING_TID) and never PROFILING_TYPE: the Java field does
                    // not exist yet while this predicate is being built, and the tid resolves through the type
                    // graph at test time. The key is a PATH-ENCODED uri so a shard's origin is ONE addressable
                    // segment (ws/localhost/1112/mtron, never ws://localhost:1112/mtron) and the rec is
                    // walkable. This is what lets one profile describe ANY topology -- a star's flat fan-out, a
                    // tree's nested hops, a divide-and-conquer plan -- because each level simply holds the
                    // reports of the places below it.
                    // NOTE: keyed with the base uri::T for now; a dedicated path-encoded refinement does not
                    // exist yet, so the encoding is a writer convention until the type states it.
                    // THE ENTRIES A REPORT ADDS (machine, and a collated report's place, depth, leaf, level and path;
                    // profileCollate) are typed by this entry alone and documented below. Slots of their own failed
                    // every report, a local profile() included (measured 2026-11-19): a pattern's key is required
                    // unless it is optional (Rec#test), and a lane's report holds none of a collated report's entries.
                    T(PATH_ENCODING_TID.maybe()), T(PROFILING_TID.maybe())), Map.ofEntries(
                    Map.entry(uri(FORMAT), "lazily constructed pretty print format"),
                    Map.entry(uri(STAGE), "per-pipeline-stage min/max wall-clock timings (rewrite/resolve/apply)"),
                    Map.entry(uri(INSTS), "the resolved instruction count"),
                    Map.entry(uri(RESOLVE), "resolve sub-stages (inst-resolve/generic-binding/inst-composition)"),
                    Map.entry(uri(REWRITE), "per-rewrite-rule instruction reduction (rules + net total)"),
                    Map.entry(uri(APPLY), "processor sub-stages (split/apply/next)"),
                    Map.entry(uri(PER_INST), "per-instruction monad/coefficient flow + time"),
                    Map.entry(uri(FLOW), "bulk-compression flow (monads propagated vs coefficient sum)"),
                    Map.entry(uri(CACHE), "type-graph cache hit/miss/hit-rate"),
                    Map.entry(uri(MACHINE), "the machine the report was made on: its host, its vid and its process id"),
                    Map.entry(uri(PLACE), "a distributed profile's places: each lane's report, keyed by the place it was dealt to, named as the topology names it (//0, else //host:port, //local at home)"),
                    Map.entry(uri(DEPTH), "the levels of places below this report (a star is 1)"),
                    Map.entry(uri(LEAF), "the leaves below this report: how many (count), the objs their runs carried (c_sum) and their summed apply (time)"),
                    Map.entry(uri(LEVEL), "per level below this report, the places' apply: count, min, max and total"),
                    Map.entry(uri(PATH), "the critical path: the slowest place at each level, from here down to a leaf")))
            .desc("a structured profile report produced by profile(); a distributed profile nests its places' reports")
            .example("{1,2,3}.sum().profile()>>format    [-- the str::T text table --]")
            .example("{1,2,3}.sum().profile()>>flow      [-- [monads=>…,coeff_sum=>…,compression=>…,processors=>…] --]")
            .create();
    
   /* public static final Type MONO_TYPE = TypeBuilder.build()
            .tid(MONO_TID)
            .vid(MONO_TID)
            .predicate((lhs, inst) -> bool(lhs.isBytes() || lhs.isBool() || lhs.isInt() || lhs.isReal() || lhs.isStr() || lhs.isUri() || lhs.isObjInst()))
            .create();*/

   /* public static final Type NUM_TYPE = TypeBuilder.build()
            .tid(NUM_TID)
            .vid(NUM_TID)
            .predicate((lhs, inst) -> bool(lhs.isInt() || lhs.isReal()))
            .create();*/

    /* public static final Type POLY_TYPE = TypeBuilder.build()
             .tid(POLY_TID)
             .vid(POLY_TID)
             .predicate((lhs, inst) -> bool(lhs.isLst() || lhs.isRec() || lhs.isRel() || lhs.isCode()))
             .create();*/
    public static final Uri NONE = uri(f("none"), URI_TID, null);


    public mInstSet() {
        super(new LinkedHashMap<>(Map.<Obj, Obj>of(uri(PATTERN), uri(M_ISA_TID.extend(ALL)), uri(QPROC), lst())), INSTSET_TID, M_ISA_TID);
    }

    // resolved rewrite match patterns, memoized on first use so the per-application
    // is_(eq_(zero_())) / is_(eq_(one_())) construction (each inst build runs
    // objCheckAndSave → objTypeCheck → a full type test) happens once, not on every rewrite pass.
    //private static final AtomicReference<List<Inst>> PLUS_ZERO_MATCH = new AtomicReference<>();
    //private static final AtomicReference<List<Inst>> MULT_ONE_MATCH = new AtomicReference<>();

    /*private static List<Inst> cachedMatch(final AtomicReference<List<Inst>> ref, final java.util.function.Supplier<List<Inst>> build) {
        final List<Inst> existing = ref.get();
        if (null != existing)
            return existing;
        ref.compareAndSet(null, build.get());
        return ref.get();
    }*/

    public void setup() {
        // RE-STAGED EVERY BOOT, not once per JVM. The algebra recs point at LIVE operation insts (`!*plus`, `!*mult`,
        // their roles), and the retained log would otherwise hand a later boot the objects of an earlier machine --
        // the role pointers then name dead spaces and an algebra reads as "no role-bearing operation".
        mTypes.declareAlgebras();
        this.selfTID(INSTSET_TID);
        this.jvm().putAll(new LinkedHashMap<>(Map.of(
                uri(PATTERN), uri(M_ISA_TID.extend(ALL)),
                uri(TYPE), lst(
                        //  docWrap(MONO_TYPE, "an atomic obj"),
                        //  docWrap(POLY_TYPE, "a obj composed of other objs"),
                        //  docWrap(NOOBJ_TYPE, "a no object"),
                        docWrap(ALL_TYPE, "universal type matching all objs within coefficient range",
                                "abc.isa(#::T)                  [-- abc --]",
                                "12.3.isa(#::T)                 [-- 12.3 --]",
                                "{2}'c string'.isa(#{+}::T)     [-- {2}'c string' --]"),
                        docWrap(BOOL_TYPE, "a 2 valued mono: true or false",
                                "true",
                                "false",
                                "true.and(_,not(_)) [-- false --]",
                                "true.or(_,not(_))  [-- true -- ]"),
                        docWrap(INT_TYPE, "a 64-bit signed integer",
                                "123",
                                "-234",
                                "123+(-234) [-- -111 --]"),
                        docWrap(REAL_TYPE, "a 64-bit floating point number",
                                "123.45",
                                "-234.56",
                                "123.45+(-234.56) [-- -111.11   --]",
                                "12.34E-5         [-- 0.0001234 --]"),
                        docWrap(BYTES_TYPE, "a sequence of 8-bit unsigned integers",
                                "0xab12",
                                "0xab + 0x12                            [-- 0xab12 --]",
                                "0x6d+0x74+0x72+0x6f+0x6e.as(str::T)    [-- mtron  --]"),
                        docWrap(STR_TYPE, "an ordered sequence of UTF-32 characters",
                                "\"double quoted\"",
                                "'singled quoted'",
                                "\"\"\" multi-line triple double quoted \"\"\"",
                                "''' multi-line triple single quoted '''",
                                "\"lhs processed by ${_} template parameter\"",
                                "5.map('result: ${+2}')   [-- result 7 --]"),
                        docWrap(URI_TYPE, "a uniform resource identifier",
                                "a/b/c                            [-- relative/node uri              --]",
                                "/a/b/c/                          [-- absolute/branch uri            --]",
                                "a/b/c/                           [-- relative/branch uri            --]",
                                "/a/b/c                           [-- absolute/node uri              --]",
                                "<http://mtron.gov/tax?a=b&c=d>   [-- use < > when . or space in uri --]"),
                        docWrap(LAW_TYPE, "a process law — one of the labels the law vocabulary declares, a union of uris",
                                "law::commutative      [-- a law: commutative is one of the union's labels --]",
                                "law::not_a_law        [-- no law: the union names no such label          --]",
                                "*/m/law/+             [-- every stored process law                       --]"),
                        THEORY_TYPE, RING_THEORY_TYPE, GROUP_THEORY_TYPE, MONOID_THEORY_TYPE, COMMUTATIVE_MONOID_THEORY_TYPE, FIELD_THEORY_TYPE, RIG_THEORY_TYPE, BOOLEAN_THEORY_TYPE, LATTICE_THEORY_TYPE, SEMILATTICE_THEORY_TYPE, NEAR_RING_THEORY_TYPE,
                        docWrap(THEORY_FORM_TYPE, "a theory form — the key a type's theory block gives an instance: a theory's own name, or add_ or mul_ and a one-op theory's name; a union of uris",
                                "theory_form::ring         [-- a theory with several op roles: its own name          --]",
                                "theory_form::add_monoid   [-- a one-op theory: the instruction family its op binds  --]"),
                        docWrap(REL_TYPE, "a directed binary poly coupling two objs",
                                "a=>b",
                                "a=>b=>c=>d       [-- a=>(b=>(c=>d)) --]",
                                "a=>b=>c=>d.>>    [-- b=>(c=>d)      --]",
                                "a=>b=>c=>d>>.>>  [-- c=>d           --]",
                                "(a=>b)(b=>c)     [-- a=>c           --]",
                                "{a,b}=>{c,d}     [-- 2-2 hyper-rel  --]",
                                "{a=>b}=>{e=>f}   [-- functor rel    --]"),
                        docWrap(LST_TYPE, "an ordered sequence poly of objs",
                                "[,]              [-- empty lst  --]",
                                "[a,b,c]          [-- 3 uri lst  --]",
                                "['a','b','c']    [-- 3 str lst  --]",
                                "[a,[b,[c,d],e]]  [-- nested lst --]"),
                        docWrap(REC_TYPE, "a poly composed of uniquely keyed rels",
                                "[=>]                  [-- empty rec      --]",
                                "[a=>1,b=>2]           [-- 2 uri=>int rec --]",
                                "[a=>[b=>1,c=>[d=>3]]] [-- nested rec     --]",
                                "[a=>[b=>+1,c=>_]]     [-- inst values    --]"),
                        docWrap(INST_TYPE, "a call with apply defined by an lhs obj, a poly of args, and an body of code",
                                "abc(?int::T,?int::T){ *<0> + *<1> }        [-- position args inst    --]",
                                "abc(a=>?int::T,b=>?int::T){ *a + *b }      [-- named args inst       --]",
                                "abc(a=>else(1),b=>else(2)){ *a + *b }      [-- default args inst     --]",
                                "abc(a=>?int::T,b=>?>3.else(4)){ *a + *b }  [-- contextual args inst  --]",
                                "abc(a=>?int::T,b=>|(*a +10)){ _.*b }       [-- dependent arg inst    --]",
                                "(_,_){*0 + *1}                             [-- 2-arg lambda inst     --]"),
                        docWrap(CODE_TYPE, "a call with apply defined by an lhs obj and a sequence of insts",
                                "12.plus(mult(_))            [-- 2-depth code           --]",
                                "1-<[_,-<[_,_]>-]>-.sum()    [-- sugar'd branching code --]"),
                        //  docWrap(OBJS_TYPE, "an ordered sequence poly of objs and noobjs"),
                        docWrap(FAIL_TYPE, "a reified exception obj that can be caught",
                                "fail::[ouch]                [-- a fail obj with message --]",
                                "fail::[doh] + 2             [-- plus(2) skipped over    --]",
                                "fail::[dah] + 2 + catch(9)  [-- fail flattened to 9     --]"),
                        /// ///////////////////////////////////
                        MEM_SPACE_TYPE = TypeBuilder.build()
                                .tid(SPACE_TID)
                                .vid(MEM_SPACE_TID)
                                // .isaPredicate(rec(uri(DATA).maybe().asUri(), URI_TYPE).maybe())
                                .constructor(
                                        instC(INST_CTOR_TID.rng(MEM_SPACE_TID),
                                                lst(isa_(REC_TYPE).tryToInst()),
                                                (lhs, inst) -> memSpace.of(inst.arg(0).asRec(), inst.arg(0).vid())),
                                        Map.of(uri(DATA).maybe(), "a file location to save space state (reads on creation and writes on close)"))
                                .desc("an in-memory space with objs indexed by a topic trie")
                                .create(),
                        /*docWrap(ESTORE_SPACE_TYPE = TypeBuilder.build()
                                        .tid(SPACE_TID)
                                        .vid(ESTORE_SPACE_TID)
                                        // .isaPredicate(rec(uri(DATA).maybe().asUri(), URI_TYPE).maybe())
                                        .constructor(
                                                instC(INST_CTOR_TID.rng(ESTORE_SPACE_TID),
                                                        lst(isa_(REC_TYPE).tryToInst()),
                                                        (lhs, inst) -> estoreSpace.of(inst.arg(0).asRec(), inst.arg(0).vid()))).create(), "", "",
                                Map.of(uri(DATA).maybe(), "a file location to save space state (reads on creation and writes on close)"),
                                "an in-memory space with objs indexed by a topic trie"),*/
                        docWrap(QPROC_TYPE, """
                                qprocs (query processors) are optional space components.
                                qproc behaviors are driven by a qprocs specified uri ?-query pattern.
                                not all spaces have the same set of attached qprocs.
                                qprocs must be attached to a space before use. a space's qprocs are accessible at
                                
                                     *space/vid/qProc — i.e. a space \\(\\mathcal{S}\\) exposes its qprocs as \\(\\mathrm{qprocs}(\\mathcal{S})\\)
                                """),
                        /// ///////////////////////////////////
                        REGEX_TYPE = TypeBuilder.build().tid(STR_TID).vid(REGEX_TID, "a str refined to a regex pattern").create(),
                        /// ///////////////////////////////////
                        SUBQ_TYPE = TypeBuilder.build()
                                .tid(QPROC_TID)
                                .vid(SUBQ_TID, """
                                        uri publish-subscribe qproc.
                                        when writing an inst to a subq uri, be sure to |-block prefix 
                                        so as to store the inst and not its evaluation in the assignment.
                                        """)
                                .isaPredicate(rec(uri(SUB).maybe().asUri(), rec(T(URI_TID.maybe()), SUBQ_TYPE)), Map.of(uri(SUB).maybe().asUri(), "subscriptions to register immediately upon construction"))
                                .constructor(QCollection::subQ)
                                .example("/usr/ai/#?subq -> sub::[code=>print('ai update: ${_}')] [-- /usr/ai subtree watch  --]", "a?subq         -> sub::[code=>>>1+1.println(_).to(a)]   [-- infinite incr loop     --]", "*tree?subq                                              [-- all subs for tree      --]", "*tree/#?subq                                            [-- all subs for all tree  --]")
                                .create(),
                        SUB_TYPE,
                        PUB_TYPE,
                        LOCK_TYPE,
                        ANALQ_TYPE,
                        docWrap(TYPEQ_TYPE, "addr type constraint qproc",
                                "abc?typeq -> int::T       [-- abc can only reference a single integer --]",
                                "abc?typeq -> 'not an int' [-- yields a fail::T --]"),
                        docWrap(DOCS_TYPE, "a documentation structure to attach to objs and access via docq query processor"),
                        docWrap(DECS_TYPE, "an algebraic declaration to attach to objs and access via decq query processor"),
                        docWrap(DOCQ_TYPE, "addr documentation qproc",
                                "*docq?docq [-- this documentation --]",
                                "*int?docq  [-- documentation for int::T --]"),
                        docWrap(MINTQ_TYPE, "mint a unique uri extension to obj vid",
                                "1@abc?mintq [-- 1@abc/235ae3 --]"),
                        docWrap(SHORTQ_TYPE, "create an untyped smaller representation of obj referent",
                                "*abc?shortq=10 [-- optional value is max length of obj components (default " + DEFAULT_SHORTQ_MAX_LENGTH + ") --]"),
                        docWrap(SAFEQ_TYPE, "warns when the space is being written to"),
                        docWrap(INCRQ_TYPE, "internal counter increments and appends value to vid"),
                        docWrap(EMBEDQ_TYPE, "store and retrieve an obj's vector embedding. On a vec space, embedq::[model=>!*<location>] references the model the space embeds with, read at each write: a spec there is built into a model at every write, and with nothing there a write that brings no embedding fails"),
                        docWrap(CONSTQ_TYPE, "prevents the vid from being mutated once set"),
                        docWrap(REFQ_TYPE, "enables uri-to-uri referencing"),
                        docWrap(LINEQ_TYPE, "read or write a str to another str at a particular line or line range",
                                "*<mtron.txt?lineq=14>    [-- \"line 14\"                             --][-- read a single line  --]",
                                "<mtron.txt?lineq=14>     -> \"line 14 replacement\"                     [-- write a single line --]",
                                "<mtron.txt?lineq=14-25>  -> \"\"\"line 14\\\u200Bnthrough 25 replacement\"\"\"     [-- write a line range  --]",
                                "*<mtron.txt?lineq=14-25> [-- \"\"\"line 14\\\u200Bnthrough 25 replacement\"\"\" --][-- read a line range   --]",
                                "*<mtron.txt?mimeq=text/plain&lineq=2>                                 [-- read 2nd line of the mime transformed encoding --]"),
                        docWrap(MIMEQ_TYPE, "maps the obj to the specified mime type"),
                        docWrap(LOCKQ_TYPE, "advisory locks over regions of space: a write to a uri matching a held, unexpired lock throws",
                                "cs:src/#?lockq -> lock::[usr=>/usr/agent1,expire=>datetime://...]   [-- acquire --]",
                                "cs:src/.../Foo.java -> ...                                          [-- throws while locked --]",
                                "cs:src/#?lockq -> noobj                                             [-- release --]")),

                uri(CONST), lst(
                        /*docWrap(NONE, "a token uri denoting nothing. used for deleting obj in space.")*/),
                uri(INST), lst(Stream.of(
                        Bool.BoolType.insts().stream(),
                        Bytes.BytesType.insts().stream(),
                        mInsts.int_insts().stream(),
                        mInsts.real_insts().stream(),
                        mInsts.bool_insts().stream(),
                        mInsts.bytes_insts().stream(),
                        mInsts.str_insts().stream(),
                        mInsts.uri_insts().stream(),
                        mInsts.lsts_insts().stream(),
                        mInsts.rec_insts().stream(),
                        mInsts.inst_insts().stream(),
                        mInsts.code_insts().stream(),
                        mInsts.objs_insts().stream(),
                        Int.IntType.insts().stream(),
                        Real.RealType.insts().stream(),
                        Str.StrType.insts().stream(),
                        Uri.UriType.insts().stream(),
                        Inst.InstType.insts().stream(),
                        Rel.RelType.insts().stream(),
                        Code.CodeType.insts().stream(),
                        Fail.FailType.insts().stream(),
                        //  Objs.ObjsType.insts().stream(),
                        SpaceType.insts().stream(),
                        ObjType.insts().stream(),
                        Obj.Helper.isaInsts().stream(),
                        Stream.of(docWrap(instC(M_ISA_INST_TID.extend("save").dom(ALL).rng(ALL), lst(), (lhs, inst) -> lhs.save()),
                                        "persist the lhs obj at its own vid — a no-op when the lhs has no vid",
                                        "42@abc.save()   [-- 42@abc --]"),
                                docWrap(instA(INST_CTOR_TID),
                                        "the generic type constructor — the base of the type-literal syntax, refined by each type to its own constructor: \\(c_{\\tau}(\\mathrm{spec}) \\mapsto v : \\tau\\)",
                                        "memspace::[data=>/usr/marko]   [-- a memspace::T built from its constructor arg --]"),
                                docWrap(instC(M_ISA_INST_TID.extend("lcd").dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL_STAR)), (lhs, inst) -> Type.Helper.findLCD(inst.arg(0).stream().map(o -> o.isType() ? o.asType() : o.type()).toList())),
                                        null,
                                        "the lcd of the arg type set",
                                        Map.of(jnt(0), "a collection of objs (non-type obj types are extracted)"),
                                        "calculates the deepest branch of the type hierarchy for which all the argument types are a refinement off"))
                ).flatMap(i -> i)),
                uri(REWRITE), lst(
                        // capture the original (pre-collapse) code for profile(): runs FIRST so the
                        // id/plus/mult collapses below don't strip the code before it is timed.
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("profile_analysis").q(ZINDEX, 1000),
                                code -> {
                                    final List<Inst> insts = code.insts();
                                    if (insts.isEmpty() || insts.size() < 2) return code;
                                    // TWO MARKERS, ONE CAPTURE: the profile() a writer types, and the bare profile_collate()
                                    // a distribution plan ends a profiled master's run with (Shard), its lanes answering
                                    // reports. A minted collator carries its run as its argument, so it is never a marker.
                                    final Tuple.Pair<Integer, Inst> profileInst = IteratorUtil.indexedStream(insts.iterator())
                                            .filter(i -> PROFILE_INST_TID.equals(i.get1().tid().basePath())
                                                    || (PROFILE_COLLATE_TID.equals(i.get1().tid().basePath()) && i.get1().args().isEmpty()))
                                            .findAny().orElse(null);
                                    if (null == profileInst) return code;
                                    final List<Inst> preceding = new ArrayList<>(insts.subList(0, profileInst.get0()));
                                    // capture the pre-collapse code by CLOSURE, not as an inst arg — an arg
                                    // (even block-wrapped) is resolved/collapsed by the outer resolve, which
                                    // would strip id()/plus(0)/mult(1) before the rewrite can be timed.
                                    final Code precedingCode = MCode.of(preceding);
                                    final List<Inst> bundledCode = new ArrayList<>();
                                    // dom is maybe (not maybeSome): one lhs in, exactly one report out. a bulk (gather)
                                    // dom made the monad loop apply this inst *and* flush it through the barrier, so
                                    // profile() returned two reports — a {2} objs, printed as two tables end to end.
                                    bundledCode.add(PROFILE_INST_TID.equals(profileInst.get1().tid().basePath())
                                            ? profileAnalysis(precedingCode)
                                            : profileCollate(precedingCode));
                                    if (profileInst.get0() + 1 < insts.size()) {
                                        final Inst capInst = insts.get(profileInst.get0() + 1);
                                        bundledCode.add(capInst.dom(PROFILING_TYPE));
                                        if (profileInst.get0() + 2 < insts.size()) {
                                            bundledCode.addAll(insts.subList(profileInst.get0() + 2, profileInst.get0() + 3));
                                        }
                                    }
                                    return code.jvm(bundledCode);
                                }), "rewrites a().b().c().profile() to profile_analysis(a().b().c()), and a distribution plan's a().branch(…).profile_collate() to profile_collate(a().branch(…)), the master's run timed around its branch: \\(\\mathrm{profile} \\leadsto \\mathrm{profile\\_analysis}(\\ldots)\\)"),

                        // THE UNIT LAW (Marko, 2026-11-08): monoid_theory states op(id) ⇝ ε, every monoid instance a carrier
                        // declares binds it (Law.Helper.rules), and each bound unit drops from a chain. The theories are /m's,
                        // so the law runs on the base path, the category imported or not: it is the id_removal and the
                        // plus_zero / mult_one this table once spelled syntactically, now read off the declared algebras
                        // (code's mul_monoid binds id(), since composition is adjacency). THE IDENTITY IS A VALUE, SO THE
                        // OPERAND IS COMPARED, one rule at a time: a match only type-tests, and a computed operand passes by
                        // its range (plus(map(3).neg()) would pass as plus(0), and derivation_contraction would never see it).
                        // Only composition's identity is one: the additive group's id is end, composition's zero, and taking
                        // it for the identity turned 5.end() into 5. A lone instruction is a selector (a payload), not a
                        // morphism to collapse, so the bare identity stays alone; a sole unit such as [plus(0)] still drops.
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("theory_unit_removal"), code -> {
                            Code out = code;
                            for (final Rec unit : Law.Helper.rules(MONOID_THEORY_TID)) {
                                // the unit's equation read off its raw map: a rel minted per unit per pass type-checked itself
                                final Inst term = unit.recValue().keySet().iterator().next().asCall().insts().getFirst();
                                out = Law.Helper.rewrite(out, List.of(unit), (scanned, start, length) -> term.args().equals(scanned.insts().get(start).args())
                                        && (!term.args().isEmpty() || 1 < scanned.insts().size()));
                            }
                            return out;
                        }), "the monoid's identity rule, stated once on monoid_theory and bound by every monoid instance of the operand's type: an operation applied to its own identity drops out, \\(\\mathrm{op}(\\mathrm{id}) \\leadsto \\varepsilon\\), and code's identity operation drops out of a chain, composition being adjacency. e.g. \\(x + 0 \\leadsto x\\), \\(x \\cdot 1 \\leadsto x\\), \\(x \\cdot \\langle\\rangle \\leadsto x\\), \\(x \\cdot \\mathrm{id} \\leadsto x\\)"),

                        // ONE REWRITE PER RULE (Marko, 2026-11-10; its rules alone, 2026-11-13): each carries its rules as its
                        // declared argument (InstSet.Helper.rewriter) and hands them to Law.Helper.rewrite under its own gate. A
                        // law's rule is stated in the name op, which the law's claimants bind (Law.Helper.claimed), and its gate
                        // reads the law at the carrier flowing into the run (Rewriter.Helper.carriers)
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("map_nest"), MAP_NEST, (code, rules) ->
                                        Law.Helper.rewrite(code, List.of(rules), (scanned, start, length) -> true)),
                                "the stream lift is idempotent: a mapping of a mapping is the mapping, whatever it maps, \\(\\mathrm{map}(\\mathrm{map}(f)) \\leadsto \\mathrm{map}(f)\\). e.g. \\(\\mathrm{map}(\\mathrm{map}(\\mathrm{sum})) \\leadsto \\mathrm{map}(\\mathrm{sum})\\)"),
                        // the hole ranges over the form: a run changes only where the operation of what the hole captured, an
                        // instruction or the op symbol a chain carries before resolution (map(neg)), declares the mapper form
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("form_map_unwrap"), FORM_MAP_UNWRAP, (code, rules) -> {
                                    Code out = code;
                                    for (final Map.Entry<Obj, Obj> rule : rules.recValue().entrySet()) {
                                        final List<Inst> pattern = rule.getKey().asCall().insts();
                                        out = Law.Helper.rewrite(out, List.of(rec(rule.getKey(), rule.getValue())), (scanned, start, length) -> {
                                            final Map<fURI, Obj> holes = Code.Helper.bind(scanned.insts().subList(start, start + length), pattern);
                                            return null != holes && !holes.isEmpty() && holes.values().stream().allMatch(hole -> Inst.Form.mapper == InstBuilder.declaredForm(
                                                    hole.isInst() ? hole.asInst().tid() : hole.isUri() ? Machine.current().memory().alignPrefix(hole.uriValue()) : null));
                                        });
                                    }
                                    return out;
                                }),
                                "the stream lift unwraps: a mapping of a mapper is the mapper, \\(\\mathrm{map}(f) \\leadsto f\\), where the operation of \\(f\\) declares the mapper form. e.g. \\(\\mathrm{map}(\\mathrm{neg}) \\leadsto \\mathrm{neg}\\), while \\(\\mathrm{map}(\\mathrm{sum})\\) stays"),
                        // an involution at the carrier, by its own declared law or as the carrier's additive-group inverse
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("law_involution"), LAW_INVOLUTION, (code, rules) ->
                                        Law.Helper.rewrite(code, Law.Helper.claimed(rules, Law.involution), (scanned, start, length) -> {
                                            final Inst op = scanned.insts().get(start);
                                            final Type carrier = Rewriter.Helper.carriers(scanned).get(start);
                                            if (Law.Helper.declares(op, carrier, Law.involution))
                                                return true;
                                            final Inst inverse = TheoryHelper.invInst(carrier);
                                            return !inverse.isNoObj() && inverse.test(op);
                                        })),
                                "the involution law: an adjacent pair of an operation that is an involution at the chain's carrier drops out, \\(f \\cdot f \\leadsto \\varepsilon\\), as does a pair of the carrier's additive-group inverse. e.g. \\(\\mathrm{neg} \\cdot \\mathrm{neg} \\leadsto \\varepsilon\\)"),
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("law_idempotent"), LAW_IDEMPOTENT, (code, rules) ->
                                        Law.Helper.rewrite(code, Law.Helper.claimed(rules, Law.idempotent), (scanned, start, length) ->
                                                Law.Helper.declares(scanned.insts().get(start), Rewriter.Helper.carriers(scanned).get(start), Law.idempotent))),
                                "the idempotent law: an adjacent pair of an operation declared idempotent at the chain's carrier, its operands equal, is one, \\(f \\cdot f \\leadsto f\\). e.g. \\(\\mathrm{ucase} \\cdot \\mathrm{ucase} \\leadsto \\mathrm{ucase}\\)"),
                        // a fold: the contract dispatch picks at the carrier gathers many to one (a reducer or a join), and the
                        // operation is declared monoidic there -- the shape a repeated fold is idempotent on
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("law_monoidic"), LAW_MONOIDIC, (code, rules) ->
                                        Law.Helper.rewrite(code, Law.Helper.claimed(rules, Law.monoidic), (scanned, start, length) -> {
                                            final Inst op = scanned.insts().get(start);
                                            final Rec declared = Law.Helper.declared(op.tid().basePath(), Rewriter.Helper.carriers(scanned).get(start));
                                            if (declared.isNoObj())
                                                return false;
                                            final Inst.Form form = Inst.Form.of(op.tid(declared.atDirect(OBJ).tid()));
                                            return (Inst.Form.reducer == form || Inst.Form.join == form) && Law.Helper.declares(declared, Law.monoidic);
                                        })),
                                "the monoidic law: a fold of a fold is the fold, \\(f \\cdot f \\leadsto f\\), for a reducer or join declared monoidic at the chain's carrier. e.g. \\(\\mathrm{sum} \\cdot \\mathrm{sum} \\leadsto \\mathrm{sum}\\)"),
                        // THE STRICTER BOUND: the gate reads the bounds a and b its run bound, so each claimed rule runs under
                        // its own pattern. The bound kept, b, is ordered past a by the comparison itself (b > a under gt, b < a
                        // under lt), or the two are equal. The comparison is evaluated, so it must be declared pure as well as
                        // poset at the carrier, and both bounds must be values. A pair the order relates neither way stays
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("law_poset"), LAW_POSET, (code, rules) -> {
                                    Code out = code;
                                    for (final Rec rule : Law.Helper.claimed(rules, Law.poset)) {
                                        final List<Inst> pattern = rule.recValue().keySet().iterator().next().asCall().insts();
                                        out = Law.Helper.rewrite(out, List.of(rule), (scanned, start, length) -> {
                                            final Map<fURI, Obj> names = Code.Helper.bind(scanned.insts().subList(start, start + length), pattern);
                                            final Obj a = null == names ? null : names.get(f("a"));
                                            final Obj b = null == names ? null : names.get(f("b"));
                                            if (null == a || null == b || a.isCall() || b.isCall())
                                                return false;
                                            final Inst comparison = scanned.insts().get(start).arg(0).asCall().insts().getFirst();
                                            final Rec declared = Law.Helper.declared(comparison.tid().basePath(), Rewriter.Helper.carriers(scanned).get(start));
                                            return Law.Helper.declares(declared, Law.poset) && Law.Helper.declares(declared, Law.pure)
                                                    && (a.equals(b) || Processor.Helper.apply(b, comparison.args(lst(a))).booleanCheck());
                                        });
                                    }
                                    return out;
                                }),
                                "the poset law: two filters on one comparison are the filter on the stricter bound, the comparison evaluated to tell which, \\(x > a \\wedge x > b \\Leftrightarrow x > \\max(a, b)\\). e.g. \\(\\mathrm{is}(\\mathrm{gt}(1)) \\cdot \\mathrm{is}(\\mathrm{gt}(3)) \\leadsto \\mathrm{is}(\\mathrm{gt}(3))\\)"),
                        // THE ABSORBED RUN: the rule's {+}#, which a rewrite takes as long as it can and shortens where this
                        // refuses it, never holds the seed (the chain's initial form, read off the contract its typing resolved
                        // it to), and the op must be absorbing at the carrier flowing into it. Dropping each instruction of the
                        // run must not change what the op answers, read off declarations alone, so an undeclared op is never
                        // dropped. PURE: one that is not is effectful (side, print, to) and stops the run. MULTIPLICITY: a
                        // terminal op (rng {0}) answers noobj whatever reaches it, so any pure instruction goes; any other answers
                        // a constant per obj, so the instruction must hand it one obj per obj -- a mapper, or a filter when the op
                        // is non-strict, its {?} dom firing on the noobj the filter leaves ({1,2,3}.is(gt(1)).zero() is {2}0).
                        // CARRIER: the op's law was read for the chain's carrier, so the instruction maps its own carrier to
                        // itself at both ends, since a declaration is found by EITHER end (5.as(str::T).zero() is '', not 0)
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("law_absorbing"), LAW_ABSORBING, (code, rules) ->
                                        Law.Helper.rewrite(code, Law.Helper.claimed(rules, Law.absorbing), (scanned, start, length) -> {
                                            final List<Type> carriers = Rewriter.Helper.carriers(scanned);
                                            final int last = start + length - 1;
                                            final Inst seed = Rewriter.Helper.contracts(scanned).getFirst();
                                            if (0 == start && null != seed && seed.isInitial())
                                                return false;
                                            final Rec absorbing = Law.Helper.declared(scanned.insts().get(last).tid().basePath(), carriers.get(last));
                                            if (!Law.Helper.declares(absorbing, Law.absorbing))
                                                return false;
                                            final fURI op = absorbing.atDirect(OBJ).tid();
                                            final Long least = op.dom().c().min();
                                            return IntStream.range(start, last).allMatch(i -> {
                                                final Rec prior = Law.Helper.declared(scanned.insts().get(i).tid().basePath(), carriers.get(i));
                                                // a swallowed instruction is dropped with everything it is handed, so all of it must be pure
                                                if (prior.isNoObj() || !scanned.insts().get(i).tid(prior.atDirect(OBJ).tid()).isPure())
                                                    return false;
                                                if (op.rng().c().isZero())
                                                    return true;
                                                final fURI tid = prior.atDirect(OBJ).tid();
                                                final fURI dom = tid.dom().basePath();
                                                if (!dom.equals(tid.rng().basePath()) || !(dom.isGeneric() || (null != carriers.get(i).vid() && dom.equals(carriers.get(i).vid().basePath()))))
                                                    return false;
                                                final Inst.Form form = Inst.Form.of(scanned.insts().get(i).tid(tid));
                                                return Inst.Form.mapper == form || (Inst.Form.filter == form && (null == least || 0L >= least));
                                            });
                                        })),
                                "the absorbing law \\(g \\cdot f \\leadsto f\\): an operation declared absorbing at the chain's carrier swallows the run before it that cannot change what it answers -- pure instructions that hand it one obj per obj and keep the carrier, a filter too when its dom is non-strict, any pure instruction when it answers noobj, never the seed. e.g. \\((x + 3) \\cdot \\mathrm{zero} \\leadsto x \\cdot \\mathrm{zero}\\), while \\(\\{1,2,3\\}.\\mathrm{is}(\\mathrm{gt}(1)).\\mathrm{zero}\\) keeps its filter"),
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("else_after_count"), ELSE_AFTER_COUNT, (code, rules) ->
                                        Law.Helper.rewrite(code, List.of(rules), (scanned, start, length) -> true)),
                                "count is total, so an else after it is dead code: \\(\\mathrm{count} \\cdot \\mathrm{else}(x) \\leadsto \\mathrm{count}\\)"),
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("range_skip_take"), RANGE_SKIP_TAKE, (code, rules) ->
                                        Law.Helper.rewrite(code, List.of(rules), (scanned, start, length) -> true)),
                                "a window is a skip and a take, its bounds literal ints: \\(\\mathrm{range}(i, n) \\leadsto \\mathrm{skip}(i) \\cdot \\mathrm{take}(n)\\)"),

                        // THE INVERSE LAW: group_theory states op(a)·op(inv(a)) ⇝ ε and op(inv(a))·op(a) ⇝ ε, every group
                        // instance a carrier declares binds them (Law.Helper.rules), and an adjacent pair with equal operands
                        // cancels where the second is the inverse the first declares at the chain's carrier, in that direction.
                        // The theories are /m's. A declared inverse is one-sided: inverse(f) = g claims x.f().g() = x (f a
                        // section, g its retraction), never x.g().f() = x -- on int, x.mult(2).div(2) is x but 5.div(2).mult(2)
                        // is 4. Endpoints are unavailable before resolution, so the operand type that selected the declaration
                        // is the type guard. An operand that is its type's declared zero never cancels: the zero annihilates
                        // under mult (mult(0) has no inverse), and under plus it is the unit law's anyway. An involution's pair
                        // is law_involution's.
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("inverse_cancellation"), code ->
                                Law.Helper.rewrite(code, Law.Helper.rules(GROUP_THEORY_TID), (scanned, start, length) -> {
                                    final Inst a = scanned.insts().get(start);
                                    final Inst b = scanned.insts().get(start + 1);
                                    final fURI inverse = TheoryHelper.roleAddress(Law.Helper.declared(a.tid().basePath(), Rewriter.Helper.carriers(scanned).get(start)), INVERSE);
                                    return null != inverse && b.tid().basePath().equals(inverse.basePath())
                                            && a.args().equals(b.args())
                                            && a.args().elements().noneMatch(arg -> TheoryHelper.instances(arg.type()).jvm().values().stream()
                                            .anyMatch(block -> block.isRec() && arg.equals(block.asRec().at(ZERO))));
                                })), "the inverse law \\(f \\cdot f^{-1} \\leadsto \\varepsilon\\) on the declared opposing edge: an adjacent inverse pair with equal operands cancels -- \\(x + 3 - 3 \\leadsto x\\), \\(x \\cdot 2 \\div 2 \\leadsto x\\)"),

                        // DERIVATION CONTRACTION: a derived instruction's primitive composition folds back to the derived
                        // operation (plus . neg |-> minus, mult . inv |-> div), read off the derivations instructions declare
                        // beside themselves (Law.Helper.derivations). Each equation runs backward: every contiguous run that
                        // tests the rhs becomes the lhs, its holes reading what the rhs's holes matched. The rhs is matched as a
                        // whole WINDOW (a one-instruction rhs nests its composition in its args, plus(map(#::T@b).neg())), since
                        // matching only its last instruction would contract every sum to count. AN EQUATION SPEAKS WHERE ITS
                        // CONTRACT RUNS (Marko, 2026-11-08): only where the carrier flowing into the run dispatches the derived
                        // op to the contract that declares it (Law.Helper.selects). Real's div ≡ mult·inv once contracted an
                        // int's 7.mult(2.inv()) to a truncating div, and int's minus ≡ plus·neg a lst's plus into the lst minus.
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("derivation_contraction"), code -> {
                            Code out = code;
                            for (final Map.Entry<fURI, Rec> derivation : Law.Helper.derivations().entrySet())
                                out = Law.Helper.rewrite(out, List.of(rec(derivation.getValue().atDirect(uri(RHS)).asCode(), derivation.getValue().atDirect(uri(LHS)).asCode())),
                                        (scanned, start, length) -> Law.Helper.selects(Rewriter.Helper.carriers(scanned).get(start), derivation.getKey()));
                            return out;
                        }), "folds a derived instruction's primitive composition back to the derived operation, from the type's declared \\(\\mathrm{derivation}\\): \\(\\mathrm{plus} \\cdot \\mathrm{neg} \\leadsto \\mathrm{minus}\\), \\(\\mathrm{mult} \\cdot \\mathrm{inv} \\leadsto \\mathrm{div}\\)"),

                        // THE CODE RING'S NORMAL FORMS, read off the additive op its theory declares (branch): the
                        // left-distributive form (Marko, 2026-11-10: two parallel expressions run as one, branched where their
                        // instructions differ), a·b + a·c ⇝ a·(b + c); like terms, x + x ⇝ x{2}; and the one-term sum, S(x) ⇝ x.
                        // The category registered the last two until 2026-11-12: its rewrites are /m's now. Each reads the op
                        // off the code ring's declared theory at rewrite time, so no body names branch, and a code ring that
                        // declares no additive op leaves the chain as it is. The op is matched by its base path: it is
                        // registered generic, so a space read's admitted() would drop it, and the bare address is unique to the
                        // code ring. Two members are the same term by Law.Helper.sameMember, the one test both laws read.
                        //
                        // THE LEFT-DISTRIBUTIVE FORM goes as far as the like-term normal form and no further: one shared
                        // instruction a pass (the fixpoint reaches a longer prefix); every member alike in its head and point, so
                        // no point moves, and in its span, or sharing the shortest of exact runs; every member keeping a
                        // remainder, so no unit is introduced; and the
                        // head pure, as it then runs once instead of once per member. A branch feeds its members one obj at a
                        // time, so the head must take one obj at a time too (a mapper, filter or flatmapper): a shared reducer
                        // would gather the whole stream ahead of the branch, and {1,2}.branch(count().plus(1),count().plus(2))
                        // would count 2 where each member counts 1
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("branch_factor"), code -> {
                            final fURI op = TheoryHelper.additiveWitness(T(CODE_TID));
                            if (null == op)
                                return code;
                            final List<Inst> insts = code.insts();
                            final Predicate<Obj> factorable = m -> m.isCode() && m.c().isOne() && 1 < m.asCode().insts().size();
                            // the rewriter runs before resolution, so a head's form and purity are read for the carrier flowing into its branch.
                            // EVERY CHAIN IS PROBED, ONE WITHOUT THE OP TOO (2026-11-25): the probe memoizes a chain that resolves whole by
                            // its identity (Resolver.Helper.RESOLVE_CACHE), and the compile runs that walk after a later rule rewrites the
                            // code in place (DBRewriteBuilder's selfJVM, split_merge_*, pred_extract). Skipping the probe runs those rules'
                            // outputs instead: whether they may run is the question e70959af9 left open, and not every DB plan is checked yet
                            final List<Type> carriers = Rewriter.Helper.carriers(code);
                            boolean changed = false;
                            final List<Inst> out = new ArrayList<>(insts.size() + 1);
                            for (int i = 0; i < insts.size(); i++) {
                                final Inst inst = insts.get(i);
                                // A LAYOUT FACTORS NOTHING: a head run ahead of a deal changes what each lane is dealt
                                // (a filter moves the members between lanes), and a lane's head is its move
                                final List<Obj> members = op.basePath().equals(inst.tid().basePath()) && !inst.args().isRec() && !BranchInst.isLayout(inst)
                                        ? inst.args().elements().toList() : List.of();
                                final Inst head = 2 > members.size() || !factorable.test(members.getFirst()) ? null : members.getFirst().asCode().insts().getFirst();
                                final Rec declared = null == head ? noobjRec() : Law.Helper.declared(head.tid().basePath(), carriers.get(i));
                                // HEADS ALIKE BUT FOR AN EXACT SPAN SHARE THEIR SHORTEST RUN (Marko, 2026-11-15: a span stays on its
                                // instruction), a^m.b + a^n.c = a^m.(b + a^(n-m).c): {2}plus(1).mult(3) and {3}plus(1).mult(4) share
                                // {2}plus(1), as their unrolled chains shared plus(1).plus(1), the longer keeping the rest of its run
                                final boolean exact = null != head && members.stream().allMatch(m -> factorable.test(m)
                                        && m.asCode().insts().getFirst().tid().b().isExact() && 0 < m.asCode().insts().getFirst().tid().b().min());
                                if (declared.isNoObj()
                                        || !EnumSet.of(Inst.Form.mapper, Inst.Form.filter, Inst.Form.flatmapper).contains(Inst.Form.of(head.tid(declared.atDirect(OBJ).tid())).subring())
                                        || !head.tid(declared.atDirect(OBJ).tid()).isPure() || !members.stream().allMatch(m -> factorable.test(m)
                                        && Law.Helper.sameMember(exact ? m.asCode().insts().getFirst().tid(m.asCode().insts().getFirst().tid().b(head.tid().b())) : m.asCode().insts().getFirst(), head)
                                        && m.asCode().insts().getFirst().tid().d().equals(head.tid().d()))) {
                                    out.add(inst);
                                    continue;
                                }
                                final long shortest = exact ? members.stream().mapToLong(m -> m.asCode().insts().getFirst().tid().b().min()).min().orElseThrow() : 0L;
                                changed = true;
                                // a narrowed span is a fresh instruction (instB writes the tid), never a clone (Shard.unroll)
                                out.add(!exact || shortest == head.tid().b().min() ? head : instB(head.tid().b(cInt.of(shortest)), head.args()));
                                out.add(inst.args(lst(members.stream()
                                        .map(m -> {
                                            final List<Inst> rest = new ArrayList<>(m.asCode().insts());
                                            final Inst shared = rest.removeFirst();
                                            if (exact && shortest < shared.tid().b().min())
                                                rest.addFirst(instB(shared.tid().b(cInt.of(shared.tid().b().min() - shortest)), shared.args()));
                                            return (Obj) MCode.code(rest).tryToInst();
                                        })
                                        .toList())));
                            }
                            return changed ? MCode.code(out) : code;
                        }), "the left-distributive normal form, law-indexed: members of the algebra's additive op that all start with the same pure instruction share it ahead of the op -- \\(\\mathrm{branch}(a \\cdot b, a \\cdot c) \\leadsto a \\cdot \\mathrm{branch}(b, c)\\), \\(ab + ac = a(b + c)\\) -- one instruction a pass, the heads alike to their point and to their span (exact runs sharing the shortest, \\(a^m b + a^n c \\leadsto a^m (b + a^{n-m} c)\\)) and taking one obj at a time (a map, filter or flatmap, as a branch feeds its members), every member keeping a remainder"),
                        // THE LIKE-TERM LAW: equal members of an occurrence of the op collapse to the member that first appeared,
                        // carrying the summed multiplicities, S(x,x,…,x) ⇝ S(x{n}). A LAYOUT IS A ROUTE TABLE
                        // (BranchInst#isLayout): its members are SCHEDULED (each runs at its lane's place), not free additive
                        // terms. Equal lanes under ONE key are already summed by the rec itself (a => x, a => x is a => x{2}),
                        // lanes at different places never collapse, and equal lanes of a deal each take their own share, which
                        // one lane with their summed point would not: {1,2,3}.branch?<=int{*}&split=part(count(),count()) is
                        // {2,1}, never {2}3. A MEMBER'S MULTIPLICITY is the coefficient the law sums: an inst's own point d,
                        // the point of a code's last inst (on the member's output), else the obj's point (a stream's count).
                        // Never the head's: a point there floats past a reducer, so
                        // 5.branch(plus(1).count(),plus(1).count()) collapsed to plus{2}(1).count() and answered 2, not {2}1.
                        // Never the whole coefficient b·d: a span is part of the function (like terms share it), so summing it
                        // as a point read {?}plus(1) + plus(1) as plus{1,2}(1). The same holds for a type and a value: a type's
                        // span is its filter's power ({0}nat::T is the identity, no nat::T) and a value's a run the branch
                        // keeps curled (Marko, 2026-11-18), so terms of one span alone are alike (Law.Helper.sameMember). The
                        // summed point is written back there, its span untouched
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("branch_collapse"), code -> {
                            final fURI op = TheoryHelper.additiveWitness(T(CODE_TID));
                            if (null == op)
                                return code;
                            final Function<Obj, cInt> multiplicity = member -> member.isCode() && !member.asCode().insts().isEmpty()
                                    ? member.asCode().insts().getLast().tid().d()
                                    : member.isObjs() ? member.c() : member.tid().d();
                            boolean changed = false;
                            final List<Inst> out = new ArrayList<>(code.insts().size());
                            for (final Inst inst : code.insts()) {
                                if (!op.basePath().equals(inst.tid().basePath()) || inst.args().isRec() || 2 > inst.args().count() || BranchInst.isLayout(inst)) {
                                    out.add(inst);
                                    continue;
                                }
                                final List<Obj> reps = new ArrayList<>();
                                final List<cInt> sums = new ArrayList<>();
                                for (final Obj arg : inst.args().elements().toList()) {
                                    int slot = -1;
                                    for (int j = 0; j < reps.size(); j++) {
                                        if (Law.Helper.sameMember(reps.get(j), arg)) {
                                            slot = j;
                                            break;
                                        }
                                    }
                                    if (-1 == slot) {
                                        reps.add(arg);
                                        sums.add(multiplicity.apply(arg));
                                    } else
                                        sums.set(slot, sums.get(slot).plus(multiplicity.apply(arg)));
                                }
                                if (reps.size() < inst.args().count()) {
                                    changed = true;
                                    final List<Obj> merged = new ArrayList<>(reps.size());
                                    // a representative is kept as written only when it already carries the sum: the first member may be
                                    // zero-pointed (branch(id{0}(),id()) sums to 1 on id{0}()), so a sum of 1 is no proof of an unpointed rep
                                    for (int i = 0; i < reps.size(); i++) {
                                        final Obj rep = reps.get(i);
                                        final cInt sum = sums.get(i);
                                        if (multiplicity.apply(rep).equals(sum))
                                            merged.add(rep);
                                        else if (rep.isCode() && !rep.asCode().insts().isEmpty()) {
                                            final List<Inst> insts = new ArrayList<>(rep.asCode().insts());
                                            insts.set(insts.size() - 1, insts.getLast().d(sum).asInst());
                                            merged.add(MCode.code(insts));
                                        } else
                                            merged.add(rep.isObjs() ? rep.c(sum) : Law.Helper.withPoint(rep, sum));
                                    }
                                    out.add(inst.args(lst(merged)));
                                } else
                                    out.add(inst);
                            }
                            return changed ? MCode.code(out) : code;
                        }), "the additive like-term law, law-indexed: equal members of any occurrence of the algebra's additive op collapse to the single member with the summed coefficients -- on the code ring (S read from \\(\\mathrm{object}[\\mathrm{code}].\\mathrm{law}\\), \\(S = \\mathrm{branch}\\)) \\(\\mathrm{branch}(+2, +4, +2) \\leadsto \\mathrm{branch}(+2\\{2\\}, +4)\\) -- the additive normal form the code ring's distributivity law consumes"),
                        // THE ONE-TERM SUM LAW: an occurrence of the op whose members have come to one is that member, added to
                        // nothing, S(x) ⇝ x. The frame unwraps and its own coefficient folds into the member, branch{c}(b) ⇝ b{c}:
                        // a code member takes it on its last inst, where its multiplicity is read, and any other member takes the
                        // multiplication itself. A call member splices in by its insts, a non-call member lifts to the map that
                        // emits it, stream-transparent either way. A LONE MEMBER IS ONE TERM AS THE ONE LANE IT MEANS
                        // (BranchInst#lanes), and only a lane that never changes machines (BranchInst#moves) inlines: the branch's
                        // merge returns home (Marko, 2026-11-24), where an inlined lane keyed to another place, or a member that
                        // moves, would run here or leave the caller wherever it moved. A home key's ?timeout is the cut that
                        // stays, cut?timeout=n(<~>, program), which moves nowhere, so it inlines as the lane, never dropped. A
                        // SPANNED ONE-TERM SUM IS A RUN of the frame, never a point on its member (Marko, 2026-11-15): {2}branch(b)
                        // is b.b, so it stays for Inst.Helper.runs to walk, where folding its c() (span x point) in made
                        // {2}branch(plus{3}(34)) one plus{6}(34)
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("branch_inline"), code -> {
                            final fURI op = TheoryHelper.additiveWitness(T(CODE_TID));
                            if (null == op)
                                return code;
                            boolean changed = false;
                            final List<Inst> out = new ArrayList<>(code.insts().size());
                            for (final Inst inst : code.insts()) {
                                // A POSITIONAL SPLIT IS NO ONE-TERM SUM EITHER: its lone lane runs on a thread of its own, and
                                // its ?split is checked where it runs (BranchInst#split refuses one it cannot perform), both of
                                // which an inlined member would skip. A DESIGNATOR KEY names its places when the branch runs
                                // (BranchInst#designated), and a branch its lanes refuse refuses there, so neither is inlined
                                final List<BranchInst.Lane> lanes;
                                try {
                                    lanes = op.basePath().equals(inst.tid().basePath()) && 1 == inst.args().count() && inst.tid().b().isOne()
                                            && (inst.args().isRec() ? !BranchInst.designated(inst) : !inst.tid().hasQ(SPLIT)) ? BranchInst.lanes(inst) : List.of();
                                } catch (final MTronException e) {
                                    out.add(inst);
                                    continue;
                                }
                                if (1 == lanes.size() && !BranchInst.moves(lanes.getFirst().member())) {
                                    final Obj lone = lanes.getFirst().member();
                                    final cInt c = inst.c();
                                    final Obj member;
                                    if (cInt.ONE().equals(c))
                                        member = lone;
                                    else if (lone.isCode()) {
                                        final List<Inst> insts = new ArrayList<>(lone.asCode().insts());
                                        insts.set(insts.size() - 1, (Inst) insts.getLast().c(o -> o.mult(c)));
                                        member = MCode.code(insts);
                                    } else
                                        member = lone.c(o -> o.mult(c));
                                    changed = true;
                                    if (member.isCall())
                                        out.addAll(member.asCall().insts());
                                    else
                                        out.add((Inst) map_(member).tryToInst());
                                } else
                                    out.add(inst);
                            }
                            return changed ? MCode.code(out) : code;
                        }), "the one-term sum law, law-indexed: an occurrence of the algebra's additive op with a single member is that member added to nothing -- \\(\\mathrm{a} \\cdot \\mathrm{branch}(b) \\cdot \\mathrm{c} \\leadsto \\mathrm{a} \\cdot b \\cdot \\mathrm{c}\\), the frame's own coefficient folded into the member, \\(\\mathrm{branch}\\{c\\}(b) \\leadsto b\\{c\\}\\) -- a non-call member unwraps as the map that emits it, \\(\\mathrm{branch}(x) \\leadsto \\mathrm{map}(x)\\)"),
                        // THE POINT FLOATS OFF A MAP'S ARGUMENT (Marko, 2026-11-17): map(x{c}) ⇝ map{c}(x), the map emitting c
                        // copies of what x emits once, so its range of one obj holds and map(int{2}::T) is map{2}(int::T).
                        // branch(int::T,int::T) compiles to it: its like terms sum to branch(int{2}::T) and its one member
                        // inlines as map(int{2}::T). A value or a type floats its point when the point is exact: a ranged one
                        // is a quantifier, and a call keeps its points where its reducers count them
                        /*docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("map_float"), code -> {
                            boolean changed = false;
                            final List<Inst> out = new ArrayList<>(code.insts().size());
                            for (final Inst inst : code.insts()) {
                                final Obj arg = MAP_INST_TID.equals(inst.tid().basePath()) && inst.args().isLst() &&
                                        1 == inst.args().count() ? inst.arg(0) : noobj();
                                final cInt point = arg.isNoObj() || arg.isCall() || (!arg.isType() && !arg.tid().b().isOne())
                                        ? cInt.ONE() : arg.isType() ? arg.tid().d() : arg.c();
                                if (point.isExact() && null != point.min() && !point.isOne() && !point.isZero()) {
                                    final Obj once = arg.isType() ? arg.d(cInt.ONE()) : arg.c(cInt.ONE());
                                    final Inst floated = (Inst) map_(once).tryToInst();
                                    out.add(floated.tid(floated.tid().b(inst.tid().b()).d(inst.tid().d().mult(point))).asInst());
                                    changed = true;
                                } else
                                    out.add(inst);
                            }
                            return changed ? MCode.code(out) : code;
                        }), "the point floats off a map's argument: \\(\\mathrm{map}(x\\{c\\}) \\leadsto \\mathrm{map}\\{c\\}(x)\\), the map emitting \\(c\\) copies of what \\(x\\) emits once, for a value or a type \\(x\\) and an exact \\(c\\). e.g. \\(\\mathrm{map}(\\mathrm{int}\\{2\\}) \\leadsto \\mathrm{map}\\{2\\}(\\mathrm{int})\\)"),*/

                        // Compress a bare rshift chain into a single walk: >>.>>.>> => >> 3.
                        // DISABLED: the fold is only referentially sound on a uri (where
                        // >> N is a depth walk); on a rec >> N is still positional, so
                        // >>.>>.>>.>> (a value broadcast) ≠ >> 4 (an index).  Once rec >> N
                        // is unified as "descend N", this becomes sound everywhere.
                        // InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("rshift_chain"),
                        //         code -> code.selfJVM(
                        //                 Rewriter.search(code.insts())
                        //                         .match(Tuple.Pair.with(instA(RSHIFT_INST_TID), 2))
                        //                         .rewriteChain(run -> {
                        //                             if (run.stream().anyMatch(i -> !i.args().isEmpty()))
                        //                                 return run;
                        //                             return List.of(instB(RSHIFT_INST_TID, lst(jnt(run.size()))));
                        //                         })).asCode()),

                        // Collapse identical branches in split-merge by summing coefficients
                        // Pattern: -<[inst,inst,...]>- → inst{n}
                        // This leverages the ring structure where identical branches collapse on merge
                        // Note: Only applies to split-merge pairs, as split alone creates superposition
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("split_merge_collapse"),
                                code -> code.selfJVM(
                                        RewriterBuilder.search(code.insts())
                                                .match(List.of(instA(SPLIT_INST_TID), instA(MERGE_INST_TID)))
                                                .rewrite(map -> {
                                                    final List<Inst> matched = map.values().stream().toList();
                                                    final Inst splitInst = matched.get(0);
                                                    final Inst mergeInst = matched.get(1);

                                                    if (splitInst.args().count() > 0 && splitInst.arg(0).isLst()) {
                                                        final Lst branches = splitInst.arg(0).asLst();
                                                        // Check if all branches are identical instructions
                                                        if (branches.count() > 1) {
                                                            final List<Obj> branchList = branches.elements().toList();
                                                            final Obj firstBranch = branchList.get(0);

                                                            // First check if firstBranch is an instruction
                                                            if (!firstBranch.isInst()) {
                                                                return matched;
                                                            }

                                                            // Check if all branches are the same instruction
                                                            boolean allIdentical = branchList.stream()
                                                                    .allMatch(b -> b.isInst() &&
                                                                            b.asInst().tid().basePath().equals(firstBranch.asInst().tid().basePath()) &&
                                                                            b.asInst().args().count() == firstBranch.asInst().args().count() &&
                                                                            (b.asInst().args().count() == 0 ||
                                                                                    b.asInst().arg(0).equals(firstBranch.asInst().arg(0))));

                                                            if (allIdentical) {
                                                                // Sum the coefficients (using max() since coefficients are exact values)
                                                                final long totalCoeff = branchList.stream()
                                                                        .mapToLong(b -> b.asInst().c().max())
                                                                        .sum();

                                                                // Return single instruction with summed coefficient
                                                                // The merge is implicit in the collapsed instruction
                                                                return List.of(firstBranch.asInst().c(c -> cInt.of(totalCoeff)).asInst());
                                                            }
                                                        }
                                                    }
                                                    return matched;
                                                })).asCode()), "applies the abelian monoid law on split code paths: \\(\\mathrm{split}([f\\{c_1\\}, \\ldots, f\\{c_n\\}]) \\cdot \\mathrm{merge} \\leadsto f\\{\\sum_i c_i\\}\\)"),

                        // Left factoring: pull out common prefix from split branches
                        // Pattern: a-<[b.c.d, b.c.e]>- → a.b.c-<[d, e]>-
                        // This reduces clock cycles by executing common prefix once
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("split_merge_left_factor"),
                                code -> code.selfJVM(
                                        RewriterBuilder.search(code.asCode().insts())
                                                .match(List.of(instA(SPLIT_INST_TID), instA(MERGE_INST_TID)))
                                                .repeat()
                                                .rewrite(map -> {
                                                    final List<Inst> matched = map.values().stream().toList();
                                                    final Inst splitInst = matched.get(0);
                                                    final Inst mergeInst = matched.get(1);

                                                    if (splitInst.args().count() > 0 && splitInst.arg(0).isLst()) {
                                                        final Lst branches = splitInst.arg(0).asLst();
                                                        final List<Obj> branchList = branches.jvm();

                                                        if (branchList.size() > 1) {
                                                            // Get instruction lists for each branch
                                                            final List<List<Inst>> branchInsts = branchList.stream()
                                                                    .map(b -> b.<Call>as().insts())
                                                                    .toList();

                                                            // Find common prefix length
                                                            int commonPrefixLen = 0;
                                                            final int minLen = branchInsts.stream().mapToInt(List::size).min().orElse(0);

                                                            for (int i = 0; i < minLen; i++) {
                                                                final Inst firstInst = branchInsts.get(0).get(i);
                                                                final int idx = i;
                                                                final boolean allMatch = branchInsts.stream()
                                                                        .allMatch(insts -> insts.get(idx).tid().equals(firstInst.tid()) &&
                                                                                insts.get(idx).args().equals(firstInst.args()));
                                                                if (allMatch) {
                                                                    commonPrefixLen++;
                                                                } else {
                                                                    break;
                                                                }
                                                            }

                                                            if (commonPrefixLen > 0 && commonPrefixLen < minLen) {
                                                                // Only optimize if there's a common prefix AND remaining instructions
                                                                // (don't optimize if all branches are identical - that's handled by collapse rewrite)

                                                                // Extract common prefix
                                                                final List<Inst> commonPrefix = branchInsts.get(0).subList(0, commonPrefixLen);

                                                                // Create new branches without the common prefix
                                                                final int commonPrefixLenFinal = commonPrefixLen;
                                                                final List<Obj> newBranches = branchInsts.stream()
                                                                        .map(insts -> (Obj) MCode.of(insts.subList(commonPrefixLenFinal, insts.size())).tryToInst())
                                                                        .toList();

                                                                // Return: common_prefix + split(new_branches) + merge
                                                                return Stream.concat(
                                                                        commonPrefix.stream(),
                                                                        Stream.of(
                                                                                instB(SPLIT_INST_TID, lst(lst(newBranches))),
                                                                                instB(MERGE_INST_TID, lst())
                                                                        )
                                                                ).toList();
                                                            }
                                                        }
                                                    }
                                                    // No optimization possible, return original
                                                    return matched;
                                                })).asCode()), "leverages distributive ring law to pull common monoidally bound components to the right: \\(\\mathrm{split}([p \\cdot d, p \\cdot e]) \\cdot \\mathrm{merge} \\leadsto p \\cdot \\mathrm{split}([d, e]) \\cdot \\mathrm{merge}\\)"),

                        // Right factoring: pull out common suffix from split branches
                        // Pattern: a-<[b.d, c.d]>- → a-<[b, c]>-.d
                        // This reduces clock cycles by executing common suffix once
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("split_merge_right_factor"),
                                code -> code.selfJVM(
                                        RewriterBuilder.search(code.asCode().insts())
                                                .match(List.of(instA(SPLIT_INST_TID), instA(MERGE_INST_TID)))
                                                .repeat()
                                                .rewrite(map -> {
                                                    final List<Inst> matched = map.values().stream().toList();
                                                    final Inst splitInst = matched.get(0);
                                                    final Inst mergeInst = matched.get(1);

                                                    if (splitInst.args().count() > 0 && splitInst.arg(0).isLst()) {
                                                        final Lst branches = splitInst.arg(0).asLst();
                                                        final List<Obj> branchList = branches.jvm();

                                                        if (branchList.size() > 1) {
                                                            // Get instruction lists for each branch
                                                            final List<List<Inst>> branchInsts = branchList.stream()
                                                                    .map(b -> b.<Call>as().insts())
                                                                    .toList();

                                                            // Find common suffix length
                                                            int commonSuffixLen = 0;
                                                            final int minLen = branchInsts.stream().mapToInt(List::size).min().orElse(0);

                                                            for (int i = 1; i <= minLen; i++) {
                                                                final int offset = i;
                                                                final Inst firstInst = branchInsts.getFirst().get(branchInsts.getFirst().size() - offset);
                                                                final boolean allMatch = branchInsts.stream()
                                                                        .allMatch(insts -> {
                                                                            final Inst inst1 = insts.get(insts.size() - offset);
                                                                            return inst1.tid().equals(firstInst.tid()) &&
                                                                                    inst1.args().equals(firstInst.args());
                                                                        });
                                                                if (allMatch) {
                                                                    commonSuffixLen++;
                                                                } else {
                                                                    break;
                                                                }
                                                            }
                                                            if (commonSuffixLen > 0 && commonSuffixLen < minLen) {
                                                                // Only optimize if there's a common suffix AND remaining instructions
                                                                // (don't optimize if all branches are identical - that's handled by collapse rewrite)

                                                                // Extract common suffix
                                                                final List<Inst> firstBranchInsts = branchInsts.getFirst();
                                                                final List<Inst> commonSuffix = firstBranchInsts.subList(
                                                                        firstBranchInsts.size() - commonSuffixLen,
                                                                        firstBranchInsts.size()
                                                                );

                                                                // Create new branches without the common suffix
                                                                final int commonSuffixLenFinal = commonSuffixLen;
                                                                final List<Obj> newBranches = branchInsts.stream()
                                                                        .map(insts -> (Obj) MCode.of(insts.subList(0, insts.size() - commonSuffixLenFinal)).tryToInst())
                                                                        .toList();

                                                                // Return: split(new_branches) + merge + common_suffix
                                                                return Stream.concat(
                                                                        Stream.of(
                                                                                instB(SPLIT_INST_TID, lst(lst(newBranches))),
                                                                                instB(MERGE_INST_TID, lst())
                                                                        ),
                                                                        commonSuffix.stream()
                                                                ).toList();
                                                            }
                                                        }
                                                    }
                                                    // No optimization possible, return original
                                                    return matched;
                                                })).asCode()), "leverages distributive ring law to pull common monoidally bound components to the left: \\(\\mathrm{split}([d \\cdot r, e \\cdot r]) \\cdot \\mathrm{merge} \\leadsto \\mathrm{split}([d, e]) \\cdot \\mathrm{merge} \\cdot r\\)"),
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("explain_analysis").q(ZINDEX, 1000),
                                code -> {
                                    final List<Inst> insts = code.insts();
                                    if (insts.isEmpty() || insts.size() < 2) return code;
                                    final Tuple.Pair<Integer, Inst> explainInst = IteratorUtil.indexedStream(insts.iterator()).filter(i -> i.get1().tid().basePath().equals(EXPLAIN_INST_TID)).findAny().orElse(null);
                                    if (null == explainInst || explainInst.get0() <= 0) return code;
                                    final List<Inst> preceding = new ArrayList<>(insts.subList(0, explainInst.get0()));
                                    // capture BOTH stages of the chain by CLOSURE, not as an inst arg — an arg
                                    // (even block-wrapped) is resolved/collapsed by the outer resolve, which
                                    // would strip id()/plus(0)/mult(1) before explain can be computed.
                                    //
                                    // SUBMITTED is the expression as written. COMPILED is that same chain through the
                                    // compiler's own rewriter (Code.rewrite -> compiler().rewriter()), which is what
                                    // actually runs — and therefore what explain() REPORTS. Capturing only SUBMITTED
                                    // (all this once did) made explain() render the pre-collapse chain while the
                                    // compiler collapsed it: the table then contradicted both the documented "prints
                                    // the compiled code" and profile(), which measures that same 8 -> 2 reduction.
                                    //
                                    // each stage gets its OWN list: the fixpoint replaces its input's inst list in
                                    // place (selfJVM), so a shared list would hand the submitted table the rewritten
                                    // rows. The insts themselves are never mutated — a rule that retypes an inst
                                    // builds a new one — so SUBMITTED keeps rendering what the user wrote.
                                    final Code submitted = MCode.of(new ArrayList<>(preceding)).resolve(noobj());
                                    final Code compiled = MCode.of(new ArrayList<>(preceding)).rewrite().resolve(noobj());
                                    // a chain the laws can empty outright would leave no insts to describe — report
                                    // the submitted stage in that case rather than tripping over an empty stage list
                                    final Code reported = compiled.insts().isEmpty() ? submitted : compiled;
                                    final List<Inst> bundledCode = new ArrayList<>();
                                    // dom is maybe (not maybeSome), mirroring profile_analysis: one lhs in, exactly
                                    // one report out. see profile_analysis — a gather dom double-applied the report,
                                    // which here merged into a single explanation{2} and a double-width >>format.
                                    bundledCode.add(instC(M_ISA_INST_TID.extend("explain_analysis").dom(ALL.maybe()).rng(EXPLANATION_TID),
                                            lst(),
                                            (lhs, inst) -> explainRec(reported, submitted)));
                                    if (explainInst.get0() + 1 < insts.size()) {
                                        final Inst capInst = insts.get(explainInst.get0() + 1);
                                        bundledCode.add(capInst.dom(EXPLANATION_TYPE));
                                        if (explainInst.get0() + 2 < insts.size()) {
                                            bundledCode.addAll(insts.subList(explainInst.get0() + 2, insts.size()));
                                        }
                                    }
                                    return code.jvm(bundledCode);
                                }), "rewrites a().b().c().explain() to explain_analysis(a().b().c()), carrying trailing insts (e.g. >>format) — \\(\\mathrm{explain} \\leadsto \\mathrm{explain\\_analysis}(\\ldots)\\), mirror of profile_analysis"),
                        // AN EXPLAIN EXTENSION IS A REWRITE (Marko, 2026-11-20): no query processor and no plugin API. It
                        // meets x.explain() before explain_analysis takes it (it keeps the default ?zindex) and puts an
                        // instruction after the marker that holds x by closure, as explain_analysis does. explain_analysis
                        // carries that instruction as it carries any after the marker, so at run time the explanation flows
                        // through it: it writes its rec at ext/<name> and appends that rec's format to the explanation's.
                        // x.explain()>>ext/topology/format is this extension's view, x.explain()>>format the table with the
                        // view after it. A chain that fans out nowhere has no topology, and its explanation stays as it was.
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("explain_topology"), code -> {
                            final List<Inst> insts = code.insts();
                            final fURI extension = EXPLAIN_INST_TID.extend(EXT).extend(TOPOLOGY);
                            final int marker = IntStream.range(0, insts.size())
                                    .filter(i -> EXPLAIN_INST_TID.equals(insts.get(i).tid().basePath())).findFirst().orElse(-1);
                            if (0 >= marker || insts.stream().anyMatch(inst -> extension.equals(inst.tid().basePath())))
                                return code;
                            final List<Inst> asked = new ArrayList<>(insts.subList(0, marker));
                            final List<Inst> extended = new ArrayList<>(insts);
                            extended.add(marker + 1, instC(extension.dom(ALL.maybe()).rng(EXPLANATION_TID), lst(), (lhs, inst) -> {
                                final Obj topology = Topology.explain(MCode.of(new ArrayList<>(asked)).rewrite().resolve(noobj()));
                                if (topology.isNoObj() || !lhs.isRec())
                                    return lhs;
                                final String table = lhs.asRec().at(uri(FORMAT)).strValue();
                                final String drawing = topology.asRec().at(uri(FORMAT)).strValue();
                                return lhs.asRec()
                                        .at(f(EXT).extend(TOPOLOGY), topology, Poly.MUTABLE)
                                        .at(f(FORMAT), auto_(() -> str(table + drawing)), Poly.MUTABLE);
                            }));
                            return code.jvm(extended);
                        }), "an explain extension: the distribution topology of the compiled chain, its point coefficients "
                                + "alone (the seed, each lane's share and what its instructions leave, the merge at home and "
                                + "what the master's instructions leave), as data at explain()>>ext/topology/flow, drawn at "
                                + "explain()>>ext/topology/format and appended to explain()>>format. A lane with levels left "
                                + "re-plans where it runs, so that machine explains it (a peer is asked over the network) and "
                                + "its subtree is drawn inside the lane"),
                        InstBuilder.build()
                                .tid(M_ISA_REWRITE_TID.extend("pred_extract"))
                                .body((code, inst) -> {
                                    if (code.asCode().insts().stream().noneMatch(i -> i.tid().basePath().equals(REPEAT_INST_TID)))
                                        return code;
                                    return code.selfJVM(code.asCode().insts().stream().map(i -> {
                                        if (!i.tid().basePath().equals(REPEAT_INST_TID))
                                            return i;
                                        Inst out = i;
                                        out = extractIntComparison(out, EMIT);
                                        out = extractIntComparison(out, UNTIL);
                                        return out;
                                    }).collect(CommonUtil.ListCollector.single()));
                                })
                                .create(),
                        // THE DISTRIBUTION RULES ARE /m's (Marko, 2026-11-24), as every local rule has been since
                        // 2026-11-12: a peer that never imported /m/math/cat had no law_shard, so a lane it was sent
                        // with hop budget left ran flat and the tree never branched. Dialing a peer is the opt-in
                        // (peers are explicit): a machine that dials nothing and states no place set pays one look at
                        // its peer map for law_shard, and one scan for a whole-frontier repeat for law_bsp, before
                        // either takes a snapshot of its network. Both run after every
                        // database pushdown (?pre=[/m/db/+/inst/rewrite]: a fold pushed into its store is no fold to
                        // deal) and, in /m's own rank, after the local laws (0) and before the capture rules (1000).
                        //
                        // the distribution kernel -- Thm 19 of Stream Ring Theory, registered as a rewrite:
                        // (a+b)·r = (a·r + b·r)·combiner(r) for a reducer whose partials recombine through a
                        // monoidic, commutative fold -- r itself (sum, prod), the fold its derivation names
                        // (count -> sum, order(s) -> kmerge(s)), or pointwise for a keyed group. The seed bulk is
                        // factored over the machine's dialed peers, the elementwise (floatable) run is replicated
                        // per factor with a local partial reduce, and the combiner is applied once more at home:
                        //
                        //   {1,2,3}.plus(2).sum()
                        //     |->  branch(<a> => start({1,3}).plus(2).sum?hop=0(),
                        //                 <b> => start({2}).plus(2).sum?hop=0()).sum()
                        //
                        // branch keys each part by the place it runs (<~> home, a peer's authority); a remote lane
                        // ships its program uncompiled to its place's eval, every lane runs on its own virtual
                        // thread, and the branch gathers the partials for the combiner. A peer with hop budget left
                        // re-plans its part over its own peers (the tree).
                        //
                        // POLICY: the guard requires dialed peers and a hop budget (the plan's tree placement, or
                        // ?hop=n on the chain; ?hop=0 opts a fold out), so on a lone machine the rule is the identity
                        // and the default schedule is unchanged. Whether a NETWORKED machine should auto-distribute
                        // every eligible chain at compile time, or only via an explicit >>law_shard edge, is policy.
                        // THE SUPERSTEP (Marko, 2026-11-22): a whole-frontier repeat whose code is pure and floatable
                        // deals its frontier over the dialed peers every pass -- the loop stays home, each pass a
                        // branch that parts the frontier by the branch's own rule and merges the lanes before the
                        // next pass (lock-step BSP):
                        //
                        //   x.repeat?<=A{*}(code=>out(),until=>loop()?>4)
                        //     |->  x.repeat?<=A{*}(code=>branch?<=A{*}&split=part(<//p> => out()?hop=0,
                        //                                                         <//q> => out()?hop=0),
                        //                          until=>loop()?>4)
                        //
                        // its own rule, not a cut in law_shard's plan (Marko): only the dialed peers take a share,
                        // never the roster
                        docWrap(InstSet.Helper.rewriter(
                                        M_ISA_REWRITE_TID.extend("law_bsp").q(PRE, DB_REWRITES).q(ZINDEX, 500),
                                        code -> Bsp.apply(code)),
                                "the superstep: a whole-frontier repeat (repeat?<=A{*}) whose code is pure and floatable deals its frontier over the dialed peers every pass -- \\(\\mathrm{repeat}^{\\{*\\}}(f) \\leadsto \\mathrm{repeat}^{\\{*\\}}(\\mathrm{branch}^{\\{*\\}}_{\\mathrm{part}}(p_1 \\Rightarrow f, \\ldots, p_n \\Rightarrow f))\\): the loop stays home and each pass is a branch that parts the frontier over the peers (lock-step BSP)"),
                        docWrap(InstSet.Helper.rewriter(
                                        M_ISA_REWRITE_TID.extend("law_shard").q(PRE, DB_REWRITES).q(ZINDEX, 500),
                                        code -> Shard.lone(Machine.current().network().peers(), code)
                                                ? code : PlanInterpreter.lower(PlanInterpreter.SHARD_PLAN, code)),
                                "the distribution kernel \\((a+b) \\cdot r = (a \\cdot r + b \\cdot r) \\cdot \\mathrm{combiner}(r)\\) (Thm 19): factor the seed bulk over the dialed peers, replicate the floatable run per factor with a local partial reduce, recombine the partials behind a barrier through the reducer's combiner -- computed, never declared: \\(\\Sigma\\) and \\(\\Pi\\) recombine through themselves, \\(\\mathrm{count}\\) through \\(\\Sigma\\), \\(\\mathrm{order}(s)\\) through \\(\\mathrm{kmerge}(s)\\), \\(\\mathrm{group}([k \\Rightarrow h])\\) pointwise through \\(\\Sigma\\) then \\(\\mathrm{combiner}(h)\\) per key, and \\(\\mathrm{mean}\\) through itself after its lift \\(\\ell\\), each lane answering its mean weighted by its count -- \\(\\{1,2,3\\}.f.\\mathrm{count} \\leadsto \\mathrm{branch}(a \\Rightarrow \\{1,3\\}.f.\\mathrm{count},\\ b \\Rightarrow \\{2\\}.f.\\mathrm{count}).\\Sigma\\)"))

                /*uri(SUGAR), lst(sugars().stream()
                        .map(s -> rec(
                                START, null == s.getStartToken() ? noobj() : str(s.getStartToken()),
                                END, null == s.getEndToken() ? noobj() : str(s.getEndToken()),
                                ARGS, jnt(s.getArgCount()),
                                PATTERN, s.getInstChain().stream().map(fURI::toUri).collect(new CommonUtil.LstCollector()))))*/)));
        docWrap(this, "the core instruction set of metatron containing the base types and useful instructions to manipulate them");
        // PUBLISH THE LAW VOCABULARY AS DATA, IN THIS SET'S OWN MOUNT: one uri per law at /m/law/<label>, its prose at
        // ?docq and its shapes at ?decq are staged here and land in the drain super.setup() runs for /m — so the values,
        // the law::T type above and the space that owns them are published together. register() is address-keyed, so a
        // re-mount (or a second call) republishes the same writes rather than duplicating them.
        Law.register();
        super.setup();
    }


    /**
     * The per-evaluation dom and rng of one instruction, the branch applying its own rule: its c_dom is its
     * grain ({?} refines to {1}, an explicit {*} barrier or {n} page is kept), and its c_rng is the sum of its
     * branches' rngs, each branch program typed at that grain. Every other instruction reports its compiled
     * dom/rng as-is — those are its declared coefficients, since a {1} dom feeds one obj per evaluation and the
     * stream's multiplicity is c_out's business, not c_dom's. This is the single source of truth for the
     * instruction table, shared by explain()'s table and the ExplainTool widget.
     */
    public record ExplainTypes(Type dom, Type rng, cInt cDom, cInt cRng) {
    }

    public static ExplainTypes explainTypes(final Inst i) {
        // only a local branch applies its own rule; a layout (BranchInst#isLayout: lanes headed by cuts, a deal, keys)
        // is a route table the plan rewriter types, and its rng is the compiler's (the topology extension owns the
        // distributed count)
        if (!i.tid().basePath().equals(BRANCH_INST_TID) || !i.args().isLst() || BranchInst.isLayout(i))
            return new ExplainTypes(i.dom(), i.rng(), i.dom().c(), i.rng().c());
        final cInt grain = i.dom().c().isMaybe() ? cInt.ONE() : i.dom().c();
        final List<Obj> programs = i.args().values().filter(b -> !b.isNoObj()).toList();
        // a branch program emits its range times the coefficients of its instructions: plus{2}(2) is two ints, and a
        // collapsed code carries its summed coefficient on an inner inst
        final cInt sum = programs.stream()
                .map(o -> o.isCall()
                        ? o.asCall().insts().stream().map(Obj::c).reduce(o.isCode() ? o.rng().c().mult(o.c()) : o.rng().c(), cInt::mult)
                        : o.c())
                .reduce(cInt.ZERO(), cInt::plus);
        final List<Type> rngs = programs.stream().filter(Obj::isCall).map(o -> o.asCall().rng()).toList();
        final Type lcd = rngs.isEmpty() ? i.rng() : Type.Helper.findLCD(rngs);
        return new ExplainTypes(T(i.dom().tid().c(grain)), T(lcd.tid().c(sum)), grain, sum);
    }

    /**
     * Build a column-justified text table of the instructions in {@code code}. A cell may hold SEVERAL
     * LINES (the UI serializer breaks a branch's place-keyed args one entry per line), so a row is as
     * tall as its tallest cell. Terminal-free — suitable for use in rewrites and non-interactive contexts.
     */
    private static String explainTable(final Code code) {
        final List<String> headers = List.of(
                "op", "dom", "rng", "args", "f", "desc", "c_dom", "c_rng", "c_out");
        final List<List<String>> rows = new ArrayList<>();
        rows.add(headers);
        cInt running = cInt.ONE();
        for (final Inst i : code.insts()) {
            // AN INSTRUCTION IS {span}inst{point}, written as the language writes it: c() is span x point, a value's
            // count of copies, so {10}plus(4) read as plus{10}, a point of ten, and {2}plus{3}(1) as plus{6}
            final ExplainTypes e = explainTypes(i);
            // c_out is the running output count. {0} (initial) and {*} (gather) reset it to c_rng; {1} and {?}
            // (per-element, at most one obj per eval) scale it by c_rng; {n>1} (page) groups the stream and scales it
            running = (e.cDom().isZero() || null == e.cDom().max()) ? e.cRng()
                    : e.cDom().lte(cInt.ONE()) ? running.mult(e.cRng())
                    : running.div(e.cDom()).mult(e.cRng());
            rows.add(List.of(
                    (i.tid().b().isOne() ? "" : ("{" + i.tid().b() + "}")) + i.tid().name() + (i.tid().d().isOne() ? "" : ("{" + i.tid().d() + "}")),
                    e.dom().vid().small() + "::T",
                    e.rng().vid().small() + "::T",
                    explainArgs(i),
                    i.hasf() ? (i.f().isLambda() ? "<j>" : "<m>") : "<?>",
                    Inst.Form.of(i).toString(),
                    "{" + e.cDom() + "}",
                    "{" + e.cRng() + "}",
                    "{" + running + "}"));
        }
        final int cols = headers.size();
        final int[] widths = new int[cols];
        for (final List<String> row : rows)
            for (int c = 0; c < cols; c++)
                widths[c] = Math.max(widths[c], widestLine(row.get(c)));
        final StringBuilder sb = new StringBuilder("\n");
        for (final List<String> row : rows) {
            final List<String[]> cells = row.stream().map(cell -> cell.split("\n", -1)).toList();
            final int height = cells.stream().mapToInt(lines -> lines.length).max().orElse(1);
            for (int line = 0; line < height; line++) {
                for (int c = 0; c < cols; c++)
                    sb.append(String.format(" %-" + widths[c] + "s ", line < cells.get(c).length ? cells.get(c)[line] : ""));
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * ONE ARGUMENT LIST, RENDERED BY THE UI SERIALIZER — the same writer the console, the docs pipeline and
     * a log line use, so a branch's place-keyed args break and clip the way every rec does: an entry per
     * line once the rec reads wider than {@link ObjmtronUISerializer#NESTED_STRING_THRESHOLD}, and the
     * serializer's own {@code ...} when the list runs past its clip.
     */
    private static String explainArgs(final Inst inst) {
        return inst.args().isEmpty() ? "" : ObjmtronUISerializer.diagnostic().write(inst.args());
    }

    /**
     * The widest physical line of a (possibly multi-line) cell.
     */
    private static int widestLine(final String cell) {
        return Arrays.stream(cell.split("\n", -1)).mapToInt(String::length).max().orElse(0);
    }

    /**
     * The dom/rng value of a stage rec: a zero-coefficient (noobj) type yields noobj itself,
     * otherwise the type's vid as a uri. (a noobj{c} uri and a noobj literal parse to different
     * classes — noobj-vs-noobj is the one equivalence test literals can express)
     */
    private static Obj domRngValue(final Type t) {
        // a range unbounded below has no min (cc's A.any(): a coefficient may be set negative), and is no zero
        if (Long.valueOf(0L).equals(t.c().min())) return noobj();
        return uri(f(t.vid().small().pathString()));
    }

    /**
     * The least count a coefficient admits, or noobj (the slot omitted) when it is unbounded below, as a range is
     * whose coefficient may be set negative (cc's A.any()).
     */
    private static Obj least(final cInt c) {
        return null == c.min() ? noobj() : jnt(c.min());
    }

    /**
     * The int-comparison tids a repeat's until/emit predicate may fold to, as a superposition (branch) so one
     * {@code test()} matches {@code gt}/{@code lt}/{@code gte}/{@code lte}/{@code eq}/{@code neq} alike.
     */
    private static final fURI INT_CMP_TID = GT_INST_TID.plus(LT_INST_TID).plus(GTE_INST_TID)
            .plus(LTE_INST_TID).plus(EQ_INST_TID).plus(NEQ_INST_TID);

    /**
     * Every database instset's rewrites, {@code /m/db/+/inst/rewrite}: the distribution rules run after them all, so
     * their {@code ?pre} is this one family, written as the {@code [{+}uri::T]} it reads back as.
     */
    private static final List<fURI> DB_REWRITES = List.of(DB_ISA_TID.extend("+").extend(INST).extend(REWRITE));

    /**
     * Lift a repeat's until/emit predicate out of its filter wrapper when it is {@code is(cmp(int))} (or
     * {@code filter(cmp(int))}), the wrapper's one argument being the one comparison instruction: the repeat body's
     * single-inst apply then runs the native {@code intValue() OP arg(0).intValue()} directly — no monadic walk,
     * no nested processor, no per-iteration re-resolve. A predicate that is not that shape leaves the inst unchanged.
     */
    private static Inst extractIntComparison(final Inst repeat, final String key) {
        final Obj predicate = repeat.arg(key, 0);
        if (predicate.isNoObj() || !predicate.isInst())
            return repeat;
        final Inst filter = predicate.asInst();
        final fURI wrapper = filter.tid().basePath();
        if ((!IS_INST_TID.equals(wrapper) && !FILTER_INST_TID.equals(wrapper)) || 1 != filter.args().count() || !filter.arg(0).isCall())
            return repeat;
        final List<Inst> tested = filter.arg(0).<Call>as().asCode().insts();
        if (1 != tested.size() || !tested.getFirst().test(instB(INT_CMP_TID, lst(INT_TYPE))))
            return repeat;
        // resolve to the native int overload so the repeat body's apply runs intValue() OP arg(0).intValue()
        // directly — a resolved inst carries its f, so resolve() short-circuits and no processor is spawned.
        final Inst nativeComparison = tested.getFirst().resolve(jnt(0));
        return repeat.args(repeat.args().asRec().at(uri(key), nativeComparison));
    }

    /**
     * Build the explanation::T rec of the COMPILED instructions in {@code code}: expression-level
     * metadata (desc), per-instruction stages (per_inst), and a lazily constructed format
     * (today's text table) — with the rewrite chain wired, {@code desc.rewrite} being the
     * explanation of {@code submitted}, the chain as the user wrote it. Terminal-free — suitable
     * for use in rewrites and non-interactive contexts.
     */
    private static Rec explainRec(final Code code, final Code submitted) {
        final List<Inst> insts = code.insts();
        // per-instruction stage records
        final List<Obj> perInstRecs = new ArrayList<>();
        cInt running = cInt.ONE();
        for (final Inst i : insts) {
            final ExplainTypes e = explainTypes(i);
            running = (e.cDom().isZero() || null == e.cDom().max()) ? e.cRng()
                    : e.cDom().lte(cInt.ONE()) ? running.mult(e.cRng())
                    : running.div(e.cDom()).mult(e.cRng());
            perInstRecs.add(rec(
                    uri(OP), uri(f(i.tid().name())),
                    uri(DOM), domRngValue(e.dom()),
                    uri(RNG), domRngValue(e.rng()),
                    // the args as their own objs (ints, calls, ...), not their string renderings
                    uri(ARGS), lst(i.args().elements().toList()),
                    uri(Tokens.F), i.hasf() ? (i.f().isLambda() ? str("<j>") : i.getMtronFunctionObj()) : noobj(),
                    uri(FORM), uri(f(Inst.Form.of(i).toString())),
                    uri(C_DOM), least(e.cDom()),
                    uri(C_RNG), least(e.cRng()),
                    uri(C_OUT), least(running)));
        }
        // rewrite-stage chain: this rec describes the COMPILED code, so it points BACK at the submitted
        // expression's rec; that rec is the chain's last and terminates it (noobj). explain() therefore
        // returns the HEAD of the chain — the stage that runs — and >>desc>>rewrite walks to what was written.
        final Obj submittedRec = null == submitted ? noobj() : explainRec(submitted, null);
        return rec(
                mutableMap(uri(FORMAT), auto_(instLambda(ALL.maybe(), STR_TID, (lhs, inst) -> str(explainTable(code)))),
                        uri(DESC), rec(
                                uri(DOM), domRngValue(explainTypes(insts.getFirst()).dom()),
                                uri(RNG), domRngValue(explainTypes(insts.getLast()).rng()),
                                uri(INSTS), jnt(insts.size()),
                                uri(REWRITE), submittedRec),
                        uri(PER_INST), lst(perInstRecs)), EXPLANATION_TID, null);
    }

    /**
     * Time the resolve (compile) and apply (evaluate) stages of {@code code} after a single
     * warm-up run, and return a small text table of min/max stage times. Terminal-free —
     * suitable for use in rewrites and non-interactive contexts.
     */
    /**
     * THE INSTRUCTION {@code profile()} MINTS — {@code profile} is a MAKER: it is not itself the instruction
     * that gets evaluated, the rewrite mints this one around the captured program instead.
     * <p>
     * It is a FOLD and a BARRIER: it aggregates every monad the run produced up to the marker and answers ONE
     * compressed {@code profile::profiling} rec. That is why it declares {@code monoidic} (the partials
     * regroup), {@code commutative} (a collation cannot depend on arrival order) and {@code pure} (it
     * observes, it does not mutate) — the same three laws {@code sum} declares, so a shard may end in a
     * {@code profile_analysis} and have its report recombined at the master.
     * <p>
     * The captured program is bound by CLOSURE, never as an argument: an inst arg — even block-wrapped — is
     * resolved/collapsed by the outer resolve, which would strip {@code id()}, {@code plus(0)} and
     * {@code mult(1)} before they could be timed (see the {@code profile_analysis} rewrite rule).
     * <p>
     * THE DOM STAYS {@code maybe}, not {@code maybeSome}: a bulk dom made the monad loop apply this inst AND
     * flush it through the barrier, so {@code profile()} answered two reports — a {@code {2}} objs printed as
     * two tables end to end. That is the tension to settle when the shard end-cap lands: {@code Form.reducer}
     * (which {@code law_shard}'s cut reads) is derived from a many-to-one dom, so this inst currently reads
     * as a mapper however its laws are declared.
     */
    public static final fURI PROFILE_ANALYSIS_TID = M_ISA_INST_TID.extend("profile_analysis");

    /**
     * Mint the analysis inst for a captured program: {@code profile()} in a chain is replaced by one of these,
     * closed over everything that preceded the marker.
     */
    public static Inst profileAnalysis(final Code preceding) {
        return InstBuilder.build()
                .tid(PROFILE_ANALYSIS_TID, "the analysis stage profile() mints: aggregate the monads up to the marker into one compressed report")
                .dom(ALL.maybe(), "the monads the run produced up to the profile marker")
                .rng(PROFILING_TID, "the compressed profile report")
                .args(lst(preceding))
                .body((lhs, inst) -> profileTable(preceding, new AtomicReference<>()))
                .law(Law.monoidic, Law.commutative, Law.pure)
                // THE FORM IS DECLARED, not derived: Form.of reads a many-to-one dom as a reducer, and this
                // inst's dom is deliberately `maybe` (a bulk dom made it answer two reports). Declared so
                // Law.Helper.combiner's FORM gate lets the declared laws be consulted — without it the inst
                // classifies as a mapper and declaresAll is never reached.
                .form(Inst.Form.reducer)
                .desc("profile() is a MAKER: this is the instruction it mints around the captured program. A FOLD and a BARRIER — it aggregates every monad up to the marker and answers one compressed profile::profiling rec, so its laws are the ones a shard's report needs in order to recombine at the master: monoidic (the partials regroup), commutative (a collation is order-free) and pure (it observes, it does not mutate). The program is captured by closure, never as an argument, so the outer resolve cannot collapse the instructions being timed.")
                .create();
    }

    /**
     * THE COLLATION INSTRUCTION — {@code profile_collate(run)}: the master's half of a DISTRIBUTED profile, and the
     * reason {@code profiling::T} is recursive.
     * <p>
     * THE MASTER'S RUN IS TIMED AROUND ITS BRANCH. A profiled cut ships lanes that end in {@code profile()}, so each
     * answers a report instead of a partial, and ends the master's run with a bare {@code profile_collate()}. The
     * {@code profile_analysis} rule, which runs after the distribution rule, captures the run before that marker as it
     * captures a local one and mints this instruction over it. The body times the run as {@code profile()} times a
     * local run (one warm-up, five timed runs), so the master's own report is an ordinary profiling::T whose
     * {@code apply} holds the branch's gather wait, the one number no lane can report. The reports the last timed run
     * gathered nest under it: ONE report, {@code place => [<path-encoded machine> => profiling::T, …]}.
     * <p>
     * KEYED, NEVER POSITIONAL — a reader must be able to say WHICH machine owns a number without reconstructing the
     * fan-out order, and the topologies differ (a star's flat fan-out, a tree's hops, a divide-and-conquer plan), so the
     * structure has to be self-describing rather than implied by the layout. THE KEY IS THE PATH ENCODING
     * ({@link #PATH_ENCODING_TID}): {@code ws/localhost/1112/mtron} is one addressable segment,
     * {@code ws://localhost:1112/mtron} is not a key at all.
     * <p>
     * A TREE NESTS LIKE THE TREE. An interior node, a peer handed a lane with hop budget left, is a master to its own
     * places, so it answers a collated report and its parent nests it whole. The figures of a whole tree are FOLDED
     * BOTTOM-UP, because they form a commutative monoid: each node folds its places' figures as it collates, and a leaf
     * contributes itself, so nothing below a place is walked twice — {@code depth} (the levels below), {@code leaf}
     * (how many leaves, the objs their runs carried as {@code c_sum}, their summed apply as {@code time}), {@code level}
     * (per level below, the places' apply: count, min, max, total) and {@code path} (the critical path: the slowest
     * place at each level, down to a leaf). A place's apply is its stage apply min, its steady-state run.
     * <p>
     * MONOIDIC + COMMUTATIVE + PURE for the same reason the other two profiling insts are: the reports regroup across
     * lanes and machines, the index does not depend on arrival order, and collation observes.
     */
    public static final fURI PROFILE_COLLATE_TID = M_ISA_INST_TID.extend("profile_collate");

    /**
     * Mint the collation inst over the master's captured run. Deliberately NOT registered as an instruction of the
     * ISA: like {@link #profileAnalysis(Code)} it is a MAKER's product, minted by the {@code profile_analysis} rule
     * where a distribution plan ended a profiled master's run in a bare {@code profile_collate()}.
     */
    public static Inst profileCollate(final Code run) {
        return InstBuilder.build()
                .tid(PROFILE_COLLATE_TID, "the master's half of a distributed profile: its run timed around the branch, the lanes' reports nested under it")
                .dom(ALL.maybe(), "the monads the master's run produced up to the marker")
                .rng(PROFILING_TID, "the master's own report, each lane's report under place, keyed by the place it was dealt to")
                .args(lst(run))
                .form(Inst.Form.reducer)
                .law(Law.monoidic, Law.commutative, Law.pure)
                .body((lhs, inst) -> {
                    final AtomicReference<Obj> gathered = new AtomicReference<>(noobj());
                    final Rec own = profileTable(run, gathered);
                    // THE PLACES: each report the last timed run gathered, NAMED AS THE TOPOLOGY NAMES ITS LANE (Marko,
                    // 2026-11-22: "booger//633589 ... align it with //<prefix> or ws://<host>:<port>"): by the address
                    // the master dealt the lane to, read through this machine's names (//0), else //host:port, and
                    // //local for a lane at home. The lanes answer in their order, one report each
                    // (BranchInst#route), so report i is lane i's. A run whose reports do not line up with its routes
                    // keys them by the machine that made them, as does a name two lanes share (two lanes at home). A
                    // third report on one name and one machine (law_shard's third home lane) takes the second's key,
                    // so it overwrites that report: not yet fixed, as a peerless shard collates no lane report at all
                    // yet. A report that names no machine (one made in a machine without a vid) is the local one by
                    // construction.
                    final List<Obj> reports = gathered.get().stream().filter(Obj::isRec).toList();
                    final List<Obj> keys = run.insts().stream()
                            .filter(BranchInst::isLayout)
                            .findFirst()
                            .map(branch -> BranchInst.lanes(branch).stream()
                                    .map(lane -> (Obj) uri(lane.home() ? f("~") : lane.place())).toList())
                            .orElse(List.of());
                    final Map<Obj, Obj> places = new LinkedHashMap<>();
                    for (int r = 0; r < reports.size(); r++) {
                        final Obj where = reports.get(r).asRec().at(uri(MACHINE));
                        final Obj byMachine = where.isUri() ? uri(where.uriValue().pathEncoding()) : uri(LOCAL);
                        final Obj named = keys.size() == reports.size() ? uri(f(placeName(keys.get(r)))) : byMachine;
                        places.put(places.containsKey(named) ? byMachine : named, reports.get(r));
                    }
                    // THE SUBTREE, FOLDED BOTTOM-UP: leaves, objs and lane time add, depth takes the max, and a place's
                    // level i is this node's level i + 1, merged as counts and totals add and min and max fold
                    long leaves = 0L, objs = 0L, depth = 0L;
                    double time = 0.0, slowest = -1.0;
                    Obj straggler = noobj();
                    final List<double[]> levels = new ArrayList<>();
                    for (final Map.Entry<Obj, Obj> place : places.entrySet()) {
                        final Rec report = place.getValue().asRec();
                        final double apply = report.at(uri(f(STAGE).extend(APPLY).extend(MIN))).realValue();
                        final boolean collated = !report.at(uri(PLACE)).isNoObj();
                        leaves += collated ? report.at(uri(f(LEAF).extend(COUNT))).intValue() : 1L;
                        objs += profileObjs(report);
                        time += collated ? report.at(uri(f(LEAF).extend(TIME))).realValue() : apply;
                        depth = Math.max(depth, collated ? report.at(uri(DEPTH)).intValue() : 0L);
                        final List<double[]> below = new ArrayList<>(List.of(new double[]{1.0, apply, apply, apply}));
                        report.at(uri(LEVEL)).elements().map(Obj::asRec).forEach(level -> below.add(new double[]{
                                level.at(uri(COUNT)).intValue(), level.at(uri(MIN)).realValue(),
                                level.at(uri(MAX)).realValue(), level.at(uri(TOTAL)).realValue()}));
                        for (int i = 0; i < below.size(); i++) {
                            if (levels.size() == i) {
                                levels.add(below.get(i));
                                continue;
                            }
                            final double[] merged = levels.get(i);
                            merged[0] += below.get(i)[0];
                            merged[1] = Math.min(merged[1], below.get(i)[1]);
                            merged[2] = Math.max(merged[2], below.get(i)[2]);
                            merged[3] += below.get(i)[3];
                        }
                        if (apply > slowest) {
                            slowest = apply;
                            straggler = place.getKey();
                        }
                    }
                    // THE CRITICAL PATH: the straggler, then the straggler's own critical path, down to a leaf
                    final List<Obj> path = new ArrayList<>();
                    if (!straggler.isNoObj()) {
                        path.add(straggler);
                        places.get(straggler).asRec().at(uri(PATH)).elements().forEach(path::add);
                    }
                    final Map<Obj, Obj> fields = new LinkedHashMap<>();
                    own.<Rel>elements().forEach(kv -> fields.put(kv.first(), kv.second()));
                    fields.put(uri(PLACE), rec(places));
                    fields.put(uri(DEPTH), jnt(places.isEmpty() ? 0L : depth + 1L));
                    fields.put(uri(LEAF), rec(uri(COUNT), jnt(leaves), uri(C_SUM), jnt(objs), uri(TIME), real(time, MILLIS_TYPE.vid(), null)));
                    fields.put(uri(LEVEL), lst(levels.stream().map(level -> (Obj) rec(
                            uri(COUNT), jnt((long) level[0]),
                            uri(MIN), real(level[1], MILLIS_TYPE.vid(), null),
                            uri(MAX), real(level[2], MILLIS_TYPE.vid(), null),
                            uri(TOTAL), real(level[3], MILLIS_TYPE.vid(), null))).toList()));
                    fields.put(uri(PATH), lst(path));
                    // THE ROOT'S HEADER, its body one level in, as each place's header heads the place's body
                    final StringBuilder root = new StringBuilder(String.format("%n  master %s   apply %.3f ms, %d places%n",
                            "//" + LOCAL,
                            own.at(uri(f(STAGE).extend(APPLY).extend(MIN))).realValue(), places.size()));
                    profileText(rec(fields), true).lines().forEach(line -> root.append("  ").append(line).append('\n'));
                    final String text = root.toString();
                    fields.put(uri(FORMAT), auto_(instLambda(ALL.maybe(), STR_TID, (self, format) -> str(text))));
                    return rec(fields, PROFILING_TID, null);
                })
                .desc("the master's half of a distributed profile: the plan ends a profiled master's run in a bare profile_collate(), and the profile_analysis rule mints this instruction over the run before it. It times the master's run as profile() times a local run, so its apply holds the branch's gather wait, and nests the report each lane answered under place, keyed by the place it was dealt to, named as the topology names it (//0, else //host:port). A tree nests like the tree, and the figures of the whole tree (depth, leaf, level, path) fold bottom-up as each node collates. Same laws as profile(): monoidic, commutative, pure.")
                .create();
    }

    /**
     * THE OBJS A PLACE TOOK: a collated place's leaves' coefficient sum, else the largest stream its run carried, the
     * most coefficients any one of its instructions answered (read off its per-instruction flow). Under the seed axis
     * that is the members dealt to it (its start's, a bulked member counting every copy it holds); under the run axis,
     * where every lane's start answers the one seed, it is the terms the lane unrolled (its spanned instruction's).
     */
    private static long profileObjs(final Rec report) {
        return !report.at(uri(PLACE)).isNoObj()
                ? report.at(uri(f(LEAF).extend(C_SUM))).intValue()
                : report.at(uri(PER_INST)).elements().mapToLong(inst -> inst.asRec().at(uri(C_OUT)).intValue()).max().orElse(0L);
    }

    /**
     * THE MACHINE THIS REPORT WAS GENERATED ON — the host the network answers at composed with the vid of
     * the machine the profile ran in, i.e. the mtron {@code /network/host} applied to the machine's own vid
     * ({@code booger/abc}). Written without the dereference marker in this comment on purpose: the literal
     * sequence would terminate the javadoc block.
     * <p>
     * A PROFILING REC PRODUCED ON A SHARD MUST NAME ITS ORIGIN, or a collated report cannot attribute a
     * number: the master sees k reports of identical shape and has nothing but arrival order to key them by.
     * The host is the network component's own field (read at the component, not through the full read path,
     * since this runs inside a profile); a machine with no vid — a rootless construct — contributes no
     * identity rather than a bare host, which would read as a machine called "".
     */
    /**
     * A LANE'S PLACE, NAMED AS THE TOPOLOGY NAMES IT: a key with no host is home, //local; any other is read through
     * this machine's names (Network.Helper#placeName).
     */
    private static String placeName(final Obj key) {
        final fURI written = key.uriValue().resolve().qLess();
        return !written.hasHost() ? "//" + LOCAL : Network.Helper.placeName(Machine.current().network(), written);
    }

    private static Obj machineIdentity() {
        final Machine machine = Machine.current();
        final fURI vid = machine.vid();
        if (null == vid)
            return noobj();
        final Obj host = machine.network().read(f(HOST));
        final Obj composed = host.isUri() ? uri(host.uriValue().extend(vid)) : uri(vid);
        // THE PROCESS ID IS PART OF THE IDENTITY, because host+frame is a PLACE, not a VM. Every peer forked on
        // one host is a root machine on the same OS host, so they all answer `<host>/.` -- a collated profile
        // then cannot tell a shard's report from the master's, `merge` BULKS the two indistinguishable values
        // and the index collapses to one row labelled `master`. Measured: two local peers produced
        // [master => profiling::[insts=>3]] where insts=3 was a SLAVE's chain.
        // The pid rides the PATH, not a `?q`: the collation key is pathEncoding(), which renders the path.
        return uri(composed.uriValue().toString() + "/" + ProcessHandle.current().pid());
    }

    /**
     * TIME A RUN: one warm-up, then five timed runs of {@code code}, each rewritten, resolved and applied, and the
     * report of what they cost. What the last timed run answered is handed back through {@code answer}: a collator's
     * run answers the reports its lanes made.
     */
    private static Rec profileTable(final Code code, final AtomicReference<Obj> answer) {
        // snapshot the pre-collapse insts: Code.rewrite() mutates its input in place (via
        // selfJVM), so the timed loop must rewrite a fresh copy each iteration — the snapshot
        // is what makes the original 7 → 3 reduction measurable.
        final List<Inst> originalInsts = code.insts();
        // A PROFILE REHOMES ITS EXPRESSION (Marko, 2026-11-24: explain() and profile() are the return home): each run
        // starts where the profile stands and comes back to it, so a machine change in the profiled chain (a cut into
        // this server's tree persists, as a move does) neither starts the next run elsewhere nor leaves the caller
        // there
        final Machine home = Machine.current();
        final MachinePerspective.Step published = MachinePerspective.last();
        // warm the resolver + type graph so the timed runs are steady-state
        try {
            Machine.Helper.evalAt(home, () -> MCode.of(new ArrayList<>(originalInsts)).resolve(noobj()).apply(noobj()));
        } catch (final Throwable ignored) {
            // cold-start semi-resolution may throw; the timed loop below will surface a real failure
        }
        // THE TIMED RUNS ARE PROFILED INTO AN ANALYZER OF THEIR OWN: every probe they pass -- the resolver's stages, each
        // rewrite rule, the processor's split/apply/next, each instruction's flow, the type graph's hits and misses --
        // records into it and nowhere else, so a profile measures exactly its own runs (never a concurrent evaluation's)
        // and zeroes no one's meters. The warm-up above is not profiled.
        final Analyzer profile = new BasicAnalyzer();
        final Analyzer outer = Analyzer.PROFILE.get();
        final int iters = 5;
        long resolveMin = Long.MAX_VALUE, resolveMax = 0L;
        long rewriteMin = Long.MAX_VALUE, rewriteMax = 0L;
        long applyMin = Long.MAX_VALUE, applyMax = 0L;
        final AtomicReference<Code> lastRewritten = new AtomicReference<>();
        final AtomicReference<Code> lastResolved = new AtomicReference<>();
        Analyzer.PROFILE.set(profile);
        try {
            for (int i = 0; i < iters; i++) {
                final long rw0 = System.nanoTime();
                final Code rewritten = MCode.of(new ArrayList<>(originalInsts)).rewrite();
                final long rw1 = System.nanoTime();
                final Code resolved = Machine.current().compiler().resolver().apply(noobj(), rewritten);
                // EACH ROW TIMES ITS OWN STAGE (2026-11-25). The typer is the compile's last stage, so it is timed with the
                // resolver, and the run is the processor's alone: resolved.apply(noobj()) handed the code to
                // Machine.evaluate, which compiled it again, so the apply row held a second rewrite and resolve, and the
                // resolve row was timed from the start of the rewrite
                final Code typed = Machine.current().compiler().typer().apply(resolved);
                final long r1 = System.nanoTime();
                final long a0 = System.nanoTime();
                answer.set(Machine.Helper.evalAt(home, () -> typed.insts().isEmpty()
                        ? typed.apply(noobj())
                        : Machine.current().processor().apply(typed).apply(noobj())));
                final long a1 = System.nanoTime();
                rewriteMin = Math.min(rewriteMin, rw1 - rw0);
                rewriteMax = Math.max(rewriteMax, rw1 - rw0);
                resolveMin = Math.min(resolveMin, r1 - rw1);
                resolveMax = Math.max(resolveMax, r1 - rw1);
                applyMin = Math.min(applyMin, a1 - a0);
                applyMax = Math.max(applyMax, a1 - a0);
                lastRewritten.set(rewritten);
                lastResolved.set(resolved);
            }
        } finally {
            Analyzer.PROFILE.set(outer);
            // the bar shows where the caller stands: a run that moved published its landing, and the expression is home
            if (published != MachinePerspective.last())
                MachinePerspective.publish(home);
        }
        final Code rewritten = lastRewritten.get();
        final Code resolved = lastResolved.get();
        final Analysis analysis = profile.analysis();
        // a meter's reading at its path -- a counter's count, a timer's total (…/total) -- or 0 for a probe the runs
        // never passed
        final ToLongFunction<fURI> read = path -> analysis.at(uri(path)).orElse(jnt(0)).intValue();
        final long tResolve = read.applyAsLong(f(RESOLVE).extend(INST_RESOLVE).extend(TOTAL));
        final long tBind = read.applyAsLong(f(RESOLVE).extend(GENERIC_BINDING).extend(TOTAL));
        final long tCompose = read.applyAsLong(f(RESOLVE).extend(INST_COMPOSITION).extend(TOTAL));
        final long tSplit = read.applyAsLong(f(APPLY).extend(SPLIT).extend(TOTAL));
        final long tApply = read.applyAsLong(f(APPLY).extend(APPLY).extend(TOTAL));
        final long tNext = read.applyAsLong(f(APPLY).extend(NEXT).extend(TOTAL));
        // rewrite => [<rule> => [in, out, time]], each rule read off its own rec, slowest first
        final ToLongFunction<Obj> ruleTime = rule -> rule.asRec().at(uri(f(TIME).extend(TOTAL))).orElse(jnt(0)).intValue();
        final ToLongFunction<Obj> ruleIn = rule -> rule.asRec().at(uri(IN)).orElse(jnt(0)).intValue();
        final ToLongFunction<Obj> ruleOut = rule -> rule.asRec().at(uri(OUT)).orElse(jnt(0)).intValue();
        final List<Map.Entry<Obj, Obj>> rules = new ArrayList<>(analysis.at(uri(REWRITE)).orElse(rec()).asRec().jvm().entrySet());
        rules.sort((a, b) -> Long.compare(ruleTime.applyAsLong(b.getValue()), ruleTime.applyAsLong(a.getValue())));
        // the net reduction (original → rewritten inst count) and the total rule time
        final long totalRuleTime = rules.stream().mapToLong(rule -> ruleTime.applyAsLong(rule.getValue())).sum();
        final long monads = read.applyAsLong(f(FLOW).extend(MONADS));
        final long coeffSum = read.applyAsLong(f(FLOW).extend(C_SUM));
        final long hits = read.applyAsLong(f(CACHE).extend(HITS));
        final long misses = read.applyAsLong(f(CACHE).extend(MISSES));
        final double ms = 1_000_000.0; // nanos -> millis
        // per-rewrite-rule reduction records, sorted by time (the order the text table reads them in)
        final List<Obj> ruleRecs = rules.stream()
                .map(rule -> (Obj) rec(
                        uri(NAME), rule.getKey(),
                        uri(IN), jnt(ruleIn.applyAsLong(rule.getValue())),
                        uri(OUT), jnt(ruleOut.applyAsLong(rule.getValue())),
                        uri(TIME), real(ruleTime.applyAsLong(rule.getValue()) / ms, MILLIS_TYPE.vid(), null)))
                .toList();
        // per-instruction monad/coefficient flow records
        final List<Inst> insts = resolved.insts();
        final List<Obj> perInstRecs = new ArrayList<>();
        for (int i = 0; i < insts.size(); i++) {
            final fURI at = f(PER_INST).extend(String.valueOf(i));
            perInstRecs.add(rec(
                    uri(NAME), uri(f(insts.get(i).tid().name())),
                    uri(MONAD_IN), jnt(read.applyAsLong(at.extend(MONAD_IN)) / iters),
                    uri(C_IN), jnt(read.applyAsLong(at.extend(C_IN)) / iters),
                    uri(MONAD_OUT), jnt(read.applyAsLong(at.extend(MONAD_OUT)) / iters),
                    uri(C_OUT), jnt(read.applyAsLong(at.extend(C_OUT)) / iters),
                    uri(TIME), real(read.applyAsLong(at.extend(TIME).extend(TOTAL)) / ms / iters, MILLIS_TYPE.vid(), null)));
        }
        final Map<Obj, Obj> fields = mutableMap(
                // WHICH MACHINE PRODUCED THIS REPORT — `*/network/host.mult(*~/.vid())`, e.g. booger/abc.
                // Without it a collated report cannot say where a number came from; with it, the master
                // keys the shards' reports by origin instead of by arrival order.
                uri(MACHINE), machineIdentity(),
                uri(STAGE), rec(
                        uri(REWRITE), rec(uri(MIN), real(rewriteMin / ms, MILLIS_TYPE.vid(), null), uri(MAX), real(rewriteMax / ms, MILLIS_TYPE.vid(), null)),
                        uri(RESOLVE), rec(uri(MIN), real(resolveMin / ms, MILLIS_TYPE.vid(), null), uri(MAX), real(resolveMax / ms, MILLIS_TYPE.vid(), null)),
                        uri(APPLY), rec(uri(MIN), real(applyMin / ms, MILLIS_TYPE.vid(), null), uri(MAX), real(applyMax / ms, MILLIS_TYPE.vid(), null))),
                uri(INSTS), jnt(insts.size()),
                uri(RESOLVE), rec(
                        uri(INST_RESOLVE), real(tResolve / ms / iters, MILLIS_TYPE.vid(), null),
                        uri(GENERIC_BINDING), real(tBind / ms / iters, MILLIS_TYPE.vid(), null),
                        uri(INST_COMPOSITION), real(tCompose / ms / iters, MILLIS_TYPE.vid(), null)),
                uri(REWRITE), rec(
                        uri(RULES), lst(ruleRecs),
                        uri(TOTAL), rec(uri(IN), jnt(originalInsts.size()), uri(OUT), jnt(rewritten.insts().size()), uri(TIME), real(totalRuleTime / ms, MILLIS_TYPE.vid(), null))),
                uri(APPLY), rec(
                        uri(SPLIT), real(tSplit / ms / iters, MILLIS_TYPE.vid(), null),
                        uri(APPLY), real(tApply / ms / iters, MILLIS_TYPE.vid(), null),
                        uri(NEXT), real(tNext / ms / iters, MILLIS_TYPE.vid(), null)),
                uri(PER_INST), lst(perInstRecs),
                uri(FLOW), rec(
                        uri(MONADS), jnt(monads), uri(C_SUM), jnt(coeffSum),
                        uri(COMPRESSION), real(0L == monads ? 0.0 : (double) coeffSum / (double) monads),
                        uri(PROCESSORS), jnt(read.applyAsLong(f(FLOW).extend(PROCESSORS)))),
                // the type graph's lookups during the timed runs; a run that looked nothing up missed nothing
                uri(CACHE), rec(
                        uri(HITS), jnt(hits), uri(MISSES), jnt(misses),
                        uri(HIT_RATE), real(0L == hits + misses ? 1.0 : (double) hits / (double) (hits + misses))));
        // the text is rendered from the fields, as a nesting master renders a place's, and the format leads the report
        final String text = profileText(rec(fields), true);
        final Map<Obj, Obj> report = mutableMap(uri(FORMAT), auto_(instLambda(ALL.maybe(), STR_TID, (lhs, inst) -> str("\n" + text))));
        report.putAll(fields);
        return rec(report, PROFILING_TID, null);
    }

    /**
     * A PROFILE'S TEXT, RENDERED FROM ITS DATA and never from the run that made it: a place's report crosses the wire
     * as data (its own format is a lambda of the machine that made it), so the master renders every place it nests from
     * the place's fields. THE TEXT NESTS LIKE THE TREE: a collated report's body leads with the summary over its direct
     * places (and at the {@code root} the figures of the whole tree), then its own run, then each place under a header
     * of its own (its key, its apply, and a leaf's objs or a node's count of places), the place's body one level in. A
     * leaf's body is its run alone. The formulas are named where they print: imbalance is max ÷ mean apply, and
     * efficiency is Σ lane apply ÷ (k × gather wait), the gather wait being the master's own apply (min, as every apply
     * here is: the steady-state run).
     */
    private static String profileText(final Rec report, final boolean root) {
        // a reading at a path of a report's rec, or 0 where the report holds none
        final ToDoubleBiFunction<Obj, fURI> ms = (rec, path) -> {
            final Obj value = rec.asRec().at(uri(path));
            return value.isReal() ? value.realValue() : 0.0;
        };
        final ToLongBiFunction<Obj, fURI> n = (rec, path) -> {
            final Obj value = rec.asRec().at(uri(path));
            return value.isInt() ? value.intValue() : 0L;
        };
        final fURI applied = f(STAGE).extend(APPLY).extend(MIN);
        final StringBuilder sb = new StringBuilder();
        final Obj places = report.at(uri(PLACE));
        final Obj straggler = report.at(uri(PATH)).elements().findFirst().orElse(noobj());
        if (!places.isNoObj()) {
            final List<Rel> children = places.asRec().<Rel>elements().toList();
            final double wait = ms.applyAsDouble(report, applied);
            final double[] applies = children.stream().mapToDouble(child -> ms.applyAsDouble(child.second(), applied)).toArray();
            final double total = Arrays.stream(applies).sum();
            final double mean = children.isEmpty() ? 0.0 : total / children.size();
            final double max = Arrays.stream(applies).max().orElse(0.0);
            final int width = children.stream().mapToInt(child -> child.first().toString().length()).max().orElse(0);
            sb.append(String.format("  places      %d, gathered in %.3f ms (the master's apply, its gather wait included)%n", children.size(), wait));
            sb.append(String.format("  %-" + Math.max(5, width) + "s  %8s  %10s  %6s%n", "place", "objs", "apply (ms)", "share"));
            for (int i = 0; i < children.size(); i++)
                sb.append(String.format("  %-" + Math.max(5, width) + "s  %8d  %10.3f  %5.1f%%%s%n", children.get(i).first(),
                        profileObjs(children.get(i).second().asRec()), applies[i], 0.0 == total ? 0.0 : applies[i] * 100.0 / total,
                        children.get(i).first().equals(straggler) ? "  straggler" : ""));
            sb.append(String.format("  apply       min %.3f   max %.3f   mean %.3f (ms)%n", Arrays.stream(applies).min().orElse(0.0), max, mean));
            sb.append(String.format("  imbalance   %.2f (max ÷ mean apply)%n", 0.0 == mean ? 0.0 : max / mean));
            sb.append(String.format("  efficiency  %.3f (Σ lane apply ÷ (k × gather wait))%n", 0.0 == wait || children.isEmpty() ? 0.0 : total / (children.size() * wait)));
            if (root) {
                sb.append(String.format("  tree        depth %d   leaves %d   objs %d   lane time %.3f ms%n", n.applyAsLong(report, f(DEPTH)),
                        n.applyAsLong(report, f(LEAF).extend(COUNT)), n.applyAsLong(report, f(LEAF).extend(C_SUM)), ms.applyAsDouble(report, f(LEAF).extend(TIME))));
                final List<Obj> levels = report.at(uri(LEVEL)).elements().toList();
                for (int i = 0; i < levels.size(); i++) {
                    final long count = n.applyAsLong(levels.get(i), f(COUNT));
                    final double levelMean = 0L == count ? 0.0 : ms.applyAsDouble(levels.get(i), f(TOTAL)) / count;
                    sb.append(String.format("  level %-5d %d places, imbalance %.2f%n", i + 1, count, 0.0 == levelMean ? 0.0 : ms.applyAsDouble(levels.get(i), f(MAX)) / levelMean));
                }
                // the critical path, walked down the places it names (by key equality: a machine's path may hold a
                // dot segment, <host/./pid>, which a uri lookup would resolve away)
                final StringJoiner critical = new StringJoiner(" → ");
                Obj step = report;
                for (final Obj hop : report.at(uri(PATH)).elements().toList()) {
                    step = step.isNoObj() ? step : step.asRec().at(uri(PLACE)).<Rel>elements()
                            .filter(place -> place.first().equals(hop)).findFirst().map(Rel::second).orElse(noobj());
                    critical.add(String.format("%s %.3f ms", hop, step.isNoObj() ? 0.0 : ms.applyAsDouble(step, applied)));
                }
                sb.append("  critical    ").append(critical).append('\n');
            }
        }
        sb.append("  stage     min (ms)   max (ms)\n");
        for (final String stage : List.of(REWRITE, RESOLVE, APPLY))
            sb.append(String.format("  %-8s  %8.3f   %8.3f%n", stage, ms.applyAsDouble(report, f(STAGE).extend(stage).extend(MIN)), ms.applyAsDouble(report, f(STAGE).extend(stage).extend(MAX))));
        sb.append(String.format("  insts     %d%n", n.applyAsLong(report, f(INSTS))));
        final double tResolve = ms.applyAsDouble(report, f(RESOLVE).extend(INST_RESOLVE));
        final double tBind = ms.applyAsDouble(report, f(RESOLVE).extend(GENERIC_BINDING));
        final double tCompose = ms.applyAsDouble(report, f(RESOLVE).extend(INST_COMPOSITION));
        final double resSum = tResolve + tBind + tCompose;
        sb.append("  resolve sub-stages (avg ms):\n");
        sb.append(String.format("    inst-resolve      %8.3f  %5.1f%%%n", tResolve, 0.0 == resSum ? 0.0 : tResolve * 100.0 / resSum));
        sb.append(String.format("    generic-binding   %8.3f  %5.1f%%%n", tBind, 0.0 == resSum ? 0.0 : tBind * 100.0 / resSum));
        sb.append(String.format("    inst-composition  %8.3f  %5.1f%%%n", tCompose, 0.0 == resSum ? 0.0 : tCompose * 100.0 / resSum));
        sb.append("  rewrite rules:\n");
        final List<Obj> rules = report.at(uri(f(REWRITE).extend(RULES))).elements().limit(6).toList();
        sb.append("                ");
        rules.forEach(rule -> sb.append(String.format("%-20s", rule.asRec().at(uri(NAME)))));
        sb.append("\n      insts.in  ");
        rules.forEach(rule -> sb.append(String.format("%-20d", n.applyAsLong(rule, f(IN)))));
        sb.append("\n      insts.out ");
        rules.forEach(rule -> sb.append(String.format("%-20d", n.applyAsLong(rule, f(OUT)))));
        sb.append("\n      time(ms)  ");
        rules.forEach(rule -> sb.append(String.format("%-20.3f", ms.applyAsDouble(rule, f(TIME)))));
        sb.append('\n');
        sb.append(String.format("      TOTAL     inst.in %d   inst.out %d   time(ms) %.3f%n", n.applyAsLong(report, f(REWRITE).extend(TOTAL).extend(IN)),
                n.applyAsLong(report, f(REWRITE).extend(TOTAL).extend(OUT)), ms.applyAsDouble(report, f(REWRITE).extend(TOTAL).extend(TIME))));
        final double tSplit = ms.applyAsDouble(report, f(APPLY).extend(SPLIT));
        final double tApply = ms.applyAsDouble(report, f(APPLY).extend(APPLY));
        final double tNext = ms.applyAsDouble(report, f(APPLY).extend(NEXT));
        final double appSum = tSplit + tApply + tNext;
        sb.append("  apply sub-stages (avg ms):\n");
        sb.append(String.format("    split     %8.3f  %5.1f%%%n", tSplit, 0.0 == appSum ? 0.0 : tSplit * 100.0 / appSum));
        sb.append(String.format("    apply     %8.3f  %5.1f%%%n", tApply, 0.0 == appSum ? 0.0 : tApply * 100.0 / appSum));
        sb.append(String.format("    next      %8.3f  %5.1f%%%n", tNext, 0.0 == appSum ? 0.0 : tNext * 100.0 / appSum));
        sb.append("  per-instruction (per iteration):\n");
        final List<Obj> perInst = report.at(uri(PER_INST)).elements().toList();
        sb.append("                ");
        perInst.forEach(inst -> sb.append(String.format("%-10s", inst.asRec().at(uri(NAME)))));
        sb.append('\n');
        for (final String[] row : new String[][]{{"in.monad  ", MONAD_IN}, {"in.coeff  ", C_IN}, {"out.monad ", MONAD_OUT}, {"out.coeff ", C_OUT}}) {
            sb.append("      ").append(row[0]);
            perInst.forEach(inst -> sb.append(String.format("%-10d", n.applyAsLong(inst, f(row[1])))));
            sb.append('\n');
        }
        sb.append("      time(ms)  ");
        perInst.forEach(inst -> sb.append(String.format("%-10.3f", ms.applyAsDouble(inst, f(TIME)))));
        sb.append('\n');
        sb.append(String.format("  flow       monads=%d coeff-sum=%d compression=%.2f processors=%d%n", n.applyAsLong(report, f(FLOW).extend(MONADS)),
                n.applyAsLong(report, f(FLOW).extend(C_SUM)), ms.applyAsDouble(report, f(FLOW).extend(COMPRESSION)), n.applyAsLong(report, f(FLOW).extend(PROCESSORS))));
        sb.append(String.format("  cache     hits=%d misses=%d hit=%.1f%%%n", n.applyAsLong(report, f(CACHE).extend(HITS)),
                n.applyAsLong(report, f(CACHE).extend(MISSES)), ms.applyAsDouble(report, f(CACHE).extend(HIT_RATE)) * 100.0));
        if (!places.isNoObj())
            places.asRec().<Rel>elements().forEach(child -> {
                final Obj below = child.second().asRec().at(uri(PLACE));
                sb.append(String.format("  %s   apply %.3f ms, %s%s%n", child.first(), ms.applyAsDouble(child.second(), applied),
                        below.isNoObj() ? "a leaf, objs " + profileObjs(child.second().asRec()) : below.asRec().<Rel>elements().count() + " places",
                        child.first().equals(straggler) ? ", straggler" : ""));
                profileText(child.second().asRec(), false).lines().forEach(line -> sb.append("  ").append(line).append('\n'));
            });
        return sb.toString();
    }

    @Override
    public void close() {
        // do nothing
    }

    @Override
    public Set<Sugar> sugars() {
        return new LinkedHashSet<>(List.of(
                Sugar.prefix("%==", List.of(GROUP_INST_TID), 1),
                Sugar.prefix("==", List.of(SELECT_INST_TID), 1),
                Sugar.prefix("??", List.of(SORTA_INST_TID), 1),
                Sugar.prefix("?~", List.of(IS_INST_TID, SORTA_INST_TID), 1),
                Sugar.prefix("?=", List.of(IS_INST_TID, EQ_INST_TID), 1),
                Sugar.prefix("?>=", List.of(IS_INST_TID, GTE_INST_TID), 1),
                Sugar.prefix("?>", List.of(IS_INST_TID, GT_INST_TID), 1),
                Sugar.prefix("?<=", List.of(IS_INST_TID, LTE_INST_TID), 1),
                Sugar.prefix("?<", List.of(IS_INST_TID, LT_INST_TID), 1),
                Sugar.prefix("?!=", List.of(IS_INST_TID, NEQ_INST_TID), 1),
                Sugar.prefix("?", List.of(ISA_INST_TID), 1),
                Sugar.prefix("!@", List.of(AUTO_AT_INST_TID), 1),
                Sugar.prefix("@", List.of(AT_INST_TID), 1),
                Sugar.prefix("|", List.of(BLOCK_INST_TID), 1),
                Sugar.wrap("_/", "\\_", List.of(WITHIN_INST_TID), 1),
                // Sugar.wrap("=", "=>", List.of(AS_INST_TID), 1),  // TODO: gut
                Sugar.prefix("_", List.of(ID_INST_TID), 0),
                Sugar.prefix("* ", List.of(MULT_INST_TID), 1),
                Sugar.prefix("*", List.of(FROM_INST_TID), 1),
                Sugar.prefix(">|", List.of(CASCADE_INST_TID), 1),
                Sugar.prefix(">|", List.of(CASCADE_INST_TID), 0),
                // Sugar.prefix(">|", List.of(BARRIER_INST_TID), 0),
                Sugar.prefix(">-", List.of(MERGE_INST_TID), 1),
                Sugar.prefix(">-", List.of(MERGE_INST_TID), 0),
                Sugar.prefix("-<|", List.of(CHOOSE_INST_TID), 1),
                Sugar.prefix("-<", List.of(SPLIT_INST_TID), 1),
                Sugar.prefix("-<", List.of(SPLIT_INST_TID), 0),
                Sugar.prefix("->", List.of(REF_INST_TID), 1),
                Sugar.prefix(">>=", List.of(UPDATE_INST_TID), 1),
                Sugar.prefix(">>", List.of(RSHIFT_INST_TID), 1),
                Sugar.prefix(">>", List.of(RSHIFT_INST_TID), 0),
                Sugar.prefix("<<", List.of(LSHIFT_INST_TID), 1),
                Sugar.prefix("<<", List.of(LSHIFT_INST_TID), 0),
                Sugar.prefix("++", List.of(MPLUS_INST_TID), 1), // TODO: gut
                Sugar.prefix("+", List.of(PLUS_INST_TID), 1),
                Sugar.prefix("-", List.of(MINUS_INST_TID), 1),
                Sugar.prefix(";", List.of(END_INST_TID), 0),
                //Sugar.prefix("=", List.of(EQ_INST_TID), 1),
                //  Sugar.wrap("(", ")", List.of(GET_INST_TID), 1),
                //Sugar.prefix("./", List.of(GET_INST_TID), 1),
                // Sugar.prefix("^*", List.of(M_ISA_INST_TID.extend("auto_to")), 0),
                Sugar.prefix("!*", List.of(AUTO_FROM_INST_TID), 1),
                Sugar.prefix("!", List.of(AUTO_INST_TID), 1),
                // Sugar.prefix("~", List.of(THREAD_INST_TID), 1),
                Sugar.infix(" & ", List.of(AND_INST_TID)),
                Sugar.infix(" | ", List.of(OR_INST_TID))));
    }

    static {
        // EVERY CARRIER'S ALGEBRA IS DECLARED AT ITS TYPE (mTypes.declareAlgebras) — the theory instances it
        // models, with !* pointers to the live operations. Invoked from this static block, not from a field
        // initializer in mTypes, because the recs name the inst tids above and the theory tids below: a field
        // there would close a class-initialisation cycle. Staged here, and published to ?decq on first read (the
        // q-proc's lazy hook).
        mTypes.declareAlgebras();
        // A THEORY'S LAW BATTERY AND RULES ARE NOT DECLARED HERE: they are the theory's own data, stated in its
        // type (THEORY_TYPE's javadoc), so nothing is staged for /m/theory/*?decq.
    }

}