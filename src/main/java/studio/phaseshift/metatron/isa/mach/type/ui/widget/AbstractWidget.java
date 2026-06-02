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
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.Arrays;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractWidget<W extends AbstractWidget<W>> implements Widget<W> {

    protected Terminal terminal = Console.getTerminal();
    protected Style<W> style = Style.empty();
    protected Size size;
    protected Display display;
    protected Cursor cursor;
    protected Attributes attributes;

    // Pane bounds - set when this widget should be confined to a specific pane region.
    // All values are 1-based terminal coordinates. paneStartRow == -1 means no constraint.
    protected int paneStartRow = -1;
    protected int paneStartCol = 1;
    protected int paneAvailHeight = -1;
    protected int paneAvailWidth = -1;

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

    /** Returns {@code true} when pane bounds have been set via {@link #setPaneBounds}. */
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

    /**
     * Erase the widget's rendered area.  Call from {@link #close()} instead
     * of manually emitting cursor-movement escape sequences.
     *
     * <p>In <b>absolute mode</b> (pane bounds set) this is a no-op: the
     * pane layout will be restored by the console's {@code renderPanes()}
     * call that follows every widget invocation.
     *
     * <p>In <b>relative mode</b> the cursor is moved up to the start of the
     * widget area, all rendered lines are cleared, and the cursor is
     * repositioned ready for the caller to redraw the prompt.
     *
     * @param totalHeightUsed the value returned by {@link WidgetCanvas#finish()}
     *                        in the last completed redraw cycle.
     */
    protected void eraseWidget(final int totalHeightUsed) {
        if (hasPaneBounds() || totalHeightUsed <= 0) return;
        // Relative mode: move up, clear each line, then position cursor at top
        // so the caller can redraw the prompt at the same location.
        final StringBuilder sb = new StringBuilder();
        sb.append(Graphitty.string("{{^%d}}{{|1}}", totalHeightUsed));
        for (int i = 0; i <= totalHeightUsed; i++) {
            sb.append(Graphitty.string("{{-X-}}\n"));
        }
        sb.append(Graphitty.string("{{^%d}}{{|1}}", totalHeightUsed + 1));
        Graphitty.out(terminal.output(), sb.toString());
        terminal.writer().flush();
    }

    public AbstractWidget() {
        this.size = this.terminal.getSize();
        this.display = new Display(this.terminal, false);
        this.display.resize(this.size.getRows(), this.size.getColumns());
        this.cursor = new Cursor(0, 0);
    }
    
    public Style<W> getStyle() {
        return this.style;
    }

    public W cursor(final Cursor cursor) {
        this.cursor = cursor;
        return (W) this;
    }

    public W style(final Style<W> style) {
        this.style = style;
        return (W) this;
    }

    @Override
    public void display() {
        this.display.resize(this.height(), this.width());
        this.display.updateAnsi(Arrays.stream(this.format().split("\n")).map(s -> s + "\n").map(Graphitty::string).toList(), -1);
        this.terminal.writer().flush();
    }

    @Override
    public void run() {
        this.attributes = this.terminal.enterRawMode();
        this.terminal.puts(InfoCmp.Capability.keypad_xmit);
        this.terminal.writer().flush();
        //this.display.updateAnsi(Arrays.stream(this.format().split("\n")).map(Graphitty::string).toList(), -1);
        if (this.style.attachment != null)
            this.style.attachment.run();
    }

    public void close() {
        if (null != this.style.attachment)
            this.style.attachment.close();
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
        //  this.attributes = null;
    }
}
