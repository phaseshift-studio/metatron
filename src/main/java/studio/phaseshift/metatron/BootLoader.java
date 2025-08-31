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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.inst.SInst;
import studio.phaseshift.metatron.lang.obj.SObj;
import studio.phaseshift.metatron.struct.Router;
import studio.phaseshift.metatron.struct.Struct;
import studio.phaseshift.metatron.struct.mem.MemRouter;
import studio.phaseshift.metatron.struct.mem.MemStruct;
import studio.phaseshift.metatron.ui.Graphitty;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class BootLoader {

    private static final Logger LOG = LoggerFactory.getLogger(BootLoader.class);

    public static Router ROUTER;

    public static void load() {
        try {
            LOG.info(Graphitty.parse("booting metatron on %s !g[%s!g]!!".formatted(
                    SObj.Uri.of(InetAddress.getLocalHost().getHostName(), fURI.of("host")),
                    SObj.Uri.of(InetAddress.getLocalHost().getHostAddress(), fURI.of("ipv4")))));
        } catch (final UnknownHostException e) {
            LOG.warn(Graphitty.parse("booting metatron on a non-networked jvm"));
        }
        final Struct mnt = new MemStruct(fURI.of("/mnt/#"), fURI.of("/mnt"));
        final Struct sys = new MemStruct(fURI.of("/sys/#"), fURI.of("/mnt/sys"));
        final Router router = (ROUTER = new MemRouter(fURI.of("/sys/router")));
        router.registerStruct(mnt);
        router.registerStruct(sys);
        router.registerStruct(new MemStruct(fURI.of("/mtron/#"), fURI.of("/mnt/lang/mtron")));
        router.registerStruct(new MemStruct(fURI.of("+"), fURI.of("/sys/stack")));
        SInst.load();
    }
}
