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

import studio.phaseshift.metatron.furi.C;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Objs;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.isa.m.mInstSet.ALL_STAR;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;

/**
 * LazyObjs: A lazy, replayable implementation of Objs that caches materialized objects.
 *
 * Key features:
 * 1. Lazy materialization: Only materializes objects when needed
 * 2. Replayable: Can call stream() multiple times without re-creating objects
 * 3. Incremental caching: Materializes objects on-demand and caches them
 * 4. Bulk optimization: Applies bulk deduplication only when fully materialized
 *
 * Performance benefits:
 * - Avoids upfront materialization cost in objs(Iterator)
 * - Allows short-circuiting operations (e.g., .findFirst()) to avoid full materialization
 * - Caches materialized objects for replay without re-creation
 * - Reduces memory pressure by deferring allocation
 */
public class LazyObjs implements Objs {

    private final Iterator<Obj> source;
    private final List<Obj> cache;
    private boolean fullyMaterialized;
    private cInt cachedC;
    private fURI tid;
    private fURI vid;

    private LazyObjs(final Iterator<Obj> source, final fURI tid, final fURI vid) {
        this.source = source;
        this.cache = new ArrayList<>();
        this.fullyMaterialized = false;
        this.cachedC = null;
        this.tid = tid;
        this.vid = vid;
    }

    /**
     * Create a lazy Objs from an iterator.
     * The iterator will be consumed incrementally as needed.
     *
     * This method follows the same contract as MObjs.objs(Iterator):
     * - Returns noobj() if empty
     * - Returns the single object if only one element
     * - Returns LazyObjs only if multiple elements
     */
    public static Obj lazyObjs(final Iterator<Obj> source) {
        return lazyObjs(source, ALL_STAR, null);
    }

    public static Obj lazyObjs(final Iterator<Obj> source, final fURI tid, final fURI vid) {
        // We need to peek at the first two elements to determine what to return
        // This maintains the contract that objs() returns the actual object type when possible
        if (!source.hasNext())
            return noobj();

        final Obj first = source.next();
        if (first.isNoObj()) {
            // Skip noobj and try next
            return lazyObjs(source, tid, vid);
        }

        if (!source.hasNext())
            return first;

        // Multiple elements - create LazyObjs with first element already cached
        return new LazyObjs(source, tid, vid, first);
    }

    /**
     * Private constructor that starts with one element already cached.
     */
    private LazyObjs(final Iterator<Obj> source, final fURI tid, final fURI vid, final Obj firstElement) {
        this.source = source;
        this.cache = new ArrayList<>();
        this.cache.add(firstElement);
        this.fullyMaterialized = false;
        this.cachedC = null;
        this.tid = tid;
        this.vid = vid;
    }

    /**
     * Materialize the next object from the source iterator and cache it.
     * Returns true if an object was materialized, false if source is exhausted.
     */
    private boolean materializeNext() {
        if (fullyMaterialized)
            return false;

        if (source.hasNext()) {
            final Obj obj = source.next();
            if (!obj.isNoObj()) {
                cache.add(obj);
                if (cachedC != null) {
                    cachedC = cachedC.plus(obj.c());
                }
                return true;
            }
            return materializeNext(); // Skip noobj and try next
        } else {
            fullyMaterialized = true;
            return false;
        }
    }

    /**
     * Ensure all objects are materialized from the source iterator.
     */
    private void materializeAll() {
        if (fullyMaterialized)
            return;

        while (source.hasNext()) {
            final Obj obj = source.next();
            if (!obj.isNoObj()) {
                cache.add(obj);
            }
        }
        fullyMaterialized = true;
        cachedC = null; // Invalidate cached coefficient
    }

    /**
     * Convert to eager MObjs with bulk optimization.
     * This is called when operations require full materialization.
     */
    private Obj toEager() {
        materializeAll();
        if (cache.isEmpty())
            return noobj();
        if (cache.size() == 1)
            return cache.get(0);
        return objs(cache, tid, vid);
    }

    @Override
    public boolean isNoObj() {
        // Try to materialize at least one object to check if empty
        if (cache.isEmpty() && !fullyMaterialized) {
            materializeNext();
        }
        return cache.isEmpty();
    }

