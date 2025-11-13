/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.mach.machInstSet;
import studio.phaseshift.metatron.lang.db.grph.inst.grphInstSet;
import studio.phaseshift.metatron.lang.db.kv.inst.kvInstSet;
import studio.phaseshift.metatron.lang.db.kv.kvSpace;
import studio.phaseshift.metatron.lang.db.vec.vecInstSet;
import studio.phaseshift.metatron.lang.net.clstr.clstrInstSet;
import studio.phaseshift.metatron.lang.net.web.docs.docSpace;
import studio.phaseshift.metatron.lang.net.web.webInstSet;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.sys.router.impl.MRouter;
import studio.phaseshift.metatron.lang.sys.sysInstSet;
import studio.phaseshift.metatron.lang.util.logObj;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.ui.Mode;
import studio.phaseshift.metatron.ui.mode.console.Console;
import studio.phaseshift.metatron.ui.mode.server.Server;
import studio.phaseshift.metatron.util.MTronException;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.REC_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

public class BootLoader implements Obj {

    /// ////////////////////////////////////////////////////////////////////////
    /// the global variables that must be gc()'d on close
    /// ////////////////////////////////////////////////////////////////////////
    public static boolean BOOTING = true;
    private static final GraphittyLogger LOG;
    public static Router ROUTER;
    public static Rec GLOBAL;
    public static Mode MODE;
    /// ////////////////////////////////////////////////////////////////////////

    public static final String HOST = "host";
    public static final String BOOT = "boot";
    public static boolean TYPE_CHECK = true;

