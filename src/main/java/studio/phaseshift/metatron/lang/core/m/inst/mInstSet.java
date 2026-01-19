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

package studio.phaseshift.metatron.lang.core.m.inst;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.obj.NoObj;
import studio.phaseshift.metatron.lang.core.m.type.*;
import studio.phaseshift.metatron.lang.core.m.type.impl.MInstSet;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.id_;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;

public class mInstSet extends MInstSet {

    public static final fURI MTRON_TID = f("/m");
    // /m/obj
    public static final fURI FAIL_TID = MTRON_TID.extend("fail");
    public static final fURI BOOL_TID = MTRON_TID.extend("bool");
    public static final fURI BYTES_TID = MTRON_TID.extend("bytes");
    public static final fURI INT_TID = MTRON_TID.extend("int");
    public static final fURI REAL_TID = MTRON_TID.extend("real");
    public static final fURI STR_TID = MTRON_TID.extend("str");
    public static final fURI URI_TID = MTRON_TID.extend("uri");
    public static final fURI REL_TID = MTRON_TID.extend("rel");
    public static final fURI LST_TID = MTRON_TID.extend("lst");
    public static final fURI REC_TID = MTRON_TID.extend("rec");
    public static final fURI INST_TID = MTRON_TID.extend("inst");
    public static final fURI TYPE_TID = MTRON_TID.extend("type");
    /// ////////////////////////////////////////////////////////
    public static final fURI ID_INST_TID = INST_TID.extend("id");
    public static final fURI EXPLAIN_INST_TID = INST_TID.extend("explain");
    public static final fURI TO_STR_INST_TID = INST_TID.extend("tostr");
    public static final fURI HAS_INST_TID = INST_TID.extend("has");
    public static final fURI CATCH_INST_TID = INST_TID.extend("catch");
    public static final fURI APPLY_INST_TID = INST_TID.extend("apply");
    public static final fURI START_INST_TID = INST_TID.extend("start");
    public static final fURI RFROM_TID = INST_TID.extend("rfrom");
    public static final fURI COUNT_INST_TID = INST_TID.extend("count");
    public static final fURI SUM_INST_TID = INST_TID.extend("sum");
    public static final fURI CC_INST_TID = INST_TID.extend("cc");
    public static final fURI PROD_INST_TID = INST_TID.extend("prod");
    public static final fURI POW_INST_TID = INST_TID.extend("pow");
    public static final fURI REDUCE_INST_TID = INST_TID.extend("reduce");
    public static final fURI MULT_INST_TID = INST_TID.extend("mult");
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
    public static final fURI LT_INST_TID = INST_TID.extend("lt");
    public static final fURI GTE_INST_TID = INST_TID.extend("gte");
    public static final fURI LTE_INST_TID = INST_TID.extend("lte");
    public static final fURI NOT_INST_TID = INST_TID.extend("not");
    public static final fURI TAKE_INST_TID = INST_TID.extend("take");
    public static final fURI SKIP_INST_TID = INST_TID.extend("skip");
    public static final fURI BARRIER_TID = INST_TID.extend("barrier");
    public static final fURI REIFY_INST_TID = INST_TID.extend("reify");
    public static final fURI SELECT_INST_TID = INST_TID.extend("select");
    public static final fURI WHERE_INST_TID = INST_TID.extend("where");
    public static final fURI GROUP_INST_TID = INST_TID.extend("group");
    public static final fURI ELSE_INST_TID = INST_TID.extend("else");
    public static final fURI END_INST_TID = INST_TID.extend("end");
    public static final fURI THREAD_INST_TID = INST_TID.extend("thread");
    public static final fURI SOURCE_INST_TID = INST_TID.extend("source");
    public static final fURI SWAP_TID = INST_TID.extend("swap");
    public static final fURI PRINT_INST_TID = INST_TID.extend("print");
    public static final fURI LSHIFT_INST_TID = INST_TID.extend("lshift");
    public static final fURI RSHIFT_INST_TID = INST_TID.extend("rshift");
    public static final fURI CODE_TID = MTRON_TID.extend("code");
    public static final fURI NOOBJ_TID = fURI.of("noobj");
    public static final fURI ALL_STAR = ALL.maybeSome();
    public static final fURI OBJS_TID = MTRON_TID.extend("objs");
    public static final fURI MATH_INST_TID = MTRON_TID.extend("math");
    /*public static final fURI URI_SCHEME_TID = MTRON_TID.extend("uri:scheme");
    public static final fURI URI_PORT_TID = MTRON_TID.extend("uri:port");
    public static final fURI URI_HOST_TID = MTRON_TID.extend("uri:host");*/
    public static final fURI URI_PATH_TID = MTRON_TID.extend("uri:path");
    public static final fURI Q_INST_TID = INST_TID.extend("q");
    public static final fURI URI_C_TID = MTRON_TID.extend("uri:c");
    public static final fURI STR_SPLIT_TID = MTRON_TID.extend("str:split");
    public static final fURI STR_LOWER_TID = MTRON_TID.extend("str:lower");
    public static final fURI STR_UPPER_TID = MTRON_TID.extend("str:upper");
    public static final fURI STR_CONTAINS_TID = MTRON_TID.extend("str:contains");
    public static final fURI SCHEME_INST_TID = MTRON_TID.extend("scheme");
    public static final fURI HOST_INST_TID = MTRON_TID.extend("host");
    public static final fURI PORT_INST_TID = MTRON_TID.extend("port");
    public static final Set<fURI> BASE_TYPES = Set.of(
            FAIL_TID, BOOL_TID, BYTES_TID, INT_TID, REAL_TID,
            STR_TID, URI_TID, REL_TID,
            LST_TID, REC_TID, INST_TID,
            CODE_TID, OBJS_TID, NOOBJ_TID);
    /// ////////////
    /// ////////////
    public static final fURI POLY_TID = MTRON_TID.extend("poly");
    public static final fURI MONO_TID = MTRON_TID.extend("mono");

