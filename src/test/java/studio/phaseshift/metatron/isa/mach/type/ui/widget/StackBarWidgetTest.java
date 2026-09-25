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
import studio.phaseshift.metatron.isa.AbstractWidgetTest;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.ui.uiInstSet;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * The general stacked bar: the data contract (widths, percent, framing) in
 * tabular rows, the per-section style entries in dedicated tests — the rows
 * stay quote-free on purpose, since a CSV field keeps its quotes verbatim.
 */
public class StackBarWidgetTest extends AbstractWidgetTest {

    public StackBarWidgetTest() {
        super(uiInstSet::new);
    }

    @Override
    protected Object sampleWidget() {
        return new StackBarWidget(mutableMap(
                uri("data"), rec(mutableMap(uri("ai"), jnt(345), uri("user"), jnt(3234), uri("sys"), jnt(2434))),
                uri("context"), jnt(262144),
                uri("total"), jnt(12606)));
    }

    private static StackBarWidget parse(final String code) {
        final Object obj = ObjmtronSerializer.parse(code);
        assertTrue(obj instanceof StackBarWidget, code + " should construct a stack bar: " + obj);
        return (StackBarWidget) obj;
    }

    /** The bar is its width in the style rec (a floor), plus the brackets — and the frames when there are any. */
    @ParameterizedTest
    @CsvSource(value = {
            "stack_bar_widget::[data=>[ai=>100,sys=>600,user=>300]] % 42",
            "stack_bar_widget::[data=>[a=>1,b=>2],style=>[width=>60]] % 62",
            "stack_bar_widget::[data=>[a=>1,b=>2],context=>100,total=>95] % 42",
            "stack_bar_widget::[data=>[ai=>345,user=>3234,sys=>2434],context=>262144,total=>12606] % 42",
    }, delimiter = '%')
    void shouldSpanItsStyledWidth(final String code, final String width) {
        final String f = parse(code).format();
        assertEquals(Integer.parseInt(width), Graphitty.viewLength(Graphitty.string(f)), f);
    }

    /** With a context the bar is the window, and total over the context is the percent. */
    @ParameterizedTest
    @CsvSource(value = {
            "stack_bar_widget::[data=>[a=>1],context=>100,total=>95] % 95",
            "stack_bar_widget::[data=>[a=>1],context=>100,total=>999] % 100",
            "stack_bar_widget::[data=>[a=>100,b=>20],context=>80] % 100",
            "stack_bar_widget::[data=>[a=>1],context=>200,total=>5] % 3",
            "stack_bar_widget::[data=>[system=>1414,ai=>4153,tool_result=>1786,user=>311],context=>262144,total=>12606] % 5",
    }, delimiter = '%')
    void shouldPaintTotalOverContext(final String code, final String percent) {
        final String f = parse(code).format();
        assertTrue(f.contains(percent + "%"), code + " should paint " + percent + "% (" + f + ")");
    }

    /** Without a context the data IS the bar: a composition with no percent at all. */
    @ParameterizedTest
    @CsvSource(value = {
            "stack_bar_widget::[data=>[ai=>345,user=>3234,sys=>2434]]",
            "stack_bar_widget::[data=>[a=>1,b=>2],style=>[width=>60]]",
    }, delimiter = '%')
    void shouldComposeWithoutAContext(final String code) {
        final String f = parse(code).format();
        assertFalse(f.contains("%"), "a composition paints no percent (" + f + ")");
    }

    /** post and pre frame the bar, and post is any resolved content — a literal or a number. */
    @ParameterizedTest
    @CsvSource(value = {
            "stack_bar_widget::[data=>[a=>1],post=>262144] % ",
    }, delimiter = '%')
    void shouldPaintPostContent(final String code, final String frame) {
        final String f = parse(code).format();
        assertEquals("] 262144", f.substring(f.length() - 8), "post content closes the bar (" + f + ")");
    }

    void shouldPaintPreAndPost(final String code, final String pre, final String post) {
        final String f = parse(code).format();
        assertEquals((null == pre || pre.isEmpty() ? "" : pre + " ") + "[", f.substring(0, f.indexOf("[") + 1), f);
        if (null != post && !post.isEmpty()) {
            assertEquals("] " + post, f.substring(f.lastIndexOf("]")), f);
        }
    }

    @Test
    void shouldPaintPreAndPostLiterals() {
        this.shouldPaintPreAndPost(
                "stack_bar_widget::[data=>[a=>1,b=>2],pre=>\"head\",post=>\"256k\"]",
                "head", "256k");
        this.shouldPaintPreAndPost(
                "stack_bar_widget::[data=>[a=>1,b=>2],pre=>\"token usage\"]",
                "token usage", "");
        this.shouldPaintPreAndPost(
                "stack_bar_widget::[data=>[a=>1,b=>2],post=>\"48k\"]",
                "", "48k");
    }