    static {
        LOG = Graphitty.log(new BootLoader());
        //Registry.singleton().register(mtronInstSet.INST_TID, () -> mtronInstSet.of(fURI.NULL));
        Registry.open().register(sysInstSet.MSYS_TID, sysInstSet::create);
        Registry.open().register(kvInstSet.MKV_TID, kvInstSet::create);
        Registry.open().register(webInstSet.MWEB_TID, webInstSet::create);
        Registry.open().register(grphInstSet.MGRPH_TID, grphInstSet::create);
        Registry.open().register(llmInstSet.MLLM_TID, llmInstSet::create);
        Registry.open().register(vecInstSet.MVEC_TID, vecInstSet::create);
        Registry.open().register(machInstSet.MACH_TID, machInstSet::create);
        Registry.open().register(clstrInstSet.MCLSTR_TID, clstrInstSet::create);
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
                    rel(uri("nodes"), lst(uri("ws://a.local:8888"), uri("ws://b.local:8888"), uri("..."))),
                    rel(uri("mode"), objs(uri("server"), uri("console"))),
                    rel(uri("boot"), uri("./boot.mtron")),
                    "metatron '[mode=>console,log=>info,host=>ws://localhost:8888,nodes=>[ws://127.0.0.1:8887]]'");
            System.exit(0);
        } else {
            Rec options = args.length > 0 ? mParser.parse(args[0]).as() : rec();
            logObj.setSLF4J(options.has(uri("log")) ? options.at(uri("log")).uriValue().toString() : "trace");
            LOG.debug("user options: %s", options);
            GLOBAL = options;
            BootLoader.load(options);
        }
    }

    public static void load(final Rec options) {
        if (BOOTING) {
            Runtime.getRuntime().addShutdownHook(new Thread(BootLoader::close));
            fURI remoteAuthority = null;
            /// /// START OF BOOTING PROCESS /// /// allow boot description to be read from a mtron file
            try {
                remoteAuthority = options.at(HOST).orElse(uri("ws://" + InetAddress.getLocalHost().getHostName() + ".local" + ":" + 8887)).uriValue();
            } catch (final Exception e) {
                LOG.warn("booting metatron on a non-networked jvm");
            }
            startMode(options);
            LOG.info("available instruction sets: %s", Registry.open().registrants());
            ROUTER = new MRouter(remoteAuthority, f("/sys/router"));
            kvInstSet.create();
            kvSpace.of(f("/mnt/#"), fURI.NULL).vid(f("/mnt"));
            kvSpace.of(f("/sys/#"), fURI.NULL).vid(f("/mnt/sys"));
            mInstSet.create(f("/mnt/lang/m"));
            Router.writeToSpace(Router.global());
            Router.writeToSpace("boot/options", options);
            ROUTER.start();
            //  Router.writeToSpace(new mtronInstSet(fURI.of("/mnt/lang/m")));
            ///////////////////////////////////////////////////////////////
            if (options.has(uri(BOOT))) {
                LOG.none("\t {{m}}BEGIN:{{g}} evaluating provided boot loader: {{b}}%s{{X}}\n", options.at(uri(BOOT)).uriValue());
                try {
                    final long count = mParser.eval(Path.of(options.at(BOOT).uriValue().toString()).toFile()).count();
                    LOG.info("processed boot input: {{b}}%s{{/b}} {{g}}[{{y}}loc: %d{{/y}}]{{/g}}", options.at(BOOT).uriValue(), count);
                } catch (final IOException e) {
                    LOG.error(e);
                    System.exit(0);
                }
                LOG.none("\t {{m}}END:{{g}} evaluating provided boot loader: {{b}}%s{{X}}\n", options.at(uri(BOOT)).uriValue());
            }
            ///////////////////////////////////////////////////////////////
            final Obj log = Router.writeToSpace(logObj.of(rec(options.at("log").orElse(uri("trace")), lst(uri("#"))), f("/sys/log")));
            LOG.info("logging now handled by %s", log);

            //Router.writeToSpace(new FileSpace(FileSystems.getDefault(), f("/home/#"), f("/mnt/fs")));
            // Router.writeToSpace(new MGraph(TinkerFactory.createModern(), f("/tp/#"), f("/mnt/tp")));
            //mkvSpace.of(f("/tp/#")).vid(f("/mnt/tp"));
            //new TP3Translator(f("/tp")).translate(TinkerFactory.createModern());
            // new MqttSpace(f("zigbee2mqtt/#?broker=mqtt://192.168.66.2:1883&prefix=/mqtt"), f("/mnt/zigbee2mqtt")));
            if (options.at("mode").equals(uri("docs")))
                docSpace.of(options.at(HOST).uriValue().port(7777));
            else if (options.at("mode").equals(uri("console"))) {
                //     Router.writeToSpace(RemoteSpace.open(f("ws://chibi.local:8888"), f("/shared/#"), f("/mnt/shared")));
            } else if (options.at("mode").equals(uri("server")))
                Router.writeToSpace(new kvSpace(fURI.of("/shared/#"), fURI.of("/mnt/shared")));
            /// ///////////////////////////////////
            LOG.info("%s {{g}}successfully{{/g}} booted", Graphitty.sillyPrint("metatron", true, true));
            BOOTING = false;
            /// /// END OF BOOTING PROCESS /// ///
        } else {
            LOG.warn("boot processes previously completed -- ignoring request to boot");
        }
    }

    public static void startMode(final Rec options) {
        final Obj mode = options.at("mode");
        if (null == mode)
            throw MTronException.of("no mode specified (see --help): %s", options);
        else if (mode.uriValue().equals(f("testing")))
            MODE = Mode.NoOp.of();
        else if (mode.uriValue().equals(f("console")))
            MODE = Console.of(options);
        else if (mode.uriValue().equals(f("server")))
            MODE = Server.of(options);
        else if (mode.uriValue().equals(f("docs")))
            MODE = Console.of(options);
        else
            throw MTronException.of("unknown mode %s (see --help): %s", mode.uriValue(), options);
        MODE.start();
    }

    public static void close() {
        BOOTING = true;
        LOG.none("\n");
        Router.global().close();
        MODE.stop();
        ROUTER = null;
        GLOBAL = null;
        System.gc();
        LOG.info("%s {{g}}successfully{{/g}} shutdown", Graphitty.sillyPrint("metatron", true, true));
    }

    @Override
    public <J> J jvm() {
        return null;
    }

    @Override
    public fURI tid() {
        return f(BOOT);
    }

    @Override
    public fURI vid() {
        return f(BOOT);
    }

    @Override
    public <O extends Obj> O clone(Object jvm, fURI tid, fURI vid) {
        return (O) this;
    }

    @Override
    public Obj clone() {
        return this;
    }
}
