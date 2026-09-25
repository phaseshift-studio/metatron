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
import studio.phaseshift.metatron.isa.AbstractWidgetTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.ui.uiInstSet;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A grid of embedded widgets: each cell is a <em>runnable</em> widget (a rec carrying its type's
 * {@code tid}), the grid lays the cells out and manages their geometry, and pointer clicks are
 * handed to the cell under them.  The whole state lives in the rec's {@code grid} key, so every
 * assertion here reads back through the rec and never trusts a field.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class GridWidgetTest extends AbstractWidgetTest {

    private static final fURI TID = f("/m/mach/ui/widget/grid_widget");

    public GridWidgetTest() {
        super(uiInstSet::new);
    }

    @Override
    protected Object sampleWidget() {
        return new GridWidget(2, 1)
                .cell(0, 0, new AccordionWidget("round", "trip"))
                .cell(1, 0, new AccordionWidget("trip", "back"));
    }

    @Test
    public void shouldRenderEveryCellOfAOneColumnGrid() {
        final GridWidget grid = new GridWidget(3, 1)
                .cell(0, 0, new AccordionWidget("alpha", "first body\nline two"))
                .cell(1, 0, new AccordionWidget("beta"))
                .cell(2, 0, new AccordionWidget("gamma"));
        final String rendered = grid.format();
        assertTrue(rendered.contains("alpha"), "first cell title in: " + rendered);
        assertTrue(rendered.contains("beta"), "second cell title in: " + rendered);
        assertTrue(rendered.contains("gamma"), "third cell title in: " + rendered);
        assertTrue(rendered.contains("first body"), "an expanded cell shows its body in: " + rendered);
    }

    @Test
    public void shouldKeepACellRunnableAndNotAString() {
        final GridWidget grid = new GridWidget(2, 1)
                .cell(0, 0, new AccordionWidget("one"))
                .cell(1, 0, new AccordionWidget("two"));
        assertTrue(grid.cell(0, 0) instanceof AccordionWidget,
                "cell (0,0) must resolve to a live widget, not a captured string: " + grid.cell(0, 0));
        assertTrue(grid.cell(1, 0) instanceof AccordionWidget,
                "cell (1,0) must resolve to a live widget: " + grid.cell(1, 0));
    }

    @Test
    public void shouldRenderTwoGridsOverOneRecIdentically() {
        final GridWidget source = new GridWidget(1, 1).cell(0, 0, new AccordionWidget("notes", "one\ntwo"));
        ((AccordionWidget) source.cell(0, 0)).expand();
        final Map<Obj, Obj> body = source.jvm();
        final GridWidget copyA = new GridWidget(body, TID, null);
        final GridWidget copyB = new GridWidget(body, TID, null);
        assertEquals(copyA.format(), copyB.format(),
                "two grids over the same rec must render identically (rec parity)");
    }

    @Test
    public void shouldReadAToggledCellBackThroughTheRec() {
        final GridWidget grid = new GridWidget(1, 1).cell(0, 0, new AccordionWidget("notes", "one\ntwo\nthree"));
        ((AccordionWidget) grid.cell(0, 0)).expand();
        assertTrue(grid.format().contains("one"), "an expanded cell shows its body: " + grid.format());

        ((AccordionWidget) grid.cell(0, 0)).collapse();
        final GridWidget again = new GridWidget(grid.jvm(), TID, null);
        final String folded = again.format();
        assertFalse(folded.contains("one"), "a folded cell draws only its header: " + folded);
        assertTrue(folded.contains("[+]"), "a folded header carries the [+] toggle: " + folded);
    }

    @Test
    public void shouldDispatchAClickToTheCellUnderThePointer() {
        final GridWidget grid = new GridWidget(2, 1)
                .cell(0, 0, new AccordionWidget("top", "t1"))
                .cell(1, 0, new AccordionWidget("bottom"));
        grid.format();   // lay out — each cell's box (top/left/width/height) is written

        // "top" is 3 code units, so its toggle glyph sits at cols 3+3 .. 3+3+3
        assertTrue(grid.onClick(0, 6), "a click on the first cell's toggle should be consumed by that cell");
        assertFalse(grid.onClick(0, 0), "a click away from the toggle should fall through the grid");
    }

    @Test
    public void shouldRecordEachCellAtTheRowWhereItActuallyDraws() {
        final GridWidget grid = new GridWidget(2, 1)
                .cell(0, 0, new AccordionWidget("alpha", "aa"))
                .cell(1, 0, new AccordionWidget("beta", "bb"));
        final String canvasText = grid.format();   // lays out, writing each cell's top/left/width/height
        final String[] canvas = canvasText.split("\n", -1);

        // the second cell's header is the first rendered line containing "beta"
        int drawn = -1;
        for (int i = 0; i < canvas.length; i++)
            if (canvas[i].contains("beta")) {
                drawn = i;
                break;
            }
        assertTrue(drawn >= 0, "the second cell's header must appear on the canvas:\n" + canvasText);
        final int recorded = grid.cell(1, 0).getStyle().top();
        assertEquals(recorded, drawn,
                "cell (1,0)'s recorded top (" + recorded + ") must equal the canvas row it actually draws on ("
                        + drawn + "), or a click on its header misses it:\n" + canvasText);
        // and a click on that header's toggle [at the recorded row] is consumed
        final int toggleCol = 3 + 4;   // "beta" is 4 code units
        assertTrue(grid.onClick(drawn, toggleCol), "a click on the second cell's toggle must be consumed");
    }

    @Test
    public void shouldReflowCellsToFitTheGridsWidth() {
        final GridWidget grid = new GridWidget(1, 1)
                .cell(0, 0, new AccordionWidget("wrap", "a considerably long body line that should reflow when the grid is narrow"));
        grid.style().width(24).applyStyle();
        final String rendered = grid.format();
        final int longest = Arrays.stream(rendered.split("\n", -1)).mapToInt(Highlighter::visualLength).max().orElse(0);
        assertTrue(longest <= 24,
                "the cells must reflow to the grid width; the longest rendered line was " + longest + ":\n" + rendered);
    }

    @Test
    public void shouldRenderAPanelBodyAsACell() {
        final GridWidget grid = new GridWidget(1, 1)
                .cell(0, 0, new PanelWidget("heading", "the cell text lives in a panel body"));
        final String rendered = grid.format();
        assertTrue(rendered.contains("heading"), "the panel title renders as a cell: " + rendered);
        assertTrue(rendered.contains("the cell text lives in a panel body"),
                "the panel body renders as a cell: " + rendered);
    }

    @Test
    public void shouldRenderIdempotently() {
        final GridWidget grid = new GridWidget(2, 2)
                .cell(0, 0, new AccordionWidget("a")).cell(0, 1, new AccordionWidget("b"))
                .cell(1, 0, new AccordionWidget("c")).cell(1, 1, new AccordionWidget("d"));
        assertEquals(grid.format(), grid.format(), "formatting the same grid twice must render the same canvas");
    }

    // ── dynamic: grow, ragged, shrink ─────────────────────────────

    @Test
    public void shouldGrowAGridByAddingACellInANewColumn() {
        // a one-column grid; adding a cell that reaches column 1 in row 0 made the rows ragged,
        // and the render walked off the shorter row — the index-out-of-bounds you hit
        final GridWidget grid = new GridWidget(2, 1)
                .cell(0, 0, new AccordionWidget("one"))
                .cell(1, 0, new AccordionWidget("two"));
        grid.cell(0, 1, new AccordionWidget("three"));   // grow to 2 columns (row 1 is still 1 wide)
        final String rendered = grid.format();
        assertTrue(rendered.contains("one"), "first cell in: " + rendered);
        assertTrue(rendered.contains("three"), "the newly added cell in: " + rendered);
        // and keep growing down a brand-new row, past everything that existed
        grid.cell(3, 0, new AccordionWidget("deep"));
        assertTrue(grid.format().contains("deep"), "a cell added to a new row renders: " + grid.format());
    }

    @Test
    public void shouldRenderARaggedGridWithoutWalkingOffAShortRow() {
        // rows of unequal length, the way an mtron lst-of-lsts naturally comes in
        final Obj wide = lst(List.of((Obj) new AccordionWidget("wide", "aa\nbb"), (Obj) new AccordionWidget("x")));
        final Obj narrow = lst(List.of((Obj) new AccordionWidget("narrow", "cc")));
        final Obj grid = lst(List.of(wide, narrow));
        final GridWidget gw = new GridWidget(mutableMap(uri("grid"), grid), TID, null);
        final String rendered = gw.format();   // used to throw IndexOutOfBoundsException here
        assertTrue(rendered.contains("wide"), "a wide row renders: " + rendered);
        assertTrue(rendered.contains("narrow"), "a short row renders: " + rendered);
    }

    @Test
    public void shouldDropACellAndRenderTheRest() {
        final GridWidget grid = new GridWidget(2, 1)
                .cell(0, 0, new AccordionWidget("keep"))
                .cell(1, 0, new AccordionWidget("drop"));
        grid.cell(1, 0, null);   // remove the second cell
        final String rendered = grid.format();
        assertTrue(rendered.contains("keep"), "the surviving cell renders: " + rendered);
        assertFalse(rendered.contains("drop"), "the removed cell is gone from: " + rendered);
    }

    @Test
    public void shouldReAddAndGrowARowAfterRemovingACell() {
        final GridWidget grid = new GridWidget(2, 1)
                .cell(0, 0, new AccordionWidget("keep"))
                .cell(1, 0, new AccordionWidget("drop"));
        grid.cell(1, 0, null);                           // remove the row's only cell
        grid.cell(1, 0, new AccordionWidget("reborn"));  // re-add into the same spot
        grid.cell(1, 1, new AccordionWidget("extra"));   // and grow that row to a 2nd column (row 0 is still 1 wide)
        final String rendered = grid.format();           // used to walk off the short row here
        assertTrue(rendered.contains("keep"), "the surviving original cell stays: " + rendered);
        assertTrue(rendered.contains("reborn"), "the re-added cell renders: " + rendered);
        assertTrue(rendered.contains("extra"), "the grown 2nd column renders: " + rendered);
        assertFalse(rendered.contains("drop"), "the old removed cell is gone: " + rendered);
        assertTrue(grid.cell(1, 1) instanceof AccordionWidget, "the grown cell is a live widget");
    }
}
