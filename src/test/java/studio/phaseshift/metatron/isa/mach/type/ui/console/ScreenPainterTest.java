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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The screen's painter: which rows a frame repaints, and how a styled row is cut
 * to the width it has.
 *
 * <p>In these tables {@code <E>} is the escape character, {@code <R>} the reset a
 * clipped row always closes with, a window is {@code a|b|c}, an empty window is
 * {@code <empty>}, and "which rows a frame repainted" is the row numbers it moved
 * the cursor to — {@code <none>} when it wrote nothing at all.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ScreenPainterTest extends AbstractMetatronTest {

    private static final Pattern ROW = Pattern.compile("\033\\[(\\d+);1H");

    // ── cutting a row to its width ─────────────────────────────────

    @ParameterizedTest()
    @CsvSource(value = {
            "abc          % 5 % abc<R>          % a row narrower than the screen is painted whole",
            "abc          % 3 % abc<R>          % a row exactly as wide as the screen is painted whole",
            "abcdef       % 3 % abc<R>          % a wider row is cut at the screen edge",
            "abc          % 0 % <empty>         % no columns means nothing to paint",
            "<empty>      % 5 % <empty>         % an empty row paints nothing",
            "日本語です    % 4 % 日本<R>          % a wide glyph costs two columns",
            "日本         % 3 % 日<R>            % a wide glyph that does not fit is dropped, never split",
            "📥abc        % 3 % 📥a<R>           % an emoji is one glyph across two chars",
            "<E>[36mabc<R> % 2 % <E>[36mab<R>    % the colour of the part that survives is kept",
            "<E>[36m日本語  % 3 % <E>[36m日<R>    % ...and the cut is still a column cut inside a colour",
    }, delimiter = '%', quoteCharacter = '"')
    void testClipCutsByColumnAndKeepsStyling(final String ansi, final int width,
                                             final String expected, final String description) {
        assertEquals(decode(expected), ScreenPainter.clip(decode(ansi), width), description);
    }

    // ── which rows a frame repaints ────────────────────────────────

    @ParameterizedTest()
    @CsvSource(value = {
            "5 % 3 % 3 % a|b|c % 5,6,7   % a first frame has nothing on screen to trust, so it paints every row",
            "1 % 2 % 3 % a|b   % 1,2     % the region starts at the row it was told",
            "5 % 2 % 3 % a|b|c % 5,6     % a window longer than the region is ignored past its end",
    }, delimiter = '%')
    void testFirstFramePaintsEveryRow(final int top, final int rows, final int width,
                                      final String window, final String expected, final String description) {
        final ScreenPainter painter = new ScreenPainter(top, rows, width);
        assertEquals(expected, repainted(painter.frame(window(window))), description);
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "5 % 3 % 3 % a|b|c   % a|b|c      % <none>  % an unchanged frame writes nothing at all",
            "5 % 3 % 3 % a|b|c   % a|B|c      % 6       % only the row that changed is repainted",
            "5 % 3 % 3 % a|b|c   % b|c|d      % 5,6,7   % a shifted window moves every row it shows",
            "5 % 3 % 3 % a|b|c   % a          % 6,7     % a shorter window blanks the rows below it",
            "5 % 3 % 3 % a|b|c   % <empty>    % 5,6,7   % an empty window blanks the region",
            "5 % 3 % 3 % a|b|c   % a|b|c|d    % <none>  % rows past the end of the region are ignored",
            "5 % 1 % 1 % ab      % ab         % <none>  % a row too wide to have been painted whole still matches",
    }, delimiter = '%')
    void testLaterFramesPaintOnlyWhatChanged(final int top, final int rows, final int width,
                                             final String first, final String second,
                                             final String expected, final String description) {
        final ScreenPainter painter = new ScreenPainter(top, rows, width);
        painter.frame(window(first));
        assertEquals(expected, repainted(painter.frame(window(second))), description);
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "5 % 2 % 3 % a|b % 5,6 % forgetting the screen forces a full repaint",
    }, delimiter = '%')
    void testInvalidateForcesARepaint(final int top, final int rows, final int width,
                                      final String window, final String expected, final String description) {
        final ScreenPainter painter = new ScreenPainter(top, rows, width);
        painter.frame(window(window));
        painter.invalidate();
        assertEquals(expected, repainted(painter.frame(window(window))), description);
    }

    /** A repainting frame erases the tail of every row it writes, so nothing of the old row survives. */
    @ParameterizedTest()
    @CsvSource(value = {
            "5 % 3 % 3 % a|b|c % a|B|c   % 6      % 1 % the changed row is erased to the end of the line",
            "5 % 3 % 3 % a|b|c % <empty> % 5,6,7  % 3 % every blanked row is erased too",
    }, delimiter = '%')
    void testARepaintedRowIsErasedToTheEndOfTheLine(final int top, final int rows, final int width,
                                                    final String first, final String second,
                                                    final String expectedRows, final int expectedErases,
                                                    final String description) {
        final ScreenPainter painter = new ScreenPainter(top, rows, width);
        painter.frame(window(first));
        final String frame = painter.frame(window(second));
        assertEquals(expectedRows, repainted(frame), description + ": rows touched");
        assertEquals(expectedErases, erases(frame), description + ": rows erased");
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "5 % 2 % 3 % a|b|c % 2 % a window longer than the region is not remembered past its end",
    }, delimiter = '%')
    void testPaintedHoldsOneRowPerRegionRow(final int top, final int rows, final int width,
                                            final String window, final int expected,
                                            final String description) {
        final ScreenPainter painter = new ScreenPainter(top, rows, width);
        painter.frame(window(window));
        assertEquals(expected, painter.painted().size(), description);
    }

    // ── which uri a column is pointing at ──────────────────────────

    @ParameterizedTest()
    @CsvSource(value = {
            "{{link}}/m/obj/a{{/link}}          % 0  % /m/obj/a % the first column of the link",
            "{{link}}/m/obj/a{{/link}}          % 7  % /m/obj/a % the last column of the link",
            "{{link}}/m/obj/a{{/link}}          % 8  % <none>   % one column past it is not the link",
            "before {{link}}/m/obj/a{{/link}}   % 10 % /m/obj/a % the columns before it do not move it",
            "before {{link}}/m/obj/a{{/link}}   % 3  % <none>   % text before the link is text",
            "{{link}}/a/{{/link}}+{{link}}/b/{{/link}} % 4 % /b/ % a second link answers for its own columns",
            "{{link}}/a/{{/link}}+{{link}}/b/{{/link}} % 1 % /a/ % and the first for its own",
            "{{link}}a&b{{/link}}               % 0  % <none>   % text the furi parser rejects is not a link",
            "/m/obj/a                           % 2  % <none>   % a uri that was never marked is not a link",
    }, delimiter = '%')
    void testALinkIsFoundByTheColumnPointedAt(final String markup, final int column,
                                              final String expected, final String description) {
        final String row = Graphitty.string(markup);
        assertEquals("<none>".equals(expected) ? null : expected,
                ScreenPainter.linkAt(row, column), description);
    }

    // ── what is painted is not what is stored ──────────────────────

    @ParameterizedTest()
    @CsvSource(value = {
            "{{link}}/m/obj/a{{/link}}         % /m/obj/a         % 0         % /m/obj/a % a link is painted as its uri",
            "see {{link}}/m/obj/a{{/link}} now  % see /m/obj/a now % 6         % /m/obj/a % and the text around it is untouched",
            "{{link}}a&b{{/link}}              % a&b              % 0         % <none>   % text that is not a uri is painted plain",
    }, delimiter = '%')
    void testAPaintedRowCarriesNoHyperlink(final String markup, final String expected, final int probe,
                                           final String expectedUri, final String description) {
        final String row = Graphitty.string(markup);
        final String painted = ScreenPainter.printable(row, 60);
        assertEquals(false, painted.contains("\033]8;"),
                description + ": the terminal must not be handed a hyperlink — it would take the click itself");
        assertEquals(expected, Graphitty.strip(painted), description + ": the text is unchanged");
        assertEquals("<none>".equals(expectedUri) ? null : expectedUri, ScreenPainter.linkAt(row, probe),
                description + ": the stored row still resolves its link for the click");
    }

    // ── helpers ────────────────────────────────────────────────────    // ── helpers ────────────────────────────────────────────────────

    /** The row numbers a frame moved the cursor to, or {@code <none>} when it wrote nothing. */
    private static String repainted(final String frame) {
        if (null == frame || frame.isEmpty()) return "<none>";
        final Matcher matcher = ROW.matcher(frame);
        final StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            if (!sb.isEmpty()) sb.append(",");
            sb.append(matcher.group(1));
        }
        return sb.isEmpty() ? "<none>" : sb.toString();
    }

    /** How many rows a frame erased to end of line — one per row it wrote. */
    private static int erases(final String frame) {
        if (null == frame) return 0;
        int count = 0;
        for (int i = frame.indexOf("\033[K"); i >= 0; i = frame.indexOf("\033[K", i + 1)) count++;
        return count;
    }

    /** {@code a|b|c} is a window, {@code <empty>} is no rows at all. */
    private static List<String> window(final String spec) {
        return null == spec || spec.isEmpty() || "<empty>".equals(spec) ? List.of() : List.of(spec.split("\\|", -1));
    }

    /** {@code <E>} is escape, {@code <R>} the reset, {@code <empty>} the empty string. */
    private static String decode(final String text) {
        return null == text ? null
                : "<empty>".equals(text) ? ""
                : text.replace("<E>", "\033").replace("<R>", "\033[0m");
    }
}
