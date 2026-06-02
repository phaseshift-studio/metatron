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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

/**
 * Abstract test class for InstResolver implementations.
 * <p>
 * Subclasses should provide the specific resolver to test via the constructor.
 * All parameterized tests will run against that resolver, making it easy to
 * compare behavior across different resolution strategies.
 * <p>
 * To add a new resolver test:
 * <ol>
 *   <li>Create a new test class extending AbstractInstResolverTest</li>
 *   <li>Pass the resolver supplier to the constructor</li>
 *   <li>Optionally override tests or add resolver-specific tests</li>
 * </ol>
 */
public abstract class AbstractInstResolverTest extends AbstractMetatronTest {

    protected final Supplier<InstResolver> resolverSupplier;
    private InstResolver previousResolver;

    protected AbstractInstResolverTest(final Supplier<InstResolver> resolverSupplier) {
        this.resolverSupplier = resolverSupplier;
    }

    @BeforeEach
    protected void setupResolver() {
        // Save current resolver and install test resolver
        this.previousResolver = InstResolver.get();
        InstResolver.set(this.resolverSupplier.get());
    }

    @AfterEach
    protected void restoreResolver() {
        // Restore previous resolver
        if (this.previousResolver != null) {
            InstResolver.set(this.previousResolver);
        }
    }

