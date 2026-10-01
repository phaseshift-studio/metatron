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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the harness's own lifecycle: fork → ready → seed → roster → attach → ask.
 * <p>
 * This is the suite that would have caught the bug that cost us a debugging round — forked peers inheriting
 * neither the parent's log config nor its quiet, so three DEBUG-chatty boots blew the ready window. Nothing
 * else exercised {@link PeerCluster#of} / {@code start()} / {@link PeerCluster#connect()}: the pure tests use
 * {@link PeerCluster#detached} and never fork, and the display test renders a table directly. A harness whose
 * setup is untested fails as a mystery timeout in somebody else's test.
 * <p>
 * Two peers, not three: enough to prove addressing is per-peer, cheap enough to run deliberately.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class PeerClusterLifecycleTest extends AbstractMetatronTest {

    private static PeerCluster cluster;

    @BeforeAll
    public static void setup() throws Exception {
        cluster = PeerCluster.of(2)
                .store(PeerCluster.DEFAULT_ROOT)
                .seed(PeerCluster.DEFAULT_SEED_MATRIX)
                .start()
                .connect();
        TestSpace.Helper.attach(cluster.peers(), "memspace::[pattern => /shared/#]@/sys/space/shared");
    }

    @AfterAll
    public static void teardown() {
        if (null != cluster) {
            cluster.close();
            cluster = null;
        }
    }

    @Test
    public void testEveryPeerIsUpAndAddressed() {
        assertEquals(2, cluster.size());
        for (final Peer peer : cluster.peers()) {
            assertTrue(peer.alive(), peer + " should be alive");
            assertTrue(peer.port() > 0, peer + " should have a port");
        }
        assertNotEquals(cluster.peers().get(0).prefix(), cluster.peers().get(1).prefix(),
                "peers must be addressed distinctly");
    }

    @Test
    public void testProvisionedSeedsLanded() {
        // one peer, one namespace, and a seed matrix modulated per peer — so the same path answers
        // differently on each machine. If provisioning ever silently no-ops again, every read here is
        // noobj rather than a plausible number.
        for (final Peer peer : cluster.peers()) {
            final int index = peer.index();
            final String root = cluster.root();
            assertEquals(index, cluster.send(index, "*<" + root + "/a>").intValue(),
                    "peer " + index + "'s /a is its own index");
            assertEquals(index * 10, cluster.send(index, "*<" + root + "/b>").intValue(),
                    "peer " + index + "'s /b is ten times its index");
            assertEquals(index * 100, cluster.send(index, "*<" + root + "/c>").intValue(),
                    "peer " + index + "'s /c is a hundred times its index");
            assertEquals(PeerCluster.probe(index), cluster.send(index, "*<" + root + "/probe>").strValue(),
                    "peer " + index + " holds its own probe value");
        }
    }

    @Test
    public void testAttachedSpaceIsMountedOnEveryPeer() {
        for (final Peer peer : cluster.peers())
            assertTrue(peer.evaluate("*</sys/space/shared>.vid()").isUri(),
                    "the TestSpace definition should be mounted on " + peer);
    }

    @Test
    public void testPeerProfileSeesMonadicWork() {
        for (final Peer peer : cluster.peers())
            assertTrue(peer.flow(PeerCluster.FLOW_PROBE, "monads") > 0,
                    peer + " should report monadic flow for " + PeerCluster.FLOW_PROBE);
    }

    @Test
    public void testPeerAnalysisRendersLiveRows() {
        final String analysis = PeerCluster.peerAnalysisToString(cluster.peers(),
                PeerCluster.call("*<" + cluster.root() + "/a>"),
                PeerCluster.call("*</sys/space/shared>"));
        assertTrue(analysis.contains(String.valueOf(cluster.port(1))), analysis);
        assertTrue(analysis.contains("┌"), "the table is bordered: " + analysis);
    }

    @Test
    public void testTracesNarrateWhatEachPeerDid() {
        final String trace = cluster.trace(1);
        assertTrue(trace.contains("store mounted"), trace);
        assertTrue(trace.contains(PeerNode.READY), trace);
    }
}
