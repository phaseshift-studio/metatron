/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang.obj.mtron;

import org.javatuples.Pair;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.*;
import studio.phaseshift.metatron.lang.obj.base.furi.TypefURI;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.space.mem.MSpace;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.fURI.f;
import static studio.phaseshift.metatron.lang.obj.mtron.MBool.bool;
import static studio.phaseshift.metatron.lang.obj.mtron.MInt.jnt;
import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;
import static studio.phaseshift.metatron.lang.obj.mtron.MType.T;

public class MInstSet extends MSpace implements InstSet {

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
    public static final fURI POLY_TID = fURI.of("/mtron/poly");
    public static final fURI MONO_TID = fURI.of("/mtron/mono");
    public static final fURI NOOBJ_TID = fURI.of("");

    public static final fURI MTRON_INST_TID = fURI.of("/mtron/inst");
    public static final fURI ID_TID = INST_TID.extend("id");
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
    public static final fURI AT_TID = INST_TID.extend("at");
    public static final fURI IS_TID = INST_TID.extend("is");
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

    // inst_tid -> <inst_tid_dom -> set<inst>>
    private static final Map<fURI, Map<fURI, Set<Inst>>> SYMBOL_TABLE = new LinkedHashMap<>();
    private static final Inst ID__ = MInst.instB(ID_TID, MLst.of());
    private static final Lst NO_ARGS__ = MLst.of();

