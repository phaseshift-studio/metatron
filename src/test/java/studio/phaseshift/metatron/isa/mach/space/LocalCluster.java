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
import studio.phaseshift.metatron.isa.web.space.ws.handler.mtron_wsHandler;
import studio.phaseshift.metatron.isa.web.space.ws.wsSpace;

import java.util.*;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * Manages a collection of in-JVM {@link LocalNode} instances for distributed
 * metatron testing (Tier 1).
 * <p>
 * Usage:
 * <pre>
 *   try (var cluster = new LocalCluster(3)) {      // 3 nodes on generated ports
 *       cluster.init();                              // wire peers and create spaces
 *       cluster.cluster(0).write(...);               // write through node 0
 *       assertEquals(1, cluster.node(0).outboundSendCount(...));
 *   }                                                // auto-close on exit
 * </pre>
 * <p>
 * {@link LocalCluster} manages the two-phase lifecycle of {@link LocalNode}:
 * <ol>
 *   <li>All nodes are constructed with assigned ports.</li>
 *   <li>{@link #init()} wires peer references and creates each node's spaces.</li>
 * </ol>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class LocalCluster implements AutoCloseable {

    private final List<LocalNode> nodes;
    private final Map<Integer, LocalNode> nodesByPort;
    private boolean initialized = false;

    // ====================================================================
    // Construction
    // ====================================================================

    /**
     * Create a cluster with {@code nodeCount} nodes on generated ports.
     *
     * @param nodeCount number of nodes to create (must be {@literal >} 0)
     */
    public LocalCluster(final int nodeCount) {
        this(generatePorts(nodeCount));
    }

    /**
     * Create a cluster with nodes on the given ports.
     *
     * @param ports the ports to assign to each node
     */
    public LocalCluster(final int... ports) {
        this.nodes = Arrays.stream(ports)
                .mapToObj(LocalNode::new)
                .collect(Collectors.toList());
        this.nodesByPort = this.nodes.stream()
                .collect(Collectors.toMap(LocalNode::port, n -> n));
        this.nodesByPort.forEach((port, node) -> {
            wsSpace.of(mutableMap(
                    uri(HOST), uri("ws://localhost:" + port),
                    uri(PATTERN), uri("ws://localhost:" + port + "/#"),
                    uri(ROUTE), rec(mutableMap(uri("mtron"), new mtron_wsHandler(mutableMap(), null)))), f("/sys/space/cluster" + port));
        });
    }

    /**
     * Create a cluster from a pre-built collection of nodes.
     * The caller is responsible for eventually calling {@link #init()}.
     */
    public LocalCluster(final Collection<LocalNode> nodes) {
        this.nodes = new ArrayList<>(nodes);
        this.nodesByPort = this.nodes.stream()
                .collect(Collectors.toMap(LocalNode::port, n -> n));
    }

    // ====================================================================
    // Initialisation
    // ====================================================================

    /**
     * Wire peer references and initialise all nodes.
     * <p>
     * Phase 1 — {@link LocalNode#init(Map)} creates each node's spaces with
     * type-conforming peer entries ({@code host}, {@code protocol} only).
     * Phase 2 — {@link LocalNode#wirePeerDispatchers(Map)} injects the
     * {@code client.send} lambda via mutable updates, so the clusterSpace
     * type predicate is satisfied during construction.
     * <p>
     * Must be called once before any node is used.  Safe to call multiple times
     * (subsequent calls are no-ops).
     */
    public void init() {
        if (this.initialized)
            return;
        // Phase 1 — create spaces with type-conforming config
        for (final LocalNode node : this.nodes) {
            node.init(this.nodesByPort);
        }
        // Phase 2 — inject client.send lambdas post-type-validation
        for (final LocalNode node : this.nodes) {
            node.wirePeerDispatchers(this.nodesByPort);
        }
        this.initialized = true;
    }

    // ====================================================================
    // Accessors
    // ====================================================================

    /**
     * Number of nodes in the cluster.
     */
    public int size() {
        return this.nodes.size();
    }

    /**
     * Node by index (0-based).
     */
    public LocalNode node(final int index) {
        return this.nodes.get(index);
    }

    /**
     * Node by port number.
     */
    public LocalNode nodeByPort(final int port) {
        final LocalNode n = this.nodesByPort.get(port);
        if (n == null)
            throw new IllegalArgumentException("no node with port " + port);
        return n;
    }

    /**
     * The clusterSpace for the node at the given index.
     */
    public clstrSpace cluster(final int index) {
        return this.nodes.get(index).cluster();
    }

    /**
     * The clusterSpace for the node at the given port.
     */
    public clstrSpace clusterByPort(final int port) {
        return nodeByPort(port).cluster();
    }

    /**
     * All nodes in the cluster.
     */
    public List<LocalNode> nodes() {
        return Collections.unmodifiableList(this.nodes);
    }

    /**
     * All host URIs in the cluster.
     */
    public List<fURI> hostUris() {
        return this.nodes.stream()
                .map(LocalNode::hostUri)
                .collect(Collectors.toList());
    }

    /**
     * All ports in the cluster.
     */
    public int[] ports() {
        return this.nodes.stream()
                .mapToInt(LocalNode::port)
                .toArray();
    }

    /**
     * Total outbound send attempts across all nodes in the cluster.
     * Useful for verifying that remote writes don't leak between nodes
     * in unexpected ways.
     */
    public int totalSendCount() {
        return this.nodes.stream()
                .mapToInt(LocalNode::totalOutboundSendCount)
                .sum();
    }

    // ====================================================================
    // Lifecycle
    // ====================================================================

    /**
     * Shut down all nodes in reverse creation order and clean up
     * Router registrations.
     */
    @Override
    public void close() {
        // Close in reverse order for clean dependency teardown
        final ListIterator<LocalNode> it = this.nodes.listIterator(this.nodes.size());
        while (it.hasPrevious()) {
            try {
                it.previous().close();
            } catch (final Exception e) {
                // Log but don't suppress subsequent close calls
                System.err.println("error closing node: " + e.getMessage());
            }
        }
        this.nodes.clear();
        this.nodesByPort.clear();
        this.initialized = false;
    }

    // ====================================================================
    // Helpers
    // ====================================================================

    /**
     * Generate {@code count} unique ports in the ephemeral range.
     */
    private static int[] generatePorts(final int count) {
        if (count < 1)
            throw new IllegalArgumentException("nodeCount must be >= 1, got " + count);
        final Random rng = new Random();
        final Set<Integer> seen = new HashSet<>();
        final int[] ports = new int[count];
        for (int i = 0; i < count; i++) {
            int p;
            do {
                p = rng.nextInt(10000, 65000);
            } while (!seen.add(p));
            ports[i] = p;
        }
        return ports;
    }

    @Override
    public String toString() {
        return "LocalCluster{size=" + size() + ", ports=" + Arrays.toString(ports()) + "}";
    }
}
