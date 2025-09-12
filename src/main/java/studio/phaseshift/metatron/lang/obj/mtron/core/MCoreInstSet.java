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

package studio.phaseshift.metatron.lang.obj.mtron.core;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.monoid.MMonoid;
import studio.phaseshift.metatron.lang.obj.base.*;
import studio.phaseshift.metatron.lang.obj.mtron.*;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;

import static studio.phaseshift.metatron.lang.obj.BObj.NOOBJ_URI;

public class MCoreInstSet extends MObj implements InstSet {

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

    // inst_tid -> <inst_tid_dom -> set<inst>>
    private static final Map<fURI, Map<fURI, Set<Inst>>> SYMBOL_TABLE = new LinkedHashMap<>() {{
        put(ID_TID, Map.of(fURI.of("#"), Set.of(MInst.instC(ID_TID, MLst.of(), (lhs, inst) -> lhs))));
        put(START_TID,
                Map.of(
                        fURI.of("#"), // should be '' (noobj)
                        Set.of(MInst.instC(START_TID, MLst.of(MInst.instA(ID_TID)), (lhs, inst) -> inst.arg(0)))));
        put(PLUS_TID,
                Map.of(
                        fURI.of("#"),
                        Set.of(MInst.instC(PLUS_TID, MLst.of(MInst.instA(ID_TID)),
                                (lhs, inst) -> MInt.of(lhs.<Int>as().value() + inst.arg(0).<Int>as().value())))));

        put(MULT_TID,
                Map.of(
                        fURI.of("#"),
                        Set.of(MInst.instC(MULT_TID, MLst.of(MInst.instA(ID_TID)),
                                (lhs, inst) -> MInt.of(lhs.<Int>as().value() * inst.arg(0).<Int>as().value())))));

        put(DOM_TID,
                Map.of(
                        fURI.of("#"),
                        Set.of(MInst.instC(DOM_TID, MLst.of(),
                                (lhs, inst) -> {
                                    if (lhs.isRel())
                                        return lhs.relValue().getValue0();
                                    else if (lhs.isRec())
                                        return MObjs.of(lhs.recValue().keySet());
                                    else
                                        return lhs;
                                }))));

        put(RNG_TID,
                Map.of(
                        fURI.of("#"),
                        Set.of(MInst.instC(RNG_TID, MLst.of(),
                                (lhs, inst) -> {
                                    if (lhs.isRel())
                                        return lhs.relValue().getValue1();
                                    else if (lhs.isRec())
                                        return MObjs.of(lhs.recValue().values());
                                    else
                                        return lhs;
                                }))));

        put(MAP_TID,
                Map.of(
                        fURI.of("#"),
                        Set.of(MInst.instC(MAP_TID, MLst.of(MInst.instA(ID_TID)),
                                (lhs, inst) -> inst.arg(0)))));
        put(TO_TID,
                Map.of(
                        fURI.of("#"),
                        Set.of(MInst.instC(TO_TID, MLst.of(MInst.instA(ID_TID)),
                                (lhs, inst) -> Router.global().write(inst.arg(0).uriValue(), lhs)))));
        put(FROM_TID,
                Map.of(
                        fURI.of("#"),
                        Set.of(MInst.instC(FROM_TID, MLst.of(MInst.instA(ID_TID)),
                                (lhs, inst) -> Router.global().read(inst.arg(0).uriValue())))));
        put(REF_TID,
                Map.of(
                        fURI.of("#"),
                        Set.of(MInst.instC(REF_TID, MLst.of(MInst.instA(ID_TID)),
                                (lhs, inst) -> Router.global().write(lhs.uriValue(), inst.arg(0))))));

        put(BLOCK_TID,
                Map.of(
                        fURI.of("#"),
                        Set.of(MInst.instC(BLOCK_TID, MLst.of(MInst.instA(ID_TID)),
                                (lhs, inst) -> inst.arg(0)))));


        put(TID_TID,
                Map.of(
                        fURI.of("#"),
                        Set.of(MInst.instC(TID_TID, MLst.of(),
                                (lhs, inst) -> lhs.tid().toUri()))));


        put(VID_TID,
                Map.of(
                        fURI.of("#"),
                        Set.of(MInst.instC(VID_TID, MLst.of(),
                                (lhs, inst) -> Optional.ofNullable(lhs.vid()).orElse(NOOBJ_URI).toUri()))));


        put(TYPE_TID,
                Map.of(
                        fURI.of("#"),
                        Set.of(MInst.instC(TYPE_TID, MLst.of(),
                                (lhs, inst) -> {
                            final Obj type = Router.global().read(inst.arg(0).tid());
                            return type.isNoObj() ? new MObj(null,lhs.tid(),lhs.tid()) : type;
                        }))));

        put(WITHIN_TID,
                Map.of(
                        fURI.of("#"),
                        Set.of(MInst.instC(WITHIN_TID, MLst.of(MInst.instA(ID_TID)),
                                (lhs, inst) -> {
                                    final List<Obj> map = new ArrayList<>();
                                    final MMonoid monoid = new MMonoid(inst.arg(0),MObjs.of(lhs.<Poly>as().elements()));
                                    IteratorUtil.iterate(IteratorUtil.consume(monoid.iterator(), map::add));
                                    return MLst.of(map);
                                }))));

        put(MERGE_TID,
                Map.of(
                        fURI.of("#"),
                        Set.of(MInst.instC(MERGE_TID, MLst.of(),
                                (lhs, inst) -> MObjs.of(lhs.<Poly>as().elements())))));

        put(SPLIT_TID,
                Map.of(
                        fURI.of("#"),
                        Set.of(MInst.instC(SPLIT_TID, MLst.of(MInst.instA(ID_TID)),
                                (lhs, inst) -> {
                                    if (inst.arg(0).isLst()) {
                                        final List<Obj> split_lst = new ArrayList<>();
                                        for (final Obj a : inst.arg(0).lstValue()) {
                                            split_lst.add(a.apply(lhs));
                                        }
                                        return MLst.of(split_lst);
                                    } else if (inst.arg(0).isRec()) {
                                        final Map<Obj,Obj> split_rec = new LinkedHashMap<>();
                                        for(final Map.Entry<Obj,Obj> entry : inst.arg(0).recValue().entrySet()) {
                                            final Obj key = entry.getKey().apply(lhs);
                                            if(!key.isNoObj())
                                                split_rec.put(key,entry.getValue().apply(lhs));
                                        }
                                        return MRec.of(split_rec);
                                    } else {
                                        return inst.arg(0);
                                    }
                                }))));
    }};


    public MCoreInstSet() {
        super(SYMBOL_TABLE, TID, fURI.NONE);
    }

    @Override
    public InstSet clone(final Object value, final fURI tid, final fURI vid) {
        return this;
    }

    @Override
    public Map<fURI, Map<fURI, Set<Inst>>> value() {
        return (Map<fURI, Map<fURI, Set<Inst>>>) this.value;
    }
}
