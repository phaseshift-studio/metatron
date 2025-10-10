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
import studio.phaseshift.metatron.lang.obj.mtron.c.cInt;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.function.Function;
import java.util.stream.Stream;

public interface Objs extends Obj {

    @Override
    Obj clone(final Object value, final fURI tid, final fURI vid);

    @Override
    Iterable<Obj> value();

    @Override
    fURI tid();

    @Override
    Obj append(final Obj obj);

    @Override
    cInt uniqueCount();

    @Override
    Obj c(final Function<cInt, cInt> func);

    @Override
    cInt c();

    @Override
    default Stream<Obj> stream() {
        return IteratorUtil.stream(this.value());
    }

}