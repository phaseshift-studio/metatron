# docSpace Implementation - Key Learnings

## Summary

After implementing the initial docSpace CRUD operations, the user refactored the code to use existing metatron patterns and utilities. This document captures the key learnings from that refactoring.

## ✅ All Tests Passing

**Final Status: 76 tests run, 0 failures, 0 errors, 0 skipped**

## Key Learnings

### 1. **Use ObjSerializer Pattern Instead of Custom Type Conversion**

**Before (Custom Implementation):**
```java
// Custom methods for each type conversion
private Obj documentToObj(final Document doc) { ... }
private Obj valueToObj(final Object value) { ... }
private Document objToDocument(final Obj obj) { ... }
private Object objToValue(final Obj obj) { ... }
```

**After (Using ObjBSONSerializer):**
```java
// Use the serializer framework
protected ObjSerializer<BsonValue> serializer;

// In constructor:
this.serializer = this.at(uri(SERIALIZER)).orElse(new ObjBSONSerializer());

// In directReader():
this.serializer.read(doc.toBsonDocument())
this.serializer.readRec(doc.toBsonDocument())

// In directWriter():
this.serializer.writeRec(obj.asRec())
this.serializer.write(obj)
```

**Why This Matters:**
- `ObjBSONSerializer` extends `AbstractObjSerializer<BsonValue>` - a framework pattern
- Handles all type conversions consistently (Bool, Int, Real, Str, Uri, Bytes, Fail, Lst, Rec)
- Uses magic numbers for special types (URI, Bytes, Fail) stored as BsonBinary
- Supports custom serializers via configuration (`uri(SERIALIZER)`)
- Consistent with other serializers in the system (ObjByteBufferSerializer, ObjSimpleJSONSerializer)

### 2. **Use Space.Helper.routeFromSpace() for Path Parsing**

**Before (Manual String Parsing):**
```java
private fURI stripPatternPrefix(final fURI pattern) {
    final String patternStr = pattern.toString();
    final String prefix = this.pattern.retractPattern().toString();
    if (patternStr.startsWith(prefix)) {
        return f(patternStr.substring(prefix.length()));
    }
    return pattern;
}

// Then split on "/" and parse manually
final String[] parts = relativePath.toString().split("/");
final String collectionName = parts[0];
final String documentId = parts[1];
```

**After (Using Space.Helper):**
```java
// Use the routing helper to convert space URIs to routed paths
final String collectionName = Space.Helper.routeFromSpace(pattern, this.routes()).segments(0, null);
final String documentID = Space.Helper.routeFromSpace(pattern, this.routes()).segments(1, null);
```

**Why This Matters:**
- `Space.Helper.routeFromSpace()` handles the route mapping automatically
- Works with the `ROUTE` configuration: `rec(uri("mongo:"), uri(""))`
- The empty string `""` means direct mapping (no prefix transformation)
- `segments(index, defaultValue)` safely extracts path segments
- Consistent with how other spaces (fsSpace, etc.) handle routing

### 3. **Use fURI.segments() Instead of Manual Parsing**

**Before:**
```java
final String fieldPath = String.join(".", Arrays.copyOfRange(parts, 2, parts.length));
```

**After:**
```java
final String fieldPath = pattern.segments().subList(1, pattern.segmentLength()).stream()
    .collect(Collectors.joining("."));
```

**Why This Matters:**
- `fURI.segments()` returns a proper List<String> of path segments
- `fURI.segmentLength()` gives the count
- `fURI.segments(index, defaultValue)` safely gets a specific segment (added by user)
- No need for string splitting and array manipulation

### 4. **Use Stream API for Collection Iteration**

**Before:**
```java
final List<IdObj> results = new ArrayList<>();
for (Document doc : collection.find()) {
    // ... process doc
    results.add(IdObj.of(docUri, obj));
}
return results.iterator();
```

**After:**
```java
return collectionStream.map(c -> this.database.getCollection(c)).flatMap(collection -> {
    // ... process documents
    return results.stream();  // or Stream.of(IdObj.of(...))
}).iterator();
```

**Why This Matters:**
- More functional, composable approach
- Easier to chain operations (map, flatMap, filter)
- Consistent with metatron's functional style
- Better for lazy evaluation and memory efficiency

### 5. **Database Name Extraction from fURI**

**Before (Manual String Parsing):**
```java
private String extractDatabaseName(final String connectionString) {
    final String withoutScheme = connectionString.replace("mongodb://", "");
    final int slashIndex = withoutScheme.indexOf('/');
    // ... complex parsing logic
}
```

**After (Using fURI API):**
```java
final fURI connectionfURI = config.get(uri(HOST)).uriValue();
this.databaseName = connectionfURI.segments(0, null);
```

**Why This Matters:**
- The HOST config is already a fURI: `uri("mongodb://localhost:27017/mydb")`
- `segments(0, null)` extracts the first segment (database name)
- fURI handles the parsing automatically
- No need for manual string manipulation

### 6. **Configuration Pattern**

**Key Configuration Fields:**
```java
uri(HOST)       // Connection fURI: mongodb://host:port/database
uri(SERIALIZER) // Optional custom serializer
uri(ROUTE)      // Route mapping: rec(uri("mongo:"), uri(""))
```