    @Test
    void shouldPaintTheWhatsLeftSectionLast() {
        final String f = parse(
                "stack_bar_widget::[data=>[ai=>22, <>=>50, system=>1],style=>[section=>[<>=>style::[body=>\"tail\"]]]]").format();
        assertTrue(f.contains("tail"), "the what's-left section is painted (" + f + ")");
        assertTrue(f.indexOf("tail") > f.indexOf("system"), "it comes after the named sections (" + f + ")");
    }

    @Test
    void shouldStyleTheWhatsLeftSectionFromUnused() {
        final String f = parse(
                "stack_bar_widget::[data=>[ai=>22, <>=>50],style=>[section=>[<>=>style::[background=>\"{{[y]}}\",width=>30]]]]").format();
        assertTrue(f.contains("{{[y]}}"), "the unused entry styles the what's-left section (" + f + ")");
    }

    @Test
    void shouldPaintAnEntryBodyAsTheLabel() {
        final String f = parse(
                "stack_bar_widget::[data=>[ai=>22, user=>1],style=>[section=>[ai=>style::[body=>\"model\"]]]]").format();
        assertTrue(f.contains("model"), "the entry's body paints as the label (" + f + ")");
        assertFalse(f.contains(">{{[g]}}ai"), "the key name yields to the body (" + f + ")");
    }

    @Test
    void shouldPaintThePercentLeftAsTheLeftLabel() {
        final String f = parse(
                "stack_bar_widget::[data=>[ai=>22, user=>10, system=>5, <>=>422],style=>[section=>[<>=>style::[body=>\"85%\"]]]]").format();
        assertTrue(f.contains("85%"), "the unused body labels the what's-left section (" + f + ")");
        assertTrue(f.indexOf("85%") > f.indexOf("ai"), "it sits at the right edge of the bar (" + f + ")");
    }

    @Test
    void shouldPaintAComputedNumericBody() {
        final String f = parse(
                "stack_bar_widget::[data=>[ai=>22, <>=>50],style=>[section=>[<>=>style::[body=>(50).div(75),background=>\"{{[k]}}\"]]]]").format();
        assertTrue(f.contains("{{[k]}}0"), "a computed int body paints its text form (" + f + ")");
        final String g = parse(
                "stack_bar_widget::[data=>[ai=>22, <>=>50],style=>[section=>[<>=>style::[body=>(75.0).div(100.0),background=>\"{{[k]}}\"]]]]").format();
        assertTrue(g.contains("0.75"), "a computed real body paints its text form (" + g + ")");
    }

    @Test
    void shouldPaintThePercentBody() {
        final String f = parse(
                "stack_bar_widget::[data=>[ai=>22, <>=>50],style=>[section=>[<>=>style::[body=>(75.0).div(100.0) * 100.0,background=>\"{{[k]}}\"]]]]").format();
        assertTrue(f.contains("75.0"), "an all-real percent body paints (" + f + ")");
    }

    /** A section's style entry — a style rec of its own: the fragment around its label, and its width. */
    @Test
    void shouldStyleEntriesFromTheOneStyleRec() {
        final String f = parse(
                "stack_bar_widget::[data=>[ai=>3,user=>1],style=>[width=>80,section=>[ai=>style::[foreground=>\"{{g}}\",width=>40]]]]").format();
        assertTrue(f.contains("{{g}}ai"), "the entry's foreground paints before its label (" + f + ")");
        assertTrue(f.contains("{{X}}user"), "a section without a style paints plain (" + f + ")");
    }

    @Test
    void shouldStyleTheUnusedSectionByTheUnusedKey() {
        final String f = parse(
                "stack_bar_widget::[data=>[a=>1,b=>2],context=>100,style=>[section=>[unused=>style::[background=>\"{{[y]}}\"]]]]").format();
        assertTrue(f.contains("{{[y]}}"), "the unused entry's background paints the tail (" + f + ")");
        assertFalse(f.contains("{{[k]}}"), "the default black tail is gone (" + f + ")");
    }

    @Test
    void shouldDropNonPositiveSections() {
        final String f = parse("stack_bar_widget::[data=>[a=>0,b=>5]]").format();
        assertFalse(f.contains("}}a"), "a zero section is dropped (" + f + ")");
        assertTrue(f.contains("}}b"), "the remaining section still draws (" + f + ")");
    }

    @Test
    void shouldDrawSmallestFirstLargestLast() {
        final String f = parse("stack_bar_widget::[data=>[user=>3234,sys=>2434,ai=>345]]").format();
        final int ai = f.indexOf("}}ai");
        final int sys = f.indexOf("}}sys");
        final int user = f.indexOf("}}user");
        assertTrue(ai < sys && sys < user && ai >= 0, "smallest first, largest last (" + f + ")");
    }

    @Test
    void shouldRenderOneLine() {
        final StackBarWidget w = parse("stack_bar_widget::[data=>[ai=>345,user=>3234],context=>262144,total=>10000]");
        final String f = w.format();
        assertFalse(f.contains("\n"), "a bar is one line (" + f + ")");
        assertEquals(1, w.height(), "a bar is one line tall");
    }
}
