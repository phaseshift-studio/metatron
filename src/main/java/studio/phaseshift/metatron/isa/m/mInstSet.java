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

package studio.phaseshift.metatron.isa.m;

import studio.phaseshift.metatron.algebra.MultMonoid;
import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.algebra.rewrite.Rewriter;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.Q.Q_TYPE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.space.memSpace.MEM_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.m.space.metaSpace.META_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.m.space.stackSpace.STACK_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Bytes.BYTES_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Code.CODE_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Fail.FAIL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Rel.REL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instA;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

@InstSet.JREService(vid = "/m")
public class mInstSet extends AbstractInstSet {

    public static final fURI M_ISA_TID = f("/m");
    public static final fURI MTRON_TID = f("/m");
    // /m/obj
    public static final fURI FAIL_TID = M_ISA_TID.extend("fail");
    public static final fURI BOOL_TID = M_ISA_TID.extend("bool");
    public static final fURI BYTES_TID = M_ISA_TID.extend("bytes");
    public static final fURI INT_TID = M_ISA_TID.extend("int");
    public static final fURI REAL_TID = M_ISA_TID.extend("real");
    public static final fURI STR_TID = M_ISA_TID.extend("str");
    public static final fURI URI_TID = M_ISA_TID.extend("uri");
    public static final fURI REL_TID = M_ISA_TID.extend("rel");
    public static final fURI LST_TID = M_ISA_TID.extend("lst");
    public static final fURI REC_TID = M_ISA_TID.extend("rec");
    public static final fURI M_ISA_INST_TID = M_ISA_TID.extend("inst");
    public static final fURI M_ISA_REWRITE_TID = M_ISA_INST_TID.extend("rewrite");
    public static final fURI INSTSET_TID = M_ISA_TID.extend("instset");
    public static final fURI OBJS_TID = M_ISA_TID.extend("objs");
    public static final fURI TYPE_TID = M_ISA_TID.extend("type");
    public static final fURI CODE_TID = M_ISA_TID.extend("code");
    public static final fURI NOOBJ_TID = f("noobj");
    public static final fURI ALL_STAR = ALL.maybeSome();
    public static final fURI SPACE_TID = M_ISA_TID.extend("space");
    /// ////////////////////////////////////////////////////////
    public static final fURI LIKE_INST_TID = M_ISA_INST_TID.extend("like");
    public static final fURI CAUSE_INST_TID = M_ISA_INST_TID.extend("cause");
    public static final fURI NATIVE_INST_TID = M_ISA_INST_TID.extend("native");
    public static final fURI SERIALIZE_INST_TID = M_ISA_INST_TID.extend("serialize");
    public static final fURI ID_INST_TID = M_ISA_INST_TID.extend("id");
    public static final fURI DEDUP_INST_TID = M_ISA_INST_TID.extend("dedup");
    public static final fURI EXPLAIN_INST_TID = M_ISA_INST_TID.extend("explain");
    public static final fURI HAS_INST_TID = M_ISA_INST_TID.extend("has");
    public static final fURI EVAL_INST_TID = M_ISA_INST_TID.extend("eval");
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
    public static final fURI MAP_INST_TID = M_ISA_INST_TID.extend("map");
    public static final fURI PARENT_INST_TID = M_ISA_INST_TID.extend("parent");
    public static final fURI FILTER_INST_TID = M_ISA_INST_TID.extend("filter");
    public static final fURI SIDE_INST_TID = M_ISA_INST_TID.extend("side");
    public static final fURI TO_INST_TID = M_ISA_INST_TID.extend("to");
    public static final fURI FROM_INST_TID = M_ISA_INST_TID.extend("from");
    public static final fURI REF_INST_TID = M_ISA_INST_TID.extend("ref");
    public static final fURI SPLIT_INST_TID = M_ISA_INST_TID.extend("split"); // -<
    public static final fURI CHOOSE_INST_TID = M_ISA_INST_TID.extend("choose"); // -<|
    public static final fURI CHAIN_TID = M_ISA_INST_TID.extend("chain"); // -<;
    public static final fURI MERGE_INST_TID = M_ISA_INST_TID.extend("merge");
    public static final fURI FILL_TID = M_ISA_INST_TID.extend("fill");
    public static final fURI FIND_TID = M_ISA_INST_TID.extend("find");
    public static final fURI RMERGE_TID = M_ISA_INST_TID.extend("rmerge");
    public static final fURI RANGE_INST_TID = M_ISA_INST_TID.extend("range");
    public static final fURI WITHIN_INST_TID = M_ISA_INST_TID.extend("within");
    public static final fURI AUTO_INST_TID = M_ISA_INST_TID.extend("auto");
    public static final fURI AUTO_FROM_INST_TID = M_ISA_INST_TID.extend("auto_from");
    public static final fURI AUTO_AT_INST_TID = M_ISA_INST_TID.extend("auto_at");
    public static final fURI BLOCK_INST_TID = M_ISA_INST_TID.extend("block");
    public static final fURI RNG_INST_TID = M_ISA_INST_TID.extend("rng");
    public static final fURI DOM_INST_TID = M_ISA_INST_TID.extend("dom");
    public static final fURI TID_INST_TID = M_ISA_INST_TID.extend("tid");
    public static final fURI VID_INST_TID = M_ISA_INST_TID.extend("vid");
    public static final fURI TYPE_INST_TID = M_ISA_INST_TID.extend("type");
    public static final fURI GET_INST_TID = M_ISA_INST_TID.extend("get");
    public static final fURI FAILURE_INST_TID = M_ISA_INST_TID.extend("failure");
    public static final fURI AS_INST_TID = M_ISA_INST_TID.extend("as");
    public static final fURI REVERSE_INST_TID = M_ISA_INST_TID.extend("reverse");
    public static final fURI CLOSE_INST_TID = M_ISA_INST_TID.extend("close");
    public static final fURI REPEAT_INST_TID = M_ISA_INST_TID.extend("repeat");
    public static final fURI AT_INST_TID = M_ISA_INST_TID.extend("at");
    public static final fURI IS_INST_TID = M_ISA_INST_TID.extend("is");
    public static final fURI ISA_INST_TID = M_ISA_INST_TID.extend("isa");
    public static final fURI OR_INST_TID = M_ISA_INST_TID.extend("or");
    public static final fURI AND_INST_TID = M_ISA_INST_TID.extend("and");
    public static final fURI MATCHES_INST_TID = M_ISA_INST_TID.extend("matches");
    public static final fURI EQ_INST_TID = M_ISA_INST_TID.extend("eq");
    public static final fURI NEQ_INST_TID = M_ISA_INST_TID.extend("neq");
    public static final fURI GT_INST_TID = M_ISA_INST_TID.extend("gt");
    public static final fURI REGEX_INST_TID = M_ISA_INST_TID.extend("regex");
    public static final fURI ORDER_INST_TID = M_ISA_INST_TID.extend("order");
    public static final fURI LT_INST_TID = M_ISA_INST_TID.extend("lt");
    public static final fURI GTE_INST_TID = M_ISA_INST_TID.extend("gte");
    public static final fURI LTE_INST_TID = M_ISA_INST_TID.extend("lte");
    public static final fURI NOT_INST_TID = M_ISA_INST_TID.extend("not");
    public static final fURI TAKE_INST_TID = M_ISA_INST_TID.extend("take");
    public static final fURI SKIP_INST_TID = M_ISA_INST_TID.extend("skip");
    public static final fURI BARRIER_INST_TID = M_ISA_INST_TID.extend("barrier");
    public static final fURI REIFY_INST_TID = M_ISA_INST_TID.extend("reify");
    public static final fURI SELECT_INST_TID = M_ISA_INST_TID.extend("select");
    public static final fURI UPDATE_INST_TID = M_ISA_INST_TID.extend("update");
    public static final fURI WHERE_INST_TID = M_ISA_INST_TID.extend("where");
    public static final fURI GROUP_INST_TID = M_ISA_INST_TID.extend("group");
    public static final fURI ELSE_INST_TID = M_ISA_INST_TID.extend("else");
    public static final fURI END_INST_TID = M_ISA_INST_TID.extend("end");
    public static final fURI THREAD_INST_TID = M_ISA_INST_TID.extend("thread");
    public static final fURI IMPORT_INST_TID = M_ISA_INST_TID.extend("import");
    public static final fURI SOURCE_INST_TID = M_ISA_INST_TID.extend("source");
    public static final fURI SWAP_TID = M_ISA_INST_TID.extend("swap");
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
    /// ////////////
    /// ////////////
    public static final fURI POLY_TID = M_ISA_TID.extend("poly");
    public static final fURI MONO_TID = M_ISA_TID.extend("mono");
    public static final fURI NUM_TID = M_ISA_TID.extend("num");

