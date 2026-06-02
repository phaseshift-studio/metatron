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

package studio.phaseshift.metatron.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MTronExceptionTest extends AbstractMetatronTest {

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvSource(value = {
            "hello world                    | hello world                       | plain text passthrough",
            "text with literal %s           | text with literal %s              | bare %s format specifier",
            "result: %s and %d              | result: %s and %d                 | multiple format specifiers",
            "search results with %%s code   | search results with %s code       | escaped percent in text",
            "no format specifiers here      | no format specifiers here         | no format specifiers",
    }, delimiter = '|')
    void testOfWithNoArgs(final String input, final String expected, final String desc) {
        final MTronException e = assertDoesNotThrow(
                () -> MTronException.of((Object) input),
                () -> "of(Object) should not throw on: " + desc);
        assertEquals(expected, e.getMessage(), "message should match input for: " + desc);
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvSource(value = {
            "hello %s world | hello    | hello hello world             | standard %s substitution",
            "count: %s      | 42       | count: 42                     | %s with string arg",
    }, delimiter = '|')
    void testOfWithArgs(final String format, final String arg, final String expected, final String desc) {
        final MTronException e = assertDoesNotThrow(
                () -> MTronException.of(format, arg),
                () -> "of(format, arg) should not throw on: " + desc);
        assertEquals(expected, e.getMessage(), "formatted message mismatch for: " + desc);
    }
}
