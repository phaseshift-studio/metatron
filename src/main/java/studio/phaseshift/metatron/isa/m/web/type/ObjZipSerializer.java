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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.sys.space.fsSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.web.webInstSet;
import studio.phaseshift.metatron.util.MTronException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * The zip serializer: a zip archive whose single entry is the obj as
 * {@link ObjmtronSerializer} text.
 * <p>
 * ONE EXCEPTION, and it is the reason this serializer exists: an obj that
 * <em>names</em> a file system file or directory ({@code <mfs:dir>} of a
 * mounted {@link fsSpace}) is not serialized into an obj-of-the-file — the
 * file or the directory tree is zipped as it stands. That is what makes
 * {@code ?mimeq=application/zip} on a directory read move the directory
 * rather than a rendering of it.
 * <p>
 * The read direction is the write direction's inverse for an obj archive; an
 * archive of files has no single obj to answer, and says so.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjZipSerializer extends AbstractObjSerializer<ByteBuffer> {

    /**
     * the entry an obj rides in: the zip's payload is mtron source text.
     */
    private static final String ENTRY = "obj.mtron";

    public static final fURI OBJ_ZIP_SERIALIZER_TID = webInstSet.OBJ_ZIP_SERIALIZER_TID;

    private static final ObjZipSerializer INSTANCE = new ObjZipSerializer();

    public static ObjZipSerializer single() {
        return INSTANCE;
    }

    public ObjZipSerializer() {
        super(OBJ_ZIP_SERIALIZER_TID, OBJ_ZIP_SERIALIZER_TID);
    }

    @Override
    public ByteBuffer outputBytes(final Obj obj) {
        return this.write(obj);
    }

    @Override
    public Obj inputBytes(final ByteBuffer bytes) {
        return this.read(bytes);
    }

    @Override
    public ByteBuffer write(final Obj obj) {
        // an obj that names files IS those files: zip them as they are rather than an obj-of-the-file
        final Space space = obj.isUri() && Machine.loaded()
                ? Machine.current().memory().getSpaceFor(obj.uriValue().basePath())
                : null;
        try (final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             final ZipOutputStream zip = new ZipOutputStream(bytes)) {
            if (space instanceof fsSpace)
                zipFile(zip, fsSpace.staticObjToFile(obj), "");
            else {
                zip.putNextEntry(new ZipEntry(ENTRY));
                zip.write(ObjmtronSerializer.single().outputBytes(obj).array());
                zip.closeEntry();
            }
            zip.finish();
            return ByteBuffer.wrap(bytes.toByteArray());
        } catch (final IOException e) {
            throw MTronException.of(e);
        }
    }

    /**
     * one file or directory tree, each entry named by its path below
     * {@code parent} — a directory is an entry ending in {@code /}, so an
     * empty one survives the trip.
     */
    private static void zipFile(final ZipOutputStream zip, final File file, final String parent) throws IOException {
        final String name = parent + file.getName();
        zip.putNextEntry(new ZipEntry(file.isDirectory() ? name + "/" : name));
        if (file.isDirectory()) {
            zip.closeEntry();
            final File[] children = file.listFiles();
            if (null != children)
                for (final File child : children)
                    zipFile(zip, child, name + "/");
        } else {
            Files.copy(file.toPath(), zip);
            zip.closeEntry();
        }
    }

    @Override
    public Obj read(final ByteBuffer bytes) {
        try (final ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes.array()))) {
            final ZipEntry entry = zip.getNextEntry();
            if (null == entry || entry.isDirectory())
                throw MTronException.of("a zip of files is not an obj: %s", entry);
            final byte[] source = zip.readAllBytes();
            // more than one entry is a file archive: it has no single obj to answer
            if (null != zip.getNextEntry())
                throw MTronException.of("a zip of many files is not one obj: %s", entry.getName());
            return ObjmtronSerializer.single().inputBytes(ByteBuffer.wrap(source));
        } catch (final IOException e) {
            throw MTronException.of(e);
        }
    }
}
