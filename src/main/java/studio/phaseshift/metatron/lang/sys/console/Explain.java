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
import studio.phaseshift.metatron.lang.util.serial.ObjStringSerializer;
import studio.phaseshift.metatron.ui.Border;
import studio.phaseshift.metatron.ui.Stylable;
import studio.phaseshift.metatron.ui.Widget;
import studio.phaseshift.metatron.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.ui.widget.BarMenu;
import studio.phaseshift.metatron.ui.widget.Panel;
import studio.phaseshift.metatron.ui.widget.Separator;
import studio.phaseshift.metatron.util.Tuple;

import java.util.List;

import static studio.phaseshift.metatron.lang.core.m.obj.NoObj.noobj;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Explain implements Widget, Stylable<Explain> {

    private Style<Explain> style = Style.empty();
    private final Code code;

    public Explain(final Code code) {
        this.code = code;
    }

    @Override
    public Explain style(final Style<Explain> style) {
        this.style = style;
        return this;
    }

    @Override
    public String toString() {
        final Code code = this.code.resolve(noobj()).as();
        final Profile profile = new Profile(code);
        final Panel mainBox = new Panel(Highlighter.singleton().highlight(ObjCleanStringSerializer.prettyPrintCode(code)), Border.simple.margin(2, 2).style().foreground("{{c}}").apply())
                .bottom(new Separator("-", profile).color("{{y}}"), Border.none)
                .bottom(profile, Border.simple.margin(2, 2).style().foreground("{{r}}").apply());
        final BarMenu menu = new BarMenu(List.of(Tuple.Pair.with("compile", () -> System.out.println("compiling...")), Tuple.Pair.with("optimize", () -> System.out.println("optimizing..."))))
                .style()
                .background("{{[b]&w}}")
                .attachment(mainBox, true)
                .divider("{{g}}|")
                .border(Border.simple.style().foreground("{{g}}").apply()).apply();
        return menu.toString();
    }
}
