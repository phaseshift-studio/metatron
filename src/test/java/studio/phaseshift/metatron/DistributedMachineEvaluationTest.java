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

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Test class demonstrating distributed machine evaluation functionality.
 * This test shows how the AbstractDistributedMetatronTest harness works
 * for testing cross-host machine execution.
 */
public class DistributedMachineEvaluationTest extends AbstractDistributedMetatronTest {

    private static final GraphittyLogger LOG = Graphitty.log(DistributedMachineEvaluationTest.class);

    @BeforeAll
    public static void setup() {
        // Call the parent setup
        AbstractDistributedMetatronTest.begin();

        // Setup distributed test environment with 3 hosts
        setupDistributedEnvironment("distributed_machine_evaluation", 3);

        LOG.info("=== Distributed Machine Evaluation Test Setup ===");
    }

    @AfterAll
    public static void tearDown() {
        LOG.info("=== Distributed Machine Evaluation Test Complete ===");
        AbstractDistributedMetatronTest.end();
    }

    @Test
    @DisplayName("Test basic distributed machine setup")
    public void testDistributedMachineSetup() {
        LOG.info("Testing basic distributed machine setup");

        // Verify that our test infrastructure is properly initialized
        Obj stats = getTestStatistics();
        assertNotNull(stats, "Test statistics should be available");

        Obj debugUtils = getDebugUtilities();
        assertNotNull(debugUtils, "Debug utilities should be available");

        LOG.debug("Basic setup verification passed");
    }

    @Test
    @DisplayName("Test cross-host URI routing")
    public void testCrossHostRouting() {
        LOG.info("Testing cross-host URI routing");

        // Test local URI
        fURI localUri = f("/demo/data/local_object");
        verifyCrossHostRouting(LOG, localUri, false);

        // Test remote URI
        fURI remoteUri = f("http://host2:8080/demo/data/remote_object");
        verifyCrossHostRouting(LOG, remoteUri, true);

        LOG.debug("Cross-host routing test passed");
    }

    @Test
    @DisplayName("Test distributed machine evaluation")
    public void testDistributedMachineEvaluation() {
        LOG.info("Testing distributed machine evaluation");

        // Test simple machine code that should work across hosts
        String machineCode = "print?str<=str('hello world')";

        testDistributedMachineEvaluation(LOG, machineCode, null, 3);

        LOG.debug("Distributed machine evaluation test passed");
    }

    @Test
    @DisplayName("Test distributed statistics tracking")
    public void testDistributedStatistics() {
        LOG.info("Testing distributed statistics tracking");

        // Verify we can access host-specific statistics
        Obj host0Stats = getHostStatistics("host_0");
        assertNotNull(host0Stats, "Host 0 statistics should be available");

        Obj host1Stats = getHostStatistics("host_1");
        assertNotNull(host1Stats, "Host 1 statistics should be available");

        LOG.debug("Distributed statistics tracking test passed");
    }

    @Test
    @DisplayName("Test distributed operation validation")
    public void testDistributedOperationValidation() {
        LOG.info("Testing distributed operation validation");

        // Test a successful distributed operation
        checkDistributedOperation(LOG, "data_processing", true);

        // Test a failed distributed operation (simulated)
        checkDistributedOperation(LOG, "network_failure_simulation", false);

        LOG.debug("Distributed operation validation test passed");
    }

    @Test
    @DisplayName("Test distributed environment configuration")
    public void testEnvironmentConfiguration() {
        LOG.info("Testing distributed environment configuration");

        // Create a scenario with specific host configurations
        var hostConfig = new java.util.HashMap<String, Object>();
        hostConfig.put("host_0", "primary");
        hostConfig.put("host_1", "replica");
        hostConfig.put("host_2", "backup");

        createDistributedScenario(LOG, "three_host_cluster", hostConfig);

        LOG.debug("Environment configuration test passed");
    }

    @Test
    @DisplayName("Test distributed result consistency")
    public void testResultConsistency() {
        LOG.info("Testing distributed result consistency");

        // Create mock results for local and remote execution
        Obj localResult = uri("local_result");
        Obj remoteResult = uri("remote_result");

        // Results should match when computation is identical
        validateDistributedResults(LOG, localResult, remoteResult, false);

        LOG.debug("Result consistency test passed");
    }
}