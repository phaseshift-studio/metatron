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

import org.jline.terminal.Terminal;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static studio.phaseshift.metatron.isa.m.math.mathInstSet.DATETIME_TYPE;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.humanReadableDatetime;

/**
 * CardUtil — shared panel-card factory for consistent look &amp; feel across tools.
 * <p>
 * Two visual styles:
 * <ul>
 *   <li><b>card</b> — blue border, used by {@code SwipePanelWidgetTool}
 *       for browsing obj streams.</li>
 *   <li><b>popup</b> — green border, used by {@code ExplainTool} for
 *       drill-down info panels.</li>
 * </ul>
 * Both use the same identity-title and docq-body formatting.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class CardUtil {

    private CardUtil() {
    }

    // =====================================================================
    // Borders
    // =====================================================================

    public static Border cardBorder() {
        return Border.continuous.foreground("{{b}}");
    }

    public static Border popupBorder() {
        return Border.continuous.foreground("{{g}}");
    }

    // =====================================================================
    // Panel factories
    // =====================================================================

    /**
     * Build a card panel for an Obj: blue border, identity title,
     * docq-lookup body (falling back to {@link Highlighter#format}).
     */
    public static PanelWidget card(final Obj obj) {
        final PanelWidget p = new PanelWidget(titleOf(obj), bodyOf(obj));
        p.style().border(cardBorder()).applyStyle();
        constrainWidth(p);
        return p;
    }

    /**
     * Build a popup panel with a caller-supplied title: green border,
     * blue title, and the given body string.
     */
    public static PanelWidget popup(final String title, final String body) {
        final PanelWidget p = new PanelWidget("{{b}}" + title + body);
        p.style().border(popupBorder()).applyStyle();
        constrainWidth(p);
        return p;
    }

    /**
     * Build a popup panel for an Obj: green border, auto-generated
     * identity title, docq-lookup body.
     */
    public static PanelWidget popup(final Obj obj) {
        return popup(titleOf(obj), bodyOf(obj));
    }

    // =====================================================================
    // Title
    // =====================================================================

    /**
     * Build an identity title string for the given Obj.
     * <ul>
     *   <li><b>Type</b> — {@code VID::T} (base) or {@code VID::T refines TID::T}.</li>
     *   <li><b>Inst</b> — {@code basePath::T refines /m/inst::T}.</li>
     *   <li><b>Value</b> — {@code TID::T}.</li>
     * </ul>
     */
    public static String titleOf(final Obj obj) {
        if (obj.isType()) {
            if (((studio.phaseshift.metatron.isa.m.type.Type) obj).isBaseType()) {
                return Graphitty.string("{{b}}%s{{\\b}}{{m}}::T{{\\m}}", obj.vid());
            } else {
                return Graphitty.string("{{b}}%s{{\\b}}{{m}}::T{{\\m}} refines {{b}}%s{{\\b}}{{m}}::T{{\\m}}",
                        obj.vid(), obj.tid());
            }
        }
        if (obj.isInst()) {
            return Graphitty.string("{{b}}%s{{\\b}}{{m}}::T{{\\m}} refines {{b}}/m/inst{{\\b}}{{m}}::T{{\\m}}",
                    obj.tid().basePath());
        }
        // Value obj — use its type identity
        final fURI tid = obj.tid();
        if (tid != null && !tid.isGeneric()) {
            return Graphitty.string("{{b}}%s{{\\b}}{{m}}::T{{\\m}}", tid);
        }
        return Graphitty.string("{{b}}%s{{\\b}}", obj.getClass().getSimpleName());
    }

    // =====================================================================
    // Body
    // =====================================================================

    /**
     * Build body content for an Obj: docq lookup if documentation exists,
     * otherwise {@link Highlighter#format} of the raw obj.
     */
    public static String bodyOf(final Obj obj) {
        // Only do docq lookup for Types and Insts — plain values show
        // their content directly via Highlighter.format.
        if (obj.isType() || obj.isInst()) {
            final fURI key = Obj.Helper.specificTypeId(obj);
            if (key != null) {
                final Obj docObj = Router.readFromSpace(key.addQ(QCollection.DOCQ));
                if (docObj.isRec() && !QCollection.isNoDocs(docObj)) {
                    final QCollection.Docs docs = new QCollection.Docs(docObj.asRec());
                    final StringBuilder sb = new StringBuilder();

                    final String desc = docs.description();
                    if (desc != null && !desc.isBlank() && !desc.equals(QCollection.NO_DOCS_STRING)) {
                        sb.append("{{w}}").append(desc).append("{{X}}");
                    }

                    final Poly<?, ?> docsArgs = docs.args();
                    if (docsArgs != null && !docsArgs.isEmpty()) {
                        if (!sb.isEmpty()) sb.append("\n\n");
                        sb.append("{{m}}args:{{X}}\n").append(Highlighter.format(docsArgs));
                    }

                    final List<String> examples = docs.examples();
                    if (!examples.isEmpty()) {
                        if (!sb.isEmpty()) sb.append("\n\n");
                        sb.append("{{m}}examples:{{X}}\n");
                        for (int i = 0; i < examples.size(); i++) {
                            sb.append("  {{w}}").append(examples.get(i)).append("{{X}}");
                            if (i < examples.size() - 1) sb.append("\n");
                        }
                    }

                    if (!sb.isEmpty()) return sb.toString();
                }
            }
        }
        // Fallback: highlight the obj directly
        return Highlighter.format(obj);
    }

    /**
     * Build the default explorer card for an Obj.
     * <ul>
     *   <li><b>Type with poly isaPredicate</b> → interactive Selector
     *       over predicate fields (see {@link #predicateTable}).</li>
     *   <li><b>Poly value (Rec/Lst)</b> → interactive Selector over
     *       instance key/value pairs (see {@link #valueTable}).</li>
     *   <li><b>Otherwise</b> → standard {@link PanelWidget} card.</li>
     * </ul>
     * Enter on a row recurses through {@code explorerCard(drillObj).run()}
     * so nested Recs/Lsts and Types compose naturally.
     */
    public static Widget<?> explorerCard(final Obj obj) {
        // Type with poly isaPredicate → interactive Selector
        if (obj.isType()) {
            final TableWidget table = predicateTable((Type) obj);
            if (table != null) return selectorFor(table);
            return card(obj);
        }
        // Poly values (Rec/Lst) → interactive Selector
        if (obj.isPoly()) {
            return selectorFor(valueTable(obj.as()));
        }
        // Datetime → special card with human-readable format
        if (obj.testNominally(DATETIME_TYPE)) {
            return datetimeCard(obj);
        }
        // Default PanelWidget
        return card(obj);
    }

    // =====================================================================
    // Datetime card
    // =====================================================================

    /**
     * Build a card for a datetime value: standard title + docq body,
     * the raw datetime URI, and a human-readable ISO line indented
     * with a corner arrow.
     */
    private static PanelWidget datetimeCard(final Obj dt) {
        final String body = dt + "\n"
                + "  {{g}}╰{{X}}"
                + humanReadableDatetime(dt.asUri());
        final PanelWidget p = new PanelWidget(titleOf(dt), body);
        p.style().border(cardBorder()).applyStyle();
        constrainWidth(p);
        return p;
    }

    /**
     * Wrap a table in a Selector with recursive drill-down on Enter.
     */
    private static Selector selectorFor(final TableWidget table) {
        return new Selector()
                .style()
                .attachment(table, true)
                .pointer("{{r}}>")
                .applyStyle()
                .onSelect((s, row, col) -> {
                    // row = attachment row index; data rows start after
                    // the header (row 0).  Subtract 1 for metadata lookup.
                    final Obj drillObj = predicateValueAt(table, row - 1);
                    if (drillObj != null) {
                        explorerCard(drillObj).run();
                    }
                });
    }

    // =====================================================================
    // Table builders
    // =====================================================================

    /**
     * Build a {@link TableWidget} from a Type's poly {@code isaPredicate}.
     * Returns {@code null} if the type has no poly predicate.
     */
    public static TableWidget predicateTable(final Type type) {
        final Obj predObj = type.isPredicateObj();
        if (predObj == null || !predObj.isPoly()) return null;
        return buildTable(predObj.as(), "type");
    }

    /**
     * Build a {@link TableWidget} from a Poly value's instance data
     * (Rec key/value pairs or Lst indexed entries).
     */
    public static TableWidget valueTable(final Poly<?, ?> poly) {
        return buildTable(poly, "value");
    }

    /**
     * Shared table builder for both predicate and value explorers.
     */
    private static TableWidget buildTable(final Poly<?, ?> poly, final String valueHeader) {
        final TableWidget table = new TableWidget(List.of(
                poly.isRec() ? "key" : "#", valueHeader));
        table.style()
                .border(Border.continuous.foreground("{{b}}"))
                .headerDivider("{{b}}" + Border.continuous.leftSide() + "{{X}}")
                .divider("{{b}}" + Border.continuous.leftSide())
                .pointer("{{r}}>")
                .applyStyle();

        if (poly.isRec()) {
            for (final Rel entry : poly.asRec().<Rel>elements().toList()) {
                final Obj key = entry.first();
                final Obj val = entry.second();
                table.addRow(List.of(key.toString(), cellSummary(val)));
                table.addMetadata(List.of(val));
            }
        } else {
            final AtomicInteger idx = new AtomicInteger(0);
            for (final Obj val : poly.asLst().<Obj>elements().toList()) {
                table.addRow(List.of(String.valueOf(idx.getAndIncrement()), cellSummary(val)));
                table.addMetadata(List.of(val));
            }
        }
        return table;
    }

    /**
     * Compact summary string for a table cell — clips long strings.
     */
    private static String cellSummary(final Obj val) {
        if (val.isRec()) return "{…" + val.asRec().count() + " rels}";
        if (val.isLst()) return "[…" + val.asLst().count() + " objs]";
        if (val.isStr()) return clipQuoted(val.toString(), 80);
        if (val.isType()) return ((Type) val).vid().small().toString();
        return Utilities.textClip(val.toString(), 80);
    }

    /**
     * Clip a quoted string for table-cell display, preserving the closing
     * quote ({@code """}, {@code "}, {@code '}, etc.) after the ellipsis
     * so syntax highlighting stays balanced.
     */
    private static String clipQuoted(final String s, final int max) {
        final String flat = s.replace('\n', ' ').replace('\r', ' ');
        if (flat.length() <= max) return flat;
        final String close = closingQuote(flat);
        final int keep = Math.max(1, max - 1 - close.length());
        return flat.substring(0, Math.min(keep, flat.length() - close.length())) + "…" + close;
    }

    private static String closingQuote(final String s) {
        if (s.endsWith("\"\"\"")) return "\"\"\"";
        if (s.endsWith("'''")) return "'''";
        if (s.endsWith("\"")) return "\"";
        if (s.endsWith("'")) return "'";
        return "";
    }

    /**
     * Set a reasonable max-width on a panel so body text wraps.
     */
    private static void constrainWidth(final PanelWidget panel) {
        try {
            final Terminal term = Console.getTerminal();
            if (term != null) {
                final int avail = term.getWidth() - 6;
                if (avail > 20) panel.maxWidth(avail);
            }
        } catch (final Exception ignored) {
            // terminal not available — no wrapping
        }
    }

    /**
     * Return the drill-down Obj stored in metadata slot 0 of the given
     * predicate explorer table row, or {@code null}.
     */
    public static Obj predicateValueAt(final TableWidget table, final int row) {
        if (table == null || row < 0 || row >= table.rows().size()) return null;
        final List<Object> meta = table.rowMetadata(row);
        return (meta != null && !meta.isEmpty() && meta.get(0) instanceof Obj o) ? o : null;
    }
}
