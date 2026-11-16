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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Obj;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * timeout(code, time): the code's answer on the lhs if it gives one within the time, and a catchable fail::T
 * otherwise. The time runs out by interrupting the evaluating thread, so the endless walks here end where a walk checks
 * its thread, once a run ({@code {*}plus(0)} never leaves int, as {@code 1.plus(0)} is 1). The time is a time::T,
 * so /m/math is imported: its units ({@code second}, {@code millis}) resolve there.
 */
public class TimeoutInstTest extends AbstractInstSetTest {

    public TimeoutInstTest() {
        super(mathInstSet::new);
    }

    /**
     * The rows are {@code code % what it answers}. A time with a unit is converted to milliseconds
     * ({@code second::1.0} is a thousand). The lhs is fed one obj at a time, each with the whole time, and an inner
     * timeout that runs out is caught inside the outer one, whose time it does not touch.
     */
    @ParameterizedTest
    @Timeout(30)
    @CsvSource(delimiter = '%', value = {
            "1.timeout(plus(1),second::1.0)                                    % 2",
            "1.timeout(plus(1).mult(3),millis::1000.0)                         % 6",
            "{1,2,3}.timeout(plus(1),second::1.0)                              % {2,3,4}",
            "1.timeout({0,3}plus(1),second::1.0)                               % {1,2,3,4}",
            "1.timeout({*}plus(0),millis::100.0)                               % <ERROR>",
            "1.timeout({*}plus(0),millis::100.0).catch(5)                      % 5",
            "1.timeout({*}plus(0),millis::100.0).catch(5).plus(1)              % 6",
            "1.timeout(timeout({*}plus(0),millis::100.0).catch(7),second::5.0) % 7",
    })
    public void testATimeoutAnswersInTimeOrFails(final String code, final String expected) {
        checkCodeParseApply(LOG, code, expected);
    }

    /**
     * The alarm's interrupt must not outlive the timeout: the thread that evaluated it, a pooled one most often, goes on
     * to evaluate the next program uninterrupted. An endless walk ends after its time, and soon after.
     */
    @Test
    @Timeout(30)
    public void testATimeoutEndsOnTimeAndLeavesNoInterruptBehind() {
        final long start = System.nanoTime();
        final Obj cut = Machine.parse("1.timeout({*}plus(0),millis::200.0)").apply(noobj());
        final long elapsed = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(cut.isFail(), () -> "an endless walk under a timeout answers a fail: " + cut);
        assertTrue(elapsed >= 200L && elapsed < 5_000L, () -> "it ends after its 200 ms, and soon after: " + elapsed + " ms");
        assertFalse(Thread.currentThread().isInterrupted(), "the alarm's interrupt must not outlive the timeout");
        checkCodeParseApply(LOG, "1.plus(1)", "2");
    }
}
