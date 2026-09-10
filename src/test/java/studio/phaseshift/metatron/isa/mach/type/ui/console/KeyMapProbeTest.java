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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import studio.phaseshift.metatron.AbstractMetatronTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
 * diagnostic probe: feed the exact alt-key byte sequences through the JLine
 * fork's read path (dumb terminal -> readLine -> "main" keymap) and report
 * which bound handlers fire, what leaks into the line buffer, and what the
 * fork pre-binds around ESC.  Pure instrumentation — prints everything.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class KeyMapProbeTest extends AbstractMetatronTest {

    static final List<String> fired = new ArrayList<>();

    static String esc(final char c) {
        return "\033" + c;
    }

    static Binding recording(final String label) {
        return (Binding) (org.jline.reader.Widget) () -> {
            fired.add(label);
            logKey("fired {{y}}%s{{X}}", label);
            return true;
        };
    }

    static void logKey(final String format, final Object... args) {
        System.out.println("[keyprobe] " + String.format(format, args).replace("{{y}}", "").replace("{{X}}", ""));
    }

    static String show(final CharSequence seq) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < seq.length(); i++) {
            final char c = seq.charAt(i);
            if (c == 27) {
                sb.append("\\e");
            }
            else if (c < 32 || c > 126) {
                sb.append(String.format("%c", c));
            }
            else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static void dumpEscBindings(final String label, final KeyMap<Binding> keyMap) {
        logKey("%s: %d bound keys total", label, keyMap.getBoundKeys().size());
        for (final Map.Entry<String, Binding> e : keyMap.getBoundKeys().entrySet()) {
            if (e.getKey().startsWith("\033") || e.getKey().equals("\033")) {
                logKey("  bound: %s -> %s", show(e.getKey()), String.valueOf(e.getValue()));
            }
        }
    }

    @Test
    @Timeout(120)
    void probeEscSequencesThroughReadPath() throws Exception {
        // feed: alt+w CR, alt+v CR, alt+^ CR, alt+< CR, alt+> CR, CR (spare line end)
        final String input = esc('w') + "\r" + esc('v') + "\r" + esc('^') + "\r"
                + esc('<') + "\r" + esc('>') + "\r" + "\r";
        final ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Terminal terminal = TerminalBuilder.builder()
                .system(false)
                .dumb(true)
                .streams(in, out)
                .size(new org.jline.terminal.Size(40, 120))
                .build();
        final var reader = LineReaderBuilder.builder().terminal(terminal).build();

        final KeyMap<Binding> main = reader.getKeyMaps().get("main");
        logKey("keyMaps: %s", reader.getKeyMaps().keySet());
        logKey("main keymap: %s", null == main ? "null!" : "present");
        if (null == main) {
            terminal.close();
            return;
        }

        logKey("=== fork pre-bound ESC bindings (before our binds) ===");
        dumpEscBindings("pre", main);

        main.bind(recording("alt_w"), esc('w'));
        main.bind(recording("alt_v"), esc('v'));
        main.bind(recording("alt_caret"), esc('^'));
        main.bind(recording("alt_lt"), esc('<'));
        main.bind(recording("alt_gt"), esc('>'));

        logKey("=== fork ESC bindings (after our binds) ===");
        dumpEscBindings("post", main);

        logKey("=== feeding: \\e w CR | \\e v CR | \\e ^ CR | \\e < CR | \\e > CR | CR ===");
        for (int line = 1; line <= 5; line++) {
            fired.clear();
            final String result;
            try {
                result = reader.readLine();
            }
            catch (final Exception e) {
                logKey("line %d: exception %s", line, e);
                continue;
            }
            logKey("line %d: fired=%s line=\"%s\"", line, fired,
                    null == result ? "null" : result.replace("\r", "\\r").replace("\n", "\\n"));
        }
        if (fired.isEmpty()) {
            // sixth line: whatever is left
            try {
                logKey("spare line: fired=%s line=%s", fired, reader.readLine());
            }
            catch (final Exception e) {
                logKey("spare line: exception %s", e);
            }
        }
        logKey("output stream so far: %d bytes", out.size());
    }
}
