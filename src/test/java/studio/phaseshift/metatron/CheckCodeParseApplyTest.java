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

package studio.phaseshift.metatron;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The harness checks itself: an {@code <ERROR>} row of {@link #checkCodeParseApply(
 * studio.phaseshift.metatron.isa.m.mach.type.ui.graphitty.GraphittyLogger, String, String)} passes only when the code
 * fails — by a throw, or by a {@code fail::T} in its result stream. A value, a stream of values, or noobj must fail
 * the row. That branch once only logged, so every {@code <ERROR>} row through it passed whatever the code did.
 */
public class CheckCodeParseApplyTest extends AbstractMetatronTest {

    @ParameterizedTest
    @CsvSource(value = {
            // a fail::T in the result stream is the failure an <ERROR> row expects
            "3.as(lst::T)              % true",
            // so is source the parser cannot finish: it hands back a fail::T rather than a code
            "1-<[_,_                   % true",
            // a value is not a failure ...
            "1.plus(2)                 % false",
            // ... nor is a stream of values ...
            "{1,2,3}.plus(10)          % false",
            // ... nor is nothing at all
            "noobj                     % false",
    }, delimiter = '%')
    public void testAnErrorRowAssertsAFailure(final String code, final boolean fails) {
        if (fails)
            checkCodeParseApply(LOG, code, "<ERROR>");
        else
            assertThrows(AssertionError.class, () -> checkCodeParseApply(LOG, code, "<ERROR>"));
    }
}
