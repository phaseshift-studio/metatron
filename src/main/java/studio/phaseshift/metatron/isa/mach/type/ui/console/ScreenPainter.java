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

import org.jline.utils.WCWidth;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.ArrayList;
import java.util.List;

/**
 * Paints the screen's region of the terminal from a window of rows, writing only
 * the rows that changed.
 *
 * <p>A terminal is write-only: it will not tell you what a row currently holds, so
 * the only way to know whether a row needs repainting is to remember what was
 * painted there.  That is what this holds — the last painted form of each row of
 * its region — and it is what makes a repaint cheap enough to run on every frame:
 * a frame that changes one row writes one row, and a frame that changes nothing
 * writes nothing at all.
 *
 * <p>It is deliberately a plain painter with no terminal inside it: it takes the
 * rows to show and returns the bytes to write, which is what makes the damage
 * arithmetic testable without a pty.
 *
 * <p>Rows are absolute-positioned ({@code CSI row;1H}) and erased to the end of
 * the line ({@code CSI K}) after being written, so a row that shrinks cannot leave
 * the tail of what it replaced behind.  Both the write and the erase are fenced
 * with a reset: the erase takes the <em>current</em> background, so a colour left
 * active by the row being erased must not be able to tint the blank it leaves —
 * the same leak {@code FloatingSurface} guards against when it erases a widget.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class ScreenPainter {

    /** One whitespace-delimited token of a row, for reading an untagged uri out of it. */

    private final int top;
    private final int rows;
    private final int width;
    /**
     * The last painted form of each row of the region, top first.  Empty means
     * nothing is known to be on screen, so the next frame paints every row.
     */
    private List<String> painted = List.of();
    /** Rows written by the last frame (see {@link #repainted()}). */
    private int repainted = 0;

    /**
     * @param top   1-based terminal row of the region's first row
     * @param rows  how many rows the region has
     * @param width how many columns a row may use
     */
    public ScreenPainter(final int top, final int rows, final int width) {
        this.top = top;
        this.rows = Math.max(0, rows);
        this.width = Math.max(0, width);
    }

    public int top() {
        return this.top;
    }

    public int rows() {
        return this.rows;
    }

    public int width() {
        return this.width;
    }

    /**
     * Forget what is on screen, so the next frame repaints every row.  Used when
     * something other than this painter has written into the region — a resize, a
     * scroll, or any writer that is not the screen.
     */
    public void invalidate() {
        this.painted = List.of();
    }

    /**
     * The rows the painter believes are on screen, top first — each in the clipped
     * form it was written in.  Empty until the first frame.
     */
    public List<String> painted() {
        return this.painted;
    }

    /**
     * The bytes that bring the region up to date with {@code window} — a row per
     * region row, top first — or the empty string when nothing changed.
     *
     * @param window the rows to show; shorter than the region pads with blanks,
     *               longer is ignored
     * @return ANSI to write, positioned to leave the cursor at the end of the last
     * repainted row
     */
    public String frame(final List<String> window) {
        final List<String> painted = new ArrayList<>(this.rows);
        final StringBuilder sb = new StringBuilder();
        int written = 0;
        for (int i = 0; i < this.rows; i++) {
            final String source = null != window && i < window.size() && null != window.get(i) ? window.get(i) : "";
            final String row = printable(source, this.width);
            painted.add(row);
            if (i < this.painted.size() && this.painted.get(i).equals(row)) continue;
            written++;
            sb.append("\033[").append(this.top + i).append(";1H");   // absolute: no scroll, no drift
            sb.append("\033[0m");                                    // never inherit what was left active
            sb.append(row);                                          // clip() closes with its own reset
            sb.append("\033[K");                                     // erase the replaced row's tail
        }
        this.painted = List.copyOf(painted);
        this.repainted = written;
        return sb.toString();
    }

    /**
     * Forget what rows {@code from}…{@code to} hold, so the next frame repaints them
     * whether or not their content changed.
     *
     * <p>This is how someone else's write into the region is made good: the painter
     * cannot detect it (its memory of those rows is only as accurate as its own last
     * write), and a repair that had to be *queued* as bytes could be coalesced away and
     * lost — a forgotten row cannot, because the next frame is obliged to paint it.
     *
     * @param from 1-based first terminal row to forget
     * @param to   1-based last terminal row to forget
     */
    public void forget(final int from, final int to) {
        if (this.painted.isEmpty()) return;
        final List<String> forgotten = new ArrayList<>(this.painted);
        final int first = Math.max(from, this.top);
        final int last = Math.min(to, this.top + this.rows - 1);
        for (int row = first; row <= last; row++) {
            final int index = row - this.top;
            // "" can never be a painted row: every one is closed by clip() with a reset
            if (index >= 0 && index < forgotten.size()) forgotten.set(index, "");
        }
        this.painted = java.util.Collections.unmodifiableList(forgotten);
    }

    /**
     * How many rows the last {@link #frame(List)} actually wrote — the difference
     * between a frame that keeps up (one row changed) and one that repaints the
     * region, which is what a measurement needs to be able to tell apart.
     */
    public int repainted() {
        return this.repainted;
    }

    // ── cutting a row to its width ─────────────────────────────────

    /**
     * Cut a row to {@code width} columns, keeping the styling of the part that
     * survives and closing with a reset.
     *
     * <p>The cut is by <em>column</em>, not character: an escape sequence takes no
     * columns, a wide glyph takes two, and a wide glyph that does not fit is
     * dropped rather than split in half.  This is the one thing the viewport
     * helpers cannot do — {@code ScrollView.windowHorizontally} strips a line
     * before windowing it, which is the right trade when a reader has scrolled
     * sideways, and the wrong one for a row being painted as the screen.
     *
     * @param ansi  the row, styled
     * @param width columns available
     * @return the row, guaranteed no wider than {@code width}
     */
    public static String clip(final String ansi, final int width) {
        return wrap(ansi, width).get(0);
    }

    /**
     * A row as the terminal should receive it: the same text, with any hyperlink sequence
     * dropped.
     *
     * <p>A row keeps its OSC 8 hyperlink (<em>that</em> is how a click finds the uri), but it
     * must not be <em>sent</em> as one: a terminal that sees a hyperlink takes the click for
     * itself and opens the uri with the desktop — "Unsupported operation" for a uri only
     * metatron understands — so the click never reaches the console that meant to handle it.
     * The underline stays, which is what makes the text read as a link.
     */
    public static String printable(final String row, final int width) {
        return clip(withoutLinks(row), width);
    }

    /**
     * The row with its terminal commands gone — cursor moves, line clears, mode
     * sets — keeping SGR styling and OSC sequences, which the screen stores and
     * answers.
     *
     * <p>This is the row the screen records: a cursor move stored inside a row
     * would run <em>mid-paint</em> — the terminal obeys it at the wrong moment —
     * and a line clear would erase whatever the painter had just written, so
     * every command a widget emitted is kept only as the motion it caused a
     * live frame, never as content.
     */
    public static String noCommands(final String ansi) {
        if (null == ansi || ansi.isEmpty() || ansi.indexOf('\033') < 0) return ansi;
        final StringBuilder out = new StringBuilder(ansi.length());
        int i = 0;
        while (i < ansi.length()) {
            if ('\033' != ansi.charAt(i)) {
                out.append(ansi.charAt(i));
                i++;
                continue;
            }
            final int end = escapeEnd(ansi, i);
            final String seq = ansi.substring(i, end);
            final char kind = i + 1 < ansi.length() ? ansi.charAt(i + 1) : 0;
            if (']' == kind || (kind == '[' && isSgr(seq)))
                out.append(seq);
            i = end;
        }
        return out.toString();
    }

    /**
     * The row with its OSC 8 hyperlink sequences removed — the uri survives only in the
     * stored row, for a click to read back out of it (see {@link #linkAt(String, int)}).
     */
    public static String withoutLinks(final String row) {
        if (null == row || row.isEmpty() || row.indexOf('\033') < 0) return row;
        final StringBuilder out = new StringBuilder(row.length());
        int i = 0;
        while (i < row.length()) {
            if ('\033' == row.charAt(i)) {
                final int end = escapeEnd(row, i);
                if (!row.startsWith("\033]8;", i)) out.append(row, i, end);
                i = end;
                continue;
            }
            out.append(row.charAt(i));
            i++;
        }
        return out.toString();
    }

    /**
     * Break a styled row into rows that each fit {@code width} columns, keeping
     * the styling running across the break: whatever was in force where a row
     * ended is re-asserted where the next one begins, so the pieces read as one
     * wrapped line rather than as a line that lost its colour halfway.
     *
     * <p>A glyph wider than the whole row is dropped — there is no row it could be
     * drawn in — and every returned row is closed with a reset.
     *
     * @param ansi  the row, styled
     * @param width columns available; {@code <= 0} returns one empty row
     * @return one or more rows, each no wider than {@code width}
     */
    public static List<String> wrap(final String ansi, final int width) {
        if (null == ansi || ansi.isEmpty() || width <= 0) return List.of("");
        final List<String> rows = new ArrayList<>();
        final StringBuilder row = new StringBuilder(ansi.length() + 4);
        final StringBuilder active = new StringBuilder();
        int columns = 0;
        int i = 0;
        while (i < ansi.length()) {
            if ('\033' == ansi.charAt(i)) {
                final int end = escapeEnd(ansi, i);
                final String sequence = ansi.substring(i, end);
                row.append(sequence);
                if (isSgr(sequence)) {
                    if (isReset(sequence)) active.setLength(0);
                    else active.append(sequence);
                }
                i = end;
                continue;
            }
            final int cp = ansi.codePointAt(i);
            final int w = Math.max(0, WCWidth.wcwidth(cp));
            if (columns + w > width) {
                rows.add(row.append("\033[0m").toString());
                row.setLength(0);
                row.append(active);
                columns = 0;
            }
            if (w <= width) {
                row.appendCodePoint(cp);
                columns += w;
            }
            i += Character.charCount(cp);
        }
        // the last piece is always a row: the flush above only happens when
        // something was left over to place, and a row of nothing but styling is
        // still a row (it paints a blank line in its colour)
        rows.add(row.append("\033[0m").toString());
        return List.copyOf(rows);
    }

    /**
     * Split resolved ANSI text into rows that each stand on their own: whatever
     * styling was in force when a row began is re-asserted at its start.
     *
     * <p>A screen repaints rows independently — that is the whole point of a
     * damage rect — so a row cannot rely on the row above it having been drawn
     * first to carry its colour, which is exactly what a plain {@code split("\n")}
     * of a multi-line styled string would assume.
     *
     * @param ansi resolved ANSI text
     * @return one entry per line, styling self-contained
     */
    public static List<String> rows(final String ansi) {
        if (null == ansi || ansi.isEmpty()) return List.of();
        final List<String> rows = new ArrayList<>();
        final StringBuilder row = new StringBuilder();
        final StringBuilder active = new StringBuilder();
        int i = 0;
        while (i < ansi.length()) {
            final char c = ansi.charAt(i);
            if ('\n' == c) {
                // a CRLF newline: the carriage return is the terminal's, not the row's,
                // and keeping it would count a phantom column in every measurement
                if (!row.isEmpty() && '\r' == row.charAt(row.length() - 1))
                    row.setLength(row.length() - 1);
                rows.add(row.toString());
                row.setLength(0);
                row.append(active);
                i++;
                continue;
            }
            if ('\033' == c) {
                final int end = escapeEnd(ansi, i);
                final String sequence = ansi.substring(i, end);
                row.append(sequence);
                if (isSgr(sequence)) {
                    if (isReset(sequence)) active.setLength(0);
                    else active.append(sequence);
                }
                i = end;
                continue;
            }
            row.append(c);
            i++;
        }
        rows.add(row.toString());
        return List.copyOf(rows);
    }

    /**
     * The uri of the link covering {@code column} of {@code row}, or null when the row has
     * no link there.
     *
     * <p>Rows keep the OSC 8 sequences their text was rendered with — the terminal needs
     * them to be a hyperlink — so the spans are read back out of the row rather than tracked
     * beside it: the row is what is displayed, and a span recorded separately could drift
     * from what a reader is pointing at.
     *
     * @param row    the row as stored (styling included)
     * @param column 0-based column within the row
     */
    /**
     * Whether a row holds a clickable uri — the console arms the mouse on this, so it must agree
     * with {@link #linkAt} exactly.
     * <p>
     * One source only: a span the console's serializer tagged.  A uri becomes clickable because
     * {@code ObjConsoleSerializer.writeUri} wrapped it in {@code {{link}}}, which renders as OSC 8 —
     * i.e. because the console <em>serialized a uri</em>.  Text that merely resembles one is text:
     * the console does not parse its own output as objs, so a path written into a string, a log
     * message or an echoed line is left for the reader to select and copy.
     */
    public static boolean hasLink(final String row) {
        return Graphitty.linkClickable() && null != row && row.contains("\033]8;");
    }

    public static String linkAt(final String row, final int column) {
        if (!Graphitty.linkClickable() || null == row || row.isEmpty() || column < 0) return null;
        String open = null;
        int start = 0;
        int columns = 0;
        int i = 0;
        while (i < row.length()) {
            if ('\033' == row.charAt(i)) {
                final int end = escapeEnd(row, i);
                final String sequence = row.substring(i, end);
                if (sequence.startsWith("\033]8;")) {
                    final String uri = osc8Uri(sequence);
                    if (null == uri || uri.isEmpty()) {
                        if (null != open && column >= start && column < columns) return open;
                        open = null;
                    } else {
                        if (null != open && column >= start && column < columns) return open;
                        open = uri;
                        start = columns;
                    }
                }
                i = end;
                continue;
            }
            final int cp = row.codePointAt(i);
            columns += Math.max(0, WCWidth.wcwidth(cp));
            i += Character.charCount(cp);
        }
        if (null != open && column >= start && column < columns) return open;
        return null;
    }

    /** The uri an OSC 8 sequence carries, or "" for the sequence that closes a hyperlink. */
    private static String osc8Uri(final String sequence) {
        int from = sequence.indexOf(';');
        if (from < 0) return "";
        from = sequence.indexOf(';', from + 1);
        if (from < 0) return "";
        int to = sequence.length();
        final char last = sequence.charAt(to - 1);
        if (0x07 == last) to--;
        else if (0x1b == last) to -= 2;   // a string terminator rather than a BEL
        return from + 1 <= to ? sequence.substring(from + 1, to) : "";
    }

    /** True for a select-graphic-rendition sequence ({@code CSI … m}). */
    private static boolean isSgr(final String sequence) {
        return sequence.length() > 2 && 'm' == sequence.charAt(sequence.length() - 1);
    }

    /**
     * True when an SGR sequence resets everything — {@code CSI m}, {@code CSI 0m},
     * or any parameter list carrying a 0, since a 0 in SGR clears every attribute.
     */
    private static boolean isReset(final String sequence) {
        final String params = sequence.substring(2, sequence.length() - 1);
        if (params.isEmpty()) return true;
        for (final String param : params.split(";"))
            if (param.isEmpty() || "0".equals(param)) return true;
        return false;
    }

    /**
     * The index just past the escape sequence starting at {@code start}: a CSI
     * runs to its final byte, an OSC to BEL or ST, and anything else is the two
     * characters of the escape.
     */
    private static int escapeEnd(final String ansi, final int start) {
        final int length = ansi.length();
        if (start + 1 >= length) return length;
        final char kind = ansi.charAt(start + 1);
        if ('[' == kind) {
            for (int i = start + 2; i < length; i++)
                if (ansi.charAt(i) >= 0x40 && ansi.charAt(i) <= 0x7e) return i + 1;
            return length;
        }
        if (']' == kind) {
            for (int i = start + 2; i < length; i++) {
                final char c = ansi.charAt(i);
                if (0x07 == c) return i + 1;
                if (0x1b == c) return Math.min(length, i + 2);
            }
            return length;
        }
        return Math.min(length, start + 2);
    }
}
