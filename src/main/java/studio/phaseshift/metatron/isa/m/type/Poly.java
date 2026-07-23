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
import studio.phaseshift.metatron.util.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst0;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
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
    };

    BiFunction<Poly<?, ?>, Object, Poly<?, ?>> IMMUTABLE = (poly, jvm) -> poly.clone(jvm, poly.tid(), poly.vid());

    long count();

    default boolean isEmpty() {
        return 0 == this.count();
    }

    <O extends Obj> Stream<O> elements();

    <O extends Obj> Stream<O> valueElements();

    /// ////////////////////////////////////////////////////////////////////////////////////

    Poly<?, ?> zero();

    <O extends Obj> O at(final Obj key);

    default <O extends Obj> O at(final String key) {
        return this.at(uri(key));
    }

    default Lst atLst(final String key) {
        return this.at(key).orElse(lst0());
    }

    default Rec atRec(final String key) {
        return this.at(key).orElse(rec0());
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
        return true;
    }//return this.elements().allMatch(x -> x.isResolved(nested));
    

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
                    .map(r -> rel(jnt(r.get0()), rel(r.get1().getKey(), r.get1().getValue())))
                    .reduce(new ArrayList<>(), (a, b) -> {
                        a.add(b);
                        return a;
                    }, (a, b) -> {
                        a.addAll(b);
                        return a;
                    }), tid, vid);
        }

        public static Obj selectPolyRecursion(final Poly<?, ?> lhs, final Poly<?, ?> rhs, final boolean verify) {
            if (lhs.isRec()) {
                if (rhs.isRec())
                    return selectRecRecursion(lhs.asRec(), rhs.asRec(), verify);
            } else if (lhs.isLst()) {
                if (rhs.isLst())
                    return selectLstLstRecursion(lhs.asLst(), rhs.asLst(), verify);
                else if (rhs.isRec())
                    return selectLstRecRecursion(lhs.asLst(), rhs.asRec(), verify);
            } else if (lhs.isRel() && rhs.isRel())
                return selectRelRecursion(lhs.asRel(), rhs.asRel(), verify);
            if (verify)
                throw ProjectionFailureException.instance();
            return noobj();
        }

        public static Obj selectLstLstRecursion(final Lst lhs, final Lst rhs, final boolean verify) {
            final List<Obj> result = selectLstLstRecursionRaw(lhs, rhs, (a, b) -> selectPolyRecursion(a.as(), b.as(), verify), verify);
            return lst(result, rhs.tid(), rhs.vid());
        }

        public static List<Obj> selectLstLstRecursionRaw(final Lst lhs, final Lst rhs, final BiFunction<Poly<?, ?>, Poly<?, ?>, Obj> polyRecursion, final boolean verify) {
            final List<Obj> result = new ArrayList<>();
            final List<Obj> rhsList = rhs.lstValue();
            final List<Obj> lhsList = lhs.lstValue();
            for (int i = 0; i < Math.min(lhsList.size(), rhsList.size()); i++) {
                final Obj selectKey = jnt(i);
                final Obj rhsValue = rhs.at(selectKey);
                final Obj lhsValue = lhs.at(selectKey).selfVID(null);
                final Obj newValue = (lhsValue.isPoly() && rhsValue.isPoly() ? polyRecursion.apply(lhsValue.as(), rhsValue.as()) : rhsValue.apply(lhsValue)).selfVID(null);
                if (verify && newValue.isNothing())
                    throw ProjectionFailureException.instance();
                if (!newValue.isNone())
                    result.add(newValue);
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

        public static Obj selectRelRecursion(final Rel lhs, final Rel rhs, final boolean verify) {
            final Object result = selectRelRecursionRaw(lhs, rhs, (a, b) -> selectPolyRecursion(a.as(), b.as(), verify));
            return result instanceof Obj ? (Obj) result : rel(((Tuple.Pair<Obj, Obj>) result).get0(), ((Tuple.Pair<Obj, Obj>) result).get1());
        }

        public static Obj selectRecRecursion(final Rec lhs, final Rec rhs, final boolean verify) {
            final Map<Obj, Obj> result = selectRecRecursionRaw(lhs, rhs, (a, b) -> selectPolyRecursion(a.as(), b.as(), verify), verify);
            return result.isEmpty() ? noobj() : rec(result, rhs.tid(), rhs.vid());
        }

        public static Lst selectLstRecRecursion(final Lst lhs, final Rec rhs, final boolean verify) {
            final List<Obj> result = new ArrayList<>();
            IteratorUtil.indexedStream(lhs.jvm().iterator()).forEach(pair -> {
                final Obj newElement = rhs.jvm().entrySet().stream()
                        .filter(kv -> jnt(pair.get0()).test(kv.getKey()))
                        .map(kv -> (pair.get1().isPoly() && kv.getValue().isPoly()) ?
                                Poly.Helper.selectPolyRecursion(pair.get1().asPoly(), kv.getValue().asPoly(), verify) :
                                kv.getValue().apply(pair.get1())).findFirst().orElse(noobj());
                if (verify && newElement.isNoObj())
                    throw ProjectionFailureException.instance();
                if (!newElement.isNone())
                    result.add(newElement);
            });
            return lst(result);
        }

        public static Map<Obj, Obj> selectRecRecursionRaw(final Rec lhs, final Rec rhs, final BiFunction<Poly<?, ?>, Poly<?, ?>, Obj> polyRecursion, final boolean verify) {
            final Map<Obj, Obj> result = new LinkedHashMap<>();
            rhs.jvm().forEach((rKey, rValue) -> {
                final AtomicBoolean found = new AtomicBoolean(false);
                final Obj lSelectKeys = objs(lhs.jvm().keySet().stream().map(rKey).filter(lKey -> !lKey.isNoObj()));
                lSelectKeys.stream().filter(k -> !k.isNoObj()).forEach(lSelectKey -> {
                    final Obj selectKeyOne = lSelectKey.c(cInt::one);
                    if (!lSelectKey.isNoObj()) {
                        final Obj lhsValue = lhs.asRec().at(selectKeyOne);
                        if (!lhsValue.isNoObj()) {
                            found.set(true);
                            final Obj rhsValue = rValue.autoResolve(rhs);
                            final Obj selectValue = (lhsValue.isPoly() && rhsValue.isPoly() ?
                                    polyRecursion.apply(lhsValue.as(), rhsValue.as()) :
                                    verify ? (lhsValue.test(rhsValue) ? lhsValue : noobj()) :
                                            rhsValue.apply(lhsValue)).selfVID(null);
                            if (selectValue.isNoObj() && verify)
                                throw ProjectionFailureException.instance();
                            if (!selectValue.isNoObj() && (!selectValue.isRec() || !selectValue.asRec().isEmpty()))
                                result.compute(selectKeyOne, (a, b) -> null == b ? selectValue : (b.isObjCall() ? b.apply(selectValue) : b.append(selectValue))); // TODO: the c(1) may not be necessary
                        } else if (verify)
                            throw ProjectionFailureException.instance();
                    }
                });
                if (!found.get() && verify)
                    throw ProjectionFailureException.instance();

            });
            return result;
        }

        /// //////////////////////////////////////////////////////////////////////////

        public static Obj updateRecursion(final Obj lhs, final Obj rhs, final BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
            final Obj result;
           if (lhs.isObjs() && rhs.isPoly())
                result = objs(lhs.asObjs().elements()
                        .map(e -> updateRecursion(e, rhs, operation).vid(e.vid()))
                        .filter(e -> !e.isNoObj()));
            else if (lhs.isPoly() && (rhs.isPoly() || rhs.isObjCall()))
                result = updatePolyRecursion(lhs.as(), rhs, operation).vid(lhs.vid());

            else
                result = rhs.apply(lhs).vid(lhs.vid());
            return result;
        }


        public static Obj updatePolyRecursion(final Poly<?, ?> lhs, final Obj rhs, final BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
            final Obj result;
            if (rhs.isNoObj() || rhs.isNone())
                result = rhs;
            else if (rhs.isObjCall()) {
                // Structural instruction — compute against LHS directly, don't
                // recurse into updateRecRecursion (which SELECT-filters).
                // The top-level UPDATE instruction handles the space write.
                final Obj computed;
                /*if (lhs.isRec() && rhs.tid().basePath().toString().endsWith("/plus")) {
                    computed = lhs.asRec().plus(rhs.asInst().arg(0).asRec());
                } else {*/
                computed = rhs.apply(lhs);
                //}
                result = computed.isFail() ? lhs : computed;
            } else if (lhs.isRec() && rhs.isRec())
                result = updateRecRecursion(lhs.asRec(), rhs.asRec(), operation);
            else if (lhs.isLst() && rhs.isLst())
                result = updateLstRecursion(lhs.asLst(), rhs.asLst(), operation);
            else if (lhs.isLst() && rhs.isRec())
                result = updateLstRecRecursion(lhs.asLst(), rhs.asRec(), operation);
            else if (lhs.isRel() && rhs.isRel())
                result = updateRelRecursion(lhs.asRel(), rhs.asRel(), operation);
            else
                result = rhs.apply(lhs);
            return result;
        }

        private static Obj updateRecRecursion(final Rec lhs, final Rec rhs, final BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
            final Rec lhsClone = lhs.jvm().entrySet().stream().map(kv -> rel(kv.getKey().clone(), kv.getValue().clone())).collect(new CommonUtil.RecCollector());
            final Map<Obj, Obj> rhsApplied = Poly.Helper.selectRecRecursionRaw(lhsClone, rhs, (a, b) -> updatePolyRecursion(a.as(), b.as(), operation), false);
            lhsClone.jvm().forEach((lhsKey, lhsValue) -> rhsApplied.compute(lhsKey.c(cInt::one), (rhsKey, rhsValue) -> {
                if (null == rhsValue)
                    return lhsValue;
                if (rhsValue.isNoObj())
                    return noobj();
                if (rhsValue.isNone())
                    return noobj();
                return rhsValue;

            }));
            return operation.apply(lhsClone, rhsApplied).vid(lhs.vid()).tid(lhs.tid());
        }

        private static Obj updateLstRecursion(final Lst lhs, final Lst rhs, BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
            final List<Obj> lhsList = lhs.lstValue();
            final List<Obj> rhsList = rhs.lstValue();
            final List<Obj> result = new ArrayList<>();
            for (int i = 0; i < Math.min(lhsList.size(), rhsList.size()); i++) {
                final Obj newElement = updateRecursion(lhs.at(i), rhs.at(i), operation);
                if (!newElement.isNone())
                    result.add(newElement);
            }
            return operation.apply(lhs, result).vid(lhs.vid()).tid(lhs.tid());
        }

        public static Obj updateLstRecRecursion(final Lst lhs, final Rec rhs, BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
            final List<Obj> result = new ArrayList<>();
            IteratorUtil.indexedStream(lhs.jvm().iterator()).forEach(pair -> {
                final Obj newElement = rhs.jvm().entrySet().stream()
                        .filter(kv -> jnt(pair.get0()).test(kv.getKey()))
                        .map(kv -> (pair.get1().isPoly() && kv.getValue().isPoly()) ?
                                Poly.Helper.updatePolyRecursion(pair.get1().asPoly(), kv.getValue().asPoly(), operation) :
                                kv.getValue().apply(pair.get1())).findFirst().orElse(pair.get1());
                if (!newElement.isNone())
                    result.add(newElement);
            });
            return operation.apply(lhs, result);
        }

        private static Obj updateRelRecursion(final Rel lhs, final Rel rhs, BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
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
            while (!temp.isRootType() && !temp.isNoObj() && !temp.isNone() && temp.asType().hasPredicate()) {
                result.jvm().addAll(Poly.Helper.diffLstRecursion(lhs.asLst(), Type.Helper.typePredicateObj(temp).asLst()).jvm());
                temp = temp.parentType();
            }
            return result;
        }

        public static Rec diffTypeRecRecursion(final Rec lhs, final Type rhs) {
            Type temp = rhs;
            final Rec result = rec();
            while (!temp.isRootType() && !temp.isNoObj() && !temp.isNone() && temp.asType().hasPredicate()) {
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
                final Obj result = rhs.apply(lhs);
                if (result.isNone())
                    return noobj();
                return result;
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
                        result.at(kvMatch.get().first(), kvMatch.get().jvm().get1().isNone() ? noobj() : kvMatch.get().second(), MUTABLE);
                    else {
                        if (x.first().c().isZeroable())
                            result.at(x.first(), x.jvm().get1().isNone() ? noobj() : x.second().apply(), MUTABLE);
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
