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
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A card is a bordered badge for one obj.  It sizes to its content — a declared width is
 * a limit rather than a fill — and what it must never do is draw wider than the width it
 * was given, which is what a long title used to make it do.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class CardWidgetTest extends AbstractMetatronTest {

    @BeforeAll
    static void setUp() {
        AbstractMetatronTest.begin();
    }

    private static CardWidget card(final String title, final String body, final int width) {
        final CardWidget card = new CardWidget(title, body);
        card.style().border(Border.continuous).width(width).applyStyle();
        return card;
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "36 % 30 % clipped % a title wider than the card is clipped to it",
            "20 % 30 % intact  % a title the card's width has room for is drawn whole",
    }, delimiter = '%')
    void testTitleIsClippedToTheDeclaredWidth(final int titleLength, final int width,
                                              final String expectation, final String description) {
        final String title = "t".repeat(titleLength);
        final List<String> rows = List.of(card(title, "a body", width).format().split("\n", -1));
        final String titleRow = rows.get(1);
        for (final String row : rows)
            assertTrue(Highlighter.visualLength(row) <= width,
                    "%s: no row is wider than the width the card was given: %s".formatted(description, row));
        if ("clipped".equals(expectation.trim())) {
            assertTrue(titleRow.contains("…"), "the clipped title is marked as clipped: " + titleRow);
            assertEquals(width, Highlighter.visualLength(rows.get(0)),
                    "and the card is the width it was given, not the width of its title: " + rows.get(0));
        } else {
            assertTrue(titleRow.contains(title), "a title that fits is drawn whole: " + titleRow);
            assertEquals(titleLength + 2, Highlighter.visualLength(rows.get(0)),
                    "a card smaller than the width it was given keeps its natural size: " + rows.get(0));
        }
    }

    /**
     * A declared width is a cap and not a fill: a long body wraps to it rather than
     * being left for whatever draws the card to clip (the surface clips an over-wide
     * line by STRIPPING it, which takes the card's colors with it).
     */
    @Test
    public void testANarrowCardWrapsItsBodyAndNeverGrowsPastItsWidth() {
        final String body = "a body that is far too long to fit in twenty columns";
        final CardWidget natural = card("short", body, 0);
        assertEquals(body.length() + 2, Highlighter.visualLength(natural.format().split("\n", -1)[0]),
                "undirected, the card is as wide as its content: " + natural.format());

        final List<String> rows = List.of(card("short", body, 20).format().split("\n", -1));
        assertTrue(rows.size() > 4, "the body wrapped rather than being clipped away: " + rows);
        for (final String row : rows)
            assertEquals(20, Highlighter.visualLength(row),
                    "told to be 20 columns wide, every row of the card is 20: " + row);
        assertTrue(String.join("\n", rows).contains("twenty columns"),
                "and the whole body is still there: " + rows);
    }
}
