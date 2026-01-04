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

import org.jline.utils.InfoCmp;
import studio.phaseshift.metatron.lang.core.m.type.Code;
import studio.phaseshift.metatron.lang.util.serial.ObjCleanStringSerializer;
import studio.phaseshift.metatron.ui.Border;
import studio.phaseshift.metatron.ui.Stylable;
import studio.phaseshift.metatron.ui.Widget;
import studio.phaseshift.metatron.ui.widget.BarMenu;
import studio.phaseshift.metatron.ui.widget.Panel;
import studio.phaseshift.metatron.ui.widget.Selector;
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
    private final Selector selector;
    private final Panel mainBox;
    private final BarMenu menu;

    public Explain(final Code code) {

        this.style = this.style().border(Border.simple.foreground("{{m}}"));
        this.code = code.resolve(noobj()).as();
        Profile profile = new Profile(this.code);
        profile.table.style().headerDivider("{{[b]}} ").apply();
        this.selector = new Selector().style().margin(1, 1).pointer("{{r}}>{{X}}").attachment(profile, true).rowRange(1, profile.table.rowCount()).apply();
        this.mainBox = new Panel(Highlighter.format(ObjCleanStringSerializer.prettyPrintCode(code).stripTrailing())).style().border(Border.simple.margin(2, 2).foreground("{{c}}")).apply()
                .bottom(new Separator("-", profile).color("{{y}}"))
                .bottom(this.selector);
        this.menu = new BarMenu(List.of(Tuple.Pair.with("compile", () -> System.out.println("compiling...")), Tuple.Pair.with("optimize", () -> System.out.println("optimizing..."))))
                .style()
                .background("{{[b]&w}}")
                .attachment(mainBox, true)
                .divider("{{g}}|")
                .border(Border.simple.foreground("{{g}}")).margin(2, 2).apply();
    }

    @Override
    public Explain style(final Style<Explain> style) {
        this.style = style;
        return this;
    }

    public void run() {
        Widget.cursorOffOn(this.selector::run);
    }

    @Override
    public String toString() {
        return "{{.}}" + menu.toString();
    }
}
