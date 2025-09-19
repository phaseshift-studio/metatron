/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.mtron.MUri;
import studio.phaseshift.metatron.lang.obj.mtron.MInstSet;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.space.device.log.Log;
import studio.phaseshift.metatron.space.mem.MemRouter;
import studio.phaseshift.metatron.space.mem.MemSpace;
import studio.phaseshift.metatron.space.mem.StackSpace;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Stack;

public class BootLoader {

    private static final GraphittyLogger LOG = Graphitty.log(BootLoader.class);
    public static boolean BOOTING = true;
    public static Router ROUTER = new MemRouter(fURI.of("/sys/router"));

    public static void load() {
        if(BOOTING) {
            try {
                LOG.info("booting metatron on %s {{g}}[%s{{g}}]{{X}}",
                        MUri.of(InetAddress.getLocalHost().getHostName()).tid("host"),
                        MUri.of(InetAddress.getLocalHost().getHostAddress()).tid("ipv4"));
            } catch (final UnknownHostException e) {
                LOG.warn("booting metatron on a non-networked jvm");
            }
            final Space mnt = new MemSpace(fURI.of("/mnt/#"), fURI.of("/mnt"));
            Router.global().registerSpace(mnt);
            final Space sys = new MemSpace(fURI.of("/sys/#"), fURI.of("/mnt/sys"));
            Router.global().registerSpace(sys);
            final Space usr = new MemSpace(fURI.of("/usr/#"), fURI.of("/mnt/usr"));
            Router.global().registerSpace(usr);
            Router.global().write(Router.global().vid(), Router.global());
            final Space stk = Router.stack();
            Router.global().registerSpace(stk);
            final Space mtron = new MInstSet(fURI.of("/mnt/lang/mtron"));
            Router.global().registerSpace(mtron);
            Log.of(fURI.of("/sys/log"));

            //Router.global().registerStruct(new MqttSpace(Map.of(new MUri("broker"), new MUri("ip://192.168.66.2:1883"), new MUri("pattern"), new MUri("/mqtt/#")), MQTT_TID, fURI.of("/mnt/mqtt")));
            // Router.global().registerStruct(new MqttSpace(Map.of(new MUri("broker"), new MUri("ip://192.168.66.2:1883"), new MUri("pattern"), new MUri("zigbee2mqtt/#")), MQTT_TID, fURI.of("/mnt/zigbee2mqtt")));
            BOOTING = false;
        } else {
            LOG.warn("boot processes previously completed -- ignoring request to boot");
        }

    }

    public static Log logger() {
        return Router.global().read("/sys/log",Log.class);
    }
}
