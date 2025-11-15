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

import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

public interface Lst extends Poly, PlusMonoid.O<Lst> {

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
        final ArrayList<Obj> newList = new ArrayList<>(this.lstValue());
        newList.add(obj);
        return this.jvm(newList);
    }

    default <O extends Obj> Stream<O> elements() {
        return (Stream) IteratorUtil.stream(this.jvm()).map(e -> e.c(c -> c.mult(this.c())));
    }

    default Lst at(final Obj key, final Obj value) {
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
                return this.at(k, value);
            } else {
                final Obj v = this.jvm().get(k.intValue().intValue());
                if (v.isPoly()) {
                    return this.at(k, v.<Poly>as().at(uri(key.<Uri>as().uriValue().pretract()), value));
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
            return (O) ((this.jvm().size() > key.intValue()) ? this.jvm().get(key.<Int>as().intValue().intValue()) : noobj());
        else if (key.isUri()) {
            final String step = key.uriValue().segments().get(0);
            Obj result;
            if (step.equals("+") || step.equals("#")) {
                result = objs(this.elements());
            } else {
                if (!StringUtil.isInt(step))
                    throw MTronException.of("path segment is not an int: %s", step);
                final Int k = jnt(Long.parseLong(step));
                result = this.jvm().get(k.intValue().intValue());
            }
            if (key.uriValue().segments().size() == 1) {
                return (O) result;
            } else {
                return (O) objs(IteratorUtil.stream(result.iterator()).filter(Obj::isPoly).map(r -> r.<Poly>as().at(uri(key.<Uri>as().uriValue().pretract()))));
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
        this.lstValue().stream().map(e -> e.c(c -> c.mult(this.c()))).forEach(list::add);
        rhs.lstValue().stream().map(e -> e.c(c -> c.mult(rhs.c()))).forEach(list::add);
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
                final Obj l = this.lstValue().get(i);
                final Obj r = rhs.lstValue().get(i);
                if (!l.matches(r))
                    return false;
            }
            return true;
        } else {
            return Poly.super.matches(rhs);
        }
    }


}