# Pattern Matching

## What is Pattern Matching?

Pattern matching in Metatron allows you to query multiple objects at once using **wildcards** in URIs. Instead of fetching one object at a time, you can fetch all matching objects with a single query.

Think of it like **glob patterns** in filesystems or **topic patterns** in MQTT - but for data.

## The Two Wildcards

Metatron uses MQTT-style topic matching with two wildcards:

### `+` - Single-Level Wildcard
Matches **exactly one segment** in the URI path.

**Examples**:
```java
// Match all users (any ID)
db:users/+
// Matches: db:users/1, db:users/2, db:users/999
// Doesn't match: db:users/1/name (too many segments)

// Match all user names
db:users/+/name
// Matches: db:users/1/name, db:users/2/name
// Doesn't match: db:users/1 (missing segment)
```

### `#` - Multi-Level Wildcard
Matches **zero or more segments** at the end of the URI.

**Important**: `#` must be at the **end** of the pattern!

**Examples**:
```java
// Match everything under db:
db:#
// Matches: db:users/1, db:users/1/name, db:products/+, db:anything/at/all

// Match all user data
db:users/#
// Matches: db:users/1, db:users/1/name, db:users/+/orders
// Doesn't match: db:products/1 (different table)
```

## Pattern Syntax Rules

### ✅ Valid Patterns
```java
db:users/+           // Single-level wildcard
db:users/+/name      // Wildcard in middle
db:users/#           // Multi-level at end
db:#                 // Multi-level for everything
```

### ❌ Invalid Patterns
```java
db:#/users           // # must be at end
db:+                 // Need more context (use db:+/+ or db:#)
users/+              // Missing scheme (should be db:users/+)
```

## Console vs Java Syntax

### Console Syntax
In the Metatron console, you use `*` for dereferencing:
```
mtron> *netflix:movie/+
```

### Java Syntax
In Java code, **don't use `*`** - it's console-only:
```java
// ✅ Correct
Obj movies = Router.readFromSpace(f("db:movie/+"));

// ❌ Wrong - * is console syntax
Obj movies = Router.readFromSpace(f("*db:movie/+"));
```

**Remember**: `*` is the console's way of saying "from()" - in Java, you just call `Router.readFromSpace()` directly.

## Pattern Matching Examples

### Fetch All Rows
```java
// Get all users
Obj allUsers = Router.readFromSpace(f("db:users/+"));

// Result: Record or List containing all user objects
// Exact structure depends on Space implementation
```

### Fetch Specific Field from All Rows
```java
// Get all user names
Obj allNames = Router.readFromSpace(f("db:users/+/name"));

// Result: Collection of name values
```

### Fetch All Data from Table
```java
// Get everything from users table
Obj everything = Router.readFromSpace(f("db:users/#"));

// Matches: users/1, users/1/name, users/+, etc.
```

### Nested Patterns
```java
// Get all order items for all orders
Obj allItems = Router.readFromSpace(f("db:orders/+/items/+"));

// Matches: orders/1/items/1, orders/1/items/2, orders/2/items/1, etc.
```

## How Spaces Handle Patterns

When a Space receives a pattern, it needs to:

1. **Parse the pattern** - Identify wildcards and literal segments
2. **Query the data system** - Translate pattern to native query
3. **Return matching results** - As an iterator of (URI, Obj) pairs

### Example: tbleSpace Pattern Handling

```java
// Pattern: db:users/+
// After routing: users/+

// tbleSpace parses this as:
// - Table: "users"
// - Row: "+" (wildcard - all rows)
// - Field: none (return full rows)

// Executes SQL:
SELECT * FROM users

// Returns iterator of:
(users/1, {id: 1, name: "Alice", age: 30})
(users/2, {id: 2, name: "Bob", age: 25})
(users/3, {id: 3, name: "Charlie", age: 35})
```

### Example: Field-Level Pattern

```java
// Pattern: db:users/+/name
// After routing: users/+/name

// tbleSpace parses this as:
// - Table: "users"
// - Row: "+" (wildcard - all rows)
// - Field: "name" (specific field)

// Executes SQL:
SELECT id, name FROM users
// Note: Includes primary key for row identification

// Returns iterator of:
(users/1/name, "Alice")
(users/2/name, "Bob")
(users/3/name, "Charlie")
```

## Pattern Matching in SQL

The `ExistingTableSchema` class handles pattern matching for SQL tables:

### Path Parsing
```java
List<String> tablePath = parseTablePath(pattern);
// Returns: [tableName, rowId, fieldName]
// Uses "+" as placeholder for wildcards

// Examples:
// "users/1/name" → ["users", "1", "name"]
// "users/+/name" → ["users", "+", "name"]
// "users/+" → ["users", "+", "+"]
```

