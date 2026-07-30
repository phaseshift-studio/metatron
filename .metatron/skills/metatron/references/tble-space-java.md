---
name: tble-space-java
description: Java-side tbleSpace architecture — relational-database space, dual-path reads (table-mapped + KV store), ExistingTableSchema lifecycle, SQL rewrite pushdown, schema generation, dialect handling, VID stamping
---

# tbleSpace (Java)

## Package map

```
isa/tble/tbleSpace.java              ← space implementation (Constructor, directReader, addQ, close)
isa/tble/tbleInstSet.java            ← instruction set + SQL rewrite registration
isa/tble/KVStoreUtil.java            ← key-value URI-to-SQL LIKE translation
isa/tble/schema/SQLRewriteUtils.java ← SQL condition formatting (equality, comparison, AND-joiner)
isa/tble/space/ExistingTableSchema.java  ← table metadata, ALTER TABLE, type coercion
isa/tble/space/SQLSchemaGenerator.java   ← generates per-database SQLSchemaInstSet
isa/tble/space/SQLSchemaInstSet.java     ← per-database schema instset (Types for table columns)
isa/tble/space/tbleIncrQ.java            ← DB-backed increment queue
isa/tble/schema/storage/TableSchema.java ← table-mapped read path
isa/tble/schema/storage/TypedKeyValueSchema.java  ← KV-store read path with type coercion
isa/tble/schema/storage/fURIAwareIndexedSchema.java ← URI-indexed KV reads
isa/tble/schema/storage/SimpleKeyValueSchema.java   ← raw KV reads
```

## Architecture overview

`tbleSpace` bridges metatron's URI-graph model to relational databases. It extends `AbstractSpace<Connection>` (JDBC
connection is the raw state) and implements `SchemaSpace`, giving it a schema-backed read path with metadata-driven type
coercion.

```
┌───────────────────────────────────────────────────┐
│                    tbleSpace                      │
│  extends AbstractSpace<Connection>                │
│  implements SchemaSpace                           │
├───────────────────────────────────────────────────┤
│  sjvm         → JDBC Connection                   │
│  backend      → "sqlite" | "postgresql" | "mysql" │
│  databaseName → catalog name                      │
│  schema       → TableSchema | TypedKeyValueSchema │
│  existingTableSchema → ExistingTableSchema        │
│  schemaGenerator     → SQLSchemaGenerator         │
│  schemaInstset       → SQLSchemaInstSet           │
│  routes              → URI-prefix routing map     │
├───────────────────────────────────────────────────┤
│  directReader:  dual-path (table-mapped + KV)     │
│  directWriter:  routed through schema             │
│  rewrites:      registered in tbleInstSet         │
└───────────────────────────────────────────────────┘
```

## Constructor and initialization

```java
public tbleSpace(final Connection sjvm, final Map<Obj, Obj> config,
                 final fURI tid, final fURI vid)
```

**Initialization sequence:**

1. **Store JDBC connection** as `sjvm` (raw state via `AbstractSpace<Connection>`)
2. **Extract backend name** from `DatabaseMetaData.getDatabaseProductName().toLowerCase()`
3. **Initialize schema**: introspects existing tables via `ExistingTableSchema`, builds `TableSchema` for known tables
   and `TypedKeyValueSchema` for KV paths
4. **Initialize table mapping**: scans `_mtron_meta` for TID entries, maps table names to their declared metatron types
5. **Generate schema instset**: `SQLSchemaGenerator.generateSchemaInstset()` creates a per-database `SQLSchemaInstSet`
   with `Type` objects for every table column, registered as a Router space for type resolution
6. **Wire SCHEMA**: stores the schema instset at `uri(SCHEMA)` on the space itself (`SchemaSpace.schema()` resolves from
   here)

```java
// Backend detection
public String backend() {
    return this.backend;
}

public boolean isSqlite() {
    return this.backend != null && this.backend.contains("sqlite");
}
```

## Dual-path read: table-mapped vs key-value

`directReader()` is the heart of tbleSpace. It dispatches reads to one of two paths based on the URI:

