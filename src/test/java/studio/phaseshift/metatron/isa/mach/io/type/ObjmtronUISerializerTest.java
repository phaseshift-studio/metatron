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

package studio.phaseshift.metatron.isa.mach.io.type;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.impl.MInst;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.REC_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.AUTO_AT_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The UI serializer renders for a reader: uris are tagged as links, big polys are clipped
 * and indented, reals are quantized to the display limit, and pointer instructions are drawn
 * in the instance's pointer style.  (The plain serializer's lossless-legal-mtron contract is
 * asserted in ObjmtronSerializerPurityTest.)
 */
public class ObjmtronUISerializerTest extends AbstractMetatronTest {

    @Test
    public void testUrisAreTaggedAsLinks() {
        final Obj obj = ObjmtronSerializer.parse("uri::/a/b/c").apply();
        final String written = ObjmtronUISerializer.single().write(obj);
        LOG.debug("linked uri => %s", written);
        assertTrue(written.contains("{{link}}"), written);
        assertTrue(written.contains("{{/link}}"), written);
        assertTrue(written.contains("/a/b/c"), written);
    }

    @Test
    public void testSmallRecStaysOnOneLine() {
        // a rec whose mtron text is short reads on one line: the stack of newlines a break
        // would add buys nothing
        final Obj obj = ObjmtronSerializer.parse("[name=>'marko',age=>29]").apply();
        final String written = ObjmtronUISerializer.blackWhite().write(obj);
        LOG.debug("small rec => %s", written);
        assertFalse(written.contains("\n"), String.format("a short rec belongs on one line: %s", written));
        assertTrue(written.contains("name=>'marko'"), written);
        assertTrue(written.contains("age=>29"), written);
    }

    @Test
    public void testWideRecBreaksUnderItsFirstEntry() {
        // a rec too wide for one line breaks, and the aligned model is what it breaks into: the
        // first entry rides the opening bracket and every entry after it begins at the column
        // the first one begins at
        final Obj obj = ObjmtronSerializer.parse("[address/home/city=>'santa fe',address/work/city=>'nomansland']").apply();
        final String written = ObjmtronUISerializer.blackWhite().write(obj);
        LOG.debug("wide rec => %s", written);
        final int newline = written.indexOf('\n');
        assertTrue(newline > 0, String.format("a rec too wide for one line breaks: %s", written));
        assertTrue(written.startsWith("[address/home/city=>"), String.format("the first entry rides the opening bracket: %s", written));
        assertEquals(written.indexOf("address/home/city"), written.indexOf("address/work/city") - (newline + 1),
                String.format("the second entry lines up under the first: %s", written));
    }

    @Test
    public void testWideTypePrefixDropsTheEntriesToTheirOwnLines() {
        // a type name that is a path — the console's /m/mach/ui/console/command_palette::[…] —
        // is wide before the first entry is even written, so nothing is carried on the opening
        // line: the entries take lines of their own and line up with each other
        final Obj obj = ObjmtronSerializer.parse("/m/mach/ui/console/command_palette::[console=>2,help=>3,quit=>4,reset=>5]").apply();
        final String written = ObjmtronUISerializer.blackWhite().write(obj);
        LOG.debug("wide type prefix => %s", written);
        final int firstLine = written.indexOf('\n');
        assertTrue(firstLine > 0, String.format("a wide type prefix drops the entries to their own lines: %s", written));
        assertTrue(written.substring(0, firstLine).endsWith("::["),
                String.format("nothing is carried on the opening line: %s", written));
        final int secondLine = firstLine + 1;
        final int thirdLine = written.indexOf('\n', secondLine) + 1;
        assertEquals(written.indexOf("console=>") - secondLine, written.indexOf("help=>") - thirdLine,
                String.format("the entries line up with each other: %s", written));
    }

