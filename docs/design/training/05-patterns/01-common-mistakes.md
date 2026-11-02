# Common Mistakes and How to Avoid Them

This document captures common mistakes learned from real debugging sessions and development experience with Metatron.

## 1. Using `*` in Java Code

### ❌ Wrong
```java
Obj data = Router.readFromSpace(f("*db:users/1"));
```

### ✅ Correct
```java
Obj data = Router.readFromSpace(f("db:users/1"));
```

### Why?
`*` is **console syntax only** - it's the console's way of saying "from()". In Java, you call `Router.readFromSpace()` directly, which already does the "from" operation.

**Console**: `*db:users/1` means "from(db:users/1)"
**Java**: `Router.readFromSpace(f("db:users/1"))` already means "from"

---

## 2. Calling `directReader()` Directly

### ❌ Wrong
```java
@Test
public void testUserRead() {
    tbleSpace space = createSpace();
    Obj user = space.directReader().apply(f("/users/1")).next().obj();
}
```

### ✅ Correct
```java
@Test
public void testUserRead() {
    Obj user = Router.readFromSpace(f("db:users/1"));
}
```

### Why?
`directReader()` is an **internal API** for Spaces. It expects paths that have already been translated by the Router. When you call it directly:
- Route mapping doesn't happen
- Poly unrolling doesn't work
- Pattern prefix isn't stripped
- You bypass the Router's coordination

**Always use `Router.readFromSpace()`** in application and test code.

---

## 3. Forgetting Pattern Wildcards

### ❌ Wrong
```java
// Pattern without wildcard - matches nothing
tbleSpace space = tbleSpace.of(
    rec(uri(PATTERN), uri("db:")),  // Missing wildcard!
    ...
);
```

### ✅ Correct
```java
// Pattern with wildcard - matches db:anything
tbleSpace space = tbleSpace.of(
    rec(uri(PATTERN), uri("db:#")),  // Has wildcard
    ...
);
```

### Why?
Patterns need wildcards to match URIs:
- `db:` matches only exactly "db:" (nothing useful)
- `db:#` matches "db:" followed by anything
- `db:users/#` matches "db:users/" followed by anything

**Always include `#` or `+` in your patterns.**

---

## 4. Case-Sensitive Table Names

### ❌ Wrong
```java
// Table stored as "users" (lowercase)
// But looking up as "Users" (mixed case)
if (tableSchemas.containsKey(tableName)) {  // Fails!
    ...
}
```

### ✅ Correct
```java
// Always use toLowerCase() for table lookups
if (tableSchemas.containsKey(tableName.toLowerCase())) {
    ...
}
```

### Why?
SQL table names are case-insensitive in most databases, but Java Maps are case-sensitive. Metatron stores tables with lowercase keys, so always use `toLowerCase()` when looking up tables.

**Bug we fixed**: `parseTablePath()` wasn't using `toLowerCase()`, causing table lookups to fail.

---

## 5. Not Cleaning Up Test Data

### ❌ Wrong
```java
@Test
public void testUsers() {
    createTable("users");
    insertData();
    // Test logic...
    // No cleanup!
}
```

### ✅ Correct
```java
@Test
public void testUsers() {
    createTable("users");
    try {
        insertData();
        // Test logic...
    } finally {
        // Cleanup happens even if test fails
        dropTable("users");
    }
}
```

### Why?
If a test fails before cleanup, the next test may encounter "table already exists" errors. Always put cleanup in a `finally` block.

**Bug we fixed**: `testTableMapping` wasn't cleaning up the `users` table when it failed, causing `testPolyUnrollingWithPatternExistingTable` to fail.

---

## 6. Forgetting Primary Keys in SELECT

### ❌ Wrong
```java
// Selecting only the field
SELECT title FROM movie WHERE id = ?
// But then trying to build row ID...
buildRowId(rs);  // Fails! No 'id' column
```

### ✅ Correct
```java
// Always include primary keys
SELECT id, title FROM movie WHERE id = ?
// Now buildRowId() works
buildRowId(rs);  // Success!
```

### Why?
When reading specific fields, `buildRowId()` needs the primary key columns to construct the row's URI. Always include primary keys in your SELECT statements.

**Bug we fixed**: Field-level access was only selecting the requested field, but `buildRowId()` needed the primary key.

---

## 7. Using `"+"` as a String Instead of Placeholder

### ❌ Wrong
```java
// Checking if field is requested
if (tablePath.size() > 2) {
    // Assumes size > 2 means field exists
    // But what if tablePath = ["users", "+", "+"]?
}
```

### ✅ Correct
```java
// Check both size AND placeholder value
if (tablePath.size() > 2 && !tablePath.get(2).equals("+")) {
    // Now we know it's a real field, not a placeholder
}
```

### Why?
We use `"+"` as a placeholder in `parseTablePath()` to indicate "no specific value". Always check both the size and the placeholder value.

**Bug we fixed**: SQL was being generated with trailing commas because we didn't check for the `"+"` placeholder.

---

## 8. Doing Translation Work in Spaces

