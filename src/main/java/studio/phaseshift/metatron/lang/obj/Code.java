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

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.monoid.mtron.MMonoid;

import java.util.List;

public interface Code extends Obj {

    @Override
    Code clone(final Object value, final fURI tid, final fURI vid);

    @Override
    List<Inst> value();

    default Inst inst(final int index) {
        return index < this.value().size() ? this.value().get(index) : NoObj.single();
    }

    default Inst next(final Inst inst) {
        boolean found = false;
        for (final Inst i : this.value()) {
            if (found) return i;
            if (i == inst) found = true;
        }
        return NoObj.single();
    }

    @Override
    default Obj apply(final Obj lhs) {
        return MMonoid.of(lhs,this).iterator().next();
    }

    Code resolve(final Obj start);

}