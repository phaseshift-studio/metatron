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
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.SkipRegexTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.db.vec.space.ChromaV2Client;
import studio.phaseshift.metatron.isa.m.db.vec.space.VectorDBClient;
import studio.phaseshift.metatron.isa.m.db.vec.space.vecSpace;
import studio.phaseshift.metatron.util.MTronException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static studio.phaseshift.metatron.Tokens.HTTP;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
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
        @SkipRegexTest.Skip(method = "testRshiftUriGraphSpine")
})
public class ChromaDBVecSpaceTest extends AbstractVecSpaceTest {

    private static final int CHROMADB_PORT = 8000;
    private static GenericContainer<?> chromaContainer;
    private static fURI endpoint;

    @BeforeAll
    public static void startContainer() {
        chromaContainer = new GenericContainer<>(
                DockerImageName.parse("chromadb/chroma:1.5.3"))
                .withExposedPorts(CHROMADB_PORT)
                // the server keeps four files open for every collection it has indexed (an HNSW index's header, data,
                // length and link lists), and a deleted collection keeps its four: clearDatabase drops the collection a
                // case wrote, so every case that writes costs the server four descriptors. Docker's default soft limit of
                // 1024 runs out at about 250 such cases, and then the server's SQLite answers "(code: 14) unable to open
                // database file"
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withUlimits(List.of(new Ulimit("nofile", 65536L, 65536L))))
                .waitingFor(Wait.forHttp("/api/v2/heartbeat")
                        .forPort(CHROMADB_PORT))
                .withStartupTimeout(Duration.ofMinutes(3));

        chromaContainer.start();

        endpoint = f().scheme(HTTP).host(chromaContainer.getHost()).port(chromaContainer.getMappedPort(CHROMADB_PORT)).extend("/api/v2");
        staticClient = new ChromaV2Client(endpoint, mutableMap(uri("#"), lst(real(0.1), real(0.2))));

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

    @Test
    public void testAnEntityThatCarriesItsEmbeddingNeedsNoEmbeddingFunction() throws Exception {
        // a client set up with no embedding function, as the docker boot's vecspace is (boot/space/vec/chromadb.mtron)
        final VectorDBClient bare = new ChromaV2Client(endpoint, new LinkedHashMap<>());
        final VectorDBClient.CollectionData coll = bare.createCollection("bare");
        bare.upsert(coll.id(), f("bare"), new VectorDBClient.EntityData(f("given"), str("spark"), rec0(), lst(real(0.25), real(0.75))));
        AbstractMetatronTest.checkEquality(LOG, lst(real(0.25), real(0.75)),
                bare.get(coll.id(), List.of("given")).entities().getFirst().embedding(), true);
        // an entity that carries none has nothing to embed it
        assertThrows(MTronException.class, () -> bare.upsert(coll.id(), f("bare"),
                new VectorDBClient.EntityData(f("made"), str("arc"), rec0(), null)));
    }

    @AfterAll
    public static void stopContainer() {
        staticClient = null;
        if (chromaContainer != null && chromaContainer.isRunning()) {
            chromaContainer.stop();
        }
    }
}
