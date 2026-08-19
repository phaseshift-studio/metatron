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

import studio.phaseshift.metatron.furi.fURI;

import java.util.Map;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class KeyboardCombos {

    private KeyboardCombos() {
        // do nothing
    }

    /** Named special keys and their raw terminal byte sequences. */
    private static final Map<String, String> SPECIAL_KEYS = Map.ofEntries(
            Map.entry("tab", "\t"),
            Map.entry("enter", "\r"),
            Map.entry("return", "\r"),
            Map.entry("esc", "\033"),
            Map.entry("escape", "\033"),
            Map.entry("space", " "),
            Map.entry("up", "\033[A"),
            Map.entry("down", "\033[B"),
            Map.entry("right", "\033[C"),
            Map.entry("left", "\033[D"),
            Map.entry("home", "\033[H"),
            Map.entry("end", "\033[F"),
            Map.entry("pageup", "\033[5~"),
            Map.entry("pagedown", "\033[6~"),
            Map.entry("backspace", "\177"),
            Map.entry("delete", "\033[3~"));

    /**
     * CSI-modifiable keys mapped to their {@code <code><suffix>} pair.  The
     * modifier code is injected between them to form {@code \033[<code>;<mod><suffix>}.
     */
    private static final Map<String, String> CSI_CODES = Map.ofEntries(
            Map.entry("up", "1A"),
            Map.entry("down", "1B"),
            Map.entry("right", "1C"),
            Map.entry("left", "1D"),
            Map.entry("home", "1H"),
            Map.entry("end", "1F"),
            Map.entry("pageup", "5~"),
            Map.entry("pagedown", "6~"),
            Map.entry("delete", "3~"));

    /**
     * Parse a {@code fURI}-shaped combo (e.g. {@code alt_m}) into the raw
     * terminal byte sequence JLine's {@code KeyMap} expects (e.g.
     * {@code "\033m"}).  The combo is split on {@code _}: leading tokens are
     * modifiers ({@code alt}/{@code meta}, {@code ctrl}, {@code shift}); the
     * final token is the key (a named special key or a single character).
     *
     * @param combo the combo uri (e.g. {@code alt_m})
     * @return the terminal sequence, or {@code null} if unknown/empty
     */
    public static String parse(final fURI combo) {
        if (null == combo) return null;
        return parse(combo.name());
    }

    /**
     * Parse a combo string (e.g. {@code "alt_m"}) into the raw terminal byte
     * sequence JLine's {@code KeyMap} expects.
     *
     * @param combo the combo string
     * @return the terminal sequence, or {@code null} if unknown/empty
     */
    public static String parse(final String combo) {
        if (null == combo || combo.isBlank()) return null;
        final String[] tokens = combo.split("_");
        final String key = tokens[tokens.length - 1].toLowerCase();

        boolean ctrl = false, alt = false, shift = false;
        for (int i = 0; i < tokens.length - 1; i++) {
            switch (tokens[i].toLowerCase()) {
                case "alt", "meta" -> alt = true;
                case "ctrl" -> ctrl = true;
                case "shift" -> shift = true;
                default -> {
                    return null; // unknown modifier
                }
            }
        }

        // Single-character key: alt → ESC-prefix, ctrl → control char.
        if (key.length() == 1) {
            if (alt) return "\033" + key;
            if (ctrl) return String.valueOf((char) (key.charAt(0) & 0x1F));
            return shift ? key.toUpperCase() : key;
        }

        // Named special key without modifiers.
        if (!ctrl && !alt && !shift)
            return SPECIAL_KEYS.get(key);

        // Named special key with modifiers → CSI sequence.  Modifier code:
        // 1 none, 2 shift, 3 alt, 4 shift+alt, 5 ctrl, 6 shift+ctrl, ...
        final int mod = 1 + (shift ? 1 : 0) + (alt ? 2 : 0) + (ctrl ? 4 : 0);
        final String code = CSI_CODES.get(key);
        if (null == code) return null;
        return "\033[" + code.charAt(0) + ";" + mod + code.substring(1);
    }
}
