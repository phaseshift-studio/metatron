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
import studio.phaseshift.metatron.Tracer;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.algebra.Law;
import studio.phaseshift.metatron.algebra.rewrite.RewriterBuilder;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.Sugar;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.resolver.ScoringResolver;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.rewriter.FixPointRewriter;
import studio.phaseshift.metatron.isa.m.mach.type.processor.SwarmProcessor;
import studio.phaseshift.metatron.isa.m.mach.type.processor.monad.StatefulMonad;
import studio.phaseshift.metatron.isa.m.math.cat.LawTable;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.InstBuilder;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.Tuple;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.QProc.QPROC_TID;
import static studio.phaseshift.metatron.furi.QProc.QPROC_TYPE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MILLIS_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
import static studio.phaseshift.metatron.isa.m.type.Fail.FAIL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
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
    // (object::T>>law, morphism::T.law); it does not own them.
    public static final fURI THEORY_TID = M_ISA_TID.extend("theory");
    public static final fURI RING_THEORY_TID = THEORY_TID.extend("ring_theory");
    public static final fURI GROUP_THEORY_TID = THEORY_TID.extend("group_theory");
    public static final fURI MONOID_THEORY_TID = THEORY_TID.extend("monoid_theory");
    public static final fURI COMMUTATIVE_MONOID_THEORY_TID = THEORY_TID.extend("commutative_monoid");
    public static final fURI FIELD_THEORY_TID = THEORY_TID.extend("field_theory");
    public static final fURI RIG_THEORY_TID = THEORY_TID.extend("rig_theory");
    public static final fURI BOOLEAN_THEORY_TID = THEORY_TID.extend("boolean_theory");
    public static final fURI LATTICE_THEORY_TID = THEORY_TID.extend("lattice_theory");
    public static final fURI SEMILATTICE_THEORY_TID = THEORY_TID.extend("semilattice_theory");
    public static final fURI NEAR_RING_THEORY_TID = THEORY_TID.extend("near_ring_theory");

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

    public static final fURI LIKE_INST_TID = M_ISA_INST_TID.extend("like");
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
    public static final fURI MAPP_INST_TID = M_ISA_INST_TID.extend("mapp");
    public static final fURI PARENT_INST_TID = M_ISA_INST_TID.extend("parent");
    public static final fURI FILTER_INST_TID = M_ISA_INST_TID.extend("filter");
    public static final fURI SIDE_INST_TID = M_ISA_INST_TID.extend("side");
    public static final fURI TO_INST_TID = M_ISA_INST_TID.extend("to");
    public static final fURI FROM_INST_TID = M_ISA_INST_TID.extend("from");
    public static final fURI REF_INST_TID = M_ISA_INST_TID.extend("ref");
    public static final fURI BRANCH_INST_TID = M_ISA_INST_TID.extend("branch"); // -<[]>-
    public static final fURI SPLIT_INST_TID = M_ISA_INST_TID.extend("split"); // -<
    public static final fURI CHOOSE_INST_TID = M_ISA_INST_TID.extend("choose"); // -<|
    public static final fURI MERGE_INST_TID = M_ISA_INST_TID.extend("merge");
    public static final fURI COMPOSE_INST_TID = M_ISA_INST_TID.extend("compose"); // a·b — serial composition (a then b)
    public static final fURI FILL_TID = M_ISA_INST_TID.extend("fill");
    public static final fURI FIND_TID = M_ISA_INST_TID.extend("find");
    public static final fURI RMERGE_TID = M_ISA_INST_TID.extend("rmerge");
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
    public static final fURI MATCHES_INST_TID = M_ISA_INST_TID.extend("matches");
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
    public static final fURI CASCADE_INST_TID = M_ISA_INST_TID.extend("cascade");
    public static final fURI REMOVE_INST_TID = M_ISA_INST_TID.extend("remove");
    public static final fURI UPDATE_INST_TID = M_ISA_INST_TID.extend("update");
    public static final fURI GROUP_INST_TID = M_ISA_INST_TID.extend("group");
    public static final fURI ELSE_INST_TID = M_ISA_INST_TID.extend("else");
    public static final fURI END_INST_TID = M_ISA_INST_TID.extend("end");
    //  public static final fURI THREAD_INST_TID = M_ISA_INST_TID.extend("thread");
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
    public static final fURI TYPER_TYPE_TID = f("/m/sys/typer");
    public static final fURI REWRITER_TYPE_TID = f("/m/sys/rewriter");
    public static final fURI TRACER_TYPE_TID = f("/m/sys/tracer");
    /// ////////////
    /// ////////////
    public static final fURI POLY_TID = M_ISA_TID.extend("poly");
    public static final fURI MONO_TID = M_ISA_TID.extend("mono");
    public static final fURI NUM_TID = M_ISA_TID.extend("num");

    public static final fURI MEM_SPACE_TID = M_ISA_TID.extend("space").extend("memspace");
    public static Type MEM_SPACE_TYPE;
    public static final fURI ESTORE_SPACE_TID = M_ISA_TID.extend("space").extend("estorespace");
    public static Type ESTORE_SPACE_TYPE;
    public static Type REGEX_TYPE;
    public static final fURI REGEX_TID = STR_TID.extend("rx");

    public static final Type ALL_TYPE = Type.Builder.build().tid(ALL).vid(ALL).create();
    public static final Type ALL_MAYBE_TYPE = Type.Builder.build().tid(ALL.maybe()).vid(ALL.maybe()).create();
    public static final Type SPACE_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(SPACE_TID)
            .isaPredicate(rec(
                    uri(PATTERN), URI_TYPE,
                    uri(QPROC).maybe(), lst(QPROC_TYPE.maybe().asType()),
                    uri(ROUTE).maybe(), rec(T(URI_TID.maybe()), URI_TYPE),
                    uri(SCHEMA).maybe(), T(INSTSET_TID), // nominal-only: avoids structural recursion in InstSet
                    uri(ON_CLOSE).maybe(), ALL_TYPE)).create();

    public static final Type REWRITER_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(REWRITER_TYPE_TID)
            .isaPredicate(rec(URI_TYPE, T(LST_TID.poly(M_ISA_INST_TID))))
            .create();
    public static final Type TYPER_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(TYPER_TYPE_TID)
            .isaPredicate(rec(
                    uri(TypeCheck.inst_dom.name()), BOOL_TYPE,
                    uri(TypeCheck.inst_rng.name()), BOOL_TYPE,
                    uri(TypeCheck.type_pred.name()), BOOL_TYPE,
                    uri(TypeCheck.code_resolve.name()), BOOL_TYPE,
                    uri(TypeCheck.obj_write.name()), BOOL_TYPE))
            .constructor(stages -> rec(
                    uri(TypeCheck.inst_dom.name()), stages.asRec().at(uri(TypeCheck.inst_dom.name())).orElse(BOOL_FALSE),
                    uri(TypeCheck.inst_rng.name()), stages.asRec().at(uri(TypeCheck.inst_rng.name())).orElse(BOOL_FALSE),
                    uri(TypeCheck.type_pred.name()), stages.asRec().at(uri(TypeCheck.type_pred.name())).orElse(BOOL_FALSE),
                    uri(TypeCheck.code_resolve.name()), stages.asRec().at(uri(TypeCheck.code_resolve.name())).orElse(BOOL_FALSE),
                    uri(TypeCheck.obj_write.name()), stages.asRec().at(uri(TypeCheck.obj_write.name())).orElse(BOOL_FALSE))).create();
    public static final Type TRACER_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(TRACER_TYPE_TID)
            .isaPredicate(rec(uri("stack"), rec(
                    uri(Tracer.mtron_stack.name()).maybe().asUri(), BOOL_TYPE,
                    uri(Tracer.java_stack.name()).maybe(), BOOL_TYPE)))
            .constructor(stacks -> rec(uri("stack"), rec(
                    uri(Tracer.mtron_stack.name()), stacks.asRec().at(uri(Tracer.mtron_stack.name())).orElse(BOOL_TRUE),
                    uri(Tracer.java_stack.name()), stacks.asRec().at(uri(Tracer.java_stack.name())).orElse(BOOL_FALSE))))
            .create();

    public static final fURI EXPLANATION_TID = EXPLAIN_INST_TID.extend("explanation");
    public static final Type EXPLANATION_TYPE = Type.Builder.build()
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
                            uri(C_DOM), INT_TYPE,
                            uri(C_RNG), INT_TYPE))))
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
    public static final Type PROFILING_TYPE = Type.Builder.build()
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
                    T(PATH_ENCODING_TID.maybe()), T(PROFILING_TID.maybe())))
            .create();
    
   /* public static final Type MONO_TYPE = Type.Builder.build()
            .tid(MONO_TID)
            .vid(MONO_TID)
            .predicate((lhs, inst) -> bool(lhs.isBytes() || lhs.isBool() || lhs.isInt() || lhs.isReal() || lhs.isStr() || lhs.isUri() || lhs.isObjInst()))
            .create();*/

   /* public static final Type NUM_TYPE = Type.Builder.build()
            .tid(NUM_TID)
            .vid(NUM_TID)
            .predicate((lhs, inst) -> bool(lhs.isInt() || lhs.isReal()))
            .create();*/

    /* public static final Type POLY_TYPE = Type.Builder.build()
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
    private static final AtomicReference<List<Inst>> PLUS_ZERO_MATCH = new AtomicReference<>();
    private static final AtomicReference<List<Inst>> MULT_ONE_MATCH = new AtomicReference<>();

    private static List<Inst> cachedMatch(final AtomicReference<List<Inst>> ref, final java.util.function.Supplier<List<Inst>> build) {
        final List<Inst> existing = ref.get();
        if (null != existing)
            return existing;
        ref.compareAndSet(null, build.get());
        return ref.get();
    }

    public void setup() {
        this.selfTID(INSTSET_TID);
        this.jvm().putAll(new LinkedHashMap<>(Map.of(
                uri(PATTERN), uri(M_ISA_TID.extend(ALL)),
                uri(TYPE), lst(
                        //  docWrap(MONO_TYPE, "an atomic obj"),
                        //  docWrap(POLY_TYPE, "a obj composed of other objs"),
                        //  docWrap(NOOBJ_TYPE, "a no object"),
                        docWrap(ALL_TYPE, "universal type matching all objs within coefficient range",
                                "abc.matches(#::T)              [-- true --]",
                                "12.3.matches(#::T)             [-- true --]",
                                "{2}'a string'.matches(#::T)    [-- false --]",
                                "{2}'b string'.matches(#{2}::T) [-- true --]",
                                "{2}'c string'.matches(#{+}::T) [-- true --]"),
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
                        docWrap(INSTSET_TYPE,
                                "an extension of space with structural requirements regarding obj construction",
                                "creates the instset and registers it with the global router",
                                mutableMap(
                                        uri(CONST).maybe(), "constants used across the instset",
                                        uri(TYPE).maybe(), "types used to structure objs of the instset",
                                        uri(INST).maybe(), "instructions associated with the types of the instset",
                                        uri(REWRITE).maybe(), "code<=code instructions that capture the algebraic equalities of the instset",
                                        uri(SUGAR).maybe(), "custom instruction syntax sugars given to the parser"),
                                "an aggregate of consts, types, insts, rewrites, and sugars structuring a domain of discourse"),
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
                        docWrap(TYPER_TYPE, null, null, mutableMap(
                                        uri(TypeCheck.inst_dom.name()), "ensure lhs obj matches instruction domain",
                                        uri(TypeCheck.inst_rng.name()), "ensure rhs obj matches instruction range",
                                        uri(TypeCheck.type_pred.name()), "ensure type constructor argument matches type predicate",
                                        uri(TypeCheck.obj_write.name()), "ensure obj matches type on space write",
                                        uri(TypeCheck.code_resolve.name()), "ensure only fully resolved code can be executed"),
                                """
                                        stages where the type checker should be applied.
                                        the more stages that are active, the slower instructions evaluate.
                                        however, more active stages reduces potential for data corruption.
                                        typically use many stages when designing code and once stable,
                                        remove stages accordingly for increased performance.
                                        """),
                        docWrap(TRACER_TYPE, null, null, mutableMap(
                                        uri(Tracer.mtron_stack.name()), "render mtron stack traces on uncaught fail::T",
                                        uri(Tracer.java_stack.name()), "render java stack traces on uncaught fail::T"),
                                """
                                        diagnostic stages for surfacing internal java state.
                                        useful for debugging native java issues that mtron instructions
                                        trigger but cannot introspect.
                                        """),
                        docWrap(PROFILING_TYPE,
                                Map.of(
                                        uri(FORMAT), "lazily constructed pretty print format",
                                        uri(STAGE), "per-pipeline-stage min/max wall-clock timings (rewrite/resolve/apply)",
                                        uri(INSTS), "the resolved instruction count",
                                        uri(RESOLVE), "resolve sub-stages (inst-resolve/generic-binding/inst-composition)",
                                        uri(REWRITE), "per-rewrite-rule instruction reduction (rules + net total)",
                                        uri(APPLY), "processor sub-stages (split/apply/next)",
                                        uri(PER_INST), "per-instruction monad/coefficient flow + time",
                                        uri(FLOW), "bulk-compression flow (monads propagated vs coefficient sum)",
                                        uri(CACHE), "type-graph cache hit/miss/hit-rate"),
                                "a structured profile report produced by profile()",
                                "{1,2,3}.sum().profile()>>format    [-- the str::T text table --]",
                                "{1,2,3}.sum().profile()>>flow      [-- [monads=>…,coeff_sum=>…,compression=>…,processors=>…] --]"),
                        docWrap(EXPLANATION_TYPE,
                                Map.of(
                                        uri(FORMAT), "lazily constructed pretty-print format (the current text table) — a lazy inst, materialized interactively",
                                        uri(DESC), "expression-level metadata of the COMPILED chain: whole-expression dom/rng, inst count, and the rewrite chain — desc.rewrite is the SUBMITTED expression's explanation ({?}), which terminates the chain (noobj)",
                                        uri(PER_INST), "lst of per-instruction stage recs (op/dom/rng/args/f/form/c_dom/c_rng) — args are the real objs, not their string renderings; f is the wrapped function ('<j>' = java/opaque)"),
                                "a structured explanation of a resolved expression produced by explain()",
                                "1.plus(2).explain()>>desc>>rng     [-- int — the type flowing out of the expression --]",
                                "1.plus(2).explain()>>per_inst      [-- per-inst recs: [op=>start,args=>[1],form=>initial,c_dom=>0,c_rng=>1], [op=>plus,dom=>int,rng=>int,args=>[2],f=>'<j>',form=>mapper,c_dom=>1,c_rng=>1] --]",
                                "1.plus(2).explain()>>format         [-- the str::T text table --]",
                                "6.plus(0).explain()>>desc>>insts    [-- 1 — the reported chain is the COMPILED one, so the unit law already dropped plus(0) --]",
                                "6.plus(0).explain()>>desc>>rewrite  [-- the SUBMITTED rec: >>insts is 2 (start, plus), the expression as written --]"),
                        docWrap(SPACE_TYPE, null, null, Map.of(
                                        uri(PATTERN), "the uri address region the space will manage",
                                        uri(QPROC).maybe(), "query processors (qproc) that augment space capabilities",
                                        uri(ROUTE).maybe(), "a secondary router after global router for inter and intra-space redirects",
                                        uri(SCHEME).maybe(), "an instset defining the type structure of the space objs",
                                        uri(ON_CLOSE).maybe(), "an optional shutdown down hook"),
                                "storage systems structured as uri addressed objs",
                                "memspace::[pattern=>/usr/marko/#,q=>[mimeq::[=>]]]@/sys/space/usr/marko"),
                        docWrap(MEM_SPACE_TYPE = Type.Builder.build()
                                        .tid(SPACE_TID)
                                        .vid(MEM_SPACE_TID)
                                        // .isaPredicate(rec(uri(DATA).maybe().asUri(), URI_TYPE).maybe())
                                        .constructor(
                                                instC(INST_CTOR_TID.rng(MEM_SPACE_TID),
                                                        lst(isa_(REC_TYPE).tryToInst()),
                                                        (lhs, inst) -> memSpace.of(inst.arg(0).asRec(), inst.arg(0).vid()))).create(), "", "",
                                Map.of(uri(DATA).maybe(), "a file location to save space state (reads on creation and writes on close)"),
                                "an in-memory space with objs indexed by a topic trie"),
                        /*docWrap(ESTORE_SPACE_TYPE = Type.Builder.build()
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
                        docWrap(REGEX_TYPE = Type.Builder.build().tid(STR_TID).vid(REGEX_TID).create(), "a str refined to a regex pattern"),
                        /// ///////////////////////////////////
                        SUBQ_TYPE = docWrap(Type.Builder.build()
                                        .tid(QPROC_TID)
                                        .vid(SUBQ_TID)
                                        .isaPredicate(rec(uri(SUB).maybe().asUri(), rec(T(URI_TID.maybe()), SUBQ_TYPE)))
                                        .constructor(QCollection::subQ)
                                        .create(), "", "",
                                Map.of(uri(SUB).maybe().asUri(), "subscriptions to register immediately upon construction"),
                                """
                                        uri publish-subscribe qproc.
                                        when writing an inst to a subq uri, be sure to |-block prefix 
                                        so as to store the inst and not its evaluation in the assignment.
                                        """,
                                "/usr/ai/#?subq -> sub::[code=>print('ai update: ${_}')] [-- /usr/ai subtree watch  --]",
                                "a?subq         -> sub::[code=>>>1+1.println(_).to(a)]   [-- infinite incr loop     --]",
                                "*tree?subq                                              [-- all subs for tree      --]",
                                "*tree/#?subq                                            [-- all subs for all tree  --]"),
                        SUB_TYPE,
                        PUB_TYPE,
                        LOCK_TYPE,
                        docWrap(TYPEQ_TYPE, "addr type constraint qproc",
                                "abc?typeq -> int::T       [-- abc can only reference a single integer --]",
                                "abc?typeq -> 'not an int' [-- yields a fail::T --]"),
                        docWrap(DOCS_TYPE, "a documentation structure to attach to objs and access via docq query processor"),
                        docWrap(DOCQ_TYPE, "addr documentation qproc",
                                "*docq?docq [-- this documentation --]",
                                "*int?docq  [-- documentation for int::T --]"),
                        docWrap(MINTQ_TYPE, "mint a unique uri extension to obj vid",
                                "1@abc?mintq [-- 1@abc/235ae3 --]"),
                        docWrap(SHORTQ_TYPE, "create an untyped smaller representation of obj referent",
                                "*abc?shortq=10 [-- optional value is max length of obj components (default " + DEFAULT_SHORTQ_MAX_LENGTH + ") --]"),
                        docWrap(SAFEQ_TYPE, "warns when the space is being written to"),
                        docWrap(INCRQ_TYPE, "internal counter increments and appends value to vid"),
                        docWrap(EMBEDQ_TYPE, "either store and retrieve obj's vector embedding"),
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
                        mInsts.code_insts().stream(),
                        mInsts.objs_insts().stream(),
                        Int.IntType.insts().stream(),
                        Real.RealType.insts().stream(),
                        Str.StrType.insts().stream(),
                        Uri.UriType.insts().stream(),
                        Inst.InstType.insts().stream(),
                        Rel.RelType.insts().stream(),
                        Lst.LstType.insts().stream(),
                        RecType.insts().stream(),
                        Code.CodeType.insts().stream(),
                        Fail.FailType.insts().stream(),
                        //  Objs.ObjsType.insts().stream(),
                        SpaceType.insts().stream(),
                        ObjType.insts().stream(),
                        NoObj.NoObjType.insts().stream(),
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
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("profile_analysis").addQ("analysis"),
                                code -> {
                                    final List<Inst> insts = code.insts();
                                    if (insts.isEmpty() || insts.size() < 2) return code;
                                    final Tuple.Pair<Integer, Inst> profileInst = IteratorUtil.indexedStream(insts.iterator()).filter(i -> i.get1().tid().basePath().equals(PROFILE_INST_TID)).findAny().orElse(null);
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
                                    bundledCode.add(profileAnalysis(precedingCode));
                                    if (profileInst.get0() + 1 < insts.size()) {
                                        final Inst capInst = insts.get(profileInst.get0() + 1);
                                        bundledCode.add(capInst.dom(PROFILING_TYPE));
                                        if (profileInst.get0() + 2 < insts.size()) {
                                            bundledCode.addAll(insts.subList(profileInst.get0() + 2, profileInst.get0() + 3));
                                        }
                                    }
                                    return code.jvm(bundledCode);
                                }), "rewrites a().b().c().profile() to profile_analysis(a().b().c()): \\(\\mathrm{profile} \\leadsto \\mathrm{profile\\_analysis}(\\ldots)\\)"),

                        // Remove identity instructions (no-op)
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("id_removal"),
                                code -> 1 == code.insts().size() ? code : code.selfJVM(
                                        RewriterBuilder.search(code.insts())
                                                .match(instA(ID_INST_TID).insts())
                                                .rewrite(x -> List.of())).asCode()), "removes identity instructions: \\(g \\cdot \\mathrm{id} \\leadsto g\\)"),

                        // NOTE: map_nest / map_inst / plus_zero / mult_one have been removed — they are the
                        // syntactic shadows of catInstSet's `form_map_unwrap` (the transparent `map` lift)
                        // and `theory_unit_removal` (op(id) for the operand's declared algebra). id_removal
                        // stays: the category is only present when `/m/math/cat` is imported, and the identity
                        // instruction has to be dropped on the base path too (testSplitMergeCode needs it).

                        // Eliminate else() after non-maybe instruction (dead code)
                        // Pattern: .count().else(x) → .count() (count always returns a value)
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("else_after_count"),
                                code -> code.selfJVM(
                                        RewriterBuilder.search(code.insts())
                                                .match(List.of(instA(COUNT_INST_TID), instA(ELSE_INST_TID)))
                                                .rewrite(map -> {
                                                    final List<Inst> matched = map.values().stream().toList();
                                                    // COUNT always returns int, so ELSE is dead code
                                                    return List.of(matched.getFirst());
                                                })).asCode()), "removes the else following a count — \\(\\mathrm{count}\\) is total, so the fallback is dead code: \\(\\mathrm{count}(x) \\cdot \\mathrm{else}(y) \\leadsto \\mathrm{count}(x)\\)"),
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

                        // plus_zero / mult_one are gone — `plus(0)` and `mult(1)` are exactly the unit
                        // patterns of the operand's declared ring/monoid, which is what catInstSet's
                        // `theory_unit_removal` removes (and it does so only when the type actually declares
                        // that identity, which is why the syntactic versions had to be disabled).

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
                                                })).asCode()), "applies the abelian monoid law on split code paths: \\(\\mathrm{split}([f, \\ldots, f]) \\cdot \\mathrm{merge} \\leadsto f\\{\\sum_i c_i\\}\\)"),

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
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("range_skip_take"),
                                code -> code.selfJVM(
                                        RewriterBuilder.search(code.asCode().insts())
                                                .match(List.of(instA(RANGE_INST_TID)))
                                                .repeat()
                                                .rewrite(map -> {
                                                    final List<Inst> matched = map.values().stream().toList();
                                                    final Inst rangeInst = matched.getFirst();
                                                    return Stream.of(
                                                            instB(SKIP_INST_TID, lst(rangeInst.arg(0))),
                                                            instB(TAKE_INST_TID, lst(jnt(rangeInst.arg(1).intValue() - rangeInst.arg(0).intValue())))).toList();
                                                })).asCode()), "rewrites a range inst to skip/take: \\(\\mathrm{range}(i, n) \\leadsto \\mathrm{skip}(i) \\cdot \\mathrm{take}(n - i)\\)"),

                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("explain_analysis").addQ("analysis"),
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
                                }), "rewrites a().b().c().explain() to explain_analysis(a().b().c()), carrying trailing insts (e.g. >>format) — \\(\\mathrm{explain} \\leadsto \\mathrm{explain\\_analysis}(\\ldots)\\), mirror of profile_analysis"))

                /*uri(SUGAR), lst(sugars().stream()
                        .map(s -> rec(
                                START, null == s.getStartToken() ? noobj() : str(s.getStartToken()),
                                END, null == s.getEndToken() ? noobj() : str(s.getEndToken()),
                                ARGS, jnt(s.getArgCount()),
                                PATTERN, s.getInstChain().stream().map(fURI::toUri).collect(new CommonUtil.LstCollector()))))*/)));
        docWrap(this, "the core instruction set of metatron containing the base types and useful instructions to manipulate them");
        super.setup();
    }


    /**
     * Build a column-justified text table of the instructions in {@code code}.
     * Terminal-free — suitable for use in rewrites and non-interactive contexts.
     */
    private static String explainTable(final Code code) {
        final java.util.List<String> headers = java.util.List.of(
                "op", "dom", "rng", "args", "f", "desc", "c_dom", "c_rng");
        final java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();
        rows.add(headers);
        for (final Inst i : code.insts()) {
            rows.add(java.util.List.of(
                    i.tid().name() + (i.tid().c().isOne() ? "" : ("{" + i.tid().c() + "}")),
                    i.dom().vid().small() + "::T",
                    i.rng().vid().small() + "::T",
                    i.args().elements()
                            .map(o -> o.isCall() ? o.asCall().insts().stream()
                                    .map(x -> x.tid().name())
                                    .reduce((a, b) -> a + "." + b).orElse("") : o.toShortString())
                            .reduce((a, b) -> a + "," + b).orElse(""),
                    i.hasf() ? (i.f().isLambda() ? "<j>" : "<m>") : "<?>",
                    studio.phaseshift.metatron.isa.m.type.Inst.Form.of(i).toString(),
                    "{" + i.dom().c() + "}",
                    "{" + i.rng().c() + "}"));
        }
        final int cols = headers.size();
        final int[] widths = new int[cols];
        for (final java.util.List<String> row : rows) {
            for (int c = 0; c < cols; c++) {
                widths[c] = Math.max(widths[c], row.get(c).length());
            }
        }
        final StringBuilder sb = new StringBuilder("\n");
        for (int r = 0; r < rows.size(); r++) {
            final java.util.List<String> row = rows.get(r);
            for (int c = 0; c < cols; c++) {
                final String cell = row.get(c);
                sb.append(String.format(" %-" + widths[c] + "s ", cell));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * The dom/rng value of a stage rec: a zero-coefficient (noobj) type yields noobj itself,
     * otherwise the type's vid as a uri. (a noobj{c} uri and a noobj literal parse to different
     * classes — noobj-vs-noobj is the one equivalence test literals can express)
     */
    private static Obj domRngValue(final Type t) {
        if (0L == t.c().min()) return noobj();
        return uri(f(t.vid().small().pathString()));
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
        for (final Inst i : insts) {
            perInstRecs.add(rec(
                    uri(OP), uri(f(i.tid().name())),
                    uri(DOM), domRngValue(i.dom()),
                    uri(RNG), domRngValue(i.rng()),
                    // the args as their own objs (ints, calls, ...), not their string renderings
                    uri(ARGS), lst(i.args().elements().toList()),
                    uri(Tokens.F), i.hasf() ? (i.f().isLambda() ? str("<j>") : i.getMtronFunctionObj()) : noobj(),
                    uri(FORM), uri(f(Inst.Form.of(i).toString())),
                    uri(C_DOM), jnt(i.dom().c().min()),
                    uri(C_RNG), jnt(i.rng().c().min())));
        }
        // rewrite-stage chain: this rec describes the COMPILED code, so it points BACK at the submitted
        // expression's rec; that rec is the chain's last and terminates it (noobj). explain() therefore
        // returns the HEAD of the chain — the stage that runs — and >>desc>>rewrite walks to what was written.
        final Obj submittedRec = null == submitted ? noobj() : explainRec(submitted, null);
        return rec(
                mutableMap(uri(FORMAT), auto_(instLambda(ALL.maybe(), STR_TID, (lhs, inst) -> str(explainTable(code)))),
                        uri(DESC), rec(
                                uri(DOM), domRngValue(insts.getFirst().dom()),
                                uri(RNG), domRngValue(insts.getLast().rng()),
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
                .body((lhs, inst) -> profileTable(preceding))
                .law(Law.monoidic, Law.commutative, Law.pure)
                // THE FORM IS DECLARED, not derived: Form.of reads a many-to-one dom as a reducer, and this
                // inst's dom is deliberately `maybe` (a bulk dom made it answer two reports). Declared so
                // LawTable.combiner's FORM gate lets the declared laws be consulted — without it the inst
                // classifies as a mapper and declaresAll is never reached.
                .form(Inst.Form.reducer)
                .docDesc("profile() is a MAKER: this is the instruction it mints around the captured program. A FOLD and a BARRIER — it aggregates every monad up to the marker and answers one compressed profile::profiling rec, so its laws are the ones a shard's report needs in order to recombine at the master: monoidic (the partials regroup), commutative (a collation is order-free) and pure (it observes, it does not mutate). The program is captured by closure, never as an argument, so the outer resolve cannot collapse the instructions being timed.")
                .create();
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
     * THE COLLATION INSTRUCTION — {@code profile_collate()}: the ⊕ a DISTRIBUTED profile needs at the master,
     * and the reason {@code profiling::T} is recursive.
     * <p>
     * It takes the multiplicity of reports (the shards' answers plus this machine's own) and answers ONE rec
     * indexed by where each report was made: {@code <path-encoded machine> => profiling::T}, with this
     * machine's own at {@code master}. Keyed, never positional — a reader must be able to say WHICH machine
     * owns a number without reconstructing the fan-out order, and the topologies differ (a star's flat
     * fan-out, a tree's hops, a divide-and-conquer plan), so the structure has to be self-describing rather
     * than implied by the layout.
     * <p>
     * THE KEY IS THE PATH ENCODING ({@link #PATH_ENCODING_TID}): {@code ws/localhost/1112/mtron} is one
     * addressable segment, {@code ws://localhost:1112/mtron} is not a key at all.
     * <p>
     * MONOIDIC + COMMUTATIVE + PURE for the same reason the other two profiling insts are: the reports
     * regroup across lanes and machines, the index does not depend on arrival order, and collation observes.
     * The master's own row is the one NO shard can supply — its {@code apply} stage is the gather wait — which
     * is why it must be identified and kept rather than summed away.
     */
    public static final fURI PROFILE_COLLATE_TID = M_ISA_INST_TID.extend("profile_collate");

    /**
     * Mint the collation inst. Deliberately NOT registered as an instruction of the ISA: like
     * {@link #profileAnalysis(Code)} it is a MAKER's product, emitted by the distribution plan where a shard
     * ends in {@code profile()} and the master has reports instead of partials.
     */
    public static Inst profileCollate() {
        return InstBuilder.build()
                .tid(PROFILE_COLLATE_TID, "index the gathered profiling::T reports by the machine each was made on")
                .dom(ALL.maybeSome(), "the profiling::T reports the shards answered, plus this machine's own")
                .rng(REC_TID, "one rec: <path-encoded machine> => profiling::T, this machine's own at `master`")
                .form(Inst.Form.reducer)
                .law(Law.monoidic, Law.commutative, Law.pure)
                .body((lhs, inst) -> {
                    final Obj here = machineIdentity();
                    final Map<Obj, Obj> rows = new LinkedHashMap<>();
                    lhs.stream().filter(Obj::isRec).forEach(report -> {
                        final Obj where = report.asRec().at(uri(MACHINE));
                        // THE MASTER IS NAMED, not inferred from arrival order: `machine == here` is the one
                        // cheap test, and a report that names no machine is the local one by construction.
                        rows.put(!where.isUri() || where.equals(here)
                                        ? uri("master")
                                        : uri(where.uriValue().pathEncoding()),
                                report);
                    });
                    return rec(rows);
                })
                .docDesc("the master's half of a distributed profile: index the gathered profiling::T reports by the machine that made them (path-encoded), keeping this machine's own at `master`. The counterpart of profile(), which folds a LOCAL run into one report; this folds the REPORTS into one index. Same laws, different arity — and the recursion in profiling::T is what lets a report hold the reports of the places below it, so a star, a tree and a divide-and-conquer plan all describe themselves the same way.")
                .create();
    }

    private static Obj machineIdentity() {
        final Machine machine = Machine.current();
        final fURI vid = machine.vid();
        if (null == vid)
            return noobj();
        final Obj host = machine.network().at(uri(HOST));
        final Obj composed = host.isUri() ? uri(host.uriValue().extend(vid)) : uri(vid);
        // THE PROCESS ID IS PART OF THE IDENTITY, because host+frame is a PLACE, not a VM. Every peer forked on
        // one host is a root machine on the same OS host, so they all answer `<host>/.` -- a collated profile
        // then cannot tell a shard's report from the master's, `merge` BULKS the two indistinguishable values
        // and the index collapses to one row labelled `master`. Measured: two local peers produced
        // [master => profiling::[insts=>3]] where insts=3 was a SLAVE's chain.
        // The pid rides the PATH, not a `?q`: the collation key is pathEncoding(), which renders the path.
        return uri(composed.uriValue().toString() + "/" + ProcessHandle.current().pid());
    }

    private static Rec profileTable(final Code code) {
        // snapshot the pre-collapse insts: Code.rewrite() mutates its input in place (via
        // selfJVM), so the timed loop must rewrite a fresh copy each iteration — the snapshot
        // is what makes the original 7 → 3 reduction measurable.
        final List<Inst> originalInsts = code.insts();
        // warm the resolver + type graph so the timed runs are steady-state
        try {
            MCode.of(new ArrayList<>(originalInsts)).resolve(noobj()).apply(noobj());
        } catch (final Throwable ignored) {
            // cold-start semi-resolution may throw; the timed loop below will surface a real failure
        }
        final TypeGraph graph = Machine.current().memory().typeGraph();
        graph.resetStats();
        ScoringResolver.resetTimings();
        FixPointRewriter.resetRewriteTimings();
        StatefulMonad.resetTimings();
        SwarmProcessor.resetTimings();
        final int iters = 5;
        long resolveMin = Long.MAX_VALUE, resolveMax = 0L;
        long rewriteMin = Long.MAX_VALUE, rewriteMax = 0L;
        long applyMin = Long.MAX_VALUE, applyMax = 0L;
        final AtomicReference<Code> lastRewritten = new AtomicReference<>();
        final AtomicReference<Code> lastResolved = new AtomicReference<>();
        for (int i = 0; i < iters; i++) {
            final long rw0 = System.nanoTime();
            final Code rewritten = MCode.of(new ArrayList<>(originalInsts)).rewrite();
            final long rw1 = System.nanoTime();
            final Code resolved = Machine.current().compiler().resolver().apply(noobj(), rewritten);
            final long r1 = System.nanoTime();
            final long a0 = System.nanoTime();
            resolved.apply(noobj());
            final long a1 = System.nanoTime();
            rewriteMin = Math.min(rewriteMin, rw1 - rw0);
            rewriteMax = Math.max(rewriteMax, rw1 - rw0);
            resolveMin = Math.min(resolveMin, r1 - rw0);
            resolveMax = Math.max(resolveMax, r1 - rw0);
            applyMin = Math.min(applyMin, a1 - a0);
            applyMax = Math.max(applyMax, a1 - a0);
            lastRewritten.set(rewritten);
            lastResolved.set(resolved);
        }
        final Code rewritten = lastRewritten.get();
        final Code resolved = lastResolved.get();
        final long resSum = ScoringResolver.T_RESOLVE.get() + ScoringResolver.T_BIND.get() + ScoringResolver.T_COMPOSE.get();
        final long appSum = StatefulMonad.T_SPLIT.get() + StatefulMonad.T_APPLY.get() + StatefulMonad.T_NEXT.get();
        final StringBuilder sb = new StringBuilder("\n");
        sb.append("  stage     min (ms)   max (ms)\n");
        sb.append(String.format("  rewrite   %8.3f   %8.3f%n", rewriteMin / 1_000_000.0, rewriteMax / 1_000_000.0));
        sb.append(String.format("  resolve   %8.3f   %8.3f%n", resolveMin / 1_000_000.0, resolveMax / 1_000_000.0));
        sb.append(String.format("  apply     %8.3f   %8.3f%n", applyMin / 1_000_000.0, applyMax / 1_000_000.0));
        sb.append(String.format("  insts     %d%n", resolved.insts().size()));
        sb.append("  resolve sub-stages (avg ms):\n");
        sb.append(String.format("    inst-resolve      %8.3f  %5.1f%%%n", ScoringResolver.T_RESOLVE.get() / 1_000_000.0 / iters, 0L == resSum ? 0.0 : ScoringResolver.T_RESOLVE.get() * 100.0 / resSum));
        sb.append(String.format("    generic-binding   %8.3f  %5.1f%%%n", ScoringResolver.T_BIND.get() / 1_000_000.0 / iters, 0L == resSum ? 0.0 : ScoringResolver.T_BIND.get() * 100.0 / resSum));
        sb.append(String.format("    inst-composition  %8.3f  %5.1f%%%n", ScoringResolver.T_COMPOSE.get() / 1_000_000.0 / iters, 0L == resSum ? 0.0 : ScoringResolver.T_COMPOSE.get() * 100.0 / resSum));
        sb.append("  rewrite rules:\n");
        final List<Map.Entry<String, AtomicLong>> rules = new ArrayList<>(FixPointRewriter.REWRITE_TIMINGS.entrySet());
        rules.sort((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()));
        final int nRules = Math.min(6, rules.size());
        sb.append("                ");
        for (int i = 0; i < nRules; i++)
            sb.append(String.format("%-20s", rules.get(i).getKey()));
        sb.append('\n');
        sb.append("      insts.in  ");
        for (int i = 0; i < nRules; i++) {
            final AtomicLong v = FixPointRewriter.REWRITE_INS.get(rules.get(i).getKey());
            sb.append(String.format("%-20d", null == v ? 0L : v.get()));
        }
        sb.append('\n');
        sb.append("      insts.out ");
        for (int i = 0; i < nRules; i++) {
            final AtomicLong v = FixPointRewriter.REWRITE_OUTS.get(rules.get(i).getKey());
            sb.append(String.format("%-20d", null == v ? 0L : v.get()));
        }
        sb.append('\n');
        sb.append("      time(ms)  ");
        for (int i = 0; i < nRules; i++)
            sb.append(String.format("%-20.3f", rules.get(i).getValue().get() / 1_000_000.0));
        sb.append('\n');
        // summary: the net reduction (original → rewritten inst count) and the total rule time.
        long totalRuleTime = 0L;
        for (final AtomicLong v : FixPointRewriter.REWRITE_TIMINGS.values())
            totalRuleTime += v.get();
        sb.append(String.format("      TOTAL     inst.in %d   inst.out %d   time(ms) %.3f%n",
                originalInsts.size(), rewritten.insts().size(), totalRuleTime / 1_000_000.0));
        sb.append("  apply sub-stages (avg ms):\n");
        sb.append(String.format("    split     %8.3f  %5.1f%%%n", StatefulMonad.T_SPLIT.get() / 1_000_000.0 / iters, 0L == appSum ? 0.0 : StatefulMonad.T_SPLIT.get() * 100.0 / appSum));
        sb.append(String.format("    apply     %8.3f  %5.1f%%%n", StatefulMonad.T_APPLY.get() / 1_000_000.0 / iters, 0L == appSum ? 0.0 : StatefulMonad.T_APPLY.get() * 100.0 / appSum));
        sb.append(String.format("    next      %8.3f  %5.1f%%%n", StatefulMonad.T_NEXT.get() / 1_000_000.0 / iters, 0L == appSum ? 0.0 : StatefulMonad.T_NEXT.get() * 100.0 / appSum));
        sb.append("  per-instruction (per iteration):\n");
        final List<Inst> insts = resolved.insts();
        sb.append("                ");
        for (final Inst inst : insts)
            sb.append(String.format("%-10s", inst.tid().name()));
        sb.append('\n');
        sb.append("      in.monad  ");
        for (int i = 0; i < insts.size(); i++) {
            final AtomicLong v = StatefulMonad.INST_MONAD_IN.get(f(String.valueOf(i)));
            sb.append(String.format("%-10d", null == v ? 0L : v.get() / iters));
        }
        sb.append('\n');
        sb.append("      in.coeff  ");
        for (int i = 0; i < insts.size(); i++) {
            final AtomicLong v = StatefulMonad.INST_COEFF_IN.get(f(String.valueOf(i)));
            sb.append(String.format("%-10d", null == v ? 0L : v.get() / iters));
        }
        sb.append('\n');
        sb.append("      out.monad ");
        for (int i = 0; i < insts.size(); i++) {
            final AtomicLong v = StatefulMonad.INST_MONAD_OUT.get(f(String.valueOf(i)));
            sb.append(String.format("%-10d", null == v ? 0L : v.get() / iters));
        }
        sb.append('\n');
        sb.append("      out.coeff ");
        for (int i = 0; i < insts.size(); i++) {
            final AtomicLong v = StatefulMonad.INST_COEFF_OUT.get(f(String.valueOf(i)));
            sb.append(String.format("%-10d", null == v ? 0L : v.get() / iters));
        }
        sb.append('\n');
        sb.append("      time(ms)  ");
        for (int i = 0; i < insts.size(); i++) {
            final AtomicLong v = StatefulMonad.INST_TIME.get(f(String.valueOf(i)));
            sb.append(String.format("%-10.3f", null == v ? 0.0 : v.get() / 1_000_000.0 / iters));
        }
        sb.append('\n');
        final long monads = StatefulMonad.MONADS.get();
        final long coeffSum = StatefulMonad.COEFF_SUM.get();
        sb.append(String.format("  flow       monads=%d coeff-sum=%d compression=%.2f processors=%d%n",
                monads, coeffSum, 0L == monads ? 0.0 : (double) coeffSum / (double) monads, SwarmProcessor.PROCESSORS.get()));
        sb.append(String.format("  cache     hits=%d misses=%d hit=%.1f%%%n", graph.hits(), graph.misses(), graph.hitRate() * 100.0));
        final String text = sb.toString();
        final double ms = 1_000_000.0; // nanos -> millis
        // per-rewrite-rule reduction records, sorted by time (same order as the text table)
        final List<Obj> ruleRecs = FixPointRewriter.REWRITE_TIMINGS.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .map(e -> {
                    final AtomicLong in = FixPointRewriter.REWRITE_INS.get(e.getKey());
                    final AtomicLong out = FixPointRewriter.REWRITE_OUTS.get(e.getKey());
                    return (Obj) rec(
                            uri(NAME), uri(f(e.getKey())),
                            uri(IN), jnt(null == in ? 0L : in.get()),
                            uri(OUT), jnt(null == out ? 0L : out.get()),
                            uri(TIME), real(e.getValue().get() / ms, MILLIS_TYPE.vid(), null));
                }).toList();
        // per-instruction monad/coefficient flow records
        final List<Obj> perInstRecs = new ArrayList<>();
        for (int i = 0; i < insts.size(); i++) {
            final AtomicLong mi = StatefulMonad.INST_MONAD_IN.get(f(String.valueOf(i)));
            final AtomicLong ci = StatefulMonad.INST_COEFF_IN.get(f(String.valueOf(i)));
            final AtomicLong mo = StatefulMonad.INST_MONAD_OUT.get(f(String.valueOf(i)));
            final AtomicLong co = StatefulMonad.INST_COEFF_OUT.get(f(String.valueOf(i)));
            final AtomicLong ti = StatefulMonad.INST_TIME.get(f(String.valueOf(i)));
            perInstRecs.add(rec(
                    uri(NAME), uri(f(insts.get(i).tid().name())),
                    uri(MONAD_IN), jnt((null == mi ? 0L : mi.get()) / iters),
                    uri(C_IN), jnt((null == ci ? 0L : ci.get()) / iters),
                    uri(MONAD_OUT), jnt((null == mo ? 0L : mo.get()) / iters),
                    uri(C_OUT), jnt((null == co ? 0L : co.get()) / iters),
                    uri(TIME), real((null == ti ? 0L : ti.get()) / ms / iters, MILLIS_TYPE.vid(), null)));
        }
        return rec(mutableMap(
                uri(FORMAT), auto_(instLambda(ALL.maybe(), STR_TID, (lhs, inst) -> str(text))),
                // WHICH MACHINE PRODUCED THIS REPORT — `*/network/host.mult(*~/.vid())`, e.g. booger/abc.
                // Without it a collated report cannot say where a number came from; with it, the master
                // keys the shards' reports by origin instead of by arrival order.
                uri(MACHINE), machineIdentity(),
                uri(STAGE), rec(
                        uri(REWRITE), rec(uri(MIN), real(rewriteMin / ms, MILLIS_TYPE.vid(), null), uri(MAX), real(rewriteMax / ms, MILLIS_TYPE.vid(), null)),
                        uri(RESOLVE), rec(uri(MIN), real(resolveMin / ms, MILLIS_TYPE.vid(), null), uri(MAX), real(resolveMax / ms, MILLIS_TYPE.vid(), null)),
                        uri(APPLY), rec(uri(MIN), real(applyMin / ms, MILLIS_TYPE.vid(), null), uri(MAX), real(applyMax / ms, MILLIS_TYPE.vid(), null))),
                uri(INSTS), jnt(resolved.insts().size()),
                uri(RESOLVE), rec(
                        uri(INST_RESOLVE), real(ScoringResolver.T_RESOLVE.get() / ms / iters, MILLIS_TYPE.vid(), null),
                        uri(GENERIC_BINDING), real(ScoringResolver.T_BIND.get() / ms / iters, MILLIS_TYPE.vid(), null),
                        uri(INST_COMPOSITION), real(ScoringResolver.T_COMPOSE.get() / ms / iters, MILLIS_TYPE.vid(), null)),
                uri(REWRITE), rec(
                        uri(RULES), lst(ruleRecs),
                        uri(TOTAL), rec(uri(IN), jnt(originalInsts.size()), uri(OUT), jnt(rewritten.insts().size()), uri(TIME), real(totalRuleTime / ms, MILLIS_TYPE.vid(), null))),
                uri(APPLY), rec(
                        uri(SPLIT), real(StatefulMonad.T_SPLIT.get() / ms / iters, MILLIS_TYPE.vid(), null),
                        uri(APPLY), real(StatefulMonad.T_APPLY.get() / ms / iters, MILLIS_TYPE.vid(), null),
                        uri(NEXT), real(StatefulMonad.T_NEXT.get() / ms / iters, MILLIS_TYPE.vid(), null)),
                uri(PER_INST), lst(perInstRecs),
                uri(FLOW), rec(
                        uri(MONADS), jnt(monads), uri(C_SUM), jnt(coeffSum),
                        uri(COMPRESSION), real(0L == monads ? 0.0 : (double) coeffSum / (double) monads),
                        uri(PROCESSORS), jnt(SwarmProcessor.PROCESSORS.get())),
                uri(CACHE), rec(
                        uri(HITS), jnt(graph.hits()), uri(MISSES), jnt(graph.misses()),
                        uri(HIT_RATE), real(graph.hitRate()))), PROFILING_TID, null);
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
        // there would close a class-initialisation cycle. Staged here and persisted to ?lawq by the drain that
        // runs when this set is mounted. The law table no longer restates any carrier's algebra.
        mTypes.declareAlgebras();
        // EVERY THEORY'S LAW BATTERY IS DECLARED AT THE THEORY (mTypes.declareAxioms) — WHICH process laws an
        // instance of it entails, so a validator can turn a carrier's declared instances into a claim list. Same
        // method-not-field reason as above: the batteries name Law constants and theory tids.
        mTypes.declareAxioms();
    }

    /**
     * ALGEBRA TOOL — the claimed-law oracle: does a type's declared algebra satisfy the laws someone <em>claims</em>
     * it does?
     *
     * <p>A CLAIM IS AN ARGUMENT, NEVER A STORE READ. The laws arrive as a list, are tested against known-good probe
     * operands, and answered in a {@link Report}; nothing is written back to {@code ?lawq}. That is the point: a claim
     * is a hypothesis, and an unverified hypothesis must not become a declaration.
     *
     * <p>WHERE THE ROLES COME FROM — the type's declared algebra ({@code object::T>>law}): instance name → rec of role
     * → operation. {@link studio.phaseshift.metatron.isa.m.math.cat.LawTable#typeLawsOf(Type)} serves it, and that
     * served rec is itself the {@code ?lawq} declaration the base carriers write at their type; when no law table is
     * mounted (the category is optional) the declaration is read directly, so the tool answers on a plain boot. Role
     * pointers are read exactly as {@code TheoryHelper#roleAddress} reads them ({@code atDirect} plus
     * {@link Obj.Helper#getPointer}); that helper is package-private in {@code isa.m.math.cat}, so the two-line read
     * is copied here rather than widening it.
     *
     * <p>WHICH OPERATIONS ARE ANALYZED — {@link #analyzeType(Type, List)} runs {@link #analyzeTypeInst(Type, Inst, List)}
     * for every role-bearing operation the type declares and unions the reports; {@link #analyzeTypeInst(Type, Inst,
     * List)} checks the claims against that one instruction.
     *
     * <p>PROBES — the analyzers take no sample argument, so one is looked up by the type's CARRIER tid: the built-in
     * table, else a sample registered through {@link #registerSample(fURI, Obj)} (the values the type tests return
     * from {@code sample()}), else the type's OWN declared {@code zero}/{@code one} — so the tool answers for any
     * mtron type, and answers {@code uncheckable "no probe"} when the declaration names no element to try. THE SECOND
     * OPERAND IS A SECOND, DISTINCT SAMPLE, not a derivation from the first: on a free monoid no term built from ONE
     * generator can falsify {@code a·b = b·a} (every {@code a^n} commutes with every {@code a^m}), so str's
     * concatenation — the canonical non-commutative claim — would be declared to hold if only one {@code "abc"}
     * existed. A carrier with no registered partner falls back to the probe itself (a weaker answer, and the reason
     * a validator with two distinct elements registers a sample). A DEGENERATE PAIR is refused, never compared: an
     * operand that is not a value of the carrier, or two identical operands, makes any agreement between the sides
     * evidence of nothing (see {@code AlgebraTool#degenerate}). A product that happens to EQUAL one of its operands is
     * NOT such a pair: that is absorption — a property of the operation (bool's {@code true ∨ false = true},
     * {@code true ∧ false = false}) — so the law is judged over it, never refused.
     *
     * <p>VERDICTS — {@code holds} (both sides equal), {@code failed} (they differ), {@code unchecked} (a role the law
     * needs is not declared for this operation — the over-claim case) and {@code uncheckable} (the law is about a
     * DIFFERENT SHAPE than this operation, or carries no runnable equation: {@code pure}, {@code poset}, …, or its
     * probe pair is degenerate — see {@link #matches} and {@code AlgebraTool#degenerate}). {@code ok} is true iff no
     * claim failed and none was unchecked: neither an uncheckable claim nor a degenerate probe is a failure. A judged
     * claim records BOTH evaluated sides as {@code got=>[lhs-result, rhs-result]}, so a broken side is visible in the
     * report instead of inferred from one rendered string.
     * <p>APPLICABILITY IS A SIGNATURE MATCH, NEVER A LADDER — {@link #matches} compares {@link Law#wrt()} with the
     * operation's contract (dom/rng with the poly {@code A} standing for the carrier, and arity only when the
     * signature declares {@code args}). A refused claim is a category error, so it answers {@code uncheckable} with
     * the law's shape named ({@code "the law is about inst?rng=bool&dom=A&args=[A]"}), and the same rule is the
     * census a declared law is held to.
     */
    public static final class AlgebraTool {

        private AlgebraTool() {
        }

        /**
         * The roles a theory instance binds its operations under.
         */
        private static final List<String> OP_ROLES = List.of(OP, ADD, MUL, OR, AND, JOIN, MEET, INV, NOT);

        /**
         * THE INSTANCE NAMES THAT STATE A GROUP — the algebras an {@code involution} claim may read in its group
         * form, together with the spellings {@code declareAlgebras} gives them ({@code add_group}, {@code mult_group},
         * {@code free_groupoid}). An operation bound by a lattice, boolean or plain monoid has no group inverse, so
         * the group equation is not its law: the claim is UNCHECKABLE there, never failed.
         */
        private static final List<String> GROUP_ALGEBRAS = List.of("group", "ring", "field", "near_ring");
        /**
         * The additive roles of a theory instance.
         */
        private static final List<String> ADD_ROLES = List.of(ADD, OR, JOIN);
        /**
         * The multiplicative roles of a theory instance.
         */
        private static final List<String> MUL_ROLES = List.of(MUL, AND, MEET);

        /**
         * The operand slots the equations are rendered over — the same slots the algebra tests write a probe into
         * before dereferencing {@code *a}. They are ordinary memory cells: a call rewrites them.
         */
        private static final String A = "*a";
        private static final String B = "*b";
        private static final String D = "*d";
        private static final String Z = "*z";

        /**
         * THE PROBES — keyed by CARRIER tid (a refinement finds its base's probe: {@code nat::T} answers with
         * {@code int}'s 23), and exactly the operands the type tests return from {@code sample()}. Mutable through
         * {@link #registerSample(fURI, Obj)}: a carrier this class does not name supplies its own probe, so the tool
         * answers for ANY mtron type, not only the eight base carriers.
         */
        private static final Map<fURI, Obj> SAMPLES = samples();
        /**
         * THE SECOND OPERAND — a distinct, known-good element of the same carrier. See the class notes: a single
         * generator cannot falsify a commutativity claim on a free monoid.
         */
        private static final Map<fURI, Obj> PARTNERS = partners();

        /**
         * REGISTER A CARRIER'S PROBE — the extension point a type test uses instead of a private map: the sample an
         * equation's {@code *a} is written with. Keyed by the carrier's BASE path, the same key {@link #probe} reads,
         * so a refinement's registration serves its base too. Null and {@code noobj} are ignored (they would be a
         * probe that says nothing), which leaves the declaration's own zero/one in charge.
         */
        public static void registerSample(final fURI carrier, final Obj sample) {
            if (null == carrier || null == sample || sample.isNoObj())
                return;
            SAMPLES.put(null == carrier.basePath() ? carrier : carrier.basePath(), sample);
        }

        private static Map<fURI, Obj> samples() {
            final Map<fURI, Obj> samples = new ConcurrentHashMap<>();
            samples.put(INT_TID.basePath(), jnt(23));
            samples.put(REAL_TID.basePath(), real(23.5d));
            samples.put(STR_TID.basePath(), str("abc"));
            samples.put(BOOL_TID.basePath(), bool(true));
            samples.put(BYTES_TID.basePath(), bytes(ByteBuffer.wrap(new byte[]{1, 2, 3})));
            samples.put(URI_TID.basePath(), uri(f("/a/b")));
            samples.put(LST_TID.basePath(), lst(jnt(1), jnt(2), jnt(3)));
            samples.put(REC_TID.basePath(), rec(uri("a"), jnt(1), uri("b"), jnt(2)));
            samples.put(REL_TID.basePath(), rel(uri("a"), jnt(1)));
            return samples;
        }

        private static Map<fURI, Obj> partners() {
            final Map<fURI, Obj> partners = new LinkedHashMap<>();
            partners.put(INT_TID.basePath(), jnt(12));
            partners.put(REAL_TID.basePath(), real(12.5d));
            partners.put(STR_TID.basePath(), str("xyz"));
            partners.put(BOOL_TID.basePath(), bool(false));
            partners.put(BYTES_TID.basePath(), bytes(ByteBuffer.wrap(new byte[]{4, 5, 6})));
            partners.put(URI_TID.basePath(), uri(f("/c/d")));
            partners.put(LST_TID.basePath(), lst(jnt(4), jnt(5), jnt(6)));
            partners.put(REC_TID.basePath(), rec(uri("c"), jnt(3), uri("d"), jnt(4)));
            partners.put(REL_TID.basePath(), rel(uri("c"), jnt(3)));
            partners.put(CODE_TID.basePath(), instB(END_INST_TID, lst()));
            return Collections.unmodifiableMap(partners);
        }

        /* ------------------------------------------------------------------ the public analyzers */

        /**
         * Validate the CLAIMED laws against a type: every role-bearing operation the type declares is analyzed and
         * the verdicts are unioned in declaration order. A type that declares no operation leaves every claim
         * {@code unchecked} — a claim about an algebra that was never written is exactly the over-claim the tool
         * exists to expose.
         */
        public static Report analyzeType(final Type type, final List<Law> claimedLaws) {
            final List<Law> claimed = null == claimedLaws ? List.of() : claimedLaws;
            final List<Op> ops = opsOf(type);
            if (ops.isEmpty()) {
                final List<Result> results = new ArrayList<>();
                for (final Law law : claimed)
                    results.add(Result.unchecked(law, OP, "the type declares no role-bearing operation"));
                return report(type, null, claimed, results);
            }
            final List<Report> reports = new ArrayList<>();
            for (final Op op : ops)
                reports.add(analyzeTypeInst(type, op.inst(), claimed));
            return merge(type, reports);
        }

        /**
         * Validate the CLAIMED laws against one instruction of the type. The instruction's role in the type's algebra
         * decides which identity and inverse a law reads; an instruction the algebra does not bind is analyzed
         * against the type's declared operations alone, with no identity of its own. A targeted analysis REFUSES a
         * degenerate probe exactly as the type-level run does: the precondition lives in {@link #check}, which both
         * analyzers reach, so a claim about one instruction can never be judged over operands that are not values of
         * the carrier or are identical.
         */
        public static Report analyzeTypeInst(final Type type, final Inst inst, final List<Law> claimedLaws) {
            final List<Law> claimed = null == claimedLaws ? List.of() : claimedLaws;
            final Op op = op(type, inst);
            return report(type,
                    null == op ? null : uri(op.inst().tid()),
                    claimed, check(type, op, claimed));
        }

        /**
         * Whether the type's declared algebra BINDS this operation — the same role read {@link #opsOf} makes,
         * exposed because a hand-written interpretation of an equation template needs the guard the analyzer uses
         * ("does this carrier declare the operation this row is about?"). Type-agnostic: no Java interface is asked.
         */
        public static boolean binds(final Type type, final fURI operation) {
            if (null == operation || null == operation.basePath())
                return false;
            return opsOf(type).stream().anyMatch(op -> op.address().basePath().equals(operation.basePath()));
        }

        /**
         * WHETHER THE LAW IS ABOUT THIS INSTRUCTION AT ALL — the signature match, and the decision that used to be a
         * ladder of {@code op.binary()} / "is this the group operation?" / "is this the multiplicative operation?"
         * tests spread through the equations.
         * <p>
         * A law is a statement about a SHAPE ({@link Law#wrt()}), so it applies to an instruction only when the
         * instruction's contract has that shape:
         * <ol>
         *   <li>an {@code obj}-shaped ({@code pure}) or bare-{@code inst}-shaped ({@code floatable},
         *       {@code blocked}, {@code partial}) law is about the instruction itself, so it matches every
         *       instruction — the equations then answer with their own reason, exactly as before;</li>
         *   <li>a carrier law ({@code inst?dom=…&rng=…}) matches when the instruction's {@code dom} and {@code rng}
         *       agree with the signature's, with the poly variable {@code A} standing for the carrier — one
         *       variable, so every {@code A} in the signature must land on the SAME carrier type;</li>
         *   <li>arity is read ONLY from the signature's {@code args} component: {@code args=[A]} demands exactly one
         *       argument beyond the lhs (a binary operation), while a signature with no {@code args} is
         *       ARITY-AGNOSTIC and matches any arity — an element law, a unary {@code neg}, or a fold. That is what
         *       lets an idempotent {@code zero} and an idempotent {@code or} be the same law.</li>
         * </ol>
         * A mismatch is a CATEGORY ERROR, never a counterexample: the instruction is not the kind of thing the law
         * is about, so the verdict is {@code uncheckable} with the law's shape named, and neither confirms nor
         * refutes it.
         */
        public static boolean matches(final Law law, final Inst inst) {
            return null == law || null == inst || null == signatureMismatch(law, inst);
        }

        /**
         * THE SIGNATURE MATCH — null when the law's signature names this instruction's contract, else the one-line
         * refusal that names the law's shape. The census reads this through {@link #matches}, so the rule that
         * refuses a claim at analysis time is the same rule a declaration is held to.
         */
        private static String signatureMismatch(final Law law, final Inst inst) {
            if (null == law || null == inst || null == inst.tid())
                return null;
            final fURI signature = law.wrt();
            if (!signature.hasDom() && !signature.hasRng())
                return null;                        // `inst` (form) and `obj` (effect): about the instruction itself
            final fURI dom = inst.tid().dom();
            final fURI rng = inst.tid().rng();
            final List<fURI> args = signature.args();
            // arity is a claim ONLY when the signature carries args: no args means "any arity"
            if (!args.isEmpty() && args.size() != inst.args().count())
                return about(law);
            final String[] carrier = {null};
            if (!agrees(dom, signature.dom(), carrier) || !agrees(rng, signature.rng(), carrier))
                return about(law);
            for (int i = 0; i < args.size(); i++)
                if (!agrees(argType(inst, i), args.get(i), carrier))
                    return about(law);
            return null;
        }

        /**
         * Match one signature component against the instruction's component of the same slot — {@code dom} to
         * {@code dom}, {@code rng} to {@code rng}, one {@code args} element to one declared argument type.
         * <p>
         * An undeclared endpoint (the {@code #} a missing {@code dom}/{@code rng} reads as) and a GENERIC contract
         * (the {@code A}/{@code B} of a generic registration) accept anything, because they state no concrete type
         * to refuse. A signature's {@code A} BINDS the carrier through {@code carrier} and must agree with every
         * other {@code A} in the signature; an {@code A} carrying a coefficient ({@code A{*}}) additionally demands
         * an endpoint that can hold MANY copies — the fold shape is not the single-element one.
         */
        private static boolean agrees(final fURI actual, final fURI wanted, final String[] carrier) {
            if (null == actual || null == wanted)
                return true;
            final String actualName = baseName(actual);
            if (actual.isGeneric() || "#".equals(actualName) || "+".equals(actualName))
                return true;
            if (wanted.isGeneric()) {
                if (null == carrier[0])
                    carrier[0] = actualName;            // ONE poly variable: the carrier the law is written over
                else if (!carrier[0].equals(actualName))
                    return false;
                return wanted.c().isOne() || admitsMany(actual);
            }
            return baseName(wanted).equals(actualName);
        }

        /**
         * The type name a signature component is compared by. A law is written in the process vocabulary
         * ({@code dom=A}, {@code rng=bool}), so its names are relative, while an instruction's contract is absolute
         * ({@code /m/bool}); the base name is what both spellings share.
         */
        private static String baseName(final fURI uri) {
            if (null == uri)
                return "";
            final fURI base = uri.basePath();
            return null == base ? uri.toString() : base.name();
        }

        /**
         * Whether an endpoint can carry more than one element — the {@code A{*}} of a fold demands it, and a
         * single-element endpoint ({@code A}, {@code A{?}}) does not satisfy it.
         */
        private static boolean admitsMany(final fURI component) {
            final cInt c = component.c();
            return !c.isZero() && (null == c.max() || c.max() > 1);
        }

        /**
         * The declared type of an instruction's argument slot, as the address the signature is compared against;
         * null when the slot declares no type (which matches anything).
         */
        private static fURI argType(final Inst inst, final int index) {
            final Obj arg = inst.arg(index);
            return null == arg || arg.isNoObj() ? null : Obj.Helper.specificTypeId(arg);
        }

        /**
         * The refusal a shape mismatch answers with — the law's own signature, so a reader can see which shape was
         * expected instead of inferring it from a law name.
         */
        private static String about(final Law law) {
            return "the law is about " + law.wrt();
        }

        /**
         * The union of per-operation reports: the verdicts concatenate, the operations are listed, and any failure or
         * unchecked claim anywhere makes the union not ok.
         */
        private static Report merge(final Type type, final List<Report> reports) {
            final List<Obj> holds = new ArrayList<>();
            final List<Obj> failed = new ArrayList<>();
            final List<Obj> unchecked = new ArrayList<>();
            final List<Obj> uncheckable = new ArrayList<>();
            final List<Obj> ops = new ArrayList<>();
            for (final Report report : reports) {
                holds.addAll(report.holds().lstValue());
                failed.addAll(report.failed().lstValue());
                unchecked.addAll(report.unchecked().lstValue());
                uncheckable.addAll(report.uncheckable().lstValue());
                if (null != report.inst())
                    ops.add(report.inst());
            }
            return new Report(null == type ? null : type.vid(), ops.isEmpty() ? null : lst(ops),
                    reports.isEmpty() ? lst() : reports.getFirst().claimed(),
                    lst(holds), lst(failed), lst(unchecked), lst(uncheckable),
                    failed.isEmpty() && unchecked.isEmpty());
        }

        /* ------------------------------------------------------------------ the report */

        /**
         * The verdict of one analyzer run, rendered by {@link #toRec()} as
         * <pre>{type=&gt;…, inst=&gt;…, claimed=&gt;[…], holds=&gt;[…], failed=&gt;[…], unchecked=&gt;[…],
         * uncheckable=&gt;[…], ok=&gt;bool}</pre>
         * where each verdict list holds one rec per law — {@code {law=&gt;"involution", role=&gt;"inv",
         * lhs=&gt;"…", rhs=&gt;"…"}} plus {@code got=&gt;[lhs-result, rhs-result]} on a judged claim and
         * {@code reason} on an unchecked/uncheckable one.
         * {@code inst} is the instruction analyzed; a type-level run carries the lst of operations it unioned.
         */
        public record Report(fURI type, Obj inst, Lst claimed, Lst holds, Lst failed, Lst unchecked,
                             Lst uncheckable, boolean ok) {

            public Obj toRec() {
                return rec(
                        uri(TYPE), null == this.type ? noobj() : uri(this.type),
                        uri(INST), null == this.inst ? noobj() : this.inst,
                        uri(CLAIMED), this.claimed,
                        uri(HOLDS), this.holds,
                        uri(FAILED), this.failed,
                        uri(UNCHECKED), this.unchecked,
                        uri(UNCHECKABLE), this.uncheckable,
                        uri(OK), bool(this.ok));
            }
        }

        private static Report report(final Type type, final Obj inst, final List<Law> claimed, final List<Result> results) {
            final List<Obj> holds = new ArrayList<>();
            final List<Obj> failed = new ArrayList<>();
            final List<Obj> unchecked = new ArrayList<>();
            final List<Obj> uncheckable = new ArrayList<>();
            for (final Result result : results) {
                switch (result.verdict()) {
                    case holds -> holds.add(toRec(result));
                    case failed -> failed.add(toRec(result));
                    case unchecked -> unchecked.add(toRec(result));
                    case uncheckable -> uncheckable.add(toRec(result));
                }
            }
            return new Report(null == type ? null : type.vid(), inst,
                    lst(claimed.stream().map(law -> (Obj) uri(law.name())).toList()),
                    lst(holds), lst(failed), lst(unchecked), lst(uncheckable),
                    failed.isEmpty() && unchecked.isEmpty());
        }

        /* ------------------------------------------------------------------ the algebra */

        /**
         * The type's declared algebra — the theory instances it models. {@code LawTable.typeLawsOf} is the serving
         * read; the {@code ?lawq} declaration it serves first is the fallback when no law table is mounted, and it is
         * where the base carriers write their algebra ({@code mTypes.declareAlgebras}).
         */
        private static Rec algebra(final Type type) {
            if (null == type || !Machine.loaded())
                return rec0();
            final Rec declared = LawTable.typeLawsOf(type);
            if (!declared.jvm().isEmpty())
                return declared;
            final Obj declaration = lawOf(type);
            return declaration.isRec() ? declaration.asRec() : rec0();
        }

        /**
         * The op address a law block's role binds, or null when the role is absent — the two-line read
         * {@code TheoryHelper#roleAddress} makes, copied because that helper is package-private in
         * {@code isa.m.math.cat}: the value arrives as a {@code !*} auto pointer naming its target in its own
         * argument, so the address is read without resolving it.
         */
        private static fURI roleAddress(final Rec block, final String role) {
            if (null == block)
                return null;
            final Obj holder = block.atDirect(uri(role));
            if (holder.isNoObj())
                return null;
            if (holder.isUri())
                return holder.uriValue();
            return Obj.Helper.getPointer(holder).orElse(null);
        }

        /**
         * The VALUE a role binds — a literal element ({@code zero => 0}, {@code id => ""}, {@code one => <>}) or,
         * for an operation used as a value (the code ring's {@code one => !*id}), the operation itself. Null when the
         * role is absent or names an operation the type does not register.
         */
        private static Obj value(final Type type, final Rec block, final String... roles) {
            if (null == block)
                return null;
            for (final String role : roles) {
                final Obj holder = block.atDirect(uri(role));
                if (holder.isNoObj())
                    continue;
                final Optional<fURI> pointer = Obj.Helper.getPointer(holder);
                if (pointer.isEmpty())
                    return holder;
                final Inst target = contract(type, pointer.get());
                if (null != target)
                    return target;
            }
            return null;
        }

        /**
         * The registered contract an op address names over this carrier — what decides an operation's arity, and
         * what tells a role holding a real operation from one holding a bare uri.
         */
        private static Inst contract(final Type type, final fURI address) {
            if (!Machine.loaded() || null == type || null == address || null == address.basePath())
                return null;
            try {
                return Typer.Helper.deduceTypeInst(type, address);
            } catch (final Throwable e) {
                return null;
            }
        }

        /* ------------------------------------------------------------------ the operations */

        /**
         * An operation of the type: its address, its registered contract, and the role/instance that binds it.
         */
        private record Op(fURI address, Inst inst, String role, String instance, Rec block, String name,
                          boolean binary) {
        }

        /**
         * Every role-bearing operation the type declares, one per op family (a {@code plus} declared by a
         * {@code ring}, an {@code add_group} and an {@code add_monoid} is ONE operation — the richest binding wins,
         * so a monoid/group instance's own {@code id} and {@code inv} are the ones a law reads).
         */
        private static List<Op> opsOf(final Type type) {
            final Map<String, Op> ops = new LinkedHashMap<>();
            for (final Map.Entry<Obj, Obj> entry : algebra(type).jvm().entrySet()) {
                if (!entry.getKey().isUri() || !entry.getValue().isRec())
                    continue;
                final Rec block = entry.getValue().asRec();
                for (final String role : OP_ROLES) {
                    final fURI address = roleAddress(block, role);
                    if (null == address || null == address.basePath() || ops.containsKey(address.basePath().toString()))
                        continue;
                    final Op op = bind(type, address);
                    if (null != op)
                        ops.put(address.basePath().toString(), op);
                }
            }
            return new ArrayList<>(ops.values());
        }

        /**
         * THE RICHEST BINDING of an operation over the type — how the type's algebra says the operation is declared.
         * An {@code op} role outranks the named roles (a group/monoid instance states the operation's own identity
         * and inverse), an identity adds, an inverse adds more: int's {@code plus} is one operation declared by a
         * {@code ring}, an {@code add_group} and an {@code add_monoid}, and the group is the binding whose
         * {@code id} (0) and {@code inv} (neg) an {@code involution} claim reads. Null when the address names no
         * registered operation of the type.
         */
        private static Op bind(final Type type, final fURI address) {
            if (null == address || null == address.basePath())
                return null;
            final Inst inst = contract(type, address);
            if (null == inst)
                return null;
            Op best = null;
            int bestScore = -1;
            for (final Map.Entry<Obj, Obj> entry : algebra(type).jvm().entrySet()) {
                if (!entry.getKey().isUri() || !entry.getValue().isRec())
                    continue;
                final Rec block = entry.getValue().asRec();
                for (final String role : OP_ROLES) {
                    final fURI bound = roleAddress(block, role);
                    if (null == bound || null == bound.basePath() || !bound.basePath().equals(address.basePath()))
                        continue;
                    final Op candidate = new Op(address, inst, role, entry.getKey().uriValue().name(), block,
                            address.basePath().name(), 1 == inst.args().count());
                    final int score = (OP.equals(role) ? 4 : 0)
                            + (null != identity(type, candidate) ? 2 : 0)
                            + (null != roleAddress(block, INV) ? 1 : 0);
                    if (score > bestScore) {
                        best = candidate;
                        bestScore = score;
                    }
                }
            }
            return null == best
                    ? new Op(address, inst, OP, null, rec0(), address.basePath().name(), 1 == inst.args().count())
                    : best;
        }

        /**
         * One instruction of the type bound the way {@link #opsOf} binds it.
         */
        private static Op op(final Type type, final Inst inst) {
            if (null == inst || null == inst.tid() || null == inst.tid().basePath())
                return null;
            final Op bound = bind(type, inst.tid());
            return null != bound ? bound
                    : new Op(inst.tid(), inst, OP, null, rec0(), inst.tid().basePath().name(),
                    1 == inst.args().count());
        }

        /* ------------------------------------------------------------------ the roles a law reads */

        /**
         * The type-level roles a law may need: the additive op, the multiplicative op, the inverse op, the zero.
         */
        private record Roles(fURI add, fURI mul, fURI inv, Obj zero) {
        }

        private static Roles roles(final Type type) {
            fURI add = null;
            fURI mul = null;
            fURI inv = null;
            Obj zero = null;
            for (final Map.Entry<Obj, Obj> entry : algebra(type).jvm().entrySet()) {
                if (!entry.getValue().isRec())
                    continue;
                final Rec block = entry.getValue().asRec();
                if (null == add)
                    add = first(block, ADD_ROLES);
                if (null == mul)
                    mul = first(block, MUL_ROLES);
                if (null == inv)
                    inv = first(block, List.of(INV, NOT));
                if (null == zero)
                    zero = value(type, block, ZERO, BOTTOM);
            }
            return new Roles(add, mul, inv, zero);
        }

        private static fURI first(final Rec block, final List<String> roles) {
            for (final String role : roles) {
                final fURI address = roleAddress(block, role);
                if (null != address && null != address.basePath())
                    return address;
            }
            return null;
        }

        /**
         * The identity of the operation in its OWN binding: the instance's {@code id}, or — for a named additive or
         * multiplicative role — the instance's {@code zero}/{@code one} (a ring states its identities as elements, not
         * as an {@code id} role). Null when the binding declares neither, which is what makes a {@code unit} claim on
         * a bare semigroup {@code unchecked} rather than wrong.
         */
        private static Obj identity(final Type type, final Op op) {
            final Obj direct = value(type, op.block(), ID);
            if (null != direct)
                return direct;
            if (ADD_ROLES.contains(op.role()))
                return value(type, op.block(), ZERO, BOTTOM);
            if (MUL_ROLES.contains(op.role()))
                return value(type, op.block(), ONE, TOP);
            return null;
        }

        /* ------------------------------------------------------------------ the law tests */

        /**
         * The equations a law is tested with, rendered over the probe slots — one pair (lhs, rhs) per law. The
         * SIGNATURE is consulted first ({@link #matches}): a law whose subject shape this operation does not have is
         * {@code uncheckable} with the shape named, because it is a category error and not a counterexample. Past
         * that gate a law whose required role does not resolve is {@code unchecked}; a law this version has no
         * runnable equation for is {@code uncheckable} with its own reason.
         * <p>
         * THE BINARY GUARDS BELOW ARE NO LONGER CLASSIFICATION. A signature with no {@code args} is arity-agnostic
         * on purpose (an idempotent {@code zero}, a commutative stream reduction), so an operation it matches may
         * still not be binary; the templates below state these laws between OPERANDS, so such an operation answers
         * the same {@code "the operation is not binary"} it always did — the law applies, the equation does not.
         */
        private static List<Result> test(final Type type, final Op op, final Roles roles, final Law law) {
            // THE SUBJECT SHAPE DECIDES APPLICABILITY — a law about another shape is not this operation's law
            final String mismatch = signatureMismatch(law, op.inst());
            if (null != mismatch)
                return List.of(Result.uncheckable(law, op.role(), mismatch));
            switch (law) {
                case monoidic:
                    return op.binary()
                            ? List.of(evaluate(law, op.role(),
                            apply(op.name(), apply(op.name(), A, B), A),
                            apply(op.name(), A, apply(op.name(), B, A))))
                            : List.of(notBinary(law, op));
                case commutative:
                    return op.binary()
                            ? List.of(evaluate(law, op.role(), apply(op.name(), A, B), apply(op.name(), B, A)))
                            : List.of(notBinary(law, op));
                case idempotent:
                    // no args in the signature: the law is arity-agnostic, and the template below is its binary form
                    return op.binary()
                            ? List.of(evaluate(law, op.role(), apply(op.name(), A, A), A))
                            : List.of(notBinary(law, op));
                case unit: {
                    if (!op.binary())
                        return List.of(notBinary(law, op));
                    final Obj identity = identity(type, op);
                    if (null == identity)
                        return List.of(Result.unchecked(law, ID, "the operation declares no identity"));
                    slot("d", identity);
                    return List.of(evaluate(law, op.role(), apply(op.name(), A, D), A));
                }
                case absorbing: {
                    if (!op.binary())
                        return List.of(notBinary(law, op));
                    if (null == roles.zero())
                        return List.of(Result.unchecked(law, ZERO, "the type declares no zero"));
                    slot("z", roles.zero());
                    return List.of(evaluate(law, op.role(), apply(op.name(), A, Z), Z));
                }
                case involution:
                    return involution(type, op, roles, law);
                case complement:
                    return complement(type, op, roles, law);
                case left_distributive:
                case right_distributive:
                case semi_right_distributive:
                    return distributive(op, roles, law);
                default:
                    return List.of(Result.uncheckable(law, op.role(), uncheckableReason(law)));
            }
        }

        /**
         * {@code involution} is THREE claims, decided by where the operation sits in the algebra:
         * <ul>
         *   <li>(a) THE INVERSE ITSELF — the operation is the instance's {@code inv}/{@code not} role, and states
         *       {@code inv(inv(a)) = a}. This is what a Boolean algebra's {@code not} satisfies.</li>
         *   <li>(b) A GROUP OPERATION — the operation binds to a GROUP-ish instance ({@code group}, {@code ring},
         *       {@code field}, {@code near_ring}; the {@code add_group}/{@code mult_group} spellings) and has an
         *       {@code inv} role beside it, so it states {@code a·inv(a) = id}.</li>
         *   <li>(c) EVERYTHING ELSE — a lattice, Boolean, rig or monoid operation is NOT a group operation, so the
         *       group form is not its law: {@code uncheckable} with that reason, never a failure. A Boolean algebra's
         *       {@code and}/{@code or} land here, and the complement they do satisfy is {@code Law.complement}.</li>
         * </ul>
         * A role the type does not declare at all leaves the claim unchecked — the over-claim case.
         */
        private static List<Result> involution(final Type type, final Op op, final Roles roles, final Law law) {
            if (null == roles.inv())
                return List.of(Result.unchecked(law, INV, "the type declares no inverse operation"));
            final fURI bound = null != roleAddress(op.block(), INV) ? roleAddress(op.block(), INV) : roleAddress(op.block(), NOT);
            if (null != bound && null != bound.basePath() && bound.basePath().equals(op.address().basePath()))
                return List.of(evaluate(law, op.role(), call(op.name(), call(op.name(), A)), A));
            if (null == bound || !groupish(op.instance()))
                return List.of(Result.uncheckable(law, op.role(), "the operation is not a group operation"));
            if (!op.binary())
                return List.of(notBinary(law, op));   // the group form is an OPERAND equation, so it needs two
            final Obj identity = identity(type, op);
            if (null == identity)
                return List.of(Result.unchecked(law, ID, "the group operation declares no identity"));
            slot("d", identity);
            return List.of(evaluate(law, op.role(), apply(op.name(), A, call(bound.basePath().name(), A)), D));
        }

        /**
         * Does the instance that binds an operation state a GROUP? The names are the ones {@code declareAlgebras}
         * uses ({@code ring}, {@code field}, {@code add_group}, {@code mult_group}, {@code free_groupoid}) plus the
         * plain theory names {@code GROUP_ALGEBRAS} names, so a future spelling that keeps the suffix is recognized.
         */
        private static boolean groupish(final String instance) {
            if (null == instance)
                return false;
            final String name = instance.toLowerCase(Locale.ROOT);
            return GROUP_ALGEBRAS.contains(name) || name.endsWith("_group")
                    || name.endsWith("_groupoid") || name.endsWith("_ring") || name.endsWith("_field");
        }

        /**
         * {@code complement} is the complemented-lattice law as an OPERAND equation: {@code a·inv(a) = inv(id)} —
         * an element combined with its complement is the OTHER bound of the lattice, so for {@code and} over a
         * Boolean algebra {@code a∧¬a = ¬⊤ = ⊥}, and for {@code or} {@code a∨¬a = ¬⊥ = ⊤}. It needs no role the
         * declaration does not already carry: the operation must be BINARY, the type must declare an inverse
         * ({@code inv}/{@code not}), and the binding must state an identity ({@code id}, else the {@code zero}/
         * {@code one} its named additive/multiplicative role states). A non-binary operation (the inverse itself)
         * carries no operand equation and is {@code uncheckable}; a missing inverse or identity is the over-claim
         * case ({@code unchecked}).
         */
        private static List<Result> complement(final Type type, final Op op, final Roles roles, final Law law) {
            if (!op.binary())
                return List.of(notBinary(law, op));
            final fURI bound = null != roleAddress(op.block(), INV) ? roleAddress(op.block(), INV) : roles.inv();
            if (null == bound || null == bound.basePath())
                return List.of(Result.unchecked(law, INV, "the type declares no inverse operation"));
            final Obj identity = identity(type, op);
            if (null == identity)
                return List.of(Result.unchecked(law, ID, "the operation declares no identity"));
            slot("d", identity);
            return List.of(evaluate(law, op.role(),
                    apply(op.name(), A, call(bound.basePath().name(), A)),
                    call(bound.basePath().name(), D)));
        }

        /**
         * The three distributivity laws are about the MULTIPLICATIVE operation distributing over the additive one, so
         * both roles must resolve and the analyzed operation must be the multiplicative one. {@code semi_right} is the
         * reducer's law (Thm 19): the right side re-applies the operation.
         */
        private static List<Result> distributive(final Op op, final Roles roles, final Law law) {
            if (null == roles.add())
                return List.of(Result.unchecked(law, ADD, "the type declares no additive operation"));
            if (null == roles.mul())
                return List.of(Result.unchecked(law, MUL, "the type declares no multiplicative operation"));
            if (!roles.mul().basePath().equals(op.address().basePath()))
                return List.of(Result.uncheckable(law, op.role(), "the law is about the multiplicative operation"));
            final String add = roles.add().basePath().name();
            final String times = op.name();
            final String lhs = switch (law) {
                case left_distributive -> apply(times, A, apply(add, B, A));                  // a·(b+a)
                default -> apply(times, apply(add, A, B), A);                                 // (a+b)·a
            };
            final String rhs = switch (law) {
                case left_distributive -> apply(add, apply(times, A, B), apply(times, A, A)); // a·b + a·a
                case right_distributive -> apply(add, apply(times, A, A), apply(times, B, A)); // a·a + b·a
                default -> apply(times, apply(add, apply(times, A, A), apply(times, B, A)), A); // (a·a + b·a)·a
            };
            return List.of(evaluate(law, op.role(), lhs, rhs));
        }

        private static Result notBinary(final Law law, final Op op) {
            return Result.uncheckable(law, op.role(), "the operation is not binary");
        }

        /**
         * The laws this version carries no runnable equation for, and why.
         */
        private static String uncheckableReason(final Law law) {
            return switch (law) {
                case pure -> "a declared effect, not an equation";
                case floatable -> "a stream-ring coefficient law, not an operand equation";
                case blocked -> "a declared barrier, not an equation";
                case poset -> "the type declares no order operation to compare";
                case action -> "an action needs a monoid acting on the carrier, which no role declares";
                case magmadic -> "closure only — the law demands no equation";
                case kleene -> "an unbounded iterate, not a finite operand test";
                case nilpotent -> "an unbounded iterate to a zero power, not a finite operand test";
                case partial -> "the zeroable lift of a value, not an equation";
                case prime -> "an element property, not an equation between operands";
                case zero_divisor -> "an existential over distinct elements";
                case cyclic -> "an existential over a generated subalgebra";
                default -> "no runnable test for this law";
            };
        }

        /* ------------------------------------------------------------------ evaluation */

        /**
         * THE NUMERIC TOLERANCE a law's two sides are compared within — see {@link #equal(Obj, Obj)}.
         */
        private static final double EPSILON = 1.0e-9d;

        /**
         * EQUALITY FOR A LAW'S TWO SIDES — structural equality ( ) OR, for the two numeric carriers
         * ({@code int}, {@code real}), a small RELATIVE epsilon: {@code |l-r| <= EPSILON·max(1,|l|,|r|)}.
         *
         * <p>WHY THE EPSILON EXISTS: {@code real} is IEEE-754, so a field law that is exact over the rationals need
         * not be exact over the doubles — {@code a·(1/a)} can land one ulp off 1, and a purely structural compare
         * would report a law FALSE that the carrier actually satisfies (a false failure, the worst answer an oracle
         * can give). 1e-9 is far above the last-place error of a double (2.2e-16 relative) and far below any
         * distinction an algebraic law test can draw.
         *
         * <p>WHY IT IS NOT IN {@code Obj.equals}/{@code hashCode}: approximate equality is NOT transitive (a≈b and
         * b≈c do not give a≈c), so it cannot define an equivalence relation — and {@code equals}/{@code hashCode}
         * are exactly that for hashed lookups ({@code Obj.Helper.objEquals} backs rec keys, space reads and set
         * membership). Two reals 1e-12 apart must stay two entries. The tolerance lives HERE, at the point where a
         * law is judged, and nowhere else.
         *
         * <p>Public because the hand-written interpretations of the same templates (the algebra tests) compare what
         * the tool compares — one notion of "the two sides agree".
         */
        public static boolean equal(final Obj lhs, final Obj rhs) {
            if (null == lhs || null == rhs)
                return lhs == rhs;
            if (lhs.equals(rhs))
                return true;
            final Double left = numeric(lhs);
            final Double right = numeric(rhs);
            if (null == left || null == right)
                return false;
            final double scale = Math.max(1.0d, Math.max(Math.abs(left), Math.abs(right)));
            return Math.abs(left - right) <= EPSILON * scale;
        }

        /**
         * The double a numeric obj denotes ({@code int} or {@code real}); null for every other carrier.
         */
        private static Double numeric(final Obj obj) {
            if (null == obj)
                return null;
            try {
                if (obj.isInt())
                    return obj.intValue().doubleValue();
                if (obj.isReal())
                    return obj.realValue().doubleValue();
            } catch (final Throwable e) {
                return null;
            }
            return null;
        }

        /**
         * THE EVALUATION — render the equation as mtron, parse it, and apply it: the mechanism the algebra tests
         * already use ({@code ObjmtronSerializer.parse(code).apply()} over a probe written to memory). The two sides
         * are compared by {@link #equal(Obj, Obj)} — structural, with the numeric tolerance — and BOTH evaluated
         * objs are carried into the result, on a failure as well as on a hold.
         */
        private static Result evaluate(final Law law, final String role, final String lhs, final String rhs) {
            try {
                final Obj left = ObjmtronSerializer.parse(lhs).apply();
                final Obj right = ObjmtronSerializer.parse(rhs).apply();
                return equal(left, right)
                        ? Result.holds(law, role, lhs, rhs, left, right)
                        : Result.failed(law, role, lhs, rhs, left, right);
            } catch (final Throwable e) {
                return Result.uncheckable(law, role, "the equation could not be evaluated: " + message(e));
            }
        }

        private static String message(final Throwable e) {
            final String message = null == e.getMessage() ? e.getClass().getSimpleName() : e.getMessage();
            return message.length() > 160 ? message.substring(0, 160) + "..." : message;
        }

        private static void slot(final String name, final Obj value) {
            Machine.current().memory().write(name, value);
        }

        /**
         * The operands a check runs over: {@code *a} is the probe, {@code *b} its distinct partner, {@code *d} the
         * identity a {@code unit} law reads and {@code *z} the zero an {@code absorbing} law reads.
         *
         * <p>THE PROBE PAIR IS GUARDED FIRST ({@link #degenerate}) — a law whose operands are not values of the
         * carrier or are the same element is answered {@code uncheckable} before its equation is run. Both analyzers
         * pass through here, so {@link #analyzeTypeInst(Type, Inst, List)} refuses a degenerate probe exactly as
         * {@link #analyzeType(Type, List)} does.
         */
        private static List<Result> check(final Type type, final Op op, final List<Law> claimed) {
            final List<Result> results = new ArrayList<>();
            if (null == op) {
                for (final Law law : claimed)
                    results.add(Result.unchecked(law, OP, "no operation is bound"));
                return results;
            }
            final Obj probe = probe(type);
            if (null == probe) {
                for (final Law law : claimed)
                    results.add(Result.uncheckable(law, op.role(), "no probe"));
                return results;
            }
            if (!Machine.loaded()) {
                for (final Law law : claimed)
                    results.add(Result.uncheckable(law, op.role(), "no machine to evaluate against"));
                return results;
            }
            final Obj partner = partner(type, probe);
            slot("a", probe);
            slot("b", partner);
            final Roles roles = roles(type);
            for (final Law law : claimed) {
                final String probeReason = degenerate(type, probe, partner);
                if (null != probeReason)
                    results.add(Result.uncheckable(law, op.role(), probeReason));
                else
                    results.addAll(test(type, op, roles, law));
            }
            return results;
        }

        /* ------------------------------------------------------------------ the probe precondition */

        /**
         * THE DEGENERATE-PROBE PRECONDITION — the reasons a law's verdict would be evidence of nothing, or null when
         * the probe pair is sound. Checked per law, BEFORE the equation runs, and reported as {@code uncheckable}
         * (never {@code failed}): two sides that agree over operands the operation never received say nothing about
         * the carrier.
         *
         * <p>The motivating bug — {@code verify(plus?int<=int, [monoidic, commutative])} answered {@code commutative
         * FAILED} with {@code got=>'12'}, a str holding the partner operand, while {@code monoidic} still reported
         * {@code holds}. Equality of two expressions is no evidence when the operands are of the wrong type
         * ({@code "the probe is not a value of the carrier"}) or identical ({@code "the probe operands are
         * identical"}). There are exactly these two reasons, and no third: a product that equals one of its operands
         * is ABSORPTION, a property of the operation that a law is judged over (bool's {@code or} and {@code and}
         * collapse every probe to {@code true}/{@code false} and are still commutative), so it is never a broken
         * operand binding. Refusing it made {@code bool}'s {@code or} report {@code commutative} uncheckable on a
         * claim that in fact holds.
         */
        private static String degenerate(final Type type, final Obj a, final Obj b) {
            if (!ofCarrier(type, a) || !ofCarrier(type, b))
                return "the probe is not a value of the carrier";
            if (equal(a, b))
                return "the probe operands are identical";
            return null;
        }

        /**
         * Is this obj a value of the carrier? Two reads, both required: its tid sits on the carrier's path (a
         * refinement of the carrier's own address is still on that path), and the carrier's type test admits the
         * value's type — a {@code str("12")} registered as int's probe has neither, and a value that renders as a
         * different type (the {@code got=>'12'} symptom) is refused here rather than compared.
         */
        private static boolean ofCarrier(final Type type, final Obj operand) {
            if (null == type || null == type.tid() || !isValue(type, operand))
                return false;
            final fURI carrier = null == type.tid().basePath() ? type.tid() : type.tid().basePath();
            final fURI value = null == operand.tid() ? null
                    : (null == operand.tid().basePath() ? operand.tid() : operand.tid().basePath());
            if (null == value)
                return false;
            return value.equals(carrier) || value.hasPrefix(carrier) || carrier.hasPrefix(value);
        }

        /**
         * The probe an operation of the type is applied to, by carrier tid. A REGISTERED sample wins; with none, the
         * type's own declaration supplies one — the literal it binds under {@code zero} (then {@code one}/{@code id})
         * — so the tool answers for ANY mtron type, not only the eight base carriers whose samples are built in. A
         * type whose declaration names neither, or whose {@code zero}/{@code one} is not a value of the type, answers
         * {@code uncheckable "no probe"}: the algebra never said what element to try. {@code code} carries no scalar
         * sample: its operand is the type's own identity morphism, else {@code id()}.
         */
        private static Obj probe(final Type type) {
            if (null == type || null == type.tid())
                return null;
            final fURI carrier = null == type.tid().basePath() ? type.tid() : type.tid().basePath();
            final Obj sample = SAMPLES.get(carrier);
            if (null != sample)
                return sample;
            if (CODE_TID.basePath().equals(carrier)) {
                for (final Map.Entry<Obj, Obj> entry : algebra(type).jvm().entrySet()) {
                    if (!entry.getValue().isRec())
                        continue;
                    final Obj identity = value(type, entry.getValue().asRec(), ONE, ID);
                    if (null != identity)
                        return identity;
                }
                return instB(ID_INST_TID, lst());
            }
            return declaredProbe(type);
        }

        /**
         * THE FALLBACK PROBE — the type's declared {@code zero}/{@code bottom} (an additive carrier's 0), else its
         * {@code one}/{@code top}/{@code id} (a multiplicative carrier's 1). The role's value must BE a value of the
         * type: a role holding an operation (a {@code !*} pointer) is an operation, not a probe.
         */
        private static Obj declaredProbe(final Type type) {
            for (final List<String> roles : List.of(List.of(ZERO, BOTTOM), List.of(ONE, TOP, ID))) {
                for (final Map.Entry<Obj, Obj> entry : algebra(type).jvm().entrySet()) {
                    if (!entry.getValue().isRec())
                        continue;
                    final Obj candidate = value(type, entry.getValue().asRec(), roles.toArray(String[]::new));
                    if (isValue(type, candidate))
                        return candidate;
                }
            }
            return null;
        }

        /**
         * Is this obj a value of the type (not an operation, not nothing, not a type)?
         */
        private static boolean isValue(final Type type, final Obj candidate) {
            if (null == candidate || candidate.isNoObj() || candidate.isInst() || candidate.isCode() || candidate.isType())
                return false;
            try {
                return type.test(candidate.type());
            } catch (final Throwable e) {
                return false;
            }
        }

        private static Obj partner(final Type type, final Obj probe) {
            if (null == type || null == type.tid())
                return probe;
            final fURI carrier = null == type.tid().basePath() ? type.tid() : type.tid().basePath();
            final Obj partner = PARTNERS.get(carrier);
            return null == partner ? probe : partner;
        }

        /**
         * {@code lhs.op(rhs)} as mtron — the chain form the algebra tests write ({@code *a.plus(*b)}).
         */
        private static String apply(final String op, final String lhs, final String rhs) {
            return lhs + "." + op + "(" + rhs + ")";
        }

        /**
         * {@code lhs.op()} as mtron — a unary operation ({@code *a.neg()}).
         */
        private static String call(final String op, final String lhs) {
            return lhs + "." + op + "()";
        }

        /* ------------------------------------------------------------------ the verdict */

        private enum Verdict {
            holds, failed, unchecked, uncheckable
        }

        /**
         * One law's verdict, with the two OBJs the equation actually evaluated to — one per side, not one rendered
         * string. Both sides are kept because a single side is not evidence: the bug this shape exists to expose
         * reported {@code "got"=>'12'} for {@code plus?int<=int}, a str holding the partner operand, and no reader
         * could tell whether the lhs had computed anything at all.
         */
        private record Result(Law law, String role, Verdict verdict, String lhs, String rhs, Obj gotL, Obj gotR,
                              String reason) {

            static Result holds(final Law law, final String role, final String lhs, final String rhs,
                                final Obj gotL, final Obj gotR) {
                return new Result(law, role, Verdict.holds, lhs, rhs, gotL, gotR, null);
            }

            static Result failed(final Law law, final String role, final String lhs, final String rhs,
                                 final Obj gotL, final Obj gotR) {
                return new Result(law, role, Verdict.failed, lhs, rhs, gotL, gotR, null);
            }

            static Result unchecked(final Law law, final String role, final String reason) {
                return new Result(law, role, Verdict.unchecked, "", "", null, null, reason);
            }

            static Result uncheckable(final Law law, final String role, final String reason) {
                return new Result(law, role, Verdict.uncheckable, "", "", null, null, reason);
            }
        }

        /**
         * One law's verdict as a rec: {@code {law, role, lhs, rhs}} plus the evaluated pair {@code got=>
         * [lhs-result, rhs-result]} on a judged law, {@code reason} on an unchecked/uncheckable one. The pair is a
         * {@code lst} of the two {@code Obj}s — {@code got=>[35,35]} says both sides reached the operation, while
         * {@code got=>['12',35]} names the broken side immediately.
         */
        private static Obj toRec(final Result result) {
            final Map<Obj, Obj> map = new LinkedHashMap<>();
            map.put(uri(LAW), uri(result.law().name()));
            if (null != result.role())
                map.put(uri(ROLE), uri(result.role()));
            map.put(uri(LHS), str(result.lhs()));
            map.put(uri(RHS), str(result.rhs()));
            if (null != result.gotL() && null != result.gotR())
                map.put(uri(GOT), lst(result.gotL(), result.gotR()));
            if (null != result.reason())
                map.put(uri(REASON), str(result.reason()));
            return rec(map);
        }
    }
}