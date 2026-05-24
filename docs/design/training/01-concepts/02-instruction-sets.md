# Instruction Sets

## What is an Instruction Set?

An **Instruction Set** is the fundamental organizational unit in Metatron. It's like a library or package, but more structured. Each instruction set is a complete, self-contained module that defines everything needed to work with a particular domain.

Think of instruction sets as **specialized toolkits** - each one provides the types, operations, and optimizations for a specific area of computing.

## The Five Components

Every instruction set has five key components:

### 1. Types (`type`)
The data types this instruction set works with.

**Example** (`mInstSet` - the core instruction set):
- `int` - Integer numbers
- `real` - Floating-point numbers
- `str` - Strings
- `lst` - Lists
- `rec` - Records (key-value maps)
- `uri` - URIs

**Example** (`tbleInstSet` - SQL instruction set):
- SQL-specific types
- Table schemas
- Query result types

### 2. Constants (`obj`)
Constant values and objects used by the instruction set.

**Example**:
- Default connection strings
- Standard SQL keywords
- Common configuration values

### 3. Instructions (`insts`)
The operations and functions that work on the types.

**Example** (`mInstSet`):
- `+` - Addition
- `map` - Transform each element
- `filter` - Select matching elements
- `count` - Count elements
- `from` - Fetch from URI

**Example** (`tbleInstSet`):
- `query` - Execute SQL query
- `join` - Join tables
- `select` - Project columns

### 4. Rewrites (`rewrites`)
Code transformations that optimize instruction sequences.

**Key Insight**: Rewrites are **endofunctors in the category of code**:
```
f: code → code
```

They take code (a list of instructions) and return optimized code that produces the same result.

**Example** (`tbleInstSet`):
```java
// Pattern: [from, count]
// Rewrite: SELECT COUNT(*) FROM table
// Instead of fetching all rows and counting in memory

*netflix:movie.count()
// Becomes: SELECT COUNT(*) FROM movie
// Returns: 11983
```

**Example** (`mInstSet`):
```java
// Pattern: [id, id, id, ...]
// Rewrite: Remove all identity operations (they do nothing)

// Pattern: [map, map]
// Rewrite: Flatten nested maps into single map
```

### 5. Sugars (`sugars`)
Syntactic conveniences for the console that make the user experience more enjoyable.

**Example**:
- `.count()` instead of verbose syntax
- `[?predicate]` for filtering
- `>>` for dereferencing

## Example: tbleInstSet

The SQL instruction set encapsulates everything about working with SQL databases:

```java
public class tbleInstSet extends AbstractInstSet {

    @Override
    public Set<Type> types() {
        // SQL-specific types
        return Set.of(TABLE_TYPE, SCHEMA_TYPE, ...);
    }

    @Override
    public Set<Obj> constants() {
        // Default values, keywords
        return Set.of(DEFAULT_DRIVER, DEFAULT_HOST, ...);
    }

    @Override
    public Set<Inst> insts() {
        // SQL operations
        return Set.of(QUERY_INST, JOIN_INST, ...);
    }

    @Override
    public Set<Inst> rewrites() {
        // Query optimizations
        return Set.of(
            // [from, count] → SELECT COUNT(*)
            countRewrite,
            // [from, filter, count] → SELECT COUNT(*) WHERE ...
            filterCountRewrite,
            // More rewrites...
        );
    }

    @Override
    public Set<Sugar> sugars() {
        // Console conveniences
        return Set.of(...);
    }
}
```

## Why Instruction Sets?

### Modularity
Each instruction set is self-contained:
- SQL logic lives in `tbleInstSet`
- Graph logic lives in `tp3InstSet`
- Core logic lives in `mInstSet`

No cross-contamination!

### Composability
Instruction sets can build on each other:
- `tbleInstSet` uses types from `mInstSet`
- Custom instruction sets can extend existing ones
- Mix and match as needed

### Optimization
Rewrites are localized to the instruction set that understands the domain:
- SQL optimizations in `tbleInstSet`
- Graph optimizations in `tp3InstSet`
- Core optimizations in `mInstSet`

### Extensibility
Adding new capabilities is clean:
1. Create a new instruction set
2. Define types, constants, instructions
3. Add domain-specific rewrites
4. Register with Metatron

## The Core Instruction Set: mInstSet

`mInstSet` is the foundation - it provides the basic types and operations that all other instruction sets build on:

**Types**:
- `int`, `real`, `bool`, `str` - Primitives
- `lst`, `rec` - Collections
- `uri` - Addresses
- `inst`, `type` - Meta-types

**Key Instructions**:
- `from` (`*`) - Fetch from URI
- `to` (`!`) - Execute instruction
- `map`, `filter`, `reduce` - Collection operations
- `+`, `-`, `*`, `/` - Arithmetic
- `count`, `sum`, `avg` - Aggregations

**Rewrites**:
- Remove identity operations
- Flatten nested maps
- (More to come!)

## Rewrites: The Optimization Engine

Rewrites are where Metatron gets smart. They're **pattern-based transformations** that recognize instruction sequences and optimize them.

### Current Rewrite Pattern

```java
InstSet.Helper.rewriter(f("rewrite_name"), code -> {
    // 1. Pattern match on instruction sequence
    final List<fURI> instTIDs = code.insts().stream()
        .map(Obj::tid)
        .toList();

    // 2. Check if pattern matches
    if (manyMatches(instTIDs, List.of(FROM_INST_TID, COUNT_INST_TID))) {
        // 3. Check preconditions (e.g., is it a SQL space?)
        final Space space = Router.global().getSpace(uri);
        if (space instanceof tbleSpace) {
            // 4. Return optimized code
            return MCode.of(List.of(nativeSqlCountInst));
        }
    }

    // 5. No match - return original code
    return code;
})
```

### Future: Better Rewrite API

The current API is verbose. Future improvements might look like:

**Regex/FSM Style** (transducer):
```java
rewrite()
    .pattern("[from, count]")
    .when(space -> space instanceof tbleSpace)
    .to(code -> nativeSqlCount(code))
```

**Fluent Style**:
```java
rewrite(pattern("[from, count]"))
    .when(space -> space instanceof tbleSpace)
    .to(code -> nativeSqlCount(code))
```

## Creating Your Own Instruction Set

To create a custom instruction set:

1. **Extend AbstractInstSet**:
```java
public class myInstSet extends AbstractInstSet {
    // Implement the five methods
}
```

2. **Define Your Domain**:
   - What types do you need?
   - What operations make sense?
   - What optimizations are possible?

3. **Implement Rewrites**:
   - Identify common patterns
   - Create optimizations
   - Test for semantic equivalence

4. **Add Sugars**:
   - Make it pleasant to use
   - Console shortcuts
   - Readable syntax

## Key Takeaways

1. **Instruction Sets are modules** - Complete, self-contained toolkits
2. **Five components** - Types, constants, instructions, rewrites, sugars
3. **Rewrites are endofunctors** - `code → code` transformations
4. **Domain-specific optimization** - Each instruction set optimizes its own domain
5. **Composable and extensible** - Build on existing instruction sets

## Next Steps

- Learn about [Spaces and Routing](03-spaces-and-routing.md) - how instruction sets connect to data
- Explore [Pattern Matching](04-pattern-matching.md) - powerful query capabilities
- See [Query Optimization with Rewrites](../03-advanced/01-rewrites.md) - deep dive into rewrites

---

**Remember**: Instruction sets are the **building blocks** of Metatron. They encapsulate domain knowledge and make the system extensible and optimizable.
