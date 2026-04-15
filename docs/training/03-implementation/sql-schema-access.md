# SQL Schema Access Implementation

## Overview

This document describes the implementation of SQL schema access in metatron's `tbleSpace`, which allows users to inspect SQL table structures as mtron types before querying data. This feature mirrors the schema access functionality available in `grphSpace` for graph databases.

## Key Insight: Capital Letter fURIs

**Important**: Capital letter fURIs (like `S`, `V`, `E`) denote **generic types** in metatron. Using them for data paths causes the system to return type references instead of actual data.

- ❌ **Wrong**: `*/database/S` → Returns `schema:S::T` (a type reference)
- ✅ **Correct**: `*/database/schema` → Returns the actual schema data

This is why we use `schema` instead of `S` for the schema path.

## Usage

### Accessing SQL Schemas

When a `tbleSpace` is configured with table mapping enabled (via the `TABLE` configuration), you can access the schema:

```java
// Create a space with table mapping enabled
Router.global().write(
    f("/sys/space/tble/mydb"),
    tble(
        rec(
            uri(PATTERN), uri("db:#"),
            uri(ROUTE), rec(uri("db:"), uri("/mydb/")),
            uri(HOST), uri("jdbc:sqlite:path/to/database.db"),
            uri(DRIVER), uri("org.sqlite.JDBC"),
            uri(TABLE), uri("*")  // Enable table mapping
        ).jvm(),
        f("/sys/space/tble/mydb")
    )
);

// Access the schema
final Obj schema = Router.global().read(f("db:schema"));

// The schema is a rec with:
// - pattern: The base pattern for table types (e.g., "db:schema/mydb/#")
// - tables: A list of table type definitions
```

### Schema Structure

The schema object is a `rec` containing:

```
[
  pattern => db:schema/mydb/#,
  tables => [
    /m/tble/rrow::T[isa([
      id => int::T,
      name => str::T,
      email => str::T
    ])]@db:schema/mydb/users,
    /m/tble/rrow::T[isa([
      id => int::T,
      title => str::T,
      price => real::T
    ])]@db:schema/mydb/products,
    ...
  ]
]
```

Each table type:
- Is a `rrow::T` (relational row type)
- Has an `isa` structure defining column names and types
- Is located at `pattern/tablename`

### SQL Type Mapping

SQL types are mapped to mtron types as follows:

| SQL Type | mtron Type |
|----------|------------|
| INTEGER, BIGINT, SMALLINT, TINYINT | `int::T` |
| VARCHAR, CHAR, TEXT, CLOB | `str::T` |
| REAL, FLOAT, DOUBLE, DECIMAL, NUMERIC | `real::T` |
| BOOLEAN, BIT | `bool::T` |
| DATE, TIME, TIMESTAMP | `str::T` |
| BLOB, BINARY, VARBINARY | `str::T` |
| Other types | `str::T` (default) |

## Implementation Details

### Architecture

The implementation consists of three main components:

1. **SQLSchemaGenerator** (`/src/main/java/studio/phaseshift/metatron/isa/tble/schema/SQLSchemaGenerator.java`)
   - Utility class that generates mtron type definitions from SQL table metadata
   - Uses lazy initialization to avoid circular dependencies during space construction
   - Maps SQL types to mtron types
   - Generates `rrow::T[isa([...])]` types for each table

2. **tbleSpace Integration** (`/src/main/java/studio/phaseshift/metatron/isa/tble/tbleSpace.java`)
   - Initializes `SQLSchemaGenerator` when table mapping is enabled
   - Sets `schemaPrefix` to `pattern.retractPattern().extend("schema")`
   - In `directReader()`, checks if the requested path matches the schema prefix
   - Returns the generated schema as a `rec` when accessed

3. **Test Coverage** (`/src/test/java/studio/phaseshift/metatron/isa/tble/tbleSpaceTest.java`)
   - `testSQLSchemaAccess()` verifies schema can be read and has correct structure
   - Validates that schema is a `rec` with `tables` list
   - Ensures table types are properly generated

