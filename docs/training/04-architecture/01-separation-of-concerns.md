# Separation of Concerns in Metatron

## The Three-Layer Architecture

Metatron has a clean separation of concerns across three main layers:

```
┌─────────────────────────────────────────┐
│         Application Code                │
│   (Uses Router.readFromSpace())         │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│            Router Layer                 │
│  - Pattern matching                     │
│  - Route mapping                        │
│  - Space selection                      │
│  - Path translation                     │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│         Space.Helper Layer              │
│  - Poly unrolling                       │
│  - Field access resolution              │
│  - Reference dereferencing              │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│            Space Layer                  │
│  - directReader() / directWriter()      │
│  - Raw data access                      │
│  - System-specific operations           │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│         Data System                     │
│  (SQL, MongoDB, Filesystem, etc.)       │
└─────────────────────────────────────────┘
```

## Layer Responsibilities

### 1. Router Layer

**What it does**:
- ✅ Receives URI requests from application code
- ✅ Finds the matching Space based on URI pattern
- ✅ Applies route mapping to translate URIs
- ✅ Strips pattern prefixes
- ✅ Coordinates between multiple Spaces
- ✅ Delegates to Space.Helper for poly unrolling

**What it doesn't do**:
- ❌ Access data systems directly
- ❌ Know about SQL, MongoDB, etc.
- ❌ Perform poly unrolling itself

**Key Methods**:
```java
Router.readFromSpace(fURI uri)
Router.writeToSpace(fURI uri, Obj value)
Router.global().getSpace(fURI uri)
```

### 2. Space.Helper Layer

**What it does**:
- ✅ Handles poly unrolling (field access on records/lists)
- ✅ Resolves multi-level field access (e.g., `/users/1/name`)
- ✅ Dereferences `!*` references
- ✅ Provides utility methods for Spaces

**What it doesn't do**:
- ❌ Access data systems directly
- ❌ Route URIs
- ❌ Translate paths

**Key Methods**:
```java
Space.Helper.resolveRead(space, pattern, reader)
Space.Helper.unrollPoly(obj, segments)
```

### 3. Space Layer

**What it does**:
- ✅ Implements `directReader()` and `directWriter()`
- ✅ Returns raw data from the underlying system
- ✅ Delegates to schemas (ExistingTableSchema, etc.)
- ✅ Handles system-specific operations
- ✅ Minimal and focused

**What it doesn't do**:
- ❌ Translate URIs (Router does this)
- ❌ Strip pattern prefixes (Router does this)
- ❌ Unroll poly types (Space.Helper does this)
- ❌ Complex path manipulation

**Key Methods**:
```java
directReader()  // Returns Function<fURI, Iterator<Pair<fURI, Obj>>>
directWriter()  // Returns BiFunction<fURI, Obj, Obj>
rewrite()       // Applies route mapping (called by Router)
```

## The Flow of a Read Operation

Let's trace what happens when you call `Router.readFromSpace(f("db:users/1/name"))`:

### Step 1: Application Code
```java
Obj userName = Router.readFromSpace(f("db:users/1/name"));
```

### Step 2: Router
```java
// Router receives: "db:users/1/name"
// 1. Find Space with pattern "db:#" → tabledbSpace
// 2. Apply route mapping: "db:" → ""
//    Result: "users/1/name"
// 3. Delegate to Space.Helper for poly unrolling
```

### Step 3: Space.Helper
```java
// Space.Helper receives: "users/1/name"
// 1. Split into base + segments: "users/1" + ["name"]
// 2. Call directReader() to get base object
// 3. Unroll poly: extract "name" field from user record
// 4. Return the name value
```

### Step 4: Space (directReader)
```java
// directReader receives: "users/1"
// 1. Delegate to ExistingTableSchema
// 2. Parse path: table="users", row="1"
// 3. Execute SQL: SELECT * FROM users WHERE id = 1
// 4. Return user record
```

### Step 5: Data System
```sql
-- SQL database executes:
SELECT * FROM users WHERE id = 1
-- Returns: {id: 1, name: "Alice", age: 30}
```

### Step 6: Return Path
```
Data System → Space → Space.Helper → Router → Application
{id: 1, name: "Alice", age: 30} → user record → "Alice" → "Alice" → "Alice"
```

## Why This Separation Matters

### 1. Single Responsibility
Each layer has **one job**:
- Router: Routing and coordination
- Space.Helper: Poly unrolling
- Space: Data access

### 2. Testability
You can test each layer independently:
```java
// Test Router without Spaces
@Test
public void testRouting() {
    // Mock Space, test routing logic
}

// Test Space without Router
@Test
public void testSpaceRead() {
    // Call directReader() directly with known input
}
```

### 3. Extensibility
Adding new features is clean:
- New routing logic → Router
- New poly unrolling → Space.Helper
- New data system → New Space

