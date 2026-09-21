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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The screen's content model: how text becomes rows, what a viewport of a given
 * height shows, and how a reader keeps their place while rows are still arriving.
 *
 * <p>Rows are written in these tables as {@code a|b|c}, an empty row as
 * {@code ~}, and no rows at all as {@code <none>}.  In an input cell {@code "\n"}
 * is a newline and {@code <empty>} is the empty string.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ScreenBufferTest extends AbstractMetatronTest {

    // ── text becomes rows ──────────────────────────────────────────

    @ParameterizedTest()
    @CsvSource(value = {
            "a\\nb     % a|b      % a newline closes a row and opens the next",
            "a\\n      % a        % a trailing newline closes a row without adding a blank one",
            "a         % a        % text with no newline is one row",
            "\"\\n\"    % ~        % a lone newline is one blank row",
            "a\\n\\nb   % a|~|b    % an empty segment between newlines is a blank row",
            "a\\nb\\nc  % a|b|c    % three rows",
            "<empty>   % <none>   % empty text adds nothing",
    }, delimiter = '%', quoteCharacter = '"')
    void testAppendSplitsTextIntoRows(final String text, final String expected, final String description) {
        final ScreenBuffer buffer = new ScreenBuffer(100);
        buffer.append(decode(text));
        assertEquals(expected, all(buffer), description);
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "met       % atron % metatron  % a fragment continues the open row",
            "\"met\\n\"  % atron % met|atron % a closed row starts a new one",
    }, delimiter = '%', quoteCharacter = '"')
    void testAFragmentContinuesTheOpenRow(final String first, final String second,
                                          final String expected, final String description) {
        final ScreenBuffer buffer = new ScreenBuffer(100);
        buffer.append(decode(first));
        buffer.append(decode(second));
        assertEquals(expected, all(buffer), description);
    }

    // ── the viewport ───────────────────────────────────────────────

    @ParameterizedTest()
    @CsvSource(value = {
            "3 % 3 % a|b|c     % content that fills the viewport is shown whole",
            "3 % 5 % ~|~|a|b|c % a partly filled screen pads above, so the newest row keeps the last row",
            "3 % 2 % b|c       % a following viewport shows the newest rows, not the first",
            "5 % 2 % d|e       % ...whatever the content length",
            "0 % 3 % ~|~|~     % an empty buffer shows blank rows",
    }, delimiter = '%')
    void testVisibleIsExactlyTheRequestedHeight(final int appended, final int viewport,
                                                final String expected, final String description) {
        final ScreenBuffer buffer = new ScreenBuffer(100);
        for (int i = 0; i < appended; i++) buffer.appendRow(String.valueOf((char) ('a' + i)));
        assertEquals(expected, show(buffer.visible(viewport)), description);
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "0 % 3 % 0 % an empty buffer has nothing to scroll",
            "2 % 3 % 0 % content shorter than the viewport has nothing to scroll",
            "5 % 2 % 3 % five rows in a two-row viewport have three rows of travel",
            "5 % 5 % 0 % content that fills the viewport has nothing to scroll",
            "5 % 7 % 0 % a viewport bigger than the content has nothing to scroll",
    }, delimiter = '%')
    void testMaxScroll(final int rows, final int viewport, final int expected, final String description) {
        final ScreenBuffer buffer = new ScreenBuffer(100);
        for (int i = 0; i < rows; i++) buffer.appendRow(String.valueOf((char) ('a' + i)));
        assertEquals(expected, buffer.maxScroll(viewport), description);
    }

    // ── follow, and holding a place ────────────────────────────────

    @ParameterizedTest()
    @CsvSource(value = {
            "abcde % 2 % 1 % d|e % c|d % one row back shows the row before",
            "abcde % 2 % 2 % d|e % b|c % two rows back",
            "abcde % 2 % 9 % d|e % a|b % scrolling past the top clamps to the first window",
            "abc   % 5 % 1 % ~|~|a|b|c % ~|~|a|b|c % a viewport with nothing to scroll cannot be scrolled back",
    }, delimiter = '%')
    void testViewportFollowsTheTailUntilScrolledBack(final String content, final int viewport,
                                                     final int back, final String atTail,
                                                     final String scrolled, final String description) {
        final ScreenBuffer buffer = rows(content);
        assertEquals(atTail, show(buffer.visible(viewport)), description + ": at the tail");
        buffer.scrollBy(-back, viewport);
        assertEquals(scrolled, show(buffer.visible(viewport)), description + ": scrolled back");
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "abcde % 2 % 1 % c|d % rows arriving below never move a reader holding a place",
            "abcde % 2 % 2 % b|c % ...one row further back either",
    }, delimiter = '%')
    void testScrollingBackPinsAnAbsoluteRow(final String content, final int viewport, final int back,
                                            final String expected, final String description) {
        final ScreenBuffer buffer = rows(content);
        buffer.scrollBy(-back, viewport);
        assertEquals(expected, show(buffer.visible(viewport)), description + ": before new rows");
        buffer.appendRow("f");
        buffer.appendRow("g");
        assertEquals(expected, show(buffer.visible(viewport)), description + ": after new rows");
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "abcde % 2 % 0 % false % holding the place you are already at is not following",
            "abcde % 2 % 1 % true  % scrolling forward by the row you gave up returns to the tail",
            "abcde % 2 % 3 % true  % scrolling back down to the tail resumes the follow",
            "abcde % 2 % 9 % true  % scrolling back past the end lands on the tail and follows",
    }, delimiter = '%')
    void testReturningToTheTailResumesFollow(final String content, final int viewport, final int forward,
                                             final boolean expected, final String description) {
        final ScreenBuffer buffer = rows(content);
        buffer.scrollBy(-1, viewport);
        buffer.scrollBy(forward, viewport);
        assertEquals(expected, buffer.following(), description);
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "abcde % 2 % 1 % one row back is history, not the tail",
            "abcde % 2 % 2 % ...as is two",
            "abcde % 2 % 3 % ...and as is the first window",
        }, delimiter = '%')
    void testScrollingBackStopsFollowing(final String content, final int viewport, final int back,
                                         final String description) {
        final ScreenBuffer buffer = rows(content);
        buffer.scrollBy(-back, viewport);
        assertEquals(false, buffer.following(), description);
    }

    // ── retention ──────────────────────────────────────────────────

    @ParameterizedTest()
    @CsvSource(value = {
            "4 % 3 % c|d|e  % 4 % the cap keeps the newest rows and drops the oldest",
            "2 % 3 % ~|d|e  % 2 % the cap is exactly the retained size",
    }, delimiter = '%')
    void testRetentionCapDropsTheOldest(final int capacity, final int viewport, final String expected,
                                        final int expectedSize, final String description) {
        final ScreenBuffer buffer = new ScreenBuffer(capacity);
        for (final String row : List.of("a", "b", "c", "d", "e")) buffer.appendRow(row);
        assertEquals(expectedSize, buffer.size(), description + ": retained size");
        assertEquals(expected, show(buffer.visible(viewport)), description);
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "4   % 1 % 2 % 1 % b|c % b|c % aging a row out does not slide the reader's place",
            "100 % 2 % 2 % 1 % b|c % b|c % with no trim to compensate for, nothing moves either",
            "3   % 1 % 2 % 1 % b|c % c|d % the row held is the row aged out, so the window shows the oldest survivor",
    }, delimiter = '%')
    void testRetentionKeepsTheReaderAnchored(final int capacity, final int appended, final int viewport,
                                             final int back, final String before, final String after,
                                             final String description) {
        final ScreenBuffer buffer = new ScreenBuffer(capacity);
        for (final String row : List.of("a", "b", "c", "d")) buffer.appendRow(row);
        buffer.scrollBy(-back, viewport);
        assertEquals(before, show(buffer.visible(viewport)), description + ": before the trim");
        for (int i = 0; i < appended; i++) buffer.appendRow(String.valueOf((char) ('e' + i)));
        assertEquals(after, show(buffer.visible(viewport)), description + ": after the trim");
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "abcde % 2 % 2 % clearing empties the screen and follows again",
    }, delimiter = '%')
    void testClearEmptiesAndFollows(final String content, final int viewport, final int back,
                                    final String description) {
        final ScreenBuffer buffer = rows(content);
        buffer.scrollBy(-back, viewport);
        buffer.clear();
        assertEquals(0, buffer.size(), description + ": size");
        assertEquals(List.of("", ""), buffer.visible(viewport), description + ": blank rows");
        assertEquals(true, buffer.following(), description + ": follow");
    }

    // ── helpers ────────────────────────────────────────────────────

    private static ScreenBuffer rows(final String content) {
        final ScreenBuffer buffer = new ScreenBuffer(100);
        for (final String row : content.split("")) buffer.appendRow(row);
        return buffer;
    }

    /** {@code \n} in a table cell is a newline, {@code <empty>} the empty string. */
    private static String decode(final String text) {
        return null == text ? null : "<empty>".equals(text) ? "" : text.replace("\\n", "\n");
    }

    /** Every retained row — a viewport exactly as tall as the content. */
    private static String all(final ScreenBuffer buffer) {
        return show(buffer.visible(buffer.size()));
    }

    /** Rows as a table cell: {@code a|b|c}, an empty row as {@code ~}, none as {@code <none>}. */
    private static String show(final List<String> rows) {
        if (rows.isEmpty()) return "<none>";
        return String.join("|", rows.stream().map(r -> r.isEmpty() ? "~" : r).toList());
    }
}
