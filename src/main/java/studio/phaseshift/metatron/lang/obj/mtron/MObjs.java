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
import studio.phaseshift.metatron.lang.obj.Inst;
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Objs;
import studio.phaseshift.metatron.lang.obj.mtron.c.cInt;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class MObjs implements Objs {

    private final Map<Obj, cInt> cstream; // <obj{1}, coeff{+}>
    private fURI vid;

    public MObjs(final Iterable<Obj> jvm) {
        this(jvm, null);
    }

    public MObjs(final Iterable<Obj> jvm, final fURI vid) {
        this(flattenToMap(new LinkedHashMap<>(), jvm), vid);
    }

    private MObjs(final Map<Obj, cInt> jvmAlternative, final fURI vid) {
        this.vid = vid;
        this.cstream = jvmAlternative;
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

    public static Objs empty() {
        return new MObjs(new LinkedList<>()); // a noobj that can be appended
    }

    public static Obj objs(final Obj... objs) {
        return objs.length == 0 ? NoObj.single() : objs(List.of(objs));
    }

    public static Obj objs(final Iterable<Obj> objs) {
        final Map<Obj, cInt> map = new LinkedHashMap<>();
        return tryToShrink(flattenToMap(map, objs)).orElseGet(() -> new MObjs(map, fURI.NULL));
    }

    public static Obj objs(final Stream<Obj> objs) {
        return MObjs.objs(objs.toList());
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
    public Obj resolve(final Obj obj) {
        return objs(flatten(this).map(o -> o.resolve(obj)));
    }

    /*private boolean isOnlyCall() {
        return !this.cstream.isEmpty() && this.cstream.keySet().stream().anyMatch(Obj::isCall);
    }*/

    @Override
    public Obj append(final Obj obj) {
        if (obj.isNoObj())
            return this;
        //else if (obj.isCall() && this.isOnlyCall())
        //  return this.iterator().next().append(obj);
        return tryToShrink(flattenToMap(this.cstream, obj)).orElse(this);
    }

    @Override
    public cInt uniqueC() {
        return cInt.of((long) this.cstream.size());
    }

    @Override
    public Iterable<Obj> jvm() {
        return this.cstream.entrySet().stream().map(kv -> kv.getValue().isOne() ? kv.getKey() : kv.getKey().c(kv.getValue())).toList();
    }


  /*  public <O extends Obj> O take(final fURI selector) {
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
    }*/

    @Override
    public cInt c() {
        return this.cstream.values().stream().reduce(cInt.ZERO(), cInt::plus);
    }

    @Override
    public Obj c(final Function<cInt, cInt> func) {
        // throw MTronException.of("can not update the c of an objs programmatically: %s", this);
        this.cstream.keySet().forEach(obj -> this.cstream.computeIfPresent(obj, (k, v) -> func.apply(v)));
        return this;
    }

    @Override
    public Obj take() {
        for (final Obj key : this.cstream.keySet()) {
            final cInt value = this.cstream.remove(key);
            if (null == value)
                return key;
            else if (!value.isZero()) return key.c(value);
        }
        return null;
    }

   /* @Override
    public Tuple.Pair<Obj, Obj> headTailsSplit(final Function<Obj, Object> partitioner) {
        Obj head = null;
        final Map<Obj, cInt> tail = new LinkedHashMap<>();
        Object part = null;
        for (final Map.Entry<Obj, cInt> kv : this.cstream.entrySet()) {
            final Obj next = kv.getKey().c(kv.getValue());
            final Object nextPart = partitioner.apply(next);
            if (null == part)
                part = nextPart;
            if (Objects.equals(part, nextPart)) {
                head = (null == head) ? next : head.append(next);
            } else {
                tail.put(kv.getKey(), kv.getValue());
            }
        }
        final Obj headObj = null == head ? NoObj.single() : head;
        final Obj tailObj = tail.isEmpty() ? NoObj.single() : tail.size() == 1 ? tail.entrySet().stream().map(kv -> kv.getKey().c(kv.getValue())).iterator().next() : new MObjs(tail, this.vid);
        //Graphitty.log(this).info("SPLIT: %s ::  %s", headObj, tailObj);
        return Tuple.Pair.with(headObj, tailObj);
    }*/

    @Override
    public Tuple.Pair<Obj, Obj> take(final Inst inst) {
        if (this.isNoObj())
            return Tuple.Pair.with(NoObj.single(), NoObj.single());
        else if (inst.dom().c().most().isZero())
            return Tuple.Pair.with(NoObj.single(), this);
        /// ////////////////////////////////////
        if (inst.dom().c().isOne() || inst.dom().c().max() == null || this.c().lte(inst.dom().c().most()))
            return Tuple.Pair.with(this, NoObj.single());
        /// ////////////////////////////////////
        cInt total = cInt.ZERO();
        boolean done = false;
        final Map<Obj, cInt> taken = new HashMap<>();
        final Map<Obj, cInt> remaining = new HashMap<>();
        for (final Map.Entry<Obj, cInt> kv : this.cstream.entrySet()) {
            if (!done) {
                taken.put(kv.getKey(), kv.getValue());
                total = total.plus(kv.getValue());
                if (total.gte(inst.dom().c()))
                    done = true;
            } else {
                remaining.put(kv.getKey(), kv.getValue());
            }
        }
        final Obj takenObj = taken.isEmpty() ? NoObj.single() : taken.size() == 1 ? taken.entrySet().stream().map(kv -> kv.getKey().c(kv.getValue())).iterator().next() : new MObjs(taken, fURI.NULL);
        final Obj remainingObj = remaining.isEmpty() ? NoObj.single() : remaining.size() == 1 ? remaining.entrySet().stream().map(kv -> kv.getKey().c(kv.getValue())).iterator().next() : new MObjs(remaining, this.vid);
        return Tuple.Pair.with(takenObj, remainingObj);

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
        return new MObjs(this.jvm(), vid);
    }

    @Override
    public fURI vid() {
        return this.vid;
    }

    @Override
    public Obj clone(final Object jvm, final fURI tid, final fURI vid) {
        return objs(flatten((Iterable<Obj>) jvm));
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

    @Override
    public Objs clone() {
       /* try {
            return (Objs) super.clone();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }*/
        return (Objs) this.clone(this.jvm(), this.tid(), this.vid);
    }
}
