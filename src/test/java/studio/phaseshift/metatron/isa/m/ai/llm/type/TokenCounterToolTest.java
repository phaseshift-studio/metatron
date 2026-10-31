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

package studio.phaseshift.metatron.isa.m.ai.llm.type;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.AbstractWidgetTest;
import studio.phaseshift.metatron.isa.m.ai.llm.llmInstSet;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.m.sys.ui.Widget;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TokenCounterToolTest extends AbstractWidgetTest {

    public TokenCounterToolTest() {
        super(llmInstSet::new);
    }

    @Override
    protected Object sampleWidget() {
        return new TokenCounterTool(mutableMap(
                uri("in"), jnt(12606),
                uri("max"), jnt(100000),
                uri("est"), rec(mutableMap(uri("system"), jnt(1414), uri("ai"), jnt(4153),
                        uri("tool"), jnt(1786), uri("user"), jnt(311)))));
    }

    private static TokenCounterTool parse(final String code) {
        final Object obj = ObjmtronSerializer.parse(code);
        assertInstanceOf(TokenCounterTool.class, obj, code + " should construct a token counter: " + obj);
        return (TokenCounterTool) obj;
    }

    // ── the percent: in over max ──────────────────────────────────────
    @ParameterizedTest
    @CsvSource(value = {
            // the chat the data comes from
            "token_counter_tool::[in=>12606,out=>330,max=>100000,est=>[system=>1414,ai=>4153,tool=>1786,user=>311]] % 13",
            // no est at all: the percent stands alone over the window
            "token_counter_tool::[in=>262144,max=>262144] % 100",
            "token_counter_tool::[in=>999999,max=>100000] % 100",   // clamped
            "token_counter_tool::[in=>2048,max=>100000,est=>[ai=>100]] % 2",
    }, delimiter = '%')
    void shouldReportTotalAgainstWindow(final String code, final String percent) {
        assertTrue(parse(code).format().contains(percent + "%"), code + " should report " + percent + "%");
    }

    // ── the size label: the window itself ─────────────────────────────

    @ParameterizedTest
    @CsvSource(value = {
            "token_counter_tool::[in=>10,max=>262144] % 256k",
            "token_counter_tool::[in=>10,max=>65536] % 64k",
            "token_counter_tool::[in=>10,max=>8192] % 8k",
            "token_counter_tool::[in=>10,max=>8388608] % 8m",
            "token_counter_tool::[in=>10,max=>12582912] % 12m",
            "token_counter_tool::[in=>10,max=>999] % 999",        // no clean power: its own number
    }, delimiter = '%')
    void shouldLabelWindowSize(final String code, final String size) {
        assertTrue(parse(code).format().endsWith("│ " + size), code + " should end with its window size: " + parse(code).format());
    }

    // ── the bar: sections, order, colors, one line ────────────────────

    @Test
    void shouldConstructHeadlessAndRender() {
        final TokenCounterTool tool = parse(
                "token_counter_tool::[in=>12606,out=>330,max=>100000,est=>[system=>1414,ai=>4153,tool=>1786,user=>311]]");
        assertInstanceOf(Widget.class, tool, "a token counter is a widget");
        final String formatted = tool.format();
        assertTrue(formatted.startsWith("│"), "the bar leads with its flat line: " + formatted);
        assertTrue(formatted.contains("│ "), "the bar is closed before the size: " + formatted);
    }

    @Test
    void shouldDrawSmallestFirstLargestLast() {
        // shares wide enough that no label clips — the order assertion below
        // reads the labels whole.  Probes anchor at the color tag's close, so
        // padding after the label cannot move them
        final String formatted = parse(
                "token_counter_tool::[in=>840,max=>900,est=>[ai=>420,system=>140,tool=>180,user=>100]]").format();
        final int usr = formatted.indexOf("}}usr");
        final int sys = formatted.indexOf("}}sys");
        final int tool = formatted.indexOf("}}tool");
        final int ai = formatted.indexOf("}}ai");
        assertTrue(usr < sys, "usr is less than sys (100 < 140): " + formatted);
        assertTrue(sys < tool, "sys is less than tool (140 < 180): " + formatted);
        assertTrue(tool < ai, "tool is less than ai (180 < 420): " + formatted);
    }

    @Test
    void shouldClipSectionsToTheirWidthsWhenTheSharesAreTiny() {
        // the bar is the truth: each of these shares is below a column, so each is
        // one column wide and shows its first letter — never the label it measures
        final String formatted = parse(
                "token_counter_tool::[in=>980,max=>100000,est=>[system=>622,ai=>170,user=>409,tool=>334]]").format();
        assertTrue(formatted.contains("{{[g]}}a{{X}}{{[y]}}t{{X}}{{[m]}}u{{X}}{{[c]}}s{{X}}"),
                "a share below a column is one column wide and clipped to its first letter: " + formatted);
        assertTrue(formatted.contains("{{[k]}}"), "the unused tail keeps its background: " + formatted);
        assertTrue(formatted.endsWith("{{X}}│ 100k"), "the bar must close with its window size: " + formatted);
    }

    @Test
    void shouldSizeSectionsProportionallyWhenThereIsRoom() {
        // widths 4, 6, 8, 19: each section its true share, labels unclipped and
        // padded to fit — and monotonic, a wider bar is always the wider share
        final String formatted = parse(
                "token_counter_tool::[in=>840,max=>900,est=>[system=>140,ai=>420,tool=>180,user=>100]]").format();
        assertTrue(formatted.contains("{{[m]}}usr {{X}}{{[c]}}sys   {{X}}{{[y]}}tool    {{X}}{{[g]}}ai"
                        + " ".repeat(17) + "{{X}}{{W}}93%"),
                "each section is its share of the whole, the label only as wide as the share buys: " + formatted);
    }

    @Test
    void shouldFillTheBarProportionallyWhenThereIsNoWindow() {
        // no max: the bar IS the composition — the shares sum to the whole canvas,
        // full labels where a share buys one, and nothing beyond the closing rule
        final String formatted = parse(
                "token_counter_tool::[in=>980,est=>[system=>622,ai=>170,user=>409,tool=>334]]").format();
        assertTrue(formatted.contains("{{[g]}}ai  {{X}}{{[y]}}tool     {{X}}{{[m]}}usr        {{X}}{{[c]}}sys             {{X}}│"),
                "the composition fills the bar: sections proportional, the bar closed at its full width: " + formatted);
        assertFalse(formatted.contains("%"), "no window means no percent: " + formatted);
    }

    @Test
    void shouldGiveEverySectionAndTheUnusedTailABackground() {
        final String formatted = parse(
                "token_counter_tool::[in=>12606,max=>100000,est=>[system=>1414,ai=>4153,tool=>1786,user=>311]]").format();
        for (final String background : new String[]{"{{[c]}}", "{{[g]}}", "{{[m]}}", "{{[y]}}", "{{[k]}}"}) {
            assertTrue(formatted.contains(background), background + " should be drawn: " + formatted);
        }
        final String ansi = Graphitty.string(formatted);
        assertTrue(ansi.contains("[4"), "backgrounds should render as ANSI: " + Ansi(ansi));
        assertTrue(formatted.endsWith("{{X}}│ 100k"), "the bar must reset after its tail: " + formatted);
    }

    @Test
    void shouldRenderExactlyOneLine() {
        final Widget tool = parse(
                "token_counter_tool::[in=>12606,max=>100000,est=>[system=>1414,ai=>4153,tool=>1786,user=>311]]");
        assertEquals(1, tool.height(), "a token counter is a single line");
        assertFalse(tool.format().contains("\n"), "the bar never breaks its line: " + tool.format());
    }

    @Test
    void shouldKeepPercentVisibleWhenTheWindowIsNearlyFull() {
        // 980 of 1000: the sections want more columns than the bar has — the pct must survive
        final String formatted = parse(
                "token_counter_tool::[in=>980,max=>1000,est=>[system=>300,ai=>300,tool=>190,user=>190]]").format();
        assertTrue(formatted.contains("98%"), "a near-full window must still show its percent: " + formatted);
    }


    @Test
    void shouldFitItsWholeLineInsideTheStyleBox() {
        // the float surface strips the color of a line that overflows its box
        // (style.width) — so the whole bar, brackets and size label included,
        // must stay inside the box: that is what keeps its color on re-render.
        final String code = "token_counter_tool::[style=>[anchor=>bottom_right,width=>47],in=>13154,out=>480,max=>262144"
                + ",est=>[system=>1464,user=>263,ai=>671,tool=>3329]]";
        final String line = parse(code).format();
        assertTrue(Graphitty.viewLength(line) <= 47,
                "the whole bar must fit its style box of 47: " + line);
        assertTrue(line.contains("{{[m]}}"), "the sections keep their background codes: " + line);
    }

    @Test
    void shouldPaintTheFullSpanWhenTheWindowIsFull() {
        // the invariant: a bar that says 100% fills its span, whatever the est
        // categories say.  The percent is in over max and the sections are a
        // chars/4 estimate that deliberately does NOT sum to in — sizing the
        // fill on the estimate alone leaves a full window reading 100% over a
        // bar that stops half way, which is the reported symptom.  Here the
        // estimate is 300 of the window's 600, so an est-sized fill is exactly
        // the half-fill that was wrong.
        final String full = parse(
                "token_counter_tool::[style=>[width=>67],in=>600,max=>600,est=>[system=>100,user=>80,ai=>70,tool=>50]]").format();
        assertTrue(full.contains("100%"), "a full window reads 100%: " + full);
        assertEquals(CANVAS - "100%".length(), colorColumns(full),
                "100% must paint every column the percent leaves: " + full);
        assertEquals(CANVAS, contentColumns(full), "the bar always spans its canvas: " + full);
    }

    @Test
    void shouldPaintHalfTheSpanAtFiftyPercent() {
        final String half = parse(
                "token_counter_tool::[style=>[width=>67],in=>300,max=>600,est=>[system=>100,user=>80,ai=>70,tool=>50]]").format();
        assertTrue(half.contains("50%"), "half a window reads 50%: " + half);
        assertEquals(30, colorColumns(half), "50% must paint half the canvas: " + half);
        assertEquals(CANVAS, contentColumns(half), "the bar always spans its canvas: " + half);
    }

    @Test
    void shouldFillByTheEstimateWhenTheProviderReportsNothing() {
        // the live widget carries est and no in at all (the provider reported
        // no usage): the estimate is then the only fill there is, so the bar
        // paints it and the percent describes it — never a bare 0% over a bar
        // that is visibly carrying content
        final String bar = parse(
                "token_counter_tool::[style=>[width=>67],max=>600,est=>[system=>100,user=>80,ai=>70,tool=>50]]").format();
        assertTrue(bar.contains("50%"), "the estimate's share of the window is the percent: " + bar);
        assertEquals(30, colorColumns(bar), "the estimate paints its own share of the window: " + bar);
    }

    @Test
    void shouldPaintTheLiveWindowWithoutAProviderCount() {
        // the widget as the harness actually holds it: a 262144 window and the
        // four est categories, no in at all (the provider reported none).  The
        // estimate is 49487 of 262144 — about 19% of the canvas, which is what
        // the bar must paint and what the percent must say.
        final String bar = parse(
                "token_counter_tool::[style=>[width=>67],max=>262144,"
                        + "est=>[system=>1437,user=>1374,ai=>19587,tool=>27089]]").format();
        assertTrue(bar.contains("19%"), "the estimate's share of the window is the percent: " + bar);
        assertNotEquals(0, colorColumns(bar), "the estimate must paint a fill, not a bare percent: " + bar);
        assertTrue(colorColumns(bar) <= 11, "the fill stays inside the estimate's share: " + bar);
    }

    /**
     * The canvas: the style box, less the two corner rules and the trailing
     * {@code " size"} label.
     */
    private static final int CANVAS = 67 - 2 - " 600".length();

    /**
     * The width the colored sections paint — the composition's share of the
     * canvas, which is the fill the percent describes.
     */
    private static int colorColumns(final String bar) {
        int cells = 0;
        for (final String cell : bar.split("\\{\\{X\\}\\}")) {
            final String stripped = Graphitty.strip(cell);
            // the unused tail, the percent, and the closing rule with its
            // size label are not the fill
            if (stripped.isBlank() || stripped.endsWith("%") || stripped.contains("\u2502"))
                continue;
            cells += stripped.length();
        }
        return cells;
    }

    /**
     * The width of everything between the rules — sections, percent and any
     * tail — measured as the terminal shows it.
     */
    private static int contentColumns(final String bar) {
        final int open = bar.indexOf('\u2502');
        final int close = bar.lastIndexOf('\u2502');
        assertTrue(open >= 0 && close > open, "the bar is closed by two rules: " + bar);
        return Graphitty.viewLength(bar.substring(open + 1, close));
    }

    private static String Ansi(final String s) {
        return s.replace("\033", "<ESC>");
    }
}
