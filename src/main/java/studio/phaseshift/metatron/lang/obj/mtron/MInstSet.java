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

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.monoid.mtron.MMonoid;
import studio.phaseshift.metatron.lang.obj.*;
import studio.phaseshift.metatron.lang.obj.base.furi.TypefURI;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class MInstSet extends MObj implements InstSet {

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
    public static final fURI NOOBJ_REF_TID = fURI.of("/mtron/noobj");
    public static final fURI NOOBJ_TID = fURI.of("");

    public static final fURI TID = fURI.of("/mtron/core");
    public static final fURI ID_TID = fURI.of("id");
    public static final fURI START_TID = fURI.of("start");
    public static final fURI MULT_TID = fURI.of("mult");
    public static final fURI PLUS_TID = fURI.of("plus");
    public static final fURI MAP_TID = fURI.of("map");
    public static final fURI TO_TID = fURI.of("to");
    public static final fURI FROM_TID = fURI.of("from");
    public static final fURI REF_TID = fURI.of("ref");
    public static final fURI SPLIT_TID = fURI.of("split");
    public static final fURI MERGE_TID = fURI.of("merge");
    public static final fURI WITHIN_TID = fURI.of("within");
    public static final fURI BLOCK_TID = fURI.of("block");
    public static final fURI RNG_TID = fURI.of("rng");
    public static final fURI DOM_TID = fURI.of("dom");
    public static final fURI TID_TID = fURI.of("tid");
    public static final fURI VID_TID = fURI.of("vid");
    public static final fURI TYPE_TID = fURI.of("type");
    public static final fURI AT_TID = fURI.of("at");
    public static final fURI IS_TID = fURI.of("is");
    public static final fURI EQ_TID = fURI.of("eq");
    public static final fURI NEQ_TID = fURI.of("neq");
    public static final fURI GT_TID = fURI.of("gt");
    public static final fURI LT_TID = fURI.of("lt");
    public static final fURI GTE_TID = fURI.of("gte");
    public static final fURI LTE_TID = fURI.of("lte");

    // inst_tid -> <inst_tid_dom -> set<inst>>
    private static final Map<fURI, Map<fURI, Set<Inst>>> SYMBOL_TABLE = new LinkedHashMap<>();

    public void load() {
        this.define(MERGE_TID, fURI.ONE, fURI.MANY, MLst.of(), (lhs, inst) -> lhs.isPoly() ? MObjs.of(lhs.<Poly>as().elements()) : lhs);
        this.define(AT_TID, fURI.ONE, fURI.MANY, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> lhs.vid(inst.arg(0).uriValue()));
        this.define(TYPE_TID, fURI.ONE, URI_TID, MLst.of(), (lhs, inst) -> Router.global().read(inst.arg(0).tid()).orElse(new MObj(null, lhs.tid(), lhs.tid())));
        this.define(TID_TID, fURI.ONE, URI_TID, MLst.of(), (lhs, inst) -> lhs.tid().toUri());
        this.define(VID_TID, fURI.ONE, URI_TID, MLst.of(), (lhs, inst) -> lhs.vid().toUri());
        this.define(MAP_TID, fURI.ONE, fURI.ONE, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> inst.arg(0));
        this.define(BLOCK_TID, fURI.ONE, fURI.ONE, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> inst.arg(0));
        this.define(DOM_TID, REC_TID, URI_TID, MLst.of(), (lhs, inst) -> MObjs.of(lhs.recValue().keySet()));
        this.define(DOM_TID, REL_TID,URI_TID,  MLst.of(), (lhs, inst) -> MObjs.of(lhs.relValue().getValue0()));
        this.define(DOM_TID, fURI.ONE,URI_TID,  MLst.of(), (lhs, inst) -> lhs);
        this.define(RNG_TID, REC_TID, URI_TID, MLst.of(), (lhs, inst) -> MObjs.of(lhs.recValue().values()));
        this.define(RNG_TID, REL_TID,URI_TID,  MLst.of(), (lhs, inst) -> MObjs.of(lhs.relValue().getValue1()));
        this.define(RNG_TID, fURI.ONE, URI_TID, MLst.of(), (lhs, inst) -> lhs);
        this.define(TO_TID, fURI.ONE, fURI.ONE, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> Router.global().write(inst.arg(0).uriValue(), lhs));
        this.define(FROM_TID, fURI.ONE, fURI.MANY, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> Router.global().read(inst.arg(0).uriValue()));
        this.define(REF_TID, fURI.ONE, fURI.ONE, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> Router.global().write(lhs.uriValue(), inst.arg(0)));
        this.define(ID_TID, fURI.ONE, fURI.ONE, MLst.of(), (lhs, inst) -> lhs);
        this.define(START_TID, fURI.of(""), fURI.MANY, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> inst.arg(0));
        this.define(PLUS_TID, INT_TID, INT_TID, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> lhs.value(lhs.intValue() + inst.arg(0).intValue()));
        this.define(PLUS_TID, REAL_TID, REAL_TID, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> lhs.value(lhs.realValue() + inst.arg(0).realValue()));
        this.define(PLUS_TID, URI_TID,URI_TID, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> lhs.value(lhs.uriValue().extend(inst.arg(0).uriValue())));
        this.define(MULT_TID, INT_TID, INT_TID, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> lhs.value(lhs.intValue() * inst.arg(0).intValue()));
        this.define(MULT_TID, REAL_TID, REAL_TID, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> lhs.value(lhs.realValue() * inst.arg(0).realValue()));
        this.define(MULT_TID, URI_TID,  URI_TID, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> lhs.value(lhs.uriValue().retractPattern().extend(inst.arg(0).uriValue())));
        this.define(IS_TID, fURI.ONE, fURI.ONE, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> inst.arg(0).boolValue() ? lhs : NoObj.single());
        this.define(EQ_TID, fURI.ONE, BOOL_TID, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> MBool.of(lhs.equals(inst.arg(0))));
        this.define(NEQ_TID, fURI.ONE, BOOL_TID, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> MBool.of(!lhs.equals(inst.arg(0))));
        this.define(GT_TID, INT_TID, BOOL_TID,MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> MBool.of(lhs.intValue() > inst.arg(0).intValue()));
        this.define(GT_TID, REAL_TID,BOOL_TID, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> MBool.of(lhs.realValue() > inst.arg(0).realValue()));
        this.define(GT_TID, STR_TID, BOOL_TID,MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> MBool.of(lhs.strValue().compareTo(inst.arg(0).strValue()) > 0));
        this.define(WITHIN_TID, fURI.ONE, fURI.ONE, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> MLst.of(IteratorUtil.asList( MMonoid.of(MObjs.of(lhs.<Poly>as().elements()),inst.arg(0).as()).iterator())));
        this.define(SPLIT_TID, LST_TID, LST_TID, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> MLst.of(inst.arg(0).lstValue().stream().map(e -> e.apply(lhs)).toList()));
        this.define(SPLIT_TID, REC_TID, REC_TID, MLst.of(MInst.instA(ID_TID)), (lhs, inst) ->  MRec.of( inst.arg(0).recValue().entrySet().stream().map(kv -> List.of(kv.getKey(),kv.getValue().apply(lhs))).collect(Collectors.toMap(kv -> kv.get(0),kv -> kv.get(1), (a,b) -> b))));
        this.define(SPLIT_TID, fURI.ONE, fURI.ONE, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> inst.arg(0));
        this.store();
    }

    protected void store() {
        Router.global().write(NOOBJ_TID, MType.of(NOOBJ_TID));
        Router.global().write(BOOL_TID, MType.of(BOOL_TID));
        Router.global().write(INT_TID, MType.of(INT_TID));
        Router.global().write(REAL_TID, MType.of(REAL_TID));
        Router.global().write(STR_TID, MType.of(STR_TID));
        Router.global().write(URI_TID, MType.of(URI_TID));
        Router.global().write(REL_TID, MType.of(REL_TID));
        Router.global().write(LST_TID, MType.of(LST_TID));
        Router.global().write(REC_TID, MType.of(REC_TID));
        Router.global().write(OBJS_TID, MType.of(OBJS_TID));
        SYMBOL_TABLE.forEach((k1, v1) -> v1.forEach((k2, v2) -> v2.forEach(i -> Router.global().write(i.tid(), i))));
    }


    protected MInstSet define(final fURI tid, final fURI domain, final fURI range, final Poly args, final BiFunction<Obj, Inst, Obj> f) {
        SYMBOL_TABLE
                .computeIfAbsent(tid, k -> new LinkedHashMap<>())
                .computeIfAbsent(domain, k -> new LinkedHashSet<>())
                .add(MInst.instC(tid
                        .query(fURI.DOM,TypefURI.orNone(domain))
                        .query(fURI.RNG,TypefURI.orNone(range)), args, f));
        return this;
    }

    public MInstSet() {
        super(SYMBOL_TABLE, TID, fURI.NONE);
        this.load();
    }

    public static InstSet of() {
        return new MInstSet();
    }

    @Override
    public InstSet clone(final Object value, final fURI tid, final fURI vid) {
        return this;
    }

    @Override
    public Map<fURI, Map<fURI, Set<Inst>>> value() {
        return SYMBOL_TABLE;
    }
}
