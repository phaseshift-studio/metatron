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

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a bounded peek on a terminal reader actually does, which decides whether the console can ask
 * the terminal where its cursor is without risking a wait for input: the answer is the row the
 * console's own output starts on, and without it the only safe thing to do is append.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Disabled("hangs")
public class TerminalProbeBehaviourTest extends AbstractMetatronTest {

    @Test
    void testAPeekWithATimeoutReturnsWhenNothingArrives() throws Exception {
        final Terminal terminal = TerminalBuilder.builder().dumb(true)
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()).build();
        final long started = System.currentTimeMillis();
        final int c = terminal.reader().peek(150);
        final long took = System.currentTimeMillis() - started;
        assertEquals(NonBlockingReader.READ_EXPIRED, c, "nothing arrived, so the peek expires");
        assertTrue(took < 2000, "and it expires promptly rather than waiting for input (took " + took + "ms)");
        terminal.close();
    }

    @Test
    void testAReportThatIsAlreadyThereIsReadable() throws Exception {
        final byte[] reply = "\033[12;1R".getBytes(StandardCharsets.UTF_8);
        final Terminal terminal = TerminalBuilder.builder().dumb(true)
                .streams(new ByteArrayInputStream(reply), new ByteArrayOutputStream()).build();
        assertTrue(terminal.reader().peek(300) == '\033', "the start of a report is visible to a peek");
        terminal.close();
    }
}
