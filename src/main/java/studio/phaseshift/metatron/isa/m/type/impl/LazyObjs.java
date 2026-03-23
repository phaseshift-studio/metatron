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

package studio.phaseshift.metatron.isa.m.type.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Objs;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.isa.m.mInstSet.ALL_STAR;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;

/**
 * LazyObjs: A lazy, replayable implementation of Objs that caches materialized objects.
 * <p>
 * Key features:
 * 1. Lazy materialization: Only materializes objects when needed
 * 2. Replayable: Can call stream() multiple times without re-creating objects
 * 3. Incremental caching: Materializes objects on-demand and caches them
 * 4. Bulk optimization: Applies bulk deduplication only when fully materialized
 * <p>
 * Performance benefits:
 * - Avoids upfront materialization cost in objs(Iterator)
 * - Allows short-circuiting operations (e.g., .findFirst()) to avoid full materialization
 * - Caches materialized objects for replay without re-creation
 * - Reduces memory pressure by deferring allocation
 */
public class LazyObjs implements Objs {

    private static final Logger log = LoggerFactory.getLogger(LazyObjs.class);
    private Iterator<Obj> source;
    private LinkedHashMap<Obj, cInt> cache;
    private List<Obj> jvm;
    private cInt objsC = null;
    private fURI tid;
    private fURI vid;

    private LazyObjs(final Iterator<Obj> source, final fURI tid, final fURI vid) {
        this.source = source;
        this.cache = null;
        this.jvm = null;
        this.tid = tid;
        this.vid = vid;
    }

    private void initCache(final Obj first) {
        this.cache = new LinkedHashMap<>();
        if (!first.isNoObj())
            this.cache.put(first.c(cInt.ONE()), first.c());
    }

    public static Obj lazyObjs(final List<Obj> source) {
        if (source.isEmpty())
            return noobj();
        else if (source.size() == 1)
            return source.getFirst();
        return lazyObjs(source.iterator(), ALL_STAR, null);
    }


    public static Obj lazyObjs(final Iterator<Obj> source) {
        return lazyObjs(source, ALL_STAR, null);
    }

    public static Obj lazyObjs(final Iterator<Obj> source, final fURI tid, final fURI vid) {
        // We need to peek at the first two elements to determine what to return
        // This maintains the contract that objs() returns the actual object type when possible
        if (!source.hasNext())
            return noobj();

        final Obj first = source.next();
        if (!source.hasNext())
            return first;
        final LazyObjs lazy = new LazyObjs(source, tid, vid);
        lazy.initCache(first);
        return lazy;
    }

    @Override
    public boolean isNoObj() {
        final boolean no = (null == this.cache || this.cache.isEmpty()) && (null == this.jvm || this.jvm.isEmpty()) && !this.source.hasNext();
        if (no)
            this.tid = this.tid().zero();
        return no;

    }

    @Override
    public Obj resolve(final Obj obj) {
        this.drainToList();
        this.jvm.forEach(o -> o.resolve(obj));
        return this;
    }

    @Override
    public Obj append(final Obj obj) {
        if (obj.isNoObj())
            return this;
        if (null == this.cache)
            this.initCache(obj);
        else
            this.cache.merge(obj.c(cInt.ONE()), obj.c(), cInt::plus);
        return this;
    }

    @Override
    public cInt uniqueC() {
        this.drainToCache();
        return cInt.of(cache.size());
    }

    @Override
    public Iterable<Obj> jvm() {
        this.drainToList();
        return this.jvm;
    }

    @Override
    public cInt c() {
        this.drainToCache();
        final cInt total = this.cache.values().stream().reduce(cInt.ZERO(), cInt::plus);
        return null == this.objsC ? total : total.mult(this.objsC);
    }

    @Override
    public Obj c(final Function<cInt, cInt> func) {
        this.objsC = func.apply(null == this.objsC ? cInt.ONE() : this.objsC);
        return this;
    }

    private Obj multObjsC(final Obj obj) {
        return null == this.objsC ? obj : obj.c(this.objsC);
    }

    @Override
    public Obj take() {
        if (null != this.jvm && !this.jvm.isEmpty())
            return this.multObjsC(this.jvm.removeFirst());
        else if (!cache.isEmpty()) {
            final Map.Entry<Obj, cInt> entry = cache.sequencedEntrySet().removeFirst();
            return this.multObjsC(entry.getKey().c(entry.getValue()));
        } else if (source.hasNext())
            return this.multObjsC(source.next());
        else {
            this.tid = this.tid().zero();
            return noobj();
        }
    }

