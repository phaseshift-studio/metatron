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
import static studio.phaseshift.metatron.lang.obj.SObj.NoObj;


public class MParserTest {

    @Test
    public void testCommentParse() {
        assertFalse(MParser.parse("# a comment").matched);
        assertEquals(NoObj.of(), MParser.parse("# a comment").resultValue);
    }

    @Test
    public void testBoolParse() {
        assertEquals("m:bool", MParser.parse("true").resultValue.type().toString());
        assertTrue(MParser.parse("true").matched);
        assertFalse(MParser.parse("233true").matched);
        assertEquals(Bool.of(true), MParser.parse("true").resultValue);
        assertEquals(Bool.of(false), MParser.parse("false").resultValue);
    }

    @Test
    public void testIntParse() {
        assertTrue(MParser.parse("1234  ").matched);
        assertEquals(Int.of(1234), MParser.parse("1234 ").resultValue);
        assertEquals(NoObj.of(), MParser.parse("abc").resultValue);
    }

    @Test
    public void testRealParse() {
        assertTrue(MParser.parse("1234.23").matched);
        //assertFalse(MParser.parse("1235").matched);
        assertEquals(Real.of(1234.23), MParser.parse("1234.23").resultValue);
        assertEquals(NoObj.of(), MParser.parse("abc").resultValue);
    }

    @Test
    public void testStrParse() {
        assertTrue(MParser.parse("'abc'").matched);
        assertEquals(Str.of("abc"), MParser.parse("'abc'").resultValue);
        assertEquals(Str.of("aBc35 4e6"), MParser.parse("'aBc35 4e6'").resultValue);
        assertFalse(MParser.parse("\"abc\"").matched);
    }
}