    @Override
    public Obj resolve(final Obj obj) {
        materializeAll();
        return objs(cache.stream().map(o -> o.resolve(obj)).iterator());
    }

    @Override
    public Obj append(final Obj obj) {
        // Appending forces materialization to maintain order
        materializeAll();
        if (obj.isNoObj())
            return this.toEager();

        if (obj instanceof Objs) {
            IteratorUtil.fill(((Iterable<Obj>) obj.jvm()).iterator(), cache);
        } else {
            cache.add(obj);
        }
        cachedC = null; // Invalidate cached coefficient
        return this.toEager();
    }

    @Override
    public cInt uniqueC() {
        materializeAll();
        return cInt.of(cache.size());
    }

    @Override
    public Iterable<Obj> jvm() {
        materializeAll();
        return cache;
    }

    @Override
    public cInt c() {
        if (cachedC != null)
            return cachedC;

        materializeAll();
        cachedC = cInt.ZERO();
        for (final Obj o : cache) {
            cachedC = cachedC.plus(o.c());
        }
        return cachedC;
    }

    @Override
    public Obj c(final Function<cInt, cInt> func) {
        materializeAll();
        return objs(cache.stream().map(obj -> obj.c(func)).iterator());
    }

    @Override
    public Obj take() {
        if (cache.isEmpty() && !materializeNext())
            return null;

        final Obj result = cache.remove(0);
        cachedC = null; // Invalidate cached coefficient
        return result;
    }

    @Override
    public Tuple.Pair<Obj, Obj> take(final cInt c) {
        // Taking requires full materialization to handle coefficients correctly
        return toEager().asObjs().take(c);
    }

    @Override
    public Stream<Obj> stream() {
        // Return a stream that materializes incrementally from the replayable iterator
        return java.util.stream.StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED),
                false
        );
    }

    @Override
    public Iterator<Obj> iterator() {
        return new Iterator<Obj>() {
            private int cacheIndex = 0;

            @Override
            public boolean hasNext() {
                return cacheIndex < cache.size() || (!fullyMaterialized && source.hasNext());
            }

            @Override
            public Obj next() {
                if (cacheIndex < cache.size()) {
                    return cache.get(cacheIndex++);
                } else if (materializeNext()) {
                    return cache.get(cacheIndex++);
                } else {
                    throw new NoSuchElementException();
                }
            }
        };
    }

    @Override
    public fURI tid() {
        materializeAll();
        try {
            return cache.stream()
                    .map(Obj::tid)
                    .reduce(fURI::plus)
                    .orElse(fURI.NOOBJ);
        } catch (final Exception e) {
            return cache.stream()
                    .map(Obj::tid)
                    .reduce(fURI::commonRoot)
                    .orElse(fURI.NOOBJ);
        }
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
        materializeAll();
        return objs(cache).vid(vid);
    }

    @Override
    public String toString() {
        // Use the same serialization as MObjs for consistent formatting
        // This ensures proper coefficient normalization and bulk deduplication
        materializeAll();
        return toEager().toString();
    }

    @Override
    public int hashCode() {
        materializeAll();
        return Objects.hash(cache.size(), vid);
    }

    @Override
    public boolean equals(final Object other) {
        // Materialize this LazyObjs
        materializeAll();

        // Convert to eager MObjs for proper comparison
        final Obj a = toEager();

        // If other is also LazyObjs, materialize it first
        if (other instanceof LazyObjs) {
            ((LazyObjs) other).materializeAll();
            final Obj b = ((LazyObjs) other).toEager();
            return a.equals(b);
        }

        // Otherwise use standard comparison
        return a.equals(other);
    }

    @Override
    public Objs clone() {
        materializeAll();
        return (Objs) objs(new ArrayList<>(cache), tid, vid);
    }

    @Override
    public Objs self(final Object jvm, final fURI tid, final fURI vid) {
        this.cache.clear();
        this.cache.addAll((List<Obj>) jvm);
        this.tid = tid;
        this.vid = vid;
        this.fullyMaterialized = true;
        this.cachedC = null;
        return this;
    }
}
