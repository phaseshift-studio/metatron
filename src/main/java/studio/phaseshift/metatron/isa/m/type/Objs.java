/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.isa.m.type;

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.impl.MObjs;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;

public interface Objs extends Obj {
    
    static Obj trySingleton(final Obj obj) {
        return null != obj && obj.isObjs() ? objs(obj) : obj;
    }

    @Override
    default Type rng() {
        return Type.Helper.findLCD(IteratorUtil.stream(this.jvm()).map(Obj::rng).toList());
    }

    @Override
    default Type dom() {
        return Type.Helper.findLCD(IteratorUtil.stream(this.jvm()).map(Obj::dom).toList());
    }

    /*@Override
    default Type type() {
        final cInt cc = IteratorUtil.stream(this.jvm()).map(Obj::c).reduce(cInt.ZERO(), cInt::plus);
        final Type t = Type.Helper.findLCD(IteratorUtil.stream(this.jvm()).map(Obj::type).toList());
        return T(t.vid()).c(cc).as();
    }*/

    @Override
    default Obj autoResolve(final Obj obj) {
        return objs(this.stream().map(x -> (null == x ? noobj() : x).autoResolve(obj)));
    }

    default Obj autoResolve() {
        return this.autoResolve(noobj());
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

    default Objs zero() {
        return MObjs.objs0();
    }

}