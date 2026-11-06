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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.sys.ui.Border;
import studio.phaseshift.metatron.isa.m.sys.ui.Stylable;
import studio.phaseshift.metatron.isa.m.sys.ui.console.Console;
import studio.phaseshift.metatron.isa.m.sys.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.sys.ui.widget.AccordionWidget;
import studio.phaseshift.metatron.isa.m.sys.ui.widget.FloatingSurface;
import studio.phaseshift.metatron.isa.m.sys.ui.widget.MiniWidget;
import studio.phaseshift.metatron.isa.m.sys.ui.widget.MiniWidget.LineUI;
import studio.phaseshift.metatron.isa.m.sys.ui.widget.MiniWidget.SpanUI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.space.ws.WireStats;
import studio.phaseshift.metatron.isa.m.web.space.ws.WireTrace;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronUISerializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE NETWORK PANEL — what this machine and its peers are saying to each other, as a foldable accordion.
 * <p>
 * It is an {@link AccordionWidget}: the foldable header (a {@code [-]} / {@code [+]} toggle) and the collapse
 * come from the accordion, while the BODY is a live read of the wire's ledger ({@link WireTrace}) — never mirrored
 * into the rec, because a trace is volatile by nature and mirroring it would spend a store write per tick. Both
 * directions are drawn: a round trip leads with the request that was sent and tabs its reply in beneath it (the
 * answer is anchored to the request's vid, so the pair reads as one move), while a frame that arrived with no
 * round trip waiting for it leads with a left arrow as a received row of its own. The frames themselves are objs,
 * so each is drawn through the console's own serializer
 * ({@link ObjmtronUISerializer#unlinked()}, flattened to one line) — it colors what a type carries, tags a uri as a
 * link, and uses the compact form — while the panel's chrome (peers, counts, arrows, timings) is not. It is created
 * from mtron and displayed:
 *
 * <pre>{@code
 * network_panel_tool::[
 *   max   => 12,
 *   style => style::[anchor=>bottom_right, width=>96, height=>14]]@<net>.display()
 * }</pre>
 * <p>
 * Its rec is the panel's POLICY, not its content: {@code max} (rows drawn), {@code paused} (freeze what is drawn),
 * {@code peer} (a lane filter), plus the accordion's {@code expanded} (the fold, latched by the toggle). So
 * {@code @<net> >>= [paused=>true]} freezes it, {@code @<net> >>= [expanded=>false]} folds it, and
 * {@code *<net>} shows how it is configured.
 * <p>
 * As a floating widget it inherits the console's gestures (focus, wheel, chevrons, resize), the accordion's header
 * fold, and a per-peer fold on every lane header: clicking a lane's {@code ▼} closes that lane to its header
 * ({@code ▶}), so one busy peer's rows can be put away while the rest of the wire stays in view. A folded lane's
 * header still carries its counts, and a per-peer {@code [x]} clear button.
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
     * the rec key holding the peers whose rows are folded away. A peer absent from it is open, so a fresh lane is
     * born open and the fold is a policy the user states, not a state the panel infers.
     */
    private static final String FOLDED = "folded";
    /**
     * the span keys a lane header carries: the marker folds the lane, the button clears it (see {@link MiniWidget})
     */
    private static final String FOLD_KEY = "fold";
    private static final String CLEAR_KEY = "clear";
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
    /**
     * the glyph that tabs an ANSWER in under the request it answers — U+21B3, a down-right arrow (↳), indented to
     * the column the request's kind starts at, so the two lines read as one move
     */
    private static final String REPLY_TAB = "   {{c}}↳{{X}}";
    /**
     * the glyph that leads a frame which ARRIVED with no round trip waiting for it
     */
    private static final String RECEIVED_MARK = "{{c}}←";
    /**
     * the syntax a frame is drawn in: what crosses the wire is mtron code, so the obj is highlighted as mtron
     */
    private static final String FRAME_SYNTAX = "mtron";

    private static volatile long lastNudgeMs = 0L;
    private static volatile boolean listening = false;

    public NetworkPanelTool(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid, false);
        this.title(Machine.current().network().at(HOST).orElse(uri(LOCALHOST)) + " network");
        // the defaults apply only when the creator wrote no style of their own — a creator's style is their write
        // and must survive the re-hydration every .display() performs, not be overwritten by the panel's defaults
        if (!Stylable.Style.isStyle(this.at(uri(studio.phaseshift.metatron.Tokens.STYLE)))) {
            // a panel is something you WATCH, so it defaults to a corner rather than to the transcript; the border
            // and the green foreground are the accordion's defaults, and the width keeps the box readable out of the box
            final Stylable.Style<AccordionWidget> defaults = Stylable.Style.empty();
            defaults.border(Border.continuous);
            defaults.foreground("{{g}}");
            defaults.floatAt(FloatingSurface.Anchor.BOTTOM_RIGHT, 0, 0, 0);
            defaults.width(DEFAULT_WIDTH);
            this.style(defaults);
        }
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

    /**
     * The peers whose rows are folded away; a lane absent from this set is open. The fold is the panel's POLICY,
     * so it lives in the rec (like {@code max}, {@code paused} and {@code peer}) — a Java field would be lost by
     * the re-hydration every render performs, springing the lane open under the reader.
     *
     * @return the folded peers, in the order they were folded
     */
    public Set<String> foldedPeers() {
        final Obj folded = this.at(uri(FOLDED));
        final Set<String> peers = new LinkedHashSet<>();
        // an Lst is a CONTAINER, not a multiplicity: stream() yields the list itself, so its slots are read as a
        // list. (Only an Objs streams its members.) A peer NAME is a str's raw value, never its quoted rendering.
        if (folded.isLst())
            folded.lstValue().forEach(peer -> {
                if (peer.isStr())
                    peers.add(peer.strValue());
            });
        return peers;
    }

    /**
     * Fold a lane's rows away, or unfold it — the pointer's per-peer accordion, written through to the rec.
     *
     * @param peer the lane whose fold is toggled
     */
    void toggleFold(final String peer) {
        final Set<String> folded = this.foldedPeers();
        if (!folded.remove(peer))
            folded.add(peer);
        this.put(uri(FOLDED), lst(folded.stream().map(name -> str(name)).toArray(Obj[]::new)));
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
                this.foldedPeers(), Math.max(0, interior - 2));
    }

    // ── the pointer: fold on the header, clear on a lane header ────

    /**
     * The accordion's {@code [-]} / {@code [+]} fold on the header, then the hot spots a lane header carries: its
     * {@code ▼}/{@code ▶} marker (the per-peer fold) and its {@code [x]} clear button. A span's columns are
     * line-relative (see {@link MiniWidget}), so the box's content offset is added here; which spans a row carries
     * comes from the same {@link #layout} the renderer draws.
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
        final List<LineUI> layout = layout(WireTrace.snapshot(this.max()), this.paused(), this.max(),
                this.peerFilter(), this.foldedPeers(), Math.max(0, interior - 2));
        if (bodyRow >= layout.size())
            return false;
        final Optional<SpanUI> hit = layout.get(bodyRow).at(col - CONTENT_OFFSET);
        if (hit.isEmpty())
            return false;
        final SpanUI span = hit.get();
        return switch (span.key()) {
            // THE LANE MARKER IS THE PER-PEER ACCORDION: it puts that peer's rows away (or brings them back)
            case FOLD_KEY -> {
                this.toggleFold(span.about().strValue());
                yield true;
            }
            case CLEAR_KEY -> {
                WireTrace.clear(span.about().strValue());
                yield true;
            }
            default -> false;
        };
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
     * The panel's lines: a summary line, then one line per lane and its newest exchanges — each round trip with
     * its answer tabbed in beneath it, and each unsolicited inbound frame as a received row. A folded lane draws
     * its header and nothing else.
     * <p>
     * Pure — the snapshot comes in, markup comes out — so the layout is tested without a terminal.
     *
     * @param snapshot   the window to draw
     * @param paused     whether capture is paused (the panel says so; a frozen view must not look like a quiet one)
     * @param maxRows    how many lines the body may hold
     * @param peerFilter a substring a lane must contain, or empty for every lane
     * @param folded     the peers whose rows are folded away; their header is still drawn
     * @param width      the columns available (a line longer than this is clipped)
     * @return the body lines, never more than {@code maxRows}
     */
    static List<String> panelLines(final WireTrace.Snapshot snapshot, final boolean paused, final int maxRows,
                                   final String peerFilter, final Set<String> folded, final int width) {
        return layout(snapshot, paused, maxRows, peerFilter, folded, width).stream().map(LineUI::markup).toList();
    }

    /**
     * The full body: the lines {@link #panelLines} returns, each with the hot spots its pointer should honour (a
     * lane header's marker and clear button; nothing on a summary or a data row). One pass builds both, so a hot
     * spot cannot drift from the line the renderer draws.
     */
    private static List<LineUI> layout(final WireTrace.Snapshot snapshot, final boolean paused, final int maxRows,
                                       final String peerFilter, final Set<String> folded, final int width) {
        final List<LineUI> lines = new ArrayList<>();
        // the color LETTER, so it can be dropped into a tag: a timeout tints the trip count and stays quiet otherwise
        final String trouble = snapshot.timeouts() > 0 ? "r" : "y";
        // A CROSS, NOT A TIMEPIECE. This is a count of deadlines that were missed, and an hourglass beside live
        // numbers reads as a clock — which is exactly how it was misread. Drawn only when there is one, so a
        // healthy wire says nothing at all.
        final String alarms = snapshot.timeouts() > 0 ? " {{w}}\u2717 {{r}}%d{{w}}".formatted(snapshot.timeouts()) : "";
        lines.add(new LineUI(MiniWidget.clip("{{c}}⇄{{%s}}%d {{c}}↓{{%s}}%d {{c}}↑{{%s}}%d%s  {{y}}%d peer%s%s"
                        .formatted(trouble, snapshot.roundTrips(),
                                trouble, snapshot.received(),
                                trouble, snapshot.sent(),
                                alarms,
                                snapshot.lanes().size(),
                                1 == snapshot.lanes().size() ? "" : "s",
                                paused ? "  {{r}}PAUSED" : ""),
                width)));
        int remaining = maxRows - 1;
        for (final WireTrace.Lane lane : snapshot.lanes()) {
            if (remaining <= 1)
                break;
            if (!peerFilter.isEmpty() && !lane.peer().contains(peerFilter))
                continue;
            final String last = lane.lastLatencyMs() < 0 ? "" : "  {{y}}" + lane.lastLatencyMs() + "ms{{X}}";
            final String timeout = lane.timeouts() > 0 ? "  {{r}}\u2717 " + lane.timeouts() : "";
            final boolean laneFolded = folded.contains(lane.peer());
            // THE LANE MARKER IS THE PER-PEER ACCORDION: ▼ when the lane is open, ▶ when it is closed to its
            // header. The clear button rides right after the marker either way, so its columns are the same, and
            // both are hot spots on this line keyed for the pointer's switch in onClick.
            final String header = MiniWidget.marker(!laneFolded) + CLEAR_BUTTON + "%s  {{c}}⇄{{y}}%d%s%s"
                    .formatted(ObjmtronUISerializer.unlinked().readWrite(lane.peer()), lane.exchanges(), last, timeout);
            final Obj peer = str(lane.peer());
            lines.add(new LineUI(MiniWidget.clip(header, width), List.of(
                    new SpanUI(0, MiniWidget.MARKER_COLUMNS, FOLD_KEY, peer),
                    new SpanUI(MiniWidget.MARKER_COLUMNS, MiniWidget.MARKER_COLUMNS + CLEAR_BUTTON_WIDTH,
                            CLEAR_KEY, peer))));
            remaining--;
            // a folded lane is its header and nothing else: the header's counts already say what is behind the fold
            if (laneFolded)
                continue;
            // how many of the lane's exchanges this lane has actually drawn, so the "older" line counts what is
            // left rather than what the snapshot happened to hold
            int shown = 0;
            for (final WireTrace.Row row : lane.rows()) {
                // THE ANSWER BELONGS UNDER THE QUESTION. A round trip's reply shares the request's vid, so it is
                // drawn tabbed in beneath the request; a request drawn without its answer (or the answer alone)
                // is half a conversation, so the pair is drawn only when both lines fit.
                final boolean received = "push".equals(row.kind());
                final boolean answered = row.ok() && !row.reply().isEmpty();
                final int needed = !received && answered ? 2 : 1;
                if (remaining < needed)
                    break;
                if (received) {
                    // a frame that arrived with no round trip waiting for it is an INBOUND move, not a failure:
                    // it leads with a left arrow and has no round trip to time
                    final String prefix = "{{w}}  " + RECEIVED_MARK + " {{m}}" + row.kind() + "{{w}} ";
                    lines.add(new LineUI(MiniWidget.clip(
                            prefix + frame(row.requestDetail(), row.reply(), prefix, width), width)));
                } else {
                    final String mark = row.ok() ? "{{c}}→" : "{{r}}✗";
                    final String elapsed = row.ok() ? "{{y}}" + row.elapsedMs() + "ms{{X}}" : "timeout";
                    // THE FRAME IS THE OBJ AND THE REST IS CHROME, so only the frame is drawn through the UI
                    // serializer: the arrow, the kind and the timing keep their panel colors
                    final String prefix = "{{w}}  " + mark + " {{m}}" + row.kind() + "{{w}} " + elapsed + " "
                            + (row.ok() ? "{{w}}" : "{{r}}");
                    lines.add(new LineUI(MiniWidget.clip(prefix + frame(row.requestDetail(),
                            row.request().isEmpty() ? row.reply() : row.request(), prefix, width), width)));
                    if (answered) {
                        lines.add(new LineUI(MiniWidget.clip(
                                REPLY_TAB + frame(row.replyDetail(), row.reply(), REPLY_TAB, width), width)));
                    }
                }
                remaining -= needed;
                shown++;
            }
            if (remaining > 0 && shown < lane.exchanges()) {
                lines.add(new LineUI(MiniWidget.clip(
                        "{{w}}  … %d older".formatted(lane.exchanges() - shown), width)));
                remaining--;
            }
        }
        if (snapshot.lanes().isEmpty()) {
            lines.add(new LineUI(MiniWidget.clip("{{k}} no network traffic", width)));
        } else if (snapshot.dropped() > 0 && remaining > 0) {
            lines.add(new LineUI(MiniWidget.clip(
                    "{{w}}  (dropped %d off the window)".formatted(snapshot.dropped()), width)));
        }
        return lines;
    }

    /**
     * A FRAME DRAWN AS THE OBJ IT IS — through the UI serializer, which is the console's own rendering: it colors
     * what has meaning (a uri and its link, a typed value, a code body in its own syntax) and draws its compact
     * form. That form breaks collections over lines, so the render is flattened back to the one line a row is.
     * <p>
     * The frame is clipped to the columns it has left BEFORE it is colored. Coloring first and clipping after is
     * what loses the highlight: the clipper hands back plain text (so a cut line can never end inside a markup
     * tag), which is exactly how a long request came out uncolored while its short answer stayed colored. The
     * row's leading text (the arrow, the kind, the timing) is chrome: it is measured to know how much room the
     * frame has, and is never drawn as part of it.
     * <p>
     * Without the obj — it has left the detail window (see {@link WireTrace.Row}) — the row's stored text is all
     * there is, and it is highlighted as mtron so an old row is still readable.
     *
     * @param obj    the frame itself, or {@code null} when the trace no longer holds it
     * @param text   the frame's one-line text (see {@link WireTrace#render(Obj)})
     * @param prefix the row's chrome, drawn before the frame
     * @param width  the display columns the whole row may occupy, or {@code <= 0} for no limit
     * @return the frame as it will be drawn, ending in "…" when it was cut
     */
    private static String frame(final Obj obj, final String text, final String prefix, final int width) {
        final String rendered = null == obj
                ? Highlighter.highlightLine(FRAME_SYNTAX, text)
                : Highlighter.highlightLine(FRAME_SYNTAX, Arrays.stream(ObjmtronUISerializer.unlinked().write(obj).split("\n")).map(String::strip).reduce("", (a, b) -> a + " " + b));
        if (width <= 0 || Graphitty.viewLength(prefix + rendered) <= width)
            return rendered;
        // the ellipsis takes a column of its own, so the visible frame is one column narrower
        final int room = Math.max(1, width - Graphitty.viewLength(prefix) - 1);
        return Highlighter.highlightLine(FRAME_SYNTAX, Graphitty.viewPrefix(Graphitty.strip(rendered), room)) + "…";
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
