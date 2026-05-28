/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

/**
 * An opaque rendering surface handed to widgets during each redraw cycle.
 *
 * <p>Widget authors call {@link #line}, {@link #blankLine}, and {@link #statusLine}
 * to describe <em>what</em> they want to render.  The canvas decides <em>how</em>:
 *
 * <ul>
 *   <li><b>Absolute mode</b> – when pane bounds have been set on the owning widget,
 *       every write is confined to the pane's inner content area using absolute
 *       ANSI cursor-positioning sequences.  Lines that would overflow the pane are
 *       silently dropped; lines are clipped to the pane width.
 *   <li><b>Relative mode</b> – when no pane bounds are set, the classic
 *       cursor-up-then-print-with-clear approach is used so the widget works
 *       correctly in a single-pane console.
 * </ul>
 *
 * <p>Widget authors never need to inspect {@link AbstractWidget#hasPaneBounds()} or
 * emit any positioning escape sequences themselves.
 *
 * <p>Usage pattern inside a widget's {@code redraw()} method:
 * <pre>{@code
 *   final WidgetCanvas canvas = beginRedraw(totalHeightUsed);
 *   canvas.line("some content");
 *   canvas.blankLine();
 *   canvas.statusLine("{{w}}esc{{g}}:quit{{X}}");
 *   totalHeightUsed = canvas.finish();
 * }</pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class WidgetCanvas {

    private final Terminal terminal;
    private final StringBuilder output = new StringBuilder();

    // ---- absolute-mode geometry ----
    private final boolean absolute;
    private final int contentStartRow;  // 1-based
    private final int contentStartCol;  // 1-based
    private final int contentWidth;     // chars
    private final int contentMaxLines;  // max content rows before status row
    private final int statusRow;        // 1-based row reserved for the status bar

    // ---- relative-mode state ----
    private final int previousHeight;   // lines rendered in the prior cycle

    // ---- shared state ----
    private int currentLine = 0;

    /**
     * Package-private – created via {@link AbstractWidget#beginRedraw(int)}.
     */
    WidgetCanvas(final AbstractWidget<?> widget, final int previousHeight) {
        this.terminal      = widget.terminal;
        this.previousHeight = previousHeight;

        if (widget.hasPaneBounds()) {
            // ----------------------------------------------------------------
            // Absolute mode: render inside the pane's inner content area.
            //
            // Pane layout (all rows are 1-based terminal rows):
            //   paneStartRow                       → pane top border
            //   paneStartRow + 1 .. paneStartRow + H-3  → content rows (widget draws here)
            //   paneStartRow + H-2                 → status / prompt row
            //   paneStartRow + H-1                 → pane bottom border
            // ----------------------------------------------------------------
            this.absolute        = true;
            this.contentStartRow = widget.paneStartRow + 1;
            this.contentStartCol = widget.paneStartCol + 1;
            this.contentWidth    = widget.paneAvailWidth - 2;
            this.contentMaxLines = widget.paneAvailHeight - 3;
            this.statusRow       = widget.paneStartRow + widget.paneAvailHeight - 2;
        } else {
            // ----------------------------------------------------------------
            // Relative mode: cursor-up then sequential line printing.
            // ----------------------------------------------------------------
            this.absolute        = false;
            this.contentStartRow = 0;   // unused
            this.contentStartCol = 1;   // unused
            this.contentWidth    = Integer.MAX_VALUE;
            this.contentMaxLines = Integer.MAX_VALUE;
            this.statusRow       = -1;  // unused

            // Move cursor up to the start of the previous render so we overwrite it.
            if (previousHeight > 0) {
                output.append(Graphitty.string("{{^%d}}", previousHeight));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Public rendering API
    // -------------------------------------------------------------------------

    /**
     * Append one content line to the canvas.
     *
     * <p>In absolute mode: the line is clipped to the pane width and written at
     * the next available content row via absolute cursor positioning.  Calls that
     * exceed {@code contentMaxLines} are silently ignored.
     *
     * <p>In relative mode: the current terminal line is cleared and the content
     * is written, followed by a newline.
     */
    public void line(final String content) {
        if (absolute) {
            if (currentLine >= contentMaxLines) return; // pane is full – drop silently
            final int absRow = contentStartRow + currentLine;
            // Position, clear within-pane width (NOT \033[2K which would clear the entire line),
            // reposition, then write clipped content.
            output.append("\033[").append(absRow).append(";").append(contentStartCol).append("H");
            output.append(" ".repeat(contentWidth));
            output.append("\033[").append(absRow).append(";").append(contentStartCol).append("H");
            final String stripped = Graphitty.strip(content);
            output.append(stripped.length() > contentWidth
                    ? stripped.substring(0, contentWidth - 3) + "..."
                    : content);
        } else {
            output.append("\r").append(Graphitty.string("{{-X-}}")).append(content).append("\r\n");
        }
        currentLine++;
    }

    /**
     * Append a blank separator line.
     */
    public void blankLine() {
        line("");
    }

    /**
     * Render the status/hint bar.
     *
     * <p>In absolute mode: the bar is placed at the dedicated status row inside
     * the pane (the row above the pane's bottom border), clipped to pane width.
     *
     * <p>In relative mode: the bar is written immediately after the last content
     * line <em>without</em> a trailing newline so the cursor stays on that line,
     * ready for the widget to be erased from.
     *
     * @param content Graphitty-formatted status string
     */
    public void statusLine(final String content) {
        if (absolute) {
            output.append("\033[").append(statusRow).append(";").append(contentStartCol).append("H");
            output.append(" ".repeat(contentWidth));
            output.append("\033[").append(statusRow).append(";").append(contentStartCol).append("H");
            final String stripped = Graphitty.strip(Graphitty.string(content));
            output.append(stripped.length() > contentWidth
                    ? Graphitty.string(content).substring(0, contentWidth - 3) + "..."
                    : Graphitty.string(content));
        } else {
            // No trailing newline – cursor remains on this line for erase bookkeeping.
            output.append("\r").append(Graphitty.string("{{-X-}}" + content));
        }
    }

    /**
     * Finalise the redraw cycle: clear any leftover rows from a previous
     * (larger) render, then flush everything to the terminal.
     *
     * @return the number of content lines rendered – store this value as
     *         {@code totalHeightUsed} in the widget for the next cycle.
     */
    public int finish() {
        if (absolute) {
            // Erase unused content rows so stale content from previous renders
            // (e.g. when the stack shrinks) does not linger in the pane.
            while (currentLine < contentMaxLines) {
                final int absRow = contentStartRow + currentLine;
                output.append("\033[").append(absRow).append(";").append(contentStartCol).append("H");
                output.append(" ".repeat(contentWidth));
                currentLine++;
            }
        } else {
            // Clear extra lines left over from a previous, taller render.
            while (currentLine < previousHeight) {
                output.append("\r").append(Graphitty.string("{{X-}}\r\n"));
                currentLine++;
            }
        }
        Graphitty.out(terminal.output(), output.toString());
        terminal.writer().flush();
        return currentLine;
    }

    /** Number of content lines written so far (excluding the status row). */
    public int lineCount() {
        return currentLine;
    }
}
