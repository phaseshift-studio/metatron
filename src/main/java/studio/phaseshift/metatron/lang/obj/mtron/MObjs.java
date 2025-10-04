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
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

package studio.phaseshift.metatron.lang.obj.mtron;

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
    private final Map<Obj, cInt> map = new LinkedHashMap<>();

/*
 private static fURI computeTID(final Iterable<Obj> value) {
        Set<fURI> types = IteratorUtil.stream(value).map(Obj::tid).map(fURI::basePath).collect(Collectors.toSet());
        // TODO: make efficient
        final long minCount = IteratorUtil.stream(value).map(Obj::tid).map(f -> (f.coefficientValue().min() != null) ? f.coefficientValue().min() : 1).reduce(0L, Long::sum);
        final long maxCount = IteratorUtil.stream(value).map(Obj::tid).map(f -> (f.coefficientValue().max() != null) ? f.coefficientValue().max() : 1).reduce(0L, Long::sum);
        //final long count = IteratorUtil.count(this.value());
        if (types.isEmpty() || 0 == maxCount) return fURI.NONE.zero();
        if (types.size() == 1) return types.iterator().next().coefficient(MCoeff.Int.of(minCount,maxCount).toString());
        final fURI temp = types.stream().reduce(fURI::commonRoot).get();
        return temp.coefficient(MCoeff.Int.of(minCount,maxCount).toString());
    }
 */

    public MObjs(final Iterable<Obj> ints, final fURI vid) {
        this.vid = vid;
        ints.forEach(i -> {
            this.map.compute(i.tid(i.tid().cLess()), (lng, it) -> null == it ? i.tid().cV() : it.plus(i.tid().cV()));
        });
    }

    @Override
    public Objs append(final Obj obj) {
        obj.iterator().forEachRemaining(i -> {
            this.map.compute(i.tid(i.tid().cLess()), (lng, it) -> null == it ? i.tid().cV() : it.plus(i.tid().cV()));
        });
        return this.value(this.value());
    }

    @Override
    public <O extends Obj> O remove() {
        while (this.map.keySet().iterator().hasNext()) {
            final O key = (O) this.map.keySet().iterator().next();
            final cInt value = this.map.remove(key);
            if (null == value)
                return key;
            else if (!value.isZero()) return (O) key.tid(key.tid().c(value.toString()));
        }
        return null;
    }


    public <O extends Obj> O remove(final fURI selector) {
        while (this.map.keySet().iterator().hasNext()) {
            final O key = (O) this.map.keySet().iterator().next();
            final cInt value = this.map.get(key);
            if (null == value) {
                this.map.remove(key);
                return key;
            } else if (value.within(selector.cV())) {
                final cInt newValue = value.minus(selector.cV());
                if (newValue.isZeroOrNeg())
                    this.map.remove(key);
                else
                    this.map.put(key, newValue);
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
        return this.map.entrySet().stream().filter(kv -> !kv.getValue().isZero()).map(kv -> kv.getValue().isOne() ? kv.getKey() : kv.getKey().tid(kv.getKey().tid().c(kv.getValue().toString()))).toList();
    }

    @Override
    public fURI tid() {
        try {
            return this.map.entrySet().stream().filter(kv -> !kv.getValue().isZero()).map(kv -> kv.getKey().tid().c(kv.getValue().toString())).reduce(fURI::plus).orElse(fURI.NONE.zero());
        } catch (final Exception e) {
            return this.map.entrySet().stream().filter(kv -> !kv.getValue().isZero()).map(kv -> kv.getKey().tid().c(kv.getValue().toString())).reduce(fURI::commonRoot).orElse(fURI.NONE.zero());
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
        return MType.of(this, this.tid());
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
        return Objects.hash(this.map, this.vid);
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof MObjs && Objects.equals(this.vid, ((MObjs) other).vid) && Objects.equals(this.map, ((MObjs) other).map);
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