```
read(pattern)
    │
    ├─ URI matches a known table?  ──→  TableSchema.read()
    │   (collection name in existingTableSchema.getTableNames())
    │   • Column-level type coercion via ExistingTableSchema
    │   • VID stamping from primary key columns
    │   • Poly unrolling for wildcard patterns
    │
    └─ URI doesn't match any table?  ──→  TypedKeyValueSchema.read()
        (or SimpleKeyValueSchema)
        • fURIAwareIndexedSchema for URI-prefix matching
        • Translate URI patterns to SQL LIKE clauses via KVStoreUtil
        • Deserialize JSON from kv_store.obj column
```

**Key difference:** table-mapped reads return rows as `Rec` objects with typed columns. KV-store reads return rows
serialized as JSON blobs, deserialized on read.

**Table guard predicate** (`tableGuard` in `tbleInstSet`):

```java
final BiPredicate<tbleSpace, List<Inst>> tableGuard = (space, matches) -> {
    final Obj ref = matches.getFirst().arg(0);
    if (!ref.isUri()) return true;
    final fURI resolved = space.redirect(ref.uriValue(), true);
    final DataPath dp = DataPath.withoutDB(resolved);
    if (!dp.hasCollection() || dp.collectionIsWildcard()) return false;
    return space.existingTableSchema != null
            && space.existingTableSchema.getTableNames()
            .contains(dp.collection().toLowerCase());
};
```

Guards SQL rewrite pushdown: only fires when the URI's collection name matches an actual table in the database.

## ExistingTableSchema

Manages table metadata discovered at construction time. Key capabilities:

- **`getTableNames()`** — all table names in the database
- **`getColumnTypes(tableName)`** — column name → metatron type URI (from `_mtron_meta`)
- **`coerceRow(tableName, rawRow)`** — apply column types to a raw JDBC row
- **`addColumnOnTheFly(tableName, columnName, value)`** — `ALTER TABLE ADD COLUMN` when a write introduces a new field
  (race-safe: falls back to `DatabaseMetaData` re-read on concurrent column add)

Column types are stored in `_mtron_meta` as `$table` sentinel rows. When a table has no meta row, the schema generator
infers types from JDBC metadata.

## SQL rewrite pushdown

Registered in `tbleInstSet.setup()` under `uri(REWRITE)`. All rewrites follow the `RewriteBuilder` pattern (space-aware
native pushdown).

| Rewrite                  | Match pattern                  | SQL generated                                                |
|--------------------------|--------------------------------|--------------------------------------------------------------|
| `sql_count`              | `from(uri).count()`            | `SELECT COUNT(*) FROM table`                                 |
| `sql_sum`                | `from(uri).sum()`              | `SELECT SUM(column) FROM table`                              |
| `sql_mean`               | `from(uri).mean()`             | `SELECT AVG(column) FROM table`                              |
| `sql_limit`              | `from(uri).take(n)`            | `SELECT * FROM table LIMIT n`                                |
| `sql_offset`             | `from(uri).skip(n)`            | `SELECT * FROM table OFFSET n`                               |
| `sql_offset_limit`       | `sql_offset.take(m)`           | `SELECT * FROM table LIMIT m OFFSET n`                       |
| `sql_has`                | `from(uri).has()`              | `SELECT EXISTS(SELECT 1 FROM table LIMIT 1)`                 |
| `sql_where`              | `from(uri).where(pred)`        | `SELECT * FROM table WHERE conditions`                       |
| `sql_where_count`        | `sql_where.count()`            | `SELECT COUNT(*) FROM table WHERE conditions`                |
| `sql_where_limit`        | `sql_where.take(n)`            | `SELECT * FROM table WHERE conditions LIMIT n`               |
| `sql_where_offset`       | `sql_where.skip(n)`            | `SELECT * FROM table WHERE conditions OFFSET n`              |
| `sql_where_offset_limit` | `sql_where_offset.take(m)`     | `SELECT * FROM table WHERE conditions LIMIT m OFFSET n`      |
| `sql_order`              | `from(uri).order(select(col))` | `SELECT * FROM table ORDER BY col1, col2`                    |
| `sql_distinct`           | `from(uri).dedup(select(col))` | `SELECT DISTINCT col1, col2 FROM table`                      |
| `sql_select`             | `from(uri)>>{cols}`            | `SELECT col1, col2 FROM table`                               |
| `kv_count`               | `from(kvPattern).count()`      | `SELECT COUNT(*) FROM kv_store WHERE furi LIKE ...`          |
| `kv_limit`               | `from(kvPattern).take(n)`      | `SELECT furi, obj FROM kv_store WHERE furi LIKE ... LIMIT n` |

