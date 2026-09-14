/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package studio.phaseshift.metatron.isa.mach.type.ui.widget;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.ui.Stylable;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_TABLE_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A table is three rec keys and nothing else, and it must not matter who wrote them:
 * the same table built through the Java API and built from a rec render identically,
 * cell for cell and type for type.  The JRec version failed exactly that — it kept
 * Java {@code List}s for one path and converted the rec for the other, so a column
 * could hand back a {@code String} or an {@link Obj} depending on its history.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TableWidgetTest extends AbstractMetatronTest {

    /**
     * The rendered content lines: markup stripped, each line trimmed, framing dropped.
     * {@code Border.none} — the default — draws its frame in spaces, so the frame
     * arrives as blank lines, and the left frame column as leading whitespace; what a
     * table test cares about is the text and the column padding between cells.
     */
    private static List<String> rendered(final TableWidget table) {
        return Highlighter.unformat(table.format()).lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    private static TableWidget built(final List<String> headers, final List<List<Object>> rows) {
        final TableWidget table = new TableWidget(headers);
        rows.forEach(table::addRow);
        return table;
    }

    private static TableWidget fromRec(final Obj header, final Obj row) {
        final Map<Obj, Obj> jvm = mutableMap();
        if (null != header) jvm.put(uri(HEADER), header);
        if (null != row) jvm.put(uri(ROW), row);
        return new TableWidget(jvm, UI_TABLE_TID, null);
    }

    /* ================================================================
     * One table, two ways to write it
     * ================================================================ */

    @Test
    public void testAJavaBuiltAndARecBuiltTableAreTheSameTable() {
        final List<String> headers = List.of("name", "qty");
        final List<Object> row = List.of("alpha", "3");
        final TableWidget java = built(headers, List.of(row));
        final TableWidget rec = fromRec(lst(str("name"), str("qty")),
                lst(lst(str("alpha"), str("3"))));

        assertEquals(headers, rec.headers(), "headers read back from the rec");
        assertEquals(List.of(row), rec.rows(), "rows read back from the rec");
        assertEquals(rendered(java), rendered(rec), "and both render identically:\n"
                + java.format() + "\n---\n" + rec.format());
        assertEquals(java.entry(0, 1), rec.entry(0, 1), "with cell values of the same Java type");
    }

    @Test
    public void testAUriCellReadsBackAsAFuriWhicheverWayTheTableWasWritten() {
        // SubsWidget casts entry(r, c) straight to fURI, so a uri cell has to arrive as
        // one — from the rec as much as from the Java API (the JRec reader only unwrapped
        // strs, so the two paths disagreed here)
        final fURI target = studio.phaseshift.metatron.furi.fURI.Singleton.f("/m/mach/ui");
        final TableWidget java = built(List.of("uri"), List.of(List.of(target)));
        final TableWidget rec = fromRec(null, lst(lst(uri(target))));

        assertInstanceOf(fURI.class, java.entry(0, 0), "the Java path hands back the fURI");
        assertInstanceOf(fURI.class, rec.entry(0, 0), "and so does the rec path");
        assertEquals(target, rec.entry(0, 0));
    }

    @Test
    public void testMetadataCarriesDataAsObjs() {
        // CardUtil.predicateValueAt and ExplainTool's (Type)/(cInt) casts read metadata
        // cells back as Objs, so metadata must not be flattened to text
        final TableWidget table = new TableWidget(List.of("key", "value"));
        table.addRow(List.of("a", "1")).addMetadata(List.of(jnt(7), str("seven")));

        final List<Object> metadata = table.rowMetadata(0);
        final Obj objCell = assertInstanceOf(Obj.class, metadata.get(0), "an obj cell stays an Obj");
        assertEquals(7, objCell.intValue().intValue());
        assertEquals("seven", metadata.get(1), "and a str cell is still text");
        assertEquals("1", table.entry(0, 1), "the display row and its metadata are separate");
        assertTrue(rendered(table).get(1).contains("a"), "metadata is never drawn:\n" + table.format());
        assertFalse(rendered(table).get(1).contains("seven"), "metadata is never drawn:\n" + table.format());
    }

    /* ================================================================
     * Rendering
     * ================================================================ */

    @Test
    public void testHeadersAndRows() {
        final TableWidget table = built(List.of("name", "qty"),
                List.of(List.of("alpha", "3"), List.of("beta", "12")));
        assertEquals(List.of("name  qty", "alpha 3", "beta  12"), rendered(table),
                "a header line and one line per row, columns padded to the widest cell:\n" + table.format());
    }

    @Test
    public void testRaggedRowsRenderAndSkipMissingColumns() {
        final TableWidget table = built(List.of("a", "b", "c"), List.of(List.of("one")));
        assertEquals(List.of("a   b c", "one"), rendered(table), "a short row draws what it has:\n" + table.format());
        assertEquals(List.of("one"), table.column(0));
        assertTrue(table.column(2).isEmpty(), "a column nothing has is empty, not an exception");
    }

    @Test
    public void testAnEmptyTableRendersNothing() {
        assertEquals("", new TableWidget().format(), "no headers and no rows is the empty string");
        assertEquals(List.of("a b"), rendered(built(List.of("a", "b"), List.of())),
                "headers with no rows still draw the header line");
    }

    /* ================================================================
     * Writes
     * ================================================================ */

    @Test
    public void testUpsertReplacesTheRowWithMatchingKey() {
        final TableWidget table = new TableWidget(List.of("layer", "pct"));
        table.upsertRow(List.of("layer1", "10"), 0);
        assertEquals(1, table.rows().size());
        table.upsertRow(List.of("layer1", "58"), 0);
        assertEquals(1, table.rows().size(), "the same key updates in place");
        assertEquals("58", table.entry(0, 1));
        table.upsertRow(List.of("layer2", "23"), 0);
        assertEquals(2, table.rows().size(), "a new key appends");
    }

    @Test
    public void testClearDropsTheDataAndKeepsTheHeaders() {
        final TableWidget table = built(List.of("name"), List.of(List.of("alpha")));
        table.addMetadata(List.of(str("hidden")));
        table.clear();
        assertTrue(table.rows().isEmpty(), "rows are gone");
        assertTrue(table.metadata().isEmpty(), "metadata is gone");
        assertEquals(List.of("name"), table.headers(), "the headers stay");
        assertEquals(List.of("name"), rendered(table), "and the header line still draws: " + table.format());
    }

    @Test
    public void testARecWriteIsVisibleToTheNextRead() {
        // the state is the rec, so a table handed a rec sees what that rec says
        final Map<Obj, Obj> jvm = mutableMap(uri(HEADER), lst(str("name")),
                uri(ROW), lst(lst(str("alpha"))));
        final TableWidget table = new TableWidget(jvm, UI_TABLE_TID, null);
        assertEquals(1, table.rows().size());
        table.addRow(List.of("beta"));
        assertEquals(List.of("alpha", "beta"), table.rows().stream().map(r -> r.get(0)).toList(),
                "addRow appends to what the rec already held");
    }

    /* ================================================================
     * Style
     * ================================================================ */

    @Test
    public void testStyleLivesInTheRecAndChangesTheRender() {
        final TableWidget table = built(List.of("name"), List.of(List.of("alpha")));
        assertTrue(Stylable.Style.isStyle(table.at(Stylable.STYLE_KEY)),
                "construction materialises the default style into the rec: " + table);
        assertFalse(table.getStyle().foreground().isEmpty(), "the default foreground is the rec's");
        assertFalse(table.getStyle().background().isEmpty(), "and so is the default background");

        table.style().divider("|").applyStyle();
        assertEquals("|", table.getStyle().divider(), "a style write lands in the rec");
        assertTrue(table.format().contains("|"), "and the render picks it up: " + table.format());
    }

}
