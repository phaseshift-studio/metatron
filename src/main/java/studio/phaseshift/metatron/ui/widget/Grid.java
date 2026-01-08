/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.ui.widget;

import studio.phaseshift.metatron.ui.Widget;

import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Grid extends AbstractWidget<Grid> {


    protected final List<Widget<?>> widgets;
    protected final List<Integer> widths;
    protected final List<Integer> heights;
    protected final int columns;
    protected final int rows;

    public Grid(final List<Widget<?>> widgets, final int columns) {
        this.widgets = widgets;
        this.columns = columns;
        this.rows = widgets.size() / columns;
        this.widths = widgets.stream().map(Widget::width).toList();
        this.heights = widgets.stream().map(Widget::height).toList();
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder().append("\n");

        final int totalHeight = this.rows * this.widgets.stream().map(Widget::height).reduce(0, Integer::max);
        sb.append("\n".repeat(totalHeight)).append("{{^%s&|0}}".formatted(totalHeight+1));
        final int totalWidth = this.columns * this.widgets.stream().map(Widget::width).reduce(0, Integer::max);
        
        for (int i = 0; i < this.widgets.size(); i = i + this.columns) {
            for (int j = 0; j < this.columns; j++) {
                final Widget<?> current = this.widgets.get(i + j);
                for (int r = 0; r < current.rowCount(); r++) {
                    sb.append(current.rowString(r));
                    sb.append("\n{{>%s}}".formatted((current.width()+1) * j));
                }
                if (j < columns - 1)
                    sb.append("{{^%s&>%s}}".formatted(current.height(), current.width()+1));
            }
            sb.append("{{<%s}}".formatted(totalWidth*this.columns));
        }
        return sb.toString();
    }
}
