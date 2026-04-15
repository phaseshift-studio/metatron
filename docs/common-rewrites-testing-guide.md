# CommonRewrites Testing Guide

This guide explains how to test database optimization rewrites across different database implementations using the `CommonRewritesTestContract`.

## The Problem

We needed to test rewrites (count, sum, mean) across multiple database types (SQL, MongoDB, etc.) but faced a challenge:
- **Different databases require different test data formats** (SQL tables vs MongoDB documents)
- **Each database has its own setup/teardown requirements**
- **Tests should verify the same behavior across all implementations**

## The Solution: Metatron-Native Test Data

Instead of writing database-specific SQL or MongoDB setup code, we use **Metatron syntax** to create the test dataset. This works across ALL database types automatically!

### Key Insight

```java
// This single line of Metatron code works for BOTH SQL and MongoDB:
/tble/rewrite_test/1 -> [id:1, value:1, name:'item1', weight:1.5]

// For SQL: tbleSpace.write() converts it to: INSERT INTO rewrite_test ...
// For MongoDB: dcmntSpace.write() converts it to: db.collection.insertOne(...)
```

## Implementation Steps

### 1. Implement the Contract Interface

Add `implements CommonRewritesTestContract` to your test class:

```java
public class tbleSpaceTest extends AbstractSpaceTest implements CommonRewritesTestContract {
    // ... existing test code ...
}
```

### 2. Specify the Base URI

Implement one method to tell the contract where to create test data:

```java
@Override
public fURI getRewriteTestDatasetBaseUri() {
    return f("/tble/rewrite_test");  // For SQL
    // OR
    return f("mongo:rewrite_test");  // For MongoDB
}
```

### 3. Add Test Method Wrappers

For each rewrite test (count, sum, mean), add a wrapper method:

```java
@ParameterizedTest(name = "[{index}] {0}")
@MethodSource("provideCountRewriteTestCases")
public void testCountRewrite(String description, String expression, Obj expectedValue) throws Exception {
    CommonRewritesTestContract.super.testCountRewrite(description, expression, expectedValue);
}

static Stream<Arguments> provideCountRewriteTestCases() {
    return new tbleSpaceTest().generateCountRewriteTestCases();
}
```

Repeat this pattern for `testSumRewrite` and `testMeanRewrite`.

### 4. (Optional) Override Cleanup

By default, cleanup uses Metatron to delete records. For SQL, you might want to drop the table:

```java
@Override
public void cleanupRewriteTestDataset() throws Exception {
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
         Statement stmt = conn.createStatement()) {
        stmt.executeUpdate("DROP TABLE IF EXISTS rewrite_test");
    }
}
```

For MongoDB, the default cleanup works perfectly - no override needed!

## What Gets Tested

The contract tests three core rewrites:

1. **Count Rewrite**: Verifies `*.count()` uses native COUNT instead of loading all data
2. **Sum Rewrite**: Verifies `*.sum()` uses native SUM instead of in-memory aggregation
3. **Mean Rewrite**: Verifies `*.mean()` uses native AVG instead of computing in code

### Test Dataset

The default setup creates 10 records with predictable values:

```
baseUri/1  -> [id:1,  value:1,  name:'item1',  weight:1.5]
baseUri/2  -> [id:2,  value:2,  name:'item2',  weight:3.0]
baseUri/3  -> [id:3,  value:3,  name:'item3',  weight:4.5]
...
baseUri/10 -> [id:10, value:10, name:'item10', weight:15.0]
```

Expected aggregation results:
- **Count**: 10
- **Sum**: 55 (1+2+3+...+10)
- **Mean**: 5.5 (55/10)

## Benefits

✅ **Database-agnostic test data** - Same Metatron syntax works for all databases
✅ **Minimal boilerplate** - Just implement `getRewriteTestDatasetBaseUri()` and add test wrappers
✅ **Automatic encoding** - Each space's `write()` method handles database-specific conversion
✅ **Consistent verification** - Same test cases validate behavior across all implementations
✅ **Easy to extend** - Add new databases by implementing one method

## Complete Example: SQL (tbleSpace)

