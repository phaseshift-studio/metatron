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
import studio.phaseshift.metatron.isa.m.type.Rec;
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
import studio.phaseshift.metatron.isa.m.type.impl.MFail;
import studio.phaseshift.metatron.isa.mach.io.space.fs.fsSpace;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.machInstSet;
import studio.phaseshift.metatron.isa.mach.type.LogObj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.router.BasicRouter;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
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

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public class BootLoader implements Rec, Feature.SelfClone {

    /// ////////////////////////////////////////////////////////////////////////
    /// the global variables that must be gc()'d on close
    /// ////////////////////////////////////////////////////////////////////////
    public static boolean BOOTING = true;
    public static boolean TESTING = false;
    private static final GraphittyLogger LOG;
    public static Router ROUTER;
    public static Rec ARGS;
    private static volatile ExecutorService EXECUTOR;
    /** Keeps the main thread alive in headless mode (no console REPL to block it). */
    private static final CountDownLatch SHUTDOWN_LATCH = new CountDownLatch(1);


    static {
        LOG = Graphitty.log(new BootLoader());
        //EXECUTOR = Executors.newCachedThreadPool(new mThreadFactory());
        EXECUTOR = Executors.newCachedThreadPool(r -> new Thread(r, "metatron-" + Thread.currentThread().getId()));
    }

    public static ExecutorService getExecutor() {
        return EXECUTOR;
    }

    public static void main(final String[] args) throws IOException {
        if (args.length == 1 && args[0].equals("--help")) {
            LOG.none("""
                     
                     %s: %s
                       {{g}}({{X}}arguments must be provided as a single mtron %s{{g}}){{X}}
                       \te.g. %s
                       {{r}}----------------------------------------------------------{{/r}}
                       boot args:
                         %s
                         %s
                         %s
                         %s
                     
                       example:
                         %s
                     
                     """,
                    Graphitty.sillyPrint("metatron", true, true),
                    Graphitty.sillyPrint("reference-oriented computing", true, true),
                    T(REC_TID),
                    rec(uri("k1"), uri("v1"), uri("..."), uri("..."), uri("kn"), uri("vn")),
                    rel(uri("log"), objs(uri("info"), uri("debug"), uri("warn"), uri("error"), uri("trace"))),
                    rel(uri("host"), uri("ws://0.0.0.0:8888")),
                    rel(uri("cluster"), objs(uri("ws://a.local:8888"), uri("ws://b.local:8888"), uri("..."))),
                    rel(uri("boot"), uri("conf/boot.mtron")),
                    "metatron '[boot=><conf/boot.mtron>,log=>info,host=><ws://0.0.0.0:8888>,cluster=>{<ws://localhost:8887>}]'");
            System.exit(0);
        } else {
            try {
                ARGS = args.length > 0 ? ObjmtronSerializer.parse(args[0]).as() : rec();
                LogObj.setSLF4J(ARGS.has(uri("log")) ? ARGS.at(uri("log")).uriValue().toString() : "info");
            } catch (final Exception e) {
                LOG.error(e);
                System.exit(0);
            }
            if (args.length > 0)
                LOG.info("unparsed boot args:\n%s", args[0]);
            ARGS = args.length > 0 ? ObjmtronSerializer.parse(args[0]).as() : rec();
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
                            LOG.info("header boot args:\n%s", String.join("\n", bootArgs));
                            ARGS.jvm().putAll(ObjmtronSerializer.parse(String.join("\n", bootArgs)).as().jvmAs());
                        } else {
                            LOG.warn("boot args section not properly closed in %s", bootPath);
                        }
                    }
                } catch (IOException e) {
                    LOG.error(e);
                    System.exit(0);
                }
            }
            BootLoader.load(ARGS);
        }
    }

    public static void load(final Rec args) {
        if (BOOTING) {
            // Re-create executor if a previous test run shut it down
            if (EXECUTOR == null || EXECUTOR.isShutdown())
                EXECUTOR = Executors.newCachedThreadPool(r -> new Thread(r, "metatron-" + Thread.currentThread().getId()));
            /// /// PARSING OF BOOT ARGUMENT REC /// ///
            LOG.info("final boot args:\n%s", args);
            if (args.has(BOOT))
                args.at(uri(BOOT), f(Paths.get("").toAbsolutePath().normalize().toString()).extend(args.at(BOOT).uriValue()).toUri(), MUTABLE);
            LogObj.setSLF4J(args.has(uri("log")) ? args.at(uri("log")).uriValue().toString() : "info");
            LOG.info("%s", Graphitty.sillyPrint("booting metatron", true, true));
            Runtime.getRuntime().addShutdownHook(new Thread(BootLoader::close));
            LOG.info("available instruction sets\n\t(via %s%s)%s", "META-INF/services/",
                    InstSet.class.getCanonicalName(),
                    InstSet.loadInstSetProvider(ALL)
                            .map(p -> p.type().getAnnotation(InstSet.JREService.class).vid())
                            .reduce("", (a, b) -> a + "\n\t\t" + b));
            fURI localAuthority = null;
            /// /// START OF BOOTING PROCESS /// /// allow boot description to be read from a mtron file
            String hostname = null;
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (final Exception e) {
                hostname = System.getenv(HOSTNAME);
            }
            if (null == hostname)
                LOG.warn("booting metatron on a non-networked jvm");
            else {
                localAuthority = args.at(HOST).orElse(uri(WS + "://" + hostname + ".local" + ":" + 8999)).uriValue();
                args.at(LOCAL, uri(hostname), MUTABLE);
            }
            final fURI SYS_VID = f("/sys");
            final Space sysSpace = memSpace.of(SYS_VID.extend("#"), null);
            sysSpace.jvm().put(uri(QPROC), lst(QCollection.docQ(), QCollection.subq(), QCollection.incrQ()));
            /// CREATE A ROUTER AND ATTACH IT TO SYS
            ROUTER = new BasicRouter(localAuthority, SYS_VID.extend("router"));
            sysSpace.write(ROUTER.vid(), ROUTER);
            Router.global().addSpace(sysSpace.self(sysSpace.jvm(), sysSpace.tid(), SYS_VID).as());
            LOG.debug("router location: %s", ROUTER.vid());
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
                    System.exit(0);
                }
                LOG.info("\t {{m}}END:{{g}} evaluating provided boot loader: {{b}}%s{{X}}\n", args.at(uri(Tokens.BOOT)).uriValue());
            }
            final Obj log = Router.writeToSpace(LogObj.of(rec(args.at("log").orElse(uri("trace")), lst(uri(ALL))), SYS_VID.extend("log")));
            LOG.info("logging now handled by %s", log);
            ///////////////////////////////////////////////////////////////
            LOG.info("%s {{g}}successfully{{/g}} booted", Graphitty.sillyPrint("metatron", true, true));
            BOOTING = false;
            System.gc();
            /// /// END OF BOOTING PROCESS /// ///
            // If a console (or other blocking component) was loaded from the boot script, it
            // will have blocked mParser.eval() above and we never reach here.  In headless mode
            // (no console) we reach here immediately -- park the main thread so the JVM stays
            // alive until SIGTERM triggers close() → SHUTDOWN_LATCH.countDown().
            try {
                SHUTDOWN_LATCH.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            LOG.warn("boot processes previously completed -- ignoring request to boot");
        }
    }

    public static void close() {
        try {
            BOOTING = true;
            SHUTDOWN_LATCH.countDown();  // release headless main-thread park (no-op if already 0)
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
