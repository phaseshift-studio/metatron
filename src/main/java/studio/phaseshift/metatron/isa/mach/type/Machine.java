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

package studio.phaseshift.metatron.isa.mach.type;

import studio.phaseshift.metatron.algebra.Ring;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.ALL_STAR;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MONAD_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Machine extends Call, Ring<Call> {

    Type MACH_MACHINE_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(MACH_MACHINE_TID)
            .isaPredicate(rec(
                    uri(HALTED), T(ALL_STAR),
                    uri(RUN), T(MACH_MONAD_TID.maybeSome()),
                    uri(BARRIER), LST_TYPE))
            .create();

    @Override
    Machine clone(final Object jvm, final fURI tid, final fURI vid);

    // code, running, barriers, halted
    @Override
    Map<Obj, Obj> jvm();

    Fail interrupt();

    NoObj pause();

    Obj run();

    @Override
    default boolean isResolved(final boolean nested) {
        return this.code().isResolved(nested);
    }

    default Obj halted() {
        return this.jvm().get(uri(HALTED));
    }

    default Lst barriers() {
        return this.jvm().get(uri(BARRIER)).as();
    }

    default Obj running() {
        return this.jvm().get(uri(RUN));
    }

    default Code code() {
        return this.jvm().get(uri(CODE)).as();
    }

    default Machine code(final Code code) {
        final Map<Obj, Obj> map = new HashMap<>(this.jvm());
        map.put(uri(CODE), code);
        return this.clone(map, this.tid(), this.vid());
    }

    Machine onHalt(final Consumer<Obj> halted);

    Consumer<Obj> onHalt();

    @Override
    default Machine plus(final Call other) {
        // two machines executing in parallel
        final boolean otherMachine = other instanceof Machine;
        /*return this.clone(Tuple.Quartet.with(this.jvm().get(uri(CODE)).plus(otherMachine ? other.<Machine>as().jvm().get(uri(CODE)) : other.jvm().get(uri(CODE))),
                        otherMachine ? this.jvm().get(uri(RUNNING)).append(other.<Machine>as().jvm().get(uri(RUNNING))) : this.jvm().get(uri(RUNNING)),
                        otherMachine ? this.jvm().get(uri(BARRIER)).append(other.<Machine>as().jvm().get(uri(BARRIER))) : this.jvm().get(uri(BARRIER)),
                        otherMachine ? this.jvm().get(uri(HALTED)).append(other.<Machine>as().jvm().get(uri(HALTED))) : this.jvm().get(uri(HALTED))),
                this.tid().plus(other.tid()), this.vid());*/
        return null;
    }

    @Override
    default Machine mult(final Call other) {
       /* return this.clone(Tuple.Quartet.with(this.value().get0().mult(other.value().get0()),
                this.value().get1().append(other.value().get1()),
                this.value().get2().append(other.value().get2()),
                this.value().get3().append(other.value().get3())), this.tid().plus(other.tid()), this.vid());*/
        return this;
    }

    @Override
    Machine resolve(final Obj lhs);

    @Override
    default Type dom() {
        return this.code().dom();
    }

    @Override
    default Type rng() {
        return this.code().rng();
    }

}