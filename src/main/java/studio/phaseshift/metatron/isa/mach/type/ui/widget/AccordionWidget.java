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
import studio.phaseshift.metatron.isa.m.type.reflect.JRecElement;
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

    private static final Obj K_TITLE  = uri("title");
    private static final Obj K_BODY   = uri("body");
    private static final Obj K_EXP    = uri("expanded");
    private static final Obj K_TOGGLE = uri("toggle");

    // @JRecElement annotations are metadata for mtron introspection only.
    @JRecElement(key = "title", rng = "/m/str")
    private String _title = "";
    @JRecElement(key = "expanded", rng = "/m/bool")
    private boolean _expanded = true;

    private static final String EXPAND_INDICATOR = "[-]";
    private static final String COLLAPSE_INDICATOR = "[+]";

    private Style<AccordionWidget> style = Style.empty();
    private int lastRenderHeight;
    private Cursor cursor;

    /** Buffered appends — coalesced to avoid O(n²) string joining and per-line Router reads. */
    private final StringBuilder pendingBuffer = new StringBuilder();
    private static final int BUFFER_FLUSH_THRESHOLD = 4096;

    // ── JRec constructor ───────────────────────────────────────────

    public AccordionWidget(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(new ConcurrentHashMap<>(jvm), tid, vid);
        if (this.style.border() == Border.none) this.style.border(Border.continuous);
        if (this.style.foreground().isEmpty()) this.style.foreground("{{g}}");
        // Pull style config (incl. float) from the JVM so run() sees it
        readStyle();
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

    public void expand()   { jvmWrite(K_EXP, bool(true)); }
    public void collapse() { jvmWrite(K_EXP, bool(false)); }
    public void toggle()   { jvmWrite(K_EXP, bool(!this.isExpanded())); }

    public AccordionWidget title(final String t) {
        jvmWrite(K_TITLE, str(t));
        return this;
    }

    public AccordionWidget body(final String text) {
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
        final String pending;
        synchronized (this.pendingBuffer) {
            if (this.pendingBuffer.length() == 0) return this;
            pending = this.pendingBuffer.toString();
            this.pendingBuffer.setLength(0);
        }
        // I/O outside the lock — read + merge + write
        final Obj existing = this.at(K_BODY);
        final String existingStr = (existing != null && existing.isStr()) ? existing.strValue() : "";
        final String newBody = existingStr.isEmpty() ? pending : existingStr + "\n" + pending;
        jvmWrite(K_BODY, str(newBody));
        return this;
    }

    // ── public accessors ───────────────────────────────────────────

    public boolean isExpanded() {
        final Obj e = this.at(K_EXP);
        return null == e || !e.isBool() || e.boolValue();
    }

    public String title() {
        final Obj t = this.at(K_TITLE);
        return null != t && t.isStr() ? t.strValue() : "";
    }

    public List<String> bodyLines() {
        flush();
        return this.readBody();
    }

    public AccordionWidget clearBody() {
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

    @Override
    public Style<AccordionWidget> getStyle() {
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
            Graphitty.writeToTerminal("\033[" + this.lastRenderHeight + "A\033[J");
            this.lastRenderHeight = 0;
        }
    }

    @Override
    public int height() {
        flush();
        if (!this.isExpanded()) return 2;
        final List<String> lines = this.displayLines();
        return lines.isEmpty() ? 2 : lines.size() + 2;
    }

    /** Body lines after word-wrap (if floatWidth is set on the style). */
    private List<String> displayLines() {
        final List<String> body = this.readBody();
        final int floatW = this.style.width();
        if (floatW <= 0) return body;
        return Stylable.Style.wrapLines(body, floatW - 3);
    }

    /** Read the body from the rec, resolving any auto_ expression. */
    private List<String> readBody() {
        final Obj b = this.at(K_BODY);
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
        flush();  // persist buffered appends before rendering
        final String title = this.title();
        final boolean expanded = this.isExpanded();
        final List<String> body = this.readBody();
        final String ind = expanded ? EXPAND_INDICATOR : COLLAPSE_INDICATOR;

        // Latch instructions and style from JVM on first render
        if (!jvmRead().containsKey(K_TOGGLE)) {
            readStyle();
            this.at(K_TOGGLE, instLambda((l, i) -> { this.toggle(); return noobj(); }), MUTABLE);
            this.at(uri("expand"), instLambda((l, i) -> { this.expand();  return noobj(); }), MUTABLE);
            this.at(uri("collapse"), instLambda((l, i) -> { this.collapse(); return noobj(); }), MUTABLE);
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
        final List<String> displayLines = floatW > 0 ? displayLines() : new ArrayList<>(body);
        final int bodyWidth = displayLines.stream().map(Highlighter::visualLength).max(Integer::compareTo).orElse(0);
        final int titleW = Highlighter.visualLength(title) + Highlighter.visualLength(ind) + 3;
        final int width = floatW > 0 ? Math.max(titleW, Math.min(Math.max(1, floatW - 2), bodyWidth + 3))
                                     : Math.max(titleW, bodyWidth + 3);

        final Border border = this.style.border() == Border.none ? Border.continuous : this.style.border();
        final StringBuilder sb = new StringBuilder();

        if (expanded && !displayLines.isEmpty()) {
            buildTitleBar(sb, border, title, ind, width);
            sb.append("\n");
            for (final String line : displayLines) {
                sb.append(Widget.X).append(border.leftSide()).append(Widget.X)
                        .append(this.style.foreground()).append(" ").append(line)
                        .append(" ".repeat(Math.max(0, width - Highlighter.visualLength(line) - 1)))
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
