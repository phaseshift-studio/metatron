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
import studio.phaseshift.metatron.ui.Table;

import java.util.List;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Profile {

    protected final Obj obj;

    public Profile(final Obj obj) {
        this.obj = obj;
    }

    public String toString() {
        final Table table = new Table(List.of("op", "dom", "rng", "desc", "c_dom", "c_rng"));
        if (obj.isCode()) {
            cInt dom = cInt.ONE();
            cInt rng = cInt.ONE();
            boolean first = true;
            for (final Inst i : obj.codeValue()) {
                dom = first ? i.dom().c() : rng;
                boolean inDom = i.dom().c().lte(rng);
                rng = first ? i.rng().c() : i.rng().c().mult(dom);
                first = false;
                final String back = i.hasf() && !i.dom().tid().hasPattern() ? "{{b}}" : "{{y}}";
                table.addRow(List.of(
                        back + i.tid().name(),
                        i.dom(),
                        i.rng(),
                        "{{m}}" + Inst.Form.of(i).toString(),
                        "{{g}}{{{" + (inDom ? "y" : "r") + "}}" + dom.toString() + "{{g}}}{{X}}",
                        "{{g}}{{{y}}" + rng.toString() + "{{g}}}{{X}}"));
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
        return table.toString();
    }
}
