/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.impl.MObjs;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

public interface Objs extends Obj, PlusMonoid.O<Objs> {

    Type OBJS_TYPE = Type.Builder.build().tid(OBJS_TID).vid(OBJS_TID).create();

    static Obj trySingleton(final Obj obj) {
        return null != obj && obj.isObjs() ? objs(obj) : obj;
    }

    @Override
    default Type rng() {
        return IteratorUtil.stream(this.jvm()).map(Obj::rng).findFirst().orElse(NoObj.noobj().type());
    }

    @Override
    default Type dom() {
        return IteratorUtil.stream(this.jvm()).map(Obj::dom).findFirst().orElse(NoObj.noobj().type());
    }

    @Override
    default Obj autoResolve(final Obj obj) {
        return objs(this.stream().map(x -> x.autoResolve(obj)));
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

    @Override
    default Objs zero() {
        return MObjs.objs0();
    }

    @Override
    default Objs plus(final Objs other) {
        final Obj first = this.take();
        final Obj second = other.take();
        final PlusMonoid.O<?> result = null == first ? (null == second ? this.zero() : (PlusMonoid.O<?>) second) : (PlusMonoid.O<?>) ((PlusMonoid.O) first).plus((PlusMonoid.O) second);
        return (Objs) objs(List.of(result, this, other), ALL_STAR, null);
    }

    class ObjsType {
        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(AS_INST_TID.dom(ALL_STAR).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> lst(lhs.stream().toList(), inst.arg(0).tid(), null))
            ));
        }
    }

}