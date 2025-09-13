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
import studio.phaseshift.metatron.lang.obj.base.Obj;
import studio.phaseshift.metatron.lang.obj.base.Uri;
import studio.phaseshift.metatron.lang.obj.base.furi.TypefURI;
import studio.phaseshift.metatron.lang.obj.mtron.MLst;
import studio.phaseshift.metatron.lang.obj.mtron.MRec;
import studio.phaseshift.metatron.lang.obj.mtron.MUri;
import studio.phaseshift.metatron.lang.obj.mtron.core.MCoreInstSet;
import studio.phaseshift.metatron.space.Router;
import studio.phaseshift.metatron.space.Space;
import studio.phaseshift.metatron.space.mem.MemRouter;
import studio.phaseshift.metatron.space.mem.MemSpace;
import studio.phaseshift.metatron.space.mqtt.MqttSpace;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.space.mqtt.MqttSpace.MQTT_TID;

public class BootLoader {

    private static final GraphittyLogger LOG = Graphitty.log(BootLoader.class);
    public static boolean BOOTING = true;
    public static Router ROUTER = new MemRouter(fURI.of("/sys/router"));

    public static void load() {

        try {
            LOG.info("booting metatron on %s {{g}}[%s{{g}}]{{X}}",
                    MUri.of(InetAddress.getLocalHost().getHostName()).tid("host"),
                    MUri.of(InetAddress.getLocalHost().getHostAddress()).tid("ipv4"));
        } catch (final UnknownHostException e) {
            LOG.warn("booting metatron on a non-networked jvm");
        }
        final Space mnt = new MemSpace(fURI.of("/mnt/#"), fURI.of("/mnt"));
        final Space sys = new MemSpace(fURI.of("/sys/#"), fURI.of("/mnt/sys"));
        // final Router router = (ROUTER = new MemRouter(fURI.of("/sys/router")));
        Router.global().registerStruct(mnt);
        Router.global().registerStruct(sys);
      //  Router.global().registerStruct(new MemSpace(fURI.of("/mtron/#"), fURI.of("/mnt/lang/mtron")));
        Router.global().registerStruct(new MemSpace(fURI.of("#"), fURI.of("/sys/stack")));
      //  Router.global().registerStruct(new MemSpace(fURI.of("/test/#"), fURI.of("/sys/test")));
        //Router.global().registerStruct(new MqttSpace(Map.of(new MUri("broker"), new MUri("ip://192.168.66.2:1883"), new MUri("pattern"), new MUri("/mqtt/#")), MQTT_TID, fURI.of("/mnt/mqtt")));
        // Router.global().registerStruct(new MqttSpace(Map.of(new MUri("broker"), new MUri("ip://192.168.66.2:1883"), new MUri("pattern"), new MUri("zigbee2mqtt/#")), MQTT_TID, fURI.of("/mnt/zigbee2mqtt")));
        new MCoreInstSet().load();


                        BOOTING = false;
                        //SInst.load();
        //SInst.ext();
    }
}
