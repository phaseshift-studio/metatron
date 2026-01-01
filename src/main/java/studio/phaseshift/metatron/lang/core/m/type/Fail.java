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

package studio.phaseshift.metatron.lang.core.m.type;

import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.util.Tuple;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.FAIL_TID;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Fail extends Obj, PlusMonoid<Fail> {

    Type FAIL_TYPE = T(FAIL_TID);

    @Override
    Fail clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    Tuple.Pair<Throwable, Fail> jvm();

    Fail plus(final Fail rhs);

    default Fail jvm(final Fail value) {
        return this.clone(value, this.tid(), this.vid());
    }

    default Fail tid(final fURI tid) {
        return this.clone(this.jvm(), tid, this.vid());
    }

    default Throwable message() {
        return this.jvm().get0();
    }

    default Optional<Fail> cause() {
        return Optional.ofNullable(this.jvm().get1());
    }

    Fail caught();

    @Override
    default boolean isResolved(final boolean nested) {
        return true;
    }

    final class FailType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(f("/m/inst/cause").dom(FAIL_TID).rng(FAIL_TID.maybe()), lst(), (lhs, inst) -> lhs.<Fail>as().cause().map(x -> (Obj) x).orElse(noobj())) // necessary cause of type casting
            ));

        }
    }

    interface CaughtFail extends Fail {

    }
}
