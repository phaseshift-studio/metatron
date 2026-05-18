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
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Bytes;
import studio.phaseshift.metatron.isa.m.type.NoObj;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.start_;
import static studio.phaseshift.metatron.isa.m.parser.mParser.m_bool;
import static studio.phaseshift.metatron.isa.m.parser.mParser.m_bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instB;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;


public class mParserTest extends AbstractMetatronTest {

    @Test
    public void testCommentParse() {
        assertEquals(NoObj.noobj(), ObjmtronSerializer.parse("[-- a comment --]"));
        assertEquals(start_(jnt(1)).plus_(jnt(2)).end_().map_(jnt(4)), ObjmtronSerializer.parse("1+2;map(4)"));
        assertEquals(start_(jnt(1)).plus_(jnt(2)), ObjmtronSerializer.parse("[-- a comment --]\n\r1+2"));
        assertEquals(NoObj.noobj(), ObjmtronSerializer.parse("[-- a comment --]"));
        assertThrows(Exception.class, () -> ObjmtronSerializer.parse("[-- a comment\n\r\n\r --]1+2"));
        assertThrows(Exception.class, () -> ObjmtronSerializer.parse("-- a comment\n\n --"));
        assertEquals(NoObj.noobj(), ObjmtronSerializer.parse("[-- a comment \n\n --]\n\n"));
    }

    @Test
    public void testBoolParse() {
        assertEquals(BOOL_TID, m_bool().parse("true").<Obj>get().tid());
        assertEquals(bool(true), ObjmtronSerializer.parse("true"));
        assertEquals(bool(false), ObjmtronSerializer.parse("false"));
    }

    @Test
    public void testBytesParse() {
        assertEquals(BYTES_TID, m_bytes().parse("0xabc123").<Bytes>get().tid());
        assertArrayEquals(HexFormat.of().parseHex("abc123"), m_bytes().parse("0xabc123").<Bytes>get().jvm().array());
    }


    @Test
    public void testIntParse() {
        assertEquals(jnt(1234), ObjmtronSerializer.parse("1234 "));
        Obj t = jnt(1);
        assertEquals(t, ObjmtronSerializer.parse("1 "));
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
        assertEquals(real(1234.23), ObjmtronSerializer.parse("1234.23"));
    }


    @Test
    public void testStrParse() {
        assertEquals(str("abc").jvm(), ObjmtronSerializer.parse("'abc'").jvm());
        assertEquals(str("aBc35 4e6").jvm(), ObjmtronSerializer.parse("'aBc35 4e6'").jvm());
    }

    @Test
    public void testUriParse() {
        assertEquals(
                f("http://metatron.com?a=2&b=3"),
                uri("http://metatron.com?a=2&b=3").uriValue());
        assertEquals(
                uri("http://metatron.com?a=2&b=3").uriValue(),
                ObjmtronSerializer.parse("<http://metatron.com?a=2&b=3>").uriValue());
        assertEquals(
                f("http://metatron.com?a=2&b=3"),
                ObjmtronSerializer.parse("<http://metatron.com?a=2&b=3>").uriValue());
        for (fURI x : List.of(
                ObjmtronSerializer.parse("<http://metatron.com?a=2&b=3>").uriValue(),
                f("http://metatron.com?a=2&b=3"),
                uri("http://metatron.com?a=2&b=3").uriValue())) {
            assertEquals("http", x.scheme());
            assertEquals("metatron.com", x.host());
            assertEquals(-1, x.port());
            assertEquals(List.of(), x.path());
            assertEquals(cInt.ONE(), x.c());
            assertEquals(List.of(), x.poly());
            assertEquals(Map.of("a", "2", "b", "3"), x.qMap());
        }
        /// ///////////////////////////////////
        assertEquals(uri("http://metatron.com?a&b").uriValue(), ObjmtronSerializer.parse("<http://metatron.com?a&b>").uriValue());
        assertEquals(uri("http://metatron.com?a&b"), ObjmtronSerializer.parse("<http://metatron.com?a&b>"));
        assertEquals(uri("http://metatron.com?a=a/b/c&b=a"), ObjmtronSerializer.parse("<http://metatron.com?a=a/b/c&b=a>"));
        assertThrows(MTronException.class, () -> ObjmtronSerializer.parse("/metatron.com?a&b")); // TODO: this will be needed moving forward with monad distribution and uri authorities
        assertEquals(uri("metatron/com?a&b"), ObjmtronSerializer.parse("metatron/com?a&b"));
    }

    @Test
    public void testRelParse() {
        assertEquals(rel(uri("a"), uri("b")).jvm(), ObjmtronSerializer.parse("a => b").jvm());
        assertEquals(rel(jnt(1), uri("b")).jvm(), ObjmtronSerializer.parse("1 => b").jvm());
        assertEquals(rel(jnt(1), real(4.3)).jvm(), ObjmtronSerializer.parse("1 => 4.3").jvm());
    }

    @Test
    public void testInstParse() {
        assertEquals(instB(PLUS_INST_TID, lst(jnt(1), jnt(2))), ObjmtronSerializer.parse("plus(1,2)"));
        assertEquals(instB(PLUS_INST_TID, lst()), ObjmtronSerializer.parse("plus()"));
        assertTrue(ObjmtronSerializer.parse("plus()").asInst().jvm().get0().isEmpty());
        assertEquals(0, ObjmtronSerializer.parse("plus()").asInst().jvm().get0().count());
    }

    @Test
    public void testSugar() {
        assertEquals(lst(jnt(1), lst(jnt(1), lst(jnt(1)))), ObjmtronSerializer.parse("1-<[_,-<[_,-<[_]]]").apply());
    }
}
