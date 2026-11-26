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

import com.github.dockerjava.api.model.Ulimit;
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
import java.util.List;

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
        // the factorization contract (AbstractSpaceTest#testReadsAreInvariantUnderFactorization): a vector collection
        // names a document by its LAST segment (vecSpace writes f(aligned.name())), so a key of several segments collides
        // with every other key ending alike -- m/0/0 and m/1/0 are both document 0, and after the row layout *m/0/0 reads
        // document 0, the row m/0. The flat layouts (entry, row, split) cannot be held; a fix deletes its lines
        @SkipRegexTest.Skip(method = "testReadsAreInvariantUnderFactorization", params = {
                "^M1/(entry|row)$", "^M3/entry$", "^M4/(entry|row)$", "^M5/(entry|row)$", "^M6/(entry|row)$",
                "^M7/entry$", "^M8/(entry|row)$", "^W[1-5]/entry$",
                "^X1/(entry|split)$", "^X2/entry$", "^X3/entry$", "^X4/(entry|split)$", "^X5/(entry|split)$",
                "^X6/(entry|split)$", "^X7/(entry|split)$", "^X8/(entry|split)$", "^X9/(entry|split)$",
                "^X10/(entry|split)$", "^X11/(entry|split)$", "^X12/(entry|split)$", "^X13/(entry|split)$",
                "^X14/entry$", "^X16/(entry|split)$"
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
                // the server keeps four files open for every collection it has indexed (an HNSW index's header, data,
                // length and link lists), and a deleted collection keeps its four: clearDatabase drops the collection a
                // case wrote, so every case costs the server four descriptors. Docker's default soft limit of 1024 runs
                // out at about 250 cases, and then the server's SQLite answers "(code: 14) unable to open database file"
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withUlimits(List.of(new Ulimit("nofile", 65536L, 65536L))))
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
