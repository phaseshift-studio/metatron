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

import studio.phaseshift.metatron.lang.core.m.type.Code;
import studio.phaseshift.metatron.lang.util.serial.ObjCleanStringSerializer;
import studio.phaseshift.metatron.ui.Border;
import studio.phaseshift.metatron.ui.Stylable;
import studio.phaseshift.metatron.ui.Widget;
import studio.phaseshift.metatron.ui.widget.AbstractWidget;
import studio.phaseshift.metatron.ui.widget.Panel;
import studio.phaseshift.metatron.ui.widget.Selector;
import studio.phaseshift.metatron.ui.widget.Table;

import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class CodeTable extends AbstractWidget<CodeTable> {
    protected final Code code;
    protected final Table table;
    protected final Selector selector;
    protected Panel panel;

    public CodeTable(final Code code) {
        this.code = code;
        this.table = new Table(List.of("code")).style().background("{{[b]}}").foreground("{{y}}").divider("{{r}}|").apply();
        final String codeString = ObjCleanStringSerializer.prettyPrintCode(code);
        for (final String line : codeString.split("\n")) {
            this.table.addRow(List.of(line.replace(" ",".")));
        }
        this.panel = new Panel(this.table.toString()).style().border(Border.simple.foreground("{{b}}")).apply();
        this.selector = new Selector().style().margin(2, 2).pointer("{{r}}>{{X}}").attachment(this.panel, true).rowRange(1, this.panel.rowCount()-1).apply();
    }
    
    public void run() {
       Widget.cursorOffOn(this.selector::run);
    }

    public String toString() {
        return this.panel.toString();
    }

    @Override
    public int width() {
        return this.panel.width();
    }

    @Override
    public int height() {
        return this.panel.height();
    }
}