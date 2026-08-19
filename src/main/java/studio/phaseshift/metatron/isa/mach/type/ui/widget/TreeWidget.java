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

package studio.phaseshift.metatron.isa.mach.type.ui.widget;

import org.jline.terminal.Cursor;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.reflect.JRec;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.id_;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TreeWidget extends JRec<TreeWidget> implements Widget<TreeWidget> {

    private final List<TreeRow> rows = new ArrayList<>();
    private Style<TreeWidget> style = Style.empty();
    private Cursor cursor;
    private Set<fURI> forceExpand = Set.of();

    /**
     * One precomputed row in the tree.
     */
    private record TreeRow(CommonUtil.TreeEntry entry, String prefix, String suffix) {
        String fullLine() {
            return prefix + entry.name() + suffix;
        }
    }

    // ── JRec constructor ───────────────────────────────────────────

    public TreeWidget(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        if (this.style.border() == Border.none) this.style.border(Border.continuous);
        readStyle();
    }

    private void readStyle() {
        final Obj s = this.at(uri("style"));
        if (s != null && s.isRec()) {
            final Style<TreeWidget> st = Style.from(s.as());
            st.stylable = this;
            this.style(st);
        }
    }

    /* ================================================================
     * Row building
     * ================================================================ */

    private void ensureBuilt() {
        this.buildRows();
    }

    /**
     * Set URIs whose children should always be read regardless of {@link #max}
     * depth, enabling per-branch expansion.  Triggers a rebuild on next render.
     */
    public void forceExpand(final Set<fURI> forceExpand) {
        this.forceExpand = Objects.requireNonNull(forceExpand);
    }

    private void buildRows() {
        rows.clear();
        final Obj r = this.at(uri(ROOT));
        final fURI root = (r != null && r.isUri()) ? r.uriValue() : null;
        if (null == root) return;
        final Obj m = this.at(uri(MAX));
        final int max = (m != null && m.isInt()) ? m.asInt().intValue().intValue() : 0;
        final Obj c = this.at(uri(CODE));
        final Call code = (c != null && c.isInst()) ? c.as() : id_().tryToInst();
        final boolean[] lastStack = new boolean[Math.max(max, 1) + 32]; // generous upper bound for expanded branches
        final Border border = this.style.border();
        CommonUtil.treeConsumer(root, max, this.forceExpand, entry -> {
            final int d = entry.depth();
            if (d > 0) lastStack[d - 1] = entry.isLast();
            final String prefix = treePrefix(d, lastStack, entry.isLast(), border);
            final Obj mapped = code.apply(entry.obj());
            final String suffix = stringSuffix(mapped);
            rows.add(new TreeRow(entry, prefix, suffix));
        });
    }

    private static String treePrefix(final int depth, final boolean[] lastStack,
                                     final boolean isLast, final Border border) {
        if (depth == 0) return "";
        final StringBuilder sb = new StringBuilder();
        for (int level = 1; level < depth; level++) {
            sb.append(lastStack[level - 1] ? "    " : border.leftSide() + "   ");
        }
        final String tee = isLast ? border.bottomLeftCorner() : border.leftIntersection();
        final String arm = border.topSide();
        sb.append(tee).append(arm).append(arm).append(" ");
        return sb.toString();
    }

    private static String stringSuffix(final Obj obj) {
        if (obj == null || obj.isNoObj()) return "";
        return "  " + obj.toShortString();
    }

    /* ================================================================
     * Widget contract
     * ================================================================ */

    @Override
    public TreeWidget cursor(final Cursor cursor) {
        this.cursor = cursor;
        return this;
    }

    @Override
    public Style<TreeWidget> getStyle() {
        return this.style;
    }

    @Override
    public TreeWidget style(final Style<TreeWidget> style) {
        this.style = style;
        if (this.style.border() == Border.none) this.style.border(Border.continuous);
        return this;
    }

    @Override
    public void close() {
        Widget.super.close();
    }

    @Override
    public String renderInPlace() {
        return this.format() + "\n";
    }

    @Override
    public String renderFresh() {
        return this.format() + "\n";
    }

    /* ================================================================
     * Rendering
     * ================================================================ */

    public void refresh() {
        ensureBuilt();
    }

    @Override
    public String format() {
        ensureBuilt();
        final StringBuilder sb = new StringBuilder();
        final String fg = this.style.foreground();
        final String bg = this.style.background();
        for (final TreeRow row : rows) {
            sb.append(bg).append(fg).append(row.fullLine()).append("\n");
        }
        if (!sb.isEmpty()) sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    public List<String> rowStrings() {
        return Arrays.asList(this.format().split("\n"));
    }

    /* ================================================================
     * Accessors
     * ================================================================ */

    public List<CommonUtil.TreeEntry> entries() {
        ensureBuilt();
        return rows.stream().map(TreeRow::entry).toList();
    }

    public int rowCount() {
        return rows.size();
    }
}
