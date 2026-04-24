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

import studio.phaseshift.metatron.isa.dcmnt.schema.storage.ObjBSONSerializer;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.web.parser.ObjHTMLSerializer;
import studio.phaseshift.metatron.isa.web.parser.ObjMarkdownSerializer;
import studio.phaseshift.metatron.isa.web.parser.ObjPlainTextSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static studio.phaseshift.metatron.isa.m.type.ObjFactory.LOG;
import static studio.phaseshift.metatron.isa.web.webInstSet.*;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Content {
    public enum ContentType {
        APPLICATION_BSON("application/bson"),
        APPLICATION_JSON("application/json"),
        APPLICATION_LD_JSON("application/ld+json"),
        MEDIA("media/"),
        MEDIA_MPEG("media/mpeg"),
        APPLICATION_OCTET_STREAM("application/octet-stream"),
        APPLICATION_ATOM_XML("application/atom+xml"),
        APPLICATION_XML("application/xml"),
        APPLICATION_MTRON("application/mtron"),
        APPLICATION_JAVASCRIPT("application/javascript"),
        TEXT_HTML("text/html"),
        TEXT_PLAIN("text/plain"),
        TEXT_CSS("text/css"),
        TEXT_MARKDOWN("text/markdown"),
        TEXT_JAVASCRIPT("text/javascript"),
        TEXT_X_SHELLSCRIPT("text/x-shellscript"),
        IMAGE_PNG("image/png"),
        IMAGE_JPEG("image/jpeg"),
        IMAGE_GIF("image/gif"),
        IMAGE_SVG("image/svg+xml"),
        IMAGE_ICO("image/x-icon"),
        APPLICATION_XHTML_XML("application/xhtml+xml");
        public final String value;

        ContentType(final String value) {
            this.value = value;
        }

        public static ContentType of(final String contentType) {
            return null == contentType ? TEXT_PLAIN : Arrays.stream(ContentType.values()).filter(ct -> (contentType.contains(ct.value))).findAny().orElse(TEXT_PLAIN);
        }

        public static ContentType fromProbe(final File file) {
            try {
                if (file.getName().contains("."))
                    return fromExtension(file.getName());
                final ContentType contentType = of(Files.probeContentType(file.toPath()));
                LOG.debug("probed content type: %s", contentType);
                return contentType == TEXT_PLAIN ? ContentType.APPLICATION_MTRON : contentType;
            } catch (final IOException e) {
                LOG.error(e);
                return TEXT_PLAIN;
            }
        }

        public static ContentType fromType(final Obj obj) {
            if (obj.type().vid().basePath().equals(HTML_TID)) return TEXT_HTML;
            if (obj.type().vid().basePath().equals(MARKDOWN_TID)) return TEXT_MARKDOWN;
            if (obj.type().vid().basePath().equals(JSON_TID)) return APPLICATION_JSON;
            return APPLICATION_MTRON;
        }

        /**
         * Determine content type from file extension when Files.probeContentType() fails
         */
        public static ContentType fromExtension(final String filename) {
            if (filename == null) return TEXT_PLAIN;
            final String lower = filename.toLowerCase();
            if (lower.endsWith(".mtron")) return APPLICATION_MTRON;
            if (lower.endsWith(".css")) return TEXT_CSS;
            if (lower.endsWith(".js")) return APPLICATION_JAVASCRIPT;
            if (lower.endsWith(".md")) return TEXT_MARKDOWN;
            if (lower.endsWith(".html") || lower.endsWith(".htm")) return TEXT_HTML;
            if (lower.endsWith(".json")) return APPLICATION_JSON;
            if (lower.endsWith(".xml")) return APPLICATION_XML;
            if (lower.endsWith(".png")) return IMAGE_PNG;
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return IMAGE_JPEG;
            if (lower.endsWith(".gif")) return IMAGE_GIF;
            if (lower.endsWith(".svg")) return IMAGE_SVG;
            if (lower.endsWith(".ico")) return IMAGE_ICO;
            if (lower.endsWith(".sh")) return TEXT_X_SHELLSCRIPT;
            if (lower.endsWith(".bash")) return TEXT_X_SHELLSCRIPT;
            if (lower.endsWith(".py")) return TEXT_X_SHELLSCRIPT;
            return TEXT_PLAIN;
        }

        public boolean isJson() {
            return this.equals(APPLICATION_JSON) || this.equals(APPLICATION_LD_JSON);
        }

        public boolean isHtml() {
            return this.equals(TEXT_HTML);
        }

        public boolean isMarkdown() {
            return this.equals(TEXT_MARKDOWN);
        }

        public boolean isMtron() {
            return this.equals(APPLICATION_MTRON);
        }

        public boolean isXml() {
            return this.equals(APPLICATION_ATOM_XML) || this.equals(APPLICATION_XHTML_XML) || this.equals(APPLICATION_XML);
        }

        public boolean isAudio() {
            return List.of(MEDIA, MEDIA_MPEG).contains(this);
        }

        public boolean isBinary() {
            return this.equals(APPLICATION_OCTET_STREAM);
        }

        public boolean isPlain() {
            return this.equals(TEXT_PLAIN);
        }

        public boolean isBSON() {
            return this.equals(APPLICATION_BSON);
        }

        public boolean isShell() {
            return this.equals(TEXT_X_SHELLSCRIPT);
        }

        public static final String VALUE = "Content-Type";

        public ObjSerializer<?> serializer() {
            if (this.isMtron()) return ObjmtronSerializer.singleNoClip();
            if (this.isJson()) return ObjSimpleJSONSerializer.single();
            if (this.isHtml()) return ObjHTMLSerializer.single();
            if (this.isMarkdown()) return ObjMarkdownSerializer.single();
            if (this.isBSON()) return ObjBSONSerializer.single();
            if (this.isShell()) return ObjPlainTextSerializer.single();
            if (this.isPlain()) return ObjPlainTextSerializer.single();
            return null;
        }

        public boolean hasSerializer() {
            return this.serializer() != null;
        }

        public Obj fromBytes(final String data) {
            return this.fromBytes(data.getBytes());
        }

        public Obj fromBytes(final byte[] data) {
            final Obj obj = Optional.ofNullable(this.serializer())
                    .map(s -> s.inputBytes(ByteBuffer.wrap(data)))
                    .orElseThrow(() -> MTronException.of("no serializer for %s", this.value));
            if (this.isHtml()) return obj.as(HTML_TYPE);
            //if(this.isJson()) return obj.as(JSON_TYPE); // TODO: need test cases
            //if(this.isBinary()) return obj.as(BYTES_TYPE);
            return obj;
        }

        public byte[] toBytes(final Obj obj) {
            return Optional.ofNullable(this.serializer())
                    .map(s -> s.outputBytes(obj).array())
                    .orElseThrow(() -> MTronException.of("no serializer for %s", this.value));

        }
    }
}
