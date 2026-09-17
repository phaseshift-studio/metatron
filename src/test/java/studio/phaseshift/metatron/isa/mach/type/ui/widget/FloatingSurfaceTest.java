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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.io.IOException;
import java.util.List;

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

    @Test
    public void shouldParseMiddleAnchor() {
        assertEquals(FloatingSurface.Anchor.MIDDLE, FloatingSurface.Anchor.parse("middle"));
        assertEquals(FloatingSurface.Anchor.MIDDLE, FloatingSurface.Anchor.parse("m"));
        assertEquals(FloatingSurface.Anchor.MIDDLE, FloatingSurface.Anchor.parse("MIDDLE"));
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
        // negative top pushes DOWN past the bottom edge — pinned so the box's
        // bottom edge stops at the terminal's last row (40): lastRow = 40 - 5 + 1 = 36
        assertEquals(TERM_HEIGHT - 5 + 1, slot.lastRow,
                "bottom-left top=-3: negative top pushes DOWN, pinned at the bottom edge");
    }

    @Test
    public void shouldHandleBottomRightWithBothOffsets() {
        final FloatingSurface.Slot slot = FloatingSurface.Slot.anchored(
                FloatingSurface.Anchor.BOTTOM_RIGHT, 40, 2, 5);
        slot.resolve(TERM_HEIGHT, TERM_WIDTH, 6);
        // lastRow = 40 - 6 + 1 - 2 = 33 (2-row margin from bottom)
        assertEquals(TERM_HEIGHT - 6 + 1 - 2, slot.lastRow,
                "bottom-right top=2: 2-row margin from bottom edge");
        // left=5 pushes right past the right edge — pinned so the box's right
        // edge stops at the terminal: lastCol = 120 - 40 + 1 = 81
        assertEquals(TERM_WIDTH - 40 + 1, slot.lastCol,
                "bottom-right left=5: pushed right, pinned at the right edge");
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

    // ── Slot.resolve — middle anchor, vertically+horizontally centered ──

    @Test
    public void shouldResolveMiddleCentered() {
        final FloatingSurface.Slot slot = FloatingSurface.Slot.anchored(
                FloatingSurface.Anchor.MIDDLE, 40, 0, 0);
        slot.resolve(TERM_HEIGHT, TERM_WIDTH, 10);
        // lastRow = (40 - 10) / 2 + 1 = 16 → rows 16–25, widget center = 20.5
        assertEquals((TERM_HEIGHT - 10) / 2 + 1, slot.lastRow,
                "middle: lastRow should center the widget vertically");
        // lastCol = (120 - 40) / 2 = 40 → columns 40–79, widget center = 59.5
        assertEquals((TERM_WIDTH - 40) / 2, slot.lastCol,
                "middle: lastCol should center the widget horizontally");
    }

    @Test
    public void shouldResolveMiddleWithOffsets() {
        final FloatingSurface.Slot slot = FloatingSurface.Slot.anchored(
                FloatingSurface.Anchor.MIDDLE, 40, 3, 5);
        slot.resolve(TERM_HEIGHT, TERM_WIDTH, 10);
        assertEquals((TERM_HEIGHT - 10) / 2 + 1 + 3, slot.lastRow,
                "middle top=3: positive top pushes DOWN (away from center, top edge)");
        assertEquals((TERM_WIDTH - 40) / 2 + 5, slot.lastCol,
                "middle left=5: pushed right (away from left edge)");
    }

    @Test
    public void shouldClampMiddleToTopForTallWidget() {
        // Widget taller than terminal → lastRow clamped to 1
        final FloatingSurface.Slot slot = FloatingSurface.Slot.anchored(
                FloatingSurface.Anchor.MIDDLE, 40, 0, 0);
        slot.resolve(TERM_HEIGHT, TERM_WIDTH, 100);
        assertEquals(1, slot.lastRow, "middle: widget taller than terminal clamps to row 1");
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

    // ── Anchored widget render ──────────────────────────────────────

    @Test
    public void surfaceRenderResetsColorBeforeDrawing() throws Exception {
        // The surface's erase/buffer-zone spaces must never inherit a
        // background left active by prior console output (e.g. the status
        // line's trailing bg) — otherwise they render as a stray colored
        // blank line above floating widgets.  renderInternal must reset SGR
        // right after saving the cursor.
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final Terminal diagTerm = TerminalBuilder.builder().dumb(true)
                .size(new org.jline.terminal.Size(40, 120))
                .streams(new java.io.ByteArrayInputStream(new byte[0]), out).build();
        final FloatingSurface diagSurface = new FloatingSurface(diagTerm);
        final AccordionWidget accordion = new AccordionWidget("thoughts");
        accordion.style().floatAt(FloatingSurface.Anchor.TOP_LEFT, 80, 3, 0).applyStyle();
        diagSurface.add(accordion, FloatingSurface.Anchor.TOP_LEFT, 80, 3, 0);
        diagSurface.render();
        Thread.sleep(300);
        final String rendered = out.toString();
        assertTrue(rendered.startsWith("\033[s\033[m"),
                "render should reset SGR right after save-cursor so buffer-zone spaces are never painted: "
                        + rendered.replace("\033", "<ESC>"));
        diagTerm.close();
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

    // ── widgetKey / focus state ────────────────────────────────────

    @Test
    public void shouldDeriveKeyFromVidWhenPresent() {
        final AccordionWidget w = new AccordionWidget(new java.util.HashMap<>(),
                studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_ACCORDION_TID,
                studio.phaseshift.metatron.furi.fURI.Singleton.f("my_think_accordion"));
        assertEquals("my_think_accordion", FloatingSurface.widgetKey(w));
    }

    @Test
    public void shouldFallBackToIdentityKeyWithoutVid() {
        final AccordionWidget w = new AccordionWidget("no-vid");
        final String key = FloatingSurface.widgetKey(w);
        assertTrue(key.startsWith("oid#"), "vid-less widget should get an identity key: " + key);
    }

    @Test
    public void shouldRoundTripFocusKey() {
        assertNull(surface.focusKey());
        surface.setFocusKey("think_widget");
        assertEquals("think_widget", surface.focusKey());
        surface.setFocusKey(null);
        assertNull(surface.focusKey());
    }

    // ── widgets() deterministic focus order ─────────────────────────

    @Test
    public void shouldOrderWidgetsByAnchorThenOffsetsThenWidth() {
        final AccordionWidget topLeft = new AccordionWidget("top-left");
        final AccordionWidget topRightNear = new AccordionWidget("top-right-near");
        final AccordionWidget topRightFar = new AccordionWidget("top-right-far");
        final AccordionWidget bottomRight = new AccordionWidget("bottom-right");
        surface.add(topLeft, FloatingSurface.Anchor.TOP_LEFT, 30, 0, 0);
        surface.add(topRightNear, FloatingSurface.Anchor.TOP_RIGHT, 40, 0, 0);
        surface.add(topRightFar, FloatingSurface.Anchor.TOP_RIGHT, 20, 5, 0);
        surface.add(bottomRight, FloatingSurface.Anchor.BOTTOM_RIGHT, 50, 0, 0);

        final List<Widget<?>> ordered = surface.widgets();
        assertEquals(4, ordered.size(), "snapshot should hold every pinned widget");
        assertSame(topLeft, ordered.get(0), "top row before bottom row (anchor reading order)");
        assertSame(topRightNear, ordered.get(1), "smaller top offset first within the same anchor");
        assertSame(topRightFar, ordered.get(2), "larger top offset after within the same anchor");
        assertSame(bottomRight, ordered.get(3), "bottom row last");
    }

    @Test
    public void shouldOrderWidgetsByZIndexFirst() {
        final AccordionWidget high = new AccordionWidget("high");
        final AccordionWidget low = new AccordionWidget("low");
        final AccordionWidget middle = new AccordionWidget("middle");
        surface.add(high, FloatingSurface.Anchor.TOP_LEFT, 30, 0, 0);
        surface.add(low, FloatingSurface.Anchor.BOTTOM_RIGHT, 50, 0, 0);
        assertEquals(List.of(high, low), surface.widgets(),
                "equal z-index keeps anchor reading order (TOP_LEFT before BOTTOM_RIGHT)");
        // Raise the TOP_LEFT widget's z-index: it should jump to the end of
        // the cycle (highest z draws on top) — the other two keep anchor order.
        high.style().zIndex(Integer.MAX_VALUE).applyStyle();
        surface.add(middle, FloatingSurface.Anchor.TOP_RIGHT, 30, 0, 0);
        assertEquals(List.of(middle, low, high), surface.widgets(),
                "higher z-index sorts last; within equal z, anchor reading order (TOP_RIGHT before BOTTOM_RIGHT)");
    }

    // ── nudge (resize) ─────────────────────────────────────────────

    @Test
    public void shouldNudgeWidthAndClampToLowerBound() {
        final AccordionWidget w = new AccordionWidget("sizing");
        surface.add(w, FloatingSurface.Anchor.TOP_RIGHT, 40, 0, 0);
        assertTrue(surface.nudge(w, 8, 0), "nudging a pinned widget should report success");
        assertEquals(48, surface.slotOf(w).targetWidth);
        assertEquals(48, w.getStyle().width(),
                "the style width should be updated for content-shaping widgets");
        surface.nudge(w, -999, 0);
        assertEquals(10, surface.slotOf(w).targetWidth,
                "width shrinks to the 10-column floor, never below");
    }

    @Test
    public void shouldNudgeHeightCapFromNaturalAndClampToLowerBound() {
        final AccordionWidget w = new AccordionWidget("rows", "line one\nline two\nline three");
        w.expand();
        surface.add(w, FloatingSurface.Anchor.BOTTOM_LEFT, 40, 0, 0);
        assertEquals(5, w.height(), "three body lines + title + bottom border = 5");
        assertTrue(surface.nudge(w, 0, 2));
        assertEquals(5 + 2, surface.slotOf(w).heightCap);
        surface.nudge(w, 0, -999);
        assertEquals(3, surface.slotOf(w).heightCap, "height clamp floor is 3 rows");
    }

    @Test
    public void shouldRefuseToNudgeUnpinnedWidget() {
        final AccordionWidget ghost = new AccordionWidget("ghost");
        assertFalse(surface.nudge(ghost, 4, 4));
    }

    // ── durable geometry across re-float (.display() re-hydration) ──

    @Test
    public void shouldKeepResizedGeometryWhenWidgetReFloats() {
        final AccordionWidget original = new AccordionWidget("thoughts", "body content");
        original.expand();
        assertEquals(3, original.height(), "1 body line + title bar + bottom border = 3 rows");
        surface.add(original, FloatingSurface.Anchor.BOTTOM_LEFT, 40, 0, 0);
        surface.nudge(original, 8, 0);   // 40 → 48
        surface.nudge(original, 0, 3);   // natural height (3) + 3 = 6

        // A .display() update re-hydrates the widget as a FRESH instance and
        // re-floats it at its stored (un-resized) style — the surface must
        // keep the geometry it already owns.
        final AccordionWidget rehydrated = new AccordionWidget("thoughts", "body content");
        rehydrated.expand();
        surface.add(rehydrated, FloatingSurface.Anchor.BOTTOM_LEFT, 40, 0, 0);

        assertFalse(surface.contains(original), "the stale instance should be replaced");
        final FloatingSurface.Slot slot = surface.slotOf(rehydrated);
        assertNotNull(slot);
        assertEquals(48, slot.targetWidth, "resized width must survive the re-float");
        assertEquals(3 + 3, slot.heightCap, "resized height cap must survive the re-float");
    }

    // ── focus marker render ────────────────────────────────────────

    @Test
    public void shouldRenderFocusMarkerOnlyForFocusedWidget() throws Exception {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final Terminal term = TerminalBuilder.builder().dumb(true)
                .size(new org.jline.terminal.Size(40, 120))
                .streams(new java.io.ByteArrayInputStream(new byte[0]), out).build();
        final FloatingSurface diag = new FloatingSurface(term);
        final AccordionWidget left = new AccordionWidget("left");
        final AccordionWidget right = new AccordionWidget("right");
        diag.add(left, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        diag.add(right, FloatingSurface.Anchor.TOP_RIGHT, 40, 0, 0);

        diag.setFocusKey(FloatingSurface.widgetKey(left));
        diag.render();
        Thread.sleep(300);
        final String focusedPass = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(focusedPass.contains("▶"),
                "the pass with a focused widget must draw the focus marker: "
                        + focusedPass.replace("\033", "<ESC>"));

        diag.setFocusKey(null);
        diag.render();
        Thread.sleep(300);
        final String total = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        final String defocusedPass = total.substring(focusedPass.length());
        assertFalse(defocusedPass.contains("▶"),
                "the pass after defocus must blank the stale marker cell: "
                        + defocusedPass.replace("\033", "<ESC>"));
        term.close();
    }

    // ── focus durability across re-float (vid-less fresh instances) ──

    @Test
    public void shouldCarryFocusAcrossRefloatIntoFreshInstance() throws Exception {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final Terminal term = TerminalBuilder.builder().dumb(true)
                .size(new org.jline.terminal.Size(40, 120))
                .streams(new java.io.ByteArrayInputStream(new byte[0]), out).build();
        final FloatingSurface diag = new FloatingSurface(term);

        // first float — fresh AccordionWidget instance (no vid, like a
        // store re-hydration of a vid-less widget)
        final AccordionWidget first = new AccordionWidget("audit");
        diag.add(first, FloatingSurface.Anchor.BOTTOM_RIGHT, 65, 8, -2);
        final String firstKey = FloatingSurface.widgetKey(first);
        diag.setFocusKey(firstKey);
        diag.render();
        Thread.sleep(300);
        final String focusPass = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(1, countOccurrences(focusPass, "▶"),
                "marker present while the original instance is focused: "
                        + focusPass.replace("\033", "<ESC>"));

        // re-float — every .display() update builds a FRESH instance; the
        // old key must keep resolving to the newcomer (the live bug: focus
        // and resize died after the first update because the fresh
        // instance carried no vid and the lookup fell through)
        final AccordionWidget fresh = new AccordionWidget("audit");
        diag.add(fresh, FloatingSurface.Anchor.BOTTOM_RIGHT, 65, 8, -2);
        final String freshKey = FloatingSurface.widgetKey(fresh);
        assertNotEquals(firstKey, freshKey,
                "the fresh re-floated instance must have a different identity key");
        assertEquals(freshKey, diag.resolveKey(firstKey),
                "the old focus key must resolve through the re-float lineage to the fresh instance");

        diag.render();
        Thread.sleep(300);
        final String refloatedPass = out.toString(java.nio.charset.StandardCharsets.UTF_8)
                .substring(focusPass.length());
        assertEquals(1, countOccurrences(refloatedPass, "▶"),
                "focus must survive the re-float — the fresh instance is the focused one: "
                        + refloatedPass.replace("\033", "<ESC>"));
        // overpainting at the SAME cell is harmless (two coalesced passes);
        // the real stacking is a box landing on a DIFFERENT row
        final java.util.Set<String> boxPositions = new java.util.LinkedHashSet<>();
        final java.util.regex.Matcher box = java.util.regex.Pattern
                .compile("\\033\\[(\\d+);(\\d+)H(?:\\033\\[m)*┌")
                .matcher(refloatedPass);
        while (box.find())
            boxPositions.add(box.group(1) + ";" + box.group(2));
        assertEquals(1, boxPositions.size(),
                "the re-float must replace, not stack — every box copy lands on the same cell: "
                        + refloatedPass.replace("\033", "<ESC>"));
        term.close();
    }

    // ── drag: the chevron handle, the translation, and what it writes ─

    /** A 120-column by 40-row terminal (jline's Size takes columns first). */
    private static Terminal dragTerminal(final java.io.OutputStream out) throws IOException {
        return TerminalBuilder.builder().dumb(true)
                .size(new org.jline.terminal.Size(120, 40))
                .streams(new java.io.ByteArrayInputStream(new byte[0]), out).build();
    }

    /**
     * The drag is a translation, and that is the whole point of the chevron being the
     * handle: the cell the user grabs is the widget's own top-left corner, so the corner
     * lands wherever the pointer goes — for every anchor, top or bottom or middle.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "top_left", "top_middle", "top_right",
            "middle",
            "bottom_left", "bottom_middle", "bottom_right",
    })
    public void testDraggingMovesTheWidgetByThePointerDelta(final String anchorName) throws Exception {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final Terminal term = dragTerminal(out);
        final FloatingSurface surface_ = new FloatingSurface(term);
        final AccordionWidget widget = new AccordionWidget("drag me");
        final FloatingSurface.Anchor anchor = FloatingSurface.Anchor.parse(anchorName);
        surface_.add(widget, anchor, 40, 0, 0);
        surface_.renderNow();

        final FloatingSurface.Cell before = surface_.origin(widget);
        assertNotNull(before, anchorName + ": a pinned widget has a drawn origin");
        // move toward the middle, so the delta is in range for every anchor (a
        // bottom-anchored widget starts near the bottom, a top-anchored one near the top)
        final int dRow = before.row() <= term.getHeight() / 2 ? 3 : -3;
        final int dCol = before.col() <= term.getWidth() / 2 ? 5 : -5;
        assertTrue(surface_.placeAt(widget, before.row() + dRow, before.col() + dCol),
                anchorName + ": an anchored widget accepts a placement");
        surface_.renderNow();
        final FloatingSurface.Cell after = surface_.origin(widget);
        assertEquals(before.row() + dRow, after.row(), anchorName + ": the corner follows the pointer in rows");
        assertEquals(before.col() + dCol, after.col(), anchorName + ": the corner follows the pointer in columns");

        // and the offsets it stored are the ones that reproduce that cell
        final FloatingSurface.Placement placement = surface_.placement(widget);
        assertEquals(anchor, placement.anchor(), anchorName + ": the anchor is not changed by a drag");
        surface_.add(widget, anchor, 40, placement.top(), placement.left());
        surface_.renderNow();
        final FloatingSurface.Cell refloated = surface_.origin(widget);
        assertEquals(after.row(), refloated.row(), anchorName + ": re-floating at the dragged offsets stays put");
        assertEquals(after.col(), refloated.col(), anchorName + ": re-floating at the dragged offsets stays put");
        assertEquals(1, surface_.drawnWidgetCount(), anchorName + ": and replaces the slot instead of orphaning it");
        term.close();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "-100 % -100 % dragged off the top-left, the box is held at the corner",
            "9999 % 9999 % dragged past the bottom-right, the box is held fully on screen",
    }, delimiter = '%')
    public void testDraggingClampsSoTheHandleStaysReachable(final int row, final int col,
                                                            final String description) throws Exception {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final Terminal term = dragTerminal(out);
        final FloatingSurface surface_ = new FloatingSurface(term);
        final AccordionWidget widget = new AccordionWidget("hold me");
        surface_.add(widget, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        surface_.renderNow();

        assertTrue(surface_.placeAt(widget, row, col), description);
        surface_.renderNow();
        final FloatingSurface.Cell origin = surface_.origin(widget);
        final FloatingSurface.Placement placement = surface_.placement(widget);
        // the whole box stays on the terminal: the top-left is held so the box's
        // far (bottom-right) edge stops at the terminal edge, never past it
        assertEquals(Math.min(Math.max(1, row), Math.max(1, term.getHeight() - placement.height() + 1)),
                origin.row(), description + " (row)");
        assertEquals(Math.min(Math.max(1, col), Math.max(1, term.getWidth() - placement.width() + 1)),
                origin.col(), description + " (col)");
        term.close();
    }

    @Test
    public void testAWidgetOffersExactlyTwoHandles() throws Exception {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final Terminal term = dragTerminal(out);
        final FloatingSurface surface_ = new FloatingSurface(term);
        final AccordionWidget widget = new AccordionWidget("handle", "one\ntwo\nthree");
        surface_.add(widget, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        surface_.renderNow();

        // Scan the terminal rather than guess at the box: exactly two cells of a widget
        // are handles — its top-left (MOVE, the chevron) and its bottom-right (RESIZE,
        // the corner marker).  Everything else stays free for clicks and scrolling.
        final java.util.List<String> handles = new java.util.ArrayList<>();
        for (int row = 1; row <= term.getHeight(); row++)
            for (int col = 1; col <= term.getWidth(); col++) {
                final FloatingSurface.Handle handle = surface_.handleAt(widget, row, col);
                if (null != handle) handles.add(row + "," + col + ":" + handle);
            }
        assertEquals(2, handles.size(), "one move handle and one resize handle: " + handles);

        final FloatingSurface.Cell origin = surface_.origin(widget);
        assertEquals(origin.row() + "," + origin.col() + ":MOVE", handles.get(0),
                "the move handle is the widget's own top-left cell (the chevron)");
        assertTrue(handles.get(1).endsWith(":RESIZE"), "the other is the resize handle: " + handles);
        assertNull(surface_.handleAt(widget, origin.row(), origin.col() + 1),
                "the body is not a handle — clicks there keep working");
        assertNull(surface_.handleAt(widget, origin.row() + 1, origin.col()),
                "and neither is the row below the chevron");
        term.close();
    }

    /**
     * A resize is measured from where the box already is — the slot's width is the base
     * (nudge()'s convention) and the height cap, when there is one, is the other — and
     * it is clamped to the lower bounds (a resize can never demolish a widget) and to
     * the terminal (it cannot outgrow it).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "55 % 12   % the requested box is taken",
            "999 % 999 % past the terminal, the box stops at it",
            "1 % 1     % below the lower bounds, the widget keeps a body",
    }, delimiter = '%')
    public void testResizeClampsToTheTerminalAndTheLowerBounds(final int width, final int height,
                                                                final String description) throws Exception {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final Terminal term = dragTerminal(out);
        final FloatingSurface surface_ = new FloatingSurface(term);
        final AccordionWidget widget = new AccordionWidget("resize me", "one\ntwo\nthree");
        surface_.add(widget, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        surface_.renderNow();

        assertTrue(surface_.resizeTo(widget, width, height), description);
        surface_.renderNow();
        final FloatingSurface.Placement placement = surface_.placement(widget);
        assertEquals(Math.min(Math.max(10, width), term.getWidth()), placement.width(),
                description + " (width, floored at MIN_WIDTH and capped at the terminal)");
        assertEquals(Math.min(Math.max(3, height), term.getHeight()), placement.height(),
                description + " (height, floored at MIN_HEIGHT and capped at the terminal)");
        term.close();
    }

    @Test
    public void testResizingMovesTheFreeEdgeAndNotTheAnchoredOne() throws Exception {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final Terminal term = dragTerminal(out);
        final FloatingSurface surface_ = new FloatingSurface(term);
        // 18 body lines (20 drawn rows) behind a 14-row cap, so raising the cap to 26
        // grows the drawn box to the content's 20 rows — a cap only ever limits, which
        // is why a resize that raises it past the content changes nothing
        final AccordionWidget widget = new AccordionWidget("grower",
                "l01\nl02\nl03\nl04\nl05\nl06\nl07\nl08\nl09\nl10\nl11\nl12\nl13\nl14\nl15\nl16\nl17\nl18");
        widget.expand();
        widget.style().height(14).applyStyle();
        surface_.add(widget, FloatingSurface.Anchor.BOTTOM_RIGHT, 30, 0, 0);
        surface_.renderNow();

        final FloatingSurface.Cell before = surface_.origin(widget);
        assertTrue(surface_.resizeTo(widget, 55, 26));
        surface_.renderNow();
        final FloatingSurface.Cell after = surface_.origin(widget);
        assertTrue(after.col() < before.col(),
                "a right-anchored box grows leftward: " + before.col() + " -> " + after.col());
        assertTrue(after.row() < before.row(),
                "and a bottom-anchored box grows upward: " + before.row() + " -> " + after.row());
        term.close();
    }

    @Test
    public void testAFixedWidgetIsNotResizable() throws Exception {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final Terminal term = dragTerminal(out);
        final FloatingSurface surface_ = new FloatingSurface(term);
        final AccordionWidget widget = new AccordionWidget("pane-owned");
        surface_.add(widget, 3, 5);
        surface_.renderNow();
        assertFalse(surface_.resizeTo(widget, 20, 8), "a fixed slot belongs to a pane layout");
        term.close();
    }

    @Test
    public void testAFixedWidgetIsNotDraggable() throws Exception {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final Terminal term = dragTerminal(out);
        final FloatingSurface surface_ = new FloatingSurface(term);
        final AccordionWidget widget = new AccordionWidget("pane-owned");
        surface_.add(widget, 3, 5);            // a fixed cell — a pane layout owns it
        surface_.renderNow();

        assertNull(surface_.placement(widget), "a fixed slot has no anchor to offset from");
        assertFalse(surface_.placeAt(widget, 10, 10), "and cannot be moved by a drag");
        term.close();
    }

    @Test
    public void shouldBlankTheStaleRightEdgeWhenShrinkingInPlace() throws Exception {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final Terminal term = TerminalBuilder.builder().dumb(true)
                .size(new org.jline.terminal.Size(40, 120))
                .streams(new java.io.ByteArrayInputStream(new byte[0]), out).build();
        final FloatingSurface surface_ = new FloatingSurface(term);
        final AccordionWidget widget = new AccordionWidget("shrink me", "");
        widget.style().width(40).applyStyle();
        surface_.add(widget, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);   // lastCol = 1 always
        surface_.renderNow();
        final String firstPass = out.toString(java.nio.charset.StandardCharsets.UTF_8);

        // TOP_LEFT keeps lastCol pinned at 1, so this is the in-place (sameCol)
        // erase path: the stale tail is [31, 40], not the leading [1, 10].  The
        // erase must position its cursor at the stale region's start, column 31
        // (the old right border lives at column 40).
        surface_.resizeTo(widget, 30, 3);
        surface_.renderNow();
        final String shrinkPass = out.toString(java.nio.charset.StandardCharsets.UTF_8)
                .substring(firstPass.length());

        assertTrue(shrinkPass.contains("\033[2;31H"),
                "the in-place shrink must blank the stale right edge, not the new box's left edge: "
                        + shrinkPass.replace("\033", "<ESC>"));
        term.close();
    }

    @Test
    public void shouldKeepTheBoxOnScreenWhenGrownPastTheTerminal() throws Exception {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final Terminal term = dragTerminal(out);   // 120 cols x 40 rows
        final FloatingSurface surface_ = new FloatingSurface(term);
        final AccordionWidget widget = new AccordionWidget("grow me", "one\ntwo\n{{r}}red{{X}} 50%");
        surface_.add(widget, FloatingSurface.Anchor.TOP_MIDDLE, 40, 0, 0);
        surface_.renderNow();

        surface_.resizeTo(widget, 500, 500);
        surface_.renderNow();
        final String grown = out.toString(java.nio.charset.StandardCharsets.UTF_8);

        // every cursor-position must land inside the terminal — a write past the
        // edge wraps/scrolls and corrupts the far side
        final java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\033\\[(\\d+);(\\d+)H").matcher(grown);
        while (m.find()) {
            final int row = Integer.parseInt(m.group(1));
            final int col = Integer.parseInt(m.group(2));
            assertTrue(row >= 1 && row <= term.getHeight() && col >= 1 && col <= term.getWidth(),
                    "out-of-bounds write " + row + ";" + col + " in: " + grown.replace("\033", "<ESC>"));
        }
        term.close();
    }

    @Test
    public void shouldNotCorruptWhenASyntaxBlockIsClippedByTheViewport() throws Exception {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final Terminal term = dragTerminal(out);
        final FloatingSurface surface_ = new FloatingSurface(term);
        final AccordionWidget widget = new AccordionWidget("audit",
                "one\ntwo\n{{syntax:java}}public class X {\n}\n{{/syntax:java}}\nthree\nfour");
        widget.style().height(4).applyStyle();   // clip so the block is split
        surface_.add(widget, FloatingSurface.Anchor.BOTTOM_RIGHT, 40, 0, 0);
        surface_.renderNow();
        final String output = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(output.contains("{{"),
                "no raw Graphitty codes may leak when a syntax block is clipped: "
                        + output.replace("\033", "<ESC>"));
        term.close();
    }

    @Test
    public void shouldTolerateUnmatchedSyntaxAndRuleTags() {
        // the viewport can clip a {{syntax:…}} block (or a {{c}}…{{/c}} wrap) so
        // the open tag is gone but the close remains — the render must degrade
        // to plain text, never throw and never leak raw {{…}} codes
        assertDoesNotThrow(() -> Graphitty.string("{{/syntax:java}}"),
                "a stray syntax close must not throw");
        assertDoesNotThrow(() -> Graphitty.string("{{syntax:java}}code"),
                "a dangling syntax open must not throw");
        assertDoesNotThrow(() -> Graphitty.string("{{/c}}text"),
                "a stray color-rule close must not throw");
        assertEquals("text", Graphitty.string("{{/syntax:java}}text"),
                "a stray syntax close is dropped, not echoed");
    }

    @Test
    public void shouldKeepEveryPinnedWidgetKeyResolvableAcrossRepeatedRefloats() throws Exception {
        final Terminal term = TerminalBuilder.builder().dumb(true)
                .size(new org.jline.terminal.Size(40, 120))
                .streams(new java.io.ByteArrayInputStream(new byte[0]), new java.io.ByteArrayOutputStream())
                .build();
        final FloatingSurface diag = new FloatingSurface(term);
        final AccordionWidget audit = new AccordionWidget("audit");
        final AccordionWidget notes = new AccordionWidget("notes");
        diag.add(audit, FloatingSurface.Anchor.BOTTOM_RIGHT, 65, 8, -2);
        diag.add(notes, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        final String auditKey = FloatingSurface.widgetKey(audit);
        final String notesKey = FloatingSurface.widgetKey(notes);

        // two update generations of the same slot (the update pattern that
        // killed focus: @<xxx>>>=[body=>...].display() on every change)
        diag.add(new AccordionWidget("audit"), FloatingSurface.Anchor.BOTTOM_RIGHT, 65, 8, -2);
        diag.add(new AccordionWidget("audit"), FloatingSurface.Anchor.BOTTOM_RIGHT, 65, 8, -2);

        final List<Widget<?>> live = diag.widgets();
        assertEquals(2, live.size(), "two pinned widgets, no zombie copies: " + live);
        assertTrue(live.stream().anyMatch(w -> FloatingSurface.widgetKey(w).equals(notesKey)),
                "the untouched widget must keep its key: " + live);
        final String resolved = diag.resolveKey(auditKey);
        assertTrue(live.stream().anyMatch(w -> FloatingSurface.widgetKey(w).equals(resolved)),
                "the oldest audit key must resolve to a live widget key — got "
                        + resolved + " vs " + live);
        assertEquals(1, live.stream().filter(w -> FloatingSurface.widgetKey(w).equals(resolved)).count(),
                "exactly one live widget may carry the resolved key (no duplicates): " + live);
        term.close();
    }

    // ── focus marker ghosting (exactly one marker on screen, ever) ──

    @Test
    public void shouldKeepExactlyOneFocusMarkerWhenFocusMoves() throws Exception {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final Terminal term = TerminalBuilder.builder().dumb(true)
                .size(new org.jline.terminal.Size(40, 120))
                .streams(new java.io.ByteArrayInputStream(new byte[0]), out).build();
        final FloatingSurface diag = new FloatingSurface(term);
        final AccordionWidget left = new AccordionWidget("left");
        final AccordionWidget right = new AccordionWidget("right");
        diag.add(left, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        diag.add(right, FloatingSurface.Anchor.TOP_RIGHT, 40, 0, 0);

        diag.setFocusKey(FloatingSurface.widgetKey(left));
        diag.render();
        Thread.sleep(300);
        final String focusedPass = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(1, countOccurrences(focusedPass, "▶"),
                "exactly one marker while a widget is focused: "
                        + focusedPass.replace("\033", "<ESC>"));

        // move the focus — the old marker lived inside the old widget's box
        // (repainted clean this pass); the new corner marker is the only one
        diag.setFocusKey(FloatingSurface.widgetKey(right));
        diag.render();
        Thread.sleep(300);
        final String movedPass = out.toString(java.nio.charset.StandardCharsets.UTF_8)
                .substring(focusedPass.length());
        assertEquals(1, countOccurrences(movedPass, "▶"),
                "exactly one marker after focus moves — the previous pass's "
                        + "marker cell must be repainted clean, no ghost: "
                        + movedPass.replace("\033", "<ESC>"));
        term.close();
    }

    static int countOccurrences(final String haystack, final String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    // ── scrolling: content off the viewport is off-viewport, not gone ──

    /** A widget whose body is longer than any viewport it will be given. */
    private static AccordionWidget noteWidget(final int bodyLines) {
        final StringBuilder body = new StringBuilder();
        for (int i = 1; i <= bodyLines; i++) {
            if (body.length() > 0) body.append('\n');
            body.append("L%02d".formatted(i));
        }
        final AccordionWidget widget = new AccordionWidget("notes", body.toString());
        widget.expand();
        return widget;
    }

    /** A terminal + surface whose output can be read back per render pass. */
    private static final class CapturingSurface {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final Terminal term;
        final FloatingSurface surface;

        CapturingSurface() throws IOException {
            this.term = TerminalBuilder.builder().dumb(true)
                    .size(new org.jline.terminal.Size(TERM_HEIGHT, TERM_WIDTH))
                    .streams(new java.io.ByteArrayInputStream(new byte[0]), this.out).build();
            this.surface = new FloatingSurface(this.term);
        }

        /** Render synchronously and return only what these passes wrote.
         *  <p>The first (barrier) pass drains anything the surface queued
         *  fire-and-forget — a scroll/nudge render would otherwise land inside
         *  the captured window and be read as this pass's output.  Two
         *  capturing passes then follow: a widget's render can be a no-op on a
         *  pass (its region already matches), so one pass is not enough
         *  evidence — the assertions only need this state to have been drawn
         *  once, and never need a pre-scroll pass to have been missed. */
        String pass() {
            this.surface.renderNow();
            final String before = this.out.toString(java.nio.charset.StandardCharsets.UTF_8);
            this.surface.renderNow();
            this.surface.renderNow();
            final String all = this.out.toString(java.nio.charset.StandardCharsets.UTF_8);
            // measured in CHARS, not bytes — the box-drawing glyphs are 3 bytes each
            return all.substring(before.length());
        }

        void close() throws IOException {
            // stop the render thread first — a queued pass that lands after the
            // terminal closes writes into a dead stream (and logs about it)
            this.surface.shutdown();
            this.term.close();
        }
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "0 % 0 % 8  % L30 % L01 % the tail is what a live widget shows",
            "0 % 0 % 20 % L30 % L01 % a taller viewport still shows the tail",
            "0 % 4 % 8  % L05 % L30 % a style seeded offset opens the widget partway into its own text (4 body rows in)",
    }, delimiter = '%')
    void testViewportShowsTheNewestContent(final int scrollX, final int scrollY, final int height,
                                           final String visible, final String hidden, final String description) throws Exception {
        final CapturingSurface capturing = new CapturingSurface();
        final AccordionWidget widget = noteWidget(30);
        widget.style().height(height).scrollTo(scrollX, scrollY).applyStyle();
        capturing.surface.add(widget, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        capturing.surface.renderNow();
        final String pass = capturing.pass();
        assertTrue(pass.contains(visible), "%s: should show %s in %s".formatted(description, visible, printable(pass)));
        assertFalse(pass.contains(hidden), "%s: the newest content fills the viewport, so %s is off it: %s"
                .formatted(description, hidden, printable(pass)));
        capturing.close();
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "-30 % L01 % L30 % scrolling back reveals the first line and drops the last",
            "-5  % L25 % L01 % a five row scroll back stops five rows into the text (the bottom border stays pinned)",
    }, delimiter = '%')
    void testScrollBackRevealsTextThatLeftTheViewport(final int dy, final String visible,
                                                      final String hidden, final String description) throws Exception {
        final CapturingSurface capturing = new CapturingSurface();
        final AccordionWidget widget = noteWidget(30);
        widget.style().height(8).applyStyle();
        capturing.surface.add(widget, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        capturing.surface.renderNow();
        assertTrue(capturing.surface.scroll(widget, 0, dy), "the widget should accept a vertical scroll");
        final String pass = capturing.pass();
        assertTrue(pass.contains(visible), "%s: %s should be back in view: %s".formatted(description, visible, printable(pass)));
        assertFalse(pass.contains(hidden), "%s: the far end is now off the viewport: %s".formatted(description, printable(pass)));
        capturing.close();
    }

    @Test
    public void shouldNotDiscardContentWhenScrolled() {
        final AccordionWidget widget = noteWidget(30);
        widget.style().height(8).applyStyle();
        surface.add(widget, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        surface.renderNow();
        // the body the widget holds is untouched by any amount of scrolling —
        // the viewport is a window over it, not a destructive crop
        final String bodyBefore = widget.bodyLines().toString();
        surface.scroll(widget, 0, -20);
        surface.scroll(widget, 0, 7);
        surface.scroll(widget, 0, -99);
        assertEquals(bodyBefore, widget.bodyLines().toString(),
                "scrolling must never mutate the widget's body");
        assertEquals(30, widget.bodyLines().size(), "every appended line stays alive in the body");
    }

    @Test
    public void shouldFollowNewContentUntilTheReaderScrollsBack() {
        final AccordionWidget widget = noteWidget(30);
        widget.style().height(8).applyStyle();
        surface.add(widget, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        surface.renderNow();
        assertFalse(surface.isScrolled(widget), "a fresh widget follows its newest content");

        // scroll back: the reader's place is now theirs, and new content must
        // not yank it away
        surface.scroll(widget, 0, -10);
        assertTrue(surface.isScrolled(widget));
        final int heldRow = surface.slotOf(widget).scrollY;
        widget.appendLine("L31");
        surface.renderNow();
        assertEquals(heldRow, surface.slotOf(widget).scrollY,
                "appending text must not move a reader who is reading earlier text");

        // back to the end → following again
        surface.scroll(widget, 0, 99);
        assertFalse(surface.isScrolled(widget), "scrolling to the end resumes the follow");
        assertEquals(surface.slotOf(widget).maxScrollY, surface.slotOf(widget).scrollY);
    }

    @Test
    public void shouldCarryTheScrollPlaceAcrossReFloat() {
        final AccordionWidget first = noteWidget(30);
        first.style().height(8).applyStyle();
        surface.add(first, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        surface.renderNow();
        surface.scroll(first, 0, -12);
        final int scrolledTo = surface.slotOf(first).scrollY;
        assertTrue(scrolledTo > 0, "the widget should have scrolled back");

        // .display() re-hydrates the widget into a fresh instance; the reader's
        // place in the text is not something an update should throw away
        final AccordionWidget fresh = noteWidget(30);
        fresh.style().height(8).applyStyle();
        surface.add(fresh, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        assertEquals(scrolledTo, surface.slotOf(fresh).scrollY, "the scroll offset must survive the re-float");
        assertTrue(surface.isScrolled(fresh), "and so must the fact that the reader is not at the tail");
    }

    @Test
    public void shouldSeedTheScrollPlaceFromTheStyle() {
        final AccordionWidget widget = noteWidget(30);
        widget.style().height(8).scrollTo(0, 4).applyStyle();
        surface.add(widget, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        surface.renderNow();
        assertEquals(4, surface.slotOf(widget).scrollY,
                "a style scrollY seeds the viewport (a widget can open partway into its own text)");
        assertTrue(surface.isScrolled(widget), "a seeded offset is not the tail, so it is not following");
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "union(x,y) % true  % true  % both axes are offered",
            "union(y)   % false % true  % a vertical-only widget refuses a horizontal scroll",
            "union(x)   % true  % false % a horizontal-only widget refuses a vertical scroll",
            "none       % false % false % a widget can opt out of scrolling entirely",
    }, delimiter = '%')
    void testScrollRespectsDeclaredAxes(final String declaration, final boolean acceptsX,
                                        final boolean acceptsY, final String description) throws Exception {
        final AccordionWidget widget = noteWidget(30);
        widget.style().height(8).applyStyle();
        widget.getStyle().jvm().put(studio.phaseshift.metatron.isa.m.type.impl.MUri.uri("scroll"),
                studio.phaseshift.metatron.isa.m.type.impl.MUri.uri(
                        declaration.replace("union(", "").replace(")", "")));
        surface.add(widget, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        surface.renderNow();
        assertEquals(acceptsX, surface.scroll(widget, 3, 0), description + " (x)");
        assertEquals(acceptsY, surface.scroll(widget, 0, 3), description + " (y)");
    }

    @Test
    public void shouldReportWhereTheViewportSits() {
        final AccordionWidget widget = noteWidget(30);
        widget.style().height(8).applyStyle();
        surface.add(widget, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        surface.renderNow();
        assertTrue(surface.canScroll(widget), "30 body lines in an 8 row viewport must be scrollable");
        assertTrue(surface.scrollInfo(widget).startsWith("rows "),
                "the viewport should report its place: " + surface.scrollInfo(widget));
        surface.scroll(widget, 0, -10);
        assertTrue(surface.scrollInfo(widget).contains("scrolled"),
                "scrolled away from the tail should say so: " + surface.scrollInfo(widget));
        final String atTail = surface.scrollInfo(widget);
        surface.scrollToTail(widget);
        assertFalse(surface.scrollInfo(widget).contains("scrolled"), "back at the tail: " + surface.scrollInfo(widget));
        assertNotEquals(atTail, surface.scrollInfo(widget));
    }

    @Test
    public void shouldNotScrollAWidgetThatFits() {
        final AccordionWidget widget = noteWidget(2);
        widget.style().height(20).applyStyle();
        surface.add(widget, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        surface.renderNow();
        assertFalse(surface.canScroll(widget), "a widget that fits has nothing off its viewport");
        assertEquals("", surface.scrollInfo(widget));
        assertEquals(0, surface.slotOf(widget).maxScrollY);
    }

    @Test
    public void shouldWindowAWidgetTallerThanTheTerminal() throws Exception {
        // No style height at all: the widget is naturally taller than the
        // screen.  It becomes a viewport on the screen rather than drawing
        // rows off the bottom of the terminal.
        final CapturingSurface capturing = new CapturingSurface();
        final AccordionWidget widget = noteWidget(TERM_HEIGHT * 3);
        capturing.surface.add(widget, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        capturing.surface.renderNow();
        final FloatingSurface.Slot slot = capturing.surface.slotOf(widget);
        final int termRows = capturing.term.getHeight();
        assertTrue(slot.prevHeight <= termRows - 2,
                "a widget must stay on screen: " + slot.prevHeight + " rows on a " + termRows + " row terminal");
        assertTrue(capturing.surface.canScroll(widget), "and what did not fit is reachable by scrolling");
        assertTrue(capturing.surface.scrollInfo(widget).contains("/"),
                "the viewport knows the content it is windowing: " + capturing.surface.scrollInfo(widget));
        capturing.close();
    }

    @Test
    public void shouldHitTestTheWidgetUnderAPoint() {
        final AccordionWidget left = noteWidget(6);
        left.style().height(6).applyStyle();
        surface.add(left, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        surface.renderNow();
        final FloatingSurface.Slot slot = surface.slotOf(left);
        assertSame(left, surface.widgetAt(slot.lastRow, slot.lastCol),
                "the widget's own top-left cell is inside it");
        assertNull(surface.widgetAt(slot.lastRow + slot.prevHeight + 5, slot.lastCol),
                "well below the widget is outside it");
    }

    private static String printable(final String rendered) {
        return rendered.replace("\033", "<ESC>").replace("\n", "<LF>");
    }

    // ── pointer clicks (focus, affordances) ────────────────────────

    @Test
    public void shouldTranslateAClickIntoTheWidgetsOwnCoordinates() throws Exception {
        final CapturingSurface capturing = new CapturingSurface();
        final AccordionWidget widget = new AccordionWidget("notes", "one\ntwo");
        widget.expand();
        widget.style().height(10).applyStyle();
        capturing.surface.add(widget, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        capturing.surface.renderNow();
        final FloatingSurface.Slot slot = capturing.surface.slotOf(widget);

        // the indicator lives at title-row, column 3 + title length
        final int indicatorCol = slot.lastCol + 3 + "notes".length();
        assertTrue(capturing.surface.click(widget, slot.lastRow, indicatorCol),
                "the surface must hand the click to the widget, in the widget's own cells");
        assertFalse(widget.isExpanded(), "the click on [-] collapsed the accordion");

        // a click in the body is nobody's affordance: the widget declines it
        assertFalse(capturing.surface.click(widget, slot.lastRow + 2, slot.lastCol),
                "a body click is not an affordance — the console focuses the widget instead");
        assertTrue(capturing.surface.click(widget, slot.lastRow, indicatorCol + 1),
                "the indicator is a target again (it now reads [+])");
        assertTrue(widget.isExpanded());
        capturing.close();
    }

    @Test
    public void shouldOnlyClaimPointerTargetsOnceSomethingIsDrawn() throws Exception {
        final CapturingSurface capturing = new CapturingSurface();
        assertEquals(false, capturing.surface.hasPointerTargets(),
                "an empty surface has nothing for the pointer to do");
        final AccordionWidget widget = noteWidget(6);
        capturing.surface.add(widget, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        assertEquals(false, capturing.surface.hasPointerTargets(),
                "pinned but not yet drawn is still nothing to click");
        capturing.surface.renderNow();
        assertEquals(true, capturing.surface.hasPointerTargets(),
                "a drawn widget is something the pointer can focus or scroll");
        capturing.surface.clear();
        assertEquals(false, capturing.surface.hasPointerTargets(), "and it goes away with the widgets");
        capturing.close();
    }
}
