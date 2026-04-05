# Spaces and Routing

## What is a Space?

A **Space** is an adapter that connects Metatron to a specific data system. Think of it as a **translator** that speaks both Metatron's language (URIs and objects) and the data system's native language (SQL, MongoDB queries, filesystem operations, etc.).

Each Space is responsible for:
1. **Reading data** from its underlying system
2. **Writing data** to its underlying system
3. **Translating URIs** to system-specific addresses
4. **Returning Metatron objects** (Obj, Rec, Lst, etc.)

## Types of Spaces

### tbleSpace - SQL Databases
Connects to SQL databases (SQLite, PostgreSQL, MySQL):
```java
tbleSpace sqlSpace = tbleSpace.of(
    rec(
        uri(PATTERN), uri("db:#"),           // Matches URIs like db:users/1
        uri(HOST), uri("sqlite:data.db"),    // Database connection
        uri(DRIVER), uri("org.sqlite.JDBC"), // JDBC driver
        uri(ROUTE), rec(uri("db:"), uri("")) // Strip "db:" prefix
    ).jvm(),
    f("/sys/space/tble/mydb")
);
```

### graphSpace - Graph Databases
Connects to TinkerPop3 graph databases:
```java
graphSpace graphSpace = graphSpace.of(
    rec(
        uri(PATTERN), uri("graph:#"),
        uri(HOST), uri("localhost:8182"),
        uri(ROUTE), rec(uri("graph:"), uri(""))
    ).jvm(),
    f("/sys/space/graph/mygraph")
);
```

### Custom Spaces
You can create spaces for any data system:
- Document databases (MongoDB, CouchDB)
- Filesystems
- REST APIs
- Message queues
- In-memory caches
- Anything with data!

## The Router

The **Router** is the central hub that:
1. **Receives URI requests** from application code
2. **Finds the matching Space** based on URI patterns
3. **Translates the URI** using the Space's route mapping
4. **Delegates to the Space** for actual data access
5. **Returns the result** to the caller

### How Routing Works

```java
// Application code
Obj user = Router.readFromSpace(f("db:users/1"));

// Router's process:
// 1. Receive URI: "db:users/1"
// 2. Find Space with pattern "db:#" → tabledbSpace
// 3. Apply route mapping: "db:" → ""
//    Result: "users/1"
// 4. Call: tabledbSpace.directReader().apply(f("users/1"))
// 5. Return: User object
```

## Pattern Matching

Spaces register with the Router using **URI patterns**:

### Pattern Syntax
- `db:#` - Matches `db:` followed by anything
  - Matches: `db:users/1`, `db:products/+`, `db:orders/1/items`
- `db:users/#` - Matches `db:users/` followed by anything
  - Matches: `db:users/1`, `db:users/+/name`
  - Doesn't match: `db:products/1`

### Pattern Wildcards
- `#` - Multi-level wildcard (must be at the end)
  - `db:#` matches all paths starting with `db:`
- `+` - Single-level wildcard
  - `db:users/+` matches `db:users/1` but not `db:users/1/name`

**Important**: Patterns are for **routing**, wildcards are for **querying**:
```java
// Pattern (in Space config): "db:#"
// Query (in application): "db:users/+"
```

## Route Mapping

Route mapping defines how URIs are translated before reaching the Space.

### Basic Route
```java
uri(ROUTE), rec(uri("db:"), uri(""))
```
- **From**: `db:`
- **To**: `` (empty)
- **Effect**: Strips the `db:` prefix

**Example**:
```
Input:  db:users/1
Output: users/1
```

### Nested Route
```java
uri(ROUTE), rec(uri("db:"), uri("/tble/"))
```
- **From**: `db:`
- **To**: `/tble/`
- **Effect**: Replaces `db:` with `/tble/`

**Example**:
```
Input:  db:users/1
Output: /tble/users/1
```

### Why Route Mapping?

Route mapping allows:
1. **Clean external URIs**: Users see `db:users/1`
2. **Internal organization**: Space sees `users/1` or `/tble/users/1`
3. **Flexibility**: Change internal structure without breaking external API
4. **Namespace isolation**: Multiple spaces can coexist

## Separation of Concerns

This is crucial to understand:

### Router's Job
- ✅ Receive URI requests
- ✅ Find matching Space
- ✅ Apply route mapping
- ✅ Handle poly unrolling (via Space.Helper)
- ✅ Coordinate between Spaces

### Space's Job
- ✅ Read/write from underlying data system
- ✅ Return raw data as Metatron objects
- ✅ Handle system-specific operations
- ❌ **NOT** path translation (Router does this)
- ❌ **NOT** poly unrolling (Space.Helper does this)

### directReader() is Minimal
The `directReader()` method in a Space should be **simple**:
```java
@Override
public Function<fURI, Iterator<Pair<fURI, Obj>>> directReader() {
    return pattern -> {
        // Just return raw data matching the pattern
        // No path manipulation, no poly unrolling
        // Router has already done the translation
        return getDataFromSystem(pattern);
    };
}
```

