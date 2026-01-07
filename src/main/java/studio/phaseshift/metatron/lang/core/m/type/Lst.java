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

import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.util.Common;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.*;
import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

public interface Lst extends Poly<Lst, List<Obj>>, PlusMonoid.O<Lst> {

    @Override
    default Stream<Rel> indexedStream() {
        final AtomicInteger i = new AtomicInteger(0);
        return this.jvm().stream().map(e -> rel(jnt(i.getAndIncrement()), e).c(c -> this.c()).as());
    }

    @Override
    Lst clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    List<Obj> jvm();

    @Override
    default long count() {
        return this.jvm().size();
    }

    default Lst add(final Obj obj) {
        return this.add(obj, IMMUTABLE);
    }

    default Lst add(final Obj obj, final BiFunction<Poly<?, ?>, Object, Poly<?, ?>> operation) {
        final ArrayList<Obj> newList = new ArrayList<>(this.lstValue());
        newList.add(obj);
        return (Lst) operation.apply(this, newList);
    }

    default <O extends Obj> Stream<O> elements() {
        return (Stream) IteratorUtil.stream(this.jvm()).map(e -> e.c(c -> c.mult(this.c())));
    }

    default Lst at(final Obj key, final Obj value, final BiFunction operation) {
        if (key.isInt()) {
            final ArrayList<Obj> newList = new ArrayList<>(this.lstValue());
            if (value.isNoObj())
                newList.remove(key.intValue().intValue());
            else
                newList.set(key.intValue().intValue(), value);
            return this.clone(newList, this.tid(), this.vid());
        } else if (key.isUri()) {
            final Int k = jnt(Long.parseLong(key.uriValue().segments().get(0)));
            if (key.uriValue().segments().size() == 1) {
                return this.at(k, value, operation);
            } else {
                final Obj v = this.jvm().get(k.intValue().intValue());
                if (v.isLst()) {
                    return this.at(k, v.<Lst>as().at(uri(key.<Uri>as().uriValue().pretract()), value, operation), operation).as();
                } else if (v.isRec()) {
                    return this.at(k, v.<Rec>as().at(uri(key.<Uri>as().uriValue().pretract()), value, operation), operation).as();
                } else {
                    throw MTronException.of("unknown key value for lst: %s => %s", key, value);
                }
            }
        } else {
            throw MTronException.of("unknown key for lst: %s", key);
        }
    }

    @Override
    default <O extends Obj> O at(final Obj key) {
        if (key.isInt())
            return (O) ((this.jvm().size() > key.intValue()) ? this.jvm().get(key.<Int>as().intValue().intValue()).autoResolve(key) : noobj());
        else if (key.isUri()) {
            final String step = key.uriValue().segments().getFirst();
            Stream<Obj> result;
            if (step.equals("+") || step.equals("#")) {
                result = this.elements();
            } else {
                if (!Common.isInt(step))
                    return (O) noobj();
                //throw MTronException.of("path segment is not an int: %s", step);
                final Int k = jnt(Long.parseLong(step));
                if (this.jvm().size() <= k.intValue().intValue())
                    return (O) noobj();
                result = Stream.of(this.jvm().get(k.intValue().intValue()));
            }
            if (key.uriValue().segments().size() == 1) {
                return (O) objs(result.map(e -> e.autoResolve(key)).filter(x -> !x.isNoObj()));
            } else {
                return (O) objs(result.map(e -> e.autoResolve(key)).filter(x -> !x.isNoObj()).filter(Obj::isPoly).map(r -> r.<Poly>as().at(uri(key.<Uri>as().uriValue().pretract()))));
            }
        } else {
            throw MTronException.of("unknown key for lst: %s", key);
        }
    }

    @Override
    default Lst c(final Function<cInt, cInt> f) {
        return (Lst) Poly.super.c(f);
    }

    @Override
    default Lst plus(final Lst rhs) {
        final List<Obj> list = new ArrayList<>();
        this.elements().map(e -> e.c(c -> c.mult(this.c()))).forEach(list::add);
        rhs.elements().map(e -> e.c(c -> c.mult(rhs.c()))).forEach(list::add);
        return this.<Lst>jvm(list).c(cInt::one);
    }

