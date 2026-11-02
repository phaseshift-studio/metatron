# tbleSpace MQTT-Indexed Schema

## Overview

The `tbleSpace` now supports **MQTT-style pattern matching** through a pluggable schema system. The default `MqttIndexedSchema` uses MariaDB/MySQL **generated columns** to decompose fURIs into path segments, enabling efficient indexed queries for MQTT wildcards.

## MQTT Wildcards

- **`+`** - Single-level wildcard (matches exactly one path segment)
- **`#`** - Multi-level wildcard (matches zero or more path segments, must be last)

## Architecture

### Schema Interface

All schemas implement the `TableSchema` interface:

```java
public interface TableSchema {
    void initialize(Connection conn) throws SQLException;
    int write(Connection conn, fURI furi, String objJson) throws SQLException;
    Iterator<FuriObjPair> read(Connection conn, fURI pattern) throws SQLException;
    int delete(Connection conn, fURI furi) throws SQLException;
    boolean supportsMqttPatterns();
    String version();
}
```

### Available Schemas

1. **MqttIndexedSchema** (default)
   - Uses virtual generated columns (seg1-seg5)
   - Supports MQTT pattern matching
   - Optimized for MariaDB/MySQL 10.2+
   - Indexes on each segment for fast queries

2. **SimpleSchema**
   - Basic furi/obj table
   - No MQTT pattern support
   - Good for small datasets or exact-match queries

## Database Schema

### MqttIndexedSchema Table Structure

```sql
CREATE TABLE objs (
    furi VARCHAR(512) NOT NULL PRIMARY KEY,
    obj TEXT NOT NULL,

    -- Virtual generated columns (auto-computed from furi)
    seg1 VARCHAR(128) AS (...) VIRTUAL,
    seg2 VARCHAR(128) AS (...) VIRTUAL,
    seg3 VARCHAR(128) AS (...) VIRTUAL,
    seg4 VARCHAR(128) AS (...) VIRTUAL,
    seg5 VARCHAR(128) AS (...) VIRTUAL,

    -- Indexes for fast pattern matching
    INDEX idx_seg1 (seg1),
    INDEX idx_seg2 (seg2),
    INDEX idx_seg3 (seg3),
    INDEX idx_seg4 (seg4),
    INDEX idx_seg5 (seg5),
    INDEX idx_seg1_seg2 (seg1, seg2),
    INDEX idx_seg1_seg2_seg3 (seg1, seg2, seg3)
) ENGINE=InnoDB;
```

### How It Works

1. **Write**: Insert fURI and JSON object
   - Segments are automatically computed by database
   - No Java code needed to populate segments

2. **Read with Pattern**: Query using indexed segments
   - `/sensor/+/temperature` → `WHERE seg1='sensor' AND seg3='temperature'`
   - `/sensor/#` → `WHERE seg1='sensor'`
   - `/sensor/kitchen/+` → `WHERE seg1='sensor' AND seg2='kitchen' AND seg4 IS NULL`

3. **Pattern Matching**: Java-side verification for complex patterns
   - Handles patterns beyond 5 segments
   - Validates MQTT wildcard semantics

## Usage Examples

### Configuration

```java
tbleSpace.of(
    rec(
        uri(PATTERN), uri("/db/#"),
        uri(HOST), uri("jdbc:mariadb://localhost:3306/metatron"),
        uri(DRIVER), uri("org.mariadb.jdbc.Driver"),
        uri(ROUTE), rec(uri("/db/*"), uri("/*"))
    ).jvm(),
    f("/sys/space/db")
);
```

### Writing Objects

```java
// Write sensor data
space.write(f("/sensor/kitchen/temperature"), jnt(22.5));
space.write(f("/sensor/bedroom/temperature"), jnt(20.1));
space.write(f("/sensor/kitchen/humidity"), jnt(45));
```

### Reading with MQTT Patterns

```java
// Read all temperature sensors: /sensor/+/temperature
Iterator<Tuple.Pair<fURI, Obj>> temps = space.directReader().apply(f("/sensor/+/temperature"));

// Read all sensor data: /sensor/#
Iterator<Tuple.Pair<fURI, Obj>> allSensors = space.directReader().apply(f("/sensor/#"));

// Read all kitchen sensors: /sensor/kitchen/+
Iterator<Tuple.Pair<fURI, Obj>> kitchen = space.directReader().apply(f("/sensor/kitchen/+"));
```

