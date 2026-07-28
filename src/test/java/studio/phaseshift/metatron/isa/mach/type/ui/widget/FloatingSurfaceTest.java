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

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FloatingSurfaceTest extends AbstractMetatronTest {

    private static final int TERM_HEIGHT = 40;
    private static final int TERM_WIDTH = 120;

    // ── Anchor.parse ───────────────────────────────────────────────

    @Test
    public void shouldParseFullAnchorNames() {
        assertEquals(FloatingSurface.Anchor.TOP_LEFT, FloatingSurface.Anchor.parse("top_left"));
        assertEquals(FloatingSurface.Anchor.TOP_RIGHT, FloatingSurface.Anchor.parse("top_right"));
        assertEquals(FloatingSurface.Anchor.BOTTOM_LEFT, FloatingSurface.Anchor.parse("bottom_left"));
        assertEquals(FloatingSurface.Anchor.BOTTOM_RIGHT, FloatingSurface.Anchor.parse("bottom_right"));
    }

    @Test
    public void shouldParseShortAnchorNames() {
        assertEquals(FloatingSurface.Anchor.TOP_LEFT, FloatingSurface.Anchor.parse("tl"));
        assertEquals(FloatingSurface.Anchor.TOP_RIGHT, FloatingSurface.Anchor.parse("tr"));
        assertEquals(FloatingSurface.Anchor.BOTTOM_LEFT, FloatingSurface.Anchor.parse("bl"));
        assertEquals(FloatingSurface.Anchor.BOTTOM_RIGHT, FloatingSurface.Anchor.parse("br"));
    }

    @Test
    public void shouldParseCaseInsensitive() {
        assertEquals(FloatingSurface.Anchor.TOP_LEFT, FloatingSurface.Anchor.parse("Top_Left"));
        assertEquals(FloatingSurface.Anchor.BOTTOM_RIGHT, FloatingSurface.Anchor.parse("BR"));
    }

    @Test
    public void shouldDefaultToTopRightOnNull() {
        assertEquals(FloatingSurface.Anchor.TOP_RIGHT, FloatingSurface.Anchor.parse(null));
    }

    @Test
    public void shouldDefaultToTopRightOnEmpty() {
        assertEquals(FloatingSurface.Anchor.TOP_RIGHT, FloatingSurface.Anchor.parse(""));
    }

    @Test
    public void shouldDefaultToTopRightOnUnknown() {
        assertEquals(FloatingSurface.Anchor.TOP_RIGHT, FloatingSurface.Anchor.parse("middle_center"));
    }

    // ── Slot.resolve — top anchors (lastRow is fixed) ──────────────

    @Test
    public void shouldResolveTopLeftFlush() {
        final FloatingSurface.Slot slot = FloatingSurface.Slot.anchored(
                FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        slot.resolve(TERM_HEIGHT, TERM_WIDTH, 5);
        assertEquals(2, slot.lastRow, "top-left: lastRow should be 2 (fixed, just below top edge)");
        assertEquals(1, slot.lastCol, "top-left: lastCol should be 1 (flush left)");
    }

    @Test
    public void shouldResolveTopRightFlush() {
        final FloatingSurface.Slot slot = FloatingSurface.Slot.anchored(
                FloatingSurface.Anchor.TOP_RIGHT, 40, 0, 0);
        slot.resolve(TERM_HEIGHT, TERM_WIDTH, 5);
        assertEquals(2, slot.lastRow, "top-right: lastRow should be 2 (fixed)");
        assertEquals(TERM_WIDTH - 40 + 1, slot.lastCol,
                "top-right: lastCol should be pinned to right edge");
    }

    @Test
    public void shouldResolveTopLeftWithTopOffset() {
        final FloatingSurface.Slot slot = FloatingSurface.Slot.anchored(
                FloatingSurface.Anchor.TOP_LEFT, 40, 3, 0);
        slot.resolve(TERM_HEIGHT, TERM_WIDTH, 5);
        assertEquals(5, slot.lastRow, "top-left with top=3: lastRow = 2 + 3 = 5 (pushed down)");
        assertEquals(1, slot.lastCol);
    }

    @Test
    public void shouldResolveTopLeftWithNegativeTopOffset() {
        final FloatingSurface.Slot slot = FloatingSurface.Slot.anchored(
                FloatingSurface.Anchor.TOP_LEFT, 40, -1, 0);
        slot.resolve(TERM_HEIGHT, TERM_WIDTH, 5);
        assertEquals(1, slot.lastRow, "top-left with top=-1: lastRow = 2 + (-1) = 1 (pulled up)");
    }

    // ── Slot.resolve — bottom anchors, height-dependent lastRow ────

    @Test
    public void shouldResolveBottomLeftFlushWithTallWidget() {
        final FloatingSurface.Slot slot = FloatingSurface.Slot.anchored(
                FloatingSurface.Anchor.BOTTOM_LEFT, 40, 0, 0);
        slot.resolve(TERM_HEIGHT, TERM_WIDTH, 10);
        // lastRow = 40 - 10 + 1 = 31 → widget occupies rows 31–40
        assertEquals(TERM_HEIGHT - 10 + 1, slot.lastRow,
                "bottom-left flush: widget bottom should be flush with terminal bottom");
        assertEquals(1, slot.lastCol);
    }

    @Test
    public void shouldResolveBottomLeftFlushWithShortWidget() {
        final FloatingSurface.Slot slot = FloatingSurface.Slot.anchored(
                FloatingSurface.Anchor.BOTTOM_LEFT, 40, 0, 0);
        slot.resolve(TERM_HEIGHT, TERM_WIDTH, 2);
        // lastRow = 40 - 2 + 1 = 39 → widget occupies rows 39–40
        assertEquals(TERM_HEIGHT - 2 + 1, slot.lastRow,
                "bottom-left flush: short widget stays flush with bottom");
        assertEquals(1, slot.lastCol);
    }

    @Test
    public void shouldResolveBottomRightFlush() {
        final FloatingSurface.Slot slot = FloatingSurface.Slot.anchored(
                FloatingSurface.Anchor.BOTTOM_RIGHT, 40, 0, 0);
        slot.resolve(TERM_HEIGHT, TERM_WIDTH, 5);
        assertEquals(TERM_HEIGHT - 5 + 1, slot.lastRow, "bottom-right: flush with bottom");
        assertEquals(TERM_WIDTH - 40 + 1, slot.lastCol, "bottom-right: flush with right");
    }

    @Test
    public void shouldResolveBottomLeftClampedToTop() {
        // Widget taller than terminal → lastRow clamped to 1
        final FloatingSurface.Slot slot = FloatingSurface.Slot.anchored(
                FloatingSurface.Anchor.BOTTOM_LEFT, 40, 0, 0);
        slot.resolve(TERM_HEIGHT, TERM_WIDTH, 100);
        assertEquals(1, slot.lastRow, "bottom-left: widget taller than terminal clamps to row 1");
    }

    // For bottom-anchored widgets, top acts as a margin from the bottom edge
    // (like CSS 'bottom'): positive top = away from bottom = UP, negative = past bottom = DOWN.
    // Left always measures from the left edge regardless of anchor.

    @Test
    public void shouldPushBottomWidgetUpWithPositiveTop() {
        final FloatingSurface.Slot slot = FloatingSurface.Slot.anchored(
                FloatingSurface.Anchor.BOTTOM_LEFT, 40, 3, 0);
        slot.resolve(TERM_HEIGHT, TERM_WIDTH, 5);
        // lastRow = 40 - 5 + 1 - 3 = 33 (3-row margin from bottom)
        assertEquals(TERM_HEIGHT - 5 + 1 - 3, slot.lastRow,
                "bottom-left top=3: positive top pushes UP (margin from bottom edge)");
    }

    @Test
    public void shouldPushBottomWidgetDownWithNegativeTop() {
        final FloatingSurface.Slot slot = FloatingSurface.Slot.anchored(
                FloatingSurface.Anchor.BOTTOM_LEFT, 40, -3, 0);
        slot.resolve(TERM_HEIGHT, TERM_WIDTH, 5);
        // lastRow = 40 - 5 + 1 - (-3) = 39 (extends below bottom)
        assertEquals(TERM_HEIGHT - 5 + 1 + 3, slot.lastRow,
                "bottom-left top=-3: negative top pushes DOWN (past bottom edge)");
    }

    @Test
    public void shouldHandleBottomRightWithBothOffsets() {
        final FloatingSurface.Slot slot = FloatingSurface.Slot.anchored(
                FloatingSurface.Anchor.BOTTOM_RIGHT, 40, 2, 5);
        slot.resolve(TERM_HEIGHT, TERM_WIDTH, 6);
        // lastRow = 40 - 6 + 1 - 2 = 33 (2-row margin from bottom)
        assertEquals(TERM_HEIGHT - 6 + 1 - 2, slot.lastRow,
                "bottom-right top=2: 2-row margin from bottom edge");
        // lastCol = 120 - 40 + 1 + 5 = 86 (pushed right by 5)
        assertEquals(TERM_WIDTH - 40 + 1 + 5, slot.lastCol,
                "bottom-right left=5: pushed right (away from left edge)");
    }

    @Test
    public void shouldShiftBottomWidgetUpWhenHeightIncreases() {
        // When a bottom-anchored widget expands, lastRow must decrease
        // so the bottom edge stays flush (until offset).
        final FloatingSurface.Slot slot = FloatingSurface.Slot.anchored(
                FloatingSurface.Anchor.BOTTOM_LEFT, 40, 0, 0);
        slot.resolve(TERM_HEIGHT, TERM_WIDTH, 2);   // collapsed, 2 rows
        final int collapsedRow = slot.lastRow;       // 39
        slot.resolve(TERM_HEIGHT, TERM_WIDTH, 10);  // expanded, 10 rows
        final int expandedRow = slot.lastRow;        // 31
        assertTrue(expandedRow < collapsedRow,
                "bottom-left: expanded widget should start at a smaller row number (higher on screen)");
        assertEquals(TERM_HEIGHT - 10 + 1, expandedRow,
                "bottom-left expanded: should be flush with terminal bottom");
    }

    // ── Fixed (non-anchored) slots ─────────────────────────────────

    @Test
    public void shouldKeepFixedSlotPositionOnResolve() {
        final FloatingSurface.Slot slot = FloatingSurface.Slot.fixed(10, 20);
        slot.resolve(TERM_HEIGHT, TERM_WIDTH, 99);
        assertEquals(10, slot.lastRow, "fixed slot: lastRow should never change");
        assertEquals(20, slot.lastCol, "fixed slot: lastCol should never change");
    }

    @Test
    public void shouldIdentifyAnchoredVsFixed() {
        final FloatingSurface.Slot anchored = FloatingSurface.Slot.anchored(
                FloatingSurface.Anchor.TOP_RIGHT, 40, 0, 0);
        final FloatingSurface.Slot fixed = FloatingSurface.Slot.fixed(5, 10);
        assertTrue(anchored.isAnchored());
        assertFalse(fixed.isAnchored());
    }

    // ── FloatingSurface API (with dumb terminal) ────────────────────

    private Terminal terminal;
    private FloatingSurface surface;

    @BeforeEach
    public void setUp() throws IOException {
        terminal = TerminalBuilder.builder().dumb(true).build();
        surface = new FloatingSurface(terminal);
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (terminal != null) {
            terminal.close();
        }
    }

    @Test
    public void shouldBeEmptyInitially() {
        assertTrue(surface.isEmpty());
    }

    @Test
    public void shouldAddAndContainWidget() {
        final AccordionWidget w = new AccordionWidget("Test");
        surface.add(w, FloatingSurface.Anchor.TOP_RIGHT, 40);
        assertTrue(surface.contains(w));
        assertFalse(surface.isEmpty());
    }

    @Test
    public void shouldRemoveWidget() {
        final AccordionWidget w = new AccordionWidget("Test");
        surface.add(w, FloatingSurface.Anchor.TOP_RIGHT, 40);
        surface.remove(w);
        assertFalse(surface.contains(w));
        assertTrue(surface.isEmpty());
    }

    @Test
    public void shouldClearAllWidgets() {
        final AccordionWidget a = new AccordionWidget("A");
        final AccordionWidget b = new AccordionWidget("B");
        surface.add(a, FloatingSurface.Anchor.TOP_LEFT, 30);
        surface.add(b, FloatingSurface.Anchor.BOTTOM_RIGHT, 40);
        assertFalse(surface.isEmpty());
        surface.clear();
        assertTrue(surface.isEmpty());
        assertFalse(surface.contains(a));
        assertFalse(surface.contains(b));
    }

    @Test
    public void shouldUpdateFixedPositionOnReAdd() {
        final AccordionWidget w = new AccordionWidget("Test");
        surface.add(w, 5, 10);
        surface.add(w, 7, 12);
        // Can't directly inspect Slot.lastRow/lastCol from the public API,
        // but the widget should still be contained and the surface non-empty.
        assertTrue(surface.contains(w));
        assertFalse(surface.isEmpty());
    }

    @Test
    public void shouldNotContainUnaddedWidget() {
        final AccordionWidget w = new AccordionWidget("Ghost");
        assertFalse(surface.contains(w));
    }

    @Test
    public void shouldRenderEmptySurfaceWithoutError() {
        // render() on an empty surface should be a no-op
        assertDoesNotThrow(() -> surface.render());
    }

    @Test
    public void shouldRemoveNonexistentWidgetGracefully() {
        final AccordionWidget w = new AccordionWidget("Ghost");
        assertDoesNotThrow(() -> surface.remove(w));
    }
}