### Key Code Sections

#### tbleSpace Constructor (lines 194-205)

```java
// Initialize SQL schema generator for type definitions (lazy - will generate types on first access)
this.schemaPrefix = this.pattern.retractPattern().extend("schema").toString();
final String dbName = sjvm.getCatalog() != null ? sjvm.getCatalog() : "db";
final fURI schemaPath = f(this.schemaPrefix).extend(dbName);

this.schemaGenerator = new SQLSchemaGenerator(
    this.existingTableSchema.getTableMetadata(),
    schemaPath
);

LOG.info("initialized {{g}}SQL schema{{X}} at %s (lazy) with %s table types",
    schemaPath, this.existingTableSchema.getTableNames().size());
```

#### tbleSpace directReader() (lines 258-274)

```java
// Check if this is a schema path (e.g., */netflix/schema or */netflix/schema/movie)
// Only if table mapping is enabled (schemaPrefix will be non-null)
if (this.schemaPrefix != null && this.schemaGenerator != null) {
    if (f(this.schemaPrefix).test(pattern)) {
        // Return the schema object itself - create it lazily
        final Map<Obj, Obj> schemaRec = new LinkedHashMap<>();
        schemaRec.put(uri("pattern"), uri(this.schemaGenerator.getSchemaBasePath().extend("#")));
        schemaRec.put(uri("tables"), lst(this.schemaGenerator.getTableTypes().stream()
            .map(t -> (Obj) t).toList()));
        return IdObj.of(f(this.schemaPrefix), rec(schemaRec)).iterator();
    } else if (pattern.hasPrefix(this.schemaPrefix)) {
        // Schema subpath - let the schema InstSet handle it
        return Collections.emptyIterator();
    }
}
```

### Lazy Initialization

The schema generator uses lazy initialization to avoid circular dependencies:

1. During space construction, only the `SQLSchemaGenerator` object is created
2. Type generation happens on first access via `getTableTypes()`
3. This ensures the space is fully initialized before types are created
4. Types can reference the space without causing initialization loops

## Comparison with Graph Schemas

### Similarities

Both SQL and graph schemas:
- Are accessible via `*/space_name/schema`
- Return a `rec` with metadata and type definitions
- Use lazy initialization to avoid circular dependencies
- Provide type information before querying data

### Differences

| Aspect | Graph Schema (grphSpace) | SQL Schema (tbleSpace) |
|--------|------------------------|------------------------|
| **Type Source** | Manually defined in schema classes | Auto-generated from SQL metadata |
| **Structure** | Vertex and edge types | Table row types |
| **Type Format** | Custom vertex/edge types | `rrow::T[isa([...])]` |
| **Discovery** | Predefined schema classes | Database introspection via JDBC |
| **Flexibility** | Can define custom properties | Limited to SQL column types |

## Foreign Key Support

### Discovery

Foreign keys are automatically discovered from the database metadata and included in the schema:

```
[
  pattern => db:schema/mydb/#,
  tables => [...],
  foreign_keys => [
    [table=>books, column=>author_id, references_table=>authors, references_column=>id],
    [table=>orders, column=>customer_id, references_table=>customers, references_column=>id],
    ...
  ]
]
```

### Lazy Traversal

Foreign key columns use **lazy resolution** to prevent infinite recursion in graph-like structures:

```java
// When reading a row, FK columns contain auto_from instructions
final Obj employee = Router.global().read(f("db:employees/123"));

// The manager_id field is an auto_from instruction (not yet resolved)
final Obj managerId = employee.asRec().at(uri("manager_id"));

// Only when accessed does it resolve to the actual manager record
final Obj manager = employee.asRec().at(uri("manager_id"));
// manager is now the full employee record for the manager
```

