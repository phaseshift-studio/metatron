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

package studio.phaseshift.metatron.isa.mach.io.space.fs;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.sys.space.fsSpace;
import studio.phaseshift.metatron.isa.web.type.MIME;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.ROUTE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A file that is not text is read as {@code bytes}, and those bytes reach the wire untouched.
 * <p>
 * Both halves are load-bearing, which is why both are asserted. {@code fsSpace.readFileAsObj} used to
 * {@code str(new String(fileBytes))} for any MIME with no type and no serializer — which is every
 * {@code image/*}, and every unlisted extension, because the fallback MIME is {@code text/plain}. Decoding binary
 * through UTF-8 and re-encoding it on the way out does not fail loudly, it <em>grows</em>: a 1337-byte favicon was
 * served as 2231 bytes, every non-ASCII byte having become a three-byte replacement character. The browser then
 * draws a broken image, which reads as "the logo is missing" rather than as a bug. And reading bytes correctly is
 * not enough on its own: {@code MIME.MIMEType.toBytes} falls back to the plain-text serializer, which would
 * stringify them right back.
 * <p>
 * Sizes are asserted rather than a hash, so a failure says which way the corruption went.
 */
class fsBinaryReadTest extends AbstractMetatronTest {

    private static final GraphittyLogger LOG = Graphitty.log(fsBinaryReadTest.class);
    private static final Path WEBSITE = Path.of("docs/website");

    @BeforeAll
    public static void bootFileSpace() {
        InstSet.importInstSet(f("#"));
        fsSpace.of(FileSystems.getDefault(), rec(
                        uri(PATTERN), uri(f("mfs:#")),
                        uri(ROUTE), rec(uri(f("mfs:")), uri(f(WEBSITE.toAbsolutePath().toString())))),
                f("/sys/space/test/fsBinaryRead"));
    }

    /**
     * The read: a binary document is bytes of exactly the file's length; a text document stays a str — typed by
     * its MIME ({@code html::T}, {@code css::T}), which is the intended design and the regression guard here: the
     * same rule must not turn text into bytes.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "images/favicon-32x32.png      % bytes % bytes % the small logo: the file this bug was found with",
            "images/metatron-character.png % bytes % bytes % a large binary asset",
            "images/apple-touch-icon.png   % bytes % bytes % another image, so one lucky file cannot pass for the rule",
            "index.html                    % text  % html  % text stays text, typed by its MIME",
            "css/bootstrap.min.css         % text  % css   % and css keeps the type the serving bug was about",
    }, delimiter = '%')
    void testBinaryIsReadAsBytes(final String path, final String kind, final String expectedTid,
                                 final String desc) throws Exception {
        final long fileSize = Files.size(WEBSITE.resolve(path));
        final Obj obj = Router.readFromSpace(f("mfs:" + path));
        if (obj.isBytes()) {
            LOG.info("read mfs:%s => %s [%d bytes, file is %d]", path, obj.tid(), obj.asBytes().jvm().array().length, fileSize);
            assertEquals(fileSize, obj.asBytes().jvm().array().length,
                    desc + ": the read must carry the file's bytes, not a UTF-8 re-encoding of them");
        } else {
            LOG.info("read mfs:%s => %s [%d chars, file is %d bytes]", path, obj.tid(), obj.strValue().length(), fileSize);
            assertTrue(obj.isStr(), desc + ": a text document must stay a str, got " + obj.tid());
        }
        assertEquals("bytes".equals(kind), obj.isBytes(), desc);
        assertEquals(expectedTid, obj.tid().name(), desc);
    }

    /**
     * The wire: bytes are written verbatim for a MIME with no serializer of its own.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "image/png                % an image passes through byte-for-byte, not through the plain-text fallback",
            "image/jpeg               % nor does a jpeg",
            "application/octet-stream % nor an untyped binary",
    }, delimiter = '%')
    void testBytesAreWrittenVerbatim(final String mime, final String desc) {
        // every byte value, including the ones UTF-8 cannot carry alone — the corruption's raw material
        final byte[] raw = new byte[256];
        for (int i = 0; i < raw.length; i++)
            raw[i] = (byte) i;
        final byte[] written = MIME.MIMEType.of(mime).toBytes(bytes(raw));
        LOG.info("%s: %d bytes in, %d bytes out", mime, raw.length, written.length);
        assertEquals(raw.length, written.length, desc);
        assertTrue(Arrays.equals(raw, written), desc);
    }

    /**
     * A text MIME still goes through its serializer, so the passthrough above is not a blanket bypass.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "text/plain % hello       % plain text is serialized as text",
            "text/html  % <p>hello</p> % html is serialized as html",
    }, delimiter = '%')
    void testTextIsStillSerialized(final String mime, final String content, final String desc) {
        final byte[] written = MIME.MIMEType.of(mime).toBytes(str(content));
        final String roundTripped = new String(written, StandardCharsets.UTF_8);
        LOG.info("%s -> %s", mime, roundTripped);
        assertTrue(roundTripped.contains(content), desc + ": " + roundTripped);
    }
}
