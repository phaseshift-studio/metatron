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

package studio.phaseshift.metatron;
/// ///////////////////////////////////////////////

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Feature;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MFail;
import studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.machInstSet;
import studio.phaseshift.metatron.isa.mach.type.LogObj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.router.BasicRouter;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static ch.qos.logback.classic.Level.TRACE;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.block_;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class BootLoader implements Rec, Feature.SelfClone {
    private static final String SUREFIRE_REAL_CLASS_PATH = "surefire.real.class.path";
    /// ////////////////////////////////////////////////////////////////////////
    /// the global variables that must be gc()'d on close
    /// ////////////////////////////////////////////////////////////////////////
    public static boolean BOOTING = true;
    public static boolean TESTING = false;
    public static boolean ONE_SHOT = false;
    public static java.util.function.IntConsumer EXIT_HANDLER = System::exit;
    private static final GraphittyLogger LOG;
    public static Router ROUTER;
    public static Rec ARGS;
    private static volatile ExecutorService EXECUTOR;
    /**
     * Keeps the main thread alive in headless mode (no console REPL to block it).
     */
    private static final CountDownLatch SHUTDOWN_LATCH = new CountDownLatch(1);
    private static final Supplier<ExecutorService> THREAD_POOL_SUPPLIER = () -> {
        return Executors.newCachedThreadPool(r -> new Thread(r, "metatron-" + Thread.currentThread().getId()));
    };

    static {
        LOG = Graphitty.log(new BootLoader());
        //EXECUTOR = Executors.newCachedThreadPool(new mThreadFactory());
        EXECUTOR = THREAD_POOL_SUPPLIER.get();
    }

    public static ExecutorService getExecutor() {
        return EXECUTOR;
    }

    public static void main(final String[] args) throws IOException {
        // --- CLI flag parsing ------------------------------------------------
        String bootFile = null;
        String evalExpr = null;
        String filePath = null;
        String extraArgs = null;
        boolean pipeMode = false;
        boolean quiet = false;
        boolean showVersion = false;
        boolean showHelp = args.length == 0;
        String legacyArgs = null;

        int i = 0;
        while (i < args.length) {
            final String arg = args[i];
            switch (arg) {
                case "-b", "--boot" -> {
                    if (++i < args.length) bootFile = args[i];
                    else {
                        System.err.println("metatron: -b requires a file argument");
                        EXIT_HANDLER.accept(1);
                    }
                }
                case "-e", "--eval" -> {
                    if (++i < args.length) evalExpr = args[i];
                    else {
                        System.err.println("metatron: -e requires an expression argument");
                        EXIT_HANDLER.accept(1);
                    }
                }
                case "-f", "--file" -> {
                    if (++i < args.length) filePath = args[i];
                    else {
                        System.err.println("metatron: -f requires a file argument");
                        EXIT_HANDLER.accept(1);
                    }
                }
                case "-p", "--pipe" -> pipeMode = true;
                case "-q", "--quiet" -> quiet = true;
                case "-v", "--version" -> showVersion = true;
                case "-h", "--help" -> showHelp = true;
                default -> {
                    // positional: [rec expr], legacy, or bare expression
                    if (arg.startsWith("[")) {
                        if (args.length == 1)
                            legacyArgs = arg;
                        else
                            extraArgs = arg;
                    } else if (evalExpr == null && filePath == null) {
                        evalExpr = arg;          // bare expression (no -e needed)
                        showHelp = false;
                    } else {
                        System.err.println("metatron: unknown option: " + arg);
                        System.err.println("Try 'metatron --help' for more information.");
                        EXIT_HANDLER.accept(1);
                    }
                }
            }
            i++;
        }

        // --- Immediate-exit flags -------------------------------------------
        if (showVersion) {
            System.out.println("metatron " + METATRON_VERSION);
            EXIT_HANDLER.accept(0);
        }
        if (showHelp) {
            printHelp();
            EXIT_HANDLER.accept(0);
        }

        // --- Boot file resolution: -b flag -> $METATRON_BOOT env -> null ----
        if (bootFile == null)
            bootFile = System.getenv("METATRON_BOOT");

        // --- Suppress diagnostic output for eval/piping mode -----------------
        if (quiet || evalExpr != null || filePath != null)
            LogObj.setSLF4J("error");

        // --- Build ARGS rec for load() --------------------------------------
        if (legacyArgs != null) {
            // legacy: single mtron-expression string
            try {
                ARGS = ObjmtronSerializer.parse(legacyArgs).as();
                if (!quiet) LogObj.setSLF4J(ARGS.has(uri("log")) ? ARGS.at(uri("log")).uriValue().toString() : "info");
            } catch (final Exception e) {
                LOG.error(e);
                EXIT_HANDLER.accept(1);
            }
            if (!quiet) LOG.info("unparsed boot args:\n%s", legacyArgs);
        } else {
            ARGS = rec();
            // merge extra mtron-rec args (e.g. '[log=>info,a=>b]')
            if (extraArgs != null) {
                try {
                    ARGS.jvm().putAll(ObjmtronSerializer.parse(extraArgs).as().jvmAs());
                } catch (final Exception e) {
                    LOG.error(e);
                    EXIT_HANDLER.accept(1);
                }
            }
            // -b flag takes priority over any boot in extraArgs
            if (bootFile != null)
                ARGS.jvm().put(uri(BOOT), uri(bootFile));
            if (evalExpr != null || filePath != null || quiet)
                ARGS.jvm().put(uri("log"), uri("error"));
        }

        // --- Parse boot-file header for embedded args -----------------------
        if (ARGS.has(BOOT)) {
            final Path bootPath = Paths.get(f(Paths.get("").toAbsolutePath().normalize().toString()).extend(ARGS.at(BOOT).uriValue()).toString());
            fsSpace.makeFile(bootPath).vid(f("boot/file"));
            try (final FileInputStream bootReader = new FileInputStream(bootPath.toFile())) {
                final List<String> bootLines = Arrays.asList(new String(bootReader.readAllBytes()).split("\n"));
                final int argsStart = IteratorUtil.indexedStream(bootLines.iterator()).filter(x -> x.get1().startsWith("[== boot args ==]")).map(x -> x.get0()).findFirst().orElse(-1);
                if (argsStart != -1) {
                    final int argsEnd = IteratorUtil.indexedStream(bootLines.iterator()).filter(x -> x.get1().startsWith("[===============]")).map(x -> x.get0()).findFirst().orElse(-1);
                    if (argsEnd != -1) {
                        final List<String> bootArgs = bootLines.subList(argsStart + 1, argsEnd);
                        if (!quiet) LOG.info("header boot args:\n%s", String.join("\n", bootArgs));
                        ARGS.jvm().putAll(ObjmtronSerializer.parse(String.join("\n", bootArgs)).as().jvmAs());
                    } else {
                        LOG.warn("boot args section not properly closed in %s", bootPath);
                    }
                }
            } catch (IOException e) {
                LOG.error(e);
                EXIT_HANDLER.accept(1);
            }
        }

        // --- Boot the system ------------------------------------------------
        if (evalExpr != null || filePath != null)
            ONE_SHOT = true;
        BootLoader.load(ARGS);

        // --- Evaluate expression or file, print result to stdout, exit ------
        if (evalExpr != null || filePath != null) {
            int exitCode = 0;
            try {
                // Read piped stdin (-p flag, _ placeholder binds to this)
                Obj stdinObj = noobj();
                if (pipeMode) {
                    final String stdinStr = new String(System.in.readAllBytes()).trim();
                    if (!stdinStr.isEmpty())
                        stdinObj = mParser.parse(stdinStr).apply();
                }

                // Evaluate expression
                final Obj result;
                if (!stdinObj.isNoObj()) {
                    // Piped: parsed expression applied with stdin as lhs
                    // e.g. "_ + 5" with stdin "3" → plus(_, 5).apply(3) → 8
                    result = mParser.parse(evalExpr).apply(stdinObj);
                } else if (evalExpr != null) {
                    result = mParser.eval(evalExpr);
                } else {
                    result = mParser.eval(new java.io.File(filePath));
                }

                if (!result.isNoObj())
                    System.out.print(ObjmtronSerializer.single().write(result) + "\n");
            } catch (final Exception e) {
                System.err.println("metatron: " + e.getMessage());
                exitCode = 1;
            } finally {
                close();
                EXIT_HANDLER.accept(exitCode);
            }
        }
    }

    private static void printHelp() {
        final String help = """
                            Usage: metatron [OPTIONS]
                            
                            A distributed data-oriented computing language and virtual machine.
                            
                            Options:
                              -b, --boot <file>    Boot configuration file (env: $METATRON_BOOT)
                              -e, --eval <expr>    Evaluate an mtron expression
                              -f, --file <file>    Evaluate an mtron source file
                              -p, --pipe           Read stdin as pipe input
                              -q, --quiet          Suppress diagnostic output (for piping)
                              -v, --version        Print version and exit
                              -h, --help           Show this help message
                            
                              A bare positional argument is treated as an -e expression.
                            
                            Examples:
                              metatron "1 + 2"
                              metatron "1 + 2" | metatron -p "_ + 5"
                              metatron -f myapp.mtron
                              metatron -b boot/boot.mtron
                              metatron -b boot/boot.mtron "*/sys/env/HOME"
                              METATRON_BOOT=boot/boot.mtron metatron "*/sys/env/USER"
                            
                            """;
        System.out.print(help);
    }

    public static void load(final Rec args) {
        if (BOOTING) {
            // Re-create executor if a previous test run shut it down
            if (EXECUTOR == null || EXECUTOR.isShutdown())
                EXECUTOR = THREAD_POOL_SUPPLIER.get();
            /// /// PARSING OF BOOT ARGUMENT REC /// ///
            LOG.info("final boot args:\n%s", args);
            if (args.has(BOOT))
                args.at(uri(BOOT), f(Paths.get("").toAbsolutePath().normalize().toString()).extend(args.at(BOOT).uriValue()).toUri(), MUTABLE);
            LogObj.setSLF4J(args.at(uri("log")).orElse(uri("warn")).uriValue().toString());
            LOG.info("%s", Graphitty.sillyPrint("booting metatron", true, true));
            /// /// INITIAL PHASE OF BOOT PROCESS /// ///
            Runtime.getRuntime().addShutdownHook(new Thread(BootLoader::close));
            LOG.info("available instruction sets\n\t(via %s%s)%s", "META-INF/services/",
                    InstSet.class.getCanonicalName(),
                    InstSet.loadInstSetProvider(ALL)
                            .map(p -> p.type().getAnnotation(InstSet.JREService.class).vid())
                            .reduce("", (a, b) -> a + "\n\t\t" + b));
            /// /// SET TYPE CHECKER STAGES /// ///
            final Rec typer = args.at("typer/stage")
                    .orElse(Stream.of(TypeCheck.values())
                            .map(tc -> rel(uri(tc.name()), tc == TypeCheck.code_resolve ? BOOL_FALSE : BOOL_TRUE))
                            .collect(new CommonUtil.RecCollector()));
            TypeCheck.init(typer.tid(TYPER_TYPE_TID).as());
            LOG.info("initial enabled typer stages: %s", typer);
            /// /// START OF BOOTING PROCESS /// ///
            String hostname = null;
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (final Exception e) {
                hostname = System.getenv(HOSTNAME);
            }
            if (null == hostname)
                LOG.warn("booting metatron on a non-networked jvm");
            else {
                args.at(LOCAL, uri(hostname), MUTABLE);
            }
            final fURI SYS_VID = f("/sys");
            final Space sysSpace = memSpace.of(SYS_VID.extend(ALL), null);
            sysSpace.jvm().put(uri(QPROC), lst(QCollection.docQ(), QCollection.subq(), QCollection.incrQ(), QCollection.mimeQ()));
            /// CREATE A ROUTER AND ATTACH IT TO SYS
            ROUTER = new BasicRouter(SYS_VID.extend("router"));
            sysSpace.write(ROUTER.vid(), ROUTER);
            Router.global().addSpace(sysSpace.self(sysSpace.jvm(), sysSpace.tid(), SYS_VID).as());
            LOG.debug("router location: %s", ROUTER.vid());
            sysSpace.write("/sys/typer/stage", typer);
            // LOAD STDIO INSTRUCTIONS
            sysSpace.write("/sys/io/stdout", docWrap(instC(f("/sys/io/stdout").dom(ALL.maybe()).rng(ALL.maybe()), lst(T(ALL.maybe())), (lhs, inst) -> {
                final Object arg = inst.arg(0).jvm();
                if (arg != null)
                    System.out.println(arg);
                return lhs;
            }), "maybe an obj", "maybe an obj", Map.of(), "prints arg jvm object to the terminal and emits lhs obj as rhs obj"));
            sysSpace.write("/sys/io/stdin", block_(docWrap(instC(f("/sys/io/stdin").dom(ALL.maybe()).rng(STR_TID), lst(), (lhs, inst) -> {
                final Scanner scanner = new Scanner(System.in);
                final String input = scanner.nextLine();
                return str(input);
            }), "maybe an obj", "a single line of input", Map.of(), "read a line of input from the running terminal")).tryToInst());
            Router.global().registerRedirect(f("stdout"), f("/sys/io/stdout"));
            Router.global().registerRedirect(f("stdin"), f("/sys/io/stdin"));
            ///  LOAD SYSTEM ENVIRONMENTAL VARIABLES
            System.getenv().entrySet().stream()
                    .map(kv -> new AbstractMap.SimpleEntry<>(SYS_VID.extend("env").extend(kv.getKey()), str(kv.getValue())))
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(fURI::name)))
                    .forEach(kv -> sysSpace.write(kv.getKey(), kv.getValue()));
            /// LOAD DEFAULT INSTRUCTION SET (/m and /m/mach)
            final InstSet m = new mInstSet();
            Router.global().addSpace(m);  // explicit registration after full construction
            Router.writeToSpace(m);
            m.setup();
            final InstSet mach = new machInstSet();
            Router.global().addSpace(mach);  // explicit registration after full construction
            Router.writeToSpace(mach);
            sysSpace.write("/sys/space/stack", Router.stack());
            mach.setup();
            /// WRITE THE BOOT ARGS TO THE ROUTER STACK
            Router.writeToSpace(f("boot/args"), args);
            ///  ADD INCRQ PROCESSOR TO SYS FOR AUTO INCREMENTING FAIL STACK
            MFail.FAIL_STACK_PATTERN = args.at("fail_stack_pattern").orElse(uri("/sys/fail?incrq=./+")).uriValue();
            ROUTER.start();
            ///////////////////////////////////////////////////////////////
            if (args.has(uri(Tokens.BOOT))) {
                LOG.info("\t {{m}}BEGIN:{{g}} evaluating provided boot loader: {{b}}%s{{X}}\n", args.at(uri(Tokens.BOOT)).uriValue());
                try {
                    final Path bootPath = Path.of(args.at(Tokens.BOOT).uriValue().toString());
                    fsSpace.makeFile(bootPath).vid(f("boot/file"));
                    final long count = mParser.eval(bootPath.toFile(), e -> LOG.error("%s\n%s", e.getCause() == null ? e.getMessage() : e.getCause().getMessage(), e)).count();
                    LOG.info("processed boot input: {{b}}%s{{/b}} {{g}}[{{y}}out: %d{{/y}}]{{/g}}", args.at(Tokens.BOOT).uriValue(), count);
                } catch (final IOException e) {
                    LOG.error(e);
                    EXIT_HANDLER.accept(0);
                }
                LOG.info("\t {{m}}END:{{g}} evaluating provided boot loader: {{b}}%s{{X}}\n", args.at(uri(Tokens.BOOT)).uriValue());
            }
            final Obj log = Router.writeToSpace(LogObj.of(rec(args.at(LOGG).orElse(uri(TRACE.levelStr)), lst(uri(ALL))), SYS_VID.extend(LOGG)));
            LOG.info("logging now handled by %s", log);
            ///////////////////////////////////////////////////////////////
            LOG.info("%s {{g}}successfully{{/g}} booted", Graphitty.sillyPrint("metatron", true, true));
            BOOTING = false;
            System.gc();
            /// /// END OF BOOTING PROCESS /// ///
            // If a console (or other blocking component) was loaded from the boot script, it
            // will have blocked mParser.eval() above and we never reach here.  In headless mode
            // (no console, not testing) we reach here immediately -- park the main thread so the
            // JVM stays alive until SIGTERM triggers close() → SHUTDOWN_LATCH.countDown().
            final String surefireClassPath = System.getProperty(SUREFIRE_REAL_CLASS_PATH);
            if (!TESTING && !ONE_SHOT && (surefireClassPath == null || surefireClassPath.isEmpty())) {
                Thread.currentThread().setName("metatron-main");
                try {
                    SHUTDOWN_LATCH.await();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            LOG.warn("boot processes previously completed -- ignoring request to boot");
        }
    }

    public static void close() {
        try {
            BOOTING = true;
            SHUTDOWN_LATCH.countDown();  // release headless main-thread park (no-op if already 0)
            if (!ONE_SHOT)
                LOG.none("\n");
            if (Router.loaded())
                Router.global().close();
            ROUTER = null;
            ARGS = null;
            EXECUTOR.shutdown();
            System.gc();
            LOG.info("%s {{g}}successfully{{/g}} shutdown", Graphitty.sillyPrint("metatron", true, true));
        } catch (final Exception e) {
            LOG.error("%s {{r}}unsuccessfully{{/r}} shutdown:\n\t", Graphitty.sillyPrint("metatron", true, true), e);
        }
    }

    @Override
    public Map<Obj, Obj> jvm() {
        return ARGS.jvm();
    }

    @Override
    public Rec self(final Object jvm, final fURI tid, final fURI vid) {
        return this;
    }

    @Override
    public fURI tid() {
        return f(Tokens.BOOT);
    }

    @Override
    public fURI vid() {
        return f(Tokens.BOOT);
    }

    @Override
    public Rec clone(final Object jvm, final fURI tid, final fURI vid) {
        return Feature.SelfClone.super.clone(jvm, tid, vid);
    }

    @Override
    public Obj clone() {
        return Feature.SelfClone.super.clone();
    }

}
