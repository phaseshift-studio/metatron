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
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Rec;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.lang.obj.mtron.MUri.uri;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.REC_TID;

public class MRec extends MObj implements Rec {
    public MRec(final Map<Obj, Obj> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    public MRec(final Map<Obj, Obj> value) {
        this(value, REC_TID, fURI.NULL);
    }

    public static Rec rec(final Obj key, final Obj value, final Obj... kvs) {
        return MRec.of(key, value, kvs);
    }

    public static Rec rec(final Map<Obj,Obj> map) {
        return MRec.of(map);
    }

    public static Rec rec() {
        return MRec.of(new LinkedHashMap<>());
    }

    @Override
    public Rec clone(final Object value, final fURI tid, final fURI vid) {
        return super.clone(value, tid, vid, (a, b, c) -> new MRec((Map<Obj,Obj>) a, b, c));
    }

    public Rec put(final Obj key, final Obj value) {
        final fURI k = key.uriValue();
        if (k.segments().isEmpty())
            return this;
        final LinkedHashMap<Obj, Obj> map = new LinkedHashMap<>(this.recValue());
        if (k.segments().size() == 1)
            map.put(key, value);
        else {
            final Obj v = map.get(MUri.of(k.segments().get(0)));
            map.put(uri(k.segments().get(0)), (v.isRec() ? v.<Rec>as() : rec()).put(k.pretract().toUri(), value));
        }
        return this.value(map);
    }

    @Override
    public Map<Obj, Obj> value() {
        return (Map<Obj, Obj>) this.value;
    }

    public static Rec of(final Map<Obj, Obj> value) {
        return new MRec(value);
    }

    public static Rec of() {
        return new MRec(new LinkedHashMap<>());
    }

    public static Rec of(final Map<Obj, Obj> value, final fURI tid) {
        return new MRec(value, tid, fURI.NULL);
    }

    public static Rec of(final Obj key, final Obj value, final Obj... kv) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        map.put(key, value);
        for (int i = 0; i < kv.length; i = i + 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return MRec.of(map);
    }

    public static Rec ofUriKeyed(final Object... kv) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i = i + 2) {
            map.put(MUri.of(kv[i].toString()), (Obj) kv[i + 1]);
        }
        return MRec.of(map);
    }

    public static Rec ofUriKeyed(final Map<String, String> value, final fURI tid) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        for (final Map.Entry<String, String> kv : value.entrySet()) {
            map.put(MUri.of(kv.getKey()), MStr.of(kv.getValue()));
        }
        return MRec.of(map, tid);
    }

    @Override
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
    }
}
