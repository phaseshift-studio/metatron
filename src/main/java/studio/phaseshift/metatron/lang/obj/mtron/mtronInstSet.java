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

package studio.phaseshift.metatron.lang.obj.mtron;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.*;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.fURI.ALL;
import static studio.phaseshift.metatron.lang.fURI.f;
import static studio.phaseshift.metatron.lang.obj.mtron.MBool.bool;
import static studio.phaseshift.metatron.lang.obj.mtron.MInst.instC;
import static studio.phaseshift.metatron.lang.obj.mtron.MInt.jnt;
import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;
import static studio.phaseshift.metatron.lang.obj.mtron.MObjs.objs;
import static studio.phaseshift.metatron.lang.obj.mtron.MReal.real;
import static studio.phaseshift.metatron.lang.obj.mtron.MRec.rec;
import static studio.phaseshift.metatron.lang.obj.mtron.MType.T;
import static studio.phaseshift.metatron.lang.obj.mtron.MUri.uri;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronFluent.StartLess.e1se;

public class mtronInstSet extends MInstSet {


    public static final fURI MTRON_TID = fURI.of("/mtron");
    public static final fURI BOOL_TID = fURI.of("/mtron/bool");
    public static final fURI INT_TID = fURI.of("/mtron/int");
    public static final fURI REAL_TID = fURI.of("/mtron/real");
    public static final fURI STR_TID = fURI.of("/mtron/str");
    public static final fURI URI_TID = fURI.of("/mtron/uri");
    public static final fURI REL_TID = fURI.of("/mtron/rel");
    public static final fURI LST_TID = fURI.of("/mtron/lst");
    public static final fURI REC_TID = fURI.of("/mtron/rec");
    public static final fURI INST_TID = fURI.of("/mtron/inst");
    public static final fURI CODE_TID = fURI.of("/mtron/code");
    public static final fURI OBJS_TID = fURI.of("/mtron/objs");
    public static final fURI OBJS_ID = ALL.maybeSome();
    public static final fURI POLY_TID = fURI.of("/mtron/poly");
    public static final fURI MONO_TID = fURI.of("/mtron/mono");
    public static final fURI NOOBJ_TID = fURI.of("");

    public static final Set<fURI> BASE_TYPES = Set.of(
            BOOL_TID, INT_TID, REAL_TID,
            STR_TID, URI_TID, REL_TID,
            LST_TID, REC_TID, INST_TID,
            CODE_TID, OBJS_TID, NOOBJ_TID);