    //public static final Set<fURI> MARKER_TYPES = Set.of(MONO_TID, POLY_TID, NUM_TID);
    public static final Set<fURI> BASE_TYPES = Set.of(
            FAIL_TID, BOOL_TID, BYTES_TID, INT_TID, REAL_TID,
            STR_TID, URI_TID, REL_TID,
            LST_TID, REC_TID, M_ISA_INST_TID,
            CODE_TID, OBJS_TID, NOOBJ_TID);

    public static final Type SPACE_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(SPACE_TID)
            .isaPredicate(rec(
                    uri(PATTERN), URI_TYPE,
                    uri(QSTRING).maybe(), lst(Q_TYPE),
                    uri(SCHEMA).maybe(), REC_TYPE)).create();

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


    public mInstSet() {
        super(new LinkedHashMap<>(Map.of(uri(PATTERN), uri(M_ISA_TID.extend(ALL)))), M_ISA_TID, M_ISA_TID);
    }

    public void setup() {
        this.selfTID(INSTSET_TID);
        this.jvm().putAll(new LinkedHashMap<>(Map.of(
                uri(PATTERN), uri(M_ISA_TID.extend(ALL)),
                uri(CONST), lst(docWrap(noobj(), "a no object")),
                uri(TYPE), lst(
                        //  docWrap(MONO_TYPE, "an atomic obj"),
                        //  docWrap(POLY_TYPE, "a obj composed of other objs"),
                        //  docWrap(NOOBJ_TYPE, "a no object"),
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
                        docWrap(INSTSET_TYPE, "an aggregate of types, insts, consts, rewrites, and sugars structuring a domain of discourse"),
                        docWrap(INST_TYPE, "a call with apply defined by an lhs obj, an poly of args, and an body of code",
                                "abc(?int::T,?int::T){ *<0> + *<1> }        [-- position args inst    --]",
                                "abc(a=>?int::T,b=>?int::T){ *a + *b }      [-- named args inst       --]",
                                "abc(a=>else(1),b=>else(2)){ *a + *b }      [-- default args inst     --]",
                                "abc(a=>?int::T,b=>?>3.else(4)){ *a + *b }  [-- contextual args inst  --]",
                                "abc(a=>?int::T,b=>|(*a +10)){ _.*b }       [-- dependent arg inst    --]",
                                "(_,_){*<0>+*<1>}                           [-- 2-arg lambda inst     --]"),
                        docWrap(CODE_TYPE, "a call with apply defined by an lhs obj and a sequence of insts",
                                "12.plus(mult(_))            [-- 2-depth code           --]",
                                "1-<[_,-<[_,_]>-]>-.sum()    [-- sugar'd branching code --]"),
                        //  docWrap(OBJS_TYPE, "an ordered sequence poly of objs and noobjs"),
                        docWrap(FAIL_TYPE, "a reified exception handling obj that can be caught",
                                "fail::[ouch]                [-- a fail obj with message --]",
                                "fail::[doh] + 2             [-- plus(2) skipped over    --]",
                                "fail::[dah] + 2 + catch(9)  [-- fail flattened to 9       --]"),
                        /// ///////////////////////////////////
                        docWrap(SPACE_TYPE, "storage systems structured as uri addressed objs"),
                        docWrap(MEM_SPACE_TYPE, "a in-memory space with objs indexed by a topic trie"),
                        docWrap(STACK_SPACE_TYPE, "the machine's thread local stack exposed as a space",
                                "2.to(a).plus(from(a))     [-- 4 via writing/reading a         --]",
                                "a->2+*a                   [-- 4 via sugar'd writing/reading a --]"),
                        META_SPACE_TYPE,
                        docWrap(Q_TYPE, """
                                qprocs (query processors) are optional space components.
                                qproc behaviors are driven by a qprocs specified uri ?-query pattern.
                                not all spaces have the same set of attached qprocs and thus, qprocs must
                                be attached to a space before use. a space's qprocs are accessible at
                               
                                     \\(\\texttt{*space/vid/q => lst[q]::T}\\)
                                """),
                        /// ///////////////////////////////////
                        docWrap(SUBQ_TYPE, "addr publish-subscribe qproc",
                                "/usr/ai/+?subq -> |print('ai update: ${_}') [-- /usr/ai subtree watch  --]",
                                "a?subq         -> |(>>1+1.println(_).to(a)) [-- infinite incr loop     --]",
                                "*+?subq                                     [-- all subs for addr tree --]"),
                        SUB_TYPE,
                        docWrap(TYPEQ_TYPE, "addr type constraint qproc",
                                "abc?typeq -> int::T       [-- abc can only reference a single integer --]",
                                "abc?typeq -> 'not an int' [-- yields a fail::T --]"),
                        docWrap(DOCQ_TYPE, "addr documentation qproc",
                                "*docq?docq [-- this documentation --]",
                                "*int?docq  [-- documentation for int::T --]"),
                        docWrap(INCRQ_TYPE, "a query that returns an increment"),
                        docWrap(CONSTQ_TYPE, "a query that returns a constant")),
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
                        Type.TypeType.insts().stream(),
                        SpaceType.insts().stream(),
                        ObjType.insts().stream(),
                        NoObj.NoObjType.insts().stream()
                ).flatMap(i -> i)),
                uri(REWRITE), lst(
                        // Remove identity instructions (no-op)
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("id_removal"),
                                code -> code.selfJVM(
                                        Rewriter.search(code.insts())
                                                .match(instA(ID_INST_TID).insts())
                                                .rewrite(x -> List.of())).asCode()), "removes identity instructions"),

                        // Flatten nested map instructions
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("map_nest"),
                                code -> code.selfJVM(
                                        Rewriter.search(code.insts())
                                                .match(instB(MAP_INST_TID, lst(instA(MAP_INST_TID))).insts())
                                                .repeat()
                                                .rewrite(map -> map.values().stream().map(objs -> objs.arg(0).asInst()).toList())).asCode()), "flattens nested map instructions"),

                        // Eliminate else() after non-maybe instruction (dead code)
                        // Pattern: .count().else(x) → .count() (count always returns a value)
                        InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("else_after_count"),
                                code -> code.selfJVM(
                                        Rewriter.search(code.insts())
                                                .match(List.of(instA(COUNT_INST_TID), instA(ELSE_INST_TID)))
                                                .rewrite(map -> {
                                                    final List<Inst> matched = map.values().stream().toList();
                                                    // COUNT always returns int, so ELSE is dead code
                                                    return List.of(matched.getFirst());
                                                })).asCode()),

                        // Optimize plus(0) for any PlusMonoid (identity)
                        // Pattern: .plus(0) → identity (no-op)
                        // DISABLED: This rewrite is interfering with Rec operations (RecTest.testAt() failures)
                        // The rewrite removes .plus(0) operations that are needed for record access patterns

                        InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("plus_zero"),
                                code -> code.selfJVM(
                                        Rewriter.search(code.insts())
                                                .match(List.of(instB(PLUS_INST_TID, lst(is_(eq_(zero_())).tryToInst()))))
                                                .rewrite(map -> {
                                                    final Inst plusInst = map.values().iterator().next();
                                                    if (plusInst.args().count() > 0) {
                                                        if (plusInst.arg(0) instanceof PlusMonoid<?> && ((PlusMonoid<?>) plusInst.arg(0)).isZero()) {
                                                            // plus(0) is identity, remove it
                                                            return List.of();
                                                        }
                                                    }
                                                    return List.of(plusInst);
                                                })).asCode()),


                        // Optimize mult(1) for integers (identity)
                        // Pattern: .mult(1) → identity (no-op)
                        // DISABLED: This rewrite is interfering with list operations

                        InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("mult_one"),
                                code -> code.selfJVM(
                                        Rewriter.search(code.insts())
                                                .match(List.of(mult_(is_(eq_(one_()))).tryToInst().as()))
                                                .rewrite(map -> {
                                                    final Inst multInst = map.values().iterator().next();
                                                    if (multInst.args().count() > 0) {
                                                        if (multInst.arg(0) instanceof MultMonoid<?> && ((MultMonoid<?>) multInst.arg(0)).isOne()) {
                                                            // mult(1) is identity, remove it
                                                            return List.of();
                                                        }
                                                    }
                                                    return List.of(multInst);
                                                })).asCode()),


                        // Collapse identical branches in split-merge by summing coefficients
                        // Pattern: -<[inst,inst,...]>- → inst{n}
                        // This leverages the ring structure where identical branches collapse on merge
                        // Note: Only applies to split-merge pairs, as split alone creates superposition
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("split_merge_collapse"),
                                code -> code.selfJVM(
                                        Rewriter.search(code.insts())
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
                                                })).asCode()), "applies abelian monoid law on split code paths"),

                        // Left factoring: pull out common prefix from split branches
                        // Pattern: a-<[b.c.d, b.c.e]>- → a.b.c-<[d, e]>-
                        // This reduces clock cycles by executing common prefix once
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("split_merge_left_factor"),
                                code -> code.selfJVM(
                                        Rewriter.search(code.asCode().insts())
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
                                                })).asCode()), "leverages distributive ring law to pull common monoidally bound components to the right"),

                        // Right factoring: pull out common suffix from split branches
                        // Pattern: a-<[b.d, c.d]>- → a-<[b, c]>-.d
                        // This reduces clock cycles by executing common suffix once
                        docWrap(InstSet.Helper.rewriter(M_ISA_REWRITE_TID.extend("split_merge_right_factor"),
                                code -> code.selfJVM(
                                        Rewriter.search(code.asCode().insts())
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
                                                })).asCode()), "leverages distributive ring law to pull common monoidally bound components to the left")))));
        docWrap(this, "the core instruction set of metatron containing the base types and useful instructions to manipulate them");
        super.setup();
    }


    @Override
    public void close() {
        // do nothing
    }

    @Override
    public Set<Tuple.Triplet<Tuple.Pair<String, String>, List<fURI>, Integer>> sugars() {
        return new LinkedHashSet<>(List.of(
                Tuple.Triplet.with(Tuple.Pair.with("select", null), List.of(SELECT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("where", null), List.of(WHERE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("update", null), List.of(UPDATE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?==", null), List.of(WHERE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("%==", null), List.of(GROUP_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("==", null), List.of(SELECT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("=", "=>"), List.of(UPDATE_INST_TID), 2),
                Tuple.Triplet.with(Tuple.Pair.with("=~", null), List.of(MATCHES_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?=~", null), List.of(IS_INST_TID, MATCHES_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?=", null), List.of(IS_INST_TID, EQ_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?>=", null), List.of(IS_INST_TID, GTE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?>", null), List.of(IS_INST_TID, GT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?<=", null), List.of(IS_INST_TID, LTE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?<", null), List.of(IS_INST_TID, LT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?!=", null), List.of(IS_INST_TID, NEQ_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?", null), List.of(ISA_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("!@", null), List.of(AUTO_AT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("@", null), List.of(AT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("|", null), List.of(BLOCK_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("_/", "\\_"), List.of(WITHIN_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("_", null), List.of(ID_INST_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with("* ", null), List.of(MULT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("*", null), List.of(FROM_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">|", null), List.of(BARRIER_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">|", null), List.of(BARRIER_INST_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with(">>-", null), List.of(RNG_INST_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with(">-", null), List.of(MERGE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">-", null), List.of(MERGE_INST_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with("-<|", null), List.of(CHOOSE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("-<", null), List.of(SPLIT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("->", null), List.of(REF_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">>=", null), List.of(UPDATE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">>", null), List.of(RSHIFT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">>", null), List.of(RSHIFT_INST_TID), 0),
                //Tuple.Triplet.with(Tuple.Pair.with("<<", null), List.of(LSHIFT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("<<", null), List.of(LSHIFT_INST_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with("++", null), List.of(MPLUS_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("+", null), List.of(PLUS_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("-", null), List.of(MINUS_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(";", null), List.of(END_INST_TID), 0),
                //  Tuple.Triplet.with(Tuple.Pair.with("(", ")"), List.of(GET_INST_TID), 1),
                //Tuple.Triplet.with(Tuple.Pair.with("./", null), List.of(GET_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("^*", null), List.of(M_ISA_INST_TID.extend("auto_to")), 0),
                Tuple.Triplet.with(Tuple.Pair.with("!*", null), List.of(AUTO_FROM_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("!", null), List.of(AUTO_INST_TID), 1)));
        //   Tuple.Triplet.with(Tuple.Pair.with("^", null), List.of(PARENT_INST_TID), 0)));
        //  Tuple.Triplet.with(Tuple.Pair.with("^", null), List.of(LIFT_INST_TID), 1)));
    }
}