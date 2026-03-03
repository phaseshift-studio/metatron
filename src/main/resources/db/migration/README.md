# Database Migration Scripts

This directory contains SQL migration scripts for tbleSpace schemas.

## Available Migrations

### V1__create_mqtt_indexed_objs_table.sql

Creates the MQTT-indexed `objs` table with virtual generated columns for efficient pattern matching.

**Compatible with:**
- MariaDB 10.2+
- MySQL 5.7+

**Features:**
- Virtual generated columns (seg1-seg5) auto-computed from furi
- Indexes on each segment for fast MQTT pattern queries
- Composite indexes for common multi-segment patterns
- UTF-8 MB4 character set for full Unicode support

## Running Migrations

### Manual Migration

```bash
# Connect to your database
mysql -u username -p database_name

# Run the migration
source src/main/resources/db/migration/V1__create_mqtt_indexed_objs_table.sql

# Verify table structure
DESCRIBE objs;

# Test segment generation
INSERT INTO objs (furi, obj) VALUES ('/sensor/kitchen/temperature', '{"value": 22.5}');
SELECT furi, seg1, seg2, seg3, seg4, seg5 FROM objs;
```

### Using Flyway (Optional)

If you want to use Flyway for migration management:

1. Add Flyway dependency to `pom.xml`:
```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <version>9.22.3</version>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
    <version>9.22.3</version>
</dependency>
```

2. Configure Flyway in your application:
```java
Flyway flyway = Flyway.configure()
    .dataSource(jdbcUrl, username, password)
    .locations("classpath:db/migration")
    .load();
flyway.migrate();
```

3. Flyway will automatically detect and run `V1__*.sql` scripts.

## Migration Naming Convention

Flyway-compatible naming:
- `V{version}__{description}.sql` - Versioned migrations
- `R__{description}.sql` - Repeatable migrations

Example:
- `V1__create_mqtt_indexed_objs_table.sql`
- `V2__add_metadata_columns.sql`
- `R__refresh_views.sql`

## Testing Migrations

Before running in production:

1. **Test on a copy of production data**
```bash
# Create test database
mysqldump production_db > backup.sql
mysql test_db < backup.sql

# Run migration on test database
mysql test_db < V1__create_mqtt_indexed_objs_table.sql

# Verify data integrity
mysql test_db -e "SELECT COUNT(*) FROM objs;"
```

2. **Verify segment generation**
```sql
-- Check that segments are populated correctly
SELECT
    furi,
    seg1, seg2, seg3, seg4, seg5
FROM objs
LIMIT 100;

-- Test MQTT pattern queries
SELECT * FROM objs WHERE seg1 = 'sensor' AND seg3 = 'temperature';
SELECT * FROM objs WHERE seg1 = 'sensor';
```

3. **Check index usage**
```sql
-- Verify indexes are being used
EXPLAIN SELECT * FROM objs WHERE seg1 = 'sensor' AND seg3 = 'temperature';
```

## Rollback

If you need to rollback to the simple schema:

```sql
-- Backup data
CREATE TABLE objs_backup AS SELECT furi, obj FROM objs;

-- Drop MQTT-indexed table
DROP TABLE objs;

-- Create simple table
CREATE TABLE objs (
    furi VARCHAR(512) NOT NULL PRIMARY KEY,
    obj TEXT NOT NULL,
    INDEX idx_furi (furi)
) ENGINE=InnoDB;

-- Restore data
INSERT INTO objs (furi, obj) SELECT furi, obj FROM objs_backup;

-- Cleanup
DROP TABLE objs_backup;
```

## Notes

- **Virtual columns** are computed on-the-fly and don't consume storage
- **Indexes on virtual columns** are materialized and do consume storage
- **Character set**: UTF-8 MB4 supports full Unicode including emojis
- **Collation**: `utf8mb4_unicode_ci` for case-insensitive comparisons

## Troubleshooting

### Error: "Unknown column type 'VIRTUAL'"

**Solution**: Upgrade to MariaDB 10.2+ or MySQL 5.7+

### Error: "Key column 'seg1' doesn't exist in table"

**Solution**: Ensure you're using a database that supports indexes on generated columns (MariaDB 10.2+, MySQL 5.7+)

### Slow queries after migration

**Solution**: Run `ANALYZE TABLE objs;` to update index statistics

### Segments not populating

**Solution**: Check that fURIs are in the format `/segment1/segment2/...`
