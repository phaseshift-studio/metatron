/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.space.fs;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.Str;
import studio.phaseshift.metatron.lang.mtron.type.impl.MRec;
import studio.phaseshift.metatron.space.MSpace;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.util.MTronException;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.MTRON_SPACE_TID;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MUri.uri;

public class FileSpace extends MSpace<FileSystem> {

    public static final fURI FILESPACE_TID = MTRON_SPACE_TID.extend("fs");

    public FileSpace(final FileSystem fs, final fURI pattern, final fURI vid) {
        super(fs, pattern, FILESPACE_TID, vid);
    }

    @Override
    public Obj read(final fURI vid) {
        return Space.Helper.resolveRead(this, vid, this.directReader());
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        // return this.qs().processPreWrite(vid, vid, obj).orElseGet(() -> {
        Space.Helper.resolveWrite(this, vid.basePath(), obj, this.directWriter(), this.directReader());
        return obj;
        //   return this.qs().processPostWrite(vid, vid, obj).orElse(this.qs().processQlessWrite(vid, vid, obj).orElse(obj));
        // });
    }

    @Override
    public Function<fURI, Map<fURI, Obj>> directReader() {
        return (key) -> {
            if (key.equals(fURI.ALL))
                throw MTronException.of("infinite nested walks on file system not allowed");
            else {
                if (key.hasPattern()) {
                    try (Stream<Path> walk = Files.walk(Path.of(this.pattern.retractPattern().toString()), vid.segments().size(), FileVisitOption.FOLLOW_LINKS)) {
                        return walk.map(p -> f(p.toString())).filter(p -> p.matches(vid)).collect(Collectors.toMap(p -> p, p -> uri(p.toString()), Obj::append, LinkedHashMap::new));
                    } catch (IOException e) {
                        throw MTronException.of(e);
                    }
                } else {
                    try {
                        final Path vidPath = Path.of(key.toString());
                        if (Files.isDirectory(vidPath)) {
                            return Files.list(vidPath)
                                    .collect(Collectors.toMap(
                                            p -> f(p.toString()),
                                            p -> MRec.fromUriKeyed("name", str(p.getFileName().toString()), "permissions", str(MTronException.wrap(() -> Files.getPosixFilePermissions(p)).toString())), Obj::append, LinkedHashMap::new));
                        } else {
                            final Str value = str(Files.readString(vidPath));
                            return Map.of(vid, value);
                        }
                    } catch (final IOException e) {
                        throw MTronException.of(e);
                    }

                }
            }
        };
    }

    @Override
    public BiConsumer<fURI, Obj> directWriter() {
        return (pattern, obj) -> {
            if (pattern.hasPattern()) {
                this.directReader().apply(pattern).forEach((key, value) -> this.write(key, obj));
            } else {
                try {
                    if (obj.isNoObj()) {
                        Files.delete(Path.of(pattern.toString()));
                    } else {
                        final FileWriter writer = new FileWriter(pattern.toString());
                        if (obj.isStr())
                            writer.write(obj.strValue());
                        else
                            writer.write(obj.toString());
                        writer.close();
                    }
                } catch (final Exception e) {
                    throw MTronException.of(e);
                }
            }
        };
    }

}
