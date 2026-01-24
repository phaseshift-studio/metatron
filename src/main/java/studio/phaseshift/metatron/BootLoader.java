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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.ai.llm.llmInstSet;
import studio.phaseshift.metatron.lang.core.m.inst.mInstSet;
import studio.phaseshift.metatron.lang.core.m.parser.mParser;
import studio.phaseshift.metatron.lang.core.m.type.Feature;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.mach.machInstSet;
import studio.phaseshift.metatron.lang.db.grph.inst.grphInstSet;
import studio.phaseshift.metatron.lang.db.kv.inst.kvInstSet;
import studio.phaseshift.metatron.lang.db.kv.kvSpace;
import studio.phaseshift.metatron.lang.db.tabl.tablInstSet;
import studio.phaseshift.metatron.lang.db.vec.vecInstSet;
import studio.phaseshift.metatron.lang.iot.iotInstSet;
import studio.phaseshift.metatron.lang.net.clstr.clstrInstSet;
import studio.phaseshift.metatron.lang.net.web.webInstSet;
import studio.phaseshift.metatron.lang.sys.fs.fileSpace;
import studio.phaseshift.metatron.lang.sys.fs.fsInstSet;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.sys.router.impl.MRouter;
import studio.phaseshift.metatron.lang.sys.sysInstSet;
import studio.phaseshift.metatron.lang.sys.ui.uiInstSet;
import studio.phaseshift.metatron.lang.util.LogObj;
import studio.phaseshift.metatron.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.ui.graphitty.GraphittyLogger;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static studio.phaseshift.metatron.Tokens.BOOT;
import static studio.phaseshift.metatron.Tokens.WS;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.ai.llm.llmInstSet.LLM_INSTSET_TID;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.REC_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.core.mach.machInstSet.MACH_INSTSET_TID;
import static studio.phaseshift.metatron.lang.db.grph.inst.grphInstSet.GRPH_INSTSET_TID;
import static studio.phaseshift.metatron.lang.db.kv.inst.kvInstSet.KV_INSTSET_TID;
import static studio.phaseshift.metatron.lang.db.tabl.tablInstSet.TABL_INSTSET_TID;
import static studio.phaseshift.metatron.lang.db.vec.vecInstSet.VEC_INSTSET_TID;
import static studio.phaseshift.metatron.lang.iot.iotInstSet.IOT_INSTSET_TID;
import static studio.phaseshift.metatron.lang.net.clstr.clstrInstSet.CLSTR_INSTSET_TID;
import static studio.phaseshift.metatron.lang.net.web.webInstSet.WEB_INSTSET_TID;
import static studio.phaseshift.metatron.lang.sys.fs.fsInstSet.FS_INSTSET_TID;
import static studio.phaseshift.metatron.lang.sys.sysInstSet.SYS_OBJ_TID;
import static studio.phaseshift.metatron.lang.sys.sysInstSet.SYS_TID;
import static studio.phaseshift.metatron.lang.sys.ui.uiInstSet.UI_INSTSET_TID;

public class BootLoader implements Rec, Feature.SelfClone {

