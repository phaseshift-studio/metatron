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

import studio.phaseshift.metatron.isa.mach.type.ui.ScrollView;

import java.util.ArrayList;
import java.util.List;

/**
 * The console's own screen content: the rows it draws itself instead of writing
 * into the terminal and letting them scroll away.
 *
 * <p>A terminal's buffer is write-only — nothing can read a row back out of it —
 * so any widget drawn over terminal-owned text has no way to restore what it
 * covers: erasing the widget leaves blanks where the text used to be.  A buffer
 * the console owns fixes that at the root: every row is reproducible, so a
 * damage rect can be repainted from the content rather than patched over.
 *
 * <p>The viewport follows the rules {@link ScrollView} already defines for
 * widgets, so a reader's expectations hold here too:
 *
 * <ul>
 *   <li><b>Tail by default</b> ({@code follow}) — appended rows stay visible
 *       while you watch, which is what a live console wants.</li>
 *   <li><b>Scrolling back pins an absolute row</b> — content appended below
 *       grows the buffer without dragging the reader's place along, so reading
 *       history is not a fight with an active job.</li>
 *   <li><b>Returning to the tail resumes the follow</b> — no mode to exit.</li>
 *   <li><b>Nothing is lost</b> until the retention cap trims it, and trimming
 *       keeps the reader anchored to the same content it was reading.</li>
 * </ul>
 *
 * <p><b>One element is one row.</b>  A row is what the terminal will draw on one
 * line — the writer wraps long text before appending it — so the viewport counts
 * rows and a wrapped line can never drift the accounting, which is the failure
 * mode of counting newlines and hoping every line took one row.
 *
 * <p>Rows are held for a bounded cost.  The retained rows are addressed through
 * a moving base index, so {@link #visible(int)} is a window over the live list —
 * never a copy of the history — and trimming is amortized constant rather than a
 * shift of the whole buffer per appended row.
 *
 * <p>Every method is synchronized: rows arrive from job threads and the console
 * thread while the reader scrolls, and the terminal is single-writer.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class ScreenBuffer {

    /**
     * How many rows the screen keeps when no other cap is asked for.  Rows are
     * strings, so this is deliberately generous — the cost of history is a few
     * hundred kilobytes, while the cost of losing it is a reader who cannot see
     * what a job just printed.
     */
    public static final int DEFAULT_CAPACITY = 10_000;

    /**
     * How many dropped rows may sit below the base index before the dead prefix
     * is reclaimed.  Compaction is a copy, so it is batched to make trimming
     * amortized constant rather than a whole-buffer move per row.
     */
    private static final int COMPACT_THRESHOLD = 1024;

    private final int capacity;
    private final ArrayList<String> rows = new ArrayList<>();
    /**
     * Index in {@link #rows} of the oldest retained row.  Everything below it is
     * dead (trimmed) and is reclaimed at the next compaction.
     */
    private int base = 0;
    /**
     * The absolute retained-row offset of the viewport's top, held only while
     * {@link #follow} is false — a following viewport derives its offset instead,
     * so it cannot fall behind the tail.
     */
    private int scrollY = 0;
    private boolean follow = true;
    /**
     * True while the newest row is still open — a partial row the next
     * {@link #append(String)} continues, the way a terminal does not start a new
     * line until it sees the newline.
     */
    private boolean partial = false;

    public ScreenBuffer() {
        this(DEFAULT_CAPACITY);
    }

    public ScreenBuffer(final int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    // ── writing ────────────────────────────────────────────────────

    /**
     * Append text the way a terminal takes it: a newline closes the current row,
     * the text after it opens the next, and text with no trailing newline stays
     * open for whatever is appended next.
     *
     * @param text the text to append; {@code null} is ignored
     */
    public synchronized void append(final String text) {
        if (null == text || text.isEmpty()) return;
        final String[] parts = text.split("\n", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i == parts.length - 1) {
                // the tail either continues an open row or opens one; a trailing
                // newline (an empty tail) has already been accounted for by the
                // close of the segment before it, so it appends nothing
                if (!parts[i].isEmpty()) this.openRow(parts[i]);
            } else {
                this.openRow(parts[i]);
                this.partial = false;
            }
        }
    }

    /**
     * Append one complete row — the caller has already wrapped it to the screen
     * width and knows it is a row and not a fragment.
     *
     * @param row the row to append; {@code null} is an empty row
     */
    public synchronized void appendRow(final String row) {
        this.add(null == row ? "" : row);
        this.partial = false;
    }

    /**
     * Continue the open row with {@code fragment}, or open a row with it when the
     * previous row was closed.
     */
    private void openRow(final String fragment) {
        if (this.partial && !this.rows.isEmpty()) {
            this.rows.set(this.rows.size() - 1, this.rows.get(this.rows.size() - 1) + fragment);
        } else {
            this.add(fragment);
        }
        this.partial = true;
    }

    /** Add a row and enforce the retention cap. */
    private void add(final String row) {
        this.rows.add(row);
        final int dropped = this.rows.size() - this.base - this.capacity;
        if (dropped > 0) {
            this.base += dropped;
            // a viewport holding an ABSOLUTE row must lose exactly what was
            // dropped, or the reader's place slides up the screen as old rows
            // age out — the same content, a different row
            if (!this.follow) this.scrollY = Math.max(0, this.scrollY - dropped);
            if (this.base > COMPACT_THRESHOLD) {
                this.rows.subList(0, this.base).clear();
                this.base = 0;
            }
        }
    }

    // ── reading ────────────────────────────────────────────────────

    /**
     * The rows a viewport of {@code rows} rows shows, oldest first and trimmed to
     * exactly that many: content shorter than the viewport is padded <em>above</em> it,
     * so the newest row sits on the viewport's last row.
     *
     * <p>Padding above rather than below is what a console with a fixed prompt line
     * needs.  The prompt does not scroll, so the rows that do scroll are the ones just
     * above it: output arrives there, and the oldest output leaves the top of the
     * screen.  Padding below would instead hang the transcript off the top of the
     * screen with a gap above the prompt, and every new line would appear at the top,
     * far from the prompt that produced it.
     *
     * @param rows visible rows; {@code <= 0} shows nothing
     * @return exactly {@code max(0, rows)} rows
     */
    public synchronized List<String> visible(final int rows) {
        final int count = Math.max(0, rows);
        if (0 == count) return List.of();
        final List<String> retained = this.retained();
        final List<String> window = ScrollView.windowVertically(retained, 0, this.offset(count), count);
        if (window.size() == count) return List.copyOf(window);
        final List<String> padded = new ArrayList<>(count);
        while (padded.size() < count - window.size()) padded.add("");
        padded.addAll(window);
        return List.copyOf(padded);
    }

    /**
     * The absolute retained-row offset the viewport's top sits at: the tail while
     * following, and the pinned row otherwise.
     *
     * @param rows visible rows
     */
    public synchronized int offset(final int rows) {
        final int max = this.maxScroll(rows);
        return this.follow ? max : Math.min(this.scrollY, max);
    }

    /**
     * How many rows of travel the viewport has — 0 when the content fits.
     *
     * @param rows visible rows
     */
    public synchronized int maxScroll(final int rows) {
        return ScrollView.maxY(this.size(), 0, 0, Math.max(0, rows));
    }

    /** How many rows are retained (what a reader can scroll through). */
    public synchronized int size() {
        return this.rows.size() - this.base;
    }

    /** True while the viewport is showing the newest rows. */
    public synchronized boolean following() {
        return this.follow;
    }

    // ── scrolling ──────────────────────────────────────────────────

    /**
     * Move the viewport by {@code delta} rows (negative = back into history),
     * clamped to the content.  Landing on the tail resumes the follow, so
     * scrolling back down needs no separate "return to live" gesture.
     *
     * @param delta rows to move
     * @param rows  visible rows
     */
    public synchronized void scrollBy(final int delta, final int rows) {
        this.scrollTo(this.offset(rows) + delta, rows);
    }

    /**
     * Pin the viewport's top to an absolute retained row, clamped to the content.
     *
     * @param y    absolute retained-row offset
     * @param rows visible rows
     */
    public synchronized void scrollTo(final int y, final int rows) {
        final int max = this.maxScroll(rows);
        this.scrollY = Math.max(0, Math.min(y, max));
        this.follow = this.scrollY >= max;
    }

    /** Show the newest rows again. */
    public synchronized void scrollToTail() {
        this.scrollY = 0;
        this.follow = true;
    }

    /** Drop every row, leaving an empty buffer that follows again. */
    public synchronized void clear() {
        this.rows.clear();
        this.base = 0;
        this.scrollY = 0;
        this.follow = true;
        this.partial = false;
    }

    // ── internals ──────────────────────────────────────────────────

    /**
     * The retained rows as a view of the live list — no copy, so a window over a
     * long history costs the rows it shows rather than the rows it holds.
     */
    private List<String> retained() {
        return this.rows.subList(this.base, this.rows.size());
    }
}
