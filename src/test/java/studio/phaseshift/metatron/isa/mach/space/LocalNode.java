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

package studio.phaseshift.metatron.isa.mach.space;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MInst;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TRUE;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.CLSTR_SPACE_TID;

/**
 * A single logical metatron node running in-process for distributed testing (Tier 1).
 * <p>
 * Each {@code LocalNode} owns:
 * <ul>
 *   <li>A {@link memSpace} registered under its authority namespace for local data storage</li>
 *   <li>A {@link clstrSpace} configured with its own host URI and references to peer nodes</li>
 *   <li>Per-peer send counters that track how many outbound peer writes occurred</li>
 * </ul>
 * <p>
 * Nodes are created via a two-phase lifecycle managed by {@link LocalCluster}:
 * <ol>
 *   <li>Construct with a port ({@link #LocalNode(int)})</li>
 *   <li>Initialize with the full peer map ({@link #init(Map)})</li>
 * </ol>
 * This allows the cluster to assign ports before wiring peer references
 * (which avoids the circular-dependency problem at construction time).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class LocalNode implements AutoCloseable {

    protected final GraphittyLogger LOG;

    // ── Identity ──────────────────────────────────────────────────────────
    private final int port;
    private final fURI hostUri;

    // ── Spaces ────────────────────────────────────────────────────────────
    private Space localStore;
    private clstrSpace cluster;

    // ── Peer bookkeeping ──────────────────────────────────────────────────
    /**
     * Count of outbound sends to each peer, keyed by peer host URI.
     * Incremented in the send lambda when {@link clstrSpace#write(fURI, Obj)}
     * dispatches a remote write through the peer's {@code client.send}.
     */
    final Map<fURI, AtomicInteger> outboundSendCounts = new ConcurrentHashMap<>();

    // ── Initialization flags ──────────────────────────────────────────────
    private boolean initialized = false;

    // ====================================================================
    // Construction
    // ====================================================================

    /**
     * Create a minimal node shell.  Call {@link #init(Map)} before using.
     *
     * @param port the unique port for this node (used to build {@code hostUri})
     */
    public LocalNode(final int port) {
        this.port = port;
        this.hostUri = f("ws://localhost:" + port);
        this.LOG = Graphitty.log(this);
    }

    /**
     * Initialize spaces and register peers.  Called by {@link LocalCluster}
     * after all node ports have been assigned.
     *
     * @param allNodes all nodes in the cluster, keyed by port
     */
    public void init(final Map<Integer, LocalNode> allNodes) {
        if (this.initialized)
            throw new IllegalStateException("LocalNode is already initialized");

        // ── 1. Local data store ───────────────────────────────────────────
        // Register a memSpace under this node's authority so the Router can
        // route reads/writes to "ws://localhost:<port>/..." to this node.
        this.localStore = memSpace.of(rec(
                uri(PATTERN), uri("ws://localhost:" + port + "/#"),
                uri(ROUTE), MRec.rec(new java.util.LinkedHashMap<>())
        ), f("/sys/space/cluster/store/" + port));

        // ── 2. Peer configuration ─────────────────────────────────────────
        final Rec peerRec = buildPeerRec(allNodes);

        // ── 3. Cluster-space configuration ───────────────────────────────
        final Map<Obj, Obj> config = new HashMap<>();
        config.put(uri(PATTERN), uri("ws://localhost:" + port + "/cluster/#"));
        config.put(uri(HOST), uri(this.hostUri.toString()));
        if (!peerRec.jvm().isEmpty())
            config.put(uri(PEER), peerRec);

        // ── 4. Create the clusterSpace ────────────────────────────────────
        this.cluster = new clstrSpace(
                new HashMap<>(),
                config,
                CLSTR_SPACE_TID,
                f("/sys/space/cluster/" + port)
        );

        this.initialized = true;
        LOG.info("LocalNode initialized: port={{b}}%d{{X}} host={{b}}%s{{X}} with %d peers",
                port, hostUri, peerRec.jvm().size());
    }

    /**
     * Build the peers rec for this node.  Each peer entry is an empty
     * {@link Rec} keyed by the peer&#39;s host URI.  In production a peer
     * entry is a {@code wsclient::T} (live {@code WebSocketRecClient});
     * for Tier 1 we use a plain Rec with {@code send} and {@code state}
     * instructions injected later by {@link #wirePeerDispatchers(Map)}.
     */
    private Rec buildPeerRec(final Map<Integer, LocalNode> allNodes) {
        final Map<Obj, Obj> entries = new java.util.LinkedHashMap<>();
        for (final LocalNode peer : allNodes.values()) {
            if (peer.port == this.port)
                continue;
            entries.put(uri(peer.hostUri), rec(uri(STATE), MInst.instLambda((lhs, inst) -> BOOL_TRUE)));
        }
        return MRec.rec(entries);
    }

    /**
     * After the clusterSpace is constructed and type-validation has passed,
     * inject {@code send} into each peer entry so that the remote-write
     * path in {@link clstrSpace#write(fURI, Obj)} can dispatch through it
     * (Tier 1 simulation — evaluates via Router rather than live WebSocket).
     * <p>
     * Uses {@link Rec#jvm()} for raw key lookup because {@code Rec.at()}
     * treats URI keys as path navigation and cannot resolve authority-only
     * URIs (e.g. {@code ws://host:port}).
     */
    public void wirePeerDispatchers(final Map<Integer, LocalNode> allNodes) {
        if (this.cluster == null)
            throw new IllegalStateException("clusterSpace not initialized; call init() first");

        final Map<Obj, Obj> peerJvm = this.cluster.at(PEER)
                .orElse(MRec.rec(new java.util.LinkedHashMap<>()))
                .asRec().jvm();

        for (final LocalNode peer : allNodes.values()) {
            if (peer.port == this.port)
                continue;

            final Obj peerKey = uri(peer.hostUri.toString());
            final Obj peerEntry = peerJvm.get(peerKey);
            if (peerEntry == null || peerEntry.isNoObj())
                continue;

            // Create the send lambda that records the dispatch and evaluates
            // via Router (simulating remote peer evaluation in Tier 1)
            final Inst sendLambda = MInst.instLambda((lhs, inst) -> {
                outboundSendCounts
                        .computeIfAbsent(peer.hostUri, k -> new AtomicInteger(0))
                        .incrementAndGet();
                LOG.debug("in-JVM send: {} -> {} (count={})",
                        hostUri, peer.hostUri, outboundSendCounts.get(peer.hostUri).get());
                // Extract URI from instruction and evaluate via Router
                final Inst lhsInst = (Inst) lhs;
                final fURI targetVid = lhsInst.arg(0).uriValue();
                final Obj maybeObj = lhsInst.arg(1);
                if (maybeObj.isNoObj())
                    return Router.readFromSpace(targetVid);
                else
                    return Router.writeToSpace(targetVid, maybeObj);
            });

            // Inject send and state via mutable update — type predicate already passed
            peerEntry.asRec().at(uri(SEND), sendLambda, Poly.MUTABLE);
            peerEntry.asRec().at(uri(STATE), MInst.instLambda((lhs, inst) -> BOOL_TRUE), Poly.MUTABLE);
        }
    }

    // ====================================================================
    // Accessors
    // ====================================================================

    /**
     * The unique port assigned to this node.
     */
    public int port() {
        return this.port;
    }

    /**
     * The host URI (e.g. {@code ws://localhost:21001}).
     */
    public fURI hostUri() {
        return this.hostUri;
    }

    /**
     * The clusterSpace for this node.
     */
    public clstrSpace cluster() {
        return this.cluster;
    }

    /**
     * The local data store (memSpace) for this node.
     */
    public Space localStore() {
        return this.localStore;
    }

    /**
     * How many times this node attempted a write to the peer at {@code peerHost}.
     * Useful for verifying that remote writes flow through the send path.
     */
    public int outboundSendCount(final fURI peerHost) {
        final AtomicInteger c = this.outboundSendCounts.get(peerHost);
        return c == null ? 0 : c.get();
    }

    /**
     * Total outbound send attempts across all peers.
     */
    public int totalOutboundSendCount() {
        return this.outboundSendCounts.values().stream()
                .mapToInt(AtomicInteger::get)
                .sum();
    }

    // ====================================================================
    // Lifecycle
    // ====================================================================

    @Override
    public void close() {
        if (this.cluster != null) {
            Router.global().removeSpace(this.cluster.vid());
            this.cluster.close();
            this.cluster = null;
        }
        if (this.localStore != null) {
            Router.global().removeSpace(this.localStore.vid());
            this.localStore.close();
            this.localStore = null;
        }
        this.initialized = false;
    }

    @Override
    public String toString() {
        return "LocalNode{port=" + port + ", host=" + hostUri + ", initialized=" + initialized + "}";
    }
}
