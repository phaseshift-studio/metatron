# Distributed Testing Framework

This document describes the distributed testing framework for Metatron, designed to test distributed machine evaluation across multiple hosts/spaces.

## Overview

The distributed testing framework provides infrastructure for:
- Testing cross-host machine execution
- Monitoring distributed system performance
- Validating distributed operation consistency
- Debugging distributed systems with detailed statistics

## Key Components

### 1. AbstractDistributedMetatronTest

The base test class that provides common functionality for distributed tests:

```java
public abstract class AbstractDistributedMetatronTest {
    // Test infrastructure initialization
    public static void begin();
    public static void end();
    
    // Distributed environment setup
    public static void setupDistributedEnvironment(String testName, int hostCount);
    
    // Testing utilities
    public static void checkDistributedOperation(GraphittyLogger LOG, String operation, boolean shouldSucceed);
    public static void verifyCrossHostRouting(GraphittyLogger LOG, fURI targetUri, boolean shouldRoute);
    public static void testDistributedMachineEvaluation(GraphittyLogger LOG, String machineCode, Obj input, int expectedHosts);
}
```

### 2. DistributedTestUtils

Utility class with helper methods for distributed testing:

```java
public class DistributedTestUtils {
    // Test state management
    public static void initializeTestEnvironment(String testName);
    public static void recordOperation(String operationName, boolean isCrossHost, long executionTimeMs);
    
    // URI routing helpers
    public static boolean requiresCrossHostRouting(fURI uri);
    public static String getHostFromUri(fURI uri);
    
    // System validation
    public static boolean validateSystemState();
    public static Map<String, Object> getTestStatistics();
}
```

### 3. Test Usage Example

```java
public class DistributedMachineEvaluationTest extends AbstractDistributedMetatronTest {
    
    @BeforeAll
    public static void setup() {
        // Initialize test environment with 3 hosts
        setupDistributedEnvironment("distributed_machine_evaluation", 3);
    }
    
    @Test
    public void testCrossHostRouting() {
        fURI remoteUri = f("http://host2:8080/demo/data/remote_object");
        verifyCrossHostRouting(LOG, remoteUri, true);
    }
    
    @Test
    public void testDistributedMachineEvaluation() {
        String machineCode = "print?str<=str('hello world')";
        testDistributedMachineEvaluation(LOG, machineCode, null, 3);
    }
}
```

## Test Statistics and Monitoring

The framework automatically tracks:
- Total operations performed
- Cross-host operations
- Latency measurements
- Error counts
- Host-specific statistics

These statistics are available through the `getTestStatistics()` method and can be used for performance analysis and debugging.

## Debugging Support

The distributed testing framework integrates with Metatron's logging system to provide:
- Detailed operation traces
- Cross-host routing information
- Performance metrics
- Error reporting and stack traces

This infrastructure will enable robust testing of the distributed machine evaluation capabilities that you're planning to implement.