    /// ////////////////////////////////////////////////////////////////////////
    /// the global variables that must be gc()'d on close
    /// ////////////////////////////////////////////////////////////////////////
    public static boolean BOOTING = true;
    private static final GraphittyLogger LOG;
    public static Router ROUTER;
    public static Rec ARGS;
    public static boolean TYPE_CHECK = true;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    static {
        LOG = Graphitty.log(new BootLoader());
        //Registry.singleton().register(mInstSet.INST_TID, () -> mInstSet.of(fURI.NULL));
        Registry.open().register(SYS_TID, sysInstSet::create);
        Registry.open().register(FS_INSTSET_TID, fsInstSet::create);
        Registry.open().register(KV_INSTSET_TID, kvInstSet::create);
        Registry.open().register(WEB_INSTSET_TID, webInstSet::create);
        Registry.open().register(IOT_INSTSET_TID, iotInstSet::create);
        Registry.open().register(GRPH_INSTSET_TID, grphInstSet::create);
        Registry.open().register(LLM_INSTSET_TID, llmInstSet::create);
        Registry.open().register(VEC_INSTSET_TID, vecInstSet::create);
        Registry.open().register(MACH_INSTSET_TID, machInstSet::create);
        Registry.open().register(CLSTR_INSTSET_TID, clstrInstSet::create);
        Registry.open().register(UI_INSTSET_TID, uiInstSet::create);
        Registry.open().register(TABL_INSTSET_TID, tablInstSet::create);
        // Registry.singleton().register(miotInstSet.INST_TID, () -> miotInstSet.of(fURI.NULL));
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
                ARGS = args.length > 0 ? mParser.parse(args[0]).as() : rec();
                LogObj.setSLF4J(ARGS.has(uri("log")) ? ARGS.at(uri("log")).uriValue().toString() : "info");
            } catch (final Exception e) {
                LOG.error(e);
                System.exit(0);
            }
            if (args.length > 0)
                LOG.info("unparsed boot args:\n%s", args[0]);
            ARGS = args.length > 0 ? mParser.parse(args[0]).as() : rec();
            BootLoader.load(ARGS);
        }
    }

    public static void load(final Rec args) {
        if (BOOTING) {
            LOG.info("parsed boot args:\n%s", args);
            if (args.has(BOOT))
                args.put(uri(BOOT), f(Paths.get("").toAbsolutePath().normalize().toString()).extend(args.at(BOOT).uriValue()).toUri(), MUTABLE);
            LogObj.setSLF4J(args.has(uri("log")) ? args.at(uri("log")).uriValue().toString() : "info");
            LOG.info("%s", Graphitty.sillyPrint("booting metatron", true, true));
            Runtime.getRuntime().addShutdownHook(new Thread(BootLoader::close));
            LOG.info("accessible instruction sets: %s", Registry.open().registrants());
            fURI remoteAuthority = null;
            /// /// START OF BOOTING PROCESS /// /// allow boot description to be read from a mtron file
            String hostname = null;
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (final Exception e) {
                hostname = System.getenv(Tokens.HOSTNAME);
            }
            if (null == hostname)
                LOG.warn("booting metatron on a non-networked jvm");
            else
                remoteAuthority = args.at(Tokens.HOST).orElse(uri(WS + "://" + hostname + ".local" + ":" + 8999)).uriValue();
            ROUTER = new MRouter(remoteAuthority, SYS_OBJ_TID.extend("router"));
            kvSpace.of(f("/sys/#"), null);
            new sysInstSet(SYS_TID.extend("mod/sys"));
            Router.writeToSpace(mInstSet.create(f("/sys/mod/m")));
            Router.writeToSpace(Router.global());
            Router.writeToSpace(new fsInstSet(f("/sys/mod/fs")));
            Router.writeToSpace(f("boot/args"), args);

            ROUTER.start();
            ///////////////////////////////////////////////////////////////
            if (args.has(uri(Tokens.BOOT))) {
                LOG.info("\t {{m}}BEGIN:{{g}} evaluating provided boot loader: {{b}}%s{{X}}\n", args.at(uri(Tokens.BOOT)).uriValue());
                try {
                    final Path bootPath = Path.of(args.at(Tokens.BOOT).uriValue().toString());
                    fileSpace.makeFile(bootPath).vid(f("boot/file"));
                    final long count = mParser.eval(bootPath.toFile(), e -> LOG.error("%s\n%s", e.getCause() == null ? e.getMessage() : e.getCause().getMessage(), e)).count();
                    LOG.info("processed boot input: {{b}}%s{{/b}} {{g}}[{{y}}loc: %d{{/y}}]{{/g}}", args.at(Tokens.BOOT).uriValue(), count);
                } catch (final IOException e) {
                    LOG.error(e);
                    System.exit(0);
                }
                LOG.info("\t {{m}}END:{{g}} evaluating provided boot loader: {{b}}%s{{X}}\n", args.at(uri(Tokens.BOOT)).uriValue());
            }
            ///////////////////////////////////////////////////////////////
            final Obj log = Router.writeToSpace(LogObj.of(rec(args.at("log").orElse(uri("trace")), lst(uri(ALL))), SYS_OBJ_TID.extend("log")));
            LOG.info("logging now handled by %s", log);
            /// ///////////////////////////////////
            LOG.info("%s {{g}}successfully{{/g}} booted", Graphitty.sillyPrint("metatron", true, true));
            BOOTING = false;
            System.gc();
            /// /// END OF BOOTING PROCESS /// ///
        } else {
            LOG.warn("boot processes previously completed -- ignoring request to boot");
        }
    }

    public static void close() {
        try {
            BOOTING = true;
            LOG.none("\n");
            if (Router.loaded())
                Router.global().close();
            ROUTER = null;
            ARGS = null;
            EXECUTOR.shutdownNow();
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
