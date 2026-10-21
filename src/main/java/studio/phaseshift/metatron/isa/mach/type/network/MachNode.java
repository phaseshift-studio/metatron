/*
 * metatron: a distributed virtual machine and language
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

package studio.phaseshift.metatron.isa.mach.type.network;

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.web.space.ws.handler.mtron_wsHandler;
import studio.phaseshift.metatron.isa.web.space.ws.wsSpace;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * One peer mtron instance, in its own OS process — the production twin of the test harness's {@code PeerNode}.
 * Forked by {@link PeerSpawn} when {@code push(<uri>)} names a loopback authority, it boots a VM, mounts the
 * pushed namespace as a store, serves that store over {@code mtron_ws}, and blocks until the parent reaps it.
 * <p>
 * A separate process is the point: it has its own {@code Router}, its own spaces, its own serializer state, and
 * only a socket to the parent — which is exactly what makes an authority-routed read or write a real distributed
 * act rather than an in-JVM alias. The parent reaches it through the {@code /network/roster} roster, so the same
 * authority dispatch that serves a declared peer serves a spawned one.
 * <p>
 * Everything it does is narrated to stdout, which {@link PeerSpawn} captures per node as that node's trace, and
 * {@link #READY} is printed once it is serving — the parent polls for it rather than guessing a sleep.
 * <p>
 * Usage: {@code MachNode <port> <root>}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class MachNode {

    /** printed once the node is serving, so {@link PeerSpawn} can stop polling */
    public static final String READY = "MACH_READY";

    /** the mtron_ws endpoint every node serves, and the route the roster transports dial */
    public static final String MTRON_ROUTE = "/mtron";

    private MachNode() {
        // static entry point
    }

    public static void main(final String[] args) throws Exception {
        final int port = Integer.parseInt(args[0]);
        final String root = args[1];
        // A node is an ordinary VM, booted the way a test VM is — but it is NOT a test: TESTING is set only to
        // skip load()'s headless park (which would consume the main thread before the store is mounted and READY
        // announced). The node owns its own lifetime and is reaped by the parent that forked it.
        BootLoader.BOOTING = true;
        BootLoader.TESTING = true;
        BootLoader.load(rec(uri("log"), uri("warn")));
        InstSet.importInstSet(f("/m/web"));
        trace("node: booted (root=%s, port=%d)", root, port);
        // the namespace this node owns: a store that answers every address under the pushed path
        memSpace.of(rec(uri(PATTERN), uri(root + "/#")), f("/sys/space/mach/store"));
        trace("node: store mounted at %s/#", root);
        // the ws server that makes this node addressable from the parent's roster
        wsSpace.of(mutableMap(
                        uri(PATTERN), uri("ws://#"),
                        uri(HOST), uri("ws://0.0.0.0:" + port),
                        uri(ROUTE), rec(uri(MTRON_ROUTE), uri(mtron_wsHandler.WS_MTRON_HANDLER_TID.toString()))),
                f("/sys/space/mach/ws"));
        trace("node: serving ws://0.0.0.0:%d%s", port, MTRON_ROUTE);
        System.out.println(READY + " " + port);
        System.out.flush();
        // park: the node serves until the parent destroys the process, which runs BootLoader's shutdown hook
        Thread.currentThread().join();
    }

    /** a node trace line — stdout, which {@link PeerSpawn} captures per node */
    private static void trace(final String format, final Object... args) {
        System.out.printf("[node] " + format + "%n", args);
        System.out.flush();
    }
}
