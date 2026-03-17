# SQL Schema Access Implementation

## Overview

This document describes the implementation of SQL schema access in metatron's `tbleSpace`, which allows users to inspect SQL table structures as mtron types before querying data. This feature mirrors the schema access functionality available in `tp3Space` for graph databases.

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

| Aspect | Graph Schema (tp3Space) | SQL Schema (tbleSpace) |
|--------|------------------------|------------------------|
| **Type Source** | Manually defined in schema classes | Auto-generated from SQL metadata |
| **Structure** | Vertex and edge types | Table row types |
| **Type Format** | Custom vertex/edge types | `rrow::T[isa([...])]` |
| **Discovery** | Predefined schema classes | Database introspection via JDBC |
| **Flexibility** | Can define custom properties | Limited to SQL column types |

## Future Enhancements

This schema access implementation provides the foundation for:

1. **Foreign Key Traversal**: Using schema information to navigate relationships between tables
2. **Type Validation**: Ensuring queries match table structures
3. **Query Optimization**: Using type information for better query planning
4. **Schema Evolution**: Tracking changes to table structures over time

## Testing

Run the schema access test:

```bash
mvn test -Dtest=tbleSpaceTest#testSQLSchemaAccess
```

All 141 tbleSpace tests pass with this implementation.

## Related Files

- `/src/main/java/studio/phaseshift/metatron/isa/tble/schema/SQLSchemaGenerator.java` - Schema generation logic
- `/src/main/java/studio/phaseshift/metatron/isa/tble/tbleSpace.java` - Space integration
- `/src/test/java/studio/phaseshift/metatron/isa/tble/tbleSpaceTest.java` - Test coverage
- `/src/main/java/studio/phaseshift/metatron/isa/grph/tp3/space/tp3Space.java` - Graph schema reference
