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
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Rel;

import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.REL_TID;
import static studio.phaseshift.metatron.util.Tuple.Pair;


public class MRel extends MObj implements Rel {
    public MRel(final Pair<Obj, Obj> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    public MRel(final Pair<Obj, Obj> value) {
        this(value, REL_TID, fURI.NULL);
    }

    @Override
    public Rel clone(final Object jvm, final fURI tid, final fURI vid) {
        return (Rel) super.clone(jvm, tid, vid);
    }

    @Override
    public Pair<Obj, Obj> jvm() {
        return (Pair<Obj, Obj>) this.jvm;
    }

    public static Rel of(final Obj dom, final Obj rng) {
        return new MRel(Pair.with(dom, rng));
    }

    public static Rel of(final Obj dom, final Obj rng, final fURI tid) {
        return new MRel(Pair.with(dom, rng), tid, fURI.NULL);
    }

    public static Rel rel(final Obj dom, final Obj rng) {
        return MRel.of(dom,rng);
    }
}
