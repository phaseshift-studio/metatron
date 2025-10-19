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

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.NoObj;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.lang.obj.Str;
import studio.phaseshift.metatron.lang.obj.mtron.MRec;
import studio.phaseshift.metatron.lang.obj.mtron.MStr;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.space.mem.MSpace;
import studio.phaseshift.metatron.util.MTronException;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.lang.fURI.f;
import static studio.phaseshift.metatron.lang.obj.mtron.MStr.str;
import static studio.phaseshift.metatron.lang.obj.mtron.MUri.uri;

public class FileSpace extends MSpace<FileSystem> {

    public static final fURI FILESPACE_TID = f("/mtron/io/fs");

    public FileSpace(final FileSystem fs, final fURI pattern, final fURI vid) {
        super(fs, pattern, FILESPACE_TID, vid);
    }

    @Override
    public Obj read(final fURI vid) {
        return Space.Helpers.resolveRead(this, vid, (key) -> {
            if (key.equals(fURI.ALL))
                throw MTronException.of("infinite nested walks on file system not allowed");
            else {
                if (key.hasPattern()) {
                    try (Stream<Path> walk = Files.walk(Path.of(this.pattern.retractPattern().toString()), vid.segments().size(), FileVisitOption.FOLLOW_LINKS)) {
                        return walk.map(p -> f(p.toString())).filter(p -> p.matches(vid)).collect(Collectors.toMap(p -> p, p -> uri(p.toString()),Obj::append, LinkedHashMap::new));
                    } catch (IOException e) {
                        throw MTronException.of(e);
                    }
                } else {
                    try {
                        final Path vidPath = Path.of(vid.toString());
                        if (Files.isDirectory(vidPath)) {
                            return Files.list(vidPath)
                                    .collect(Collectors.toMap(
                                            p -> f(p.toString()),
                                            p -> MRec.ofUriKeyed("name", str(p.getFileName().toString()), "permissions", str(MTronException.wrap(() -> Files.getPosixFilePermissions(p)).toString())), Obj::append, LinkedHashMap::new));
                        } else {
                            final Str value = MStr.of(Files.readString(vidPath));
                            return Map.of(vid, value);
                        }
                    } catch (IOException e) {
                        throw MTronException.of(e);
                    }

                }
            }
        });


    }

    @Override
    public Obj write(fURI vid, Obj obj) {
        return NoObj.single();
    }
}
