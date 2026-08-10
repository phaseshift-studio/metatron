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

import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Utilities {

    private Utilities() {
        //do nothing
    }

    public static final CharSequence esc_key = "\u001b";
    public static final CharSequence tab_key = "\t";
    public static final CharSequence enter_key = "\r";
    public static final String up_key = "{{^1}}";
    public static final String down_key = "{{v1}}";
    public static final CharSequence left_key = "\u2190";
    public static final CharSequence right_key = "\02192";

    /** Regex to capture leading Graphitty codes for re-insertion on wrapped lines. */
    private static final Pattern LEADING_CODES = Pattern.compile("^(\\{\\{[^}]*}})*");

    public static int maxWidth(final List<String> strings) {
        return strings.stream().flatMap(s -> Arrays.stream(s.split("\n"))).map(Highlighter::visualLength).max(Integer::compareTo).orElse(0);
    }

    /**
     * Word-wrap a single line at the given width, preserving leading Graphitty codes
     * so that continuation lines keep their colour.  Lines already within the limit
     * are returned as-is.  A {@code maxW <= 0} means no wrapping.
     *
     * @param line the line to wrap (may contain Graphitty markup)
     * @param maxW maximum visible characters per output line
     * @return wrapped lines (single-element list if no wrapping needed)
     */
    public static List<String> wordWrap(final String line, final int maxW) {
        if (maxW <= 0 || Highlighter.visualLength(line) <= maxW) {
            return List.of(line);
        }
        // Extract leading Graphitty codes so continuation lines keep colour
        final Matcher m = LEADING_CODES.matcher(line);
        final String leadIn = m.find() ? m.group() : "";
        final String rest = leadIn.isEmpty() ? line : line.substring(leadIn.length());

        // Strip any remaining codes for clean wrapping
        final String stripped = Highlighter.unformat(rest);
        final String[] words = stripped.split(" ");
        final List<String> wrapped = new ArrayList<>();
        final StringBuilder current = new StringBuilder();

        for (final String word : words) {
            if (word.isEmpty()) continue;
            final int newLen = current.length() + (current.isEmpty() ? 0 : 1) + word.length();
            if (newLen > maxW && !current.isEmpty()) {
                wrapped.add(leadIn + current);
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append(' ');
            current.append(word);
            // Handle a single word longer than maxW — hard-break it
            while (current.length() > maxW) {
                wrapped.add(leadIn + current.substring(0, maxW));
                current.delete(0, maxW);
            }
        }
        if (!current.isEmpty()) wrapped.add(leadIn + current);
        return wrapped.isEmpty() ? List.of("") : wrapped;
    }

    /**
     * Clip text to {@code maxW} visible characters, appending {@code …} when
     * truncated.  Preserves leading Graphitty codes so the clip marker inherits
     * the same colour.  A {@code maxW <= 0} means no clipping.
     *
     * @param text the text to clip (may contain Graphitty markup)
     * @param maxW maximum visible characters before the ellipsis
     * @return original text or a clipped version ending in {@code …}
     */
    public static String textClip(final String text, final int maxW) {
        // Collapse newlines to spaces so table rows stay single-line.
        final String collapsed = text.replace('\n', ' ').replace('\r', ' ');
        if (maxW <= 0 || Highlighter.visualLength(collapsed) <= maxW) return collapsed;
        final Matcher m = LEADING_CODES.matcher(collapsed);
        final String leadIn = m.find() ? m.group() : "";
        final String rest = leadIn.isEmpty() ? collapsed : collapsed.substring(leadIn.length());
        final String stripped = Highlighter.unformat(rest);
        return leadIn + stripped.substring(0, Math.max(1, maxW - 1)) + "…";
    }

    public static void runCursorLessWidget(final Widget<?> widget, final boolean close) {
        int height=widget.height();
        Graphitty.log(Widget.class).none("{{.}}");
        widget.run();
        Graphitty.log(Widget.class).none("{{*}}{{^%d}}", height);
        if (close)
            widget.close();
    }

}
