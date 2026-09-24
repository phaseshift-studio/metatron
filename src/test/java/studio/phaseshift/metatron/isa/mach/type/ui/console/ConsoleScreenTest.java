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
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The screen as a whole: console markup in, rows out, and the two ways bytes come
 * back — the frame that keeps up, and the band that repairs what a widget covered.
 *
 * <p>Rows are asserted on their text ({@code Graphitty.strip}) so the tables read
 * as what a reader sees; styling is asserted where styling is the point.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ConsoleScreenTest extends AbstractMetatronTest {

    @ParameterizedTest()
    @CsvSource(value = {
            "alpha        % 10 % alpha      % plain output is one row",
            "a\\nb\\nc     % 10 % a|b|c      % newlines make rows",
            "a\\n          % 10 % a          % a trailing newline does not add a blank row",
            "{{y}}warm{{X}} % 10 % warm     % markup is resolved, not shown",
    }, delimiter = '%')
    void testAppendTurnsConsoleOutputIntoRows(final String markup, final int width,
                                              final String expected, final String description) {
        final ConsoleScreen screen = screen(3, width);
        screen.append(decode(markup));
        assertEquals(expected, plain(screen.visible()), description);
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "4  % abcdefgh    % abcd|efgh % a row longer than the screen wraps instead of running off it",
            "4  % abcd        % abcd      % a row exactly as wide as the screen does not wrap",
            "4  % abc         % abc       % ...nor does a shorter one",
            "10 % ab\\ncd     % ab|cd     % wrapping is per row, never across a newline",
    }, delimiter = '%')
    void testARowWrapsToTheRegionWidth(final int width, final String markup,
                                       final String expected, final String description) {
        final ConsoleScreen screen = screen(4, width);
        screen.append(decode(markup));
        assertEquals(expected, plain(screen.visible()), description);
    }

    /**
     * An interactive tool's frame arrives with its own cursor arithmetic — cursor-up,
     * line clears, absolute positioning.  Recorded verbatim, that arithmetic would run
     * mid-paint: a command inside a row moves the cursor while a frame is being written
     * and erases whatever the painter had just drawn.  The screen records the text and
     * the styling, and drops the commands.
     */
    @Test
    void testAToolFrameRecordsTextNotCommand() {
        final String warm = Graphitty.string("{{y}}");
        // a tool frame as the funnel receives it: cursor-up, then a line each of
        // erase-write and newline — three rows of content
        final String frame = "\033[3A"
                + "\033[2Kop|dom\033[0m\r\n"
                + "\033[2K" + warm + "dom|rng\033[0m\r\n"
                + "\033[2Kstatus\033[0m";
        final ConsoleScreen screen = screen(3, 40);
        screen.appendAnsi(frame);
        final List<String> rows = screen.visible();
        assertEquals(3, rows.size(), "a three-row screen shows exactly the frame's three rows");
        assertEquals("op|dom", Graphitty.strip(rows.get(0)), "line one is line one, command-free");
        assertEquals("dom|rng", Graphitty.strip(rows.get(1)), "line two is line two, command-free");
        for (final String row : rows) {
            final String readable = row.replace("\033", "<E>").replace("\007", "<BEL>");
            assertEquals(false, hasCommand(row), "no terminal command may survive into a stored row: " + readable);
        }
        assertEquals(true, rows.get(1).startsWith(warm), "the styling the row carried is kept");
    }

    /** True when a row still holds a terminal command — a cursor mover or a line clear. */
    private static boolean hasCommand(final String row) {
        int i = 0;
        while (i < row.length()) {
            if ('\033' != row.charAt(i)) {
                i++;
                continue;
            }
            int end = row.length();
            if (i + 1 < row.length() && '[' == row.charAt(i + 1)) {
                for (int j = i + 2; j < row.length(); j++)
                    if (row.charAt(j) >= 0x40 && row.charAt(j) <= 0x7e) {
                        end = j + 1;
                        break;
                    }
                final String seq = row.substring(i, end);
                if ('m' != seq.charAt(seq.length() - 1)) return true;
                i = end;
                continue;
            }
            if (i + 1 < row.length() && ']' == row.charAt(i + 1)) {  // OSC — the screen's links
                while (i < row.length() && 0x07 != row.charAt(i)) i++;
                continue;
            }
            i += 2;
        }
        return false;
    }

    /**
     * The point of storing rows rather than text: a wrapped row keeps the styling
     * that was running where it broke, so it can be painted on its own.
     */
    @Test
    void testStylingSurvivesARowBreak() {
        final String warm = Graphitty.string("{{y}}");
        final ConsoleScreen screen = screen(2, 3);
        screen.append("{{y}}abcdef{{X}}");
        final List<String> rows = screen.visible();
        assertEquals(2, rows.size(), "a six-column word in a three-column screen is two rows");
        assertEquals("abc", Graphitty.strip(rows.get(0)), "first row");
        assertEquals("def", Graphitty.strip(rows.get(1)), "second row");
        assertEquals(true, rows.get(1).startsWith(warm),
                "the second row re-asserts the colour the first row left running");
    }

    // ── bytes out ──────────────────────────────────────────────────

    @Test
    void testAFramePaintsOnceThenGoesQuiet() {
        final ConsoleScreen screen = screen(3, 10);
        screen.append("a\nb\nc");
        final String first = screen.frame();
        assertNotEquals("", first, "the first frame has nothing on screen to trust");
        assertEquals("", screen.frame(), "an unchanged frame writes nothing");
    }

    /**
     * What a floating widget owes the screen: it drew over rows and erased its old
     * box with blanks, so the screen believes those rows are current even though
     * what is actually there is a hole.  A band is what puts the text back.
     */
    @Test
    void testABandRepairsRowsTheFrameWillNotTouch() {
        final ConsoleScreen screen = screen(3, 10);
        screen.append("a\nb\nc");
        screen.frame();
        assertEquals("", screen.frame(), "the frame is quiet while nothing changes");
        final String repair = screen.band(1, 3);
        assertNotEquals("", repair, "a band repaints whether or not the frame thinks it must");
        assertEquals(true, repair.contains("\033[1;1H"), "the band starts at the row asked for");
        assertEquals(true, repair.contains("\033[3;1H"), "and covers the last row of the range");
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "0  % 0  % <empty> % a band above the region has nothing to repair",
            "4  % 9  % <empty> % a band below the region has nothing to repair",
            "9  % 2  % <empty> % an inverted band has nothing to repair",
    }, delimiter = '%')
    void testABandOutsideTheRegionIsEmpty(final int from, final int to,
                                          final String expected, final String description) {
        final ConsoleScreen screen = screen(3, 10);
        screen.append("a\nb\nc");
        screen.frame();
        assertEquals(decode(expected), screen.band(from, to), description);
    }

    @Test
    void testALayoutChangeForcesTheNextFrameToRepaint() {
        final ConsoleScreen screen = screen(3, 10);
        screen.append("a\nb\nc");
        screen.frame();
        assertEquals(false, screen.layout(1, 3, 10), "the same geometry is not a change");
        assertEquals("", screen.frame(), "so the frame stays quiet");
        assertEquals(true, screen.layout(1, 2, 10), "a narrower region is a change");
        assertNotEquals("", screen.frame(), "and what was on screen is no longer known");
    }

    // ── reading history ────────────────────────────────────────────

    @Test
    void testScrollingBackLeavesTheTailAndReturningResumesIt() {
        final ConsoleScreen screen = screen(2, 10);
        screen.append("a\nb\nc\nd\ne");
        assertEquals("d|e", plain(screen.visible()), "a fresh screen shows the newest rows");
        assertEquals(true, screen.following(), "and is following");
        screen.scrollBy(-1);
        assertEquals("c|d", plain(screen.visible()), "one row back shows the row before");
        assertEquals(false, screen.following(), "which is history, not the tail");
        screen.scrollToTail();
        assertEquals("d|e", plain(screen.visible()), "returning to the tail shows the newest rows");
        assertEquals(true, screen.following(), "and follows again");
    }

    @Test
    void testHistoryIsRetainedPastTheViewport() {
        final ConsoleScreen screen = screen(2, 10);
        // the newline matters: append() has terminal semantics, so a line with no
        // newline stays open for the next append to continue
        for (int i = 0; i < 40; i++) screen.append("row-" + i + "\n");
        assertEquals(40, screen.size(), "every row is kept, not only the visible ones");
        screen.scrollBy(-38);
        assertEquals("row-0|row-1", plain(screen.visible()), "and the oldest rows are still reachable");
    }

    /**
     * The append path keeps the hyperlink the row was rendered with, so a click can read the
     * uri back out of the row the reader is pointing at — while what is painted to the
     * terminal has it removed, so the terminal reports the click instead of opening the uri.
     */
    @Test
    void testAStoredRowKeepsTheLinkThePaintedRowDrops() {
        final ConsoleScreen screen = screen(3, 60);
        screen.append("{{link}}/usr/dr/message/+{{/link}}");
        final String stored = screen.visible().get(screen.visible().size() - 1);
        assertEquals("/usr/dr/message/+", ScreenPainter.linkAt(stored, 2),
                "the stored row answers for the columns of its link");
        assertEquals(false, ScreenPainter.printable(stored, 60).contains("\033]8;"),
                "and the painted row is not a hyperlink");
    }

    // ── helpers ────────────────────────────────────────────────────

    /** A screen with a region of {@code rows} rows at the top of the terminal. */
    private static ConsoleScreen screen(final int rows, final int width) {
        final ConsoleScreen screen = new ConsoleScreen(100);
        screen.layout(1, rows, width);
        return screen;
    }

    /** The text a reader would see, joined for a table — the region's padding is not content. */
    private static String plain(final List<String> rows) {
        final List<String> shown = new java.util.ArrayList<>(rows.stream().map(Graphitty::strip).toList());
        // a partly filled screen pads ABOVE its content, so the newest row sits on the
        // last row of the region — the padding is not content either way
        while (!shown.isEmpty() && shown.get(0).isEmpty()) shown.remove(0);
        while (!shown.isEmpty() && shown.get(shown.size() - 1).isEmpty()) shown.remove(shown.size() - 1);
        return shown.isEmpty() ? "<empty>" : String.join("|", shown);
    }

    private static String decode(final String text) {
        return null == text ? null
                : "<empty>".equals(text) ? ""
                : text.replace("\\n", "\n");
    }
}
