# Metatron JDBC Driver - Implementation Notes

## What Was Built

A **read-only JDBC driver** for browsing metatron spaces in database tools like IntelliJ IDEA.

## Files Created

1. **MetatronDriver.java** - JDBC Driver entry point, handles connection URLs
2. **MetatronConnection.java** - Connection to metatron router
3. **MetatronStatement.java** - Executes queries, translates SQL to metatron patterns
4. **MetatronResultSet.java** - Wraps results, flattens nested objects to rows
5. **MetatronResultSetMetaData.java** - Column metadata
6. **MetatronDatabaseMetaData.java** - Database metadata (minimal)
7. **META-INF/services/java.sql.Driver** - Service provider configuration
8. **README.md** - Usage documentation

## Key Design Decisions

### 1. Router Integration
Uses `Router.the()` to access the local metatron router. This means:
- ✅ No network protocol needed for MVP
- ✅ Direct access to all spaces
- ❌ Only works with local router (future: remote connections)

### 2. SQL Translation
Simple regex-based SQL parser that handles:
- `SELECT * FROM table` → `space:table/+`
- `SELECT * FROM table WHERE id=X` → `space:table/X`
- `SELECT field FROM table` → `space:table/+/field`
- Raw patterns: `*space:table/+` (direct passthrough)

### 3. Data Flattening
Nested mtron objects are flattened to SQL rows using dot notation:
```
[name=><Alice>, address=>[city=><NYC>]]
→ columns: furi, name, address.city
```

### 4. Type Conversion
Mtron types → Java types via `.jvm()` method:
- `Int` → `Long`
- `Real` → `Double`
- `Bool` → `Boolean`
- `Str` → `String`
- `Uri` → `String`
- `Poly` → `String` (mtron representation)

## Current Limitations

1. **Read-only** - No INSERT/UPDATE/DELETE
2. **Simple SQL only** - No JOINs, GROUP BY, ORDER BY
3. **No schema introspection** - Can't auto-discover tables/columns yet
4. **Forward-only cursors** - No scrolling
5. **Local router only** - No remote connections

## Testing

To test the driver:

```java
// Load driver
Class.forName("studio.phaseshift.metatron.isa.mach.io.jdbc.MetatronDriver");

// Connect
Connection conn = DriverManager.getConnection("jdbc:metatron://localhost:7777/acme");

// Query
Statement stmt = conn.createStatement();
ResultSet rs = stmt.executeQuery("SELECT * FROM customers WHERE id = 357");

// Process
while (rs.next()) {
    System.out.println(rs.getString("name"));
}
```

## IntelliJ Setup

1. Build: `mvn clean package`
2. Add Data Source → Generic
3. Driver: Add `metatron-0.1-SNAPSHOT.jar`
4. Class: `studio.phaseshift.metatron.isa.mach.io.jdbc.MetatronDriver`
5. URL: `jdbc:metatron://localhost:7777/acme`

## Future Enhancements

### Phase 2 - Write Support
- Implement `executeUpdate()` for INSERT/UPDATE/DELETE
- Translate to `router.write()` calls

### Phase 3 - Schema Introspection
- Implement `getTables()` - list top-level paths in space
- Implement `getColumns()` - introspect object structure
- Auto-discover schema from sample data

### Phase 4 - Advanced SQL
- Parse more complex SQL (JOINs, aggregations)
- Implement in-memory query processing
- Support ORDER BY, GROUP BY, LIMIT

### Phase 5 - Remote Connections
- Implement network protocol for remote router access
- Connection pooling
- Authentication/authorization

## Technical Notes

### Why Router.read() Returns Obj, Not Iterator<IdObj>

The router returns an `Obj` which could be:
- A single value
- A list of values
- A complex nested structure

The driver wraps results in `Space.IdObj` for the ResultSet to process.

### Why Flattening?

SQL tools expect flat rows with columns. Nested mtron objects need to be flattened:
- Records → dot-notation columns
- Lists → JSON-like strings (for now)

### Why Simple SQL Parser?

Full SQL parsing is complex. The regex-based approach:
- ✅ Handles 80% of use cases
- ✅ Easy to understand and maintain
- ✅ Can be enhanced incrementally
- ❌ Won't handle complex queries (yet)

## Compilation

All files compile successfully with no errors:
```
mvn compile
[INFO] BUILD SUCCESS
```

## Next Steps

1. **Test with real data** - Try connecting from IntelliJ
2. **Add schema introspection** - Implement getTables()/getColumns()
3. **Handle edge cases** - Test with various data types
4. **Add write support** - Implement executeUpdate()
5. **Document patterns** - Add more SQL→pattern examples

## Questions for User

1. Should we implement schema introspection next?
2. Do you want write support (INSERT/UPDATE/DELETE)?
3. Should we add support for more complex SQL patterns?
4. Do you need remote router connections?

---

**Status**: ✅ Compiles successfully, ready for testing!
