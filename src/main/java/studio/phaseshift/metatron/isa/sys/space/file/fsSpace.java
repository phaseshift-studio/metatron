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

package studio.phaseshift.metatron.isa.sys.space.file;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.Q;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.MSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MObjs;
import studio.phaseshift.metatron.isa.sys.type.console.Highlighter;
import studio.phaseshift.metatron.isa.sys.type.Router;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;
import studio.phaseshift.metatron.util.Tuple.Pair;

import java.io.*;
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
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.sys.sysInstSet.FILE_TID;
import static studio.phaseshift.metatron.isa.sys.sysInstSet.SYS_ISA_TID;

public class fsSpace extends MSpace<FileSystem> {

    public static final fURI FS_TID = SYS_ISA_TID.extend("space/fs");
    private static final Rec FS_REC = rec(
            uri(Tokens.PATTERN), URI_TYPE,
            uri(Tokens.REWRITE), rel(URI_TYPE, URI_TYPE),
            uri(Tokens.SCRIPT).maybe(), rec(URI_TYPE, URI_TYPE));
    public static final Type FS_TYPE = T(FS_TID, isa_(rec()),
            instC(INST_TID.dom(ALL.maybe()).rng(FS_TID),
                    lst(isa_(rec(uri(Tokens.PATTERN), T(URI_TID))).tryToInst()),
                    (lhs, inst) -> fsSpace.of(FileSystems.getDefault(), inst.arg(0).asRec(), inst.arg(0).vid())));

    private final Tuple.Pair<String, String> rewrite;

    public static fsSpace of(final FileSystem sjvm, final Rec config, final fURI vid) {
        return new fsSpace(sjvm, config.jvm(), vid);
    }

    private fsSpace(final FileSystem sjvm, final Map<Obj, Obj> jvm, final fURI vid) {
        super(sjvm, jvm, FS_TID, vid);
        final Rel rewrite = this.at(uri(Tokens.REWRITE)).orElse(rel(uri(""), uri(""))).asRel();
        LOG.info("rewrite: %s", rewrite);
        final String prefix = rewrite.first().uriValue().toString().replace("~", System.getProperty(USER_HOME));
        final String prepend = rewrite.second().uriValue().toString().replace("~", System.getProperty(USER_HOME));
        this.rewrite = Tuple.Pair.with(prefix, prepend);
    }

    @Override
    public Obj read(final fURI vid) {
        return Q.Helper.processPreRead(this.qs(), vid, vid).orElseGet(() -> {
            Obj result = Space.Helper.resolveRead(this, vid.basePath(), directReader());
            //return result;
            return Q.Helper.processPostRead(this.qs(), vid, vid, result).orElse(result);
        });

        //return Space.Helper.resolveRead(this, vid, this.directReader());
        // return objs(this.directReader().apply(vid).entrySet().stream().map(kv -> vid.isNode() ? kv.getValue() : rel(uri(kv.getKey()), kv.getValue())));
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return Q.Helper.processPreWrite(this.qs(), vid, vid, obj).orElseGet(() -> {
            Obj result = Space.Helper.resolveWrite(this, vid.basePath(), obj, this.directWriter(), this.directReader());
            return Q.Helper.processPostWrite(this.qs(), vid, vid, obj).orElse(Q.Helper.processQlessWrite(this.qs(), vid, vid, obj).orElse(result));
        });
        //   return this.qs().processPreWrite(vid, vid, obj).orElseGet(() -> {
        //  return this.directWriter().apply(vid, obj);

        //Space.Helper.resolveWrite(this, vid.basePath(), obj, this.directWriter(), this.directReader());
        //return obj;
        //   return this.qs().processPostWrite(vid, vid, obj).orElse(this.qs().processQlessWrite(vid, vid, obj).orElse(obj));
        // });
    }

