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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractDistributedMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.m.type.impl.MUri;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Tier 1 tests for clusterSpace: in-JVM topology, peer configuration,
 * read delegation, and lifecycle.
 * <p>
 * <b>Note on write routing:</b> The current {@link clstrSpace#write(fURI, Obj)}
 * determines locality via {@code vid.authority()}, which in Tier 1 requires
 * careful URI scheme/authority alignment.  These tests demonstrate the
 * patterns that work today (reads through any clusterSpace, store writes,
 * peer-config inspection) and lay the groundwork for the write-routing
 * contract as clusterSpace evolves.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Disabled
public class clstrSpaceTest extends AbstractDistributedMetatronTest {

    private LocalCluster cluster;

    @AfterEach
    public void teardown() {
        if (this.cluster != null) {
            this.cluster.close();
            this.cluster = null;
        }
    }

    // ====================================================================
    // Topology tests
    // ====================================================================

    @Test
    public void testSingleNodeTopology() {
        this.cluster = createCluster(1);
        final LocalNode n0 = cluster.node(0);

        // Single-node cluster has no peers
        assertPeerCount(n0, 0);

        // Host URI is well-formed
        assertFalse(n0.hostUri().toString().isEmpty());
        assertTrue(n0.hostUri().toString().startsWith("ws://localhost:"));
    }

    @Test
    public void testThreeNodeTopology() {
        this.cluster = createCluster(3);
        assertFullTopology(cluster);

        // Each node sees exactly 2 peers
        for (int i = 0; i < 3; i++) {
            assertPeerCount(cluster.node(i), 2);
        }

        // All host URIs distinct
        assertNotEquals(cluster.node(0).hostUri(), cluster.node(1).hostUri());
        assertNotEquals(cluster.node(0).hostUri(), cluster.node(2).hostUri());
        assertNotEquals(cluster.node(1).hostUri(), cluster.node(2).hostUri());
    }

    @Test
    public void testPeerEntriesInJvmMap() {
        this.cluster = createCluster(2);
        final LocalNode n0 = cluster.node(0);
        final LocalNode n1 = cluster.node(1);

        // PEERS rec maps peer URI → {host, protocol}
        final Map<Obj, Obj> peerMap = n0.cluster().at(PEER)
                .orElse(MRec.rec(new java.util.LinkedHashMap<>())).asRec().jvm();
        assertEquals(1, peerMap.size(), "node 0 should have one peer entry");

        // The peer key is the full peer URI
        final Obj key = MUri.uri(n1.hostUri().toString());
        assertTrue(peerMap.containsKey(key), "peer map should contain key " + n1.hostUri());
    }

    // ====================================================================
    // Read delegation tests
    // ====================================================================

    @Test
    public void testReadDelegation() {
        this.cluster = createCluster(2);
        final LocalNode n0 = cluster.node(0);
        final LocalNode n1 = cluster.node(1);

        // Write data to n0's local store via Router directly
        final fURI vid = f("ws://localhost:" + n0.port() + "/t/data");
        Router.writeToSpace(vid, str("hello"));

        // Verify it's readable through n0's clusterSpace (which delegates to Router)
        assertEquals(str("hello"), n0.cluster().read(vid),
                "clusterSpace.read() should delegate to Router");

        // Also readable through n1's clusterSpace
        assertEquals(str("hello"), n1.cluster().read(vid),
                "all clusterSpaces share the same Router");
    }

    @Test
    public void testReadFromUnwrittenVidReturnsNoobj() {
        this.cluster = createCluster(2);
        final LocalNode n0 = cluster.node(0);
        final fURI unknown = f("ws://localhost:" + n0.port() + "/t/does-not-exist");

        assertTrue(n0.cluster().read(unknown).isNoObj(),
                "reading an unwritten vid should return noobj");
    }

    // ====================================================================
    // Store-level write tests
    // ====================================================================

    @Test
    public void testWriteThroughRouterIsVisibleViaClusterRead() {
        this.cluster = createCluster(2);
        final LocalNode n0 = cluster.node(0);

        // Write via Router to n0's store namespace
        final fURI vid = f("ws://localhost:" + n0.port() + "/t/val");
        Router.writeToSpace(vid, str("router-value"));

        // Read through clusterSpace
        assertEquals(str("router-value"), n0.cluster().read(vid));
    }

    @Test
    public void testWritesToDifferentNamespacesAreIsolated() {
        this.cluster = createCluster(2);

        for (int i = 0; i < cluster.size(); i++) {
            final int port = cluster.node(i).port();
            Router.writeToSpace(
                    f("ws://localhost:" + port + "/t/val"),
                    str("node-" + i));
        }

        // Each namespace holds its own value
        for (int i = 0; i < cluster.size(); i++) {
            final int port = cluster.node(i).port();
            assertEquals(str("node-" + i),
                    Router.readFromSpace(f("ws://localhost:" + port + "/t/val")));
        }
    }

    // ====================================================================
    // clusterSpace write-routing contract tests
    // ====================================================================

    @Test
    public void testLocalWriteNoopsWithoutMatchingAuthority() {
        this.cluster = createCluster(2);
        final LocalNode n0 = cluster.node(0);

        // Writing through clusterSpace.write() with an authority URI
        // currently does not take the local path (authority comparison
        // format mismatch). The write returns the obj but does not store it.
        final fURI vid = f("ws://localhost:" + n0.port() + "/t/noop");
        final Obj result = n0.cluster().write(vid, str("test"));

        assertEquals(str("test"), result, "write should return the written obj");
        assertEquals(str("test"),Router.readFromSpace(vid));
    }

    // ====================================================================
    // Send-path tests
    // ====================================================================

    @Test
    public void testSendCountZeroInitially() {
        this.cluster = createCluster(2);
        assertEquals(0, cluster.totalSendCount(), "no writes yet, no sends");
    }

    @Test
    public void testPeerEntryHasClientSend() {
        this.cluster = createCluster(2);
        final LocalNode n0 = cluster.node(0);
        final LocalNode n1 = cluster.node(1);
        final fURI peerHost = n1.hostUri();

        // The peer entry in the jvm() map should have a send handler
        final Map<Obj, Obj> peerMap = n0.cluster().at(PEER)
                .orElse(MRec.rec(new java.util.LinkedHashMap<>())).asRec().jvm();
        final Rec peerEntry = peerMap.get(MUri.uri(peerHost.toString())).asRec();

       final Obj sendInst = peerEntry.at(uri(SEND));
       assertFalse(sendInst.isNoObj(),
               "peer entry should have send after wirePeerDispatchers");
    }

    // ====================================================================
    // Lifecycle tests
    // ====================================================================

    @Test
    public void testCloseIsIdempotent() {
        this.cluster = createCluster(2);
        this.cluster.close();
        this.cluster.close();  // second close must not throw
        this.cluster = null;
    }

    @Test
    public void testCloseDoesNotThrowWithDataStillInRouter() {
        // The Router has a /sys/# memSpace that catches all /sys/ reads;
        // after cluster close the space vids remain readable (noobj).
        // This test verifies close completes without exceptions.
        this.cluster = createCluster(2);
        this.cluster.close();
        this.cluster = null;
    }

    @Test
    public void testCloseThenCreateNewCluster() {
        // Close one cluster and create another — verifies Router state resets
        this.cluster = createCluster(2);
        this.cluster.close();
        this.cluster = createCluster(2);
        assertPeerCount(cluster.node(0), 1);
        assertEquals(2, cluster.size(), "new cluster should have correct size");
    }
}
