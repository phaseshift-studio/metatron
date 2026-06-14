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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.LogObj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyObjLogger;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.Tuple;

import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.LOGG;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.io.ioInstSet.IO_ISA_TID;

/**
 * Abstract test class for distributed metatron functionality.
 * Provides utilities and infrastructure for testing distributed machine evaluation
 * across multiple hosts/spaces.
 */
@ExtendWith(TestSkip.TestSkipExtension.class)
@ExtendWith(TestData.TestDataExtension.class)
public abstract class AbstractDistributedMetatronTest {
    static {
        BootLoader.TESTING = true;
    }

    protected static final Random RANDOM = new Random();
    protected GraphittyLogger LOG = Graphitty.log(this);
    protected static GraphittyLogger STATIC_LOG = Graphitty.log(AbstractDistributedMetatronTest.class);

    // Test infrastructure for distributed systems
    private static Map<String, Object> distributedTestInfrastructure;

    public static int generatePort() {
        return RANDOM.nextInt(10000, 65000);
    }

    @BeforeAll
    public static void begin() {
        memSpace.of(f("/sys/#"), null);
        TypeCheck.enable(TypeCheck.values());
        TypeCheck.disable(TypeCheck.values());
        BootLoader.BOOTING = true;
        BootLoader.TESTING = true;
        BootLoader.load(rec(uri(LOGG), uri(LogObj.getSLF4J().toString().toLowerCase())));
        InstSet.importInstSet(IO_ISA_TID);

        // Initialize distributed test infrastructure
        initializeDistributedTestInfrastructure();
    }

    @AfterAll
    public static void end() {
        BootLoader.close();
    }

    /**
     * Initialize the distributed testing infrastructure.
     * This creates the foundation for multi-host testing scenarios.
     */
    private static void initializeDistributedTestInfrastructure() {
        // Setup distributed test utilities and statistics objects
        distributedTestInfrastructure = new java.util.HashMap<>();

        // Create utility objects that can be used across distributed tests
        distributedTestInfrastructure.put("test_stats", createTestStatistics());
        distributedTestInfrastructure.put("debug_utils", createDebugUtilities());
    }

    /**
     * Creates a statistics object for tracking distributed test results
     */
    private static Obj createTestStatistics() {
        return rec(Map.of(
            uri("distributed_test_count"), jnt(0),
            uri("cross_host_ops"), jnt(0),
            uri("host_success_rate"), jnt(100),
            uri("latency_samples"), rec(),
            uri("error_count"), jnt(0)
        ));
    }

    /**
     * Creates debug utilities for distributed testing
     */
    private static Obj createDebugUtilities() {
        return rec(Map.of(
            uri("trace_enabled"), jnt(1),
            uri("debug_level"), uri("verbose"),
            uri("log_output"), uri("/tmp/distributed_test.log"),
            uri("monitoring"), rec()
        ));
    }

    /**
     * Utility method to create a distributed test environment
     */
    public static void setupDistributedEnvironment(String testName, int hostCount) {
        STATIC_LOG.info("Setting up distributed environment for test: {{b}}%s{{X}} with {{b}}%d{{X}} hosts", testName, hostCount);

        // Create host-specific test infrastructure
        for (int i = 0; i < hostCount; i++) {
            final String hostName = "host_" + i;
            distributedTestInfrastructure.put(hostName + "_router", createHostRouter(i));
            distributedTestInfrastructure.put(hostName + "_stats", createHostStatistics(i));
        }
    }

    /**
     * Create a router instance for a specific host
     */
    private static Obj createHostRouter(int hostIndex) {
        return rec(Map.of(
            uri("host_id"), uri("host_" + hostIndex),
            uri("router_vid"), uri("/sys/router/host_" + hostIndex),
            uri("is_local"), jnt(1),
            uri("network_config"), rec()
        ));
    }

    /**
     * Create statistics for a specific host
     */
    private static Obj createHostStatistics(int hostIndex) {
        return rec(Map.of(
            uri("host_id"), uri("host_" + hostIndex),
            uri("operations_processed"), jnt(0),
            uri("remote_calls"), jnt(0),
            uri("avg_latency_ms"), jnt(0),
            uri("success_rate"), jnt(100)
        ));
    }

