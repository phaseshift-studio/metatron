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

package studio.phaseshift.metatron.isa.m.sys.ui.widget;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.sys.ui.widget.MiniWidget.LineUI;
import studio.phaseshift.metatron.isa.m.sys.ui.widget.MiniWidget.SpanUI;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * The mini-widget patterns: a marker pair, a line with hot spots, and a markup-safe clip — the line-sized pieces a
 * host widget composes inside its own box, with no rec, border, or render pass of their own.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MiniWidgetTest extends AbstractMetatronTest {

    /**
     * The accordion marker is the pair the panel's lanes use, and both spellings occupy the declared columns — the
     * rest of a host's line is laid out after it.
     */
    @Test
    void theMarkerIsTheAccordionPairAndAlwaysTheDeclaredColumns() {
        final String open = MiniWidget.marker(true);
        final String folded = MiniWidget.marker(false);
        assertTrue(open.contains("▼"), "an open section reads as ▼: " + open);
        assertTrue(folded.contains("▶"), "a folded section reads as ▶: " + folded);
        assertEquals(MiniWidget.MARKER_COLUMNS, Graphitty.viewLength(open),
                "the open marker occupies its declared columns: " + open);
        assertEquals(MiniWidget.MARKER_COLUMNS, Graphitty.viewLength(folded),
                "the folded marker occupies its declared columns: " + folded);
    }

    /**
     * A hot spot is half-open {@code [start, end)} and line-relative: its first column hits, its last does not, and
     * the next span begins exactly where it ends.
     */
    @Test
    void aLineAnswersTheSpanUnderTheColumn() {
        final LineUI line = new LineUI("{{c}}▼{{w}} {{r}}[x]{{w}} ws://peer:1/mtron", List.of(
                new SpanUI(0, MiniWidget.MARKER_COLUMNS, "fold", noobj()),
                new SpanUI(MiniWidget.MARKER_COLUMNS, MiniWidget.MARKER_COLUMNS + 3, "clear", noobj())));
        assertEquals("fold", line.at(0).orElseThrow().key(), "the marker's first column");
        assertEquals("fold", line.at(MiniWidget.MARKER_COLUMNS - 1).orElseThrow().key(), "the marker's last column");
        assertEquals("clear", line.at(MiniWidget.MARKER_COLUMNS).orElseThrow().key(),
                "the next span starts where the marker ends");
        assertEquals("clear", line.at(MiniWidget.MARKER_COLUMNS + 2).orElseThrow().key(), "the clear span's last column");
        assertTrue(line.at(MiniWidget.MARKER_COLUMNS + 3).isEmpty(), "past the last span is inert");
        assertEquals("clear", MiniWidget.at(line.spans(), MiniWidget.MARKER_COLUMNS + 2).orElseThrow().key(),
                "the same hit test answers from the span list a bar keeps");
        assertTrue(line.at(-1).isEmpty(), "before the line is inert");
        assertTrue(new LineUI("an inert line").at(0).isEmpty(), "a line with no spans answers nothing");
    }

    /**
     * The clip keeps the styling of a line that fits and hands back plain, ellipsis-marked text when it must cut —
     * a cut line never ends inside a markup tag.
     */
    @Test
    void clipKeepsAFittingLineAndCutsAnOverlongOne() {
        final String fits = "{{c}}▼{{w}} {{r}}[x]{{w}} peer";
        assertEquals(fits, MiniWidget.clip(fits, 40), "a line that fits is drawn as written, markup included");
        final String cut = MiniWidget.clip("{{c}}" + "x".repeat(60) + "{{w}}", 20);
        assertTrue(cut.endsWith("…"), "a cut line is marked: " + cut);
        assertTrue(Graphitty.viewLength(cut) <= 20, "and fits the columns it was given: " + Graphitty.viewLength(cut));
        assertFalse(cut.contains("{{"), "a cut line is handed back plain, never ending inside a tag: " + cut);
        assertEquals("tiny", MiniWidget.clip("tiny", 0), "no width means no clipping");
    }
}
