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
import studio.phaseshift.metatron.isa.m.type.reflect.SpaceRec;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_TABLE_TID;

/**
 * A tabular data widget.  Its state is three rec keys — {@code header} (a lst of
 * column names), {@code row} (a lst of rows) and {@code metadata} (a lst of rows of
 * data behind the display) — and nothing else: no Java fields, so a table written by
 * mtron and a table built through the Java API are the same table, read and written
 * through the same path, and neither can go stale behind the other.
 *
 * <p>Cells are {@code Object} on the Java side because both ends of the wire carry
 * more than text: {@code addRow} takes whatever a caller has (a String, an fURI, a
 * whole Obj — a table row is a place to hang data, and {@code ExplainTool} parks
 * {@code Type}s and {@code cInt}s in its metadata rows).  The two conversions are
 * therefore each in exactly one place and are inverses: {@link #obj(Object)} on the
 * way into the rec, {@link #cell(Obj)} on the way out (a str reads as a String, a uri
 * as an fURI, everything else is handed back as the Obj it is).  That is what keeps a
 * mtron-built table and a Java-built table interchangeable — the JRec version kept
 * Java cells for one and converted the other, so the same column could hand back a
 * String or an Obj depending on who built the table.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TableWidget extends SpaceRec<TableWidget> implements Widget<TableWidget> {

    private static final Obj K_HEADER = uri(HEADER);
    private static final Obj K_ROW = uri(ROW);
    private static final Obj K_METADATA = uri(METADATA);

    // ── construction ───────────────────────────────────────────────

    public TableWidget(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.readStyle();
    }

    public TableWidget() {
        this(new LinkedHashMap<>(), UI_TABLE_TID, null);
    }

    /**
     * A table with headers and no rows yet — the shape every caller wants before
     * adding data ({@code new TableWidget(List.of("path", "actual")).addRow(...)}).
     */
    public TableWidget(final List<String> headers) {
        this();
        this.headers(headers);
    }

    /**
     * Adopt the style the rec carries, filling in this widget's defaults — the rec,
     * not a Java field, is where a style lives, so a store-backed table keeps the one
     * it was given.  Done here rather than in {@code style(setter)} so that a style
     * written by mtron is completed the same way as one written through the API (the
     * setter used to re-apply these defaults on every write, which left a
     * mtron-constructed table with no foreground/background at all).
     */
    private void readStyle() {
        final Obj s = this.get(this.read(), STYLE_KEY);
        final Style<TableWidget> style = Style.isStyle(s) ? Style.from(s) : Style.empty();
        style.stylable = this;
        if (style.foreground().isEmpty()) style.foreground("{{w}}");
        if (style.background().isEmpty()) style.background("{{[X]}}");
        this.put(STYLE_KEY, style);
    }

    // ── the cell boundary ──────────────────────────────────────────

    /** A Java cell as the rec stores it. */
    private static Obj obj(final Object cell) {
        if (cell instanceof Obj o) return o;
        if (cell instanceof String s) return str(s);
        if (cell instanceof fURI u) return uri(u);
        if (cell instanceof Boolean b) return bool(b);
        if (cell instanceof Integer i) return jnt(i);
        if (cell instanceof Long l) return jnt(l);
        if (cell instanceof Double d) return real(d);
        if (cell instanceof Float f) return real(f);
        return str(String.valueOf(cell));
    }

    /**
     * A rec cell as Java callers expect it — the inverse of {@link #obj(Object)}: a
     * str is a String, a uri is an fURI, anything else is left as the Obj carrying it
     * (that is how {@code CardUtil} and {@code ExplainTool} get their data back out).
     */
    private static Object cell(final Obj cell) {
        if (cell.isStr()) return cell.strValue();
        if (cell.isUri()) return cell.uriValue();
        return cell;
    }

    private static List<Obj> objs(final List<?> cells) {
        final List<Obj> objs = new ArrayList<>(cells.size());
        cells.forEach(c -> objs.add(obj(c)));
        return objs;
    }

    private static List<Object> cells(final Obj row) {
        final List<Object> cells = new ArrayList<>();
        forEachValue(row, c -> cells.add(cell(c)));
        return cells;
    }

    private static Obj asLst(final List<Obj> objs) {
        return lst(objs.toArray(new Obj[0]));
    }

    // ── the reads (one rec read per call, one per pass in a render) ─

    /** The column names, in order. */
    public List<String> headers() {
        return this.headers(this.read());
    }

    private List<String> headers(final Map<Obj, Obj> fields) {
        final List<String> headers = new ArrayList<>();
        forEachValue(this.get(fields, K_HEADER), o -> {
            if (o.isStr()) headers.add(o.strValue());
        });
        return headers;
    }

    /** One column name. */
    public String header(final int column) {
        return this.headers().get(column);
    }

    /** Every row, as Java cells. */
    public List<List<Object>> rows() {
        return this.rows(this.read());
    }

    private List<List<Object>> rows(final Map<Obj, Obj> fields) {
        final List<List<Object>> rows = new ArrayList<>();
        forEachValue(this.get(fields, K_ROW), row -> rows.add(cells(row)));
        return rows;
    }

    /** One row. */
    public List<Object> row(final int index) {
        return this.rows().get(index);
    }

    /** One cell. */
    public Object entry(final int row, final int column) {
        return this.row(row).get(column);
    }

    /** One column, top to bottom (a ragged row simply has no such column). */
    public List<Object> column(final int col) {
        final List<Object> column = new ArrayList<>();
        for (final List<Object> row : this.rows())
            if (row.size() > col) column.add(row.get(col));
        return column;
    }

    /** Every metadata row, as Java cells — data the display does not show. */
    public List<List<Object>> metadata() {
        final List<List<Object>> metadata = new ArrayList<>();
        forEachValue(this.get(this.read(), K_METADATA), row -> metadata.add(cells(row)));
        return metadata;
    }

    /** The metadata row behind one displayed row. */
    public List<Object> rowMetadata(final int index) {
        return this.metadata().get(index);
    }

    /** One metadata cell. */
    public Object entryMetadata(final int row, final int column) {
        return this.rowMetadata(row).get(column);
    }

    // ── the writes (read-merge-write: the rec is the only copy) ─────

    /** Replace the column names. */
    public TableWidget headers(final List<String> headers) {
        this.put(K_HEADER, asLst(objs(headers)));
        return this;
    }

    /** Append a row. */
    public TableWidget addRow(final List<?> cells) {
        final List<Obj> rows = new ArrayList<>(this.raw(this.read(), K_ROW));
        rows.add(asLst(objs(cells)));
        this.put(K_ROW, asLst(rows));
        return this;
    }

    /**
     * Append a row — or replace the existing row whose cell at {@code keyColumn} is
     * equal to the new row's, which is how a progress display updates its own lines
     * instead of growing a new one per tick.
     */
    public TableWidget upsertRow(final List<?> cells, final int keyColumn) {
        final List<Obj> rows = new ArrayList<>(this.raw(this.read(), K_ROW));
        final List<Obj> fresh = objs(cells);
        int existing = -1;
        for (int i = 0; i < rows.size() && existing < 0; i++) {
            final List<Object> row = cells(rows.get(i));
            if (row.size() > keyColumn && fresh.size() > keyColumn
                    && Objects.equals(row.get(keyColumn), cell(fresh.get(keyColumn))))
                existing = i;
        }
        if (existing < 0) rows.add(asLst(fresh));
        else rows.set(existing, asLst(fresh));
        this.put(K_ROW, asLst(rows));
        return this;
    }

    /** Append a metadata row (data behind a displayed row, not shown). */
    public TableWidget addMetadata(final List<?> cells) {
        final List<Obj> metadata = new ArrayList<>(this.raw(this.read(), K_METADATA));
        metadata.add(asLst(objs(cells)));
        this.put(K_METADATA, asLst(metadata));
        return this;
    }

    /** Drop every row and metadata row; the column names stay. */
    public TableWidget clear() {
        this.put(K_ROW, noobj());
        this.put(K_METADATA, noobj());
        return this;
    }

    /** The row values as the rec holds them — what a read-merge-write carries forward. */
    private List<Obj> raw(final Map<Obj, Obj> fields, final Obj key) {
        final List<Obj> rows = new ArrayList<>();
        forEachValue(this.get(fields, key), rows::add);
        return rows;
    }

    // ── formatting ─────────────────────────────────────────────────

    @Override
    public String format() {
        // ONE style read and ONE data read for the pass: a render never hits the space
        // per cell, and never writes anything into the rec while drawing.
        final Map<Obj, Obj> fields = this.read();
        final Style<TableWidget> style = Style.from(this.get(fields, STYLE_KEY));
        final List<String> headers = this.headers(fields);
        final List<List<Object>> rows = this.rows(fields);
        final List<Integer> widths = widths(headers, rows);
        final StringBuilder sb = new StringBuilder();
        if (!headers.isEmpty()) {
            final String headerDivider = style.headerDivider().isEmpty() && !style.divider().isEmpty()
                    ? " ".repeat(Highlighter.visualLength(style.divider()))
                    : style.headerDivider();
            sb.append(style.background()).append(style.foreground()).append(headerDivider);
            for (int i = 0; i < headers.size(); i++)
                sb.append(headers.get(i)).append(style.foreground())
                        .append(padding(widths, i, headers.get(i)))
                        .append(headerDivider);
            sb.append("\n");
        }
        for (int i = 0; i < rows.size(); i++)
            sb.append(formattedRow(rows, widths, i, style)).append("\n");
        if (!sb.isEmpty()) sb.deleteCharAt(sb.length() - 1);
        if (sb.isEmpty()) return "";
        return style.border().wrap(sb).toString();
    }

    /**
     * One width per column: the widest thing that will be drawn in it — a header, or
     * any cell, unformatted and split on newlines — across the columns the table
     * actually has.  (The old width pass padded short rows with a hard-coded 1 and
     * had to guess the column count separately.)
     */
    private static List<Integer> widths(final List<String> headers, final List<List<Object>> rows) {
        final int columns = Math.max(headers.size(), rows.stream().mapToInt(List::size).max().orElse(0));
        final List<Integer> widths = new ArrayList<>(columns);
        for (int column = 0; column < columns; column++) {
            final int i = column;
            widths.add(Math.max(
                    i < headers.size() ? headers.get(i).length() : 0,
                    rows.stream().filter(row -> row.size() > i)
                            .map(row -> Highlighter.unformat(String.valueOf(row.get(i))))
                            .flatMap(s -> Arrays.stream(s.split("\n")))
                            .mapToInt(String::length)
                            .max().orElse(0)));
        }
        return widths;
    }

    private static String formattedRow(final List<List<Object>> rows, final List<Integer> widths,
                                       final int index, final Style<TableWidget> style) {
        final List<Object> row = rows.get(index);
        final StringBuilder sb = new StringBuilder();
        sb.append(style.divider());
        for (int i = 0; i < row.size(); i++) {
            final Object entry = row.get(i);
            sb.append(Highlighter.format(entry))
                    .append(padding(widths, i, Highlighter.unformat(String.valueOf(entry))))
                    .append(style.divider());
        }
        return sb.toString();
    }

    private static String padding(final List<Integer> widths, final int index, final String entry) {
        return " ".repeat(1 + Math.abs(widths.get(index) - Highlighter.visualLength(entry)));
    }

    @Override
    public List<String> rowStrings() {
        return Arrays.asList(this.format().split("\n"));
    }

    // ── Widget contract ────────────────────────────────────────────

    @Override
    public TableWidget cursor(final Cursor cursor) {
        return this;   // a layout hint for a parent widget; nothing reads it back
    }

    @Override
    public Style<TableWidget> getStyle() {
        return Style.from(this.get(this.read(), STYLE_KEY));
    }

    @Override
    public TableWidget style(final Style<TableWidget> style) {
        final Style<TableWidget> st = null == style ? Style.empty() : style;
        st.stylable = this;
        this.put(STYLE_KEY, st);
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
