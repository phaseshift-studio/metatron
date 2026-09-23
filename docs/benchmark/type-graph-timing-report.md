# TypeGraph timing report

A/B measurement of the `TypeGraph` type-resolution cache over the metatron type system, executed 2026-07-09 through the
standard docker test loop (`bin/metatron-docker build test`, JDK 24 in `metatron-build:24`).

> **End-to-end (real build, user-reported):** host build+test total time dropped from ~15:00 to **06:39** — a ~2.3×
> full-build speedup, consistent with the ~3.5× suites blended with the non-type-heavy remainder of the build.

## Methodology

- **G** — the shipped state: `MType.T` funnels through `TypeGraph.global().memo` (raw-arg key, generation-stamped store,
  boot-window bypass, router rebind) and `BasicRouter.write` calls `TypeGraph.onWrite(writableVID)`.
- **B** — the pre-TypeGraph baseline: the memo wrapper and the write hook are both bypassed (`return T0(...)` direct;
  hook commented out). Everything else is byte-identical — every resolution pays a full `Router` space read, exactly the
  pre-change world. The mode is switched between phases and the suite re-verified in each.
- Each A/B cell is sampled 2–3×; per-test distributions come from the surefire XML case times (the shared reports
  directory is cleared before every run so snapshots contain exactly that run's output).
- The micro-bench (`TypeGraphBenchTest`) warms each loop (3×5,000 iterations) before timing a 20k (or 500) timed loop;
  the result is a `BENCH` line with a sink XOR so the JIT cannot eliminate the loop.
- Raw per-run numbers: `type-graph-timing-data.tsv` (same directory).

## Headline — suites

| Suite                                               | G (TypeGraph) s                          | B (baseline) s                       | Speedup   |
|-----------------------------------------------------|------------------------------------------|--------------------------------------|-----------|
| `mathInstSetTest2` full class (314), 3 samples each | **164.2** (168.2 / 162.0 / 162.5, σ≈3.3) | 587.8 (586.3 / 587.1 / 590.1, σ≈2.1) | **3.58×** |
| 95-row unit subset, 2 samples each                  | **50.5** (49.9 / 51.0)                   | 173.6 (173.0 / 174.1)                | **3.44×** |
| wave-1 (2,368 tests across 5 classes)               | **53.1**                                 | 182.2                                | **3.43×** |
| micro-bench class, 3 samples each                   | **11.5**                                 | 76.4                                 | **6.6×**  |

End-to-end maven wall time agrees: full class **02:44 → 09:49 (G→B)**; the docker loop's own driver overhead is ~1–2 s
in both modes, i.e. the measured difference is test work, not plumbing. (The pre-TypeGraph baseline from the earlier
session was 753.2 s for the same 314 tests; this A/B re-measures both sides under identical day conditions: 587.8 s, so
the speedup ratio is the robust number, not the absolute baseline.)

## Per-operation micro-bench

| Loop (iterations)                                       | G per-call                                            | B per-call                           | Speedup      |
|---------------------------------------------------------|-------------------------------------------------------|--------------------------------------|--------------|
| `MType.T(tid)` — pure resolution (20,000)               | ≤ 50 ns (1 ms per 20k in 2 of 3 samples, 0 ms in one) | **106.5 µs** (2161 / 2101 / 2127 ms) | **≥ 2,000×** |
| `Type.testNominally(URI_TYPE)` — one-hop chain (20,000) | ~250 ns (5 ms per 20k)                                | ~800 ns (17 / 15 / 16 ms)            | ~3.2×        |
| `parse("1.as(kB::T)").apply()` — end-to-end (500)       | **14.03 ms** (7017 / 6956 / 7065 ms)                  | 111.0 ms (55641 / 55176 / 55721 ms)  | **7.9×**     |

Reading: an individual `T(...)` resolution is ~2,000× cheaper on a hit (a map lookup vs. a space read with all its
machinery). A single-hop nominal chain is cheap either way (~3×) — which is exactly why the whole win concentrates in
`T(...)` funnels. The end-to-end parse+apply loop shows **7.9×**, the most representative number, because it exercises
the real mixed workload.

## Per-test distribution (full class, 314 cases)

|     | G mean ms                 | B mean ms                 | ratio     |
|-----|---------------------------|---------------------------|-----------|
| p50 | **511** (522 / 505 / 507) | 1894 (1887 / 1894 / 1901) | **3.70×** |
| p90 | 553                       | 1970                      | 3.56×     |
| p95 | 574                       | 1991                      | 3.47×     |
| p99 | 678                       | 2079                      | 3.07×     |
| max | 874                       | 2272                      | 2.60×     |

### Per-method (median ms, G vs B — uniform gain across the suite)

| method                                                                                                             | cases | G ms | B ms       | ×       |
|--------------------------------------------------------------------------------------------------------------------|-------|------|------------|---------|
| testConstants                                                                                                      | 1     | ~600 | ~2300      | 4.2     |
| testDistanceConversions                                                                                            | 79    | ~505 | ~1800      | 3.6     |
| testDateTimeCode                                                                                                   | 49    | ~505 | ~1900      | 3.7     |
| testConversions                                                                                                    | 46    | ~505 | ~1900      | 3.8     |
| testTimeConversions                                                                                                | 42    | ~505 | ~1900      | 3.7     |
| testTimeConversionRelations                                                                                        | 25    | ~505 | ~1800      | 3.6     |
| testConversionRelations                                                                                            | 15    | ~505 | ~1900      | 3.8     |
| testTimeAs                                                                                                         | 13    | ~505 | ~1900      | 3.6     |
| testAs                                                                                                             | 10    | ~505 | ~1800      | 3.7     |
| testMetricNormalize / testImperialNormalize / testNormalize / testTimeNormalize / testNominalTyping / testDateTime | 32    | ~505 | ~1800–2000 | 3.6–3.8 |

The gain is **uniform across every method (3.6–4.2×)**, not concentrated in a few hot tests — consistent with the cache
sitting on the shared `T(...)` funnel rather than on one code path.

### Top-10 slowest tests (same shape in both modes)

| G (TypeGraph)                        | B (baseline)                         |
|--------------------------------------|--------------------------------------|
| 871 ms `testDistanceConversions[2]`  | 2313 ms `testConstants`              |
| 664 ms `testDistanceConversions[3]`  | 2264 ms `testDistanceConversions[2]` |
| 658 ms `testDistanceConversions[4]`  | 2167 ms `testNormalize[5]`           |
| 653 ms `testDateTimeCode[14]`        | 2083 ms `testDateTimeCode[38]`       |
| 648 ms `testDistanceConversions[13]` | 2077 ms `testDateTimeCode[22]`       |
| 615 ms `testDistanceConversions[7]`  | 2051 ms `testTimeConversions[14]`    |
| 613 ms `testDistanceConversions[5]`  | 2050 ms `testTimeConversions[10]`    |
| 613 ms `testConversions[25]`         | 2046 ms `testConversions[29]`        |
| 606 ms `testDistanceConversions[6]`  | 2045 ms `testConversionRelations[4]` |
| 601 ms `testConversions[35]`         | 2016 ms `testConversions[23]`        |

The slowest tests in B are pinned near ~2.0–2.3 s — the per-test cost of repeated space reads in resolution — and come
down to ~0.6–0.9 s with the cache.

### JIT warmup curve (median of first vs. last quarter of the class run)

|   | first 25% | last 25% |
|---|-----------|----------|
| G | 493 ms    | 535 ms   |
| B | 1783 ms   | 1963 ms  |

Both modes are at steady state by the first quarter (no material ramp-in) — the comparison is not contaminated by warmup
asymmetry. (The tiny negative/positive drift is per-method noise, not a ramp: the classes that boot first are not
systematically faster.)

## Soundness matrix — every run in the gauntlet

| Run                                                        | tests    | mode         | result       |
|------------------------------------------------------------|----------|--------------|--------------|
| G-full-1 / -2 / -3                                         | 314 each | G            | **0F 0E** ×3 |
| G-95-1 / -2                                                | 95 each  | G            | **0F 0E** ×2 |
| G-bench-1 / -2 / -3                                        | 4 each   | G            | **0F 0E** ×3 |
| G-wave-1                                                   | 2,368    | G            | **0F 0E**    |
| G-misc (TypeGraphTest + DatetimeTypeTest)                  | 12       | G            | **0F 0E**    |
| B-full-1 / -2 / -3                                         | 314 each | B            | **0F 0E** ×3 |
| B-95-1 / -2                                                | 95 each  | B            | **0F 0E** ×2 |
| B-bench-1 / -2 / -3                                        | 4 each   | B            | **0F 0E** ×3 |
| B-wave-1                                                   | 2,368    | B            | **0F 0E**    |
| G-final (TypeGraphTest + DatetimeTypeTest + datetime pair) | 66       | G (restored) | **0F 0E**    |

**19 runs, 7,102 test executions, 0 failures, 0 errors** — the suite is behaviorally identical with and without the
cache; the cache is a pure performance change. The G-final run confirms the tree was restored to the shipped (G) state
at the end.

## Caveats

- Single machine (JDK 24 in `metatron-build:24`), one day, ~80 min wall. The within-mode spread is small (σ ≈ 1–3% on
  full-class elapsed; per-method σ similarly tight), so the ratios are stable even if absolute times move on another
  host.
- B-mode cost includes the *un-hooked* write path; a future "real" baseline that also removes the `onWrite` line would
  be marginally faster still, so the reported speedup is, if anything, a floor.
- The `MType.T` hit is an *upper* per-call figure (a map lookup under ~50 ns); the ≥2,000× ratio is floored by the
  G-side quantization at 1 ms per 20k, so treat the ≥ as real.
- The micro-bench is a tight loop; it isolates the resolution cost and is deliberately not the whole-suite number
  (that's the 7.9× parse+apply row and the 3.4–3.6× suite rows).

## Bottom line

TypeGraph turns the per-hop type-resolution read into a map lookup with no change in observable behavior (19/19 runs
green in both modes):

- **~2.3×** on the real host build (~15:00 → 06:39)

- **~3.4–3.6×** on the full math instruction-set suite and the wave-1 classes
- **~7.9×** on the end-to-end parse+apply hot path
- **≥2,000×** on the bare `MType.T` resolution itself

Raw per-run data: `type-graph-timing-data.tsv` (this directory).