**Composition:** Multiple rewrites can chain via the `Code.rewrite()` fixed-point loop. Simple example —
`from(uri).skip(100).take(10)` fires two rewrites in sequence:

1. `sql_offset`: `from(uri).skip(100)` → `sql_offset(uri, 100)`
2. `sql_offset_limit`: `sql_offset(uri, 100).take(10)` → `sql_offset_limit(uri, 100, 10)`

Result: a single `SELECT * FROM table LIMIT 10 OFFSET 100`.

A more complex chain — `from(uri).where(pred).skip(n).take(m)` fires three rewrites:

1. `sql_where`: `from(uri).where(pred)` → `sql_where(uri, pred)`
2. `sql_where_offset`: `sql_where(uri, pred).skip(n)` → `sql_where_offset(uri, pred, n)`
3. `sql_where_offset_limit`: `sql_where_offset(uri, pred, n).take(m)` → `sql_where_offset_limit(uri, pred, n, m)`

Result: a single `SELECT * FROM table WHERE pred LIMIT m OFFSET n`.

### Dialect handling

The standalone `sql_offset` and `sql_where_offset` rewrites need per-backend SQL because
`OFFSET` without `LIMIT` is dialect-specific:

```java
if(space.isSqlite()){
sql ="SELECT * FROM "+tableName +" LIMIT -1 OFFSET "+skip;
}else if(space.

backend() !=null
        &&(space.

backend().

contains("mysql") ||space.

backend().

contains("mariadb"))){
sql ="SELECT * FROM "+tableName
        +" LIMIT 18446744073709551615 OFFSET "+skip;  // max BIGINT UNSIGNED
}else{
sql ="SELECT * FROM "+tableName +" OFFSET "+skip;  // PostgreSQL
}
```

The combined `sql_offset_limit`, `sql_where_offset_limit` rewrites don't need dialect handling —
`LIMIT m OFFSET n` is standard SQL across all backends.

### How order/dedup extract columns

Both `order()` and `dedup()` take a column spec wrapped in a `select()` (`==`) or `rshift()` (`>>`)
instruction. The `OrderRewriteBuilder` and `DedupRewriteBuilder` unwrap the wrapper and extract column names via
`CommonRewrites.extractColumnNames()`, which handles all legal mtron forms (in desugared and sugar notation):

| mtron (desugared)                 | mtron (sugar)               | extracted columns |
|-----------------------------------|-----------------------------|-------------------|
| `order(select(name))`             | `order(==name)`             | `["name"]`        |
| `order(select([name=>_,age=>_]))` | `order(==[name=>_,age=>_])` | `["name", "age"]` |
| `order(rshift(name))`             | `order(>>name)`             | `["name"]`        |
| `order(rshift({name, age}))`      | `order(>>{name,age})`       | `["name", "age"]` |
| `dedup(select(name))`             | `dedup(==name)`             | `["name"]`        |
| `dedup(select([name=>_,age=>_]))` | `dedup(==[name=>_,age=>_])` | `["name", "age"]` |
| `dedup(rshift(name))`             | `dedup(>>name)`             | `["name"]`        |
| `dedup(rshift({name, age}))`      | `dedup(>>{name,age})`       | `["name", "age"]` |

### DISTINCT return types

Single-column `dedup(select(name))` returns scalar values (strings or numbers). Multi-column
`dedup(select([name=>_, value=>_]))` returns `Rec` objects with column-name keys.

### VID stamping

Every row-returning SQL rewrite stamps routable VIDs so the Router can locate the space for subsequent operations. The
`sql_distinct` rewrite is the exception — it returns scalar values or lightweight recs, not full database rows with
VIDs.

