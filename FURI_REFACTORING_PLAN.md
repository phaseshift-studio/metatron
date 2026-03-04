# fURI Refactoring Plan

## Objective
Optimize fURI to be the fastest code in Metatron, eliminating the coefficient string parsing bottleneck.

## Current State

**File**: `/home/killswitch/software/metatron/src/main/java/studio/phaseshift/metatron/furi/fURI.java`
- **Size**: 1045 lines
- **Test Coverage**: 855 lines in fURITest.java (excellent!)
- **Role**: Central to all Metatron operations (URIs, types, coefficients, pattern matching)

## The Bottleneck

### Coefficient Operations (String Parsing)
Every `.c()` call currently:
1. Parses the fURI string
2. Extracts the last segment
3. Removes `{ }` braces
4. Calls `Integer.parseInt()`
5. Does math operations
6. Converts back to string
7. Rebuilds the fURI

**Impact**: With tens of thousands of vertices in graph traversals, this happens constantly and is eating ~3 seconds of execution time.

## Refactoring Strategy

### Phase 1: Coefficient Caching (QUICK WIN)
**Goal**: Eliminate repeated parsing of the same coefficient

**Approach**:
```java
public class fURI {
    private final String host;
    private final String scheme;
    // ... existing fields ...

    // NEW: Cache parsed coefficient
    private transient cInt cachedCoefficient = null;

    public cInt c() {
        if (cachedCoefficient == null) {
            cachedCoefficient = parseCoefficient(); // Parse once
        }
        return cachedCoefficient;
    }

    public fURI c(String c) {
        // Invalidate cache when coefficient changes
        fURI result = new fURI(...);
        result.cachedCoefficient = cInt.of(c); // Pre-cache
        return result;
    }
}
```

**Expected impact**: 2-3x speedup on coefficient-heavy operations

**Risk**: Low - just adding a cache field

### Phase 2: Separate Coefficient from fURI (BIGGER WIN)
**Goal**: Don't encode coefficient in the URI string at all

**Approach**:
```java
public class fURI {
    private final String host;
    private final String scheme;
    // ... existing fields ...

    // NEW: Direct coefficient field (not encoded in string)
    private final cInt coefficient;

    public cInt c() {
        return coefficient; // Direct access, no parsing!
    }

    public fURI c(String c) {
        return new fURI(scheme, host, port, sstart, path, send, poly, query, cInt.of(c));
    }

    public fURI c(cInt c) {
        return new fURI(scheme, host, port, sstart, path, send, poly, query, c);
    }
}
```

**Expected impact**: 5-10x speedup on coefficient operations

**Risk**: Medium - changes internal representation, but tests will catch issues

### Phase 3: Optimize String Operations (POLISH)
**Goal**: Reduce allocations and improve parsing performance

**Targets**:
1. **String concatenation** - Use StringBuilder
2. **String splitting** - Cache split results
3. **Substring operations** - Minimize allocations
4. **Pattern matching** - Optimize wildcard matching

**Expected impact**: 1.5-2x additional speedup

**Risk**: Low - internal optimizations

### Phase 4: Immutability and Flyweight Pattern (ADVANCED)
**Goal**: Reduce object creation for common fURIs

**Approach**:
```java
public class fURI {
    // Flyweight cache for common URIs
    private static final Map<String, fURI> CACHE = new ConcurrentHashMap<>();

    public static fURI of(String uri) {
        // Return cached instance for common URIs
        return CACHE.computeIfAbsent(uri, fURI::new);
    }
}
```

**Expected impact**: Reduced GC pressure, faster equality checks

**Risk**: Medium - need to ensure thread safety

## Implementation Plan

### Step 1: Baseline Profiling
```bash
# Run tests with profiler
mvn test -Dtest=fURITest

# Profile coefficient operations specifically
# Identify hot methods in fURI
```

### Step 2: Implement Phase 1 (Coefficient Caching)
1. Add `cachedCoefficient` field
2. Modify `c()` to use cache
3. Invalidate cache on coefficient changes
4. Run tests: `mvn test -Dtest=fURITest`
5. Profile and measure improvement

### Step 3: Implement Phase 2 (Separate Coefficient)
1. Add `coefficient` field to constructor
2. Remove coefficient from string encoding
3. Update all coefficient operations
4. Run tests: `mvn test -Dtest=fURITest`
5. Fix any failures
6. Profile and measure improvement

### Step 4: Implement Phase 3 (String Optimizations)
1. Profile to find hot string operations
2. Optimize one at a time
3. Run tests after each change
4. Measure cumulative improvement

### Step 5: Consider Phase 4 (Flyweight)
1. Analyze object creation patterns
2. Implement caching for common URIs
3. Ensure thread safety
4. Run tests
5. Measure improvement

## Testing Strategy

### Existing Tests
- **fURITest.java**: 855 lines of comprehensive tests
- Run after EVERY change: `mvn test -Dtest=fURITest`
- All tests must pass before proceeding

### Performance Tests
```java
@Test
public void testCoefficientPerformance() {
    fURI uri = fURI.of("/test").c("5");
    long start = System.nanoTime();
    for (int i = 0; i < 100000; i++) {
        cInt c = uri.c();
        c.max();
    }
    long elapsed = System.nanoTime() - start;
    LOG.info("Coefficient access: {} ns per operation", elapsed / 100000);
}
```

### Integration Tests
- Run tp3SpaceTest#testProfiling after each phase
- Measure end-to-end improvement
- Target: Get from 3153ms down to 300-600ms

## Risk Mitigation

### Safety Net
1. **Comprehensive tests** - 855 lines of fURITest.java
2. **Incremental changes** - One phase at a time
3. **Continuous testing** - Run tests after every change
4. **Git commits** - Commit after each successful phase
5. **Rollback plan** - Can revert any phase if needed

### Potential Issues
1. **Serialization** - If coefficient is cached, mark as `transient`
2. **Equality** - Ensure cached fields don't affect equals/hashCode
3. **Thread safety** - If using flyweight, ensure concurrent access is safe
4. **Backward compatibility** - Ensure string representation stays the same for external APIs

## Success Metrics

### Performance Targets
- **Phase 1**: 2-3x speedup on coefficient operations
- **Phase 2**: 5-10x speedup on coefficient operations
- **Phase 3**: 1.5-2x additional speedup
- **Overall**: Get tp3SpaceTest from 3153ms to 300-600ms (5-10x total)

### Quality Metrics
- ✅ All 855 fURITest tests pass
- ✅ No regressions in other tests
- ✅ Code remains readable and maintainable
- ✅ Performance improvement verified by profiling

## Timeline Estimate

- **Phase 1 (Caching)**: 2-4 hours
- **Phase 2 (Separate Coefficient)**: 4-8 hours
- **Phase 3 (String Optimizations)**: 4-8 hours
- **Phase 4 (Flyweight)**: 4-8 hours (optional)

**Total**: 1-2 days of focused work

## Next Steps

1. **Review this plan** - Get user approval
2. **Set up profiling** - Baseline measurements
3. **Start Phase 1** - Coefficient caching (quick win)
4. **Iterate** - Test, measure, optimize
5. **Celebrate** - When we hit 10x total speedup! 🎉

---

**Status**: 📋 Plan Ready
**Confidence**: High (excellent test coverage)
**Expected Impact**: 5-10x speedup
**Risk**: Low-Medium (incremental approach with safety net)
