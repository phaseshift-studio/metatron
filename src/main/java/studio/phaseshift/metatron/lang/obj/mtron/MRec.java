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
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Rec;

import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.lang.obj.mtron.MInstSet.REC_TID;

public class MRec extends MObj implements Rec {
    public MRec(final Map<Obj, Obj> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    public MRec(final Map<Obj, Obj> value) {
        this(value, REC_TID, fURI.NULL);
    }

    @Override
    public Rec clone(final Object value, final fURI tid, final fURI vid) {
        return new MRec((Map<Obj, Obj>) value, tid, vid);
    }

    @Override
    public Map<Obj, Obj> value() {
        return (Map<Obj, Obj>) this.value;
    }

    public static Rec of(final Map<Obj, Obj> value) {
        return new MRec(value);
    }

    public static Rec of(final Map<Obj, Obj> value, final fURI tid) {
        return new MRec(value, tid, fURI.NULL);
    }

    public static Rec of(final Obj... kv) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i = i + 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return MRec.of(map);
    }

    public static Rec ofUriKeyed(final Object... kv) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i = i + 2) {
            map.put(MUri.of(kv[i].toString()), (Obj)kv[i + 1]);
        }
        return MRec.of(map);
    }

    public static Rec ofUriKeyed(final Map<String,String> value, final fURI tid) {
        final Map<Obj, Obj> map = new LinkedHashMap<>();
        for(final Map.Entry<String,String> kv : value.entrySet()) {
            map.put(MUri.of(kv.getKey()),MStr.of(kv.getValue()));
        }
        return MRec.of(map,tid);
    }
}
