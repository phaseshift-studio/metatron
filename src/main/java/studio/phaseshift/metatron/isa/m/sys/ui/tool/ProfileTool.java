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

    public ProfileTool(final Call call, final int argsWidth) {
        this.instTable = new TableWidget(List.of("op", "dom", "rng", "args", "f", "desc", "c_dom", "c_rng"));
        cInt dom = cInt.ONE();
        cInt rng = cInt.ONE();
        boolean first = true;
        for (final Inst i : call.insts()) {
            final Inst.Form form = Inst.Form.of(i);
            dom = first ? i.dom().c() : rng;
            boolean inDom = i.dom().c().lte(rng);
            // an initial inst makes its objs from its args, not from what flows in (the head, and the start() after
            // every ';'), so its count is its own range's -- as a reducer's is one
            rng = (form == Inst.Form.reducer) ? cInt.ONE() : (first || form == Inst.Form.initial ? i.rng().c() : i.rng().c().mult(dom));
            first = false;
            boolean found = !Machine.current().memory().read(i.tid().basePath()).isNoObj();
            this.instTable.addRow(List.of(
                    (found ? "{{b}}" : "{{r}}") + i.tid().name() + (i.tid().c().isOne() ? "" : ("{" + i.tid().c() + "}")),
                    i.dom().vid().small() + "::T",
                    i.rng().vid().small() + "::T",
                    (i.args().elements().allMatch(x -> x.isResolved(true)) ? "{{g}}" : "{{y}}") + argsSummary(i.args(), argsWidth),
                    i.hasf() ? (i.f().isLambda() ? "{{y}}<j>" : "{{y}}<m>") : "{{r}}<?>",
                    "{{m}}" + form.toString(),
                    "{{g}}{{{" + (inDom ? "y" : "r") + "}}" + dom.toString() + "{{g}}}{{X}}",
                    "{{g}}{{{y}}" + rng.toString() + "{{g}}}{{X}}")).style().background("{{[b]}}").foreground("{{y}}").divider("{{r}}|").apply();
            this.instTable.addMetadata(List.of(i, i.dom(), i.rng(), i.args(), null == i.f() ? Inst.f.of(noobj()) : i.f(), i, dom, rng));
        }
        this.style().attachment(this.instTable, true).apply();
    }

    public Lst getTable() {
        return this.instTable.at(ROW).stream().collect(new CommonUtil.LstCollector());
    }


    /** The args column is never narrower than this (the "args" header width). */
    public static final int ARGS_MIN_WIDTH = 4;

    /** The other 7 columns + dividers + padding + border, which the args column leaves room for (plus a 3-col safety margin). */
    private static final int ARGS_NON_ARGS = 65;

    /**
     * The args column width: wide enough to show the args without clipping (up to
     * {@code naturalWidth}), capped at the terminal's room (minus the other columns'
     * fixed footprint and the level's indent), never narrower than {@link #ARGS_MIN_WIDTH}.
     */
    public static int argsWidth(final int terminalWidth, final int offsetX, final int naturalWidth) {
        return Math.max(ARGS_MIN_WIDTH, Math.min(naturalWidth, terminalWidth - offsetX - ARGS_NON_ARGS));
    }

    /**
     * The width the args column needs to show every argument without a {@code …} clip —
     * the widest row's {@code <slot> + tokens joined by ", "}.
     */
    public static int naturalArgsWidth(final Call call) {
        int max = 0;
        for (final Inst i : call.insts())
            max = Math.max(max, Highlighter.visualLength(" " + String.join(", ", argTokens(i.args()))));
        return max;
    }

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
     * The args cell as plain text, clipped or padded to exactly {@code width} columns.
     * The leading space is the pointer slot: the inline selector replaces it (or a
     * comma-space) with {@code >}, so the arg text never shifts column.
     */
    public static String argsSummary(final Poly<?, ?> args, final int width) {
        return padToWidth(" " + String.join(", ", argTokens(args)), width);
    }

    public static String summarizeArg(final Obj o) {
        return o.isCall()
                ? o.asCall().insts().stream().map(i -> i.tid().name()).reduce((a, b) -> a + "." + b).orElse("")
                : o.toShortString();
    }

    private static String padToWidth(final String s, final int width) {
        final int len = Highlighter.visualLength(s);
        if (len == width) return s;
        if (len > width) return clipToWidth(s, width);
        return s + " ".repeat(width - len);
    }

    private static String clipToWidth(final String s, final int width) {
        final int keep = Math.max(0, width - 1);
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
