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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.tool.ModalTool;
import studio.phaseshift.metatron.isa.mach.type.ui.tool.SwipePanelWidgetTool;
import studio.phaseshift.metatron.isa.mach.type.ui.tool.TreeSelectTool;
import studio.phaseshift.metatron.isa.mach.ui.uiInstSet;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_WIDGET_TID;

/**
 * The field rule, enforced instead of agreed: <b>a widget's state lives in its rec</b>,
 * so a Java field whose name is a key the widget's {@code Type} declares is a bug — two
 * homes for one value, and the rec is the one that survives the re-hydration every
 * {@code .display()} performs.
 *
 * <p>Both sides are read reflectively: the class's instance fields (inherited ones
 * included, so a field added to a base class is caught for every widget that has it) and
 * the key set of the type's {@code isaPredicate} — the rec of {@code key => value-type}
 * pairs a widget {@code Type} is built from in {@code uiInstSet}.  Static fields are
 * skipped: they are constants and lookups, not state on an object.
 *
 * <p>What this does <em>not</em> catch, and what the other two layers cover: a field that
 * holds rec data under a <em>different</em> name ({@code javaPopulated},
 * {@code TableWidget.headers} against the {@code header} key) is a name test's blind spot,
 * and a per-render cache is invisible to it entirely.  The behavioral guard for those is
 * the rec-parity cases in {@code TreeWidgetTest}/{@code TableWidgetTest} (two instances
 * over one rec render identically, and a write to the rec shows up in the next render),
 * and the declared-key contract is {@code WidgetTypeContractTest} (every key a widget
 * reads must be declared, under live type predicates).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class WidgetStateDisciplineTest extends AbstractInstSetTest {

    public WidgetStateDisciplineTest() {
        super(uiInstSet::new);
    }

    /**
     * Every registered widget type and the Java class that renders it.  The coverage test
     * below fails when a widget type is registered without a row here, so a new widget
     * cannot quietly go unchecked.
     */
    private static Stream<Arguments> widgetClasses() {
        return Stream.of(
                Arguments.of("accordion_widget", AccordionWidget.class),
                Arguments.of("panel_widget", PanelWidget.class),
                Arguments.of("progress_table_widget", ProgressTableWidget.class),
                Arguments.of("table_widget", TableWidget.class),
                Arguments.of("tree_widget", TreeWidget.class),
                Arguments.of("selector_widget", Selector.class),
                Arguments.of("label_line_widget", LabelLineWidget.class),
                Arguments.of("menu_bar_widget", MenuBarWidget.class),
                Arguments.of("tree_select_widget", TreeSelectTool.class),
                Arguments.of("swipe_panel_widget", SwipePanelWidgetTool.class),
                Arguments.of("modal_widget", ModalTool.class));
    }

    @ParameterizedTest
    @MethodSource("widgetClasses")
    void testNoFieldShadowsAKeyTheWidgetTypeDeclares(final String widget,
                                                     final Class<?> widgetClass) {
        final Set<String> declared = declaredKeys("/m/mach/ui/widget/" + widget);
        final List<String> shadowing = new ArrayList<>();
        for (final Field field : instanceFields(widgetClass))
            if (declared.contains(field.getName())) shadowing.add(field.getName());

        assertTrue(shadowing.isEmpty(),
                "%s declares %s, which the %s type declares as a rec key (%s).  A key that "
                        .formatted(widgetClass.getSimpleName(), shadowing, widget, declared)
                        + "has a rec key belongs in the rec: read it with read()/get(fields, key) "
                        + "and write it with put(key, value), and delete the field.  If the field "
                        + "genuinely is not rec data (a collaborator, per-render scratch, or the "
                        + "rec's own identity), rename it so it cannot be read as state.");
    }

    /**
     * The reflection above is only worth anything if the key extraction actually returns
     * keys: a silent empty set would make every case above pass vacuously.
     */
    @Test
    void testTheTypeKeyExtractionSeesTheDeclaredKeys() {
        final Set<String> all = new LinkedHashSet<>();
        widgetClasses().forEach(args -> all.addAll(declaredKeys("/m/mach/ui/widget/" + args.get()[0])));
        assertTrue(all.containsAll(List.of("title", "body", "root", "max", "row", "header", "height", "lines")),
                "the declared-key extraction must find the keys the widget types are built from: " + all);
    }

    /**
     * A registered widget type with no row in {@link #widgetClasses()} would never be
     * checked — which is how the library got here in the first place.
     */
    @Test
    void testEveryRegisteredWidgetTypeIsCovered() {
        final Set<String> covered = new LinkedHashSet<>();
        widgetClasses().forEach(args -> covered.add((String) args.get()[0]));
        final Set<String> registered = new LinkedHashSet<>();
        for (final Field field : uiInstSet.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !Type.class.isAssignableFrom(field.getType())) continue;
            try {
                field.setAccessible(true);
                final Object value = field.get(null);
                if (!(value instanceof Type type) || null == type.vid()) continue;
                // the widget types are the registered types under /m/mach/ui/widget/ (the
                // base itself is not one — nothing renders /m/mach/ui/widget)
                final String tid = type.vid().toString();
                if (tid.startsWith(UI_WIDGET_TID.toString() + "/"))
                    registered.add(tid.substring(tid.lastIndexOf('/') + 1));
            } catch (final Exception ignored) {
                // a static that will not read is not a widget type we can check
            }
        }
        assertFalse(registered.isEmpty(), "no widget types were discovered — the discovery itself is broken");
        final Set<String> uncovered = new LinkedHashSet<>(registered);
        uncovered.removeAll(covered);
        assertTrue(uncovered.isEmpty(),
                "these registered widget types have no row in widgetClasses(), so nothing checks them: " + uncovered);
    }

    // ── reflection helpers ─────────────────────────────────────────

    /** A type's declared keys: the key side of its {@code isaPredicate} rec of key => value-type. */
    private static Set<String> declaredKeys(final String tid) {
        final Set<String> keys = new LinkedHashSet<>();
        final Obj registered = Router.global().read(f(tid));
        if (!registered.isType()) return keys;
        final Obj predicate = registered.asType().isPredicateObj();
        if (null == predicate || !predicate.isRec()) return keys;
        predicate.asRec().elements().forEach(rel -> {
            final Obj key = rel.first();
            if (key.isUri()) keys.add(key.uriValue().toString());
        });
        return keys;
    }

    /** A class's instance fields, inherited ones included — a base-class field counts too. */
    private static List<Field> instanceFields(final Class<?> type) {
        final List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; null != c && !Object.class.equals(c); c = c.getSuperclass())
            for (final Field field : c.getDeclaredFields())
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic())
                    fields.add(field);
        return fields;
    }
}
