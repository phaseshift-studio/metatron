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

package studio.phaseshift.metatron.isa.web.parser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjXSVSerializerTest extends AbstractMetatronTest {

    @BeforeAll
    public static void importWeb() {
        InstSet.importInstSet(f("/m/web"));
    }

    private static ObjXSVSerializer serializer(final String delimiter, final boolean header) {
        return ObjXSVSerializer.of(rec(uri("delimiter"), str(delimiter), uri("header"), bool(header)), null);
    }

    // ===================================================================
    //  Read: no header row → lst[lst]::T of parsed column values
    // ===================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(quoteCharacter = '~', delimiter = '%', value = {
            "true,233 % [[true,233]] % booleans and ints",
            "2344.345,12.25 % [[2344.345,12.25]] % reals",
            "'hello',<http://a.com> % [['hello',<http://a.com>]] % quoted str and wrapped uri",
            "a/b/c,false % [[a/b/c,false]] % bare token parses as uri, false parses as bool",
    })
    public void testNoHeaderComma(final String csv, final String expected, final String desc) {
        assertEquals(ObjmtronSerializer.parse(expected), new ObjXSVSerializer().read(csv), desc);
    }

    @Test
    public void testNoHeaderRows() {
        final Obj result = new ObjXSVSerializer().read("1,2\n3,4");
        assertEquals(ObjmtronSerializer.parse("[[1,2],[3,4]]"), result);
    }

    @Test
    public void testNoHeaderSingleInstance() {
        assertEquals(ObjmtronSerializer.parse("[[1,2]]"), ObjXSVSerializer.single().read("1,2"));
    }

    @Test
    public void testEmptyCellNoHeader() {
        final Obj result = new ObjXSVSerializer().read("true,,233");
        assertEquals(ObjmtronSerializer.parse("[[true,noobj,233]]"), result);
    }

    // ===================================================================
    //  Read: header row → lst[rec]::T with uri::T keys
    // ===================================================================

    @Test
    public void testHeaderRows() {
        final Obj result = serializer(",", true).read("name,age\nmarko,42");
        assertEquals(ObjmtronSerializer.parse("[[name=><marko>,age=>42]]"), result);
    }

    @Test
    public void testTabDelimitedHeader() {
        final Obj result = serializer("\t", true).read("name\tage\nmarko\t42");
        assertEquals(ObjmtronSerializer.parse("[[name=><marko>,age=>42]]"), result);
    }

    @Test
    public void testEmptyCellHeader() {
        // missing column value → noobj → key dropped from the rec (noobj is deletion)
        final Obj result = serializer(",", true).read("a,b\n1,");
        assertEquals(ObjmtronSerializer.parse("[[a=>1]]"), result);
    }

    // ===================================================================
    //  Write: round-trip back to the delimiter-separated form
    // ===================================================================

    @Test
    public void testWriteNoHeaderRoundTrip() {
        final ObjXSVSerializer s = new ObjXSVSerializer();
        assertEquals("1,2\n3,4", s.write(s.read("1,2\n3,4")));
    }

    @Test
    public void testWriteHeaderRoundTrip() {
        final ObjXSVSerializer s = serializer(",", true);
        assertEquals("name,age\nmarko,42", s.write(s.read("name,age\nmarko,42")));
    }

    @Test
    public void testWriteLstOfRecsNoHeader() {
        final ObjXSVSerializer s = new ObjXSVSerializer();
        assertEquals("1,2\n3,4", s.write(ObjmtronSerializer.parse("[[a=>1,b=>2],[a=>3,b=>4]]")));
    }

    @Test
    public void testWriteTabDelimited() {
        final ObjXSVSerializer s = serializer("\t", true);
        assertEquals("name\tage\nmarko\t42", s.write(s.read("name\tage\nmarko\t42")));
    }

    // ===================================================================
    //  webInstSet wiring: obj_xsv::[delimiter=>...,header=>...] constructs
    // ===================================================================

    @Test
    public void testMtronConstruction() {
        // the obj_xsv::T type is registered in webInstSet: construction re-tags the config rec
        final Obj spec = ObjmtronSerializer.parse("obj_xsv::[delimiter=>';',header=>true]").apply();
        assertEquals(f("/m/web/serializer/obj_xsv"), spec.tid());
    }

    @Test
    public void testAsInst() {
        // '...'.as(xsv::T) re-tags a str through the serializer
        final Obj result = ObjmtronSerializer.parse("'name,age\nmarko,42'.as(xsv::T)").apply();
        assertTrue(result.isStr(), "must produce a str");
        assertEquals(f("/m/web/mime/xsv"), result.tid(), "must be tagged as xsv::T");
        assertEquals("name,age\nmarko,42", result.strValue(), "normalized value must round-trip");
    }
}
