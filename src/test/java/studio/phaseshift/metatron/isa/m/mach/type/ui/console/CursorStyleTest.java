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

package studio.phaseshift.metatron.isa.m.mach.type.ui.console;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The cursor style the console draws the prompt's insertion point in, and that
 * {@code ReaderSetup.showCursor} writes both the show-cursor code and the chosen
 * style to the terminal before a prompt — so a reader can see where the arrow
 * keys have moved them even when the terminal's own blink is off.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class CursorStyleTest extends AbstractMetatronTest {

    @ParameterizedTest()
    @CsvSource(value = {
            "blink-underline % 3 % the default is a blinking underscore",
            "underline       % 4 % a steady underscore",
            "blink-bar       % 5 % a blinking vertical bar",
            "bar             % 6 % a steady vertical bar",
            "blink-block     % 1 % a blinking block",
            "block           % 2 % a steady block",
            "garbage         % 3 % an unknown name falls back to blink-underline",
            "<empty>         % 3 % a blank name falls back too",
    }, delimiter = '%')
    void testCursorStyle(final String name, final int param, final String desc) {
        final String style = ReaderSetup.cursorStyle("<empty>".equals(name) ? "" : name);
        assertEquals("\033[" + param + " q", style, desc);
    }

    @Test
    void testShowCursorSequence() {
        assertEquals("\033[?25h\033[3 q", ReaderSetup.cursorShow(),
                "showCursor writes the show-cursor code then the (default) blink-underline style");
    }

    @Test
    void testResetCursorSequence() {
        assertEquals("\033[0 q\033[?25h", ReaderSetup.cursorReset(),
                "resetCursor restores the terminal's own style then shows the cursor");
    }

    @Test
    void testCursorMethodsIgnoreNullTerminal() {
        // Both must be no-ops, not throws — the exit hook calls resetCursor on a
        // terminal that may already be gone.
        ReaderSetup.showCursor(null);
        ReaderSetup.resetCursor(null);
    }
}