**Don't do this** in directReader():
- ❌ Strip pattern prefixes
- ❌ Rewrite URIs
- ❌ Unroll poly types
- ❌ Complex transformations

**Do this** in directReader():
- ✅ Query the underlying system
- ✅ Return raw results
- ✅ Delegate to schemas/handlers
- ✅ Keep it simple!

## Space Configuration

A Space is configured with a record containing:

### Required Fields
- `PATTERN` - URI pattern to match (e.g., `"db:#"`)
- `HOST` - Connection string to data system
- `DRIVER` - Driver class (for JDBC, etc.)
- `ROUTE` - Route mapping record

### Optional Fields
- `TABLE` - List of table definitions (for SQL)
- `SCHEMA` - Schema information
- Custom configuration per Space type

### Example: Complete tbleSpace Config
```java
tbleSpace space = tbleSpace.of(
    rec(
        uri(PATTERN), uri("db:#"),
        uri(HOST), uri("sqlite:target/test.db"),
        uri(DRIVER), uri("org.sqlite.JDBC"),
        uri(ROUTE), rec(uri("db:"), uri("")),
        uri(TABLE), lst(
            rec(
                uri("name"), str("users"),
                uri("schema"), rec(
                    uri("id"), uri("int"),
                    uri("name"), uri("string"),
                    uri("age"), uri("int")
                )
            )
        )
    ).jvm(),
    f("/sys/space/tble/mydb")
);
```

## Space Registration

When a Space is created, it automatically registers with the Router (if it extends `AbstractSpace`):

```java
// Creating a Space automatically registers it
tbleSpace space = tbleSpace.of(config, vid);

// Now the Router knows about it
Obj data = Router.readFromSpace(f("db:users/1"));
// Router finds the space and delegates to it
```

## Multiple Spaces

You can have multiple Spaces with different patterns:

```java
// SQL database
tbleSpace sqlSpace = tbleSpace.of(
    rec(uri(PATTERN), uri("db:#"), ...),
    f("/sys/space/tble/sql")
);

// Graph database
graphSpace graphSpace = graphSpace.of(
    rec(uri(PATTERN), uri("graph:#"), ...),
    f("/sys/space/graph/main")
);

// Filesystem
fileSpace fsSpace = fileSpace.of(
    rec(uri(PATTERN), uri("file:#"), ...),
    f("/sys/space/file/data")
);

// Now you can access all three:
Obj user = Router.readFromSpace(f("db:users/1"));      // SQL
Obj node = Router.readFromSpace(f("graph:person/1"));  // Graph
Obj file = Router.readFromSpace(f("file:/data/doc.txt")); // Filesystem
```

## Space.Helper - The Poly Unroller

`Space.Helper` provides utility methods for Spaces, most importantly **poly unrolling**:

### What is Poly Unrolling?
When you access a field on an object, Metatron needs to "unroll" the poly type:
```java
// User is a Rec (poly type)
// Accessing /name should return the name field
Router.readFromSpace(f("db:users/1/name"))
```

### How It Works
```java
Space.Helper.resolveRead(space, pattern, obj -> {
    // This lambda is called with the base object
    // Space.Helper handles the field access
    return obj;
});
```

**Key Point**: Spaces don't do poly unrolling themselves - they delegate to `Space.Helper.resolveRead()`.

## Common Patterns

### Reading Data
```java
// Single object
Obj user = Router.readFromSpace(f("db:users/1"));

// With pattern
Obj allUsers = Router.readFromSpace(f("db:users/+"));

// Field access (poly unrolling)
Obj userName = Router.readFromSpace(f("db:users/1/name"));
```

### Writing Data
```java
Router.writeToSpace(
    f("db:users/1"),
    rec(
        uri("name"), str("Alice"),
        uri("age"), jnt(30)
    )
);
```

### Testing Spaces
```java
// In tests, use Router.readFromSpace(), not directReader()
@Test
public void testUserRead() {
    Obj user = Router.readFromSpace(f("db:users/1"));
    assertFalse(user.isNoObj());
}
```

## Key Takeaways

1. **Spaces are adapters** - They connect Metatron to data systems
2. **Router coordinates** - It finds Spaces and applies route mapping
3. **Pattern matching** - Spaces register with URI patterns
4. **Route mapping** - Translates external URIs to internal paths
5. **Separation of concerns** - Router translates, Space reads/writes
6. **directReader() is minimal** - Just return raw data
7. **Space.Helper handles poly** - Don't do it in the Space
8. **Multiple Spaces coexist** - Different patterns, different systems

## Next Steps

- Learn about [Pattern Matching](04-pattern-matching.md) - powerful query capabilities
- Explore [Universal References](05-universal-references.md) - the graph abstraction
- See [Router vs Space vs Helper](../04-architecture/02-router-space-helper.md) - detailed architecture

---

**Remember**: Spaces are **translators**, not **transformers**. They speak the data system's language and return Metatron objects. The Router handles the routing and translation.
