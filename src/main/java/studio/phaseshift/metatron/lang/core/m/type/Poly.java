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

package studio.phaseshift.metatron.lang.core.m.type;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

public interface Poly<P extends Poly<P, J>, J> extends Obj {

    BiFunction<Poly<?, ?>, Object, Poly<?, ?>> MUTABLE = (poly, jvm) -> poly.self(jvm, poly.tid(), poly.vid());

    BiFunction<Poly<?, ?>, Object, Poly<?, ?>> IMMUTABLE = (poly, jvm) -> poly.clone(jvm, poly.tid(), poly.vid());

    long count();

    default boolean isEmpty() {
        return 0 == this.count();
    }

    <O extends Obj> Stream<O> elements();

    <O extends Obj> O at(final Obj key);

    default P at(final Obj key, final Obj value) {
        return this.at(key, value, IMMUTABLE);
    }


    P at(final Obj key, final Obj value, final BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation);

    default boolean has(final Obj key) {
        return !this.at(key).isNoObj();
    }

    default boolean has(final String key) {
        return this.has(uri(key));
    }

    default boolean has(final long index) {
        return index < this.count();
    }

    default Stream<Rel> indexedStream() {
        return Stream.of(rel(this.vid().toUri(), this));
    }
}
