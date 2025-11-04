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
import studio.phaseshift.metatron.lang.mach.machInstSet;
import studio.phaseshift.metatron.lang.mgrph.mgrphInstSet;
import studio.phaseshift.metatron.lang.mkv.mkvInstSet;
import studio.phaseshift.metatron.lang.mkv.mkvSpace;
import studio.phaseshift.metatron.lang.mllm.mllmInstSet;
import studio.phaseshift.metatron.lang.msys.Router;
import studio.phaseshift.metatron.lang.msys.impl.MRouter;
import studio.phaseshift.metatron.lang.msys.msysInstSet;
import studio.phaseshift.metatron.lang.mtron.mtronInstSet;
import studio.phaseshift.metatron.lang.mtron.mtronParser;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.Rec;
import studio.phaseshift.metatron.lang.mvec.mvecInstSet;
import studio.phaseshift.metatron.lang.mweb.mwebInstSet;
import studio.phaseshift.metatron.space.device.log.Log;
import studio.phaseshift.metatron.space.fs.FileSpace;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.ui.Mode;
import studio.phaseshift.metatron.ui.mode.console.Console;
import studio.phaseshift.metatron.ui.mode.server.Server;
import studio.phaseshift.metatron.util.MTronException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.FileSystems;
import java.util.Arrays;
import java.util.List;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.REC_TID;
import static studio.phaseshift.metatron.lang.mtron.type.NoObj.noobj;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MUri.uri;

public class BootLoader implements Obj {

    public static final String HOST = "host";
    private static final GraphittyLogger LOG;
    public static boolean TYPE_CHECK = true;
    public static boolean BOOTING = true;
    /// ////////////////////////////////////////////////////////////////////////
    public static Router ROUTER;
    public static Rec GLOBAL;
    public static Mode MODE;

