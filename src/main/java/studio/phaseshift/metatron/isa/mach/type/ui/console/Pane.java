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

package studio.phaseshift.metatron.isa.mach.type.ui.console;

import org.jline.terminal.Terminal;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A Pane is a leaf node in the pane tree - an actual terminal region with:
 * - Output buffer (thread-safe, for parallel output from background threads)
 * - Language mode (mtron, gremlin, sql)
 * - Machine reference (for interruption)
 *
 * <pre>
 * ┌─────────────────────────────────┐
 * │ [0] Pane Output                 │ ← outputBuffer rendered here
 * │ ==> result1                     │
 * │ ==> result2                     │
 * │                                 │
 * │ mtron> ___                      │ ← prompt (when active)
 * └─────────────────────────────────┘
 * </pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Pane implements PaneNode {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);
    private static final int DEFAULT_MAX_OUTPUT_LINES = 1000;

    private final int id;
    private Console.Language language;
    private Machine machine;
    private final List<String> outputBuffer;
    private final int maxOutputLines;
    private volatile boolean needsRedraw = false;

    // Reference to console for redraw requests
    private Console console;

    // Render region tracking (updated each render)
    private int lastStartRow = 1;
    private int lastStartCol = 1;
    private int lastHeight = 24;
    private int lastWidth = 80;

    public Pane() {
        this(Console.Language.MTRON, DEFAULT_MAX_OUTPUT_LINES);
    }

    public Pane(final Console.Language language, final int maxOutputLines) {
        this.id = ID_COUNTER.getAndIncrement();
        this.language = language;
        this.maxOutputLines = maxOutputLines;
        this.outputBuffer = Collections.synchronizedList(new ArrayList<>());
        this.machine = null;
    }

    public void setConsole(final Console console) {
        this.console = console;
    }

    public int id() {
        return this.id;
    }

    public Console.Language language() {
        return this.language;
    }

    public Pane language(final Console.Language language) {
        this.language = language;
        return this;
    }

    public Machine machine() {
        return this.machine;
    }

    public Pane machine(final Machine machine) {
        this.machine = machine;
        return this;
    }

    public void clearMachine() {
        this.machine = null;
    }

    /**
     * Append output to this pane's buffer. Thread-safe.
     * Lines exceeding maxOutputLines are trimmed from the top.
     * Triggers a redraw request.
     */
    public void appendOutput(final String text) {
        if (text == null) return;
        // Split on newlines and add each
        for (final String line : text.split("\n", -1)) {
            this.outputBuffer.add(line);
        }
        // Trim old lines
        while (this.outputBuffer.size() > this.maxOutputLines) {
            this.outputBuffer.remove(0);
        }
        this.needsRedraw = true;
        if (this.console != null) {
            this.console.requestRedraw();
        }
    }

    /**
     * Append a result object to output (formatted with ==> and syntax highlighted). Thread-safe.
     */
    public void appendResult(final Obj result) {
        result.stream().forEach(o -> {
            // Use Highlighter for syntax highlighting
            this.appendOutput(Graphitty.string("{{m}}=={{g}}>{{X}}") + Highlighter.format(o));
        });
    }

    /**
     * Write formatted output (for prompts, messages). Thread-safe.
     */
    public void write(final String text) {
        this.appendOutput(text);
    }

    public void clearOutput() {
        this.outputBuffer.clear();
        this.needsRedraw = true;
    }

    public List<String> outputBuffer() {
        return new ArrayList<>(this.outputBuffer); // Return copy for thread safety
    }

    public boolean needsRedraw() {
        return this.needsRedraw;
    }

    public void clearRedrawFlag() {
        this.needsRedraw = false;
    }

    /**
     * Get the last N lines of output that fit in the given height.
     */
    public List<String> visibleOutput(final int height) {
        final List<String> buffer = this.outputBuffer();
        final int visibleLines = Math.min(buffer.size(), Math.max(0, height - 2)); // -2 for border/prompt
        if (visibleLines <= 0) return List.of();
        return buffer.subList(buffer.size() - visibleLines, buffer.size());
    }

    /**
     * Generate the prompt string for this pane.
     */
    public String prompt() {
        return Graphitty.string("{{g}}[{{y}}%d{{g}}]{{X}} %s".formatted(this.id, this.language.prompt));
    }

    // ========== Region Tracking ==========

    /**
     * Get the row where the prompt should appear (just above bottom border).
     */
    public int getPromptRow() {
        return this.lastStartRow + this.lastHeight - 2;
    }

    /**
     * Get the column where the prompt should start.
     */
    public int getPromptCol() {
        return this.lastStartCol;
    }

    /**
     * Get the width available for the prompt line.
     */
    public int getPromptWidth() {
        return this.lastWidth - 1;
    }

    // ========== PaneNode interface ==========

    @Override
    public void render(final Terminal terminal, final int startRow, final int startCol,
                       final int height, final int width, final Pane activePane) {
        // Store render region for cursor positioning
        this.lastStartRow = startRow;
        this.lastStartCol = startCol;
        this.lastHeight = height;
        this.lastWidth = width;

        final boolean isActive = this == activePane;
        final List<String> visible = this.visibleOutput(height);

        // Build the pane content
        final StringBuilder sb = new StringBuilder();

        // Header line with pane ID
        final String header = isActive
                ? Graphitty.string("{{[g]}}[%d]{{X}}".formatted(this.id))
                : Graphitty.string("{{[b]}}[%d]{{X}}".formatted(this.id));
        sb.append("\u001b[").append(startRow).append(";").append(startCol).append("H"); // Move cursor
        sb.append(header);
        sb.append(Graphitty.string("{{%s}}".formatted(isActive ? "g" : "b")));
        sb.append("─".repeat(Math.max(0, width - 4)));
        sb.append("{{X}}");

        // Output lines
        int row = startRow + 1;
        for (final String line : visible) {
            if (row >= startRow + height - 1) break; // Leave room for bottom
            sb.append("\u001b[").append(row).append(";").append(startCol).append("H");
            // Truncate line to fit width
            final String stripped = Graphitty.strip(line);
            final String truncated = stripped.length() > width - 1
                    ? stripped.substring(0, width - 4) + "..."
                    : line;
            sb.append(truncated);
            // Clear to end of pane region (not full line to avoid bleeding into other pane)
            final int clearLen = width - Graphitty.strip(truncated).length() - 1;
            if (clearLen > 0) sb.append(" ".repeat(clearLen));
            row++;
        }

        // Clear remaining rows
        while (row < startRow + height - 1) {
            sb.append("\u001b[").append(row).append(";").append(startCol).append("H");
            sb.append(" ".repeat(width - 1));
            row++;
        }

        // Bottom border
        sb.append("\u001b[").append(startRow + height - 1).append(";").append(startCol).append("H");
        sb.append(Graphitty.string("{{%s}}".formatted(isActive ? "g" : "b")));
        sb.append("─".repeat(Math.max(0, width - 1)));
        sb.append("{{X}}");

        terminal.writer().print(Graphitty.string(sb.toString()));
        this.needsRedraw = false;
    }

    @Override
    public boolean isLeaf() {
        return true;
    }

    @Override
    public List<Pane> getAllPanes() {
        return List.of(this);
    }

    @Override
    public Pane findPane(final int id) {
        return this.id == id ? this : null;
    }

    @Override
    public boolean replaceChild(final Pane oldPane, final PaneNode newNode) {
        // Leaf nodes have no children to replace
        return false;
    }

    @Override
    public PaneNode removePane(final Pane pane) {
        // If removing self, return null (parent will handle)
        return this == pane ? null : this;
    }

    @Override
    public String toString() {
        return "Pane[%d, %s, lines=%d]".formatted(this.id, this.language.name, this.outputBuffer.size());
    }
}