    @Test
    public void testDeepEntryColumnDropsTheEntriesToTheirOwnLines() {
        // the guard behind the type-prefix rule: once the column an entry would begin at runs
        // past MAX_ENTRY_COLUMN — here a long entry key rather than a type name — the entries
        // take their own lines too
        final Obj inner = rec("console", uri(f("/usr/marko/console")),
                "help", uri(f("/m/help")),
                "quit", uri(f("/m/quit")));
        final Obj outer = rec("command_palette_definition_and_helpers", inner, "other", str("x"));
        final String written = ObjmtronUISerializer.blackWhite().write(outer);
        LOG.debug("deep entry column => %s", written);
        final int firstLine = written.indexOf('\n');
        assertTrue(firstLine > 0, String.format("an entry past the column limit takes its own lines: %s", written));
        assertTrue(written.substring(0, firstLine).endsWith("=>["),
                String.format("nothing is carried on the opening line: %s", written));
        final int secondLine = firstLine + 1;
        final int thirdLine = written.indexOf('\n', secondLine) + 1;
        assertEquals(written.indexOf("console=>") - secondLine, written.indexOf("help=>") - thirdLine,
                String.format("the entries line up with each other: %s", written));
    }

    @Test
    @Disabled
    public void testLongFailMessageIsClippedKeepingItsFrame() {
        // a fail's message can carry a whole obj — the inst apply failure: machine::[…] of a type
        // mismatch — and it used to print at whatever length it was.  The in-flow rendering is
        // bounded, and it is clipped from the MIDDLE: the head names the context and the tail
        // carries the reason and the (at <uri>) frame, with the dumped obj elided between them
        final String message = "inst apply failure: machine::[pattern=><#>,q=>[docq::[pattern=>docq,"
                + "pre_read=>inst,obj=>memspace,inst=>instset]]] unable to convert uri::T (at /m/mach/inst/move@0)";
        final String written = ObjmtronUISerializer.blackWhite().write(fail(message));
        LOG.debug("long fail => %s", written);
        assertFalse(written.contains(message), String.format("the whole message must not be printed: %s", written));
        assertTrue(written.length() < message.length() / 2, String.format("the rendering is bounded: %s", written));
        assertTrue(written.contains("inst apply failure"),
                String.format("the head is kept, so the context still reads: %s", written));
        assertTrue(written.contains("uri::T (at /m/mach/inst/move@0)"),
                String.format("the tail is kept, so the reason and its frame survive: %s", written));
    }

    @Test
    @Disabled
    public void testFailStackBlockIsBounded() {
        // the [msg][cause][cause] stack block is a fail's stack: the levels that fit within
        // clip->fail are shown, and the block is ellipsed rather than running on
        final String shortChain = ObjmtronUISerializer.blackWhite().write(
                fail(new RuntimeException("outer context", new RuntimeException("inner cause"))));
        LOG.debug("short fail chain => %s", shortChain);
        assertTrue(shortChain.contains("outer context"), shortChain);
        assertTrue(shortChain.contains("inner cause"), String.format("a chain that fits is shown whole: %s", shortChain));
        assertFalse(shortChain.contains("[...]"), String.format("nothing was left out of it: %s", shortChain));

        final String longChain = ObjmtronUISerializer.blackWhite().write(
                ObjmtronSerializer.parse("fail::[alpha][bravo][charlie][delta][echo][foxtrot][golf][hotel][india][juliet]").apply());
        LOG.debug("long fail chain => %s", longChain);
        assertTrue(longChain.contains("[...]"), String.format("a chain past the budget is ellipsed: %s", longChain));
        assertFalse(longChain.contains("juliet"), String.format("a level that did not fit is not printed: %s", longChain));
    }

    @Test
    @Disabled
    public void testNestedFailIsBoundedLikeATopLevelOne() {
        // the same bounded stack block wherever a fail is rendered: nested, a message that dumped
        // an obj into its bracket would otherwise break the line the fail sits in
        final String message = "inst apply failure: machine::[pattern=><#>,q=>[docq::[pattern=>docq,"
                + "pre_read=>inst,obj=>memspace,inst=>instset]]] unable to convert uri::T (at /m/mach/inst/move@0)";
        final String written = ObjmtronUISerializer.blackWhite().write(lst(fail(message)));
        LOG.debug("nested fail => %s", written);
        assertFalse(written.contains(message), String.format("the whole message must not be printed: %s", written));
        assertTrue(written.contains("uri::T (at /m/mach/inst/move@0)"),
                String.format("the nested fail keeps its reason and frame: %s", written));
    }

