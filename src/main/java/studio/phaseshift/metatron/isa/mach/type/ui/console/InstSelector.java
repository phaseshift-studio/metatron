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

import java.util.ArrayList;
import java.util.List;

import static org.jline.keymap.KeyMap.key;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
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
        QUIT, DOWN_ROW, UP_ROW, SELECT
    }

    private final List<Inst> instructions = new ArrayList<>();
    private final fURI domType;
    private final Table table;
    private final String originalBufferText;
    private Attributes savedAttributes;
    private boolean running = false;
    private int selectedRow = 0;
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

        // Build the table
        this.table = new Table(List.of("op", "dom", "rng"));
        this.table.style()
                .headerDivider("{{[b]}} ")
                .border(Border.simple.foreground("{{b}}"))
                .pointer("{{r}}>")
                .apply();

        for (final Inst inst : this.instructions) {
            this.table.addRow(List.of(
                    inst.tid().name(),
                    inst.dom().tid().small(),
                    inst.rng().tid().small()
            ));
        }
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
        // Clear the widget display while still in raw mode
        // Move cursor up to start of widget and clear all lines
        if (totalHeightUsed > 0) {
            StringBuilder clear = new StringBuilder();
            clear.append(Graphitty.string("{{^%d}}{{|1}}", totalHeightUsed));
            for (int i = 0; i <= totalHeightUsed; i++) {
                clear.append("{{-X-}}\n");
            }
            // Move back up and position for prompt
            clear.append(Graphitty.string("{{^%d}}{{|1}}", totalHeightUsed + 1));
            Graphitty.out(terminal.output(), clear.toString());
        }

        // Draw the prompt and buffer content while still in raw mode
        Graphitty.out(terminal.output(), "{{-X-}}");
        Graphitty.out(terminal.output(), Console.LOCAL_INSTANCE.prompt());
        Graphitty.out(terminal.output(), Highlighter.format(Console.LOCAL_INSTANCE.getReader().getBuffer().toString()));
        terminal.writer().flush();

        // Now restore terminal state
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
        keyMap.bind(Action.QUIT, Utilities.esc_key);
        keyMap.bind(Action.SELECT, Utilities.enter_key);
        return keyMap;
    }

    private void handleAction(Action action) {
        switch (action) {
            case DOWN_ROW:
                selectedRow = Math.min(selectedRow + 1, instructions.size() - 1);
                break;

            case UP_ROW:
                selectedRow = Math.max(selectedRow - 1, 0);
                break;

            case SELECT:
                if (selectedRow >= 0 && selectedRow < instructions.size()) {
                    this.selectedInst = instructions.get(selectedRow);
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

    private void redraw() {
        StringBuilder output = new StringBuilder();

        // Remember old height so we can clear extra lines
        int previousHeight = totalHeightUsed;

        // Move cursor up to redraw area and to column 1 (if we've drawn before)
        if (totalHeightUsed > 0) {
            output.append(Graphitty.string("{{^%d}}{{|1}}", totalHeightUsed));
        }

        int currentLine = 0;

        // Title line - clear entire line first
        output.append(Graphitty.string("{{-X-}}{{g}}insts with {{c}}dom={{y}}%s{{X}} {{w}}(%d found){{X}}\n",
                domType.small(), instructions.size()));
        currentLine++;

        // Get the formatted table lines
        List<String> lines = this.table.rowStrings();

        for (int lineIdx = 0; lineIdx < lines.size(); lineIdx++) {
            String line = lines.get(lineIdx);

            output.append("{{-X-}}"); // Clear entire line

            // Data rows start at index 2 (after border + header)
            int dataLineIdx = lineIdx - 2;
            boolean isDataRow = dataLineIdx >= 0 && dataLineIdx < instructions.size();
            boolean isSelectedRow = isDataRow && dataLineIdx == selectedRow;

            if (isSelectedRow) {
                // Show selection pointer
                output.append(highlightSelectedRow(line));
            } else {
                output.append(line);
            }

            output.append("\n");
            currentLine++;
        }

        // Status line - no newline at end, and DON'T count it in currentLine
        // because the cursor stays on this line (no \n)
        output.append(Graphitty.string("{{-X-}}{{w}}{{[b]}} esc{{g}}:cancel | {{w}}<^v>{{g}}:nav | {{w}}enter{{g}}:select {{X}}"));

        // Clear any extra lines from previous (larger) display
        while (currentLine < previousHeight) {
            output.append("\n{{-X-}}");  // Move down and clear entire line
            currentLine++;
        }

        totalHeightUsed = currentLine;

        Graphitty.out(terminal.output(), output.toString());
        terminal.writer().flush();
    }

    /**
     * Replace the first divider with the pointer indicator for the selected row.
     */
    private String highlightSelectedRow(String line) {
        // Find the first divider and replace it with pointer
        int firstDivider = line.indexOf(divider);
        if (firstDivider >= 0) {
            return line.substring(0, firstDivider) +
                   Graphitty.string(pointer) +
                   line.substring(firstDivider + 1);
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
