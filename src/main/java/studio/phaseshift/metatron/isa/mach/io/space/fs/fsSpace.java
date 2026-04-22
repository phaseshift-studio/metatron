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

package studio.phaseshift.metatron.isa.mach.io.space.fs;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MObjs;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.type.Content;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.SCRIPT;
import static studio.phaseshift.metatron.Tokens.USER_HOME;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.*;

public class fsSpace extends AbstractSpace<FileSystem> {

    private static final Uri NOOBJ_URI = uri(f(""), URI_TID.zero(), null);
    public static final fURI FS_SPACE_TID = MACH_ISA_TID.extend("space").extend("fsspace");
    public static final Type FS_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(FS_SPACE_TID)
            .isaPredicate(rec(
                    uri(Tokens.PATTERN), URI_TYPE,
                    uri(Tokens.ROUTE), rec(URI_TYPE, URI_TYPE),
                    uri(Tokens.SCRIPT).maybe(), rec(URI_TYPE, URI_TYPE)))
            .constructor(
                    instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(FS_SPACE_TID), lst(REC_TYPE),
                            (lhs, inst) -> fsSpace.of(FileSystems.getDefault(), inst.arg(0).asRec(), inst.arg(0).vid()))).create();

    public static fsSpace of(final FileSystem sjvm, final Rec config, final fURI vid) {
        return new fsSpace(sjvm, config.jvm(), vid);
    }

    private fsSpace(final FileSystem sjvm, final Map<Obj, Obj> jvm, final fURI vid) {
        super(sjvm, jvm, FS_SPACE_TID, vid);
        final Map<Uri, Uri> tempRoutes = new LinkedHashMap<>(this.routes);
        this.routes.clear();
        tempRoutes.entrySet()
                .stream()
                .map(kv -> Map.entry(
                        uri(kv.getKey().toString().replace("~", System.getProperty(USER_HOME))),
                        uri(kv.getValue().toString().replace("~", System.getProperty(USER_HOME)))))
                .forEach(kv -> this.routes.put(kv.getKey(), kv.getValue()));
    }

    public static File staticObjToFile(final Obj obj) {
        try {
            final fsSpace space = Router.global().getSpace(obj.uriValue().basePath()).as();
            return new File(space.redirect(obj.uriValue().basePath(), true).toString());
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    public static Uri makeFile(final Path path) {
        try {
            if (path.toString().isEmpty())
                return NOOBJ_URI;
            return uri(f(path.toString()));//.q("p", PosixFilePermissions.toString(Files.getPosixFilePermissions(path))), path.endsWith("/") ? DIR_TID : FILE_TID, null);
        } /*catch (final NoSuchFileException e) {
            return NOOBJ_URI;
        } */ catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public Obj fileToObj(final File file) {
        try {
            if (file.exists()) {
                if (file.isFile()) {
                    Content.ContentType contentType = Content.ContentType.fromProbe(file);
                    final FileInputStream fs = new FileInputStream(file);
                    byte[] fileBytes = fs.readAllBytes();
                    fs.close();
                    final String source = new String(fileBytes, StandardCharsets.UTF_8);
                    final fURI vid = source.startsWith("[-- @<") ? f(source.substring(6, source.indexOf("> --]\n")).trim()) : null;
                    if (vid != null) contentType = Content.ContentType.APPLICATION_MTRON;
                    LOG.debug("fileToObj: %s => %s", file.getPath(), vid);
                    return contentType.hasSerializer() ? contentType.fromBytes(fileBytes).selfVID(vid) : uri(this.redirect(f(file.getPath()), false), FILE_TID, null);
                } else {
                    return uri(this.redirect(f(file.getPath()), false), DIR_TID, null);
                }
            }
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
        return noobj();
    }

    @Override
    public void close() {
        // do nothing (can't close file system)
    }

    public Obj objToFile(final fURI vid, final Obj obj) {
        try {
            //if (!file.isFile())
            //   throw MTronException.of("not a file: %s", file);
            final Content.ContentType contentType = Content.ContentType.fromType(obj);
            final File file = new File(this.redirect(vid, true).toString());
            LOG.info("writing %s to %s", obj, file.getPath());
            if (!file.exists()) {
                new File(f(file.getAbsolutePath()).retract(1).toString()).mkdirs();
                file.createNewFile();
            }
            final fURI selfVID = obj.vid();
            try (final FileOutputStream writer = new FileOutputStream(file, vid.hasQ("append"))) {
                if (contentType.isMtron() && !vid.hasQ("append")) {
                    final String at_vid = selfVID == null ? null : "[-- @<" + selfVID + "> --]\n";
                    if (null != at_vid) writer.write(at_vid.getBytes(StandardCharsets.UTF_8));
                }
                writer.write(contentType.toBytes(obj.selfVID(null)));
                writer.flush();
            }
            return obj.selfVID(selfVID);
        } catch (final Exception e) {
            throw MTronException.of(e, vid.toString());
        }
    }


    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        return (key) -> {
            final fURI keyQless = key.qLess();
            if (key.equals(ALL))
                throw MTronException.of("infinite recursive walks on file system currently prohibited");
            else {
                if (key.hasPattern()) {
                    try (final Stream<Path> walk = Files.walk(Path.of(Space.Helper.routeFromSpace(keyQless.retractPattern(), this.routes).toString()), keyQless.hasPattern("#") ? Integer.MAX_VALUE : keyQless.asNode().path().size())) {
                        return walk
                                .filter(p -> {
                                    try {
                                        return this.redirect(f(p.toString()), false).test(f("#"));
                                    } catch (final Exception e) {
                                        LOG.error(e);
                                        return false;
                                    }
                                })
                                .collect(Collectors.toMap(p -> Space.Helper.routeFromSpace(f(p.toString()), this.routes), p -> {
                                    final File file = p.toFile();
                                    return fileToObj(file);
                                }, Obj::append, LinkedHashMap::new))
                                .entrySet()
                                .stream()
                                .map(kv -> IdObj.of(kv.getKey(), kv.getValue()))
                                .iterator();
                    } catch (IOException e) {
                        throw MTronException.of(e);
                    }

                } else {
                    try {
                        final Path vidPath = Path.of(Space.Helper.routeFromSpace(keyQless.name().equals("apply") ? keyQless.retract(1) : keyQless, this.routes).toString());
                        final File file = vidPath.toFile();
                        return IteratorUtil.of(IdObj.of(key, keyQless.name().equals("apply") ?
                                instC(keyQless.retract(1).dom(ALL.maybe()).rng(ALL_STAR), lst(T(ALL_STAR)), (lhs, inst) -> {
                                    LOG.debug("applying: %s => %s", lhs, inst);
                                    final Uri toExec = makeFile(vidPath);
                                    if (!file.canExecute())
                                        throw MTronException.of("file permissions prevent execution of %s", toExec);
                                    return this.internalApply(toExec, inst.args());
                                }) :
                                this.fileToObj(file)));
                    } catch (final Exception e) {
                        throw MTronException.of(e);
                    }
                }
            }
        };
    }

    private Obj evalScript(final File scriptPath, final String scriptEngine, final Poly<?, ?> args) {
        final List<Obj> result = new ArrayList<>();
        try {
            final String[] command = new String[2 + (int) args.count()];
            command[0] = scriptEngine;
            command[1] = scriptPath.getAbsolutePath();
            int j = 2;
            for (final Obj arg : args) {
                command[j++] = arg.toString();
            }
            final ProcessBuilder processBuilder = new ProcessBuilder(command);
            LOG.debug("evaluating script %s", processBuilder.command());
            final Map<String, String> env = processBuilder.environment();
            env.put("ENV_KEY", "ENV_VALUE");
            final Process process = processBuilder.start();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Obj x;
                try {
                    x = ObjmtronSerializer.parse(line);
                } catch (final Exception e) {
                    x = str(line);
                }
                LOG.debug("%s", x);
                result.add(x);
            }
            process.waitFor();
            LOG.debug("script executed successfully");
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
        return MObjs.objs(result);
    }

    private String checkScriptEvaluation(final File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String firstLine = reader.readLine();
            if (firstLine != null) {
                // if (true)
                //     return "/bin/sh";
                if (firstLine.startsWith("#!"))
                    return this.at(SCRIPT).orElse(rec0())
                            .elements()
                            .filter(pair -> firstLine.contains(pair.first().uriValue().toString()))
                            .map(Rel::second)
                            .map(engine -> engine.uriValue().toString())
                            .findFirst()
                            .orElse(null);
            }
        } catch (final IOException e) {
            LOG.warn("error reading script file: %s", file, e);
        }
        return null;
    }

    public Obj internalApply(final Obj fileObj, final Poly<?, ?> args) {
        if (fileObj.tid().basePath().equals(FILE_TID)) {
            LOG.debug("internal apply: %s => %s", args, fileObj);
            final Path path = Paths.get(fileObj.uriValue().basePath().toString());
            final File file = path.toFile();
            final String scriptEngine = checkScriptEvaluation(file);
            if (scriptEngine != null)
                return this.evalScript(file, scriptEngine, args);
        }
        return fileObj;
    }

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (pattern, obj) -> {
            if (pattern.hasPattern()) {
                this.directReader().apply(pattern).forEachRemaining(kv -> this.write(kv.furi(), kv.obj()));
            } else {
                final Path path = Paths.get(this.redirect(pattern.basePath(), false).toString());
                final File file = path.toFile();
                try {
                    if (obj.isNoObj()) {
                        final File delete = new File(this.redirect(pattern, true).toString());
                        Files.deleteIfExists(delete.toPath());
                        return noobj();
                    } else {
                        if (file.isDirectory()) {
                            if (!file.exists())
                                file.mkdirs();
                        } else {
                            this.objToFile(f(path.toString()), obj);
                            if (pattern.hasQ("p")) {
                                final Set<PosixFilePermission> currentP = PosixFilePermissions.fromString(Files.getPosixFilePermissions(path).toString());
                                final Set<PosixFilePermission> newP = PosixFilePermissions.fromString(pattern.qValue("p", String.class));
                                if (!currentP.equals(newP))
                                    Files.setPosixFilePermissions(file.toPath(), newP);
                            }
                        }
                    }
                } catch (final Exception e) {
                    throw MTronException.of(e);
                }
            }
            return obj;
        };
    }

}
