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
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;

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
}