```java
// Discover primary key columns
final DatabaseMetaData dbMeta = space.sjvm().getMetaData();
final List<String> pkColumns = new ArrayList<>();
try(
final ResultSet pkRs = dbMeta.getPrimaryKeys(null, null, tableName)){
        while(pkRs.

next())pkColumns.

add(pkRs.getString("COLUMN_NAME"));
        }

// Stamp VID on each row
        while(rs.

next()){
final Rec rawRow = ObjSQLSerializer.readCurrentAsRec(rs);
final fURI rowVID = Space.Helper.routeToSpace(
        pkColumns.isEmpty()
                ? space.vid().extend(tableName).extend(rawRow.at(uri("id")).toString())
                : pkColumns.stream()
                .map(col -> rawRow.at(uri(col)).toString())
                .reduce(space.vid().extend(tableName),
                        (vid, seg) -> vid.extend(seg), (a, b) -> b),
        space.routes());
    rows.

append(rawRow.selfVID(rowVID));
        }
```

## SQLSchemaInstSet

A per-database `InstSet` that holds `Type` objects for every table column. Generated by
`SQLSchemaGenerator.generateSchemaInstset()` during construction:

```java
// VID is in /m/ namespace → writes route to memSpace, not back into tbleSpace
public SQLSchemaInstSet(final fURI schemaVid, final Collection<Type> types) {
    super(mutableMap(
            uri(PATTERN), uri(schemaVid.extend(ALL)),
            uri(TYPE), lst(types.stream().map(t -> (Obj) t).toList())
    ), INSTSET_TID, schemaVid);
}
```

The generated Types encode column-level metatron types (int, str, bool, etc.) and foreign key references in their
`isaPredicate`. The schema instset is registered as a Router space so type resolution can find it.

## KVStoreUtil

Translates URI patterns into SQL `LIKE` clauses for the key-value storage path:

```java
// URI: /col/user123/profile/email
// → WHERE furi LIKE '/col/user123/profile/email' OR furi LIKE '/col/user123/profile/email/%'
public static String translateKVPatternToSQL(final fURI stored)
```

Handles wildcard patterns (`+`, `#`), exact matches, and hierarchical prefix matching.

## SQLRewriteUtils

SQL-specific formatting for predicate translation. Used by `sql_where`:

```java
public static final CommonRewrites.ConditionFormatter CONDITION_FORMATTER =
        new CommonRewrites.ConditionFormatter() {
            @Override
            public String equality(String field, String value) {
                return field + " = " + value;
            }

            @Override
            public String comparison(String field, ComparisonOp op, String value) {
                return field + " " + op.symbol() + " " + value;
            }

            @Override
            public String exists(String field) {
                return field + " IS NOT NULL";
            }

            @Override
            public String escapeLiteral(String s) {
                return s.replace("'", "''");
            }
        };

public static final CommonRewrites.PredicateJoiner PREDICATE_JOINER =
        conds -> String.join(" AND ", conds);
```

## Increment queue (tbleIncrQ)

Replaces the default in-memory `?incrq` with a database-backed implementation:

```java

@Override
public Space addQ(final QProc qProc) {
    final QProc toAdd = qProc.pattern().equals(QCollection.INCRQ_PATTERN)
            ? new tbleIncrQ(this) : qProc;
    // ... register in QPROC list
}
```

Uses `RETURN_GENERATED_KEYS` for auto-increment — the database assigns the VID, which propagates back through the
`?incrq` instruction.

## Close lifecycle

```java

@Override
public void close() {
    try {
        this.sjvm().close();     // close JDBC connection
        SchemaSpace.super.close(); // close schema instset
    } catch (final Exception e) {
        LOG.error(e);
    } finally {
        super.close();           // remove from Router, close QProcs
    }
}
```

`SchemaSpace.super.close()` handles closing the schema instset and cleaning up Router registrations.

## Test infrastructure

```
AbstractTbleSpaceTest           ← shared test base (schema setup, rewrite tests)
├── PostgreSQLTbleSpaceTest     ← PostgreSQL backend
├── MariaDBTbleSpaceTest         ← MariaDB backend
├── MySQLTbleSpaceTest           ← MySQL backend
└── SqliteTbleSpaceTest          ← SQLite backend (in-memory)
```

Each backend provides a `DatabaseConfig` with DDL templates, boolean literal representation, and connection setup. Tests
use `@ParameterizedTest` with a static `@BeforeAll` that seeds 10 rows into `rewrite_test(id, value, name, active)` plus
additional tables (`users`, `products`, `companies`, `people`).

## Foreign key inference

`ExistingTableSchema` infers foreign key relationships through three mechanisms and wraps FK column values
in `auto_from_()` on read so they resolve lazily to the referenced row.

### Discovery mechanisms

