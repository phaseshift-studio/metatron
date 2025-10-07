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

package studio.phaseshift.metatron.lang.monoid;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.*;

import static studio.phaseshift.metatron.util.Tuple.Quartet;

;

public interface MTonoid extends Obj, Call {

    @Override
    MTonoid clone(final Object value, final fURI tid, final fURI vid);

    // code, running, barriers, halted
    @Override
    Quartet<Code, Obj, Lst, Obj> value();


    default Obj halted() {
        return this.value().get3();
    }

    default Lst barriers() {
        return this.value().get2();
    }

    default Obj running() {
        return this.value().get1();
    }

    default Code code() {
        return this.value().get0();
    }

    default MTonoid code(final Code code) {
        return this.clone(Quartet.with(code, this.running(), this.barriers(), this.halted()), this.tid(), this.vid());
    }


    @Override
    MTonoid resolve(final Obj lhs);

    @Override
    default Type dom() {
        return this.value().get0().dom();
    }

    @Override
    default Type rng() {
        return this.value().get0().rng();
    }

}