    public static final fURI MTRON_INST_TID = fURI.of("/mtron/inst");
    public static final fURI ID_TID = INST_TID.extend("id");
    public static final fURI APPLY_TID = INST_TID.extend("apply");
    public static final fURI START_TID = INST_TID.extend("start");
    public static final fURI COUNT_TID = INST_TID.extend("count");
    public static final fURI SUM_TID = INST_TID.extend("sum");
    public static final fURI PROD_TID = INST_TID.extend("prod");
    public static final fURI REDUCE_TID = INST_TID.extend("reduce");
    public static final fURI MULT_TID = INST_TID.extend("mult");
    public static final fURI PLUS_TID = INST_TID.extend("plus");
    public static final fURI MAP_TID = INST_TID.extend("map");
    public static final fURI FILTER_TID = INST_TID.extend("filter");
    public static final fURI TO_TID = INST_TID.extend("to");
    public static final fURI FROM_TID = INST_TID.extend("from");
    public static final fURI REF_TID = INST_TID.extend("ref");
    public static final fURI SPLIT_TID = INST_TID.extend("split");
    public static final fURI MERGE_TID = INST_TID.extend("merge");
    public static final fURI WITHIN_TID = INST_TID.extend("within");
    public static final fURI BLOCK_TID = INST_TID.extend("block");
    public static final fURI RNG_TID = INST_TID.extend("rng");
    public static final fURI DOM_TID = INST_TID.extend("dom");
    public static final fURI TID_TID = INST_TID.extend("tid");
    public static final fURI VID_TID = INST_TID.extend("vid");
    public static final fURI TYPE_TID = INST_TID.extend("type");
    public static final fURI GET_TID = INST_TID.extend("get");
    public static final fURI AS_TID = INST_TID.extend("as");
    public static final fURI AT_TID = INST_TID.extend("at");
    public static final fURI IS_TID = INST_TID.extend("is");
    public static final fURI ISA_TID = INST_TID.extend("isa");
    public static final fURI IN_TID = INST_TID.extend("in");
    public static final fURI EQ_TID = INST_TID.extend("eq");
    public static final fURI NEQ_TID = INST_TID.extend("neq");
    public static final fURI GT_TID = INST_TID.extend("gt");
    public static final fURI LT_TID = INST_TID.extend("lt");
    public static final fURI GTE_TID = INST_TID.extend("gte");
    public static final fURI LTE_TID = INST_TID.extend("lte");
    public static final fURI NOT_TID = INST_TID.extend("not");
    public static final fURI BARRIER_TID = INST_TID.extend("barrier");
    public static final fURI REIFY_TID = INST_TID.extend("reify");
    public static final fURI CROSS_TID = INST_TID.extend("cross");
    public static final fURI ELSE_TID = INST_TID.extend("else");
    public static final fURI END_TID = INST_TID.extend("end");
    public static final fURI SWAP_TID = INST_TID.extend("swap");

    public mtronInstSet(final fURI vid) {
        super(MTRON_TID, vid);
        this.types().forEach(t -> Router.global().registerRewrite(f(t.tid().name()), t.tid()));
    }

    @Override
    public Set<Type> types() {
        return Stream.of(T(BOOL_TID), T(INT_TID), T(REAL_TID), T(STR_TID), T(URI_TID), T(LST_TID),
                T(REC_TID), T(INST_TID), T(REC_TID), T(OBJS_TID), T(NOOBJ_TID)).collect(Collectors.toSet());
    }

