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

package studio.phaseshift.metatron.isa.m.web.type;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractSerializerTest;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.sys.space.fsSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.QPROC;
import static studio.phaseshift.metatron.Tokens.ROUTE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjZipSerializerTest extends AbstractSerializerTest<ByteBuffer> {

    private static final String FS_PATTERN = "objzip:#";

    private Path root;

    public ObjZipSerializerTest() {
        super(ObjZipSerializer.single(), null, null);
    }

    /**
     * an obj archive is one mtron-source entry — the reading side's whole
     * contract, since an obj is what comes back.
     */
    @Test
    public void testAnObjRidesAsMtronSourceInOneEntry() {
        final Obj obj = ObjmtronSerializer.single().parse("rec::[name=>'marko',age=>29]").apply();
        final ByteBuffer zip = ObjZipSerializer.single().outputBytes(obj);
        final Map<String, String> entries = unzip(zip);
        assertEquals(1, entries.size(), "an obj archive holds one entry: " + entries.keySet());
        final Map.Entry<String, String> entry = entries.entrySet().iterator().next();
        assertEquals(ObjmtronSerializer.single().write(obj), entry.getValue(), "the entry is mtron source");
        assertEquals(obj, ObjZipSerializer.single().read(zip), "the archive reads back as the obj");
    }

    /**
     * the special case, through the query processor the user reaches: an obj
     * naming a directory moves the directory, not a rendering of it.
     */
    @Test
    public void testMimeQZipOfADirectoryIsTheFiles() throws IOException {
        this.mountTempFs();
        final Obj zipped = Machine.read(f("objzip:source?mimeq=application/zip"));
        assertTrue(zipped.isBytes(), "?mimeq=application/zip answers the archive as bytes, got: " + zipped);
        final Map<String, String> entries = unzip(zipped.asBytes().jvm());
        assertEquals(Map.of(
                "source/", "",
                "source/alma.txt", "alma",
                "source/empty/", "",
                "source/ragtime/", "",
                "source/ragtime/Duke.java", "class Duke {}"), entries, "the directory tree is the archive");
        // a uri naming a file is that one file, not an obj-of-the-file
        assertEquals(Map.of("standalone.txt", "stand alone"),
                unzip(ObjZipSerializer.single().outputBytes(uri("objzip:standalone.txt"))),
                "a file is zipped as the file");
        // the archive of files has no single obj to read: the read refuses rather than answering one member
        assertThrows(MTronException.class, () -> ObjZipSerializer.single().read(zipped.asBytes().jvm()));
    }

    @AfterEach
    public void unmountTempFs() {
        if (null != this.root) {
            Machine.current().memory().removeSpace(f("/sys/space/fs/objzip"));
            CommonUtil.deleteDirectory(this.root);
            this.root = null;
        }
    }

    private void mountTempFs() throws IOException {
        this.root = Files.createTempDirectory("objzip");
        final Path source = this.root.resolve("source");
        Files.createDirectories(source.resolve("ragtime"));
        Files.createDirectories(source.resolve("empty"));
        Files.writeString(source.resolve("alma.txt"), "alma");
        Files.writeString(source.resolve("ragtime/Duke.java"), "class Duke {}");
        Files.writeString(this.root.resolve("standalone.txt"), "stand alone");
        fsSpace.of(FileSystems.getDefault(), rec(
                uri(PATTERN), uri(FS_PATTERN),
                uri(QPROC), lst(QCollection.mimeQ()),
                uri(ROUTE), rec(uri("objzip:"), uri(this.root.toAbsolutePath().toString()))),
                f("/sys/space/fs/objzip"));
    }

    /**
     * the archive as a name → text map, for asserting what a zip holds.
     */
    private static Map<String, String> unzip(final ByteBuffer bytes) {
        final Map<String, String> entries = new LinkedHashMap<>();
        try (final ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes.array()))) {
            ZipEntry entry = zip.getNextEntry();
            while (null != entry) {
                entries.put(entry.getName(), entry.isDirectory() ? "" : new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                entry = zip.getNextEntry();
            }
        } catch (final IOException e) {
            throw MTronException.of(e);
        }
        return entries;
    }
}
