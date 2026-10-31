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

package studio.phaseshift.metatron.isa.m.web.type;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.fURI.Singleton;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.sys.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Obj.Helper;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.Type.Builder;
import studio.phaseshift.metatron.isa.m.type.impl.*;

import java.util.Arrays;
import java.util.Map;

public class ObjmtronUISerializerTest extends AbstractMetatronTest {

    public ObjmtronUISerializerTest() {
    }

    @Test
    public void testUrisAreTaggedAsLinks() {
        Obj obj = ObjmtronSerializer.parse("uri::/a/b/c").apply();
        String written = ObjmtronUISerializer.single().write(obj);
        this.LOG.debug("linked uri => %s", new Object[]{written});
        Assertions.assertTrue(written.contains("{{link}}"), written);
        Assertions.assertTrue(written.contains("{{/link}}"), written);
        Assertions.assertTrue(written.contains("/a/b/c"), written);
    }

    @Test
    public void testSmallRecStaysOnOneLine() {
        Obj obj = ObjmtronSerializer.parse("[name=>'marko',age=>29]").apply();
        String written = ObjmtronUISerializer.blackWhite().write(obj);
        this.LOG.debug("small rec => %s", new Object[]{written});
        Assertions.assertFalse(written.contains("\n"), String.format("a short rec belongs on one line: %s", written));
        Assertions.assertTrue(written.contains("name=>'marko'"), written);
        Assertions.assertTrue(written.contains("age=>29"), written);
    }

    @Test
    public void testWideRecBreaksUnderItsFirstEntry() {
        Obj obj = ObjmtronSerializer.parse("[address/home/city=>'santa fe',address/work/city=>'nomansland']").apply();
        String written = ObjmtronUISerializer.blackWhite().write(obj);
        this.LOG.debug("wide rec => %s", new Object[]{written});
        int newline = written.indexOf(10);
        Assertions.assertTrue(newline > 0, String.format("a rec too wide for one line breaks: %s", written));
        Assertions.assertTrue(written.startsWith("[address/home/city=>"), String.format("the first entry rides the opening bracket: %s", written));
        Assertions.assertEquals(written.indexOf("address/home/city"), written.indexOf("address/work/city") - (newline + 1), String.format("the second entry lines up under the first: %s", written));
    }

    @Test
    public void testWideTypePrefixDropsTheEntriesToTheirOwnLines() {
        Obj obj = ObjmtronSerializer.parse("/m/mach/ui/console/command_palette::[console=>2,help=>3,quit=>4,reset=>5]").apply();
        String written = ObjmtronUISerializer.blackWhite().write(obj);
        this.LOG.debug("wide type prefix => %s", new Object[]{written});
        int firstLine = written.indexOf(10);
        Assertions.assertTrue(firstLine > 0, String.format("a wide type prefix drops the entries to their own lines: %s", written));
        Assertions.assertTrue(written.substring(0, firstLine).endsWith("::["), String.format("nothing is carried on the opening line: %s", written));
        int secondLine = firstLine + 1;
        int thirdLine = written.indexOf(10, secondLine) + 1;
        Assertions.assertEquals(written.indexOf("console=>") - secondLine, written.indexOf("help=>") - thirdLine, String.format("the entries line up with each other: %s", written));
    }

    @Test
    public void testDeepEntryColumnDropsTheEntriesToTheirOwnLines() {
        Obj inner = MRec.rec("console", MUri.uri(Singleton.f("/usr/marko/console")), new Object[]{"help", MUri.uri(Singleton.f("/m/help")), "quit", MUri.uri(Singleton.f("/m/quit"))});
        Obj outer = MRec.rec("command_palette_definition_and_helpers", inner, new Object[]{"other", MStr.str("x")});
        String written = ObjmtronUISerializer.blackWhite().write(outer);
        this.LOG.debug("deep entry column => %s", new Object[]{written});
        int firstLine = written.indexOf(10);
        Assertions.assertTrue(firstLine > 0, String.format("an entry past the column limit takes its own lines: %s", written));
        Assertions.assertTrue(written.substring(0, firstLine).endsWith("=>["), String.format("nothing is carried on the opening line: %s", written));
        int secondLine = firstLine + 1;
        int thirdLine = written.indexOf(10, secondLine) + 1;
        Assertions.assertEquals(written.indexOf("console=>") - secondLine, written.indexOf("help=>") - thirdLine, String.format("the entries line up with each other: %s", written));
    }