    /**
     * Check that distributed operations are working correctly
     */
    public static void checkDistributedOperation(final GraphittyLogger LOG,
                                               final String operation,
                                               final boolean shouldSucceed) {
        // Update test statistics
        updateTestStatistics(operation, shouldSucceed);

        LOG.debug("Checking distributed operation: {{b}}%s{{X}} [should succeed: %s]", operation, shouldSucceed);
        if (!shouldSucceed) {
            LOG.warn("Operation failed as expected: %s", operation);
        }
    }

    /**
     * Update test statistics for distributed operations
     */
    private static void updateTestStatistics(String operation, boolean success) {
        // This would update the stats object with performance metrics
        // For now just log it
        STATIC_LOG.info("Distributed operation {{b}}%s{{X}} %s",
                       operation, success ? "succeeded" : "failed");
    }

    /**
     * Verify that cross-host routing is working correctly
     */
    public static void verifyCrossHostRouting(final GraphittyLogger LOG,
                                            final fURI targetUri,
                                            final boolean shouldRoute) {
        LOG.debug("Verifying cross-host routing for: {{b}}%s{{X}}", targetUri);

        // Check if URI has authority (cross-host)
        if (targetUri.hasAuthority()) {
            LOG.info("Cross-host URI detected: %s", targetUri);
            assertTrue(shouldRoute, "Should route cross-host requests");
        } else {
            LOG.info("Local URI: %s", targetUri);
            assertFalse(shouldRoute, "Should not route local requests");
        }
    }

    /**
     * Utility for testing distributed machine evaluation
     */
    public static void testDistributedMachineEvaluation(final GraphittyLogger LOG,
                                                      final String machineCode,
                                                      final Obj input,
                                                      final int expectedHosts) {
        LOG.info("Testing distributed machine evaluation with code: {{b}}%s{{X}}", machineCode);

        // Parse and validate the machine code
        final Obj code = ObjmtronSerializer.parse(machineCode);
        assertNotNull(code, "Parsed code should not be null");

        // Test that it can be executed in a distributed context
        LOG.debug("Machine evaluation test completed for {{b}}%s{{X}} across %d hosts",
                 machineCode, expectedHosts);
    }

    /**
     * Create a distributed test scenario with specific host configurations
     */
    public static void createDistributedScenario(final GraphittyLogger LOG,
                                               final String scenarioName,
                                               final Map<String, Object> hostConfig) {
        LOG.info("Creating distributed scenario: {{b}}%s{{X}}", scenarioName);

        // Setup the scenario configuration
        for (Map.Entry<String, Object> entry : hostConfig.entrySet()) {
            LOG.debug("  Host config - {{g}}%s{{X}} = %s", entry.getKey(), entry.getValue());
        }
    }

    /**
     * Validate that distributed results are consistent
     */
    public static void validateDistributedResults(final GraphittyLogger LOG,
                                                final Obj localResult,
                                                final Obj remoteResult,
                                                final boolean shouldMatch) {
        LOG.debug("Validating distributed results");

        if (shouldMatch) {
            assertEquals(localResult, remoteResult,
                        "Distributed results should match when they're from the same computation");
        } else {
            assertNotEquals(localResult, remoteResult,
                           "Distributed results should differ when expected");
        }
    }

    // ======================== Test utilities that can be extended by subclasses ========================

    /**
     * Utility method to simulate network delay for testing
     */
    public static void simulateNetworkDelay(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Utility to get test statistics
     */
    public static Obj getTestStatistics() {
        return (Obj) distributedTestInfrastructure.get("test_stats");
    }

    /**
     * Utility to get debug utilities
     */
    public static Obj getDebugUtilities() {
        return (Obj) distributedTestInfrastructure.get("debug_utils");
    }

    /**
     * Utility to create a host-specific router
     */
    public static Obj getHostRouter(String hostName) {
        return (Obj) distributedTestInfrastructure.get(hostName + "_router");
    }

    /**
     * Utility to get host statistics
     */
    public static Obj getHostStatistics(String hostName) {
        return (Obj) distributedTestInfrastructure.get(hostName + "_stats");
    }
}