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

import studio.phaseshift.metatron.lang.sys.console.Highlighter;
import studio.phaseshift.metatron.ui.Stylable;
import studio.phaseshift.metatron.ui.Widget;
import studio.phaseshift.metatron.util.Tuple;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Table extends AbstractWidget<Table> {

    protected final List<String> headers;
    protected final List<Tuple.Pair<String, Runnable>> menu;
    protected final List<List<Object>> table;

    public Table(final List<String> headers, final List<Tuple.Pair<String, Runnable>> menu) {
        this.headers = headers;
        this.menu = menu;
        this.table = new ArrayList<>();
    }

    public Table(final List<String> headers) {
        this.headers = headers;
        this.menu = null;
        this.table = new ArrayList<>();
    }

    public Table addRow(final List<Object> entries) {
        this.table.add(entries);
        return this;
    }

    public List<Object> row(final int index) {
        return this.rows().get(index);
    }

    public List<List<Object>> rows() {
        return this.table;
    }

    public List<Object> column(int col) {
        final List<Object> column = new ArrayList<>();
        for (int i = 0; i < this.table.size(); i++) {
            column.add(this.table.get(i).get(col));
        }
        return column;
    }

    public List<Integer> formattedWidths(final List<String> rowesque) {
        final List<Integer> widths = new ArrayList<>();
        for (int i = 0; i < rowesque.size(); i++) {
            final int ii = i;
            widths.add(Math.max(rowesque.get(i).length(), this.table.stream().map(row -> Highlighter.unformat(row.size() > ii ? row.get(ii).toString() : "")).flatMap(s -> Arrays.stream(s.split("\n"))).map(String::length).max(Integer::compareTo).orElse(0)));
        }
        return widths;
    }

    public String formattedRow(final int index) {
        final List<Integer> widths = null == this.headers ? new ArrayList<>() : this.formattedWidths(this.headers);
        if (widths.size() < this.row(0).size()) {
            for (int i = 0; i < this.row(0).size() - widths.size(); i++) {
                widths.add(1);
            }
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(this.style.divider);
        for (int i = 0; i < this.row(index).size(); i++) {
            final String high = Highlighter.format(this.entry(index, i));
            final String low = Highlighter.unformat(this.entry(index, i).toString());
            sb.append(high)
                    .append(this.addSpace(widths, i, low))
                    .append(this.style.divider);
        }
        return sb.toString();
    }

    public int formattedWidth() {
        return this.formattedRows().stream().map(Highlighter::visualLength).max(Integer::compareTo).orElse(0);
    }


    public List<String> formattedRows() {
        final List<String> frows = new ArrayList<>();
        for (int i = 0; i < rows().size(); i++) {
            frows.add(this.formattedRow(i));
        }
        return frows;
    }

    public Object entry(final int row, final int col) {
        return this.row(row).get(col);
    }

    private String addSpace(final List<Integer> widths, final int index, final Object entry) {
        return " ".repeat(1 + Math.abs(widths.get(index) - Highlighter.visualLength(entry.toString().trim())));
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (null != this.headers) {
            if (this.style.headerDivider.isEmpty() && !this.style.divider.isEmpty())
                this.style.headerDivider = " ".repeat(Highlighter.visualLength(this.style.divider));
            final List<Integer> widths = this.formattedWidths(this.headers);
            sb.append(this.style.background)
                    .append(this.style.foreground)
                    .append(this.style.headerDivider);
            for (int i = 0; i < this.headers.size(); i++) {
                sb.append(this.headers.get(i))
                        //.append(this.style.background)
                        .append(this.style.foreground)
                        .append(this.addSpace(widths, i, this.headers.get(i)))
                        .append(this.style.headerDivider);
            }
            sb.append("{{X}}\n");
        }
        if (null != this.menu) {
            final List<Integer> widths = this.formattedWidths(this.menu.stream().map(Tuple.Pair::get0).toList());
            sb.append(this.style.divider)
                    .append(this.style.foreground);
            for (int i = 0; i < this.menu.size(); i++) {
                sb.append(this.style.foreground)
                        .append(this.menu.get(i).get0())
                        .append(this.addSpace(widths, i, this.menu.get(i).get0()))
                        .append(this.style.divider)
                        .append(this.style.foreground);
            }
            sb.append("{{X}}");
        }
        sb.append(formattedRows().stream().map(row -> row + "{{X}}\n").reduce("", (a, b) -> a + b));
        return sb.deleteCharAt(sb.length() - 1).toString();
    }

    @Override
    public Table style(final Style<Table> style) {
        this.style = style;
        if (this.style.foreground.isEmpty())
            this.style.foreground = "{{w}}";
        if (this.style.background.isEmpty())
            this.style.background = "{{[X]}}";
        if (this.style.divider.isEmpty())
            this.style.divider = "{{g}}|";
        return this;
    }
}