    @Test
    @Disabled
    public void testLongFailMessageIsClippedKeepingItsFrame() {
        String message = "inst apply failure: machine::[pattern=><#>,q=>[docq::[pattern=>docq,pre_read=>inst,obj=>memspace,inst=>instset]]] unable to convert uri::T (at /m/mach/inst/move@0)";
        String written = ObjmtronUISerializer.blackWhite().write(MFail.fail("inst apply failure: machine::[pattern=><#>,q=>[docq::[pattern=>docq,pre_read=>inst,obj=>memspace,inst=>instset]]] unable to convert uri::T (at /m/mach/inst/move@0)", new Object[0]));
        this.LOG.debug("long fail => %s", new Object[]{written});
        Assertions.assertFalse(written.contains("inst apply failure: machine::[pattern=><#>,q=>[docq::[pattern=>docq,pre_read=>inst,obj=>memspace,inst=>instset]]] unable to convert uri::T (at /m/mach/inst/move@0)"), String.format("the whole message must not be printed: %s", written));
        Assertions.assertTrue(written.length() < "inst apply failure: machine::[pattern=><#>,q=>[docq::[pattern=>docq,pre_read=>inst,obj=>memspace,inst=>instset]]] unable to convert uri::T (at /m/mach/inst/move@0)".length() / 2, String.format("the rendering is bounded: %s", written));
        Assertions.assertTrue(written.contains("inst apply failure"), String.format("the head is kept, so the context still reads: %s", written));
        Assertions.assertTrue(written.contains("uri::T (at /m/mach/inst/move@0)"), String.format("the tail is kept, so the reason and its frame survive: %s", written));
    }

    @Test
    @Disabled
    public void testFailStackBlockIsBounded() {
        String shortChain = ObjmtronUISerializer.blackWhite().write(MFail.fail(new RuntimeException("outer context", new RuntimeException("inner cause"))));
        this.LOG.debug("short fail chain => %s", new Object[]{shortChain});
        Assertions.assertTrue(shortChain.contains("outer context"), shortChain);
        Assertions.assertTrue(shortChain.contains("inner cause"), String.format("a chain that fits is shown whole: %s", shortChain));
        Assertions.assertFalse(shortChain.contains("[...]"), String.format("nothing was left out of it: %s", shortChain));
        String longChain = ObjmtronUISerializer.blackWhite().write(ObjmtronSerializer.parse("fail::[alpha][bravo][charlie][delta][echo][foxtrot][golf][hotel][india][juliet]").apply());
        this.LOG.debug("long fail chain => %s", new Object[]{longChain});
        Assertions.assertTrue(longChain.contains("[...]"), String.format("a chain past the budget is ellipsed: %s", longChain));
        Assertions.assertFalse(longChain.contains("juliet"), String.format("a level that did not fit is not printed: %s", longChain));
    }

    @Test
    @Disabled
    public void testNestedFailIsBoundedLikeATopLevelOne() {
        String message = "inst apply failure: machine::[pattern=><#>,q=>[docq::[pattern=>docq,pre_read=>inst,obj=>memspace,inst=>instset]]] unable to convert uri::T (at /m/mach/inst/move@0)";
        String written = ObjmtronUISerializer.blackWhite().write(MLst.lst(new Obj[]{MFail.fail("inst apply failure: machine::[pattern=><#>,q=>[docq::[pattern=>docq,pre_read=>inst,obj=>memspace,inst=>instset]]] unable to convert uri::T (at /m/mach/inst/move@0)", new Object[0])}));
        this.LOG.debug("nested fail => %s", new Object[]{written});
        Assertions.assertFalse(written.contains("inst apply failure: machine::[pattern=><#>,q=>[docq::[pattern=>docq,pre_read=>inst,obj=>memspace,inst=>instset]]] unable to convert uri::T (at /m/mach/inst/move@0)"), String.format("the whole message must not be printed: %s", written));
        Assertions.assertTrue(written.contains("uri::T (at /m/mach/inst/move@0)"), String.format("the nested fail keeps its reason and frame: %s", written));
    }

