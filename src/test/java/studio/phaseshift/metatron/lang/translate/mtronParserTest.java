/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.lang.translate;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.lang.mtron.mtronParser;
import studio.phaseshift.metatron.lang.mtron.type.NoObj;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MBool.bool;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MReal.real;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.BOOL_TID;
import static studio.phaseshift.metatron.lang.mtron.mtronParser.m_bool;


public class mtronParserTest {

    @Test
    public void testCommentParse() {
        assertEquals(NoObj.single(), mtronParser.parse("--- a comment"));
    }

    @Test
    public void testBoolParse() {
        assertEquals(BOOL_TID, m_bool().parse("true").<Obj>get().tid());
        assertEquals(bool(true), mtronParser.parse("true"));
        assertEquals(bool(false), mtronParser.parse("false"));
    }

    @Test
    public void testIntParse() {
        assertEquals(jnt(1234), mtronParser.parse("1234 "));
        Obj t = jnt(1);
        assertEquals(t, mtronParser.parse("1 "));
        assertEquals(objs(List.of(jnt(1), jnt(5))), t.append(jnt(5)));
        assertEquals(objs(List.of(jnt(1), jnt(4))), t.append(jnt(4)));
        assertEquals(objs(List.of(jnt(1), jnt(3), jnt(4), jnt(5))),
                t.append(jnt(3)).append(jnt(4)).append(jnt(5)));
        assertEquals(objs(List.of(jnt(1), jnt(3), jnt(4), jnt(5))),
                t.append(jnt(3)).append(jnt(4)).append(jnt(5)));
        //assertEquals(Int.of(10), ObjParser.parse("start(4).plus(plus(2))").apply(Int.of(4)));
        //  assertEquals(Int.of("m:nat", 1234), ObjParser.parse("m:nat[1234] "));
    }

    @Test
    public void testRealParse() {
        assertEquals(real(1234.23), mtronParser.parse("1234.23"));
    }


    @Test
    public void testStrParse() {
        assertEquals(str("abc").jvm(), mtronParser.parse("'abc'").jvm());
        assertEquals(str("aBc35 4e6").jvm(), mtronParser.parse("'aBc35 4e6'").jvm());
    }

    @Test
    public void testUriParse() {
        assertEquals(uri("http://metatron.com?a=2&b=3"), mtronParser.parse("<http://metatron.com?a=2&b=3>"));
        assertEquals(uri("http://metatron.com?a&b"), mtronParser.parse("<http://metatron.com?a&b>"));
        assertEquals(uri("http://metatron.com?a=a/b/c&b=a"), mtronParser.parse("<http://metatron.com?a=a/b/c&b=a>"));
        assertThrows(MTronException.class, () -> mtronParser.parse("/metatron.com?a&b")); // TODO: this will be needed moving forward with monad distribution and uri authorities
        assertEquals(uri("metatron/com?a&b"), mtronParser.parse("metatron/com?a&b"));
    }
}
