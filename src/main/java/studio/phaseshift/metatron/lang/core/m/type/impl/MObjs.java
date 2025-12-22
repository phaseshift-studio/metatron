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

package studio.phaseshift.metatron.lang.core.m.type.impl;

import studio.phaseshift.metatron.furi.C;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.obj.NoObj;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Objs;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class MObjs implements Objs {

    private Map<Obj, cInt> cstream; // <obj{1}, coeff{+}>
    private fURI vid;
    
    public MObjs(final Iterable<Obj> jvm) {
        this(jvm, null);
    }

    public MObjs(final Iterable<Obj> jvm, final fURI vid) {
        this(flattenToMap(Collections.synchronizedMap(new LinkedHashMap<>()), jvm), vid);
    }

    protected MObjs(final Map<Obj, cInt> jvmAlternative, final fURI vid) {
        this.vid = vid;
        this.cstream = jvmAlternative instanceof LinkedHashMap<Obj, cInt> ? (LinkedHashMap<Obj, cInt>) jvmAlternative : Collections.synchronizedMap(new LinkedHashMap<>(jvmAlternative));
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
            return Optional.of(NoObj.noobj());
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
        return objs.length == 0 ? NoObj.noobj() : objs(List.of(objs));
    }

    public static Obj objs(final Iterable<Obj> objs) {
        final Map<Obj, cInt> map = Collections.synchronizedMap(new LinkedHashMap<>());
        return tryToShrink(flattenToMap(map, objs)).orElseGet(() -> new MObjs(map, fURI.fnull));
    }

    public static Obj objs(final Stream<Obj> objs) {
        return MObjs.objs(objs.toList());
    }

    @Override
    public Obj resolve(final Obj obj) {
        return objs(flatten(this).map(o -> o.resolve(obj)));
    }

    @Override
    public Obj append(final Obj obj) {
        if (obj.isNoObj()) return this;
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
        final Map.Entry<Obj, cInt> entry = ((LinkedHashMap<Obj, cInt>) this.cstream).pollFirstEntry();
        return null == entry ? null : null == entry.getValue() ? entry.getKey() : entry.getKey().c(entry.getValue());
    }
    
    @Override
    public Tuple.Pair<Obj, Obj> take(final cInt c) {
        final cInt currentC = this.c();
        if (c.isMaybeSome() || c.equals(currentC))
            return Tuple.Pair.with(this, NoObj.noobj());
        if (c.isZero())
            return Tuple.Pair.with(NoObj.noobj(), this);
        final List<Obj> retrieved = new ArrayList<>();
        final List<Obj> remaining = new ArrayList<>();
        cInt total = cInt.ZERO();
        for (Map.Entry<Obj, cInt> entry : this.cstream.entrySet()) {
            final cInt toTake = c.minus(total);
            if (toTake.gt(c.zero())) {
                if (entry.getValue().lte(toTake)) {
                    retrieved.add(entry.getKey().c(entry.getValue()));
                    total = total.plus(entry.getValue());
                } else {
                    remaining.add(entry.getKey().c(entry.getValue().minus(toTake)));
                    retrieved.add(entry.getKey().c(toTake));
                    total = total.plus(toTake);
                }
            } else {
                remaining.add(entry.getKey().c(entry.getValue()));
            }
        }
        return Tuple.Pair.with(objs(retrieved), objs(remaining));
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
        return (Objs) this.clone(this.jvm(), this.tid(), this.vid);
    }

    @Override
    public Objs self(final Object jvm, final fURI tid, final fURI vid) {
        this.cstream = flattenToMap(new LinkedHashMap<>(), (Iterable<Obj>) jvm);
        this.vid = vid;
        return this;
    }
}
