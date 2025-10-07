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

package studio.phaseshift.metatron.lang.monoid.mtron;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.monoid.Monad;
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Rec;
import studio.phaseshift.metatron.lang.obj.mtron.MObj;
import studio.phaseshift.metatron.lang.obj.mtron.MRec;
import studio.phaseshift.metatron.lang.obj.mtron.c.cInt;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import static studio.phaseshift.metatron.lang.obj.MInstSet.MTRON_TID;
import static studio.phaseshift.metatron.util.Tuple.Triplet;

// monoid, obj, inst, state
public class MMonad extends MObj implements Monad {

    public static final fURI MMONAD_TID = MTRON_TID.extend("lang/monad");

    private final GraphittyLogger LOG = Graphitty.log(this);

    private MMonad(final Triplet<Obj, Inst, Rec> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);

    }

    @Override
    public Triplet<Obj, Inst, Rec> value() {
        return (Triplet<Obj, Inst, Rec>) this.value;
    }

    @Override
    public Monad tid(final fURI tid) {
        return (Monad) super.tid(tid);
    }

    @Override
    public Monad c(final cInt coeff) {
        return (Monad) super.c(coeff);
    }

    @Override
    public Monad vid(final fURI vid) {
        return (Monad) super.vid(vid);
    }

    public Obj plus(final Monad other) {
        return this.tid(this.tid.plus(other.tid()));
    }

    @Override
    public Monad clone(final Object value, final fURI tid, final fURI vid) {
        return new MMonad((Triplet<Obj, Inst, Rec>) value, tid, vid);
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

    public static Monad of(final Obj obj, final Inst inst) {
        return new MMonad(Triplet.with(obj, inst, MRec.EMPTY_REC), MMONAD_TID, fURI.NULL);
    }
}