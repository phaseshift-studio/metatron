# Lazy Foreign Key Resolution - Implementation Summary

## Problem

The initial foreign key traversal implementation eagerly resolved foreign keys when reading rows:

```java
// OLD: Eager resolution
if (fk != null) {
    return traverseForeignKey(fk, fkValue.toString());  // Immediately reads referenced row
}
```

This caused **infinite recursion** in graph-like structures, such as:
- Self-referencing tables (employees → manager → manager's manager → ...)
- Circular references (A → B → C → A)

## Solution: Lazy Resolution with `auto_from`

Changed to return `auto_from` instructions that only resolve when accessed:

```java
// NEW: Lazy resolution
if (fk != null) {
    final fURI referencedPath = f(fk.toTable()).extend(fkValue.toString());
    return auto_from_(referencedPath).tryToInst();  // Returns instruction, not data
}
```

## How It Works

### The Pattern from `Rec` and `Rel`

Metatron's poly types (`Rec`, `Rel`) carefully distinguish between:

1. **Internal access** via `jvm().get()` - Returns raw values without resolution
2. **External access** via `at()` - Calls `autoResolve(this)` on values before returning

Example from `Rec.at()`:
```java
return this.jvm().getOrDefault(key, NoObj.noobj()).autoResolve(this).parent(this);
//     ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ raw access
//                                                ^^^^^^^^^^^^^^^^^ lazy resolution
```

### Auto-Resolution Behavior

From `Obj.autoResolve()`:
```java
default Obj autoResolve(final Obj obj) {
    return this.isInst() && (this.tid().basePath().equals(AUTO_FROM_INST_TID) || ...) ?
            this.apply(obj) :  // Resolve if it's an auto_from instruction
            this;              // Otherwise return as-is
}
```

### Result

When a row is read:
1. FK columns contain `auto_from` instructions (not resolved)
2. The row can be stored in memory without loading the entire graph
3. Only when a FK field is accessed via `at()` does resolution happen
4. Each access resolves one level, preventing infinite recursion

## Example

```java
// Read employee with self-referencing manager FK
final Obj employee = Router.global().read(f("db:employees/4"));

// employee record contains:
// [
//   id => 4,
//   name => 'Employee',
//   manager_id => auto_from(db:employees/3)  // Instruction, not data
// ]

// Accessing manager_id triggers ONE level of resolution
final Obj manager = employee.asRec().at(uri("manager_id"));

// manager record contains:
// [
//   id => 3,
//   name => 'Manager',
//   manager_id => auto_from(db:employees/2)  // Still lazy!
// ]

// Can traverse arbitrarily deep without loading entire hierarchy
final Obj ceo = manager.asRec().at(uri("manager_id"))
                       .asRec().at(uri("manager_id"));
```

## Implementation Changes

### File: `ExistingTableSchema.java`

**Added import:**
```java
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
```

**Modified `readColumnWithMetadata()`:**
```java
// Check if this column is a foreign key
final ForeignKeyMetadata fk = getForeignKeyForColumn(tableName, columnName);
if (fk != null) {
    // This is a foreign key - return an auto_from instruction for lazy resolution
    final Object fkValue = rs.getObject(col.name);
    if (fkValue != null && !rs.wasNull()) {
        // Build the full path to the referenced row including space pattern
        // e.g., "acme:employees/1056" not just "employees/1056"
        // Use retractPattern() to strip the wildcard from the pattern (acme:# -> acme:)
        final fURI referencedPath = this.space.pattern().retractPattern()
                .extend(fk.toTable())
                .extend(fkValue.toString());
        // Return auto_from instruction that will resolve lazily when accessed
        return auto_from_(referencedPath).tryToInst();
    }
    return noobj();
}
```

**Removed method:**
- `traverseForeignKey()` - No longer needed, resolution happens via `auto_from`

### File: `tbleSpaceTest.java`

**Added test:**
```java
@Test
public void testLazyForeignKeyResolution() throws Exception {
    // Creates self-referencing employee hierarchy
    // Verifies no infinite recursion when reading
    // Tests traversal through multiple levels
}
```

## Test Results

All 146 tests pass:
- 141 original tests
- 4 foreign key discovery tests
- 1 new lazy resolution test

```bash
mvn test -Dtest=tbleSpaceTest
# Tests run: 146, Failures: 0, Errors: 0, Skipped: 0
```

## Key Insights

1. **Capital letter fURIs denote types**, not data paths
2. **`jvm().get()` vs `at()`**: Internal vs external access determines when resolution happens
3. **`auto_from` is an instruction**, not data - it only resolves when applied
4. **Lazy resolution prevents infinite recursion** in graph-like structures
5. **Pattern matches graph schemas**: Same behavior as `auto_from()` in `graphSpace`

## Benefits

1. **Memory efficient**: Don't load entire graph when reading one row
2. **No infinite recursion**: Each access resolves one level only
3. **Consistent with metatron patterns**: Matches `auto_from` behavior in graph schemas
4. **Flexible traversal**: Can navigate arbitrarily deep hierarchies on-demand
5. **Transparent to users**: FK traversal "just works" via `>>` operator

## Documentation

Updated `/docs/training/03-implementation/sql-schema-access.md` with:
- Foreign Key Support section
- Lazy Traversal explanation
- Example with self-referencing table
- Implementation details
- Updated test commands

## Related Concepts

- **Poly containers**: `Rec`, `Rel`, `Lst` - all use lazy resolution pattern
- **Instructions**: `auto_from`, `auto`, `type` - resolve when applied
- **Graph schemas**: `graphSpace` uses same pattern for vertex/edge references
- **fURI routing**: Paths resolve through spaces on-demand
