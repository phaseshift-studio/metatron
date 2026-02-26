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

package studio.phaseshift.metatron.isa.mach.type.monad;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Monad;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.List;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MONAD_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BasicMonad extends AbstractMonad implements Monad {

    private static final GraphittyLogger LOG = Graphitty.log(BasicMonad.class);
    public static final fURI MACH_BASIC_MONAD_TID = MACH_MONAD_TID.extend("basic");

    List<Obj> jvm;

    protected BasicMonad(final List<Obj> jvm, final fURI tid, final fURI vid) {
        super(tid,vid);
        this.jvm = jvm;
    }

    @Override
    public Monad clone(final Object jvm, final fURI tid, final fURI vid) {
        return new BasicMonad((List<Obj>) jvm, tid, vid);
    }

    @Override
    public List<Obj> jvm() {
        return this.jvm;
    }

    @Override
    public Monad clone() {
        final BasicMonad clone = (BasicMonad) super.clone();
        return clone;
    }

    @Override
    public <OBJ extends Obj> OBJ self(final Object jvm, final fURI tid, final fURI vid) {
        this.jvm = (List<Obj>) jvm;
        this.tid = tid;
        this.vid = vid;
        return (OBJ) this;
    }

    @Override
    public Monad plus(final Monad objs) {
        return new BasicMonad(List.of(this, objs), this.tid().plus(objs.tid()), this.vid());
    }
    
    /// //////////////////////////////////////////////////////////////////////////////////////

    public static Monad monad(final List<Obj> jvm, final fURI tid, final fURI vid) {
        return new BasicMonad(jvm, tid, vid);
    }

    public static Monad monad(final Obj obj, final Inst inst) {
        return monad(List.of(obj, inst, noobj()), MACH_BASIC_MONAD_TID, null);
    }

    public static Monad monad(final Obj obj) {
        return monad(obj, noobj());
    }
}
