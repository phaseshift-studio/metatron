/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang.obj;

import org.javatuples.Pair;
import studio.phaseshift.metatron.lang.fURI;

public interface Rel extends Poly {

    @Override
    Rel clone(final Object value, final fURI tid, final fURI vid);

    @Override
    Pair<Obj, Obj> value();

    @Override
    default long count() {
        return 2;
    }

    @Override
    default Iterable<Obj> elements() {
        return (Iterable) this.value();
    }

    /// /////////////////////////////////////////////////////////
    /// /////////////////////////////////////////////////////////

    default Type dom() {
        return this.value().getValue0().dom();
    }

    default Type rng() {
        return this.value().getValue1().rng();
    }

}