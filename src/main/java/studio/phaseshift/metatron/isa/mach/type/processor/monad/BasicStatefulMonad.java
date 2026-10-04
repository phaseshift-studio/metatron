/*
 * metatron: a distributed virtual machine and language
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

package studio.phaseshift.metatron.isa.mach.type.processor.monad;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.processor.Monad;
import studio.phaseshift.metatron.util.CommonUtil;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MONAD_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BasicStatefulMonad extends AbstractStatefulMonad {

    public static final fURI MACH_BASIC_MONAD_TID = MACH_MONAD_TID; // .extend("basic");

    Lst jvm;

    public BasicStatefulMonad(final Lst jvm, final fURI tid, final fURI vid) {
        super(tid, vid);
        this.jvm = jvm;
    }

    @Override
    public StatefulMonad clone(final Object jvm, final fURI tid, final fURI vid) {
        return new BasicStatefulMonad((Lst) jvm, tid, vid);
    }

    @Override
    public Monad<Lst> attach(final Code code) {
        this.jvm().lstValue().set(3, code);
        return this;
    }

    @Override
    public Lst jvm() {
        return this.jvm;
    }

    @Override
    public StatefulMonad clone() {
        final BasicStatefulMonad clone = (BasicStatefulMonad) super.clone();
        clone.jvm = (Lst) this.jvm.clone();
        return clone;
    }

    @Override
    public StatefulMonad self(final Object jvm, final fURI tid, final fURI vid) {
        this.jvm = (Lst) jvm;
        this.tid = tid;
        this.vid = vid;
        return this;
    }

    /// //////////////////////////////////////////////////////////////////////////////////////

    public static StatefulMonad statefulMonad(final Obj obj, final Inst inst, final Rec state, final Call code) {
        return new BasicStatefulMonad(lst(CommonUtil.arrayList(obj, inst, state, code)), MACH_BASIC_MONAD_TID, null);
    }

    public static StatefulMonad statefulMonad(final Obj obj, final fURI instVID, final Rec state) {
        return new BasicStatefulMonad(lst(CommonUtil.arrayList(obj, Inst.Helper.idInst(instVID), state, noobj())), MACH_BASIC_MONAD_TID, null);
    }

    public static StatefulMonad statefulMonad(final Obj obj, final fURI instVID) {
        return new BasicStatefulMonad(lst(CommonUtil.arrayList(obj, Inst.Helper.idInst(instVID), rec0(), noobj())), MACH_BASIC_MONAD_TID, null);
    }

    public static StatefulMonad statefulMonad(final Obj obj) {
        return statefulMonad(obj, noobj(), rec0(), noobj());
    }
}
