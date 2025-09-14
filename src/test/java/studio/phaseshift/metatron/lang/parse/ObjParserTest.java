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
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.mtron.*;
import studio.phaseshift.metatron.util.MTronException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class ObjParserTest {

    @Test
    public void testCommentParse() {
        assertEquals(NoObj.single(), ObjParser.parse("--- a comment"));
    }

    @Test
    public void testBoolParse() {
        // assertEquals(MBool.TID, m_bool().parse("true").<Obj>get().tid());
        assertEquals(MBool.of(true), ObjParser.parse("true"));
        assertEquals(MBool.of(false), ObjParser.parse("false"));
    }

    @Test
    public void testIntParse() {
        assertEquals(MInt.of(1234), ObjParser.parse("1234 "));
        //assertEquals(Int.of(10), ObjParser.parse("start(4).plus(plus(2))").apply(Int.of(4)));
        //  assertEquals(Int.of("m:nat", 1234), ObjParser.parse("m:nat[1234] "));
    }

    @Test
    public void testRealParse() {
        assertEquals(MReal.of(1234.23), ObjParser.parse("1234.23"));
    }


    @Test
    public void testStrParse() {
        assertEquals(MStr.of("abc").value(), ObjParser.parse("'abc'").value());
        assertEquals(MStr.of("aBc35 4e6").value(), ObjParser.parse("'aBc35 4e6'").value());
    }

    @Test
    public void testUriParse() {
        assertEquals(MUri.of("http://metatron.com?a=2&b=3"), ObjParser.parse("<http://metatron.com?a=2&b=3>"));
        assertEquals(MUri.of("http://metatron.com?a&b"), ObjParser.parse("<http://metatron.com?a&b>"));
        assertThrows(MTronException.class, () -> ObjParser.parse("/metatron.com?a&b"));
        assertEquals(MUri.of("metatron/com?a&b"), ObjParser.parse("metatron/com?a&b"));
    }
}
