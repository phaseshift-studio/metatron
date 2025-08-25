/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import static studio.phaseshift.metatron.lang.obj.SObj.Bool;
import static studio.phaseshift.metatron.lang.obj.SObj.Str;
import static studio.phaseshift.metatron.lang.obj.SObj.Real;
import static studio.phaseshift.metatron.lang.obj.SObj.Int;
import static studio.phaseshift.metatron.lang.obj.SObj.Uri;
import static studio.phaseshift.metatron.lang.obj.SObj.NoObj;


public class MParserTest {

    @Test
    public void testCommentParse() {
        assertEquals(NoObj.of(), MParser.parse("# a comment"));
    }

    @Test
    public void testBoolParse() {
        assertEquals("m:bool", MParser.parse("true").type().toString());
        assertEquals(Bool.of(true), MParser.parse("true"));
        assertEquals(Bool.of(false), MParser.parse("false"));
    }

    @Test
    public void testIntParse() {
        assertEquals(Int.of(1234), MParser.parse("1234 "));
        assertEquals(Int.of("m:nat", 1234), MParser.parse("m:nat[1234] "));
    }

    @Test
    public void testRealParse() {
        assertEquals(Real.of(1234.23), MParser.parse("1234.23"));
    }

    @Test
    public void testStrParse() {
        assertEquals(Str.of("abc"), MParser.parse("'abc'"));
        assertEquals(Str.of("aBc35 4e6"), MParser.parse("'aBc35 4e6'"));
    }

    @Test
    public void testUriParse() {
        assertEquals(Uri.of("http://metatron.com?a=2&b=3"), MParser.parse("http://metatron.com?a=2&b=3"));
    }
}
