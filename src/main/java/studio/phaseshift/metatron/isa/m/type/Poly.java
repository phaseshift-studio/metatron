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
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public interface Poly<P extends Poly<P, J>, J> extends Obj {

    BiFunction<Poly<?, ?>, Object, Poly<?, ?>> APPEND = (poly, jvm) -> {
        Obj.Helper.objCheckAndSave(poly, jvm, poly.tid(), poly.vid());
        return poly;
    };

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
    
    <O extends Obj> Stream<O> valueElements();

    /// ////////////////////////////////////////////////////////////////////////////////////

    <O extends Obj> O at(final Obj key);

    default <O extends Obj> O at(final String key) {
        return this.at(uri(key));
    }

    default <O extends Obj> O at(final fURI key) {
        return this.at(uri(key));
    }

    /// ////////////////////////////////////////////////////////////////////////////////////

    P at(final Obj key, final Obj value, final BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation);

    default P at(final fURI key, final Obj value, final BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
        return this.at(uri(key), value, operation);
    }

    default P at(final String key, final Obj value, final BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
        return this.at(uri(key), value, operation);
    }

    default P at(final Obj key, final Obj value) {
        return this.at(key, value, IMMUTABLE);
    }

    default P at(final fURI key, final Obj value) {
        return this.at(uri(key), value, IMMUTABLE);
    }

    default P at(final String key, final Obj value) {
        return this.at(uri(key), value, IMMUTABLE);
    }

    /// ////////////////////////////////////////////////////////////////////////////////////

    default boolean has(final Obj key) {
        return !this.at(key).isNoObj();
    }

    default boolean has(final String key) {
        return this.has(uri(key));
    }

    default boolean has(final fURI key) {
        return this.has(uri(key));
    }

    default boolean has(final long index) {
        return index < this.count();
    }

    /// /////////////////////////////////////////////////////////////////////////////////////

    default Stream<Rel> indexedStream() {
        return Stream.of(rel(this.vid().toUri(), this));
    }

    Stream<Obj> values();

    Stream<Obj> keys();

    @Override
    default boolean isResolved(final boolean nested) {
        return this.elements().allMatch(x -> x.isResolved(nested));
    }

  /*  @Override
    default Obj autoResolve(final Obj obj) {
        return Obj.super.autoResolve(obj).parent(this);
    }*/

    /// ///////////////////////////////////////////////////////////////////////////////////////

    class Helper {
        public static Rec transformLstToRec(final Lst lhs, final fURI tid, final fURI vid) {
            return IteratorUtil.indexedStream(lhs.lstValue().iterator())
                    .map(r -> rel(jnt(r.get0()), r.get1()))
                    .collect(new CommonUtil.RecCollector(tid, vid));
        }

        public static Lst transformRecToLst(final Rec lhs, final fURI tid, final fURI vid) {
            return lst(IteratorUtil.indexedStream(lhs.recValue().entrySet().iterator())
                    .map(r -> rel(jnt(r.get0()), rel(r.get1().getKey(),r.get1().getValue())))
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
            rhs.jvm().forEach((key, value) -> {
                final Obj selectKeys = objs(lhs.jvm().keySet().stream().map(key).filter(k2 -> !k2.isNoObj()));
                selectKeys.stream().forEach(selectKey -> {
                    final Obj selectKeyOne = selectKey.c(cInt::one);
                    final Obj lhsValue = lhs.asRec().at(selectKeyOne);
                    final Obj rhsValue = value.isAutoFrom() ? value : value.autoResolve(rhs);
                    final Obj selectValue = lhsValue.isPoly() && rhsValue.isPoly() ?
                            polyRecursion.apply(lhsValue.as(), rhsValue.as()) :
                            rhsValue.apply(lhsValue);
                    if (!selectValue.isNoObj() && (!selectValue.isRec() || !selectValue.asRec().isEmpty()))
                        result.compute(selectKeyOne, (a, b) -> null == b ? selectValue : b.append(selectValue)); // TODO: the c(1) may not be necessary
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
            lhs.jvm().entrySet().forEach(kv -> result.compute(kv.getKey().c(cInt::one), (a, b) -> {
                if (null == b) {
                    if (kv.getValue().isPoly()) {
                        return updatePolyRecursion(kv.getValue().as(), kv.getValue().as(), operation);
                    }
                    if (kv.getValue().isNoObj()) {
                        return noobj();
                    } else {
                        return a.isAutoFrom() ? a : kv.getValue().apply(lhs);
                    }
                } else {
                    if (b.isNoObj()) {
                        //  lhs.logger().warn("FOUND");
                        return noobj();

                    }
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

        /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        private static final Uri GOOD = uri("=");
        private static final Uri SORTA = uri("/");
        private static final Uri FAIL = uri("X");

        public static Obj diffTypeRecursion(final Obj lhs, final Type rhs) {
            return lhs.isRec() ? diffTypeRecRecursion(lhs.asRec(), rhs) : diffTypeLstRecursion(lhs.asLst(), rhs);
        }

        public static Lst diffTypeLstRecursion(final Lst lhs, final Type rhs) {
            Type temp = rhs;
            final Lst result = lst();
            while (null != temp && !temp.isNoObj() && temp.asType().hasPredicate()) {
                result.jvm().addAll(Poly.Helper.diffLstRecursion(lhs.asLst(), Type.Helper.typePredicateObj(temp).asLst()).jvm());
                temp = temp.parentType();
            }
            return result;
        }

        public static Rec diffTypeRecRecursion(final Rec lhs, final Type rhs) {
            Type temp = rhs;
            final Rec result = rec();
            while (null != temp && !temp.isNoObj() && temp.asType().hasPredicate()) {
                result.jvm().putAll(Poly.Helper.diffRecRecursion(lhs.asRec(), Type.Helper.typePredicateObj(temp).asRec()).jvm());
                temp = temp.parentType();
            }
            return result;
        }

        public static Obj diffObjRecursion(final Obj lhs, final Obj rhs) {
            if (lhs.isRec() && rhs.isRec())
                return diffRecRecursion(lhs.asRec(), rhs.asRec());
            else if (lhs.isLst() && rhs.isLst())
                return diffLstRecursion(lhs.asLst(), rhs.asLst());
            else {
                if (lhs.test(rhs)) {
                    return rel(lhs, rel(GOOD, rhs));
                } else {
                    return rel(lhs, rel(FAIL, rhs));
                }
            }
        }

        public static Lst diffLstRecursion(final Lst lhs, final Lst rhs) {
            final List<Obj> result = new ArrayList<>();
            final int max = Math.max(lhs.lstValue().size(), rhs.lstValue().size());
            for (int i = 0; i < max; i++) {
                final Obj x = i < lhs.lstValue().size() ? lhs.lstValue().get(i) : noobj();
                final Obj y = i < rhs.lstValue().size() ? rhs.lstValue().get(i) : noobj();
                if (!x.test(y))
                    result.add(rel(FAIL, x));
                else
                    result.add(rel(GOOD, x));
            }
            return lst(result);
        }


        public static Rec diffRecRecursion(final Rec lhs, final Rec rhs) {
            final Rec result = rec();
            rhs.elements().forEach(x -> {
                final Optional<Rel> kvMatch = lhs.asRec().elements().filter(y -> y.first().test(x.first()) && y.second().test(x.second())).findFirst();
                if (kvMatch.isPresent())
                    result.at(rel(kvMatch.get().first(), rel(GOOD, x.first())), rel(kvMatch.get().second(), rel(GOOD, x.second())), MUTABLE);
                else {
                    final Optional<Rel> kMatch = lhs.asRec().elements().filter(y -> y.first().test(x.first())).findFirst();
                    if (kMatch.isPresent())
                        result.at(rel(kMatch.get().first(), rel(SORTA, x.first())), diffObjRecursion(kMatch.get().second(), x.second()), MUTABLE);
                    else if (!x.first().c().isZeroable() && !x.second().c().isZeroable())
                        result.at(rel(noobj(), rel(FAIL, x.first())), rel(noobj(), rel(FAIL, x.second())), MUTABLE);
                    else if (!x.second().c().isZeroable())
                        result.at(rel(noobj(), rel(GOOD, x.first())), rel(noobj(), rel(SORTA, x.second())), MUTABLE);
                    else
                        result.at(rel(noobj(), rel(x.first().c().isZeroable() ? GOOD : SORTA, x.first())), rel(noobj(), rel(GOOD, x.second())), MUTABLE);
                }
            });
            return result;
        }

        /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        public static Obj applyObjRecursion(final Obj lhs, final Obj rhs) {
            if (lhs.isRec() && rhs.isRec())
                return applyRecRecursion(lhs.asRec(), rhs.asRec());
            else if (lhs.isLst() && rhs.isLst())
                return applyLstRecursion(lhs.asLst(), rhs.asLst());
            else {
                return rhs.apply(lhs);
            }
        }

        public static Lst applyLstRecursion(final Lst lhs, final Lst rhs) {
            final List<Obj> result = new ArrayList<>();
            final int max = Math.max(lhs.lstValue().size(), rhs.lstValue().size());
            for (int i = 0; i < max; i++) {
                final Obj x = i < lhs.lstValue().size() ? lhs.lstValue().get(i) : noobj();
                final Obj y = i < rhs.lstValue().size() ? rhs.lstValue().get(i) : noobj();
                if (!x.test(y))
                    result.add(fail(MTronException.of("lhs does not match rhs: %s %s", x, y)));
                else
                    result.add(applyObjRecursion(x, y));
            }
            return lst(result);
        }


        public static Rec applyRecRecursion(final Rec lhs, final Rec rhs) {
            final Rec result = rec();
            rhs.asRec().elements().forEach(x -> {
                try {
                    final Optional<Rel> kvMatch = lhs.asRec().elements()
                            .filter(y -> y.first().test(x.first()))
                            .filter(y -> y.second().test(x.second()))
                            .map(y -> rel(applyObjRecursion(y.first(), x.second()), applyObjRecursion(y.second(), x.second())))
                            .findFirst();
                    if (kvMatch.isPresent())
                        result.at(kvMatch.get().first(), kvMatch.get().second(), MUTABLE);
                    else {
                        if (x.first().c().isZeroable())
                            result.at(x.first(), x.second().apply(), MUTABLE);
                        else
                            result.at(x.first(), noobj(), MUTABLE);
                    }
                } catch (final Exception e) {
                    result.at(x.first(), fail(MTronException.of("error applying %s to %s", x.second(), x.first()), fail(e)), MUTABLE);
                }
            });
            return result;
        }
    }
}
