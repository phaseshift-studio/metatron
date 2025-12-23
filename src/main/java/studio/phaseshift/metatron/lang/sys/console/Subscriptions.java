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

package studio.phaseshift.metatron.lang.sys.console;

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.utils.Display;
import org.jline.utils.InfoCmp;
import studio.phaseshift.metatron.lang.Space;
import studio.phaseshift.metatron.lang.core.m.type.InstSet;
import studio.phaseshift.metatron.lang.core.m.type.Rel;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.ui.Box;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.Table;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.util.*;

import static org.jline.keymap.KeyMap.key;
import static studio.phaseshift.metatron.furi.fURI.f;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Subscriptions {
    private enum Operation {
        DOWN_ROW,
        UP_ROW,
        RIGHT_COL,
        LEFT_COL,
        EXIT
    }

    private final Console console;
    private final Terminal terminal;
    private final Size size = new Size();
    private final BindingReader bindingReader;
    private final Map<String,Table> states = new LinkedHashMap<>();
    
    public Subscriptions(Console console) {
        this.console = console;
        this.terminal = console.getTerminal();
        /// ///////////////////////////////////////////////////////
        final Table spaceTable = new Table(List.of("vid","pattern"));
        Router.global().spaces().elements().filter(r -> !(r.second() instanceof InstSet)).forEach(r -> {
            spaceTable.addRow(List.of(r.<Rel>as().first().toString(), r.<Rel>as().second().<Space>as().pattern()));
        });
        this.states.put("space",spaceTable);
        /// ///////////////////////////////////////////////////////
        final Table instTable = new Table(List.of("vid","pattern"));
        Router.global().spaces().elements().filter(r -> r.second() instanceof InstSet).forEach(r -> {
            instTable.addRow(List.of(r.<Rel>as().first().toString(), r.<Rel>as().second().<Space>as().pattern()));
        });
        this.states.put("inst",instTable);
        ///  states: machines and clusters
        this.bindingReader = new BindingReader(terminal.reader());
    }
    
    public String select() {
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
            keyMap.bind(Operation.DOWN_ROW, key(terminal, InfoCmp.Capability.key_down));
            keyMap.bind(Operation.UP_ROW, key(terminal, InfoCmp.Capability.key_up));
            keyMap.bind(Operation.RIGHT_COL, key(terminal, InfoCmp.Capability.key_right));
            keyMap.bind(Operation.LEFT_COL, key(terminal, InfoCmp.Capability.key_left));
            keyMap.bind(Operation.EXIT, "\r");
            Router.global().logger().none(Graphitty.string("{{.}}"));
            while (true) {
                display.resize(size.getRows(), size.getColumns());
                final int selectRowFinal = selectRow;
                final int selectColFinal = selectCol;
                final List<String> currentStateDisplay = new ArrayList<>();
                final String selectedState = new ArrayList<>(this.states.keySet()).get(selectCol);
                /// ///////////////////////////////////////////////////////////////////////////////////////////////
                currentStateDisplay.add(Graphitty.string(IteratorUtil.indexedStream(this.states.keySet().iterator())
                        .map(s -> ((s.get0() == selectColFinal) ? "{{c}}" : "{{y}}") + s.get1() + "{{X}}")
                        .map(Graphitty::string)
                        .reduce("",(a,b)->a + "{{g}} | {{X}}" + b)));
                currentStateDisplay.addAll(
                        IteratorUtil.indexedStream(this.states.get(selectedState).formattedRows().iterator())
                                .map(s -> ((s.get0() == selectRowFinal) ? "{{c}}>{{X}}" : " ") + s.get1())
                                .map(Graphitty::string)
                                .toList());
                /// ////////////////////////////////////////////////////////////////////////////////////////////////
                display.updateAnsi(currentStateDisplay,
                        size.cursorPos(1, this.states.get(selectedState).formattedWidth() + 2));
                Operation op = bindingReader.readBinding(keyMap);
                switch (op) {
                    case RIGHT_COL:
                        selectCol++;
                        if (selectCol > this.states.size() - 1)
                            selectCol = 0;
                        break;
                    case LEFT_COL:
                        selectCol--;
                        if (selectCol < 0)
                            selectCol = this.states.size() - 1;
                        break;
                    case DOWN_ROW:
                        selectRow++;
                        if (selectRow > this.states.get(selectedState).rows().size() - 1)
                            selectRow = 0;
                        break;
                    case UP_ROW:
                        selectRow--;
                        if (selectRow < 0)
                            selectRow = this.states.get(selectedState).rows().size() - 1;
                        break;
                    case EXIT:
                        Router.global().logger().none(Graphitty.string("{{*}}"));
                        return this.states.get(selectedState).row(selectRow).toString();
                }
                Router.global().logger().none(Graphitty.erase(25));
                final String location = this.states.get(selectedState).entry(selectRow, 0).toString();
                if (!location.contains("mod") && !location.contains("#")) {
                    try {
                        String space = Graphitty.strip(this.states.get(selectedState).entry(selectRow, 1).toString().trim());
                        Router.global().logger().none(Graphitty.floating(new Box("{{m}}subscriptions{{X}}", Router.global().read(f(space).query("sub")).toString(), Box.BASIC_BORDER).toString()));
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
}