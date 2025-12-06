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
import studio.phaseshift.metatron.lang.core.m.type.Rel;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.Table;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.jline.keymap.KeyMap.key;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Subscriptions {
    private enum Operation {
        FORWARD_ONE_LINE,
        BACKWARD_ONE_LINE,
        EXIT
    }

    private final Console console;
    private final Terminal terminal;
    private final List<String> lines = new ArrayList<>();
    private final Size size = new Size();
    private final BindingReader bindingReader;

    public Subscriptions(Console console) {
        this.console = console;
        this.terminal = console.getTerminal();
        this.bindingReader = new BindingReader(terminal.reader());
        lines.add(Graphitty.sillyPrint("select space", true, true));
        Table table = new Table(List.of("vid","pattern"));
        Router.global().spaces().elements().forEach(r -> {
            table.addRow(List.of(r.<Rel>as().first().toString(), r.<Rel>as().second().<Space>as().pattern()));
        });
        lines.addAll(Arrays.asList(table.toString().split("\n")));
    }

    private List<String> displayLines(int cursorRow) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < this.lines.size(); i++) {
            final String line = this.lines.get(i);
            if (i == cursorRow)
                out.add(Graphitty.string(line.replaceFirst("\\|","{{r}}>{{X}}") + "{{X}}\n"));
            else
                out.add(Graphitty.string(line + "{{X}}\n"));
        }
        return out;
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
            int selectRow = 2;
            KeyMap<Operation> keyMap = new KeyMap<>();
            keyMap.bind(Operation.FORWARD_ONE_LINE, key(terminal, InfoCmp.Capability.key_down));
            keyMap.bind(Operation.BACKWARD_ONE_LINE, key(terminal, InfoCmp.Capability.key_up));
            keyMap.bind(Operation.EXIT, "\r");
            while (true) {
                display.resize(size.getRows(), size.getColumns());
                display.updateAnsi(
                        displayLines(selectRow),
                        size.cursorPos(0, lines.get(0).length()));
                Operation op = bindingReader.readBinding(keyMap);
                switch (op) {
                    case FORWARD_ONE_LINE:
                        selectRow++;
                        if (selectRow > lines.size() - 1)
                            selectRow = 2;
                        break;
                    case BACKWARD_ONE_LINE:
                        selectRow--;
                        if (selectRow < 2)
                            selectRow = lines.size() - 1;
                        break;
                    case EXIT:
                        return this.lines.get(selectRow);
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