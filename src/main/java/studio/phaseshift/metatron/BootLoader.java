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
import studio.phaseshift.metatron.lang.db.vec.vecInstSet;
import studio.phaseshift.metatron.lang.net.clstr.clstrInstSet;
import studio.phaseshift.metatron.lang.net.iot.iotInstSet;
import studio.phaseshift.metatron.lang.net.web.webInstSet;
import studio.phaseshift.metatron.lang.sys.fs.fileSpace;
import studio.phaseshift.metatron.lang.sys.fs.fsInstSet;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.sys.router.impl.MRouter;
import studio.phaseshift.metatron.lang.sys.sysInstSet;
import studio.phaseshift.metatron.lang.util.logObj;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

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
import static studio.phaseshift.metatron.lang.db.vec.vecInstSet.VEC_INSTSET_TID;
import static studio.phaseshift.metatron.lang.net.clstr.clstrInstSet.CLSTR_INSTSET_TID;
import static studio.phaseshift.metatron.lang.net.iot.iotInstSet.IOT_INSTSET_TID;
import static studio.phaseshift.metatron.lang.net.web.webInstSet.WEB_INSTSET_TID;
import static studio.phaseshift.metatron.lang.sys.fs.fsInstSet.FS_INSTSET_TID;
import static studio.phaseshift.metatron.lang.sys.sysInstSet.ROUTER_TID;
import static studio.phaseshift.metatron.lang.sys.sysInstSet.SYS_INSTSET_TID;

public class BootLoader implements Rec, Feature.SelfClone {

    /// ////////////////////////////////////////////////////////////////////////
    /// the global variables that must be gc()'d on close
    /// ////////////////////////////////////////////////////////////////////////
    public static boolean BOOTING = true;
    private static final GraphittyLogger LOG;
    public static Router ROUTER;
    public static Rec ARGS;
    public static boolean TYPE_CHECK = true;

    static {
        LOG = Graphitty.log(new BootLoader());
        //Registry.singleton().register(mInstSet.INST_TID, () -> mInstSet.of(fURI.NULL));
        Registry.open().register(SYS_INSTSET_TID, sysInstSet::create);
        Registry.open().register(FS_INSTSET_TID, fsInstSet::create);
        Registry.open().register(KV_INSTSET_TID, kvInstSet::create);
        Registry.open().register(WEB_INSTSET_TID, webInstSet::create);
        Registry.open().register(IOT_INSTSET_TID, iotInstSet::create);
        Registry.open().register(GRPH_INSTSET_TID, grphInstSet::create);
        Registry.open().register(LLM_INSTSET_TID, llmInstSet::create);
        Registry.open().register(VEC_INSTSET_TID, vecInstSet::create);
        Registry.open().register(MACH_INSTSET_TID, machInstSet::create);
        Registry.open().register(CLSTR_INSTSET_TID, clstrInstSet::create);
        // Registry.singleton().register(miotInstSet.INST_TID, () -> miotInstSet.of(fURI.NULL));
    }

