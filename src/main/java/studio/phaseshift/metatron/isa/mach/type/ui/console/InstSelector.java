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
import org.jline.utils.InfoCmp;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.AbstractWidget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Table;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Utilities;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.WidgetCanvas;

import java.util.ArrayList;
import java.util.List;

import static org.jline.keymap.KeyMap.key;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_INST_TID;

/**
 * InstSelector - A widget for selecting instructions based on domain type.
 *
 * Features:
 * - Navigate rows with up/down arrow keys
 * - Press Enter to select instruction and append to buffer
 * - Press ESC to cancel
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class InstSelector extends AbstractWidget<InstSelector> {

    private enum Action {
        QUIT, DOWN_ROW, UP_ROW, LEFT_COL, RIGHT_COL, SELECT
    }

    private final List<Inst> instructions = new ArrayList<>();
    private final fURI domType;
    private final Table table;
    private final String originalBufferText;
    private Attributes savedAttributes;
    private boolean running = false;
    private int selectedRow = 0;      // Row in the table (each row has 2 instructions)
    private int selectedCol = 0;      // 0 = left instruction, 1 = right instruction
    private int totalHeightUsed = 0;
    private Inst selectedInst = null;

    private final String pointer = "{{r}}>";
    private final String divider = "|";

    public InstSelector(final Code code, final String originalBufferText) {
        this.originalBufferText = originalBufferText;

        // Get the last instruction's range type and query for matching instructions
        if (!code.codeValue().isEmpty()) {
            final Inst lastInst = code.codeValue().getLast();
            this.domType = lastInst.rng().tid();
            final Obj instructionsObj = Router.global().read(M_ISA_INST_TID.extend("#").dom(domType));
            instructionsObj.stream().forEach(obj -> {
                if (obj.isInst()) {
                    this.instructions.add(obj.as());
                }
            });
        } else {
            this.domType = null;
        }

        // Build the table with 7 columns (2 instructions per row with separator)
        this.table = new Table(List.of("op", "dom", "rng", "", "op", "dom", "rng"));
        this.table.style()
                .headerDivider("{{[b]}} ")
                .border(Border.simple.foreground("{{b}}"))
                .pointer("{{r}}>")
                .apply();

        // Add instructions in pairs (2 per row)
        for (int i = 0; i < this.instructions.size(); i += 2) {
            final Inst left = this.instructions.get(i);
            if (i + 1 < this.instructions.size()) {
                final Inst right = this.instructions.get(i + 1);
                this.table.addRow(List.of(
                        left.tid().name(),
                        left.dom().tid().small(),
                        left.rng().tid().small(),
                        "",  // separator column
                        right.tid().name(),
                        right.dom().tid().small(),
                        right.rng().tid().small()
                ));
            } else {
                // Odd number of instructions - empty right side
                this.table.addRow(List.of(
                        left.tid().name(),
                        left.dom().tid().small(),
                        left.rng().tid().small(),
                        "",  // separator column
                        "",
                        "",
                        ""
                ));
            }
        }
    }

    private int getTableRowCount() {
        return (instructions.size() + 1) / 2;  // Ceiling division
    }

    private int getSelectedIndex() {
        return selectedRow * 2 + selectedCol;
    }

    public boolean hasInstructions() {
        return !this.instructions.isEmpty();
    }

    @Override
    public void run() {
        if (this.instructions.isEmpty()) {
            return;
        }

        // Enter raw mode
        savedAttributes = terminal.enterRawMode();
        terminal.puts(InfoCmp.Capability.keypad_xmit);
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        terminal.writer().flush();

        // Main event loop
        this.running = true;
        BindingReader bindingReader = new BindingReader(terminal.reader());
        KeyMap<Action> keyMap = buildKeyMap();

        while (running) {
            redraw();
            Action action = bindingReader.readBinding(keyMap);
            handleAction(action);
        }
    }

    @Override
    public void close() {
        // Erase the widget area (eraseWidget is a no-op in absolute/pane-bounded mode
        // since renderPanes() will restore the layout; in relative mode it clears lines
        // and positions the cursor ready for the prompt to be redrawn).
        eraseWidget(totalHeightUsed);

        // Redraw the prompt and current buffer (only needed in relative mode;
        // in absolute mode renderPanes() called by the Console tab binding restores everything).
        if (!hasPaneBounds()) {
            Graphitty.out(terminal.output(), "{{-X-}}");
            Graphitty.out(terminal.output(), Console.LOCAL_INSTANCE.prompt());
            Graphitty.out(terminal.output(), Highlighter.format(Console.LOCAL_INSTANCE.getReader().getBuffer().toString()));
        }
        terminal.writer().flush();

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
        keyMap.bind(Action.LEFT_COL, key(terminal, InfoCmp.Capability.key_left));
        keyMap.bind(Action.RIGHT_COL, key(terminal, InfoCmp.Capability.key_right));
        keyMap.bind(Action.QUIT, Utilities.esc_key);
        keyMap.bind(Action.SELECT, Utilities.enter_key);
        return keyMap;
    }

    private void handleAction(Action action) {
        final int maxRow = getTableRowCount() - 1;

        switch (action) {
            case DOWN_ROW:
                if (selectedRow < maxRow) {
                    selectedRow++;
                    // If we moved to a row where right column is empty, stay on left
                    if (selectedCol == 1 && getSelectedIndex() >= instructions.size()) {
                        selectedCol = 0;
                    }
                }
                break;

            case UP_ROW:
                selectedRow = Math.max(selectedRow - 1, 0);
                break;

            case LEFT_COL:
                selectedCol = 0;
                break;

            case RIGHT_COL:
                // Only move right if there's an instruction there
                if (selectedRow * 2 + 1 < instructions.size()) {
                    selectedCol = 1;
                }
                break;

            case SELECT:
                final int idx = getSelectedIndex();
                if (idx >= 0 && idx < instructions.size()) {
                    this.selectedInst = instructions.get(idx);
                    // Update buffer directly while still in raw mode (no echo)
                    final String instName = selectedInst.tid().name();
                    Console.LOCAL_INSTANCE.getReader().getBuffer().clear();
                    Console.LOCAL_INSTANCE.getReader().getBuffer().write(originalBufferText + instName + "(");
                }
                running = false;
                break;

            case QUIT:
                this.selectedInst = null;
                running = false;
                break;
        }
    }

    /**
     * Redraw the selector table.
     * <p>
     * All rendering mechanics are delegated to the {@link WidgetCanvas} returned
     * by {@link #beginRedraw} — this method contains no pane-boundary logic.
     */
    private void redraw() {
        final WidgetCanvas canvas = beginRedraw(totalHeightUsed);

        // Title
        canvas.line(Graphitty.string("{{g}}insts with {{c}}dom={{y}}%s{{X}} {{w}}(%d found){{X}}",
                domType.small(), instructions.size()));

        // Table rows
        final List<String> lines = this.table.rowStrings();
        for (int lineIdx = 0; lineIdx < lines.size(); lineIdx++) {
            final String line      = lines.get(lineIdx);
            final int dataLineIdx  = lineIdx - 2;
            final boolean isDataRow  = dataLineIdx >= 0 && dataLineIdx < getTableRowCount();
            final boolean isSelected = isDataRow && dataLineIdx == selectedRow;
            canvas.line(isSelected ? highlightSelectedColumn(line, selectedCol) : line);
        }

        canvas.statusLine("{{w}}{{[b]}} esc{{g}}:cancel {{w}}<>^v{{g}}:nav {{w}}enter{{g}}:select {{X}}");
        totalHeightUsed = canvas.finish();
    }

    /**
     * Replace the appropriate divider with the pointer indicator based on selected column.
     * Column 0 = first divider (left instruction)
     * Column 1 = fifth divider (right instruction, after 3 left columns + separator)
     */
    private String highlightSelectedColumn(String line, int col) {
        // Find the correct divider to replace
        // For col 0: replace 1st divider (before op1)
        // For col 1: replace 5th divider (before op2, after 3 left cols + 1 separator)
        int targetDivider = (col == 0) ? 0 : 4;
        int dividerCount = 0;
        int dividerPos = -1;

        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == divider.charAt(0)) {
                if (dividerCount == targetDivider) {
                    dividerPos = i;
                    break;
                }
                dividerCount++;
            }
        }

        if (dividerPos >= 0) {
            return line.substring(0, dividerPos) +
                   Graphitty.string(pointer) +
                   line.substring(dividerPos + 1);
        }
        return line;
    }

    @Override
    public String format() {
        return ""; // Rendering is handled by run()
    }

    @Override
    public String toString() {
        return "InstSelector[dom=" + domType + ", count=" + instructions.size() + "]";
    }
}
