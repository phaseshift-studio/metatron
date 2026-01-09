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

import org.jline.terminal.Attributes;
import org.jline.terminal.Cursor;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.utils.Display;
import org.jline.utils.InfoCmp;
import studio.phaseshift.metatron.lang.sys.console.Console;
import studio.phaseshift.metatron.ui.Widget;
import studio.phaseshift.metatron.ui.graphitty.Graphitty;

import java.util.Arrays;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractWidget<W extends AbstractWidget<W>> implements Widget<W> {

    protected Terminal terminal = Console.getTerminal();
    protected Style<W> style = Style.empty();
    protected Display display;
    protected Cursor cursor;
    protected Attributes attributes;
    protected Size size;

    public W style(final Style<W> style) {
        this.style = style;
        return (W) this;
    }

    @Override
    public void display() {
        this.display.updateAnsi(Arrays.stream(this.format().split("\n")).map(Graphitty::string).toList(), 0);
    }

    @Override
    public void run() {
        this.display = new Display(Console.getTerminal(), true);
        this.attributes = this.terminal.enterRawMode();
        this.terminal.puts(InfoCmp.Capability.keypad_xmit);
        this.terminal.writer().flush();
        this.size = new Size(Console.getTerminal().getSize().getColumns(), this.terminal.getSize().getRows());
        //  this.display();
    }

    public void close() {
        this.display.clear();
        this.terminal.setAttributes(this.attributes);
        this.terminal.puts(InfoCmp.Capability.exit_ca_mode);
        this.terminal.puts(InfoCmp.Capability.keypad_local);
        this.terminal.writer().write(Graphitty.string("{{v10}}"));
        this.terminal.writer().flush();
        Graphitty.log(this).none("{{*}}"); // show cursor
    }
}