    // ========================================================================
    // BASIC ARITHMETIC RESOLUTION TESTS
    // ========================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "1.plus(2)                                                              % 3",
            "10.plus(5)                                                             % 15",
            "1.mult(3)                                                              % 3",
            "5.mult(4)                                                              % 20",
            "10.minus(3)                                                            % 7",
            "6.0.mult(0.5)                                                          % 3.0",
            "1.5.plus(2.5)                                                          % 4.0",
            "2.0.mult(3.0)                                                          % 6.0",
    }, delimiter = '%')
    public void testBasicArithmetic(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    // ========================================================================
    // TYPE CONVERSION (as) TESTS - KEY FOR SPECIFICITY
    // ========================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "1.as(real::T)                                                          % 1.0",
            "1.5.as(int::T)                                                         % 1",
            "true.as(int::T)                                                        % 1",
            "false.as(int::T)                                                       % 0",
            "1.as(str::T)                                                           % \"1\"",
            "true.as(str::T)                                                        % \"true\"",
            "false.as(str::T)                                                       % \"false\"",
    }, delimiter = '%')
    public void testAsTypeConversion(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0x48656c6c6f.as(str::T)                                                % \"Hello\"",
            "\"abc\".as(bytes::T)                                                    % 0x616263",
    }, delimiter = '%')
    public void testBytesStringConversion(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    // ========================================================================
    // COMPARISON OPERATION TESTS
    // ========================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "5.gt(3)                                                                % true",
            "3.gt(5)                                                                % false",
            "5.lt(3)                                                                % false",
            "3.lt(5)                                                                % true",
            "5.eq(5)                                                                % true",
            "5.eq(3)                                                                % false",
            "5.gte(5)                                                               % true",
            "5.gte(3)                                                               % true",
            "5.lte(5)                                                               % true",
            "3.lte(5)                                                               % true",
    }, delimiter = '%')
    public void testComparisons(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    // ========================================================================
    // STRING OPERATION TESTS
    // ========================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "'hello'.plus(' world')                                                 % \"hello world\"",
            "'ABC'.lcase()                                                          % \"abc\"",
            "'abc'.ucase()                                                          % \"ABC\"",
    }, delimiter = '%', quoteCharacter = '~')
    public void testStrOperations(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    // ========================================================================
    // LIST OPERATION TESTS
    // ========================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "[1,2,3]>-.count()                                                        % 3",
            "[1,2,3]>-.sum()                                                          % 6",
            "{1,2,3}.plus(2)                                                        % {3,4,5}",
    }, delimiter = '%')
    public void testLstOperations(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    // ========================================================================
    // RECORD OPERATION TESTS
    // ========================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "[a=>1,b=>2,c=>3].>>a                                                   % 1",
            "[a=>1,b=>2,c=>3].>>b                                                   % 2",
            "[a=>1,b=>2,c=>3]>-.count()                                               % 3",
    }, delimiter = '%')
    public void testRecOperations(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    // ========================================================================
    // CHAINED RESOLUTION TESTS
    // ========================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "1.plus(2).plus(3).plus(4)                                              % 10",
            "[1,2,3]>-.sum().plus(4)                                                  % 10",
            "5.gt(3).and(true)                                                      % true",
            "5.gt(3).and(5.lt(3))                                                   % false",
    }, delimiter = '%')
    public void testChainedResolution(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    // ========================================================================
    // DOMAIN SPECIFICITY TESTS
    // ========================================================================

    @Test
    public void testResolverIsInstalled() {
        // Verify the correct resolver is active
        InstResolver current = InstResolver.get();
        assertNotNull(current);
        assertEquals(resolverSupplier.get().getClass(), current.getClass());
    }

    @Test
    public void testResolveReturnsNonNull() {
        // Basic sanity check - resolution should return something
        Obj lhs = mParser.m_obj().parse("1").get();
        Obj inst = mParser.m_obj().parse("plus(2)").get();
        Obj result = inst.apply(lhs);
        assertNotNull(result);
        assertFalse(result.isNoObj());
        assertFalse(result.isFail());
    }

    // ========================================================================
    // ZERO AND ONE (ALGEBRAIC IDENTITY) TESTS
    // ========================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "5.zero()                                                               % 0",
            "5.5.zero()                                                             % 0.0",
            "5.one()                                                                % 1",
            "5.5.one()                                                              % 1.0",
    }, delimiter = '%')
    public void testAlgebraicIdentities(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    // ========================================================================
    // NEGATION TESTS
    // ========================================================================

    @ParameterizedTest
    @CsvSource(value = {
            "5.neg()                                                                % -5",
            "-5.neg()                                                               % 5",
            "5.5.neg()                                                              % -5.5",
            "true.not()                                                             % false",
            "false.not()                                                            % true",
    }, delimiter = '%')
    public void testNegation(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    // ========================================================================
    // BENCHMARKING
    // ========================================================================

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

    protected BenchmarkResult runBenchmark(String name, Runnable operation) {
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

    // Helper methods to create instructions for benchmarking
    protected Inst plusInst(Obj arg) {
        return instB(mInstSet.PLUS_INST_TID, lst(arg));
    }

    protected Inst multInst(Obj arg) {
        return instB(mInstSet.MULT_INST_TID, lst(arg));
    }

    protected Inst asInst(Obj arg) {
        return instB(mInstSet.AS_INST_TID, lst(arg));
    }

    @Test
    @Disabled
    @DisplayName("Benchmark: instruction resolution performance")
    public void benchmarkResolutionPerformance() {
        List<BenchmarkResult> results = new ArrayList<>();

        // Benchmark various resolution scenarios
        results.add(runBenchmark("plus(int) on int", () -> plusInst(jnt(10)).resolve(jnt(42))));
        results.add(runBenchmark("plus(real) on real", () -> plusInst(real(10.0)).resolve(real(23.5))));
        results.add(runBenchmark("mult(int) on int", () -> multInst(jnt(2)).resolve(jnt(42))));
        results.add(runBenchmark("mult(real) on real", () -> multInst(real(2.0)).resolve(real(23.5))));
        results.add(runBenchmark("as(real) on int", () -> asInst(T(mInstSet.REAL_TID)).resolve(jnt(42))));
        results.add(runBenchmark("as(int) on real", () -> asInst(T(mInstSet.INT_TID)).resolve(real(42.5))));

        // Log results
        String resolverName = resolverSupplier.get().getClass().getSimpleName();
        LOG.warn("========================================");
        LOG.warn("BENCHMARK: %s", resolverName);
        LOG.warn("========================================");
        for (BenchmarkResult r : results) {
            LOG.warn("  %s", r);
        }
        LOG.warn("========================================");

        // Calculate totals
        double totalAvgMicros = results.stream().mapToDouble(BenchmarkResult::avgMicros).sum();
        LOG.warn("total avg time for all ops: %.2fμs", totalAvgMicros);
    }
}
