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

package studio.phaseshift.metatron.isa.web.type;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.dcmnt.schema.storage.ObjBSONSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.web.parser.ObjHTMLSerializer;

import static org.junit.jupiter.api.Assertions.*;

class MIMETest extends AbstractMetatronTest {
    @Test
    void testOf() {
        assertEquals(MIME.MIMEType.APPLICATION_JSON, MIME.MIMEType.of("application/json"));
        assertEquals(MIME.MIMEType.TEXT_HTML, MIME.MIMEType.of("text/html; charset=UTF-8"));
        assertNull(MIME.MIMEType.of(null));
        assertNull(MIME.MIMEType.of("unknown/type"));
    }

    @Test
    void testFromExtension() {
        assertEquals(MIME.MIMEType.TEXT_CSS, MIME.MIMEType.fromExtension("style.css", null));
        assertEquals(MIME.MIMEType.APPLICATION_JAVASCRIPT, MIME.MIMEType.fromExtension("script.js", null));
        assertEquals(MIME.MIMEType.TEXT_HTML, MIME.MIMEType.fromExtension("index.html", null));
        assertEquals(MIME.MIMEType.APPLICATION_JSON, MIME.MIMEType.fromExtension("data.json", null));
        assertEquals(MIME.MIMEType.IMAGE_PNG, MIME.MIMEType.fromExtension("image.png", null));
        assertEquals(MIME.MIMEType.IMAGE_JPEG, MIME.MIMEType.fromExtension("photo.jpeg", null));
        assertEquals(MIME.MIMEType.APPLICATION_MTRON, MIME.MIMEType.fromExtension("file.mtron", null));
        assertEquals(MIME.MIMEType.TEXT_PLAIN, MIME.MIMEType.fromExtension("file.unknown", MIME.MIMEType.TEXT_PLAIN));
        assertNull(MIME.MIMEType.fromExtension("file.unknown", null));
        assertNull(MIME.MIMEType.fromExtension(null, null));
    }

    @Test
    void testIsJson() {
        assertTrue(MIME.MIMEType.APPLICATION_JSON.isJson());
        assertTrue(MIME.MIMEType.APPLICATION_LD_JSON.isJson());
        assertFalse(MIME.MIMEType.TEXT_HTML.isJson());
    }

    @Test
    void testIsHtml() {
        assertTrue(MIME.MIMEType.TEXT_HTML.isHtml());
        assertFalse(MIME.MIMEType.APPLICATION_JSON.isHtml());
    }

    @Test
    void testIsMtron() {
        assertTrue(MIME.MIMEType.APPLICATION_MTRON.isMtron());
        assertFalse(MIME.MIMEType.APPLICATION_JSON.isMtron());
    }

    @Test
    void testIsXml() {
        assertTrue(MIME.MIMEType.APPLICATION_XML.isXml());
        assertTrue(MIME.MIMEType.APPLICATION_ATOM_XML.isXml());
        assertTrue(MIME.MIMEType.APPLICATION_XHTML_XML.isXml());
        assertFalse(MIME.MIMEType.APPLICATION_JSON.isXml());
    }

    @Test
    void testIsAudio() {
        assertTrue(MIME.MIMEType.MEDIA.isAudio());
        assertTrue(MIME.MIMEType.MEDIA_MPEG.isAudio());
        assertFalse(MIME.MIMEType.APPLICATION_JSON.isAudio());
    }

    @Test
    void testIsBinary() {
        assertTrue(MIME.MIMEType.APPLICATION_OCTET_STREAM.isBinary());
        assertFalse(MIME.MIMEType.APPLICATION_JSON.isBinary());
    }

    @Test
    void testIsPlain() {
        assertTrue(MIME.MIMEType.TEXT_PLAIN.isPlain());
        assertFalse(MIME.MIMEType.APPLICATION_JSON.isPlain());
    }

    @Test
    void testSerializer() {
        assertTrue(MIME.MIMEType.APPLICATION_MTRON.serializer() instanceof ObjmtronSerializer);
        assertTrue(MIME.MIMEType.APPLICATION_JSON.serializer() instanceof ObjSimpleJSONSerializer);
        assertTrue(MIME.MIMEType.TEXT_HTML.serializer() instanceof ObjHTMLSerializer);
        assertTrue(MIME.MIMEType.APPLICATION_BSON.serializer() instanceof ObjBSONSerializer);
        // assertTrue(Content.ContentType.TEXT_PLAIN.serializer() instanceof ObjSimpleJSONSerializer);
    }
}
