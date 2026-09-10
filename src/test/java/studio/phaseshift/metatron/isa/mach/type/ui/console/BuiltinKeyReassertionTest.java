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

import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/*
 * A builtin shortcut key bound at console startup must survive a later
 * shadow binder (a menu line key, a tool): the console reclaims the
 * sequence before every prompt.  The jline fork's own defaults (e.g.
 * \e< = beginning-of-history, \e> = end-of-history — both silent) explain
 * why an unbound alt+< / alt+> looks exactly like "nothing happened".
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BuiltinKeyReassertionTest extends AbstractMetatronTest {

    /*
     * one row per built-in shortcut sequence — each must be reclaimable
     * after a foreign binder takes the key mid-session
     */
    @ParameterizedTest
    @Timeout(60)
    @CsvSource(value = {
            "1 % alt+w cycling",
            "2 % alt+v height shrink",
            "3 % alt+^ height grow",
            "4 % alt+< width shrink",
            "5 % alt+> width grow",
    }, delimiter = '%')
    void shadowedBuiltinIsReclaimedBeforeNextPrompt(final int tag, final String desc) throws Exception {
        // the exact escape sequence per tag (kept out of CSV for readability)
        final String[] sequences = {"\033w", "\033v", "\033^", "\033<", "\033>"};
        final String sequence = sequences[tag - 1];

        final Terminal terminal = TerminalBuilder.builder()
                .system(false)
                .dumb(true)
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())
                .size(new org.jline.terminal.Size(40, 120))
                .build();
        try {
            final var reader = LineReaderBuilder.builder().terminal(terminal).build();
            final KeyMap<Binding> main = reader.getKeyMaps().get("main");
            assertNotNull(main, "the fork must expose the 'main' keymap: " + reader.getKeyMaps().keySet());

            final Binding builtin = (Binding) (org.jline.reader.Widget) () -> true;
            final Binding foreign = (Binding) (org.jline.reader.Widget) () -> true;

            final Map<String, Object> registered = new LinkedHashMap<>();
            registered.put(sequence, builtin);

            main.bind(builtin, sequence);
            // simulate a later shadow (menu line key, tool, foreign code)
            main.bind(foreign, sequence);
            assertNotSame(builtin, main.getBound(sequence),
                    "setup: the foreign binder must shadow the builtin first — " + desc);

            // the console reassertion (what prepareForInput does each prompt)
            Console.reassertBuiltin(main, registered);

            assertSame(builtin, main.getBound(sequence),
                    "reassertion must hand the sequence back to the builtin handler — " + desc);

            // idempotent when nothing was shadowed
            Console.reassertBuiltin(main, registered);
            assertSame(builtin, main.getBound(sequence),
                    "a second reassertion must be a no-op when the builtin holds — " + desc);
        }
        finally {
            terminal.close();
        }
    }
}