    public void load() {
        this.define(NOOBJ_TID, fURI.ANY.maybe(), fURI.ANY.maybe(), MLst.of(), (lhs, inst) -> lhs); // noobj is also an inst (no inst)
        this.define(REIFY_TID, fURI.ANY.maybe(), REC_TID, MLst.of(), (lhs, inst) ->
                MRec.ofUriKeyed(
                        "tid", MRec.ofUriKeyed(
                                "path", MUri.of(lhs.tid().path()),
                                "c", MRec.ofUriKeyed(
                                        "min", MInt.of(lhs.tid().coefficientValue().min()),
                                        "max", MInt.of(lhs.tid().coefficientValue().max())),
                                "query", MStr.of(lhs.tid().queryMap().toString())),
                        "value", MObjFactory.of().create(lhs.value()))
        );
        this.define(ELSE_TID, fURI.ANY.maybe(), fURI.ANY, MLst.of(ID__), (lhs, inst) -> inst.arg(0));
        this.define(GET_TID, REC_TID, fURI.ANY.any(), MLst.of(ID__), (lhs, inst) -> lhs.<Rec>as().at(inst.arg(0)));
        this.define(BARRIER_TID, fURI.ANY.any(), fURI.ANY.any(), MLst.of(ID__), (lhs, inst) -> inst.arg(0).apply(lhs), MObjs.of(List.of()));
        this.define(MERGE_TID, LST_TID, fURI.ANY.any(), NO_ARGS__, (lhs, inst) -> lhs.isPoly() ? MObjs.of(lhs.<Poly>as().elements()) : lhs);
        this.define(MERGE_TID, REC_TID, REL_TID.any(), NO_ARGS__, (lhs, inst) -> lhs.isPoly() ? MObjs.of(lhs.<Poly>as().elements()) : lhs);
        this.define(MERGE_TID, fURI.ANY, fURI.ANY, NO_ARGS__, (lhs, inst) -> lhs);
        this.define(AT_TID, fURI.ANY, fURI.ANY, MLst.of(ID__), (lhs, inst) -> lhs.vid(inst.arg(0).uriValue()));
        this.define(TYPE_TID, fURI.ANY, fURI.ANY, MLst.of(), (lhs, inst) -> MType.of(lhs.tid()));
        this.define(TID_TID, fURI.ANY, URI_TID, MLst.of(), (lhs, inst) -> lhs.tid().toUri());
        this.define(VID_TID, fURI.ANY, URI_TID, MLst.of(), (lhs, inst) -> lhs.vid().toUri());
        this.define(MAP_TID, fURI.ANY, fURI.ANY, MLst.of(ID__), (lhs, inst) -> inst.arg(0));
        this.define(FILTER_TID, fURI.ANY, fURI.ANY.maybe(), MLst.of(ID__), (lhs, inst) -> inst.arg(0));
        this.define(BLOCK_TID, fURI.ANY.maybe(), fURI.ANY.maybe(), MLst.of(ID__), (lhs, inst) -> inst.arg(0));
        this.define(DOM_TID, REC_TID, fURI.ANY, MLst.of(), (lhs, inst) -> MObjs.of(lhs.recValue().keySet()));
        this.define(DOM_TID, REL_TID, fURI.ANY, MLst.of(), (lhs, inst) -> MObjs.of(lhs.relValue().getValue0()));
        this.define(DOM_TID, fURI.ANY, fURI.ANY, MLst.of(), (lhs, inst) -> lhs);
        this.define(RNG_TID, REC_TID, fURI.ANY, MLst.of(), (lhs, inst) -> MObjs.of(lhs.recValue().values()));
        this.define(RNG_TID, REL_TID, fURI.ANY, MLst.of(), (lhs, inst) -> MObjs.of(lhs.relValue().getValue1()));
        this.define(RNG_TID, fURI.ANY, fURI.ANY, MLst.of(), (lhs, inst) -> lhs);
        this.define(TO_TID, fURI.ANY.maybe(), fURI.ANY.maybe(), MLst.of(ID__), (lhs, inst) -> Router.global().write(inst.arg(0).uriValue(), lhs));
        this.define(FROM_TID, fURI.ANY.maybe(), fURI.ANY.any(), MLst.of(ID__), (lhs, inst) -> Router.global().read(inst.arg(0).uriValue()));
        this.define(REF_TID, fURI.ANY, fURI.ANY, MLst.of(ID__), (lhs, inst) -> Router.global().write(lhs.uriValue(), inst.arg(0)));
        this.define(ID_TID, fURI.ANY.maybe(), fURI.ANY.maybe(), MLst.of(), (lhs, inst) -> lhs);
        this.define(START_TID, NOOBJ_TID.zero(), fURI.ANY.any(), MLst.of(ID__), (lhs, inst) -> inst.arg(0));
        this.define(PLUS_TID, BOOL_TID, BOOL_TID, MLst.of(ID__), (lhs, inst) -> lhs.value(lhs.boolValue() || inst.arg(0).boolValue()));
        this.define(PLUS_TID, INT_TID, INT_TID, lst(T(INT_TID)), (lhs, inst) -> lhs.value(lhs.intValue() + inst.arg(0).intValue()));
        this.define(PLUS_TID, REAL_TID, REAL_TID, MLst.of(ID__), (lhs, inst) -> lhs.value(lhs.realValue() + inst.arg(0).realValue()));
        this.define(PLUS_TID, STR_TID, STR_TID, MLst.of(ID__), (lhs, inst) -> lhs.value(lhs.strValue() + inst.arg(0).strValue()));
        this.define(PLUS_TID, URI_TID, URI_TID, MLst.of(ID__), (lhs, inst) -> lhs.value(lhs.uriValue().extend(inst.arg(0).uriValue())));
        this.define(PLUS_TID, LST_TID, LST_TID, MLst.of(ID__), (lhs, inst) -> lhs.value(Stream.concat(lhs.lstValue().stream(), inst.arg(0).lstValue().stream()).toList()));
        this.define(MULT_TID, INT_TID, INT_TID, MLst.of(ID__), (lhs, inst) -> lhs.value(lhs.intValue() * inst.arg(0).intValue()));
        this.define(MULT_TID, REAL_TID, REAL_TID, MLst.of(ID__), (lhs, inst) -> lhs.value(lhs.realValue() * inst.arg(0).realValue()));
        this.define(MULT_TID, URI_TID, URI_TID, MLst.of(ID__), (lhs, inst) -> lhs.value(lhs.uriValue().retractPattern().extend(inst.arg(0).uriValue())));
        this.define(IS_TID, fURI.ANY, fURI.ANY.maybe(), MLst.of(ID__), (lhs, inst) -> inst.arg(0).boolValue() ? lhs : NoObj.single());
        this.define(IN_TID, fURI.ANY, BOOL_TID, MLst.of(ID__), (lhs, inst) -> bool(lhs.matches(inst.arg(0))));
        this.define(EQ_TID, fURI.ANY, BOOL_TID, MLst.of(ID__), (lhs, inst) -> bool(lhs.equals(inst.arg(0))));
        this.define(NEQ_TID, fURI.ANY, BOOL_TID, MLst.of(ID__), (lhs, inst) -> bool(!lhs.equals(inst.arg(0))));
        this.define(GT_TID, INT_TID, BOOL_TID, MLst.of(ID__), (lhs, inst) -> bool(lhs.intValue() > inst.arg(0).intValue()));
        this.define(GT_TID, REAL_TID, BOOL_TID, MLst.of(ID__), (lhs, inst) -> bool(lhs.realValue() > inst.arg(0).realValue()));
        this.define(GT_TID, STR_TID, BOOL_TID, MLst.of(ID__), (lhs, inst) -> bool(lhs.strValue().compareTo(inst.arg(0).strValue()) > 0));
        this.define(LT_TID, INT_TID, BOOL_TID, MLst.of(ID__), (lhs, inst) -> bool(lhs.intValue() < inst.arg(0).intValue()));
        this.define(LT_TID, REAL_TID, BOOL_TID, MLst.of(ID__), (lhs, inst) -> bool(lhs.realValue() < inst.arg(0).realValue()));
        this.define(GTE_TID, INT_TID, BOOL_TID, MLst.of(ID__), (lhs, inst) -> bool(lhs.intValue() >= inst.arg(0).intValue()));
        this.define(GTE_TID, REAL_TID, BOOL_TID, MLst.of(ID__), (lhs, inst) -> bool(lhs.realValue() >= inst.arg(0).realValue()));
        this.define(LTE_TID, INT_TID, BOOL_TID, MLst.of(ID__), (lhs, inst) -> bool(lhs.intValue() <= inst.arg(0).intValue()));
        this.define(LTE_TID, INT_TID, BOOL_TID, MLst.of(ID__), (lhs, inst) -> bool(lhs.intValue() <= inst.arg(0).intValue()));
        this.define(NOT_TID, fURI.ANY, BOOL_TID, MLst.of(MType.of(BOOL_TID)), (lhs, inst) -> bool(!inst.arg(0).boolValue()));
        this.define(WITHIN_TID, LST_TID, LST_TID, MLst.of(ID__), (lhs, inst) -> MLst.of(lhs.<Lst>as().lstValue().stream().map(o -> inst.arg(0).apply(o)).toList()));
        this.define(CROSS_TID, LST_TID, LST_TID, MRec.ofUriKeyed("c", ID__, "l", ID__), (lhs, inst) -> {
            final List<Obj> result = new ArrayList<>();
            final Obj toEval = inst.arg(0);
            final List<Obj> lhsList = lhs.lstValue();
            final List<Obj> rhsList = inst.arg(fURI.of("l")).lstValue();
            for (int i = 0; i < lhsList.size(); i++) {
                if (rhsList.size() > i) {
                    final Obj lhsA = lhsList.get(i);
                    final Obj rhsA = rhsList.get(i);
                    Router.stack().push(MRec.ofUriKeyed("l", rhsA));
                    result.add(toEval.apply(lhsA));
                } else {
                    break;
                }
            }
            return MLst.of(result);
        });
        this.define(SPLIT_TID, fURI.ANY, fURI.ANY, MLst.of(ID__), (lhs, inst) -> {
            if (inst.arg(0).isLst()) {
                return MLst.of(inst.arg(0).lstValue().stream().map(e -> e.apply(lhs)).toList());
            } else if (inst.arg(0).isRec()) {
                return MRec.of(inst.arg(0).recValue().entrySet().stream()
                        .map(e -> e.getKey().apply(lhs).choose(Obj::isNoObj, null, x -> MRel.of(x, e.getValue().apply(x))))
                        .filter(x -> !Objects.isNull(x))
                        .collect(Collectors.toMap(a -> a.<Rel>as().first(), b -> b.<Rel>as().second(), (a, b) -> b, LinkedHashMap<Obj, Obj>::new)));
            } else if (inst.arg(0).isRel()) {
                return MRel.of(inst.arg(0).<Rel>as().first().apply(lhs), inst.arg(0).<Rel>as().second().apply(lhs));
            } else {
                return inst.arg(0).apply(lhs);
            }

        });
        this.define(COUNT_TID, fURI.ANY.any(), INT_TID, MLst.of(), (lhs, inst) -> IteratorUtil.reduce(lhs.iterator(), jnt(0), (a, b) -> MInst.instB(PLUS_TID, MLst.of(jnt(1))).apply(a)));
        this.define(SUM_TID, fURI.ANY.any(), INT_TID, MLst.of(), (lhs, inst) -> IteratorUtil.reduce(lhs.iterator(), jnt(0), (a, b) -> MInst.instB(PLUS_TID, MLst.of(b)).apply(a)));
        this.store();
    }