    @Override
    public Set<Inst> insts() {
        return Stream.of(
                instC(START_TID.dom(fURI.NOOBJ.zero()).rng(A.maybeSome()), lst(T(A.maybeSome())), (lhs, inst) -> inst.arg(0)),
                instC(END_TID.dom(OBJS_ID).rng(NOOBJ_TID.zero()), lst(), (lhs, inst) -> NoObj.single()),
                instC(ID_TID.dom(A.maybe()).rng(A.maybe()), lst(), (lhs, inst) -> lhs),
                instC(ID_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(), (lhs, inst) -> lhs),
                instC(APPLY_TID.dom(ALL).rng(ALL), lst(T(INST_TID)), (lhs, inst) -> inst.arg(0).apply(lhs)),
                instC(MAP_TID.dom(ALL).rng(A), lst(T(A)), (lhs, inst) -> inst.arg(0)),
                instC(MAP_TID.dom(ALL).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> inst.arg(0)),
                instC(TID_TID.dom(ALL).rng(URI_TID), lst(), (lhs, inst) -> lhs.tid().toUri()),
                instC(VID_TID.dom(ALL).rng(ALL), lst(T(URI_TID)), (lhs, inst) -> lhs.vid(inst.arg(0).uriValue())),
                instC(VID_TID.dom(ALL).rng(URI_TID), lst(), (lhs, inst) -> null == lhs.vid() ? NoObj.single() : lhs.vid().toUri()),
                instC(ELSE_TID.dom(ALL.maybe()).rng(ALL), lst(T(ALL.maybe())), (lhs, inst) -> lhs.isNoObj() ? inst.arg(0) : lhs), // TODO: rec args needs resolution on generics connected
                instC(IS_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL)), (lhs, inst) -> inst.arg(0).boolValue() ? lhs : NoObj.single()), // TODO: generics are not working for some reason
                instC(ISA_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(T(OBJS_ID)), (lhs, inst) -> lhs.matches(inst.arg(0)) ? lhs : NoObj.single()),
                instC(IN_TID.dom(ALL.maybe()).rng(BOOL_TID), lst(T(ALL.maybe())), (lhs, inst) -> bool(lhs.matches(inst.arg(0)))),
                instC(GET_TID.dom(REC_TID).rng(OBJS_ID), lst(T(URI_TID)), (lhs, inst) -> lhs.<Rec>as().at(inst.arg(0))),
                instC(GET_TID.dom(LST_TID).rng(OBJS_ID), lst(T(INT_TID)), (lhs, inst) -> lhs.<Lst>as().at(inst.arg(0))),
                /// ///////////////////////////////////////////////////////////////////////////////////////////////////
                instC(BLOCK_TID.dom(A.maybe()).rng(A.maybe()), lst(T(A)), (lhs, inst) -> inst.arg(0)),
                instC(SWAP_TID.dom(A).rng(B), lst(T(C)), (lhs, inst) -> lhs.apply(inst.arg(0))),
                /// ///////////////////////////////////////////////////////////////////////////////////////////////////
                instC(SPLIT_TID.dom(ALL).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> MLst.of(inst.arg(0).lstValue().stream().map(e -> e.apply(lhs)).toList())),
                instC(SPLIT_TID.dom(ALL.maybeSome()).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> MLst.of(inst.arg(0).lstValue().stream().map(e -> e.apply(lhs)).toList())),
                instC(SPLIT_TID.dom(ALL).rng(REL_TID), lst(T(REL_TID)), (lhs, inst) -> MRel.of(inst.arg(0).<Rel>as().first().apply(lhs), inst.arg(0).<Rel>as().second().apply(lhs))),
                instC(SPLIT_TID.dom(ALL).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> MRec.of(inst.arg(0).recValue().entrySet().stream().map(e -> e.getKey().apply(lhs).choose(Obj::isNoObj, x -> null, x -> MRel.of(x, e.getValue().apply(lhs)))).filter(x -> !Objects.isNull(x)).collect(Collectors.toMap(a -> a.<Rel>as().first(), b -> b.<Rel>as().second(), Obj::append, LinkedHashMap<Obj, Obj>::new)))),
                instC(SPLIT_TID.dom(A.maybeSome()).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) ->
                        inst.arg(0).value(lhs.stream().flatMap(o -> inst.arg(0).<Rec>as()
                                        .stream()
                                        .map(rel -> rel.first()
                                                .apply(o)
                                                .andThen(oo -> oo.isNoObj() ?
                                                        NoObj.single() :
                                                        rel.second(rel.second().apply(oo))).apply(o))//.choose(Obj::isNoObj, NoObj.single(), r -> rel.second(rel.second().apply(o))))
                                        .filter(p -> !p.isNoObj())
                                        .map(Obj::<Rel>as))
                                .collect(Collectors.toMap(Rel::first, Rel::second, Obj::append, LinkedHashMap::new)))),
                instC(SPLIT_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(T(A.maybeSome())), (lhs, inst) -> objs(inst.arg(0).stream().map(o -> o.apply(lhs)))),
                instC(SPLIT_TID.dom(ALL).rng(ALL.maybeSome()), lst(T(ALL.some())), (lhs, inst) -> objs(inst.arg(0).stream().map(o -> o.apply(lhs)))),
                instC(SPLIT_TID.dom(ALL).rng(ALL.maybeSome()), lst(T(ALL)), (lhs, inst) -> inst.arg(0).apply(lhs)),
                /// ///////////////////////////////////////////////////////////////////////////////////////////////////
                instC(MERGE_TID.dom(LST_TID).rng(OBJS_ID), lst(), (lhs, inst) -> objs(lhs.<Lst>as().value())),
                instC(MERGE_TID.dom(REC_TID).rng(REL_TID.maybeSome()), lst(), (lhs, inst) -> objs(lhs.stream().toList())),
                //
                instC(MERGE_TID.dom(A.maybeSome()).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> inst.arg(0).value(Stream.concat(lhs.stream(), inst.arg(0).lstValue().stream()).toList())),
                instC(MERGE_TID.dom(REL_TID.maybeSome()).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> inst.arg(0).value(Stream.concat(lhs.stream().map(Obj::as), inst.arg(0).<Rec>as().stream()).collect(Collectors.toMap(Rel::first, Rel::second, Obj::append, LinkedHashMap::new)))),
                instC(MERGE_TID.dom(A).rng(A), lst(), (lhs, inst) -> lhs),
                //
                instC(MERGE_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(T(A.maybeSome())), (lhs, inst) -> objs(Stream.concat(lhs.stream(), inst.arg(0).stream()))),
                instC(MERGE_TID.dom(A.maybeSome()).rng(A.maybeSome()), lst(), (lhs, inst) -> lhs),
                /// ///////////////////////////////////////////////////////////////////////////////////////////////////
                instC(DOM_TID.dom(REL_TID).rng(ALL), lst(), (lhs, inst) -> lhs.relValue().get0()),
                instC(RNG_TID.dom(REL_TID).rng(ALL.some()), lst(), (lhs, inst) -> lhs.relValue().get1()),
                instC(DOM_TID.dom(REC_TID).rng(OBJS_ID), lst(), (lhs, inst) -> objs(lhs.recValue().keySet())),
                instC(RNG_TID.dom(REC_TID).rng(OBJS_ID), lst(), (lhs, inst) -> objs(lhs.recValue().values())),
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
                instC(PLUS_TID.dom(BOOL_TID).rng(BOOL_TID), lst(T(BOOL_TID)), (lhs, inst) -> lhs.value(lhs.boolValue() || inst.arg(0).boolValue())),
                instC(PLUS_TID.dom(INT_TID).rng(INT_TID), lst(T(INT_TID)), (lhs, inst) -> lhs.value(lhs.intValue() + inst.arg(0).intValue())),
                instC(PLUS_TID.dom(INT_TID.some()).rng(INT_TID.some()), lst(T(INT_TID)), (lhs, inst) -> objs(lhs.<Int>stream().map(i -> i.value(i.intValue() + inst.arg(0).intValue())))),
                instC(PLUS_TID.dom(REAL_TID).rng(REAL_TID), lst(T(REAL_TID)), (lhs, inst) -> lhs.value(lhs.realValue() + inst.arg(0).realValue())),
                instC(PLUS_TID.dom(STR_TID).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> lhs.value(lhs.strValue() + inst.arg(0).strValue())),
                instC(PLUS_TID.dom(URI_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> lhs.value(lhs.uriValue().plus(inst.arg(0).uriValue()))),
                instC(PLUS_TID.dom(LST_TID).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> lhs.value(Stream.concat(lhs.lstValue().stream(), inst.arg(0).lstValue().stream()).toList())),
                instC(PLUS_TID.dom(REC_TID).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> lhs.value(Stream.concat(lhs.recValue().entrySet().stream(), inst.arg(0).recValue().entrySet().stream()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Obj::append, LinkedHashMap::new)))),
                instC(MULT_TID.dom(BOOL_TID).rng(BOOL_TID), lst(T(BOOL_TID)), (lhs, inst) -> lhs.value(lhs.boolValue() && inst.arg(0).boolValue())),
                instC(MULT_TID.dom(INT_TID).rng(INT_TID), lst(T(INT_TID)), (lhs, inst) -> lhs.value(lhs.intValue() * inst.arg(0).intValue())),
                instC(MULT_TID.dom(REAL_TID).rng(REAL_TID), lst(T(REAL_TID)), (lhs, inst) -> lhs.value(lhs.realValue() * inst.arg(0).realValue())),
                // MULT_TID, MInst.instC(MULT_TID.dom(STR_TID).rng(STR_TID), lst(T(STR_TID)), (lhs, inst) -> lhs.value(lhs.strValue() + inst.arg(0).strValue())),
                instC(MULT_TID.dom(URI_TID).rng(URI_TID), lst(T(URI_TID)), (lhs, inst) -> lhs.value(lhs.uriValue().mult(inst.arg(0).uriValue()))),
                instC(MULT_TID.dom(LST_TID).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> lhs.value(lhs.lstValue().stream().flatMap(a -> inst.arg(0).lstValue().stream().map(b -> MRel.of(a, b))).toList())),
                // MULT_TID, MInst.instC(MULT_TID.dom(REC_TID).rng(REC_TID), lst(T(REC_TID)), (lhs, inst) -> lhs.value(Stream.concat(lhs.recValue().entrySet().stream(), inst.arg(0).recValue().entrySet().stream()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b)))),
                /// ///////////////////////////////////////////////////////////////////////////////////////////////////
                instC(TO_TID.dom(ALL.maybe()).rng(ALL.maybe()), lst(T(URI_TID)), (lhs, inst) -> Router.global().write(inst.arg(0).uriValue(), lhs)),
                instC(FROM_TID.dom(ALL.maybe()).rng(OBJS_ID), lst(T(URI_TID)), (lhs, inst) -> Router.global().read(inst.arg(0).uriValue())),
                instC(REF_TID.dom(ALL).rng(OBJS_ID), lst(T(OBJS_ID)), (lhs, inst) -> Router.global().write(lhs.uriValue(), inst.arg(0))),
                instC(TYPE_TID.dom(A).rng(A), lst(), (lhs, inst) -> lhs.type()),
                instC(TYPE_TID.dom(A.some()).rng(A.some()), lst(), (lhs, inst) -> objs(lhs).type()),
                /// ///////////////////////////////////////////////////////////////////////////////////////////////////
                instC(AS_TID.dom(OBJS_ID).rng(A), lst(T(T(A))), (lhs, inst) -> {
                    final Type t = inst.arg(0).as();
                    if (T(LST_TID).matches(t)) {
                        if (lhs.isObjs()) {
                            return lst(IteratorUtil.stream(lhs.objsValue()).toList());
                        }
                    }
                    throw MTronException.of("unknown pair: %s %s", lhs, t);
                }),
                instC(WITHIN_TID.dom(LST_TID).rng(LST_TID), lst(T(OBJS_ID)), (lhs, inst) -> lst(inst.arg(0).apply(objs(lhs.lstValue())))),
                instC(WITHIN_TID.dom(REC_TID).rng(REC_TID), lst(T(OBJS_ID)), (lhs, inst) -> rec(lhs.recValue().entrySet().stream().map(kv -> inst.arg(0).apply(MRel.of(kv.getKey(), kv.getValue())).<Rel>as()).collect(Collectors.toMap(Rel::first, Rel::second, Obj::append, LinkedHashMap<Obj, Obj>::new)))),
                instC(BARRIER_TID.dom(OBJS_ID).rng(OBJS_ID), lst(T(OBJS_ID)), (lhs, inst) -> inst.arg(0).apply(lhs)),
                instC(COUNT_TID.dom(ALL.maybeSome()).rng(INT_TID), lst(), (lhs, inst) -> inst.seed().value(IteratorUtil.reduce(lhs.iterator(), inst.seed(), (a, b) -> jnt(a.intValue() + b.c().max())).intValue() * inst.c().max()), jnt(0)),
                instC(SUM_TID.dom(INT_TID.maybeSome()).rng(INT_TID), lst(), (lhs, inst) -> inst.seed().value(IteratorUtil.reduce(lhs.iterator(), inst.seed(), (a, b) -> jnt(a.intValue() + (b.intValue() * b.c().max()))).intValue() * inst.c().max()), jnt(0)),
                instC(SUM_TID.dom(REAL_TID.maybeSome()).rng(REAL_TID), lst(), (lhs, inst) -> IteratorUtil.reduce(lhs.iterator(), inst.seed(), (a, b) -> real(a.realValue() + (b.realValue() * b.c().max()))), real(0.0)),
                instC(SUM_TID.dom(LST_TID.maybeSome()).rng(LST_TID), lst(), (lhs, inst) -> IteratorUtil.reduce(lhs.iterator(), inst.seed(), (a, b) -> lst(Stream.concat(a.lstValue().stream(), b.lstValue().stream()).toList())), lst()),
                instC(REIFY_TID.dom(ALL.maybe()).rng(REC_TID), lst(), (lhs, inst) ->
                        MRec.ofUriKeyed(
                                "tid", MRec.ofUriKeyed(
                                        "path", MUri.of(lhs.tid().path()),
                                        "c", MRec.ofUriKeyed(
                                                "min", MInt.of(lhs.tid().cV().min()),
                                                "max", MInt.of(lhs.tid().cV().max())),
                                        "query", MStr.of(lhs.tid().query().toString())),
                                "value", MObjFactory.of().create(lhs.value()))),
                instC(CROSS_TID.dom(LST_TID).rng(LST_TID), rec(uri("other"), T(LST_TID), uri("func"), e1se(MInst.instA(ID_TID))), (lhs, inst) -> {
                    final List<Obj> result = new ArrayList<>();
                    final Obj toEval = inst.arg(f("func"));
                    final List<Obj> lhsList = lhs.lstValue();
                    final List<Obj> rhsList = inst.arg(f("other")).lstValue();
                    for (int i = 0; i < lhsList.size(); i++) {
                        if (rhsList.size() > i) {
                            final Obj lhsA = lhsList.get(i);
                            final Obj rhsA = rhsList.get(i);
                            Router.stack().push(rec(uri("b1"), rhsA));
                            result.add(toEval.isNoObj() ? rhsA.apply(lhsA) : toEval.<Inst>as().apply(lhsA));
                        } else {
                            break;
                        }
                    }
                    return lhs.value(result);
                })
        ).collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        //TODO: convert below to the pure write() model above
        // this.define(NOOBJ_TID, fURI.ANY.maybe(), fURI.ANY.maybe(), MLst.of(), (lhs, inst) -> lhs); // noobj is also an inst (no inst)
        /*
        this.define(BARRIER_TID, fURI.ANY.any(), fURI.ANY.any(), MLst.of(ID__), (lhs, inst) -> inst.arg(0).apply(lhs), MObjs.of(List.of()));
        this.define(AT_TID, fURI.ANY, fURI.ANY, MLst.of(ID__), (lhs, inst) -> lhs.vid(inst.arg(0).uriValue()));
        this.define(DOM_TID, REC_TID, fURI.ANY, MLst.of(), (lhs, inst) -> MObjs.of(lhs.recValue().keySet()));
        this.define(DOM_TID, REL_TID, fURI.ANY, MLst.of(), (lhs, inst) -> MObjs.of(lhs.relValue().getValue0()));
        this.define(DOM_TID, fURI.ANY, fURI.ANY, MLst.of(), (lhs, inst) -> lhs);
        this.define(RNG_TID, REC_TID, fURI.ANY, MLst.of(), (lhs, inst) -> MObjs.of(lhs.recValue().values()));
        this.define(RNG_TID, REL_TID, fURI.ANY, MLst.of(), (lhs, inst) -> MObjs.of(lhs.relValue().getValue1()));
        this.define(RNG_TID, fURI.ANY, fURI.ANY, MLst.of(), (lhs, inst) -> lhs);*/

    }

    public static mtronInstSet of(final fURI vid) {
        return new mtronInstSet(vid);
    }
}
