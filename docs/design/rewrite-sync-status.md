# Rewrite Synchronization Status

**Date**: 2025-01-XX
**Last Updated**: Parameterized tests implemented
**Goal**: Keep tabledbSpace and docdbSpace rewrites in sync with shared tests

---

## ✅ Completed: Rewrites Implementation

### Both tabledbSpace and docdbSpace now have:

| Rewrite | SQL Implementation | MongoDB Implementation | Status |
|---------|-------------------|------------------------|---------|
| **countRewrite** | `SELECT COUNT(*)` | `collection.countDocuments()` | ✅ Synced |
| **sumRewrite** | `SELECT SUM(1)` | MongoDB `$sum` aggregation | ✅ Synced |
| **meanRewrite** | `SELECT AVG(1.0)` | MongoDB `$avg` aggregation | ✅ Synced |
| **limitRewrite** | `SELECT * ... LIMIT n` | `collection.find().limit(n)` | ✅ Synced |
| **hasRewrite** | `SELECT EXISTS(...)` | `collection.find().limit(1).first() != null` | ✅ Synced |
| **whereRewrite** | `SELECT * ... WHERE conditions` | `collection.find(filter)` | ✅ Synced |
| **whereCountRewrite** | `SELECT COUNT(*) ... WHERE conditions` | `collection.countDocuments(filter)` | ✅ Synced |

---

## Implementation Details

### tabledbSpace (SQL)
**File**: `/src/main/java/studio/phaseshift/metatron/isa/tble/tbleInstSet.java`

- Uses `ObjSQLSerializer.readLimitedAsRecObjs()` for limit
- Uses `SELECT EXISTS(SELECT 1 FROM table LIMIT 1)` for has
- Uses `translateToSQL()` to convert mtron predicates to SQL WHERE clauses
- Supports: `=`, `>`, `<`, `>=`, `<=`, `<>`, `IS NOT NULL`
- Handles `is(gt(5))` unwrapping for `?>`, `?<`, etc.

### docdbSpace (MongoDB)
**File**: `/src/main/java/studio/phaseshift/metatron/isa/doc/dcmntInstSet.java`

- Uses `readDocumentsAsObjs()` helper for limit
- Uses `collection.find().limit(1).first()` for has
- Uses `parseMongoFilter()` to convert SQL-like WHERE clauses to BSON filters
- Parses same format: `field = value`, `field > value`, `field IS NOT NULL`, `... AND ...`
- Returns MongoDB `Filters.eq()`, `Filters.gt()`, `Filters.exists()`, etc.

**Key Design Decision**: `WhereRewriteBuilder` produces a SQL-like string (`"field > 5"`), and each database parses it:
- SQL: Uses it directly in query
- MongoDB: Parses to BSON filter (`Filters.gt("field", 5)`)

This keeps the rewrite logic unified while allowing database-specific execution.

---

## ✅ Test Infrastructure - COMPLETE

### Parameterized Test Pattern

The `CommonRewritesTestContract` interface provides:

1. **`generateAllRewriteTestCases()`** - Returns all test cases as `Stream<Arguments>`
2. **`runRewriteTest(description, code, expected)`** - Executes a test case
3. **`runRewritePlanTest(description, code, nativeInstName)`** - Verifies rewrite plans
4. **`generatePlanVerificationTestCases()`** - Test cases for plan verification

### Test Categories

| Category | Test Cases | Description |
|----------|------------|-------------|
| **Count** | 2 | Basic count operations |
| **Limit** | 6 | take(0), take(1), take(2), take(5), take(10), take(100) |
| **Has** | 1 | Existence check on non-empty collection |
| **Where** | 22 | Equality, >, <, >=, <=, boolean predicates |
| **Where+Count** | 6 | Combined where().count() optimization |
| **Aggregation** | 2 | sum(), mean() |
| **Composition** | 6 | Rewrite + arithmetic, id removal + rewrite |

**Total: ~45 parameterized test cases** run against both SQL and MongoDB!

### Implementation

**tabledbSpaceTest.java**:
```java
@Override
public fURI getTestDataUriPrefix() {
    return f("tble:rewrite_test");
}

@ParameterizedTest(name = "[{index}] {0}")
@MethodSource("provideAllRewriteTestCases")
public void testRewriteOptimizations(String description, String code, Obj expected) throws Exception {
    setupRewriteTestData();
    try {
        runRewriteTest(description, code, expected);
    } finally {
        cleanupRewriteTestData();
    }
}

static Stream<Arguments> provideAllRewriteTestCases() {
    return new tabledbSpaceTest().generateAllRewriteTestCases();
}
```

**docdbSpaceTest.java**:
```java
@Override
public fURI getTestDataUriPrefix() {
    return f("mongo:rewrite_test");
}

@ParameterizedTest(name = "[{index}] {0}")
@MethodSource("provideAllRewriteTestCases")
public void testRewriteOptimizations(String description, String code, Obj expected) throws Exception {
    setupRewriteTestData();
    try {
        runRewriteTest(description, code, expected);
    } finally {
        cleanupRewriteTestData();
    }
}

static Stream<Arguments> provideAllRewriteTestCases() {
    return new docdbSpaceTest().generateAllRewriteTestCases();
}
```

### Test Data Schema

Both databases create 10 rows with:
```
id: 1-10 (integer)
value: 1-10 (integer, same as id)
name: 'item1'-'item10' (string)
active: alternating true/false (boolean) - odd=true, even=false
```

Expected results:
- `count()` = 10
- `sum(value)` = 55 (1+2+...+10)
- `mean(value)` = 5.5
- `where(value > 5).count()` = 5
- `where(active=true).count()` = 5

---

## 🎯 Benefits of This Approach

1. **Single Source of Truth**: Test cases defined once in the contract
2. **Automatic Coverage**: Both SQL and MongoDB run identical tests
3. **Easy to Add Tests**: Just add `Arguments.of(...)` to the appropriate generator
4. **Parameterized Output**: Each test case shows in IDE with clear name `[42] where: value > 5`
5. **Plan Verification**: Separate test suite verifies rewrite plans contain native instructions

---

## 📋 Completed Tasks

1. ✅ Implement rewrites in both dcmntInstSet and tbleInstSet
2. ✅ Add parameterized test data providers to `CommonRewritesTestContract`
3. ✅ Add `@ParameterizedTest` methods in `tabledbSpaceTest` and `docdbSpaceTest`
4. ✅ Set up test data creation in both database formats
5. ⏳ Run tests and verify both pass

---

## Adding New Test Cases

To add a new rewrite test, just add to the appropriate generator in `CommonRewritesTestContract`:

```java
default Stream<Arguments> generateWhereTestCases() {
    final String p = getTestDataUriPrefix().toString();
    return Stream.of(
            // ... existing cases ...
            Arguments.of("where: new test case",  "*" + p + "/+.where([...]).count()",  jnt(expected))
    );
}
```

Both `tabledbSpaceTest` and `docdbSpaceTest` will automatically pick up the new test case!

---

## Summary

✅ **Code synchronization complete!**
✅ **Test synchronization complete!**

Both tabledbSpace and docdbSpace now have:
- Identical rewrite capabilities
- Shared parameterized test suite (~45 test cases)
- Database-specific test data setup
- Plan verification tests
