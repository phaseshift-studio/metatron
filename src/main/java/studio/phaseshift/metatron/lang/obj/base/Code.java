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

package studio.phaseshift.metatron.lang.obj.base;

import studio.phaseshift.metatron.lang.fURI;

import java.util.List;

public interface Code extends Obj {
    public static final fURI TID = fURI.of("/mtron/code");

    @Override
    Code clone(final Object value, final fURI tid, final fURI vid);

    @Override
    List<Inst> value();

    default Inst inst(final int index) {
        return index < this.value().size() ? this.value().get(index) : NoObj.single();
    }

}