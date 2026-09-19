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

import org.jline.terminal.Attributes;
import org.jline.terminal.Cursor;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.utils.Display;
import org.jline.utils.InfoCmp;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.reflect.SpaceRec;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;

import java.util.Map;

/**
 * The base for interactive widgets and tools: a {@link SpaceRec}, so state is rec
 * keys and the two rules of {@code SpaceRec} (one read path, one write path) apply
 * here too.  What is left in Java are collaborators and per-render scratch — the
 * terminal, its {@link Display}, the saved terminal {@link Attributes}, and the pane
 * bounds the console sets before a redraw — none of which is widget data.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractWidget<W extends AbstractWidget<W>> extends SpaceRec<W> implements Widget<W> {

    public AbstractWidget() {
        // the map must be mutable: the write path installs into this rec
        this(new java.util.LinkedHashMap<>(), Tokens.REC_TID, null);
    }

    public AbstractWidget(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        // The jline Display is terminal-bound (its ctor calls getStringCapability).
        // Defer creating it until a terminal is actually available so headless
        // construction (e.g. type::rec in an MCP/agent eval) still yields a Widget.
        if (null != this.terminal) {
            final Size size = this.terminal.getSize();
            this.display = new Display(this.terminal, false);
            this.display.resize(size.getRows(), size.getColumns());
        }
    }

    protected Terminal terminal = Console.getTerminal();
    protected Display display;
    protected Attributes attributes;

    // Pane bounds - set when this widget should be confined to a specific pane region.
    // All values are 1-based terminal coordinates. paneStartRow == -1 means no constraint.
    protected int paneStartRow = -1;
    protected int paneStartCol = 1;
    protected int paneAvailHeight = -1;
    protected int paneAvailWidth = -1;

    /**
     * Returns an ANSI string that renders this widget in-place, overwriting the
     * previous render.  On the first call (or after {@link #renderFresh()}) this
     * behaves like a normal render.  On subsequent calls the cursor moves up to
     * erase the old lines before printing the new content.
     *
     * @return ANSI-escaped string suitable for writing directly to the terminal
     */
    public String renderInPlace() {
        return this.format() + "\n";
    }

    /**
     * @return the widget's formatted output with a trailing newline
     */
    public String renderFresh() {
        return this.format() + "\n";
    }

    /**
     * Constrain this widget's rendering to the given pane region so it never
     * draws outside the pane's borders.
     *
     * @param startRow 1-based terminal row where the pane starts (including its top border)
     * @param startCol 1-based terminal column where the pane starts (including its left border)
     * @param height   total height of the pane in rows (including borders)
     * @param width    total width of the pane in columns (including borders)
     */
    public void setPaneBounds(final int startRow, final int startCol,
                              final int height, final int width) {
        this.paneStartRow = startRow;
        this.paneStartCol = startCol;
        this.paneAvailHeight = height;
        this.paneAvailWidth = width;
    }

    /**
     * Returns {@code true} when pane bounds have been set via {@link #setPaneBounds}.
     */
    public boolean hasPaneBounds() {
        return this.paneStartRow > 0;
    }

    /**
     * Create a {@link WidgetCanvas} for the current redraw cycle.
     *
     * <p>Call this at the start of every {@code redraw()} method, pass the
     * canvas lines to draw to via {@link WidgetCanvas#line}, and finish the
     * cycle with {@link WidgetCanvas#finish()}.  The canvas transparently
     * handles absolute (pane-bounded) vs relative rendering – the widget
     * author does not need to know which mode is active.
     *
     * @param previousTotalHeight the {@code totalHeightUsed} value from the
     *                            preceding redraw cycle (used to clear stale
     *                            lines in relative mode); pass {@code 0} for
     *                            the first call.
     */
    protected WidgetCanvas beginRedraw(final int previousTotalHeight) {
        return new WidgetCanvas(this, previousTotalHeight);
    }

    @Override
    public Style<W> getStyle() {
        return Style.from(this.get(this.read(), STYLE_KEY));
    }

    @Override
    public W cursor(final Cursor cursor) {
        // a position hint for a parent laying this widget out; nothing reads it
        // back, so there is nothing to keep
        return (W) this;
    }

    /**
     * Bind a style to this widget and put it in the widget's rec.
     *
     * <p>The rec — not a Java field — is the home of the style, so a
     * store-backed widget's look survives the re-hydration every
     * {@code .display()} update performs, and anything that reads the widget's
     * rec sees the same style the render does.
     */
    @Override
    public W style(final Style<W> style) {
        final Style<W> s = null == style ? Style.empty() : style;
        s.stylable = (W) this;
        this.put(STYLE_KEY, s);
        return (W) this;
    }

    @Override
    public void run() {
        this.attributes = this.terminal.enterRawMode();
        this.terminal.puts(InfoCmp.Capability.keypad_xmit);
        this.terminal.writer().flush();
        //this.display.updateAnsi(Arrays.stream(this.format().split("\n")).map(Graphitty::string).toList(), -1);
        final Widget<?> attachment = this.getStyle().attachment();
        if (attachment != null)
            attachment.run();
    }

    @Override
    public void close() {
        final Widget<?> attachment = this.getStyle().attachment();
        if (null != attachment)
            attachment.close();
        //this.terminal.puts(InfoCmp.Capability.clear_screen);
        //this.display.update(List.of(), this.size.cursorPos(this.cursor.getX(), this.cursor.getY()));
        //this.display.reset();
        //this.display.resize(0,0);
        if (null != this.attributes) {
            this.terminal.setAttributes(this.attributes);
        }
        this.terminal.puts(InfoCmp.Capability.exit_ca_mode);
        this.terminal.puts(InfoCmp.Capability.keypad_local);
        this.terminal.writer().flush();
        Widget.super.close();
        //  this.attributes = null;
    }
}
