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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * Demonstration of the Tier 1 (in-JVM) distributed test infrastructure.
 * <p>
 * Validates cluster-space topology, read delegation, and store-level
 * write isolation across multiple in-process metatron nodes.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class DistributedMachineEvaluationTest extends AbstractDistributedMetatronTest {

    private static final GraphittyLogger LOG = Graphitty.log(DistributedMachineEvaluationTest.class);
    private LocalCluster cluster;

    @BeforeEach
    public void setup() {
        this.cluster = createCluster(3);
        LOG.info("=== Distributed Machine Evaluation Test Setup === 3 nodes");
    }

    @AfterEach
    public void tearDown() {
        if (this.cluster != null) {
            this.cluster.close();
            this.cluster = null;
        }
    }

    @Test
    @DisplayName("Full 3-node topology — each node sees both peers")
    public void testFullTopology() {
        assertFullTopology(cluster);
        for (int i = 0; i < cluster.size(); i++) {
            assertPeerCount(cluster.node(i), 2);
        }
    }

    @Test
    @DisplayName("Read delegates to Router — each node reads its own namespace via clusterSpace")
    public void testReadOwnNamespace() {
        // Write to each node's namespace via Router
        for (int i = 0; i < cluster.size(); i++) {
            final int port = cluster.node(i).port();
            Router.writeToSpace(
                    f("ws://localhost:" + port + "/t/x"),
                    str("node-" + i));
        }

        // Each node reads its own namespace through its clusterSpace
        for (int i = 0; i < cluster.size(); i++) {
            assertEquals(str("node-" + i),
                    cluster.cluster(i).read(
                            f("ws://localhost:" + cluster.node(i).port() + "/t/x")));
        }

        // Router directly sees all namespaces (no authority routing)
        for (int i = 0; i < cluster.size(); i++) {
            assertEquals(str("node-" + i),
                    Router.readFromSpace(
                            f("ws://localhost:" + cluster.node(i).port() + "/t/x")));
        }
    }

    @Test
    @DisplayName("Write isolation — each namespace stores independently")
    public void testWriteIsolation() {
        final LocalNode n0 = cluster.node(0);
        final LocalNode n1 = cluster.node(1);

        Router.writeToSpace(
                f("ws://localhost:" + n0.port() + "/t/val"), str("n0-only"));

        // n0's namespace has the value
        assertEquals(str("n0-only"),
                Router.readFromSpace(f("ws://localhost:" + n0.port() + "/t/val")));

        // n1's namespace is empty at the same path
        assertTrue(
                Router.readFromSpace(f("ws://localhost:" + n1.port() + "/t/val")).isNoObj());
    }

    @Test
    @DisplayName("Unwritten VID returns noobj through clusterSpace")
    public void testUnwrittenVid() {
        final fURI unknown = f("ws://localhost:" + cluster.node(0).port() + "/t/nowhere");
        assertTrue(cluster.cluster(0).read(unknown).isNoObj());
    }
}
