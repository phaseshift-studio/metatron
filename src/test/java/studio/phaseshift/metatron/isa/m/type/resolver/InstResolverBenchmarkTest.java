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

package studio.phaseshift.metatron.isa.m.type.resolver;

import org.junit.jupiter.api.*;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.benchmark.BenchmarkTracker;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;

import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

/**
 * Benchmark tests for instruction resolution performance.
 * <p>
 * Run with: mvn test -Dtest=InstResolverBenchmarkTest
 * <p>
 * These tests measure the time taken to resolve instructions under various conditions.
 * Use these benchmarks to compare performance before and after optimization changes.
 */
@Disabled("Run manually: mvn test -Dtest=InstResolverBenchmarkTest")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class InstResolverBenchmarkTest extends AbstractMetatronTest {

    private static final int WARMUP_ITERATIONS = 100;
    private static final int BENCHMARK_ITERATIONS = 1000;

    /**
     * Result holder for benchmark metrics
     */
    public record BenchmarkResult(
            String name,
            long minNanos,
            long maxNanos,
            double avgNanos,
            long totalNanos,
            int iterations
    ) {
        public double avgMicros() {
            return avgNanos / 1000.0;
        }

        public double avgMillis() {
            return avgNanos / 1_000_000.0;
        }

        public double opsPerSecond() {
            return iterations / (totalNanos / 1_000_000_000.0);
        }

        @Override
        public String toString() {
            return String.format(
                    "%s: avg=%.2fμs, min=%.2fμs, max=%.2fμs, ops/sec=%.0f (n=%d)",
                    name,
                    avgMicros(),
                    minNanos / 1000.0,
                    maxNanos / 1000.0,
                    opsPerSecond(),
                    iterations
            );
        }
    }

    private BenchmarkResult runBenchmark(String name, Runnable operation) {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            operation.run();
        }

        // Benchmark
        List<Long> times = new ArrayList<>(BENCHMARK_ITERATIONS);
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            operation.run();
            long end = System.nanoTime();
            times.add(end - start);
        }

        LongSummaryStatistics stats = times.stream().mapToLong(Long::longValue).summaryStatistics();
        return new BenchmarkResult(
                name,
                stats.getMin(),
                stats.getMax(),
                stats.getAverage(),
                stats.getSum(),
                BENCHMARK_ITERATIONS
        );
    }

    // ========================================================================
    // HELPER METHODS TO CREATE INSTRUCTIONS
    // ========================================================================

    private Inst plusInst(Obj arg) {
        return instB(mInstSet.PLUS_INST_TID, lst(arg));
    }

    private Inst multInst(Obj arg) {
        return instB(mInstSet.MULT_INST_TID, lst(arg));
    }

    private Inst zeroInst() {
        return instB(mInstSet.ZERO_INST_TID, lst());
    }

    private Inst oneInst() {
        return instB(mInstSet.ONE_INST_TID, lst());
    }

    private Inst negInst() {
        return instB(mInstSet.NEG_INST_TID, lst());
    }

    private Inst gtInst(Obj arg) {
        return instB(mInstSet.GT_INST_TID, lst(arg));
    }

    private Inst eqInst(Obj arg) {
        return instB(mInstSet.EQ_INST_TID, lst(arg));
    }

    private Inst asInst(Obj arg) {
        return instB(mInstSet.AS_INST_TID, lst(arg));
    }

    // ========================================================================
    // PRIMITIVE TYPE RESOLUTION BENCHMARKS
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("Benchmark: resolve plus(real) on real")
    void benchmarkPlusReal() {
        Obj lhs = real(23.5);
        Inst inst = plusInst(real(10.0));

        BenchmarkResult result = runBenchmark("plus(real) on real", () -> {
            inst.resolve(lhs);
        });

        LOG.warn("BENCHMARK: %s", result);
    }

    @Test
    @Order(2)
    @DisplayName("Benchmark: resolve plus(int) on int")
    void benchmarkPlusInt() {
        Obj lhs = jnt(42);
        Inst inst = plusInst(jnt(10));

        BenchmarkResult result = runBenchmark("plus(int) on int", () -> {
            inst.resolve(lhs);
        });

        LOG.warn("BENCHMARK: %s", result);
    }

    @Test
    @Order(3)
    @DisplayName("Benchmark: resolve mult(real) on real")
    void benchmarkMultReal() {
        Obj lhs = real(23.5);
        Inst inst = multInst(real(2.0));

        BenchmarkResult result = runBenchmark("mult(real) on real", () -> {
            inst.resolve(lhs);
        });

        LOG.warn("BENCHMARK: %s", result);
    }

    @Test
    @Order(4)
    @DisplayName("Benchmark: resolve zero() on real")
    void benchmarkZeroReal() {
        Obj lhs = real(23.5);
        Inst inst = zeroInst();

        BenchmarkResult result = runBenchmark("zero() on real", () -> {
            inst.resolve(lhs);
        });

        LOG.warn("BENCHMARK: %s", result);
    }

    @Test
    @Order(5)
    @DisplayName("Benchmark: resolve one() on real")
    void benchmarkOneReal() {
        Obj lhs = real(23.5);
        Inst inst = oneInst();

        BenchmarkResult result = runBenchmark("one() on real", () -> {
            inst.resolve(lhs);
        });

        LOG.warn("BENCHMARK: %s", result);
    }

    // ========================================================================
    // STRING OPERATION BENCHMARKS
    // ========================================================================

    @Test
    @Order(10)
    @DisplayName("Benchmark: resolve plus(str) on str")
    void benchmarkPlusStr() {
        Obj lhs = str("hello");
        Inst inst = plusInst(str(" world"));

        BenchmarkResult result = runBenchmark("plus(str) on str", () -> {
            inst.resolve(lhs);
        });

        LOG.warn("BENCHMARK: %s", result);
    }

    // ========================================================================
    // ALREADY RESOLVED INSTRUCTION BENCHMARKS (hasf() early exit)
    // ========================================================================

    @Test
    @Order(20)
    @DisplayName("Benchmark: already resolved instruction (hasf early exit)")
    void benchmarkAlreadyResolved() {
        Obj lhs = real(23.5);
        Inst inst = plusInst(real(10.0));

        // Pre-resolve the instruction
        Inst resolved = inst.resolve(lhs);

        BenchmarkResult result = runBenchmark("already resolved (hasf)", () -> {
            resolved.resolve(lhs);
        });

        LOG.warn("BENCHMARK: %s", result);
    }

    // ========================================================================
    // COMPLEX OPERATION BENCHMARKS
    // ========================================================================

    @Test
    @Order(30)
    @DisplayName("Benchmark: chained operations resolution")
    void benchmarkChainedOps() {
        Obj lhs = real(10.0);

        BenchmarkResult result = runBenchmark("chained plus.mult.neg", () -> {
            plusInst(real(5.0)).resolve(lhs);
            multInst(real(2.0)).resolve(lhs);
            negInst().resolve(lhs);
        });

        LOG.warn("BENCHMARK: %s", result);
    }

    @Test
    @Order(31)
    @DisplayName("Benchmark: full apply chain (resolve + apply)")
    void benchmarkFullApply() {
        Obj lhs = real(10.0);
        Inst inst = plusInst(real(5.0));

        BenchmarkResult result = runBenchmark("full apply (resolve+execute)", () -> {
            inst.apply(lhs);
        });

        LOG.warn("BENCHMARK: %s", result);
    }

    // ========================================================================
    // GENERIC TYPE RESOLUTION BENCHMARKS
    // ========================================================================

    @Test
    @Order(40)
    @DisplayName("Benchmark: as(type) resolution")
    void benchmarkAsType() {
        Obj lhs = jnt(42);
        Type realType = T(mInstSet.REAL_TID);

        BenchmarkResult result = runBenchmark("as(real) on int", () -> {
            asInst(realType).resolve(lhs);
        });

        LOG.warn("BENCHMARK: %s", result);
    }

    // ========================================================================
    // COMPARISON BENCHMARKS
    // ========================================================================

    @Test
    @Order(50)
    @DisplayName("Benchmark: gt comparison resolution")
    void benchmarkGtComparison() {
        Obj lhs = jnt(42);
        Inst inst = gtInst(jnt(10));

        BenchmarkResult result = runBenchmark("gt(int) on int", () -> {
            inst.resolve(lhs);
        });

        LOG.warn("BENCHMARK: %s", result);
    }

    @Test
    @Order(51)
    @DisplayName("Benchmark: eq comparison resolution")
    void benchmarkEqComparison() {
        Obj lhs = jnt(42);
        Inst inst = eqInst(jnt(42));

        BenchmarkResult result = runBenchmark("eq(int) on int", () -> {
            inst.resolve(lhs);
        });

        LOG.warn("BENCHMARK: %s", result);
    }

    // ========================================================================
    // SUMMARY TEST
    // ========================================================================

    @Test
    @Order(100)
    @DisplayName("Summary: All benchmarks comparison")
    void benchmarkSummary() {
        List<BenchmarkResult> results = new ArrayList<>();

        results.add(runBenchmark("plus(real)", () -> plusInst(real(10.0)).resolve(real(23.5))));
        results.add(runBenchmark("plus(int)", () -> plusInst(jnt(10)).resolve(jnt(42))));
        results.add(runBenchmark("mult(real)", () -> multInst(real(2.0)).resolve(real(23.5))));
        results.add(runBenchmark("zero()", () -> zeroInst().resolve(real(23.5))));
        results.add(runBenchmark("one()", () -> oneInst().resolve(real(23.5))));
        results.add(runBenchmark("neg()", () -> negInst().resolve(real(23.5))));
        results.add(runBenchmark("gt(int)", () -> gtInst(jnt(10)).resolve(jnt(42))));
        results.add(runBenchmark("eq(int)", () -> eqInst(jnt(42)).resolve(jnt(42))));

        LOG.warn("========================================");
        LOG.warn("BENCHMARK SUMMARY");
        LOG.warn("========================================");
        for (BenchmarkResult r : results) {
            LOG.warn("  %s", r);
        }
        LOG.warn("========================================");

        // Calculate totals
        double totalAvgMicros = results.stream().mapToDouble(BenchmarkResult::avgMicros).sum();
        LOG.warn("Total avg time for all ops: %.2fμs", totalAvgMicros);
    }

    // ========================================================================
    // TRACKED BENCHMARK TEST (saves results and compares to baseline)
    // ========================================================================

    /**
     * Run benchmarks with tracking - saves results to benchmark/ folder and
     * compares against baseline. Fails if regression exceeds threshold.
     * <p>
     * Run with: mvn test -Dtest=InstResolverBenchmarkTest#benchmarkTracked
     * <p>
     * To update baseline after intentional changes:
     * mvn test -Dtest=InstResolverBenchmarkTest#benchmarkUpdateBaseline
     */
    @Test
    @Order(101)
    @Disabled
    @DisplayName("Tracked: Run benchmarks and compare to baseline")
    void benchmarkTracked() throws IOException {
        BenchmarkTracker tracker = new BenchmarkTracker("inst-resolution", 0.20); // 20% regression threshold

        // Run all benchmarks
        List<BenchmarkResult> results = new ArrayList<>();
        results.add(runBenchmark("plus(real)", () -> plusInst(real(10.0)).resolve(real(23.5))));
        results.add(runBenchmark("plus(int)", () -> plusInst(jnt(10)).resolve(jnt(42))));
        results.add(runBenchmark("mult(real)", () -> multInst(real(2.0)).resolve(real(23.5))));
        results.add(runBenchmark("zero()", () -> zeroInst().resolve(real(23.5))));
        results.add(runBenchmark("one()", () -> oneInst().resolve(real(23.5))));
        results.add(runBenchmark("neg()", () -> negInst().resolve(real(23.5))));
        results.add(runBenchmark("gt(int)", () -> gtInst(jnt(10)).resolve(jnt(42))));
        results.add(runBenchmark("eq(int)", () -> eqInst(jnt(42)).resolve(jnt(42))));

        // Record results to tracker
        for (BenchmarkResult r : results) {
            tracker.record(r.name(), r.avgNanos(), r.minNanos(), r.maxNanos(), r.opsPerSecond(), r.iterations());
        }

        // Save and compare (will throw if regression detected)
        tracker.saveAndCompare(false);
    }

    /**
     * Update the baseline with current benchmark results.
     * Run this after intentional performance changes.
     */
    @Test
    @Order(102)
    @DisplayName("Update baseline with current results")
    @Disabled("Run manually: mvn test -Dtest=InstResolverBenchmarkTest#benchmarkUpdateBaseline")
    void benchmarkUpdateBaseline() throws IOException {
        BenchmarkTracker tracker = new BenchmarkTracker("inst-resolution");

        // Run all benchmarks
        List<BenchmarkResult> results = new ArrayList<>();
        results.add(runBenchmark("plus(real)", () -> plusInst(real(10.0)).resolve(real(23.5))));
        results.add(runBenchmark("plus(int)", () -> plusInst(jnt(10)).resolve(jnt(42))));
        results.add(runBenchmark("mult(real)", () -> multInst(real(2.0)).resolve(real(23.5))));
        results.add(runBenchmark("zero()", () -> zeroInst().resolve(real(23.5))));
        results.add(runBenchmark("one()", () -> oneInst().resolve(real(23.5))));
        results.add(runBenchmark("neg()", () -> negInst().resolve(real(23.5))));
        results.add(runBenchmark("gt(int)", () -> gtInst(jnt(10)).resolve(jnt(42))));
        results.add(runBenchmark("eq(int)", () -> eqInst(jnt(42)).resolve(jnt(42))));

        // Record results
        for (BenchmarkResult r : results) {
            tracker.record(r.name(), r.avgNanos(), r.minNanos(), r.maxNanos(), r.opsPerSecond(), r.iterations());
        }

        // Force update baseline
        tracker.saveAndCompare(true);
        LOG.warn("Baseline updated");
    }
}
