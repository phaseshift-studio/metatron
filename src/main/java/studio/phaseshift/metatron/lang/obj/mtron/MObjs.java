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

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class MObjs implements Objs {

    private fURI vid;
    private final Map<Obj, cInt> cstream;

    public MObjs(final Iterable<Obj> objs) {
        this(objs, null);
    }

    public MObjs(final Iterable<Obj> objs, final fURI vid) {
        this.vid = vid;
        this.cstream = flattenToMap(new LinkedHashMap<>(), objs);
    }

    private MObjs(final Map<Obj, cInt> cstream, final fURI vid) {
        this.vid = vid;
        this.cstream = cstream;
    }

    private static Stream<Obj> flatten(final Iterable<Obj> objs) {
        return IteratorUtil.stream(objs).flatMap(o -> o.isObjs() ? flatten(o.objsValue()) : Stream.of(o)).filter(o -> !o.isNoObj());
    }

    private static Map<Obj, cInt> flattenToMap(final Map<Obj, cInt> map, final Iterable<Obj> objs) {
        flatten(objs).forEach(o -> map.compute(o.c(C::one), (lng, it) -> null == it ? o.c() : it.plus(o.c())));
        return map;
    }

    private static Optional<Obj> tryToShrink(final Map<Obj, cInt> map) {
        if (map.isEmpty())
            return Optional.of(NoObj.single());
        if (1 == map.size())
            return Optional.of(map.entrySet().stream().map(kv -> kv.getKey().c(kv.getValue())).iterator().next());
        // if (map.keySet().stream().allMatch(Obj::isRing))
        //    return (Optional) map.entrySet().stream().map(a -> (Call) a.getKey().c(a.getValue())).reduce((a, b)->(Call)a.append(b));
        //return Optional.of(MInst.instB(SPLIT_TID, lst(map.entrySet().stream().map(a -> a.getKey().c(a.getValue())).toList())));
        return Optional.empty();
    }

 /*   @Override
    public Obj append(final Obj obj) {
        obj.stream().forEach(i -> this.cstream.compute(i.c(cInt::one), (lng, it) -> null == it ? i.tid().cV() : it.plus(i.tid().cV())));
        return this.cstream.size() == 1 ? this.cstream.entrySet().stream().map(kv -> kv.getKey().c(kv.getValue())).iterator().next() : ooobj(this.value());
    }*/

    @Override
    public Obj resolve(final Obj obj) {
        return objs(this.stream().map(o -> o.resolve(obj)));
    }

    private boolean isOnlyCall() {
        return !this.cstream.isEmpty() && this.cstream.keySet().stream().anyMatch(Obj::isCall);
    }

    @Override
    public Obj append(final Obj obj) {
        if (obj.isNoObj())
            return this;
        else if(obj.isCall() && this.isOnlyCall())
            return this.iterator().next().append(obj);
        return tryToShrink(flattenToMap(this.cstream, obj)).orElse(this);
    }

    @Override
    public cInt uniqueCount() {
        return cInt.of((long) this.cstream.size());
    }

    @Override
    public Iterable<Obj> value() {
        return this.cstream.entrySet().stream().map(kv -> kv.getValue().isOne() ? kv.getKey() : kv.getKey().c(kv.getValue())).toList();
    }

    @Override
    public cInt c() {
        return this.cstream.values().stream().reduce(cInt.ZERO(), cInt::plus);
    }

    @Override
    public Obj c(final Function<cInt, cInt> func) {
        this.cstream.keySet().forEach(obj -> this.cstream.computeIfPresent(obj, (k, v) -> func.apply(v)));
        return this;
    }

    @Override
    public Obj take() {
        while (this.cstream.keySet().iterator().hasNext()) {
            final Obj key = this.cstream.keySet().iterator().next();
            final cInt value = this.cstream.remove(key);
            if (null == value)
                return key;
            else if (!value.isZero()) return key.tid(key.tid().c(value.toString()));
        }
        return null;
    }


    public <O extends Obj> O take(final fURI selector) {
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

    public static Objs empty() {
        return new MObjs(new LinkedList<>());
    }


    public static Obj objs(final Obj... objs) {
        return objs(List.of(objs));
    }

    public static Obj objs(final Iterable<Obj> objs) {
        final Map<Obj, cInt> map = new LinkedHashMap<>();
        final Optional<Obj> ret = tryToShrink(flattenToMap(map, objs));
        return ret.orElseGet(() -> new MObjs(map, fURI.NULL));
    }

    public static Obj objs(final Stream<Obj> objs) {
        return MObjs.objs(objs.toList());
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
        return this;
        //return new MObjs(IteratorUtil.list(((Iterable) value)).iterator(), vid);
    }

    @Override
    public String toString() {
        return Helper.objToString(this);
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
            return objs((List) object);
        if (object instanceof Obj)
            return (Obj) object;
        throw MTronException.of("unknown object type: %s", object);

    }

    @Override
    public Objs clone() {
       /* try {
            return (Objs) super.clone();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }*/
        return this;
    }
}
