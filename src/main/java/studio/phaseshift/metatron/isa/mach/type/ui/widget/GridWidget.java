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
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.type.reflect.SpaceRec;
import studio.phaseshift.metatron.isa.mach.type.ui.Stylable;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_GRID_TID;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_STYLE_TID;

/*
 * A grid of embedded, runnable widgets: {@code grid => lst[lst[widget]]}.  A cell IS a
 * widget rec (carrying its type's {@code tid}), re-realized on every pass — so a cell is
 * live (toggle-able, style-able, addressable from the rec), never a toString snapshot.
 *
 * <p>State discipline: the grid holds <b>no</b> state fields — {@code grid} is the rec's
 * single home for the cells, and {@code rows} / {@code cols} are derived from its shape.
 * The style lives in the rec like every other widget's (the base for the children's
 * inherited look), so nothing here needs re-hydrating.
 *
 * <p>The grid is the layout/style manager for its children, mirroring how a drag updates
 * a pinned widget's {@code style::[top,left]}: on (re)layout it writes each cell's
 * <b>layout</b> ({@code width/height/top/left}) into the cell's style — authoritatively —
 * while <b>aesthetic</b> keys inherit (grid's style as the base, the cell's own keys on top).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class GridWidget extends SpaceRec<GridWidget> implements Widget<GridWidget> {

    private static final Obj K_GRID = uri("grid");

    /** The gap, in cells, between two cells in either direction. */
    private static final int GAP = 1;

    // ── constructors ───────────────────────────────────────────────

    public GridWidget(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.readStyle();
    }

    /** Allocate an empty grid of the given {@code rows} x {@code cols}; fill cells with {@link #cell}. */
    public GridWidget(final int rows, final int cols) {
        this(new LinkedHashMap<>(), UI_GRID_TID, null);
        this.put(K_GRID, this.emptyGrid(rows, cols));
    }

    // ── cell api ───────────────────────────────────────────────────

    /** Place {@code widget} at {@code (row, col)}, growing the grid to reach it.  A {@code null} removes the cell. */
    public GridWidget cell(final int row, final int col, final Widget<?> widget) {
        final List<List<Obj>> grid = this.gridOf(this.read());
        while (grid.size() <= row) grid.add(new ArrayList<>());
        final List<Obj> r = grid.get(row);
        while (r.size() <= col) r.add(noobj());
        r.set(col, null == widget ? noobj() : (Obj) widget);
        this.put(K_GRID, gridLst(grid));
        return this;
    }

    /** The (re-realized) widget at {@code (row, col)}, or {@code null} when that cell is empty. */
    public Widget<?> cell(final int row, final int col) {
        final List<List<Obj>> grid = this.gridOf(this.read());
        if (row >= grid.size()) return null;
        final List<Obj> r = grid.get(row);
        if (col >= r.size()) return null;
        return realize(r.get(col));
    }

    /** Store {@code widgets} row-major (cols = {@link #cols()}, or one per row when unallocated). */
    public GridWidget cells(final Widget<?>... widgets) {
        int n = 0;
        for (final Widget<?> w : widgets) {
            final int c = this.cols();
            final int r = c > 0 ? n / c : 0;
            final int cc = c > 0 ? n % c : n;
            this.cell(r, cc, w);
            n++;
        }
        return this;
    }

    /** Rows, derived from the rec's {@code grid} shape. */
    public int rows() {
        return this.gridOf(this.read()).size();
    }

    /** Columns, derived from the rec's {@code grid} shape. */
    public int cols() {
        final List<List<Obj>> grid = this.gridOf(this.read());
        return grid.isEmpty() ? 0 : grid.get(0).size();
    }

    // ── stylable / widget contract ─────────────────────────────────

    @Override
    public GridWidget cursor(final Cursor cursor) {
        return this;   // a layout hint for a parent laying this grid out; nothing reads it back
    }

    @Override
    public Stylable.Style<GridWidget> getStyle() {
        return (Stylable.Style<GridWidget>) Stylable.Style.from(this.get(this.read(), Stylable.STYLE_KEY));
    }

    @Override
    public GridWidget style(final Stylable.Style<GridWidget> s) {
        final Stylable.Style<GridWidget> st = null == s ? Stylable.Style.empty() : s;
        st.stylable = this;
        this.put(Stylable.STYLE_KEY, st);
        return this;
    }

    /**
     * Materialise this grid's style defaults into the rec (read through {@link #read()} so an
     * anchored grid adopts what the space holds).  A grid is a layout container: it carries the
     * inherited aesthetic base for its children, so a bare grid starts border-less.
     */
    private void readStyle() {
        final Obj s = this.get(this.read(), Stylable.STYLE_KEY);
        if (Stylable.Style.isStyle(s)) {
            this.style((Stylable.Style<GridWidget>) Stylable.Style.from(s));
            return;
        }
        this.style((Stylable.Style<GridWidget>) Stylable.Style.empty());
    }

    // ── lifecycle ──────────────────────────────────────────────────

    /**
     * A display widget must NOT take the default {@link Widget#close()}: that unfloats the grid
     * from the console's surface, which {@code .display()} (a run()+close() pair) would undo.
     */
    @Override
    public void close() {
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

    // ── pointer: dispatch to the cell under the pixel ──────────────

    /**
     * A grid-local {@code (row, col)} click is mapped onto the cell whose box (its last written
     * {@code top/left/width/height}) contains it, then translated into that cell's local
     * coordinates and handed to the cell's own {@code onClick} — this is how an embedded
     * {@code AccordionWidget}'s {@code [-]} / {@code [+]} toggles from inside the grid.  The
     * surface re-renders the grid when the click is consumed, so the toggled state shows.
     */
    @Override
    public boolean onClick(final int row, final int col) {
        final List<List<Obj>> grid = this.gridOf(this.read());
        if (grid.isEmpty()) return false;
        final int rows = grid.size();
        final int cols = grid.get(0).size();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                final Widget<?> cell = realize(grid.get(r).get(c));
                if (null == cell) continue;
                final Stylable.Style<?> st = cell.getStyle();
                final int top = st.top();
                final int left = st.left();
                final int h = Math.max(1, st.height());
                final int w = Math.max(1, st.width());
                if (row >= top && row < top + h && col >= left && col < left + w)
                    return cell.onClick(row - top, col - left);
            }
        }
        return false;
    }

    // ── rendering ──────────────────────────────────────────────────

    @Override
    public String format() {
        // ONE anchored read for the whole pass: an anchored grid renders from the space
        final Map<Obj, Obj> fields = this.read();
        final Stylable.Style<GridWidget> style = (Stylable.Style<GridWidget>) Stylable.Style.from(this.get(fields, Stylable.STYLE_KEY));
        final List<List<Obj>> grid = this.gridOf(fields);
        if (grid.isEmpty() || grid.get(0).isEmpty()) return "";
        final int rows = grid.size();
        final int cols = grid.get(0).size();

        // Realize every cell once (a cell is a widget rec re-built through its type)
        final List<List<Widget<?>>> cells = new ArrayList<>(rows);
        for (final List<Obj> rowObjects : grid) {
            final List<Widget<?>> row = new ArrayList<>(rowObjects.size());
            for (final Obj o : rowObjects) row.add(realize(o));
            cells.add(row);
        }

        // Column widths: the grid's width split evenly when it carries one; otherwise the
        // widest natural width in each column.
        final int gridWidth = style.width();
        final int[] colWidth = new int[cols];
        if (gridWidth > 0) {
            final int each = Math.max(1, (gridWidth - (cols - 1) * GAP) / cols);
            Arrays.fill(colWidth, each);
        } else {
            for (int c = 0; c < cols; c++)
                for (final List<Widget<?>> row : cells)
                    if (null != row.get(c)) colWidth[c] = Math.max(colWidth[c], row.get(c).width());
        }

        // Left offsets in the grid
        final int[] left = new int[cols];
        for (int c = 0; c < cols; c++)
            left[c] = 0 == c ? 0 : left[c - 1] + colWidth[c - 1] + GAP;

        // The full line width: pads the vertical gap lines so rows stay aligned
        final int rowWidth = Arrays.stream(colWidth).sum() + (cols - 1) * GAP;

        // Compose row by row.  Each cell is measured at its column width (natural height),
        // the row's height is the tallest cell, then the children's layout is written and they
        // re-render at that height so every cell in a row aligns.
        final List<String> canvas = new ArrayList<>();
        int top = 0;
        for (int r = 0; r < rows; r++) {
            final List<Widget<?>> row = cells.get(r);

            int rowHeight = 1;
            for (int c = 0; c < cols; c++) {
                final Widget<?> cell = row.get(c);
                if (null == cell) continue;
                this.styleCell(cell, colWidth[c], 0, 0, left[c]);
                rowHeight = Math.max(rowHeight, cell.format().split("\n", -1).length);
            }

            final List<String[]> rendered = new ArrayList<>(cols);
            for (int c = 0; c < cols; c++) {
                final Widget<?> cell = row.get(c);
                if (null == cell) {
                    rendered.add(new String[0]);
                    continue;
                }
                this.styleCell(cell, colWidth[c], rowHeight, top, left[c]);
                rendered.add(cell.format().split("\n", -1));
            }

            for (int i = 0; i < rowHeight; i++) {
                final StringBuilder line = new StringBuilder();
                for (int c = 0; c < cols; c++) {
                    if (c > 0) line.append(" ".repeat(GAP));
                    final String[] lines = rendered.get(c);
                    final String piece = i < lines.length ? lines[i] : "";
                    line.append(piece);
                    final int pad = colWidth[c] - Highlighter.visualLength(piece);
                    if (pad > 0) line.append(" ".repeat(pad));
                }
                canvas.add(line.toString());
            }
            // The layout (top below) inserts a GAP between rows, so render one here too —
            // otherwise each cell after row 0 is recorded one row lower than it draws,
            // and a click on its header misses.
            if (r < rows - 1) canvas.add(" ".repeat(rowWidth));
            top += rowHeight + GAP;
        }
        return String.join("\n", canvas);
    }

    // ── helpers ────────────────────────────────────────────────────

    /**
     * Resolve one cell's style: the grid's style as the aesthetic base, the cell's own keys on
     * top (child precedence), and the grid's layout ({@code width/height/top/left}) authoritative
     * — the same "write the geometry into the child's style" move a drag already makes.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void styleCell(final Widget<?> cell, final int width, final int height,
                           final int top, final int left) {
        final Map<Obj, Obj> merged = new LinkedHashMap<>(this.getStyle().jvm());
        merged.putAll(cell.getStyle().jvm());
        merged.put(uri("width"), jnt(width));
        merged.put(uri("height"), jnt(height));
        merged.put(uri("top"), jnt(top));
        merged.put(uri("left"), jnt(left));
        cell.style((Stylable.Style) Stylable.Style.from(new MRec(merged, UI_STYLE_TID, null)));
    }

    /** The grid's {@code grid} rec as a mutable {@code lst} of {@code lst}s of cell objs. */
    private List<List<Obj>> gridOf(final Map<Obj, Obj> fields) {
        final List<List<Obj>> out = new ArrayList<>();
        final Obj g = this.get(fields, K_GRID);
        if (null == g || g.isNoObj()) return out;
        if (g.isLst())
            for (final Obj rowObj : g.lstValue()) {
                final List<Obj> row = new ArrayList<>();
                if (rowObj != null && rowObj.isLst())
                    for (final Obj cellObj : rowObj.lstValue()) row.add(cellObj);
                out.add(row);
            }
        // A grid is a rectangle: pad the short rows with empty cells so no consumer (cols,
        // format, onClick, cell) ever indexes past the end of a short row — the very
        // index-out-of-bounds a ragged mtron grid caused when one row was wider than the next.
        int cols = 0;
        for (final List<Obj> row : out) cols = Math.max(cols, row.size());
        for (final List<Obj> row : out)
            while (row.size() < cols) row.add(noobj());
        return out;
    }

    /** A rows x cols grid of empty cells (a {@code noobj} placeholder each). */
    private Obj emptyGrid(final int rows, final int cols) {
        final List<List<Obj>> grid = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            final List<Obj> row = new ArrayList<>();
            for (int c = 0; c < cols; c++) row.add(noobj());
            grid.add(row);
        }
        return gridLst(grid);
    }

    /** Rebuild a {@code lst[lst[...]]} from the nested list view. */
    private static Obj gridLst(final List<List<Obj>> grid) {
        final List<Obj> rows = new ArrayList<>(grid.size());
        for (final List<Obj> row : grid) rows.add(lst(row));
        return lst(rows);
    }

    /** Re-realize a cell rec as a live widget through its type — the "runnable, not toString" step. */
    private static Widget<?> realize(final Obj cellObj) {
        if (cellObj instanceof Widget<?> w) return w;
        if (null == cellObj || cellObj.isNoObj() || !cellObj.isRec()) return null;
        try {
            return Widget.of(cellObj.asRec());
        } catch (final Exception e) {
            return null;
        }
    }
}
