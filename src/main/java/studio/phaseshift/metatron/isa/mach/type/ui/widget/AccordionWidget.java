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
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_ACCORDIAN_TID;

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

    // ── JRec constructor ───────────────────────────────────────────

    public AccordionWidget(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(new java.util.HashMap<>(jvm), tid, vid);
        if (this.style.border() == Border.none) this.style.border(Border.continuous);
        if (this.style.foreground().isEmpty()) this.style.foreground("{{g}}");
        // Pull style config (incl. float) from the JVM so run() sees it
        readStyle(this.jvm());
    }

    // ── convenience constructors ───────────────────────────────────

    public AccordionWidget() {
        this(Map.of(), UI_ACCORDIAN_TID, null);
    }

    public AccordionWidget(final String title) {
        this();
        title(title);
    }

    public AccordionWidget(final String title, final String body) {
        this();
        title(title);
        body(body);
    }

    // ── state mutators (write through to persistent store) ─────────

    public void expand() {
        jvmWrite(K_EXP, bool(true));
    }

    public void collapse() {
        jvmWrite(K_EXP, bool(false));
    }

    public void toggle() {
        jvmWrite(K_EXP, bool(!jvmBool(jvmRead(), K_EXP)));
    }

    public AccordionWidget title(final String t) {
        jvmWrite(K_TITLE, str(t));
        return this;
    }

    public AccordionWidget body(final String text) {
        jvmWrite(K_BODY, str(text != null ? text : ""));
        return this;
    }

    public AccordionWidget appendLine(final String line) {
        if (line == null || line.isEmpty()) return this;
        final List<String> cur = jvmBody(jvmRead(), K_BODY);
        cur.add(line);
        jvmWrite(K_BODY, str(String.join("\n", cur)));
        return this;
    }

    // ── public accessors ───────────────────────────────────────────

    public boolean isExpanded() {
        return jvmBool(jvmRead(), K_EXP);
    }

    public String title() {
        return jvmStr(jvmRead(), K_TITLE);
    }

    public List<String> bodyLines() {
        return jvmBody(jvmRead(), K_BODY);
    }

    public AccordionWidget clearBody() {
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
            Graphitty.out(Console.getTerminal().output(), "\033[" + this.lastRenderHeight + "A\033[J");
            this.lastRenderHeight = 0;
        }
    }

    @Override
    public int height() {
        final Map<Obj, Obj> jvm = jvmRead();
        final boolean expanded = jvmBool(jvm, K_EXP);
        final List<String> body = jvmBody(jvm, K_BODY);
        return (expanded && !body.isEmpty()) ? body.size() + 2 : 2;
    }

    private String indicator(final Map<Obj, Obj> jvm) {
        return jvmBool(jvm, K_EXP) ? EXPAND_INDICATOR : COLLAPSE_INDICATOR;
    }

    private void readStyle(final Map<Obj, Obj> jvm) {
        final Obj s = jvm.get(uri("style"));
        if (s != null && s.isRec()) {
            final Style<AccordionWidget> st = Style.from(s.as());
            st.stylable = this;
            this.style(st);
        }
    }

    // ── rendering ──────────────────────────────────────────────────

    @Override
    public String format() {
        final Map<Obj, Obj> jvm = jvmRead();          // single source read
        final String title = jvmStr(jvm, K_TITLE);
        final boolean expanded = jvmBool(jvm, K_EXP);
        final List<String> body = jvmBody(jvm, K_BODY);
        final String ind = indicator(jvm);

        // Latch instructions and style from JVM on first render
        if (!jvm.containsKey(K_TOGGLE)) {
            readStyle(jvm);
            this.at(K_TOGGLE, instLambda((l, i) -> {
                this.toggle();
                Graphitty.out(Console.getTerminal().output(), this.format() + "\n");
                return noobj();
            }), MUTABLE);
            this.at(uri("expand"), instLambda((l, i) -> {
                this.expand();
                Graphitty.out(Console.getTerminal().output(), this.format() + "\n");
                return noobj();
            }), MUTABLE);
            this.at(uri("collapse"), instLambda((l, i) -> {
                this.collapse();
                Graphitty.out(Console.getTerminal().output(), this.format() + "\n");
                return noobj();
            }), MUTABLE);
            this.at(uri("append"), instLambda((l, i) -> {
                this.appendLine(l.isStr() ? l.strValue() : "");
                Graphitty.out(Console.getTerminal().output(), this.format() + "\n");
                return this;
            }), MUTABLE);
        }

        final int bodyWidth = body.stream().map(Highlighter::visualLength).max(Integer::compareTo).orElse(0);
        final int titleW = Highlighter.visualLength(title) + Highlighter.visualLength(ind) + 3;
        final int width = Math.max(titleW, bodyWidth + 2);
        final Border border = this.style.border() == Border.none ? Border.continuous : this.style.border();
        final StringBuilder sb = new StringBuilder();

        if (expanded && !body.isEmpty()) {
            buildTitleBar(sb, border, title, ind, width);
            sb.append("\n");
            for (final String line : body) {
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
