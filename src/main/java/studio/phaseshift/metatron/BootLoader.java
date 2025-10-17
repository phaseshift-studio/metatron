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
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.monoid.mtron.MMonoid;
import studio.phaseshift.metatron.lang.obj.InstSet;
import studio.phaseshift.metatron.lang.obj.mext.mextInstSet;
import studio.phaseshift.metatron.lang.obj.mgrph.tp.MGraph;
import studio.phaseshift.metatron.lang.obj.mgrph.tp.mgrphInstSet;
import studio.phaseshift.metatron.lang.obj.mtron.MUri;
import studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.space.device.log.Log;
import studio.phaseshift.metatron.space.fs.FileSpace;
import studio.phaseshift.metatron.space.mem.MemRouter;
import studio.phaseshift.metatron.space.mem.MemSpace;
import studio.phaseshift.metatron.space.mqtt.MqttSpace;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.FileSystems;
import java.util.Map;

import static studio.phaseshift.metatron.lang.fURI.f;
import static studio.phaseshift.metatron.lang.obj.mtron.MUri.uri;

public class BootLoader {

    private static final GraphittyLogger LOG = Graphitty.log(BootLoader.class);
    public static boolean BOOTING = true;
    public static Router ROUTER = new MemRouter(fURI.of("/sys/router"));

    public static void load() {
        if (BOOTING) {
            try {
                LOG.info("booting metatron on %s {{g}}[%s{{g}}]{{X}}",
                        MUri.of(InetAddress.getLocalHost().getHostName()).tid("host"),
                        MUri.of(InetAddress.getLocalHost().getHostAddress()).tid("ipv4"));
            } catch (final UnknownHostException e) {
                LOG.warn("booting metatron on a non-networked jvm");
            }

            final Space mnt = new MemSpace(fURI.of("/mnt/#"), fURI.of("/mnt"));
            Router.global().addSpace(mnt);
            final Space sys = new MemSpace(fURI.of("/sys/#"), fURI.of("/mnt/sys"));
            Router.global().addSpace(sys);
            final Space usr = new MemSpace(fURI.of("/usr/#"), fURI.of("/mnt/usr"));
            Router.global().addSpace(usr);
            Router.global().write(Router.global().vid(), Router.global());
            final Space stk = Router.stack();
            Router.global().addSpace(stk);
            final Space mtron = new mtronInstSet(fURI.of("/mnt/lang/mtron"));
            Router.global().addSpace(mtron);
            MMonoid.load();
            Router.global().write("/sys/log", Log.of(f("/sys/log")));
            //final Space var = new MemSpace(fURI.of("+/+/#"), fURI.of("/mnt/var"));
            //Router.global().addSpace(var);
            final Space fs = new FileSpace(FileSystems.getDefault(), f("/home/#"), f("/mnt/fs"));
            Router.global().addSpace(fs);
            final Space grph = new MGraph(TinkerFactory.createModern(), f("/tp/#"), f("/mnt/tp"));
            Router.global().addSpace(grph);
            final InstSet mgrph = new mgrphInstSet(f("/mnt/lang/mgrph"));
            Router.global().addSpace(mgrph);
            final Space mqtt = new MqttSpace(Map.of(
                    uri("broker"), uri("mqtt://192.168.66.2:1883"),
                    uri("prefix"), uri("/mqtt"),
                    uri("pattern"), uri("zigbee2mqtt/#")), f("/mnt/zigbee2mqtt"));
            Router.global().addSpace(mqtt);
            final InstSet mext = mextInstSet.of(f("/mnt/lang/mext"));
            Router.global().addSpace(mext);
            /// ///////////////////////////////////
            /*Router.global().write(
                    "bool", uri(BOOL_TID), "int", uri(INT_TID),
                    "real", uri(REAL_TID), "str", uri(STR_TID),
                    "uri", uri(URI_TID), "rel", uri(REL_TID),
                    "lst", uri(LST_TID), "rec", uri(REC_TID));*/


            //Router.global().registerStruct(new MqttSpace(Map.of(new MUri("broker"), new MUri("ip://192.168.66.2:1883"), new MUri("pattern"), new MUri("/mqtt/#")), MQTT_TID, fURI.of("/mnt/mqtt")));
            // Router.global().registerStruct(new MqttSpace(Map.of(new MUri("broker"), new MUri("ip://192.168.66.2:1883"), new MUri("pattern"), new MUri("zigbee2mqtt/#")), MQTT_TID, fURI.of("/mnt/zigbee2mqtt")));
            BOOTING = false;
        } else {
            LOG.warn("boot processes previously completed -- ignoring request to boot");
        }

    }


    public static Log logger() {
        return Router.global().read("/sys/log", Log.class);
    }

    public static void close() {
        LOG.none(Graphitty.sillyPrint("\nshutting down the metatron\n", true, true));
        Router.global().close();
    }
}
