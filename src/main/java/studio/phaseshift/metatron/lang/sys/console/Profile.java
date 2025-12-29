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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.lang.core.m.type.Inst;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.ui.Widget;
import studio.phaseshift.metatron.ui.widget.Table;

import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Profile implements Widget {

    protected final Obj obj;
    protected final Table table;

    public Profile(final Obj obj) {
        this.obj = obj;
        this.table = new Table(List.of("op", "dom", "rng", "f", "args", "desc", "c_dom", "c_rng"));
        if (obj.isCode()) {
            cInt dom = cInt.ONE();
            cInt rng = cInt.ONE();
            boolean first = true;
            for (final Inst i : obj.codeValue()) {
                dom = first ? i.dom().c() : rng;
                boolean inDom = i.dom().c().lte(rng);
                rng = (Inst.Form.of(i) == Inst.Form.reducer) ? cInt.ONE() : (first ? i.rng().c() : i.rng().c().mult(dom));
                first = false;
                boolean found = !Router.global().read(i.tid().basePath()).isNoObj();
                this.table.addRow(List.of(
                        (found ? "{{b}}" : "{{r}}") + i.tid().name(),
                        i.dom(),
                        i.rng(),
                        i.hasf() ? (i.f().isLambda() ? "{{y}}<j>" : "{{y}}<m>") : "{{r}}<?>",
                        i.args().elements().allMatch(x -> x.isResolved(true)) ? "{{y}}<,>" : "{{r}}<?,?>",
                        "{{m}}" + Inst.Form.of(i).toString(),
                        "{{g}}{{{" + (inDom ? "y" : "r") + "}}" + dom.toString() + "{{g}}}{{X}}",
                        "{{g}}{{{y}}" + rng.toString() + "{{g}}}{{X}}")).style().background("{{[b]}}").foreground("{{y}}").divider("{{r}}|").apply();
                /*if (!i.args().isEmpty()) {
                    final Table arg = new Table(List.of(""));
                    i.args().forEach(a -> arg.addRow(List.of(new Profile(a).toString())));
                    table.addRow(List.of("","",arg));
                }*/

            }
            ;
        } //else {
        //table.addRow(List.of(obj.tid().toUri(), obj.dom(), obj.rng()));
        //}
    }

    public String toString() {
        return this.table.toString();
    }

    @Override
    public int width() {
        return this.table.width();
    }

    @Override
    public int height() {
        return this.table.height();
    }

    @Override
    public String rowString(int i) {
        return this.table.rowString(i);
    }
}
