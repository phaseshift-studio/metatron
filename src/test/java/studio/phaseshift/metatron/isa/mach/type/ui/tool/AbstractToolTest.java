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

package studio.phaseshift.metatron.isa.mach.type.ui.tool;

import org.jline.keymap.KeyMap;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;

/**
 * The one choice a tool's lifecycle makes — take the keyboard, or present — and the one exit that no key asks for.
 * <p>
 * A tool takes the keyboard only when it is modal, a console is live, and its terminal can be a keyboard. Anywhere
 * else a key loop waits on a stdin that never delivers (a reused test fork once hung on exactly that) or draws frames
 * that cannot redraw in place, so the tool presents instead. A probe tool makes the choice visible: it counts the
 * frames it draws and the times it is presented, and it leaves on {@code q}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class AbstractToolTest extends AbstractMetatronTest {

    /**
     * the console the "live" rows run beside: one for the class, since a console is process-wide anyway
     */
    private static Console CONSOLE;

    @BeforeAll
    static void openTheConsole() {
        CONSOLE = new Console(rec(), f("/sys/abstract_tool_test"));
    }

    @AfterAll
    static void closeTheConsole() {
        closeConsole(CONSOLE);
    }

    @ParameterizedTest
    @Timeout(60)
    @CsvSource(value = {
            "live % xterm      % q  % keyboard % an interactive terminal is the keyboard: one frame, then q leaves",
            "live % xterm      % '' % keyboard % the end of input leaves after one frame instead of redrawing forever",
            "live % dumb       % q  % present  % a dumb terminal (a pipe, a CI log, a test JVM) cannot be a keyboard",
            "live % dumb-color % q  % present  % nor can the colored one jline picks when TERM is set or under an IDE",
            "live % none       % q  % present  % no terminal at all presents rather than failing to enter raw mode",
            "none % xterm      % q  % present  % without a console a tool presents, however interactive its terminal",
    }, delimiter = '%')
    void testTheKeyboardIsTakenOnlyWhereKeysCanArrive(final String console, final String type, final String typed,
                                                     final String mode, final String description) throws IOException {
        final Console previous = Console.LOCAL_INSTANCE;
        Console.LOCAL_INSTANCE = "live".equals(console) ? CONSOLE : null;
        try (final Terminal terminal = "none".equals(type) ? null : terminal(type, typed)) {
            final ProbeTool tool = new ProbeTool(terminal);
            tool.run();
            final boolean keyboard = "keyboard".equals(mode);
            assertEquals(keyboard ? 1 : 0, tool.frames, description + " (frames drawn)");
            assertEquals(keyboard ? 0 : 1, tool.presentations, description + " (times presented)");
        } finally {
            Console.LOCAL_INSTANCE = previous;
        }
    }

    /**
     * A terminal of the given type whose input is what was typed and then the end of input. jline's in-memory
     * terminal claims whatever type it is given, and the type is all the lifecycle reads of it.
     */
    private static Terminal terminal(final String type, final String typed) throws IOException {
        return new DumbTerminal("abstract-tool-test", type,
                new ByteArrayInputStream(typed.getBytes(StandardCharsets.UTF_8)), new ByteArrayOutputStream(),
                StandardCharsets.UTF_8);
    }

    private enum ProbeAction {QUIT}

    /**
     * The smallest tool there is: it runs on the terminal it is given, counts its frames and its presentations, and
     * leaves on {@code q}.
     */
    private static final class ProbeTool extends AbstractTool<ProbeTool, ProbeAction> {

        /**
         * more frames than this with no key between them is a loop spinning, which fails the row rather than hang it
         */
        private static final int SPINNING = 100;

        private int frames = 0;
        private int presentations = 0;

        private ProbeTool(final Terminal terminal) {
            super();
            this.terminal = terminal;
        }

        @Override
        protected KeyMap<ProbeAction> keyMap() {
            final KeyMap<ProbeAction> keys = new KeyMap<>();
            keys.bind(ProbeAction.QUIT, "q");
            return keys;
        }

        @Override
        protected void redraw() {
            if (++this.frames > SPINNING)
                throw new AssertionError("the key loop is spinning: " + this.frames + " frames and no key");
        }

        @Override
        protected boolean handle(final ProbeAction action) {
            return ProbeAction.QUIT != action;
        }

        @Override
        protected void present() {
            this.presentations++;
        }
    }
}
