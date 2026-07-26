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
import studio.phaseshift.metatron.isa.mach.type.ui.Stylable;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.INST_CTOR_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_TABLE_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TableWidget extends JRec<TableWidget> implements Widget<TableWidget> {

    @JRecElement(key = "headers", rng = "/m/lst")
    public final List<String> headers = new ArrayList<>();

    @JRecElement(key = "rows", rng = "/m/lst")
    public final List<List<Object>> table = new ArrayList<>();

    @JRecElement(key = "metadata", rng = "/m/lst")
    public final List<List<Object>> metadata = new ArrayList<>();

    private Style<TableWidget> style = Style.empty();
    private Cursor cursor;
    private int lastRenderHeight;

    // ── JRec constructor ───────────────────────────────────────────

    public TableWidget(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    /**
     * Populate Java fields from the JVM store, but only when they are empty.
     * <p>
     * Tables built via the Java API ({@link #addRow}, {@link #addMetadata}, etc.)
     * already have their fields populated and must not be cleared — their data
     * is the source of truth.  Tables constructed from mtron via
     * {@link #TableWidget(Map, fURI, fURI)} have empty Java fields that need
     * initial population from the JVM.
     * <p>
     * Uses {@link Collectors#toCollection(ArrayList::new)} rather than
     * {@link Stream#toList()} so that rows are mutable {@link ArrayList}s
     * rather than {@code ImmutableCollections.ListN}, avoiding
     * {@code ClassCastException} when rows later flow through the JVM
     * serialization pipeline.
     */
    private void sync() {
        if (this.style == null) return; // construction guard
        final Map<Obj, Obj> jvm = jvmRead();

        // Only populate from JVM if fields haven't been set via Java API.
        if (this.headers.isEmpty()) {
            final Obj h = jvm.get(uri("headers"));
            if (h != null && !h.isNoObj())
                h.stream().filter(Obj::isStr).forEach(o -> this.headers.add(o.strValue()));
        }

        if (this.table.isEmpty()) {
            final Obj r = jvm.get(uri("rows"));
            if (r != null && !r.isNoObj())
                r.stream().forEach(row -> this.addRow(row.stream()
                        .map(cell -> (Object) (cell.isStr() ? cell.strValue() : cell))
                        .collect(Collectors.toCollection(ArrayList::new))));
        }

        if (this.metadata.isEmpty()) {
            final Obj m = jvm.get(uri("metadata"));
            if (m != null && !m.isNoObj())
                m.stream().forEach(row -> this.addMetadata(row.stream()
                        .map(cell -> (Object) (cell.isStr() ? cell.strValue() : cell))
                        .collect(Collectors.toCollection(ArrayList::new))));
        }
    }

    // ── convenience constructors ───────────────────────────────────

    public TableWidget() {
        this(Map.of(), UI_TABLE_TID, null);
    }

    public TableWidget(final List<String> headers) {
        this(Map.of(), UI_TABLE_TID, null);
        this.headers.addAll(headers);
    }


    public TableWidget(final List<String> headers, final List<List<Object>> rows) {
        this(Map.of(), UI_TABLE_TID, null);
        this.headers.addAll(headers);
        rows.forEach(this::addRow);
    }


    // ── builders ───────────────────────────────────────────────────

    public TableWidget addRow(final List<Object> entries) {
        this.table.add(entries);
        return this;
    }

    public TableWidget addMetadata(final List<Object> metadata) {
        this.metadata.add(metadata);
        return this;
    }

    public TableWidget clear() {
        this.table.clear();
        this.metadata.clear();
        return this;
    }

    // ── accessors ──────────────────────────────────────────────────

    public String header(final int column) {
        return this.headers.get(column);
    }

    public List<Object> row(final int index) {
        return this.rows().get(index);
    }

    public List<List<Object>> rows() {
        return this.table;
    }

    public List<Object> rowMetadata(final int index) {
        return this.metadata.get(index);
    }

    public List<List<Object>> metadata() {
        return this.metadata;
    }

    public List<Object> column(int col) {
        final List<Object> column = new ArrayList<>();
        for (int i = 0; i < this.table.size(); i++)
            column.add(this.table.get(i).get(col));
        return column;
    }

    public Object entry(final int row, final int col) {
        return this.row(row).get(col);
    }

    public Object entryMetadata(final int row, final int col) {
        return this.rowMetadata(row).get(col);
    }

    // ── formatting ─────────────────────────────────────────────────

    public List<Integer> formattedWidths(final List<String> rowesque) {
        final List<Integer> widths = new ArrayList<>();
        for (int i = 0; i < rowesque.size(); i++) {
            final int ii = i;
            widths.add(Math.max(rowesque.get(i).length(),
                    this.table.stream()
                            .map(row -> Highlighter.unformat(row.size() > ii ? row.get(ii).toString() : ""))
                            .flatMap(s -> Arrays.stream(s.split("\n")))
                            .map(String::length)
                            .max(Integer::compareTo).orElse(0)));
        }
        return widths;
    }

    public String formattedRow(final int index) {
        final List<Integer> widths = null == this.headers || this.headers.isEmpty()
                ? new ArrayList<>() : this.formattedWidths(this.headers);
        if (widths.size() < this.row(0).size()) {
            for (int i = 0; i < this.row(0).size() - widths.size(); i++) widths.add(1);
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(this.style.divider());
        for (int i = 0; i < this.table.get(index).size(); i++) {
            final String high = Highlighter.format(this.entry(index, i));
            final String low = Highlighter.unformat(this.entry(index, i).toString());
            sb.append(high).append(this.addSpace(widths, i, low)).append(this.style.divider());
        }
        return sb.toString();
    }

    public List<String> formattedRows() {
        final List<String> frows = new ArrayList<>();
        for (int i = 0; i < this.table.size(); i++) frows.add(this.formattedRow(i));
        return frows;
    }

    private String addSpace(final List<Integer> widths, final int index, final Object entry) {
        return " ".repeat(1 + Math.abs(widths.get(index)
                - Highlighter.visualLength(entry.toString().trim())));
    }

    @Override
    public String format() {
        this.sync();
        final StringBuilder sb = new StringBuilder();
        if (!this.headers.isEmpty()) {
            if (this.style.headerDivider().isEmpty() && !this.style.divider().isEmpty())
                this.style.headerDivider(" ".repeat(Highlighter.visualLength(this.style.divider())));
            final List<Integer> widths = this.formattedWidths(this.headers);
            sb.append(this.style.background()).append(this.style.foreground())
                    .append(this.style.headerDivider());
            for (int i = 0; i < this.headers.size(); i++) {
                sb.append(this.headers.get(i)).append(this.style.foreground())
                        .append(this.addSpace(widths, i, this.headers.get(i)))
                        .append(this.style.headerDivider());
            }
            sb.append("\n");
        }
        sb.append(formattedRows().stream().map(row -> row + "\n")
                .reduce("", (a, b) -> a + b));
        sb.deleteCharAt(sb.length() - 1);
        return this.style.border().wrap(sb).toString();
    }

    @Override
    public List<String> rowStrings() {
        return Arrays.asList(this.format().split("\n"));
    }

    // ── Widget contract ────────────────────────────────────────────

    @Override
    public TableWidget cursor(final Cursor cursor) {
        this.cursor = cursor;
        return this;
    }

    @Override
    public Style<TableWidget> getStyle() {
        return this.style;
    }

    @Override
    public TableWidget style(final Style<TableWidget> style) {
        this.style = style;
        if (this.style.foreground().isEmpty()) this.style.foreground("{{w}}");
        if (this.style.background().isEmpty()) this.style.background("{{[X]}}");
        if (this.style.divider().isEmpty()) this.style.divider("{{g}}|");
        return this;
    }

    @Override
    public void close() {
        Widget.super.close();
    }

    @Override
    public String renderInPlace() {
        return this.format() + "\n";
    }

    @Override
    public String renderFresh() {
        return this.format() + "\n";
    }

    @Override
    public String toString() {
        return this.format();
    }
}
