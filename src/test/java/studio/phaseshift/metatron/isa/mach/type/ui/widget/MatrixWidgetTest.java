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

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractWidgetTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.ui.uiInstSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A matrix of glyphs: the rec's {@code grid} is a lst of lsts of characters and cell {@code (row,
 * col)} draws in the col-th column of the face's row-th line.  Every assertion reads back through
 * the rec — never a field — and the frame is measured after stripping its markup, so what is
 * checked is what is drawn.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MatrixWidgetTest extends AbstractWidgetTest {

    private static final fURI TID = f("/m/mach/ui/widget/matrix_widget");

    public MatrixWidgetTest() {
        super(uiInstSet::new);
    }

    @Override
    protected Object sampleWidget() {
        return new MatrixWidget(List.of(List.of("#", "#", "#"), List.of("#", "@", "#"), List.of("#", "#", "#")));
    }

    // ── the one-to-one correspondence ──────────────────────────────

    @Test
    public void shouldDrawEachGlyphAtItsOwnGridCoordinate() {
        final MatrixWidget matrix = new MatrixWidget(List.of(
                List.of(".", ".", "."),
                List.of(".", "@", "."),
                List.of(".", ".", "$")));
        final String[] frame = strippedFrame(matrix);
        // with a border, face row 0 is frame row 1 and face column 0 is frame column 1
        assertEquals('@', frame[2].charAt(2), "cell (1,1) draws at frame (2,2):\n" + String.join("\n", frame));
        assertEquals('$', frame[3].charAt(3), "cell (2,2) draws at frame (3,3):\n" + String.join("\n", frame));
        assertEquals('.', frame[1].charAt(1), "cell (0,0) draws at frame (1,1):\n" + String.join("\n", frame));
    }

    @Test
    public void shouldKeepTheRenderedFrameRectangularForARaggedGrid() {
        final MatrixWidget matrix = new MatrixWidget(List.of(List.of("#", "#", "#"), List.of("#")));
        assertEquals(2, matrix.rows(), "two rows");
        assertEquals(3, matrix.cols(), "the short row is padded to the widest one");
        assertEquals(" ", matrix.cell(1, 2), "a padded cell reads as a space");
        final String[] frame = strippedFrame(matrix);
        assertEquals(frame[1].length(), frame[2].length(), "both face rows draw the same width");
    }

    // ── style: the same keys as every other widget ─────────────────

    @Test
    public void shouldHonorAnExplicitBorderlessStyle() {
        final MatrixWidget matrix = new MatrixWidget(List.of(List.of("A", "B")));
        matrix.style().border(Border.none).applyStyle();
        final String[] frame = strippedFrame(matrix);
        assertEquals("AB", frame[0], "with no border the face starts at frame row 0, column 0");
    }

    @Test
    public void shouldPlaceTheFaceInsideItsMarginsAndBorder() {
        final MatrixWidget matrix = new MatrixWidget(List.of(List.of("A")));
        matrix.style().margin(2, 1).applyStyle();
        final String[] frame = strippedFrame(matrix);
        // the line is: left border, left margin (2), the glyph, the right margin (1), right border
        assertEquals('A', frame[1].charAt(1 + 2), "the glyph sits after the border and the left margin:\n" + frame[1]);
        assertEquals(6, frame[1].length(), "border + 2 left margin + 1 glyph + 1 right margin + border: " + frame[1]);
    }

    @Test
    public void shouldClipTheFaceToADeclaredContentWidth() {
        final MatrixWidget matrix = new MatrixWidget(List.of(List.of("a", "b", "c", "d", "e")));
        matrix.style().width(4).applyStyle();
        final String[] frame = strippedFrame(matrix);
        assertEquals("abcd", frame[1].substring(1, 5), "a declared width is the content width: " + frame[1]);
    }

    @Test
    public void shouldRenderTheTitleInTheTopBorder() {
        final MatrixWidget matrix = new MatrixWidget(mutableMap(
                uri("grid"), (Obj) lst(lst(str("."), str("."), str("."), str("."), str("."), str("."))),
                uri("title"), str("lvl 1")), TID, null);
        assertTrue(strippedFrame(matrix)[0].contains("lvl 1"), "the title is drawn in the top border");
    }

    // ── the rec is the state ───────────────────────────────────────

    @Test
    public void shouldRenderTwoMatricesOverOneRecIdentically() {
        final MatrixWidget source = new MatrixWidget(List.of(List.of("#", "#"), List.of("#", "@")));
        final Map<Obj, Obj> body = source.jvm();
        assertEquals(new MatrixWidget(body, TID, null).format(), new MatrixWidget(body, TID, null).format(),
                "two matrices over the same rec must render identically (rec parity)");
    }



    @Test
    public void shouldRenderIdempotently() {
        final MatrixWidget matrix = new MatrixWidget(List.of(List.of("#", "#"), List.of("#", "#")));
        assertEquals(matrix.format(), matrix.format(), "formatting the same matrix twice renders the same frame");
    }

    // ── the mtron-facing cell inst + the grid-path write ───────────


    @Test
    public void shouldShowACellWrittenAtItsOwnGridPath() {
        final MatrixWidget matrix = new MatrixWidget(List.of(List.of(".", "."), List.of(".", ".")));
        Machine.write(f("matrix_widget_path"), matrix);
        // what @…/grid/1/0 >>= '@' does: the cell's path IS its coordinate
        Machine.write(f("matrix_widget_path/grid/1/0"), str("@"));
        final Obj readBack = Machine.read(f("matrix_widget_path"));
        assertEquals("@", new MatrixWidget(readBack.asRec().jvm(), TID, null).cell(1, 0),
                "a write at …/grid/<row>/<col> is the cell write");
        assertTrue(new MatrixWidget(readBack.asRec().jvm(), TID, null).format().contains("@"),
                "and the cell renders");
    }






    @Test
    public void shouldWalkAGridWhoseRowsComeAsAMultiplicity() {
        // A read never promises the shape its caller expects: a key can answer as a multiplicity
        // (lst{2}::T) while a literal answers as one lst of lsts, and both are the same grid.  Built
        // over the BASE widget type because the matrix predicate rightly refuses to *store* a
        // multiplicity — this locks the read, which must not depend on the shape it is handed.
        final Obj rows = objs(lst(str("."), str(".")), lst(str("."), str("@")));
        final MatrixWidget board = new MatrixWidget(mutableMap(uri("grid"), rows), f("/m/mach/ui/widget"), null);
        assertEquals(2, board.rows(), "the walk takes a multiplicity's members as rows");
        assertEquals(2, board.cols(), "and each row's members as cells");
        assertEquals("@", board.cell(1, 1), "cell (1,1) reads through the multiplicity");
        assertTrue(board.format().contains("@"), "and renders: " + board.format());
    }

    @Test
    public void shouldRenderATenByTenBoard() {
        final List<List<String>> face = new ArrayList<>();
        for (int r = 0; r < 10; r++) face.add(new ArrayList<>(Collections.nCopies(10, ".")));
        final MatrixWidget board = new MatrixWidget(face);
        assertEquals(10, board.rows(), "ten rows");
        assertEquals(10, board.cols(), "ten columns");
        final String[] frame = strippedFrame(board);
        assertEquals(12, frame.length, "ten face rows between two border rows:\n" + String.join("\n", frame));
        assertEquals(12, Highlighter.visualLength(frame[0]), "ten cells between two border columns");
    }

    @Test
    public void shouldDrawAGridStoredLazilyAsAnAuto() {
        // grid=>!*g stores an AUTO, not the grid.  A raw field read hands back the auto and the
        // board draws nothing (the caller has to force it); at() resolves it, which is the read
        // this widget owes its grid.
        ObjmtronSerializer.parse("g -> [['.','@'],['#','.']]").apply();
        final MatrixWidget board = (MatrixWidget) ObjmtronSerializer.parse("matrix_widget::[grid=>!*g]").apply();
        assertEquals(2, board.rows(), "a lazily stored grid resolves to its rows");
        assertEquals(2, board.cols(), "and its columns");
        assertEquals("@", board.cell(0, 1), "cell (0,1) reads through the resolved grid");
        assertTrue(board.format().contains("@"), "and the board draws it: " + board.format());
    }

    @Test
    public void shouldFollowAGridStoredAsItsOwnReferent() {
        // the grid can live at its OWN vid and the widget is a view over it: at() resolves the
        // referent on every read, so the board is mutated by writing the referent — never
        // matrix_widget/grid, and never through this instance.  (A live referent must be an
        // ABSOLUTE uri: a relative one is a variable frame on the writing thread, while a render
        // pass runs on the surface's render thread.  Both sides are this thread here, which is why
        // the relative spelling passes.)
        ObjmtronSerializer.parse("board_grid -> [['.','.'],['.','.']]").apply();
        final MatrixWidget board = (MatrixWidget) ObjmtronSerializer.parse("matrix_widget::[grid=>!*board_grid]").apply();
        assertEquals(".", board.cell(1, 1), "the board starts blank");
        Machine.write(f("board_grid/1/1"), str("@"));      // mutate the REFERENT (what >>= on that path does)
        assertEquals("@", board.cell(1, 1), "the next read resolves the referent and sees the write");
        assertTrue(board.format().contains("@"), "and the board draws it: " + board.format());
    }

    // ── helpers ────────────────────────────────────────────────────

    /** The widget's frame with all Graphitty markup stripped, one entry per drawn line. */
    private static String[] strippedFrame(final MatrixWidget matrix) {
        return java.util.Arrays.stream(matrix.format().split("\n", -1))
                .map(Highlighter::unformat)
                .toArray(String[]::new);
    }
}
