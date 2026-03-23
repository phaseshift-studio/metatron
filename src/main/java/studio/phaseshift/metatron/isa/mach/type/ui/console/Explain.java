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

package studio.phaseshift.metatron.isa.mach.type.ui.console;

import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.*;

import java.util.List;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Explain extends AbstractWidget<Explain> {

    private final GraphittyLogger LOG = Graphitty.log(Explain.class);
    private final Code code;
    private final Selector selector;
    private final Profile profile;
    private final Grid grid;

    public Explain(final Code code) {
        this.cursor = Console.getTerminal().getCursorPosition(i -> {
        });
        this.style = this.style().border(Border.simple.foreground("{{m}}"));
        this.code = code.resolve(noobj()).as();
        this.profile = new Profile(this.code).style().margin(2, 2).apply();
        this.profile.instTable.style().headerDivider("{{[b]}} ").margin(2, 2).border(Border.simple.foreground("{{b}}")).apply();
        this.selector = new Selector().style()
                .pointer("{{r}}>{{X}}")
                .attachment(profile, false)
                .rowRange(2, profile.instTable.rowCount() - 1)
                .apply()
                .onBrowse((s, r, c) -> {
                    final Inst sr = code.codeValue().get(r - 2);
                    /*if (sr.args().values().anyMatch(Obj::isCode)) {
                        s.style().pointer("{{g}}>{{X}}").apply();
                    } else {
                        s.style().pointer("{{r}}>{{X}}").apply();
                    }*/
                })
                .onSelect((s, r, c) -> {
                    final Table table = s.getStyle().<Profile>attachment().instTable;
                    Graphitty.out(Console.getTerminal().output(), " {{v%d}} clicked on: %s {{^%d}}", (table.height()+r)+5, table.entry(r - 2, c), (table.height()+r)+5);
                    if (table.header(c).equals("op")) {
                     //  LOG.info("{{v%d}}clicked on: %s {{^%d}}", table.height()+1, table.entry(r - 2, c), table.height()+1);
                    } else if (table.header(c).equals("args")) {
                        final Inst sr = code.codeValue().get(r - 2);
                        if (sr.args().values().anyMatch(Obj::isCode)) {
                            Graphitty.out(Console.getTerminal().output(), "\n".repeat(this.profile.height() + 2));
                            Graphitty.out(Console.getTerminal().output(), "{{^%d}}", 1);
                            final Separator sep = new Separator("{{r}}-", this);
                            Graphitty.out(Console.getTerminal().output(), sep.toString());
                            Graphitty.out(Console.getTerminal().output(), "{{v%d}}", 1);
                            sep.display();
                            sr.args().values().filter(Obj::isCode).findFirst().ifPresent(o -> {
                                this.close();
                                final Explain nest = new Explain(o.asCode());
                                nest.run();
                                nest.close();
                                Graphitty.out(Console.getTerminal().output(), "{{^%d}}", nest.profile.height() + 2);
                                this.run();
                                this.style().attachment.display();
                            });
                        }
                    }
                });
        this.grid = new Grid(List.of(this.selector), 1);
        this.style().attachment(this.grid, true).apply();
    }

    @Override
    public String toString() {
        return "";
    }


    @Override
    public String format() {
        return "";
    }

    @Override
    public void close() {
        // final String finalForm = this.style.attachment.format();
        super.close();
        // Graphitty.out(Console.getTerminal().output(), finalForm);
    }
}
