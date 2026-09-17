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
import studio.phaseshift.metatron.isa.m.type.reflect.SpaceRec;
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
public class AccordionWidget extends SpaceRec<AccordionWidget> implements Widget<AccordionWidget> {

    private static final Obj K_TITLE = uri("title");
    private static final Obj K_BODY = uri("body");
    private static final Obj K_EXP = uri("expanded");
    private static final Obj K_TOGGLE = uri("toggle");

    private static final String EXPAND_INDICATOR = "[-]";
    private static final String COLLAPSE_INDICATOR = "[+]";

    // No state fields: `title`, `body`, `expanded` and `style` are the rec's
    // (title/body are declared by the accordion type, style by the widget base),
    // and that is the only copy.  Nothing here needs rehydrating because nothing
    // here holds data.

    // ── constructor ────────────────────────────────────────────────

    public AccordionWidget(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(new ConcurrentHashMap<>(jvm), tid, vid);
        // The style lives in the rec (materialised with this widget's defaults
        // on first construction), never in a Java field a re-hydration loses.
        this.readStyle();
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
        this.put(K_EXP, bool(true));
    }

    public void collapse() {
        this.put(K_EXP, bool(false));
    }

    public void toggle() {
        this.put(K_EXP, bool(!this.isExpanded()));
    }

    public AccordionWidget title(final String t) {
        this.put(K_TITLE, str(t));
        return this;
    }

    public AccordionWidget body(final String text) {
        this.put(K_BODY, str(text != null ? text : ""));
        return this;
    }

    /**
     * Append text to the body — read the anchored lines, add, write back.  No
     * Java-side buffer, no flush, no rehydration: the rec is the text, and this
     * one merge is the only thing an append does.
     */
    public AccordionWidget appendLine(final String line) {
        if (null == line || line.isEmpty()) return this;
        final List<String> lines = this.getLines(this.read(), K_BODY);
        for (final String text : line.replace("\\n", "\n").split("\n", -1))
            lines.add(text);
        this.put(K_BODY, str(String.join("\n", lines)));
        return this;
    }

    // ── public accessors ───────────────────────────────────────────

    public boolean isExpanded() {
        return this.getBool(this.read(), K_EXP, true);
    }

    public String title() {
        return this.getStr(this.read(), K_TITLE);
    }

    public List<String> bodyLines() {
        return this.getLines(this.read(), K_BODY);
    }

    public AccordionWidget clearBody() {
        this.put(K_BODY, str(""));
        return this;
    }

    @Override
    public AccordionWidget cursor(final Cursor c) {
        return this;   // a layout hint for a parent widget (GridWidget); nothing reads it back
    }

