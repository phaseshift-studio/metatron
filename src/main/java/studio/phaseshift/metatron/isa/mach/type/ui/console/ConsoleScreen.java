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

import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.List;

/**
 * The console's screen: content on the way in, bytes on the way out.
 *
 * <p>It joins the three things a screen needs and keeps them in one place — the
 * {@link ScreenBuffer} that remembers the rows, the {@link ScreenPainter} that
 * knows what is on the terminal, and the geometry that says where the region is
 * and how wide a row may be.
 *
 * <p><b>In:</b> {@link #append(String)} takes console output exactly as the console
 * already produces it — Graphitty markup — resolves it, and stores it as rows that
 * each stand on their own: self-contained styling, wrapped to the region's width.
 * That is what lets any single row be repainted later without the rows above it
 * having been drawn first.
 *
 * <p><b>Out:</b> {@link #frame()} is the steady state — the bytes that bring the
 * region up to date, which is nothing at all when nothing changed.
 * {@link #band(int, int)} is the repair — it repaints rows from the content
 * whether or not the painter believes they changed, which is what a floating
 * widget owes the screen after it has drawn over it and moved on.  Without that,
 * erasing a widget leaves blanks where the text it covered used to be, because a
 * terminal cannot be asked what it is holding.
 *
 * <p>Every method is synchronized: rows arrive from job threads while the console
 * thread paints, and the region's geometry can change under both of them.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class ConsoleScreen {

    /**
     * Per-frame accounting, on the same switch the widget renderer uses
     * ({@code -Dmetatron.render.trace=true}): a frame's cost is the thing that
     * decides whether owning the screen is smooth, so it is measurable rather than
     * argued about.
     */
    private static final boolean TRACE = Boolean.getBoolean("metatron.render.trace");
    private final ScreenBuffer buffer;
    private ScreenPainter painter;
    private int top = 1;
    private int rows = 0;
    private int width = 0;

    public ConsoleScreen() {
        this(ScreenBuffer.DEFAULT_CAPACITY);
    }

    public ConsoleScreen(final int capacity) {
        this.buffer = new ScreenBuffer(capacity);
        this.painter = new ScreenPainter(this.top, 0, 0);
    }

    // ── geometry ───────────────────────────────────────────────────

    /**
     * Lay the region out, replacing the painter when the geometry moves: what is
     * on screen is only known for the geometry it was painted in, so a resize (or
     * a status line taking a row) invalidates that knowledge and the next frame
     * repaints in full.
     *
     * @param top   1-based terminal row of the region's first row
     * @param rows  rows the region has
     * @param width columns a row may use
     * @return true when the geometry changed
     */
    public synchronized boolean layout(final int top, final int rows, final int width) {
        final int height = Math.max(0, rows);
        final int columns = Math.max(0, width);
        final boolean moved = top != this.top || height != this.rows || columns != this.width;
        this.top = top;
        this.rows = height;
        this.width = columns;
        if (moved) this.painter = new ScreenPainter(top, height, columns);
        return moved;
    }

    public synchronized int top() {
        return this.top;
    }

    public synchronized int rows() {
        return this.rows;
    }

    public synchronized int width() {
        return this.width;
    }

    // ── content in ─────────────────────────────────────────────────

    /**
     * Append console output, in the markup the console already writes.  It is
     * resolved here (rather than being carried as markup and resolved per frame)
     * so a row holds exactly what will be painted: one row, one paint, no
     * re-parsing of the history.
     *
     * @param markup Graphitty markup, as {@code Console.write} receives it
     */
    public synchronized void append(final String markup) {
        if (null == markup || markup.isEmpty()) return;
        this.appendAnsi(Graphitty.string(markup));
    }

    /**
     * As {@link #append(String)} for text that has already been resolved — what the
     * console's output funnel delivers, since {@code Graphitty.out} resolves before
     * it hands the text on.
     *
     * @param ansi resolved ANSI text (markup would be passed through untouched)
     */
    public synchronized void appendAnsi(final String ansi) {
        if (null == ansi || ansi.isEmpty()) return;
        // Wrap row by row, then hand the rows to the buffer as ONE stream.
        //
        // Appending each row on its own would be wrong in a way that shows: a row handed
        // over as finished cannot be continued, so a write that does not end in a newline
        // ("==>" and the value serialized after it, the pieces of a streamed reply) would
        // start a new row instead of extending the last one — and every newline would add
        // a blank row, because a newline with nothing before it is an empty row and not a
        // "close what is open".  The buffer's own append already has terminal semantics
        // (a newline closes the open row, text with no newline leaves it open); it just
        // needs the text as a stream rather than pre-cut rows.
        final List<String> lines = ScreenPainter.rows(ansi);
        final StringBuilder stream = new StringBuilder(ansi.length() + 16);
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) stream.append('\n');
            final String line = lines.get(i);
            // Output can arrive before the region has been laid out (the boot banner is
            // written while the console is still being constructed), and a row is not
            // discardable just because no width is known yet: it is kept whole and wrapped
            // to whatever width the region turns out to have.  Wrapping to zero would drop
            // it — losing the row, not deferring its layout.
            stream.append(this.width > 0 ? String.join("\n", ScreenPainter.wrap(line, this.width)) : line);
        }
        this.buffer.append(stream.toString());
    }

    // ── bytes out ──────────────────────────────────────────────────

    /** The bytes that bring the region up to date, or "" when nothing changed. */
    public synchronized String frame() {
        final long started = TRACE ? System.nanoTime() : 0;
        final List<String> window = this.buffer.visible(this.rows);
        final String frame = this.painter.frame(window);
        if (TRACE) {
            Console.rawErr().println("[screen] frame: %d rows, %d written, %d bytes, %dms".formatted(
                    this.rows, this.painter.repainted(), frame.length(),
                    (System.nanoTime() - started) / 1_000_000));
            // what the region shows, row by row: a blank row is content, not the absence
            // of it, so the only way to see where blanks come from is to look at them
            for (int i = 0; i < window.size(); i++)
                Console.rawErr().println("[screen] row %02d |%s|".formatted(i + 1, Graphitty.strip(window.get(i))));
        }
        return frame;
    }

    /**
     * Repaint rows {@code from}…{@code to} (1-based terminal rows, clamped to the
     * region) from the content, whether or not the painter believes they changed.
     *
     * <p>This is the repair path: a floating widget that erased its old box wrote
     * blanks over rows the screen still believes it painted, so no frame will
     * touch them again on their own.  Repainting from the buffer puts the text
     * back, and the painter's memory stays correct because it was never wrong —
     * it describes the content, which is the same as what is now on screen.
     *
     * @return ANSI to write, or "" when the region is empty
     */
    public synchronized String band(final int from, final int to) {
        if (this.rows <= 0 || this.width <= 0) return "";
        final List<String> window = this.buffer.visible(this.rows);
        final int first = Math.max(from, this.top);
        final int last = Math.min(to, this.top + this.rows - 1);
        if (first > last) return "";
        final StringBuilder sb = new StringBuilder();
        for (int row = first; row <= last; row++) {
            final int index = row - this.top;
            final String source = index < window.size() ? window.get(index) : "";
            sb.append("\033[").append(row).append(";1H");
            sb.append("\033[0m");
            sb.append(ScreenPainter.printable(source, this.width));
            sb.append("\033[K");
        }
        return sb.toString();
    }

    /** Forget what is on screen, so the next frame repaints every row. */
    public synchronized void invalidate() {
        this.painter.invalidate();
    }

    /**
     * Forget what rows {@code from}…{@code to} hold (1-based terminal rows, clamped to
     * the region): the next frame repaints them from the content, which is how rows a
     * widget erased — or was removed from — are put back.
     */
    public synchronized void forget(final int from, final int to) {
        this.painter.forget(from, to);
    }

    /**
     * True when the visible transcript holds a link.
     *
     * <p>The console needs this to ask for the mouse: a click on a link is the screen's own
     * affordance (it types the uri at the prompt), and a click only reaches the console while
     * the console holds the pointer — with the pointer handed to the terminal, the click is
     * the terminal's, which is how a link used to open "Unsupported operation" in the desktop
     * instead of typing anything.
     */
    public synchronized boolean hasLinks() {
        for (final String row : this.buffer.visible(this.rows))
            if (ScreenPainter.hasLink(row)) return true;
        return false;
    }

    /** How many visible rows hold a link — what {@code :links} reports. */
    public synchronized int linkRows() {
        int n = 0;
        for (final String row : this.buffer.visible(this.rows))
            if (ScreenPainter.hasLink(row)) n++;
        return n;
    }

    /** The rows the screen would show right now — what a frame paints. */
    public synchronized List<String> visible() {
        return this.buffer.visible(this.rows);
    }

    // ── reading history ────────────────────────────────────────────

    public synchronized void scrollBy(final int delta) {
        this.buffer.scrollBy(delta, this.rows);
    }

    public synchronized void scrollToTail() {
        this.buffer.scrollToTail();
    }

    public synchronized boolean following() {
        return this.buffer.following();
    }

    public synchronized int size() {
        return this.buffer.size();
    }

    public synchronized void clear() {
        this.buffer.clear();
    }
}
