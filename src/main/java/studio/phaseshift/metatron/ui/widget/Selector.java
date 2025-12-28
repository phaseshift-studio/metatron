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

import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import studio.phaseshift.metatron.ui.Widget;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Selector implements Widget {

    private enum Operation {
        DOWN_ROW,
        UP_ROW,
        RIGHT_COL,
        LEFT_COL,
        EXIT
    }

    private final Widget base;
    private int current;
    private Terminal terminal;
    private final Size size = new Size();
    private int rows = 0;
    private int cols = 0;

    public Selector(final Terminal terminal, final Widget base) {
        this.terminal = terminal;
        this.base = base;
    }

   /* public void run() {
        Display display = new Display(terminal, true);
        Attributes attr = terminal.enterRawMode();
        try {
            terminal.puts(InfoCmp.Capability.enter_ca_mode);
            terminal.puts(InfoCmp.Capability.keypad_xmit);
            terminal.writer().flush();
            size.copy(terminal.getSize());
            display.clear();
            display.reset();
            int selectRow = 0;
            int selectCol = 0;
            KeyMap<Operation> keyMap = new KeyMap<>();
            keyMap.bind(DOWN_ROW, key(terminal, InfoCmp.Capability.key_down));
            keyMap.bind(UP_ROW, key(terminal, InfoCmp.Capability.key_up));
            keyMap.bind(RIGHT_COL, key(terminal, InfoCmp.Capability.key_right));
            keyMap.bind(LEFT_COL, key(terminal, InfoCmp.Capability.key_left));
            keyMap.bind(EXIT, "\r");
            Router.global().logger().none(Graphitty.string("{{.}}"));
            while (true) {
                display.resize(size.getRows(), size.getColumns());
                final int selectRowFinal = selectRow;
                final int selectColFinal = selectCol;
                final List<String> currentStateDisplay = new ArrayList<>();
                final String selectedState = this.base.rowString(selectCol);
                /// ///////////////////////////////////////////////////////////////////////////////////////////////
                /*currentStateDisplay.add(Graphitty.string(IteratorUtil.indexedStream(this.states.keySet().iterator())
                        .map(s -> ((s.get0() == selectColFinal) ? "{{c}}" : "{{y}}") + s.get1() + "{{X}}")
                        .map(Graphitty::string)
                        .reduce("",(a,b)->a + "{{g}} | {{X}}" + b)));
                currentStateDisplay.addAll(
                                IntStream.range(0, this.base.height()).map(i -> (i == selectRow ? "{{r}}>{{X}}" : "") + this.base.rowString(i))

                                        .formattedRows().iterator())
                        .map(s -> ((s.get0() == selectRowFinal) ? "{{c}}>{{X}}" : " ") + s.get1())
                        .map(Graphitty::string)
                        .toList());
                /// ////////////////////////////////////////////////////////////////////////////////////////////////
               // display.updateAnsi(currentStateDisplay,
               //         size.cursorPos(1, this.rowString(selectRow).formattedWidth() + 2));
                Operation op = terminal.readBinding(keyMap);
                switch (op) {
                    case RIGHT_COL:
                        selectCol++;
                        if (selectCol > this.base.columnCount() - 1)
                            selectCol = 0;
                        break;
                    case LEFT_COL:
                        selectCol--;
                        if (selectCol < 0)
                            selectCol = this.base.columnCount() - 1;
                        break;
                    case DOWN_ROW:
                        selectRow++;
                        if (selectRow > this.base.rowCount() - 1)
                            selectRow = 0;
                        break;
                    case UP_ROW:
                        selectRow--;
                        if (selectRow < 0)
                            selectRow = this.base.rowCount() - 1;
                        break;
                    case EXIT:
                        Router.global().logger().none(Graphitty.string("{{*}}"));
                        return this.base.rowString(selectRow).toString();
                }
                Router.global().logger().none(Graphitty.erase(25));
                final String location = this.states.get(selectedState).entry(selectRow, 0).toString();
                if (!location.contains("mod") && !location.contains("#")) {
                    try {
                        String space = Graphitty.strip(this.states.get(selectedState).entry(selectRow, 1).toString().trim());
                        Router.global().logger().none(Graphitty.floating(new Panel("{{m}}subscriptions{{X}}", Router.global().read(f(space).query("sub")).toString(), Border.simple).toString()));
                    } catch (final Exception e) {
                        // do nothing
                    }
                }
            }
        } finally {
            terminal.setAttributes(attr);
            terminal.puts(InfoCmp.Capability.exit_ca_mode);
            terminal.puts(InfoCmp.Capability.keypad_local);
            terminal.writer().flush();
        }
    }
}*/


}
