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
     * @param maxW maximum visible columns per output line
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
        int currentColumns = 0;   // a line's budget is columns, not chars

        for (final String word : words) {
            if (word.isEmpty()) continue;
            final int wordColumns = Highlighter.visualLength(word);
            if (currentColumns + (current.isEmpty() ? 0 : 1) + wordColumns > maxW && !current.isEmpty()) {
                wrapped.add(leadIn + current);
                current.setLength(0);
                currentColumns = 0;
            }
            if (!current.isEmpty()) {
                current.append(' ');
                currentColumns++;
            }
            current.append(word);
            currentColumns += wordColumns;
            // Handle a single word longer than maxW — hard-break it at a column boundary,
            // which is not a char index: a wide glyph costs two columns per one char
            while (currentColumns > maxW) {
                final String pending = current.toString();
                final String head = Graphitty.viewPrefix(pending, maxW);
                wrapped.add(leadIn + head);
                current.delete(0, head.length());
                currentColumns -= Highlighter.visualLength(head);
            }
        }
        if (!current.isEmpty()) wrapped.add(leadIn + current);
        return wrapped.isEmpty() ? List.of("") : wrapped;
    }

    /**
     * Clip text to {@code maxW} visible columns, appending {@code …} when
     * truncated.  Preserves leading Graphitty codes so the clip marker inherits
     * the same colour — and pulls the closers for those codes FORWARD, so a colour
     * the author opened is not left running past the end of the clipped text
     * (see {@link #pulledClosers}).  A {@code maxW <= 0} means no clipping.
     *
     * @param text the text to clip (may contain Graphitty markup)
     * @param maxW maximum visible columns before the ellipsis
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
        return leadIn + Graphitty.viewPrefix(stripped, Math.max(1, maxW - 1)) + "…"
                + pulledClosers(leadIn, rest);
    }

    /** One Graphitty rule — {@code {{g}}}, {@code {{/g}}}, {@code {{b&amp;[k]}}} — and its pieces. */
    private static final Pattern RULE = Pattern.compile("\\{\\{([^{}]*)}}");

    /** The rule that opens a link, which captures text rather than colour. */
    private static final String LINK_RULE = "link";

    /**
     * The rules {@code markup} leaves OPEN, outermost first: a piece opens, {@code /name}
     * closes the rule it names, and {@code X} — the reset — closes everything at once.
     * A {@code link}, a {@code syntax:…} block and an emoji shortcode are not colours and
     * are ignored, exactly as the renderer ignores them.
     */
    private static List<String> openRules(final String markup) {
        final List<String> open = new ArrayList<>();
        final Matcher m = RULE.matcher(markup);
        while (m.find()) {
            for (final String piece : m.group(1).split("&")) {
                if (piece.isEmpty()) continue;
                if ("X".equals(piece)) {
                    open.clear();   // the reset closes every rule at once
                } else if (piece.startsWith("/")) {
                    final String name = piece.substring(1);
                    // the renderer pops the TOP rule and drops a close that names another
                    if (!open.isEmpty() && open.get(open.size() - 1).equals(name))
                        open.remove(open.size() - 1);
                } else if (!piece.equals(LINK_RULE) && !piece.startsWith(LINK_RULE + ":")
                        && !piece.startsWith(Graphitty.SYNTAX_RULE_PREFIX)
                        && !(piece.length() > 2 && ':' == piece.charAt(0) && piece.endsWith(":"))) {
                    open.add(piece);
                }
            }
        }
        return open;
    }

    /**
     * The closers a clipped string still owes: the rules its kept markup leaves open
     * ({@code leadIn}) whose close the clip cut away.
     *
     * <p>The author's own close is pulled FORWARD rather than a reset being invented —
     * {@code {{b}}a title too long…{{/b}}} keeps its blue to the title and no further —
     * so a clip neither leaks a colour past the ellipsis into the rest of the box nor
     * writes a tag the author did not.  {@code {{X}}}, the reset, is a close as well and
     * settles every open rule at once, which is the form most of this codebase writes.
     * A rule the author never closed anywhere is left as authored: that leak is in the
     * source, and the unclipped render has it too.
     *
     * <p>Closers come out in the order they appear in the discarded text, which for
     * well-formed markup is the inside-out order the opens need.
     */
    private static String pulledClosers(final String leadIn, final String discarded) {
        final List<String> open = openRules(leadIn);
        if (open.isEmpty()) return "";
        final StringBuilder closers = new StringBuilder();
        final Set<String> pulled = new HashSet<>();
        final Matcher m = RULE.matcher(discarded);
        while (pulled.size() < open.size() && m.find()) {
            for (final String piece : m.group(1).split("&")) {
                if ("X".equals(piece)) return closers.append("{{X}}").toString();
                if (!piece.startsWith("/")) continue;
                final String name = piece.substring(1);
                if (open.contains(name) && pulled.add(name))
                    closers.append("{{/").append(name).append("}}");
            }
        }
        return closers.toString();
    }

    /**
     * A bordered box's title, clipped to the columns the box leaves it.
     *
     * <p>An explicit {@code style=>[width=>x]} is the box's width, and of
     * everything a box draws the title is the one thing that can be arbitrarily
     * long — so it is the thing that gives way, clipped with an ellipsis, rather
     * than the title widening the box past the width it was told to be.  Every
     * titled box computes its own numbers and clips through here
     * ({@link AccordionWidget}, {@link PanelWidget}, {@link CardWidget}), so the
     * rule is stated once and the boxes cannot drift apart on it.
     *
     * <p>A title is ONE line by definition, so newlines are flattened to spaces: a
     * title carrying one cannot break the box it sits in.  A title that opens a
     * colour and closes it later is clipped as {@code {{b}}the title…{{/b}}} — the
     * close is pulled forward with the rest of the clip (see {@link #textClip}), so
     * the colour stops at the title instead of running on into the box border.
     *
     * @param title  the title as authored (may carry Graphitty markup; the leading
     *               codes are kept so the clip marker inherits the title's colour)
     * @param width  the width the box was told to be — {@code <= 0} when it sizes to
     *               its content, in which case the title comes back whole (a
     *               content-sized box has the columns its title needs)
     * @param chrome the columns of {@code width} the box spends on anything that is
     *               NOT the title: its border sides, its margins, and any glyph it
     *               draws beside the title
     * @return the title as the box draws it — empty when what is left is not enough
     *         for even one column of it (the box's own chrome wins: a toggle glyph
     *         and a border are what the box is FOR)
     */
    public static String titleClip(final String title, final int width, final int chrome) {
        if (null == title || title.isEmpty()) return "";
        final String oneLine = title.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
        if (width <= 0) return oneLine;
        final int room = width - chrome;
        return room <= 0 ? "" : textClip(oneLine, room);
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
