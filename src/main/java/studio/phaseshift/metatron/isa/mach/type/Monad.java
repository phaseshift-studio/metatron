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

import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.util.Objects;

import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Monad<OBJ extends Obj> extends Obj {

    Inst inst();

    Obj obj();

    default Rec state() {
        return rec0();
    }

    /// //////////////////////////////////////////////

    default boolean halted() {
        return this.inst().isNoObj();
    }

    default boolean dead() {
        return this.obj().isNoObj();
    }

    default boolean zombie() {
        return this.dead() && !this.halted();
    }

    /// ///////////////////////////////////////////////

    @Override
    OBJ jvm();

    class Helpers {
        
        public static String monadToString(final Monad monad) {
            return "%s::[%s<=o==M==i=>%s]".formatted(monad.tid(), monad.obj(), monad.inst());
        }

        public static int monadHashCode(final Monad monad) {
            return Objects.hash(monad.tid().one(), monad.jvm());
        }

        public static boolean monadEquals(final Monad monad, final Object other) {
            return other instanceof Monad && Obj.Helper.objEquals(monad, other);
        }

        public static boolean monadcLessEquals(final Monad monad, final Object other) {
            return other instanceof Monad && Obj.Helper.objcLessEquals(monad, other);
        }
    }


}
