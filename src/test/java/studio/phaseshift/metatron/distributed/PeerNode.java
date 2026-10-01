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

package studio.phaseshift.metatron.distributed;

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.web.space.ws.handler.mtron_wsHandler;
import studio.phaseshift.metatron.isa.web.space.ws.wsSpace;
import studio.phaseshift.metatron.isa.web.type.MIME;

import java.nio.file.Files;
import java.nio.file.Path;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.io.ioInstSet.IO_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * One peer VM, in its own OS process: boot metatron, mount a store namespace, evaluate a provisioning script,
 * serve that store over {@code mtron_ws}, and block. Pure mechanism — every test-facing concern (which spaces,
 * what data, which peers) belongs to {@link PeerCluster}, which forks this class.
 * <p>
 * A separate process is the point. The retired in-JVM scaffolding ran several "nodes" inside one JVM sharing one
 * {@code Router}, so it could not fail on an authority, wire or correlation bug — exactly the three classes a
 * cross-host test exists to catch. A forked JVM has its own Router, its own spaces, its own serializer state,
 * and only a socket between them.
 * <p>
 * Everything it does is narrated to stdout, which the cluster captures per peer as that peer's <em>trace</em>:
 * the store it mounted, the exact script it was provisioned with, and the endpoint it is serving. A script that
 * fails to evaluate kills the process with the script printed, so a bad space template surfaces as a dead peer
 * with a readable trace rather than as a mysterious timeout.
 * <p>
 * Usage: {@code PeerNode <index> <port> <root> [scriptPath]}. Prints {@link #READY} once serving.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class PeerNode {

    /** printed once the node is serving, so {@link PeerCluster} can stop polling */
    public static final String READY = "PEER_READY";

    private PeerNode() {
        // static entry point
    }

    public static void main(final String[] args) throws Exception {
        final int index = Integer.parseInt(args[0]);
        final int port = Integer.parseInt(args[1]);
        final String root = args[2];
        final Path script = args.length > 3 ? Path.of(args[3]) : null;
        // mirror AbstractMetatronTest's boot sequence exactly — a peer VM is an ordinary VM
        BootLoader.BOOTING = true;
        BootLoader.TESTING = true;
        // boot at warn: a forked JVM does not inherit the parent's -Dlogback.configurationFile, so without this
        // it logs at default DEBUG and three concurrent chatty boots will not be ready before the test does
        BootLoader.load(rec(uri("log"), uri("warn")));
        InstSet.importInstSet(IO_ISA_TID);
        InstSet.importInstSet(f("/m/web"));
        trace("peer %d: booted (root=%s, port=%d)", index, root, port);
        // the namespace this peer owns — what makes it addressable for data rather than merely reachable
        memSpace.of(rec(uri(PATTERN), uri(root + "/#")), f("/sys/space/peer/store"));
        trace("peer %d: store mounted at %s/#", index, root);
        // the provisioning script: spaces and data, already modulated to this peer by the cluster.
        // Statement by statement, split on ';' and newline, because `exec` parses a SINGLE expression — the `;`
        // sugar belongs to the console's end()-splitting entry point, so a multi-statement script handed to
        // `exec` whole fails on its second statement. (This is also how @TestData's file branch works.)
        if (null != script && Files.exists(script) && !Files.readString(script).isBlank()) {
            final String source = Files.readString(script);
            trace("peer %d: provisioning:%n%s", index, source);
            for (final String line : source.split("[;\n]")) {
                final String statement = line.trim();
                if (statement.isEmpty())
                    continue;
                // `exec` on the mtron MIME type only PARSES: it returns the expression unevaluated, and
                // discarding that value looks exactly like a peer whose store is empty. The apply() is the
                // write, and the trace prints what actually landed so a silent no-op cannot hide again.
                final Obj result = MIME.MIMEType.APPLICATION_MTRON.exec(str(statement)).apply();
                trace("peer %d: provisioned %s => %s", index, statement, result);
            }
        } else {
            trace("peer %d: no provisioning script", index);
        }
        // the ws server that makes this peer addressable, exposing the mtron endpoint at /mtron
        wsSpace.of(mutableMap(
                        uri(PATTERN), uri("ws://#"),
                        uri(HOST), uri("ws://0.0.0.0:" + port),
                        uri(ROUTE), rec(uri("/mtron"), uri(mtron_wsHandler.WS_MTRON_HANDLER_TID.toString()))),
                f("/sys/space/peer/ws"));
        trace("peer %d: serving ws://0.0.0.0:%d/mtron", index, port);
        System.out.println(READY + " " + index + " " + port);
        System.out.flush();
        Thread.currentThread().join();
    }

    /** a peer trace line — stdout, which {@link PeerCluster} captures per peer */
    private static void trace(final String format, final Object... args) {
        System.out.printf("[peer] " + format + "%n", args);
        System.out.flush();
    }
}
