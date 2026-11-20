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

package studio.phaseshift.metatron.isa.m.sys.space;

import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.stream.LogOutputStream;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.web.type.MIME;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.MIMEQ_PATTERN;
import static studio.phaseshift.metatron.isa.m.sys.sysInstSet.FS_SPACE_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class fsSpace extends AbstractSpace<FileSystem> {

    private static final Uri NOOBJ_URI = uri(f(""), URI_TID.zero(), null);

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
            final Space space = Machine.current().memory().getSpaceFor(obj.uriValue().basePath());
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
                    // A directory stores nothing (its value is its subtree, which Space.Helper derives: the reader
                    // never reads one as a value); a direct caller gets its uri.
                    return uri(this.redirect(f(file.getPath()), false));//.asBranch());
                }
            }
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
        return noobj();
    }

    /**
     * Whether these bytes are a well-formed UTF-8 text — the test that decides whether a document can be read as
     * a String at all. Malformed input is reported rather than replaced, since a replacement character is exactly
     * the corruption this guards against.
     */
    private static boolean isUtf8(final byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (final CharacterCodingException e) {
            return false;
        }
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
        LOG.debug("reading %s [mime:%s]", file.getPath(), mimeType.value);
        // Use parse (not eval) to avoid executing potential write-side-effect expressions
        // in the file content (e.g. !* or -> sugar that Machine.write).
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
        // A document that is not text is bytes — never a String. Decoding binary through UTF-8 and re-encoding it
        // on the way out mangles every non-ASCII byte, so an image reaches the browser as a broken image (a
        // 1337-byte favicon was served as 2231). Two tests, because either one alone leaves a hole: the declared
        // type (an image is not text), and the bytes themselves — an unlisted extension falls back to text/plain
        // (pdf, sqlite, woff2), so the declared type would still feed binary through a String. An explicit
        // ?mimeq= is the client stating the format, so it is honoured either way.
        if (null == mimeType)
            return bytes(fileBytes);
        if (!mimeType.isText() || (!fromMimeq && !isUtf8(fileBytes)))
            return bytes(fileBytes);
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
        MIME.MIMEType mimeType = vid.hasQ(MIMEQ_PATTERN) ?
                MIME.MIMEType.of(vid.q(MIMEQ_PATTERN.toString())) :
                MIME.MIMEType.fromType(obj, MIME.MIMEType.APPLICATION_MTRON);
        final File file = new File(this.redirect(vid.qLess(), true).toString());
        mimeType = MIME.MIMEType.fromProbe(file, mimeType);
        if (file.isDirectory())
            throw MTronException.of("unable to write obj to an existing directory with same vid: %s", vid);
        LOG.debug("writing %s to %s [mime:%s]", obj, file.getPath(), mimeType.value);
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
     * ONLY WHAT IS STORED (Marko, 2026-11-19), the filesystem's own way: a file stores its obj, and a directory stores
     * nothing -- its value is its subtree, which Space.Helper derives (a container written at a directory is laid out
     * in it). A pattern walks the files and directories it reaches, each directory an interior node (noobj), so a
     * {@code +} listing names the level without reading below it. An address inside a stored file (a field of a rec,
     * a slot of a lst) is no file: Space.Helper reads it from the file above it.
     */
    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        return (key) -> {
            final fURI keyQless = key.qLess();
            if (key.equals(ALL))
                throw MTronException.of("infinite recursive walks on file system currently prohibited");
            if (key.hasPattern())
                return this.walk(key, false).iterator();
            try {
                final File file = Path.of(Space.Helper.routeFromSpace(keyQless, this.routes()).toString()).toFile();
                if (!file.exists())
                    return IteratorUtil.of();
                if (file.isDirectory())
                    return IteratorUtil.of();
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
                return IteratorUtil.of(IdObj.of(key, value));
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        };
    }

    /**
     * the files and directories a pattern reaches, to the depth its wildcards walk (a {@code +} per level, {@code #}
     * all the way): a file with its obj, a directory as an interior node (noobj) -- each one the pattern matches (a
     * walk reaches everything down to its depth). A pattern WRITE also lands in the slots of each stored poly
     * ({@code unroll}), so a write fills a stored row or column in place.
     */
    private List<IdObj> walk(final fURI key, final boolean unroll) {
        final fURI keyQless = key.qLess();
        final fURI nodePattern = keyQless.asNode();
        final Path walkRoot = Path.of(Space.Helper.routeFromSpace(keyQless.retractPattern(), this.routes()).toString());
        if (!Files.exists(walkRoot))
            return List.of();
        // The walk depth is the number of + wildcards in the pattern:
        // +/ means direct children (depth 1), +/+ means grandchildren
        // (depth 2), etc.  # means unbounded.
        final int walkDepth = keyQless.hasPattern("#") ? Integer.MAX_VALUE
                : keyQless.hasPattern("+") ? (int) keyQless.asNode().path().stream().filter("+"::equals).count()
                : keyQless.asNode().path().size();
        try (final Stream<Path> walk = Files.walk(walkRoot, walkDepth)) {
            return walk
                    .filter(p -> {
                        try {
                            return !p.equals(walkRoot) && this.redirect(f(p.toString()), false).test(f("#"));
                        } catch (final Exception e) {
                            LOG.error(e);
                            return false;
                        }
                    })
                    .map(p -> IdObj.of(Space.Helper.routeToSpace(f(p.toString()), this.routes()),
                            p.toFile().isDirectory() ? noobj() : this.fileToObj(p.toFile(), key.qMap())))
                    .flatMap(id -> Stream.concat(
                            id.furi().test(nodePattern) ? Stream.of(id) : Stream.empty(),
                            unroll && id.obj().isPoly()
                                    ? Space.Helper.unrollPoly(id.furi(), id.obj().as(), nodePattern.asRelative()).stream()
                                    : Stream.empty()))
                    .toList();
        } catch (final IOException e) {
            throw MTronException.of(e);
        }
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
                        // a line of program output is mtron literal only when it parses; the parser
                        // reports plain text as a fail::T VALUE, not a throw, so test the result
                        final Obj parsed = ObjmtronSerializer.parse(line);
                        return parsed.stream().anyMatch(Obj::isFail) ? str(line) : parsed;
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
        // avoid Poly.MUTABLE → objCheckAndSave → Machine.write (reentrant).
        // Poly.MUTABLE triggers Machine.write on any value with a vid(),
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
                // a pattern write lands on what is stored -- the files and, filling them in place, their slots -- and a
                // delete on every file and directory it matches, each removed whole (deleting the slots of a lst one by
                // one would shrink it under its own indices); a directory holds no value to write otherwise
                this.walk(pattern, !obj.isNoObj()).stream()
                        .filter(id -> obj.isNoObj() || !id.obj().isNoObj())
                        .forEach(id -> this.write(id.furi(), obj));
            } else {
                final Path path = Paths.get(this.redirect(pattern.basePath(), true).toString());
                final File file = path.toFile();
                try {
                    if (obj.isNoObj()) {
                        final File delete = new File(this.redirect(pattern, true).toString());
                        if (delete.isDirectory())
                            CommonUtil.deleteDirectory(delete.toPath());
                        // a slot inside a stored poly file (m/2/0 of the row file m/2) is no file of its own:
                        // deleting it deletes nothing, where the filesystem would refuse a path through a file
                        else if (delete.exists())
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
                        // a container written at a directory is laid out in it, an entry per slot (Marko, 2026-11-19:
                        // containers written to a space are laid out in uri space): a rec reads back as itself, a lst
                        // as a rec of its indices. A key with a wildcard is a segment of the data, and a path holding it
                        // would be written as a pattern -- every file it matches -- so such a container is refused
                        if (obj.isPoly()) {
                            final List<Tuple.Pair<fURI, Obj>> slots = obj.<Poly<?, ?>>as().indexedStream()
                                    .filter(slot -> null != slot.jvm().get1())
                                    .map(slot -> Tuple.Pair.with(f(slot.jvm().get0().jvm().toString()), (Obj) slot.jvm().get1()))
                                    .toList();
                            if (slots.stream().anyMatch(slot -> slot.get0().hasPattern()))
                                throw MTronException.of("%s is a directory: a container with a wildcard key cannot be laid out in it: %s", pattern, obj);
                            slots.forEach(slot -> this.write(pattern.extend(slot.get0()), slot.get1()));
                        }
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
