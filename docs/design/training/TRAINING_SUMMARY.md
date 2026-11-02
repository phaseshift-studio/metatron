# Metatron Training Summary

This document provides a quick reference guide to the key concepts, patterns, and principles in Metatron.

## Key Distinction

- **Mtron** = The programming language (like JavaScript)
- **Metatron** = The complete environment (like Node.js)
  - Includes: spaces, routers, monads, machines, serializers, instruction sets, and the mtron language

## Quick Start

### Java API
```java
// Basic Read
Obj user = Router.readFromSpace(f("db:users/1"));

// Pattern Matching
Obj allUsers = Router.readFromSpace(f("db:users/+"));
Obj allNames = Router.readFromSpace(f("db:users/+/name"));

// Write
Router.writeToSpace(f("db:users/1"), rec(uri("name"), str("Alice")));
```

### Mtron Language
```metatron
*db:users/1                  % read user
*db:users/+                  % read all users
*db:users/+.>>name           % get all names
db:users/1 -> [name=>"Alice"] % write user
{1,2,3}.plus(2).sum()        % 12
"a b c".-<' '.>-','          % "a,b,c"
```

## Core Concepts

### 1. Metatron is a Meta-Layer
- **Universal abstraction** over all data systems
- Turns any data system into a **graph database**
- Unified URI-based interface
- Cross-system navigation

### 2. Instruction Sets
Five components:
- **Types** - Data types (int, str, lst, rec, etc.)
- **Constants** - Constant values
- **Instructions** - Operations (map, filter, count, etc.)
- **Rewrites** - Code optimizations (code → code)
- **Sugars** - Console conveniences

### 3. Spaces and Routing
- **Space** - Adapter to a data system (SQL, MongoDB, etc.)
- **Router** - Coordinates between Spaces
- **Pattern** - URI pattern for matching (e.g., `db:#`)
- **Route** - URI translation (e.g., `db: → ""`)

### 4. Pattern Matching
- **`+`** - Single-level wildcard (one segment)
- **`#`** - Multi-level wildcard (end of URI)
- **MQTT semantics** - Same as MQTT topic matching

### 5. Universal References
- **`!*uri`** - Reference to any object anywhere
- **Native refs** - Foreign keys, DBRefs, symlinks
- **`>>` operator** - Navigate the graph
- **Cross-system** - References work everywhere

## Architecture Layers

```
Application Code
       ↓
    Router (routing, translation)
       ↓
Space.Helper (poly unrolling)
       ↓
    Space (data access)
       ↓
  Data System
```

### Responsibilities
- **Router**: Pattern matching, route mapping, coordination
- **Space.Helper**: Poly unrolling, field access
- **Space**: Raw data access, minimal logic

## Key Principles

### 1. Use Router, Not directReader()
```java
// ✅ Correct
Obj data = Router.readFromSpace(f("db:users/1"));

// ❌ Wrong
Obj data = space.directReader().apply(f("/users/1"));
```

### 2. `*` is Console Only
```java
// ✅ Java
Obj data = Router.readFromSpace(f("db:users/1"));

// ❌ Java (wrong)
Obj data = Router.readFromSpace(f("*db:users/1"));

// ✅ Console
mtron> *db:users/1
```

### 3. Patterns Need Wildcards
```java
// ✅ Correct
uri(PATTERN), uri("db:#")

// ❌ Wrong
uri(PATTERN), uri("db:")
```

### 4. Spaces are Minimal
```java
// ✅ Simple directReader()
@Override
public Function<fURI, Iterator<Pair<fURI, Obj>>> directReader() {
    return pattern -> schema.read(pattern);
}

// ❌ Complex directReader() (doing too much)
@Override
public Function<fURI, Iterator<Pair<fURI, Obj>>> directReader() {
    return pattern -> {
        // 100 lines of translation, unrolling, etc.
    };
}
```

