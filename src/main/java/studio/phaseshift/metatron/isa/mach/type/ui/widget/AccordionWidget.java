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

package studio.phaseshift.metatron.isa.mach.type.ui.widget;

import org.jline.terminal.Cursor;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.reflect.JRec;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.Stylable;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_ACCORDION_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class AccordionWidget extends JRec<AccordionWidget> implements Widget<AccordionWidget> {

    private static final Obj K_TITLE = uri("title");
    private static final Obj K_BODY = uri("body");
    private static final Obj K_EXP = uri("expanded");
    private static final Obj K_TOGGLE = uri("toggle");

    private static final String EXPAND_INDICATOR = "[-]";
    private static final String COLLAPSE_INDICATOR = "[+]";

    private Style<AccordionWidget> style = Style.empty();
    private int lastRenderHeight;
    private Cursor cursor;

    /**
     * Buffered appends — coalesced to avoid O(n²) string joining and per-line Router reads.
     */
    private StringBuilder pendingBuffer = new StringBuilder();
    private static final int BUFFER_FLUSH_THRESHOLD = 4096;

    // ── JRec constructor ───────────────────────────────────────────

    public AccordionWidget(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(new ConcurrentHashMap<>(jvm), tid, vid);
        if (this.style.border() == Border.none) this.style.border(Border.continuous);
        if (this.style.foreground().isEmpty()) this.style.foreground("{{g}}");
        // Pull style config (incl. float) from the JVM so run() sees it
        readStyle();
    }

    /**
     * JRec rehydration (a widget read back from a serialized space) bypasses the
     * constructor, so the plain-Java transient fields are left null.  Restore
     * them before any method touches {@link #style} or {@link #pendingBuffer}.
     */
    private void ensureRehydrated() {
        if (null == this.style) {
            this.style = Style.empty();
            if (this.style.border() == Border.none) this.style.border(Border.continuous);
            if (this.style.foreground().isEmpty()) this.style.foreground("{{g}}");
        }
        if (null == this.pendingBuffer)
            this.pendingBuffer = new StringBuilder();
    }

    // ── convenience constructors ───────────────────────────────────

    public AccordionWidget() {
        this(Map.of(), UI_ACCORDION_TID, null);
    }

    public AccordionWidget(final String title) {
        this();
        this.title(title);
    }

    public AccordionWidget(final String title, final String body) {
        this();
        this.title(title);
        this.body(body);
    }

    // ── state mutators (write through to persistent store) ─────────

    public void expand() {
        jvmWrite(K_EXP, bool(true));
    }

    public void collapse() {
        jvmWrite(K_EXP, bool(false));
    }

    public void toggle() {
        jvmWrite(K_EXP, bool(!this.isExpanded()));
    }

    public AccordionWidget title(final String t) {
        jvmWrite(K_TITLE, str(t));
        return this;
    }

    public AccordionWidget body(final String text) {
        ensureRehydrated();
        synchronized (this.pendingBuffer) {
            this.pendingBuffer.setLength(0);
        }
        jvmWrite(K_BODY, str(text != null ? text : ""));
        return this;
    }

    /**
     * Append a line to the body. Lines are buffered in memory and flushed to the
     * backing JVM when the buffer exceeds {@link #BUFFER_FLUSH_THRESHOLD} chars,
     * or when {@link #flush()}, {@link #format()}, {@link #bodyLines()}, or
     * {@link #height()} is called.
     */
    public AccordionWidget appendLine(final String line) {
        ensureRehydrated();
        if (line == null || line.isEmpty()) return this;
        boolean shouldFlush = false;
        synchronized (this.pendingBuffer) {
            if (this.pendingBuffer.length() > 0) {
                this.pendingBuffer.append('\n');
            }
            this.pendingBuffer.append(line);
            shouldFlush = this.pendingBuffer.length() >= BUFFER_FLUSH_THRESHOLD;
        }
        if (shouldFlush) {
            flush();
        }
        return this;
    }

    /**
     * Flush the pending append buffer to the JVM backing store.
     * Idempotent — safe to call from render loops.
     */
    public AccordionWidget flush() {
        ensureRehydrated();
        final String pending;
        synchronized (this.pendingBuffer) {
            if (this.pendingBuffer.length() == 0) return this;
            pending = this.pendingBuffer.toString();
            this.pendingBuffer.setLength(0);
        }
        // I/O outside the lock — read + merge + write
        final Obj existing = field(jvmRead(), K_BODY);
        final String existingStr = (existing != null && existing.isStr()) ? existing.strValue() : "";
        final String newBody = existingStr.isEmpty() ? pending : existingStr + "\n" + pending;
        jvmWrite(K_BODY, str(newBody));
        return this;
    }

    // ── public accessors ───────────────────────────────────────────

    /**
     * A field read through the widget's SOURCE OF TRUTH.
     *
     * <p>{@code at()} reads the construction-time snapshot, which is the whole
     * story for an ephemeral widget but wrong for a pinned, store-backed one
     * ({@code accordion_widget::[...]@<think_widget>}): {@link #jvmWrite} lands
     * a mutation in the space, so a read that does not go to the space would
     * keep serving the pre-mutation value — an accordion would fold in the
     * store and go on drawing itself unfolded.  Reads are taken through
     * {@link #jvmRead()} (the same source {@code format()} renders from), with
     * a uri-name fallback because a store round-trip need not preserve key
     * identity.
     */
    private static Obj field(final Map<Obj, Obj> fields, final Obj key) {
        final Obj direct = fields.get(key);
        if (null != direct) return direct;
        for (final Map.Entry<Obj, Obj> entry : fields.entrySet())
            if (entry.getKey().isUri() && key.isUri()
                    && entry.getKey().uriValue().equals(key.uriValue()))
                return entry.getValue();
        return null;
    }

    public boolean isExpanded() {
        return isExpanded(jvmRead());
    }

    private boolean isExpanded(final Map<Obj, Obj> fields) {
        final Obj e = field(fields, K_EXP);
        return null == e || !e.isBool() || e.boolValue();
    }

    public String title() {
        return title(jvmRead());
    }

    private String title(final Map<Obj, Obj> fields) {
        final Obj t = field(fields, K_TITLE);
        return null != t && t.isStr() ? t.strValue() : "";
    }

    public List<String> bodyLines() {
        flush();
        return this.readBody(jvmRead());
    }

    public AccordionWidget clearBody() {
        ensureRehydrated();
        synchronized (this.pendingBuffer) {
            this.pendingBuffer.setLength(0);
        }
        jvmWrite(K_BODY, str(""));
        return this;
    }

    @Override
    public AccordionWidget cursor(final Cursor c) {
        this.cursor = c;
        return this;
    }

    /** Columns the {@code [-]} / {@code [+]} indicator occupies in the header. */
    private static final int INDICATOR_WIDTH = 3;

    /**
     * A click on the header's {@code [-]} / {@code [+]} glyph folds or unfolds
     * the accordion — the pointer's way of doing what {@code @<widget>.toggle()}
     * does.
     *
     * <p>The target is the glyph, NOT the whole header: a header click is how a
     * pointer focuses and scrolls a widget, and folding on the way in turns
     * "let me read this" into "where did the text go" (a folded accordion draws
     * nothing but its header).  The glyph sits in the first rendered row after
     * the border cell, a space, the title and another space (see
     * {@link #buildTitleBar}).
     *
     * <p>Anywhere else — including the header — is none of this widget's
     * business: the click falls through and the console focuses the widget.
     */
    @Override
    public boolean onClick(final int row, final int col) {
        if (row != 0) return false;
        final int start = 3 + Highlighter.visualLength(this.title());
        if (col < start || col >= start + INDICATOR_WIDTH) return false;
        this.toggle();
        return true;
    }

    @Override
    public Style<AccordionWidget> getStyle() {
        ensureRehydrated();
        return this.style;
    }

    @Override
    public AccordionWidget style(final Style<AccordionWidget> s) {
        this.style = s;
        if (this.style.border() == Border.none) this.style.border(Border.continuous);
        if (this.style.foreground().isEmpty()) this.style.foreground("{{g}}");
        return this;
    }

    // ── lifecycle ──────────────────────────────────────────────────

    @Override
    public void close() {
        if (this.lastRenderHeight > 0) {
            Graphitty.out(Console.getTerminal().output(), "\033[" + this.lastRenderHeight + "A\033[J");
            this.lastRenderHeight = 0;
        }
    }

    @Override
    public int height() {
        flush();
        final long __p0 = System.nanoTime();
        final Map<Obj, Obj> fields = jvmRead();
        final long __p1 = System.nanoTime();
        if (!this.isExpanded(fields)) return 2;
        final List<String> lines = this.displayLines(fields);
        return lines.isEmpty() ? 2 : lines.size() + 2;
    }

    /**
     * Body lines after word-wrap (if floatWidth is set on the style).
     */
    private List<String> displayLines(final Map<Obj, Obj> fields) {
        final List<String> body = this.readBody(fields);
        final int floatW = this.style.width();
        if (floatW <= 0) return body;
        return Stylable.Style.wrapLines(body, floatW - 3);
    }

    /**
     * Read the body from the rec, resolving any auto_ expression.
     */
    private List<String> readBody(final Map<Obj, Obj> fields) {
        final Obj b = field(fields, K_BODY);
        final List<String> lines = new ArrayList<>();
        if (b != null && !b.isNoObj()) {
            if (b.isStr())
                java.util.Arrays.asList(b.strValue().replace("\\n", "\n").split("\n", -1)).forEach(lines::add);
            else
                b.stream().filter(Obj::isStr).forEach(o -> lines.add(o.strValue()));
        }
        return lines;
    }

    private void readStyle() {
        final Obj s = this.at(uri("style"));
        if (s != null && s.isRec()) {
            final Style<AccordionWidget> st = Style.from(s.as());
            st.stylable = this;
            this.style(st);
        }
    }

    // ── rendering ──────────────────────────────────────────────────

    @Override
    public String format() {
        ensureRehydrated();
        flush();  // persist buffered appends before rendering
        // ONE read of the source of truth for the whole pass: a store-backed
        // widget must render (and toggle) from the space, not from a snapshot
        final long __p0 = System.nanoTime();
        final Map<Obj, Obj> fields = jvmRead();
        final long __p1 = System.nanoTime();
        final String title = this.title(fields);
        final boolean expanded = this.isExpanded(fields);
        final List<String> body = this.readBody(fields);

        // A folded accordion shows nothing but its header, so the header says
        // how much it is holding: a folded box with text in it and a box with
        // an empty body must not look the same ("where did my text go" is
        // otherwise indistinguishable from "the text never arrived").
        final String ind = expanded ? EXPAND_INDICATOR
                : (body.isEmpty() ? COLLAPSE_INDICATOR : COLLAPSE_INDICATOR + " " + body.size());

        // Latch instructions and style from JVM on first render
        if (!jvmRead().containsKey(K_TOGGLE)) {
            readStyle();
            this.at(K_TOGGLE, instLambda((l, i) -> {
                this.toggle();
                return noobj();
            }), MUTABLE);
            this.at(uri("expand"), instLambda((l, i) -> {
                this.expand();
                return noobj();
            }), MUTABLE);
            this.at(uri("collapse"), instLambda((l, i) -> {
                this.collapse();
                return noobj();
            }), MUTABLE);
            this.at(uri("append"), instLambda((l, i) -> {
                this.appendLine(l.isStr() ? l.strValue() : "");
                // Defer rendering to the Console prompt cycle — rendering
                // mid-stream fights with the console cursor.
                if (Console.LOCAL_INSTANCE != null) {
                    Console.LOCAL_INSTANCE.requestRedraw();
                }
                return this;
            }), MUTABLE);
        }

        // Width: use floatWidth from style if set, else compute from content
        final int floatW = this.style.width();
        final List<String> displayLines = floatW > 0 ? displayLines(fields) : new ArrayList<>(body);
        final long __p2 = System.nanoTime();
        if (Boolean.getBoolean("metatron.render.trace"))
            System.err.println("[render]   acc jvmRead=" + (__p1 - __p0) / 1_000_000
                    + "ms body=" + (__p2 - __p1) / 1_000_000 + "ms lines=" + displayLines.size());
        final int bodyWidth = displayLines.stream().map(Highlighter::visualLength).max(Integer::compareTo).orElse(0);
        final int titleW = Highlighter.visualLength(title) + Highlighter.visualLength(ind) + 3;
        final int width = floatW > 0 ? Math.max(titleW, Math.min(Math.max(1, floatW - 2), bodyWidth + 3))
                : Math.max(titleW, bodyWidth + 3);

        final Border border = this.style.border() == Border.none ? Border.continuous : this.style.border();
        // read the highlight language ONCE per pass, not once per body line
        final String language = this.style.highlight();
        final StringBuilder sb = new StringBuilder();

        if (expanded && !displayLines.isEmpty()) {
            buildTitleBar(sb, border, title, ind, width);
            sb.append("\n");
            for (final String line : displayLines) {
                sb.append(Widget.X).append(border.leftSide()).append(Widget.X)
                        .append(this.style.foreground()).append(" ").append(Highlighter.highlightLine(language, line))
                        .repeat(" ", Math.max(0, width - Highlighter.visualLength(line) - 1))
                        .append(Widget.X).append(border.rightSide()).append(Widget.X).append("\n");
            }
            sb.append(Widget.X).append(border.bottomLeftCorner())
                    .append(border.bottomSide().repeat(width))
                    .append(border.bottomRightCorner()).append(Widget.X);
        } else {
            buildTitleBar(sb, border, title, ind, width);
            sb.append("\n");
            sb.append(Widget.X).append(border.bottomLeftCorner())
                    .append(border.bottomSide().repeat(width))
                    .append(border.bottomRightCorner()).append(Widget.X);
        }
        return sb.toString();
    }

    private void buildTitleBar(final StringBuilder sb, final Border border,
                               final String title, final String ind, final int width) {
        final String t = " " + title + " " + ind + " ";
        sb.append(Widget.X).append(border.topLeftCorner()).append(t);
        final int r = width - Highlighter.visualLength(t);
        if (r > 0) sb.append(border.topSide().repeat(r));
        sb.append(border.topRightCorner()).append(Widget.X);
    }

    @Override
    public String toString() {
        return this.format();
    }

    @Override
    public String renderInPlace() {
        final String f = this.format();
        final int n = f.split("\n").length;
        final StringBuilder sb = new StringBuilder();
        if (this.lastRenderHeight > 0) {
            sb.append("\033[").append(this.lastRenderHeight).append("A\033[J");
        }
        sb.append(f).append("\n");
        this.lastRenderHeight = n + 1;
        return sb.toString();
    }

    @Override
    public String renderFresh() {
        this.lastRenderHeight = 0;
        return this.format() + "\n";
    }
}
