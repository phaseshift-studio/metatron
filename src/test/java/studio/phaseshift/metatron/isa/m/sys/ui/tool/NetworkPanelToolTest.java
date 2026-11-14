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

package studio.phaseshift.metatron.isa.m.sys.ui.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.Analyzer;
import studio.phaseshift.metatron.isa.m.mach.type.analyzer.BasicAnalyzer;
import studio.phaseshift.metatron.isa.m.sys.ui.Stylable;
import studio.phaseshift.metatron.isa.m.sys.ui.console.Console;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.sys.ui.widget.AccordionWidget;
import studio.phaseshift.metatron.isa.m.sys.ui.widget.FloatingSurface;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.web.space.ws.WireTrace;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.sys.ui.uiInstSet.UI_NETWORK_PANEL_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The panel's LAYOUT, as a pure function: a snapshot and a policy in, text out. No terminal, no console.
 * <p>
 * The tool's own wiring — float on display, repaint on traffic — is exercised by the console harness; what can be
 * pinned down here is that the numbers, the lanes, the clipping and the "this view is paused" marker are right.
 * <p>
 * The exception is a run, which reads the console and its terminal to decide whether to take the keyboard: a console
 * that another class left in a reused fork once put this class's run into a key loop that hung the suite.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class NetworkPanelToolTest extends AbstractMetatronTest {

    private static final String PEER_A = "ws://alpha:8555/mtron";
    private static final String PEER_B = "ws://beta:8555/mtron";

    /**
     * the network's meters for this test, which the summary line counts from — fresh per test, as the window is
     */
    private Analyzer wire;

    @BeforeEach
    void resetWire() {
        // the panel's type lives in the ui instruction set, which a bare boot does not import
        InstSet.importInstSet(studio.phaseshift.metatron.isa.m.sys.ui.uiInstSet.UI_ISA_TID);
        this.wire = new BasicAnalyzer();
        WireTrace.clear();
        WireTrace.pause(false);
    }

    /**
     * A round trip as the tap records it (WebSocketObj.sendRecv): a row in the window and an observation of the
     * network's round-trip timer, beside it.
     */
    private void exchange(final String peer, final String request, final long elapsedMs) {
        WireTrace.exchange(peer, null, str(request), str("answer:" + request), System.currentTimeMillis(),
                elapsedMs, WireTrace.Outcome.OK);
        this.wire.record(f(WIRE), f(TRIP), elapsedMs * 1_000_000L);
    }

    private static String plain(final String line) {
        return Graphitty.strip(Graphitty.string(line));
    }

    private List<String> lines(final boolean paused, final int maxRows, final String filter, final int width) {
        return this.lines(paused, maxRows, filter, Set.of(), width);
    }

    private List<String> lines(final boolean paused, final int maxRows, final String filter,
                               final Set<String> folded, final int width) {
        return NetworkPanelTool.panelLines(WireTrace.snapshot(maxRows), this.wire.analysis(), paused, maxRows, filter,
                folded, width);
    }

    /**
     * The summary line leads with the three counts the status gauge shows, so the panel and the bar never disagree.
     */
    @Test
    void shouldLeadWithTheWireCounts() {
        exchange(PEER_A, "1.plus(1)", 4L);
        exchange(PEER_A, "2.plus(2)", 6L);
        final String summary = plain(lines(false, 12, "", 120).getFirst());
        assertTrue(summary.contains("⇄2"), "two round trips completed: " + summary);
        assertTrue(summary.contains("1 peer"), "one lane is described: " + summary);
        // EVERY COUNT IS A WIRE FACT, the network's meter counted at its tap: the round trips this fixture records as the
        // tap does, but ↓/↑ are counted where frames are (send/onMessage), so they stay at zero here -- the honest answer
        assertTrue(summary.contains("↓0") && summary.contains("↑0"),
                "frames are counted by the socket tap, not invented by the ledger: " + summary);
    }

    /**
     * A lane leads with its peer and its own totals, then that peer's newest exchanges — the request, then the
     * answer tabbed in under it.
     */
    @Test
    void shouldDrawALaneThenItsExchanges() {
        exchange(PEER_A, "sqr(3)", 4L);
        final List<String> lines = lines(false, 12, "", 120);
        assertTrue(plain(lines.get(1)).contains(PEER_A), "the lane names its peer: " + plain(lines.get(1)));
        assertTrue(plain(lines.get(1)).contains("⇄1"), "and counts its exchanges");
        final String row = plain(lines.get(2));
        assertTrue(row.contains("sqr(3)"), "the exchange shows the request that was sent: " + row);
        assertTrue(row.contains("4ms"), "and how long the answer took: " + row);
        assertTrue(row.contains("send_recv"), "and what kind of move it was: " + row);
    }

    /**
     * A FOLDED LANE IS ITS HEADER AND NOTHING ELSE: the per-peer accordion puts that peer's rows away while the
     * header still reports its counts and keeps its clear button.
     */
    @Test
    void shouldFoldALaneToItsHeader() {
        exchange(PEER_A, "sqr(3)", 4L);
        final List<String> folded = lines(false, 12, "", Set.of(PEER_A), 120);
        assertEquals(2, folded.size(), "the summary and the lane header, with no rows: " + folded);
        final String header = plain(folded.get(1));
        assertTrue(header.contains("▶"), "the header shows the closed marker: " + header);
        assertTrue(header.contains("⇄1"), "and still counts the lane's exchanges: " + header);
        assertTrue(header.contains("[x]"), "and keeps its clear button: " + header);
        assertFalse(String.join("\n", folded.stream().map(NetworkPanelToolTest::plain).toList()).contains("sqr(3)"),
                "none of the lane's rows are drawn: " + folded);
    }

    /**
     * The fold is per peer: the marked lane closes while every other lane keeps its rows in view.
     */
    @Test
    void shouldFoldOnlyTheLaneThatWasMarked() {
        exchange(PEER_A, "first", 1L);
        exchange(PEER_B, "second", 1L);
        final String all = String.join("\n",
                lines(false, 12, "", Set.of(PEER_A), 120).stream().map(NetworkPanelToolTest::plain).toList());
        assertFalse(all.contains("first"), "the folded lane's rows are hidden: " + all);
        assertTrue(all.contains("second"), "the other lane's rows stay in view: " + all);
    }

    /**
     * The lane marker is the per-peer accordion: clicking {@code ▼} closes that lane to its header ({@code ▶}),
     * and clicking it again opens it. The fold is written to the panel's rec, so a re-hydration keeps it.
     */
    @Test
    void shouldFoldALaneByItsMarker() {
        exchange(PEER_A, "sqr(3)", 4L);
        final NetworkPanelTool tool = new NetworkPanelTool(mutableMap(), UI_NETWORK_PANEL_TID, null);
        assertTrue(plain(tool.format()).contains("▼"), "the lane starts open: " + plain(tool.format()));
        // body row 0 is the summary and body row 1 the lane header, so the header is box row 2; its marker is the
        // first content column (the box side and its inset take two)
        assertTrue(tool.onClick(2, 2), "clicking the lane marker folds the lane");
        assertTrue(tool.foldedPeers().contains(PEER_A), "the peer is folded in the rec: " + tool.foldedPeers());
        final String folded = plain(tool.format());
        assertTrue(folded.contains("▶"), "the header now shows the closed marker: " + folded);
        assertFalse(folded.contains("sqr(3)"), "and the lane's rows are gone: " + folded);
        final String[] rows = folded.split("\n");
        for (final String row : rows)
            assertEquals(rows[0].length(), row.length(), "the folded header keeps the box square: [" + row + "]");
        assertTrue(tool.onClick(2, 2), "clicking it again unfolds the lane");
        assertTrue(tool.foldedPeers().isEmpty(), "the peer is no longer folded");
        assertTrue(plain(tool.format()).contains("sqr(3)"), "its rows are back");
    }

    /**
     * A SENT MESSAGE AND THE ANSWER IT GOT ARE ONE EXCHANGE, read as one conversation: the reply is tabbed in
     * beneath the request with a down-right arrow, and carries no second time (the round trip is the request's).
     */
    @Test
    void shouldTabTheReplyUnderTheRequestItAnswers() {
        exchange(PEER_A, "sqr(3)", 4L);
        final List<String> lines = lines(false, 12, "", 120);
        final String reply = plain(lines.get(3));
        assertTrue(reply.startsWith("   ↳ "), "the reply is tabbed in under the request it answers: " + reply);
        assertTrue(reply.contains("answer:sqr(3)"), "and shows the answer that came back: " + reply);
        assertFalse(reply.contains("ms"), "the round trip is stated once, on the request line: " + reply);
    }

    /**
     * The frames are OBJS, so each is drawn through the console's serializer and highlighted as mtron: the frame
     * carries ANSI color, while the panel's chrome (a lane header, the arrows, the timing) carries only panel
     * markup.
     */
    @Test
    void shouldDrawTheFramesThroughTheUiSerializer() {
        WireTrace.exchange(PEER_A, null, uri("/a/b"), uri("/c/d"), System.currentTimeMillis(), 4L,
                WireTrace.Outcome.OK);
        final List<String> lines = lines(false, 12, "", 120);
        assertTrue(lines.get(2).contains("\u001B["),
                "the sent obj is colored: " + lines.get(2).replace("\u001B", "\\e"));
        assertTrue(plain(lines.get(2)).contains("/a/b"), "and shows the obj itself: " + plain(lines.get(2)));
        assertTrue(lines.get(3).contains("\u001B["),
                "the received obj is colored too: " + lines.get(3).replace("\u001B", "\\e"));
        assertFalse(lines.get(1).contains("\u001B["),
                "a lane header is chrome, not a frame: " + lines.get(1).replace("\u001B", "\\e"));
    }

    /**
     * A row that overflows is CUT, and what survives stays colored: the frame is clipped BEFORE it is colored,
     * so a long request does not come out plain while its short answer is colored — which is exactly how the
     * `send_recv …` prefix made the sent obj look unhighlighted. A frame that fits keeps its color too.
     */
    @Test
    void shouldKeepTheColorWhenARowIsClipped() {
        WireTrace.exchange(PEER_A, null, str("this.is.a.long.request." + "x".repeat(120)), uri("/m/answer"),
                System.currentTimeMillis(), 56L, WireTrace.Outcome.OK);
        final List<String> lines = lines(false, 12, "", 40);
        assertTrue(lines.get(2).contains("\u001B["),
                "the visible part of a clipped sent obj stays colored: " + lines.get(2).replace("\u001B", "\\e"));
        assertTrue(plain(lines.get(2)).contains("…"), "and the row says it was cut: " + plain(lines.get(2)));
        assertTrue(lines.get(3).contains("\u001B["),
                "a frame that fits is colored too: " + lines.get(3).replace("\u001B", "\\e"));
        assertTrue(plain(lines.get(3)).contains("/m/answer"),
                "and shows the answer that came back: " + plain(lines.get(3)));
    }

    /**
     * A frame that arrived with no round trip waiting for it is an INBOUND move — a left arrow, no round trip to
     * time, and never the red cross of a deadline that was missed.
     */
    @Test
    void shouldDrawAnUnsolicitedFrameAsReceivedNotAsATimeout() {
        WireTrace.push(PEER_A, uri("/m/listen"));
        final List<String> lines = lines(false, 12, "", 120);
        final String row = plain(lines.get(2));
        assertTrue(row.contains("←"), "a received frame leads with a left arrow: " + row);
        assertTrue(row.contains("/m/listen"), "and shows the frame that arrived: " + row);
        assertTrue(row.contains("push"), "as the inbound move it is: " + row);
        assertFalse(row.contains("timeout"), "a received frame never wears the timeout label: " + row);
        assertFalse(row.contains("✗"), "and never the red cross of a missed deadline: " + row);
        assertEquals(3, lines.size(), "it is a row of its own, with nothing tabbed under it: " + lines);
        assertTrue(lines.get(2).contains("\u001B["),
                "the received frame is colored: " + lines.get(2).replace("\u001B", "\\e"));
    }

    /**
     * The lanes are the traffic: the busiest peer is drawn first.
     */
    @Test
    void shouldDrawTheMostRecentlyActivePeerFirst() {
        exchange(PEER_A, "early", 1L);
        CommonUtilSleep.sleep(5);
        exchange(PEER_B, "later", 1L);
        final List<String> lines = lines(false, 12, "", 120);
        assertTrue(plain(lines.get(1)).contains(PEER_B), "the lane that spoke last leads: " + plain(lines.get(1)));
    }

    /**
     * A filter keeps one lane — the panel's `peer` key.
     */
    @Test
    void shouldDrawOnlyTheFilteredLane() {
        exchange(PEER_A, "alpha says", 1L);
        exchange(PEER_B, "beta says", 1L);
        final List<String> lines = lines(false, 12, "beta", 120);
        final String all = String.join("\n", lines.stream().map(NetworkPanelToolTest::plain).toList());
        assertTrue(all.contains(PEER_B), "the filtered lane is drawn");
        assertFalse(all.contains(PEER_A), "and the other one is not: " + all);
    }

    /**
     * A paused panel SAYS so: a frozen view and a quiet wire must not look the same.
     */
    @Test
    void shouldSayWhenTheViewIsPaused() {
        exchange(PEER_A, "held", 1L);
        assertTrue(plain(lines(true, 12, "", 120).getFirst()).contains("PAUSED"), "the header says the view is frozen");
        assertFalse(plain(lines(false, 12, "", 120).getFirst()).contains("PAUSED"), "and says nothing when it is live");
    }

    /**
     * The body is bounded, and every line fits the width it was given.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "12 % 12 % a normal panel holds at most its max rows",
            "3  % 3  % and a short one holds fewer",
    }, delimiter = '%')
    void shouldBoundTheBodyAndClipEveryLine(final int maxRows, final int expectedMax, final String description) {
        for (int i = 0; i < 20; i++)
            exchange(PEER_A, "request-number-" + i, 1L);
        final List<String> lines = lines(false, maxRows, "", 40);
        assertTrue(lines.size() <= expectedMax, description + " (got " + lines.size() + ")");
        for (final String line : lines)
            assertTrue(plain(line).length() <= 40, description + " (line too wide: " + plain(line) + ")");
    }

    /**
     * A timeout is drawn in the lane that suffered it, on the summary line and in the lane's own count.
     */
    @Test
    void shouldMarkATimeoutOnTheSummaryAndTheLane() {
        WireTrace.exchange(PEER_B, null, str("ping"), null, System.currentTimeMillis(), 30_000L,
                WireTrace.Outcome.TIMEOUT);
        // the tap counts the missed deadline beside the row
        this.wire.incr(f(WIRE), f(TIMEOUT), 1L);
        final List<String> lines = lines(false, 12, "", 120);
        assertTrue(plain(lines.getFirst()).contains("✗ 1"), "the summary counts the timeout with a cross, not a clock: " + plain(lines.getFirst()));
        assertTrue(plain(lines.get(1)).contains("✗ 1"), "and the lane does too: " + plain(lines.get(1)));
        assertTrue(plain(lines.get(2)).contains("timeout"), "the exchange says what happened: " + plain(lines.get(2)));
        assertEquals(3, lines.size(), "nothing came back, so nothing is tabbed under it: " + lines);
    }

    /**
     * Empty is a state, and the panel names it rather than drawing a bare box.
     */
    @Test
    void shouldSayWhenTheWireIsQuiet() {
        final String all = String.join("\n", lines(false, 12, "", 120).stream().map(NetworkPanelToolTest::plain).toList());
        assertTrue(all.contains("no network traffic"), "an empty wire says so: " + all);
    }

    /**
     * The panel's policy comes from its rec, and the registered type constructs it — `network_panel_tool::[…].display()`.
     */
    @Test
    void shouldTakeItsPolicyFromItsRec() {
        final NetworkPanelTool tool = new NetworkPanelTool(mutableMap(), UI_NETWORK_PANEL_TID, null);
        assertEquals(12, tool.max(), "rows default to the panel's default");
        assertFalse(tool.paused(), "and the view starts live");
        tool.max(30).pause(true);
        assertEquals(30, tool.max(), "the rec carries the row count");
        assertTrue(tool.paused(), "and the paused flag");
        final String rendered = Graphitty.strip(Graphitty.string(tool.format()));
        assertTrue(rendered.contains("PAUSED"), "so a rec-driven pause is drawn: " + rendered);
        assertTrue(rendered.startsWith("┌"), "and it renders as a bordered panel: " + rendered.split("\n")[0]);
    }

    /**
     * Without a console the panel must still format — a boot file that renders it headless should draw the box, not
     * throw.
     */
    @Test
    @Timeout(60)
    void shouldFormatWithoutAConsole() {
        final Console console = Console.LOCAL_INSTANCE;
        Console.LOCAL_INSTANCE = null;
        try {
            final NetworkPanelTool tool = new NetworkPanelTool(mutableMap(), UI_NETWORK_PANEL_TID, null);
            assertTrue(Graphitty.strip(Graphitty.string(tool.format())).startsWith("┌"),
                    "the panel formats to a bordered box without a console");
        } finally {
            Console.LOCAL_INSTANCE = console;
        }
    }

    /**
     * The panel is a foldable accordion: the header's {@code [-]} folds it to just its header, and {@code [+]} (with
     * the body line count) unfolds it again. The toggle sits right after the title, and the title carries the
     * machine's host — so its column is read from the drawn header, not assumed from the title's length.
     */
    @Test
    void shouldFoldAndUnfold() {
        final NetworkPanelTool tool = new NetworkPanelTool(mutableMap(), UI_NETWORK_PANEL_TID, null);
        assertTrue(tool.isExpanded(), "the panel starts expanded");
        final String header = plain(tool.format()).split("\n")[0];
        final int toggle = header.indexOf("[-]");
        assertTrue(toggle > 0, "the expanded header shows the fold marker: " + header);
        assertTrue(tool.onClick(0, toggle), "clicking the toggle folds the panel");
        assertFalse(tool.isExpanded(), "now folded");
        final String folded = plain(tool.format());
        assertTrue(folded.contains("[+]"), "the folded header shows the unfold marker: " + folded);
        assertFalse(folded.contains("▼"), "and draws none of the body: " + folded);
        final int unfold = folded.split("\n")[0].indexOf("[+]");
        assertTrue(tool.onClick(0, unfold), "clicking the toggle again unfolds it");
        assertTrue(tool.isExpanded(), "expanded again");
    }

    /**
     * The creator's style is their write: the panel's defaults must not overwrite it.
     */
    @Test
    void shouldKeepTheCreatorsStyle() {
        final Stylable.Style<AccordionWidget> style =
                Stylable.Style.empty();
        style.floatAt(FloatingSurface.Anchor.TOP_LEFT, 0, 0, 0);
        style.width(50);
        final NetworkPanelTool tool = new NetworkPanelTool(
                mutableMap(studio.phaseshift.metatron.isa.m.type.impl.MUri.uri("style"), style),
                UI_NETWORK_PANEL_TID, null);
        assertEquals(50, tool.getStyle().width(), "the creator's width survives the panel's defaults");
        assertEquals(FloatingSurface.Anchor.TOP_LEFT,
                tool.getStyle().anchor(), "and so does the creator's anchor");
    }

    /**
     * The type is registered, so a user can create the tool the way every other tool is created.
     */
    @Test
    void shouldBeRegisteredAsAToolType() {
        final studio.phaseshift.metatron.isa.m.type.Obj type =
                studio.phaseshift.metatron.isa.m.mach.type.Machine.read(UI_NETWORK_PANEL_TID);
        assertFalse(type.isNoObj(), "the panel's type is registered");
        assertInstanceOf(studio.phaseshift.metatron.isa.m.type.Type.class, type, "and it is a type");
    }

    /**
     * Every lane header carries a clear button — the pointer's per-peer clear — and the summary line does not.
     */
    @Test
    void shouldDrawAClearButtonOnEachLaneHeader() {
        exchange(PEER_A, "alpha", 1L);
        exchange(PEER_B, "beta", 1L);
        final List<String> lines = lines(false, 12, "", 120);
        // body row 0 is the summary; each lane is then a header (with the button), its exchange and the tabbed
        // answer under it, so the two lane headers sit at body rows 1 and 4
        assertFalse(plain(lines.get(0)).contains("[x]"), "the summary line carries no button: " + plain(lines.get(0)));
        assertTrue(plain(lines.get(1)).contains("[x]"), "the first lane header carries the button: " + plain(lines.get(1)));
        assertTrue(plain(lines.get(4)).contains("[x]"), "and the second does too: " + plain(lines.get(4)));
    }

    /**
     * The clear button drops only the peer whose header was clicked — the pointer's per-peer clear, not the
     * inspector's whole-wire {@code c}.
     */
    @Test
    void shouldClearOnlyTheClickedPeer() {
        exchange(PEER_A, "alpha", 1L);
        CommonUtilSleep.sleep(5);
        exchange(PEER_B, "beta", 1L);
        final NetworkPanelTool tool = new NetworkPanelTool(mutableMap(), UI_NETWORK_PANEL_TID, null);
        // the most recently active peer (PEER_B) leads: its header is box row 2 (row 0 = top border, row 1 = the
        // summary), and the "[x]" button spans box columns [4, 7) — border side, leading space, then the "▼ " marker
        assertFalse(tool.onClick(2, 1), "a click on the header's leading space clears nothing");
        assertTrue(tool.onClick(2, 4), "a click on the clear button clears that lane");
        final WireTrace.Snapshot snapshot = WireTrace.snapshot(12);
        assertEquals(1, snapshot.lanes().size(), "only the clicked peer's lane went away");
        assertTrue(snapshot.lanes().getFirst().peer().contains("alpha"), "the untouched peer remains");
    }

    /**
     * A clipped line (ending in an ellipsis) must not push the right border out: every row of the box shares its
     * width, so the right edge stays a straight line.
     */
    @Test
    void shouldKeepTheRightBorderAlignedWhenALineIsClipped() {
        exchange(PEER_A, "1.plus(2)", 1L);       // short, and highlighted whole: measured with ANSI in it
        exchange(PEER_A, "x".repeat(200), 1L);   // longer than the panel, so the row clips with an ellipsis
        final NetworkPanelTool tool = new NetworkPanelTool(mutableMap(), UI_NETWORK_PANEL_TID, null);
        final String[] rows = Graphitty.strip(Graphitty.string(tool.format())).split("\n");
        final int width = rows[0].length();
        assertTrue(width > 2, "the box has a top border to measure");
        boolean clipped = false;
        for (final String row : rows) {
            assertEquals(width, row.length(), "every row shares the box's width (no ragged right edge): " + row);
            if (row.contains("…")) {
                clipped = true;
                assertTrue(row.contains("… │"), "the ellipsis keeps a column from the right border: " + row);
            }
        }
        assertTrue(clipped, "the fixture actually clipped a line");
    }

    /**
     * Word-wrap must measure visual columns (markup stripped), not characters: a markup-heavy lane header — whose
     * tags inflate its char count — must not be split onto two lines when it fits on one.
     */
    @Test
    void wrapLinesShouldMeasureVisualColumnsNotMarkupCharacters() {
        final String header = "{{c}}▼{{w}} {{r}}[x]{{w}} ws://peer:1/mtron {{y}}⇄4 60ms";
        final int visual = Graphitty.viewLength(header);
        assertTrue(visual < header.length(),
                "the markup is longer than what it draws: " + header.length() + " chars vs " + visual + " columns");
        final List<String> wrapped =
                Stylable.Style.wrapLines(List.of(header), visual);
        assertEquals(1, wrapped.size(), "a line that fits visually is not split by its markup: " + wrapped);
    }

    /**
     * a tiny sleep helper, so the two chats in the ordering test are unambiguously seconds apart
     */
    private static final class CommonUtilSleep {
        private static void sleep(final long ms) {
            studio.phaseshift.metatron.util.CommonUtil.sleepThread(ms);
        }
    }
}