### 5. Cleanup in Finally Blocks
```java
@Test
public void test() {
    setup();
    try {
        // Test logic
    } finally {
        cleanup();  // Always runs
    }
}
```

## Common Patterns

### Creating a Space
```java
tbleSpace space = tbleSpace.of(
    rec(
        uri(PATTERN), uri("db:#"),
        uri(HOST), uri("sqlite:data.db"),
        uri(DRIVER), uri("org.sqlite.JDBC"),
        uri(ROUTE), rec(uri("db:"), uri("")),
        uri(TABLE), lst()
    ).jvm(),
    f("/sys/space/tble/mydb")
);
```

### Reading Data
```java
// Single object
Obj user = Router.readFromSpace(f("db:users/1"));

// All objects
Obj users = Router.readFromSpace(f("db:users/+"));

// Specific field
Obj name = Router.readFromSpace(f("db:users/1/name"));

// All fields
Obj names = Router.readFromSpace(f("db:users/+/name"));
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

### Creating References
```java
// Metatron reference
Obj customerRef = str("!*db:customers/123");

// Store it
Router.writeToSpace(
    f("db:orders/1"),
    rec(uri("customer"), customerRef)
);

// Dereference it
Obj customer = Router.readFromSpace(f("db:orders/1/customer"));
// Returns actual customer object
```

## Common Mistakes

1. ❌ Using `*` in Java code
2. ❌ Calling `directReader()` directly
3. ❌ Forgetting pattern wildcards (`db:` instead of `db:#`)
4. ❌ Case-sensitive table lookups (use `toLowerCase()`)
5. ❌ Not cleaning up test data
6. ❌ Forgetting primary keys in SELECT
7. ❌ Using `"+"` without checking for placeholder
8. ❌ Doing translation work in Spaces
9. ❌ Incorrect route mapping
10. ❌ Not using `DROP TABLE IF EXISTS`

## Debugging Checklist

When something doesn't work:

1. **Check the pattern** - Does it have wildcards? (`db:#`)
2. **Check the route** - Does it match the pattern? (`db: → ""`)
3. **Check the URI** - Is the scheme correct? (`db:users/1`)
4. **Check the layer** - Are you using Router? (not directReader)
5. **Check the type** - Is it Rec or Lst? (check before casting)
6. **Check the logs** - What does the Space see?
7. **Check the SQL** - What query is being executed?
8. **Check cleanup** - Is test data interfering?

## Performance Tips

### 1. Use Query Rewrites
```java
// Automatically optimized
*db:movie.count()
// Becomes: SELECT COUNT(*) FROM movie
```

### 2. Fetch Only What You Need
```java
// ✅ Good - only fetches name
Obj names = Router.readFromSpace(f("db:users/+/name"));

// ❌ Wasteful - fetches everything
Obj users = Router.readFromSpace(f("db:users/+"));
```

### 3. Push Filters Down
```java
// ✅ Good - filter at database
Obj active = Router.readFromSpace(f("db:users/+[?active=true]"));

// ❌ Wasteful - filter in memory
Obj all = Router.readFromSpace(f("db:users/+"));
Obj active = all.filter(u -> u.at(uri("active")).boolValue());
```

## Testing Best Practices

### 1. Use Router in Tests
```java
@Test
public void testRead() {
    Obj user = Router.readFromSpace(f("db:users/1"));
    assertFalse(user.isNoObj());
}
```

### 2. Clean Up in Finally
```java
@Test
public void test() {
    createTable("users");
    try {
        // Test logic
    } finally {
        dropTable("users");
    }
}
```

### 3. Test Each Layer
- Router tests - pattern matching, routing
- Space.Helper tests - poly unrolling
- Space tests - data access
- Integration tests - end-to-end

## The Vision

Metatron's goal is to create a **universal graph** where:

1. **Any data system** can be a node
2. **Any reference** (native or `!*`) can be an edge
3. **Everything is navigable** through a common interface
4. **Cross-system queries** work seamlessly
5. **Automatic optimization** happens transparently

