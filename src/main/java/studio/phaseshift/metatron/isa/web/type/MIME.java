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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.dcmnt.schema.storage.ObjBSONSerializer;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.io.type.ObjJavaSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.web.parser.ObjHTMLSerializer;
import studio.phaseshift.metatron.isa.web.parser.ObjMarkdownSerializer;
import studio.phaseshift.metatron.isa.web.parser.ObjPlainTextSerializer;
import studio.phaseshift.metatron.isa.web.parser.ObjXMLSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static studio.phaseshift.metatron.isa.m.type.ObjFactory.LOG;
import static studio.phaseshift.metatron.isa.web.webInstSet.*;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MIME {
    public enum MIMEType {
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
        TEXT_JAVA("text/x-java"),
        TEXT_PYTHON("text/x-python"),
        IMAGE_PNG("image/png"),
        IMAGE_JPEG("image/jpeg"),
        IMAGE_GIF("image/gif"),
        IMAGE_SVG("image/svg+xml"),
        IMAGE_ICO("image/x-icon"),
        APPLICATION_XHTML_XML("application/xhtml+xml");
        public final String value;

        MIMEType(final String value) {
            this.value = value;
        }

        /**
         * Returns true if this content type should be transmitted as a WebSocket text frame
         * rather than a binary frame (i.e. it is human-readable UTF-8 text).
         */
        private static final Set<MIMEType> TEXT_MIMES =
                Set.of(APPLICATION_JSON, APPLICATION_LD_JSON, APPLICATION_MTRON,
                        APPLICATION_JAVASCRIPT, APPLICATION_XML, APPLICATION_XHTML_XML, APPLICATION_ATOM_XML);

        public boolean isText() {
            return this.value.startsWith("text/") || TEXT_MIMES.contains(this);
        }

        public static MIMEType of(final String contentType) {
            if (null == contentType) return null;
            final String normalized = contentType.contains(";") ? contentType.substring(0, contentType.indexOf(';')).trim() : contentType;
            return Arrays.stream(MIMEType.values()).filter(ct -> (normalized.equalsIgnoreCase(ct.value))).findAny().orElse(null);
        }

        public static MIMEType of(final String contentType, final MIMEType defaultType) {
            final MIMEType mimeType = MIMEType.of(contentType);
            return null == mimeType ? defaultType : mimeType;
        }

        public static MIMEType fromProbe(final File file, final MIMEType defaultType) {
            try {
                final MIMEType contentType = of(Files.probeContentType(file.toPath()));
                LOG.debug("probed content type: %s", contentType);
                return contentType == null ? defaultType : contentType;
            } catch (final IOException e) {
                LOG.error("using default type %s as file probe failed: %s", defaultType, e);
                return defaultType;
            }
        }

        /**
         * determine the content type from the obj type. e.g. json::T, html::T, markdown::T.
         * defaults to application/mtron.
         */
        public static MIMEType fromType(final Obj obj, final MIMEType defaultType) {
            final fURI basePath = Obj.Helper.specificTypeId(obj).basePath();
            if (basePath.equals(HTML_TID)) return TEXT_HTML;
            if (basePath.equals(MARKDOWN_TID)) return TEXT_MARKDOWN;
            if (basePath.equals(JSON_TID)) return APPLICATION_JSON;
            if (basePath.equals(XML_TID)) return APPLICATION_XML;
            if (basePath.equals(CSS_TID)) return TEXT_CSS;
            if (basePath.equals(JAVA_TID)) return TEXT_JAVA;
            return defaultType;
        }

        /**
         * determine the content type from a file extension
         */
        public static MIMEType fromExtension(final String filename, final MIMEType defaultType) {
            if (filename == null) return defaultType;
            final String lower = filename.toLowerCase();
            final String extension = (lower.contains(".") ? lower.substring(lower.lastIndexOf('.') + 1) : "").trim();
            if (extension.isEmpty() || extension.equals("mtron")) return APPLICATION_MTRON;
            if (extension.equals("txt")) return TEXT_PLAIN;
            if (extension.equals("css")) return TEXT_CSS;
            if (extension.equals("js")) return APPLICATION_JAVASCRIPT;
            if (extension.equals("md")) return TEXT_MARKDOWN;
            if (extension.equals("html") || extension.endsWith("htm")) return TEXT_HTML;
            if (extension.equals("json")) return APPLICATION_JSON;
            if (extension.equals("xml")) return APPLICATION_XML;
            if (extension.equals("png")) return IMAGE_PNG;
            if (extension.equals("jpg") || extension.equals("jpeg")) return IMAGE_JPEG;
            if (extension.equals("gif")) return IMAGE_GIF;
            if (extension.equals("svg")) return IMAGE_SVG;
            if (extension.equals("ico")) return IMAGE_ICO;
            if (extension.equals("sh")) return TEXT_X_SHELLSCRIPT;
            if (extension.equals("bash")) return TEXT_X_SHELLSCRIPT;
            if (extension.equals("py")) return TEXT_PYTHON;
            if (extension.equals("java")) return TEXT_JAVA;
            return defaultType;
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

        public boolean isJava() {
            return this.equals(TEXT_JAVA);
        }

        public static final String VALUE = "Content-Type";

        public ObjSerializer<?> serializer() {
            if (this.isMtron()) return ObjmtronSerializer.singleNoClip();
            if (this.isJson()) return ObjSimpleJSONSerializer.single();
            if (this.isHtml()) return ObjHTMLSerializer.single();
            if (this.isXml()) return ObjXMLSerializer.single();
            if (this.isMarkdown()) return ObjMarkdownSerializer.single();
            if (this.isJava()) return ObjJavaSerializer.single();
            if (this.isBSON()) return ObjBSONSerializer.single();
            if (this.isShell()) return ObjPlainTextSerializer.single();
            if (this.isPlain()) return ObjPlainTextSerializer.single();
            return ObjPlainTextSerializer.single();
        }

        public Obj exec(final Str source) {
            if (this.isShell()) {
                try {
                    Runtime.getRuntime().exec(new String[]{source.strValue()});
                } catch (final IOException e) {
                    throw MTronException.of(e);
                }
            } else if (this.isMtron()) {
                return ObjmtronSerializer.parse(source.strValue());
            }
            throw MTronException.of("no exec for %s", this.value);
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