    protected void store() {
        List.of(Pair.with(NOOBJ_TID, MType.of(NOOBJ_TID)),
                Pair.with(BOOL_TID, MType.of(BOOL_TID)),
                Pair.with(INT_TID, MType.of(INT_TID)),
                Pair.with(REAL_TID, MType.of(REAL_TID)),
                Pair.with(STR_TID, MType.of(STR_TID)),
                Pair.with(URI_TID, MType.of(URI_TID)),
                Pair.with(REL_TID, MType.of(REL_TID)),
                Pair.with(LST_TID, MType.of(LST_TID)),
                Pair.with(REC_TID, MType.of(REC_TID)),
                Pair.with(OBJS_TID, MType.of(OBJS_TID)),
                Pair.with(INST_TID, MType.of(INST_TID)),
                Pair.with(CODE_TID, MType.of(CODE_TID))).forEach(kv -> {
            Router.global().registerRewrite(f(kv.getValue0().name()), kv.getValue0());
        });
        // SYMBOL_TABLE.forEach((k1, v1) -> v1.forEach((k2, v2) -> v2.forEach(i -> Router.global().write(i.tid(), i)))
    }


    protected MInstSet define(final fURI tid, final fURI domain, final fURI range, final Poly args, final BiFunction<Obj, Inst, Obj> f) {
        return this.define(tid, domain, range, args, f, NoObj.single());
    }

    protected MInstSet define(final fURI tid, final fURI domain, final fURI range, final Poly args, final BiFunction<Obj, Inst, Obj> f, final Obj seed) {
        Router.global().registerRewrite(fURI.of(tid.name()), tid);
        this.write(tid, MInst.instC(tid
                .query(fURI.DOM, TypefURI.orNone(domain))
                .query(fURI.RNG, TypefURI.orNone(range)), args, f, seed));

     /*   SYMBOL_TABLE
                .computeIfAbsent(tid, k -> new LinkedHashMap<>())
                .computeIfAbsent(domain, k -> new LinkedHashSet<>())
                .add);*/
        return this;
    }

    public MInstSet(final fURI vid) {
        super(MTRON_TID.extend("#"), MTRON_TID, vid);
        this.load();
    }

    public static InstSet of(final fURI vid) {
        return new MInstSet(vid);
    }

    @Override
    public MInstSet clone(final Object value, final fURI tid, final fURI vid) {
        return this;
    }

    @Override
    public Map<fURI, Map<fURI, Set<Inst>>> value() {
        return SYMBOL_TABLE;
    }
}