### Example: Cross-System Navigation
```java
// Start in SQL
Obj order = Router.readFromSpace(f("db:orders/1"));

// Navigate to customer (SQL FK)
Obj customer = order >> uri("customer_id");

// Navigate to preferences (MongoDB ref)
Obj prefs = customer >> uri("preferences");
// customer.preferences = "!*mongo:preferences/abc123"

// Navigate to product (SQL FK)
Obj product = order >> uri("product_id");

// Navigate to image (filesystem ref)
Obj image = product >> uri("image");
// product.image = "!*file:/images/product.jpg"
```

## Resources

### Documentation

**Language & Environment:**
- [Mtron Language Syntax](02-language/01-mtron-syntax.md) - Complete language reference
- [Metatron Environment](02-language/02-metatron-environment.md) - Spaces, routers, monads, machines

**Concepts:**
- [What is Metatron?](01-concepts/01-what-is-metatron.md)
- [Instruction Sets](01-concepts/02-instruction-sets.md)
- [Spaces and Routing](01-concepts/03-spaces-and-routing.md)
- [Pattern Matching](01-concepts/04-pattern-matching.md)
- [Universal References](01-concepts/05-universal-references.md)

### Architecture
- [Separation of Concerns](04-architecture/01-separation-of-concerns.md)
- [Router vs Space vs Helper](04-architecture/02-router-space-helper.md)
- [The Universal Graph Vision](04-architecture/03-universal-graph.md)

### Patterns
- [Common Mistakes](05-patterns/01-common-mistakes.md)
- [Best Practices](05-patterns/02-best-practices.md)
- [Debugging Guide](05-patterns/03-debugging-guide.md)

### Examples
- [Basic Operations](02-examples/01-basic-operations.md) - From mInstSetTest.java
- [Advanced Patterns](02-examples/02-advanced-patterns.md) - From boot.mtron
- [Basic Reads](02-examples/01-basic-reads.md)
- [Pattern Wildcards](02-examples/02-pattern-wildcards.md)
- [Field-Level Access](02-examples/03-field-access.md)
- [Writing Data](02-examples/04-writing-data.md)

### Advanced
- [Query Optimization with Rewrites](03-advanced/01-rewrites.md)
- [Foreign Key Traversal](03-advanced/02-foreign-key-traversal.md)
- [Creating Custom Spaces](03-advanced/03-custom-spaces.md)

## Quick Reference Card

```
┌─────────────────────────────────────────────────────────┐
│                  METATRON QUICK REFERENCE               │
├─────────────────────────────────────────────────────────┤
│ Read:    Router.readFromSpace(f("db:users/1"))         │
│ Write:   Router.writeToSpace(f("db:users/1"), obj)     │
│ Pattern: db:users/+  (all users)                       │
│ Field:   db:users/1/name  (specific field)             │
│ Ref:     !*db:users/123  (reference)                   │
│ Nav:     obj >> uri("field")  (navigate)               │
├─────────────────────────────────────────────────────────┤
│ Wildcards:                                              │
│   +  Single-level (one segment)                        │
│   #  Multi-level (end of URI)                          │
├─────────────────────────────────────────────────────────┤
│ Layers:                                                 │
│   Router       → Routing, translation                  │
│   Space.Helper → Poly unrolling                        │
│   Space        → Data access                           │
├─────────────────────────────────────────────────────────┤
│ Remember:                                               │
│   ✓ Use Router, not directReader()                    │
│   ✓ * is console only                                 │
│   ✓ Patterns need wildcards (db:#)                    │
│   ✓ Spaces are minimal                                │
│   ✓ Cleanup in finally blocks                         │
└─────────────────────────────────────────────────────────┘
```

---

**Welcome to the Grid!** 🎮✨

For more information, visit: http://metatron.phaseshift.studio
