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

package studio.phaseshift.metatron.isa.mach.network;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.Network;
import studio.phaseshift.metatron.isa.mach.type.ui.Border;
import studio.phaseshift.metatron.isa.mach.type.ui.Stylable.Style;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.TableWidget;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRecClient;
import studio.phaseshift.metatron.isa.web.type.MIME;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_CLIENT_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A cluster of peer VMs — one OS process each — plus the declarations that make them addressable from the test
 * VM. The transport half of the {@code Network} authority dispatch: {@link #connect} writes the {@code /sys/peer}
 * roster, and {@link #transport} is the inst a {@code Network.read/write} hands its {@code from}/{@code to}
 * message to.
 */
public final class PeerCluster implements AutoCloseable {

    /** the namespace every peer owns */
    public static final String DEFAULT_ROOT = "/n";

    /** the mtron_ws endpoint the peers serve */
    public static final String MTRON_ROUTE = "/mtron";

    /**
     * the standard seed — peer {@code i} holds {@code <root>/a = i}, {@code <root>/b = i*10},
     * {@code <root>/c = i*100} and {@code <root>/probe = "peer-i-store-probe"}
     */
    public static final String DEFAULT_SEED_MATRIX =
            "$index.to($root/a); $index.mult(10).to($root/b); $index.mult(100).to($root/c); 'peer-$index-store-probe'.to($root/probe)";

    private static final MIME.MIMEType MTRON = MIME.MIMEType.APPLICATION_MTRON;
    private static final long REPLY_TIMEOUT_MS = 15_000L;
    private static final long READY_TIMEOUT_MS = 180_000L;

    private static final GraphittyLogger LOG = Graphitty.log(PeerCluster.class);

    private static volatile PeerCluster active;

    /** the cluster running in this JVM, or {@code null} */
    public static PeerCluster active() {
        return active;
    }

    /** the value at {@code <root>/a} on peer {@code i} under {@link #DEFAULT_SEED_MATRIX} */
    public static int seedA(final int i) {
        return i;
    }

    /** the value at {@code <root>/b} on peer {@code i} under {@link #DEFAULT_SEED_MATRIX} */
    public static int seedB(final int i) {
        return i * 10;
    }

    /** the value at {@code <root>/c} on peer {@code i} under {@link #DEFAULT_SEED_MATRIX} */
    public static int seedC(final int i) {
        return i * 100;
    }

    /** the value at {@code <root>/probe} on peer {@code i} under {@link #DEFAULT_SEED_MATRIX} */
    public static String probe(final int i) {
        return "peer-" + i + "-store-probe";
    }

    public static Builder of(final int count) {
        return new Builder(count);
    }

    /**
     * Declares a cluster before it exists. Seed templates are mtron source with {@code $index}/{@code $root}/
     * {@code $port} placeholders resolved once per peer by {@link #modulate}.
     */
    public static final class Builder {

        private final int count;
        private String root = DEFAULT_ROOT;
        private final List<String> seeds = new ArrayList<>();

        private Builder(final int count) {
            if (count < 1)
                throw new IllegalArgumentException("a cluster needs at least one peer");
            this.count = count;
        }

        /** the namespace every peer owns (default {@value #DEFAULT_ROOT}); also supplied as {@code $root} */
        public Builder store(final String root) {
            this.root = root;
            return this;
        }

        /** data writes, evaluated on every peer after its spaces exist */
        public Builder seed(final String... templates) {
            Collections.addAll(this.seeds, templates);
            return this;
        }

        public PeerCluster start() throws Exception {
            // ports first: $port cannot be resolved until every peer's address is known
            final List<Integer> ports = new ArrayList<>();
            for (int i = 0; i < this.count; i++)
                ports.add(freePort());
            final PeerCluster cluster = new PeerCluster(ports, this.root, true);
            LOG.warn("forking %d peer(s) under %s: %s", this.count, this.root,
                    ports.stream().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("<none>"));
            for (int i = 1; i <= this.count; i++) {
                final Path script = cluster.writeScript(i, this.seeds);
                cluster.processes.add(fork(i, cluster.port(i), this.root, script, cluster.logs.get(i - 1)));
            }
            cluster.awaitReady();
            active = cluster;
            return cluster;
        }
    }

    // ========================================================================
    // state
    // ========================================================================

    private final List<Integer> ports;
    private final String root;
    private final List<Process> processes = new ArrayList<>();
    private final List<Path> logs = new ArrayList<>();
    private final List<Peer> peers = new ArrayList<>();

    private PeerCluster(final List<Integer> ports, final String root, final boolean withTraces) throws IOException {
        this.ports = ports;
        this.root = root;
        for (int i = 0; i < ports.size(); i++) {
            if (withTraces)
                this.logs.add(Files.createTempFile("metatron-peer-" + (i + 1) + "-", ".log"));
            this.peers.add(new Peer(this, i + 1));
        }
    }

    /**
     * A cluster that exists only as addresses: {@code n} peers at fixed ports, no processes, no sockets.
     */
    public static PeerCluster detached(final int count, final String root, final int basePort) throws IOException {
        final List<Integer> ports = new ArrayList<>();
        for (int i = 0; i < count; i++)
            ports.add(basePort + i);
        return new PeerCluster(ports, root, false);
    }

    /** handles to every peer */
    public List<Peer> peers() {
        return List.copyOf(this.peers);
    }

    // ========================================================================
    // modulation — one template, N addresses
    // ========================================================================

    /**
     * Resolve a seed template for one peer: {@code $index}, {@code $port} and {@code $root} become that peer's
     * particulars.
     */
    String modulate(final String template, final int oneBased) {
        return template
                .replace("$root", this.root)
                .replace("$port", String.valueOf(this.port(oneBased)))
                .replace("$index", String.valueOf(oneBased));
    }

    /**
     * Write a peer's provisioning script: its seed templates, modulated to that peer, as pure mtron.
     */
    private Path writeScript(final int oneBased, final List<String> seeds) throws IOException {
        final List<String> statements = new ArrayList<>();
        for (final String seed : seeds)
            statements.add(this.modulate(seed, oneBased));
        final Path path = Files.createTempFile("metatron-peer-" + oneBased + "-", ".mtron");
        Files.writeString(path, String.join(";\n", statements));
        return path;
    }

    // ========================================================================
    // addressing
    // ========================================================================

    public int size() {
        return this.ports.size();
    }

    /** the store root every peer owns */
    public String root() {
        return this.root;
    }

    /** is the i-th peer's process still alive? */
    public boolean alive(final int oneBased) {
        final Process process = this.processes.isEmpty() ? null : this.processes.get(oneBased - 1);
        return null != process && process.isAlive();
    }

    /** the mtron port of the i-th peer (1-based) */
    public int port(final int oneBased) {
        return this.ports.get(oneBased - 1);
    }

    /** the i-th peer's connectable data-root prefix: {@code ws://localhost:<port><root>} */
    public fURI prefix(final int oneBased) {
        return f("ws://localhost:" + this.port(oneBased)).extend(this.root);
    }

    // ========================================================================
    // declaration — the roster that makes peers reachable
    // ========================================================================

    /**
     * Install the declared peer roster ({@code /sys/peer}): one entry per peer, keyed by its authority, whose
     * value is the transport that reaches it. This is the step that turns a running process into a peer this VM
     * may talk to.
     */
    public PeerCluster connect() {
        final Map<Obj, Obj> roster = new LinkedHashMap<>();
        for (int i = 1; i <= this.size(); i++)
            roster.put(uri("ws://localhost:" + this.port(i)), this.transport(i));
        Machine.write(Network.Helper.peerRosterPath(), rec(roster));
        LOG.warn("roster declared (%d peer(s)): %s", roster.size(),
                roster.keySet().stream().map(Object::toString).reduce((a, b) -> a + ", " + b).orElse("<empty>"));
        return this;
    }

    /**
     * The transport that reaches the i-th peer: open one {@code wsclient} to its {@code mtron_ws} endpoint, send
     * the message ({@code from(localized)} for a read, {@code obj.to(localized)} for a write), return the response.
     */
    public Inst transport(final int oneBased) {
        final int port = this.port(oneBased);
        return instLambda((lhs, inst) -> {
            final Obj response = this.rawSend(oneBased, lhs);
            return response.isNoObj() && !this.answers(oneBased)
                    ? fail("peer %d did not answer %s", port, lhs.toShortString())
                    : response;
        });
    }

    private Obj rawSend(final int oneBased, final Obj message) {
        final int port = this.port(oneBased);
        WebSocketRecClient client = null;
        try {
            // the 2-arg (timeout) sendRecv lives on the WebSocketObj the client drives, so build the rec first
            final WebSocketRec wsRec = new WebSocketRec(mutableMap(
                    uri(HOST), uri("ws://localhost:" + port + MTRON_ROUTE + "?out=" + MTRON.value),
                    uri(IN), uri(MTRON.value),
                    uri(OUT), uri(MTRON.value)), WS_CLIENT_TID, null);
            client = new WebSocketRecClient(wsRec);
            return wsRec.sendRecv(message, REPLY_TIMEOUT_MS);
        } catch (final Exception e) {
            return fail(e);
        } finally {
            if (null != client)
                client.close();
        }
    }

    /** a trivial round trip that needs no store at all, so it separates "nothing there" from "not answering" */
    private boolean answers(final int oneBased) {
        return !this.rawSend(oneBased, ObjmtronSerializer.parse("1.plus(1)")).isNoObj();
    }

    // ========================================================================
    // display — the table PeerDisplayTest renders
    // ========================================================================

    /**
     * Render a table the way {@code TestReport} renders its summary: a {@link TableWidget} with continuous
     * borders, under a bold title.
     */
    static String table(final String title, final List<String> headers, final List<List<Object>> rows) {
        final TableWidget widget = new TableWidget(headers);
        widget.style(Style.empty());
        widget.getStyle().border(Border.continuous).divider("│").headerDivider("│");
        rows.forEach(widget::addRow);
        return Graphitty.string("\n{{B}}%s{{X}}\n%s\n", title, widget.format());
    }

    // ========================================================================
    // teardown
    // ========================================================================

    /**
     * Stop every peer, and land each one's trace in {@code target/peer-N.log} first so it outlives the JVM.
     * Never throws.
     */
    @Override
    public void close() {
        if (active == this)
            active = null;
        for (final Process process : this.processes) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS))
                    process.destroyForcibly();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        this.processes.clear();
    }

    // ========================================================================
    // forking
    // ========================================================================

    private static int freePort() throws IOException {
        try (final ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static Process fork(final int index, final int port, final String root, final Path script,
                                final Path log) throws IOException {
        final List<String> command = new ArrayList<>();
        command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        // inherit only the VM flags the child needs — the parent's full arg list can carry agents that fail
        for (final String arg : ManagementFactory.getRuntimeMXBean().getInputArguments())
            if (arg.startsWith("--add-opens") || arg.startsWith("--add-modules")
                    || arg.startsWith("--enable-native-access") || arg.startsWith("--sun-misc-unsafe-memory-access"))
                command.add(arg);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(PeerNode.class.getName());
        command.add(String.valueOf(index));
        command.add(String.valueOf(port));
        command.add(root);
        command.add(script.toString());
        return new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
    }

    private void awaitReady() throws Exception {
        final long deadline = System.currentTimeMillis() + READY_TIMEOUT_MS;
        for (int i = 0; i < this.processes.size(); i++) {
            final Process process = this.processes.get(i);
            final Path log = this.logs.get(i);
            final long started = System.currentTimeMillis();
            boolean ready = false;
            while (System.currentTimeMillis() < deadline) {
                if (!process.isAlive())
                    throw new IllegalStateException(
                            "peer %d exited before it was ready:\n%s".formatted(i + 1, Files.readString(log)));
                if (Files.exists(log) && Files.readString(log).contains(PeerNode.READY)) {
                    ready = true;
                    break;
                }
                Thread.sleep(100L);
            }
            if (!ready)
                throw new IllegalStateException(
                        "peer %d did not become ready in time:\n%s".formatted(i + 1, Files.readString(log)));
            LOG.warn("peer %d loaded [%s] in %dms", i + 1, this.prefix(i + 1), System.currentTimeMillis() - started);
        }
        LOG.warn("all peers up and ready (%d)", this.processes.size());
    }
}
