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

package studio.phaseshift.metatron.isa.grph;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * JanusGraph TestContainer wrapper for grphSpace tests.
 * <p>
 * Starts an in-memory JanusGraph instance with custom vertex ID support
 * enabled, so tests can use known vertex IDs ({@code /g/V/1}, etc.).
 * The container uses Groovy script evaluation as its Gremlin Server backend
 * — no external storage (Cassandra/BerkeleyDB) required.
 * <p>
 * Lifecycle: {@link #setup()} starts the container; {@link #teardown()} stops it.
 * The container should be managed at the test class level (static, @BeforeAll/@AfterAll).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class JanusGraphContainer {

    private GenericContainer<?> container;

    /**
     * Start the JanusGraph container with in-memory backend and custom vertex IDs.
     */
    public void setup() {
        container = new GenericContainer<>(DockerImageName.parse("janusgraph/janusgraph:1.1.0"))
                .withExposedPorts(8182)
                .withEnv("JANUS_PROPS_TEMPLATE", "inmemory")
                .withEnv("janusgraph.graph.set-vertex-id", "true")
                .withEnv("janusgraph.graph.allow-custom-vid-types", "true")
                .waitingFor(Wait.forLogMessage(".*Channel started at port 8182.*", 1))
                .withStartupTimeout(Duration.ofMinutes(2));
        container.start();
    }

    /**
     * @return the Docker host (usually {@code localhost})
     */
    public String getHost() {
        if (container == null || !container.isRunning()) {
            throw new IllegalStateException("JanusGraph container not started. Call setup() first.");
        }
        return container.getHost();
    }

    /**
     * @return the dynamically mapped host port for Gremlin Server (8182)
     */
    public int getPort() {
        if (container == null || !container.isRunning()) {
            throw new IllegalStateException("JanusGraph container not started. Call setup() first.");
        }
        return container.getMappedPort(8182);
    }

    /**
     * Stop the container and release resources.
     */
    public void teardown() {
        if (container != null) {
            container.stop();
            container = null;
        }
    }
}
