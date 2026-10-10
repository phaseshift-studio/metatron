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

package studio.phaseshift.metatron.furi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

/**
 * The global String -> {@link fURI} cache.
 * <p>
 * Parsing a uri is the single most expensive thing {@link fURI} does — a nine-group regex match, a {@code Matcher}, a
 * {@code LinkedHashMap} and a {@code cInt} per call (measured at ~700-1000ns on the corpus in fURIPerformanceTest) —
 * while the VM parses the SAME addresses over and over: every type clone, every memSpace resolution, every
 * {@code mParser.f()}. Caching collapses a repeat to a {@code ConcurrentHashMap.get} (single-digit ns) and, because
 * the cache hands back one instance per key, makes that instance's own lazy derivations ({@code resolve},
 * {@code hashCode}, {@code toString}, {@code basePath}) shared instead of rebuilt per copy.
 * <p>
 * <b>It lives here rather than inside {@code fURI.Singleton} because it is a policy, not a grammar rule:</b> the
 * interface owns how a uri is spelled, this owns how many of them are remembered and what happens when it runs out.
 * {@link fURI.Singleton#of(String)} is its only caller.
 * <p>
 * <b>Bounded, and bounded as a policy rather than just a number.</b> An address can carry data
 * ({@code /usr/u/48213}), so an unbounded cache is a leak. At production volume (millions of uris, most of them
 * one-shot) what the bound does when it bites matters as much as the number itself:
 * <ul>
 *   <li>The cache is TWO generations. {@code live} is the one being filled, {@code retired} the one it replaced, and
 *       a lookup consults both — so a hot address keeps answering for up to two generations instead of dying at a
 *       single boundary.</li>
 *   <li>When {@code live} reaches half the budget, ONE thread swaps in a fresh generation and retires the old one (a
 *       CAS, so the losers of the race simply insert into whatever is current). Rotation is O(1). The {@code clear()}
 *       it replaced was O(capacity) and — the reason it had to go — could be performed by EVERY thread that observed
 *       a full cache, so under load the cache could repeatedly wipe itself.</li>
 *   <li>{@code live} + {@code retired} never exceed {@code -Dmetatron.furi.intern.max} (default 65536 entries, on the
 *       order of 20-27MB at ~410 bytes/entry), so the ceiling is real even though eviction is generational rather
 *       than per-entry. The budget is a ceiling, not a reservation: memory tracks the distinct addresses actually
 *       seen.</li>
 * </ul>
 * <p>
 * Keys are the ANGLE-STRIPPED form, so {@code <a/b>} and {@code a/b} share one instance. Misses are resolved with
 * {@code get}/{@code putIfAbsent} rather than {@code computeIfAbsent}: a mapping function that re-entered the cache
 * would trip {@code ConcurrentHashMap}'s recursive-update guard, and the classic (illegal) way to write this is the
 * one that must not be written. Caching is safe because every {@link fURI} species is immutable in observable state.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class fURICache {

    private fURICache() {
    }

    /** The ceiling on pooled uris ({@code -Dmetatron.furi.intern.max}). */
    private static final int MAX = configuredMax();

    /** The cache's bound (`-Dmetatron.furi.intern.max`); a malformed override must not take the initializer down. */
    private static int configuredMax() {
        try {
            return Math.max(2, Integer.parseInt(System.getProperty("metatron.furi.intern.max", String.valueOf(1 << 16))));
        } catch (final NumberFormatException e) {
            return 1 << 16;
        }
    }

    /** Rotate at half the budget, so live + retired together stay within it. */
    private static final int GENERATION = Math.max(1, MAX / 2);

    /** The two generations, swapped as a unit so a reader never sees a half-installed pair. */
    private static final class Pool {
        private final Map<String, fURI> live;
        private final Map<String, fURI> retired;

        private Pool(final Map<String, fURI> live, final Map<String, fURI> retired) {
            this.live = live;
            this.retired = retired;
        }
    }

    private static final AtomicReference<Pool> POOL = new AtomicReference<>(new Pool(new ConcurrentHashMap<>(1 << 12), null));
    private static final LongAdder HITS = new LongAdder();
    private static final LongAdder MISSES = new LongAdder();
    private static final LongAdder ROTATIONS = new LongAdder();

    /**
     * The cached uri for {@code key}, parsing and pooling one via {@code parser} on a miss. The cache — not the
     * caller — owns both the eviction policy and the hit/miss accounting, so a caller cannot forget one or skew the
     * other; {@code parser} is a method reference on the miss path, so this stays allocation-free where it matters.
     */
    public static fURI cached(final String key, final Function<String, fURI> parser) {
        final fURI hit = lookup(key);
        if (null != hit) {
            HITS.increment();
            return hit;
        }
        MISSES.increment();
        final fURI parsed = parser.apply(key);
        insert(key, parsed);
        return parsed;
    }

    /** A cached uri for the key, from either generation, or null. */
    private static fURI lookup(final String key) {
        final Pool pool = POOL.get();
        final fURI live = pool.live.get(key);
        if (null != live)
            return live;
        return null == pool.retired ? null : pool.retired.get(key);
    }

    /** Pool a freshly parsed uri, rotating the generation first if it is due. */
    private static void insert(final String key, final fURI parsed) {
        final Pool observed = POOL.get();
        if (observed.live.size() >= GENERATION)
            rotate(observed);
        POOL.get().live.putIfAbsent(key, parsed);
    }

    /** Retire the live generation once it is due; the CAS guarantees exactly one thread performs the swap. */
    private static void rotate(final Pool observed) {
        if (observed.live.size() < GENERATION)
            return;
        if (POOL.compareAndSet(observed, new Pool(new ConcurrentHashMap<>(1 << 12), observed.live)))
            ROTATIONS.increment();
    }

    // ======================== observability ========================

    /**
     * The number of uris currently cached — the live generation plus the retired one. Never exceeds {@link #max()};
     * each generation carries at most half the budget by construction.
     */
    public static int size() {
        final Pool pool = POOL.get();
        return pool.live.size() + (null == pool.retired ? 0 : pool.retired.size());
    }

    /** The configured ceiling on cached uris ({@code -Dmetatron.furi.intern.max}, default 65536). */
    public static int max() {
        return MAX;
    }

    /** How many generations have been retired — zero on a vocabulary that fits the budget. */
    public static long rotations() {
        return ROTATIONS.sum();
    }

    /** The number of {@link #cached} calls answered from the cache. */
    public static long hits() {
        return HITS.sum();
    }

    /** The number of {@link #cached} calls that had to parse. */
    public static long misses() {
        return MISSES.sum();
    }

    /** Drop every cached uri (they are re-parsed on next use); the tallies are left intact. */
    public static void clear() {
        POOL.set(new Pool(new ConcurrentHashMap<>(1 << 12), null));
    }
}
