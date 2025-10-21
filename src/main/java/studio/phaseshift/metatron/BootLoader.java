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
import studio.phaseshift.metatron.io.net.MServer;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.mext.mextInstSet;
import studio.phaseshift.metatron.lang.obj.mgrph.tp.MGraph;
import studio.phaseshift.metatron.lang.obj.mgrph.tp.mgrphInstSet;
import studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.space.device.log.Log;
import studio.phaseshift.metatron.space.fs.FileSpace;
import studio.phaseshift.metatron.space.mem.MemSpace;
import studio.phaseshift.metatron.space.remote.RemoteSpace;
import studio.phaseshift.metatron.space.router.MRouter;
import studio.phaseshift.metatron.ui.Console;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.FileSystems;
import java.util.HashMap;
import java.util.Map;

import static studio.phaseshift.metatron.lang.fURI.f;

public class BootLoader {

    private static final GraphittyLogger LOG = Graphitty.log(BootLoader.class);
    public static boolean BOOTING = true;
    public static Router ROUTER;
    public static MServer SERVER;

    public static void main(final String[] args) throws IOException {
        final Map<String, String> params = new HashMap<>();
        for (final String arg : args) {
            final String[] kv = arg.split("=");
            params.put(kv[0].replace("--", ""), kv[1]);
        }
        BootLoader.load(params);
    }

    public static void load(final Map<String, String> args) {
        if (BOOTING) {
            try {

                if (args.containsKey("console"))
                    new Console(args).start();
                ROUTER = new MRouter(f("ws://" + InetAddress.getLocalHost().getHostName() + ".local" + ":" + 8887), fURI.of("/sys/router"));
                ROUTER.start();
            } catch (final Exception e) {
                LOG.warn("booting metatron on a non-networked jvm");
            }

            Router.global().write(f("/mnt"), new MemSpace(f("/mnt/#"), f("/mnt")));
            Router.global().write(f("/mnt/sys"), new MemSpace(fURI.of("/sys/#"), fURI.of("/mnt/sys")));
            Router.global().write(f("/mnt/usr"), new MemSpace(fURI.of("/usr/#"), fURI.of("/mnt/usr")));
            Router.global().write(f("/sys/router"), Router.global());
            Router.global().addSpace(Router.stack());
            Router.global().write(f("/mnt/lang/mtron"), new mtronInstSet(fURI.of("/mnt/lang/mtron")));
            Router.global().write("/sys/log", Log.of(f("/sys/log")));
            Router.global().write(f("/mnt/fs"), new FileSpace(FileSystems.getDefault(), f("/home/#"), f("/mnt/fs")));
            Router.global().write(f("/mnt/tp"), new MGraph(TinkerFactory.createModern(), f("/tp/#"), f("/mnt/tp")));
            Router.global().write(f("/mnt/lang/mgrph"), new mgrphInstSet(f("/mnt/lang/mgrph")));
            /*Router.global().write(f("/mnt/zigbee2mqtt"), new MqttSpace(Map.of(
                    uri("broker"), uri("mqtt://192.168.66.2:1883"),
                    uri("prefix"), uri("/mqtt"),
                    uri("pattern"), uri("zigbee2mqtt/#")), f("/mnt/zigbee2mqtt")));*/
            Router.global().write(f("/mnt/lang/mext"), mextInstSet.of(f("/mnt/lang/mext")));
            Router.global().write(f("/mnt/ws/chibi.local/8887/usr"), new RemoteSpace(f(args.getOrDefault("host", "ws://localhost:8887") + "/usr/#"), f("/mnt/ws/chibi.local/8887/usr")));
            /// ///////////////////////////////////
            BOOTING = false;
        } else {
            LOG.warn("boot processes previously completed -- ignoring request to boot");
        }
    }

    public static void close() {
        LOG.none(Graphitty.sillyPrint("\nshutting down the metatron\n", true, true));
        Router.global().close();
        BOOTING = true;
    }
}
