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

package studio.phaseshift.metatron.isa;

import org.jline.jansi.Ansi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.EmojiTable;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.jline.jansi.Ansi.ansi;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GraphittyTest extends AbstractMetatronTest {

    @Test
    public void testRewrites() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Graphitty g = new Graphitty(Map.of("abc", "hello"), out);
        /*g.print("{{abc}} here");
        assertEquals("hello here", out.toString());
        out.reset();
        g.print("{{r}}red{{/r}}");
        assertEquals(ansi().fg(Ansi.Color.RED).a("red").reset().toString(), out.toString());
        out.reset();*/
        g.print("{{r}}red{{g}}green{{/g}}back to{{/r}}");
        assertEquals(ansi().fg(Ansi.Color.RED).a("red").fg(Ansi.Color.GREEN).a("green").reset().fg(Ansi.Color.RED).a("back to").reset().toString(), out.toString());
    }

    // ── Emoji shortcodes ({{:name:}}) ───────────────────────────────

    @ParameterizedTest
    @CsvSource(value = {
            "{{:beer:}}                 % 🍺",
            "{{:beer:}} cheers          % 🍺 cheers",
            "{{:us:}}                   % 🇺🇸",
            "{{:rocket:}}               % 🚀",
            "raw 🐿 emoji               % raw 🐿 emoji",
            "{{:definitely_not_an_emoji:}} % :definitely_not_an_emoji:",
    }, delimiter = '%')
    void testEmojiShortcode(final String code, final String expected) {
        assertEquals(expected, Graphitty.string(code));
    }

    @Test
    public void testEmojiComposesWithColor() {
        final String s = Graphitty.string("{{:beer:&b}}");
        assertTrue(s.contains("🍺"), "emoji should render alongside the color rule: " + s);
        assertTrue(s.contains("\033[34m"), "{{:beer:&b}} should also emit the blue color rule: " + s);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "beer   % 🍺",
            "rocket % 🚀",
    }, delimiter = '%')
    void testEmojiTableLookup(final String name, final String expected) {
        assertEquals(expected, EmojiTable.get(name));
    }

}
