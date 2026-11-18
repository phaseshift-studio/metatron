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
import studio.phaseshift.metatron.util.MTronException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * How {@link Training.Extractor} renders a template. A hole that names a parameter takes that column's value; a hole
 * shaped like a parameter name that names none of them fails the extraction, since the template was written for
 * another signature, and so does one missing a brace; any other {@code {{{...}}}} is mtron's own str template and
 * is kept as written. The fixtures
 * below are methods the extractor reads, never tests JUnit runs (they carry no {@code @ParameterizedTest}).
 */
public class TrainingTest extends AbstractMetatronTest {

    @Training(instruction = "what does {{{code}}} evaluate to?", input = "{{{code}}}", output = "{{{expected}}}")
    @CsvSource(value = {"7.mult(6) % 42"}, delimiter = '%')
    private static void namesItsColumns(final String code, final String expected) {
    }

    @Training(input = "{{{code}}}.map(\"answer: {{{mult(6)}}}\")", output = "{{{expected}}}")
    @CsvSource(value = {"7 % \"answer: 42\""}, delimiter = '%')
    private static void keepsAnMtronStrTemplate(final String code, final String expected) {
    }

    @Training(input = "{{{code}}}.map(\"nested: {{{ 1 + {{{ 2 }}} }}}\")", output = "{{{expected}}}")
    @CsvSource(value = {"7 % \"nested: 3\""}, delimiter = '%')
    private static void keepsANestedMtronStrTemplate(final String code, final String expected) {
    }

    // the defect that left {{{code}}} in the corpus: a (code, expected) template on a (datetime, expected) method
    @Training(input = "{{{code}}}", output = "{{{expected}}}")
    @CsvSource(value = {"datetime::<//2024.12:25/09/00/00/000?tz=-0500> % 'Wednesday, December 25, 2024 09:00:00 AM -05:00'"}, delimiter = '%')
    private static void namesAColumnItLacks(final String datetime, final String expected) {
    }

    // a typo in the instruction alone: input and output still verify, so the verifier would pass it into training
    @Training(instruction = "what does {{{ cod }}} evaluate to?", input = "{{{code}}}", output = "{{{expected}}}")
    @CsvSource(value = {"7.mult(6) % 42"}, delimiter = '%')
    private static void misspellsAColumnInItsInstruction(final String code, final String expected) {
    }

    @Training(instruction = "what does {{{ code }}} evaluate to?", input = "{{{ code }}}", output = "{{{expected}}}")
    @CsvSource(value = {"7.mult(6) % 42"}, delimiter = '%')
    private static void spacesAColumnHole(final String code, final String expected) {
    }

    // a hole a brace short matches no hole pattern, so it would reach the corpus as text (RecTest carried one)
    @Training(instruction = "what does {{{code}} evaluate to?", input = "{{{code}}}", output = "{{{expected}}}")
    @CsvSource(value = {"7.mult(6) % 42"}, delimiter = '%')
    private static void closesAHoleABraceShort(final String code, final String expected) {
    }

    @Training(instruction = "what does {{code}}} evaluate to?", input = "{{{code}}}", output = "{{{expected}}}")
    @CsvSource(value = {"7.mult(6) % 42"}, delimiter = '%')
    private static void opensAHoleABraceShort(final String code, final String expected) {
    }

    @ParameterizedTest
    @CsvSource(value = {
            "namesItsColumns                  % 7.mult(6)",
            "keepsAnMtronStrTemplate          % 7.map(\"answer: {{{mult(6)}}}\")",
            "keepsANestedMtronStrTemplate     % 7.map(\"nested: {{{ 1 + {{{ 2 }}} }}}\")",
            "namesAColumnItLacks              % <ERROR>",
            "misspellsAColumnInItsInstruction % <ERROR>",
            "spacesAColumnHole                % 7.mult(6)",
            "closesAHoleABraceShort           % <ERROR>",
            "opensAHoleABraceShort            % <ERROR>",
    }, delimiter = '%')
    void testRender(final String fixture, final String input) throws NoSuchMethodException {
        final Method method = TrainingTest.class.getDeclaredMethod(fixture, String.class, String.class);
        final CsvSource csv = method.getAnnotation(CsvSource.class);
        if ("<ERROR>".equals(input))
            assertThrows(MTronException.class, () -> Training.Extractor.from(method, csv));
        else
            assertEquals(input, Training.Extractor.from(method, csv).entries().getFirst().input());
    }
}
