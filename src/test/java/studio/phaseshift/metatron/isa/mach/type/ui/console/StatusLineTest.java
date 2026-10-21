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

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.web.space.ws.WireStats;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

/*
 * The status banner: a message never replaces the one before it — it is pushed onto the
 * head of the banner and the messages already there shift right, divided by a dot.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class StatusLineTest extends AbstractMetatronTest {

    /** The banner divides messages with a bullet, newest leftmost. */
    private static final String DOT = " • ";
    /** The line's colors in the banner's normal state: yellow on blue. */
    private static final String FORE = "y";
    private static final String BACK = "b";

    /** The banner is class-level state shared by every status line, so each test starts blank. */
    @BeforeEach
    void clearBannerBefore() {
        StatusLine.message(str(""));
        WireStats.reset();
    }

    @AfterEach
    void clearBannerAfter() {
        StatusLine.message(str(""));
        WireStats.reset();
    }

    /** What a terminal shows of the banner's markup: the envelope, then the messages. */
    private static String visibleBanner() {
        return Graphitty.strip(StatusLine.bannerMarkup(FORE, BACK));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "monad halted                    % monad halted                             % a lone message stands alone",
            "monad halted;cycle 3 spun       % cycle 3 spun • monad halted              % a new message shifts the old one right",
            "monad halted;cycle 3 spun;void  % void • cycle 3 spun • monad halted        % the banner scrolls rightward, newest leftmost",
    }, delimiter = '%')
    void testMessageShiftsTheBannerRight(final String posted, final String banner, final String description) {
        for (final String message : posted.split(";"))
            StatusLine.message(str(message.trim()));
        assertEquals("✉️  " + banner, visibleBanner(), description);
    }

    /**
     * The wire's gauge: the three counts that replaced bytes-in/out, as the bar renders them.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "0  % 0 % 0 % ⇄0 ↓0 ↑0   % an idle wire reads as zeros, not as an error",
            "12 % 8 % 4 % ⇄12 ↓8 ↑4  % trips, arrivals and departures are counted separately",
    }, delimiter = '%')
    void testNetGauge(final long trips, final long received, final long sent, final String expected, final String description) {
        for (int i = 0; i < trips; i++)
            WireStats.roundTrip(5);
        for (int i = 0; i < received; i++)
            WireStats.received("peer", null);
        for (int i = 0; i < sent; i++)
            WireStats.sent("peer", null);
        assertEquals(expected, Graphitty.strip(StatusLine.netMarkup("y")), description);
    }

    /**
     * A wire that answers is the normal state and stays quiet; a timed-out one says so in color, without changing
     * the counts.
     */
    @Test
    void shouldTintTheTripCountOnlyWhenTheWireHasTimedOut() {
        assertFalse(StatusLine.netMarkup("y").contains("{{r}}"), "an answering wire is quiet");
        WireStats.timeout();
        assertTrue(StatusLine.netMarkup("y").contains("{{r}}"), "a timeout is the one thing worth a color");
        assertEquals("⇄0 ↓0 ↑0", Graphitty.strip(StatusLine.netMarkup("y")),
                "the count is unchanged — only its color is");
    }

    @Test
    void shouldPutTheNewestMessageInBoldAndStepEveryMessageBehindItDown() {
        StatusLine.message(str("monad halted"));
        StatusLine.message(str("cycle 3 spun"));
        assertEquals("{{[g]}}✉️ {{[b]}} {{Y&[b]}}cycle 3 spun{{X&[b]}}{{y}} • monad halted",
                StatusLine.bannerMarkup(FORE, BACK),
                "the newest message wears the line's color turned up, and the divider plus everything it pushed right wear that color as the line has it");
    }

    @Test
    void shouldAccentAnErrorRedAndAnythingElseGreen() {
        StatusLine.message(str("monad 7 killed: error at /sys/thread"));
        assertEquals("✉️  monad 7 killed: error at /sys/thread", visibleBanner());
        StatusLine.message(str("monad 7 halted"));
        assertEquals("✉️  monad 7 halted • monad 7 killed: error at /sys/thread", visibleBanner());
    }

    @Test
    void shouldCollapseARepeatOfTheMessageAlreadyAtTheHead() {
        StatusLine.message(str("on_partial_response"));
        StatusLine.message(str("on_partial_response"));
        StatusLine.message(str("on_partial_response"));
        assertEquals("✉️  on_partial_response", visibleBanner(),
                "the same sighting again is not a new message");
        StatusLine.message(str("on_tool_execute: sqr(3) => 9"));
        StatusLine.message(str("on_partial_response"));
        assertEquals("✉️  on_partial_response • on_tool_execute: sqr(3) => 9 • on_partial_response", visibleBanner(),
                "only a repeat of the head collapses — an older message re-posted is a new sighting");
    }

    @Test
    void shouldKeepOnlyTheNewestMessages() {
        for (int i = 1; i <= StatusLine.MESSAGE_HISTORY + 3; i++)
            StatusLine.message(str("cycle %d".formatted(i)));
        final String expected = IntStream.iterate(StatusLine.MESSAGE_HISTORY + 3, i -> i - 1).limit(StatusLine.MESSAGE_HISTORY)
                .mapToObj("cycle %d"::formatted).reduce((a, b) -> a + DOT + b).orElseThrow();
        assertEquals("✉️  " + expected, visibleBanner(), "the oldest messages scroll off the tail");
    }

    @Test
    void shouldClearTheBannerOnAnEmptyMessage() {
        StatusLine.message(str("monad halted"));
        StatusLine.message(str(""));
        assertEquals("✉️  ", visibleBanner());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "monad halted  % 12 % monad halted % a line that already fits is left whole",
            "monad halted  % 5  % monad        % the banner's tail falls off the right edge",
            "monad halted  % 1  % m            % even the last column of the banner is used",
    }, delimiter = '%')
    void testClipToTheColumnsLeftOnTheLine(final String text, final int columns, final String clipped, final String description) {
        final AttributedString banner = AttributedString.fromAnsi(text);
        assertEquals(clipped, StatusLine.clip(banner, columns).toString(), description);
        assertEquals(Math.min(columns, text.length()), StatusLine.clip(banner, columns).columnLength(), description);
    }

    @Test
    void shouldClipToNothingWhenTheLineHasNoRoomLeft() {
        assertEquals(0, StatusLine.clip(AttributedString.fromAnsi("monad halted"), 0).columnLength(),
                "a full line leaves the banner nothing to draw into");
        assertEquals(0, StatusLine.clip(AttributedString.fromAnsi("monad halted"), -7).columnLength(),
                "widgets wider than the terminal leave the banner nothing to draw into");
    }

    @Test
    void shouldKeepTheStyleOfTheColumnsThatSurviveClipping() {
        final AttributedString banner = AttributedString.fromAnsi(Graphitty.string("{{r}}monad 7 killed{{X}} the rest"));
        final AttributedString clipped = StatusLine.clip(banner, 11);
        assertEquals("monad 7 kil", clipped.toString());
        assertEquals(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED), clipped.styleAt(0),
                "clipping a banner keeps the color it was posted with");
    }
}
