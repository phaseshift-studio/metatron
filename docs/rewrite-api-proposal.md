# Rewrite API Proposal: Shared Patterns Across Database Types

## Current State

Both `tbleInstSet` and `docInstSet` have similar rewrite patterns but with code duplication:

### Pattern Analysis

**Common Pattern:**
1. Match instruction sequence (e.g., `from().count()`)
2. Extract fURI from first instruction
3. Check if space is correct type (`tbleSpace` or `docSpace`)
4. Execute native database operation
5. Apply coefficient multiplication from last instruction
6. Return optimized result

### Current Implementation Issues

1. **Boilerplate repetition** - Each rewrite has ~40 lines of similar code
2. **Type-specific logic mixed with generic logic** - Hard to see what's database-specific
3. **No abstraction for common operations** - Each database reimplements the same pattern
4. **Error handling duplication** - Same try-catch pattern everywhere

## Proposed Solution: Fluent Rewrite Builder API

### Vision

```java
// Define rewrite with minimal boilerplate
return RewriteBuilder.forDatabase(docSpace.class)
    .match(FROM_INST_TID, COUNT_INST_TID)
    .optimize("mongo_native_count", (space, furi, coeff) -> {
        String collection = furi.segments().getFirst();
        long count = space.database.getCollection(collection).countDocuments();
        return jnt(count).c(c -> c.mult(coeff));
    })
    .build();
```

### Proposed API Design

```java
/**
 * Builder API for creating database optimization rewrites
 */
public class RewriteBuilder<S extends Space> {

    private final Class<S> spaceType;
    private final List<fURI> matchPattern = new ArrayList<>();
    private String rewriteName;
    private fURI rewriteTid;

    public static <S extends Space> RewriteBuilder<S> forDatabase(Class<S> spaceType) {
        return new RewriteBuilder<>(spaceType);
    }

    /**
     * Match a sequence of instructions to optimize
     */
    public RewriteBuilder<S> match(fURI... instTids) {
        matchPattern.addAll(Arrays.asList(instTids));
        return this;
    }

    /**
     * Define the native optimization with type-safe lambda
     */
    public RewriteBuilder<S> optimize(String name,
                                       NativeOptimization<S> optimization) {
        this.rewriteName = name;
        this.optimization = optimization;
        return this;
    }

    /**
     * Set the rewrite TID
     */
    public RewriteBuilder<S> tid(fURI tid) {
        this.rewriteTid = tid;
        return this;
    }

    /**
     * Build the final Inst rewrite
     */
    public Inst build() {
        return InstSet.Helper.rewriter(rewriteTid, code ->
            code.selfJVM(Rewriter.search(code.insts())
                .match(matchPattern.stream()
                    .map(tid -> instB(tid, lst()))
                    .toList())
                .rewrite(map -> {
                    fURI oldfURI = code.codeValue().getFirst().arg(0).asUri().uriValue();
                    Space space = Router.global().getSpace(oldfURI);

                    if (spaceType.isInstance(space)) {
                        S typedSpace = spaceType.cast(space);
                        fURI expandedfURI = space.rewrite(oldfURI, true);
                        int coeff = code.codeValue().getLast().c();

                        return List.of(
                            createOptimizedInst(typedSpace, expandedfURI, coeff)
                        );
                    }
                    return map.values().stream().map(Obj::asInst).toList();
                })).asCode());
    }

    @FunctionalInterface
    public interface NativeOptimization<S extends Space> {
        Obj execute(S space, fURI furi, int coefficient) throws Exception;
    }
}
```

### Example Usage - MongoDB Rewrites

```java
@Override
public Set<Inst> rewrites() {
    return new LinkedHashSet<>(List.of(
        // Count optimization
        RewriteBuilder.forDatabase(docSpace.class)
            .tid(DOC_ISA_REWRITE_TID.extend("native_count"))
            .match(FROM_INST_TID, COUNT_INST_TID)
            .optimize("mongo_native_count", (space, furi, coeff) -> {
                String collection = furi.segments().getFirst();
                long count = space.database.getCollection(collection).countDocuments();
                return jnt(count).c(c -> c.mult(coeff));
            })
            .build(),

        // Sum optimization
        RewriteBuilder.forDatabase(docSpace.class)
            .tid(DOC_ISA_REWRITE_TID.extend("native_sum"))
            .match(FROM_INST_TID, SUM_INST_TID)
            .optimize("mongo_native_sum", (space, furi, coeff) -> {
                String collection = furi.segments().getFirst();
                Document result = space.database.getCollection(collection)
                    .aggregate(Arrays.asList(
                        new Document("$group", new Document("_id", null)
                            .append("total", new Document("$sum", 1)))
                    )).first();
                Number total = result != null ? result.get("total", Number.class) : 0;
                return (total instanceof Double)
                    ? real(total.doubleValue())
                    : jnt(total.longValue());
            })
            .build()
    ));
}
```

### Example Usage - SQL Rewrites

```java
@Override
public Set<Inst> rewrites() {
    return new LinkedHashSet<>(List.of(
        // Count optimization
        RewriteBuilder.forDatabase(tbleSpace.class)
            .tid(TBLE_ISA_REWRITE_TID.extend("sql_native_count"))
            .match(FROM_INST_TID, COUNT_INST_TID)
            .optimize("sql_native_count", (space, furi, coeff) -> {
                String table = furi.segments().getFirst();
                try (Statement stmt = space.sjvm().createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    return rs.next()
                        ? jnt(rs.getInt(1)).c(c -> c.mult(coeff))
                        : jnt(0);
                }
            })
            .build()
    ));
}
```

