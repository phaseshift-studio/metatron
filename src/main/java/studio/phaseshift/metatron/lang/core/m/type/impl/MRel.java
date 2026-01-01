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

package studio.phaseshift.metatron.lang.core.m.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rel;

import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.REL_TID;
import static studio.phaseshift.metatron.util.Tuple.Pair;


public class MRel extends MObj implements Rel {
    public MRel(final Pair<Obj, Obj> value, final fURI tid, final fURI vid) {
        super(value, null == tid ? REL_TID : tid, vid);
    }

    public MRel(final Pair<Obj, Obj> value) {
        this(value, REL_TID, fURI.fnull);
        if (value.get0().isNoObj() || value.get1().isNoObj())
            this.tid = this.tid().c("0");
    }

    public static Rel rel(final Obj dom, final Obj rng) {
        return rel(dom, rng, REL_TID, fURI.fnull);
    }

    public static Rel rel(final Obj dom, final Obj rng, final fURI tid, final fURI vid) {
        return new MRel(Pair.with(dom, rng), tid, vid);
    }

    public static Rel rel(final Pair<Obj, Obj> pair, final fURI tid, final fURI vid) {
        return new MRel(pair, tid, vid);
    }

    public static Rel rel(final Pair<Obj, Obj> pair) {
        return new MRel(pair);
    }

    @Override
    public Rel clone(final Object jvm, final fURI tid, final fURI vid) {
        final MRel temp = super.clone(jvm, tid, vid);
        if (temp.jvm().get0().isNoObj() || temp.jvm().get1().isNoObj())
            temp.tid = temp.tid().c("0");
        return temp;
    }

    @Override
    public Pair<Obj, Obj> jvm() {
        return (Pair<Obj, Obj>) this.jvm;
    }


    public Stream<Rel> indexedStream() {
        return Stream.of(this);
    }
}
