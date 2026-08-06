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

package studio.phaseshift.metatron.isa.mach.type.ui;

import org.jline.terminal.Cursor;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.FloatingSurface;

import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Widget<W extends Widget<W>> extends Stylable<W>, AutoCloseable, Runnable {

    public static final String X = "{{X}}";

    @Override
    default void close() {
        if (null != Console.LOCAL_INSTANCE)
            this.unfloat(Console.LOCAL_INSTANCE.getFloatingSurface());
        Console.userMode.set(false);
    }

    W cursor(final Cursor cursor);

    /**
     * Present this widget on the terminal.  Display-only widgets render
     * once and return; interactive widgets (selectors, explain tools)
     * override this to enter a modal input loop.
     *
     * <p>If the widget's {@link Stylable.Style} carries a
     * {@link Stylable.Style#floatAt(FloatingSurface.Anchor, int, int, int) float anchor},
     * the widget is pinned to the console's {@link FloatingSurface} instead
     * of being rendered in-place.
     */
    @Override
    default void run() {
        Console.userMode.set(true);
        final var style = this.getStyle();
        if (Console.LOCAL_INSTANCE != null && style.hasFloat()) {
            final int floatW = style.width() > 0 ? style.width() : this.width();
            this.floatAt(Console.LOCAL_INSTANCE.getFloatingSurface(),
                    style.anchor(), floatW, style.top(), style.left());
            Console.LOCAL_INSTANCE.getFloatingSurface().render();
        } else {
            Graphitty.out(Console.getTerminal().output(), this.format() + "\n");
        }
    }

    /**
     * Render in-place: move cursor up over previous render, clear old lines,
     * print new content.  First call renders normally; subsequent calls
     * overwrite the previous output.
     */
    String renderInPlace();

    /**
     * Reset in-place tracking and return a fresh render string.
     */
    String renderFresh();

    default int height() {
        return this.rowStrings().size();
    }

    default int width() {
        return this.rowStrings().stream().map(Highlighter::visualLength).max(Integer::compareTo).orElse(0);
    }

    default int rowCount() {
        return this.height();
    }

    default int columnCount() {
        return this.width();
    }

    default String rowString(int i) {
        return this.rowStrings().get(i);
    }

    default String format() {
        return Highlighter.format(this.toString());
    }

    default List<String> rowStrings() {
        return List.of(this.format().split("\n"));
    }

    /**
     * Pin this widget to a {@link FloatingSurface} at the given terminal
     * coordinates.  The widget will be drawn at {@code (row, col)} on every
     * {@link FloatingSurface#render()} call, appearing to float over the
     * terminal content beneath it.
     *
     * @param surface the floating surface to pin to
     * @param row     1-based terminal row
     * @param col     1-based terminal column
     * @return this widget (fluent)
     */
    @SuppressWarnings("unchecked")
    default W floatAt(final FloatingSurface surface, final int row, final int col) {
        surface.add(this, row, col);
        return (W) this;
    }

    /**
     * Pin this widget to a terminal corner with a target display width.
     * The actual position is recalculated on every
     * {@link FloatingSurface#render()} call from the current terminal
     * dimensions and the widget's height, so the widget stays pinned to
     * its corner across terminal resizes.
     *
     * @param surface the floating surface to pin to
     * @param anchor  which corner of the terminal to pin to
     * @param width   the column width for positioning and content clipping
     * @return this widget (fluent)
     */
    @SuppressWarnings("unchecked")
    default W floatAt(final FloatingSurface surface, final FloatingSurface.Anchor anchor, final int width) {
        surface.add(this, anchor, width);
        return (W) this;
    }

    /**
     * Pin this widget to a terminal corner with offsets (CSS-style
     * {@code top} and {@code left} from the anchor edge).
     */
    @SuppressWarnings("unchecked")
    default W floatAt(final FloatingSurface surface, final FloatingSurface.Anchor anchor,
                      final int width, final int top, final int left) {
        surface.add(this, anchor, width, top, left);
        return (W) this;
    }

    /**
     * Remove this widget from a {@link FloatingSurface}.  The area the
     * widget previously occupied is cleared.
     *
     * @param surface the floating surface to unpin from
     * @return this widget (fluent)
     */
    @SuppressWarnings("unchecked")
    default W unfloat(final FloatingSurface surface) {
        surface.remove(this);
        return (W) this;
    }
}
