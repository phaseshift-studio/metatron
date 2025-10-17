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
import studio.phaseshift.metatron.space.Space;

import java.util.Set;

import static studio.phaseshift.metatron.lang.fURI.f;


public interface InstSet extends Space {

    fURI A = f("A");
    fURI B = f("B");
    fURI C = f("C");
    fURI D = f("D");
    fURI E = f("E");
    fURI F = f("F");
    fURI G = f("G");

    @Override
    fURI pattern();

    Set<Obj> consts();

    Set<Type> types();

    Set<Inst> insts();

    Set<Inst> rewrites();

    @Override
    default void append(final fURI addr, final Obj... obj) {
    }
}