```java
public class tbleSpaceTest extends AbstractSpaceTest implements CommonRewritesTestContract {

    @Override
    public fURI getRewriteTestDatasetBaseUri() {
        return f("/tble/rewrite_test");
    }

    // Count tests
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideCountRewriteTestCases")
    public void testCountRewrite(String description, String expression, Obj expectedValue) throws Exception {
        CommonRewritesTestContract.super.testCountRewrite(description, expression, expectedValue);
    }

    static Stream<Arguments> provideCountRewriteTestCases() {
        return new tbleSpaceTest().generateCountRewriteTestCases();
    }

    // Sum tests
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideSumRewriteTestCases")
    public void testSumRewrite(String description, String expression, double expectedValue) throws Exception {
        CommonRewritesTestContract.super.testSumRewrite(description, expression, expectedValue);
    }

    static Stream<Arguments> provideSumRewriteTestCases() {
        return new tbleSpaceTest().generateSumRewriteTestCases();
    }

    // Mean tests
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideMeanRewriteTestCases")
    public void testMeanRewrite(String description, String expression, double expectedValue) throws Exception {
        CommonRewritesTestContract.super.testMeanRewrite(description, expression, expectedValue);
    }

    static Stream<Arguments> provideMeanRewriteTestCases() {
        return new tbleSpaceTest().generateMeanRewriteTestCases();
    }

    // Optional: SQL-specific cleanup
    @Override
    public void cleanupRewriteTestDataset() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP TABLE IF EXISTS rewrite_test");
        }
    }
}
```

## Complete Example: MongoDB (docSpace)

```java
public class docSpaceTest extends AbstractSpaceTest implements CommonRewritesTestContract {

    @Override
    public fURI getRewriteTestDatasetBaseUri() {
        return f("mongo:rewrite_test");
    }

    // Count tests
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideCountRewriteTestCases")
    public void testCountRewrite(String description, String expression, Obj expectedValue) throws Exception {
        CommonRewritesTestContract.super.testCountRewrite(description, expression, expectedValue);
    }

    static Stream<Arguments> provideCountRewriteTestCases() {
        return new docSpaceTest().generateCountRewriteTestCases();
    }

    // Repeat for sum, mean...

    // No cleanup override needed - default implementation works perfectly!
}
```

## Custom Test Data

If you need different test values, override the setup and expected value methods:

```java
@Override
public void setupRewriteTestDataset() throws Exception {
    final fURI baseUri = getRewriteTestDatasetBaseUri();

    // Create custom dataset with values: 10, 20, 30, 40, 50
    for (int i = 1; i <= 5; i++) {
        final String mtronCode = String.format(
            "%s/%d -> [id:%d, value:%d]",
            baseUri, i, i, i * 10
        );
        mParser.eval(mtronCode);
    }
}

@Override
public long getExpectedCount() { return 5L; }

@Override
public double getExpectedSum() { return 150.0; }

@Override
public double getExpectedMean() { return 30.0; }
```

## Architecture

```
CommonRewritesTestContract (interface)
├── setupRewriteTestDataset()      // Creates data via Metatron
├── cleanupRewriteTestDataset()    // Deletes via Metatron (or override)
├── getRewriteTestDatasetBaseUri() // Implemented by test class
├── testCountRewrite()             // Test logic (default implementation)
├── testSumRewrite()               // Test logic (default implementation)
├── testMeanRewrite()              // Test logic (default implementation)
└── generate*TestCases()           // Test case generators

Test Class (tbleSpaceTest, docSpaceTest)
├── Implements getRewriteTestDatasetBaseUri()
├── Adds @ParameterizedTest wrappers
├── Provides static method sources
└── (Optional) Overrides cleanup for DB-specific operations
```

## Files

- **Contract**: `src/test/java/studio/phaseshift/metatron/algebra/rewrite/CommonRewritesTestContract.java`
- **Examples**: `src/test/java/studio/phaseshift/metatron/algebra/rewrite/CommonRewritesTestContractExample.java`

## Performance Impact

These tests verify that rewrites provide significant performance improvements:

- **Count**: O(1) index lookup vs O(n) full scan (~1000x speedup for 1M+ rows)
- **Sum/Mean**: Native database engine vs in-memory computation (~10-100x speedup)

The tests ensure these optimizations are actually being applied, not just falling back to the default implementation.
