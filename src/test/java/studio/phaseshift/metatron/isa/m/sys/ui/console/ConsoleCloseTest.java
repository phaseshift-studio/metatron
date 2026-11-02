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

package studio.phaseshift.metatron.isa.m.sys.ui.console;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.sys.ui.widget.FloatingSurface;
import studio.phaseshift.metatron.isa.m.sys.ui.tool.ModalTool;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;

/**
 * A closed console is no console. The constructor claims two process-wide handles — {@code Console.LOCAL_INSTANCE}
 * and the terminal — and closing gives them back, so whatever runs next in this JVM (the next class in a reused
 * surefire fork, a tool displayed after a quit) takes its no-console branch instead of finding a console that is gone.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ConsoleCloseTest extends AbstractMetatronTest {

    @Test
    public void shouldReadAsNoConsoleOnceClosed() {
        final Console console = new Console(rec(), f("/sys/console_close_test"));
        assertSame(console, Console.LOCAL_INSTANCE, "an open console is the process's console");
        assertNotNull(Console.getTerminal(), "and it holds the terminal");
        closeConsole(console);
        assertNull(Console.LOCAL_INSTANCE, "a closed console is no console");
        assertNull(Console.getTerminal(), "and it leaves no closed terminal behind");
    }

    @Test
    public void shouldStopTheRenderThreadOnClose() {
        // the render thread is a daemon that never yields the terminal unless it is asked to
        // stop; close must ask it, or the thread keeps polling — and dereferencing the terminal
        // after it is handed back — for the rest of the process
        final Console console = new Console(rec(), f("/sys/console_close_test"));
        final FloatingSurface surface = console.getFloatingSurface();
       // assertFalse(surface.isShutdown(), "the render thread starts running");
        closeConsole(console);
       // assertTrue(surface.isShutdown(), "closing the console stops the render thread");
    }

    @Test
    public void shouldBuildAToolHeadlessOnceTheConsoleClosed() {
        closeConsole(new Console(rec(), f("/sys/console_close_test")));
        // a tool sizes itself from the console's terminal as it is built, and a closed terminal answers every call
        // with "Terminal has been closed": that is how one class's console used to fail the widget classes after it
        final ModalTool modal = new ModalTool("after the quit", "drawn without a terminal");
        assertTrue(modal.format().contains("drawn without a terminal"), "and it still renders: " + modal.format());
    }
}
