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

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MInst;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.AUTO_AT_INST_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
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

    @Test
    public void testRealDisplayQuantizesWhilePlainIsLossless() {
        final Obj obj = ObjmtronSerializer.parse("real::1.234567890").apply();
        final String plain = ObjmtronSerializer.single().write(obj);
        final String ui = ObjmtronUISerializer.single().write(obj);
        LOG.debug("plain => %s   ui => %s", plain, ui);
        assertTrue(plain.contains("1.23456789"), String.format("the plain serializer keeps the exact value: %s", plain));
        assertTrue(ui.contains("1.2346"), String.format("the UI serializer quantizes to the display limit: %s", ui));
        assertFalse(ui.contains("1.23456789"), ui);
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
        final int firstLine = written.indexOf('\n');
        assertTrue(firstLine > 0, String.format("a long rec through the clip path must nest onto its own lines: %s", written));
        assertFalse(written.substring(0, firstLine).contains("=>"), String.format("no entry may sit on the opening line: %s", written));
        final int lastLine = written.lastIndexOf('\n');
        assertTrue(written.substring(lastLine + 1).contains("=>") || written.substring(lastLine + 1).contains("]"),
                String.format("the closing bracket follows the last entry: %s", written));
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
        assertTrue(written.contains("\n"), String.format("a rec of type values must nest onto its own lines: %s", written));
        assertFalse(written.substring(0, written.indexOf('\n')).contains("=>"),
                String.format("no entry may sit on the opening line: %s", written));
    }
}
