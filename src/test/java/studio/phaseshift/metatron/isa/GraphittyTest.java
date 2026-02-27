/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.AbstractMetatronTest;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.jline.jansi.Ansi.ansi;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

}