    public static File resolveFile(final Obj fileObj) {
        try {
            final fsSpace space = Router.global().getSpace(fileObj.uriValue().basePath()).as();
            return Paths.get(Space.Helper.toNativeSpace(fileObj.uriValue().basePath(), space.rewrite)).toFile();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    public Obj resolveObj(final Uri path) {
        try {
            final File file = Paths.get(path.uriValue().toString()).toFile();
            final fsSpace space = Router.global().getSpace(Space.Helper.fromNativeSpace(file.getPath(), this.rewrite)).as();
            return uri(Space.Helper.fromNativeSpace(file.getPath(), space.rewrite), FILE_TID, null);
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }


    public static Uri makeFile(final Path path) {
        try {
            return uri(f(path.toString()).query("p", PosixFilePermissions.toString(Files.getPosixFilePermissions(path))), FILE_TID, null);
        } catch (final NoSuchFileException e) {
            return uri("").c(cInt.ZERO()).asUri();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public Function<fURI, Iterator<Tuple.Pair<fURI, Obj>>> directReader() {
        return (key) -> {
            if (key.equals(ALL))
                throw MTronException.of("infinite nested walks on file system not allowed");
            else {
                if (key.hasPattern()) {
                    try (final Stream<Path> walk = Files.walk(Path.of(Space.Helper.toNativeSpace(key.retractPattern(), this.rewrite)), vid.hasPattern() ? Integer.MAX_VALUE : vid.segments().size() + 1, FileVisitOption.FOLLOW_LINKS)) {
                        return walk
                                .filter(p -> Space.Helper.fromNativeSpace(p.toString(), this.rewrite).matches(key))
                                .map(fsSpace::makeFile)
                                .map(this::resolveObj)
                                .collect(Collectors.toMap(p -> p, p -> p, Obj::append, LinkedHashMap::new))
                                .entrySet()
                                .stream()
                                .map(kv -> Pair.with(kv.getKey().uriValue(), kv.getValue()))
                                .iterator();
                    } catch (IOException e) {
                        throw MTronException.of(e);
                    }

                } else {
                    try {
                        final Path vidPath = Path.of(Space.Helper.toNativeSpace(key.name().equals("apply") ? key.retract() : key, this.rewrite));
                        if (Files.isDirectory(vidPath)) {
                            return Files.list(vidPath).map(fsSpace::makeFile).map(p -> Pair.<fURI, Obj>with(p.uriValue(), resolveObj(p))).iterator();
                        } else {
                            return IteratorUtil.of(Pair.with(Space.Helper.fromNativeSpace(vidPath.toString(), this.rewrite), key.name().equals("apply") ?
                                    instC(key.retract().dom(ALL.maybe()).rng(ALL_STAR), lst(T(ALL_STAR)), (lhs, inst) -> {
                                        LOG.debug("applying: %s => %s", lhs, inst);
                                        final Uri toExec = makeFile(vidPath);
                                        if (!vidPath.toFile().canExecute())
                                            throw MTronException.of("file permissions prevent execution of %s", toExec);
                                        return this.internalApply(toExec, inst.args());
                                    }) :
                                    resolveObj(makeFile(vidPath))));
                        }
                    } catch (final NoSuchFileException e) {
                        LOG.warn("no such file: %s", key);
                        return IteratorUtil.of();
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
                    x = mParser.parse(line);
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
                this.directReader().apply(pattern).forEachRemaining(kv -> this.write(kv.get0(), kv.get1()));
            } else {
                try {
                    if (obj.isNoObj()) {
                        throw MTronException.of("deleting files currently not supported", pattern);
                        //   Files.delete(Path.of(pattern.toString()));
                    } else {
                        final Path path = Paths.get(Space.Helper.toNativeSpace(pattern.basePath(), this.rewrite));
                        final File file = path.toFile();
                        LOG.info("writing %s to %s", obj, path);
                        file.createNewFile();
                        final FileOutputStream writer = new FileOutputStream(file, true);
                        if (obj.isBytes())
                            writer.write(obj.bytesValue().array());
                        else if (obj.isStr())
                            writer.write(obj.strValue().getBytes(StandardCharsets.UTF_8));
                        else
                            writer.write(Highlighter.unformat(obj.toString()).getBytes(StandardCharsets.UTF_8));
                        writer.flush();
                        writer.close();
                        if (pattern.hasQuery("p")) {
                            final Set<PosixFilePermission> currentP = PosixFilePermissions.fromString(Files.getPosixFilePermissions(path).toString());
                            final Set<PosixFilePermission> newP = PosixFilePermissions.fromString(pattern.queryValue(f("p"), String.class));
                            if (!currentP.equals(newP))
                                Files.setPosixFilePermissions(file.toPath(), newP);
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
