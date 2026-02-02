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

import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.sys.type.ui.Border;
import studio.phaseshift.metatron.isa.sys.type.ui.Widget;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.sys.type.ui.widget.AbstractWidget;
import studio.phaseshift.metatron.isa.sys.type.ui.widget.Selector;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Explain extends AbstractWidget<Explain> {

    private final Code code;
    private final Selector selector;
    private final Profile profile;

    public Explain(final Code code) {
        this.cursor = Console.getTerminal().getCursorPosition(i -> {
        });
        this.style = this.style().border(Border.simple.foreground("{{m}}"));
        this.code = code.resolve(noobj()).as();
        this.profile = new Profile(this.code);
        this.profile.instTable.style().headerDivider("{{[b]}} ").apply();
        this.selector = new Selector().style()
                .pointer("{{r}}>{{X}}")
                .attachment(profile, false)
                .rowRange(2, profile.instTable.rowCount() - 1)
                .apply()
                .onBrowse((s, i) -> {
                    final Inst si = code.codeValue().get(i - 2);
                });
    }

    @Override
    public void run() {
        Widget.cursorOffOn(this.selector::run);
    }

    @Override
    public String toString() {
        return this.selector.toString();
    }


    @Override
    public String format() {
        return this.selector.format();
    }

    @Override
    public void close() {
        final String finalForm = this.profile.format();
        final int height = this.selector.height();
        this.selector.close();
        this.profile.close();
        super.close();
        Graphitty.out(Console.getTerminal().output(), "{{^" + height + "}}");
        Graphitty.out(Console.getTerminal().output(), finalForm);
    }
}
