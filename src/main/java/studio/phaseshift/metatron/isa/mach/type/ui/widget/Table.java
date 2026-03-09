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

package studio.phaseshift.metatron.isa.mach.type.ui.widget;

import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Table extends AbstractWidget<Table> {

    protected final List<String> headers;
    protected final List<List<Object>> table;
    protected final List<Integer> maxColWidth;


    public Table(final List<String> headers) {
        this.headers = headers;
        this.table = new ArrayList<>();
        this.maxColWidth = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            this.maxColWidth.add(Integer.MAX_VALUE);
        }
    }

    public Table(final List<String> headers, final List<Integer> maxColWidth) {
        this.headers = headers;
        this.table = new ArrayList<>();
        this.maxColWidth = maxColWidth;
    }

    public Table addRow(final List<Object> entries) {
        this.table.add(entries);
        return this;
    }

    public Table clear() {
        this.table.clear();
        return this;
    }

    public List<Object> row(final int index) {
        return this.rows().get(index);
    }

    public List<List<Object>> rows() {
        return null == this.table ? List.of() : this.table;
    }

    public List<Object> column(int col) {
        final List<Object> column = new ArrayList<>();
        for (int i = 0; i < this.table.size(); i++) {
            column.add(this.table.get(i).get(col));
        }
        return column;
    }


    @Override
    public List<String> rowStrings() {
        return Arrays.asList(this.format().split("\n"));
    }

    public List<Integer> formattedWidths(final List<String> rowesque) {
        final List<Integer> widths = new ArrayList<>();
        for (int i = 0; i < rowesque.size(); i++) {
            final int ii = i;
            widths.add(Math.min(this.maxColWidth.get(i), Math.max(rowesque.get(i).length(), this.table.stream().map(row -> Highlighter.unformat(row.size() > ii ? row.get(ii).toString() : "")).flatMap(s -> Arrays.stream(s.split("\n"))).map(String::length).max(Integer::compareTo).orElse(0))));
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
        for (int i = 0; i < this.table.get(index).size(); i++) {
            final String high = Highlighter.format(this.entry(index, i));
            final String low = Highlighter.unformat(this.entry(index, i).toString());
            sb.append(high)
                    .append(this.addSpace(widths, i, low))
                    .append(this.style.divider);
        }
        return sb.toString();
    }

    private String clip(final String str, final int amount) {
        if (amount > 0)
            return str.substring(amount);
        else
            return str.substring(0, str.length() + amount) + "...";
    }

    public List<String> formattedRows() {
        final List<String> frows = new ArrayList<>();
        for (int i = 0; i < this.table.size(); i++) {
            frows.add(this.clip(this.formattedRow(i), this.maxColWidth.get(i)));
        }
        return frows;
    }

    public Object entry(final int row, final int col) {
        return this.row(row).get(col);
    }

    private String addSpace(final List<Integer> widths, final int index, final Object entry) {
        return " ".repeat(1 + Math.abs(widths.get(index) - Highlighter.visualLength(entry.toString().trim())));
    }

    @Override
    public String toString() {
        return this.format();
    }

    @Override
    public String format() {
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
            sb.append("\n");
        }
        sb.append(formattedRows().stream().map(row -> row + "\n").reduce("", (a, b) -> a + b));
        sb.deleteCharAt(sb.length() - 1);
        return this.style.border.wrap(sb).toString();
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
