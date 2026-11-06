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
import studio.phaseshift.metatron.isa.m.mach.type.processor.monad.StatefulMonad;
import studio.phaseshift.metatron.isa.m.type.Obj;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/*
 * repeat() behavior + timing probe. The monad loop already accrues processor-stage timing in {@link StatefulMonad}
 * (split / apply / next, plus monad propagation counters), and the repeat body itself accrues its internal phases
 * (escape / emit / until / code / bookkeeping). This test reads those accumulators around a single evaluation and
 * reports a per-phase breakdown, which is the "where is it slow" evidence behind the repeat() optimization.
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
     * Parse, warm up (once, untimed — heats the fURI intern pool and JIT), then reset the accumulators and time one
     * more apply. The parse is deliberately outside the timed region so the number is the repeat evaluation itself.
     */
    private static Probe probe(final String code) {
        Machine.parse(code).apply(noobj());
        StatefulMonad.resetTimings();
        final Obj program = Machine.parse(code);
        final long t0 = System.nanoTime();
        program.apply(noobj());
        final long wall = System.nanoTime() - t0;
        return new Probe(wall,
                StatefulMonad.T_SPLIT.get(), StatefulMonad.T_APPLY.get(), StatefulMonad.T_NEXT.get(),
                StatefulMonad.T_REPEAT_ESC.get(), StatefulMonad.T_REPEAT_EMIT.get(), StatefulMonad.T_REPEAT_UNTIL.get(),
                StatefulMonad.T_REPEAT_CODE.get(), StatefulMonad.T_REPEAT_BOOK.get(),
                StatefulMonad.REPEAT_ITERS.get(), StatefulMonad.MONADS.get(), StatefulMonad.COEFF_SUM.get());
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
