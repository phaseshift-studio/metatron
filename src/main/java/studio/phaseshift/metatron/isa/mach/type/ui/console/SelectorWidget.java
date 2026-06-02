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

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Attributes;
import org.jline.utils.InfoCmp;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.AbstractWidget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Table;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Utilities;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.WidgetCanvas;

import java.util.ArrayList;
import java.util.List;

import static org.jline.keymap.KeyMap.key;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class SelectorWidget<T, S extends SelectorWidget<T, S>> extends AbstractWidget<S> {

    protected enum Action {
        QUIT, DOWN_ROW, UP_ROW, LEFT_COL, RIGHT_COL, SELECT
    }

    protected final List<T> items = new ArrayList<>();
    protected final Table table;
    protected final String originalBufferText;
    protected Attributes savedAttributes;
    protected boolean running = false;
    protected int selectedRow = 0;
    protected int selectedCol = 0;
    protected int totalHeightUsed = 0;
    protected T selectedItem = null;

    protected static final String POINTER = "{{r}}>";
    protected static final String DIVIDER = "|";

    /**
     * Index of the {@code |} divider character that precedes the right-side item
     * within a rendered table row.  Value 4 matches the column layout produced by
     * {@link Table} when the {@link Border#simple} border style is used — the left
     * border itself counts as index 0.
     */
    protected static final int RIGHT_COL_DIVIDER_INDEX = 4;

    protected SelectorWidget(final String originalBufferText, final List<String> headers) {
        this.originalBufferText = originalBufferText;
        this.table = new Table(headers);
        this.table.style()
                .headerDivider("{{[b]}} ")
                .border(Border.simple.foreground("{{b}}"))
                .pointer(POINTER)
                .apply();
    }

    /* ----------------------------------------------------------
     * Subclass contract
     * ---------------------------------------------------------- */

    /** Convert one item to its table cell strings (a single "half-row"). */
    protected abstract List<String> cellsForItem(T item);

    /** Graphitty-formatted title line shown above the table. */
    protected abstract String getTitleLine();

    /** Write the selected item into the console line-buffer. */
    protected abstract void writeSelection(T item);

    /* ----------------------------------------------------------
     * Shared navigation / layout
     * ---------------------------------------------------------- */

    protected int getTableRowCount() {
        return (items.size() + 1) / 2;
    }

    protected int getSelectedIndex() {
        return selectedRow * 2 + selectedCol;
    }

    public boolean hasItems() {
        return !items.isEmpty();
    }

    /** Populate the table with item pairs after all items have been loaded. */
    protected void populateRows() {
        for (int i = 0; i < items.size(); i += 2) {
            final T left = items.get(i);
            final List<String> leftCells = cellsForItem(left);

            final List<Object> row = new ArrayList<>(leftCells);
            row.add(""); // separator column

            if (i + 1 < items.size()) {
                row.addAll(cellsForItem(items.get(i + 1)));
            } else {
                for (int j = 0; j < leftCells.size(); j++) row.add("");
            }
            table.addRow(row);
        }
    }

    /* ----------------------------------------------------------
     * Lifecycle
     * ---------------------------------------------------------- */

    @Override
    public void run() {
        if (items.isEmpty()) return;

        savedAttributes = terminal.enterRawMode();
        terminal.puts(InfoCmp.Capability.keypad_xmit);
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        terminal.writer().flush();

        running = true;
        BindingReader bindingReader = new BindingReader(terminal.reader());
        KeyMap<Action> keyMap = buildKeyMap();

        while (running) {
            try {
                redraw();
                Action action = bindingReader.readBinding(keyMap);
                handleAction(action);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void close() {
        // Tear down raw mode.
        terminal.puts(InfoCmp.Capability.cursor_visible);
        if (savedAttributes != null) {
            terminal.setAttributes(savedAttributes);
        }
        terminal.puts(InfoCmp.Capability.keypad_local);
        terminal.writer().flush();
        super.close();

        // Write selection to buffer in cooked mode.
        if (selectedItem != null) {
            writeSelection(selectedItem);
        }

        // Draw prompt + buffer on a clean line below the widget.
        if (!hasPaneBounds()) {
            Graphitty.out(terminal.output(), "\r\n\r\n");
            Graphitty.out(terminal.output(), "{{-X-}}");
            Graphitty.out(terminal.output(), Console.LOCAL_INSTANCE.prompt());
            Graphitty.out(terminal.output(), Highlighter.format(Console.LOCAL_INSTANCE.getReader().getBuffer().toString()));
        }
        terminal.writer().flush();
    }

    /* ----------------------------------------------------------
     * Input handling
     * ---------------------------------------------------------- */

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
                    if (selectedCol == 1 && getSelectedIndex() >= items.size()) {
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
                if (selectedRow * 2 + 1 < items.size()) {
                    selectedCol = 1;
                }
                break;
            case SELECT:
                final int idx = getSelectedIndex();
                if (idx >= 0 && idx < items.size()) {
                    selectedItem = items.get(idx);
                }
                running = false;
                break;
            case QUIT:
                selectedItem = null;
                running = false;
                break;
        }
    }

    /* ----------------------------------------------------------
     * Rendering
     * ---------------------------------------------------------- */

    private void redraw() {
        final WidgetCanvas canvas = beginRedraw(totalHeightUsed);
        canvas.line(getTitleLine());

        final List<String> lines = table.rowStrings();
        for (int lineIdx = 0; lineIdx < lines.size(); lineIdx++) {
            final String line = lines.get(lineIdx);
            final int dataLineIdx = lineIdx - 2;
            final boolean isDataRow = dataLineIdx >= 0 && dataLineIdx < getTableRowCount();
            final boolean isSelected = isDataRow && dataLineIdx == selectedRow;
            canvas.line(isSelected ? highlightSelectedColumn(line, selectedCol) : line);
        }

        canvas.statusLine("{{w}}{{[b]}} esc{{g}}:cancel {{w}}<>^v{{g}}:nav {{w}}enter{{g}}:select {{X}}");
        totalHeightUsed = canvas.finish();
    }

    private String highlightSelectedColumn(String line, int col) {
        int targetDivider = (col == 0) ? 0 : RIGHT_COL_DIVIDER_INDEX;
        int dividerCount = 0;
        int dividerPos = -1;

        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == DIVIDER.charAt(0)) {
                if (dividerCount == targetDivider) {
                    dividerPos = i;
                    break;
                }
                dividerCount++;
            }
        }

        if (dividerPos >= 0) {
            return line.substring(0, dividerPos) +
                   Graphitty.string(POINTER) +
                   line.substring(dividerPos + 1);
        }
        return line;
    }

    @Override
    public String format() {
        return "";
    }
}
