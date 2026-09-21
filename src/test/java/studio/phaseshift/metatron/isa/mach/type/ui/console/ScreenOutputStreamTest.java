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

package studio.phaseshift.metatron.isa.mach.type.ui.console;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The stdout proxy: which bytes are handed on, when, and what waits.
 *
 * <p>What matters is that complete lines travel as they complete, that nothing is
 * lost when a flush is the last thing a writer does, and that a character split
 * across two writes is never decoded in halves — a screen full of replacement
 * characters is how that failure shows up.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ScreenOutputStreamTest extends AbstractMetatronTest {

    @ParameterizedTest()
    @CsvSource(value = {
            "a\\nb\\n % a\\nb\\n % one write up to the last newline travels as one call",
            "ab\\n   % ab\\n    % a single line travels with its newline",
            "a\\nb   % a\\n     % text after the last newline waits for the next write",
            "\\n     % \\n       % a bare newline still counts as a line",
    }, delimiter = '%')
    void testOnlyCompleteLinesTravel(final String written, final String expected, final String description) {
        final List<String> got = new ArrayList<>();
        final ScreenOutputStream stream = new ScreenOutputStream(got::add);
        write(stream, decode(written));
        assertEquals(1, got.size(), description + ": calls");
        assertEquals(decode(expected), got.get(0), description);
    }

    @Test
    void testAPartialLineWaitsForItsNewline() {
        final List<String> got = new ArrayList<>();
        final ScreenOutputStream stream = new ScreenOutputStream(got::add);
        write(stream, "half a line");
        assertEquals(List.of(), got, "a line without a newline is not delivered yet");
        write(stream, " and the rest\n");
        assertEquals(List.of("half a line and the rest\n"), got, "the two writes are one line");
    }

    @Test
    void testAFlushDeliversAPartialLine() {
        final List<String> got = new ArrayList<>();
        final ScreenOutputStream stream = new ScreenOutputStream(got::add);
        write(stream, "prompt> ");
        stream.flush();
        assertEquals(List.of("prompt> "), got, "a flush is a writer saying that is all there is");
        stream.flush();
        assertEquals(List.of("prompt> "), got, "a second flush with nothing pending delivers nothing");
    }

    /**
     * A multi-byte character written in pieces must not be decoded until its last
     * byte is in: UTF-8 puts no character across a newline, so waiting for the line
     * is enough to keep it whole.
     */
    @Test
    void testACharacterSplitAcrossWritesIsNotDecodedInHalves() throws IOException {
        final List<String> got = new ArrayList<>();
        final ScreenOutputStream stream = new ScreenOutputStream(got::add);
        final byte[] glyph = "日本".getBytes(StandardCharsets.UTF_8);
        stream.write(glyph, 0, 1);
        stream.write(glyph, 1, 2);
        assertEquals(List.of(), got, "half a character is not delivered");
        stream.write(glyph, 3, glyph.length - 3);
        stream.write('\n');
        assertEquals(List.of("日本\n"), got, "the whole character arrives once its bytes do");
    }

    /** {@code \n} in a table cell is a newline; everything else is literal. */
    private static String decode(final String text) {
        return null == text ? null : text.replace("\\n", "\n");
    }

    private static void write(final ScreenOutputStream stream, final String text) {
        try {
            stream.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new AssertionError(e);
        }
    }
}
