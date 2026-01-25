/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.isa.m;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.m.type.NoObj;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Bytes;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.util.MTronException;

import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.isa.m.mInstSet.BOOL_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.BYTES_TID;
import static studio.phaseshift.metatron.isa.m.parser.mParser.m_bool;
import static studio.phaseshift.metatron.isa.m.parser.mParser.m_bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;


public class mParserTest {

    @Test
    public void testCommentParse() {
        assertEquals(NoObj.noobj(), mParser.parse("[-- a comment"));
        assertEquals(NoObj.noobj(), mParser.parse("[-- a comment --]"));
        // assertThrows(Exception.class, () -> mParser.parse("[-- a comment\n\r\n\r --]"));
        assertThrows(Exception.class, () -> mParser.parse("-- a comment\n\n --"));
        assertEquals(NoObj.noobj(), mParser.parse("[--- a comment\n\n ---]"));
    }
    
    @Test
    public void testBoolParse() {
        assertEquals(BOOL_TID, m_bool().parse("true").<Obj>get().tid());
        assertEquals(bool(true), mParser.parse("true"));
        assertEquals(bool(false), mParser.parse("false"));
    }

    @Test
    public void testBytesParse() {
        assertEquals(BYTES_TID, m_bytes().parse("0xabc123").<Bytes>get().tid());
        assertArrayEquals(HexFormat.of().parseHex("abc123"), m_bytes().parse("0xabc123").<Bytes>get().jvm().array());
    }
    

    @Test
    public void testIntParse() {
        assertEquals(jnt(1234), mParser.parse("1234 "));
        Obj t = jnt(1);
        assertEquals(t, mParser.parse("1 "));
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
        assertEquals(real(1234.23), mParser.parse("1234.23"));
    }


    @Test
    public void testStrParse() {
        assertEquals(str("abc").jvm(), mParser.parse("'abc'").jvm());
        assertEquals(str("aBc35 4e6").jvm(), mParser.parse("'aBc35 4e6'").jvm());
    }

    @Test
    public void testUriParse() {
        assertEquals(uri("http://metatron.com?a=2&b=3"), mParser.parse("<http://metatron.com?a=2&b=3>"));
        assertEquals(uri("http://metatron.com?a&b"), mParser.parse("<http://metatron.com?a&b>"));
        assertEquals(uri("http://metatron.com?a=a/b/c&b=a"), mParser.parse("<http://metatron.com?a=a/b/c&b=a>"));
        assertThrows(MTronException.class, () -> mParser.parse("/metatron.com?a&b")); // TODO: this will be needed moving forward with monad distribution and uri authorities
        assertEquals(uri("metatron/com?a&b"), mParser.parse("metatron/com?a&b"));
    }

    @Test
    public void testRelParse() {
        assertEquals(rel(uri("a"),uri("b")).jvm(), mParser.parse("a => b").jvm());
        assertEquals(rel(jnt(1),uri("b")).jvm(), mParser.parse("1 => b").jvm());
        assertEquals(rel(jnt(1),real(4.3)).jvm(), mParser.parse("1 => 4.3").jvm());
    }
}
