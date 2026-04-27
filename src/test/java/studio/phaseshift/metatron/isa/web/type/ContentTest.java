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

class ContentTest extends AbstractMetatronTest {
    @Test
    void testOf() {
        assertEquals(Content.ContentType.APPLICATION_JSON, Content.ContentType.of("application/json"));
        assertEquals(Content.ContentType.TEXT_HTML, Content.ContentType.of("text/html; charset=UTF-8"));
        assertNull(Content.ContentType.of(null));
        assertNull(Content.ContentType.of("unknown/type"));
    }

    @Test
    void testFromExtension() {
        assertEquals(Content.ContentType.TEXT_CSS, Content.ContentType.fromExtension("style.css", null));
        assertEquals(Content.ContentType.APPLICATION_JAVASCRIPT, Content.ContentType.fromExtension("script.js", null));
        assertEquals(Content.ContentType.TEXT_HTML, Content.ContentType.fromExtension("index.html", null));
        assertEquals(Content.ContentType.APPLICATION_JSON, Content.ContentType.fromExtension("data.json", null));
        assertEquals(Content.ContentType.IMAGE_PNG, Content.ContentType.fromExtension("image.png", null));
        assertEquals(Content.ContentType.IMAGE_JPEG, Content.ContentType.fromExtension("photo.jpeg", null));
        assertEquals(Content.ContentType.APPLICATION_MTRON, Content.ContentType.fromExtension("file.mtron", null));
        assertEquals(Content.ContentType.TEXT_PLAIN, Content.ContentType.fromExtension("file.unknown", Content.ContentType.TEXT_PLAIN));
        assertNull(Content.ContentType.fromExtension("file.unknown", null));
        assertNull(Content.ContentType.fromExtension(null, null));
    }

    @Test
    void testIsJson() {
        assertTrue(Content.ContentType.APPLICATION_JSON.isJson());
        assertTrue(Content.ContentType.APPLICATION_LD_JSON.isJson());
        assertFalse(Content.ContentType.TEXT_HTML.isJson());
    }

    @Test
    void testIsHtml() {
        assertTrue(Content.ContentType.TEXT_HTML.isHtml());
        assertFalse(Content.ContentType.APPLICATION_JSON.isHtml());
    }

    @Test
    void testIsMtron() {
        assertTrue(Content.ContentType.APPLICATION_MTRON.isMtron());
        assertFalse(Content.ContentType.APPLICATION_JSON.isMtron());
    }

    @Test
    void testIsXml() {
        assertTrue(Content.ContentType.APPLICATION_XML.isXml());
        assertTrue(Content.ContentType.APPLICATION_ATOM_XML.isXml());
        assertTrue(Content.ContentType.APPLICATION_XHTML_XML.isXml());
        assertFalse(Content.ContentType.APPLICATION_JSON.isXml());
    }

    @Test
    void testIsAudio() {
        assertTrue(Content.ContentType.MEDIA.isAudio());
        assertTrue(Content.ContentType.MEDIA_MPEG.isAudio());
        assertFalse(Content.ContentType.APPLICATION_JSON.isAudio());
    }

    @Test
    void testIsBinary() {
        assertTrue(Content.ContentType.APPLICATION_OCTET_STREAM.isBinary());
        assertFalse(Content.ContentType.APPLICATION_JSON.isBinary());
    }

    @Test
    void testIsPlain() {
        assertTrue(Content.ContentType.TEXT_PLAIN.isPlain());
        assertFalse(Content.ContentType.APPLICATION_JSON.isPlain());
    }

    @Test
    void testSerializer() {
        assertTrue(Content.ContentType.APPLICATION_MTRON.serializer() instanceof ObjmtronSerializer);
        assertTrue(Content.ContentType.APPLICATION_JSON.serializer() instanceof ObjSimpleJSONSerializer);
        assertTrue(Content.ContentType.TEXT_HTML.serializer() instanceof ObjHTMLSerializer);
        assertTrue(Content.ContentType.APPLICATION_BSON.serializer() instanceof ObjBSONSerializer);
        // assertTrue(Content.ContentType.TEXT_PLAIN.serializer() instanceof ObjSimpleJSONSerializer);
    }
}
