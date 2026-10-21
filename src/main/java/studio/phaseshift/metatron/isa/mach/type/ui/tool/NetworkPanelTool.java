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

import org.jline.keymap.KeyMap;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.Stylable;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.AbstractWidget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.FloatingSurface;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.WidgetCanvas;
import studio.phaseshift.metatron.isa.web.space.ws.WireStats;
import studio.phaseshift.metatron.isa.web.space.ws.WireTrace;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.MAX;
import static studio.phaseshift.metatron.Tokens.PAUSE;
import static studio.phaseshift.metatron.Tokens.PEER;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * THE NETWORK PANEL — what this machine and its peers are saying to each other, as a floating tool.
 * <p>
 * It is a TOOL, not a widget: its content is domain specific (peer lanes and the exchanges that crossed them) and
 * it aggregates what the wire's ledger recorded ({@link WireTrace}). It is created from mtron and displayed — there
 * is no command surface:
 *
 * <pre>{@code
 * network_panel_tool::[
 *   max   => 12,
 *   style => style::[anchor=>bottom_right, width=>96, height=>14]]@<net>.display()
 * }</pre>
 *
 * Its rec is the panel's POLICY, not its content: {@code max} (rows drawn), {@code paused} (freeze what is drawn),
 * {@code peer} (a lane filter). The rows are a live read of the trace, because a trace is volatile by nature —
 * mirroring it into the rec would spend a store write per tick to keep a copy of something already in memory. So
 * {@code @<net> >>= [paused=>true]} is how a human freezes it, and {@code *<net>} shows how it is configured.
 * <p>
 * As a floating widget it inherits the console's gestures (focus, wheel, chevrons, resize) rather than owning keys.
 * The interactive inspector (arrow-key browsing, enter for the full objs) is the next step, and follows
 * {@link ExplainTool}'s shape: raw mode, a {@code KeyMap}, {@code WidgetCanvas} lines, a {@code Deque} of levels.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class NetworkPanelTool extends AbstractTool<NetworkPanelTool, NetworkPanelTool.Action> {

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
     * what the inspector's keys mean
     */
    enum Action {
        SCROLL_UP,
        SCROLL_DOWN,
        PAGE_UP,
        PAGE_DOWN,
        PAUSE,
        CLEAR,
        TAIL,
        QUIT
    }

    /**
     * the first body line the inspector shows (the list is newest-first, so 0 is the tail)
     */
    private int scroll = 0;

    private static volatile long lastNudgeMs = 0L;
    private static volatile boolean listening = false;

    public NetworkPanelTool(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
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

    // ── lifecycle ──────────────────────────────────────────────────

    /**
     * Display it: floating when the style names an anchor (the normal case), printed once when it does not (a
     * console-less caller, a pipe).
     */
    /**
     * A panel when its style names an anchor, a scrollable inspector when it does not — the same rec either way.
     */
    @Override
    protected boolean modal() {
        // THE MODE IS THE CREATOR'S CHOICE, not the default's: getStyle() supplies a corner anchor so a bare
        // `.display()` is a panel, but "no style at all" must mean the inspector — otherwise the default would
        // silently decide the tool never takes the keyboard
        return !this.explicitStyle().hasFloat();
    }

    /**
     * the style the REC carries, with no defaults applied (see {@link #modal()})
     */
    private Stylable.Style<NetworkPanelTool> explicitStyle() {
        final Obj style = this.at(uri(studio.phaseshift.metatron.Tokens.STYLE));
        return Stylable.Style.isStyle(style) ? Stylable.Style.from(style) : Stylable.Style.empty();
    }

    /**
     * The panel: pin it to the console's floating surface. The console owns its pixels, so nothing here takes the
     * keyboard — the surface's own focus/scroll gestures and `:widgets` apply.
     */
    @Override
    protected void present() {
        final Console console = Console.LOCAL_INSTANCE;
        if (null == console) {
            super.present();   // a test, a script, a pipe: draw once if there is anywhere to draw
            return;
        }
        final Stylable.Style<NetworkPanelTool> style = this.getStyle();
        final FloatingSurface surface = console.getFloatingSurface();
        this.floatAt(surface, style.anchor(), style.width() > 0 ? style.width() : DEFAULT_WIDTH,
                style.top(), style.left());
        surface.render();
    }

    @Override
    protected KeyMap<Action> keyMap() {
        final KeyMap<Action> keys = new KeyMap<>();
        this.bindArrows(keys, Action.SCROLL_UP, Action.SCROLL_DOWN, null, null, null);
        keys.bind(Action.SCROLL_UP, "k");
        keys.bind(Action.SCROLL_DOWN, "j");
        keys.bind(Action.PAGE_UP, "\033[5~");
        keys.bind(Action.PAGE_DOWN, "\033[6~");
        keys.bind(Action.PAUSE, "p");
        keys.bind(Action.CLEAR, "c");
        keys.bind(Action.TAIL, "t");
        keys.bind(Action.QUIT, CTRL_D);
        keys.bind(Action.QUIT, "q");
        return keys;
    }

    @Override
    protected boolean handle(final Action action) {
        switch (action) {
            case SCROLL_UP -> this.scroll = Math.max(0, this.scroll - 1);
            case SCROLL_DOWN -> this.scroll = this.clampScroll(this.scroll + 1);
            case PAGE_UP -> this.scroll = this.clampScroll(this.scroll - Math.max(1, this.viewportRows() - 1));
            case PAGE_DOWN -> this.scroll = this.clampScroll(this.scroll + Math.max(1, this.viewportRows() - 1));
            case PAUSE -> this.pause(!this.paused());
            case CLEAR -> {
                WireTrace.clear();
                WireStats.reset();
                this.scroll = 0;
            }
            case TAIL -> this.scroll = 0;   // newest first: the tail is the top
            case QUIT -> {
                return false;
            }
        }
        return true;
    }

    /**
     * One inspector frame: the same lines the panel draws, windowed to the terminal, with its keys underneath.
     */
    @Override
    protected void redraw() {
        final WidgetCanvas canvas = this.canvas();
        final List<String> lines = this.bodyLines();
        final int rows = this.viewportRows();
        final int from = Math.min(this.clampScroll(this.scroll), Math.max(0, lines.size() - rows));
        for (int i = from; i < Math.min(lines.size(), from + rows); i++)
            canvas.line(lines.get(i));
        this.hint(canvas, this.paused()
                ? "{{r}}PAUSED {{w}}q/ctrl-d{{g}}:close {{w}}j/k{{g}}:scroll {{w}}p{{g}}:resume {{w}}c{{g}}:clear {{w}}t{{g}}:tail {{X}}"
                : "{{w}}q/ctrl-d{{g}}:close {{w}}j/k{{g}}:scroll {{w}}p{{g}}:pause {{w}}c{{g}}:clear {{w}}t{{g}}:tail {{X}}");
        this.settle(canvas);
    }

    /**
     * the body lines at the panel's width, for both the panel's box and the inspector's viewport
     */
    private List<String> bodyLines() {
        final int width = this.getStyle().width() > 0 ? this.getStyle().width() - 2 : DEFAULT_WIDTH - 2;
        return panelLines(WireTrace.snapshot(this.max()), this.paused(), this.max(), this.peerFilter(), width);
    }

    private int viewportRows() {
        final int height = null == this.terminal ? 10 : this.terminal.getHeight();
        return Math.max(3, height - 3);
    }

    private int clampScroll(final int candidate) {
        final int limit = Math.max(0, this.bodyLines().size() - this.viewportRows());
        return Math.max(0, Math.min(limit, candidate));
    }

    /**
     * A display tool must not unfloat: {@code .display()} closes the instance it just ran, and the default close
     * would erase the panel the display was asked to pin.
     */
    @Override
    public void close() {
    }

    // ── the style ──────────────────────────────────────────────────

    /**
     * The style, with this tool's defaults applied when the rec carries none.
     * <p>
     * The defaults are COMPUTED, not written: materializing a {@code Style} into the rec during construction makes
     * the constructor put a typed value through the type checker, and under {@code code_resolve} that path is
     * rejected platform-wide (the style type's predicate reaches runtime unresolved) — which is how a perfectly
     * good rec becomes {@code rec::t} and {@code .display()} refuses it. A panel that stores nothing but what its
     * creator wrote constructs anywhere, and a style the creator DID write is their write, not ours.
     */
    @Override
    public Stylable.Style<NetworkPanelTool> getStyle() {
        final Obj s = this.at(uri(studio.phaseshift.metatron.Tokens.STYLE));
        if (Stylable.Style.isStyle(s))
            return Stylable.Style.from(s);
        final Stylable.Style<NetworkPanelTool> defaults = Stylable.Style.empty();
        defaults.stylable = this;
        defaults.border(Border.continuous);
        defaults.foreground("{{g}}");
        // A PANEL IS SOMETHING YOU WATCH, so it defaults to a corner rather than to the transcript: without an
        // anchor the widget renders once and scrolls away, which is the behaviour this tool exists to replace.
        defaults.floatAt(FloatingSurface.Anchor.BOTTOM_RIGHT, 0, 0, 0);
        return defaults;
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

    @Override
    public String format() {
        final WireTrace.Snapshot snapshot = WireTrace.snapshot(this.max());
        final List<String> lines = panelLines(snapshot, this.paused(), this.max(), this.peerFilter(),
                this.getStyle().width() > 0 ? this.getStyle().width() - 2 : DEFAULT_WIDTH - 2);
        final int width = this.getStyle().width() > 0 ? this.getStyle().width() - 2
                : Math.max(DEFAULT_WIDTH - 2, lines.stream().map(Highlighter::visualLength).max(Integer::compareTo).orElse(0));
        final Border border = this.getStyle().border() == Border.none ? Border.continuous : this.getStyle().border();
        final String title = " net ";
        final StringBuilder sb = new StringBuilder();
        sb.append(Widget.X).append(border.topLeftCorner()).append(title);
        final int rest = width - Highlighter.visualLength(title);
        if (rest > 0) sb.append(border.topSide().repeat(rest));
        sb.append(border.topRightCorner()).append(Widget.X).append("\n");
        for (final String line : lines) {
            // the interior is `width` columns INCLUDING the leading space, or every row runs one column past the
            // top border and the right edge comes out ragged — most visibly on a line that ends in an ellipsis
            final int pad = Math.max(0, width - 1 - Highlighter.visualLength(line));
            sb.append(Widget.X).append(border.leftSide()).append(Widget.X)
                    .append(this.getStyle().foreground()).append(' ').append(line).append(" ".repeat(pad))
                    .append(Widget.X).append(border.rightSide()).append(Widget.X).append("\n");
        }
        sb.append(Widget.X).append(border.bottomLeftCorner()).append(border.bottomSide().repeat(width))
                .append(border.bottomRightCorner()).append(Widget.X);
        return sb.toString();
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
        final List<String> lines = new ArrayList<>();
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
        int remaining = maxRows - 1;
        for (final WireTrace.Lane lane : snapshot.lanes()) {
            if (remaining <= 1)
                break;
            if (!peerFilter.isEmpty() && !lane.peer().contains(peerFilter))
                continue;
            final String last = lane.lastLatencyMs() < 0 ? "" : "  " + lane.lastLatencyMs() + "ms";
            final String timeout = lane.timeouts() > 0 ? "  {{r}}\u2717 " + lane.timeouts() : "";
            lines.add(clip("{{c}}▼{{w}} %s  {{y}}⇄%d%s%s".formatted(lane.peer(), lane.exchanges(), last, timeout), width));
            remaining--;
            for (final WireTrace.Row row : lane.rows()) {
                if (remaining <= 0)
                    break;
                final String mark = row.ok() ? "{{w}}→" : "{{r}}✗";
                final String elapsed = row.ok() ? row.elapsedMs() + "ms" : "timeout";
                final String body = row.request().isEmpty() ? row.reply() : row.request();
                lines.add(clip("{{w}}  " + mark + " {{m}}" + row.kind() + "{{w}} " + elapsed + "  "
                        + (row.ok() ? "{{w}}" : "{{r}}") + body, width));
                remaining--;
            }
            if (remaining > 0 && lane.rows().size() < lane.exchanges()) {
                lines.add(clip("{{w}}  … %d older".formatted(lane.exchanges() - lane.rows().size()), width));
                remaining--;
            }
        }
        if (snapshot.lanes().isEmpty()) {
            lines.add(clip("{{w}}  no peers have spoken yet", width));
        } else if (snapshot.dropped() > 0 && remaining > 0) {
            lines.add(clip("{{w}}  (dropped %d off the window)".formatted(snapshot.dropped()), width));
        }
        return lines;
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
