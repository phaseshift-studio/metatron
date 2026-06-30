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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.grph.space.schema.modernSchema.MODERN_SCHEMA_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Test suite for {@link studio.phaseshift.metatron.isa.grph.space.grphSpace}
 * against a TestContainers-managed JanusGraph.
 * <p>
 * Extends {@link AbstractGrphSpaceTest} which contains all shared test logic.
 * This class is responsible for JanusGraph-specific lifecycle:
 * starting/stopping the TestContainer and wiring the remote Gremlin Server
 * configuration.
 * <p>
 * Vertex lookups use property-based {@code .?[name=>'marko']} predicates
 * because JanusGraph auto-assigns vertex IDs.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@Disabled
public class JanusGrphSpaceTest extends AbstractGrphSpaceTest {

    private static final JanusGraphContainer JANUS_GRAPH = new JanusGraphContainer();

    public JanusGrphSpaceTest() {
        super(f("/g"));
    }

    @BeforeAll
    public static void startContainerAndSetup() {
        JANUS_GRAPH.setup();
        setupConfigs();
        createRewriteTestSpace();
    }

    @AfterAll
    public static void stopContainer() {
        JANUS_GRAPH.teardown();
    }

    private static void setupConfigs() {
        final String hostPort = "//" + JANUS_GRAPH.getHost() + ":" + JANUS_GRAPH.getPort();

        // ── per-test space config (pattern /g/#) ──
        perTestConfigRec = rec(
                uri(PATTERN), uri("/g/#"),
                uri(HOST), uri(f(hostPort)),
                uri(ROUTE), rec(
                        uri("/g/V"), uri("V"),
                        uri("/g/E"), uri("E"),
                        uri("/g/S"), uri(MODERN_SCHEMA_TID)),
                uri(CONFIG), rec(
                        uri("serializer.className"), uri("org.apache.tinkerpop.gremlin.util.ser.GraphBinaryMessageSerializerV1"),
                        uri("serializer.config.ioRegistries"), uri("org.janusgraph.graphdb.tinkerpop.JanusGraphIoRegistry"),
                        uri("gremlin.remote.remoteTraversalSourceName"), uri("g")));

        // ── rewrite test space config (pattern /grt/#) ──
        rewriteTestConfigRec = rec(
                uri(PATTERN), uri("/grt/#"),
                uri(HOST), uri(f(hostPort)),
                uri(ROUTE), rec(
                        uri("/grt/V"), uri("V"),
                        uri("/grt/E"), uri("E"),
                        uri("/grt/S"), uri(MODERN_SCHEMA_TID)),
                uri(CONFIG), rec(
                        uri("serializer.className"), uri("org.apache.tinkerpop.gremlin.util.ser.GraphBinaryMessageSerializerV1"),
                        uri("serializer.config.ioRegistries"), uri("org.janusgraph.graphdb.tinkerpop.JanusGraphIoRegistry"),
                        uri("gremlin.remote.remoteTraversalSourceName"), uri("g")));
    }
}
