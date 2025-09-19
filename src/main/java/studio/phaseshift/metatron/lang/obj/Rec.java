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
import studio.phaseshift.metatron.lang.obj.mtron.MRel;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.Map;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.obj.mtron.MUri.uri;

public interface Rec extends Poly {

    @Override
    Rec clone(final Object value, final fURI tid, final fURI vid);

    @Override
    Map<Obj, Obj> value();

    @Override
    default long count() {
        return this.value().size();
    }

    @Override
    default Stream<Rel> stream() {
        return IteratorUtil.stream(this.elements());
    }

    @Override
    default Iterable<Rel> elements() {
        return () -> this
                .value()
                .entrySet()
                .stream()
                .map(kv -> (Rel) new MRel(Pair.with(kv.getKey(), kv.getValue()))).iterator();
    }

    @Override
    default Rec value(final Object newValue) {
        return this.clone(newValue, this.tid(), this.vid());
    }

    @Override
    default <O extends Obj> O at(final Obj key) {
        return (O) this.value().getOrDefault(key, NoObj.single());
    }

    default <O extends Obj> O at(final String key) {
        return this.at(uri(key));
    }

    Rec put(final Obj key, final Obj value);

    default Rec put(final String key, final Obj value) {
        return this.put(uri(key),value);
    }

}