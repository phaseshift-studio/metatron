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

package studio.phaseshift.metatron.isa.mach.type.ui.tool;

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Attributes;
import org.jline.utils.InfoCmp;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.impl.MFail;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.AbstractWidget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.TableWidget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Utilities;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.WidgetCanvas;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.jline.keymap.KeyMap.key;

/**
 * Interactive fail-cause-chain explorer.
 * <p>
 * Displays the {@link Throwable} cause chain of a {@link Fail} as selectable table
 * rows (class | line | message).  Pressing {@code Enter} on a row pushes a detail
 * level showing the full Java stack trace for that exception.
 * <p>
 * Navigation:
 * <ul>
 *   <li>{@code ↑↓} — move selection</li>
 *   <li>{@code Enter} — drill into stack trace for the selected cause</li>
 *   <li>{@code Esc} — back from detail → table, or close from table</li>
 * </ul>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TraceTool extends AbstractWidget<TraceTool> {

    /**
     * Raw left-side character of the border used by every table in this tool.
     */
    private static final String LEFT_BORDER = Graphitty.strip(
            Border.continuous.foreground("{{y}}").leftSide());

    private enum Action {
        QUIT, DOWN_ROW, UP_ROW, SELECT
    }

    /* ---- one level in the drill-down stack ---- */
    private static class TraceLevel {
        /**
         * The cause chain entries (table rows).
         */
        final List<CauseEntry> entries;
        /**
         * Rendered table for this level.
         */
        final TableWidget table;
        /**
         * Horizontal indent from the left.
         */
        final int offsetX;
        final int offsetY;
        int selectedRow;
        /**
         * Row in the parent level that spawned this child (-1 if root).
         */
        final int spawnRow;

        TraceLevel(final List<CauseEntry> entries,
                   final int offsetX, final int offsetY,
                   final int spawnRow) {
            this.entries = entries;
            this.table = buildTable(entries);
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.selectedRow = 0;
            this.spawnRow = spawnRow;
        }

        int dataRowCount() {
            return this.entries.size();
        }
    }

    /**
     * One row in the cause-chain table.
     */
    private record CauseEntry(String className, int line, String message, Throwable throwable) {
    }

    /* ---- instance state ---- */

    private final List<CauseEntry> rootEntries = new ArrayList<>();
    private final Deque<TraceLevel> stack = new ArrayDeque<>();

    private Attributes savedAttributes;
    private boolean running = false;
    private int totalHeightUsed = 0;

    public TraceTool(final Fail fail) {
        walkFailChain(fail, new LinkedHashSet<>(), this.rootEntries);
    }

    /* ================================================================
     * Fail chain walking (mtron-level nesting)
     * ================================================================ */

    /**
     * Walk the mtron-level fail nesting via the {@code jvm() → getCause() → fail()} bridge.
     * <p>
     * Each {@link MFail} stores a back-pointer to its {@link MTronException} via
     * {@link MTronException#setFailRef(Fail)}.  The Java cause chain threads
     * nested mtron failures, and {@link MTronException#fail()} bridges from each
     * cause back to its corresponding mtron {@link Fail}.  Walking {@code Fail → jvm()
     * → getCause() → fail() → Fail ...} yields the full mtron-level fail stack.
     * <p>
     * Cycle detection uses {@link Throwable} identity on {@code jvm()} so that
     * transient fails (those with null VID) are still traversable.
     */
    private static void walkFailChain(Fail fail, final Set<Throwable> visited, final List<CauseEntry> sink) {
        while (fail != null) {
            final Throwable jvm = fail.jvm();
            if (!visited.add(jvm)) break; // cycle guard (Throwable identity)

            final StackTraceElement origin = MTronException.originOf(jvm);
            sink.add(new CauseEntry(
                    origin.getClassName().substring(origin.getClassName().lastIndexOf('.') + 1),
                    origin.getLineNumber(),
                    jvm.getMessage(),
                    jvm));

            // Bridge: Java cause → mtron Fail
            final Throwable cause = jvm.getCause();
            if (cause instanceof MTronException e && e.fail() != null && e.fail() != fail) {
                fail = e.fail();
            } else {
                fail = null;
            }
        }
    }

    /* ================================================================
     * Table construction
     * ================================================================ */

    private static TableWidget buildTable(final List<CauseEntry> entries) {
        final TableWidget table = (TableWidget) new TableWidget(List.of("class", "line", "message"))
                .style()
                .headerDivider("{{[y]&k}} ")
                .border(Border.continuous.foreground("{{y}}"))
                .divider("{{y}}" + Border.continuous.leftSide())
                .pointer("{{r}}>")
                .applyStyle();
        for (final CauseEntry e : entries) {
            table.addRow(List.of(
                    "{{c}}" + e.className(),
                    "{{g}}" + e.line(),
                    "{{y}}" + firstLine(e.message())));
        }
        return table;
    }

    private static String firstLine(final String msg) {
        if (msg == null) return "(null)";
        final int nl = msg.indexOf('\n');
        return nl < 0 ? msg : msg.substring(0, nl) + "...";
    }

    /* ================================================================
     * Lifecycle
     * ================================================================ */

    @Override
    public void run() {
        savedAttributes = terminal.enterRawMode();
        terminal.puts(InfoCmp.Capability.keypad_xmit);
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        terminal.writer().flush();

        pushLevel(this.rootEntries, 0, 0, -1);

        this.running = true;
        BindingReader bindingReader = new BindingReader(terminal.reader());
        KeyMap<Action> keyMap = buildKeyMap();

        while (running && !stack.isEmpty()) {
            redrawStack();
            Action action = bindingReader.readBinding(keyMap);
            handleAction(action);
        }
    }

    @Override
    public void close() {
        terminal.puts(InfoCmp.Capability.cursor_visible);
        if (savedAttributes != null) {
            terminal.setAttributes(savedAttributes);
        }
        terminal.puts(InfoCmp.Capability.keypad_local);
        terminal.writer().flush();
        super.close();
    }

    /* ================================================================
     * Input
     * ================================================================ */

    private KeyMap<Action> buildKeyMap() {
        KeyMap<Action> keyMap = new KeyMap<>();
        keyMap.bind(Action.DOWN_ROW, key(terminal, InfoCmp.Capability.key_down));
        keyMap.bind(Action.UP_ROW, key(terminal, InfoCmp.Capability.key_up));
        keyMap.bind(Action.QUIT, "\u0004"); // Ctrl-D
        keyMap.bind(Action.SELECT, Utilities.enter_key);
        return keyMap;
    }

    private void handleAction(Action action) {
        TraceLevel current = stack.peek();
        if (current == null) return;

        switch (action) {
            case DOWN_ROW:
                current.selectedRow = Math.min(current.selectedRow + 1, current.dataRowCount() - 1);
                break;
            case UP_ROW:
                current.selectedRow = Math.max(current.selectedRow - 1, 0);
                break;
            case SELECT:
                handleSelect(current);
                break;
            case QUIT:
                popLevel();
                if (stack.isEmpty()) running = false;
                break;
        }
    }

    /* ---- stack helpers ---- */

    private void pushLevel(final List<CauseEntry> entries,
                           final int offsetX, final int offsetY,
                           final int spawnRow) {
        stack.push(new TraceLevel(entries, offsetX, offsetY, spawnRow));
    }

    private void popLevel() {
        if (!stack.isEmpty()) stack.pop();
    }

    private void handleSelect(TraceLevel current) {
        // Guard: only drill down from the root cause-chain level
        if (stack.size() > 1) return;
        if (current.dataRowCount() == 0) return;
        final CauseEntry selected = current.entries.get(current.selectedRow);

        // Build entries for the stack-trace detail level: one row per frame
        final List<CauseEntry> frameEntries = new ArrayList<>();
        final Throwable target = selected.throwable();
        for (final StackTraceElement frame : target.getStackTrace()) {
            frameEntries.add(new CauseEntry(
                    frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1),
                    frame.getLineNumber(),
                    (frame.getFileName() != null ? frame.getFileName() : "") + ":" + frame.getMethodName(),
                    target));
        }

        int newOffsetX = current.offsetX + 4;
        int newOffsetY = current.offsetY + current.selectedRow + 3;
        pushLevel(frameEntries, newOffsetX, newOffsetY, current.selectedRow);
    }

    /* ================================================================
     * Rendering
     * ================================================================ */

    private void redrawStack() {
        final List<TraceLevel> levels = new ArrayList<>(stack);
        Collections.reverse(levels); // Draw bottom → top

        final WidgetCanvas canvas = beginRedraw(totalHeightUsed);

        for (int levelIdx = 0; levelIdx < levels.size(); levelIdx++) {
            final TraceLevel level = levels.get(levelIdx);
            final boolean isTop = (levelIdx == levels.size() - 1);
            final TraceLevel child = (levelIdx + 1 < levels.size()) ? levels.get(levelIdx + 1) : null;
            final boolean hasChild = child != null && child.spawnRow >= 0;
            final List<String> lines = level.table.rowStrings();
            final String dimColor = isTop ? "" : "{{w}}";
            final String indent = " ".repeat(level.offsetX);

            for (int lineIdx = 0; lineIdx < lines.size(); lineIdx++) {
                final String line = lines.get(lineIdx);
                final int dataLineIdx = lineIdx - 2; // skip header + divider
                final boolean isDataRow = dataLineIdx >= 0 && dataLineIdx < level.dataRowCount();
                final boolean isSelected = isTop && isDataRow && dataLineIdx == level.selectedRow;
                final boolean isSpawn = !isTop && hasChild && isDataRow && dataLineIdx == child.spawnRow;

                final String content;
                if (isSelected) {
                    content = indent + highlightPointer(line);
                } else if (isSpawn) {
                    content = Graphitty.string(dimColor) + indent + line;
                } else if (isTop && isDataRow) {
                    content = indent + line;
                } else {
                    content = Graphitty.string(dimColor) + indent + line;
                }
                canvas.line(content);
            }

            if (hasChild) canvas.blankLine();
            if (isTop && stack.size() > 1) {
                canvas.line(Graphitty.string(indent + "  {{y}}[depth: %d]{{X}}", stack.size()));
            }
        }

        // Status / key-hint bar
        final TraceLevel top = stack.peek();
        if (top != null) {
            final boolean isDetail = stack.size() > 1;
            final String backHint = isDetail ? " {{w}}ctrl-d{{g}}:back" : " {{w}}ctrl-d{{g}}:close";
            final String selectHint = isDetail ? "" : " {{w}}enter{{g}}:stack-trace";
            canvas.statusLine(Graphitty.string(
                    "{{w}}{{[b]}}%s {{w}}^v{{g}}:navigate%s{{X}}%s  %d %s",
                    backHint.trim(),
                    selectHint,
                    isDetail ? "  {{y}}stack frames{{X}}" : "",
                    top.dataRowCount(),
                    isDetail ? "frames" : (top.dataRowCount() == 1 ? "cause" : "causes")));
        }

        totalHeightUsed = canvas.finish();
    }

    /**
     * Replace the left-edge border character with the red {@code >} pointer.
     * Uses the registered border's {@link Border#leftSide()} so the correct
     * character is consumed regardless of border style.
     */
    private static String highlightPointer(final String line) {
        final int firstDiv = line.indexOf(LEFT_BORDER);
        if (firstDiv >= 0) {
            return "{{r}}>{{X}}" + line.substring(firstDiv + LEFT_BORDER.length());
        }
        return "{{r}}>{{X}}" + line;
    }

    /* ================================================================
     * Contract
     * ================================================================ */

    @Override
    public String format() {
        return "";
    }

    @Override
    public String toString() {
        return "TraceTool[causes=" + rootEntries.size() + "]";
    }
}
