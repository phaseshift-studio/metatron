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

package studio.phaseshift.metatron.isa.m.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

/**
 * The contract of the pure mtron serializer: every {@code write(obj)} is legal, complete
 * mtron, and {@code read} brings it back equal to what was written.  This is what makes the
 * output travelable — a database row, an http body, a message between spaces — without loss.
 * The display side (clipping, indentation, links) is asserted in ObjmtronUISerializerTest.
 */
public class ObjmtronSerializerPurityTest extends AbstractMetatronTest {

    @ParameterizedTest
    @CsvSource(value = {
            "noobj",
            "bool::true",
            "bool::false",
            "int{42}::7",
            "1",
            "-100",
            "real::2.12",
            "real::1.23456789012345678",        // more than 4 decimals: must survive lossless
            "real::0.30000000000000004",
            "12.25",
            "-12.35",
            "str::'a simple string'",
            "str::\"a single quote ' inside\"",
            "str::'a hundred aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'",
            "str::\"a 'quoted' word with a `backquote`\"",
            "str::'round;trip'",        // a ';' in a quoted str is data, not a statement separator
            "uri::a/b/c",
            "<http://test.uri.com>",
            "<http://test.uri.com?a=b&c=d>",
            "<a b/c>",                          // a space in the uri forces the <...> wrap
            "<+/x>",                            // a leading wildcard must be wrapped
            "<#/multi>",
            "<a.b.c>",                          // a dot must be wrapped
            "uri{2}::a/b",
            "0x0a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f3031323334353637",
            "int::T",
            "real::T",
            "uri::T",
            "[=>]",
            "rec::[a=>b,c=>d]",
            "rec{0}::[a=>b,c=>[c=>d]]",
            "rec::[a=>[b=>[c=>[d=>[e=>[f=>[g=>42]]]]]]]",
            "rec::[k00=>v00,k01=>v01,k02=>v02,k03=>v03,k04=>v04,k05=>v05,k06=>v06,k07=>v07,k08=>v08,k09=>v09,k10=>v10,k11=>v11,k12=>v12,k13=>v13,k14=>v14]",
            "rec::[op=>plus(2).mult(7),n=>3]",  // an inst leaf inside a rec
            "rec::[a=>'x',b=>uri::/p/q,c=>[1,2]]",
            "[,]",
            "[1,2,3,4,5]",
            "[<a>,<b>,<c>,<d>]",
            "[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]",
            "[a,[b,12,'abc'],[a=>b,c=>[c=>d]]]",
            "[a=>b,c=>[c=>d]]",
            "[a=>b,c=>[b=>d]]",
            "[<a>=>b,c=><d>]",
            "plus(2).mult(7)",
            "start(1).plus(2).mult(7)",
            "{1,2,3,4,5}",
            "{,}",
            "{[1,2],[3,4],[5,6]}",
            "{true,false,{1,0},{-100,12.35,-12.35}}"
    }, delimiter = '|')
    public void testWriteIsLegalMtronThatReadsBack(final String expr) {
        final Obj obj = ObjmtronSerializer.parse(expr).apply();
        final ObjmtronSerializer ser = ObjmtronSerializer.single();
        final String written = ser.write(obj);
        LOG.debug("%s ==> %s", expr, written);

        // the written text is mtron: it parses and it is not a fail
        final Obj parsed = ObjmtronSerializer.parse(written);
        assertFalse(parsed.isFail(), String.format("write produced unparseable output: %s", written));

        // ... and reading it back is lossless, value and type
        final Obj readBack = ser.read(written);
        assertFalse(readBack.isFail(), String.format("read-back failed: %s", written));
        assertEquals(obj, readBack, String.format("round-trip lost data: %s", written));
        assertEquals(obj.type(), readBack.type(), String.format("round-trip changed the type: %s", written));
    }

    @Test
    public void testVidTaggedReadsBack() {
        // a vid-tagged value (as one read from space would be) carries its vid out and back
        // (/m is the in-memory space the test boot loads, so the vid is a live address)
        final Obj obj = str("here").vid(f("/m/purity/vid"));
        final String written = ObjmtronSerializer.single().write(obj);
        LOG.debug("vid-tagged => %s", written);
        assertTrue(written.contains("@"), String.format("the vid is not in the output: %s", written));
        final Obj readBack = ObjmtronSerializer.single().read(written);
        assertFalse(readBack.isFail(), String.format("read-back failed: %s", written));
        assertEquals(obj, readBack, String.format("round-trip lost the value: %s", written));
        assertEquals(obj.vid(), readBack.vid(), String.format("round-trip lost the vid: %s", written));
    }
}
