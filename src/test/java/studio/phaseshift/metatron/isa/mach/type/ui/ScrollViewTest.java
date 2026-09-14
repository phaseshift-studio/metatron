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

package studio.phaseshift.metatron.isa.mach.type.ui;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The scroll model itself: which axes a style declares, and the viewport
 * windowing that makes content off a viewport reachable again.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ScrollViewTest extends AbstractMetatronTest {

    // ── the scroll declaration of a style rec ──────────────────────

    @ParameterizedTest()
    @CsvSource(value = {
            "union(x,y) % xy   % the requested form - both axes",
            "union(y)   % y    % vertical only",
            "union(x)   % x    % horizontal only",
            "y          % y    % bare uri form",
            "x          % x    % bare uri form",
            "xy         % xy   % short form",
            "none       % none % scrolling off",
            "off        % none % scrolling off",
    }, delimiter = '%')
    void testScrollDeclarationOfAStyleRec(final String declaration, final String expectedAxes, final String description) {
        final Stylable.Style<?> style = style("scroll=>" + declaration);
        assertEquals(expectedAxes, Stylable.Style.axesName(style.scrollAxes()),
                "%s: scroll=>%s".formatted(description, declaration));
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "border=>continuous % xy   % an unset scroll key means: scroll whatever overflows",
            "scroll=>union(x,y) % xy   % explicit both",
            "scroll=>union(y)   % y    % explicit vertical",
            "scroll=>none       % none % explicit off",
            "scroll=>false      % none % a bool is an off switch too",
            "scroll=>union(x,y),height=>10 % xy % other style keys do not disturb it",
    }, delimiter = '%')
    void testScrollAxesDefaultAndOverride(final String styleRec, final String expectedAxes, final String description) {
        assertEquals(expectedAxes, Stylable.Style.axesName(style(styleRec).scrollAxes()), description);
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "union(x,y) % xy   % the union arrives unevaluated (quoted by =>)",
            "union(y)   % y    % and is still understood",
            "none       % none % as is the off switch",
            "true       % xy   % a bool enables both",
            "false      % none % a bool disables both",
            "x          % x    % a uri names one axis",
    }, delimiter = '%')
    void testScrollDeclarationForms(final String form, final String expectedAxes, final String description) {
        final Obj declaration = form.startsWith("union") ? str(form) : uri(form);
        assertEquals(expectedAxes, Stylable.Style.axesName(Stylable.Style.parseAxes(declaration)), description);
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "union(x,y) % true  % true  % both axes are scrollable",
            "union(y)   % false % true  % vertical only",
            "union(x)   % true  % false % horizontal only",
            "none       % false % false % neither",
    }, delimiter = '%')
    void testScrollableAxisPredicates(final String declaration, final boolean x, final boolean y, final String description) {
        final Stylable.Style<?> style = style("scroll=>" + declaration);
        assertEquals(x, style.scrollableX(), description);
        assertEquals(y, style.scrollableY(), description);
    }

    // ── vertical windowing ─────────────────────────────────────────

    @ParameterizedTest()
    @CsvSource(value = {
            "abcdef % 0 % 0  % 3 % a b c         % a fresh viewport shows the first rows",
            "abcdef % 0 % 3  % 3 % d e f         % scrolled to the end shows the last rows",
            "abcdef % 0 % 9  % 3 % d e f         % an offset past the end clamps to the last window",
            "abcdef % 0 % -2 % 3 % a b c         % a negative offset clamps to the first window",
            "abcdef % 0 % 0  % 9 % a b c d e f   % a viewport bigger than the content shows it all",
            "abcdef % 0 % 0  % 0 % a b c d e f   % no viewport means no windowing",
            "abcdef % 1 % 0  % 4 % a b c d       % chrome is pinned, the body starts under it",
            "abcdef % 1 % 2  % 4 % a d e f       % chrome stays while the body scrolls under it",
            "abcdef % 1 % 9  % 4 % a d e f       % chrome stays at the end of the body too",
            "abcdef % 1 % 0  % 9 % a b c d e f   % chrome does not eat a viewport that fits everything",
    }, delimiter = '%')
    void testVerticalWindow(final String content, final int chrome, final int offset,
                            final int viewport, final String expected) {
        final List<String> lines = Arrays.asList(content.split(""));
        assertEquals(Arrays.asList(expected.split(" ")), ScrollView.windowVertically(lines, chrome, offset, viewport),
                "content=%s chrome=%d offset=%d viewport=%d".formatted(content, chrome, offset, viewport));
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "10 % 0 % 4  % 6 % a ten-row body in a four-row viewport has six rows of travel",
            "3  % 0 % 4  % 0 % content that fits has nothing to scroll",
            "10 % 1 % 4  % 6 % pinned chrome costs the body a row of travel (4 rows show 3 body rows)",
            "10 % 1 % 12 % 0 % a viewport at least as big as the content never scrolls",
            "0  % 0 % 4  % 0 % empty content never scrolls",
    }, delimiter = '%')
    void testMaxY(final int content, final int chrome, final int viewport, final int expected, final String description) {
        assertEquals(expected, ScrollView.maxY(content, chrome, viewport), description);
    }

    // ── horizontal windowing ───────────────────────────────────────

    @ParameterizedTest()
    @CsvSource(value = {
            "abcdef % 0 % 6 % abcdef     % a line that fits is untouched",
            "abcdef % 2 % 3 % cde        % a shifted window shows the later columns",
            "abcdef % 9 % 3 % ''         % a window past the end is empty",
            "abcdef % 0 % 0 % ''         % a zero-width viewport shows nothing",
            "{{y}}abc % 0 % 6 % {{y}}abc % an unshifted window keeps the line verbatim (colors included)",
    }, delimiter = '%')
    void testHorizontalWindow(final String line, final int offset, final int width,
                              final String expected, final String description) {
        assertEquals(expected, ScrollView.windowHorizontally(line, offset, width), description);
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "10 % 6 % 4 % a wide body has four columns of travel",
            "6  % 6 % 0 % content that fits has nothing to scroll",
            "0  % 6 % 0 % empty content never scrolls",
    }, delimiter = '%')
    void testMaxX(final int contentWidth, final int viewportWidth, final int expected, final String description) {
        assertEquals(expected, ScrollView.maxX(contentWidth, viewportWidth), description);
    }

    @ParameterizedTest()
    @CsvSource(value = {
            "{{y}}abc{{X}}|de % 3 % a two-line body is as wide as its widest line",
            "{{y}}abcdef{{X}} % 6 % color codes are not columns",
    }, delimiter = '%')
    void testContentWidth(final String body, final int expectedWidth, final String description) {
        assertEquals(expectedWidth, ScrollView.contentWidth(Arrays.asList(body.split("\\|"))), description);
    }

    /** Parse a style rec body (no enclosing brackets) as the {@link Stylable.Style} widgets use. */
    private static Stylable.Style<?> style(final String styleRec) {
        return Stylable.Style.from(ObjmtronSerializer.parse("[" + styleRec + "]").apply(noobj()).asRec());
    }
}
