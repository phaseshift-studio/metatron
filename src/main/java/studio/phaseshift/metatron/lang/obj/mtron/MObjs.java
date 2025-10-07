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

package studio.phaseshift.metatron.lang.obj.mtron;

import studio.phaseshift.metatron.lang.C;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Objs;
import studio.phaseshift.metatron.lang.obj.Type;
import studio.phaseshift.metatron.lang.obj.mtron.c.cInt;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class MObjs implements Objs {

    private fURI vid;
    private final Map<Obj, cInt> cstream = new LinkedHashMap<>();

    public MObjs(final Iterable<Obj> objs, final fURI vid) {
        this.vid = vid;
        objs.forEach(i -> {
            this.cstream.compute(i.c(C::one), (lng, it) -> null == it ? i.c() : it.plus(i.c()));
        });
    }

 /*   @Override
    public Obj append(final Obj obj) {
        obj.stream().forEach(i -> this.cstream.compute(i.c(cInt::one), (lng, it) -> null == it ? i.tid().cV() : it.plus(i.tid().cV())));
        return this.cstream.size() == 1 ? this.cstream.entrySet().stream().map(kv -> kv.getKey().c(kv.getValue())).iterator().next() : ooobj(this.value());
    }*/

    @Override
    public Obj resolve(final Obj obj) {
        return ooobj(this.stream().map(o -> o.resolve(obj)));
    }

    @Override
    public Objs append(final Obj obj) {
        obj.forEach(i -> this.cstream.compute(i.tid(i.tid().cLess()), (lng, it) -> null == it ? i.c() : it.plus(i.c())));
        return this;
    }

    @Override
    public <O extends Obj> O remove() {
        while (this.cstream.keySet().iterator().hasNext()) {
            final O key = (O) this.cstream.keySet().iterator().next();
            final cInt value = this.cstream.remove(key);
            if (null == value)
                return key;
            else if (!value.isZero()) return (O) key.tid(key.tid().c(value.toString()));
        }
        return null;
    }


    public <O extends Obj> O remove(final fURI selector) {
        while (this.cstream.keySet().iterator().hasNext()) {
            final O key = (O) this.cstream.keySet().iterator().next();
            final cInt value = this.cstream.get(key);
            if (null == value) {
                this.cstream.remove(key);
                return key;
            } else if (value.within(selector.cV())) {
                final cInt newValue = value.minus(selector.cV());
                if (newValue.isZeroOrNeg())
                    this.cstream.remove(key);
                else
                    this.cstream.put(key, newValue);
                return (O) key.tid(key.tid().c(selector.c()));
            } else {
                throw MTronException.of("can't remove given selector: %s", selector);
            }

            //else if (!value.isZero()) return (O) key.tid(key.tid().coefficient(value.toString()));
        }
        return null;
    }

    public static Objs of(final Iterable<Obj> objs) {
        return objs(objs);
    }

    public static Objs of(final Obj... objs) {
        return objs(List.of(objs));
    }

    public static <O extends Obj> Objs objs(final Iterable<O> os) {
        return new MObjs((Iterable) os, null);
    }

    public static Objs objs(final Obj... objs) {
        return objs(List.of(objs));
    }

    public static Obj ooobj(final Obj... objs) {
        return ObjUtil.oneNoneOrAll(List.of(objs));
    }

    public static Obj ooobj(final Iterable<Obj> objs) {
        return ObjUtil.oneNoneOrAll(objs);
    }

    public static Obj ooobj(final Stream<Obj> objs) {
        return ObjUtil.oneNoneOrAll(objs);
    }

    @Override
    public Iterable<Obj> value() {
        return this.cstream.entrySet()
                .stream()
                .filter(kv -> !kv.getValue().isZero())
                .map(kv -> kv.getValue().isOne() ? kv.getKey() : kv.getKey().c(kv.getValue()))
                .toList();
    }

    @Override
    public fURI tid() {
        try {
            return this.cstream.entrySet()
                    .stream()
                    .filter(kv -> !kv.getValue().isZero())
                    .map(kv -> kv.getKey().c(kv.getValue()))
                    .map(Obj::tid)
                    .reduce(fURI::plus)
                    .orElse(fURI.NOOBJ);
        } catch (final Exception e) {
            return this.cstream.entrySet()
                    .stream()
                    .filter(kv -> !kv.getValue().isZero())
                    .map(kv -> kv.getKey().c(kv.getValue()))
                    .map(Obj::tid).reduce(fURI::commonRoot)
                    .orElse(fURI.NOOBJ);
        }
    }

    @Override
    public Objs vid(final fURI vid) {
        return new MObjs(this.value(), vid);
    }

    @Override
    public fURI vid() {
        return this.vid;
    }

    @Override
    public Type type() {
        return MType.of(this.tid());
    }

    @Override
    public Objs clone(final Object value, final fURI tid, final fURI vid) {
        return new MObjs(IteratorUtil.stream((Iterable) value).toList(), vid);
    }

    @Override
    public String toString() {
        return ObjUtil.objToString(this);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.cstream, this.vid);
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof MObjs && Objects.equals(this.vid, ((MObjs) other).vid) && Objects.equals(this.cstream, ((MObjs) other).cstream);
    }

    public static Obj ofUsage(final Object object) {
        if (null == object)
            return NoObj.single();
        if (object instanceof Stream)
            return ofUsage(((Stream) object).toList()); // TODO: strange....
        if (object instanceof List)
            return ObjUtil.oneNoneOrAll((List) object);
        if (object instanceof Obj)
            return (Obj) object;
        throw MTronException.of("unknown object type: %s", object);

    }
}