    @Override
    default Lst zero() {
        return lst(List.of());
    }

    @Override
    default boolean matches(final Obj rhs) {
        if (rhs.isLst()) {
            if (rhs.lstValue().size() > this.lstValue().size())
                return false;
            for (int i = 0; i < rhs.lstValue().size(); i++) {
                final Obj l = this.lstValue().get(i).autoResolve(this);
                final Obj r = rhs.lstValue().get(i).autoResolve(this);
                if (!l.matches(r))
                    return false;
            }
            return true;
        } else {
            return Poly.super.matches(rhs);
        }
    }

    public static final class LstType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(PLUS_INST_TID.dom(LST_TID).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> lhs.jvm(Stream.concat(lhs.elements(), inst.arg(0).elements()).toList())),
                    instC(MULT_INST_TID.dom(LST_TID).rng(LST_TID), lst(T(LST_TID)), (lhs, inst) -> lhs.jvm(lhs.elements().flatMap(a -> inst.arg(0).elements().map(b -> rel(a, b))).toList())),
                    instC(LSHIFT_INST_TID.dom(LST_TID).rng(LST_TID), lst(isa_(T(INT_TID)).else_(jnt(1))), (lhs, inst) -> objs(lhs.stream().filter(Obj::isLst).map(l -> l.jvm(lhs.<Lst>as().indexedStream().filter(r -> r.first().intValue() >= inst.arg(0).intValue()).map(Rel::second).toList())))),
                    instC(RSHIFT_INST_TID.dom(LST_TID).rng(LST_TID), lst(isa_(T(INT_TID)).else_(jnt(1))), (lhs, inst) -> objs(lhs.stream().filter(Obj::isLst).map(l -> l.jvm(lhs.<Lst>as().indexedStream().filter(r -> r.first().intValue() < (lhs.lstValue().size() - inst.arg(0).intValue())).map(Rel::second).toList())))),
                    instC(MERGE_INST_TID.dom(LST_TID).rng(ALL_STAR), lst(), (lhs, inst) -> objs(lhs.elements())),
                    instC(GET_INST_TID.dom(LST_TID).rng(ALL_STAR), lst(T(INT_TID)), (lhs, inst) -> lhs.<Lst>as().at(inst.arg(0))),
                    instC(GET_INST_TID.dom(LST_TID).rng(ALL_STAR), lst(T(URI_TID)), (lhs, inst) -> lhs.<Lst>as().at(inst.arg(0))),
                    instC(HAS_INST_TID.dom(LST_TID).rng(LST_TID.maybe()), lst(T(ALL)), (lhs, inst) -> lhs.<Lst>as().elements().anyMatch(r -> r.matches(inst.arg(0))) ? lhs : noobj()),
                    instC(WITHIN_INST_TID.dom(LST_TID).rng(LST_TID), lst(T(ALL_STAR)), (lhs, inst) -> lst(inst.arg(0).apply(objs(lhs.stream().flatMap(Obj::elements))).stream().toList())),
                    instC(SUM_INST_TID.dom(LST_TID.maybeSome()).rng(LST_TID), lst(), (lhs, inst) -> inst.seed().jvm(lhs.stream().reduce(inst.seed(), (a, b) -> ((Lst) a).plus((Lst) b)).lstValue()), lst()),
                    instC(SELECT_INST_TID.dom(LST_TID).rng(LST_TID.maybe()), lst(T(LST_TID)), (lhs, inst) -> crossPoly(lhs, inst.arg(0))),
                    instC(POW_INST_TID.dom(LST_TID).rng(LST_TID), lst(T(INT_TID)), (lhs, inst) -> {
                        int pow = inst.arg(0).intValue().intValue();
                        Lst l = lhs.clone(lhs.jvm(), lhs.tid(), null);
                        for (int i = 0; i < pow; i++) {
                            l = l.jvm(l.elements().flatMap(a -> inst.arg(0).elements().map(b -> rel(a, b))).toList());
                        }
                        return lhs.jvm(l);
                    })
            ));
        }
    }


}