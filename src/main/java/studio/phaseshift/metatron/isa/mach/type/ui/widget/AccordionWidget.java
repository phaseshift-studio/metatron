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
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.reflect.JRec;
import studio.phaseshift.metatron.isa.m.type.reflect.JRecElement;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.Stylable;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.INST_CTOR_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.STR_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_ACCORDIAN_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class AccordionWidget extends JRec<AccordionWidget> implements Widget<AccordionWidget> {

    private static final String KEY_TITLE = "title";
    private static final String KEY_BODY  = "body";
    private static final String KEY_EXP   = "expanded";

    @JRecElement(key = KEY_TITLE, rng = "/m/str")
    public String _title = "";

    // body managed manually via init() + this.at(uri(KEY_BODY), ...) — no @JRecElement
    public List<String> _body = new ArrayList<>();

    @JRecElement(key = KEY_EXP, rng = "/m/bool")
    public boolean _expanded = true;

    private static final String EXPAND_INDICATOR  = "[-]";
    private static final String COLLAPSE_INDICATOR = "[+]";

    private Style<AccordionWidget> style = Style.empty();
    private int lastRenderHeight;
    private Cursor cursor;
    private boolean inited;

    private final java.util.Map<Obj, Obj> _savedJvm;

    public AccordionWidget(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(new java.util.HashMap<>(jvm), tid, vid);
        this._savedJvm = jvm;
    }

    private void init() {
        if (this.inited) return;
        // Guard against fields that processInst may have cleared via reflection
        if (this._body == null) this._body = new ArrayList<>();
        if (this.style == null) this.style = Style.empty();
        // If _savedJvm isn't set yet, we're still inside super() — skip field sync
        // but don't set inited so it re-runs later after construction completes.
        if (this._savedJvm == null) return;
        this.inited = true;
        final Map<Obj, Obj> j = this._savedJvm;
        final Obj t = j.get(uri(KEY_TITLE));
        if (t != null && t.isStr()) this._title = t.strValue();
        final Obj b = j.get(uri(KEY_BODY));
        if (b != null && !b.isNoObj()) {
            this._body.clear();
            if (b.isStr()) this._body.add(b.strValue());
            else b.stream().filter(Obj::isStr).forEach(o -> this._body.add(o.strValue()));
        }
        final Obj e = j.get(uri(KEY_EXP));
        if (e != null && e.isBool()) this._expanded = e.boolValue();

        // Apply style Rec if present
        final Obj s = j.get(uri("style"));
        if (s != null && s.isRec()) {
            final Style<AccordionWidget> st = Style.from(s.as());
            st.stylable = this;
            this.style(st);
        }

        this.at(uri("toggle"),   instLambda((l, i) -> { this.toggle();   Graphitty.out(Console.getTerminal().output(), this.format() + "\n"); return this; }), MUTABLE);
        this.at(uri("expand"),   instLambda((l, i) -> { this.expand();   Graphitty.out(Console.getTerminal().output(), this.format() + "\n"); return this; }), MUTABLE);
        this.at(uri("collapse"), instLambda((l, i) -> { this.collapse(); Graphitty.out(Console.getTerminal().output(), this.format() + "\n"); return this; }), MUTABLE);
        this.at(uri("append"),   instLambda((l, i) -> { this.appendLine(l.isStr() ? l.strValue() : ""); Graphitty.out(Console.getTerminal().output(), this.format() + "\n"); return this; }), MUTABLE);
    }

    public AccordionWidget() { this(Map.of(), UI_ACCORDIAN_TID, null); }
    public AccordionWidget(final String title) { this(); this._title = title; }
    public AccordionWidget(final String title, final String body) { this(); this._title = title; if (body != null) this._body.addAll(Arrays.asList(body.split("\n"))); }

    public boolean isExpanded() { this.init(); return this._expanded; }
    public AccordionWidget expand()   { this._expanded = true;  return this; }
    public AccordionWidget collapse() { this._expanded = false; return this; }
    public AccordionWidget toggle()   { this._expanded = !this._expanded; return this; }

    public AccordionWidget title(final String t) { this._title = t; return this; }
    public String title() { this.init(); return this._title != null ? this._title : ""; }

    public AccordionWidget body(final String text) {
        this._body.clear();
        if (text != null && !text.isEmpty()) { this._body.addAll(Arrays.asList(text.split("\n"))); }
        // body stored in _body
        return this;
    }

    public AccordionWidget appendBody(final String text) {
        if (text != null && !text.isEmpty()) this._body.addAll(Arrays.asList(text.split("\n", -1)));
        return this;
    }

    public AccordionWidget appendLine(final String line) {
        if (line != null && !line.isEmpty()) this._body.add(line);
        return this;
    }

    public AccordionWidget clearBody() { this._body.clear(); return this; }
    public List<String> bodyLines() { this.init(); return List.copyOf(this._body); }

    @Override public AccordionWidget cursor(final Cursor c) { this.cursor = c; return this; }
    @Override public Style<AccordionWidget> getStyle()      { this.init(); return this.style; }

    @Override
    public AccordionWidget style(final Style<AccordionWidget> s) {
        this.style = s;
        if (this.style.border() == Border.none) this.style.border(Border.simple);
        if (this.style.foreground().isEmpty())  this.style.foreground("{{g}}");
        return this;
    }

    @Override public void run()    {}
    @Override public void display() {}

    @Override
    public void close() {
        if (this.lastRenderHeight > 0) {
            Graphitty.out(Console.getTerminal().output(), "\033[" + this.lastRenderHeight + "A\033[J");
            this.lastRenderHeight = 0;
        }
    }

    @Override
    public int height() {
        this.init();
        return (isExpanded() && !this._body.isEmpty()) ? this._body.size() + 2 : 2;
    }

    private String indicator() { return isExpanded() ? EXPAND_INDICATOR : COLLAPSE_INDICATOR; }

    @Override
    public String format() {
        this.init();
        final int bodyWidth = this._body.stream().map(Highlighter::visualLength).max(Integer::compareTo).orElse(0);
        final String ind = indicator();
        final int titleW = Highlighter.visualLength(title()) + Highlighter.visualLength(ind) + 3;
        final int width = Math.max(titleW, bodyWidth + 2);
        final Border border = this.style.border() == Border.none ? Border.simple : this.style.border();
        final StringBuilder sb = new StringBuilder();

        if (isExpanded() && !this._body.isEmpty()) {
            buildTitleBar(sb, border, ind, width);
            sb.append("\n");
            for (final String line : this._body) {
                sb.append(Widget.X).append(border.leftSide()).append(Widget.X)
                        .append(this.style.foreground()).append(" ").append(line)
                        .append(" ".repeat(Math.max(0, width - Highlighter.visualLength(line) - 1)))
                        .append(Widget.X).append(border.rightSide()).append(Widget.X).append("\n");
            }
            sb.append(Widget.X).append(border.bottomLeftCorner())
                    .append(border.bottomSide().repeat(width))
                    .append(border.bottomRightCorner()).append(Widget.X);
        } else {
            buildTitleBar(sb, border, ind, width);
            sb.append("\n");
            sb.append(Widget.X).append(border.bottomLeftCorner())
                    .append(border.bottomSide().repeat(width))
                    .append(border.bottomRightCorner()).append(Widget.X);
        }
        return sb.toString();
    }

    private void buildTitleBar(final StringBuilder sb, final Border border, final String ind, final int width) {
        final String t = " " + title() + " " + ind + " ";
        sb.append(Widget.X).append(border.topLeftCorner()).append(t);
        final int r = width - Highlighter.visualLength(t);
        if (r > 0) sb.append(border.topSide().repeat(r));
        sb.append(border.topRightCorner()).append(Widget.X);
    }

    @Override public String toString() { return this.format(); }

    @Override
    public String renderInPlace() {
        this.init();
        final String f = this.format();
        final int n = f.split("\n").length;
        final StringBuilder sb = new StringBuilder();
        if (this.lastRenderHeight > 0) { sb.append("\033[").append(this.lastRenderHeight).append("A\033[J"); }
        sb.append(f).append("\n");
        this.lastRenderHeight = n + 1;
        return sb.toString();
    }

    @Override
    public String renderFresh() { this.lastRenderHeight = 0; return this.format() + "\n"; }
}
