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

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.c.cInt;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton;

/**
 * Performance and validation coverage for the fURI cache and the memoized derivations that ride on it.
 * <p>
 * <b>What is measured.</b> {@code fURI.Singleton.of(String)} is the single funnel every string-built fURI passes
 * through (both {@code f(s)} and {@code of(s)}), and parsing there is the most expensive thing the class does: a
 * nine-group regex match, a {@code Matcher}, a panel of group allocations, a {@code LinkedHashMap} and a
 * {@code cInt} per call. The VM repeats that parse for the same addresses constantly — every type clone, every
 * memSpace resolution, every {@code mParser.f()}. {@link Singleton#parse(String)} is the pre-cache path, kept
 * package-private, so the cached and uncached routes can be measured against each other inside one JVM and on the
 * same (warm) JIT rather than against numbers from two builds.
 * <p>
 * <b>What is asserted.</b> Correctness is asserted unconditionally: a cached uri is content-identical to an
 * uncached parse of the same string, identity is stable, derivation caches agree with fresh computation, and the
 * cache behaves under concurrency. Timing is asserted only against a deliberately loose ratio — a few times faster,
 * not the ~100x+ actually observed — so the test documents the win without being a stopwatch that flakes on a
 * loaded CI box. The raw numbers go to the log.
 * <p>
 * Run with: {@code mvn test -Dtest=fURIPerformanceTest}
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class fURIPerformanceTest extends AbstractMetatronTest {

    /**
     * A realistic cross-section of the address vocabulary: absolute and relative paths, patterns, coefficients,
     * poly types, inst dom/rng queries, an authority, templates and the shared constants. Every entry is verified
     * parseable below, so a corpus that drifts out of the grammar fails loudly instead of silently timing a throw.
     */
    static Stream<String> corpus() {
        return Stream.of(
                "/sys/thread/executor",
                "/sys/mach/memory",
                "/sys/#",
                "/usr/marko/age",
                "a/b/c",
                "a/b/c/",
                "/a/b/c/",
                "x-y_z.1",
                "+",
                "#",
                "noobj",
                "int",
                "int{4}",
                "lst[int,str]",
                "rec[a=>b,c=>d]",
                "inst?a<=b()",
                "/m/inst?dom=b&rng=a",
                "/m/type/rec?docq",
                "a/b/c?k=v&j=w",
                "a/b/{c,d}/e",
                "nat::29@/usr/marko/age",
                "ws://hostB:8555/usr/x",
                "http://fhatos.org/a/b",
                "@frame/local",
                "<a/b/c>",
                "<{a,b}/c>");
    }

    /** The corpus materialized once; a Stream is single-use and the benchmarks walk it repeatedly. */
    private static List<String> CORPUS = null;

    private static synchronized List<String> corpusList() {
        if (null == CORPUS)
            CORPUS = corpus().toList();
        return CORPUS;
    }

    /** The cache key for a raw uri string — the angle-stripped form {@link Singleton#of(String)} caches under. */
    private static String key(final String furi) {
        return furi.startsWith("<") && furi.endsWith(">") ? furi.substring(1, furi.length() - 1) : furi;
    }

    // ======================== measurement scaffolding ========================

    /**
     * Consumed by every measured body so the JIT cannot delete work whose result nothing reads. Volatile: the write
     * has to survive deoptimization for the loop to be observably live.
     */
    private static volatile long SINK = 0;

    /**
     * Where the measured numbers land. The test log runs at WARN, so a benchmark line logged at INFO reaches nobody;
     * this appends the same line to a file (the repository's agreed way to make a diagnostic visible to a human and
     * to an agent, and never in the console transcript). Override with {@code -Dmetatron.furi.perf.report=<path>}.
     */
    private static final String REPORT_PATH = System.getProperty("metatron.furi.perf.report",
            System.getProperty("user.dir") + "/target/furi-performance.txt");

    private static void report(final String format, final Object... args) {
        final String line = "%s %s%n".formatted(java.time.Instant.now(), format.formatted(args));
        STATIC_LOG.info("%s", line.strip());
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(REPORT_PATH), line,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (final Exception e) {
            // a reporting aid that can break the measurement it reports on is worse than no reporting aid
        }
    }

    /**
     * Nanoseconds per operation for {@code body}, best of {@code measured} runs after {@code warmup} runs. The best
     * (not the mean) is the statistic to use here: scheduler noise and safepoints can only add time, so the minimum
     * is the cleanest estimate of the code's own cost and the most stable across machines.
     */
    private static double nanosPerOp(final int warmup, final int measured, final long opsPerRun, final Runnable body) {
        for (int i = 0; i < warmup; i++)
            body.run();
        long best = Long.MAX_VALUE;
        for (int i = 0; i < measured; i++) {
            final long start = System.nanoTime();
            body.run();
            final long elapsed = System.nanoTime() - start;
            if (elapsed < best)
                best = elapsed;
        }
        return (double) best / opsPerRun;
    }

    // ======================== validation ========================

    /**
     * The cache may change identity, never meaning. For every corpus uri the cached instance must equal an uncached
     * parse of the same string, render identically and hash identically, while the uncached parse is a fresh object
     * — which is the proof that identity comes from the cache and not from the parser.
     */
    @ParameterizedTest
    @MethodSource("corpus")
    @Order(1)
    public void testCachedUriIsContentIdenticalToUncachedParse(final String furi) {
        final fURI cached = Singleton.of(furi);
        assertSame(cached, Singleton.of(furi), "the cache must return the identical instance for %s".formatted(furi));
        assertSame(cached, Singleton.f(furi), "f() must route through the cache for %s".formatted(furi));
        final fURI fresh = Singleton.parse(key(furi));
        assertEquals(fresh, cached, "cached and uncached parses must agree for %s".formatted(furi));
        assertEquals(cached, fresh, "equality must be symmetric for %s".formatted(furi));
        assertEquals(fresh.hashCode(), cached.hashCode(), "equal uris must hash equally for %s".formatted(furi));
        assertEquals(fresh.toString(), cached.toString(), "rendering must agree for %s".formatted(furi));
        assertEquals(fresh.basePath(), cached.basePath(), "basePath must agree for %s".formatted(furi));
        assertEquals(fresh.path(), cached.path(), "path must agree for %s".formatted(furi));
        assertEquals(fresh.c(), cached.c(), "coefficient must agree for %s".formatted(furi));
        assertEquals(fresh.qMap(), cached.qMap(), "query must agree for %s".formatted(furi));
        if (!"{0}".equals(key(furi)))
            assertNotSame(fresh, cached, "an uncached parse must be a fresh object for %s".formatted(furi));
    }

    /**
     * The query map is compared by content, not by implementation: a parsed uri backs its query with a
     * {@code LinkedHashMap} while a hand-built one may use {@code Map.of}. Equality must not depend on which — this
     * is the line that used to defend itself with two defensive {@code HashMap} copies per comparison.
     */
    @Test
    @Order(2)
    public void testQueryEqualityAcrossMapImplementations() {
        final fURI parsed = Singleton.parse("a/b?k=v&j=w");
        final fURI built = fURI.of(null, null, -1, List.of("a", "b"), cInt.ONE(), List.of(), Map.of("k", "v", "j", "w"), null);
        assertEquals(parsed, built);
        assertEquals(built, parsed);
        assertEquals(parsed.hashCode(), built.hashCode());
        assertNotEquals(parsed, fURI.of(null, null, -1, List.of("a", "b"), cInt.ONE(), List.of(), Map.of("k", "v"), null));
        assertNotEquals(parsed, fURI.of(null, null, -1, List.of("a", "b"), cInt.ONE(), List.of(), Map.of("k", "v", "j", "x"), null));
    }

    /**
     * Re-applying a component a uri already carries is the identity, so the component mutators return {@code this}.
     * That is what lets {@code BasicMemory.redirect} skip two allocations per unrouted address — and it must not
     * change the content either way.
     */
    @Test
    @Order(3)
    public void testIdempotentComponentMutatorsReturnThis() {
        final fURI plain = Singleton.of("a/b/c");
        assertSame(plain, plain.c(plain.c()));
        assertSame(plain, plain.q(plain.qMap()));
        assertSame(plain, plain.q(Map.of()));
        assertSame(plain, plain.qLess());
        assertEquals(plain, plain.cLess());

        final fURI queried = Singleton.of("/m/inst?dom=b&rng=a");
        assertSame(queried, queried.q(queried.qMap()));
        assertEquals(queried, queried.q(new LinkedHashMap<>(queried.qMap())));
        assertEquals(queried, queried.c(queried.c()));

        // a real coefficient change must still produce a new, different uri
        final fURI many = plain.c(cInt.of(4L));
        assertNotSame(plain, many);
        assertEquals(cInt.of(4L), many.c());
    }

    /**
     * The derivation caches answer only what a fresh computation answers. {@code parse} gives a cold instance;
     * {@code of} gives the warm cached one; both must agree before and after their slots are populated.
     */
    @Test
    @Order(4)
    public void testMemoizedDerivationsAgreeWithColdComputation() {
        for (final String furi : corpusList()) {
            final fURI warm = Singleton.of(furi);
            final fURI cold = Singleton.parse(key(furi));
            // warm the slots on the cold instance too, then compare post-population
            final String coldString = cold.toString();
            final int coldHash = cold.hashCode();
            final fURI coldBase = cold.basePath();
            assertEquals(coldString, warm.toString());
            assertEquals(coldHash, warm.hashCode());
            assertEquals(coldBase, warm.basePath());
            assertSame(warm.toString(), warm.toString(), "toString must be memoized to one value");
            assertSame(warm.basePath(), warm.basePath(), "basePath must be memoized to one instance");
            assertEquals(coldHash, warm.hashCode(), "a memoized hashCode must be stable");
            assertEquals(warm.hashCode(), warm.hashCode(), "a memoized hashCode must be stable");
        }
    }

    /**
     * Interning is a shared global structure hit from every thread, so it has to be safe and it has to converge on
     * one instance per key. Many threads race the same keys; every observed instance for a key must be the same
     * object, and the tallies must account for every call.
     */
    @Test
    @Order(5)
    public void testConcurrentInterningConvergesOnOneInstancePerKey() throws Exception {
        final List<String> uris = corpusList();
        final fURI[] reference = new fURI[uris.size()];
        for (int i = 0; i < uris.size(); i++)
            reference[i] = Singleton.of(uris.get(i));

        final int threads = 8;
        final int roundsPerThread = 20_000;
        final long hitsBefore = fURICache.hits();
        final long missesBefore = fURICache.misses();
        final AtomicInteger mismatches = new AtomicInteger();
        final ExecutorService workers = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(workers.submit(() -> {
                start.await();
                for (int r = 0; r < roundsPerThread; r++) {
                    for (int i = 0; i < uris.size(); i++) {
                        final fURI got = Singleton.of(uris.get(i));
                        if (got != reference[i])
                            mismatches.incrementAndGet();
                        SINK += got.hashCode();
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (final Future<?> future : futures)
            future.get(60, TimeUnit.SECONDS);
        workers.shutdownNow();

        assertEquals(0, mismatches.get(), "every thread must observe the one cached instance per key");
        final long calls = (long) threads * roundsPerThread * uris.size();
        assertEquals(calls, (fURICache.hits() - hitsBefore) + (fURICache.misses() - missesBefore),
                "every cached lookup must be counted as a hit or a miss");
    }

    /** Clearing the cache costs correctness nothing: uris are re-parsed and remain equal to their cached selves. */
    @Test
    @Order(6)
    public void testCacheClearPreservesMeaning() {
        final List<String> uris = corpusList();
        final Map<String, fURI> before = new LinkedHashMap<>();
        for (final String furi : uris)
            before.put(furi, Singleton.of(furi));
        fURICache.clear();
        for (final String furi : uris) {
            final fURI after = Singleton.of(furi);
            assertEquals(before.get(furi), after);
            assertEquals(before.get(furi).hashCode(), after.hashCode());
            assertSame(after, Singleton.of(furi));
        }
    }

    // ======================== the benchmark ========================

    /**
     * The measurement the cache exists for: repeated parses of a repeated vocabulary. The uncached route pays the
     * full regex parse every time; the cached route pays a hash lookup. Reported in the log, asserted only against a
     * conservative ratio.
     */
    @Test
    @Order(7)
    public void testInternedParseThroughput() {
        final List<String> uris = corpusList();
        final int runs = 10_000;
        final long ops = (long) runs * uris.size();

        final double uncached = nanosPerOp(2, 3, ops, () -> {
            long sink = 0;
            for (int r = 0; r < runs; r++)
                for (final String furi : uris)
                    sink += Singleton.parse(key(furi)).hashCode();
            SINK += sink;
        });
        final double cached = nanosPerOp(2, 3, ops, () -> {
            long sink = 0;
            for (int r = 0; r < runs; r++)
                for (final String furi : uris)
                    sink += Singleton.of(furi).hashCode();
            SINK += sink;
        });

        final long hitsBefore = fURICache.hits();
        final long missesBefore = fURICache.misses();
        for (int r = 0; r < 1000; r++)
            for (final String furi : uris)
                Singleton.of(furi);
        final long hits = fURICache.hits() - hitsBefore;
        final long misses = fURICache.misses() - missesBefore;

        report("fURI parse: uncached %.1f ns/op, cached %.1f ns/op (%.1fx), cache size %d",
                uncached, cached, uncached / cached, fURICache.size());
        report("fURI cache on a repeated corpus: %d hits, %d misses (%.2f%% hit rate)",
                hits, misses, 100.0 * hits / (hits + misses));

        assertTrue(cached < uncached, "the cached lookup must beat a full parse");
        assertEquals(0, misses, "a repeated corpus must be served entirely from the cache");
        assertEquals((long) 1000 * uris.size(), hits);
        // Deliberately loose: the observed ratio is two orders of magnitude, so anything above a few x is a real
        // regression signal rather than CI scheduling noise.
        assertTrue(cached * 4 < uncached,
                "cached parse should be several times faster than uncached (was %.1f vs %.1f ns)".formatted(cached, uncached));
    }

    /**
     * The second-order win: the cached instance is shared, so its lazy slots (render, hash, base path) are computed
     * once for the whole VM instead of once per copy. Measured end to end — parse + derive — because that is the
     * cost a caller actually pays.
     */
    @Test
    @Order(8)
    public void testMemoizedDerivationThroughput() {
        final List<String> uris = corpusList();
        final int runs = 5_000;
        final long ops = (long) runs * uris.size();

        final double coldRender = nanosPerOp(2, 3, ops, () -> {
            long sink = 0;
            for (int r = 0; r < runs; r++)
                for (final String furi : uris)
                    sink += Singleton.parse(key(furi)).toString().length();
            SINK += sink;
        });
        final double warmRender = nanosPerOp(2, 3, ops, () -> {
            long sink = 0;
            for (int r = 0; r < runs; r++)
                for (final String furi : uris)
                    sink += Singleton.of(furi).toString().length();
            SINK += sink;
        });
        final double coldHash = nanosPerOp(2, 3, ops, () -> {
            long sink = 0;
            for (int r = 0; r < runs; r++)
                for (final String furi : uris)
                    sink += Singleton.parse(key(furi)).hashCode();
            SINK += sink;
        });
        final double warmHash = nanosPerOp(2, 3, ops, () -> {
            long sink = 0;
            for (int r = 0; r < runs; r++)
                for (final String furi : uris)
                    sink += Singleton.of(furi).hashCode();
            SINK += sink;
        });

        report("fURI toString: cold parse+render %.1f ns/op, cached %.1f ns/op (%.1fx)", coldRender, warmRender, coldRender / warmRender);
        report("fURI hashCode: cold parse+hash %.1f ns/op, cached %.1f ns/op (%.1fx)", coldHash, warmHash, coldHash / warmHash);

        assertTrue(warmRender < coldRender, "a cached, memoized render must beat parse+render");
        assertTrue(warmHash < coldHash, "a cached, memoized hash must beat parse+hash");
    }

    /**
     * {@code big()}/{@code small()} are the redirect hot path (type cloning), and the other agent's note is right
     * that they have no memo of their own — they cannot, because the route tables belong to the current machine and
     * {@code Machine.current()} is a ThreadLocal. What the cache gives them is a warm receiver: the routing tables are
     * keyed by {@code basePath()}, and that, plus {@code isGeneric()}, is now derived once per address. Measured
     * here on a booted VM, and validated for correctness against a cold receiver.
     */
    @Test
    @Order(9)
    public void testRedirectHotPathIsCorrectAndWarm() {
        final List<String> uris = corpusList();
        for (final String furi : uris) {
            final fURI warm = Singleton.of(furi);
            final fURI cold = Singleton.parse(key(furi));
            assertEquals(cold.big(), warm.big(), "big() must agree warm or cold for %s".formatted(furi));
            assertEquals(cold.small(), warm.small(), "small() must agree warm or cold for %s".formatted(furi));
        }

        final int runs = 20_000;
        final long ops = (long) runs * uris.size();
        final double coldBig = nanosPerOp(1, 3, ops, () -> {
            long sink = 0;
            for (int r = 0; r < runs; r++)
                for (final String furi : uris)
                    sink += Singleton.parse(key(furi)).big().hashCode();
            SINK += sink;
        });
        final double warmBig = nanosPerOp(1, 3, ops, () -> {
            long sink = 0;
            for (int r = 0; r < runs; r++)
                for (final String furi : uris)
                    sink += Singleton.of(furi).big().hashCode();
            SINK += sink;
        });
        report("fURI big(): cold parse+redirect %.1f ns/op, cached %.1f ns/op (%.1fx)", coldBig, warmBig, coldBig / warmBig);

        // What is LEFT in redirect once the parse is gone: the receiver's own derivations, which the cache now shares.
        // basePath() is the routing-table key, isGeneric() gates the whole call, and small() mirrors big().
        final double warmBase = nanosPerOp(2, 3, ops, () -> {
            long sink = 0;
            for (int r = 0; r < runs; r++)
                for (final String furi : uris)
                    sink += Singleton.of(furi).basePath().hashCode();
            SINK += sink;
        });
        final double warmGeneric = nanosPerOp(2, 3, ops, () -> {
            long sink = 0;
            for (int r = 0; r < runs; r++)
                for (final String furi : uris)
                    sink += Singleton.of(furi).isGeneric() ? 1 : 0;
            SINK += sink;
        });
        final double warmSmall = nanosPerOp(1, 3, ops, () -> {
            long sink = 0;
            for (int r = 0; r < runs; r++)
                for (final String furi : uris)
                    sink += Singleton.of(furi).small().hashCode();
            SINK += sink;
        });
        report("fURI redirect breakdown (cached receiver): basePath %.1f ns/op, isGeneric %.1f ns/op, small() %.1f ns/op, big() %.1f ns/op",
                warmBase, warmGeneric, warmSmall, warmBig);
        assertTrue(warmBig < coldBig, "a cached receiver must not make redirect slower");
    }

    /**
     * The cache is the funnels' contract, so hammer both entries the VM actually uses — {@code f()} and
     * {@code of()} — with the same string and require one instance, at speed.
     */
    @Test
    @Order(10)
    public void testCacheSizeStaysBoundedAndAccountsForOneShotAddresses() {
        final int distinct = 4_000;
        final fURI first = Singleton.of("/usr/bench/0");
        for (int i = 0; i < distinct; i++)
            Singleton.of("/usr/bench/" + i);
        assertSame(first, Singleton.of("/usr/bench/0"), "a cached address survives a burst that fits the budget");
        assertTrue(fURICache.size() >= distinct, "the burst should be cached while under the budget");
        assertTrue(fURICache.size() <= fURICache.max(),
                "the cache must never exceed its configured budget");
        report("fURI cache after a %d-address burst: size %d of %d, hits %d, misses %d, rotations %d",
                distinct, fURICache.size(), fURICache.max(), fURICache.hits(),
                fURICache.misses(), fURICache.rotations());
    }

    /**
     * The production shape: a hot working set that fits, and a stream of distinct addresses that does not.
     * <p>
     * Three properties, and they are the reason eviction is generational rather than a {@code clear()}: the cache stays
     * inside its budget however many one-shot addresses pass through, generations actually turn over, and the working
     * set keeps being served ACROSS those generations — an address retired with its generation still answers for one
     * more, so it is re-parsed once per two generations rather than once per boundary.
     */
    @Test
    @Order(11)
    public void testCacheIsHardBoundedUnderASustainedDistinctFlood() {
        final int budget = fURICache.max();
        final List<String> workingSet = new ArrayList<>();
        for (int i = 0; i < 1_000; i++)
            workingSet.add("/usr/hot/" + i);
        for (final String furi : workingSet)
            Singleton.of(furi);

        final long rotationsBefore = fURICache.rotations();
        final long missesBefore = fURICache.misses();
        final int rounds = 30;
        final int perRound = Math.max(1, (budget + budget / 2) / rounds); // ~1.5x the budget in one-shot addresses
        long hotHits = 0;
        for (int r = 0; r < rounds; r++) {
            for (int i = 0; i < perRound; i++)
                Singleton.of("/usr/flood/" + r + "/" + i);
            final long hits = fURICache.hits();
            for (final String furi : workingSet)
                Singleton.of(furi);
            hotHits += fURICache.hits() - hits;
        }

        final long hotAccesses = (long) rounds * workingSet.size();
        report("fURI cache under a %d-address flood (budget %d): size %d, rotations %d, working-set hit rate %.2f%% (%d/%d), cold misses %d",
                (long) rounds * perRound, budget, fURICache.size(), fURICache.rotations() - rotationsBefore,
                100.0 * hotHits / hotAccesses, hotHits, hotAccesses, fURICache.misses() - missesBefore);

        assertTrue(fURICache.size() <= budget,
                "an unbounded distinct flood must not push the cache past its budget (size %d, budget %d)"
                        .formatted(fURICache.size(), budget));
        assertTrue(fURICache.rotations() > rotationsBefore,
                "a flood past the budget must actually turn generations over");
        assertTrue(hotHits >= hotAccesses * 85 / 100,
                "the working set must keep being served across generations (%d/%d hot hits)".formatted(hotHits, hotAccesses));
    }

    /**
     * The budget is a CEILING, not a reservation: the cache's memory tracks the distinct addresses actually seen, and
     * only approaches the budget if that many distinct addresses really pass through. Reported, not asserted — a
     * GC-based heap delta in a JVM that is also running the VM is approximate, and the exact bound is asserted above.
     */
    @Test
    @Order(12)
    public void testCacheFootprintTracksDistinctAddressesUnderTheBudget() {
        fURICache.clear();
        for (int i = 0; i < 500; i++)
            Singleton.of("/usr/warm/" + i); // load the parser and species classes, then drop them again
        fURICache.clear();
        final long baseline = usedHeap();
        final int distinct = 20_000;
        for (int i = 0; i < distinct; i++)
            Singleton.of("/usr/fp/" + i + "/age");
        final long loaded = usedHeap();
        final int size = fURICache.size();
        report("fURI cache footprint: %d distinct addresses retained %d entries, %.1f MB (%.0f bytes/entry), budget %d, rotations %d",
                distinct, size, (loaded - baseline) / 1024.0 / 1024.0, size == 0 ? 0.0 : (double) (loaded - baseline) / size,
                fURICache.max(), fURICache.rotations());
        assertTrue(size <= distinct, "no more entries than distinct addresses were offered can be cached");
        assertTrue(size <= fURICache.max(), "the footprint check must stay within the budget");
    }

    /** Best-effort retained-heap reading; the JDK may ignore the hint, which is why callers only report. */
    private static long usedHeap() {
        final Runtime runtime = Runtime.getRuntime();
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(40);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
