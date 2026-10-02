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
import studio.phaseshift.metatron.algebra.rewrite.RewriterBuilder;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.Sugar;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.mach.type.compiler.resolver.ScoringResolver;
import studio.phaseshift.metatron.isa.mach.type.compiler.rewriter.FixPointRewriter;
import studio.phaseshift.metatron.isa.mach.type.processor.SwarmProcessor;
import studio.phaseshift.metatron.isa.mach.type.processor.monad.StatefulMonad;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
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
import static studio.phaseshift.metatron.isa.m.space.stackSpace.STACK_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
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

    public static final Type BOOL_TYPE = Type.Builder.build().tid(BOOL_TID).vid(BOOL_TID).create();

    public static final Type BYTES_TYPE = Type.Builder.build().tid(BYTES_TID).vid(BYTES_TID).create();

    public static final Type INT_TYPE = Type.Builder.build().tid(INT_TID).vid(INT_TID).create();

    public static final Type REAL_TYPE = Type.Builder.build().tid(REAL_TID).vid(REAL_TID).create();

    public static final Type STR_TYPE = Type.Builder.build().tid(STR_TID).vid(STR_TID).create();

    public static final Type URI_TYPE = Type.Builder.build().tid(URI_TID).vid(URI_TID).create();

    public static final Type REL_TYPE = Type.Builder.build().tid(REL_TID).vid(REL_TID).create();

    public static final Type LST_TYPE = Type.Builder.build()
            .tid(LST_TID)
            .vid(LST_TID).create();

    public static final Type REC_TYPE = Type.Builder.build().tid(REC_TID).vid(REC_TID).create();


    /**
     * Cache for fully resolved instructions when ALL arguments are literals (non-call objects).
     * Key: "lhsType|instBasePath|args" - includes lhs type, instruction name, and literal args
     * Value: The fully resolved instruction (safe to reuse since literal args don't depend on lhs)
     * Call args (inst, code, type) are NOT cached because they depend on the lhs value.
     */
    // Map<String, Inst> RESOLUTION_CACHE = new ConcurrentHashMap<>();
    // ThreadLocal<Boolean> REWRITE_MODE = ThreadLocal.withInitial(() -> false);

    // Uri ARGS_URI = uri(ARGS_FURI);
    public static final Type INST_TYPE = Type.Builder.build().tid(M_ISA_INST_TID).vid(M_ISA_INST_TID).create();

    public static final Type CODE_TYPE = Type.Builder.build().tid(CODE_TID).vid(CODE_TID).create();
    public static final Type NOOBJ_TYPE = Type.Builder.build().tid(NOOBJ_TID.zero()).vid(NOOBJ_TID.zero()).predicate((lhs, inst) -> noobj()).create();

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
    public static final fURI REMOVE_INST_TID = M_ISA_INST_TID.extend("remove");
    public static final fURI UPDATE_INST_TID = M_ISA_INST_TID.extend("update");
    public static final fURI WHERE_INST_TID = M_ISA_INST_TID.extend("where");
    public static final fURI GROUP_INST_TID = M_ISA_INST_TID.extend("group");
    public static final fURI ELSE_INST_TID = M_ISA_INST_TID.extend("else");
    public static final fURI END_INST_TID = M_ISA_INST_TID.extend("end");
    public static final fURI THREAD_INST_TID = M_ISA_INST_TID.extend("thread");
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

    public static Type AUTHORITY_TYPE;
    public static final Type ALL_TYPE = Type.Builder.build().tid(ALL).vid(ALL).create();
    public static final Type ALL_MAYBE_TYPE = Type.Builder.build().tid(ALL.maybe()).vid(ALL.maybe()).create();
    public static final Type SPACE_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(SPACE_TID)
            .isaPredicate(rec(
                    uri(PATTERN), URI_TYPE,
                    uri(QPROC).maybe(), lst(QPROC_TYPE.maybe().asType()),
                    uri(ROUTE).maybe(), rec(T(URI_TID.maybe()), URI_TYPE),
                    uri(SCHEMA).maybe(), T(INSTSET_TID) // nominal-only: avoids structural recursion in InstSet
            )).create();

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
                            uri(HITS), INT_TYPE, uri(MISSES), INT_TYPE, uri(HIT_RATE), REAL_TYPE)))
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
                                        uri(DESC), "expression-level metadata: whole-expression dom/rng, inst count, and the rewrite chain — the next stage's explanation ({?}), none when the original submission is last",
                                        uri(PER_INST), "lst of per-instruction stage recs (op/dom/rng/args/f/form/c_dom/c_rng) — args are the real objs, not their string renderings; f is the wrapped function ('<j>' = java/opaque)"),
                                "a structured explanation of a resolved expression produced by explain()",
                                "1.plus(2).explain()>>desc>>rng     [-- int — the type flowing out of the expression --]",
                                "1.plus(2).explain()>>per_inst      [-- per-inst recs: [op=>start,args=>[1],form=>initial,c_dom=>0,c_rng=>1], [op=>plus,dom=>int,rng=>int,args=>[2],f=>'<j>',form=>mapper,c_dom=>1,c_rng=>1] --]",
                                "1.plus(2).explain()>>format         [-- the str::T text table --]"),
                        docWrap(SPACE_TYPE, null, null, Map.of(
                                        uri(PATTERN), "the uri address region the space will manage",
                                        uri(QPROC).maybe(), "query processors (qproc) that augment space capabilities",
                                        uri(ROUTE).maybe(), "a secondary router after global router for inter and intra-space redirects",
                                        uri(SCHEME).maybe(), "an instset defining the type structure of the space objs"),
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
                        docWrap(STACK_SPACE_TYPE, "a thread local stack used for global variables and machine inst call frames",
                                "2.to(a).plus(from(a))     [-- 4 via writing/reading a         --]",
                                "a->2+*a                   [-- 4 via sugar'd writing/reading a --]"),
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
                                        .constructor(QCollection::subq)
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
                                "cs:src/#?lockq -> noobj                                             [-- release --]"),
                        ////////////////////////////////////////////////////////////////////////////
                        docWrap(AUTHORITY_TYPE = Type.Builder.build()
                                .tid(URI_TID)
                                .vid(AUTHORITY_TID)
                                .predicate((lhs, inst) -> {
                                    final fURI uri = inst.arg(0).uriValue();
                                    return (uri.hasHost() && !uri.hasScheme() && uri.c().isOne() && !uri.hasPoly() && uri.pathLength() == 0 && uri.qMap().isEmpty()) ?
                                            inst.arg(0) : uri().c(cInt.ZERO());
                                }).create(), "a uri containing only a host:port component with port being optional")),
                uri(CONST), lst(
                        docWrap(noobj(), "a no object. if an inst domain is no zeroable (e.g. {0}/{?}/{*}) then the inst will not evaluate.")
                        /*docWrap(NONE, "a token uri denoting nothing. used for deleting obj in space.")*/),
                uri(INST), lst(Stream.of(
                        Bool.BoolType.insts().stream(),
                        Bytes.BytesType.insts().stream(),
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
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("profile_analysis"),
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
                                    bundledCode.add(instC(M_ISA_INST_TID.extend("profile_analysis").dom(ALL.maybe()).rng(PROFILING_TID),
                                            lst(),
                                            (lhs, inst) -> profileTable(precedingCode)));
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
                                code -> code.selfJVM(
                                        RewriterBuilder.search(code.insts())
                                                .match(instA(ID_INST_TID).insts())
                                                .rewrite(x -> List.of())).asCode()), "removes identity instructions: \\(g \\cdot \\mathrm{id} \\leadsto g\\)"),

                        // Flatten nested map instructions
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("map_nest"),
                                code -> code.selfJVM(
                                        RewriterBuilder.search(code.insts())
                                                .match(instB(MAP_INST_TID.dom(ALL.maybeSome()).rng(ALL.maybeSome()), lst(instB(MAP_INST_TID.dom(ALL.maybeSome()).rng(ALL.maybeSome()), lst(ALL_TYPE)))).insts())
                                                .repeat()
                                                .rewrite(map -> map.values().stream().map(objs -> objs.arg(0).asInst()).toList())).asCode()), "flattens nested map instructions: \\(\\mathrm{map}(f) \\cdot \\mathrm{map}(g) \\leadsto \\mathrm{map}(f \\cdot g)\\)"),
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("map_inst"),
                                code -> code.selfJVM(
                                        RewriterBuilder.search(code.insts())
                                                .match(instB(MAP_INST_TID.dom(ALL.maybeSome()).rng(ALL.maybeSome()), lst(instB(M_ISA_INST_TID.extend("#"), lst(T(ALL.maybeSome()))))).insts())
                                                .repeat()
                                                .rewrite(map -> map.values().stream().map(objs -> objs.arg(0).asInst()).toList())).asCode()), "flattens a mapping of an inst to the inst: \\(f \\in \\mathrm{inst} \\Rightarrow \\mathrm{map}(f) \\leadsto f\\)"),
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

                        // Optimize plus(0) for any PlusMonoid (identity)
                        // Pattern: .plus(0) → identity (no-op)
                        // DISABLED: This rewrite is interfering with Rec operations (RecTest.testAt() failures)
                        // The rewrite removes .plus(0) operations that are needed for record access patterns

                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("plus_zero"),
                                code -> code.selfJVM(
                                        RewriterBuilder.search(code.insts())
                                                .match(cachedMatch(PLUS_ZERO_MATCH, () -> List.of(instB(PLUS_INST_TID, lst()))))
                                                .rewrite(map -> {
                                                    final Inst plusInst = map.values().iterator().next();
                                                    if (plusInst.args().count() > 0 && plusInst.arg(0).isInt() && plusInst.arg(0).asInt().intValue() == 0) {
                                                        // plus(0) is identity, remove it
                                                        return List.of();
                                                    }
                                                    return List.of(plusInst);
                                                })).asCode()), "removes plus(0) — the additive identity of a plus-monoid: \\(x + 0 \\leadsto x\\)"),

                        // Optimize mult(1) for integers (identity)
                        // Pattern: .mult(1) → identity (no-op)
                        // DISABLED: This rewrite is interfering with list operations

                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("mult_one"),
                                code -> code.selfJVM(
                                        RewriterBuilder.search(code.insts())
                                                .match(cachedMatch(MULT_ONE_MATCH, () -> List.of(instB(MULT_INST_TID, lst()))))
                                                .rewrite(map -> {
                                                    final Inst multInst = map.values().iterator().next();
                                                    if (multInst.args().count() > 0 && multInst.arg(0).isInt() && multInst.arg(0).asInt().intValue() == 1) {
                                                        // mult(1) is identity, remove it
                                                        return List.of();
                                                    }
                                                    return List.of(multInst);
                                                })).asCode()), "removes mult(1) — the multiplicative identity of a mult-monoid: \\(x \\cdot 1 \\leadsto x\\)"),

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

                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("explain_analysis"),
                                code -> {
                                    final List<Inst> insts = code.insts();
                                    if (insts.isEmpty() || insts.size() < 2) return code;
                                    final Tuple.Pair<Integer, Inst> explainInst = IteratorUtil.indexedStream(insts.iterator()).filter(i -> i.get1().tid().basePath().equals(EXPLAIN_INST_TID)).findAny().orElse(null);
                                    if (null == explainInst || explainInst.get0() <= 0) return code;
                                    final List<Inst> preceding = new ArrayList<>(insts.subList(0, explainInst.get0()));
                                    // capture the pre-collapse code by CLOSURE, not as an inst arg — an arg
                                    // (even block-wrapped) is resolved/collapsed by the outer resolve, which
                                    // would strip id()/plus(0)/mult(1) before explain can be computed.
                                    final Code precedingCode = MCode.of(preceding).resolve(noobj());
                                    final List<Inst> bundledCode = new ArrayList<>();
                                    // dom is maybe (not maybeSome), mirroring profile_analysis: one lhs in, exactly
                                    // one report out. see profile_analysis — a gather dom double-applied the report,
                                    // which here merged into a single explanation{2} and a double-width >>format.
                                    bundledCode.add(instC(M_ISA_INST_TID.extend("explain_analysis").dom(ALL.maybe()).rng(EXPLANATION_TID),
                                            lst(),
                                            (lhs, inst) -> explainRec(precedingCode)));
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
     * Build the explanation::T rec of the instructions in {@code code}: expression-level
     * metadata (desc), per-instruction stages (per_inst), and a lazily constructed format
     * (today's text table). Terminal-free — suitable for use in rewrites and
     * non-interactive contexts.
     */
    private static Rec explainRec(final Code code) {
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
        return rec(
                mutableMap(uri(FORMAT), auto_(instLambda(ALL.maybe(), STR_TID, (lhs, inst) -> str(explainTable(code)))),
                        uri(DESC), rec(
                                uri(DOM), domRngValue(insts.getFirst().dom()),
                                uri(RNG), domRngValue(insts.getLast().rng()),
                                uri(INSTS), jnt(insts.size()),
                                // rewrite-stage chain: a later stage's explanation will point at this rec (the
                                // original submitted expression's); the original terminates the chain (noobj)
                                uri(REWRITE), noobj()),
                        uri(PER_INST), lst(perInstRecs)), EXPLANATION_TID, null);
    }

    /**
     * Time the resolve (compile) and apply (evaluate) stages of {@code code} after a single
     * warm-up run, and return a small text table of min/max stage times. Terminal-free —
     * suitable for use in rewrites and non-interactive contexts.
     */
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
        final TypeGraph graph = TypeGraph.global();
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
            final Code resolved = ScoringResolver.resolveCode(noobj(), rewritten);
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
                //   Sugar.prefix("=?=", List.of(WHERE_INST_TID), 1),
                Sugar.prefix("%==", List.of(GROUP_INST_TID), 1),
                Sugar.prefix("==", List.of(SELECT_INST_TID), 1),
                Sugar.prefix("?~", List.of(IS_INST_TID, SORTA_INST_TID), 1),
                Sugar.prefix("?=", List.of(IS_INST_TID, EQ_INST_TID), 1),
                Sugar.prefix("?>=", List.of(IS_INST_TID, GTE_INST_TID), 1),
                Sugar.prefix("?>", List.of(IS_INST_TID, GT_INST_TID), 1),
                Sugar.prefix("?<=", List.of(IS_INST_TID, LTE_INST_TID), 1),
                Sugar.prefix("?<", List.of(IS_INST_TID, LT_INST_TID), 1),
                Sugar.prefix("?!=", List.of(IS_INST_TID, NEQ_INST_TID), 1),
                Sugar.prefix("?~", List.of(SORTA_INST_TID), 1),
                Sugar.prefix("?", List.of(ISA_INST_TID), 1),
                Sugar.prefix("!@", List.of(AUTO_AT_INST_TID), 1),
                Sugar.prefix("@", List.of(AT_INST_TID), 1),
                Sugar.prefix("|", List.of(BLOCK_INST_TID), 1),
                Sugar.wrap("_/", "\\_", List.of(WITHIN_INST_TID), 1),
                Sugar.wrap("=", "=>", List.of(AS_INST_TID), 1),
                Sugar.prefix("_", List.of(ID_INST_TID), 0),
                Sugar.prefix("* ", List.of(MULT_INST_TID), 1),
                Sugar.prefix("*", List.of(FROM_INST_TID), 1),
                Sugar.prefix(">|", List.of(BARRIER_INST_TID), 1),
                Sugar.prefix(">|", List.of(BARRIER_INST_TID), 0),
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
                Sugar.prefix("^*", List.of(M_ISA_INST_TID.extend("auto_to")), 0),
                Sugar.prefix("!*", List.of(AUTO_FROM_INST_TID), 1),
                Sugar.prefix("!", List.of(AUTO_INST_TID), 1),
                Sugar.prefix("~", List.of(THREAD_INST_TID), 1),
                Sugar.infix(" & ", List.of(AND_INST_TID)),
                Sugar.infix(" | ", List.of(OR_INST_TID))));
    }
}