**Key Behavior**:
- FK columns store `auto_from(referenced_path)` instructions, not the actual referenced rows
- Resolution only happens when the field is accessed via `at()` outside its poly container
- This prevents infinite recursion in self-referencing tables (e.g., employees → manager → manager's manager)
- Matches the behavior of `auto_from()` in graph schemas

**Example with Self-Referencing Table**:

```
employees table:
  id | name      | manager_id
  1  | CEO       | NULL
  2  | VP        | 1
  3  | Manager   | 2
  4  | Employee  | 3

*db:employees/4 returns:
[
  id => 4,
  name => 'Employee',
  manager_id => auto_from(*db:employees/3)  // Not yet resolved
]

Accessing manager_id triggers resolution:
*db:employees/4>>manager_id returns:
[
  id => 3,
  name => 'Manager',
  manager_id => auto_from(*db:employees/2)  // Still lazy
]
```

This allows traversing arbitrarily deep hierarchies without loading the entire graph into memory.

### Implementation

Foreign key lazy resolution is implemented in `ExistingTableSchema.readColumnWithMetadata()`:

```java
// Check if this column is a foreign key
final ForeignKeyMetadata fk = getForeignKeyForColumn(tableName, columnName);
if (fk != null) {
    // Build the full path to the referenced row including space pattern
    // e.g., "acme:employees/1056" not just "employees/1056"
    // Use retractPattern() to strip the wildcard from the pattern (acme:# -> acme:)
    final fURI referencedPath = this.space.pattern().retractPattern()
            .extend(fk.toTable())
            .extend(fkValue.toString());
    // Return auto_from instruction that will resolve lazily when accessed
    return auto_from_(referencedPath).tryToInst();
}
```

**Key Details:**
- Uses `space.pattern().retractPattern()` to get the pattern prefix without the wildcard (e.g., `acme:#` → `acme:`)
- Extends with table name and row ID to build full path (e.g., `acme:employees/1056`)
- Returns `auto_from` instruction using `auto_from_(furi).tryToInst()`
- The `!*` syntax in output shows the auto_from instruction (e.g., `!*acme:employees/1056`)
- `!` is sugar for `auto`, `*` is sugar for `from`, so `!*` = `auto_from`

## Future Enhancements

This schema access implementation provides the foundation for:

1. **Type Validation**: Ensuring queries match table structures
2. **Query Optimization**: Using type information for better query planning
3. **Schema Evolution**: Tracking changes to table structures over time
4. **Composite Foreign Keys**: Support for multi-column foreign keys

## Testing

Run the schema access tests:

```bash
# Test basic schema access
mvn test -Dtest=tbleSpaceTest#testSQLSchemaAccess

# Test foreign key discovery
mvn test -Dtest=tbleSpaceTest#testForeignKeyDiscovery

# Test lazy FK resolution (prevents infinite recursion)
mvn test -Dtest=tbleSpaceTest#testLazyForeignKeyResolution

# Run all tbleSpace tests
mvn test -Dtest=tbleSpaceTest
```

All 146 tbleSpace tests pass with this implementation (141 original + 4 FK discovery tests + 1 lazy resolution test).

## Related Files

- `/src/main/java/studio/phaseshift/metatron/isa/tble/schema/domain/SQLSchemaGenerator.java` - Schema generation logic
- `/src/main/java/studio/phaseshift/metatron/isa/tble/schema/domain/ExistingTableSchema.java` - Table discovery and FK lazy resolution
- `/src/main/java/studio/phaseshift/metatron/isa/tble/tbleSpace.java` - Space integration
- `/src/test/java/studio/phaseshift/metatron/isa/tble/tbleSpaceTest.java` - Test coverage
- `/src/main/java/studio/phaseshift/metatron/isa/grph/tp3/space/grphSpace.java` - Graph schema reference

- `/src/main/java/studio/phaseshift/metatron/isa/m/type/Rec.java` - Shows how `at()` calls `autoResolve()`
- `/src/main/java/studio/phaseshift/metatron/isa/m/type/Obj.java` - Defines `autoResolve()` behavior