    @Test
    public void testBigLstIsClipped() {
        final Obj obj = ObjmtronSerializer.parse("[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]").apply();
        final String written = ObjmtronUISerializer.single().write(obj);
        LOG.debug("clipped lst => %s", written);
        assertTrue(written.contains("...(5 more)"), String.format("the reader should see the clip marker: %s", written));
        assertFalse(written.contains("14,15"), String.format("the clipped tail should not be shown: %s", written));
    }

    @Test
    public void testBigRecIsClipped() {
        final Obj obj = ObjmtronSerializer.parse("rec::[k00=>v00,k01=>v01,k02=>v02,k03=>v03,k04=>v04,k05=>v05,k06=>v06,k07=>v07,k08=>v08,k09=>v09,k10=>v10,k11=>v11,k12=>v12,k13=>v13,k14=>v14]").apply();
        final String written = ObjmtronUISerializer.single().write(obj);
        LOG.debug("clipped rec => %s", written);
        assertTrue(written.contains("...(5 more)"), String.format("the reader should see the clip marker: %s", written));
    }

    @Test
    public void testLongStrIsClipped() {
        final Obj obj = ObjmtronSerializer.parse("str::'a hundred aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'").apply();
        final String monoForm = ObjmtronUISerializer.single().write(obj);
        final String lstForm = ObjmtronUISerializer.single().write(lst(obj));
        LOG.debug("clipped str => %s", monoForm);
        assertTrue(lstForm.contains("..."), String.format("the reader should see the clip: %s", monoForm));
        assertFalse(lstForm.contains("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), monoForm);
        assertFalse(monoForm.contains("..."), String.format("the reader should see the clip: %s", monoForm));
        assertTrue(monoForm.contains("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), monoForm);
    }

    @Test
    public void testNestedPolyIsIndented() {
        final Obj obj = ObjmtronSerializer.parse("rec::[a=>[b=>[c=>[d=>[e=>[f=>42]]]]],e=>[f=>[g=>[h=>43]]]]").apply();
        final String written = ObjmtronUISerializer.single().write(obj);
        LOG.debug("nested rec => %s", written);
        assertTrue(written.contains("\n"), String.format("a nested poly should break across indented lines: %s", written));
    }

    /**
     * A NON-FINITE REAL HAS NO MANTISSA. The display writer shortens a real to the digits after the
     * point, which on "NaN" read {@code indexOf(".")} as -1 and sliced past the end
     * ({@code Range [0, 5) out of bounds for length 3}), while "Infinity"/"-Infinity" were silently
     * cut to "Infin"/"-Infin". They have no fraction to drop, so they are spelled whole.
     */
    @Test
    public void testNonFiniteRealIsSpelledWhole() {
        final ObjmtronUISerializer ui = ObjmtronUISerializer.blackWhite();
        final String nan = ui.writeReal(real(Double.NaN));
        final String pos = ui.writeReal(real(Double.POSITIVE_INFINITY));
        final String neg = ui.writeReal(real(Double.NEGATIVE_INFINITY));
        LOG.debug("non-finite reals => %s %s %s", nan, pos, neg);
        assertTrue(nan.endsWith("NaN"), String.format("a NaN real is spelled NaN: %s", nan));
        assertTrue(pos.endsWith("Infinity"), String.format("an infinite real keeps its sign word: %s", pos));
        assertTrue(neg.endsWith("-Infinity"), String.format("a negative infinite real too: %s", neg));
    }

    @Test
    public void testRealDisplayQuantizesWhilePlainIsLossless() {
        final Obj obj = ObjmtronSerializer.parse("real::1.234567890").apply();
        final String plain = ObjmtronSerializer.single().write(obj);
        final String ui = ObjmtronUISerializer.single().write(obj);
        LOG.debug("plain => %s   ui => %s", plain, ui);
        assertTrue(plain.contains("1.23456789"), String.format("the plain serializer keeps the exact value: %s", plain));
        assertTrue(ui.contains("1.2345"), String.format("the UI serializer quantizes to the display limit: %s", ui));
        assertFalse(ui.contains("1.23456789"), ui);
    }

    /**
     * A REAL'S DISPLAY MUST NOT EAT ITS FRACTION. The display cap at four fraction digits used
     * the length of the fractional PART where it meant the length of the WHOLE string, so every
     * value whose integer part was long enough lost digits: {@code 5.5 * -5.5} (whose value is
     * -30.25) rendered as {@code -30.0}, and even {@code -1.5} rendered as {@code -1.0}. The
     * arithmetic was never wrong — only the rendering was.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "5.5 * -5.5      % -30.25",
            "-1.5            % -1.5",
            "30.25           % 30.25",
            "123.456         % 123.456",
            "1.23456789      % 1.2345",
            "5.0             % 5.0",
    }, delimiter = '%')
    public void testRealDisplayKeepsItsFraction(final String code, final String expected) {
        final String written = ObjmtronUISerializer.blackWhite().write(ObjmtronSerializer.parse(code).apply());
        LOG.debug("%s => %s", code, written);
        assertEquals(expected, written);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "1.23456789E7    % 1.2345E7",
            "1.0E-10         % 1.0E-10",
            "1.23456789E-7   % 1.2345E-7",
    }, delimiter = '%')
    public void testRealDisplayCapsTheFractionButKeepsTheExponent(final double value, final String expected) {
        final String written = ObjmtronUISerializer.blackWhite().writeReal(real(value));
        LOG.debug("%s => %s", value, written);
        assertEquals(expected, written);
    }

    @Test
    public void testPointerAddressStyle() {
        // the console instance draws an auto-pointer as its address
        final Inst pointer = MInst.instB(AUTO_AT_INST_TID, lst(uri(f("/m/plus"))));
        assertTrue(Obj.Helper.isAutoPointer(pointer), "test setup: an auto-pointer inst");
        final String written = ObjmtronUISerializer.single().write(pointer);
        LOG.debug("address style => %s", written);
        assertTrue(written.startsWith("!@"), written);
        assertTrue(written.contains("{{link}}"), written);
        assertTrue(written.contains("/m/plus"), written);
    }

    @Test
    public void testPointerBodyStyle() {
        // the log instance wraps the body in one link whose target is the pointer's address
        final Inst pointer = MInst.instB(AUTO_AT_INST_TID, lst(uri(f("/m/plus"))));
        final String written = ObjmtronUISerializer.linkBodies().write(pointer);
        LOG.debug("body style => %s", written);
        assertTrue(written.contains("{{link:/m/plus}}"), written);
        assertTrue(written.contains("{{/link}}"), written);
        assertTrue(written.contains("!@"), "the body is still the !@ form: " + written);
    }

    @Test
    public void testOfBuildsAConfiguredInstance() {
        // a rec that tunes the clip is honored by the instance built from it
        final ObjmtronUISerializer ser = ObjmtronUISerializer.of(rec("clip", rec("lst", jnt(2))), null);
        final Obj obj = ObjmtronSerializer.parse("[1,2,3,4,5]").apply();
        final String written = ser.write(obj);
        LOG.debug("configured clip => %s", written);
        assertTrue(written.contains("...(3 more)"), written);
    }

    @Test
    public void testOfDefaultsClipWhenUnset() {
        final ObjmtronUISerializer ser = ObjmtronUISerializer.of(rec("note", str("just a note")), null);
        final Obj obj = ObjmtronSerializer.parse("[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]").apply();
        final String written = ser.write(obj);
        LOG.debug("default clip => %s", written);
        assertTrue(written.contains("...(5 more)"), String.format("the default clip->lst is 10: %s", written));
    }

    @Test
    public void testLongRecThroughWriteClipIsNested() {
        // the path a type's predicate rec takes: generateType hands the value to writeClip, and
        // before the rec reached the nested generator every such rec (the *instset predicate on
        // the console) came out as one run-on line
        final Obj bigRec = rec(
                "entry0_key", uri(f("/some/predicate/suffix/0")),
                "entry1_key", uri(f("/some/predicate/suffix/1")),
                "entry2_key", uri(f("/some/predicate/suffix/2")),
                "entry3_key", uri(f("/some/predicate/suffix/3")),
                "entry4_key", uri(f("/some/predicate/suffix/4")),
                "entry5_key", uri(f("/some/predicate/suffix/5")));
        final StringBuilder sb = new StringBuilder();
        ObjmtronUISerializer.single().writeClip(sb, bigRec);
        final String written = sb.toString();
        LOG.debug("clipped rec => %s", written);
        // the console instance writes markup, so the layout is read off the text a reader sees
        final String flat = Graphitty.strip(written);
        assertTrue(flat.contains("\n"), String.format("a long rec through the clip path must nest onto its own lines: %s", flat));
        assertTrue(flat.startsWith("[entry0_key=>"), String.format("the first entry rides the opening bracket: %s", flat));
        assertTrue(flat.contains("\n entry1_key=>"), String.format("the second entry starts its own line under the first: %s", flat));
        assertFalse(flat.contains("\n  "), String.format("markup costs no columns, so every later entry stays at the first one's column: %s", flat));
        assertTrue(flat.endsWith("]"), String.format("the closing bracket follows the last entry: %s", flat));
    }

    @Test
    public void testTypeValueRecThroughWriteClipIsNested() {
        // the *instset shape: a short rec whose values are types (uri::T, int::T, ...).
        // such a rec's java toString is viewless, so the nesting decision must rest on the
        // entries' structure, not on length
        final Obj typeRec = rec(
                "pattern", uri(f("/some/predicate/pattern")).type(),
                "const", jnt(5).type(),
                "sugar", str("nested").type());
        final StringBuilder sb = new StringBuilder();
        ObjmtronUISerializer.single().writeClip(sb, typeRec);
        final String written = sb.toString();
        LOG.debug("type-value rec => %s", written);
        // a rec of types is viewless in java, so the nesting decision rests on the text the
        // entries actually cost — and the break is the aligned one
        final String flat = Graphitty.strip(written);
        assertTrue(flat.contains("\n"), String.format("a rec of type values must nest onto its own lines: %s", flat));
        assertTrue(flat.startsWith("[pattern=>"), String.format("the first entry rides the opening bracket: %s", flat));
        assertTrue(flat.contains("\n const=>"), String.format("every later entry lines up under the first: %s", flat));
    }

    @Test
    public void testSingleEntryRecHoldingAStructuredValueNests() {
        // [docq::[…]] — the machine's own q slot, and the rec the type-violation diff ends on.
        // A one-entry rec has no boundary between entries to break at, so its entry rides the
        // opening bracket; the rec it holds is wide enough to break, and its own opening line
        // has long run past MAX_ENTRY_COLUMN by then, so its entries take lines of their own
        // rather than being dragged out to that column
        final Obj longValue = rec(
                "docq::[pattern=>docq,pre_read=>inst,obj=>memspace,inst=>instset]",
                rec("pattern", str("docq"), "pre_read", str("inst"), "obj", str("memspace"), "inst", str("instset")));
        final Obj outer = rec(Map.of(uri("k"), (Obj) longValue));
        final String written = ObjmtronUISerializer.prettyPrint().write(outer);
        LOG.debug("single-entry rec => %s", written);
        final int firstLine = written.indexOf('\n');
        assertTrue(firstLine > 0, String.format("the held rec's entries take their own lines: %s", written));
        assertTrue(written.startsWith("[k=>["), String.format("the q slot's one entry rides the opening bracket: %s", written));
        assertTrue(written.substring(0, firstLine).endsWith("=>["),
                String.format("no entry is carried on the held rec's wide opening line: %s", written));
        final int secondLine = firstLine + 1;
        final int thirdLine = written.indexOf('\n', secondLine) + 1;
        assertEquals(written.indexOf("pattern=>'docq'") - secondLine, written.indexOf("pre_read=>'inst'") - thirdLine,
                String.format("the held rec's entries line up with each other: %s", written));
        final int longest = java.util.Arrays.stream(written.split("\n")).mapToInt(String::length).max().orElse(0);
        assertTrue(longest < 100, String.format("no line may run to the old wall width: %s", written));
    }

    @Test
    public void testNestedTypePredicateMaintainsDepth() {
        // the *mach shape: a rec whose value is a type whose own predicate is a multi-entry rec
        // wide enough to break.  before the type path threaded depth, the inner predicate's
        // entries came out at the same one-space indent as the outer rec's entries instead of
        // one level deeper
        final Type inner = Type.Builder.build()
                .tid(REC_TID)
                .isaPredicate(rec(
                        "p0", uri(f("/predicate/0")).type(),
                        "p1", uri(f("/predicate/1")).type(),
                        "p2", uri(f("/predicate/2")).type(),
                        "p3", uri(f("/predicate/3")).type(),
                        "p4", uri(f("/predicate/4")).type()))
                .create();
        final Obj outer = rec("child", inner, "other", str("leaf"));
        final String written = ObjmtronUISerializer.prettyPrint().write(outer);
        LOG.debug("nested type rec => %s", written);
        assertTrue(written.startsWith("[child=>"), String.format("the outer entry rides the opening bracket: %s", written));
        // the outer rec's own entry sits at depth 1, the nested type's predicate at depth 2
        assertTrue(written.contains("\n other=>"), String.format("the outer's later entry sits at depth 1: %s", written));
        assertTrue(written.contains("\n  p1=>"), String.format("the nested type's predicate entries must be at depth 2: %s", written));
        assertFalse(written.contains("\n p1=>"), String.format("the nested predicate must not reset to depth 1: %s", written));
    }

    @Test
    public void testDiagnosticInstanceNestsWithoutMarkup() {
        // the diagnostic instance: what a fail message renders through.  A type whose predicate
        // is a rec of type values wide enough to break nests onto indented lines, its predicate
        // one level deeper, and - because the message is read in a log, a test, or another
        // process - it carries no {{link}} or color markup
        final Type inner = Type.Builder.build()
                .tid(REC_TID)
                .isaPredicate(rec(
                        "p0", uri(f("/predicate/0")).type(),
                        "p1", uri(f("/predicate/1")).type(),
                        "p2", uri(f("/predicate/2")).type(),
                        "p3", uri(f("/predicate/3")).type(),
                        "p4", uri(f("/predicate/4")).type()))
                .create();
        final Obj outer = rec("child", inner, "other", str("leaf"));
        final String written = ObjmtronUISerializer.diagnostic().write(outer);
        LOG.debug("diagnostic nested type rec => %s", written);
        assertTrue(written.startsWith("[child=>"), String.format("the outer entry rides the opening bracket: %s", written));
        assertTrue(written.contains("\n other=>"), String.format("a diagnostic nests like any nested poly: %s", written));
        assertTrue(written.contains("\n  p1=>"), String.format("its nested predicate sits one level deeper: %s", written));
        assertFalse(written.contains("{{"), String.format("a diagnostic carries no renderer markup: %s", written));
    }

    @Test
    public void testUnlinkedInstanceColorsWithoutLinks() {
        // the unlinked instance: what a log line renders an obj through.  Like the diagnostic it
        // carries no {{link}} tags, but unlike the diagnostic its color stays on — a uri is drawn
        // as its colored text, not as a hyperlink no reader can click.
        final Obj obj = ObjmtronSerializer.parse("uri::/a/b/c").apply();
        final String written = ObjmtronUISerializer.unlinked().write(obj);
        LOG.debug("unlinked uri => %s", written);
        assertFalse(written.contains("{{link}}"), written);
        assertFalse(written.contains("{{/link}}"), written);
        assertTrue(written.contains("/a/b/c"), written);
        assertTrue(written.contains("{{b}}"), String.format("the uri's color markup must remain: %s", written));
        assertTrue(written.contains("{{/b}}"), written);
    }

    @Test
    public void testUnlinkedInstanceNestsLikeTheConsole() {
        // indentation is a display feature independent of linking: a poly wide enough to break
        // still spans indented lines when the instance drops the link tags
        final Obj obj = ObjmtronSerializer.parse("rec::[a=>[bbbb=>1,cccc=>2,dddd=>3,eeee=>4,ffff=>5,gggg=>6]]").apply();
        final String written = ObjmtronUISerializer.unlinked().write(obj);
        LOG.debug("unlinked nested rec => %s", written);
        final String flat = Graphitty.strip(written);
        assertTrue(flat.contains("\n"), String.format("a nested poly should indent: %s", flat));
        assertTrue(flat.startsWith("[a=>[bbbb=>1,"), String.format("the outer entry and the inner's first share the opening line: %s", flat));
        assertTrue(flat.contains("\n     cccc=>2"), String.format("the inner's later entries line up under its first: %s", flat));
        assertFalse(written.contains("{{link}}"), written);
    }

    @Test
    public void testTypeViolationDiffNests() {
        // the diff a type violation reports is built by Poly.Helper and rendered into the fail
        // message.  Its top level is one entry per failed slot and its values are the expected
        // recs, so as toString() it ran on as a single line of nested parens.  Rendered through
        // the diagnostic instance it walks one slot per indented line
        final Obj actual = rec(
                "instset", rec("pattern", uri(f("/m/#"))),
                "processor", rec("state", str("run")));
        final Obj expected = rec(
                "instset", rec("pattern", uri(f("/m/#")).type(), "sugar", str("x").type()),
                "processor", rec("state", str("run").type(), "result", str("y").type()));
        final Obj diff = Poly.Helper.diffObjRecursion(actual, expected);
        final String flat = diff.toString();
        final String written = ObjmtronUISerializer.diagnostic().write(diff);
        LOG.debug("type violation diff => %s", written);
        assertFalse(flat.contains("\n"), String.format("the plain serializer stays one-line data: %s", flat));
        assertTrue(written.split("\n").length > 2,
                String.format("the diagnostic walks the diff across indented lines: %s", written));
        assertTrue(written.startsWith("[instset=>"), String.format("the first failed slot rides the opening bracket: %s", written));
        assertTrue(written.contains("\n processor=>"), String.format("the second failed slot owns its indented line: %s", written));
        assertFalse(written.contains("{{"), String.format("no renderer markup in a fail message: %s", written));
    }

    /**
     * A STREAM'S COEFFICIENT IS DRAWN AS IT IS, not escaped.  A stream — {@code objs}, a
     * multiplicity — can carry a coefficient on a member: two equal members fold into
     * {@code {2}[1,2,3]}, and the stream's opening brace directly in front of that coefficient
     * spells {@code {{}, graphitty's rule opener.  Read as an unclosed rule, it used to consume
     * everything to the end of the line, so {@code [a=>{[1,2,3],[2,3],[1,2,3]}]} drew as
     * {@code [a=>} and the stream on its own drew as nothing at all.  A rule that never closes
     * is not a rule (see GraphittyTest), so the braces reach the screen without a backslash.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "[a=>{[1,2,3],[2,3],[1,2,3]}] % [a=>{{2}[1,2,3],[2,3]}]",
            "{[1,2,3],[2,3],[1,2,3]}      % {{2}[1,2,3],[2,3]}",
    }, delimiter = '%')
    public void testValueBracesAreTextNotMarkup(final String code, final String expected) {
        final Obj obj = ObjmtronSerializer.parse(code).apply();
        final String written = ObjmtronUISerializer.single().write(obj);
        final String shown = Graphitty.strip(written);
        LOG.debug("%s => %s (drawn as %s)", code, written, shown);
        assertEquals(expected, shown, String.format("the value's braces belong to the value: %s", written));
        assertFalse(shown.contains("\\"), String.format("a value's braces need no escape: %s", shown));
    }

    /**
     * AN INSTRUCTION'S TYPING IS DRAWN ONLY WHEN IT SAYS SOMETHING.  The pair is {@code rng<=dom}:
     * {@code #{*}<=#{?}} (all, maybe-some to all, maybe) is the typing an untyped instruction is
     * given, so drawing it states nothing — {@code inst?#{*}<=#{?}(#{*}::T)} reads
     * {@code inst(#{*}::T)} — and a tid that carries no pair has none to draw, which is why a plain
     * call used to render as {@code plus?#<=#(1,2)}.  Every other pair is a real refinement and
     * stays, and so does every OTHER q param (the one that remains takes the {@code ?}).
     */
    @ParameterizedTest
    @CsvSource(value = {
            "inst?#{*}<=#{?}(#{*}::T)   % inst(#{*}::T)           % the default typing draws as nothing",
            "inst?#{*}<=#{?}&a=b(#{*}::T) % inst?a=b(#{*}::T)     % but another q param still draws",
            "inst?a=b(#{*}::T)          % inst?a=b(#{*}::T)       % no pair, one param",
            "inst?#{1}<=#{1}(#{*}::T)   % inst?#<=#(#{*}::T)      % a real pair stays",
            "inst?str<=int(#{*}::T)     % inst?str<=int(#{*}::T)  % so does a named pair",
            "inst?str<=int&a=b(#{*}::T) % inst?str<=int&a=b(#{*}::T) % a named pair with its param",
            "plus(1,2)                  % plus(1,2)                % a plain call carries no typing noise",
            "inst(1,2)                  % inst(1,2)                % and neither does a bare inst",
    }, delimiter = '%')
    public void testInstTypingIsDrawnOnlyWhenSpecified(final String code, final String expected, final String desc) {
        // an inst literal is parsed, never applied: applying it would run the instruction
        final Obj obj = ObjmtronSerializer.parse(code);
        final String written = ObjmtronUISerializer.blackWhite().write(obj);
        LOG.debug("%s => %s", code, written);
        assertEquals(expected, written, String.format("%s: %s", desc, written));
    }

    @Test
    public void testStreamOfLstsRendersInBlackAndWhite() {
        // the uncolored instances strip the markup instead of leaving it for a renderer, and a
        // single pass is what makes that work: escaped for that pass, the coefficient's brace is
        // data on the way out (a second pass would read it as a rule, as it used to)
        final Obj obj = ObjmtronSerializer.parse("[a=>{[1,2,3],[2,3],[1,2,3]}]").apply();
        final String written = ObjmtronUISerializer.blackWhite().write(obj);
        LOG.debug("stream of lsts (black and white) => %s", written);
        assertEquals("[a=>{{2}[1,2,3],[2,3]}]", written,
                String.format("a stream's coefficient reaches an uncolored render too: %s", written));
    }

    @Test
    public void testStreamMembersRenderThroughTheUiWriter() {
        // a stream had no arm in the nested path, so a stream inside a rec fell through to the
        // plain serializer's toString(): no link tags, and a member never measured the column it
        // really sits at, which is also what left a wide member unclipped
        final Obj obj = objs(lst(uri(f("/a/b"))), lst(jnt(1), jnt(2), jnt(3), jnt(4), jnt(5), jnt(6),
                jnt(7), jnt(8), jnt(9), jnt(10), jnt(11), jnt(12)));
        final String written = ObjmtronUISerializer.single().write(obj);
        LOG.debug("stream members => %s", written);
        assertTrue(written.contains("{{link}}"),
                String.format("a uri inside a stream is tagged like any other: %s", written));
        assertTrue(written.contains("...(2 more)"),
                String.format("a member is clipped like the lst it is: %s", written));
    }
}
