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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.inst.SInst;
import studio.phaseshift.metatron.lang.obj.BObj;
import studio.phaseshift.metatron.lang.obj.SObj.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.MetatronTest.assertMEquals;
import static studio.phaseshift.metatron.lang.parse.ObjParser.m_bool;


public class ObjParserTest {
    static {
        SInst.load();
    }

    @Test
    @Disabled
    public void testCommentParse() {
        assertEquals(NoObj.of(), ObjParser.parse("# a comment"));
    }

    @Test
    public void testBoolParse() {
        assertEquals(fURI.of("bool"), m_bool().parse("true").<BObj.Obj>get().tid());
        assertEquals(new Bool(true), ObjParser.parse("true"));
        assertEquals(new Bool(false), ObjParser.parse("false"));
    }

    @Test
    public void testIntParse() {
        assertEquals(Int.of(1234), ObjParser.parse("1234 "));
        assertMEquals(4, ObjParser.compute("plus(plus(2))", Int.of(1)));
        //  assertEquals(Int.of("m:nat", 1234), ObjParser.parse("m:nat[1234] "));
    }

    @Test
    public void testRealParse() {
        assertEquals(Real.of(1234.23), ObjParser.parse("1234.23"));
    }

    @Test
    @Disabled
    public void testStrParse() {
        assertEquals(Str.of("abc"), ObjParser.parse("'abc'"));
        assertEquals(Str.of("aBc35 4e6"), ObjParser.parse("'aBc35 4e6'"));
    }

    @Test
    public void testUriParse() {
        // assertEquals(new Uri("http://metatron.com?a=2&b=3"), ObjParser.parse("http://metatron.com?a=2&b=3"));
        assertEquals(new Uri("http://metatron.com?a&b"), ObjParser.parse("http://metatron.com?a&b"));
    }
}
