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

import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.stream.Stream;

public interface Poly extends Obj {

    long count();

    default boolean isEmpty() {
        return 0 == this.count();
    }

    <O extends Obj> Iterable<O> elements();

    default <O extends Obj> Stream<O> elementStream() {
        return IteratorUtil.stream(this.elements());
    }

    <O extends Obj> O at(final Obj key);

    default Poly at(final Obj key, final Obj value) {
        return this;
    }

    default boolean has(final Obj key) {
        return !this.at(key).isNoObj();
    }

    default boolean has(final long index) {
        return index < this.count();
    }
}
