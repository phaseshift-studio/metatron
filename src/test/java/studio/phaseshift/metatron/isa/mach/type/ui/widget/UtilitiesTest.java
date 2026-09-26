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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one rule every titled box clips through: an explicit width is the box's width,
 * the box spends its own chrome first ({@code chrome}), and the title is what gives way
 * — clipped with an ellipsis rather than allowed to widen the box past it.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class UtilitiesTest extends AbstractMetatronTest {

    @BeforeAll
    static void setUp() {
        AbstractMetatronTest.begin();
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "notes               % 40 % 6 % notes              % room to spare - the title is drawn whole",
            "a long title indeed % 14 % 6 % a long …           % past the room - clipped, and filling it exactly",
            "notes               % 0  % 6 % notes              % no declared width - a content-sized box needs no clip",
            "notes               % 6  % 6 % ''                 % nothing left for a title once the chrome is paid",
            "notes               % 3  % 6 % ''                 % a width under the box's own chrome is not room either",
    }, delimiter = '%')
    void testTitleClip(final String title, final int width, final int chrome,
                       final String expected, final String description) {
        assertEquals(expected, Utilities.titleClip(title, width, chrome), description);
    }

    @Test
    void testTitleIsOneLine() {
        assertEquals("a b", Utilities.titleClip("a\nb", 0, 6),
                "a title carrying a newline cannot break the box it sits in");
        assertEquals("a b", Utilities.titleClip("a\r\nb", 10, 6),
                "and a clipped title is flattened first, so its columns are counted on one line");
    }

    // ── a clip keeps the author's own tags balanced ────────────────

    /**
     * A clip keeps the colour codes it can still see (the ones leading the text), so the
     * close for them is now BEYOND the clip.  Leaving it there leaks the colour past the
     * ellipsis — into the rest of the header, the box border and whatever follows it —
     * so the author's close is pulled forward with the clip.
     */
    @ParameterizedTest()
    @CsvSource(value = {
            "{{b}}a long title indeed{{/b}}                % {{b}}a long …{{/b}}               % the author's close is pulled forward",
            "{{b}}a long title indeed{{X}}                 % {{b}}a long …{{X}}                % the reset is a close too, and settles every open rule",
            "{{b}}{{[k]}}a long title indeed{{/[k]}}{{/b}} % {{b}}{{[k]}}a long …{{/[k]}}{{/b}} % nested rules close inside-out",
            "{{b}}a long title indeed                      % {{b}}a long …                    % an unclosed tag is left as authored - that leak is in the source",
            "{{b}}{{/b}}{{g}}a long title                  % {{b}}{{/b}}{{g}}a long …         % a rule the kept codes already closed owes nothing",
    }, delimiter = '%')
    void testClosersArePulledForward(final String title, final String expected, final String description) {
        assertEquals(expected.trim(), Utilities.textClip(title, 8), description);
    }

    @Test
    void testAClippedColourDoesNotRunPastTheClip() {
        final String clipped = Utilities.textClip("{{b}}a long title indeed{{/b}}", 8);
        assertEquals(8, Graphitty.viewLength(clipped),
                "the pulled close costs no columns — a tag draws nothing: " + clipped);
        assertTrue(clipped.startsWith("{{b}}") && clipped.endsWith("{{/b}}"),
                "the kept colour is opened and closed inside the clip: " + clipped);
    }
}
