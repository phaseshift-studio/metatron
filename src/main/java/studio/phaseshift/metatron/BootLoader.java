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

import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerFactory;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.mach.machInstSet;
import studio.phaseshift.metatron.lang.mgrph.mgrphInstSet;
import studio.phaseshift.metatron.lang.mgrph.tp.MGraph;
import studio.phaseshift.metatron.lang.mtron.mtronInstSet;
import studio.phaseshift.metatron.lang.mtron.mtronParser;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.Rec;
import studio.phaseshift.metatron.lang.mvec.mvecInstSet;
import studio.phaseshift.metatron.lang.mweb.mwebInstSet;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.space.device.log.Log;
import studio.phaseshift.metatron.space.fs.FileSpace;
import studio.phaseshift.metatron.space.kv.KVSpace;
import studio.phaseshift.metatron.space.stack.StackSpace;
import studio.phaseshift.metatron.space.remote.RemoteSpace;
import studio.phaseshift.metatron.space.router.MRouter;
import studio.phaseshift.metatron.space.spaceInstSet;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.ui.Mode;
import studio.phaseshift.metatron.ui.console.Console;
import studio.phaseshift.metatron.ui.server.Server;
import studio.phaseshift.metatron.util.MTronException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.FileSystems;
import java.util.List;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mtron.mtronInstSet.REC_TID;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MRec.rec;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MRel.rel;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MUri.uri;

public class BootLoader {

    public static final String HOST = "host";
    public static final String LOG_LEVEL = "log";
    public static final String VERBOSE = "verbose";
    private static final GraphittyLogger LOG = Graphitty.log(BootLoader.class);
    public static boolean TYPE_CHECK = true;
    public static boolean BOOTING = true;
    /// ////////////////////////////////////////////////////////////////////////
    public static Router ROUTER;
    public static Rec GLOBAL;
    public static Mode MODE;

    public static void main(final String[] args) throws IOException {
        if (args.length == 1 && args[0].equals("--help")) {
            Graphitty.log(null).none("""
                            
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
        Runtime.getRuntime().addShutdownHook(new Thread(BootLoader::close));
        fURI remoteAuthority = null;
        if (BOOTING) {
            /// /// START OF BOOTING PROCESS /// /// allow boot description to be read from a mtron file
            try {
                remoteAuthority = options.at(HOST).orElse(uri("ws://" + InetAddress.getLocalHost().getHostName() + ".local" + ":" + 8887)).uriValue();
            } catch (final Exception e) {
                LOG.warn("booting metatron on a non-networked jvm");
            }
            startMode(options);
            ROUTER = new MRouter(remoteAuthority, f("/sys/router"));
            Router.global().write(new KVSpace(f("/mnt/#"), f("/mnt")));
            Router.global().write(new KVSpace(fURI.of("/sys/#"), fURI.of("/mnt/sys")));
            Router.global().write(Router.global());
            ROUTER.start();
            Router.global().write(new KVSpace(fURI.of("/usr/#"), fURI.of("/mnt/usr")));
            Router.global().write(new spaceInstSet(f("/mnt/space")));
            Router.global().write(new StackSpace(f("+/#"), f("/sys/router/stack")));
            Router.global().write(new mtronInstSet(fURI.of("/mnt/lang/m")));
            ///////////////////////////////////////////////////////////////
            if (options.has(uri("boot"))) {
                try (final BufferedReader reader = new BufferedReader(new FileReader(options.at("boot").uriValue().toString()))) {
                    final List<String> lines = reader.lines().toList();
                    final String source = lines.stream().reduce("", (a, b) -> a + b + "\n");
                    LOG.info("boot input: {{b}}%s{{/b}} {{g}}[{{y}}loc: %d{{/y}}]{{/g}}", options.at("boot").uriValue(), lines.size());
                    LOG.info("boot result: %s", mtronParser.parse(source).apply());
                } catch (final IOException e) {
                    LOG.error(e);
                    System.exit(0);
                }
            }
            ///////////////////////////////////////////////////////////////
            Router.global().write(Log.of(f("/sys/log")));
            Router.global().write(new FileSpace(FileSystems.getDefault(), f("/home/#"), f("/mnt/fs")));
            Router.global().write(new MGraph(TinkerFactory.createModern(), f("/tp/#"), f("/mnt/tp")));
            Router.global().write(new mgrphInstSet(f("/mnt/lang/grph")));
            // Router.global().write(f("/mnt/zigbee2mqtt"), new MqttSpace(f("zigbee2mqtt/#?broker=mqtt://192.168.66.2:1883&prefix=/mqtt"), f("/mnt/zigbee2mqtt")));
            Router.global().write(new mvecInstSet(f("/mnt/lang/mvec")));
            Router.global().write(new machInstSet(f("/mnt/lang/mach")));
            Router.global().write(new mwebInstSet(f("/mnt/lang/mweb")));
          //  Router.global().write(mwebSpace.of(f("http://localhost:8777"), f("http://#"), f("/mnt/web")));
            // Router.global().write(new RemoteSpace(remoteAuthority,f("/shared/remote/#"), f("/mnt/shared/remote")));
            //Router.global().write(new KVSpace(fURI.of("/shared/#"), fURI.of("/mnt/shared")));
            if (remoteAuthority != null && !remoteAuthority.host().equals("chibi.local"))
                Router.global().write(RemoteSpace.open(f("ws://chibi.local:8888"), f("/shared/#"), f("/mnt/shared")));
            else
                Router.global().write(new KVSpace(fURI.of("/shared/#"), fURI.of("/mnt/shared")));
            /// ///////////////////////////////////
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
        Router.global().close();
        MODE.stop();
        BOOTING = true;
        LOG.none(Graphitty.sillyPrint("\nshutting down the metatron\n", true, true));
    }
}
