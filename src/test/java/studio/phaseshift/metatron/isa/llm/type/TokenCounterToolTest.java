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

package studio.phaseshift.metatron.isa.llm.type;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.llm.llmInstSet;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.ui.Widget;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TokenCounterToolTest extends AbstractInstSetTest {

    public TokenCounterToolTest() {
        super(llmInstSet::new);
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
        assertTrue(studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty.viewLength(line) <= 47,
                "the whole bar must fit its style box of 47: " + line);
        assertTrue(line.contains("{{[m]}}"), "the sections keep their background codes: " + line);
    }

    private static String Ansi(final String s) {
        return s.replace("\033", "<ESC>");
    }
}
