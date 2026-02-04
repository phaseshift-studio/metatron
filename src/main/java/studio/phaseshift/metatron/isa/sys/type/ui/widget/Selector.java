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

package studio.phaseshift.metatron.isa.sys.type.ui.widget;

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.utils.InfoCmp;
import studio.phaseshift.metatron.isa.sys.type.Router;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.Graphitty;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static org.jline.keymap.KeyMap.key;
import static studio.phaseshift.metatron.isa.sys.type.ui.widget.Selector.Operation.*;


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
        SELECTED,
        ESC_KEY
    }

    protected BiConsumer<Selector, Integer> onSelect = null;
    protected BiConsumer<Selector, Integer> onBrowse = null;

    public Selector onSelect(final BiConsumer<Selector, Integer> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    public Selector onBrowse(final BiConsumer<Selector, Integer> onBrowse) {
        this.onBrowse = onBrowse;
        return this;
    }

    @Override
    public String format() {
        return this.style.attachment.format();
    }

    public void run() {
        super.run();
        try {
            final BindingReader bindingReader = new BindingReader(terminal.reader());
            int selectRow = style.lowRowRange;
            int selectCol = 0;
            KeyMap<Operation> keyMap = new KeyMap<>();
            keyMap.bind(DOWN_ROW, key(this.terminal, InfoCmp.Capability.key_down));
            keyMap.bind(UP_ROW, key(this.terminal, InfoCmp.Capability.key_up));
            keyMap.bind(RIGHT_COL, key(this.terminal, InfoCmp.Capability.key_right));
            keyMap.bind(LEFT_COL, key(this.terminal, InfoCmp.Capability.key_left));
            keyMap.bind(QUIT, Utilities.esc_key);
            keyMap.bind(SELECTED, Utilities.enter_key);
            // Graphitty.log(this).none("{{^%s}}", this.style.attachment.rowCount() + 1);
            boolean done = false;
            while (!done) {
                final int selectRowFinal = selectRow;
                final int selectColFinal = selectCol;
                final List<String> currentStateDisplay = new ArrayList<>();
                /// ///////////////////////////////////////////////////////////////////////////////////////////////
                for (int i = 0; i < this.style.attachment.rowCount(); i++) {
                    boolean selected = i == selectRowFinal;
                    currentStateDisplay.add(
                            Graphitty.string(" ".repeat(this.style.leftMargin) +
                                    (selected ? this.style.pointer : " ".repeat(Graphitty.viewLength(this.style.pointer))) + "{{X}}" +
                                    // (selected ? "{{[w]&y}}" : "") +  
                                    this.style.attachment.rowString(i)));
                }
                /// ////////////////////////////////////////////////////////////////////////////////////////////////
                this.display.updateAnsi(currentStateDisplay, 0);
                final Operation op = bindingReader.readBinding(keyMap);
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
                        done = true;
                        break;
                    case QUIT:
                        done = true;
                        return;
                }
                if (null != this.onSelect && done) {
                    done = false;
                    this.onSelect.accept(this, selectRow);
                } else if (null != this.onBrowse)
                    this.onBrowse.accept(this, selectRow);
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
}