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
import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractWidget<W extends AbstractWidget<W>> implements Widget<W> {

    protected Terminal terminal = Console.getTerminal();
    protected Style<W> style = Style.empty();
    protected Size size;
    protected Display display;
    protected Cursor cursor;
    protected Attributes attributes;

    public AbstractWidget() {
        this.size = this.terminal.getSize();
        this.display = new Display(this.terminal, false);
        this.display.resize(this.size.getRows(), this.size.getColumns());
        this.cursor = new Cursor(0, 0);
    }

    public W cursor(final Cursor cursor) {
        this.cursor = cursor;
        return (W) this;
    }

    public W style(final Style<W> style) {
        this.style = style;
        return (W) this;
    }

    @Override
    public void display() {
        this.display.resize(this.height(), this.width());
        this.display.updateAnsi(Arrays.stream(this.format().split("\n")).map(Graphitty::string).toList(), -1);
    }

    @Override
    public void run() {
        this.attributes = this.terminal.enterRawMode();
        this.terminal.puts(InfoCmp.Capability.keypad_xmit);
        this.terminal.writer().flush();
        this.display.updateAnsi(Arrays.stream(this.format().split("\n")).map(Graphitty::string).toList(), -1);
    }

    public void close() {
        if (null != this.style.attachment)
            this.style.attachment.close();
        // this.terminal.puts(InfoCmp.Capability.clear_screen);
        this.display.update(List.of(),  this.size.cursorPos(this.cursor.getX(),this.cursor.getY()));
        this.display.reset();
        //this.display.resize(0,0);
        if (null != this.attributes) {
            this.terminal.setAttributes(this.attributes);
            this.terminal.puts(InfoCmp.Capability.exit_ca_mode);
            this.terminal.puts(InfoCmp.Capability.keypad_local);
        }
        this.terminal.writer().flush();
    }
}
