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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.ui.uiInstSet.UI_PANEL_TID;

/**
 * PanelWidget renders from its rec (it extends {@code SpaceRec}).  A body arrives as
 * lines in one of three types: a {@code str::T} — one value — split on newlines,
 * which is what an mtron writer produces; a multiplicity of strs {@code str{*}::T} —
 * many values, one line each; and a {@code lst[str]::T} — one value that is a list —
 * which is the shape Java-side construction produces.
 *
 * <p>The middle and last are not spellings of each other ({@code str{2}::T} is two
 * strs, {@code lst[str]::T} is one list).  The declared body type is {@code str{*}},
 * so a {@code lst} is rejected on write even though the renderer reads it.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class PanelWidgetTest extends AbstractMetatronTest {

    private static PanelWidget panel(final Obj body) {
        final Map<Obj, Obj> jvm = new LinkedHashMap<>();
        jvm.put(uri("title"), str("note"));
        jvm.put(uri("body"), body);
        return new PanelWidget(jvm, UI_PANEL_TID, null);
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "str     % a, b % str::T - one value, newlines break lines",
            "str{*}  % a, b % str{*}::T - a multiplicity of values, one line each",
            "lst[str] % a, b % lst[str]::T - one value that is a list, renderer tolerant",
    }, delimiter = '%')
    void testBodyShapesAllRender(final String shape, final String lines, final String description) {
        final String first = lines.split(",")[0].trim();
        final String second = lines.split(",")[1].trim();
        final Obj body = "str{*}".equals(shape.trim())
                ? objs(str(first), str(second))
                : "lst[str]".equals(shape.trim())
                ? lst(str(first), str(second))
                : str(first + "\n" + second);
        final String rendered = panel(body).format();
        assertTrue(rendered.contains("a"), "%s: should render line 'a': %s".formatted(description, rendered));
        assertTrue(rendered.contains("b"), "%s: should render line 'b': %s".formatted(description, rendered));
        assertEquals(2, rendered.lines().filter(l -> l.startsWith("│")).count(),
                "%s: exactly one bordered row per line: %s".formatted(description, rendered));
    }

    @Test
    public void shouldAdoptAndKeepStyleInTheRec() {
        final PanelWidget p = panel(str("body"));
        // construction materialised the default style into the rec (space rule: the
        // rec is the only home of state, so a re-hydration cannot lose it)
        assertTrue(studio.phaseshift.metatron.isa.mach.type.ui.Stylable.Style.isStyle(p.at(uri("style"))),
                "the rec carries the style: " + p);
        p.style().foreground("{{r}}").width(24).applyStyle();
        assertEquals("{{r}}", p.getStyle().foreground(), "a style write lands in the rec");
        assertEquals(24, p.getStyle().width());
    }

    @Test
    public void shouldWrapTheBodyToTheStyleWidth() {
        final PanelWidget p = panel(str("alpha beta gamma delta epsilon"));
        assertTrue(p.format().contains("alpha beta gamma delta epsilon"),
                "an unwrapped panel shows the line whole: " + p.format());
        p.maxWidth(12);
        final String wrapped = p.format();
        assertEquals(12, p.maxWidth(), "maxWidth is the style's width (a declared key), not a field");
        assertFalse(wrapped.contains("alpha beta gamma delta epsilon"),
                "a narrow panel breaks the line: " + wrapped);
        assertTrue(wrapped.lines().filter(l -> l.startsWith("│")).count() > 1,
                "a narrow panel wraps the body over several rows: " + wrapped);
    }

    // ── the declared width is the panel's width, title included ────

    /**
     * The body gives way to {@code maxWidth} by wrapping; the title gives way to it by
     * being clipped.  A panel whose title is the longest thing in it — a URI, say — is
     * exactly the case that used to draw wider than the width it was handed.
     *
     * <p>A panel sizes to its content, so a declared width is a limit and not a fill:
     * the box is as wide as what it holds, and never wider than the width it was given.
     */
    @ParameterizedTest()
    @CsvSource(value = {
            "36 % 30 % clipped % a title wider than the panel is clipped to it",
            "28 % 30 % intact  % a title the panel's width has room for is drawn whole",
            "4  % 30 % intact  % a short title is untouched",
    }, delimiter = '%')
    void testTitleIsClippedToTheDeclaredWidth(final int titleLength, final int width,
                                              final String expectation, final String description) {
        final String title = "t".repeat(titleLength);
        final PanelWidget p = panel(str("body"));
        p.setTitle(title);
        p.maxWidth(width);
        final List<String> lines = p.format().lines().toList();
        for (final String line : lines)
            assertTrue(Highlighter.visualLength(line) <= width + 2,
                    "no row is wider than the declared width plus the two border sides: %s "
                            .formatted(line) + "(width %d, title %d — %s)".formatted(width, titleLength, description));
        if ("clipped".equals(expectation.trim())) {
            assertTrue(lines.get(0).contains("…"), "the clipped title is marked as clipped: " + lines.get(0));
            assertEquals(width + 2, Highlighter.visualLength(lines.get(0)),
                    "and the clip fills the room it was given exactly: " + lines.get(0));
        } else
            assertTrue(lines.get(0).contains(title), "a title that fits is drawn whole: " + lines.get(0));
    }

    /**
     * The modal renders through a panel, so it inherits the clip — it constrains the
     * panel to the terminal it is running in, and a modal title must not push the box
     * past that.
     */
    @Test
    public void shouldClipAModalTitleToThePanelItRendersThrough() {
        final PanelWidget p = new PanelWidget("a confirmation title nobody would fit in 24 columns",
                "Do you want to proceed?");
        p.maxWidth(24);
        final List<String> lines = p.format().lines().toList();
        for (final String line : lines)
            assertTrue(Highlighter.visualLength(line) <= 26,
                    "the modal's box stays within the width its panel was given: " + line);
        assertTrue(lines.get(0).contains("…"), "and its title says so: " + lines.get(0));
    }
}