### ❌ Wrong
```java
@Override
public Function<fURI, Iterator<Pair<fURI, Obj>>> directReader() {
    return pattern -> {
        // Stripping pattern prefix in the Space
        fURI cleaned = stripPatternPrefix(pattern);
        // Rewriting the URI
        fURI rewritten = rewrite(cleaned);
        // This is the Router's job!
        return getData(rewritten);
    };
}
```

### ✅ Correct
```java
@Override
public Function<fURI, Iterator<Pair<fURI, Obj>>> directReader() {
    return pattern -> {
        // Just return raw data
        // Router has already done translation
        return getData(pattern);
    };
}
```

### Why?
**Separation of concerns**:
- **Router**: Handles path translation, route mapping, pattern prefix stripping
- **Space**: Just returns raw data matching the pattern
- **Space.Helper**: Handles poly unrolling

**Spaces shouldn't do translation work** - that's the Router's job.

---

## 9. Incorrect Route Mapping

### ❌ Wrong
```java
// Route that doesn't make sense
uri(ROUTE), rec(uri(""), uri(""))
// Maps from nothing to nothing
```

### ✅ Correct
```java
// Route that strips the pattern prefix
uri(ROUTE), rec(uri("db:"), uri(""))
// Maps from "db:" to "" (strips "db:")
```

### Why?
Route mapping should translate external URIs to internal paths:
- External: `db:users/1` (what users see)
- Internal: `users/1` (what Space sees)
- Route: `db: → ""` (strips the prefix)

**The route should match your pattern prefix.**

---

## 10. Not Using `DROP TABLE IF EXISTS`

### ❌ Wrong
```java
stmt.executeUpdate("DROP TABLE users");
// Fails if table doesn't exist
```

### ✅ Correct
```java
stmt.executeUpdate("DROP TABLE IF EXISTS users");
// Succeeds even if table doesn't exist
```

### Why?
In cleanup code, you want to ensure the table is gone, whether it exists or not. `IF EXISTS` makes cleanup idempotent.

---

## 11. Mixing Up Pattern Syntax

### ❌ Wrong
```java
// Using # in the middle
db:#/users

// Using + without context
db:+

// Forgetting the scheme
users/+
```

### ✅ Correct
```java
// # at the end
db:users/#

// + with full path
db:users/+

// Include the scheme
db:users/+
```

### Why?
Pattern rules:
- `#` must be at the **end** (multi-level wildcard)
- `+` matches **one segment** (need full path)
- Always include the **scheme** (db:, mongo:, file:, etc.)

---

## 12. Assuming Return Types

### ❌ Wrong
```java
// Assuming pattern returns a List
Obj result = Router.readFromSpace(f("db:users/+"));
Lst users = result.asLst();  // Might fail!
```

### ✅ Correct
```java
// Check the type first
Obj result = Router.readFromSpace(f("db:users/+"));
if (result.isLst()) {
    Lst users = result.asLst();
} else if (result.isRec()) {
    Rec users = result.asRec();
}
```

### Why?
Pattern matching can return different types depending on the Space implementation:
- Some return `Lst` (list of objects)
- Some return `Rec` (record with IDs as keys)
- Always check the type before casting

---

## 13. Over-Complicating `directReader()`

### ❌ Wrong
```java
@Override
public Function<fURI, Iterator<Pair<fURI, Obj>>> directReader() {
    return pattern -> {
        // Complex path manipulation
        // Pattern prefix stripping
        // Route rewriting
        // Poly unrolling
        // 100 lines of code...
    };
}
```

### ✅ Correct
```java
@Override
public Function<fURI, Iterator<Pair<fURI, Obj>>> directReader() {
    return pattern -> {
        // Delegate to appropriate schema
        if (existingTableSchema != null && existingTableSchema.isTablePath(pattern)) {
            return existingTableSchema.read(pattern);
        }
        // ... other schemas
        return Collections.emptyIterator();
    };
}
```

### Why?
`directReader()` should be **minimal** - just delegate to schemas and return raw data. All the complex logic (translation, unrolling, etc.) happens elsewhere.

**Keep it simple!**

---

## Key Principles to Remember

1. **`*` is console only** - Don't use in Java
2. **Use Router, not directReader()** - Router handles coordination
3. **Patterns need wildcards** - `db:#` not `db:`
4. **Cleanup in finally blocks** - Ensure cleanup happens
5. **Include primary keys** - Needed for row identification
6. **Check for placeholders** - `"+"` means "no value"
7. **Spaces are minimal** - Just return raw data
8. **Routes match patterns** - `db:` pattern needs `db:` route
9. **Use IF EXISTS** - Make cleanup idempotent
10. **Check return types** - Don't assume Lst or Rec

---

## Next Steps

- See [Best Practices](02-best-practices.md) - recommended patterns
- Read [Debugging Guide](03-debugging-guide.md) - troubleshooting tips
- Review [Separation of Concerns](../04-architecture/01-separation-of-concerns.md) - architecture principles

---

**Remember**: These mistakes were all learned from real debugging sessions. Learn from them and save yourself the debugging time! 🐛
