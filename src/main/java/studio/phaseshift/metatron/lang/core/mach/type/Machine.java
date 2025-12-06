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

package studio.phaseshift.metatron.lang.core.mach.type;

import studio.phaseshift.metatron.algebra.Ring;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.*;
import studio.phaseshift.metatron.util.Tuple;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Machine extends Call, Ring<Call> {

    @Override
    Machine clone(final Object jvm, final fURI tid, final fURI vid);

    // code, running, barriers, halted
    @Override
    Tuple.Quartet<Code, Obj, Lst, Obj> jvm();

    default Obj halted() {
        return this.jvm().get3();
    }

    default Lst barriers() {
        return this.jvm().get2();
    }

    default Obj running() {
        return this.jvm().get1();
    }

    default Code code() {
        return this.jvm().get0();
    }

    default Machine code(final Code code) {
        return this.clone(Tuple.Quartet.with(code, this.running(), this.barriers(), this.halted()), this.tid(), this.vid());
    }

    @Override
    default Machine plus(final Call other) {
        // two machines executing in parallel
        final boolean otherMachine = other instanceof Machine;
        return this.clone(Tuple.Quartet.with(this.jvm().get0().plus(otherMachine ? other.<Machine>as().jvm().get0() : other.jvm()),
                        otherMachine ? this.jvm().get1().append(other.<Machine>as().jvm().get1()) : this.jvm().get1(),
                        otherMachine ? this.jvm().get2().append(other.<Machine>as().jvm().get2()) : this.jvm().get2(),
                        otherMachine ? this.jvm().get3().append(other.<Machine>as().jvm().get3()) : this.jvm().get3()),
                this.tid().plus(other.tid()), this.vid());
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

    public static class Helper {
        private Helper() {
            // do nothing
        }

        public static String machToString(final Machine mach) {
            return Obj.Helper.objToString(mach);
        }

        public static int machHashCode(final Machine mach) {
            return Obj.Helper.objHashCode(mach);
        }

        public static boolean machEquals(final Machine mach, final Object other) {
            return Obj.Helper.objEquals(mach, other);
        }


    }

}