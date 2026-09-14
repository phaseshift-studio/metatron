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

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * a cached view over the type registry.
 * <p>
 * a type's parent chain (and every {@code MType.T(...)} resolution that walks it) is backed
 * by a space read on each hop. the graph memoizes those resolutions so that a
 * type-hierarchy walk is a map lookup instead of a {@code Router} read, while staying
 * sound for a dynamic registry:
 * <ul>
 *   <li>keys are the <em>raw</em> arguments as passed (value-equality: fURI / Call),
 *       so equal arguments resolve to the same entry regardless of call site</li>
 *   <li>router writes to a watched type path bump {@link #generation}; entries stamped
 *       with an older generation are dropped, and an entry computed concurrently with a
 *       write is never cached</li>
 *   <li>while the VM is booting the registry is still being assembled -- never memoize
 *       in that window</li>
 *   <li>a new router instance (re-boot / reload) rebinds the graph and clears it, so a
 *       second {@code BootLoader.load()} never sees the first registry</li>
 * </ul>
 * <p>
 * the cache is a <em>projection</em>: it holds references to resolved {@link Type}s, never
 * back-pointers inside them, so serialization, cloning, and save semantics of type objects
 * are untouched.
 * <p>
 * note: the hot path must stay class-init-safe -- the resolver supplier runs inside
 * {@code <clinit>} chains (static type constants), so this class must never touch
 * obj serialization (toString) or other half-initialized statics on that path.
 */
public final class TypeGraph {

    private static volatile TypeGraph THE;

    /**
     * a canonical memo key over the <em>raw</em> resolution arguments (value-equality
     * types: fURI and Obj both define value-based equals/hashCode).
     */
    public record Key(fURI tid, fURI vid, Call predicate, Call constructor) {
    }

    private static final class Entry {
        final Type type;
        final long gen;

        Entry(final Type type, final long gen) {
            this.type = type;
            this.gen = gen;
        }
    }

    private final ConcurrentHashMap<Key, Entry> resolved = new ConcurrentHashMap<>();
    private final Set<fURI> watched = ConcurrentHashMap.newKeySet();
    private final Set<fURI> watchedBase = ConcurrentHashMap.newKeySet();
    private final AtomicLong generation = new AtomicLong(1);
    private volatile Object builtFor;

    private TypeGraph() {
    }

    public static TypeGraph global() {
        final TypeGraph t = THE;
        if (null != t)
            return t;
        synchronized (TypeGraph.class) {
            if (null == THE)
                THE = new TypeGraph();
            return THE;
        }
    }

    /**
     * a memoized type factory over raw args. on a generation-current hit the
     * cached type is returned; otherwise {@code resolve} runs and its result is
     * cached under {@code key}.
     * <p>
     * generation-stamped: if an invalidation lands while {@code resolve} is running,
     * the (possibly stale) result is returned to this caller but never cached.
     */
    public Type memo(final Key key, final Supplier<Type> resolve) {
        this.rebind();
        if (BootLoader.BOOTING || (null == key.tid() && null == key.vid()))
            return resolve.get();
        final Entry hit = this.resolved.get(key);
        if (null != hit && hit.gen == this.generation.get())
            return hit.type;
        final long gen = this.generation.get();
        final Type type = resolve.get();
        if (null != type && gen == this.generation.get()) {
            this.resolved.put(key, new Entry(type, gen));
            this.watch(key);
        }
        return type;
    }

    /**
     * the invalidation hook, called by the router on every space write.
     * <p>
     * a write aliases a watched type path in full fURI form, by base path --
     * registration writes commonly arrive raw-relative or scheme-qualified,
     * and the base path is the one component that is stable across those
     * spellings -- or, for a pattern (wildcard) write, through the router's
     * redirect onto a watched big path.
     */
    public void onWrite(final fURI vid) {
        this.rebind();
        if (null == vid || BootLoader.BOOTING)
            return;
        if (this.watched.contains(vid)
                || this.watchedBase.contains(vid.basePath())
                || (vid.hasPattern() && (this.watched.contains(vid.big()) || this.watchedBase.contains(vid.big().basePath())))) {
            this.generation.incrementAndGet();
            this.resolved.clear();
            this.watched.clear();
            this.watchedBase.clear();
        }
    }

    public int size() {
        return this.resolved.size();
    }

    public long generation() {
        return this.generation.get();
    }

    public void clear() {
        this.generation.incrementAndGet();
        this.resolved.clear();
        this.watched.clear();
        this.watchedBase.clear();
    }

    /**
     * watch the key's type paths -- those are the paths a later write would alias
     * the cached value through -- plus their base paths so raw-relative and
     * scheme-qualified spellings of the same path match.
     */
    private void watch(final Key key) {
        this.addWatched(key.tid());
        this.addWatched(key.vid());
    }

    private void addWatched(final fURI f) {
        if (null == f)
            return;
        this.watched.add(f);
        if (null != f.basePath())
            this.watchedBase.add(f.basePath());
    }

    /** a new router instance is a new type registry: drop and rebind */
    private synchronized void rebind() {
        final Object router = BootLoader.ROUTER;
        if (router != this.builtFor) {
            this.builtFor = router;
            this.generation.incrementAndGet();
            this.resolved.clear();
            this.watched.clear();
            this.watchedBase.clear();
        }
    }
}
