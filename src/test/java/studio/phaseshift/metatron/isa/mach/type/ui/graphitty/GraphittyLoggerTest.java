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

package studio.phaseshift.metatron.isa.mach.type.ui.graphitty;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.DEBUG;

/**
 * A payload or exception message carrying a percent sign is literal text, never a
 * format template. Formatting it raises {@link java.util.IllegalFormatException},
 * and an escaping formatter exception surfaces at the transport as a bogus protocol
 * error — this is the bug class that produced "error sending mcp response:
 * Conversion = '`'" and "MissingFormatArgumentException: Format specifier '%s'"
 * on the wsSpace MCP handler.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class GraphittyLoggerTest extends AbstractMetatronTest {

    private static final GraphittyLogger LOG = Graphitty.log(GraphittyLoggerTest.class);

    /**
     * Neither {@code status()} nor {@code log()} may format a message twice: with no
     * arguments a message is literal text, and the line {@code status()} already
     * formatted (its arguments spliced in) must not be re-formatted on its way
     * through {@code log()}.
     */
    @ParameterizedTest()
    @CsvSource(quoteCharacter = '"', delimiter = '|', value = {
            "sent %s to %s              | {text: use %s and %d}    | mcp response payload carrying percent tokens",
            "error sending %s to %s: %s | Conversion = `           | percent followed by a backtick",
            "dropping session: %s       | MissingFormatArgumentException: Format specifier '%s' | exception text carrying a percent token",
            "100% of the space is live  | noobj                    | bare percent in the message itself",
            "reading %d tokens from %s  | 128                      | fewer arguments than specifiers"})
    public void testStatusAndLogSurvivePercentContent(final String format, final String arg, final String desc) {
        final String literal = format + " " + arg;
        assertDoesNotThrow(() -> LOG.status(DEBUG, format, arg), desc + " [status with args]");
        assertDoesNotThrow(() -> LOG.log(DEBUG, format, arg), desc + " [log with args]");
        assertDoesNotThrow(() -> LOG.status(DEBUG, literal), desc + " [status, no args]");
        assertDoesNotThrow(() -> LOG.log(DEBUG, literal), desc + " [log, no args]");
    }

    /**
     * Arguments are values: a percent token inside one reaches the line verbatim and
     * is never treated as a specifier, nor silently deleted by an argument sanitizer.
     */
    @ParameterizedTest()
    @CsvSource(quoteCharacter = '"', delimiter = '|', value = {
            "loaded %s tokens | a %s and a %d    | loaded a %s and a %d tokens | percent tokens inside an argument are values",
            "reading %s       | 100% coverage     | reading 100% coverage       | percent inside an argument reaching a template"})
    public void testLogArgumentsAreValues(final String format, final String arg, final String expected, final String desc) {
        final String line = Graphitty.strip(LOG.localError(format, arg).orElse("logging disabled"));
        assertEquals("[" + GraphittyLoggerTest.class.getSimpleName() + "] " + expected, line, desc);
    }

    /**
     * A malformed template — a literal percent next to a specifier, or an argument
     * count below the specifier count — degrades to the text with the arguments
     * appended rather than throwing out of the logger.
     */
    @ParameterizedTest()
    @CsvSource(quoteCharacter = '"', delimiter = '|', value = {
            "compiled 100% of %s  | 12 files | compiled 100% of %s 12 files | literal percent next to a specifier",
            "mismatched %s and %s | one      | mismatched %s and %s one     | fewer arguments than specifiers"})
    public void testMalformedTemplateDegradesToText(final String format, final String arg, final String expected, final String desc) {
        final String line = Graphitty.strip(LOG.localError(format, arg).orElse("logging disabled"));
        assertEquals("[" + GraphittyLoggerTest.class.getSimpleName() + "] " + expected, line, desc);
    }
}
