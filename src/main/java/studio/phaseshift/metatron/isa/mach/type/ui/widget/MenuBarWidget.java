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

import org.jline.widget.Widgets;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.console.KeyboardCombos;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_LABEL_LINE_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MenuBarWidget extends AbstractWidget<MenuBarWidget> {

    private static final Obj K_HEIGHT = uri("height");
    private static final Obj K_LINES = uri("lines");

    public MenuBarWidget() {
        super();
    }

    public MenuBarWidget(final AbstractLineWidget<?>... lineWidgets) {
        this();
        this.lines(lineWidgets);
    }

    public MenuBarWidget(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.readStyle();
    }

    private void readStyle() {
        final Obj s = this.at(uri("style"));
        if (s != null && s.isRec()) {
            final Style<MenuBarWidget> st = Style.from(s.as());
            st.stylable = this;
            this.style(st);
        }
    }

    public MenuBarWidget lines(final AbstractLineWidget<?>... lineWidgets) {
        final Obj[] objs = new Obj[lineWidgets.length];
        for (int i = 0; i < lineWidgets.length; i++) objs[i] = lineWidgets[i];
        jvmWrite(K_LINES, lst(objs));
        return this;
    }

    public MenuBarWidget height(final int height) {
        jvmWrite(K_HEIGHT, jnt(Math.max(1, height)));
        return this;
    }

    @Override
    public String format() {
        final Obj h = this.at(K_HEIGHT);
        final int barHeight = null != h && h.isInt() ? Math.max(1, h.asInt().intValue().intValue()) : 1;
        final Border border = this.style.border();
        final String fg = this.style.foreground();
        final String bg = this.style.background();
        final boolean bordered = null != border && border != Border.none;
        // Query the terminal at render time (not the construction-time snapshot),
        // so a menu bar built before the console is ready still spans full width.
        final var terminal = Console.getTerminal();
        final int termWidth = null != terminal ? Math.max(1, terminal.getWidth()) : 0;

        // Join the line widgets into a single horizontal row.
        // at() (not jvm.get()) resolves auto_ expressions like !*def so the
        // referenced line widgets are materialized before we render them.
        // elements() (not stream()) iterates the list's entries — stream()
        // on a Lst yields the list itself as a single element.
        final StringBuilder content = new StringBuilder();
        this.at(K_LINES).orElse(lst()).elements().forEach(lineObj -> {
            final AbstractLineWidget<?> line = realize(lineObj);
            if (null == line) return;
            if (!content.isEmpty()) content.append("  ");
            content.append(line.format());
            // A line widget's format() ends with {{X}} (reset); re-assert the
            // bar's background so separators and padding stay on the bar bg.
            content.append(bg);
        });
        final int contentWidth = Highlighter.visualLength(content.toString());
        final int innerWidth = Math.max(contentWidth, bordered ? termWidth - 2 : termWidth);

        final List<String> rows = new ArrayList<>();
        if (bordered) {
            rows.add(fg + bg + border.topLeftCorner()
                    + border.topSide().repeat(innerWidth)
                    + border.topRightCorner() + Widget.X);
        }
        for (int row = 0; row < barHeight; row++) {
            final StringBuilder r = new StringBuilder();
            r.append(fg).append(bg);
            if (bordered) r.append(border.leftSide());
            if (row == 0) {
                r.append(content);
                r.append(" ".repeat(innerWidth - contentWidth));
            } else {
                r.append(" ".repeat(innerWidth));
            }
            if (bordered) r.append(border.rightSide());
            r.append(Widget.X);
            rows.add(r.toString());
        }
        if (bordered) {
            rows.add(fg + bg + border.bottomLeftCorner()
                    + border.bottomSide().repeat(innerWidth)
                    + border.bottomRightCorner() + Widget.X);
        }
        return String.join("\n", rows);
    }

    /**
     * Turn a {@code lines} element into a concrete line widget.  Elements
     * produced by mtron ({@code label_line::[...]}) are already widgets;
     * bare recs are realized through their registered type constructor.
     */
    private static AbstractLineWidget<?> realize(final Obj lineObj) {
        if (lineObj instanceof AbstractLineWidget<?> line) return line;
        if (!lineObj.isRec()) return null;
        // Reconstruct the line widget directly from the rec's jvm.  A line
        // widget value's tid() is its *refinement* (the base widget type),
        // not the type's storage key, so tid-dispatch would resolve to the
        // base widget type, which has no constructor.  LabelLineWidget is
        // the only concrete line widget, so construct it directly.
        return new LabelLineWidget(lineObj.asRec().jvm(), UI_LABEL_LINE_TID, lineObj.vid());
    }

    /**
     * Pin this menu bar to the top of the terminal and render it.  Once
     * pinned it floats above console output — the console re-draws floating
     * widgets every prompt cycle, so the bar is never covered by text or by
     * other widgets rendered in-place.
     */
    @Override
    public void run() {
        if (null == Console.LOCAL_INSTANCE) {
            final var terminal = Console.getTerminal();
            if (null != terminal)
                Graphitty.out(terminal.output(), this.format() + "\n");
            return;
        }
        final var surface = Console.LOCAL_INSTANCE.getFloatingSurface();
        final var terminal = Console.getTerminal();
        final int width = null != terminal ? Math.max(1, terminal.getWidth()) : 1;
        // top=-1 pulls the bar to terminal row 1; FloatingSurface TOP anchors
        // resolve to 2 + offsetRow, leaving a buffer row above by default.
        this.floatAt(surface, FloatingSurface.Anchor.TOP_LEFT, width, -1, 0);
        surface.render();
        this.bindKeys();
    }

    /**
     * Bind each line widget's {@code key} combo to a console key listener
     * that applies its {@code on_key} instruction.
     */
    private void bindKeys() {
        if (null == Console.LOCAL_INSTANCE) return;
        final Widgets widgets = Console.LOCAL_INSTANCE.getWidgets();
        this.at(K_LINES).orElse(lst()).elements().forEach(lineObj -> {
            final AbstractLineWidget<?> line = realize(lineObj);
            if (null == line) return;
            final String combo = line.key();
            final Obj onKey = line.onKey();
            if (combo.isEmpty() || null == onKey || onKey.isNoObj()) return;
            final String seq = KeyboardCombos.parse(combo);
            if (null == seq) return;
            widgets.getKeyMap().bind((org.jline.reader.Widget) () -> {
                onKey.apply(noobj());
                return true;
            }, seq);
        });
    }

    /**
     * A menu bar is a persistent floating widget: {@link Widget#close()}
     * would unfloat it, which the {@code .display()} instruction (a
     * {@code run()} + {@code close()} pair) would then undo.  So this is a
     * no-op — the bar stays pinned until explicitly
     * {@link #unfloat(FloatingSurface)}ed.
     */
    @Override
    public void close() {
        // no-op — keep the bar pinned
    }
}
