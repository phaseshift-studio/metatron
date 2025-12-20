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

package studio.phaseshift.metatron.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Table {

    protected final List<String> headers;
    protected final List<List<Object>> table;

    public Table(final List<String> headers) {
        this.headers = headers;
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

    public List<Integer> formattedWidths() {
        final List<Integer> widths = new ArrayList<>();
        for (int i = 0; i < this.headers.size(); i++) {
            final int ii = i;
            widths.add(Math.max(this.headers.get(i).length(), this.table.stream().map(row -> Graphitty.strip(row.get(ii).toString())).flatMap(s -> Arrays.stream(s.split("\n"))).map(String::length).max(Integer::compareTo).orElse(0)));
        }
        return widths;
    }

    public String formattedRow(final int index) {
        final List<Integer> widths = this.formattedWidths();
        final StringBuilder sb = new StringBuilder();
        sb.append("{{g}}|{{X}}");
        for (int i = 0; i < this.headers.size(); i++) {
            sb.append(this.entry(index, i).toString()).append(this.addSpace(widths, i, this.entry(index, i))).append("{{g}}|{{X}}");
        }
        return sb.toString();
    }

    public int formattedWidth() {
        return this.formattedRows().stream().map(Graphitty::strip).map(String::length).max(Integer::compareTo).orElse(0);
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
        return " ".repeat(1 + Math.abs(widths.get(index) - Graphitty.strip(entry.toString().trim()).length()));
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        final List<Integer> widths = this.formattedWidths();
        sb.append("{{g}}|{{X}}");
        for (int i = 0; i < this.headers.size(); i++) {
            sb.append("{{c}}").append(this.headers.get(i)).append(this.addSpace(widths, i, this.headers.get(i))).append("{{g}}|");
        }
        sb.append("{{X}}\n");
        sb.append(formattedRows().stream().map(row -> row + "\n").reduce("", (a, b) -> a + b));
        return sb.deleteCharAt(sb.length() - 1).toString();
    }
}