## Benefits

### 1. **Reduced Boilerplate**
- From ~40 lines to ~10 lines per rewrite
- 75% reduction in code

### 2. **Type Safety**
- Generic `<S extends Space>` ensures type-safe access to space-specific methods
- Compile-time checking instead of runtime casting

### 3. **Clearer Intent**
- Separates "what to match" from "how to optimize"
- Database-specific logic is isolated in lambda

### 4. **Reusability**
- Common patterns (from().count(), from().sum()) become one-liners
- Easy to create composite rewrites

### 5. **Better Error Handling**
- Centralized try-catch in builder
- Consistent error messages
- Optional error recovery strategies

### 6. **Testability**
- Easy to test individual optimizations
- Mock `Space` implementations for unit tests

## Cross-Database Rewrite Sharing

### Abstract Rewrite Definitions

```java
/**
 * Shared rewrite patterns that work across multiple database types
 */
public class CommonRewrites {

    /**
     * Generic count optimization - subclasses provide database-specific implementation
     */
    public static <S extends Space> Inst countRewrite(
            Class<S> spaceType,
            fURI rewriteTid,
            BiFunction<S, fURI, Long> countFunction) {

        return RewriteBuilder.forDatabase(spaceType)
            .tid(rewriteTid)
            .match(FROM_INST_TID, COUNT_INST_TID)
            .optimize("native_count", (space, furi, coeff) -> {
                long count = countFunction.apply(space, furi);
                return jnt(count).c(c -> c.mult(coeff));
            })
            .build();
    }

    /**
     * Generic sum optimization
     */
    public static <S extends Space> Inst sumRewrite(
            Class<S> spaceType,
            fURI rewriteTid,
            BiFunction<S, fURI, Number> sumFunction) {

        return RewriteBuilder.forDatabase(spaceType)
            .tid(rewriteTid)
            .match(FROM_INST_TID, SUM_INST_TID)
            .optimize("native_sum", (space, furi, coeff) -> {
                Number sum = sumFunction.apply(space, furi);
                return (sum instanceof Double)
                    ? real(sum.doubleValue())
                    : jnt(sum.longValue());
            })
            .build();
    }
}
```

### Usage in docInstSet

```java
@Override
public Set<Inst> rewrites() {
    return new LinkedHashSet<>(List.of(
        CommonRewrites.countRewrite(
            docSpace.class,
            DOC_ISA_REWRITE_TID.extend("native_count"),
            (space, furi) -> {
                String collection = furi.segments().getFirst();
                return space.database.getCollection(collection).countDocuments();
            }
        ),

        CommonRewrites.sumRewrite(
            docSpace.class,
            DOC_ISA_REWRITE_TID.extend("native_sum"),
            (space, furi) -> {
                String collection = furi.segments().getFirst();
                Document result = space.database.getCollection(collection)
                    .aggregate(Arrays.asList(
                        new Document("$group", new Document("_id", null)
                            .append("total", new Document("$sum", 1)))
                    )).first();
                return result != null ? result.get("total", Number.class) : 0;
            }
        )
    ));
}
```

### Usage in tbleInstSet

```java
@Override
public Set<Inst> rewrites() {
    return new LinkedHashSet<>(List.of(
        CommonRewrites.countRewrite(
            tbleSpace.class,
            TBLE_ISA_REWRITE_TID.extend("sql_native_count"),
            (space, furi) -> {
                String table = furi.segments().getFirst();
                try (Statement stmt = space.sjvm().createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    return rs.next() ? (long) rs.getInt(1) : 0L;
                }
            }
        )
    ));
}
```

## Advanced Features

### Conditional Rewrites

```java
RewriteBuilder.forDatabase(docSpace.class)
    .match(FROM_INST_TID, COUNT_INST_TID)
    .when(space -> space.supportsAggregation())  // Only if feature supported
    .optimize(...)
    .build()
```

### Composite Rewrites

```java
RewriteBuilder.forDatabase(docSpace.class)
    .match(FROM_INST_TID, WHERE_INST_TID, COUNT_INST_TID)
    .optimize("mongo_filtered_count", (space, furi, coeff) -> {
        // Extract filter from WHERE instruction
        Bson filter = buildMongoFilter(code.insts().get(1));
        return space.database.getCollection(collection).countDocuments(filter);
    })
    .build()
```

### Fallback Strategies

```java
RewriteBuilder.forDatabase(docSpace.class)
    .match(FROM_INST_TID, SUM_INST_TID)
    .optimize(...)
    .onError(ErrorStrategy.FALLBACK_TO_ORIGINAL)  // If optimization fails, use original code
    .build()
```

## Implementation Roadmap

### Phase 1: Core Builder
- Implement `RewriteBuilder` base class
- Support basic match/optimize pattern
- Migrate one rewrite from each database type

### Phase 2: Common Rewrites
- Extract `CommonRewrites` patterns
- Refactor existing rewrites to use builder
- Add comprehensive tests

### Phase 3: Advanced Features
- Conditional rewrites
- Composite rewrites
- Error handling strategies

### Phase 4: Additional Database Types
- Extend to other space types (MQTT, Redis, etc.)
- Identify more common patterns
- Build rewrite library

## Questions for Discussion

1. Should the builder API be part of `InstSet.Helper` or a separate class?
2. How should we handle multi-step rewrites (e.g., from().where().count())?
3. Should we support rewrite composition (combining multiple rewrites)?
4. How should we handle database-specific features (e.g., MongoDB aggregation pipelines)?
5. Should rewrites be discoverable/inspectable at runtime?
