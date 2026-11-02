# Document Database Access Implementation

## Overview

This document outlines the implementation of document database access in metatron's `docSpace`, using DocumentDB (MIT licensed) with the MongoDB Java API. This follows the same architectural pattern as `tbleSpace` for SQL databases.

## Architecture

```
/src/main/java/studio/phaseshift/metatron/isa/doc/
├── docSpace.java                          // Main space implementation
├── schema/
│   ├── storage/
│   │   └── DocumentSchema.java            // How mtron encodes/stores data in MongoDB
│   └── domain/
│       ├── ExistingCollectionSchema.java  // Discover collections, indexes, references
│       └── DocumentSchemaGenerator.java   // Generate mtron types from collections
```

## Design Decisions

### Schema Discovery

**Approach**: If the database provides schema information, use it. Otherwise, collections are typed as `rec::T` (unconstrained records).

**Rationale**: Document databases are schemaless by design. We respect this flexibility while allowing users to optionally define schemas for type safety.

### ObjectId Handling

**Decision**: Map MongoDB ObjectId to mtron's `uri` type.

**Rationale**: Everything must exist in the same type space to enable cross-database references (e.g., MySQL row → MongoDB document → TinkerPop vertex).

### Nested Arrays and Documents

**Approach**: Follow vendor semantics faithfully. If MongoDB supports infinite nesting, we support infinite nesting.

**Rationale**: Remain as faithful to the true space semantics/behaviors as possible.

### Reference Resolution

Document databases use several reference patterns:

1. **DBRef** - MongoDB's standard reference format:
   ```json
   { "$ref": "users", "$id": ObjectId("...") }
   ```
   Maps to: `!*mongo:users/507f1f77bcf86cd799439011`

2. **Manual References** - Foreign key style:
   ```json
   { "userId": ObjectId("507f1f77bcf86cd799439011") }
   ```
   Detected via naming conventions or explicit schema hints.

3. **Embedded Documents** - Nested objects:
   ```json
   { "address": { "city": "Paris", "country": "France" } }
   ```
   Kept as nested `rec` objects (not references).

### Lazy Resolution

Like SQL foreign keys, document references use `auto_from` instructions:

```
*mongo:orders/123
==> [
  _id => '123',
  customerId => !*mongo:customers/456,  // auto_from instruction
  items => [...]
]

*mongo:orders/123>>customerId
==> [
  _id => '456',
  name => 'Acme Corp',
  ...
]
```

## Type Mapping

| MongoDB Type | mtron Type | Notes |
|--------------|------------|-------|
| ObjectId | uri | Enables cross-database references |
| String | str::T | Direct mapping |
| Int32 | int::T | Direct mapping |
| Int64 | int::T | Direct mapping |
| Double | real::T | Direct mapping |
| Boolean | bool::T | Direct mapping |
| Date | int::T | Timestamp in milliseconds |
| Array | lst::T | Direct mapping |
| Document | rec::T | Nested records |
| Null | noobj | mtron's null equivalent |
| DBRef | auto_from | Lazy reference resolution |

## Implementation Phases

### Phase 1: Basic CRUD Operations
- [ ] Connection management
- [ ] Read documents by ID
- [ ] Write documents
- [ ] Delete documents
- [ ] Pattern-based routing (`*mongo:collection/id`)

### Phase 2: Schema Discovery
- [ ] Discover collections
- [ ] Detect indexes
- [ ] Infer field types from sample documents
- [ ] Generate mtron type definitions
- [ ] Schema access via `*mongo:schema/database`

### Phase 3: Reference Resolution
- [ ] Detect DBRef references
- [ ] Detect manual references (naming conventions)
- [ ] Implement lazy resolution with `auto_from`
- [ ] Handle circular references

### Phase 4: Query Translation
- [ ] Map mtron queries to MongoDB find operations
- [ ] Support filtering, projection, sorting
- [ ] Aggregation pipeline integration (future)

## Package Structure

### `/isa/doc/`
- `docSpace.java` - Main space implementation, connection pooling, routing

### `/isa/doc/schema/storage/`
- `DocumentSchema.java` - How mtron stores its own data in MongoDB
- Similar to `TableSchema.java` in tbleSpace

### `/isa/doc/schema/domain/`
- `ExistingCollectionSchema.java` - Discovers existing collections and their structure
- `DocumentSchemaGenerator.java` - Generates mtron type definitions from collections
- Similar to SQL schema discovery

## Configuration

Example space configuration:

```java
docSpace.of(
    rec(
        uri(PATTERN), uri("mongo:#"),
        uri(HOST), uri("mongodb://localhost:27017/mydb"),
        uri(DRIVER), uri("com.mongodb.client.MongoClient"),
        uri(ROUTE), rec(uri("mongo:"), uri("/mongo/")),
        uri(COLLECTION), lst()  // Empty = discover all collections
    ).jvm(),
    f("/sys/space/mongo")
)
```

## Testing Strategy

1. **Unit Tests** - Test type mapping, reference detection
2. **Integration Tests** - Test with DocumentDB instance
3. **Cross-Database Tests** - Test references between SQL, MongoDB, and graph databases

## Related Files

- `/src/main/java/studio/phaseshift/metatron/isa/tble/tbleSpace.java` - SQL reference implementation
- `/src/main/java/studio/phaseshift/metatron/isa/grph/tp3/space/grphSpace.java` - Graph reference implementation
- `/docs/training/03-implementation/sql-schema-access.md` - SQL schema access documentation

## Dependencies

```xml
<!-- MongoDB Java Driver (compatible with DocumentDB) -->
<dependency>
    <groupId>org.mongodb</groupId>
    <artifactId>mongodb-driver-sync</artifactId>
    <version>4.11.0</version>
</dependency>
```

## Next Steps

1. Set up DocumentDB test instance
2. Implement basic `docSpace` with CRUD operations
3. Add schema discovery
4. Implement reference resolution with `auto_from`
5. Write comprehensive tests