    @Override
    public Tuple.Pair<Obj, Obj> take(final cInt c) {
        this.drainToCache();
        cInt takenC = cInt.ZERO();
        final List<Obj> taken = new ArrayList<>();
        final Iterator<Map.Entry<Obj, cInt>> seq = this.cache.sequencedEntrySet().iterator();
        while (takenC.lt(c)) {
            final Tuple.Pair<Obj, Obj> split;
            if (this.jvm != null && !this.jvm.isEmpty())
                split = multObjsC(this.jvm.removeFirst()).take(c.minus(takenC));
            else {
                final Map.Entry<Obj, cInt> entry = seq.next();
                final Obj obj = entry.getKey().c(entry.getValue());
                this.cache.remove(entry.getKey());
                split = multObjsC(obj).take(c.minus(takenC));
            }
            takenC = takenC.plus(split.get0().c());
            taken.add(split.get0());
            if (!split.get1().isNoObj()) {
                this.cache.merge(split.get1().c(cInt.ONE()), this.objsC == null ? split.get1().c() : split.get1().c().div(this.objsC), cInt::plus);
            }
        }
        final int size = taken.size();
        if (size == 0)
            return Tuple.Pair.with(noobj(), this);
        if (size == 1)
            return Tuple.Pair.with(taken.getFirst(), this);
        return Tuple.Pair.with(objs(taken), this);
    }

    @Override
    public Stream<Obj> stream() {
        return java.util.stream.StreamSupport.stream(Spliterators.spliteratorUnknownSize(this.iterator(), Spliterator.ORDERED), false);
    }

    @Override
    public Iterator<Obj> iterator() {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return (jvm != null && !jvm.isEmpty()) || !cache.isEmpty() || source.hasNext();
            }

            @Override
            public Obj next() {
                if (jvm != null && !jvm.isEmpty())
                    return multObjsC(jvm.removeFirst());
                if (!cache.isEmpty()) {
                    final Map.Entry<Obj, cInt> kv = cache.sequencedEntrySet().removeFirst();
                    return multObjsC(kv.getKey().c(kv.getValue()));
                } else {
                    return multObjsC(source.next());
                }
            }
        };
    }

    @Override
    public fURI tid() {
       //this.drainToList();
      //  final cInt c = this.objsC == null ? this.c() : this.objsC.mult(this.c());
     //   this.tid = this.tid.c(c.toString());
        return this.tid;
/*        if (!cache.isEmpty())
            return ALL.some();
        else if (this.source.hasNext())
            return ALL.some();
        else return ALL.zero();*/
    }

    @Override
    public Obj vid(final fURI vid) {
        this.vid = vid;
        return this;
    }

    @Override
    public fURI vid() {
        return this.vid;
    }

    @Override
    public Obj clone(final Object jvm, final fURI tid, final fURI vid) {
        try {
            this.drainToList();
            final LazyObjs clone = (LazyObjs) super.clone();
            clone.source = IteratorUtil.of();
            clone.cache = new LinkedHashMap<>();
            clone.jvm = (List<Obj>) jvm;
            clone.objsC = this.objsC;
            clone.tid = tid;
            clone.vid = vid;
            return clone;
        } catch (final CloneNotSupportedException e) {
            throw MTronException.of(e);
        }
    }

    private void drainToCache() {
        while (this.source.hasNext()) {
            final Obj obj = this.source.next();
            if (!obj.isNoObj())
                this.cache.merge(obj.c(cInt.ONE()), obj.c(), cInt::plus);
        }
    }

    private void drainToList() {
        this.drainToCache();
        final Iterator<Map.Entry<Obj, cInt>> seq = this.cache.sequencedEntrySet().iterator();
        if (null == this.jvm)
            this.jvm = new ArrayList<>();
        while (seq.hasNext()) {
            final Map.Entry<Obj, cInt> entry = seq.next();
            if (!entry.getValue().isZero())
                this.jvm.add(entry.getKey().c(entry.getValue()));
            seq.remove();
        }
    }


    @Override
    public String toString() {
        this.drainToList();
        return Obj.Helper.objToString(this);

    }

    @Override
    public int hashCode() {
        this.drainToList();
        return Obj.Helper.objHashCode(this);
    }

    @Override
    public boolean equals(final Object other) {
        this.drainToList();
        return Obj.Helper.objEquals(this, other);
    }

    @Override
    public Objs clone() {
        this.drainToList();
        final Objs clone = new LazyObjs(new ArrayList<>(this.jvm).iterator(), this.tid, this.vid);
        return clone;
    }

    @Override
    public Objs self(final Object jvm, final fURI tid, final fURI vid) {
        this.cache.clear();
        this.cache = null;
        this.source = IteratorUtil.of();
        this.jvm = (List<Obj>) jvm;
        this.tid = tid;
        this.vid = vid;
        return this;
    }
}
