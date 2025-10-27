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

import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Rec;
import studio.phaseshift.metatron.lang.obj.Rel;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.lang.obj.mtron.MUri.uri;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.REC_TID;

public class MRec extends MObj implements Rec {

    public static Rec EMPTY_REC = MRec.fromUriKeyed(Map.of());

    public MRec(final Map<Obj, Obj> value, final fURI tid, final fURI vid) {
        super(cleanMap(value), tid, vid);
    }

    public MRec(final Map<Obj, Obj> value) {
        this(value, REC_TID, fURI.NULL);
    }

    public static Rec rec(final Obj key, final Obj value, final Obj... kvs) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(key, value);
        for (int i = 0; i < kvs.length; i = i + 2) {
            map.put(kvs[i], kvs[i + 1]);
        }
        return new MRec(map, REC_TID, fURI.NULL);
    }

    public static Rec rec(final Map<Obj, Obj> map) {
        return new MRec(cleanMap(map), REC_TID, fURI.NULL);
    }

    public static Rec rec() {
        return new MRec(new LinkedHashMap<>(), REC_TID, fURI.NULL);
    }

    public static Rec fromUriKeyed(final Object key, final Obj value, final Object... kvs) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(key.toString()), value);
        for (int i = 0; i < kvs.length; i = i + 2) {
            map.put(uri(kvs[i].toString()), (Obj) kvs[i + 1]);
        }
        return new MRec(map, REC_TID, fURI.NULL);
    }

    public static Rec fromUriKeyed(final Map<fURI, Obj> jvm) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        for (final Map.Entry<fURI, Obj> kv : jvm.entrySet()) {
            map.put(uri(kv.getKey()), kv.getValue());
        }
        return new MRec(map, REC_TID, fURI.NULL);
    }

    private static Map<Obj, Obj> cleanMap(final Map<Obj, Obj> jvm) {
        if (jvm.containsKey(NoObj.single()))
            jvm.remove(NoObj.single());
        if (jvm.containsValue(NoObj.single()))
            jvm.entrySet().stream().filter(kv -> kv.getValue().isNoObj()).map(Map.Entry::getKey).toList().forEach(jvm::remove);
        return jvm;
    }

    public Rec clone() {
        return (Rec) super.clone();
    }

    @Override
    public Rec plus(final Rec objs) {
        final Map<Obj, Obj> newMap = new LinkedHashMap<>(this.recValue());
        objs.stream().flatMap(Obj::<Obj>elementStream).map(Obj::<Rel>as).forEach(o -> newMap.compute(o.first(), (k, v) -> null == v ? o.second() : v.isPlusMonoid() ? (Obj) v.<PlusMonoid.O>as().plus(o.second().<PlusMonoid.O>as()) : v.append(o.second())));
        return this.jvm(newMap);
    }

    @Override
    public Rec zero() {
        return EMPTY_REC;
    }

    @Override
    public Rec clone(final Object jvm, final fURI tid, final fURI vid) {
        return (Rec) super.clone(jvm, tid, vid);
    }

    public Rec put(final Obj key, final Obj value) {
        final fURI k = key.uriValue();
        if (k.segments().isEmpty())
            return this;
        final Map<Obj, Obj> map = new LinkedHashMap<>(this.recValue());
        if (k.segments().size() == 1)
            map.put(key, value);
        else {
            final Obj v = map.get(uri(k.segments().get(0)));
            map.put(uri(k.segments().get(0)), (v.isRec() ? v.<Rec>as() : rec()).put(k.pretract().toUri(), value));
        }
        return this.jvm(map);
    }

    @Override
    public Map<Obj, Obj> jvm() {
        return (Map<Obj, Obj>) this.jvm;
    }

    @Override
    public Rec jvm(final Object value) {
        return super.jvm(cleanMap((Map<Obj, Obj>) value));
    }

    /*@Override
    public boolean matches(final Obj rhs) {
        if (this.isNoObj() && rhs.isNoObj())
            return true;
        if (rhs.isRec()) {
            for (final Map.Entry<Obj, Obj> entry : rhs.recValue().entrySet()) {
                final Obj value = this.recValue().getOrDefault(entry.getKey(), NoObj.single());
                if (entry.getValue().isCall() && entry.getValue().apply(value).isNoObj())
                    return false;
                if (!value.matches(entry.getValue()))
                    return false;
            }
            return true;
        }
        return super.matches(rhs);
    }*/
}
