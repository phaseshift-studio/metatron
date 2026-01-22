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

package studio.phaseshift.metatron.lang.core.m.type;

import studio.phaseshift.metatron.furi.c.cInt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.AUTO_FROM_INST_TID;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.AUTO_INST_TID;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
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

    default <O extends Obj> Stream<O> argElements() {
        return this.elements().map(e -> (O) (e instanceof Rel ? ((Rel) e).second() : e));
    }

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

    @Override
    default boolean isResolved(final boolean nested) {
        return this.elements().allMatch(x -> x.isResolved(nested));
    }

    @Override
    default Obj autoResolve(final Obj obj) {
        return Obj.super.autoResolve(obj).parent(this);
    }

    class Helper {
        public static Obj selectPolyRecursion(final Poly<?, ?> lhs, final Poly<?, ?> rhs) {
            if (lhs.isRec() && rhs.isRec())
                return selectRecRecursion(lhs.asRec(), rhs.asRec());
            else if (lhs.isLst() && rhs.isLst())
                return selectLstRecursion(lhs.asLst(), rhs.asLst());
            else
                return noobj();
        }

        public static Obj selectLstRecursion(final Lst lhs, final Lst rhs) {
            final List<Obj> result = new ArrayList<>();
            final List<Obj> rhsList = rhs.lstValue();
            for (int i = 0; i < rhsList.size(); i++) {
                final Obj e = rhsList.get(i);
                final Obj selectKey = jnt(i);
                if (!selectKey.isNoObj()) {
                    final Obj lhsValue = lhs.at(selectKey);
                    final Obj selectValue = e.isCall() ? e.apply(lhsValue) : lhsValue.matches(e) ? lhsValue : noobj();
                    if (selectValue.isPoly()) {
                        result.add(selectPolyRecursion(selectValue.as(), e.as()));
                    } else
                        result.add(selectValue);
                }
            }
            return result.isEmpty() ? noobj() : lst(result);
        }

        public static Obj selectRecRecursion(final Rec lhs, final Rec rhs) {
            Map<Obj, Obj> result = new LinkedHashMap<>();
            rhs.elements().forEach(kv -> {
                final Obj selectKeys = kv.first().isCall() ? objs(lhs.elements().map(kv2 -> kv.first().apply(kv2.first())).filter(v2 -> !v2.isNoObj())) : kv.first();
                selectKeys.stream().forEach(selectKey -> {
                    if (!selectKey.isNoObj()) {
                        final Obj lhsValue = lhs.asRec().at(selectKey);
                        //if (lhsValue.matches(kv.second())) {
                        final Obj selectValue = kv.second().isCall() ? kv.second().apply(lhsValue) : kv.second();
                        if (!selectValue.isFail() && !selectValue.isNoObj()) {
                            if (selectValue.isPoly() && kv.second().isPoly()) {
                                result.compute(selectKey.c(cInt::one), (a, b) -> (b == null ? noobj() : b).append(selectPolyRecursion(selectValue.as(), kv.second().as())));
                            } else
                                result.compute(selectKey.c(cInt::one), (a, b) -> (b == null ? selectValue : b.append(selectValue)));
                        }
                        //}
                    }
                });
            });
            return result.isEmpty() ? noobj() : rec(result);
        }
    }
}
