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

package studio.phaseshift.metatron.isa.m;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.Analysis;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.Analyzer;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.BasicAnalyzer;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.function.ToLongFunction;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * repeat() behavior + timing probe. A PROFILED run ({@link Analyzer#PROFILE}) measures the processor stages
 * (apply/split, apply/apply, apply/next, plus the monad propagation counters in flow), and the repeat body measures its
 * internal phases (repeat/esc, emit, until, code and state, and its passes in repeat/loop). This test profiles a single
 * evaluation into an analyzer of its own and reports a per-phase breakdown, which is the "where is it slow" evidence
 * behind the repeat() optimization.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RepeatInstTest extends AbstractMetatronTest {

    /**
     * A snapshot of one evaluation's cost, in nanoseconds (wall-clock and the accumulated processor + repeat phases),
     * plus the monad-propagation counters. {@code sum()} is the repeat body's own accounted time, which should sit
     * inside {@code applyNs} (the repeat inst is applied by the processor, and its body does the predicate/code work).
     */
    public record Probe(long wallNs, long splitNs, long applyNs, long nextNs,
                        long escNs, long emitNs, long untilNs, long codeNs, long bookNs,
                        long iters, long monads, long coeffSum) {

        public long repeatBodyNs() {
            return this.escNs + this.emitNs + this.untilNs + this.codeNs + this.bookNs;
        }

        public long perIterNs() {
            return 0L == this.iters ? 0L : this.wallNs / this.iters;
        }

        @Override
        public String toString() {
            return String.format(
                    "wall=%,dns iters=%d monads=%d coeffSum=%d | split=%,d apply=%,d next=%,d | esc=%,d emit=%,d until=%,d code=%,d book=%,d (body=%,d)",
                    this.wallNs, this.iters, this.monads, this.coeffSum,
                    this.splitNs, this.applyNs, this.nextNs,
                    this.escNs, this.emitNs, this.untilNs, this.codeNs, this.bookNs, this.repeatBodyNs());
        }
    }

    /**
     * Parse, warm up (once, untimed and unprofiled — heats the fURI intern pool and JIT), then profile and time one more
     * apply. The parse is deliberately outside the timed region so the number is the repeat evaluation itself, and the
     * profile is an analyzer of the probe's own, so the numbers are this evaluation's and no other's.
     */
    private static Probe probe(final String code) {
        Machine.parse(code).apply(noobj());
        final Obj program = Machine.parse(code);
        final Analyzer profile = new BasicAnalyzer();
        Analyzer.PROFILE.set(profile);
        final long wall;
        try {
            final long t0 = System.nanoTime();
            program.apply(noobj());
            wall = System.nanoTime() - t0;
        } finally {
            Analyzer.PROFILE.remove();
        }
        final Analysis analysis = profile.analysis();
        final ToLongFunction<String> read = path -> analysis.at(uri(path)).orElse(jnt(0)).intValue();
        return new Probe(wall,
                read.applyAsLong("apply/split/total"), read.applyAsLong("apply/apply/total"), read.applyAsLong("apply/next/total"),
                read.applyAsLong("repeat/esc/total"), read.applyAsLong("repeat/emit/total"), read.applyAsLong("repeat/until/total"),
                read.applyAsLong("repeat/code/total"), read.applyAsLong("repeat/state/total"),
                read.applyAsLong("repeat/loop"), read.applyAsLong("flow/monads"), read.applyAsLong("flow/c_sum"));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "1.repeat(code=>plus(1),until=>loop().is(eq(10)))                     % 11",
            "1.repeat(code=>mult(2),until=>loop().is(eq(4)))                      % 16",
            "1.repeat(code=>plus(1),until=>is(gt(10)))                            % 11",
            "5.repeat(code=>minus(1),until=>is(eq(0)))                            % 0",
            "1.repeat(code=>plus(1),until=>loop().is(eq(5)),emit=>true)           % {1,2,3,4,5,6}",
    }, delimiter = '%')
    public void testRepeatCorrectness(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    @Test
    public void testRepeatBehaviorProbe() {
        // sanity: the probe captures real per-phase numbers, not zeros, for a short loop
        final Probe probe = probe("1.repeat(code=>plus(1),until=>is(gt(100)))");
        LOG.info("repeat probe: %s", probe);
        org.junit.jupiter.api.Assertions.assertTrue(probe.iters() > 1, "repeat body should run several times");
        org.junit.jupiter.api.Assertions.assertTrue(probe.applyNs() > 0, "apply phase should be non-zero");
        org.junit.jupiter.api.Assertions.assertTrue(probe.untilNs() > 0, "until phase should be non-zero");
        org.junit.jupiter.api.Assertions.assertTrue(probe.codeNs() > 0, "code phase should be non-zero");
    }

    @Test
    public void benchmarkRepeatPhases() {
        System.out.println("\n=== repeat() phase benchmark (single warm-up, one timed run each) ===");
        for (final int n : new int[]{100, 1000})
            report("single-inst until", n, "1.repeat(code=>plus(1),until=>is(gt(%d)))");
        for (final int n : new int[]{100, 1000})
            report("multi-inst until loop()", n, "1.repeat(code=>plus(1),until=>loop().is(eq(%d)))");
        for (final int n : new int[]{100, 1000})
            report("multi-inst code", n, "1.repeat(code=>plus(1).plus(1),until=>is(gt(%d)))");
        System.out.println("=== end repeat() phase benchmark ===\n");
    }

    private static void report(final String label, final int n, final String codeFmt) {
        final Probe probe = probe(String.format(codeFmt, n));
        System.out.println(String.format("%-26s n=%-6d: %s (%.1f ns/iter)", label, n, probe, probe.perIterNs() * 1.0));
    }
}
