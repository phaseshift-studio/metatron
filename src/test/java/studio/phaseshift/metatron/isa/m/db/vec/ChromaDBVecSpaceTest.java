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

package studio.phaseshift.metatron.isa.m.db.vec;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import studio.phaseshift.metatron.SkipRegexTest;
import studio.phaseshift.metatron.isa.m.db.vec.space.ChromaV2Client;
import studio.phaseshift.metatron.isa.m.db.vec.space.VectorDBClient;
import studio.phaseshift.metatron.isa.m.db.vec.space.vecSpace;

import java.time.Duration;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * Test suite for {@link vecSpace} backed by a TestContainers-managed
 * ChromaDB 1.5.3 instance (v2 API).
 * <p>
 * Uses {@link GenericContainer} directly (following the
 * {@code MySQLDatabaseConfig} pattern).  The 0.1.7 Java client is
 * incompatible with ChromaDB &ge; 1.0 — this test uses
 * {@link ChromaV2Client} instead.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@SkipRegexTest({
        // the factorization contract (AbstractSpaceTest#testReadsAreInvariantUnderFactorization): the cases this
        // space does not honor yet, by cause -- a fix deletes its lines
        @SkipRegexTest.Skip(method = "testReadsAreInvariantUnderFactorization", params = {
                // A: # answers structures beside the leaves (# answers only leaves, at full uris)
                "^M8/(entry|row|nested)$", "^X13/(nested|partial|entry|split)$",
                // B: an interior node of flat keys is not its subtree, not a + entry, not a walk step
                "^X8/(entry|split)$", "^X9/(entry|split)$", "^X10/(entry|split)$", "^X11/(entry|split)$",
                "^X12/(entry|split)$", "^X14/entry$", "^X16/(entry|split)$", "^W1/entry$", "^W2/entry$", "^W3/entry$",
                // C: an interior node inside a path-keyed rec is not its subtree, not a + entry, not a walk step
                "^X8/pathkeys$", "^X9/(partial|pathkeys)$", "^X10/(partial|pathkeys)$", "^X11/(partial|pathkeys)$",
                "^X12/(partial|pathkeys)$", "^X14/(partial|pathkeys)$", "^X16/pathkeys$", "^W1/pathkeys$", "^W2/pathkeys$",
                "^W3/pathkeys$",
                // D: a pattern across a stored rec and flat keys answers one of them, never both
                "^X4/split$", "^X5/split$",
                // E: a wildcard key answers an address a literal path key prefixes
                "^P1/pathkeys$", "^P2/pathkeys$",
                // F: vecSpace -- this space reads the layout differently
                "^M1/(entry|row)$", "^M3/entry$", "^M4/(entry|row)$", "^M5/(entry|row)$", "^M6/(entry|row|nested)$",
                "^M7/(entry|row|nested)$", "^X1/(entry|split)$", "^X2/entry$", "^X3/(partial|entry)$", "^X4/entry$",
                "^X5/(partial|entry)$", "^X6/(partial|entry|split)$", "^X7/(entry|split)$", "^X11/nested$", "^X12/nested$",
                "^W2/nested$", "^W4/entry$", "^W5/(entry|nested|pathkeys)$"
        }),
        @SkipRegexTest.Skip(method = "testRshiftUriGraphSpine")
})
public class ChromaDBVecSpaceTest extends AbstractVecSpaceTest {

    private static final int CHROMADB_PORT = 8000;
    private static GenericContainer<?> chromaContainer;

    @BeforeAll
    public static void startContainer() {
        chromaContainer = new GenericContainer<>(
                DockerImageName.parse("chromadb/chroma:1.5.3"))
                .withExposedPorts(CHROMADB_PORT)
                .waitingFor(Wait.forHttp("/api/v2/heartbeat")
                        .forPort(CHROMADB_PORT))
                .withStartupTimeout(Duration.ofMinutes(3));

        chromaContainer.start();

        final String endpoint = "http://" + chromaContainer.getHost()
                + ":" + chromaContainer.getMappedPort(CHROMADB_PORT) + "/api/v2";
        staticClient = new ChromaV2Client(f(endpoint), mutableMap(uri("#"), lst(real(0.1), real(0.2))));

        // Reset any leftover data from previous test runs
        try {
            for (final VectorDBClient.CollectionData c : staticClient.listCollections()) {
                staticClient.deleteCollection(c.name());
            }
        } catch (final Exception e) {
            System.err.println("chromadb startup cleanup warning: " + e.getMessage());
        }

        System.out.println("chromaDB 1.5.3 container started at: " + endpoint);
    }

    @AfterAll
    public static void stopContainer() {
        staticClient = null;
        if (chromaContainer != null && chromaContainer.isRunning()) {
            chromaContainer.stop();
        }
    }
}
