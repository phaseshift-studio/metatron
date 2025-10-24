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

package studio.phaseshift.metatron.lang.obj;

import studio.phaseshift.metatron.lang.fURI;

import static studio.phaseshift.metatron.util.Tuple.Pair;

public interface Rel extends Poly, Obj {

    @Override
    Rel clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    Pair<Obj, Obj> jvm();

    @Override
    default long count() {
        return 2;
    }

    @Override
    default Iterable<Obj> elements() {
        return (Iterable) this.jvm();
    }

    /// /////////////////////////////////////////////////////////
    /// /////////////////////////////////////////////////////////

    default Obj first() {
        return this.jvm().get0();
    }

    default Obj second() {
        return this.jvm().get1();
    }

    default Rel first(final Obj key) {
        return this.jvm(Pair.with(key, this.second()));
    }

    default Rel second(final Obj value) {
        return this.jvm(Pair.with(this.first(), value));
    }

    @Override
    default <O extends Obj> O at(final Obj key) {
        return (O) (this.first().matches(key) ? this.second() : NoObj.single());
    }

    /*default Type dom() {
        return this.value().getValue0().dom();
    }

    default Type rng() {
        return this.value().getValue1().rng();
    }*/

}