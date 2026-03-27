# Metatron JDBC Driver

A read-only JDBC driver for browsing metatron spaces in database tools like IntelliJ IDEA Database Tools.

## Architecture Note

The JDBC driver uses `ObjSimpleJSONSerializer` for client-server communication because it can be initialized
in a "cold start" scenario (when loaded by external tools like IntelliJ, DBeaver, etc.) without requiring
the full metatron system to be bootstrapped.

`ObjCleanStringSerializer` cannot be used here because it has deep dependencies on the mtron type system
(Obj, Rec, Lst, etc.) which require Router initialization and type system bootstrap. These dependencies
are triggered even by method signatures, causing `ExceptionInInitializerError` when the JDBC driver is
loaded externally.

Once data reaches the metatron server, `tbleSpace` uses `ObjCleanStringSerializer` for database storage,
which provides faithful mtron syntax representation and proper round-tripping.

## Features

- ✅ Read-only access to metatron spaces
- ✅ Simple SQL query support
- ✅ Automatic flattening of nested mtron objects
- ✅ IntelliJ Database Tools compatible
- ❌ Write operations (not yet supported)
- ❌ Complex SQL (joins, aggregations, etc.)

## Connection URL Format

```
jdbc:metatron://host:port/space
```

**Examples:**
```
jdbc:metatron://localhost:7777/acme
jdbc:metatron://localhost:7777/tble
```

## Supported SQL Queries

### 1. Select All from Table
```sql
SELECT * FROM customers
```
Translates to: `*acme:customers/+`

### 2. Select by ID
```sql
SELECT * FROM customers WHERE id = 357
```
Translates to: `*acme:customers/357`

### 3. Select Specific Field
```sql
SELECT name FROM customers
```
Translates to: `*acme:customers/+/name`

### 4. Select Field by ID
```sql
SELECT name FROM customers WHERE id = 357
```
Translates to: `*acme:customers/357/name`

### 5. Raw Metatron Pattern
```sql
*acme:customers/+/address/city
```
You can use raw metatron patterns directly (without the leading `*`)

## Setup in IntelliJ IDEA

### 1. Build the Driver JAR

```bash
mvn clean package
```

This creates `target/metatron-0.1-SNAPSHOT.jar` with the JDBC driver included.

### 2. Add Data Source in IntelliJ

1. Open **Database** tool window (View → Tool Windows → Database)
2. Click **+** → **Data Source** → **Other** → **Generic**
3. Configure:
   - **Name**: Metatron - acme
   - **Driver**: Click **+** to add new driver
     - **Name**: Metatron
     - **Driver Files**: Add `metatron-0.1-SNAPSHOT.jar`
     - **Class**: `studio.phaseshift.metatron.isa.mach.io.jdbc.MetatronDriver`
   - **URL**: `jdbc:metatron://localhost:7777/acme`
   - **User**: (leave empty)
   - **Password**: (leave empty)
4. Click **Test Connection**
5. Click **OK**

### 3. Browse Data

Once connected, you can:
- Browse tables (top-level paths in the space)
- Run SQL queries in the console
- View data in table format
- Export data

## How It Works

### Data Flattening

Nested mtron objects are automatically flattened into rows:

**Mtron Object:**
```mtron
[name=><Alice>, age=>30, address=>[city=><NYC>, zip=>10001]]
```

**Flattened to SQL Row:**
| furi | name | age | address.city | address.zip |
|------|------|-----|--------------|-------------|
| acme:customers/357 | Alice | 30 | NYC | 10001 |

### Pattern Translation

The driver translates simple SQL to metatron patterns:

| SQL | Metatron Pattern |
|-----|------------------|
| `SELECT * FROM customers` | `acme:customers/+` |
| `SELECT * FROM customers WHERE id=357` | `acme:customers/357` |
| `SELECT name FROM customers` | `acme:customers/+/name` |
| `SELECT name FROM customers WHERE id=357` | `acme:customers/357/name` |

## Limitations

### Current Version (1.0)

- **Read-only**: No INSERT, UPDATE, DELETE support
- **Simple queries only**: No JOINs, GROUP BY, ORDER BY, etc.
- **Single table**: No multi-table queries
- **No schema introspection**: Tables/columns not auto-discovered yet
- **Forward-only cursors**: No scrolling through results

### Future Enhancements

- [ ] Write support (INSERT, UPDATE, DELETE)
- [ ] Schema introspection (auto-discover tables/columns)
- [ ] More complex SQL support
- [ ] Better type mapping
- [ ] Connection pooling
- [ ] Remote router connections (currently uses local Router.the())

## Architecture

```
IntelliJ Database Tool
    ↓ (JDBC API)
MetatronDriver
    ↓ (translates SQL to patterns)
MetatronStatement
    ↓ (executes via router)
Router.the()
    ↓ (routes to appropriate space)
tbleSpace, memSpace, etc.
    ↓ (returns Iterator<IdObj>)
MetatronResultSet
    ↓ (flattens to rows)
IntelliJ Table View
```

## Classes

- **MetatronDriver**: JDBC Driver implementation, handles connection URLs
- **MetatronConnection**: Connection to metatron router
- **MetatronStatement**: Executes queries, translates SQL to patterns
- **MetatronResultSet**: Wraps query results, flattens nested objects
- **MetatronResultSetMetaData**: Column metadata
- **MetatronDatabaseMetaData**: Database metadata (minimal implementation)

## Example Usage (Programmatic)

```java
// Load driver (auto-loaded via ServiceLoader)
Class.forName("studio.phaseshift.metatron.isa.mach.io.jdbc.MetatronDriver");

// Connect
Connection conn = DriverManager.getConnection(
    "jdbc:metatron://localhost:7777/acme"
);

// Query
Statement stmt = conn.createStatement();
ResultSet rs = stmt.executeQuery("SELECT * FROM customers WHERE id = 357");

// Process results
while (rs.next()) {
    String name = rs.getString("name");
    int age = rs.getInt("age");
    System.out.println(name + " is " + age + " years old");
}

// Cleanup
rs.close();
stmt.close();
conn.close();
```

## Testing

The driver connects to the local metatron router via `Router.the()`. Make sure:
1. Metatron is running
2. The space you're connecting to exists
3. The space has data to query

## Contributing

This is a minimal read-only implementation. Contributions welcome for:
- Write support
- Schema introspection
- Complex SQL parsing
- Better error messages
- Performance optimizations

## License

Same as metatron project.
