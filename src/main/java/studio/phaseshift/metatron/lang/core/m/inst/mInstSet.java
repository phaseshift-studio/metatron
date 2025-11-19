/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

import net.objecthunter.exp4j.ExpressionBuilder;
import org.petitparser.parser.primitive.CharacterParser;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.obj.NoObj;
import studio.phaseshift.metatron.lang.core.m.type.*;
import studio.phaseshift.metatron.lang.core.m.type.impl.*;
import studio.phaseshift.metatron.lang.core.m.util.MathUtil;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.petitparser.parser.primitive.StringParser.of;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.furi.q.DocQ.Doc.docWrap;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.id_;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.Common.nullOrElse;

public class mInstSet extends MInstSet {

    public static final fURI MTRON_TID = f("/m");
    public static final fURI MTRON_LANG_TID = MTRON_TID.extend("lang");
    // /m/obj
    public static final fURI MTRON_SPACE_TID = f("/space");// MTRON_TID.extend("space");
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
    public static final fURI ID_TID = INST_TID.extend("id");
    public static final fURI HAS_TID = INST_TID.extend("has");
    public static final fURI CATCH_TID = INST_TID.extend("catch");
    public static final fURI APPLY_TID = INST_TID.extend("apply");
    public static final fURI START_TID = INST_TID.extend("start");
    public static final fURI RFROM_TID = INST_TID.extend("rfrom");
    public static final fURI COUNT_TID = INST_TID.extend("count");
    public static final fURI SUM_TID = INST_TID.extend("sum");
    public static final fURI PROD_TID = INST_TID.extend("prod");
    public static final fURI REDUCE_TID = INST_TID.extend("reduce");
    public static final fURI MULT_TID = INST_TID.extend("mult");
    public static final fURI PLUS_TID = INST_TID.extend("plus");
    public static final fURI MINUS_TID = INST_TID.extend("minus");
    public static final fURI MAP_TID = INST_TID.extend("map");
    public static final fURI FILTER_TID = INST_TID.extend("filter");
    public static final fURI SIDE_TID = INST_TID.extend("side");
    public static final fURI TO_TID = INST_TID.extend("to");
    public static final fURI FROM_TID = INST_TID.extend("from");
    public static final fURI REF_TID = INST_TID.extend("ref");
    public static final fURI SPLIT_TID = INST_TID.extend("split"); // -<
    public static final fURI CHOOSE_TID = INST_TID.extend("choose"); // -<|
    public static final fURI CHAIN_TID = INST_TID.extend("chain"); // -<;
    public static final fURI MERGE_TID = INST_TID.extend("merge");
    public static final fURI FILL_TID = INST_TID.extend("fill");
    public static final fURI FIND_TID = INST_TID.extend("find");
    public static final fURI RMERGE_TID = INST_TID.extend("rmerge");
    public static final fURI WITHIN_TID = INST_TID.extend("within");
    public static final fURI BLOCK_TID = INST_TID.extend("block");
    public static final fURI RNG_TID = INST_TID.extend("rng");
    public static final fURI DOM_TID = INST_TID.extend("dom");
    public static final fURI TID_TID = INST_TID.extend("tid");
    public static final fURI VID_TID = INST_TID.extend("vid");
    public static final fURI TYPE_TID = INST_TID.extend("type");
    public static final fURI GET_TID = INST_TID.extend("get");
    public static final fURI FAILURE_TID = INST_TID.extend("failure");
    public static final fURI AS_TID = INST_TID.extend("as");
    public static final fURI AT_TID = INST_TID.extend("at");
    public static final fURI IS_TID = INST_TID.extend("is");
    public static final fURI ISA_TID = INST_TID.extend("isa");
    public static final fURI OR_TID = INST_TID.extend("or");
    public static final fURI AND_TID = INST_TID.extend("and");
    public static final fURI MATCHES_TID = INST_TID.extend("matches");
    public static final fURI EQ_TID = INST_TID.extend("eq");
    public static final fURI NEQ_TID = INST_TID.extend("neq");
    public static final fURI GT_TID = INST_TID.extend("gt");
    public static final fURI LT_TID = INST_TID.extend("lt");
    public static final fURI GTE_TID = INST_TID.extend("gte");
    public static final fURI LTE_TID = INST_TID.extend("lte");
    public static final fURI NOT_TID = INST_TID.extend("not");
    public static final fURI TAKE_TID = INST_TID.extend("take");
    public static final fURI SKIP_TID = INST_TID.extend("skip");
    public static final fURI BARRIER_TID = INST_TID.extend("barrier");
    public static final fURI REIFY_TID = INST_TID.extend("reify");
    public static final fURI SELECT_TID = INST_TID.extend("select");
    public static final fURI WHERE_TID = INST_TID.extend("where");
    public static final fURI GROUP_TID = INST_TID.extend("group");
    public static final fURI ELSE_TID = INST_TID.extend("else");
    public static final fURI END_TID = INST_TID.extend("end");
    public static final fURI SWAP_TID = INST_TID.extend("swap");
    public static final fURI PRINT_TID = INST_TID.extend("print");
    public static final fURI LSHIFT_TID = INST_TID.extend("lshift");
    public static final fURI RSHIFT_TID = INST_TID.extend("rshift");
    public static final fURI CODE_TID = MTRON_TID.extend("code");
    public static final fURI NOOBJ_TID = fURI.of("");
    public static final fURI ALL_STAR = ALL.maybeSome();
    public static final fURI OBJS_TID = MTRON_TID.extend("objs");
    public static final fURI MATH_TID = MTRON_TID.extend("math");
    public static final fURI URI_SCHEME_TID = MTRON_TID.extend("uri:scheme");
    public static final fURI URI_PORT_TID = MTRON_TID.extend("uri:port");
    public static final fURI URI_HOST_TID = MTRON_TID.extend("uri:host");
    public static final fURI URI_PATH_TID = MTRON_TID.extend("uri:path");
    public static final fURI URI_Q_TID = MTRON_TID.extend("uri:q");
    public static final fURI URI_C_TID = MTRON_TID.extend("uri:c");
    public static final fURI STR_SPLIT_TID = MTRON_TID.extend("str:split");
    public static final fURI STR_LOWER_TID = MTRON_TID.extend("str:lower");
    public static final fURI STR_UPPER_TID = MTRON_TID.extend("str:upper");
    public static final fURI STR_CONTAINS_TID = MTRON_TID.extend("str:contains");
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
        return create(fURI.NULL);
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
                Tuple.Triplet.with(Tuple.Pair.with("?==", null), List.of(WHERE_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("%==", null), List.of(GROUP_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("==", null), List.of(SELECT_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("=~", null), List.of(MATCHES_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?=", null), List.of(IS_TID, EQ_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?>", null), List.of(IS_TID, GT_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?>=", null), List.of(IS_TID, GTE_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?<=", null), List.of(IS_TID, LTE_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?<", null), List.of(IS_TID, LT_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?!=", null), List.of(IS_TID, NEQ_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?=~", null), List.of(IS_TID, MATCHES_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("?", null), List.of(ISA_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("@", null), List.of(AT_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("|", null), List.of(BLOCK_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("_/", "\\_"), List.of(WITHIN_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("_", null), List.of(ID_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with("⋅", null), List.of(MULT_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("*", null), List.of(FROM_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">>-", null), List.of(RNG_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with(">-", null), List.of(MERGE_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">-", null), List.of(MERGE_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with("-<|", null), List.of(CHOOSE_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("-<", null), List.of(SPLIT_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("->", null), List.of(REF_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">>", null), List.of(RSHIFT_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(">>", null), List.of(RSHIFT_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with("<<", null), List.of(LSHIFT_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("<<", null), List.of(LSHIFT_TID), 0),
                Tuple.Triplet.with(Tuple.Pair.with("+", null), List.of(PLUS_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with("-", null), List.of(MINUS_TID), 1),
                Tuple.Triplet.with(Tuple.Pair.with(";", null), List.of(END_TID), 0)));
    }

    private Obj crossPoly(Obj lhs, Obj rhs) {
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
            return result.isEmpty() || !found.get() ? noobj() : lhs.jvm(result);
        } else if (!rhs.isCall() && (lhs.isPoly() || rhs.isPoly())) {
            return noobj();
        } else {
            return !rhs.isInstObj() ? rhs.apply(lhs) : id_().addInst(rhs.as()).apply(lhs); // TODO: hack to force type safe compilation
        }
    }

    @Override
    public Set<Inst> insts() {
        return new LinkedHashSet<>(List.of(
                instC(CATCH_TID.dom(ALL).rng(ALL.maybeSome()), lst(T(ALL.maybeSome())), (lhs, inst) -> lhs.isFail() ? inst.arg(0).apply(lhs) : lhs),
                docWrap(instC(START_TID.dom(fURI.NOOBJ.zero()).rng(A.maybeSome()), lst(T(A.maybeSome())), (lhs, inst) -> inst.arg(0)),
                        "noobj", "initial objs", Map.of(jnt(0), "initial objs"), "the initial function f()->x"),
                instC(END_TID.dom(ALL_STAR).rng(NOOBJ_TID.zero()), lst(), (lhs, inst) -> noobj()),
                docWrap(instC(PRINT_TID.dom(ALL).rng(ALL), lst(T(ALL_STAR)), (lhs, inst) -> inst.args().elements().peek(o -> LOG.none("%s\n", o)).filter(a -> false).findAny().orElse(lhs)),
                        "the rhs obj", "the lhs obj", Map.of(jnt(0), "the obj to write to stdout"), "a side-effect function f(x)-|>x"),
                instC(AT_TID.dom(ALL.maybe()).rng(ALL.maybeSome()), lst(T(URI_TID)), (lhs, inst) -> lhs.isNoObj() ? Router.readFromSpace(inst.arg(0).uriValue()).vid(inst.arg(0).uriValue()) : lhs.vid(inst.arg(0).uriValue())),
                instC(HAS_TID.dom(REC_TID).rng(REC_TID.maybe()), lst(T(ALL)), (lhs, inst) -> inst.arg(0).isRel() ?
                        (lhs.<Rec>as().elements().anyMatch(r -> r.matches(inst.arg(0))) ? lhs : noobj()) :
                        (lhs.<Rec>as().elements().map(Rel::first).anyMatch(r -> r.matches(inst.arg(0))) ? lhs : noobj())),
                instC(HAS_TID.dom(LST_TID).rng(LST_TID.maybe()), lst(T(ALL)), (lhs, inst) -> lhs.<Lst>as().elements().anyMatch(r -> r.matches(inst.arg(0))) ? lhs : noobj()),
                docWrap(
                        instC(ID_TID.dom(A).rng(A), lst(), (lhs, inst) -> lhs),
                        "an rhs obj", "an lhs obj", Map.of(), "the obj identity function f(x)->x"),
                docWrap(instC(ID_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(), (lhs, inst) -> lhs),
                        "the rhs obj", "the lhs obj", Map.of(), "a objs barrier identity function f(X)->X"),
                instC(OR_TID.dom(A).rng(A.maybe()), lst(T(BOOL_TID).c(cInt::some)), (lhs, inst) -> objs(lhs.stream().filter(l -> inst.args().elements().anyMatch(a -> a.apply(l).boolValue())))),
                instC(APPLY_TID.dom(ALL).rng(ALL.maybeSome()), lst(T(ALL.maybeSome())), (lhs, inst) -> lhs.apply(inst.arg(0))),
               /* instC(RFROM_TID.dom(ALL.maybe()).rng(OBJS_ID), lst(T(URI_TID)), (lhs, inst) -> {
                    Obj current = Router.global().read(inst.arg(0).uriValue());
                    return flatten(lhs, current);
                }),*/
                instC(RFROM_TID.dom(ALL).rng(ALL_STAR), lst(T(ALL)), (lhs, inst) -> {
                    Obj t = lhs;
                    Obj emit = MObjs.empty();
                    while (true) {
                        LOG.info("%s", t);
                        t = inst.arg(0).apply(t);
                        if (t.isNoObj())
                            break;
                        else
                            emit = emit.append(t);
                    }
                    return emit;
                }),
                instC(MAP_TID.dom(ALL).rng(A), lst(T(A)), (lhs, inst) -> inst.arg(0)),
                instC(FILTER_TID.dom(A).rng(A.maybe()), lst(T(ALL.maybe())), (lhs, inst) -> inst.arg(0).isNoObj() ? noobj() : lhs),
                instC(SIDE_TID.dom(A).rng(A), lst(T(ALL)), (lhs, inst) -> Optional.of(inst.arg(0).apply(lhs)).map(x -> (Obj) null).orElse(lhs)),
                instC(MAP_TID.dom(ALL).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> inst.arg(0)),
                instC(TID_TID.dom(ALL).rng(URI_TID), lst(), (lhs, inst) -> lhs.tid().toUri()),
                instC(VID_TID.dom(ALL).rng(ALL), lst(T(URI_TID)), (lhs, inst) -> lhs.vid(inst.arg(0).uriValue())),
                instC(VID_TID.dom(ALL).rng(URI_TID.maybe()), lst(), (lhs, inst) -> null == lhs.vid() ? noobj() : lhs.vid().toUri()),
                docWrap(instC(ELSE_TID.dom(ALL.maybe()).rng(ALL), lst(T(ALL.maybe())), (lhs, inst) -> lhs.isNoObj() ? inst.arg(0) : lhs),
                        "maybe an obj", "the lhs obj else the arg obj", Map.of(jnt(0), "the rhs obj is the lhs is noobj"), "f(lhs)->lhs if lhs is an obj, else f(noobj)->arg"),// TODO: rec args needs resolution on generics connected
                docWrap(instC(IS_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> inst.arg(0).boolValue() ? lhs : noobj()),
                        "any obj", "the lhs obj if arg is true", Map.of(jnt(0), "filter lhs if false"), "filters the lhs obj"), // TODO: generics are not working for some reason
                docWrap(instC(ISA_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> lhs.matches(inst.arg(0)) ? lhs : noobj()),
                        "an obj to match", "the unaltered obj if arg matches", Map.of(jnt(0), "filter lhs if doesn't match arg"), "a filter function f(x)->{0,x}"),
                instC(MATCHES_TID.dom(ALL.maybe()).rng(BOOL_TID), lst(T(ALL.maybe())), (lhs, inst) -> bool(lhs.matches(inst.arg(0)))),
                instC(GET_TID.dom(REC_TID).rng(ALL_STAR), lst(T(URI_TID)), (lhs, inst) -> lhs.<Rec>as().at(inst.arg(0))),
                instC(GET_TID.dom(LST_TID).rng(ALL_STAR), lst(T(INT_TID)), (lhs, inst) -> lhs.<Lst>as().at(inst.arg(0))),
                instC(GET_TID.dom(LST_TID).rng(ALL_STAR), lst(T(URI_TID)), (lhs, inst) -> lhs.<Lst>as().at(inst.arg(0))),
                /// ///////////////////////////////////////////////////////////////////////////////////////////////////
                docWrap(instC(BLOCK_TID.dom(A.maybe()).rng(B), lst(T(B)), (lhs, inst) -> inst.arg(0)),
                        "a blocked obj", "the unapplied arg", Map.of(jnt(0), "the rhs without evaluation"), "the lhs obj is halted and the arg is the rhs obj"),
                instC(SWAP_TID.dom(A).rng(B), lst(T(C)), (lhs, inst) -> lhs.apply(inst.arg(0))),
                /// ///////////////////////////////////////////////////////////////////////////////////////////////////
                docWrap(instC(SPLIT_TID.dom(STR_TID).rng(STR_TID.some()), lst(T(STR_TID)), (lhs, inst) -> objs(Arrays.stream(lhs.strValue().split(inst.arg(0).strValue())).map(MStr::str))),
                        "a str to split", "the components of the split lhs str", Map.of(jnt(0), "a token to split on"), "split the lhs string according to the token arg and emit a stream of splits"),
                instC(SPLIT_TID.dom(ALL).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> lst(inst.arg(0).elements().map(e -> e.apply(lhs)).toList())),
                instC(SPLIT_TID.dom(ALL.maybeSome()).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> lst(inst.arg(0).elements().map(e -> e.apply(lhs)).toList())),
                // instC(SPLIT_TID.dom(REL_TID).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> rec(lhs.<Rel>as().first(),lhs.<Rel>as().second())),
                //instC(SPLIT_TID.dom(ALL).rng(REL_TID), lst(T(REL_TID)), (lhs, inst) -> rel(inst.arg(0).<Rel>as().first().apply(lhs), inst.arg(0).<Rel>as().second().apply(lhs))),
                instC(SPLIT_TID.dom(ALL).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> rec(inst.arg(0).<Rec>as().elements().map(Obj::<Rel>as).map(e -> e.first().apply(lhs).choose(Obj::isNoObj, x -> null, x -> rel(x, e.second().apply(lhs)))).filter(x -> !Objects.isNull(x)))),
                // todo: allow c to generic and then the above and below instructions can be made into a single generic c inst
                //instC(SPLIT_TID.dom(ALL.maybeSome()).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> MRec.of(inst.arg(0).recValue().entrySet().stream().map(e -> e.getKey().apply(lhs).choose(Obj::isNoObj, x -> null, x -> MRel.of(x, e.getValue().apply(lhs)))).filter(x -> !Objects.isNull(x)).collect(Collectors.toMap(a -> a.<Rel>as().first(), b -> b.<Rel>as().second(), Obj::append, LinkedHashMap<Obj, Obj>::new)))),
                instC(SPLIT_TID.dom(A.maybeSome()).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) ->
                        inst.arg(0).jvm(lhs.stream().flatMap(o -> inst.arg(0).<Rec>as()
                                        .elements()
                                        .map(Obj::<Rel>as)
                                        .map(rel -> rel.first()
                                                .apply(o)
                                                .andThen(oo -> oo.isNoObj() ?
                                                        rel.second(noobj()) :
                                                        rel.second(rel.second().apply(o))).apply(o))//.choose(Obj::isNoObj, NoObj.single(), r -> rel.second(rel.second().apply(o))))
                                        .map(Obj::<Rel>as)
                                        .filter(p -> !p.first().isNoObj() && !p.second().isNoObj())
                                        .map(Obj::<Rel>as))
                                .collect(Collectors.toMap(Rel::first, Rel::second, Obj::append, LinkedHashMap::new)))),
                instC(SPLIT_TID.dom(URI_TID).rng(URI_TID.some()), lst(T(URI_TID)), (lhs, inst) -> objs(Arrays.stream(lhs.uriValue().toString().split(inst.arg(0).uriValue().toString())).map(MUri::uri))),
                instC(SPLIT_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(T(A.maybeSome())), (lhs, inst) -> objs(Stream.of(inst.arg(0)).map(o -> o.apply(lhs)))),
                instC(SPLIT_TID.dom(ALL).rng(ALL.maybeSome()), lst(T(ALL.some())), (lhs, inst) -> objs(inst.arg(0).stream().map(o -> o.apply(lhs)))),
                // instC(SPLIT_TID.dom(ALL).rng(ALL.maybeSome()), lst(T(ALL)), (lhs, inst) -> objs(inst.arg(0).apply(lhs))),
                /// ///////////////////////////////////////////////////////////////////////////////////////////////////
                docWrap(
                        instC(CHOOSE_TID.dom(ALL).rng(REL_TID.maybe()), lst(T(REC_TID)), (lhs, inst) -> inst.arg(0).<Rec>as().elements().map(Obj::<Rel>as).map(e -> e.<Rel>jvm(Tuple.Pair.with(e.first().apply(lhs), e.second()))).filter(e -> !e.first().isNoObj()).findFirst().map(e -> e.<Obj>jvm(Tuple.Pair.with(e.first(), e.second().apply(lhs)))).orElse(noobj())),
                        "any obj", "the split as an objs", Map.of(jnt(0), "the branches"), "a branching function f(x):g(a)->a',g(b)->b',..."),
                /// ///////////////////////////////////////////////////////////////////////////////////////////////////
                instC(MERGE_TID.dom(LST_TID).rng(ALL_STAR), lst(), (lhs, inst) -> objs(lhs.elements())),
                instC(MERGE_TID.dom(REC_TID).rng(REL_TID.maybeSome()), lst(), (lhs, inst) -> objs(lhs.elements())),
                instC(MERGE_TID.dom(REC_TID).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> inst.arg(0).<Rec>as().plus(lhs.as())),//objs(lhs.elementStream())),
                docWrap(instC(MERGE_TID.dom(STR_TID.maybeSome()).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> str(lhs.stream().map(Obj::<String>jvmAs).reduce((a, b) -> a + inst.arg(0).strValue() + b).orElse(""))),
                        "an str barrier", "the join of the str barrier", Map.of(jnt(0), "the join token"), "join the barrier given the str arg"),
                instC(MERGE_TID.dom(URI_TID.maybeSome()).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.stream().map(Obj::uriValue).reduce((a, b) -> a.extend(inst.arg(0).uriValue()).extend(b)).orElse(f("noobj")))),

                //
                instC(MERGE_TID.dom(A.maybeSome()).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> inst.arg(0).jvm(Stream.concat(lhs.stream(), inst.arg(0).elements()).toList())),
                instC(MERGE_TID.dom(REL_TID.maybeSome()).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> inst.arg(0).jvm(Stream.concat(lhs.stream().map(Obj::as), inst.arg(0).<Rec>as().elements().map(Obj::<Rel>as)).collect(Collectors.toMap(Rel::first, Rel::second, Obj::append, LinkedHashMap::new)))),
                instC(MERGE_TID.dom(A.maybeSome()).rng(ALL_STAR), lst(), (lhs, inst) -> objs(lhs.elements())),
                //
                instC(MERGE_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(T(A.maybeSome())), (lhs, inst) -> objs(Stream.concat(lhs.stream(), inst.arg(0).stream()))),
                // instC(MERGE_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(), (lhs, inst) -> lhs),
                /// ///////////////////////////////////////////////////////////////////////////////////////////////////
                instC(DOM_TID.dom(REL_TID).rng(ALL), lst(), (lhs, inst) -> lhs.relValue().get0()),
                instC(RNG_TID.dom(REL_TID).rng(ALL.some()), lst(), (lhs, inst) -> lhs.relValue().get1()),
                instC(DOM_TID.dom(REC_TID).rng(ALL_STAR), lst(), (lhs, inst) -> objs(lhs.recValue().keySet())),
                instC(RNG_TID.dom(REC_TID).rng(ALL_STAR), lst(), (lhs, inst) -> objs(lhs.recValue().values())),
                instC(LSHIFT_TID.dom(REL_TID).rng(ALL_STAR), lst(), (lhs, inst) -> lhs.<Rel>as().first()),
                instC(RSHIFT_TID.dom(REL_TID).rng(ALL_STAR), lst(), (lhs, inst) -> lhs.<Rel>as().second()),
                instC(LSHIFT_TID.dom(REC_TID).rng(ALL_STAR), lst(), (lhs, inst) -> objs(lhs.<Rec>as().elements().map(Rel::first))),
                instC(RSHIFT_TID.dom(REC_TID).rng(ALL_STAR), lst(), (lhs, inst) -> objs(lhs.<Rec>as().elements().map(Rel::second))),
                instC(RSHIFT_TID.dom(URI_TID).rng(URI_TID), lst(T(INT_TID.maybe())), (lhs, inst) -> uri(lhs.uriValue().pretract(inst.arg(0).orElse(jnt(1)).intValue().intValue()))),
                instC(LSHIFT_TID.dom(URI_TID).rng(URI_TID), lst(T(INT_TID.maybe())), (lhs, inst) -> uri(lhs.uriValue().retract(inst.arg(0).orElse(jnt(1)).intValue().intValue()))),
                instC(RSHIFT_TID.dom(REC_TID).rng(ALL_STAR), lst(), (lhs, inst) -> objs(lhs.recValue().values())),
                instC(LSHIFT_TID.dom(REC_TID).rng(ALL_STAR), lst(), (lhs, inst) -> objs(lhs.recValue().keySet())),
                /// ///////////////////////////////////////////////////////////////////////////////////////////////////
                instC(NOT_TID.dom(ALL).rng(BOOL_TID), lst(T(BOOL_TID)), (lhs, inst) -> bool(!inst.arg(0).boolValue())),
                instC(EQ_TID.dom(ALL).rng(BOOL_TID), lst(T(ALL)), (lhs, inst) -> bool(lhs.equals(inst.arg(0)))),
                instC(NEQ_TID.dom(ALL).rng(BOOL_TID), lst(T(ALL)), (lhs, inst) -> bool(!lhs.equals(inst.arg(0)))),
                instC(GT_TID.dom(INT_TID).rng(BOOL_TID), lst(T(INT_TID)), (lhs, inst) -> bool(lhs.intValue() > inst.arg(0).intValue())),
                instC(GT_TID.dom(REAL_TID).rng(BOOL_TID), lst(T(REAL_TID)), (lhs, inst) -> bool(lhs.realValue() > inst.arg(0).realValue())),
                instC(GT_TID.dom(STR_TID).rng(BOOL_TID), lst(T(STR_TID)), (lhs, inst) -> bool(lhs.intValue().compareTo(inst.arg(0).intValue()) > 0)),
                instC(GTE_TID.dom(INT_TID).rng(BOOL_TID), lst(T(INT_TID)), (lhs, inst) -> bool(lhs.intValue() >= inst.arg(0).intValue())),
                instC(GTE_TID.dom(REAL_TID).rng(BOOL_TID), lst(T(REAL_TID)), (lhs, inst) -> bool(lhs.realValue() >= inst.arg(0).realValue())),
                instC(GTE_TID.dom(STR_TID).rng(BOOL_TID), lst(T(STR_TID)), (lhs, inst) -> bool(lhs.intValue().compareTo(inst.arg(0).intValue()) >= 0)),
                instC(LT_TID.dom(INT_TID).rng(BOOL_TID), lst(T(INT_TID)), (lhs, inst) -> bool(lhs.intValue() < inst.arg(0).intValue())),
                instC(LT_TID.dom(REAL_TID).rng(BOOL_TID), lst(T(REAL_TID)), (lhs, inst) -> bool(lhs.realValue() < inst.arg(0).realValue())),
                instC(LT_TID.dom(STR_TID).rng(BOOL_TID), lst(T(STR_TID)), (lhs, inst) -> bool(lhs.intValue().compareTo(inst.arg(0).intValue()) < 0)),
                instC(LTE_TID.dom(INT_TID).rng(BOOL_TID), lst(T(INT_TID)), (lhs, inst) -> bool(lhs.intValue() <= inst.arg(0).intValue())),
                instC(LTE_TID.dom(REAL_TID).rng(BOOL_TID), lst(T(REAL_TID)), (lhs, inst) -> bool(lhs.realValue() <= inst.arg(0).realValue())),
                instC(LTE_TID.dom(STR_TID).rng(BOOL_TID), lst(T(STR_TID)), (lhs, inst) -> bool(lhs.intValue().compareTo(inst.arg(0).intValue()) <= 0)),
                /// ///////////////////////////////////////////////////////////////////////////////////////////////////
                instC(PLUS_TID.dom(BOOL_TID).rng(BOOL_TID), lst(T(BOOL_TID)), (lhs, inst) -> lhs.jvm(lhs.boolValue() || inst.arg(0).boolValue())),
                instC(PLUS_TID.dom(BYTES_TID).rng(BYTES_TID), lst(T(BYTES_TID)), (lhs, inst) -> lhs.<Bytes>as().plus(inst.arg(0).as())),
                instC(PLUS_TID.dom(INT_TID).rng(INT_TID), lst(T(INT_TID)), (lhs, inst) -> lhs.jvm(lhs.intValue() + inst.arg(0).intValue())),
                instC(PLUS_TID.dom(INT_TID.some()).rng(INT_TID.some()), lst(T(INT_TID)), (lhs, inst) -> objs(lhs.elements().map(i -> i.jvm(i.intValue() + inst.arg(0).intValue())))),
                instC(PLUS_TID.dom(REAL_TID).rng(REAL_TID), lst(T(REAL_TID)), (lhs, inst) -> lhs.jvm(lhs.realValue() + inst.arg(0).realValue())),
                instC(PLUS_TID.dom(STR_TID).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> lhs.jvm(lhs.strValue() + inst.arg(0).strValue())),
                instC(PLUS_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(T(URI_TID.maybe())), (lhs, inst) -> lhs.jvm(lhs.uriValue().plus(inst.arg(0).uriValue()))),
                instC(PLUS_TID.dom(LST_TID).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> lhs.jvm(Stream.concat(lhs.elements(), inst.arg(0).elements()).toList())),
                instC(PLUS_TID.dom(REC_TID).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> lhs.jvm(Stream.concat(lhs.<Rec>as().elements(), inst.arg(0).<Rec>as().elements().map(Obj::<Rel>as)).collect(Collectors.toMap(Rel::first, Rel::second, Obj::append, LinkedHashMap::new)))),
                instC(MULT_TID.dom(BOOL_TID).rng(BOOL_TID), lst(T(BOOL_TID)), (lhs, inst) -> lhs.jvm(lhs.boolValue() && inst.arg(0).boolValue())),
                instC(MULT_TID.dom(INT_TID).rng(INT_TID), lst(T(INT_TID)), (lhs, inst) -> lhs.jvm(lhs.intValue() * inst.arg(0).intValue())),
                instC(MULT_TID.dom(REAL_TID).rng(REAL_TID), lst(T(REAL_TID)), (lhs, inst) -> lhs.jvm(lhs.realValue() * inst.arg(0).realValue())),
                // MULT_TID, MInst.instC(MULT_TID.dom(STR_TID).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> lhs.value(lhs.strValue() + inst.arg(0).strValue())),
                instC(MULT_TID.dom(URI_TID).rng(URI_TID.maybe()), lst(T(URI_TID.maybe())), (lhs, inst) -> lhs.jvm(lhs.uriValue().mult(inst.arg(0).uriValue()))),
                instC(MULT_TID.dom(LST_TID).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> lhs.jvm(lhs.elements().flatMap(a -> inst.arg(0).elements().map(b -> rel(a, b))).toList())),
                // MULT_TID, MInst.instC(MULT_TID.dom(REC_TID).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> lhs.value(Stream.concat(lhs.recValue().entrySet().stream(), inst.arg(0).recValue().entrySet().stream()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b)))),
                instC(MINUS_TID.dom(INT_TID).rng(INT_TID), lst(T(INT_TID)), (lhs, inst) -> lhs.jvm(lhs.intValue() - inst.arg(0).intValue())),
                /// ///////////////////////////////////////////////////////////////////////////////////////////////////
                instC(TO_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(T(URI_TID)), (lhs, inst) -> Router.writeToSpace(inst.arg(0).uriValue(), lhs)),
                instC(FROM_TID.dom(ALL.maybe()).rng(ALL_STAR), lst(T(URI_TID)), (lhs, inst) -> Router.readFromSpace(inst.arg(0).uriValue())),
                instC(REF_TID.dom(ALL).rng(ALL_STAR), lst(T(ALL_STAR)), (lhs, inst) -> Router.writeToSpace(lhs.uriValue(), inst.arg(0))),
                instC(TYPE_TID.dom(A).rng(A), lst(), (lhs, inst) -> lhs.type()),
                instC(TYPE_TID.dom(A.some()).rng(A.some()), lst(), (lhs, inst) -> objs(lhs).type()),
                /// ///////////////////////////////////////////////////////////////////////////////////////////////////
                instC(URI_SCHEME_TID.dom(ALL).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue().scheme())),
                instC(URI_PORT_TID.dom(ALL).rng(INT_TID), lst(T(URI_TID)), (lhs, inst) -> jnt(lhs.uriValue().port())),
                instC(URI_HOST_TID.dom(ALL).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue().host())),
                instC(URI_PATH_TID.dom(ALL).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> uri(lhs.uriValue().path())),
                instC(URI_Q_TID.dom(ALL).rng(REC_TID), lst(T(URI_TID)), (lhs, inst) -> rec(lhs.uriValue().queryMap().entrySet().stream().map(kv -> rel(uri(kv.getKey()), uri(kv.getValue()))))),
                instC(URI_C_TID.dom(ALL).rng(LST_TID), lst(T(URI_TID)), (lhs, inst) -> lst(jnt(lhs.uriValue().cV().min()), jnt(lhs.uriValue().cV().max()))),
                /// ///////////////////////////////////////////////////////////////////////////////////////////////////
                instC(STR_UPPER_TID.dom(ALL).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> lhs.jvm(lhs.strValue().toUpperCase())),
                instC(STR_LOWER_TID.dom(ALL).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> lhs.jvm(lhs.strValue().toLowerCase())),
                /// ///////////////////////////////////////////////////////////////////////////////////////////////////
                instC(AS_TID.dom(A).rng(B), lst(T(B)), (lhs, inst) -> lhs.tid(inst.arg(0).tid().c(lhs.tid().c()))),
                instC(WITHIN_TID.dom(LST_TID).rng(LST_TID), lst(T(ALL_STAR)), (lhs, inst) -> lst(inst.arg(0).apply(objs(lhs.stream().flatMap(Obj::elements))).stream().toList())),
                instC(WITHIN_TID.dom(REC_TID).rng(REC_TID), lst(T(ALL_STAR)), (lhs, inst) -> rec(lhs.elements().map(r -> inst.arg(0).apply(r).<Rel>as()))),
                instC(FAILURE_TID.dom(ALL.maybeSome()).rng(FAIL_TID), lst(T(ALL.maybe())), (lhs, inst) -> fail(MTronException.of("%s", inst.arg(0).toString()))),
                instC(BARRIER_TID.dom(ALL_STAR).rng(ALL_STAR), lst(T(ALL_STAR)), (lhs, inst) -> inst.arg(0).apply(lhs)),
                instC(COUNT_TID.dom(ALL.maybeSome()).rng(INT_TID), lst(), (lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> jnt(a.intValue() + b.c().max())).intValue()/* * inst.c().max()*/), jnt(0)),
                instC(SUM_TID.dom(INT_TID.maybeSome()).rng(INT_TID), lst(), (lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> ((Int) a).plus((Int) b)).intValue()), jnt(0)),
                instC(SUM_TID.dom(LST_TID.maybeSome()).rng(LST_TID), lst(), (lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> ((Lst) a).plus((Lst) b)).lstValue()), lst()),
                instC(SUM_TID.dom(REAL_TID.maybeSome()).rng(REAL_TID), lst(), (lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> ((Real) a).plus((Real) b)).realValue()), real(0.0)),
                instC(SUM_TID.dom(URI_TID.maybeSome()).rng(URI_TID), lst(), (lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> ((Uri) a).plus((Uri) b)).uriValue()), uri(fURI.NOOBJ)),
                instC(SKIP_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(T(INT_TID)), (lhs, inst) -> lhs.take(cInt.of(inst.arg(0).intValue())).get1()), // retrieve
                instC(TAKE_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(T(INT_TID)), (lhs, inst) -> lhs.take(cInt.of(inst.arg(0).intValue())).get0()), // remaining
                //instC(SUM_TID.dom(A.maybeSome()).rng(A), lst(), (lhs, inst) -> ((Semiring.O)lhs).zero().jvm(IteratorUtil.reduce(lhs.iterator(), ((Semiring.O)lhs).zero(), (a, b) -> ((Semiring.O) a).plus((Semiring.O) b)).jvm()), uri(fURI.NOOBJ)),
                //instC(SUM_TID.dom(A.maybeSome()).rng(A), lst(), (lhs, inst) -> IteratorUtil.reduce((Iterator)lhs.iterator(), lhs.<Semiring.O>as().zero(), (a, b) -> a.plus(b)), NoObj.single()),
                //instC(SUM_TID.dom(REAL_TID.maybeSome()).rng(REAL_TID), lst(), (lhs, inst) -> IteratorUtil.reduce(lhs.iterator(), inst.seed(), (a, b) -> real(a.realValue() + (b.realValue() * b.c().max()))), real(0.0)),
                // instC(SUM_TID.dom(LST_TID.maybeSome()).rng(LST_TID), lst(), (lhs, inst) -> IteratorUtil.reduce(lhs.iterator(), inst.seed(), (a, b) -> lst(Stream.concat(a.lstValue().stream(), b.lstValue().stream()).toList())), lst()),
                instC(PROD_TID.dom(INT_TID.maybeSome()).rng(INT_TID), lst(), (lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> jnt(a.intValue() * (b.intValue() * b.c().max()))).intValue()/* * inst.c().max()*/), jnt(1)),
                instC(PROD_TID.dom(REAL_TID.maybeSome()).rng(REAL_TID), lst(), (lhs, inst) -> lhs.stream().reduce(inst.seed(), (a, b) -> real(a.realValue() * (b.realValue() * b.c().max()))), real(1.0)),
                instC(PROD_TID.dom(URI_TID.maybeSome()).rng(URI_TID), lst(), (lhs, inst) -> lhs.stream().reduce(inst.seed(), (a, b) -> uri(a.uriValue().mult(b.uriValue()))), uri(".")),
                instC(REIFY_TID.dom(ALL.maybe()).rng(REC_TID), lst(), (lhs, inst) -> rec(
                        "type", rec(
                                "tid", rec(
                                        "scheme", nullOrElse(lhs.tid().scheme(), NoObj::noobj, MUri::uri),
                                        "authority", nullOrElse(lhs.tid().hasAuthority() ? lhs.tid() : null, NoObj::noobj, z -> rec(
                                                "host", nullOrElse(z.host(), NoObj::noobj, MUri::uri),
                                                "port", nullOrElse(z.port() == -1 ? null : (long) lhs.tid().port(), NoObj::noobj, MInt::jnt)
                                        )),
                                        "path", uri(lhs.tid().path()),
                                        "c", rec(
                                                "min", jnt(lhs.tid().cV().min()),
                                                "max", jnt(lhs.tid().cV().max())),
                                        "q", nullOrElse(lhs.tid().query() == null ? null : lhs.tid().queryMap(), NoObj::noobj,
                                                q -> rec(q.entrySet().stream().map(kv -> rel(uri(kv.getKey()), uri(kv.getValue())))))),
                                "obj", rec(
                                        "value", lhs.type(),
                                        "params", nullOrElse(lhs.type().predicate() == null && lhs.type().constructor() == null ? null : lhs, NoObj::noobj, t -> rec(
                                                "predicate", nullOrElse(t.type().predicate(), NoObj::noobj, r -> r),
                                                "constructor", nullOrElse(t.type().constructor(), NoObj::noobj, r -> r))))),
                        "value", rec(
                                "vid", nullOrElse(lhs.vid(), NoObj::noobj, fURI::toUri),
                                "obj", rec(
                                        "value", MObjFactory.of().createOrFail(lhs.jvm()),
                                        "jvm", rec(
                                                "class", uri(lhs.jvm().getClass().getCanonicalName()),
                                                "projection", lhs.jvm() instanceof Tuple ?
                                                        rec(IteratorUtil.indexedStream(lhs.<Tuple>jvmAs().iterator()).map(p -> rel(jnt(p.get0()), MObjFactory.of().createOrFail(p.get1())))) :
                                                        rec(jnt(0), MObjFactory.of().create(lhs.jvm()))))))),
                instC(SELECT_TID.dom(REC_TID).rng(REC_TID.maybe()), lst(T(REC_TID)), (lhs, inst) -> crossPoly(lhs, inst.arg(0))),
                // instC(SELECT_TID.dom(REL_TID).rng(REL_TID), lst(T(REL_TID)), (lhs, inst) -> rel(inst.arg(0).<Rel>as().first().apply(lhs.<Rel>as().first()), inst.arg(0).<Rel>as().second().apply(lhs.<Rel>as().second()))),
                instC(SELECT_TID.dom(LST_TID).rng(LST_TID.maybe()), lst(T(LST_TID)), (lhs, inst) -> crossPoly(lhs, inst.arg(0))),
                //instC(SELECT_TID.dom(ALL).rng(REC_TID.maybe()), lst(T(REC_TID)), (lhs, inst) -> inst.arg(0).<Rec>as().jvm(inst.arg(0).<Rec>as().<Rel>elementStream().map(r -> Tuple.Pair.with(r.first().apply(lhs), r.second().apply(lhs))).collect(Collectors.toMap(Tuple.Pair::get0, Tuple.Pair::get1, Obj::append, LinkedHashMap::new)))),
                // instC(SELECT_TID.dom(ALL).rng(LST_TID.maybe()), lst(T(LST_TID)), (lhs, inst) -> inst.arg(0).<Lst>as().jvm(inst.arg(0).<Lst>as().elementStream().map(r -> r.apply(lhs)).toList())),
                instC(REDUCE_TID.dom(ALL.maybeSome()).rng(ALL), lst(T(ALL)), (lhs, inst) -> Stream.concat(inst.arg(0).<Inst>as().arg(0).stream(), lhs.stream()).reduce((a, b) -> inst.arg(0).<Inst>as().args(lst(a)).apply(b)).orElse(noobj())),
                instC(WHERE_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> lhs.matches(inst.arg(0)) ? lhs : noobj()),
                instC(GROUP_TID.dom(ALL.maybeSome()).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> {
                    final Map<Obj, Obj> result = new LinkedHashMap<>();
                    lhs.stream().forEach(e -> {
                        inst.arg(0).<Rec>as().elements().forEach(kv -> {
                            final Obj kk = kv.first().isCall() ? kv.first().apply(e) : (e.matches(kv.first()) ? e : noobj());
                            if (!kk.isNoObj()) {
                                final Obj vv = kv.second().apply(e);
                                if (!vv.isNoObj()) // TODO: stream through keys to get matching key for incur-append on grouping to the same key
                                    result.compute(kk, (k, v) -> (v == null) ? vv : v.append(vv));
                            }
                        });
                    });
                    return rec(result);
                }),
                instC(MATH_TID.dom(ALL.maybe()).rng(REAL_TID), lst(T(STR_TID)), (lhs, inst) -> {
                    final String equation = inst.arg(0).strValue();
                    final Set<String> variables = MathUtil.getVariables(equation);
                    final double result = new ExpressionBuilder(equation)
                            .variables(MathUtil.getVariables(equation))
                            .build()
                            .setVariables(variables.stream()
                                    .map(var -> List.of(var, Router.readFromSpace(var).<Number>jvm().doubleValue()))
                                    .collect(Collectors.toMap(
                                            a -> a.get(0).toString(),
                                            b -> (Double) b.get(1),
                                            (a, b) -> b,
                                            HashMap::new)))
                            .evaluate();
                    return real(result);
                })
        ));
    }

}
