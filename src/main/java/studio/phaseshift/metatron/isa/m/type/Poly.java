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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.Tuple;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public interface Poly<P extends Poly<P, J>, J> extends Obj {

    BiFunction<Poly<?, ?>, Object, Poly<?, ?>> MUTABLE = (poly, jvm) -> {
        Obj.Helper.objCheckAndSave(poly, jvm, poly.tid(), poly.vid());
        return poly;
        /*
          if (poly.isRec()) {
            final Map<Obj, Obj> updatedMap = ((Map<Obj, Obj>) jvm);
            final Map<Obj, Obj> originalMap = poly.asRec().jvm();
            originalMap.entrySet().stream().toList().forEach(e -> {
                if (updatedMap.containsKey(e.getKey()))
                    originalMap.put(e.getKey(), updatedMap.get(e.getKey()));
                else
                    originalMap.remove(e.getKey());
            });
            originalMap.putAll(updatedMap);
            Obj.Helper.objCheckAndSave(poly, poly.jvm(), poly.tid(), poly.vid(), true);
        } else
            Obj.Helper.objCheckAndSave(poly, jvm, poly.tid(), poly.vid());
        return poly;
         */
    };

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

    boolean has(final Obj key);

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

  /*  @Override
    default Obj autoResolve(final Obj obj) {
        return Obj.super.autoResolve(obj).parent(this);
    }*/

    class Helper {
        public static String identifyNoMatch(final Rec lhs, Rec rhs, int depth) {  
            // TODO: calculate the column widths dynamically
            // TODO: remove padding from flattened nested records
            final StringBuilder sb = new StringBuilder();
            lhs.asRec().elements().forEach(x -> {
                if (rhs.asRec().elements().noneMatch(y -> x.first().matches(y.first()) && x.second().matches(y.second()))) {
                    final Rel keyMatch = rhs.asRec().elements().filter(y -> x.first().matches(y.first())).findFirst().orElse(null);
                    if (keyMatch == null)
                        sb.append("%-15s =X> | %-15s\n".formatted(" ".repeat(depth) + x, rhs.toString().replace("\n", " ")));
                    else {
                        if (x.second().isRec() && keyMatch.second().isRec()) {
                            sb.append(identifyNoMatch(x.second().asRec(), keyMatch.second().asRec(), depth + 1));
                        } else {
                            sb.append("%-15s =X> | %-15s\n".formatted(" ".repeat(depth)+ x, keyMatch.toString().replace("\n", " ")));
                        }
                    }
                } else {
                    sb.append("%-15s =O> | %-15s\n".formatted(" ".repeat(depth) + x, rhs.asRec().elements().filter(y -> x.first().matches(y.first()) && x.second().matches(y.second())).findFirst().get().toString().replace("\n", " ")));
                }
            });
            return sb.toString();
        }


        public static Rec transformLstToRec(final Lst lhs, final fURI tid, final fURI vid) {
            return IteratorUtil.indexedStream(lhs.elements().iterator())
                    .map(r -> rel(jnt(r.get0()), r.get1()))
                    .collect(new CommonUtil.RecCollector(tid, vid));
        }

        public static Lst transformRecToLst(final Rec lhs, final fURI tid, final fURI vid) {
            return lst(IteratorUtil.indexedStream(lhs.elements().iterator())
                    .map(r -> rel(jnt(r.get0()), r.get1()))
                    .reduce(new ArrayList<>(), (a, b) -> {
                        a.add(b);
                        return a;
                    }, (a, b) -> {
                        a.addAll(b);
                        return a;
                    }), tid, vid);
        }

        public static Obj selectPolyRecursion(final Poly<?, ?> lhs, final Poly<?, ?> rhs) {
            if (lhs.isRec() && rhs.isRec())
                return selectRecRecursion(lhs.asRec(), rhs.asRec());
            else if (lhs.isLst() && rhs.isLst())
                return selectLstRecursion(lhs.asLst(), rhs.asLst());
            else if (lhs.isRel() && rhs.isRel())
                return selectRelRecursion(lhs.asRel(), rhs.asRel());
            else
                return noobj();
        }

        public static Obj selectLstRecursion(final Lst lhs, final Lst rhs) {
            final List<Obj> result = selectLstRecursionRaw(lhs, rhs, (a, b) -> selectPolyRecursion(a.as(), b.as()));
            return lst(result);
        }

        public static List<Obj> selectLstRecursionRaw(final Lst lhs, final Lst rhs, final BiFunction<Poly<?, ?>, Poly<?, ?>, Obj> polyRecursion) {
            final List<Obj> result = new ArrayList<>();
            final List<Obj> rhsList = rhs.lstValue();
            for (int i = 0; i < rhsList.size(); i++) {
                final Obj e = rhsList.get(i);
                final Obj selectKey = jnt(i);
                final Obj lhsValue = lhs.at(selectKey);
                result.add((lhsValue.isPoly() && e.isPoly() ? polyRecursion.apply(lhsValue.as(), e.as()) : e.apply(lhsValue)));
            }
            return result;
        }

        public static Object selectRelRecursionRaw(final Rel lhs, final Rel rhs, final BiFunction<Poly<?, ?>, Poly<?, ?>, Obj> polyRecursion) {
            final Obj newFirst = rhs.jvm().get0().apply(lhs.first());
            final Obj newSecond = rhs.jvm().get1().apply(lhs.second());
            if (lhs.second().isPoly() && newSecond.isPoly())
                return polyRecursion.apply(lhs.second().as(), newSecond.as());
            else
                return Tuple.Pair.with(newFirst, newSecond);
        }

        public static Obj selectRelRecursion(final Rel lhs, final Rel rhs) {
            final Object result = selectRelRecursionRaw(lhs, rhs, (a, b) -> selectPolyRecursion(a.as(), b.as()));
            return result instanceof Obj ? (Obj) result : rel(((Tuple.Pair<Obj, Obj>) result).get0(), ((Tuple.Pair<Obj, Obj>) result).get1());
        }

        public static Obj selectRecRecursion(final Rec lhs, final Rec rhs) {
            final Map<Obj, Obj> result = selectRecRecursionRaw(lhs, rhs, (a, b) -> selectPolyRecursion(a.as(), b.as()));
            return result.isEmpty() ? noobj() : rec(result);
        }

        public static Map<Obj, Obj> selectRecRecursionRaw(final Rec lhs, final Rec rhs, final BiFunction<Poly<?, ?>, Poly<?, ?>, Obj> polyRecursion) {
            final Map<Obj, Obj> result = new LinkedHashMap<>();
            rhs.elements().forEach(kv -> {
                final Obj selectKeys = objs(lhs.elements().map(kv2 -> kv.first().apply(kv2.first())).filter(v2 -> !v2.isNoObj()));
                selectKeys.stream().forEach(selectKey -> {
                    final Obj lhsValue = lhs.asRec().at(selectKey);
                    final Obj selectValue = lhsValue.isPoly() && kv.second().isPoly() ?
                            polyRecursion.apply(lhsValue.as(), kv.second().as()) :
                            kv.second().apply(lhsValue);
                    if (!selectValue.isNoObj() && (!selectValue.isRec() || !selectValue.asRec().isEmpty()))
                        result.compute(selectKey.c(cInt::one), (a, b) -> null == b ? selectValue : b.append(selectValue)); // TODO: the c(1) may not be necessary
                });
            });
            return result;
        }

        /// //////////////////////////////////////////////////////////////////////////

        public static Obj updatePolyRecursion(final Poly<?, ?> lhs, final Poly<?, ?> rhs, final BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
            if (lhs.isRec() && rhs.isRec())
                return updateRecRecursion(lhs.asRec(), rhs.asRec(), operation);
            else if (lhs.isLst() && rhs.isLst())
                return updateLstRecursion(lhs.asLst(), rhs.asLst(), operation);
            else if (lhs.isRel() && rhs.isRel())
                return updateRelRecursion(lhs.asRel(), rhs.asRel(), operation);
            else
                return noobj();
        }

        public static Obj updateRecRecursion(final Rec lhs, final Rec rhs, BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
            final Map<Obj, Obj> result = Poly.Helper.selectRecRecursionRaw(lhs, rhs, (a, b) -> updatePolyRecursion(a.as(), b.as(), operation));
           /* if(operation == MUTABLE) {
                result.forEach((k, v) -> lhs.recValue().compute(k.c(cInt::one), (a, b) -> v));
            } else */
            lhs.elements().forEach(kv -> result.compute(kv.first().c(cInt::one), (a, b) -> {
                if (null == b) {
                    if (kv.second().isPoly()) {
                        return updatePolyRecursion(kv.second().as(), lhs.as(), operation);
                    } else {
                        return kv.second().apply(lhs);
                    }
                } else {
                    return b;
                }
            }));
            return operation.apply(lhs, result);
        }

        public static Obj updateLstRecursion(final Lst lhs, final Lst rhs, BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
            final List<Obj> result = Poly.Helper.selectLstRecursionRaw(lhs, rhs, (a, b) -> updatePolyRecursion(a.as(), b.as(), operation));
            // TODO: ??
            return operation.apply(lhs, result);
        }

        public static Obj updateRelRecursion(final Rel lhs, final Rel rhs, BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
            final Object result = Poly.Helper.selectRelRecursionRaw(lhs, rhs, (a, b) -> updatePolyRecursion(a.as(), b.as(), operation));
            return result instanceof Obj ? (Obj) result : operation.apply(lhs, result);
        }
    }
}
