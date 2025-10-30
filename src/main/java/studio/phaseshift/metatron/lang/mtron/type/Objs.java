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

package studio.phaseshift.metatron.lang.mtron.type;

import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.impl.MObjs;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.mtron.type.impl.MObjs.objs;

public interface Objs extends Obj, PlusMonoid.O<Objs> {

    static Obj trySingleton(final Obj obj) {
        return obj.isObjs() ? objs(obj) : obj;
    }

    @Override
    default Type rng() {
        return IteratorUtil.stream(this.jvm()).map(Obj::rng).reduce(NoObj.noobj().type(), Type::plus);
    }

    @Override
    default Type dom() {
        return IteratorUtil.stream(this.jvm()).map(Obj::dom).reduce(NoObj.noobj().type(), Type::plus);
    }

    @Override
    Obj clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    Iterable<Obj> jvm();

    @Override
    fURI tid();

    @Override
    Obj append(final Obj obj);

    @Override
    cInt uniqueC();

    @Override
    Obj c(final Function<cInt, cInt> func);

    @Override
    cInt c();

    @Override
    default Stream<Obj> stream() {
        return IteratorUtil.stream(this.jvm());
    }

    @Override
    default <O extends Obj> Stream<O> elements() {
        return this.stream().flatMap(Obj::elements);
    }

    @Override
    default Objs zero() {
        return MObjs.empty();
    }

    @Override
    default Objs plus(final Objs other) {
        final Obj first = this.take();
        final Obj second = other instanceof Objs ? other.take() : other;
        final PlusMonoid.O<?> result = null == first ? (null == second ? this.zero() : (PlusMonoid.O<?>) second) : (PlusMonoid.O<?>) ((PlusMonoid.O) first).plus((PlusMonoid.O) second);
        return new MObjs(List.of(result, this, other));
    }

    //  @Override
    //  Tuple.Pair<Obj, Obj> headTailsSplit(final Function<Obj, Object> partitioner);

}