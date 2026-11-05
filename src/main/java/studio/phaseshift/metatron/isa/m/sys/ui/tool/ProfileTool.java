/*
 * metatron: a distributed virtual machine and language
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

package studio.phaseshift.metatron.isa.m.sys.ui.tool;

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.sys.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.m.sys.ui.widget.AbstractWidget;
import studio.phaseshift.metatron.isa.m.sys.ui.widget.TableWidget;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.ROW;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ProfileTool extends AbstractWidget<ProfileTool> {

    protected Call call;
    protected TableWidget instTable;

    public ProfileTool(final Call call) {
        this.instTable = new TableWidget(List.of("op", "dom", "rng", "args", "f", "desc", "c_dom", "c_rng"));
        cInt dom = cInt.ONE();
        cInt rng = cInt.ONE();
        boolean first = true;
        for (final Inst i : call.insts()) {
            dom = first ? i.dom().c() : rng;
            boolean inDom = i.dom().c().lte(rng);
            rng = (Inst.Form.of(i) == Inst.Form.reducer) ? cInt.ONE() : (first ? i.rng().c() : i.rng().c().mult(dom));
            first = false;
            boolean found = !Machine.current().memory().read(i.tid().basePath()).isNoObj();
            this.instTable.addRow(List.of(
                    (found ? "{{b}}" : "{{r}}") + i.tid().name() + (i.tid().c().isOne() ? "" : ("{" + i.tid().c() + "}")),
                    i.dom().vid().small() + "::T",
                    i.rng().vid().small() + "::T",
                    (i.args().elements().allMatch(x -> x.isResolved(true)) ? "{{g}}" : "{{y}}") + argsSummary(i.args()),
                    i.hasf() ? (i.f().isLambda() ? "{{y}}<j>" : "{{y}}<m>") : "{{r}}<?>",
                    "{{m}}" + Inst.Form.of(i).toString(),
                    "{{g}}{{{" + (inDom ? "y" : "r") + "}}" + dom.toString() + "{{g}}}{{X}}",
                    "{{g}}{{{y}}" + rng.toString() + "{{g}}}{{X}}")).style().background("{{[b]}}").foreground("{{y}}").divider("{{r}}|").apply();
            this.instTable.addMetadata(List.of(i, i.dom(), i.rng(), i.args(), null == i.f() ? Inst.f.of(noobj()) : i.f(), i, dom, rng));
        }
        this.style().attachment(this.instTable, true).apply();
    }

    public Lst getTable() {
        return this.instTable.at(ROW).stream().collect(new CommonUtil.LstCollector());
    }


    /**
     * Fixed display width of the {@code args} column. Every args cell is clipped or
     * padded to exactly this many columns, so the column never reflows while the
     * inline selector windows across a long argument list.
     */
    public static final int ARGS_WIDTH = 24;

    /**
     * One display token per argument, in the order the args cell renders them: a call
     * flattens to its dotted instruction names, a keyed rec argument is {@code k=>v}.
     */
    public static List<String> argTokens(final Poly<?, ?> args) {
        final List<String> tokens = new ArrayList<>();
        if (args.isLst()) {
            for (final Obj arg : args.lstValue()) tokens.add(summarizeArg(arg));
        } else if (args.isRel()) {
            final Rel rel = args.asRel();
            tokens.add(rel.first().uriValue().toString() + "=>" + summarizeArg(rel.second()));
        } else {
            for (final Map.Entry<Obj, Obj> kv : args.recValue().entrySet())
                tokens.add(kv.getKey().uriValue().toString() + "=>" + summarizeArg(kv.getValue()));
        }
        return tokens;
    }

    /**
     * The args cell as plain text, clipped or padded to exactly {@link #ARGS_WIDTH}
     * columns. The leading space is the pointer slot: the inline selector replaces it
     * (or a comma-space) with {@code >}, so the arg text never shifts column.
     */
    public static String argsSummary(final Poly<?, ?> args) {
        return padToWidth(" " + String.join(", ", argTokens(args)));
    }

    public static String summarizeArg(final Obj o) {
        return o.isCall()
                ? o.asCall().insts().stream().map(i -> i.tid().name()).reduce((a, b) -> a + "." + b).orElse("")
                : o.toShortString();
    }

    private static String padToWidth(final String s) {
        final int len = Highlighter.visualLength(s);
        if (len == ARGS_WIDTH) return s;
        if (len > ARGS_WIDTH) return clipToWidth(s);
        return s + " ".repeat(ARGS_WIDTH - len);
    }

    private static String clipToWidth(final String s) {
        final int keep = Math.max(0, ARGS_WIDTH - 1);
        return s.substring(0, Math.min(s.length(), keep)) + "…";
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
