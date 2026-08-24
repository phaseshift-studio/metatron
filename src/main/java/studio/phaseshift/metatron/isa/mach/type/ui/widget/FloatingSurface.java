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

import org.jline.terminal.Terminal;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.Map;

/**
 * A rendering surface that draws {@link Widget} instances at fixed or
 * terminal-size-aware positions, independent of any pane or layout tree.
 *
 * <p>Widgets rendered through a {@code FloatingSurface} appear to "float" over
 * the terminal content — they are drawn after the main render pass and
 * overwrite whatever characters are at their target rows and columns.  The
 * surface tracks each widget's previous render height so that subsequent
 * redraws automatically clear leftover lines from taller prior renders.
 *
 * <p>Positioning is either <b>absolute</b> (exact row and column) or
 * <b>anchored</b> (a corner of the terminal, recalculated on every render so
 * the widget stays pinned after terminal resize):
 *
 * <pre>{@code
 *   FloatingSurface surface = new FloatingSurface(terminal);
 *
 *   // Absolute — pin at row 3, col 50:
 *   accordion.floatAt(surface, 3, 50);
 *
 *   // Anchored — pin to top-right, 40 columns wide:
 *   table.floatAt(surface, FloatingSurface.Anchor.TOP_RIGHT, 40);
 *
 *   surface.render();
 * }</pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class FloatingSurface {

    /**
     * Terminal-edge anchors for size-aware positioning.
     * The widget's column is computed from the anchor and the target width;
     * its row is determined by the anchor and the widget's own height
     * (so bottom-anchored widgets stay flush with the terminal bottom).
     */
    public enum Anchor {
        TOP_LEFT,
        TOP_MIDDLE,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_MIDDLE,
        BOTTOM_RIGHT;

        /** Parse a short name (e.g. "top_right", "tr", "bottom_middle", "bm"). */
        public static Anchor parse(final String name) {
            if (name == null || name.isEmpty()) return TOP_RIGHT;
            return switch (name.toLowerCase()) {
                case "top_left", "tl"        -> TOP_LEFT;
                case "top_middle", "tm"      -> TOP_MIDDLE;
                case "top_right", "tr"       -> TOP_RIGHT;
                case "bottom_left", "bl"     -> BOTTOM_LEFT;
                case "bottom_middle", "bm"   -> BOTTOM_MIDDLE;
                case "bottom_right", "br"    -> BOTTOM_RIGHT;
                default                      -> TOP_RIGHT;
            };
        }
    }

    private final Terminal terminal;
    private final Map<Widget<?>, Slot> slots = new java.util.concurrent.ConcurrentHashMap<>();

    // ── Render thread + queue ──────────────────────────────────────
    // ALL terminal writes are serialized through this single daemon.
    // Console output:  submitAndWait  (blocks caller until written)
    // Widget renders:  submit         (fire-and-forget, coalesced)

    private final java.util.concurrent.BlockingQueue<Runnable> renderQueue =
            new java.util.concurrent.LinkedBlockingQueue<>();
    private final Thread renderThread;
    private volatile boolean running = true;

    /** Coalesce rapid-fire render() calls: only one render task can be
     *  queued at a time.  After it completes, the next render() call
     *  will queue a fresh task with the latest widget state. */
    private final java.util.concurrent.atomic.AtomicBoolean renderQueued =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /** Terminal scrolls (output newlines written at the bottom) accumulated
     *  since the last widget render pass.  Consumed by {@link #renderInternal}
     *  to erase each widget's previous representation where the scroll carried
     *  it, so stale copies don't rise up the screen. */
    private final java.util.concurrent.atomic.AtomicInteger scrollAccum =
            new java.util.concurrent.atomic.AtomicInteger(0);

    {
        renderThread = new Thread(() -> {
            while (running) {
                try {
                    renderQueue.take().run();
                } catch (final InterruptedException e) {
                    if (!running) break;
                } catch (final Throwable t) {
                    // Never let the render thread die — an uncaught exception
                    // would silently kill the daemon and stall every caller
                    // blocked in submitAndWait.
                    System.err.println("[terminal-writer] task threw: " + t.getMessage());
                }
            }
            Runnable tail;
            while ((tail = renderQueue.poll()) != null) {
                try { tail.run(); } catch (final Throwable t) { /* drain quietly */ }
            }
        }, "terminal-writer");
        renderThread.setDaemon(true);
        renderThread.start();
    }

    /** Fire-and-forget: enqueue a task for the render thread. */
    private void submit(final Runnable task) {
        this.renderQueue.offer(task);
    }

    /** Maximum seconds to wait for the render thread before falling back
     *  to a direct terminal write.  Kept short because the calling thread
     *  (often the console REPL) freezes while waiting. */
    private static final long SUBMIT_TIMEOUT_SECONDS = 3;

    /** Enqueue a task and block until it completes.  If the render thread
     *  is stalled (e.g. blocked on a slow Router write inside format()),
     *  falls back to a direct write after the timeout. */
    private void submitAndWait(final Runnable task) {
        if (Thread.currentThread() == this.renderThread) {
            task.run();
            return;
        }
        final var latch = new java.util.concurrent.CountDownLatch(1);
        this.renderQueue.offer(() -> {
            try { task.run(); } finally { latch.countDown(); }
        });
        try {
            if (!latch.await(SUBMIT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)) {
                System.err.println("[terminal-writer] timed out — direct write");
                synchronized (this.terminal) { task.run(); }
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            synchronized (this.terminal) { task.run(); }
        }
    }

    /**
     * Write text at the current cursor position and block until complete.
     * This is the single entry point for console output — log messages,
     * prompts, agent responses — all serialized on the render thread.
     *
     * <p>The text arrives already Graphitty-processed by the bridge, so
     * we write it directly without a second expansion pass.
     */
    public void writeToTerminal(final String text) {
        submitAndWait(() -> {
            // Console output is written at the bottom of the terminal, so each
            // newline scrolls the screen, carrying pinned widgets up with it.
            // Count those scrolls so the next render pass can erase each
            // widget's previous representation at its scrolled position.
            this.scrollAccum.addAndGet((int) text.chars().filter(c -> c == '\n').count());
            this.terminal.writer().print(text);
            this.terminal.writer().flush();
        });
    }

    /**
     * Create a floating surface that renders to the given terminal.
     */
    public FloatingSurface(final Terminal terminal) {
        this.terminal = terminal;
        // Route ALL terminal writes through our render thread so widget
        // push/pop cursor sequences are never interleaved with console output.
        Graphitty.setTerminalWriter(this::writeToTerminal);
    }

    // -----------------------------------------------------------------
    // Public API — absolute positioning
    // -----------------------------------------------------------------

    /**
     * Pin a widget at exact terminal coordinates.
     * The widget will be drawn at this position on every {@link #render()} call.
     * If the widget is already pinned, its position is updated.
     *
     * @param widget the widget to float
     * @param row    1-based terminal row
     * @param col    1-based terminal column
     */
    public void add(final Widget<?> widget, final int row, final int col) {
        final Slot existing = this.slots.get(widget);
        if (existing != null && !existing.isAnchored()) {
            existing.lastRow = row;
            existing.lastCol = col;
        } else {
            final Slot slot = Slot.fixed(row, col);
            if (existing != null) {
                slot.prevHeight = existing.prevHeight;
                slot.prevWidth = existing.prevWidth;
            }
            this.slots.put(widget, slot);
        }
    }

    // -----------------------------------------------------------------
    // Public API — anchored positioning
    // -----------------------------------------------------------------

    /**
     * Pin a widget to a terminal corner with a target display width.
     * The actual position is recalculated on every {@link #render()} call
     * from the current terminal dimensions and the widget's height, so the
     * widget stays pinned to its corner across terminal resizes.
     *
     * @param widget the widget to float
     * @param anchor which corner of the terminal to pin to
     * @param width  the column width the widget should occupy
     *               (used for horizontal positioning and content clipping)
     */
    public void add(final Widget<?> widget, final Anchor anchor, final int width) {
        add(widget, anchor, width, 0, 0);
    }

    public void add(final Widget<?> widget, final Anchor anchor, final int width,
                    final int top, final int left) {
        // Re-floating an anchored widget replaces any slot already pinned at the
        // same anchor position.  Anchored widgets are re-floated on every
        // update (e.g. @<think_widget>>>=[body=>...].display()), and each float
        // constructs a fresh widget instance — without this, stale copies stack
        // up and render over each other, doubling borders and never refreshing.
        Slot replaced = null;
        for (final Slot s : this.slots.values()) {
            if (s.isAnchored() && s.anchor == anchor && s.offsetRow == top && s.offsetCol == left) {
                replaced = s;
                break;
            }
        }
        if (replaced != null)
            this.slots.values().remove(replaced);
        final Slot slot = Slot.anchored(anchor, width, top, left);
        if (replaced != null) {
            // Carry the previous render region so the first redraw erases the
            // old content (a fresh slot with prevHeight 0 would leave it behind).
            slot.prevHeight = replaced.prevHeight;
            slot.prevWidth = replaced.prevWidth;
            slot.lastRow = replaced.lastRow;
            slot.lastCol = replaced.lastCol;
        }
        this.slots.put(widget, slot);
    }

    // -----------------------------------------------------------------
    // Public API — lifecycle
    // -----------------------------------------------------------------

    /**
     * Remove a widget from the surface.  The area it last occupied is cleared
     * immediately so no stale content lingers.
     *
     * @param widget the widget to stop floating
     */
    public void remove(final Widget<?> widget) {
        final Slot slot = this.slots.remove(widget);
        if (slot != null) {
            clearSlot(slot);
        }
    }

    /**
     * Render all floating widgets at their pinned positions.  Submits
     * asynchronously; coalesces rapid calls so the queue never fills
     * with redundant render tasks that would starve console writes.
     */
    public void render() {
        if (this.slots.isEmpty()) return;
        if (!this.renderQueued.compareAndSet(false, true)) return;
        submit(() -> {
            try {
                renderInternal();
            } finally {
                this.renderQueued.set(false);
            }
        });
    }

    /** Runs on the render thread.  Builds save-cursor + widgets + restore-cursor
     *  in one StringBuilder, expands {{X}} codes, and writes atomically. */
    private void renderInternal() {
        // Consume the scroll accumulated since the last pass so each widget's
        // stale copy — carried up by scrolled console output — gets erased.
        final int scroll = this.scrollAccum.getAndSet(0);
        if (this.slots.isEmpty()) return;
        final int termWidth = this.terminal.getWidth();
        final int termHeight = this.terminal.getHeight();
        final var sb = new StringBuilder(512);

        sb.append("\033[s"); // save cursor

        // Draw lower z-index widgets first so higher z-index widgets (e.g.
        // menu bars) render on top when their regions overlap.  Stable sort:
        // equal-z widgets keep their current iteration order.
        final var ordered = new java.util.ArrayList<>(this.slots.entrySet());
        ordered.sort(java.util.Comparator.comparingInt(e -> {
            final var s = e.getKey().getStyle();
            return s == null ? 0 : s.zIndex();
        }));

        for (final var entry : ordered) {
            final Widget<?> widget = entry.getKey();
            if (isDeleted(widget)) {
                eraseSlot(sb, entry.getValue());
                this.slots.remove(widget);
                continue;
            }
            renderWidget(sb, widget, entry.getValue(), termWidth, termHeight, scroll);
        }

        sb.append("\033[u"); // restore cursor

        // Process {{X}} codes → ANSI, then write directly (bypass bridge)
        final String processed = Graphitty.string(sb.toString());
        synchronized (this.terminal) {
            this.terminal.writer().print(processed);
            this.terminal.writer().flush();
        }
    }

    /**
     * Shut down the render thread.  After this, no more terminal writes
     * will be processed.
     */
    public void shutdown() {
        this.running = false;
        this.renderThread.interrupt();
    }

    /**
     * @return true if the given widget is currently pinned to this surface
     */
    public boolean contains(final Widget<?> widget) {
        return this.slots.containsKey(widget);
    }

    /**
     * @return true if no widgets are currently pinned to this surface
     */
    public boolean isEmpty() {
        return this.slots.isEmpty();
    }

    /**
     * @return the number of widgets currently pinned to this surface
     */
    int size() {
        return this.slots.size();
    }

    /**
     * Remove all widgets and clear their rendered areas.
     */
    public void clear() {
        for (final Slot slot : this.slots.values()) {
            clearSlot(slot);
        }
        this.slots.clear();
    }

    // -----------------------------------------------------------------
    // Internal rendering
    // -----------------------------------------------------------------

    /**
     * True when the widget's backing space record has been deleted.
     */
    private boolean isDeleted(final Widget<?> widget) {
        if (!(widget instanceof Obj obj)) return false;
        final fURI vid = obj.vid();
        if (null == vid) return false;
        try {
            return Router.global().read(vid).isNoObj();
        } catch (final Exception e) {
            return false;
        }
    }

    /**
     * Append the erase sequences for a slot's last render to {@code sb}.
     * Clearing is scoped to the widget's own columns (its previous width),
     * never to end-of-line, so widgets rendered at other columns on the
     * same rows are left intact.
     */
    private void eraseSlot(final StringBuilder sb, final Slot slot) {
        if (slot.prevHeight > 0 && slot.lastRow > 0) {
            final int col = slot.lastCol > 0 ? slot.lastCol : 1;
            for (int r = 0; r < slot.prevHeight; r++) {
                sb.append("\033[").append(slot.lastRow + r).append(";").append(col).append("H");
                sb.append(" ".repeat(Math.max(0, slot.prevWidth)));
            }
        }
    }

    /**
     * Render a single widget at its slot position.
     */
    private void renderWidget(final StringBuilder sb, final Widget<?> widget,
                              final Slot slot, final int termWidth, final int termHeight,
                              final int scroll) {
        final String formatted = widget.format();
        String[] lines = formatted.split("\n", -1);

        // Apply height cap — keep header lines + last N body lines (scroll-up)
        final int heightCap = widget.getStyle().height();
        final int effectiveHeight;
        if (heightCap > 0 && lines.length > heightCap) {
            final int header = widget.chromeLines();
            if (header > 0 && header < heightCap && header < lines.length) {
                // Preserve header: keep first `header` lines + last (heightCap - header) of the rest
                final int bodyKeep = heightCap - header;
                final String[] clipped = new String[heightCap];
                System.arraycopy(lines, 0, clipped, 0, header);
                System.arraycopy(lines, lines.length - bodyKeep, clipped, header, bodyKeep);
                lines = clipped;
                effectiveHeight = heightCap;
            } else {
                lines = java.util.Arrays.copyOfRange(lines, lines.length - heightCap, lines.length);
                effectiveHeight = heightCap;
            }
        } else {
            effectiveHeight = lines.length;
        }

        // Snapshot the old render region before resolve() mutates the slot.
        // Bottom-anchored widgets shift lastRow when height changes, so we
        // must use the pre-resolve coordinates for erasure.
        final int oldLastRow = slot.lastRow;
        final int oldLastCol = slot.lastCol;
        final int oldPrevHeight = slot.prevHeight;
        final int oldPrevWidth = slot.prevWidth;

        slot.resolve(termHeight, termWidth, effectiveHeight);

        final int maxWidth = Math.max(1, termWidth - slot.lastCol + 1);
        final int newWidth = renderedWidth(lines, maxWidth);

        // Scroll compensation: console output written at the bottom scrolled
        // the terminal by `scroll` rows since this widget was last drawn,
        // carrying its previous representation up the screen.  Erase it where
        // it now sits — within the widget's own columns — so stale copies
        // never rise row-by-row with each new output line.
        if (scroll > 0 && oldPrevHeight > 0) {
            final int staleRow = oldLastRow - scroll;
            final int staleHeight = Math.min(oldPrevHeight, scroll);
            if (staleRow >= 1) {
                for (int r = 0; r < staleHeight; r++) {
                    sb.append("\033[").append(staleRow + r).append(";").append(oldLastCol).append("H");
                    sb.append(" ".repeat(Math.max(0, oldPrevWidth)));
                }
            }
        }

        // Erase stale content from the old footprint.  Clearing is scoped to
        // the widget's own columns — never to end-of-line — so a widget
        // anchored at another column on the same row (e.g. a tall BOTTOM_RIGHT
        // widget under a TOP_LEFT widget) is never blanked.  Rows fully
        // covered by new content are skipped; the new lines overwrite them.
        // Clearing and rendering share one StringBuilder (single atomic
        // terminal write) so there is no flicker.
        if (oldPrevHeight > 0 && oldLastRow > 0) {
            final boolean sameCol = oldLastCol == slot.lastCol;
            for (int r = 0; r < oldPrevHeight; r++) {
                final int row = oldLastRow + r;
                int covered = 0;
                if (sameCol) {
                    final int newIndex = row - slot.lastRow;
                    if (newIndex >= 0 && newIndex < lines.length)
                        covered = writtenWidth(lines[newIndex], maxWidth);
                }
                if (covered >= oldPrevWidth) continue;
                sb.append("\033[").append(row).append(";").append(oldLastCol).append("H");
                sb.append(" ".repeat(Math.max(0, oldPrevWidth - covered)));
            }
        }

        // Buffer zone: keep the row directly above the widget clean, scoped
        // to the widget's own width so the row is never blanked beyond it.
        if (slot.lastRow > 1) {
            sb.append("\033[").append(slot.lastRow - 1).append(";").append(slot.lastCol).append("H");
            sb.append(" ".repeat(Math.max(0, newWidth)));
        }

        for (int i = 0; i < lines.length; i++) {
            sb.append("\033[").append(slot.lastRow + i).append(";").append(slot.lastCol).append("H");
            final String line = lines[i];
            if (Graphitty.viewLength(line) <= maxWidth) {
                sb.append(line);
            } else {
                sb.append(Graphitty.strip(line), 0, Math.max(0, maxWidth - 2));
                //sb.append("...");
            }
        }

        slot.prevHeight = effectiveHeight;
        slot.prevWidth = newWidth;
    }

    /**
     * Visual width of the widest line actually drawn — the number of columns
     * the widget occupies.  Clipped lines count their clipped length.
     */
    private static int renderedWidth(final String[] lines, final int maxWidth) {
        int width = 0;
        for (final String line : lines) {
            width = Math.max(width, writtenWidth(line, maxWidth));
        }
        return width;
    }

    /**
     * The number of columns {@code line} occupies when drawn: its full visual
     * length when it fits within {@code maxWidth}, otherwise the clipped
     * {@code maxWidth - 2} that {@link #renderWidget} actually writes.
     */
    private static int writtenWidth(final String line, final int maxWidth) {
        final int len = Graphitty.viewLength(line);
        return len <= maxWidth ? len : Math.max(0, maxWidth - 2);
    }

    /**
     * Clear the terminal area occupied by a slot's last render.  Scoped to
     * the widget's own columns so overlapping widgets are never blanked.
     */
    private void clearSlot(final Slot slot) {
        if (slot.prevHeight <= 0) return;
        submitAndWait(() -> {
            final var sb = new StringBuilder(64);
            for (int i = 0; i < slot.prevHeight; i++) {
                sb.append("\033[").append(slot.lastRow + i).append(";").append(slot.lastCol).append("H");
                sb.append(" ".repeat(Math.max(0, slot.prevWidth)));
            }
            synchronized (this.terminal) {
                this.terminal.writer().print(sb.toString());
                this.terminal.writer().flush();
            }
        });
    }

    // -----------------------------------------------------------------
    // Slot: position mode + render-time resolution + height tracking
    // -----------------------------------------------------------------

    static final class Slot {
        // ---- fixed mode ----
        final Integer fixedRow;
        final Integer fixedCol;

        // ---- anchored mode ----
        final Anchor anchor;
        final int targetWidth;
        final int offsetRow;
        final int offsetCol;

        // ---- computed each render ----
        int lastRow;
        int lastCol;
        int prevHeight;
        int prevWidth;

        private Slot(final Integer fixedRow, final Integer fixedCol,
                     final Anchor anchor, final int targetWidth,
                     final int offsetRow, final int offsetCol) {
            this.fixedRow = fixedRow;
            this.fixedCol = fixedCol;
            this.anchor = anchor;
            this.targetWidth = targetWidth;
            this.offsetRow = offsetRow;
            this.offsetCol = offsetCol;
        }

        static Slot fixed(final int row, final int col) {
            final Slot s = new Slot(row, col, null, 0, 0, 0);
            s.lastRow = row;
            s.lastCol = col;
            return s;
        }

        static Slot anchored(final Anchor anchor, final int targetWidth,
                             final int offsetRow, final int offsetCol) {
            return new Slot(null, null, anchor, targetWidth, offsetRow, offsetCol);
        }

        boolean isAnchored() {
            return this.anchor != null;
        }

        /**
         * Compute {@link #lastRow} and {@link #lastCol} from the current
         * terminal dimensions and widget height.  For fixed slots this is
         * a no-op (lastRow/lastCol are set once at construction).
         */
        void resolve(final int termHeight, final int termWidth, final int widgetHeight) {
            if (this.anchor == null) return;

            this.lastRow = switch (this.anchor) {
                case TOP_LEFT, TOP_MIDDLE, TOP_RIGHT -> 2 + this.offsetRow;
                case BOTTOM_LEFT, BOTTOM_MIDDLE, BOTTOM_RIGHT -> Math.max(1, termHeight - widgetHeight + 1 - this.offsetRow);
            };
            this.lastCol = switch (this.anchor) {
                case TOP_LEFT, BOTTOM_LEFT -> 1 + this.offsetCol;
                case TOP_MIDDLE, BOTTOM_MIDDLE -> Math.max(1, (termWidth - this.targetWidth) / 2 + this.offsetCol);
                case TOP_RIGHT, BOTTOM_RIGHT -> Math.max(1, termWidth - this.targetWidth + 1 + this.offsetCol);
            };
        }
    }
}
