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

package studio.phaseshift.metatron.ui.widget;

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.utils.Display;
import org.jline.utils.InfoCmp;
import studio.phaseshift.metatron.lang.sys.console.Console;
import studio.phaseshift.metatron.ui.Widget;
import studio.phaseshift.metatron.ui.graphitty.Graphitty;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.jline.keymap.KeyMap.key;
import static studio.phaseshift.metatron.ui.widget.Selector.Operation.*;


/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Selector extends AbstractWidget<Selector> {

    protected enum Operation {
        QUIT,
        DOWN_ROW,
        UP_ROW,
        RIGHT_COL,
        LEFT_COL,
        SELECTED
    }

    public boolean running() {
        return this.running;
    }

    protected boolean running = false;
    private Consumer<Integer> onSelect = i -> {
        Graphitty.log(this).none("{{|0&v1}}{{m}}selected{{X}}: %s{{|0&^1}}", i);
    };
    private Consumer<Integer> onBrowse = i -> {
        Graphitty.log(this).none("{{|0&v1}}{{m}}browsed{{X}}: %s{{|0&^1}}", i);
    };
    
    public Selector() {
    }

    public Selector onSelect(final Consumer<Integer> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    public Selector onBrowse(final Consumer<Integer> onBrowse) {
        this.onBrowse = onBrowse;
        return this;
    }

    public String toString() {
        return this.style.attachment.toString();
    }


    public void run() {
        final Terminal terminal = Console.getTerminal();
        Display display = new Display(terminal, true);
        BindingReader bindingReader = new BindingReader(terminal.reader());
        Attributes attr = terminal.enterRawMode();
        try {
            //terminal.puts(InfoCmp.Capability.enter_ca_mode);
            terminal.puts(InfoCmp.Capability.keypad_xmit);
            terminal.writer().flush();
            Size size = new Size(terminal.getSize().getColumns(), terminal.getSize().getRows());
            //display.clear();
            display.reset();
            int selectRow = style.lowRowRange;
            int selectCol = 0;
            KeyMap<Operation> keyMap = new KeyMap<>();
            keyMap.bind(QUIT, key(terminal, InfoCmp.Capability.tab));
            keyMap.bind(DOWN_ROW, key(terminal, InfoCmp.Capability.key_down));
            keyMap.bind(UP_ROW, key(terminal, InfoCmp.Capability.key_up));
            keyMap.bind(RIGHT_COL, key(terminal, InfoCmp.Capability.key_right));
            keyMap.bind(LEFT_COL, key(terminal, InfoCmp.Capability.key_left));
            keyMap.bind(SELECTED, "\r");
            Graphitty.log(this).none("{{^%s}}", style.attachment.rowCount());
            while (true) {
                display.resize(size.getRows(), size.getColumns());
                final int selectRowFinal = selectRow;
                final int selectColFinal = selectCol;
                final List<String> currentStateDisplay = new ArrayList<>();
                /// ///////////////////////////////////////////////////////////////////////////////////////////////
                for (int i = 0; i < this.style.attachment.rowCount(); i++) {
                    currentStateDisplay.add(Graphitty.string(" ".repeat(style.leftMargin) + (i == selectRowFinal ? this.style.pointer : " ".repeat(Graphitty.viewLength(this.style.pointer))) + this.style.attachment.rowString(i) + "{{|0}}"));
                }
                /// ////////////////////////////////////////////////////////////////////////////////////////////////
                display.updateAnsi(currentStateDisplay, size.cursorPos(0, 0));
                Operation op = bindingReader.readBinding(keyMap);
                switch (op) {
                    case RIGHT_COL:
                        selectCol++;
                        if (selectCol > this.style.attachment.columnCount() - 1)
                            selectCol = 0;
                        break;
                    case LEFT_COL:
                        selectCol--;
                        if (selectCol < 0)
                            selectCol = this.style.attachment.columnCount() - 1;
                        break;
                    case DOWN_ROW:
                        selectRow++;
                        if (selectRow > this.style.highRowRange - 1)
                            selectRow = this.style.lowRowRange;
                        break;
                    case UP_ROW:
                        selectRow--;
                        if (selectRow < this.style.lowRowRange)
                            selectRow = this.style.highRowRange - 1;
                        break;
                    case SELECTED:
                        //Graphitty.log(this).none("{{v%s}}",4);
                        this.onSelect.accept(selectRow);
                        return;
                    case QUIT:
                        terminal.writer().println();
                        return;
                }
                //this.onBrowse.accept(selectRow);
            }
        } catch (final Exception e) {
            e.printStackTrace();
        } finally {
            terminal.setAttributes(attr);
            terminal.puts(InfoCmp.Capability.exit_ca_mode);
            terminal.puts(InfoCmp.Capability.keypad_local);
            terminal.writer().flush();
            Graphitty.log(this).none("{{*}}"); // show cursor
        }
    }
}