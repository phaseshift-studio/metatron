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

package studio.phaseshift.metatron.isa.m.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.ObjFactory;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class MRec extends MObj implements Rec {

    public MRec(final Map<Obj, Obj> value, final fURI tid, final fURI vid) {
        super(Rec.Helper.cleanMap(value), null == tid ? REC_TID : tid, vid);
    }

    protected MRec() {
        super();
    }

    public static Rec rec(final Obj key, final Obj value, final Obj... kvs) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(key, value);
        for (int i = 0; i < kvs.length; i = i + 2) {
            if (!kvs[i].isNoObj() && !kvs[i].isNoObj())
                map.put(kvs[i], kvs[i + 1]);
        }
        return rec(map, REC_TID, null);
    }

    public static Rec rec(final String key, final Obj value, final Object... kvs) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(key), value);
        for (int i = 0; i < kvs.length; i = i + 2) {
            final Obj keyO = kvs[i] instanceof Obj ? (Obj) kvs[i] : (kvs[i] instanceof String || kvs[i] instanceof fURI ? uri(kvs[i].toString()) : MObjFactory.of().toObj(kvs[i]));
            final Obj valueO = kvs[i + 1] instanceof Obj ? (Obj) kvs[i + 1] : (kvs[i + 1] instanceof String || kvs[i + 1] instanceof fURI ? uri(kvs[i + 1].toString()) : MObjFactory.of().toObj(kvs[i + 1]));
            map.put(keyO, valueO);
        }
        return rec(map, REC_TID, null);
    }

    public static Rec rec0() {
        return Rec.EMPTY_REC;
    }
    
    public static Rec noobjRec(){
        return Rec.NOOBJ_REC;
    }

    public static Rec rec(final Map<Obj, Obj> map, final fURI tid, final fURI vid) {
        return null == tid ? new MRec(Rec.Helper.cleanMap(map), REC_TID, vid) : MObj.of(Rec.Helper.cleanMap(map), tid, vid, Rec.class);
    }

    public static Rec rec(final Map<Obj, Obj> map) {
        return rec(map, null, null);
    }

    public static Rec rec() {
        return rec(new LinkedHashMap<>(), null, null);
    }

    public static Rec rec(final Stream<Rel> stream) {
        return stream.collect(new CommonUtil.RecCollector(REC_TID, null));
    }

    public static <K, V> Rec rec(final Map<K, V> map, final ObjFactory factory) {
        return rec(map.entrySet().stream()
                .filter(kv -> kv.getKey() != null && null != kv.getValue())
                .filter(kv -> !(kv.getKey() instanceof Obj) || !((Obj) kv.getKey()).isNoObj())
                .filter(kv -> !(kv.getValue() instanceof Obj) || !((Obj) kv.getValue()).isNoObj())
                .map(kv -> rel(kv.getKey() instanceof String && !((String) kv.getKey()).contains(" ") ? uri((String) kv.getKey()) : factory.toObj(kv.getKey()), factory.toObj(kv.getValue()))));
    }

    public Rec clone() {
        final MRec clone = (MRec) super.clone();
        clone.jvm = new LinkedHashMap<>((Map<Obj,Obj>)this.jvm);
        return clone;
    }

    @Override
    public Rec clone(final Object jvm, final fURI tid, final fURI vid) {
        return super.clone(Rec.Helper.cleanMap((Map<Obj, Obj>) jvm), tid, vid);
    }

    @Override
    public Rec self(final Object jvm, final fURI tid, final fURI vid) {
        return super.self(Rec.Helper.cleanMap((Map<Obj, Obj>) jvm), tid, vid);
    }

    @Override
    public Map<Obj, Obj> jvm() {
        return (Map<Obj, Obj>) this.jvm;
    }

    @Override
    public Rec jvm(final Object jvm) {
        return super.jvm(Rec.Helper.cleanMap((Map<Obj, Obj>) jvm));
    }
}