**Route Mapping:**
- `rec(uri("mongo:"), uri(""))` means: `mongo:users/user1` → `users/user1`
- Empty string means no prefix transformation
- Could use `rec(uri("mongo:"), uri("/data/"))` to add a prefix

### 7. **Test Setup Pattern**

**Key Changes:**
```java
// Extend AbstractMetatronTest for proper lifecycle
public class docSpaceTest extends AbstractSpaceTest {

    @BeforeAll
    public static void setupAll() {
        AbstractMetatronTest.begin();  // Initialize metatron
        // ... setup MongoDB
    }

    @AfterAll
    public static void stopAll() {
        AbstractMetatronTest.end();  // Shutdown metatron
        // ... shutdown MongoDB
    }

    // Load the InstSet
    BootLoader.loadInstSetProvider(DOC_ISA_TID);
}
```

### 8. **Constants and Field Names**

**Added:**
```java
private static final String ID_FIELD = "_id";  // MongoDB's ID field
```

**Why This Matters:**
- Avoid magic strings scattered throughout code
- Easy to change if needed
- Self-documenting code

## ObjBSONSerializer Deep Dive

### Type Mapping Strategy

| mtron Type | BSON Type | Notes |
|------------|-----------|-------|
| Bool | BsonBoolean | Direct mapping |
| Int | BsonInt64 | Always use 64-bit for consistency |
| Real | BsonDouble | Direct mapping |
| Str | BsonString | Direct mapping |
| Uri | BsonBinary | Magic number 0x01 + UTF-8 bytes |
| Bytes | BsonBinary | Magic number 0x00 + raw bytes |
| Fail | BsonBinary | Magic number 0x02 + UTF-8 bytes |
| Lst | BsonArray | Recursive conversion |
| Rec | BsonDocument | Key-value pairs |
| NoObj | BsonNull | Direct mapping |

### Magic Number Pattern

```java
public static final Byte BYTES_MAGIC_NUMBER = (byte) 0x00;
public static final Byte URI_MAGIC_NUMBER = (byte) 0x01;
public static final Byte FAIL_MAGIC_NUMBER = (byte) 0x02;
```

**Why Use Magic Numbers:**
- BSON doesn't have native Uri, Bytes, or Fail types
- Store as BsonBinary with a magic number prefix
- On read, check the first byte to determine the actual type
- Allows round-trip conversion without data loss

### Serializer Framework Pattern

```java
public abstract class AbstractObjSerializer<T> implements ObjSerializer<T> {
    // Abstract methods for each type
    public abstract Bool readBool(T value);
    public abstract BsonBoolean writeBool(Bool bool);
    // ... etc for all types

    // Generic read/write dispatch to specific methods
    public Obj read(T value) { ... }
    public T write(Obj obj) { ... }
}
```

**Benefits:**
- Consistent interface across all serializers
- Easy to add new serializers (JSON, MessagePack, Protobuf, etc.)
- Type-safe with generics
- Extensible via configuration

## Pattern Summary

### Before: Custom Implementation
- Manual string parsing and splitting
- Custom type conversion methods
- Array manipulation
- Imperative loops

### After: Using Metatron Patterns
- `Space.Helper.routeFromSpace()` for path routing
- `ObjSerializer` framework for type conversion
- `fURI.segments()` for path parsing
- Stream API for functional composition
- Configuration-driven serializer selection

## Key Takeaways

1. **Don't Reinvent the Wheel**: Metatron has established patterns for common operations
2. **Use the Framework**: AbstractObjSerializer, Space.Helper, fURI methods
3. **Configuration Over Code**: Serializers, routes, etc. should be configurable
4. **Functional Style**: Prefer streams and functional composition over imperative loops
5. **Type Safety**: Use the type system (generics, sealed types) to catch errors at compile time
6. **Consistency**: Follow patterns from other spaces (tbleSpace, fsSpace, etc.)

## Next Steps

With the basic CRUD operations working, the next phases are:

1. **Schema Discovery** - Infer schemas from collections
2. **Reference Resolution** - Lazy resolution of DBRef and ObjectId references
3. **Aggregation Pipeline** - Support for MongoDB aggregation
4. **Indexing** - Create and manage indexes
5. **Transactions** - Multi-document ACID transactions (if supported by backend)

## Files to Study

For deeper understanding of metatron patterns:

- `/src/main/java/studio/phaseshift/metatron/isa/Space.java` - Space.Helper methods
- `/src/main/java/studio/phaseshift/metatron/isa/mach/io/type/AbstractObjSerializer.java` - Serializer framework
- `/src/main/java/studio/phaseshift/metatron/isa/mach/io/type/ObjByteBufferSerializer.java` - Another serializer example
- `/src/main/java/studio/phaseshift/metatron/furi/fURI.java` - URI manipulation methods
- `/src/main/java/studio/phaseshift/metatron/isa/tble/tbleSpace.java` - Reference implementation for SQL
- `/src/main/java/studio/phaseshift/metatron/isa/mach/io/space/file/fsSpace.java` - Reference implementation for filesystem

## License Compliance ✅

All dependencies remain open source with permissive licenses:
- MongoDB Java Driver: Apache 2.0
- mongo-java-server (test only): BSD-3-Clause
- DocumentDB (recommended): MIT

No changes to license compliance from Phase 2.