    /**
     * Columns the {@code [-]} / {@code [+]} indicator occupies in the header.
     */
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
        return Style.from(this.get(this.read(), STYLE_KEY));
    }

    @Override
    public AccordionWidget style(final Style<AccordionWidget> s) {
        final Style<AccordionWidget> st = null == s ? Style.empty() : s;
        st.stylable = this;
        if (st.border() == Border.none) st.border(Border.continuous);
        if (st.foreground().isEmpty()) st.foreground("{{g}}");
        this.put(STYLE_KEY, st);
        return this;
    }

    // ── lifecycle ──────────────────────────────────────────────────

    /**
     * A display widget must NOT take the default {@link Widget#close()}: that
     * unfloats the widget from the console's surface, and {@code .display()}
     * closes what it just ran — which would erase the widget it just pinned.
     */
    @Override
    public void close() {
    }

    @Override
    public int height() {
        final Map<Obj, Obj> fields = this.read();   // one anchored read, like format()
        if (!this.getBool(fields, K_EXP, true)) return 2;
        final List<String> lines = this.displayLines(this.getLines(fields, K_BODY),
                Style.from(this.get(fields, STYLE_KEY)));
        return lines.isEmpty() ? 2 : lines.size() + 2;
    }

    /**
     * Body lines after word-wrap (if floatWidth is set on the style).
     */
    private List<String> displayLines(final List<String> body, final Style<AccordionWidget> style) {
        final int floatW = style.width();
        if (floatW <= 0) return body;
        return Stylable.Style.wrapLines(body, floatW - 3);
    }

    /**
     * Adopt the style the rec carries, materialising this widget's defaults into
     * the rec when it carries none.  Read through {@link #read()} so an anchored
     * widget adopts what the space holds, not a construction-time snapshot.
     */
    private void readStyle() {
        final Obj s = this.get(this.read(), STYLE_KEY);
        if (Style.isStyle(s)) {
            this.style(Style.from(s));
            return;
        }
        final Style<AccordionWidget> fresh = Style.empty();
        fresh.stylable = this;
        fresh.border(Border.continuous);
        fresh.foreground("{{g}}");
        this.put(STYLE_KEY, fresh);
    }

    // ── rendering ──────────────────────────────────────────────────

    @Override
    public String format() {
        // ONE anchored read for the whole pass: an anchored widget renders (and
        // toggles) from the space, not from a construction-time snapshot
        final long __p0 = System.nanoTime();
        final Map<Obj, Obj> fields = this.read();
        final long __p1 = System.nanoTime();
        final String title = this.getStr(fields, K_TITLE);
        final boolean expanded = this.getBool(fields, K_EXP, true);
        final List<String> body = this.getLines(fields, K_BODY);
        final Style<AccordionWidget> style = Style.from(this.get(fields, STYLE_KEY));

        // A folded accordion shows nothing but its header, so the header says
        // how much it is holding: a folded box with text in it and a box with
        // an empty body must not look the same ("where did my text go" is
        // otherwise indistinguishable from "the text never arrived").
        final String ind = expanded ? EXPAND_INDICATOR
                : (body.isEmpty() ? COLLAPSE_INDICATOR : COLLAPSE_INDICATOR + " " + body.size());

        // Latch instructions and style from JVM on first render
        if (!this.read().containsKey(K_TOGGLE)) {
            readStyle();
            this.put(K_TOGGLE, instLambda((l, i) -> {
                this.toggle();
                return noobj();
            }), MUTABLE);
            this.put(uri("expand"), instLambda((l, i) -> {
                this.expand();
                return noobj();
            }), MUTABLE);
            this.put(uri("collapse"), instLambda((l, i) -> {
                this.collapse();
                return noobj();
            }), MUTABLE);
            this.put(uri("append"), instLambda((l, i) -> {
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
        final int floatW = style.width();
        // Height: an explicit style height grows the box past its content (the
        // body is padded with blank rows), so a vertical resize enlarges the
        // box instead of only ever clipping it.
        final int floatH = style.height();
        final List<String> displayLines = floatW > 0 ? displayLines(body, style) : new ArrayList<>(body);
        final long __p2 = System.nanoTime();
        if (Boolean.getBoolean("metatron.render.trace"))
            System.err.println("[render]   acc anchored-read=" + (__p1 - __p0) / 1_000_000
                    + "ms body=" + (__p2 - __p1) / 1_000_000 + "ms lines=" + displayLines.size());
        final int bodyWidth = displayLines.stream().map(Highlighter::visualLength).max(Integer::compareTo).orElse(0);
        final int titleW = Highlighter.visualLength(title) + Highlighter.visualLength(ind) + 3;
        // Width: an explicit style width makes the box that wide — the body and
        // title pad to it — so the box fills its footprint instead of leaving a
        // blank gap and, for a right/middle anchor, sliding instead of resizing.
        final int width = floatW > 0 ? Math.max(titleW, floatW - 2)
                : Math.max(titleW, bodyWidth + 3);

        final Border border = style.border() == Border.none ? Border.continuous : style.border();
        // read the highlight language ONCE per pass, not once per body line
        final String language = style.highlight();
        final StringBuilder sb = new StringBuilder();

        if (expanded && !displayLines.isEmpty()) {
            buildTitleBar(sb, border, title, ind, width);
            sb.append("\n");
            for (final String line : displayLines) {
                sb.append(Widget.X).append(border.leftSide()).append(Widget.X)
                        .append(style.foreground()).append(" ").append(Highlighter.highlightLine(language, line))
                        .repeat(" ", Math.max(0, width - Highlighter.visualLength(line) - 1))
                        .append(Widget.X).append(border.rightSide()).append(Widget.X).append("\n");
            }
            // a style height taller than the content pads the body with blank
            // rows — the box grows to the cap instead of only ever clipping
            for (int h = displayLines.size() + 2; h < floatH; h++)
                appendBlankBodyLine(sb, border, style, width);
            sb.append(Widget.X).append(border.bottomLeftCorner())
                    .append(border.bottomSide().repeat(width))
                    .append(border.bottomRightCorner()).append(Widget.X);
        } else {
            buildTitleBar(sb, border, title, ind, width);
            sb.append("\n");
            // collapsed (or empty): the box is just its header + bottom border —
            // no height padding, so collapsing after a resize shrinks the border
            // back to a sliver instead of leaving a big empty box
            sb.append(Widget.X).append(border.bottomLeftCorner())
                    .append(border.bottomSide().repeat(width))
                    .append(border.bottomRightCorner()).append(Widget.X);
        }
        return sb.toString();
    }

    /**
     * A blank body row, drawn with the box's own borders and foreground so a
     * height-padded body keeps its box — {@code width} columns of nothing
     * between the left and right sides.
     */
    private void appendBlankBodyLine(final StringBuilder sb, final Border border,
                                     final Style<AccordionWidget> style, final int width) {
        sb.append(Widget.X).append(border.leftSide()).append(Widget.X)
                .append(style.foreground()).append(" ")
                .repeat(" ", Math.max(0, width - 1))
                .append(Widget.X).append(border.rightSide()).append(Widget.X).append("\n");
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
        return this.format() + "\n";
    }

    @Override
    public String renderFresh() {
        return this.format() + "\n";
    }
}
