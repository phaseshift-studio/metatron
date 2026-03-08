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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.util.Tuple;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.Q;
import static studio.phaseshift.metatron.furi.Q.Q_TYPE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
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
import static studio.phaseshift.metatron.isa.m.type.NoObj.NOOBJ_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Rel.REL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

@InstSet.JREService(tid = "/m")
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
    public static final fURI INST_TID = M_ISA_TID.extend("inst");
    public static final fURI OBJS_TID = M_ISA_TID.extend("objs");
    public static final fURI TYPE_TID = M_ISA_TID.extend("type");
    public static final fURI CODE_TID = M_ISA_TID.extend("code");
    public static final fURI NOOBJ_TID = f("noobj");
    public static final fURI ALL_STAR = ALL.maybeSome();
    public static final fURI SPACE_TID = M_ISA_TID.extend("space");
    /// ////////////////////////////////////////////////////////
    public static final fURI LIKE_INST_TID = INST_TID.extend("like");
    public static final fURI CAUSE_INST_TID = INST_TID.extend("cause");
    public static final fURI NATIVE_INST_TID = INST_TID.extend("native");
    public static final fURI SERIALIZE_INST_TID = INST_TID.extend("serialize");
    public static final fURI ID_INST_TID = INST_TID.extend("id");
    public static final fURI DEDUP_INST_TID = INST_TID.extend("dedup");
    public static final fURI EXPLAIN_INST_TID = INST_TID.extend("explain");
    public static final fURI HAS_INST_TID = INST_TID.extend("has");
    public static final fURI EVAL_INST_TID = INST_TID.extend("eval");
    public static final fURI FORK_INST_TID = INST_TID.extend("fork");
    public static final fURI CATCH_INST_TID = INST_TID.extend("catch");
    public static final fURI APPLY_INST_TID = INST_TID.extend("apply");
    public static final fURI START_INST_TID = INST_TID.extend("start");
    public static final fURI COUNT_INST_TID = INST_TID.extend("count");
    public static final fURI SUM_INST_TID = INST_TID.extend("sum");
    public static final fURI CC_INST_TID = INST_TID.extend("cc");
    public static final fURI PROD_INST_TID = INST_TID.extend("prod");
    public static final fURI POW_INST_TID = INST_TID.extend("pow");
    public static final fURI REDUCE_INST_TID = INST_TID.extend("reduce");
    public static final fURI NEG_INST_TID = INST_TID.extend("neg");
    public static final fURI MULT_INST_TID = INST_TID.extend("mult");
    public static final fURI DIV_INST_TID = INST_TID.extend("div");
    public static final fURI INV_INST_TID = INST_TID.extend("inv");
    public static final fURI ZERO_INST_TID = INST_TID.extend("zero");
    public static final fURI ONE_INST_TID = INST_TID.extend("one");
    public static final fURI PLUS_INST_TID = INST_TID.extend("plus");
    public static final fURI MPLUS_INST_TID = INST_TID.extend("mplus");
    public static final fURI MINUS_INST_TID = INST_TID.extend("minus");
    public static final fURI MAP_INST_TID = INST_TID.extend("map");
    public static final fURI PARENT_INST_TID = INST_TID.extend("parent");
    public static final fURI FILTER_INST_TID = INST_TID.extend("filter");
    public static final fURI SIDE_INST_TID = INST_TID.extend("side");
    public static final fURI TO_INST_TID = INST_TID.extend("to");
    public static final fURI FROM_INST_TID = INST_TID.extend("from");
    public static final fURI REF_INST_TID = INST_TID.extend("ref");
    public static final fURI SPLIT_INST_TID = INST_TID.extend("split"); // -<
    public static final fURI CHOOSE_INST_TID = INST_TID.extend("choose"); // -<|
    public static final fURI CHAIN_TID = INST_TID.extend("chain"); // -<;
    public static final fURI MERGE_INST_TID = INST_TID.extend("merge");
    public static final fURI FILL_TID = INST_TID.extend("fill");
    public static final fURI FIND_TID = INST_TID.extend("find");
    public static final fURI RMERGE_TID = INST_TID.extend("rmerge");
    public static final fURI WITHIN_INST_TID = INST_TID.extend("within");
    public static final fURI LIFT_INST_TID = INST_TID.extend("lift");
    public static final fURI AUTO_INST_TID = INST_TID.extend("auto");
    public static final fURI AUTO_FROM_INST_TID = INST_TID.extend("auto_from");
    public static final fURI BLOCK_INST_TID = INST_TID.extend("block");
    public static final fURI RNG_INST_TID = INST_TID.extend("rng");
    public static final fURI DOM_INST_TID = INST_TID.extend("dom");
    public static final fURI TID_INST_TID = INST_TID.extend("tid");
    public static final fURI VID_INST_TID = INST_TID.extend("vid");
    public static final fURI TYPE_INST_TID = INST_TID.extend("type");
    public static final fURI GET_INST_TID = INST_TID.extend("get");
    public static final fURI FAILURE_INST_TID = INST_TID.extend("failure");
    public static final fURI AS_INST_TID = INST_TID.extend("as");
    public static final fURI REVERSE_INST_TID = INST_TID.extend("reverse");
    public static final fURI CLOSE_INST_TID = INST_TID.extend("close");
    public static final fURI REPEAT_INST_TID = INST_TID.extend("repeat");
    public static final fURI AT_INST_TID = INST_TID.extend("at");
    public static final fURI IS_INST_TID = INST_TID.extend("is");
    public static final fURI ISA_INST_TID = INST_TID.extend("isa");
    public static final fURI OR_INST_TID = INST_TID.extend("or");
    public static final fURI AND_INST_TID = INST_TID.extend("and");
    public static final fURI MATCHES_INST_TID = INST_TID.extend("matches");
    public static final fURI EQ_INST_TID = INST_TID.extend("eq");
    public static final fURI NEQ_INST_TID = INST_TID.extend("neq");
    public static final fURI GT_INST_TID = INST_TID.extend("gt");
    public static final fURI REGEX_INST_TID = INST_TID.extend("regex");
    public static final fURI ORDER_INST_TID = INST_TID.extend("order");
    public static final fURI LT_INST_TID = INST_TID.extend("lt");
    public static final fURI GTE_INST_TID = INST_TID.extend("gte");
    public static final fURI LTE_INST_TID = INST_TID.extend("lte");
    public static final fURI NOT_INST_TID = INST_TID.extend("not");
    public static final fURI TAKE_INST_TID = INST_TID.extend("take");
    public static final fURI SKIP_INST_TID = INST_TID.extend("skip");
    public static final fURI BARRIER_INST_TID = INST_TID.extend("barrier");
    public static final fURI REIFY_INST_TID = INST_TID.extend("reify");
    public static final fURI SELECT_INST_TID = INST_TID.extend("select");
    public static final fURI UPDATE_INST_TID = INST_TID.extend("update");
    public static final fURI WHERE_INST_TID = INST_TID.extend("where");
    public static final fURI GROUP_INST_TID = INST_TID.extend("group");
    public static final fURI ELSE_INST_TID = INST_TID.extend("else");
    public static final fURI END_INST_TID = INST_TID.extend("end");
    public static final fURI THREAD_INST_TID = INST_TID.extend("thread");
    public static final fURI IMPORT_INST_TID = INST_TID.extend("import");
    public static final fURI SOURCE_INST_TID = INST_TID.extend("source");
    public static final fURI SWAP_TID = INST_TID.extend("swap");
    public static final fURI PRINT_INST_TID = INST_TID.extend("print");
    public static final fURI LSHIFT_INST_TID = INST_TID.extend("lshift");
    public static final fURI RSHIFT_INST_TID = INST_TID.extend("rshift");
    public static final fURI MATH_INST_TID = INST_TID.extend("math");
    /*public static final fURI URI_SCHEME_TID = MTRON_TID.extend("uri:scheme");
    public static final fURI URI_PORT_TID = MTRON_TID.extend("uri:port");
    public static final fURI URI_HOST_TID = MTRON_TID.extend("uri:host");*/
    public static final fURI PATH_TID = M_ISA_TID.extend("path");
    public static final fURI Q_INST_TID = INST_TID.extend("q");
    public static final fURI URI_C_TID = M_ISA_TID.extend("uri:c");
    public static final fURI LCASE_INST_TID = INST_TID.extend("lcase");
    public static final fURI UCASE_INST_TID = INST_TID.extend("ucase");
    public static final fURI SCHEME_INST_TID = M_ISA_TID.extend("scheme");
    public static final fURI HOST_INST_TID = M_ISA_TID.extend("host");
    public static final fURI PORT_INST_TID = M_ISA_TID.extend("port");
    /// ////////////
    /// ////////////
    public static final fURI POLY_TID = M_ISA_TID.extend("poly");
    public static final fURI MONO_TID = M_ISA_TID.extend("mono");
    public static final fURI NUM_TID = M_ISA_TID.extend("num");

    //public static final Set<fURI> MARKER_TYPES = Set.of(MONO_TID, POLY_TID, NUM_TID);
    public static final Set<fURI> BASE_TYPES = Set.of(
            FAIL_TID, BOOL_TID, BYTES_TID, INT_TID, REAL_TID,
            STR_TID, URI_TID, REL_TID,
            LST_TID, REC_TID, INST_TID,
            CODE_TID, OBJS_TID, NOOBJ_TID);

    public static final Type SPACE_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(SPACE_TID)
            .isaPredicate(rec(
                    uri(PATTERN), URI_TYPE,
                    uri(Q).maybe(), rec(URI_TYPE, Q_TYPE))).create();

    public static final Type MONO_TYPE = Type.Builder.build()
            .tid(MONO_TID)
            .vid(MONO_TID)
            .predicate((lhs, inst) -> bool(lhs.isBytes() || lhs.isBool() || lhs.isInt() || lhs.isReal() || lhs.isStr() || lhs.isUri() || lhs.isInst()))
            .create();

    public static final Type NUM_TYPE = Type.Builder.build()
            .tid(NUM_TID)
            .vid(NUM_TID)
            .predicate((lhs, inst) -> bool(lhs.isInt() || lhs.isReal()))
            .create();

    public static final Type POLY_TYPE = Type.Builder.build()
            .tid(POLY_TID)
            .vid(POLY_TID)
            .predicate((lhs, inst) -> bool(lhs.isLst() || lhs.isRec() || lhs.isRel() || lhs.isCode()))
            .create();


    public mInstSet() {
        super(M_ISA_TID, M_ISA_TID);
    }

    @Override
    public void close() {
        // do nothing
    }

    @Override
    public Set<Type> types() {
        return new LinkedHashSet<>(List.of(
                MONO_TYPE,
                POLY_TYPE,
                NUM_TYPE,
                NOOBJ_TYPE,
                FAIL_TYPE,
                BOOL_TYPE,
                INT_TYPE,
                REAL_TYPE,
                BYTES_TYPE,
                STR_TYPE,
                URI_TYPE,
                REL_TYPE,
                LST_TYPE,
                REC_TYPE,
                INST_TYPE,
                CODE_TYPE,
                Type.Builder.build().tid(OBJS_TID).vid(OBJS_TID).create(),
                /// ///////////////////////////////////
                SPACE_TYPE,
                MEM_SPACE_TYPE,
                STACK_SPACE_TYPE,
                META_SPACE_TYPE));
    }

    @Override
    public Set<Obj> consts() {
        return Stream.of(noobj()).collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    @Override
    public Set<Inst> rewrites() {
        return new LinkedHashSet<>(List.of(
                InstSet.Helper.rewriter(f("id_removal_rewrite"), code -> code.selfJVM(code.insts().stream().filter(i -> !i.tid().equals(ID_INST_TID)).toList()).asCode()),
                InstSet.Helper.rewriter(f("map_nest_rewrite"), code -> {
                    final List<Inst> newInsts = new ArrayList<>();
                    boolean done = false;
                    while (!done) {
                        done = true;
                        for (final Inst i : code.insts()) {
                            if (i.tid().equals(MAP_INST_TID) && i.arg(0).isObjCall() && i.arg(0).<Call>as().insts().getFirst().tid().basePath().equals(MAP_INST_TID)) {
                                done = false;
                                LOG.debug("nesting rewrite: %s", i);
                                newInsts.add(i.arg(0).asInst());
                            } else {
                                newInsts.add(i);
                            }
                        }
                        code.selfJVM(new ArrayList<>(newInsts));
                        newInsts.clear();
                    }
                    return code;
                })
        ));
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
                Tuple.Triplet.with(Tuple.Pair.with("=~", null), List.of(MATCHES_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?=~", null), List.of(IS_INST_TID, MATCHES_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?=", null), List.of(IS_INST_TID, EQ_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?>", null), List.of(IS_INST_TID, GT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?>=", null), List.of(IS_INST_TID, GTE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?<=", null), List.of(IS_INST_TID, LTE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?<", null), List.of(IS_INST_TID, LT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?!=", null), List.of(IS_INST_TID, NEQ_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?", null), List.of(ISA_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("@", null), List.of(AT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("|", null), List.of(BLOCK_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("_/", "\\_"), List.of(WITHIN_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("_", null), List.of(ID_INST_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with("⋅", null), List.of(MULT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("*", null), List.of(FROM_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">|", null), List.of(BARRIER_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">|", null), List.of(BARRIER_INST_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with(">>-", null), List.of(RNG_INST_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with(">-", null), List.of(MERGE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">-", null), List.of(MERGE_INST_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with("-<|", null), List.of(CHOOSE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("-<", null), List.of(SPLIT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("->", null), List.of(REF_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">>", null), List.of(RSHIFT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">>", null), List.of(RSHIFT_INST_TID), 0),
                //Tuple.Triplet.with(Tuple.Pair.with("<<", null), List.of(LSHIFT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("<<", null), List.of(LSHIFT_INST_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with("++", null), List.of(MPLUS_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("+", null), List.of(PLUS_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("-", null), List.of(MINUS_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(";", null), List.of(END_INST_TID), 0),
                //  Tuple.Triplet.with(Tuple.Pair.with("(", ")"), List.of(GET_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("./", null), List.of(GET_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("!*", null), List.of(AUTO_FROM_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("!", null), List.of(AUTO_INST_TID), 1)));
        //   Tuple.Triplet.with(Tuple.Pair.with("^", null), List.of(PARENT_INST_TID), 0)));
        //  Tuple.Triplet.with(Tuple.Pair.with("^", null), List.of(LIFT_INST_TID), 1)));
    }

    @Override
    public Set<Inst> insts() {
        final Set<Inst> set = new LinkedHashSet<>();
        set.addAll(Fail.FailType.insts());
        set.addAll(NoObj.NoObjType.insts());
        set.addAll(Bytes.BytesType.insts());
        set.addAll(Bool.BoolType.insts());
        set.addAll(Int.IntType.insts());
        set.addAll(Real.TypeObj.insts());
        set.addAll(Str.StrType.insts());
        set.addAll(Uri.UriType.insts());
        set.addAll(Rel.RelType.insts());
        set.addAll(Rec.RecType.insts());
        set.addAll(Lst.LstType.insts());
        set.addAll(Inst.InstType.insts());
        set.addAll(Obj.ObjType.insts());
        set.addAll(Objs.ObjsType.insts());
        set.addAll(Type.TypeType.insts());
        set.addAll(Space.SpaceType.insts());
        //set.add(instC(f("/m/inst/card").dom(A).rng(B), lst(T(f("/m/sys/ui/card"))), (lhs, inst) -> str(lhs.toCleanString())));
        return set;
    }

}