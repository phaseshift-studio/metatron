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

import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.stream.LogOutputStream;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.type.MIME;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.io.*;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.MIMEQ_PATTERN;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.DIR_TID;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_ISA_TID;

public class fsSpace extends AbstractSpace<FileSystem> {

    private static final Uri NOOBJ_URI = uri(f(""), URI_TID.zero(), null);
    public static final fURI FS_SPACE_TID = MACH_ISA_TID.extend("space").extend("fsspace");
    public static final Type FS_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(FS_SPACE_TID)
            .isaPredicate(rec(
                    uri(PATTERN), URI_TYPE,
                    uri(ROUTE), rec(URI_TYPE, URI_TYPE),
                    uri(SCRIPT).maybe(), rec(URI_TYPE, URI_TYPE)))
            .constructor(
                    instC(M_ISA_INST_TID.dom(ALL.maybe()).rng(FS_SPACE_TID), lst(REC_TYPE),
                            (lhs, inst) -> fsSpace.of(FileSystems.getDefault(), inst.arg(0).asRec(), inst.arg(0).vid()))).create();

    public static fsSpace of(final FileSystem sjvm, final Rec config, final fURI vid) {
        return new fsSpace(sjvm, config.jvm(), vid);
    }

    private fsSpace(final FileSystem sjvm, final Map<Obj, Obj> jvm, final fURI vid) {
        super(sjvm, jvm, FS_SPACE_TID, vid);
        final Map<Uri, Obj> tempRoutes = new LinkedHashMap<>(this.routes());
        this.at(ROUTE).<Map<Obj, Obj>>jvmAs().clear();
        tempRoutes.entrySet()
                .stream()
                .map(kv -> Map.entry(
                        uri(kv.getKey().toString().replace("~", System.getProperty(USER_HOME))),
                        uri(kv.getValue().toString().replace("~", System.getProperty(USER_HOME)))))
                .forEach(kv -> this.at(ROUTE).<Map<Uri, Obj>>jvmAs().put(kv.getKey(), kv.getValue()));
    }