### Query Generation
```java
if (rowId.equals("+")) {
    // Wildcard - fetch all rows
    if (fieldName.equals("+")) {
        // All rows, all fields
        sql = "SELECT * FROM " + tableName;
    } else {
        // All rows, specific field
        sql = "SELECT id, " + fieldName + " FROM " + tableName;
    }
} else {
    // Specific row
    if (fieldName.equals("+")) {
        // Specific row, all fields
        sql = "SELECT * FROM " + tableName + " WHERE id = ?";
    } else {
        // Specific row, specific field
        sql = "SELECT id, " + fieldName + " FROM " + tableName + " WHERE id = ?";
    }
}
```

## Pattern Matching Best Practices

### 1. Be Specific When Possible
```java
// ✅ Good - specific pattern
Obj user = Router.readFromSpace(f("db:users/1"));

// ⚠️ Less efficient - fetches all users
Obj users = Router.readFromSpace(f("db:users/+"));
// Then filter in memory
```

### 2. Use Field-Level Patterns
```java
// ✅ Good - only fetches needed field
Obj names = Router.readFromSpace(f("db:users/+/name"));

// ❌ Wasteful - fetches all fields then extracts name
Obj users = Router.readFromSpace(f("db:users/+"));
// Then map to extract names
```

### 3. Leverage Query Optimization
```java
// With rewrites, this becomes efficient:
Obj count = Router.readFromSpace(f("db:users/+")).count();
// Rewritten to: SELECT COUNT(*) FROM users
// Instead of fetching all rows
```

### 4. Understand Return Types
```java
// Single object returns Obj
Obj user = Router.readFromSpace(f("db:users/1"));
assertTrue(user.isRec());

// Pattern may return Rec or Lst depending on Space
Obj users = Router.readFromSpace(f("db:users/+"));
// Check type before using
```

## Pattern Matching vs Filtering

### Pattern Matching (Efficient)
Happens **at the data source**:
```java
// Database does the filtering
Obj activeUsers = Router.readFromSpace(f("db:users/+[?active=true]"));
// SQL: SELECT * FROM users WHERE active = true
```

### In-Memory Filtering (Less Efficient)
Happens **after fetching**:
```java
// Fetch all, then filter
Obj allUsers = Router.readFromSpace(f("db:users/+"));
Obj activeUsers = allUsers.filter(u -> u.at(uri("active")).boolValue());
// Fetches all users, filters in memory
```

**Prefer pattern matching** when the data system supports it!

## Advanced Patterns

### Combining Wildcards
```java
// All fields of all users
db:users/+/+

// All data under users
db:users/#

// All items in all orders
db:orders/+/items/+
```

### Cross-Table Patterns (Future)
Once foreign key traversal is implemented:
```java
// All products in all orders
db:orders/+/product_id/+

// Automatically follows foreign key:
// orders.product_id → products.id
```

## Testing Pattern Matching

```java
@Test
public void testPatternMatching() {
    // Create test data
    Router.writeToSpace(f("db:users/1"), rec(uri("name"), str("Alice")));
    Router.writeToSpace(f("db:users/2"), rec(uri("name"), str("Bob")));
    Router.writeToSpace(f("db:users/3"), rec(uri("name"), str("Charlie")));

    // Test single-level wildcard
    Obj allUsers = Router.readFromSpace(f("db:users/+"));
    assertFalse(allUsers.isNoObj());

    // Test field-level pattern
    Obj allNames = Router.readFromSpace(f("db:users/+/name"));
    assertFalse(allNames.isNoObj());

    // Test multi-level wildcard
    Obj everything = Router.readFromSpace(f("db:users/#"));
    assertFalse(everything.isNoObj());
}
```

## Common Mistakes

### ❌ Using `*` in Java
```java
// Wrong - * is console syntax
Obj data = Router.readFromSpace(f("*db:users/+"));

// Correct
Obj data = Router.readFromSpace(f("db:users/+"));
```

### ❌ Forgetting the Scheme
```java
// Wrong - missing "db:" scheme
Obj data = Router.readFromSpace(f("users/+"));

// Correct
Obj data = Router.readFromSpace(f("db:users/+"));
```

### ❌ Using `#` in the Middle
```java
// Wrong - # must be at end
Obj data = Router.readFromSpace(f("db:#/users"));

// Correct
Obj data = Router.readFromSpace(f("db:users/#"));
```

## Key Takeaways

1. **Two wildcards**: `+` (single-level) and `#` (multi-level)
2. **`#` must be at end** - It's a suffix wildcard
3. **`*` is console only** - Don't use in Java code
4. **Patterns are efficient** - Pushed down to data system
5. **Field-level patterns** - Fetch only what you need
6. **MQTT semantics** - Same as MQTT topic matching
7. **Return types vary** - Check if Rec or Lst

## Next Steps

- Learn about [Universal References](05-universal-references.md) - the `!*` system
- See [Basic Reads](../02-examples/01-basic-reads.md) - practical examples
- Explore [Pattern Wildcards](../02-examples/02-pattern-wildcards.md) - more examples

---

**Remember**: Pattern matching is **powerful** - it lets you query multiple objects efficiently. Use it to avoid N+1 query problems and fetch exactly what you need!
