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

package studio.phaseshift.metatron.isa.m.sys.ui.graphitty;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.DEBUG;

/**
 * A payload or exception message carrying a percent sign is literal text, never a
 * format template. Formatting it raises {@link java.util.IllegalFormatException},
 * and an escaping formatter exception surfaces at the transport as a bogus protocol
 * error — this is the bug class that produced "error sending mcp response:
 * Conversion = '`'" and "MissingFormatArgumentException: Format specifier '%s'"
 * on the ws_server MCP handler.
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

    /**
     * {@code print} (and so {@code none}) must emit a string literally: its Graphitty
     * markup resolves, but mtron syntax highlighting is not layered on top — which is
     * how the integer-highlight yellow used to override an explicit {@code {{g}}} green
     * on a trailing number, and identifier-highlight blue used to tint the plain text.
     */
    private final List<String> captured = new ArrayList<>();

    @AfterEach
    void tearDown() {
        GraphittyLogger.registerAppendPaneWriter((id, msg) -> {
        });
        GraphittyLogger.setDefaultTargetPane(-1);
    }

    @Test
    void testNoneEmitsLiteralMarkupWithoutSyntaxHighlighting() {
        GraphittyLogger.registerAppendPaneWriter((id, msg) -> this.captured.add(msg));
        GraphittyLogger.setDefaultTargetPane(0);

        new GraphittyLogger().none("abc{{r}}bcd{{<3}}{{g}}467");

        assertEquals(1, this.captured.size(), "one message was written");
        final String msg = this.captured.get(0);
        assertTrue(msg.contains("\033[32m"), "{{g}} green reaches the line: " + msg.replace("\033", "<E>"));
        assertFalse(msg.contains("\033[33m"), "no integer-highlight yellow overrides it: " + msg.replace("\033", "<E>"));
        assertFalse(msg.contains("\033[34m"), "no identifier-highlight blue on the text: " + msg.replace("\033", "<E>"));
    }

    /**
     * The whole {@code print} instruction path, not just {@code none}: the string's
     * Graphitty markup resolves (colour and cursor move survive), but mtron syntax
     * highlighting is not layered on top — so an explicit {@code {{g}}} on a trailing
     * number stays green and the {@code {{<N}}} move is intact for the screen to apply.
     */
    @Test
    void testPrintInstructionEmitsLiteralMarkup() {
        GraphittyLogger.registerAppendPaneWriter((id, msg) -> this.captured.add(msg));
        GraphittyLogger.setDefaultTargetPane(0);

        ObjmtronSerializer.parse("print(\"abc{{r}}bcd{{<3}}{{g}}467\")").apply();

        assertEquals(1, this.captured.size(), "print wrote one message");
        final String msg = this.captured.get(0);
        assertTrue(msg.contains("\033[32m"), "{{g}} green reaches the terminal: " + msg.replace("\033", "<E>"));
        assertTrue(msg.contains("\033[3D"), "the {{<3}} cursor move survives for the screen to apply: " + msg.replace("\033", "<E>"));
        assertFalse(msg.contains("\033[33m"), "no integer-highlight yellow overrides it: " + msg.replace("\033", "<E>"));
        assertFalse(msg.contains("\033[34m"), "no identifier-highlight blue on the text: " + msg.replace("\033", "<E>"));
    }
}