    /// ////////////
    /// ////////////
    public mInstSet(final fURI vid) {
        super(MTRON_TID, vid);
    }

    public static mInstSet create() {
        return create(fURI.fnull);
    }

    public static mInstSet create(final fURI vid) {
        return new mInstSet(vid);
    }

    @Override
    public Set<Type> types() {
        return new LinkedHashSet<>(List.of(
                T(BOOL_TID),
                T(BYTES_TID),
                T(INT_TID),
                T(REAL_TID),
                T(STR_TID),
                T(URI_TID),
                T(LST_TID),
                T(REL_TID),
                T(REC_TID),
                T(INST_TID),
                T(OBJS_TID),
                T(FAIL_TID),
                T(NOOBJ_TID)));
    }

    @Override
    public Set<Obj> consts() {
        return Stream.of(noobj()).collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    @Override
    public Set<Tuple.Triplet<Tuple.Pair<String, String>, List<fURI>, Integer>> sugars() {
        return new LinkedHashSet<>(List.of(
                Tuple.Triplet.with(Tuple.Pair.with("^", null), List.of(RFROM_TID), 1),
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
                Tuple.Triplet.with(Tuple.Pair.with(">>-", null), List.of(RNG_INST_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with(">-", null), List.of(MERGE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">-", null), List.of(MERGE_INST_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with("-<|", null), List.of(CHOOSE_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("-<", null), List.of(SPLIT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("->", null), List.of(REF_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">>", null), List.of(RSHIFT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">>", null), List.of(RSHIFT_INST_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with("<<", null), List.of(LSHIFT_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("<<", null), List.of(LSHIFT_INST_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with("++", null), List.of(MPLUS_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("+", null), List.of(PLUS_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("-", null), List.of(MINUS_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(";", null), List.of(END_INST_TID), 0),
                //  Tuple.Triplet.with(Tuple.Pair.with("(", ")"), List.of(GET_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("./", null), List.of(GET_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("!*", null), List.of(AUTO_FROM_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("!", null), List.of(AUTO_INST_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("^", null), List.of(LIFT_INST_TID), 1)));
    }

    public static Obj crossPoly(Obj lhs, Obj rhs) {
        //  if(lhs.isObjs())
        //     return lhs.<Objs>as().stream().map(l -> crossPoly(l,rhs)).reduce(rec(), Obj::append);
        if (lhs.isLst() && rhs.isLst()) {
            final List<Obj> result = new ArrayList<>();
            final List<Obj> lhsList = lhs.lstValue();
            final List<Obj> rhsList = rhs.lstValue();
            final AtomicBoolean found = new AtomicBoolean(false);
            int x = 0;
            Long counter = 1L;
            boolean reset = true;
            for (int i = 0; i < lhsList.size(); i++) {
                if (rhsList.size() > x) {
                    found.set(true);
                    final Obj rhsA = rhsList.get(x);
                    if (rhsA.isInst() && rhsA.tid().basePath().equals(INST_TID)) {
                        counter = counter == 1 && reset ? (rhsA.c().max() == null ? Long.MAX_VALUE : rhsA.c().max()) : counter;
                        final List<Obj> lstLHS = new ArrayList<>();
                        final List<Obj> rhsArgs = new ArrayList<>();
                        final int argCount = (int) rhsA.<Inst>as().args().count();
                        for (int j = 0; j < rhsA.<Inst>as().args().count(); j++) {
                            final Obj element = lhsList.get(i + j);
                            lstLHS.add(element);
                            rhsArgs.add(rhsA.<Inst>as().arg(j).apply(element));
                        }

                        Inst newInst = rhsA.<Inst>as().args(lst(rhsArgs));
                        newInst = newInst.tid(newInst.tid().c("1"));
                        // LOG.info("%s",newInst);
                        Obj temp = newInst.apply(lst(lstLHS));
                        result.addAll(temp.elements().toList());
                        i = (i + argCount) - 1;
                    } else {
                        result.add(//(lhsA.isRec() && rhsA.isRec()) || (lhsA.isLst() && rhsA.isLst()) ?
                                crossPoly(lhsList.get(i), rhsA));// :
                    }
                    if (--counter == 0) {
                        reset = true;
                        counter = 1L;
                        x++;
                    } else {
                        reset = false;
                    }

                    //rhsA.apply(lhsA));
                } else {
                    break;
                }
            }
            return result.isEmpty() || !found.get() ? noobj() : lhs.jvm(result);
        } else if (lhs.isRec() && rhs.isRec()) {
            final Map<Obj, Obj> result = new LinkedHashMap<>();
            final AtomicBoolean found = new AtomicBoolean(false);
            lhs.recValue().forEach((lKey, lValue) -> {
                AtomicBoolean localFind = new AtomicBoolean(false);
                rhs.recValue().forEach((rKey, rValue) -> {
                    if (lKey.matches(rKey)) {
                        found.set(true);
                        localFind.set(true);
                        final Obj r = //((lValue.isRec() && rValue.isRec()) || (lValue.isLst() && rValue.isLst())) ?
                                crossPoly(lValue, rValue);
                        // lValue.isPoly() ? NoObj.single() : rValue.apply(lValue);
                        result.compute(rKey.apply(lKey), (k, v) -> null == v ? r : v.append(r));
                    }
                });
                if (!localFind.get()) {
                    rhs.recValue().forEach((rKey, rValue) -> {
                        if (lKey.matches(rKey) || rKey.isCall()) {
                            found.set(true);
                            final Obj r = //((lValue.isRec() && rValue.isRec()) || (lValue.isLst() && rValue.isLst())) ?
                                    crossPoly(lValue, rValue);
                            // lValue.isPoly() ? NoObj.single() : rValue.apply(lValue);
                            result.compute(rKey.apply(lKey), (k, v) -> null == v ? r : v.append(r));
                        }
                    });
                }
            });
            if (result.size() < rhs.recValue().size())
                return noobj();
            return result.isEmpty() || !found.get() ? noobj() : lhs.jvm(result);
        } else if (!rhs.isCall() && (lhs.isPoly() || rhs.isPoly())) {
            return noobj();
        } else {
            return !rhs.isInstObj() ? rhs.apply(lhs) : id_().addInst(rhs.as()).apply(lhs); // TODO: hack to force type safe compilation
        }
    }

    @Override
    public Set<Inst> insts() {
        final Set<Inst> set = new LinkedHashSet<>();
        set.addAll(Fail.FailType.insts());
        set.addAll(NoObj.NoObjType.insts());
        set.addAll(Bytes.BytesType.insts());
        set.addAll(Bool.BoolType.insts());
        set.addAll(Int.IntType.insts());
        set.addAll(Real.RealType.insts());
        set.addAll(Str.StrType.insts());
        set.addAll(Uri.UriType.insts());
        set.addAll(Rel.RelType.insts());
        set.addAll(Rec.RecType.insts());
        set.addAll(Lst.LstType.insts());
        set.addAll(Inst.InstType.insts());
        set.addAll(Obj.ObjType.insts());
        return set;
    }

}
