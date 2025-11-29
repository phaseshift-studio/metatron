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

package studio.phaseshift.metatron.lang.db.grph.inst;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.db.grph.type.tp.MGraph;
import studio.phaseshift.metatron.lang.core.m.type.Call;
import studio.phaseshift.metatron.lang.Fluent;
import studio.phaseshift.metatron.lang.core.m.type.Inst;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.inst.mFluent;
import studio.phaseshift.metatron.lang.core.m.inst.mInstSet;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;

public class grphFluent<F extends Fluent<F>> extends mFluent<F> {

    protected grphFluent() {
        this(new ArrayList<>(), mInstSet.CODE_TID, null);
    }

    protected grphFluent(final List<Inst> value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    public F start_(final Obj obj) {
        return this.addInst(instB(mInstSet.START_INST_TID, lst(obj)));
    }

    public F g(final MGraph graph) {
        return this.addInst(instB(grphInstSet.G_TID, lst(graph)));
    }

    public F g(final Call call) {
        return this.addInst(instB(grphInstSet.G_TID, lst(call)));
    }

    public F V() {
        return this.addInst(instB(grphInstSet.V_TID, lst()));
    }

    public F out(final Obj... obj) {
        return this.addInst(instB(grphInstSet.OUT_TID, lst(obj)));
    }

    public F outE(final Obj... obj) {
        return this.addInst(instB(grphInstSet.OUTE_TID, lst(obj)));
    }

    @Override
    public grphFluent<F> clone(Object jvm, fURI tid, fURI vid) {
        return new grphFluent<>(new ArrayList<>(this.jvm()), this.tid, this.vid);
    }

    /// /////////////////////////////////////////////////////////////

    public static class StartLess {

        public static <F extends grphFluent<F>> F g(final MGraph obj) {
            return (F) new grphFluent<F>().start_(obj);
        }

        public static <F extends grphFluent<F>> F out(final Obj obj) {
            return new grphFluent<F>().out(obj);
        }

        public static <F extends grphFluent<F>> F outE(final Obj obj) {
            return new grphFluent<F>().outE(obj);
        }


    }
}
