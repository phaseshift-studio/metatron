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
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;
import studio.phaseshift.metatron.lang.sys.console.Console;
import studio.phaseshift.metatron.ui.graphitty.Graphitty;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

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
    protected BiConsumer<Selector, Integer> onSelect = null;
    protected BiConsumer<Selector, Integer> onBrowse = null;

    public Selector() {
    }

    public Selector onSelect(final BiConsumer<Selector, Integer> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    public Selector onBrowse(final BiConsumer<Selector, Integer> onBrowse) {
        this.onBrowse = onBrowse;
        return this;
    }

    public String toString() {
        return this.style.attachment.toString();
    }


    public void run() {
        super.run();
        final Terminal terminal = Console.getTerminal();
        try {
            final BindingReader bindingReader = new BindingReader(terminal.reader());
            int selectRow = style.lowRowRange;
            int selectCol = 0;
            KeyMap<Operation> keyMap = new KeyMap<>();
            keyMap.bind(QUIT, key(terminal, InfoCmp.Capability.tab));
            keyMap.bind(DOWN_ROW, key(terminal, InfoCmp.Capability.key_down));
            keyMap.bind(UP_ROW, key(terminal, InfoCmp.Capability.key_up));
            keyMap.bind(RIGHT_COL, key(terminal, InfoCmp.Capability.key_right));
            keyMap.bind(LEFT_COL, key(terminal, InfoCmp.Capability.key_left));
            keyMap.bind(SELECTED, "\r");
            Graphitty.log(this).none("{{^%s}}", style.attachment.rowCount() + 1);
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
                        if (null != this.onSelect)
                            this.onSelect.accept(this, selectRow);
                        else
                            this.close();
                        return;
                    case QUIT:
                        terminal.writer().println();
                        return;
                }
                if (null != this.onBrowse)
                    this.onBrowse.accept(this, selectRow);
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
}