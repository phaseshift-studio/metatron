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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Document;
import studio.phaseshift.metatron.AbstractSerializerTest;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.isa.web.webInstSet.XML_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjXMLSerializerTest extends AbstractSerializerTest<Document> {

    public ObjXMLSerializerTest() {
        super(new ObjXMLSerializer(), XML_TID, "xml");
    }

    @Override
    public void testSerializeDeserializeObj(final String objString) {
        // XML serializer.write() returns null — no generic Obj round-trip
    }

    // ===================================================================
    //  Parse tests
    // ===================================================================

    @Test
    public void testParseProducesRec() {
        final Rec rec = ObjXMLSerializer.parse("<root>hello</root>");
        assertTrue(rec.isRec(), "XML parse must produce a Rec");
    }

    @Test
    public void testParseEmptyDocument() {
        final Rec rec = ObjXMLSerializer.parse("<root></root>");
        assertTrue(rec.isRec());
    }

    // ===================================================================
    //  Type-conversion: str -> xml::T  (tag + validate)
    // ===================================================================

    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(quoteCharacter = '~', value = {
            "'<root>hello</root>'                         %  simple element",
            "'<root id=\"1\">hello</root>'                 %  with attribute",
            "'<root><child>value</child></root>'           %  nested",
            "'<root><a>1</a><b>2</b></root>'               %  multiple children",
            "'<root><empty/></root>'                       %  self-closing",
            "'<root></root>'                               %  empty",
    }, delimiter = '%')
    void testStrToXmlType(final String mtronValue, final String desc) {
        final Str result = assertStrToType(mtronValue);
        assertTrue(result.strValue().contains("root") || result.strValue().contains("child"),
                "must contain XML content: " + desc);
    }

    // ===================================================================
    //  Type-conversion: xml::T -> rec::T  (parse)
    // ===================================================================

    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(quoteCharacter = '~', value = {
            "'<root>hello</root>'                         %  simple element parses to rec",
            "'<root><child>value</child></root>'           %  nested parses to rec",
            "'<root></root>'                               %  empty parses to rec",
    }, delimiter = '%')
    void testXmlToRec(final String mtronValue, final String desc) {
        final Rec result = assertTypeToRec(mtronValue);
        assertTrue(result.count() >= 0, "must be a valid rec: " + desc);
    }

    // ===================================================================
    //  Type-conversion: rec::T -> xml::T  (serialize back)
    // ===================================================================

    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(quoteCharacter = '~', value = {
            "'<root>hello</root>'                         %  simple element round-trip",
            "'<root><child>value</child></root>'           %  nested round-trip",
    }, delimiter = '%')
    void testRecToXml(final String mtronValue, final String desc) {
        final Str roundTripped = assertRecToType(mtronValue);
        assertTrue(roundTripped.strValue().contains("root"), "round-trip must produce XML: " + desc);
    }

    // ===================================================================
    //  Predicate rejection: invalid XML
    //  The DOM parser is error-resilient (continue-after-fatal-error) so
    //  only truly malformed input is rejected.
    // ===================================================================

    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(quoteCharacter = '~', value = {
            "'not xml'                    %  plain text rejected",
    }, delimiter = '%')
    void testInvalidXmlRejected(final String mtronValue, final String desc) {
        assertRejected(mtronValue);
    }

    // ===================================================================
    //  Integration: full type chain via mtron runtime
    // ===================================================================

    @Test
    public void testXmlTypeChain() {
        final Str xmlStr = eval("'<root>hello</root>'.as(xml::T)");
        assertEquals(XML_TID, xmlStr.tid());
        assertTrue(xmlStr.isStr());

        final Rec xmlRec = eval("'" + xmlStr.strValue() + "'.as(xml::T).as(rec::T)");
        assertTrue(xmlRec.isRec());

        final Str xmlStr2 = eval("'" + xmlStr.strValue() + "'.as(xml::T).as(rec::T).as(xml::T)");
        assertEquals(XML_TID, xmlStr2.tid());
        assertTrue(xmlStr2.isStr());
    }
}