    @Test
    public void testBigLstIsClipped() {
        Obj obj = ObjmtronSerializer.parse("[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]").apply();
        String written = ObjmtronUISerializer.single().write(obj);
        this.LOG.debug("clipped lst => %s", new Object[]{written});
        Assertions.assertTrue(written.contains("...(5 more)"), String.format("the reader should see the clip marker: %s", written));
        Assertions.assertFalse(written.contains("14,15"), String.format("the clipped tail should not be shown: %s", written));
    }

    @Test
    public void testBigRecIsClipped() {
        Obj obj = ObjmtronSerializer.parse("rec::[k00=>v00,k01=>v01,k02=>v02,k03=>v03,k04=>v04,k05=>v05,k06=>v06,k07=>v07,k08=>v08,k09=>v09,k10=>v10,k11=>v11,k12=>v12,k13=>v13,k14=>v14]").apply();
        String written = ObjmtronUISerializer.single().write(obj);
        this.LOG.debug("clipped rec => %s", new Object[]{written});
        Assertions.assertTrue(written.contains("...(5 more)"), String.format("the reader should see the clip marker: %s", written));
    }

    @Test
    public void testLongStrIsClipped() {
        Obj obj = ObjmtronSerializer.parse("str::'a hundred aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'").apply();
        String monoForm = ObjmtronUISerializer.single().write(obj);
        String lstForm = ObjmtronUISerializer.single().write(MLst.lst(new Obj[]{obj}));
        this.LOG.debug("clipped str => %s", new Object[]{monoForm});
        Assertions.assertTrue(lstForm.contains("..."), String.format("the reader should see the clip: %s", monoForm));
        Assertions.assertFalse(lstForm.contains("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), monoForm);
        Assertions.assertFalse(monoForm.contains("..."), String.format("the reader should see the clip: %s", monoForm));
        Assertions.assertTrue(monoForm.contains("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), monoForm);
    }

    @Test
    public void testNestedPolyIsIndented() {
        Obj obj = ObjmtronSerializer.parse("rec::[a=>[b=>[c=>[d=>[e=>[f=>42]]]]],e=>[f=>[g=>[h=>43]]]]").apply();
        String written = ObjmtronUISerializer.single().write(obj);
        this.LOG.debug("nested rec => %s", new Object[]{written});
        Assertions.assertTrue(written.contains("\n"), String.format("a nested poly should break across indented lines: %s", written));
    }

    @Test
    public void testNonFiniteRealIsSpelledWhole() {
        ObjmtronUISerializer ui = ObjmtronUISerializer.blackWhite();
        String nan = ui.writeReal(MReal.real(Double.NaN));
        String pos = ui.writeReal(MReal.real(Double.POSITIVE_INFINITY));
        String neg = ui.writeReal(MReal.real(Double.NEGATIVE_INFINITY));
        this.LOG.debug("non-finite reals => %s %s %s", new Object[]{nan, pos, neg});
        Assertions.assertTrue(nan.endsWith("NaN"), String.format("a NaN real is spelled NaN: %s", nan));
        Assertions.assertTrue(pos.endsWith("Infinity"), String.format("an infinite real keeps its sign word: %s", pos));
        Assertions.assertTrue(neg.endsWith("-Infinity"), String.format("a negative infinite real too: %s", neg));
    }

    @Test
    public void testRealDisplayQuantizesWhilePlainIsLossless() {
        Obj obj = ObjmtronSerializer.parse("real::1.234567890").apply();
        String plain = (String) ObjmtronSerializer.single().write(obj);
        String ui = ObjmtronUISerializer.single().write(obj);
        this.LOG.debug("plain => %s   ui => %s", new Object[]{plain, ui});
        Assertions.assertTrue(plain.contains("1.23456789"), String.format("the plain serializer keeps the exact value: %s", plain));
        Assertions.assertTrue(ui.contains("1.2345"), String.format("the UI serializer quantizes to the display limit: %s", ui));
        Assertions.assertFalse(ui.contains("1.23456789"), ui);
    }

    @ParameterizedTest
    @CsvSource(
            value = {"5.5 * -5.5      % -30.25", "-1.5            % -1.5", "30.25           % 30.25", "123.456         % 123.456", "1.23456789      % 1.2345", "5.0             % 5.0"},
            delimiter = '%'
    )
    public void testRealDisplayKeepsItsFraction(final String code, final String expected) {
        String written = ObjmtronUISerializer.blackWhite().write(ObjmtronSerializer.parse(code).apply());
        this.LOG.debug("%s => %s", new Object[]{code, written});
        Assertions.assertEquals(expected, written);
    }

    @ParameterizedTest
    @CsvSource(
            value = {"1.23456789E7    % 1.2345E7", "1.0E-10         % 1.0E-10", "1.23456789E-7   % 1.2345E-7"},
            delimiter = '%'
    )
    public void testRealDisplayCapsTheFractionButKeepsTheExponent(final double value, final String expected) {
        String written = ObjmtronUISerializer.blackWhite().writeReal(MReal.real(value));
        this.LOG.debug("%s => %s", new Object[]{value, written});
        Assertions.assertEquals(expected, written);
    }

    @Test
    public void testPointerAddressStyle() {
        Inst pointer = MInst.instB(mInstSet.AUTO_AT_INST_TID, MLst.lst(new Obj[]{MUri.uri(Singleton.f("/m/plus"))}));
        Assertions.assertTrue(Helper.isAutoPointer(pointer), "test setup: an auto-pointer inst");
        String written = ObjmtronUISerializer.single().write(pointer);
        this.LOG.debug("address style => %s", new Object[]{written});
        Assertions.assertTrue(written.startsWith("!@"), written);
        Assertions.assertTrue(written.contains("{{link}}"), written);
        Assertions.assertTrue(written.contains("/m/plus"), written);
    }

    @Test
    public void testPointerBodyStyle() {
        Inst pointer = MInst.instB(mInstSet.AUTO_AT_INST_TID, MLst.lst(new Obj[]{MUri.uri(Singleton.f("/m/plus"))}));
        String written = ObjmtronUISerializer.linkBodies().write(pointer);
        this.LOG.debug("body style => %s", new Object[]{written});
        Assertions.assertTrue(written.contains("{{link:/m/plus}}"), written);
        Assertions.assertTrue(written.contains("{{/link}}"), written);
        Assertions.assertTrue(written.contains("!@"), "the body is still the !@ form: " + written);
    }

    @Test
    public void testOfBuildsAConfiguredInstance() {
        ObjmtronUISerializer ser = ObjmtronUISerializer.of(MRec.rec("clip", MRec.rec("lst", MInt.jnt(2L), new Object[0]), new Object[0]), (fURI) null);
        Obj obj = ObjmtronSerializer.parse("[1,2,3,4,5]").apply();
        String written = ser.write(obj);
        this.LOG.debug("configured clip => %s", new Object[]{written});
        Assertions.assertTrue(written.contains("...(3 more)"), written);
    }

    @Test
    public void testOfDefaultsClipWhenUnset() {
        ObjmtronUISerializer ser = ObjmtronUISerializer.of(MRec.rec("note", MStr.str("just a note"), new Object[0]), (fURI) null);
        Obj obj = ObjmtronSerializer.parse("[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]").apply();
        String written = ser.write(obj);
        this.LOG.debug("default clip => %s", new Object[]{written});
        Assertions.assertTrue(written.contains("...(5 more)"), String.format("the default clip->lst is 10: %s", written));
    }

    @Test
    public void testLongRecThroughWriteClipIsNested() {
        Obj bigRec = MRec.rec("entry0_key", MUri.uri(Singleton.f("/some/predicate/suffix/0")), new Object[]{"entry1_key", MUri.uri(Singleton.f("/some/predicate/suffix/1")), "entry2_key", MUri.uri(Singleton.f("/some/predicate/suffix/2")), "entry3_key", MUri.uri(Singleton.f("/some/predicate/suffix/3")), "entry4_key", MUri.uri(Singleton.f("/some/predicate/suffix/4")), "entry5_key", MUri.uri(Singleton.f("/some/predicate/suffix/5"))});
        StringBuilder sb = new StringBuilder();
        ObjmtronUISerializer.single().writeClip(sb, bigRec);
        String written = sb.toString();
        this.LOG.debug("clipped rec => %s", new Object[]{written});
        String flat = Graphitty.strip(written);
        Assertions.assertTrue(flat.contains("\n"), String.format("a long rec through the clip path must nest onto its own lines: %s", flat));
        Assertions.assertTrue(flat.startsWith("[entry0_key=>"), String.format("the first entry rides the opening bracket: %s", flat));
        Assertions.assertTrue(flat.contains("\n entry1_key=>"), String.format("the second entry starts its own line under the first: %s", flat));
        Assertions.assertFalse(flat.contains("\n  "), String.format("markup costs no columns, so every later entry stays at the first one's column: %s", flat));
        Assertions.assertTrue(flat.endsWith("]"), String.format("the closing bracket follows the last entry: %s", flat));
    }

    @Test
    public void testTypeValueRecThroughWriteClipIsNested() {
        Obj typeRec = MRec.rec("pattern", MUri.uri(Singleton.f("/some/predicate/pattern")).type(), new Object[]{"const", MInt.jnt(5L).type(), "sugar", MStr.str("nested").type()});
        StringBuilder sb = new StringBuilder();
        ObjmtronUISerializer.single().writeClip(sb, typeRec);
        String written = sb.toString();
        this.LOG.debug("type-value rec => %s", new Object[]{written});
        String flat = Graphitty.strip(written);
        Assertions.assertTrue(flat.contains("\n"), String.format("a rec of type values must nest onto its own lines: %s", flat));
        Assertions.assertTrue(flat.startsWith("[pattern=>"), String.format("the first entry rides the opening bracket: %s", flat));
        Assertions.assertTrue(flat.contains("\n const=>"), String.format("every later entry lines up under the first: %s", flat));
    }

    @Test
    public void testSingleEntryRecHoldingAStructuredValueNests() {
        Obj longValue = MRec.rec("docq::[pattern=>docq,pre_read=>inst,obj=>memspace,inst=>instset]", MRec.rec("pattern", MStr.str("docq"), new Object[]{"pre_read", MStr.str("inst"), "obj", MStr.str("memspace"), "inst", MStr.str("instset")}), new Object[0]);
        Obj outer = MRec.rec(Map.of(MUri.uri("k"), longValue));
        String written = ObjmtronUISerializer.prettyPrint().write(outer);
        this.LOG.debug("single-entry rec => %s", new Object[]{written});
        int firstLine = written.indexOf(10);
        Assertions.assertTrue(firstLine > 0, String.format("the held rec's entries take their own lines: %s", written));
        Assertions.assertTrue(written.startsWith("[k=>["), String.format("the q slot's one entry rides the opening bracket: %s", written));
        Assertions.assertTrue(written.substring(0, firstLine).endsWith("=>["), String.format("no entry is carried on the held rec's wide opening line: %s", written));
        int secondLine = firstLine + 1;
        int thirdLine = written.indexOf(10, secondLine) + 1;
        Assertions.assertEquals(written.indexOf("pattern=>'docq'") - secondLine, written.indexOf("pre_read=>'inst'") - thirdLine, String.format("the held rec's entries line up with each other: %s", written));
        int longest = Arrays.stream(written.split("\n")).mapToInt(String::length).max().orElse(0);
        Assertions.assertTrue(longest < 100, String.format("no line may run to the old wall width: %s", written));
    }

    @Test
    public void testNestedTypePredicateMaintainsDepth() {
        Type inner = Builder.build().tid(Tokens.REC_TID).isaPredicate(MRec.rec("p0", MUri.uri(Singleton.f("/predicate/0")).type(), new Object[]{"p1", MUri.uri(Singleton.f("/predicate/1")).type(), "p2", MUri.uri(Singleton.f("/predicate/2")).type(), "p3", MUri.uri(Singleton.f("/predicate/3")).type(), "p4", MUri.uri(Singleton.f("/predicate/4")).type()})).create();
        Obj outer = MRec.rec("child", inner, new Object[]{"other", MStr.str("leaf")});
        String written = ObjmtronUISerializer.prettyPrint().write(outer);
        this.LOG.debug("nested type rec => %s", new Object[]{written});
        Assertions.assertTrue(written.startsWith("[child=>"), String.format("the outer entry rides the opening bracket: %s", written));
        Assertions.assertTrue(written.contains("\n other=>"), String.format("the outer's later entry sits at depth 1: %s", written));
        Assertions.assertTrue(written.contains("\n  p1=>"), String.format("the nested type's predicate entries must be at depth 2: %s", written));
        Assertions.assertFalse(written.contains("\n p1=>"), String.format("the nested predicate must not reset to depth 1: %s", written));
    }

    @Test
    public void testDiagnosticInstanceNestsWithoutMarkup() {
        Type inner = Builder.build().tid(Tokens.REC_TID).isaPredicate(MRec.rec("p0", MUri.uri(Singleton.f("/predicate/0")).type(), new Object[]{"p1", MUri.uri(Singleton.f("/predicate/1")).type(), "p2", MUri.uri(Singleton.f("/predicate/2")).type(), "p3", MUri.uri(Singleton.f("/predicate/3")).type(), "p4", MUri.uri(Singleton.f("/predicate/4")).type()})).create();
        Obj outer = MRec.rec("child", inner, new Object[]{"other", MStr.str("leaf")});
        String written = ObjmtronUISerializer.diagnostic().write(outer);
        this.LOG.debug("diagnostic nested type rec => %s", new Object[]{written});
        Assertions.assertTrue(written.startsWith("[child=>"), String.format("the outer entry rides the opening bracket: %s", written));
        Assertions.assertTrue(written.contains("\n other=>"), String.format("a diagnostic nests like any nested poly: %s", written));
        Assertions.assertTrue(written.contains("\n  p1=>"), String.format("its nested predicate sits one level deeper: %s", written));
        Assertions.assertFalse(written.contains("{{"), String.format("a diagnostic carries no renderer markup: %s", written));
    }

    @Test
    public void testUnlinkedInstanceColorsWithoutLinks() {
        Obj obj = ObjmtronSerializer.parse("uri::/a/b/c").apply();
        String written = ObjmtronUISerializer.unlinked().write(obj);
        this.LOG.debug("unlinked uri => %s", new Object[]{written});
        Assertions.assertFalse(written.contains("{{link}}"), written);
        Assertions.assertFalse(written.contains("{{/link}}"), written);
        Assertions.assertTrue(written.contains("/a/b/c"), written);
        Assertions.assertTrue(written.contains("{{b}}"), String.format("the uri's color markup must remain: %s", written));
        Assertions.assertTrue(written.contains("{{/b}}"), written);
    }

    @Test
    public void testUnlinkedInstanceNestsLikeTheConsole() {
        Obj obj = ObjmtronSerializer.parse("rec::[a=>[bbbb=>1,cccc=>2,dddd=>3,eeee=>4,ffff=>5,gggg=>6]]").apply();
        String written = ObjmtronUISerializer.unlinked().write(obj);
        this.LOG.debug("unlinked nested rec => %s", new Object[]{written});
        String flat = Graphitty.strip(written);
        Assertions.assertTrue(flat.contains("\n"), String.format("a nested poly should indent: %s", flat));
        Assertions.assertTrue(flat.startsWith("[a=>[bbbb=>1,"), String.format("the outer entry and the inner's first share the opening line: %s", flat));
        Assertions.assertTrue(flat.contains("\n     cccc=>2"), String.format("the inner's later entries line up under its first: %s", flat));
        Assertions.assertFalse(written.contains("{{link}}"), written);
    }

    @Test
    public void testTypeViolationDiffNests() {
        Obj actual = MRec.rec("instset", MRec.rec("pattern", MUri.uri(Singleton.f("/m/#")), new Object[0]), new Object[]{"processor", MRec.rec("state", MStr.str("run"), new Object[0])});
        Obj expected = MRec.rec("instset", MRec.rec("pattern", MUri.uri(Singleton.f("/m/#")).type(), new Object[]{"sugar", MStr.str("x").type()}), new Object[]{"processor", MRec.rec("state", MStr.str("run").type(), new Object[]{"result", MStr.str("y").type()})});
        Obj diff = studio.phaseshift.metatron.isa.m.type.Poly.Helper.diffObjRecursion(actual, expected);
        String flat = diff.toString();
        String written = ObjmtronUISerializer.diagnostic().write(diff);
        this.LOG.debug("type violation diff => %s", new Object[]{written});
        Assertions.assertFalse(flat.contains("\n"), String.format("the plain serializer stays one-line data: %s", flat));
        Assertions.assertTrue(written.split("\n").length > 2, String.format("the diagnostic walks the diff across indented lines: %s", written));
        Assertions.assertTrue(written.startsWith("[instset=>"), String.format("the first failed slot rides the opening bracket: %s", written));
        Assertions.assertTrue(written.contains("\n processor=>"), String.format("the second failed slot owns its indented line: %s", written));
        Assertions.assertFalse(written.contains("{{"), String.format("no renderer markup in a fail message: %s", written));
    }

    @ParameterizedTest
    @CsvSource(
            value = {"[a=>{[1,2,3],[2,3],[1,2,3]}] % [a=>{{2}[1,2,3],[2,3]}]", "{[1,2,3],[2,3],[1,2,3]}      % {{2}[1,2,3],[2,3]}"},
            delimiter = '%'
    )
    public void testValueBracesAreTextNotMarkup(final String code, final String expected) {
        Obj obj = ObjmtronSerializer.parse(code).apply();
        String written = ObjmtronUISerializer.single().write(obj);
        String shown = Graphitty.strip(written);
        this.LOG.debug("%s => %s (drawn as %s)", new Object[]{code, written, shown});
        Assertions.assertEquals(expected, shown, String.format("the value's braces belong to the value: %s", written));
        Assertions.assertFalse(shown.contains("\\"), String.format("a value's braces need no escape: %s", shown));
    }

    @ParameterizedTest
    @CsvSource(
            value = {"inst?#{*}<=#{?}(#{*}::T)   % inst(#{*}::T)           % the default typing draws as nothing", "inst?#{*}<=#{?}&a=b(#{*}::T) % inst?a=b(#{*}::T)     % but another q param still draws", "inst?a=b(#{*}::T)          % inst?a=b(#{*}::T)       % no pair, one param", "inst?#{1}<=#{1}(#{*}::T)   % inst?#<=#(#{*}::T)      % a real pair stays", "inst?str<=int(#{*}::T)     % inst?str<=int(#{*}::T)  % so does a named pair", "inst?str<=int&a=b(#{*}::T) % inst?str<=int&a=b(#{*}::T) % a named pair with its param", "plus(1,2)                  % plus(1,2)                % a plain call carries no typing noise", "inst(1,2)                  % inst(1,2)                % and neither does a bare inst"},
            delimiter = '%'
    )
    public void testInstTypingIsDrawnOnlyWhenSpecified(final String code, final String expected, final String desc) {
        Obj obj = ObjmtronSerializer.parse(code);
        String written = ObjmtronUISerializer.blackWhite().write(obj);
        this.LOG.debug("%s => %s", new Object[]{code, written});
        Assertions.assertEquals(expected, written, String.format("%s: %s", desc, written));
    }

    @Test
    public void testStreamOfLstsRendersInBlackAndWhite() {
        Obj obj = ObjmtronSerializer.parse("[a=>{[1,2,3],[2,3],[1,2,3]}]").apply();
        String written = ObjmtronUISerializer.blackWhite().write(obj);
        this.LOG.debug("stream of lsts (black and white) => %s", new Object[]{written});
        Assertions.assertEquals("[a=>{{2}[1,2,3],[2,3]}]", written, String.format("a stream's coefficient reaches an uncolored render too: %s", written));
    }

    @Test
    public void testStreamMembersRenderThroughTheUiWriter() {
        Obj obj = MObjs.objs(new Obj[]{MLst.lst(new Obj[]{MUri.uri(Singleton.f("/a/b"))}), MLst.lst(new Obj[]{MInt.jnt(1L), MInt.jnt(2L), MInt.jnt(3L), MInt.jnt(4L), MInt.jnt(5L), MInt.jnt(6L), MInt.jnt(7L), MInt.jnt(8L), MInt.jnt(9L), MInt.jnt(10L), MInt.jnt(11L), MInt.jnt(12L)})});
        String written = ObjmtronUISerializer.single().write(obj);
        this.LOG.debug("stream members => %s", new Object[]{written});
        Assertions.assertTrue(written.contains("{{link}}"), String.format("a uri inside a stream is tagged like any other: %s", written));
        Assertions.assertTrue(written.contains("...(2 more)"), String.format("a member is clipped like the lst it is: %s", written));
    }
}
