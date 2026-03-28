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

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.AbstractWidget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Table;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Utilities;

import java.util.*;

import static org.jline.keymap.KeyMap.key;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * ExplainV2 - A code explanation widget with proper nested window support.
 *
 * Features:
 * - Navigate cells with arrow keys
 * - Press Enter on 'args' column to drill into nested code
 * - Press ESC to go back up / close
 * - Nested tables appear offset and overlay the parent
 * - Proper z-order: closing a nested table restores the view
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ExplainV2 extends AbstractWidget<ExplainV2> {

    private static final GraphittyLogger LOG = Graphitty.log(ExplainV2.class);

    private enum Action {
        QUIT, DOWN_ROW, UP_ROW, RIGHT_COL, LEFT_COL, SELECT
    }

    /**
     * Represents one level in the explain stack (one code block being viewed).
     */
    private static class ExplainLevel {
        final Code code;
        final Profile profile;
        final Table table;
        final int offsetX;
        final int offsetY;
        int selectedRow;
        int selectedCol;
        final List<String> savedScreen; // What was underneath this level
        final String pointer;      // The pointer character with color (e.g., "{{r}}>")
        final String divider;      // The divider string with color (e.g., "{{r}}|")
        final String rawDivider;   // The divider without color codes (e.g., "|")
        // Track which cell in the PARENT spawned this level (for visual connector)
        final int spawnRow;        // Row in parent that spawned this (-1 if root)
        final int spawnCol;        // Column in parent that spawned this (-1 if root)

        ExplainLevel(Code code, int offsetX, int offsetY, int spawnRow, int spawnCol) {
            this.code = code.resolve(noobj()).as();
            this.profile = new Profile(this.code);
            this.profile.instTable.style()
                    .headerDivider("{{[b]}} ")
                    .border(Border.simple.foreground("{{b}}"))
                    .pointer("{{r}}>")  // Configure pointer style
                    .apply();
            this.table = this.profile.instTable;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.selectedRow = 0;  // Start at first data row (after header)
            this.selectedCol = 0;
            this.savedScreen = new ArrayList<>();
            // Get divider and pointer from table style
            this.divider = this.table.getStyle().divider;
            this.rawDivider = Graphitty.strip(this.divider);
            this.pointer = this.table.getStyle().pointer.isEmpty() ? "{{r}}>" : this.table.getStyle().pointer;
            this.spawnRow = spawnRow;
            this.spawnCol = spawnCol;
        }

        int dataRowCount() {
            return this.code.codeValue().size();
        }

        Inst getInst(int row) {
            if (row >= 0 && row < code.codeValue().size()) {
                return code.codeValue().get(row);
            }
            return null;
        }
    }

    private final Deque<ExplainLevel> stack = new ArrayDeque<>();
    private final Code rootCode;
    private Attributes savedAttributes;
    private boolean running = false;
    private int baseRow = 0;  // The row where we start drawing (below prompt)
    private int totalHeightUsed = 0;  // Track how many lines we've used
    private String statusMessage = null;  // Temporary message to show in status bar

    // Column indices for the table
    private static final int COL_OP = 0;
    private static final int COL_DOM = 1;
    private static final int COL_RNG = 2;
    private static final int COL_F = 3;
    private static final int COL_ARGS = 4;
    private static final int COL_DESC = 5;

    public ExplainV2(final Code code) {
        this.rootCode = code;
    }

    @Override
    public void run() {
        // Enter raw mode
        savedAttributes = terminal.enterRawMode();
        terminal.puts(InfoCmp.Capability.keypad_xmit);
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        terminal.writer().flush();

        // Don't clear screen - draw below current position
        // Get current cursor position as our base
        this.baseRow = 0;  // We'll draw relative to current position

        pushLevel(rootCode, 0, 0, -1, -1);  // Root has no parent spawn position

        // Main event loop
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
        // Restore terminal state
        terminal.puts(InfoCmp.Capability.cursor_visible);
        if (savedAttributes != null) {
            terminal.setAttributes(savedAttributes);
        }
        terminal.puts(InfoCmp.Capability.keypad_local);
        terminal.writer().flush();
        super.close();
    }

    private KeyMap<Action> buildKeyMap() {
        KeyMap<Action> keyMap = new KeyMap<>();
        keyMap.bind(Action.DOWN_ROW, key(terminal, InfoCmp.Capability.key_down));
        keyMap.bind(Action.UP_ROW, key(terminal, InfoCmp.Capability.key_up));
        keyMap.bind(Action.RIGHT_COL, key(terminal, InfoCmp.Capability.key_right));
        keyMap.bind(Action.LEFT_COL, key(terminal, InfoCmp.Capability.key_left));
        keyMap.bind(Action.QUIT, Utilities.esc_key);
        keyMap.bind(Action.SELECT, Utilities.enter_key);
        return keyMap;
    }

    private void handleAction(Action action) {
        ExplainLevel current = stack.peek();
        if (current == null) return;

        switch (action) {
            case DOWN_ROW:
                current.selectedRow = Math.min(current.selectedRow + 1, current.dataRowCount() - 1);
                break;

            case UP_ROW:
                current.selectedRow = Math.max(current.selectedRow - 1, 0);
                break;

            case RIGHT_COL:
                current.selectedCol = Math.min(current.selectedCol + 1, current.table.rows().get(0).size() - 1);
                break;

            case LEFT_COL:
                current.selectedCol = Math.max(current.selectedCol - 1, 0);
                break;

            case SELECT:
                handleSelect(current);
                break;

            case QUIT:
                popLevel();
                if (stack.isEmpty()) {
                    running = false;
                }
                break;
        }
    }

    private void handleSelect(ExplainLevel current) {
        // Check if we're on the 'args' column
        String header = current.table.header(current.selectedCol);

        if ("args".equals(header)) {
            Inst inst = current.getInst(current.selectedRow);
            if (inst != null) {
                // Find first code argument
                Optional<Obj> codeArg = inst.args().values()
                        .filter(Obj::isCode)
                        .findFirst();

                if (codeArg.isPresent()) {
                    // Calculate offset for nested table (indent right and down)
                    int newOffsetX = current.offsetX + 4;
                    int newOffsetY = current.offsetY + current.selectedRow + 3; // +3 for header rows

                    // Pass spawn position so we can draw connector from parent cell
                    pushLevel(codeArg.get().asCode(), newOffsetX, newOffsetY,
                              current.selectedRow, current.selectedCol);
                } else {
                    // Flash or beep - no code to drill into
                    showMessage(current, "{{y}}no nested code in args{{X}}");
                }
            }
        } else if ("op".equals(header)) {
            // Could show instruction documentation here
            Inst inst = current.getInst(current.selectedRow);
            if (inst != null) {
                showMessage(current, "{{c}}%s{{X}} :: {{m}}%s{{X}} -> {{m}}%s{{X}}",
                        inst.tid().toUri(), inst.dom(), inst.rng());
            }
        }
    }

    private void pushLevel(Code code, int offsetX, int offsetY, int spawnRow, int spawnCol) {
        ExplainLevel level = new ExplainLevel(code, offsetX, offsetY, spawnRow, spawnCol);
        stack.push(level);
    }

    private void popLevel() {
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    /**
     * Redraw all levels from bottom to top with selection highlighting.
     */
    private void redrawStack() {
        // Build complete screen from bottom to top of stack
        List<ExplainLevel> levels = new ArrayList<>(stack);
        Collections.reverse(levels); // Draw bottom first

        StringBuilder output = new StringBuilder();

        // Remember old height so we can clear extra lines
        int previousHeight = totalHeightUsed;

        // Move cursor up to redraw area (if we've drawn before)
        if (totalHeightUsed > 0) {
            output.append(Graphitty.string("{{^%d}}", totalHeightUsed));
        }

        int currentLine = 0;

        for (int levelIdx = 0; levelIdx < levels.size(); levelIdx++) {
            ExplainLevel level = levels.get(levelIdx);
            boolean isTop = (levelIdx == levels.size() - 1);

            // Check if there's a child level that spawned from this level
            ExplainLevel childLevel = (levelIdx + 1 < levels.size()) ? levels.get(levelIdx + 1) : null;
            boolean hasChild = childLevel != null && childLevel.spawnRow >= 0;

            // Get the formatted table lines
            List<String> lines = level.profile.rowStrings();

            // Determine styling based on focus
            String dimColor = isTop ? "" : "{{w}}";

            // Add horizontal offset for nested levels
            String indent = " ".repeat(level.offsetX);

            for (int lineIdx = 0; lineIdx < lines.size(); lineIdx++) {
                String line = lines.get(lineIdx);

                // Clear the line first, add indent
                output.append("{{-X-}}"); // Clear line
                output.append(indent);

                // Data rows start at index 2 (after border + header)
                int dataLineIdx = lineIdx - 2;
                boolean isDataRow = dataLineIdx >= 0 && dataLineIdx < level.dataRowCount();
                boolean isSelectedRow = isTop && isDataRow && dataLineIdx == level.selectedRow;
                // Check if this is the spawn row in a parent level
                boolean isSpawnRow = !isTop && hasChild && isDataRow && dataLineIdx == childLevel.spawnRow;

                if (isSelectedRow) {
                    // Show selection pointer at the selected column (replaces the divider with pointer)
                    output.append(highlightSelectedColumn(line, level.selectedCol, level));
                } else if (isSpawnRow) {
                    // Highlight the spawn cell in parent with background color
                    output.append(dimColor);
                    output.append(highlightSpawnCell(line, childLevel.spawnCol, level));
                } else if (isTop && isDataRow) {
                    // Non-selected data row in active window
                    output.append(line);
                } else {
                    // Header, border, or inactive window
                    output.append(dimColor);
                    output.append(line);
                }

                output.append("\n");
                currentLine++;
            }

            // Add blank line between parent and child tables
            if (hasChild) {
                output.append("{{-X-}}\n");
                currentLine++;
            }

            // Show depth indicator for nested tables
            if (isTop && stack.size() > 1) {
                output.append(indent);
                output.append(Graphitty.string("  {{y}}[depth: %d]{{X}}{{X-}}\n", stack.size()));
                currentLine++;
            }
        }

        // Status line
        ExplainLevel top = stack.peek();
        if (top != null) {
            String header = top.table.header(top.selectedCol);
            Object value = top.table.entry(top.selectedRow, top.selectedCol);
            String valueStr = Graphitty.strip(value.toString());
            if (valueStr.length() > 30) {
                valueStr = valueStr.substring(0, 30) + "...";
            }

            output.append(Graphitty.string("{{X-}}{{[b]}}{{w}} ESC{{g}}:back {{w}}↑↓←→{{g}}:nav {{w}}Enter{{g}}:select "));

            // Show temporary message or current selection
            if (statusMessage != null) {
                output.append(Graphitty.string("%s {{X}}\n", statusMessage));
                statusMessage = null;  // Clear after showing
            } else {
                output.append(Graphitty.string("{{y}}%s{{g}}={{c}}%s {{X}}\n", header, valueStr));
            }
            currentLine++;
        }

        // Clear any extra lines from previous (larger) display
        // This happens when popping back to a smaller table
        while (currentLine < previousHeight) {
            output.append("{{X-}}\n");  // Clear line and move down
            currentLine++;
        }

        totalHeightUsed = currentLine;

        Graphitty.out(terminal.output(), output.toString());
        terminal.writer().flush();
    }

    /**
     * Show a temporary message in the status bar (will be shown on next redraw).
     */
    private void showMessage(ExplainLevel level, String format, Object... args) {
        this.statusMessage = Graphitty.string(format, args);
    }

    /**
     * Replace the divider before the selected column with the pointer indicator.
     * This gives a smooth visual effect as the selector moves across columns.
     * Uses the divider and pointer from the level's style configuration.
     */
    private String highlightSelectedColumn(String line, int selectedCol, ExplainLevel level) {
        // The table row format is: |col0|col1|col2|... (where | is the divider)
        // Replace the divider before the selected column with pointer

        String rawDivider = level.rawDivider;
        int dividerLen = rawDivider.length();

        if (dividerLen == 0) {
            return line;  // No divider configured
        }

        StringBuilder result = new StringBuilder();
        int dividerCount = 0;
        int i = 0;

        while (i < line.length()) {
            // Check if we're at a divider (match the raw divider characters)
            boolean atDivider = false;
            if (i + dividerLen <= line.length()) {
                String segment = line.substring(i, i + dividerLen);
                if (segment.equals(rawDivider)) {
                    atDivider = true;
                }
            }

            if (atDivider) {
                if (dividerCount == selectedCol + 1) {
                    // Replace this divider with pointer
                    result.append(Graphitty.string(level.pointer));
                    // Skip any additional divider chars if divider is multi-char
                    if (dividerLen > 1) {
                        result.append(rawDivider.substring(1));  // Keep chars after first
                    }
                } else {
                    // Keep the original divider
                    result.append(rawDivider);
                }
                i += dividerLen;
                dividerCount++;
            } else {
                result.append(line.charAt(i));
                i++;
            }
        }

        return result.toString();
    }

    /**
     * Highlight a specific cell with background color (for showing spawn point in parent table).
     */
    private String highlightSpawnCell(String line, int colIndex, ExplainLevel level) {
        String rawDivider = level.rawDivider;
        int dividerLen = rawDivider.length();

        if (dividerLen == 0) {
            return line;
        }

        StringBuilder result = new StringBuilder();
        int dividerCount = 0;
        int i = 0;
        boolean inTargetCell = false;

        while (i < line.length()) {
            // Check if we're at a divider
            boolean atDivider = false;
            if (i + dividerLen <= line.length()) {
                String segment = line.substring(i, i + dividerLen);
                if (segment.equals(rawDivider)) {
                    atDivider = true;
                }
            }

            if (atDivider) {
                if (inTargetCell) {
                    // End of target cell - close highlight
                    result.append("{{X}}");
                    inTargetCell = false;
                }
                result.append(rawDivider);
                i += dividerLen;
                dividerCount++;

                // Start highlight after the divider before target cell
                if (dividerCount == colIndex + 1) {
                    result.append("{{[R]}}");
                    inTargetCell = true;
                }
            } else {
                result.append(line.charAt(i));
                i++;
            }
        }

        // Close highlight if we ended inside the cell
        if (inTargetCell) {
            result.append("{{X}}");
        }

        return Graphitty.string(result.toString());
    }

    @Override
    public String format() {
        return ""; // Rendering is handled by run()
    }

    @Override
    public String toString() {
        return "ExplainV2[code=" + rootCode + "]";
    }
}
