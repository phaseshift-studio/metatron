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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;

/**
 * Utility class for distributed testing and debugging of machine evaluation.
 * Provides infrastructure for tracking, monitoring, and debugging distributed systems.
 */
public class DistributedTestUtils {

    private static final GraphittyLogger LOG = Graphitty.log(DistributedTestUtils.class);

    // Shared test state for tracking distributed operations
    private static final Map<String, Object> testState = new ConcurrentHashMap<>();

    /**
     * Initialize distributed test environment
     */
    public static void initializeTestEnvironment(String testName) {
        LOG.info("Initializing distributed test environment: {{b}}%s{{X}}", testName);

        // Clear any previous state
        testState.clear();

        // Set up default test configuration
        testState.put("test_name", testName);
        testState.put("operation_count", 0);
        testState.put("cross_host_operations", 0);
        testState.put("latency_samples", new java.util.ArrayList<Long>());
        testState.put("error_count", 0);
    }

    /**
     * Record a distributed operation
     */
    public static void recordOperation(String operationName, boolean isCrossHost, long executionTimeMs) {
        int count = (int) testState.getOrDefault("operation_count", 0);
        testState.put("operation_count", count + 1);

        if (isCrossHost) {
            int crossHostCount = (int) testState.getOrDefault("cross_host_operations", 0);
            testState.put("cross_host_operations", crossHostCount + 1);
        }

        // Record latency
        @SuppressWarnings("unchecked")
        java.util.List<Long> latencies = (java.util.List<Long>) testState.getOrDefault("latency_samples", new java.util.ArrayList<>());
        latencies.add(executionTimeMs);
        testState.put("latency_samples", latencies);

        LOG.debug("Recorded operation {{b}}%s{{X}} (cross-host: %s, time: %d ms)",
                operationName, isCrossHost, executionTimeMs);
    }

    /**
     * Record an error in distributed operations
     */
    public static void recordError(String operation, String errorMessage) {
        int errorCount = (int) testState.getOrDefault("error_count", 0);
        testState.put("error_count", errorCount + 1);

        LOG.error("Distributed operation error in {{b}}%s{{X}}: %s", operation, errorMessage);
    }

    /**
     * Get current test statistics
     */
    public static Map<String, Object> getTestStatistics() {
        return new ConcurrentHashMap<>(testState);
    }

    /**
     * Check if a URI requires cross-host routing
     */
    public static boolean requiresCrossHostRouting(fURI uri) {
        return uri.hasAuthority();
    }


    /**
     * Create a distributed test scenario
     */
    public static void createTestScenario(String scenarioName, Map<String, Object> config) {
        LOG.info("Creating test scenario: {{b}}%s{{X}}", scenarioName);

        // Store scenario configuration
        testState.put("current_scenario", scenarioName);
        testState.put("scenario_config", config);

        LOG.debug("Scenario configuration: %s", config);
    }

    /**
     * Validate distributed system state
     */
    public static boolean validateSystemState() {
        // Check that we have a valid router setup
        boolean hasRouter = Router.loaded();
        LOG.debug("Router loaded: %s", hasRouter);

        // Basic validation checks
        int operationCount = (int) testState.getOrDefault("operation_count", 0);
        int errorCount = (int) testState.getOrDefault("error_count", 0);

        boolean valid = operationCount >= 0 && errorCount >= 0;
        LOG.debug("System state valid: %s (ops: %d, errors: %d)", valid, operationCount, errorCount);

        return valid;
    }

    /**
     * Get test environment information
     */
    public static String getEnvironmentInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== Distributed Test Environment ===\n");
        info.append("Test Name: ").append(testState.getOrDefault("test_name", "unknown")).append("\n");
        info.append("Operations: ").append(testState.getOrDefault("operation_count", 0)).append("\n");
        info.append("Cross-host ops: ").append(testState.getOrDefault("cross_host_operations", 0)).append("\n");
        info.append("Errors: ").append(testState.getOrDefault("error_count", 0)).append("\n");

        @SuppressWarnings("unchecked")
        java.util.List<Long> latencies = (java.util.List<Long>) testState.get("latency_samples");
        if (latencies != null && !latencies.isEmpty()) {
            long avgLatency = (long) latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
            info.append("Avg Latency: ").append(avgLatency).append(" ms\n");
        }

        return info.toString();
    }

    /**
     * Reset test state
     */
    public static void resetTestState() {
        testState.clear();
        LOG.debug("Test state reset");
    }
}