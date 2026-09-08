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
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.reflect.JRec;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
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
     * One precomputed row in the tree.  The {@code name} is what is rendered —
     * for flattened rows it is the joined path (e.g. {@code classes/com/example/scratch});
     * the {@code entry} remains the real node for selection/expansion.
     */
    private record TreeRow(CommonUtil.TreeEntry entry, String prefix, String name, String suffix) {
        String fullLine() {
            return prefix + name + suffix;
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
        final Call code = (c != null && c.isObjCall()) ? c.as() : noobj();
        final Border border = this.style.border();

        // xref config: xref=>[max=>N, code=><call>].  Present (as a rec) enables the
        // cross-reference pass; absent keeps the historical behavior byte-for-byte.
        final Obj xrefRec = this.at(uri(XREF));
        final boolean xrefEnabled = xrefRec != null && xrefRec.isRec();
        int xrefMax = 2;
        Call xrefCode = noobj();
        if (xrefEnabled) {
            final Rec xr = (Rec) xrefRec;
            final Obj xm = xr.at(uri(MAX));
            if (xm != null && xm.isInt()) xrefMax = xm.asInt().intValue().intValue();
            final Obj xc = xr.at(uri(CODE));
            if (xc != null && xc.isObjCall()) xrefCode = xc.as();
        }

        if (this.flatten()) {
            final List<CommonUtil.TreeEntry> entries = new ArrayList<>();
            CommonUtil.treeConsumer(root, max, this.forceExpand, entries::add);
            this.buildFlattenedRows(entries, code, border);
        } else {
            final boolean[] lastStack = new boolean[Math.max(max, 1) + 32]; // generous upper bound for expanded branches
            CommonUtil.treeConsumer(root, max, this.forceExpand, entry -> {
                final int d = entry.depth();
                if (d > 0) lastStack[d - 1] = entry.isLast();
                final String prefix = treePrefix(d, lastStack, entry.isLast(), border);
                final Obj mapped = code.isNoObj() ? noobj() : code.apply(entry.obj());
                final String suffix = stringSuffix(mapped);
                rows.add(new TreeRow(entry, prefix, entry.name(), suffix));
            });
        }
        if (xrefEnabled) this.decorateXrefs(xrefMax, xrefCode);
    }

    /**
     * A canonical target cluster: a parent folder's alias children whose targets
     * share a lowest-common-ancestor uri, with the member alias links.
     */
    private static final class XrefCluster {
        private String targetPath;
        private final List<XrefLink> links = new ArrayList<>();
        private XrefCluster(final String targetPath) {
            this.targetPath = targetPath;
        }
    }

    /** One auto-pointer link: the alias node uri and the canonical uri it points at. */
    private record XrefLink(fURI alias, fURI target) {
    }

    /**
     * Cross-reference decoration pass.  Runs over the already-built {@link #rows}
     * (one row per tree entry — rail/leaf markers ride on the row's suffix rather
     * than interleaving new lines, so row-to-entry mappings stay 1:1) and:
     * <ul>
     * <li>folds alias leaves under one parent whose targets share a common
     *     ancestor into a single {@code ──(N)──> label} rail on the parent row
     *     when the cluster holds at least {@code xrefMax} links;</li>
     * <li>annotates smaller/loose alias leaves with a {@code »label} suffix;</li>
     * <li>tags rendered canonical target rows with an inbound {@code ⇇N} count.</li>
     * </ul>
     * With no {@code xref.code} call, labels default to the target's tail segment.
     */
    private void decorateXrefs(final int xrefMax, final Call xrefCode) {
        if (rows.isEmpty()) return;
        final int size = rows.size();
        final Map<fURI, Integer> rowIndexOf = new HashMap<>();
        final Map<fURI, fURI> parentOf = new HashMap<>();
        final Map<String, Integer> rowIndexOfPath = new HashMap<>();
        final fURI[] atDepth = new fURI[size + 1];
        for (int i = 0; i < size; i++) {
            final CommonUtil.TreeEntry e = rows.get(i).entry;
            rowIndexOf.put(e.uri(), i);
            rowIndexOfPath.put(noTrailingSlash(e.uri().toString()), i);
            final int d = e.depth();
            if (d > 0 && null != atDepth[d - 1]) parentOf.put(e.uri(), atDepth[d - 1]);
            atDepth[d] = e.uri();
        }
        // Collect alias leaves, grouped by their parent folder.  Each alias's canonical
        // target is captured during the tree walk itself (CommonUtil._treeWalk reads the
        // raw +/ listing, where auto pointers (!* / !@) surface as raw inst values).
        final Map<fURI, List<XrefLink>> linksByParent = new HashMap<>();
        final List<XrefLink> looseLinks = new ArrayList<>(); // parent not rendered (flatten) — leaf-only
        for (int i = 0; i < size; i++) {
            final CommonUtil.TreeEntry e = rows.get(i).entry;
            final fURI target = e.xref();
            if (null == target || target.equals(e.uri())) continue;
            final fURI parent = parentOf.get(e.uri());
            final XrefLink link = new XrefLink(e.uri(), target);
            if (null == parent) looseLinks.add(link);
            else linksByParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(link);
        }
        // Cluster each parent's links by lowest common ancestor of their targets.
        final Map<fURI, List<XrefCluster>> clustersByParent = new HashMap<>();
        final Map<String, Integer> inbound = new HashMap<>();
        for (final Map.Entry<fURI, List<XrefLink>> entry : linksByParent.entrySet()) {
            final List<XrefCluster> clusters = new ArrayList<>();
            for (final XrefLink link : entry.getValue()) {
                XrefCluster hit = null;
                for (final XrefCluster cluster : clusters) {
                    final String lca = lcaPath(cluster.targetPath, link.target().toString());
                    if (null == lca) continue;
                    if (null == hit) {
                        hit = cluster;
                        cluster.targetPath = lca;
                    } else { // this link bridges two clusters — merge them
                        hit.targetPath = lcaPath(hit.targetPath, cluster.targetPath);
                        hit.links.addAll(cluster.links);
                        clusters.remove(cluster);
                        break;
                    }
                }
                if (null == hit) {
                    final XrefCluster cluster = new XrefCluster(link.target().toString());
                    clusters.add(cluster);
                    hit = cluster;
                }
                hit.links.add(link);
            }
            clustersByParent.put(entry.getKey(), clusters);
        }
        // Emit rails (clusters at/over threshold) and leaf markers, collect inbound.
        final Map<Integer, StringBuilder> extras = new HashMap<>();
        for (final Map.Entry<fURI, List<XrefCluster>> entry : clustersByParent.entrySet()) {
            final Integer parentIdx = rowIndexOf.get(entry.getKey());
            if (null == parentIdx) continue;
            for (final XrefCluster cluster : entry.getValue()) {
                if (cluster.links.size() >= xrefMax) {
                    extras.computeIfAbsent(parentIdx, k -> new StringBuilder())
                            .append("  ──(").append(cluster.links.size()).append(")──> ")
                            .append(xrefLabel(f(cluster.targetPath), xrefCode));
                    inbound.merge(cluster.targetPath, cluster.links.size(), Integer::sum);
                } else {
                    for (final XrefLink link : cluster.links) {
                        final Integer idx = rowIndexOf.get(link.alias());
                        if (null == idx) continue;
                        extras.computeIfAbsent(idx, k -> new StringBuilder())
                                .append("  »").append(xrefLabel(link.target(), xrefCode));
                        inbound.merge(link.target().toString(), 1, Integer::sum);
                    }
                }
            }
        }
        for (final XrefLink link : looseLinks) {
            final Integer idx = rowIndexOf.get(link.alias());
            if (null == idx) continue;
            extras.computeIfAbsent(idx, k -> new StringBuilder())
                    .append("  »").append(xrefLabel(link.target(), xrefCode));
            inbound.merge(link.target().toString(), 1, Integer::sum);
        }
        // Inbound counts on rendered canonical rows.
        for (final Map.Entry<String, Integer> entry : inbound.entrySet()) {
            final Integer idx = rowIndexOfPath.get(noTrailingSlash(entry.getKey()));
            if (null == idx) continue;
            extras.computeIfAbsent(idx, k -> new StringBuilder()).append("  ⇇").append(entry.getValue());
        }
        // Rebuild rows with decorated suffixes (never change row count/order).
        for (int i = 0; i < rows.size(); i++) {
            final StringBuilder extra = extras.get(i);
            if (null == extra) continue;
            final TreeRow row = rows.get(i);
            rows.set(i, new TreeRow(row.entry, row.prefix, row.name, row.suffix + extra));
        }
    }

    /**
     * @return {@code path} without a trailing branch marker, so row uris and
     * cross-reference targets compare on equal footing.
     */
    private static String noTrailingSlash(final String path) {
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    /**
     * @return the shared ancestor path of two uri paths (segment-wise), or null
     * when the paths share no common prefix.
     */
    private static String lcaPath(final String a, final String b) {
        final String[] as = a.split("/");
        final String[] bs = b.split("/");
        final int max = Math.min(as.length, bs.length);
        int i = 0;
        while (i < max && as[i].equals(bs[i])) i++;
        if (i == 0) return null;
        return String.join("/", java.util.Arrays.copyOf(as, i));
    }

    /**
     * Label for a canonical target: the {@code xref.code} call applied to a rec of
     * {@code path} (lst of segments), {@code name} (tail segment) and {@code uri}
     * (target uri) — or, with no call (or a call that throws/returns nothing), the
     * plain tail segment.
     */
    private static String xrefLabel(final fURI target, final Call xrefCode) {
        final String tail = target.asNode().name();
        if (null == xrefCode || xrefCode.isNoObj()) return tail;
        try {
            final Obj out = xrefCode.apply(xrefArg(target));
            if (null == out || out.isNoObj()) return tail;
            return out.isStr() ? out.strValue() : out.toShortString();
        } catch (final RuntimeException e) {
            return tail;
        }
    }

    private static Obj xrefArg(final fURI target) {
        final String[] segments = target.toString().split("/");
        final List<Obj> path = new ArrayList<>();
        for (final String segment : segments)
            if (!segment.isEmpty()) path.add(str(segment));
        return rec(uri(PATH), lst(path.toArray(new Obj[0])),
                uri(NAME), str(target.asNode().name()),
                uri(URI), uri(target));
    }

    private boolean flatten() {
        final Obj f = this.at(uri("flatten"));
        return f.isBool() && f.asBool().jvm();
    }

    /**
     * A node is a folder when it has children of its own or its URI is a branch
     * (trailing {@code /}).  Files — and empty branches without the marker — are
     * not folders.
     */
    private static boolean isFolder(final CommonUtil.TreeEntry entry) {
        return entry.childCount() > 0 || entry.uri().isBranch();
    }

    /**
     * Flatten chains of folders that have no file (leaf) children into a single
     * path row (e.g. {@code classes/com/example/scratch}).  A folder is folded
     * into the running path when it has exactly one child that is itself a
     * folder; the chain ends at a node with file children, multiple children,
     * or no children.  Children of the final folder hang one level below the
     * flattened path — never pushed right by the path's full width.
     */
    private void buildFlattenedRows(final List<CommonUtil.TreeEntry> entries,
                                    final Call code, final Border border) {
        final int maxNatural = entries.stream().mapToInt(CommonUtil.TreeEntry::depth).max().orElse(0);
        final boolean[] lastStack = new boolean[maxNatural + 64];
        // Active flatten offsets, one per flattened break node: {breakDepth, levelsFolded}.
        // While inside such a subtree, each node's display depth is natural − Σ offsets.
        final Deque<int[]> offsets = new ArrayDeque<>();
        int delta = 0;
        final List<String> pending = new ArrayList<>();
        int pendingStartDisplay = 0;
        boolean pendingIsLast = false;

        for (int i = 0; i < entries.size(); i++) {
            final CommonUtil.TreeEntry entry = entries.get(i);
            final int d = entry.depth();

            // Leaving subtrees whose flattened break node is no longer active.
            while (!offsets.isEmpty() && offsets.peek()[0] >= d) {
                delta -= offsets.pop()[1];
            }

            final int disp = d > 0 ? Math.max(1, d - delta) : 0;
            if (d > 0) lastStack[disp - 1] = entry.isLast();

            if (d == 0) {
                // The root is always its own row — never folded into a path.
                final String suffix = stringSuffix(code.apply(entry.obj()));
                rows.add(new TreeRow(entry, "", entry.name(), suffix));
                continue;
            }

            // Fold when the node has exactly one child and that child is a folder
            // (i.e. this node has no file children of its own).
            final boolean flattenable = entry.childCount() == 1
                    && i + 1 < entries.size()
                    && entries.get(i + 1).depth() == d + 1
                    && isFolder(entries.get(i + 1));

            if (flattenable) {
                if (pending.isEmpty()) {
                    pendingStartDisplay = disp;
                    pendingIsLast = entry.isLast();
                }
                pending.add(entry.name());
                continue; // folded into the running path — no row yet
            }

            // Break node (or leaf): emit, carrying any accumulated path.
            final String prefix;
            final String name;
            if (pending.isEmpty()) {
                prefix = treePrefix(disp, lastStack, entry.isLast(), border);
                name = entry.name();
            } else {
                prefix = treePrefix(pendingStartDisplay, lastStack, pendingIsLast, border);
                name = String.join("/", pending) + "/" + entry.name();
                // This node's subtree displays one level under the flattened path.
                final int folded = pending.size();
                offsets.push(new int[]{d, folded});
                delta += folded;
                pending.clear();
            }
            final String suffix = stringSuffix(code.apply(entry.obj()));
            rows.add(new TreeRow(entry, prefix, name, suffix));
        }
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
        sb.append(tee)/*.append(arm)*/.append(arm).append(" ");
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
