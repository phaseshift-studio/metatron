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

package studio.phaseshift.metatron.isa.sys.type.console;

import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.sys.type.ui.Border;
import studio.phaseshift.metatron.isa.sys.type.ui.widget.AbstractWidget;
import studio.phaseshift.metatron.isa.sys.type.ui.widget.Panel;
import studio.phaseshift.metatron.isa.sys.type.ui.widget.Table;

import java.util.List;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TypeTable extends AbstractWidget<CodeTable> {
    protected final Type type;
    protected final Table table;
    protected Panel panel;

    public TypeTable(final Type type) {
        this.type = type;
        this.table = new Table(List.of(" ", "obj", "desc")).style().background("{{[b]}}").foreground("{{y}}").divider("{{r}}|").apply();
        if (null != type.predicate()) {
            this.table.addRow(List.of("pred", type.predicate().asInst().args(), ""));
            this.table.addRow(List.of("", "dom", type.predicate().dom().tid()));
            this.table.addRow(List.of("", "rng", type.predicate().rng().tid()));
        } else {
            this.table.addRow(List.of("pred", noobj(), ""));
        }
        if (null != type.constructor()) {
            this.table.addRow(List.of("cons", type.constructor().asInst().args(), ""));
            this.table.addRow(List.of("", "dom", type.constructor().dom().tid()));
            this.table.addRow(List.of("", "rng", type.constructor().rng().tid()));
        } else {
            this.table.addRow(List.of("cons", noobj(), ""));
        }
        this.panel = new Panel(this.type.tid().toString(), this.table.toString()).style().border(Border.simple.foreground("{{b}}")).apply();
    }

    public void run() {
        super.run();
        this.panel.run();
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