### 4. Maintainability
Bugs are localized:
- Routing bug → Check Router
- Poly unrolling bug → Check Space.Helper
- Data access bug → Check Space

## Common Violations and Fixes

### ❌ Violation: Space Does Translation
```java
// WRONG: Space doing Router's job
@Override
public Function<fURI, Iterator<Pair<fURI, Obj>>> directReader() {
    return pattern -> {
        // Stripping pattern prefix (Router's job!)
        fURI cleaned = stripPatternPrefix(pattern);
        return getData(cleaned);
    };
}
```

### ✅ Fix: Let Router Handle It
```java
// CORRECT: Space just returns data
@Override
public Function<fURI, Iterator<Pair<fURI, Obj>>> directReader() {
    return pattern -> {
        // Router has already translated the path
        return getData(pattern);
    };
}
```

### ❌ Violation: Space Does Poly Unrolling
```java
// WRONG: Space doing Space.Helper's job
@Override
public Function<fURI, Iterator<Pair<fURI, Obj>>> directReader() {
    return pattern -> {
        // Parsing field access (Space.Helper's job!)
        if (pattern.segments().size() > 2) {
            String field = pattern.segments().get(2);
            Obj obj = getObject(pattern);
            return obj.at(uri(field));  // Poly unrolling!
        }
        return getData(pattern);
    };
}
```

### ✅ Fix: Delegate to Space.Helper
```java
// CORRECT: Let Space.Helper handle poly unrolling
// Space just returns the base object
@Override
public Function<fURI, Iterator<Pair<fURI, Obj>>> directReader() {
    return pattern -> {
        // Just return the object
        // Space.Helper will handle field access
        return getData(pattern);
    };
}
```

### ❌ Violation: Application Calls directReader()
```java
// WRONG: Bypassing Router
@Test
public void testRead() {
    tbleSpace space = createSpace();
    Obj user = space.directReader().apply(f("/users/1")).next().obj();
}
```

### ✅ Fix: Use Router
```java
// CORRECT: Go through Router
@Test
public void testRead() {
    Obj user = Router.readFromSpace(f("db:users/1"));
}
```

## Design Principles

### 1. Router is the Vendor's Friend
The Router does the "dirty work" of translation and coordination, so Space implementers (vendors) can focus on data access.

**Vendor perspective**: "I just need to implement directReader() and return data. Router handles everything else."

### 2. Spaces are Minimal
A Space should be as simple as possible:
```java
@Override
public Function<fURI, Iterator<Pair<fURI, Obj>>> directReader() {
    return pattern -> {
        // Delegate to schema
        if (schema.handles(pattern)) {
            return schema.read(pattern);
        }
        return Collections.emptyIterator();
    };
}
```

**Guideline**: If directReader() is more than 20 lines, you're probably doing too much.

### 3. Space.Helper is the Utility Belt
Space.Helper provides common functionality that all Spaces need:
- Poly unrolling
- Reference resolution
- Path manipulation

**Use it**: Don't reimplement these in every Space.

### 4. One-Way Dependencies
```
Application → Router → Space.Helper → Space → Data System
```

Dependencies flow **one way**:
- Application depends on Router
- Router depends on Space.Helper and Space
- Space depends on Data System
- **No circular dependencies**

## Testing Strategy

### Test Each Layer Independently

**Router Tests**:
```java
@Test
public void testRouting() {
    // Test pattern matching
    // Test route mapping
    // Test Space selection
}
```

**Space.Helper Tests**:
```java
@Test
public void testPolyUnrolling() {
    // Test field access
    // Test multi-level access
    // Test reference resolution
}
```

**Space Tests**:
```java
@Test
public void testDirectReader() {
    // Test data access
    // Test pattern handling
    // Test schema delegation
}
```

### Integration Tests
```java
@Test
public void testEndToEnd() {
    // Test full flow: Application → Router → Space → Data
    Obj user = Router.readFromSpace(f("db:users/1/name"));
    assertEquals(str("Alice"), user);
}
```

## Key Takeaways

1. **Three layers**: Router, Space.Helper, Space
2. **Single responsibility**: Each layer has one job
3. **Router coordinates**: Routing, translation, coordination
4. **Space.Helper unrolls**: Poly types, field access
5. **Space accesses**: Raw data from system
6. **One-way dependencies**: No circular dependencies
7. **Minimal Spaces**: Keep directReader() simple
8. **Test independently**: Each layer can be tested alone

## Next Steps

- Read [Router vs Space vs Helper](02-router-space-helper.md) - detailed comparison
- See [The Universal Graph Vision](03-universal-graph.md) - big picture
- Review [Common Mistakes](../05-patterns/01-common-mistakes.md) - what not to do

---

**Remember**: Separation of concerns makes Metatron **maintainable**, **testable**, and **extensible**. Each layer does its job and nothing more. 🎯
