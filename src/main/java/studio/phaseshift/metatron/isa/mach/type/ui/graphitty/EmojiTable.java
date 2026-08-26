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

package studio.phaseshift.metatron.isa.mach.type.ui.graphitty;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * EmojiTable — GitHub shortcode → Unicode emoji lookup, loaded once from the
 * vendored {@code emoji.txt} classpath resource (GitHub shortcodes, sourced
 * from the gemoji dataset).  {@link Graphitty} expands {@code {{:beer:}}} rules
 * through this table.
 * <p>
 * The resource maps each name (e.g. {@code beer}) to its space-separated
 * codepoints (e.g. {@code U+1F37A}) so multi-codepoint emoji (flags, ZWJ
 * sequences) load correctly.  A missing or unreadable resource degrades to an
 * empty table — {@code {{:beer:}}} then renders nothing, same as an unknown rule.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class EmojiTable {

    private static final Map<String, String> EMOJIS = load();

    private EmojiTable() {
    }

    /**
     * The emoji for a GitHub shortcode (e.g. {@code "beer"} → 🍺), or
     * {@code null} if the name is unknown.
     */
    public static String get(final String name) {
        return EMOJIS.get(name);
    }

    private static Map<String, String> load() {
        final Map<String, String> emojis = new HashMap<>();
        try (InputStream in = EmojiTable.class.getResourceAsStream("/emoji.txt")) {
            if (null == in) return emojis;
            final BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while (null != (line = reader.readLine())) {
                if (line.isEmpty()) continue;
                final int tab = line.indexOf('\t');
                if (tab < 0) continue;
                emojis.put(line.substring(0, tab), codepoints(line.substring(tab + 1)));
            }
        } catch (final Exception e) {
            return emojis;  // never break Graphitty rendering because of data
        }
        return emojis;
    }

    /** Build the emoji string from space-separated {@code U+XXXX} codepoints. */
    private static String codepoints(final String cps) {
        final StringBuilder sb = new StringBuilder();
        for (final String cp : cps.split(" ")) {
            if (cp.startsWith("U+")) {
                sb.appendCodePoint(Integer.parseInt(cp.substring(2), 16));
            }
        }
        return sb.toString();
    }
}
