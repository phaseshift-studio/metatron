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

package studio.phaseshift.metatron;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;

import java.util.Map;

import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.type.impl.MUri;
import studio.phaseshift.metatron.isa.mach.space.LocalCluster;
import studio.phaseshift.metatron.isa.mach.space.LocalNode;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.PEER;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * Abstract base for distributed metatron tests across all three tiers.
 * <p>
 * Provides utility methods and shared infrastructure for:
 * <ul>
 *   <li><b>Tier 1</b> (in-JVM) — {@link LocalCluster}, {@link LocalNode}</li>
 *   <li><b>Tier 2</b> (forked JVMs) — process-based node management</li>
 *   <li><b>Tier 3</b> (LAN multi-host) — remote-host configuration</li>
 * </ul>
 * <p>
 * Bootloader initialisation is handled by the parent class {@link AbstractMetatronTest}.
 * This class adds only distributed-specific utilities and does NOT declare its own
 * {@code @BeforeAll} / {@code @AfterAll} to avoid re-entrant initialisation.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractDistributedMetatronTest extends AbstractMetatronTest {

    protected GraphittyLogger LOG = Graphitty.log(this);

    // ========================================================================
    // Tier 1 — Cluster factory methods
    // ========================================================================

    /**
     * Create and initialise an in-JVM {@link LocalCluster} with the given
     * number of nodes on generated ports.
     *
     * @param nodeCount number of nodes (must be {@literal >} 0)
     * @return an initialised cluster (peers wired, spaces created)
     */
    public static LocalCluster createCluster(final int nodeCount) {
        final LocalCluster cluster = new LocalCluster(nodeCount);
        cluster.init();
        return cluster;
    }

    /**
     * Create and initialise an in-JVM {@link LocalCluster} on the given ports.
     *
     * @param ports port numbers, one per node
     * @return an initialised cluster
     */
    public static LocalCluster createCluster(final int... ports) {
        final LocalCluster cluster = new LocalCluster(ports);
        cluster.init();
        return cluster;
    }

    // ========================================================================
    // Topology validation helpers
    // ========================================================================

    /**
     * Assert that every node in the cluster can see every other node as a peer.
     * Checks the PEERS rec's raw {@code jvm()} map — {@code Rec.at()} does
     * path navigation and cannot resolve authority-only URIs (e.g.
     * {@code ws://host:port}).
     */
    public static void assertFullTopology(final LocalCluster cluster) {
        final int n = cluster.size();
        assertTrue(n >= 2, "need at least 2 nodes for topology checks");
        for (int i = 0; i < n; i++) {
            final LocalNode ni = cluster.node(i);
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                final fURI peerHost = cluster.node(j).hostUri();
                final Obj peersObj = ni.cluster().at(PEER)
                        .orElse(MRec.rec(new java.util.LinkedHashMap<>()));
                final Map<Obj, Obj> peerMap = peersObj.asRec().jvm();
                assertTrue(peerMap.containsKey(MUri.uri(peerHost.toString())),
                        "node %d should have peer entry for %s, but none found".formatted(i, peerHost));
            }
        }
    }

    /**
     * Assert that the clusterSpace of the given node reports the expected
     * number of peers.  Uses the raw {@code jvm()} map for accuracy.
     */
    public static void assertPeerCount(final LocalNode node, final int expected) {
        final Obj peers = node.cluster().at(PEER)
                .orElse(MRec.rec(new java.util.LinkedHashMap<>()));
        assertEquals(expected, peers.asRec().jvm().size(),
                "peer count mismatch for node " + node.port());
    }

    /**
     * Assert that a local write through the given node's clusterSpace is
     * visible via the global Router at the expected VID.
     */
    public static void assertLocalWriteVisible(final LocalNode node,
                                               final fURI vid,
                                               final Obj written) {
        final Obj readBack = Router.readFromSpace(vid);
        assertEquals(written, readBack,
                "write through node %s should be visible via Router at %s"
                        .formatted(node.hostUri(), vid));
    }

    /**
     * Verify that no data was stored at the given VID (i.e. the write
     * was NOT routed locally by that clusterSpace).
     */
    public static void assertNotStoredLocally(final fURI vid) {
        assertTrue(Router.readFromSpace(vid).isNoObj(),
                "VID %s should not be stored locally".formatted(vid));
    }

    // ========================================================================
    // Send-path validation helpers
    // ========================================================================

    /**
     * Assert that a specific number of outbound sends were recorded from
     * {@code source} to {@code targetPeer}.
     */
    public static void assertSendCount(final LocalNode source,
                                       final fURI targetPeer,
                                       final int expected) {
        assertEquals(expected, source.outboundSendCount(targetPeer),
                "send count from %s to %s".formatted(source.hostUri(), targetPeer));
    }

    /**
     * Assert that the total send count across all peers is exactly {@code expected}.
     */
    public static void assertTotalSendCount(final LocalCluster cluster,
                                            final int expected) {
        assertEquals(expected, cluster.totalSendCount(),
                "total cluster send count");
    }

    // ========================================================================
    // Wait / retry helpers
    // ========================================================================

    /**
     * Poll {@code Router.readFromSpace(vid)} until it returns a non-noobj value
     * or the timeout expires.  Useful for eventually-consistent assertions in
     * asynchronous topologies.
     *
     * @param vid     the VID to poll
     * @param timeout max wait in milliseconds
     * @return the first non-noobj value, or noobj if the poll timed out
     */
    public static Obj waitForVisible(final fURI vid, final long timeout) {
        final long deadline = System.currentTimeMillis() + timeout;
        Obj result;
        do {
            result = Router.readFromSpace(vid);
            if (!result.isNoObj())
                return result;
            try {
                Thread.sleep(10);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return noobj();
            }
        } while (System.currentTimeMillis() < deadline);
        return result;
    }

    /**
     * Poll until {@code Router.readFromSpace(vid)} returns the expected value
     * or the timeout expires, then assert equality.
     */
    public static void assertEventuallyEquals(final fURI vid,
                                              final Obj expected,
                                              final long timeoutMs) {
        final Obj actual = waitForVisible(vid, timeoutMs);
        assertEquals(expected, actual,
                "expected %s to eventually be %s at %s".formatted(
                        vid, expected, actual));
    }
}