### Pattern Examples

| Pattern | Matches | Doesn't Match |
|---------|---------|---------------|
| `/sensor/+/temperature` | `/sensor/kitchen/temperature`<br>`/sensor/bedroom/temperature` | `/sensor/kitchen/humidity`<br>`/sensor/kitchen/temperature/raw` |
| `/sensor/#` | `/sensor/kitchen`<br>`/sensor/kitchen/temperature`<br>`/sensor/bedroom/temperature/raw` | `/actuator/kitchen/light` |
| `/sensor/kitchen/+` | `/sensor/kitchen/temperature`<br>`/sensor/kitchen/humidity` | `/sensor/bedroom/temperature`<br>`/sensor/kitchen/temperature/raw` |
| `/+/kitchen/+` | `/sensor/kitchen/temperature`<br>`/actuator/kitchen/light` | `/sensor/bedroom/temperature` |
| `/sensor/+/temperature/#` | `/sensor/kitchen/temperature`<br>`/sensor/kitchen/temperature/raw` | `/sensor/kitchen/humidity` |

## Performance

### Index Usage

The schema creates indexes on each segment column, enabling efficient queries:

```sql
-- Query: /sensor/+/temperature
-- Uses: idx_seg1_seg2_seg3 (composite index)
EXPLAIN SELECT * FROM objs
WHERE seg1 = 'sensor' AND seg3 = 'temperature' AND seg4 IS NULL;

-- Query: /sensor/#
-- Uses: idx_seg1 (single column index)
EXPLAIN SELECT * FROM objs WHERE seg1 = 'sensor';
```

### Limitations

- **5 segment limit**: Patterns beyond 5 segments fall back to full table scan with Java-side filtering
- **Virtual columns**: Slight overhead on INSERT/UPDATE (negligible in practice)
- **MariaDB/MySQL only**: Generated columns require MariaDB 10.2+ or MySQL 5.7+

## Migration

### From Simple Schema to MQTT-Indexed Schema

```sql
-- 1. Backup existing data
CREATE TABLE objs_backup AS SELECT * FROM objs;

-- 2. Drop old table
DROP TABLE objs;

-- 3. Run migration script
SOURCE src/main/resources/db/migration/V1__create_mqtt_indexed_objs_table.sql;

-- 4. Restore data (segments auto-populate)
INSERT INTO objs (furi, obj) SELECT furi, obj FROM objs_backup;

-- 5. Verify segments
SELECT furi, seg1, seg2, seg3, seg4, seg5 FROM objs LIMIT 10;

-- 6. Drop backup
DROP TABLE objs_backup;
```

## Custom Schemas

To implement a custom schema:

```java
public class MyCustomSchema implements TableSchema {
    @Override
    public void initialize(Connection conn) throws SQLException {
        // Create your custom table structure
    }

    @Override
    public int write(Connection conn, fURI furi, String objJson) throws SQLException {
        // Custom write logic
    }

    @Override
    public Iterator<FuriObjPair> read(Connection conn, fURI pattern) throws SQLException {
        // Custom read logic with pattern matching
    }

    @Override
    public int delete(Connection conn, fURI furi) throws SQLException {
        // Custom delete logic
    }

    @Override
    public boolean supportsMqttPatterns() {
        return true; // or false
    }
}
```

Then use it in tbleSpace:

```java
public tbleSpace(Connection sjvm, Map<Obj, Obj> config, fURI tid, fURI vid) {
    super(sjvm, config, tid, vid);
    this.schema = new MyCustomSchema(); // Use custom schema
    this.schema.initialize(sjvm);
}
```

## Testing

Run the test suite:

```bash
# Test MQTT pattern matching
mvn test -Dtest=MqttIndexedSchemaTest

# Test tbleSpace integration
mvn test -Dtest=tbleSpaceTest

# Run all tble tests
mvn test -Dtest=*tble*
```

## References

- MQTT 3.1.1 Specification: [Topic Names and Topic Filters](https://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html#_Toc398718106)
- MariaDB Generated Columns: [Virtual Columns](https://mariadb.com/kb/en/generated-columns/)
- MySQL Generated Columns: [Generated Column Indexes](https://dev.mysql.com/doc/refman/8.0/en/create-table-generated-columns.html)