    static {
        LOG = Graphitty.log(new BootLoader());
        //Registry.singleton().register(mtronInstSet.INST_TID, () -> mtronInstSet.of(fURI.NULL));
        Registry.singleton().register(msysInstSet.MSYS_TID, msysInstSet::create);
        Registry.singleton().register(mkvInstSet.MKV_TID, mkvInstSet::create);
        Registry.singleton().register(mwebInstSet.MWEB_TID, mwebInstSet::create);
        Registry.singleton().register(mgrphInstSet.MGRPH_TID, mgrphInstSet::create);
        Registry.singleton().register(mllmInstSet.MLLM_TID, mllmInstSet::create);
        Registry.singleton().register(mvecInstSet.MVEC_TID, mvecInstSet::create);
        Registry.singleton().register(machInstSet.MACH_TID, machInstSet::create);
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
                    rel(uri("log"), objs(uri("INFO"), uri("DEBUG"), uri("WARN"), uri("ERROR"), uri("TRACE"))),
                    rel(uri("host"), uri("ws://localhost:8888")),
                    rel(uri("nodes"), lst(uri("ws://a.local:8888"), uri("ws://b.local:8888"), uri("..."))),
                    rel(uri("mode"), objs(uri("server"), uri("console"))),
                    rel(uri("boot"), uri("./boot.mtron")),
                    "metatron '[mode=>console,log=>INFO,host=>ws://localhost:8888,nodes=>[ws://127.0.0.1:8887]]'");
            System.exit(0);
        } else {
            Rec options = args.length > 0 ? mtronParser.m_rec().parse(args[0]).get() : rec();
            Log.setSLF4J(options.has(uri("log")) ? options.at(uri("log")).uriValue().toString() : "TRACE");
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
            LOG.info("available instruction sets: %s", Registry.singleton().registrants());
            ROUTER = new MRouter(remoteAuthority, f("/sys/router"));
            mkvSpace.of(f("/mnt/#")).vid(f("/mnt"));
            mkvSpace.of(f("/sys/#")).vid(f("/mnt/sys"));
            mtronInstSet.create(f("/mnt/lang/m"));
            Router.writeToSpace(Router.global());
            ROUTER.start();
            //  Router.writeToSpace(new mtronInstSet(fURI.of("/mnt/lang/m")));
            ///////////////////////////////////////////////////////////////
            if (options.has(uri("boot"))) {
                LOG.none("\t{{r}}BEGIN:{{g}} evaluating provided boot loader: %s{{X}}\n", options.at(uri("boot")));
                try (final BufferedReader reader = new BufferedReader(new FileReader(options.at("boot").uriValue().toString()))) {
                    final List<String> lines = reader.lines().toList();
                    final String source = lines.stream().reduce("", (a, b) -> a + b + "\n");
                    LOG.info("boot input: {{b}}%s{{/b}} {{g}}[{{y}}loc: %d{{/y}}]{{/g}}", options.at("boot").uriValue(), lines.size());
                    Arrays.stream(source.split(";"))
                            .filter(s -> !s.trim().isEmpty())
                            .map(s -> mtronParser.parse(s).resolve(noobj()))
                            .filter(o -> !o.isNoObj())
                            .forEach(o -> {
                                LOG.debug("boot compilation: %s", o);
                                LOG.debug("boot result: %s", o.apply());
                            });
                    LOG.none("\t{{r}}END:{{g}} evaluating provided boot loader: %s{{X}}\n", options.at(uri("boot")));
                } catch (final IOException e) {
                    LOG.error(e);
                    System.exit(0);
                }
            }
            ///////////////////////////////////////////////////////////////
            Router.writeToSpace(Log.of(rec(options.at("log").orElse(uri("TRACE")), lst(uri("#"))), f("/sys/log")));
            Router.writeToSpace(new FileSpace(FileSystems.getDefault(), f("/home/#"), f("/mnt/fs")));
            // Router.writeToSpace(new MGraph(TinkerFactory.createModern(), f("/tp/#"), f("/mnt/tp")));
            //mkvSpace.of(f("/tp/#")).vid(f("/mnt/tp"));
            //new TP3Translator(f("/tp")).translate(TinkerFactory.createModern());
            // new MqttSpace(f("zigbee2mqtt/#?broker=mqtt://192.168.66.2:1883&prefix=/mqtt"), f("/mnt/zigbee2mqtt")));
            if (options.at("mode").equals(uri("console"))) {
                //     Router.writeToSpace(mollamaSpace.of(f("http://localhost:11434"), f("/ollama/#"), f("/mnt/ollama")));
                //     Router.writeToSpace(RemoteSpace.open(f("ws://chibi.local:8888"), f("/shared/#"), f("/mnt/shared")));
            } else if (options.at("mode").equals(uri("server")))
                Router.writeToSpace(new mkvSpace(fURI.of("/shared/#"), fURI.of("/mnt/shared")));
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
            throw MTronException.of("no mode specified (see --help)");
        else if (mode.uriValue().equals(f("testing")))
            MODE = Mode.NoOp.of();
        else if (mode.uriValue().equals(f("console")))
            MODE = Console.of(options);
        else if (mode.uriValue().equals(f("server")))
            MODE = Server.of(options);
        else
            throw MTronException.of("unknown mode %s (see --help)", mode.uriValue());
        MODE.start();
    }

    public static void close() {
        BOOTING = true;
        LOG.none("\n");
        Router.global().close();
        MODE.stop();
        LOG.info("%s {{g}}successfully{{/g}} shutdown", Graphitty.sillyPrint("metatron", true, true));
    }

    @Override
    public <J> J jvm() {
        return null;
    }

    @Override
    public fURI tid() {
        return f("boot");
    }

    @Override
    public fURI vid() {
        return f("boot");
    }

    @Override
    public <O extends Obj> O clone(Object jvm, fURI tid, fURI vid) {
        return null;
    }

    @Override
    public Obj clone() {
        return null;
    }
}