1. **JDBC metadata** (`discoverReferencesAndRegister`): reads `DatabaseMetaData.getImportedKeys()` for SQL-level
   `REFERENCES` constraints.
2. **`_mtron_meta` persistence** (`loadMetaTable`): reads FK registrations persisted by `persistColumnType()`.
   Survives restarts.
3. **Naming convention** (`discoverFKByConvention` + `inferFKByConvention`): a column whose name matches another
   table name (case-insensitive, with or without trailing "s") is treated as a FK.  Examples:
   - `people.company` → table `companies`
   - `message.session` → table `session`

The lazy path (`inferFKByConvention`) fires at **read time** — if a column hasn't been registered as a FK yet,
it checks naming convention on the first read and registers the FK permanently.

### FK target path

`FKTarget.targetPath` is `"tableName/+/columnName"` — e.g. `"session/+/id"`.  `buildFKReferencePath()` strips the
`/+columnName` suffix to get the target table, then prepends the space's pattern to build the full reference path.

### Path construction and the scheme-vs-path trap

`buildFKReferencePath()` constructs the FK reference path by prepending `space.pattern().retractPattern()` (the
space's base path) to the target table name and row ID:

```java
// For space pattern </usr/dr/#>:
this.space.pattern().retractPattern()  // → /usr/dr/
    .extend(refTable)                   // → /usr/dr/session
    .extend(rowId);                     // → /usr/dr/session/1   ← correct when rowId is bare PK
```

**Critical distinction**: scheme-based patterns (`drdb:#`) vs path-based patterns (`/usr/dr/#`):

| Pattern | Bare PK `1` → FK path | Absolute value `/usr/dr/session/1` in column | Problem |
|---------|----------------------|---------------------------------------------|---------|
| `drdb:#` | `drdb:session/1` | `drdb:session/1` (scheme prefix distinguishes) | None — easy to distinguish |
| `/usr/dr/#` | `/usr/dr/session/1` | `/usr/dr/session//usr/dr/session/1` | **Path doubling** — can't tell bare PK from absolute path |

When callers store full URIs (e.g. `uri(/usr/dr/session/1)`) in FK columns instead of bare primary keys (e.g. `1`),
the path-based pattern can't distinguish them — both look like path segments.

### `readColumnWithMetadata` guards

Three guards prevent FK miswrapping in `readColumnWithMetadata()`:

| Guard | Value example | Action | Why |
|-------|-------------|--------|-----|
| Starts with `/` | `/usr/dr/session/1` | Return plain `uri()` — no FK wrapping | Caller stored a full URI, not a bare PK |
| Contains `:` | `drdb:session/1` | Return plain `uri()` — no FK wrapping | Scheme-based absolute path |
| Starts with `[` | `[!*/usr/dr/msg/4,...]` | Skip FK, fall through to type reader | JSON list of references, not a single FK |

Without these guards, three bugs occur:
1. Absolute paths get doubled (`/usr/dr/session//usr/dr/session/1`)
2. Plain URIs get wrapped in unnecessary `auto_from_` (`session=>!*/usr/dr/session/1` instead of `session=>/usr/dr/session/1`)
3. JSON lists get treated as single FK path segments (`!*/usr/dr/message/[...]`)

### FK persistence in `_mtron_meta`

`persistColumnType()` writes FK info to `_mtron_meta.ref_table`. When reading rows, `processMetaRow()` calls
`registerOrPendingFK()` which registers with `SQLSchemaGenerator.registerFK()`. The `ref_table` column stores
only the target table name (e.g. `"session"`), not a full path — `buildFKReferencePath()` reconstructs the path
at read time.

### When to avoid FK columns

If a column stores a **list** of references (e.g. `message => [!*/usr/dr/msg/1, !*/usr/dr/msg/2]`), the column
should NOT have a FK inferred. The naming convention may still match (e.g. column `message` matches table `message`),
but the `[` guard in `readColumnWithMetadata` prevents miswrapping. For new schemas, consider naming list-of-reference
columns differently from their target tables to avoid the convention match entirely.

## See also

- `rewrite-system-java.md` — RewriteBuilder, Rewriter, Code.rewrite () loop
- `type-system-java.md` — Type interface, MType, Fluent API
- `mtron-skill-reference.md` — mtron expression syntax (from, skip, take, where, count)
