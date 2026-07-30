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
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.INST_CTOR_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class PanelWidget extends JRec<PanelWidget> implements Widget<PanelWidget> {

    public static final fURI UI_PANEL_TID = f("/m/mach/ui/widget/panel");

    public static final Type UI_PANEL_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(UI_PANEL_TID)
            .isaPredicate(rec())
            .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(UI_PANEL_TID),
                    lst(T(REC_TID)), (lhs, inst) ->
                    new PanelWidget(inst.arg(0).as().jvm(), UI_PANEL_TID, inst.arg(0).vid())))
            .create();

    // @JRecElement fields are metadata for mtron introspection.
    // Java code reads from sync() which pulls from jvmRead().
    @JRecElement(key = "title", rng = "/m/str")
    private String title;
    @JRecElement(key = "body", rng = "/m/str")
    private String body;
    private Style<PanelWidget> style = Style.empty();
    private Cursor cursor;

    // ── JRec constructor ───────────────────────────────────────────

    public PanelWidget(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        readStyle(this.jvm());
    }

    private void readStyle(final Map<Obj, Obj> jvm) {
        final Obj s = jvm.get(uri("style"));
        if (s != null && s.isRec()) {
            final Style<PanelWidget> st = Style.from(s.as());
            st.stylable = this;
            this.style(st);
        }
    }

    private void sync() {
        if (this.style == null) return; // construction guard
        final Map<Obj, Obj> jvm = jvmRead();
        // Only overwrite from JVM when the key is present, so that
        // Java-populated fields (set via convenience constructors or
        // direct assignment) are not silently cleared.
        if (jvm.containsKey(uri("title")))
            this.title = jvmStr(jvm, "title");
        if (jvm.containsKey(uri("body")))
            this.body = jvmStr(jvm, "body");
    }

    // ── convenience constructors ───────────────────────────────────

    public PanelWidget() {
        this(Map.of(), UI_PANEL_TID, null);
    }

    public PanelWidget(final String body) {
        this(null, body);
    }

    public PanelWidget(final String title, final String body) {
        this(Map.of(), UI_PANEL_TID, null);
        this.title = title;
        this.body = body;
    }

    // ── composition ────────────────────────────────────────────────

    public PanelWidget bottom(final Widget<?> dims) {
        return new PanelWidget(null, this + "\n" + dims.toString());
    }

    public PanelWidget right(final Widget<?> dims) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.max(this.height(), dims.height()); i++) {
            if (i < this.height()) sb.append(this.rowString(i));
            else sb.append(" ".repeat(this.width()));
            if (i < dims.height()) sb.append(" ").append(dims.rowString(i));
            sb.append("\n");
        }
        sb.deleteCharAt(sb.length() - 1);
        return new PanelWidget(sb.toString()).style().border(this.style.border()).applyStyle();
    }

    public PanelWidget setTitle(final String title) {
        this.title = title;
        return this;
    }

    // ── Widget contract ────────────────────────────────────────────

    @Override public PanelWidget cursor(final Cursor cursor) { this.cursor = cursor; return this; }
    @Override public Style<PanelWidget> getStyle()           { return this.style; }

    @Override
    public PanelWidget style(final Style<PanelWidget> style) {
        this.style = style;
        if (this.style.border() == Border.none) this.style.border(Border.continuous);
        return this;
    }

    @Override public void close()  {}
    @Override public String renderInPlace() { return this.format() + "\n"; }
    @Override public String renderFresh()   { return this.format() + "\n"; }

    // ── rendering ──────────────────────────────────────────────────

    @Override
    public String format() {
        this.sync();
        final List<String> lines = Arrays.asList(
                (null != this.body ? this.body : "").replace("\\n", "\n").split("\\r?\\n", -1));
        final int maxLen = Stream.concat(Stream.of(this.title).filter(Objects::nonNull), lines.stream())
                .map(Highlighter::visualLength)
                .max(Integer::compareTo).orElse(0);

        final StringBuilder sb = new StringBuilder();
        sb.append(this.style.prefix());
        final String top = "%s%s".formatted(
                null == this.title ? "" : this.title,
                this.style.border().topSide().repeat(
                        null == this.title ? maxLen : maxLen - Highlighter.visualLength(this.title)))
                .stripTrailing();
        if (!top.isEmpty())
            sb.append(this.style.border().topLeftCorner()).append(top)
                    .append(this.style.border().topRightCorner()).append('\n');
        for (final String line : lines) {
            sb.append(this.style.border().leftSide()).append(line)
                    .append(" ".repeat(maxLen - Highlighter.visualLength(line)))
                    .append(this.style.border().rightSide()).append("{{X}}\n");
        }
        final String bottom = this.style.border().bottomSide().repeat(maxLen).stripTrailing();
        if (!bottom.isEmpty())
            sb.append(this.style.border().bottomLeftCorner()).append(bottom)
                    .append(this.style.border().bottomRightCorner()).append("\n");
        return sb.toString();
    }

    @Override public String toString() { return this.format(); }
}
