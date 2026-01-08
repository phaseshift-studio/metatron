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
import studio.phaseshift.metatron.lang.MSpace;
import studio.phaseshift.metatron.lang.Space;
import studio.phaseshift.metatron.lang.core.m.type.*;
import studio.phaseshift.metatron.lang.sys.console.Highlighter;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.USER_HOME;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.INST_TID;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.URI_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.sys.fs.fsInstSet.FILE_TID;
import static studio.phaseshift.metatron.lang.sys.sysInstSet.SPACE_TID;

public class fileSpace extends MSpace<FileSystem> {

    public static final fURI FS_TID = SPACE_TID.extend("fs");
    public static final Type FS_TYPE = T(FS_TID, isa_(rec()), instC(INST_TID.dom(ALL.maybe()).rng(FS_TID), lst(isa_(rec(uri(Tokens.PATTERN), T(URI_TID))).tryToInst()), (lhs, inst) -> {
        final fURI pattern = inst.arg(0).<Rec>as().at(Tokens.PATTERN).uriValue();
        final Space space = fileSpace.of(FileSystems.getDefault(), inst.arg(0).<Rec>as().jvm(), pattern, inst.arg(0).vid());
        Router.global().addSpace(space);
        return space;
    }));


    private final Tuple.Pair<String, String> rewrite;

    public static fileSpace of(final FileSystem sjvm, final Map<Obj, Obj> jvm, final fURI pattern, final fURI vid) {
        return new fileSpace(sjvm, jvm, pattern, vid);
    }

    private fileSpace(final FileSystem sjvm, final Map<Obj, Obj> jvm, final fURI pattern, final fURI vid) {
        super(sjvm, jvm, pattern, FS_TID, vid);
        final Rel rewrite =  jvm.getOrDefault(uri(Tokens.REWRITE), rel(uri(""), uri(""))).asRel();
        final String prefix = rewrite.first().uriValue().toString().replace("~", System.getProperty(USER_HOME));
        final String prepend = rewrite.second().uriValue().toString().replace("~", System.getProperty(USER_HOME));
        this.rewrite = Tuple.Pair.with(prefix,prepend);
    }

    @Override
    public Obj read(final fURI vid) {
        //return Space.Helper.resolveRead(this, vid, this.directReader());
        return objs(this.directReader().apply(vid).entrySet().stream().map(kv -> vid.isNode() ? kv.getValue() : rel(uri(kv.getKey()), kv.getValue())));
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        // return this.qs().processPreWrite(vid, vid, obj).orElseGet(() -> {
        return this.directWriter().apply(vid, obj);

        //Space.Helper.resolveWrite(this, vid.basePath(), obj, this.directWriter(), this.directReader());
        //return obj;
        //   return this.qs().processPostWrite(vid, vid, obj).orElse(this.qs().processQlessWrite(vid, vid, obj).orElse(obj));
        // });
    }

    public static Uri makeFile(final Path path) {
        try {
            return uri(f(path.toString()).query("p", PosixFilePermissions.toString(Files.getPosixFilePermissions(path))), FILE_TID, null);
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }


    @Override
    public Function<fURI, Map<fURI, Obj>> directReader() {
        return (key) -> {
            if (key.equals(ALL))
                throw MTronException.of("infinite nested walks on file system not allowed");
            else {
                if (key.hasPattern()) {
                    try (Stream<fURI> walk = Files.walk(Path.of(Space.Helper.toNativeSpace(key.retractPattern(), this.rewrite)), vid.hasPattern() ? Integer.MAX_VALUE : vid.segments().size() + 1, FileVisitOption.FOLLOW_LINKS)
                            .map(p -> Space.Helper.fromNativeSpace(p.toAbsolutePath().toString(), this.rewrite)).filter(p -> p.matches(key))) {
                        return walk.collect(Collectors.toMap(p -> p, fURI::toUri, Obj::append, LinkedHashMap::new));
                    } catch (IOException e) {
                        throw MTronException.of(e);
                    }
                } else {
                    try {
                        final Path vidPath = Path.of(Space.Helper.toNativeSpace(key, this.rewrite));
                        if (Files.isDirectory(vidPath)) {
                            return Files.list(vidPath)
                                    .collect(Collectors.toMap(
                                            p -> f(p.toString()),
                                            fileSpace::makeFile, Obj::append, LinkedHashMap::new));
                        } else {
                            return Map.of(Space.Helper.fromNativeSpace(vidPath.toString(), this.rewrite), makeFile(vidPath));
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
                        throw MTronException.of("deleting files currently not supported", pattern);
                        //   Files.delete(Path.of(pattern.toString()));
                    } else {
                        LOG.info("writing %s to %s", obj, pattern);
                        final FileOutputStream writer = new FileOutputStream(pattern.toString());
                        if (obj.isBytes())
                            writer.write(obj.bytesValue().array());
                        else if (obj.isStr())
                            writer.write(obj.strValue().getBytes(StandardCharsets.UTF_8));
                        else
                            writer.write(Highlighter.unformat(obj.toString()).getBytes(StandardCharsets.UTF_8));
                        writer.flush();
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
