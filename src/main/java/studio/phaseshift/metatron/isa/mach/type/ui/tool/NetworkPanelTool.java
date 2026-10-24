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

package studio.phaseshift.metatron.isa.mach.type.ui.tool;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.Stylable;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.AccordionWidget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.FloatingSurface;
import studio.phaseshift.metatron.isa.web.space.ws.WireStats;
import studio.phaseshift.metatron.isa.web.space.ws.WireTrace;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.MAX;
import static studio.phaseshift.metatron.Tokens.PAUSE;
import static studio.phaseshift.metatron.Tokens.PEER;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE NETWORK PANEL — what this machine and its peers are saying to each other, as a foldable accordion.
 * <p>
 * It is an {@link AccordionWidget}: the foldable header (a {@code [-]} / {@code [+]} toggle) and the collapse
 * come from the accordion, while the BODY is a live read of the wire's ledger ({@link WireTrace}) — never mirrored
 * into the rec, because a trace is volatile by nature and mirroring it would spend a store write per tick. It is
 * created from mtron and displayed:
 *
 * <pre>{@code
 * network_panel_tool::[
 *   max   => 12,
 *   style => style::[anchor=>bottom_right, width=>96, height=>14]]@<net>.display()
 * }</pre>
 *
 * Its rec is the panel's POLICY, not its content: {@code max} (rows drawn), {@code paused} (freeze what is drawn),
 * {@code peer} (a lane filter), plus the accordion's {@code expanded} (the fold, latched by the toggle). So
 * {@code @<net> >>= [paused=>true]} freezes it, {@code @<net> >>= [expanded=>false]} folds it, and
 * {@code *<net>} shows how it is configured.
 * <p>
 * As a floating widget it inherits the console's gestures (focus, wheel, chevrons, resize), the accordion's header
 * fold, and a per-peer {@code [x]} clear button on every lane header.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class NetworkPanelTool extends AccordionWidget {

    /**
     * rows drawn when the rec says nothing
     */
    private static final int DEFAULT_MAX = 12;
    /**
     * the panel's width when the style says nothing
     */
    private static final int DEFAULT_WIDTH = 96;
    /**
     * how often traffic may nudge a repaint: a panel is a view, not a strobe light
     */
    private static final long REPAINT_MS = 250L;
    /**
     * the marker that leads every lane header — a cyan "▼ "
     */
    private static final String LANE_MARKER = "{{c}}▼{{w}} ";
    /**
     * the clear button drawn on a lane header — a red "[x]" — followed by the space before the peer's name
     */
    private static final String CLEAR_BUTTON = "{{r}}[x]{{w}} ";
    /**
     * the clear button's clickable columns: the "[x]" glyph, not its trailing space
     */
    private static final int CLEAR_BUTTON_WIDTH = 3;
    /**
     * box-local columns a body row's content starts after: the left border side and the leading space
     */
    private static final int CONTENT_OFFSET = 2;

    private static volatile long lastNudgeMs = 0L;
    private static volatile boolean listening = false;

    public NetworkPanelTool(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid, false);
        this.title("net");
        // a panel is something you WATCH, so it defaults to a corner rather than to the transcript; the border and
        // the green foreground are the accordion's defaults, and the width keeps the box readable out of the box
        final Stylable.Style<AccordionWidget> defaults = Stylable.Style.empty();
        defaults.border(Border.continuous);
        defaults.foreground("{{g}}");
        defaults.floatAt(FloatingSurface.Anchor.BOTTOM_RIGHT, 0, 0, 0);
        defaults.width(DEFAULT_WIDTH);
        this.style(defaults);
        listenForTraffic();
    }

    // ── the panel's policy, read from its rec ──────────────────────

    public int max() {
        final Obj max = this.at(uri(MAX));
        return max.isNoObj() ? DEFAULT_MAX : Math.max(1, max.intValue().intValue());
    }

    public NetworkPanelTool max(final int rows) {
        this.put(uri(MAX), jnt(rows));
        return this;
    }

    public boolean paused() {
        final Obj paused = this.at(uri(PAUSE));
        return !paused.isNoObj() && paused.booleanCheck();
    }

    public NetworkPanelTool pause(final boolean paused) {
        this.put(uri(PAUSE), studio.phaseshift.metatron.isa.m.type.impl.MBool.bool(paused));
        return this;
    }

    public String peerFilter() {
        final Obj peer = this.at(uri(PEER));
        return peer.isNoObj() ? "" : peer.toString();
    }

    // ── the live body (the accordion's content, not the rec's) ─────

    /**
     * The panel's body is a live read of the trace, clipped to the accordion's content width — it is never stored
     * in the rec (see the class note), so this overrides the accordion's rec-backed body source.
     */
    @Override
    protected List<String> bodySource(final Map<Obj, Obj> fields) {
        final int interior = this.getStyle().width() > 0 ? this.getStyle().width() - 2 : DEFAULT_WIDTH - 2;
        return panelLines(WireTrace.snapshot(this.max()), this.paused(), this.max(), this.peerFilter(),
                Math.max(0, interior - 2));
    }

    // ── the pointer: fold on the header, clear on a lane header ────

    /**
     * The accordion's {@code [-]} / {@code [+]} fold on the header, then the per-peer {@code [x]} clear button on
     * a lane header's own body row. The button sits at a fixed content column right after the lane marker
     * ({@code ▼ }), so the hit test is column arithmetic; only the body row (which peer) needs the live layout,
     * and that comes from the same {@link #layout} the renderer draws.
     */
    @Override
    public boolean onClick(final int row, final int col) {
        if (super.onClick(row, col))
            return true;
        // row 0 is the box's header, so body row `i` sits at box row `i + 1`
        final int bodyRow = row - 1;
        if (bodyRow < 0)
            return false;
        final int interior = this.getStyle().width() > 0 ? this.getStyle().width() - 2 : DEFAULT_WIDTH - 2;
        final PanelLayout layout = layout(WireTrace.snapshot(this.max()), this.paused(), this.max(),
                this.peerFilter(), Math.max(0, interior - 2));
        final String peer = layout.peer(bodyRow);
        if (peer.isEmpty())
            return false;
        final int buttonStart = CONTENT_OFFSET + Highlighter.visualLength(LANE_MARKER);
        if (col < buttonStart || col >= buttonStart + CLEAR_BUTTON_WIDTH)
            return false;
        WireTrace.clear(peer);
        return true;
    }

    // ── traffic nudges a repaint ───────────────────────────────────

    /**
     * A transaction on the wire is what makes the panel stale, so that is what asks for a repaint — throttled, and
     * only while a panel is actually on screen.
     */
    private static void listenForTraffic() {
        if (listening)
            return;
        synchronized (NetworkPanelTool.class) {
            if (listening)
                return;
            listening = true;
            WireStats.listen(frame -> {
                final Console console = Console.LOCAL_INSTANCE;
                if (null == console)
                    return;
                final long now = System.currentTimeMillis();
                if (now - lastNudgeMs < REPAINT_MS)
                    return;
                // the console already knows how to redraw at the next prompt boundary; asking it is cheaper and
                // calmer than rendering from the wire thread
                if (console.getFloatingWidgets().stream().anyMatch(w -> w instanceof NetworkPanelTool)) {
                    lastNudgeMs = now;
                    console.requestRedraw();
                }
            });
        }
    }

    // ── rendering ──────────────────────────────────────────────────

    /**
     * The panel's body: its lines and, parallel to them, the peer whose clear button each line carries (empty when
     * the line has none — a summary, a row, a footer). The renderer and the pointer's hit test both read this, so
     * they can never disagree about where a clear button sits.
     */
    private record PanelLayout(List<String> lines, List<String> clearPeers) {
        /**
         * The peer the clear button on body row {@code bodyRow} clears, or "" when that row carries none.
         */
        String peer(final int bodyRow) {
            return bodyRow >= 0 && bodyRow < this.clearPeers.size() ? this.clearPeers.get(bodyRow) : "";
        }
    }

    /**
     * The panel's lines: a summary line, then one line per lane and its newest exchanges.
     * <p>
     * Pure — the snapshot comes in, markup comes out — so the layout is tested without a terminal.
     *
     * @param snapshot   the window to draw
     * @param paused     whether capture is paused (the panel says so; a frozen view must not look like a quiet one)
     * @param maxRows    how many lines the body may hold
     * @param peerFilter a substring a lane must contain, or empty for every lane
     * @param width      the columns available (a line longer than this is clipped)
     * @return the body lines, never more than {@code maxRows}
     */
    static List<String> panelLines(final WireTrace.Snapshot snapshot, final boolean paused, final int maxRows,
                                   final String peerFilter, final int width) {
        return layout(snapshot, paused, maxRows, peerFilter, width).lines();
    }

    /**
     * The full body: the lines {@link #panelLines} returns, plus the clear-button owner for each line. One pass
     * builds both, so a lane header's {@code [x]} target cannot drift from the header the renderer draws.
     */
    private static PanelLayout layout(final WireTrace.Snapshot snapshot, final boolean paused, final int maxRows,
                                      final String peerFilter, final int width) {
        final List<String> lines = new ArrayList<>();
        final List<String> clearPeers = new ArrayList<>();
        // the color LETTER, so it can be dropped into a tag: a timeout tints the trip count and stays quiet otherwise
        final String trouble = snapshot.timeouts() > 0 ? "r" : "y";
        // A CROSS, NOT A TIMEPIECE. This is a count of deadlines that were missed, and an hourglass beside live
        // numbers reads as a clock — which is exactly how it was misread. Drawn only when there is one, so a
        // healthy wire says nothing at all.
        final String alarms = snapshot.timeouts() > 0 ? " {{w}}\u2717 {{r}}%d{{w}}".formatted(snapshot.timeouts()) : "";
        lines.add(clip("{{w}}⇄{{%s}}%d {{w}}↓{{%s}}%d {{w}}↑{{%s}}%d%s  {{y}}%d peer%s%s"
                        .formatted(trouble, snapshot.roundTrips(),
                                trouble, snapshot.received(),
                                trouble, snapshot.sent(),
                                alarms,
                                snapshot.lanes().size(),
                                1 == snapshot.lanes().size() ? "" : "s",
                                paused ? "  {{r}}PAUSED" : ""),
                width));
        clearPeers.add("");
        int remaining = maxRows - 1;
        for (final WireTrace.Lane lane : snapshot.lanes()) {
            if (remaining <= 1)
                break;
            if (!peerFilter.isEmpty() && !lane.peer().contains(peerFilter))
                continue;
            final String last = lane.lastLatencyMs() < 0 ? "" : "  " + lane.lastLatencyMs() + "ms";
            final String timeout = lane.timeouts() > 0 ? "  {{r}}\u2717 " + lane.timeouts() : "";
            // the lane header carries the clear button, right after the marker and before the peer's name
            lines.add(clip(LANE_MARKER + CLEAR_BUTTON + "%s  {{y}}⇄%d%s%s"
                    .formatted(lane.peer(), lane.exchanges(), last, timeout), width));
            clearPeers.add(lane.peer());
            remaining--;
            for (final WireTrace.Row row : lane.rows()) {
                if (remaining <= 0)
                    break;
                final String mark = row.ok() ? "{{w}}→" : "{{r}}✗";
                final String elapsed = row.ok() ? row.elapsedMs() + "ms" : "timeout";
                final String body = row.request().isEmpty() ? row.reply() : row.request();
                lines.add(clip("{{w}}  " + mark + " {{m}}" + row.kind() + "{{w}} " + elapsed + "  "
                        + (row.ok() ? "{{w}}" : "{{r}}") + body, width));
                clearPeers.add("");
                remaining--;
            }
            if (remaining > 0 && lane.rows().size() < lane.exchanges()) {
                lines.add(clip("{{w}}  … %d older".formatted(lane.exchanges() - lane.rows().size()), width));
                clearPeers.add("");
                remaining--;
            }
        }
        if (snapshot.lanes().isEmpty()) {
            lines.add(clip("{{w}}  no peers have spoken yet", width));
            clearPeers.add("");
        } else if (snapshot.dropped() > 0 && remaining > 0) {
            lines.add(clip("{{w}}  (dropped %d off the window)".formatted(snapshot.dropped()), width));
            clearPeers.add("");
        }
        return new PanelLayout(lines, clearPeers);
    }

    /**
     * Clip a markup line to {@code width} visible columns, keeping the styling of what survives.
     */
    private static String clip(final String markup, final int width) {
        if (width <= 0)
            return markup;
        // measure what a terminal will show, not the markup: the same strip WidgetCanvas uses to size a line
        final String plain = studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty.strip(
                studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty.string(markup));
        if (plain.length() <= width)
            return markup;
        // a clipped markup line would end inside a tag, so a line that must be cut is handed back as plain text
        return plain.substring(0, Math.max(0, width - 1)) + "…";
    }

    @Override
    public String renderInPlace() {
        return this.format() + "\n";
    }

    @Override
    public String renderFresh() {
        return this.format() + "\n";
    }

    @Override
    public String toString() {
        return this.format();
    }

}