    public static File staticObjToFile(final Obj obj) {
        try {
            final Space space = Router.global().getSpaceFor(obj.uriValue().basePath());
            if (space instanceof fsSpace) {
                return new File(space.redirect(obj.uriValue().basePath(), true).toString());
            } else {
                throw MTronException.of("obj not embedded in a %s: %s", FS_SPACE_TID, obj);
            }
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
    public Obj fileToObj(final File file, final Map<String, String> qMap) {
        try {
            if (file.exists()) {
                if (file.isFile()) {
                    return readFileAsObj(file, qMap).vid(null);
                } else if (file.isDirectory()) {
                    // A directory's value is stored in a hidden .mtron file
                    final File hidden = new File(file, ".mtron");
                    if (hidden.exists() && hidden.isFile())
                        return readFileAsObj(hidden, qMap).vid(null);
                    return uri(this.redirect(f(file.getPath()), false), DIR_TID, null);
                }
            }
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
        return noobj();
    }

    private Obj readFileAsObj(final File file, final Map<String, String> qMap) throws IOException {
        //LOG.info("MIME %s",qMap);
        final MIME.MIMEType mimeType = qMap.containsKey(MIMEQ_PATTERN.toString()) ?
                MIME.MIMEType.of(qMap.get(MIMEQ_PATTERN.toString())) :
                MIME.MIMEType.fromProbe(file, MIME.MIMEType.fromExtension(file.getName(), MIME.MIMEType.TEXT_PLAIN));
        final FileInputStream fs = new FileInputStream(file);
        byte[] fileBytes = fs.readAllBytes();
        fs.close();
        //final String source = new String(fileBytes, StandardCharsets.UTF_8);
        //final fURI vid = source.startsWith("[-- @<") ? f(source.substring(6, source.indexOf("> --]\n")).trim()) : null;
        LOG.info("reading %s [mime:%s]", file.getPath(), mimeType.value);
        // Use parse (not eval) to avoid executing potential write-side-effect expressions
        // in the file content (e.g. !* or -> sugar that Router.writeToSpace).
        //
        // MIME resolution strategy:
        //
        // 1. Known content TID (HTML, JSON, etc.) → typed string
        //    e.g. html::"<html>...</html>" with predicate validation.
        //    Structural parse (rec::T) is handled by mimeQ postRead.
        //
        // 2. application/x-mtron from query (?mimeq=) → bare string, let mimeQ
        //    postRead handle structural parse with the correct content serializer.
        //
        // 3. application/x-mtron from file extension (.mtron) → parse via
        //    ObjmtronSerializer to produce the native mtron obj.
        //
        // 4. Other serializable MIME types → use the MIME's serializer.
        final boolean fromMimeq = qMap.containsKey(MIMEQ_PATTERN.toString());
        if (mimeType == MIME.MIMEType.APPLICATION_MTRON && fromMimeq)
            return str(new String(fileBytes));  // case 2: defer to mimeQ
        final fURI contentTid = mimeType.toTid();
        if (null != contentTid)
            return str(new String(fileBytes), contentTid, /*vid*/ null);  // case 1
        if (mimeType.hasSerializer())
            return mimeType.serializer().inputBytes(fileBytes);  // cases 3 & 4
        return str(new String(fileBytes));
    }

    @Override
    public void close() {
        // do nothing (can't close file system)
    }

    public Obj objToFile(final fURI vid, final Obj obj) {
        final MIME.MIMEType mimeType = vid.hasQ(MIMEQ_PATTERN) ?
                MIME.MIMEType.of(vid.q(MIMEQ_PATTERN.toString())) :
                MIME.MIMEType.fromType(obj, MIME.MIMEType.APPLICATION_MTRON);
        final File file = new File(this.redirect(vid.qLess(), true).toString());
        if (file.isDirectory())
            throw MTronException.of("unable to write obj to an existing directory with same vid: %s", vid);
        LOG.info("writing %s to %s [mime:%s]", obj, file.getPath(), mimeType.value);
        try {
            if (!file.exists()) {
                new File(f(file.getAbsolutePath()).retract(1).toString()).mkdirs();
                file.createNewFile();
            }
            final fURI selfVID = obj.vid();
            try (final FileOutputStream writer = new FileOutputStream(file, vid.hasQ("append"))) { // TODO: writeq=append/replace?
                if (mimeType.isMtron() && !vid.hasQ("append")) {
                    //  final String at_vid = selfVID == null ? null : "[-- @<" + selfVID + "> --]\n";
                    // if (null != at_vid) writer.write(at_vid.getBytes(StandardCharsets.UTF_8));
                }
                writer.write(mimeType.toBytes(obj.selfVID(null)));
                writer.flush();
            }
            return obj.selfVID(selfVID);
        } catch (final Exception e) {
            throw MTronException.of(e, vid.toString());
        }
    }


    /**
     * When the exact file for a VID doesn't exist, walk up the path to find
     * the nearest parent file.  Parse that file into an Obj and navigate into
     * it using the remaining path segments via {@code at()}.  This is the
     * same type-level navigation that memSpace provides natively: a rec
     * stored flat at {@code test:people/1} can answer
     * {@code test:people/1/name} because the parsed poly handles field access.
     *
     * @param file the original (non-existent) file
     * @param vid  the original VID
     * @param qMap query parameters from the read expression
     * @return an IdObj if a parent file was found and navigated, or null
     */
    /**
     * Simple recursion guard — resets each thread after top-level write completes.
     */
    private static final ThreadLocal<Integer> NEST_GUARD = ThreadLocal.withInitial(() -> 0);

    private IdObj navigateFromParentFile(final File file, final fURI vid, final Map<String, String> qMap) {
        // When called reentrantly from within resolveWrite → directReader → ...
        // return null so the caller creates a fresh file rather than re-parsing
        // the parent and re-entering the Router cycle.
        final int depth = NEST_GUARD.get();
        if (depth > 0)
            return null;
        NEST_GUARD.set(depth + 1);
        try {
            // Walk up from the file path to find an existing parent
            Path parent = file.toPath().getParent();
            while (parent != null && !Files.exists(parent)) {
                parent = parent.getParent();
            }
            if (parent == null || !Files.exists(parent))
                return null;
            final File parentFile = parent.toFile();
            // Parse parent file content
            final Obj parentObj = fileToObj(parentFile, qMap);
            if (parentObj.isNoObj())
                return null;
            // Compute the parent VID in the space's URI namespace
            final fURI parentVid = Space.Helper.routeToSpace(f(parent.toString()), this.routes());
            final String relative = vid.toString().length() >= parentVid.toString().length() ? vid.toString().substring(parentVid.toString().length()) : "";
            if (relative.isEmpty())
                return null;
            final fURI relativeFuri = f(relative.startsWith("/") ? relative.substring(1) : relative);
            final Obj result;
            if (parentObj.isRec()) {
                result = parentObj.asRec().at(uri(relativeFuri));
            } else if (parentObj.isLst()) {
                final String name = relativeFuri.toString();
                if (CommonUtil.isInt(name)) {
                    final int idx = Integer.parseInt(name);
                    final java.util.List<Obj> list = parentObj.lstValue();
                    result = idx >= 0 && idx < list.size() ? list.get(idx) : noobj();
                } else {
                    result = noobj();
                }
            } else {
                return null;
            }
            return result.isNoObj() ? null : IdObj.of(vid, result);
        } finally {
            NEST_GUARD.set(depth);
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
                    final fURI retracted = keyQless.retractPattern();
                    final Path walkRoot = Path.of(Space.Helper.routeFromSpace(retracted, this.routes()).toString());
                    if (!Files.exists(walkRoot))
                        return IteratorUtil.of();
                    final fURI walkRootFuri = Space.Helper.routeToSpace(f(walkRoot.toString()), this.routes());
                    // +/ means "direct children only" — walk exactly one level.
                    // asNode().path().size() counts the +/ segment, so for
                    // local:a/+/ it returns 2 (a, +/) which would reach
                    // grandchildren.  Force depth=1 for branch reads.
                    final int walkDepth = keyQless.hasPattern("#") ? Integer.MAX_VALUE
                            : keyQless.hasPattern("+") ? 1 : keyQless.asNode().path().size();
                    try (final Stream<Path> walk = Files.walk(walkRoot, walkDepth)) {
                        return walk
                                .filter(p -> {
                                    try {
                                        return !p.equals(walkRoot)
                                                && this.redirect(f(p.toString()), false).test(f("#"));
                                    } catch (final Exception e) {
                                        LOG.error(e);
                                        return false;
                                    }
                                })
                                .collect(Collectors.toMap(p -> Space.Helper.routeToSpace(f(p.toString()), this.routes()), p -> {
                                    final File file = p.toFile();
                                    return fileToObj(file, key.qMap());
                                }, Obj::append, LinkedHashMap::new))
                                .entrySet()
                                .stream()
                                .flatMap(kv -> {
                                    if (kv.getValue().isPoly())
                                        return Space.Helper.unrollPoly(kv.getKey(), kv.getValue().as(), key.asNode().asRelative()).stream();
                                    return Stream.of(IdObj.of(kv.getKey(), kv.getValue()));
                                })
                                .iterator();
                    } catch (IOException e) {
                        throw MTronException.of(e);
                    }

                } else {
                    try {
                        final Path vidPath = Path.of(Space.Helper.routeFromSpace(keyQless, this.routes()).toString());
                        final File file = vidPath.toFile();
                        if (!file.exists()) {
                            final IdObj parentResult = navigateFromParentFile(file, keyQless, key.qMap());
                            if (parentResult != null)
                                return IteratorUtil.of(parentResult);
                            return IteratorUtil.of();
                        }
                        final Obj value;
                        if (file.isFile() && file.canExecute() && !(this.hasQ(MIMEQ_PATTERN) && key.hasQ(MIMEQ_PATTERN.toString()))) {
                            final String engine = checkScriptEvaluation(file);
                            if (engine != null) {
                                final fURI execTID = this.vid().extend("exec").extend(file.getName()).dom(ALL.maybe()).rng(ALL_STAR);
                                value = instC(execTID, lst(T(ALL.maybeSome())), (lhs, inst) -> {
                                    LOG.debug("executing: %s => %s", lhs, inst);
                                    return this.executeFile(file, engine, inst.args());
                                });
                            } else {
                                value = this.fileToObj(file, key.qMap());
                            }
                        } else {
                            value = this.fileToObj(file, key.qMap());
                        }
                        // Exact-path read on a directory without .mtron: enumerate children at depth 1
                        if (value.isUri() && file.isDirectory()) {
                            try {
                                if (true)
                                    return IdObj.of(key, fileToObj(file, key.qMap())).iterator();
                                final java.util.List<Path> children = Files.list(file.toPath()).toList();
                                if (!children.isEmpty()) {
                                    final Map<fURI, Obj> collected = new LinkedHashMap<>();
                                    for (final Path child : children) {
                                        final fURI childFuri = Space.Helper.routeToSpace(f(child.toString()), this.routes());
                                        collected.put(childFuri, fileToObj(child.toFile(), key.qMap()));
                                    }
                                    return collected.entrySet().stream()
                                            .flatMap(kv -> {
                                                if (kv.getValue().isPoly())
                                                    return Space.Helper.unrollPoly(kv.getKey(), kv.getValue().as(), key.asNode().asRelative()).stream();
                                                return Stream.of(IdObj.of(kv.getKey(), kv.getValue()));
                                            }).iterator();
                                }
                            } catch (final IOException e) {
                                throw MTronException.of(e);
                            }
                        }
                        return IteratorUtil.of(IdObj.of(key, value));
                    } catch (final Exception e) {
                        throw MTronException.of(e);
                    }
                }
            }
        };
    }

    /**
     * Execute a file with the given engine and arguments.
     * <p>
     * Two modes:
     * <ul>
     *   <li><b>Direct execution</b> — when {@code engine} equals the file's own path.
     *       The file is invoked directly: {@code file arg1 arg2 ...}</li>
     *   <li><b>Script execution</b> — when {@code engine} differs from the file path
     *       (typically resolved from a shebang via {@link #checkScriptEvaluation(File)}).
     *       The engine interprets the script: {@code engine file arg1 arg2 ...}</li>
     * </ul>
     * Each line of stdout is parsed as mtron; parse failures become strings.
     *
     * @param file   the executable or script file
     * @param engine the interpreter path (or the file's own path for direct exec)
     * @param args   the instruction arguments forwarded to the process
     * @return the lines of stdout as objs
     */
    private Obj executeFile(final File file, final String engine, final Poly<?, ?> args) {
        try {
            final boolean isDirectExec = engine.equals(file.getAbsolutePath());
            final List<String> argList = new ArrayList<>();
            argList.add(isDirectExec ? file.getAbsolutePath() : engine);
            if (!isDirectExec)
                argList.add(file.getAbsolutePath());
            argList.addAll(args.elements().map(Str.Helper::cleanString).toList());
            return objs(new ProcessExecutor()
                    .readOutput(true)
                    .command(argList)
                    .redirectOutput(new LogOutputStream() {
                        @Override
                        protected void processLine(final String line) {
                            // This runs in real-time as the process outputs data
                            //System.out.println(line);
                        }
                    })
                    .start()
                    .getFuture().get().getOutput().getLines().stream().map(line -> {
                        try {
                            return ObjmtronSerializer.parse(line);
                        } catch (final Exception e) {
                            return str(line);
                        }
                    }));
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    /**
     * Determine the execution engine for a file.
     * <p>
     * For files with a shebang ({@code #!}), the shebang line is matched against the
     * space's {@link Tokens#SCRIPT} configuration to resolve the engine path.
     * For executable files without a recognized shebang engine, the file's own
     * absolute path is returned (direct execution mode).
     *
     * @param file the file to inspect
     * @return the engine path, or null if the file is not executable
     */
    private String checkScriptEvaluation(final File file) {
        if (!file.canExecute())
            return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String firstLine = reader.readLine();
            if (firstLine != null && firstLine.startsWith("#!")) { // shebang denotes script engine
                final String engine = this.at(SCRIPT).orElse(rec0())
                        .elements()
                        .filter(pair -> firstLine.contains(pair.first().uriValue().toString()))
                        .map(Rel::second)
                        .map(e -> e.uriValue().toString())
                        .findFirst()
                        .orElse(null);
                if (engine != null)
                    return engine;
            }
        } catch (final IOException e) {
            LOG.warn("error reading script file: %s", file, e);
        }
        // Executable without a recognized shebang engine — execute directly
        return file.getAbsolutePath();
    }

   /* @Override
    public Stream<IdObj> readStream(final fURI pattern) {
        final fURI keyQless = pattern.qLess();
        if (pattern.equals(ALL))
            throw MTronException.of("infinite recursive walks on file system currently prohibited");
        if (pattern.hasPattern()) {
            final fURI retracted = keyQless.retractPattern();
            final Path walkRoot = Path.of(Space.Helper.routeFromSpace(retracted, this.routes()).toString());
            if (!Files.exists(walkRoot))
                return Stream.empty();
            final fURI walkRootFuri = Space.Helper.routeToSpace(f(walkRoot.toString()), this.routes());
            try (final Stream<Path> walk = Files.walk(walkRoot,
                    keyQless.hasPattern("#") ? Integer.MAX_VALUE : keyQless.asNode().path().size())) {
                final Map<fURI, Obj> collected = walk
                        .filter(p -> {
                            try {
                                return !p.equals(walkRoot)
                                        && this.redirect(f(p.toString()), false).test(f("#"));
                            } catch (final Exception e) {
                                LOG.error(e);
                                return false;
                            }
                        })
                        .collect(Collectors.toMap(
                                p -> Space.Helper.routeToSpace(f(p.toString()), this.routes()),
                                p -> fileToObj(p.toFile(), pattern.qMap()),
                                Obj::append,
                                LinkedHashMap::new));
                return collected.entrySet().stream()
                        .flatMap(kv -> {
                            final Stream<IdObj> direct = Stream.of(IdObj.of(kv.getKey(), kv.getValue()));
                            if (kv.getValue().isPoly())
                                return Stream.concat(direct,
                                        Space.Helper.unrollPoly(kv.getKey(), kv.getValue().as(), pattern.asNode().asRelative()).stream());
                            return direct;
                        });
            } catch (IOException e) {
                throw MTronException.of(e);
            }
        }
        try {
            final Path vidPath = Path.of(Space.Helper.routeFromSpace(keyQless, this.routes()).toString());
            final File file = vidPath.toFile();
            final Obj value;
            if (file.exists() && file.isFile() && file.canExecute() && !(this.hasQ(MIMEQ_PATTERN) && pattern.hasQ(MIMEQ_PATTERN.toString()))) {
                final String engine = checkScriptEvaluation(file);
                if (engine != null) {
                    final fURI execTID = this.vid().extend("exec").extend(file.getName()).dom(ALL.maybe()).rng(ALL_STAR);
                    value = instC(execTID,
                            lst(),
                            (lhs, inst) -> {
                                LOG.debug("executing: %s => %s", lhs, inst);
                                return this.executeFile(file, engine, inst.args());
                            });
                    return Stream.of(IdObj.of(execTID, value));
                } else {
                    value = this.fileToObj(file, pattern.qMap());
                }
            } else if (file.exists()) {
                value = this.fileToObj(file, pattern.qMap());
            } else {
                value = noobj();
            }
            if (value.isNoObj()) {
                // Try walking up to the nearest parent file and navigating into it
                final IdObj parentResult = navigateFromParentFile(file, keyQless, pattern.qMap());
                if (parentResult != null)
                    return Stream.of(parentResult);
                return Stream.empty();
            }
            // Exact-path read on a directory without .mtron: enumerate children at depth 1
            if (value.isUri() && file.isDirectory()) {
                try (final Stream<Path> children = Files.list(file.toPath())) {
                    final Map<fURI, Obj> collected = children
                            .filter(p -> {
                                try {
                                    return this.redirect(f(p.toString()), false).test(f("#"));
                                } catch (final Exception e) {
                                    LOG.error(e);
                                    return false;
                                }
                            })
                            .collect(Collectors.toMap(
                                    p -> Space.Helper.routeToSpace(f(p.toString()), this.routes()),
                                    p -> fileToObj(p.toFile(), pattern.qMap()),
                                    Obj::append,
                                    LinkedHashMap::new));
                    if (!collected.isEmpty())
                        return collected.entrySet().stream()
                                .flatMap(kv -> {
                                    if (kv.getValue().isPoly())
                                        return Space.Helper.unrollPoly(kv.getKey(), kv.getValue().as(), pattern.asNode().asRelative()).stream();
                                    return Stream.of(IdObj.of(kv.getKey(), kv.getValue()));
                                });
                } catch (final IOException e) {
                    throw MTronException.of(e);
                }
            }
            return value.isNoObj() ? Stream.empty() : Stream.of(IdObj.of(pattern, value));
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }*/

    @Override
    public Stream<IdObj> writeStream(final fURI pattern, final Obj obj) {
        if (pattern.hasPattern()) {
            final List<IdObj> results = new ArrayList<>();
            readStream(pattern).forEach(kv -> {
                this.directWriter().apply(kv.furi(), obj);
                results.add(IdObj.of(kv.furi(), obj));
            });
            return results.stream();
        }
        final Obj result = this.directWriter().apply(pattern, obj);
        if (result.isNoObj())
            return Stream.empty();
        return Stream.of(IdObj.of(pattern, result));
    }

    /**
     * Guards against reentrant writes through the Router during a write cycle.
     * When depth exceeds 2 levels, {@link #write(fURI, Obj)} short-circuits to
     * {@link #directWriter()} to avoid stack overflow through updateRecursion
     * → Router.writeToSpace → resolveWrite → locateBasePoly → readStream.
     */
    /**
     * When the exact file for a VID doesn't exist, walk up to find the nearest
     * parent file, parse it, navigate to the target field with {@code at()},
     * set the value, and write the parent rec back.  This makes field writes
     * (e.g. {@code test:people/1/age >>= 45}) mutate the parent rec file rather
     * than creating orphan child files.
     * <p>
     * Returns the written obj on success, or null if no parent file exists
     * (meaning the caller should create a new file).
     */
    private Obj writeToParentOnField(final File file, final fURI vid, final Obj obj) {
        Path parent = file.toPath().getParent();
        while (parent != null && !Files.exists(parent)) {
            parent = parent.getParent();
        }
        if (parent == null || !Files.exists(parent))
            return null;
        final File parentFile = parent.toFile();
        // Parse parent file content
        final Obj parentObj = fileToObj(parentFile, vid.qMap());
        if (parentObj.isNoObj() || !parentObj.isPoly())
            return null;
        // Compute the parent VID and the remaining relative path
        final fURI parentVid = Space.Helper.routeToSpace(f(parent.toString()), this.routes());
        final String relative = vid.toString().substring(parentVid.toString().length());
        if (relative.isEmpty())
            return null;
        // Navigate into or mutate the parent rec using raw jvm() map access to
        // avoid Poly.MUTABLE → objCheckAndSave → Router.writeToSpace (reentrant).
        // Poly.MUTABLE triggers Router.writeToSpace on any value with a vid(),
        // which would recreate the stack cycle through resolveWrite.
        final Map<Obj, Obj> jvm = parentObj.asRec().jvm();
        final fURI relativeFuri = f(relative.startsWith("/") ? relative.substring(1) : relative);
        if (parentObj.isRec()) {
            jvm.put(uri(relativeFuri), obj);
            // Replace the old parent file with the merged content.
            // Strip vids on child objs to prevent Router writes on re-read.
            final Rec merged = MRec.rec(jvm, REC_TID, null);
            this.objToFile(vid.hasQ(MIMEQ_PATTERN) ? f(parentFile.getPath()).addQ(MIMEQ_PATTERN.toString(), vid.q(MIMEQ_PATTERN.toString())) : f(parentFile.getPath()), merged);
            return obj;
        }
        return null;
    }

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (pattern, obj) -> {
            if (pattern.hasPattern()) {
                this.directReader().apply(pattern).forEachRemaining(kv -> this.write(kv.furi(), obj));
            } else {
                final Path path = Paths.get(this.redirect(pattern.basePath(), true).toString());
                final File file = path.toFile();
                try {
                    if (obj.isNoObj()) {
                        final File delete = new File(this.redirect(pattern, true).toString());
                        if (delete.isDirectory())
                            CommonUtil.deleteDirectory(delete.toPath());
                        else
                            Files.deleteIfExists(delete.toPath());
                        return noobj();
                    } else if (!file.exists()) {
                        // File doesn't exist — try merging into a parent rec file
                        final Obj written = writeToParentOnField(file, pattern, obj);
                        if (written != null)
                            return written;
                        // No parent found — create as a new file
                        this.objToFile(pattern.hasQ(MIMEQ_PATTERN) ? f(path.toString()).addQ(MIMEQ_PATTERN.toString(), pattern.q(MIMEQ_PATTERN.toString())) : f(path.toString()), obj);
                    } else if (file.isDirectory()) {
                        if (!file.exists())
                            file.mkdirs();
                        if (obj.isPoly())
                            this.objToFile(pattern.hasQ(MIMEQ_PATTERN) ? f(new File(file, ".mtron").getPath()).addQ(MIMEQ_PATTERN.toString(), pattern.q(MIMEQ_PATTERN.toString())) : f(new File(file, ".mtron").getPath()), obj);
                    } else {
                        this.objToFile(pattern.hasQ(MIMEQ_PATTERN) ? f(path.toString()).addQ(MIMEQ_PATTERN.toString(), pattern.q(MIMEQ_PATTERN.toString())) : f(path.toString()), obj);
                        /*if (pattern.hasQ("p")) {
                            final Set<PosixFilePermission> currentP = PosixFilePermissions.fromString(Files.getPosixFilePermissions(path).toString());
                            final Set<PosixFilePermission> newP = PosixFilePermissions.fromString(pattern.qValue("p", String.class));
                            if (!currentP.equals(newP))
                                Files.setPosixFilePermissions(file.toPath(), newP);
                        }*/
                    }
                } catch (final Exception e) {
                    throw MTronException.of(e);
                }
            }
            return obj;
        };
    }

}
