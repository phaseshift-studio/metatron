/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.lang.sys.fs;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.Space;
import studio.phaseshift.metatron.lang.core.m.type.*;
import studio.phaseshift.metatron.lang.MSpace;
import studio.phaseshift.metatron.lang.core.m.type.impl.MRec;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.util.MTronException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.*;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.*;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.sys.sysInstSet.SPACE_TID;

public class fileSpace extends MSpace<FileSystem> {

    public static final fURI FS_TID = SPACE_TID.extend("fs");
    public static final Type FS_TYPE = T(FS_TID, isa_(rec()), instC(INST_TID.dom(ALL.maybe()).rng(FS_TID), lst(isa_(rec(uri(Tokens.PATTERN), T(URI_TID))).tryToInst()), (lhs, inst) -> {
        final fURI pattern = inst.arg(0).<Rec>as().at(Tokens.PATTERN).uriValue();
        final Space space = new fileSpace(FileSystems.getDefault(), inst.arg(0).<Rec>as().jvm(), pattern, inst.arg(0).vid());
        Router.global().addSpace(space);
        return space;
    }));
    public static final fURI FILE_TID = FS_TID.extend("file");
    public static final fURI DIR_TID = FS_TID.extend("dir");
    public static final Type FILE_TYPE = T(FILE_TID, isa_(rec()));
    // public static final Type DIR_TYPE =

    public fileSpace(final FileSystem fs, final Map<Obj, Obj> jvm, final fURI pattern, final fURI vid) {
        super(fs, jvm, pattern, FS_TID, vid);
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

    private static Rec makeFile(final Path path) {
        return new MRec(new LinkedHashMap<Obj, Obj>(Map.of(
                uri(Tokens.NAME), uri(path.getFileName().toString()),
                uri("permissions"), lst(MTronException.wrap(() -> (List) Files.getPosixFilePermissions(path).stream().map(x -> uri(x.toString())).toList())),
                uri("data"), auto_(instC(INST_TID.dom(ALL.maybe()).rng(BYTES_TID), lst(T(URI_TID)), (lhs, inst) -> {
                    try {
                        final File file = path.toFile();
                        final byte[] data = new byte[(int) file.length()];
                        try (final FileInputStream fis = new FileInputStream(file)) {
                            fis.read(data);
                        }
                        return bytes(ByteBuffer.wrap(data));
                    } catch (final Exception e) {
                        throw MTronException.of(e);
                    }
                })).tryToInst())), FILE_TID, fURI.fnull);
    }

    @Override
    public Function<fURI, Map<fURI, Obj>> directReader() {
        return (key) -> {
            if (key.equals(ALL))
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
                                            fileSpace::makeFile, Obj::append, LinkedHashMap::new));
                        } else {
                            return Map.of(f(vidPath.toString()), makeFile(vidPath));
                        }
                    } catch (final IOException e) {
                        throw MTronException.of(e);
                    }
                }
            }
        };
    }

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
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
            return obj;
        };
    }

}
