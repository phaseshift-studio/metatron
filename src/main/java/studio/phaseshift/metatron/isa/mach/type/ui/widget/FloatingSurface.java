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
import studio.phaseshift.metatron.BootLoader;
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
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT;

        /** Parse a short name (e.g. "top_right", "tr", "bottom_left", "bl"). */
        public static Anchor parse(final String name) {
            if (name == null || name.isEmpty()) return TOP_RIGHT;
            return switch (name.toLowerCase()) {
                case "top_left", "tl"       -> TOP_LEFT;
                case "top_right", "tr"      -> TOP_RIGHT;
                case "bottom_left", "bl"    -> BOTTOM_LEFT;
                case "bottom_right", "br"   -> BOTTOM_RIGHT;
                default                     -> TOP_RIGHT;
            };
        }
    }

    private final Terminal terminal;
    private final Map<Widget<?>, Slot> slots = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.Executor renderExecutor = BootLoader.getExecutor();
            /*java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                final Thread t = new Thread(r, "floating-surface-render");
                t.setDaemon(true);
                return t;
            });*/

    /**
     * Create a floating surface that renders to the given terminal.
     */
    public FloatingSurface(final Terminal terminal) {
        this.terminal = terminal;
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
            if (existing != null) slot.prevHeight = existing.prevHeight;
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
        final Slot existing = this.slots.get(widget);
        final Slot slot = Slot.anchored(anchor, width, top, left);
        if (existing != null) slot.prevHeight = existing.prevHeight;
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
     * Render all floating widgets at their pinned positions.
     *
     * <p>Uses ANSI save/restore cursor ({@code \033[s} / {@code \033[u}) so the
     * caller's cursor position is preserved.  Each widget's content is drawn
     * with absolute cursor positioning and clipped to the terminal width.
     * Leftover lines from a previous taller render are cleared automatically.
     */
    public void render() {
        if (this.slots.isEmpty()) return;
        this.renderExecutor.execute(this::renderInternal);
    }

    private void renderInternal() {
        if (this.slots.isEmpty()) return;
        final int termWidth = this.terminal.getWidth();
        final int termHeight = this.terminal.getHeight();
        final var sb = new StringBuilder(512);

        sb.append("\033[s"); // save cursor

        for (final var entry : this.slots.entrySet()) {
            renderWidget(sb, entry.getKey(), entry.getValue(), termWidth, termHeight);
        }

        sb.append("\033[u"); // restore cursor

        Graphitty.out(this.terminal.output(), sb.toString());
    }

    /**
     * @return true if no widgets are currently pinned to this surface
     */
    public boolean isEmpty() {
        return this.slots.isEmpty();
    }

    /**
     * Remove all widgets and clear their rendered areas.
     */
    public void clear() {
        for (final Slot slot : this.slots.values()) {
            clearSlot(slot);
        }
        this.slots.clear();
        this.terminal.writer().flush();
    }

    // -----------------------------------------------------------------
    // Internal rendering
    // -----------------------------------------------------------------

    /**
     * Render a single widget at its slot position.
     */
    private void renderWidget(final StringBuilder sb, final Widget<?> widget,
                              final Slot slot, final int termWidth, final int termHeight) {
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

        // Resolve anchored position using capped height
        slot.resolve(termHeight, termWidth, effectiveHeight);

        final int maxWidth = Math.max(1, termWidth - slot.lastCol + 1);

        if (slot.lastRow > 1) {
            sb.append("\033[").append(slot.lastRow - 1).append(";").append(slot.lastCol).append("H");
            sb.append("\033[K");
        }

        int i;
        for (i = 0; i < lines.length; i++) {
            sb.append("\033[").append(slot.lastRow + i).append(";").append(slot.lastCol).append("H");
            sb.append("\033[K");
            final String line = lines[i];
            if (Graphitty.viewLength(line) <= maxWidth) {
                sb.append(line);
            } else {
                sb.append(Graphitty.strip(line), 0, Math.max(0, maxWidth - 2));
                //sb.append("...");
            }
        }

        // Clear leftover lines from a previous taller render
        for (; i < slot.prevHeight; i++) {
            sb.append("\033[").append(slot.lastRow + i).append(";").append(slot.lastCol).append("H");
            sb.append("\033[K");
        }

        slot.prevHeight = effectiveHeight;
    }

    /**
     * Clear the terminal area occupied by a slot's last render.
     */
    private void clearSlot(final Slot slot) {
        if (slot.prevHeight <= 0) return;
        final var sb = new StringBuilder(64);
        for (int i = 0; i < slot.prevHeight; i++) {
            sb.append("\033[").append(slot.lastRow + i).append(";").append(slot.lastCol).append("H");
            sb.append("\033[K");
        }
        Graphitty.out(this.terminal.output(), sb.toString());
        this.terminal.writer().flush();
    }

    // -----------------------------------------------------------------
    // Slot: position mode + render-time resolution + height tracking
    // -----------------------------------------------------------------

    private static final class Slot {
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
                case TOP_LEFT, TOP_RIGHT -> 2 + this.offsetRow;
                case BOTTOM_LEFT, BOTTOM_RIGHT -> Math.max(1, termHeight - widgetHeight + 1 - this.offsetRow);
            };
            this.lastCol = switch (this.anchor) {
                case TOP_LEFT, BOTTOM_LEFT -> 1 + this.offsetCol;
                case TOP_RIGHT, BOTTOM_RIGHT -> Math.max(1, termWidth - this.targetWidth + 1 + this.offsetCol);
            };
        }
    }
}
