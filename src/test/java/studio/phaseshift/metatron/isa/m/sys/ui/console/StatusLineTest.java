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

package studio.phaseshift.metatron.isa.m.sys.ui.console;

import org.jline.terminal.Size;
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
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.mach.type.compiler.Typer;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.sys.ui.tool.NetworkPanelTool;
import studio.phaseshift.metatron.isa.m.sys.ui.widget.FloatingSurface;
import studio.phaseshift.metatron.isa.m.sys.ui.widget.MiniWidget.SpanUI;
import studio.phaseshift.metatron.isa.m.sys.ui.widget.PanelWidget;
import studio.phaseshift.metatron.isa.m.sys.ui.widget.TreeWidget;
import studio.phaseshift.metatron.isa.m.web.space.ws.WireStats;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.STATUS;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
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

    /**
     * The banner divides messages with a bullet, newest leftmost.
     */
    private static final String DOT = " • ";
    /**
     * The line's colors in the banner's normal state: yellow on blue.
     */
    private static final String FORE = "y";
    private static final String BACK = "b";

    /**
     * The banner is class-level state shared by every status line, so each test starts blank.
     */
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

    /**
     * What a terminal shows of the banner's markup: the envelope, then the messages.
     */
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
        assertEquals(expected, Graphitty.strip(StatusLine.netMarkup("y", BACK)).trim(), description);
    }

    /**
     * The machine widget shows the PUBLISHED perspective's vid: local, whole when it fits, shortened from the left
     * when it does not (the tail is what identifies the frame).
     */
    @ParameterizedTest
    @CsvSource(value = {
            ".                                 % .                        % the root is one character",
            "/usr/marko/scratch                % /usr/marko/scratch       % a local frame that fits is shown whole",
            "/network/peer/ws/host/8555/mtron   % …/peer/ws/host/8555/mtron % a deep frame keeps its identifying tail",
    }, delimiter = '%')
    void testMachineText(final String vid, final String expected, final String description) {
        assertEquals(expected, StatusLine.machineText(f(vid)), description);
    }

    /**
     * A wire that answers is the normal state and stays quiet; a timed-out one says so in color, without changing
     * the counts.
     */
    @Test
    void shouldTintTheTripCountOnlyWhenTheWireHasTimedOut() {
        assertFalse(StatusLine.netMarkup("y", BACK).contains("{{r&"), "an answering wire is quiet");
        WireStats.timeout();
        assertTrue(StatusLine.netMarkup("y", BACK).contains("{{r&"), "a timeout is the one thing worth a color");
        assertEquals("⇄0 ↓0 ↑0", Graphitty.strip(StatusLine.netMarkup("y", BACK)).trim(),
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

    /**
     * The marquee draws only its newest bucket of messages — the retained window is for the history popup, and
     * building all of it into every repaint is work the bar can never show.
     */
    @Test
    void shouldShowOnlyTheNewestMessagesInTheMarquee() {
        for (int i = 1; i <= StatusLine.MESSAGE_HISTORY + 3; i++)
            StatusLine.message(str("cycle %d".formatted(i)));
        final String expected = IntStream.iterate(StatusLine.MESSAGE_HISTORY + 3, i -> i - 1)
                .limit(StatusLine.BANNER_MESSAGES)
                .mapToObj("cycle %d"::formatted).reduce((a, b) -> a + DOT + b).orElseThrow();
        assertEquals("✉️  " + expected, visibleBanner(),
                "the marquee draws its newest window, not the whole retained history");
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

    /**
     * THE FIRST SEGMENT IS THE TYPER SWITCH: a click on the bar's {@code [ T ]} segment — the typer indicator that
     * leads the line — cycles the check level, the same act as {@code alt+t}. A click on a row that is not the bar
     * is not the bar's to consume.
     */
    @Test
    void shouldCycleTheTyperWhenTheTypeSegmentIsClicked() {
        final Console console = new Console(rec(), f("/sys/console_status_test"));
        final Set<fURI> before = Typer.Helper.enabled();
        try {
            // a headless terminal reports no size, and a zero-width bar has nothing to click
            Console.getTerminal().setSize(new Size(120, 40));
            final StatusLine bar = console.getStatus();
            assertTrue(bar.clickable(), "the bar's segments answer a click from construction (no widget needed)");
            bar.compileWidgets();
            final int row = Console.getTerminal().getHeight();
            // the bar opens with "▎" then the " T " segment, so terminal column 2 is on the indicator
            assertFalse(console.clickAt(row - 1, 2), "a row above the bar is not a segment");
            assertTrue(console.clickAt(row, 2), "the [ T ] segment answers the click");
            assertNotEquals(before, Typer.Helper.enabled(), "the click cycles the typer, exactly as alt+t does");
        } finally {
            Typer.CHECKS.forEach(flag -> Typer.Helper.set(flag, before.contains(flag)));
            console.getFloatingSurface().clear();
            closeConsole(console);
        }
    }

    /**
     * THE SECOND SEGMENT IS THE WIRE: clicking the net gauge floats the network panel at the top-right, sized to the
     * terminal — its natural 100x15 when the terminal allows it — with a row budget that fills the box. A second
     * click re-floats it at the same anchor, which the surface replaces rather than stacking.
     */
    @Test
    void shouldOpenTheNetworkPanelFromTheNetSegment() {
        final Console console = new Console(rec(), f("/sys/console_status_test"));
        try {
            Console.getTerminal().setSize(new Size(120, 40));
            final StatusLine bar = console.getStatus();
            assertTrue(bar.clickable(), "the bar's segments answer a click from construction (no widget needed)");
            bar.compileWidgets();
            final int row = Console.getTerminal().getHeight();
            assertTrue(console.getFloatingWidgets().isEmpty(), "nothing is floated yet");
            StatusLine.message(str("still here"));
            // the bar opens with the typer segment ("▎" + " T ") and then the net segment
            assertTrue(console.clickAt(row, 6), "the net segment answers the click");
            final NetworkPanelTool panel = console.getFloatingWidgets().stream()
                    .filter(w -> w instanceof NetworkPanelTool)
                    .map(NetworkPanelTool.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("the network panel did not float: " + console.getFloatingWidgets()));
            assertEquals(FloatingSurface.Anchor.TOP_RIGHT, panel.getStyle().anchor(), "it opens at the top-right");
            assertEquals(100, panel.getStyle().width(), "the width is its natural 100 (the terminal allows it)");
            assertEquals(20, panel.getStyle().height(), "the height is its natural 15 (the terminal allows it)");
            assertEquals(18, panel.max(), "its rows are the box's interior, so it fills the viewport");
            assertEquals(console.vid().extend(STATUS), panel.vid(),
                    "the panel lives at the status address, so mtron can reach it");
            assertTrue(StatusLine.history().contains("still here"),
                    "writing the panel's rec at the status address does not disturb the banner");
            assertTrue(console.clickAt(row, 6), "the gauge answers again");
            assertEquals(1, console.getFloatingWidgets().size(), "a re-click replaces the panel, it does not stack");
            assertEquals(console.vid().extend(STATUS),
                    ((NetworkPanelTool) console.getFloatingWidgets().getFirst()).vid(),
                    "the re-float is the same address, not a second widget");
        } finally {
            console.getFloatingSurface().clear();
            closeConsole(console);
        }
    }

    /**
     * THE MACHINE SEGMENT OPENS THE FRAME TREE: a bordered popup whose bottom border sits one row above the bar and
     * whose rows are the VM's frame tree, rooted at the root machine the bar spells {@code /.}. It is the same
     * popup rule as the message history — a click outside dismisses it — so the only way it changes what you see is
     * a click on one of its nodes.
     */
    @Test
    void shouldOpenTheMachineTreeFromTheMachineSegment() {
        final Console console = new Console(rec(), f("/sys/console_status_test"));
        try {
            Console.getTerminal().setSize(new Size(120, 40));
            final StatusLine bar = console.getStatus();
            bar.compileWidgets();
            final int row = Console.getTerminal().getHeight();
            final SpanUI machine = bar.segments().stream()
                    .filter(segment -> segment.key().equals("machine"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no machine segment: " + bar.segments()));
            assertTrue(bar.clickAt(row, machine.start() + 1), "the machine segment opens the frame tree");
            final PanelWidget popup = console.getFloatingWidgets().stream()
                    .filter(w -> w instanceof PanelWidget)
                    .map(PanelWidget.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no machine tree popup: " + console.getFloatingWidgets()));
            final TreeWidget tree = popup.getStyle().attachment();
            assertNotNull(tree, "the panel frames the frame tree as its style attachment");
            assertEquals(FloatingSurface.Anchor.BOTTOM_LEFT, popup.getStyle().anchor(), "it opens upward from the bar");
            assertEquals(1, popup.getStyle().top(), "its bottom row sits one row above the status line");
            assertEquals(machine.start(), popup.getStyle().left(), "its left edge is the clicked segment");
            assertFalse(tree.entries().isEmpty(), "the popup's rows are the walk's entries");
            assertEquals(Machine.root().vid(), tree.entries().getFirst().uri(), "rooted at the root machine");
            assertTrue(Graphitty.strip(Graphitty.string(popup.format())).contains("/."),
                    "the panel draws the tree's rows inside its frame: " + popup.format());
            assertFalse(console.clickAt(row - 6, 2), "a click outside the popup is not consumed");
            assertTrue(console.getFloatingWidgets().stream().noneMatch(w -> w instanceof PanelWidget),
                    "and it dismisses the popup");
        } finally {
            console.getFloatingSurface().clear();
            closeConsole(console);
        }
    }

    /**
     * THE TREE IS THE CHILD LINKS, walked here rather than read as one space subtree: each frame's links live in its
     * OWN space ({@code <vid>/child/...}, written by push), so a grandchild hangs under its parent — not under the
     * parent's link in the grandparent's table. The walk hands those frames to the tree as its entries.
     */
    @Test
    void shouldWalkTheMachineTreeThroughChildLinks() {
        final Machine a = Machine.current().push(f("mt_palette_a"));
        Machine.current().push(f("mt_palette_b"));
        Machine.current(a).push(f("mt_palette_c"));
        Machine.current(Machine.root());
        try {
            final List<CommonUtil.TreeEntry> entries = new ArrayList<>();
            CommandPalette.walkFrames(entries, 1000);
            // the walk starts at the root, so the VM's other frames ride along — this fixture's subtree is the test
            final List<CommonUtil.TreeEntry> mine = entries.stream()
                    .filter(entry -> entry.uri().toString().startsWith("/mt_palette_a"))
                    .toList();
            assertEquals(List.of(
                            f("/mt_palette_a"),
                            f("/mt_palette_a/mt_palette_b"),
                            f("/mt_palette_a/mt_palette_c")),
                    mine.stream().map(CommonUtil.TreeEntry::uri).toList(),
                    "parents before children, one entry per frame");
            assertEquals(List.of(1, 2, 2), mine.stream().map(CommonUtil.TreeEntry::depth).toList(),
                    "a sibling is a child of the shared parent, not of its neighbour");
            assertFalse(mine.get(1).isLast(), "the first of two siblings draws an arm");
            assertTrue(mine.get(2).isLast(), "the last of two siblings draws the corner");
        } finally {
            Machine.current(Machine.root());
        }
        assertEquals("move(</mt_palette_a/mt_palette_b>)",
                CommandPalette.moveLine(f("/mt_palette_a/mt_palette_b")), "a row's click line is its move");
        Machine.evaluate(CommandPalette.moveLine(f("/mt_palette_a/mt_palette_b")));
        assertEquals(f("/mt_palette_a/mt_palette_b"), Machine.current().vid(),
                "the submitted line walks the thread to the frame");
        Machine.evaluate(CommandPalette.moveLine(Machine.root().vid()));
        assertEquals(Machine.root().vid(), Machine.current().vid(), "and the root row's line walks home");
    }

    /**
     * The bar retains the last {@link StatusLine#MESSAGE_HISTORY} messages for the history popup, newest first —
     * the marquee shows only what fits, but nothing posted is lost until it ages out the tail.
     */
    @Test
    void shouldRetainTheLastFiftyMessagesNewestFirst() {
        for (int i = 1; i <= StatusLine.MESSAGE_HISTORY + 7; i++)
            StatusLine.message(str("note " + i));
        final List<String> history = StatusLine.history();
        assertEquals(StatusLine.MESSAGE_HISTORY, history.size(), "the window keeps its size");
        assertEquals("note " + (StatusLine.MESSAGE_HISTORY + 7), history.getFirst(), "newest first");
        assertEquals("note 8", history.getLast(), "the oldest seven fell off the tail");
    }

    /**
     * THE ENVELOPE ALONE OPENS THE HISTORY: the message segment's glyph is the hot spot while the banner text
     * behind it is inert. The popup is a panel whose bottom border sits one row above the status line, its left
     * edge on the clicked envelope; a click outside dismisses it.
     */
    @Test
    void shouldOpenTheMessageHistoryFromTheEnvelopeGlyph() {
        final Console console = new Console(rec(), f("/sys/console_status_test"));
        try {
            Console.getTerminal().setSize(new Size(120, 40));
            StatusLine.message(str("monad halted"));
            StatusLine.message(str("cycle 3 spun"));
            final String longMessage = "y".repeat(200);
            StatusLine.message(str(longMessage));
            final StatusLine bar = console.getStatus();
            bar.compileWidgets();
            final int row = Console.getTerminal().getHeight();
            final SpanUI message = bar.segments().stream()
                    .filter(segment -> segment.key().equals("message"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no message segment: " + bar.segments()));
            final int envelopeColumn = message.start() + 1;   // spans are line-relative; clicks are 1-based
            final int glyphColumns = Highlighter.visualLength("✉️");
            final int pastLineCol = Math.min(message.end() - 1, message.start() + glyphColumns + 1);
            if (pastLineCol > message.start() + glyphColumns)
                assertFalse(bar.clickAt(row, pastLineCol + 1), "past the envelope the segment is inert");
            assertTrue(bar.clickAt(row, envelopeColumn), "the envelope opens the history");
            final PanelWidget popup = console.getFloatingWidgets().stream()
                    .filter(w -> w instanceof PanelWidget)
                    .map(PanelWidget.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no history popup: " + console.getFloatingWidgets()));
            assertEquals(FloatingSurface.Anchor.BOTTOM_LEFT, popup.getStyle().anchor(), "it opens upward from the bar");
            assertEquals(1, popup.getStyle().top(), "its bottom border sits one row above the status line");
            assertEquals(message.start(), popup.getStyle().left(), "its left edge is the clicked envelope");
            final String rendered = Graphitty.strip(Graphitty.string(popup.format()));
            assertTrue(rendered.contains("cycle 3 spun") && rendered.contains("monad halted"),
                    "the retained messages are itemized: " + rendered);
            assertTrue(rendered.indexOf("cycle 3 spun") < rendered.indexOf("monad halted"), "newest first");
            assertTrue(rendered.contains("…"), "a message wider than the panel is elided, not wrapped: " + rendered);
            assertEquals(3 + 2, rendered.lines().count(),
                    "one line per message, plus the panel's two borders: " + rendered);
            assertFalse(console.clickAt(row - 6, 2), "a click outside the popup is not consumed");
            assertTrue(console.getFloatingWidgets().stream().noneMatch(w -> w instanceof PanelWidget),
                    "and it dismisses the popup");
        } finally {
            console.getFloatingSurface().clear();
            closeConsole(console);
        }
    }
}
