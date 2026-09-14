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

package studio.phaseshift.metatron.isa.mach.type.ui.console;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.AccordionWidget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.FloatingSurface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;

/**
 * The pointer's gesture vocabulary on a live console: a click on a widget
 * focuses it, a click on a widget's own affordance works that affordance, and
 * a click on terminal that has no widget under it drops the focus.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ConsoleWidgetPointerTest extends AbstractMetatronTest {

    private static final String TITLE = "notes";

    private Console console;
    private AccordionWidget notes;

    @BeforeEach
    public void setUp() {
        this.console = new Console(rec(), f("/sys/console_pointer_test"));
        // a headless terminal reports no size, and a zero-width viewport clips
        // every rendered line to one column — the click geometry needs a
        // terminal that is actually a terminal
        Console.getTerminal().setSize(new org.jline.terminal.Size(120, 40));
        this.notes = new AccordionWidget(TITLE, "one\ntwo");
        this.notes.expand();
        // TOP_LEFT pins the box at row 2, column 1 — deterministic geometry to
        // click at, in the same cells the terminal would report
        this.console.getFloatingSurface().add(this.notes, FloatingSurface.Anchor.TOP_LEFT, 40, 0, 0);
        this.console.getFloatingSurface().renderNow();
    }

    @AfterEach
    public void tearDown() {
        if (null != this.console) {
            this.console.getFloatingSurface().clear();
        }
    }

    /** The indicator sits in the title bar: border, space, title, space, [-]. */
    private int indicatorColumn() {
        return 1 + 3 + TITLE.length() + 1;   // 1-based terminal column
    }

    @Test
    public void shouldFocusTheWidgetUnderThePointer() {
        assertNull(this.console.getActiveWidget(), "nothing is focused to start with");
        // row 2 is the header (the accordion's own target); row 4 is body — a
        // click there is nobody's affordance, so it is the console's to handle
        assertFalse(this.console.clickAt(4, 2), "a click with no affordance under it is the console's");
        assertSame(this.notes, this.console.getActiveWidget(),
                "clicking a widget focuses it");
    }

    @Test
    public void shouldWorkTheAffordanceUnderThePointerAndFocusToo() {
        assertNull(this.console.getActiveWidget(), "nothing is focused to start with");
        assertTrue(this.console.clickAt(2, indicatorColumn()),
                "the accordion consumes a click on its [-] indicator");
        assertFalse(this.notes.isExpanded(), "clicked [-] → collapsed");
        assertSame(this.notes, this.console.getActiveWidget(),
                "clicking the indicator also focuses the widget it belongs to");
        assertTrue(this.console.clickAt(2, indicatorColumn()),
                "and the same cell is the [+] indicator now");
        assertTrue(this.notes.isExpanded(), "clicked [+] → expanded");
    }

    @Test
    public void shouldFocusOnAHeaderClickWithoutFolding() {
        // The header is where a pointer naturally lands on a widget, so a
        // header click must focus — folding on the way in is how a live widget
        // ends up looking empty (a folded accordion draws only its header).
        assertFalse(this.console.clickAt(2, 2), "the title text is not an affordance");
        assertSame(this.notes, this.console.getActiveWidget(), "a header click focuses the widget");
        assertTrue(this.notes.isExpanded(), "and leaves its text alone");
        assertTrue(this.console.clickAt(2, indicatorColumn()), "the glyph is the fold target");
        assertFalse(this.notes.isExpanded(), "clicked [-] → folded");
        assertTrue(this.console.clickAt(2, indicatorColumn()), "and unfolds again");
        assertTrue(this.notes.isExpanded());
    }

    @Test
    public void shouldDropTheFocusWhenTheClickLandsOnEmptyTerminal() {
        this.console.clickAt(4, 2);
        assertSame(this.notes, this.console.getActiveWidget(), "the widget is focused");

        assertFalse(this.console.clickAt(30, 80), "empty terminal consumes nothing");
        assertNull(this.console.getActiveWidget(),
                "a click on terminal with no widget under it drops the focus");
        assertTrue(this.console.pointerDismissed(),
                "and hands the mouse back to the terminal (its wheel, its selection)");
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "true  % false % 1 % true  % a focused widget owns the pointer",
            "false % false % 1 % true  % widgets on screen own it until waved off",
            "false % true  % 1 % false % waved off: the terminal gets its mouse back",
            "false % false % 0 % false % nothing on screen: the terminal keeps its mouse",
            "true  % true  % 0 % true  % a focus outranks a stale dismissal",
    }, delimiter = '%')
    void testPointerOwnership(final boolean focused, final boolean dismissed, final int widgets,
                              final boolean wanted, final String description) {
        assertEquals(wanted, Console.pointerWanted(focused, dismissed, widgets), description);
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "4  % 1  % focused     % the widget's own body cell is inside it",
            "2  % 12 % focused     % the title bar's last cell is inside it",
            "7  % 1  % not-focused % a row below the widget is outside it",
            "2  % 60 % not-focused % a column past the widget is outside it",
    }, delimiter = '%')
    void testWhatThePointerHits(final int row, final int col, final String expectation, final String description) {
        this.console.clickAt(row, col);
        if ("focused".equals(expectation.trim()))
            assertSame(this.notes, this.console.getActiveWidget(), description);
        else
            assertNull(this.console.getActiveWidget(), description);
    }

    @Test
    public void shouldOnlyTrackTheMouseWhileSomethingIsOnScreen() {
        assertEquals(true, this.console.getFloatingSurface().hasPointerTargets(),
                "a drawn widget is something the pointer can act on");
        this.console.clickAt(2, 1);
        assertSame(this.notes, this.console.getActiveWidget());
        this.console.focusWidget(null);
        assertNull(this.console.getActiveWidget());
        this.console.getFloatingSurface().clear();
        assertEquals(false, this.console.getFloatingSurface().hasPointerTargets(),
                "with nothing on screen the terminal keeps its own mouse");
    }
}
