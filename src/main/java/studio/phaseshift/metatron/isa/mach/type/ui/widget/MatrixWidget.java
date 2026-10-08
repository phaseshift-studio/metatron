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
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.Stylable;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.TITLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_MATRIX_TID;

/*
 * A matrix of glyphs: {@code grid => lst[lst[str]]}.  Cell {@code (row, col)} is drawn in the
 * col-th column of the face's row-th line — the one-to-one correspondence the widget is for.
 * It is the display half of a small turn-based grid game: the game logic lives in mtron and
 * writes cells, the widget draws them.
 *
 * <p>State discipline: the face is the rec's {@code grid} key and nothing else — no Java fields,
 * so every assertion and every render reads through the rec, and a fresh instance built by the
 * next {@code .display()} renders what the space holds.
 *
 * <p>There is no per-cell repaint path, deliberately.  A pinned widget is re-rendered IN FULL on
 * every surface pass (a prompt cycle, a resize, a click, a wheel notch), and that pass is cheap: a
 * board is plain text, so measuring is O(1) (no markup, no ANSI), nothing re-highlights, and a pass
 * that would write the same bytes is skipped whole.  So the loop is ordinary data — the grid's own
 * paths are the coordinates, and the next pass shows the write:
 *
 * <pre>{@code
 *   board -> matrix_widget::[grid=>[['.','.'],['.','.']], style=>[anchor=>top_right]]@/usr/uidoc/board
 *   @/usr/uidoc/board.display()            [-- pin it once --]
 *   @/usr/uidoc/board/grid/1/0 >>= 'X'     [-- cell (1,0); the next pass draws it --]
 *   @/usr/uidoc/board/grid/+/0 >>= '%'     [-- every row's column 0 --]
 * }</pre>
 *
 * <p>The grid is read with {@code at()}, so it may also be stored lazily as a REFERENT — a grid
 * holding an auto that points at an absolute uri (the data living at its own vid) — and the widget
 * becomes a view over it; the board is then mutated
 * by writing the referent, never through the widget.  An absolute uri is required: a relative uri is
 * a variable frame on the thread that wrote it, and a render pass runs on the surface's render
 * thread, where a relative referent does not resolve.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MatrixWidget extends SpaceRec<MatrixWidget> implements Widget<MatrixWidget> {

    private static final Obj K_GRID = uri("grid");
    private static final Obj K_TITLE = uri("title");

    /**
     * One glyph per column: cell {@code (row, col)} is drawn in the col-th column of the face.
     * A cell whose glyph is not one column wide is drawn only if it fits in the grid's width
     * (an incremental paint of such a cell falls back to a full pass rather than shifting the
     * cells to its right).
     */
    private static final int CELL_WIDTH = 1;

    // ── constructors ───────────────────────────────────────────────

    public MatrixWidget(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        this.readStyle();
    }

    /** A bordered matrix over the given rows of glyphs. */
    public MatrixWidget(final List<List<String>> face) {
        this(new LinkedHashMap<>(), UI_MATRIX_TID, null);
        if (null != face) this.put(K_GRID, gridLst(face));
    }

    // ── face api ───────────────────────────────────────────────────

    /** Rows in the face, derived from the rec's {@code grid} shape. */
    public int rows() {
        return this.faceOf(this.read()).size();
    }

    /** Columns in the face, derived from the rec's {@code grid} shape. */
    public int cols() {
        final List<List<String>> face = this.faceOf(this.read());
        return face.isEmpty() ? 0 : face.get(0).size();
    }

    /** The glyph at {@code (row, col)}, or {@code ""} when that cell is off the face. */
    public String cell(final int row, final int col) {
        final List<List<String>> face = this.faceOf(this.read());
        if (row < 0 || row >= face.size()) return "";
        final List<String> r = face.get(row);
        return (col < 0 || col >= r.size()) ? "" : r.get(col);
    }




    // ── stylable / widget contract ─────────────────────────────────

    @Override
    public MatrixWidget cursor(final Cursor cursor) {
        return this;   // a layout hint for a parent laying this matrix out; nothing reads it back
    }

    @Override
    public Style<MatrixWidget> getStyle() {
        return Style.from(this.get(this.read(), STYLE_KEY));
    }

    /**
     * Bind a style to this widget and put it in the widget's rec.  The rec — not a Java field — is
     * the home of the style, so a store-backed matrix's look survives the re-hydration every
     * {@code .display()} update performs.
     */
    @Override
    public MatrixWidget style(final Style<MatrixWidget> style) {
        final Style<MatrixWidget> s = null == style ? Style.empty() : style;
        s.stylable = this;
        this.put(STYLE_KEY, s);
        return this;
    }

    /**
     * Materialise this matrix's style defaults into the rec (read through {@link #read()} so an
     * anchored matrix adopts what the space holds).  A matrix is a box, so the default is a
     * continuous border — but an explicit {@code border => none} is honored, which is the one place
     * this differs from {@link PanelWidget}.
     */
    private void readStyle() {
        final Obj s = this.get(this.read(), STYLE_KEY);
        if (Style.isStyle(s)) {
            this.style(Style.from(s));
            return;
        }
        final Style<MatrixWidget> fresh = Style.empty();
        fresh.stylable = this;
        fresh.border(Border.continuous);
        this.put(STYLE_KEY, fresh);
    }

    /**
     * A display widget must NOT take the default {@link Widget#close()}: that unfloats the matrix
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

    // ── rendering ──────────────────────────────────────────────────

    @Override
    public String format() {
        // ONE anchored read for the whole pass: an anchored matrix renders (and latches its member
        // instruction) from the space, not from a construction-time snapshot
        final Map<Obj, Obj> fields = this.read();
        final List<List<String>> face = this.faceOf(fields);
        if (face.isEmpty() || face.get(0).isEmpty()) return "";
        final Style<MatrixWidget> style = Style.from(this.get(fields, STYLE_KEY));

        final int cols = face.get(0).size();
        final Geometry geometry = Geometry.of(style, cols);
        final Border border = style.border();
        // Border.none is a border whose every glyph is a SPACE (see Border.none), not an absent one:
        // drawing it would offset the face by a blank row and column, so a borderless matrix draws
        // no border glyphs at all and cell (0,0) lands on frame (0,0).
        final boolean bordered = !isBorderless(border);
        final String leftSide = bordered ? border.leftSide() : "";
        final String rightSide = bordered ? border.rightSide() : "";
        // Style background/foreground are re-applied on every line because each line ends with a
        // {{X}} reset that would otherwise drop them after the first row (the PanelWidget rule).
        final String color = style.background() + style.foreground();
        final String title = Utilities.titleClip(this.getStr(fields, K_TITLE),
                geometry.leftMargin() + geometry.gridWidth() + geometry.rightMargin(), 0);
        final List<String> canvas = new ArrayList<>();

        // The top border is skipped when it would draw nothing — a border-less, title-less matrix.
        final String top = (title + border.topSide().repeat(
                Math.max(0, geometry.contentWidth() - Highlighter.visualLength(title)))).stripTrailing();
        if (bordered && !top.isEmpty())
            canvas.add(color + border.topLeftCorner() + top + border.topRightCorner() + "{{X}}");
        for (int i = 0; i < geometry.topMargin(); i++)
            canvas.add(color + leftSide + " ".repeat(geometry.contentWidth()) + rightSide + "{{X}}");

        for (final List<String> row : face) {
            final String glyphs = rowText(row, geometry.gridWidth());
            canvas.add(color + leftSide
                    + " ".repeat(geometry.leftMargin())
                    + glyphs
                    + " ".repeat(Math.max(0, geometry.gridWidth() - Highlighter.visualLength(glyphs)))
                    + " ".repeat(geometry.rightMargin())
                    + "{{X}}" + rightSide);
        }

        for (int i = 0; i < geometry.bottomMargin(); i++)
            canvas.add(color + leftSide + " ".repeat(geometry.contentWidth()) + rightSide + "{{X}}");
        final String bottom = border.bottomSide().repeat(geometry.contentWidth()).stripTrailing();
        if (bordered && !bottom.isEmpty())
            canvas.add(color + border.bottomLeftCorner() + bottom + border.bottomRightCorner() + "{{X}}");
        // No trailing newline: the surface splits a widget's frame with split("\n", -1), so a
        // trailing newline would add a phantom row and shift every cell's frame coordinate.
        return style.prefix() + String.join("\n", canvas);
    }

    // ── helpers ────────────────────────────────────────────────────

    /**
     * Where the face sits inside the widget's rendered frame.  The frame's geometry is computed in
     * exactly one place so {@link #format()} and the incremental paint cannot disagree about where a
     * cell is — the same "one measurement, two consumers" discipline the accordion's header uses.
     */
    private record Geometry(int borderTop, int borderLeft, int topMargin, int leftMargin,
                            int rightMargin, int gridWidth, int bottomMargin) {

        static Geometry of(final Style<MatrixWidget> style, final int cols) {
            // A border is drawn (and so occupies rows/columns) only when one is configured, and the
            // top/bottom border lines are dropped when they would be empty.
            final boolean bordered = !isBorderless(style.border());
            final int leftMargin = Math.max(0, style.leftMargin());
            final int rightMargin = Math.max(0, style.rightMargin());
            final int declared = style.width();
            // PanelWidget's rule: a declared width is the CONTENT width (borders sit outside it),
            // and the margins are part of that content.
            final int gridWidth = declared > 0
                    ? Math.max(0, declared - leftMargin - rightMargin)
                    : Math.max(0, cols * CELL_WIDTH);
            return new Geometry(bordered ? 1 : 0, bordered ? 1 : 0,
                    Math.max(0, style.topMargin()), leftMargin, rightMargin, gridWidth,
                    Math.max(0, style.bottomMargin()));
        }


        /** The content width the border is drawn around. */
        int contentWidth() {
            return this.leftMargin + this.gridWidth + this.rightMargin;
        }
    }

    /**
     * Is this a border that draws nothing?  Compared by its glyph template, not by identity:
     * {@link Border#parse(String)} builds a fresh border for a raw template, so a style that was
     * told {@code border => none} and stored as one parses back equal to — but not the same object
     * as — {@link Border#none}.
     */
    private static boolean isBorderless(final Border border) {
        return null == border || Border.none.toString().equals(border.toString());
    }

    /**
     * A face row's glyphs, clipped to {@code gridWidth} columns — a cell whose glyph would not fit
     * (a wide glyph, or a declared width narrower than the grid) is dropped rather than left to
     * shift the cells after it.
     */
    private static String rowText(final List<String> row, final int gridWidth) {
        if (gridWidth <= 0) return "";
        final StringBuilder sb = new StringBuilder();
        int used = 0;
        for (final String glyph : row) {
            final int width = Highlighter.visualLength(glyph);
            if (used + width > gridWidth) break;
            sb.append(glyph);
            used += width;
        }
        return sb.toString();
    }

    /**
     * The rec's {@code grid} as a mutable lst of lsts of glyphs, padded to a rectangle so no
     * consumer (cols, format, cell, update) can index past the end of a short row — the very
     * index-out-of-bounds a ragged mtron grid caused.
     */
    private List<List<String>> faceOf(final Map<Obj, Obj> fields) {
        final List<List<String>> out = new ArrayList<>();
        // at(), NOT a raw map read: a grid may be stored LAZILY — grid=>!*g is an auto, and only
        // at() resolves it (autoToggle); fields.get() hands back the wrapped auto and the board
        // draws nothing until the caller forces it.  at() is also the read that walks a key whose
        // value arrives as a multiplicity, so this is the one read that has to resolve.
        final Obj g = this.at(K_GRID);
        if (null == g || g.isNoObj()) return out;
        // Never shape-inspect a read: a grid that was built by a fold arrives as a MULTIPLICITY of
        // rows (lst{10}::T) while a literal arrives as ONE lst of lsts (lst[lst[str]]::T), and both
        // are the same grid.  forEachValue walks a lst's members, a multiplicity's members, and a
        // lone value alike — the rule the body/row readers already use.
        forEachValue(g, rowObj -> {
            final List<String> row = new ArrayList<>();
            forEachValue(rowObj, cell -> row.add(glyphOf(cell)));
            out.add(row);
        });
        int cols = 0;
        for (final List<String> row : out) cols = Math.max(cols, row.size());
        for (final List<String> row : out) while (row.size() < cols) row.add(" ");
        return out;
    }

    /** A cell as one glyph: a str is its text, a structure is blank, anything else its mtron text. */
    private static String glyphOf(final Obj cell) {
        if (null == cell || cell.isNoObj()) return " ";
        if (cell.isStr()) return cell.strValue();
        if (cell.isLst() || cell.isRec() || cell.isRel()) return " ";
        return cell.toString();
    }

    /** Rebuild a {@code lst[lst[str]]} from the nested list view. */
    private static Obj gridLst(final List<List<String>> face) {
        final List<Obj> rows = new ArrayList<>(face.size());
        for (final List<String> row : face) {
            final List<Obj> cells = new ArrayList<>(row.size());
            for (final String glyph : row) cells.add(str(null == glyph ? "" : glyph));
            rows.add(lst(cells));
        }
        return lst(rows);
    }
}
