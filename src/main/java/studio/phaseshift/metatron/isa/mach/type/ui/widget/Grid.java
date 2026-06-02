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
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Grid extends AbstractWidget<Grid> {

    protected final List<Widget<?>> widgets;
    protected final int columns;
    protected final int rows;
    protected int widgetFocus = 0;

    public Grid(final List<Widget<?>> widgets, final int columns) {
        this.widgets = widgets;
        this.columns = columns;
        this.rows = widgets.size() / columns;
        final int totalWidth = this.widgets.stream().map(Widget::width).reduce(0, Integer::max);
        for (int i = 0; i < this.widgets.size(); i++) {
            this.widgets.get(i).cursor(new Cursor(totalWidth * i, 0));
        }
    }

    public void currentFocus(final int widgetIndex) {
        this.widgetFocus = widgetIndex;
    }

    @Override
    public String format() {
        final int totalHeight = this.widgets.stream().map(Widget::height).reduce(0, Integer::max);
        final List<String> gridRows = new ArrayList<>();
        try {
            for (int w = 0; w < this.widgets.size(); w = w + this.columns) {
                for (int r = 0; r < totalHeight; r++) {
                    String row = new String();
                    for (int i = w; i < (w + this.columns); i++) {
                        final Widget<?> widget = this.widgets.get(i);
                        row = row + " " + (r < widget.height() ? widget.rowString(r) : " ".repeat(widget.width()));
                    }
                    gridRows.add(row);
                }
            }
        } catch (final Exception e) {
            //do nothing
        }
        final StringBuilder sb = new StringBuilder();
        gridRows.forEach(r -> sb.append(r).append("\n"));
        gridRows.addLast(gridRows.removeLast().trim());
        return this.style.border.wrap(sb).toString();
    }

    @Override
    public void run() {
        super.run();
        this.widgets.forEach(Widget::run);
        this.display();
    }

    @Override
    public void close() {
        this.widgets.forEach(Widget::close);
        super.close();
    }
}
