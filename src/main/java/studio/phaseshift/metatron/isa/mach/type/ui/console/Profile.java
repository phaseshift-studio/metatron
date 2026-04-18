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

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.AbstractWidget;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.Table;

import java.util.List;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Profile extends AbstractWidget<Profile> {

    protected Call call;
    protected Table instTable;
    private static final int CLIP_LENGTH = 20;

    public Profile(final Call call) {
        this.instTable = new Table(List.of("op", "dom", "rng", "args", "f", "desc", "c_dom", "c_rng"));
        cInt dom = cInt.ONE();
        cInt rng = cInt.ONE();
        boolean first = true;
        for (final Inst i : call.insts()) {
            dom = first ? i.dom().c() : rng;
            boolean inDom = i.dom().c().lte(rng);
            rng = (Inst.Form.of(i) == Inst.Form.reducer) ? cInt.ONE() : (first ? i.rng().c() : i.rng().c().mult(dom));
            first = false;
            boolean found = !Router.global().read(i.tid().basePath()).isNoObj();
            this.instTable.addRow(List.of(
                    (found ? "{{b}}" : "{{r}}") + i.tid().name(),
                    i.dom().vid().small() + "::T",
                    i.rng().vid().small() + "::T",
                    (i.args().elements().allMatch(x -> x.isResolved(true)) ? "{{g}}" : "{{y}}") + generateArgsString(i.args()),
                    i.hasf() ? (i.f().isLambda() ? "{{y}}<j>" : "{{y}}<m>") : "{{r}}<?>",
                    "{{m}}" + Inst.Form.of(i).toString(),
                    "{{g}}{{{" + (inDom ? "y" : "r") + "}}" + dom.toString() + "{{g}}}{{X}}",
                    "{{g}}{{{y}}" + rng.toString() + "{{g}}}{{X}}")).style().background("{{[b]}}").foreground("{{y}}").divider("{{r}}|").apply();
            this.instTable.addMetadata(List.of(i, i.dom(), i.rng(), i.args(), null == i.f() ? Inst.f.of(noobj()) : i.f(), Router.global().read(i.tid().q("doc", null)), i.dom().c(), i.rng().c()));
        }
        this.style().attachment(this.instTable, true).apply();
    }

    private String generateArgsString(final Poly<?, ?> args) {
        final String argsString;
        if (args.isLst()) {
            argsString = args.lstValue().stream().map(o -> o.isCall() ?
                    o.asCall().insts().stream().map(i -> i.tid().name()).reduce((a, b) -> a + "." + b).orElse("") :
                    o.toShortString()).collect(Collectors.joining(","));
        } else {
            argsString = args.recValue().entrySet().stream().map(kv ->
                    kv.getKey().uriValue().toString() + "=>" + (kv.getValue().isCall() ?
                            kv.getValue().asCall().insts().stream().map(i -> i.tid().name()).reduce((a, b) -> a + "." + b).orElse("") :
                            kv.getValue().toShortString())).collect(Collectors.joining(","));
        }
        return argsString.length() > CLIP_LENGTH ? (argsString.substring(0, CLIP_LENGTH - 1) + "...") : argsString;
    }

    public String toString() {
        return this.format();
    }

    @Override
    public int columnCount() {
        return this.instTable.columnCount();
    }

    @Override
    public String format() {
        return this.instTable.format();
    }

}
