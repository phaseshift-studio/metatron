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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.TypeMismatchException;
import studio.phaseshift.metatron.isa.mach.ui.uiInstSet;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_GRID_TID;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_LABEL_LINE_TID;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_MENU_BAR_TID;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_PANEL_TID;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_TABLE_TID;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_TREE_SELECT_TOOL_TYPE;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_TREE_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The widget {@code Type}s are the contract every writer — mtron, a tool, a test —
 * is held to, and the check only runs when type predicates are enabled (tests boot
 * with them off).  So these cases flip them on and assert both directions: every key
 * and value shape the widgets and tools actually write is <em>accepted</em>, and a
 * value of the wrong type is <em>rejected</em>.
 *
 * <p>The rejections are not pedantry.  A body is one or many strs ({@code str{*}}, a
 * coefficient: {@code str{2}::T} is two strs), while {@code ['one','two']} is one
 * value of type {@code lst[str]::T} — a different type, correctly refused.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class WidgetTypeContractTest extends AbstractInstSetTest {

    public WidgetTypeContractTest() {
        super(uiInstSet::new);
    }

    @BeforeEach
    void typeChecksOn() {
        TypeCheck.enable(TypeCheck.type_pred);
    }

    @AfterEach
    void typeChecksOff() {
        TypeCheck.disable(TypeCheck.type_pred);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "minimal     % root and max, the two required keys",
            "code        % code, an optional key",
            "flatten     % flatten, read by the row builder",
            "xref        % xref, read by the cross-reference pass",
            "expand-one  % expand as one uri",
            "expand-many % expand as a multiplicity of uris (uri{*})",
    }, delimiter = '%')
    void testTreeTypeAcceptsEveryShapeTheTreeIsWritten(final String shape, final String description) {
        final Map<Obj, Obj> jvm = mutableMap(uri(ROOT), uri("/sys"), uri(MAX), jnt(2));
        switch (shape.trim()) {
            case "code" -> jvm.put(uri(CODE), instLambda((lhs, inst) -> lhs).tryToInst());
            case "flatten" -> jvm.put(uri(FLATTEN), bool(true));
            case "xref" -> jvm.put(uri(XREF), rec(uri(MAX), jnt(2)));
            // the tree_select tool writes expand exactly like this: objs() collapses a
            // single uri to that uri, so one and many are both legal rec values
            case "expand-one" -> jvm.put(uri(EXPAND), objs(uri("/sys/space")));
            case "expand-many" -> jvm.put(uri(EXPAND), objs(uri("/sys/space"), uri("/m")));
            default -> { /* root and max only */ }
        }
        assertDoesNotThrow(() -> new TreeWidget(jvm, UI_TREE_TID, null).format(),
                description + ": a tree written this way must satisfy /m/mach/ui/widget/tree_widget");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "str  % one value, split on newlines",
            "strs % a multiplicity of str values (the declared str{*})",
    }, delimiter = '%')
    void testPanelTypeAcceptsBodyShapes(final String shape, final String description) {
        final Map<Obj, Obj> jvm = "strs".equals(shape.trim())
                ? mutableMap(uri(BODY), (Obj) objs(str("alpha"), str("beta")))
                : mutableMap(uri(BODY), (Obj) str("alpha\nbeta"));
        assertDoesNotThrow(() -> new PanelWidget(jvm, UI_PANEL_TID, null).format(),
                description + ": a panel body is written as one or many strs");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "headers        % header as one lst of names",
            "rows           % row as a lst of rows (what addRow writes)",
            "metadata       % metadata, the data rows behind the display",
            "headers+rows   % both, the shape every tool builds",
    }, delimiter = '%')
    void testTableTypeAcceptsEveryShapeTheTableIsWritten(final String shape, final String description) {
        final Map<Obj, Obj> jvm = mutableMap();
        if (shape.contains("headers"))
            jvm.put(uri(HEADER), lst(str("path"), str("actual")));
        if (shape.contains("rows"))
            jvm.put(uri(ROW), lst(lst(str("a"), str("1")), lst(str("b"), str("2"))));
        if ("metadata".equals(shape.trim()))
            jvm.put(uri(METADATA), lst(lst(jnt(1), str("behind"))));
        assertDoesNotThrow(() -> new TableWidget(jvm, UI_TABLE_TID, null).format(),
                description + ": a table written this way must satisfy /m/mach/ui/widget/table_widget");
    }

    /**
     * A grid's {@code grid} key is declared a {@code lst} — a lst of rows, each a lst of cell
     * widgets.  The shape every writer (mtron, a tool, a test) actually writes must be accepted,
     * and a non-list grid refused.
     */
    @Test
    void testGridTypeAcceptsRowsOfCellWidgets() {
        assertDoesNotThrow(() -> {
            final GridWidget grid = new GridWidget(2, 2);
            grid.cell(0, 0, new AccordionWidget("alpha")).cell(0, 1, new AccordionWidget("beta"));
            grid.cell(1, 0, new AccordionWidget("gamma")).cell(1, 1, new AccordionWidget("delta"));
            grid.format();
        }, "a grid written as rows of cell widgets must satisfy /m/mach/ui/widget/grid_widget");
    }

    @Test
    void testGridTypeRejectsANonLstGrid() {
        final Map<Obj, Obj> jvm = mutableMap(uri("grid"), (Obj) str("not a lst of rows"));
        assertThrows(TypeMismatchException.class, () -> new GridWidget(jvm, UI_GRID_TID, null),
                "the grid key is declared a lst; a bare str is not one");
    }

    /**
     * The write path these classes used — {@code jvmWrite(key, value)} — went straight
     * into the map, bypassing the type check (and, on a vid-less widget, the space as
     * well).  They now go through {@code put(...)}, which is checked, so what they write
     * has to be legal for the type they declare.
     */
    @Test
    void testWidgetTypesAcceptWhatThePortedWritesProduce() {
        assertDoesNotThrow(() -> new LabelLineWidget(mutableMap(), UI_LABEL_LINE_TID, null)
                        .body("a one-line label"),
                "label_line.body writes str(BODY)");
        assertDoesNotThrow(() -> new MenuBarWidget(mutableMap(), UI_MENU_BAR_TID, null)
                        .lines(new LabelLineWidget("one"), new LabelLineWidget("two"))
                        .height(1),
                "menu_bar writes a lst(LINES) of line widgets and an int(HEIGHT)");
        assertTrue(rec(uri(ROOT), uri("/sys"), uri(MAX), jnt(3)).test(UI_TREE_SELECT_TOOL_TYPE),
                "tree_select.max is an int under a declared key (TreeSelectTool.maxDepth writes it)");
    }

    @Test
    void testPanelTypeRejectsAListBody() {
        final Map<Obj, Obj> jvm = mutableMap(uri(BODY), (Obj) lst(str("alpha"), str("beta")));
        assertThrows(TypeMismatchException.class, () -> new PanelWidget(jvm, UI_PANEL_TID, null),
                "a lst[str] is ONE value; the declared body is str{*} (one or many strs)");
    }
}
