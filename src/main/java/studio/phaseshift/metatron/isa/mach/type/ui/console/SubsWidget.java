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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.AbstractWidget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Grid;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Selector;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Table;

import java.util.List;


/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SubsWidget extends AbstractWidget<SubsWidget> {
    private enum Operation {
        DOWN_ROW,
        UP_ROW,
        RIGHT_COL,
        LEFT_COL,
        EXIT
    }

    private final Table spaceTable;
    private final Table subsTable;
    private final Selector spaceSelector;
    private final Selector subsSelector;
    // private final Selector selectorSelector;
    private Grid grid = null;

    public SubsWidget(final Console console) {
        /// ///////////////////////////////////////////////////////
        this.spaceTable = new Table(List.of("space vid", "pattern")).style()
                .border(Border.simple)
                .headerDivider("{{[b]&g}}|{{w}}")
                .apply();
        this.subsTable = new Table(List.of("subscription vid", "target", "call")).style()
                .border(Border.simple)
                .headerDivider("{{[b]&g}}|{{w}}")
                .apply();


        Router.global().spaces().elements().filter(r -> !(r.second() instanceof InstSet)).forEach(r -> {
            this.spaceTable.addRow(List.of(r.asRel().first().toString(), r.asRel().second().<Space>as().pattern()));
        });

        this.subsSelector = new Selector().style().pointer("{{r}}>").attachment(this.subsTable, true).apply().onSelect((s, r, c) -> {
            this.grid.currentFocus(0);
        });
        this.spaceSelector = new Selector().style()
                .pointer("{{r}}>")
                .attachment(this.spaceTable, true)
                .rowRange(2, this.spaceTable.rowStrings().size() + 2)
                .apply()
                .onSelect((s, r, c) -> {
                    try {
                        final fURI pattern = (fURI) this.spaceTable.entry(r - 2, 1);
                        //  Router.global().logger().none("{{>%s}}selected %s" + " ".repeat(10) + "{{<%s}}", this.spaceTable.width() + 2, pattern.toUri(), this.spaceTable.width() + 12);
                        //final Space space = Router.global().getSpace(pattern);
                        this.subsTable.clear();
                        // Obj subscriptions = Router.global().read(pattern.query("sub"));
                        this.subsTable.addRow(List.of("blah", "bleep", "bleep"));
                        // this.grid.currentFocus(1);
                        // subscriptions.stream().forEach(o ->{
                        //     this.subsTable.addRow(List.of(o.vid().toUri(), o.<PubSubQ.Subscription>as().target().toUri(), o.<PubSubQ.Subscription>as().call().toString()));
                        // });
                        // Router.global().logger().info("{{y}}%s{{X}}", this.grid.format());
                        //System.out.println("subscriptions: " + subscriptions);
                      /*  final List<Widget<?>> cards = (List) subscriptions.stream().map(o -> o.<PubSubQ.Subscription>as())
                                .map(o -> new Card(o.target().toString(),
                                        Highlighter.format(o.call().toString()))
                                        .style()
                                        .border(Border.simple.foreground("{{y}}"))
                                        .apply())
                                .toList();*/
                        //final Panel panel = new Panel(subscriptions.toString());
                        //panel.right(this);
                        //panel.display();
                        //final Grid grid = new Grid(cards, 2).style().border(Border.simple.foreground("{{b}}")).background("{{[y]}}").foreground("{{c}}").margin(1, 1).apply();
                    } catch (final Exception e) {
                        // do nothing
                    }
                });
        this.grid = new Grid(List.of(this.spaceSelector, this.subsSelector), 1).style().border(Border.none).apply();
        // this.grid.currentFocus(0);
        this.style().attachment(this.grid, true).apply();
    }

    @Override
    public String format() {
        return this.style.attachment.format();
    }
}