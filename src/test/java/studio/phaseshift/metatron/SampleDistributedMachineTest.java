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
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Sample test demonstrating how the distributed machine evaluation framework
 * would be used for testing distributed systems.
 */
public class SampleDistributedMachineTest extends AbstractDistributedMetatronTest {

    private static final GraphittyLogger LOG = Graphitty.log(SampleDistributedMachineTest.class);

    @BeforeAll
    public static void setup() {
        // Initialize the base test infrastructure
        AbstractDistributedMetatronTest.begin();

        // Setup a distributed environment with 2 hosts for this example
        setupDistributedEnvironment("sample_distributed_test", 2);

        LOG.info("=== Sample Distributed Machine Test Setup ===");
    }

    @AfterAll
    public static void tearDown() {
        LOG.info("=== Sample Distributed Machine Test Complete ===");
        AbstractDistributedMetatronTest.end();
    }

    @Test
    @DisplayName("Test distributed machine creation")
    public void testMachineCreation() {
        LOG.info("Testing distributed machine creation");

        // Verify basic infrastructure is working
        assertTrue(Router.loaded(), "Router should be loaded");

        // Test that we can create distributed test scenarios
        Map<String, Object> config = new HashMap<>();
        config.put("host_0", "primary_node");
        config.put("host_1", "secondary_node");

        createDistributedScenario(LOG, "simple_two_host", config);

        LOG.debug("Machine creation test passed");
    }

    @Test
    @DisplayName("Test cross-host URI routing")
    public void testCrossHostRouting() {
        LOG.info("Testing cross-host URI routing");

        // Test local URI
        fURI localUri = f("/demo/data/local_object");
        verifyCrossHostRouting(LOG, localUri, false);

        // Test remote URI with authority
        fURI remoteUri = f("http://host1:8080/demo/data/remote_object");
        verifyCrossHostRouting(LOG, remoteUri, true);

        // Test another remote URI
        fURI anotherRemoteUri = f("https://cluster-2.example.com:9000/data/test");
        verifyCrossHostRouting(LOG, anotherRemoteUri, true);

        LOG.debug("Cross-host routing test passed");
    }

    @Test
    @DisplayName("Test distributed operation tracking")
    public void testOperationTracking() {
        LOG.info("Testing distributed operation tracking");

        // Test successful operation
        checkDistributedOperation(LOG, "data_processing", true);

        // Test failed operation (simulated)
        checkDistributedOperation(LOG, "network_timeout", false);

        LOG.debug("Operation tracking test passed");
    }

    @Test
    @DisplayName("Test distributed statistics")
    public void testStatistics() {
        LOG.info("Testing distributed statistics");

        // Get test statistics
        Obj stats = getTestStatistics();
        assertNotNull(stats, "Test statistics should be available");

        // Get host-specific statistics
        Obj host0Stats = getHostStatistics("host_0");
        assertNotNull(host0Stats, "Host 0 statistics should be available");

        LOG.debug("Statistics test passed");
    }

    @Test
    @DisplayName("Test distributed machine evaluation scenario")
    public void testMachineEvaluationScenario() {
        LOG.info("Testing distributed machine evaluation scenario");

        // Create a simple machine code that would work in distributed environment
        String simpleMachineCode = "print?str<=str('distributed test')";

        // Test the machine evaluation
        testDistributedMachineEvaluation(LOG, simpleMachineCode, null, 2);

        LOG.debug("Machine evaluation scenario test passed");
    }

    @Test
    @DisplayName("Test distributed result validation")
    public void testResultValidation() {
        LOG.info("Testing distributed result validation");

        // Create mock results for testing
        Obj localResult = uri("local_computation_result");
        Obj remoteResult = uri("remote_computation_result");

        // Results should differ in this scenario (as they would from different hosts)
        validateDistributedResults(LOG, localResult, remoteResult, false);

        LOG.debug("Result validation test passed");
    }

    @Test
    @DisplayName("Test distributed environment configuration")
    public void testEnvironmentConfiguration() {
        LOG.info("Testing distributed environment configuration");

        // Create a complex scenario configuration
        Map<String, Object> advancedConfig = new HashMap<>();
        advancedConfig.put("host_0", Map.of(
            "role", "primary",
            "capacity", 100,
            "latency", 5
        ));
        advancedConfig.put("host_1", Map.of(
            "role", "replica",
            "capacity", 80,
            "latency", 10
        ));

        createDistributedScenario(LOG, "advanced_two_host_cluster", advancedConfig);

        LOG.debug("Environment configuration test passed");
    }
}