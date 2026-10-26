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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * SPAWNING A PEER: when an authority is still on this physical machine, the mtron instance that answers it is a real
 * OS process — forked here, declared in the {@code /network/roster} roster, and reaped with the machine that holds its
 * connection ({@link #reap}, from {@code pop()} and {@code close()}).
 * <p>
 * THE MECHANISM HAS NO INSTRUCTION YET. It was the process half of a frame that stood in the peer and shipped every
 * code application made in it; that frame is gone (code reaches a peer by being shipped there in a {@code law_shard}
 * plan), and {@code push(<authority>)} is refused until spawning has an expression of its own.
 * <p>
 * This is the production form of the test harness's {@code PeerCluster}/{@code PeerNode} pair. The invariants it
 * keeps are the ones that make an authority-routed read a real distributed act:
 * <ul>
 *   <li>the child is a separate JVM with its own {@code Router}, so nothing is shared but the socket;</li>
 *   <li>the parent waits for the child's {@link MachNode#READY} marker rather than guessing a sleep, and reports
 *       the child's own trace when it dies early or never arrives;</li>
 *   <li>the roster entry is what makes the authority known (its address; dialing it is what reaches it) —
 *       membership is declared, never emergent, so a push that failed to start its node leaves no address that
 *       quietly resolves locally.</li>
 * </ul>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class PeerSpawn {

    // A node boots in ~2-4s. 30s is generous headroom, and the difference matters: a starved container (no free
    // processes to fork) must FAIL LOUDLY AND FAST rather than look like a suite hung for three minutes per test.
    private static final long READY_TIMEOUT_MS = 30_000L;

    private static final GraphittyLogger LOG = Graphitty.log(PeerSpawn.class);

    /**
     * The process table: a spawned authority's key ({@code scheme://host:port}) to the OS process serving it.
     * A {@code java.lang.Process} is not an {@code Obj}, so it cannot live in the machine's own rec — the table
     * is the machine/OS boundary, kept beside the object graph rather than inside it.
     */
    private static final Map<String, Forked> FORKED = new ConcurrentHashMap<>();

    /**
     * the roster keys THIS class declared, so a pop only retracts what a push introduced.
     */
    private static final Set<String> DECLARED = ConcurrentHashMap.newKeySet();

    private PeerSpawn() {
        // static only
    }

    /**
     * Fork (when the authority is local) and declare a peer, then hand back the address it was declared at — the dial
     * uri of its mtron endpoint. This is the process half only: the roster row is knowledge, and nothing here dials
     * the peer or builds anything that stands in it — reaching it is dialing that address.
     * <p>
     * IDEMPOTENT BY ADDRESS: asking for an authority that is already declared FINDS the peer rather than forking a
     * twin.
     */
    public static Obj spawn(final fURI requested) {
        final fURI authority = resolve(requested);
        final String key = keyOf(authority);
        // A PEER IS DECLARED ONCE. Hand back the address it already has.
        if (FORKED.containsKey(key) || DECLARED.contains(key))
            return address(authority);
        final boolean here = Network.Helper.isLoopbackHost(authority.host());
        try {
            if (here) {
                final Path log = Files.createTempFile("metatron-node-" + authority.port() + "-", ".log");
                final Process process = fork(authority, log);
                FORKED.put(key, new Forked(process, log));
                awaitReady(process, log, authority);
            }
            final Obj address = address(authority);
            declare(key, address);
            LOG.warn("spawned node %s (forked=%s)", key, here);
            return address;
        } catch (final IOException | RuntimeException e) {
            // a half-spawned peer must leave nothing behind: no orphan process, and no roster entry claiming an
            // address nothing serves
            cleanup(key);
            throw e instanceof RuntimeException runtime ? runtime : MTronException.of(e);
        }
    }

    /**
     * is the node serving this authority still running?
     */
    public static boolean alive(final fURI authority) {
        if (null == authority)
            return false;
        final Forked forked = FORKED.get(keyOf(authority));
        return null != forked && forked.process().isAlive();
    }

    /**
     * End a machine's spawned reach: destroy the processes forked for the connections it holds, retract the roster
     * entries declared for them. Idempotent, and a no-op for a machine whose connections were never spawned — so it
     * is safe from both {@code pop()} and {@code close()}.
     */
    public static void reap(final Machine machine) {
        if (null == machine)
            return;
        // A machine whose network SLOT is its parent's shares the parent's reach — reaping those would destroy a
        // connection the child never made. A pushed machine gets its own NetworkUnion level (Machine.push), so this
        // covers the machines that do not: one read back from memory carrying a plain rec, or a frame built before
        // the union is installed. Compare the SLOTS, not the resolved components: a slot the machine owns is its own
        // even when the rec in it is plain.
        final Obj network = machine.atDirect(uri(NETWORK));
        final Obj parent = machine.parent();
        if (parent instanceof Machine parentMachine && network == parentMachine.atDirect(uri(NETWORK)))
            return;
        // WHAT NAMES A PEER IS THE CONNECTION THE MACHINE HOLDS: network.peer is <service-uri> => peer::T, and each
        // entry's `authority` is what keys the process table. NOT the machine's vid: that is a local path, so it
        // carries no authority — an earlier version tested the VID for one, returned early, and left every forked
        // node running.
        network.<Network>as().peers().jvm().values().stream()
                .filter(Obj::isRec)
                .map(connection -> connection.asRec().<Obj>atDirect(uri(ORIGIN)))
                .filter(Obj::isUri)
                .forEach(authority -> cleanup(keyOf(authority.uriValue())));
    }

    /**
     * release everything a push for {@code key} established — safe to call whether or not any of it happened.
     */
    private static void cleanup(final String key) {
        reapForked(key);
        if (DECLARED.remove(key))
            undeclare(key);
    }

    /**
     * Where a spawned node answers: the dial uri of its {@code mtron_ws} endpoint. This is the value the roster
     * stores — an address, knowledge only: a {@code ws_client} dialed at it is what reaches the node.
     */
    public static Obj address(final fURI authority) {
        return uri(keyOf(authority) + MachNode.MTRON_ROUTE);
    }

    // ========================================================================
    // the roster
    // ========================================================================

    private static void declare(final String key, final Obj address) {
        // INTO roster/machine, and the roster rebuilt around it. The roster is a container now -- it carries
        // `discover` beside `machine` -- so a flat write at its root both loses the discovery registrations and
        // puts the entry where no reader looks.
        final Obj roster = Machine.read(Network.ROOT_ROSTER);
        final Map<Obj, Obj> fields = new LinkedHashMap<>(roster.isRec() ? roster.asRec().jvm() : Map.of());
        final Obj known = roster.isRec() ? roster.asRec().at(uri(MACHINE)) : noobj();
        final Map<Obj, Obj> machines = new LinkedHashMap<>(known.isRec() ? known.asRec().jvm() : Map.of());
        machines.put(uri(key), address);
        fields.put(uri(MACHINE), rec(machines));
        Machine.write(Network.ROOT_ROSTER, rec(fields));
        DECLARED.add(key);
    }

    private static void undeclare(final String key) {
        final Obj roster = Machine.read(Network.ROOT_ROSTER);
        if (!roster.isRec())
            return;
        final Obj known = roster.asRec().at(uri(MACHINE));
        if (!known.isRec())
            return;
        final Map<Obj, Obj> machines = new LinkedHashMap<>(known.asRec().jvm());
        machines.keySet().removeIf(k -> k.isUri() && key.equals(k.uriValue().toString()));
        final Map<Obj, Obj> fields = new LinkedHashMap<>(roster.asRec().jvm());
        fields.put(uri(MACHINE), rec(machines));
        Machine.write(Network.ROOT_ROSTER, rec(fields));
    }

    // ========================================================================
    // the process
    // ========================================================================

    private static Process fork(final fURI authority, final Path log) throws IOException {
        final List<String> command = new ArrayList<>();
        command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        // inherit only the VM flags the child cannot boot without — the parent's full arg list can carry agents
        for (final String arg : ManagementFactory.getRuntimeMXBean().getInputArguments())
            if (arg.startsWith("--add-opens") || arg.startsWith("--add-modules")
                    || arg.startsWith("--enable-native-access") || arg.startsWith("--sun-misc-unsafe-memory-access"))
                command.add(arg);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(MachNode.class.getName());
        command.add(String.valueOf(authority.port()));
        // the node's namespace is the LOCALIZED path — what the parent sends over the wire, not the parent's spelling
        command.add(authority.localize().toString());
        LOG.warn("forking node %s on port %d", keyOf(authority), authority.port());
        return new ProcessBuilder(command)
                .directory(new File(System.getProperty("user.dir")))
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
    }

    private static void awaitReady(final Process process, final Path log, final fURI authority) {
        final long deadline = System.currentTimeMillis() + READY_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive())
                throw MTronException.of("node %s exited before it was ready:\n%s", keyOf(authority), trace(log));
            if (trace(log).contains(MachNode.READY))
                return;
            try {
                Thread.sleep(100L);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw MTronException.of("interrupted while waiting for node %s", keyOf(authority));
            }
        }
        throw MTronException.of("node %s did not become ready in time:\n%s", keyOf(authority), trace(log));
    }

    private static void reapForked(final String key) {
        final Forked forked = FORKED.remove(key);
        if (null == forked)
            return;
        final Process process = forked.process();
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS))
                process.destroyForcibly();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        LOG.warn("reaped node %s (pid %d)", key, process.pid());
    }

    private static String trace(final Path log) {
        try {
            return Files.exists(log) ? Files.readString(log) : "";
        } catch (final Exception e) {
            return "<unreadable trace: %s>".formatted(e.getMessage());
        }
    }

    // ========================================================================
    // addressing
    // ========================================================================

    /**
     * The authority this push will serve: its port when it named one, otherwise a free port — reflected in the
     * handle's vid, which is the only way the caller learns where the node landed. A path is required, because the
     * node's store is mounted there; a path-less authority would have to claim everything on the node, including
     * the {@code /sys} it boots into.
     */
    /**
     * The peer address this request will be served at: its port when it named one, otherwise a free port. Called
     * BEFORE a frame's address is derived, because that address encodes the port.
     */
    public static fURI resolve(final fURI requested) {
        // localize(), NOT basePath(): basePath() keeps the scheme and authority, so it would name the node's store
        // `ws://host:port/worker/#` instead of `/worker/#`, and the node would answer nothing the parent sends.
        final String root = requested.localize().toString();
        if (root.isEmpty() || root.equals("/"))
            throw MTronException.of("push(%s) needs a namespace path: the node's store is mounted there", requested);
        final fURI scheme = null == requested.scheme() ? requested.scheme("ws") : requested;
        return scheme.hasPort() ? scheme : scheme.port(freePort());
    }

    /**
     * the roster key and the wire origin: {@code scheme://host:port}, with no path
     */
    private static String keyOf(final fURI authority) {
        return authority.path(List.of()).toString();
    }

    /**
     * The <b>connection key</b> for an authority: {@code scheme://host:port} as a uri — the same service uri the
     * roster is keyed by, and what a frame's {@code network.peer} map is keyed by. Derived rather than spelled so
     * the two homes cannot drift.
     */
    public static Obj service(final fURI authority) {
        return uri(keyOf(authority));
    }

    private static int freePort() {
        try (final ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (final IOException e) {
            throw MTronException.of(e);
        }
    }

    private record Forked(Process process, Path log) {
    }
}
