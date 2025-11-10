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

package studio.phaseshift.metatron.lang.core.mach.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.mach.type.Monad;
import studio.phaseshift.metatron.lang.core.m.type.Inst;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.impl.MObj;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.util.Tuple.Triplet;
import static studio.phaseshift.metatron.lang.core.mach.machInstSet.MACH_MONAD_TID;

// monoid, obj, inst, state
public class MMonad extends MObj implements Monad {

    private final GraphittyLogger LOG = Graphitty.log(this);

    private MMonad(final Triplet<Obj, Inst, Rec> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);

    }

    public static Monad of(final Obj obj, final Inst inst) {
        return new MMonad(Triplet.with(obj, inst, rec()), MACH_MONAD_TID, fURI.NULL);
    }

    @Override
    public Triplet<Obj, Inst, Rec> jvm() {
        return (Triplet<Obj, Inst, Rec>) this.jvm;
    }

    @Override
    public Monad tid(final fURI tid) {
        return this.clone(this.jvm, tid, this.vid);
    }

    @Override
    public Monad vid(final fURI vid) {
        return (Monad) super.vid(vid);
    }

    public Obj plus(final Monad other) {
        return this.tid(this.tid.plus(other.tid()));
    }

    @Override
    public Monad clone(final Object jvm, final fURI tid, final fURI vid) {
        MMonad clone = (MMonad) this.clone();
        clone.jvm = jvm;
        clone.tid = tid.clone();
        clone.vid = null == vid ? null : vid.clone();
        return clone;
    }

    @Override
    public boolean equals(final Object other) {
        return Monad.Helpers.monadEquals(this, other);
    }

    @Override
    public int hashCode() {
        return Monad.Helpers.monadHashCode(this);
    }

    @Override
    public String toString() {
        return Monad.Helpers.monadToString(this);
    }
}