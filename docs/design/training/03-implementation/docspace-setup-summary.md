# docSpace Package Setup - Summary

## Package Structure Created

```
/src/main/java/studio/phaseshift/metatron/isa/doc/
├── package-info.java                      // Package documentation
├── schema/
│   ├── storage/
│   │   └── package-info.java              // Storage schema documentation
│   └── domain/
│       └── package-info.java              // Domain schema documentation

/src/test/java/studio/phaseshift/metatron/isa/doc/
└── (empty - ready for tests)

/docs/training/03-implementation/
└── document-database-access.md            // Implementation plan
```

## Design Decisions Confirmed

### 1. Schema Discovery
- **If database provides schema**: Use it
- **If schemaless**: Collections typed as `rec::T` (unconstrained records)
- **User can optionally define schemas** for type safety

### 2. ObjectId Handling
- **Map to `uri` type** (not a new type)
- **Rationale**: Enables cross-database references (MySQL ↔ MongoDB ↔ TinkerPop)
- **Example**: `ObjectId("507f1f77bcf86cd799439011")` → `uri("507f1f77bcf86cd799439011")`

### 3. Nested Arrays/Documents
- **Follow vendor semantics faithfully**
- **If MongoDB supports infinite nesting**: We support infinite nesting
- **Embedded documents**: Kept as nested `rec` objects (not references)

### 4. Reference Resolution
Three types of references:

1. **DBRef** (MongoDB standard):
   ```json
   { "$ref": "users", "$id": ObjectId("...") }
   ```
   → `!*mongo:users/507f1f77bcf86cd799439011`

2. **Manual References** (foreign key style):
   ```json
   { "userId": ObjectId("507f1f77bcf86cd799439011") }
   ```
   → Detected via naming conventions or schema hints

3. **Embedded Documents** (nested objects):
   ```json
   { "address": { "city": "Paris" } }
   ```
   → Nested `rec` objects (not references)

### 5. Lazy Resolution
- References use `auto_from` instructions (like SQL foreign keys)
- Prevents infinite recursion in circular references
- Resolves on-demand when accessed via `>>` operator

## Type Mapping

| MongoDB Type | mtron Type | Notes |
|--------------|------------|-------|
| ObjectId | uri | Cross-database references |
| String | str::T | Direct mapping |
| Int32/Int64 | int::T | Direct mapping |
| Double | real::T | Direct mapping |
| Boolean | bool::T | Direct mapping |
| Date | int::T | Timestamp (milliseconds) |
| Array | lst::T | Direct mapping |
| Document | rec::T | Nested records |
| Null | noobj | mtron's null |
| DBRef | auto_from | Lazy reference |

## Implementation Phases

### Phase 1: Basic CRUD (Next)
- [ ] Connection management
- [ ] Read documents by ID
- [ ] Write documents
- [ ] Delete documents
- [ ] Pattern-based routing

### Phase 2: Schema Discovery
- [ ] Discover collections
- [ ] Detect indexes
- [ ] Infer field types
- [ ] Generate mtron types
- [ ] Schema access via fURIs

### Phase 3: Reference Resolution
- [ ] Detect DBRef
- [ ] Detect manual references
- [ ] Lazy resolution with auto_from
- [ ] Handle circular references

### Phase 4: Query Translation
- [ ] Map mtron queries to MongoDB find
- [ ] Filtering, projection, sorting
- [ ] Aggregation (future)

## Technology Stack

- **Database**: DocumentDB (MIT licensed)
- **API**: MongoDB Java Driver (compatible with DocumentDB)
- **Version**: mongodb-driver-sync 4.11.0

## Example Configuration

```java
docSpace.of(
    rec(
        uri(PATTERN), uri("mongo:#"),
        uri(HOST), uri("mongodb://localhost:27017/mydb"),
        uri(DRIVER), uri("com.mongodb.client.MongoClient"),
        uri(ROUTE), rec(uri("mongo:"), uri("/mongo/")),
        uri(COLLECTION), lst()  // Empty = discover all
    ).jvm(),
    f("/sys/space/mongo")
)
```

## Example Usage

```java
// Read a document
*mongo:users/507f1f77bcf86cd799439011
==> [
  _id => '507f1f77bcf86cd799439011',
  name => 'John Doe',
  email => 'john@example.com',
  addressId => !*mongo:addresses/123  // Lazy reference
]

// Traverse reference
*mongo:users/507f1f77bcf86cd799439011>>addressId
==> [
  _id => '123',
  city => 'Paris',
  country => 'France'
]

// Access schema
*mongo:schema/mydb
==> [
  pattern => mongo:schema/mydb/#,
  collections => [...],
  references => [...]
]
```

## Next Steps

1. **Add MongoDB dependency** to pom.xml
2. **Implement basic docSpace** with connection management
3. **Implement CRUD operations** (read, write, delete)
4. **Add routing integration** with pattern matching
5. **Write initial tests** with DocumentDB

## Related Documentation

- `/docs/training/03-implementation/document-database-access.md` - Full implementation plan
- `/docs/training/03-implementation/sql-schema-access.md` - SQL reference implementation
- `/docs/training/03-implementation/lazy-fk-resolution-summary.md` - Lazy resolution pattern

## Package Documentation

All packages include `package-info.java` files with:
- Package purpose and responsibilities
- Architecture overview
- Usage examples
- Cross-references to related packages

The infrastructure is ready for implementation! 🚀