    public static void main(final String[] args) throws IOException {
        if (args.length == 1 && args[0].equals("--help")) {
            LOG.none("""
                            
                            %s: %s
                              {{g}}({{X}}arguments must be provided as a single mtron %s{{g}}){{X}}
                              %s
                              {{r}}-----------------------------------------------{{/r}}
                                %s
                                %s
                                %s
                                %s
                            
                              example:
                                %s
                            
                            """,
                    Graphitty.sillyPrint("metatron", true, true),
                    Graphitty.sillyPrint("ring-oriented computing", true, true),
                    T(REC_TID),
                    rec(uri("k1"), uri("v1"), uri("..."), uri("..."), uri("kn"), uri("vn")),
                    rel(uri("log"), objs(uri("info"), uri("debug"), uri("warn"), uri("error"), uri("trace"))),
                    rel(uri("host"), uri("ws://localhost:8888")),
                    rel(uri("cluster"), lst(uri("ws://a.local:8888"), uri("ws://b.local:8888"), uri("..."))),
                    rel(uri("mode"), objs(uri("server"), uri("console"))),
                    rel(uri("boot"), uri("./boot.mtron")),
                    "metatron '[boot=><examples/boot.mtron>,mode=>console,log=>info,host=><ws://localhost:8888>,cluster=>[<ws://127.0.0.1:8887>]]'");
            System.exit(0);
        } else {
            final Rec userArgs = args.length > 0 ? mParser.parse(args[0]).as() : rec();
            if (userArgs.has(BOOT)) {
                userArgs.put(uri(BOOT), f(Paths.get("").toAbsolutePath().normalize().toString()).extend(userArgs.at(BOOT).uriValue()).toUri(), MUTABLE);
            }
            logObj.setSLF4J(userArgs.has(uri("log")) ? userArgs.at(uri("log")).uriValue().toString() : "trace");
            LOG.debug("user options: %s", userArgs);
            ARGS = userArgs;
            BootLoader.load(userArgs);
        }
    }

    public static void load(final Rec args) {
        if (BOOTING) {
            LOG.info("%s", Graphitty.sillyPrint("booting metatron", true, true));
            Runtime.getRuntime().addShutdownHook(new Thread(BootLoader::close));
            fURI remoteAuthority = null;
            /// /// START OF BOOTING PROCESS /// /// allow boot description to be read from a mtron file
            try {
                remoteAuthority = args.at(Tokens.HOST).orElse(uri(WS + "://" + InetAddress.getLocalHost().getHostName() + ".local" + ":" + 8887)).uriValue();
            } catch (final Exception e) {
                LOG.warn("booting metatron on a non-networked jvm");
            }
            LOG.info("accessible instruction sets: %s", Registry.open().registrants());
            ROUTER = new MRouter(remoteAuthority, ROUTER_TID);
            sysInstSet.create();
            kvSpace.of(SYS_INSTSET_TID.extend(ALL), SYS_INSTSET_TID);
            Router.writeToSpace(mInstSet.create(f("/sys/router/lang/m")));
            Router.writeToSpace(Router.global());
            fsInstSet.create();
            Router.writeToSpace(f("boot/args"), args);
            ROUTER.start();
            ///////////////////////////////////////////////////////////////
            if (args.has(uri(Tokens.BOOT))) {
                LOG.none("\t {{m}}BEGIN:{{g}} evaluating provided boot loader: {{b}}%s{{X}}\n", args.at(uri(Tokens.BOOT)).uriValue());
                try {
                    final Path bootPath = Path.of(args.at(Tokens.BOOT).uriValue().toString());
                    fileSpace.makeFile(bootPath).vid(f("boot/file"));
                    final long count = mParser.eval(bootPath.toFile(), e -> LOG.error("%s\n%s", e.getCause() == null ? e.getMessage() : e.getCause().getMessage(), e)).count();
                    LOG.info("processed boot input: {{b}}%s{{/b}} {{g}}[{{y}}loc: %d{{/y}}]{{/g}}", args.at(Tokens.BOOT).uriValue(), count);
                } catch (final IOException e) {
                    LOG.error(e);
                    System.exit(0);
                }
                LOG.none("\t {{m}}END:{{g}} evaluating provided boot loader: {{b}}%s{{X}}\n", args.at(uri(Tokens.BOOT)).uriValue());
            }
            ///////////////////////////////////////////////////////////////
            final Obj log = Router.writeToSpace(logObj.of(rec(args.at("log").orElse(uri("trace")), lst(uri(ALL))), SYS_INSTSET_TID.extend("log")));
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
        BOOTING = true;
        LOG.none("\n");
        Router.global().close();
        ROUTER = null;
        ARGS = null;
        System.gc();
        LOG.info("%s {{g}}successfully{{/g}} shutdown", Graphitty.sillyPrint("metatron", true, true));
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
