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

package studio.phaseshift.metatron.lang.core.m.type.impl;

import org.apache.commons.collections.CollectionUtils;
import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.obj.NoObj;
import studio.phaseshift.metatron.lang.core.m.type.*;


import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.REC_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

public class MRec extends MObj implements Rec {

    public MRec(final Map<Obj, Obj> value, final fURI tid, final fURI vid) {
        super(cleanMap(value), tid, vid);
    }

   /* public MRec(final Map<Obj, Obj> value) {
        this(value, REC_TID, fURI.NULL);
    }*/

    public static Rec rec(final Obj key, final Obj value, final Obj... kvs) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(key, value);
        for (int i = 0; i < kvs.length; i = i + 2) {
            if (!kvs[i].isNoObj() && !kvs[i].isNoObj())
                map.put(kvs[i], kvs[i + 1]);
        }
        return new MRec(map, REC_TID, fURI.NULL);
    }

    public static Rec rec(final String key, final Obj value, final Object... kvs) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(uri(key), value);
        for (int i = 0; i < kvs.length; i = i + 2) {
            final Obj keyO = kvs[i] instanceof Obj ? (Obj) kvs[i] : (kvs[i] instanceof String || kvs[i] instanceof fURI ? uri(kvs[i].toString()) : MObjFactory.of().create(kvs[i]));
            final Obj valueO = kvs[i + 1] instanceof Obj ? (Obj) kvs[i + 1] : (kvs[i + 1] instanceof String || kvs[i + 1] instanceof fURI ? uri(kvs[i + 1].toString()) : MObjFactory.of().create(kvs[i + 1]));
            map.put(keyO, valueO);
        }
        return new MRec(map, REC_TID, fURI.NULL);
    }

    public static Rec rec(final Map<Obj, Obj> map) {
        return new MRec(map, REC_TID, fURI.NULL);
    }

    public static Rec rec() {
        return new MRec(new LinkedHashMap<>(), REC_TID, fURI.NULL);
    }

    public static Rec rec(final Stream<Rel> stream) {
        return new MRec(stream.collect(Collectors.toMap(Rel::first, Rel::second, (a, b) -> a.append(b), LinkedHashMap::new)), REC_TID, fURI.NULL);
    }

    public static <K, V> Rec rec(final Map<K, V> map, final ObjFactory factory) {
        return rec(map.entrySet().stream().map(kv -> rel(kv.getKey() instanceof String && !((String) kv.getKey()).contains(" ") ? uri((String) kv.getKey()) : factory.create(kv.getKey()), factory.create(kv.getValue()))));
    }

    private static Map<Obj, Obj> cleanMap(final Map<Obj, Obj> jvm) {
        try {
            jvm.remove(NoObj.noobj());
            if (jvm.containsValue(NoObj.noobj()))
                jvm.entrySet().stream().filter(kv -> kv.getValue().isNoObj()).map(Map.Entry::getKey).toList().forEach(jvm::remove);
        } catch (final UnsupportedOperationException e) {
            // do nothing
        }
        return jvm;
    }

    public Rec clone() {
        final MRec clone = (MRec) super.clone();
        clone.jvm = new LinkedHashMap<>(this.jvm());
        return clone;
    }

    @Override
    public Rec clone(final Object jvm, final fURI tid, final fURI vid) {
        return super.clone(jvm, tid, vid);
    }

    @Override
    public Rec self(final Object jvm, final fURI tid, final fURI vid) {
        return super.self(jvm, tid, vid);
    }
    
    @Override
    public Map<Obj, Obj> jvm() {
        return (Map<Obj, Obj>) this.jvm;
    }

    @Override
    public Rec jvm(final Object jvm) {
        return super.jvm(cleanMap((Map<Obj, Obj>) jvm));
    }
}
