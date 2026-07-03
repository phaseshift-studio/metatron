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

import org.junit.jupiter.api.*;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.mach.space.LocalCluster;
import studio.phaseshift.metatron.isa.mach.space.LocalNode;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

/**
 * Sample test demonstrating Tier 1 (in-JVM) distributed metatron test
 * infrastructure using {@link LocalCluster}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Disabled
public class SampleDistributedMachineTest extends AbstractDistributedMetatronTest {

    private static final GraphittyLogger LOG = Graphitty.log(SampleDistributedMachineTest.class);
    private LocalCluster cluster;

    @BeforeEach
    public void setup() {
        this.cluster = createCluster(2);
    }

    @AfterEach
    public void tearDown() {
        if (this.cluster != null) {
            this.cluster.close();
            this.cluster = null;
        }
    }

    @Test
    @DisplayName("Two-node topology — peer entries visible in JVM map")
    public void testTwoNodeTopology() {
        assertPeerCount(cluster.node(0), 1);
        assertPeerCount(cluster.node(1), 1);
    }

    @Test
    @DisplayName("Read delegation — clusterSpace.read() reaches Router for local authority")
    public void testReadDelegation() {
        final LocalNode n0 = cluster.node(0);
        final fURI vid = f("ws://localhost:" + n0.port() + "/t/shared");

        Router.writeToSpace(vid, str("data"));

        // Read through the owning node's clusterSpace (authority matches)
        assertEquals(str("data"), cluster.cluster(0).read(vid));

        // Also directly through Router (clusterSpace delegates here internally)
        assertEquals(str("data"), Router.readFromSpace(vid));
    }

    @Test
    @DisplayName("Isolated namespaces — each node stores independently")
    public void testIsolatedNamespaces() {
        for (int i = 0; i < cluster.size(); i++) {
            Router.writeToSpace(
                    f("ws://localhost:" + cluster.node(i).port() + "/t/val"),
                    str("v" + i));
        }
        for (int i = 0; i < cluster.size(); i++) {
            assertEquals(str("v" + i),
                    Router.readFromSpace(
                            f("ws://localhost:" + cluster.node(i).port() + "/t/val")));
        }
    }

    @Test
    @DisplayName("Close is idempotent")
    public void testCloseIsIdempotent() {
        this.cluster.close();
        this.cluster.close();
        this.cluster = null;
    }
}
