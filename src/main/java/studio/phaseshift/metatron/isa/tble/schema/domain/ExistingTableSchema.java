/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *  
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.tble.schema.domain;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSQLSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.tble.schema.storage.TableSchema;
import studio.phaseshift.metatron.isa.tble.tbleSpace;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.Tuple;

import java.sql.*;
import java.util.*;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Schema for mapping existing SQL tables to Metatron objects.
 * Discovers tables in the database and makes them accessible via fURIs.
 * <p>
 * Path format: /table_name/row_id[/field_name]
 * <p>
 * <b>Read operations:</b>
 * - /users/123 → reads row with primary key 123 from users table as a record
 * - /users/+ → reads all rows from users table
 * <p>
 * <b>Write operations:</b>
 * - /users/123 → [name=>marko,age=>29] (update/insert entire row)
 * - /users/123/name → marko (update single field)
 * <p>
 * SQL rows are converted to Metatron records where column names are keys.
 * Writes support both full row updates and individual field updates.
 * The Space.Helper.resolveWrite() method handles poly unrolling automatically.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ExistingTableSchema extends ObjSQLSerializer implements TableSchema {
    private final tbleSpace space;
    private final Map<String, TableMetadata> tableSchemas = new LinkedHashMap<>();
    private final String excludeTableName;

    /**
     * Metadata about a SQL table
     */
    public record TableMetadata(String dbName, String tableName, List<ColumnMetadata> columns,
                                List<String> primaryKeys, List<ForeignKeyMetadata> foreignKeys) {
    }

    /**
     * Metadata about a SQL column
     */
    public record ColumnMetadata(String name, int sqlType, String typeName) {
    }

    /**
     * Metadata about a foreign key relationship
     */
    public record ForeignKeyMetadata(String fromTable, String fromColumn,
                                     String toTable, String toColumn, String fkName) {
    }

    /**
     * Create a new ExistingTableSchema
     *
     * @param excludeTableName name of table to exclude from discovery (e.g., the key-value store table)
     */
    public ExistingTableSchema(final tbleSpace space, final String excludeTableName) {
        this.excludeTableName = excludeTableName;
        this.space = space;
    }

    @Override
    public void initialize(final Connection conn) throws SQLException {
        discoverTableSchemas(conn);
    }

    /**
     * Discover all tables in the database and their schemas
     */
    private void discoverTableSchemas(final Connection conn) throws SQLException {
        final DatabaseMetaData metaData = conn.getMetaData();
        final String catalog = conn.getCatalog();

        // Get all tables (excluding system tables)
        try (final ResultSet tables = metaData.getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                final String tableName = tables.getString("TABLE_NAME");
                // Skip the key-value store table
                if (tableName.equals(this.excludeTableName)) {
                    continue;
                }
                // Get columns for this table
                final List<ColumnMetadata> columns = new ArrayList<>();
                try (final ResultSet cols = metaData.getColumns(catalog, null, tableName, "%")) {
                    while (cols.next()) {
                        String columnName = cols.getString("COLUMN_NAME");
                        int sqlType = cols.getInt("DATA_TYPE");
                        String typeName = cols.getString("TYPE_NAME");
                        columns.add(new ColumnMetadata(columnName, sqlType, typeName));
                    }
                }
                // Get primary keys for this table
                final List<String> primaryKeys = new ArrayList<>();
                try (final ResultSet pks = metaData.getPrimaryKeys(catalog, null, tableName)) {
                    while (pks.next()) {
                        primaryKeys.add(pks.getString("COLUMN_NAME"));
                    }
                }

                // Get foreign keys for this table
                final List<ForeignKeyMetadata> foreignKeys = new ArrayList<>();
                try (final ResultSet fks = metaData.getImportedKeys(catalog, null, tableName)) {
                    while (fks.next()) {
                        String fkName = fks.getString("FK_NAME");
                        String fkColumnName = fks.getString("FKCOLUMN_NAME");
                        String pkTableName = fks.getString("PKTABLE_NAME");
                        String pkColumnName = fks.getString("PKCOLUMN_NAME");
                        foreignKeys.add(new ForeignKeyMetadata(
                                tableName, fkColumnName, pkTableName, pkColumnName, fkName
                        ));
                    }
                }

                this.tableSchemas.put(tableName.toLowerCase(), new TableMetadata(catalog, tableName, columns, primaryKeys, foreignKeys));
                this.space.logger().debug("discovered table: %s with %s columns, %s primary keys, and %s foreign keys",
                        tableName, columns.size(), primaryKeys.size(), foreignKeys.size());
            }
        }
        this.space.logger().info("discovered {{b}}%s{{X}} tables: %s", tableSchemas.size(), tableSchemas.keySet());
    }

    /**
     * Parse a fURI to extract table name and row identifier
     * Format: /table_name/row_id or /table_name/+ for all rows
     * Returns null if not a table path
     */
    private List<String> parseTablePath(final fURI furi) {
        // Use segments() to get only the named segments (no empty strings from slashes)
        final List<String> segments = furi.segments();
        if (segments.isEmpty())
            return null;
        // First segment should be the table name
        final String tableName = segments.getFirst();
        if (!this.tableSchemas.containsKey(tableName.toLowerCase()))
            return null;
        final List<String> tablePath = new ArrayList<>(segments);
        if (segments.size() == 1)
            tablePath.add("+");
        if (segments.size() == 2)
            tablePath.add("+");
        return tablePath;
    }

    /**
     * Read a row from a SQL table and convert it to a Metatron record
     */
    private Obj readTableRow(final ResultSet rs, final TableMetadata metadata, final String... rowNames) throws SQLException {
        final Map<Obj, Obj> labeledValues = new LinkedHashMap<>();
        for (final ColumnMetadata col : metadata.columns) {
            if (rowNames.length == 0 || Arrays.asList(rowNames).contains(col.name)) {
                final Obj value = readColumnWithMetadata(rs, col);
                labeledValues.put(uri(col.name), value);
                if (!value.isNoObj())
                    Router.global().stats().ioStats().incrBytesRecv(value.toString().getBytes().length);
            }
        }
        return rowNames.length == 1 ? objs(labeledValues.values()) : rec(labeledValues, REC_TID, null);
    }

    /**
     * Read a column value using metadata to handle type conversions properly.
     * This is especially important for SQLite which stores BOOLEAN as INTEGER.
     * Also handles foreign key traversal - if a column is a foreign key, returns the referenced row.
     */
    private Obj readColumnWithMetadata(final ResultSet rs, final ColumnMetadata col) throws SQLException {
        // Check if this column is a foreign key
        final TableMetadata currentTable = getCurrentTableMetadata(rs);
        if (currentTable != null) {
            final ForeignKeyMetadata fk = getForeignKeyForColumn(currentTable.tableName, col.name);
            if (fk != null) {
                // This is a foreign key - return an auto_from instruction for lazy resolution
                final Object fkValue = rs.getObject(col.name);
                if (fkValue != null && !rs.wasNull()) {
                    // Build the full path to the referenced row including space pattern
                    // e.g., "acme:employees/1056" not just "employees/1056"
                    // Use retractPattern() to strip the wildcard from the pattern (acme:# -> acme:)
                    final fURI referencedPath = this.space.pattern().retractPattern()
                            .extend(fk.toTable())
                            .extend(fkValue.toString());
                    // Return auto_from instruction that will resolve lazily when accessed
                    return auto_from_(referencedPath).tryToInst();
                }
                // FK value is null, return noobj
                return noobj();
            }
        }

        // Check if this is a BOOLEAN column that SQLite reports as INTEGER
        if ("BOOLEAN".equalsIgnoreCase(col.typeName) &&
                (col.sqlType == Types.INTEGER || col.sqlType == Types.TINYINT ||
                        col.sqlType == Types.SMALLINT || col.sqlType == Types.BIT)) {
            final Object value = rs.getObject(col.name);
            if (value == null || rs.wasNull()) {
                return noobj();
            }
            // Convert 0/1 to boolean
            final int intValue = rs.getInt(col.name);
            return studio.phaseshift.metatron.isa.m.type.impl.MBool.bool(intValue != 0);
        }
        // Use standard column reading for other types
        return readColumn(rs, col.name, col.sqlType);
    }

    /**
     * Get the table metadata for the current ResultSet
     */
    private TableMetadata getCurrentTableMetadata(final ResultSet rs) throws SQLException {
        try {
            final String tableName = rs.getMetaData().getTableName(1);
            if (tableName != null && !tableName.isEmpty()) {
                return tableSchemas.get(tableName.toLowerCase());
            }
        } catch (SQLException e) {
            // Some JDBC drivers don't support getTableName, ignore
        }
        return null;
    }


    /**
     * Build a row identifier from primary keys or row number
     */
    private String buildRowId(final ResultSet rs, final TableMetadata metadata) throws SQLException {
        if (!metadata.primaryKeys.isEmpty()) {
            // Use primary key value(s)
            final StringBuilder id = new StringBuilder();
            for (int i = 0; i < metadata.primaryKeys.size(); i++) {
                if (i > 0) id.append("_");
                final Object value = rs.getObject(metadata.primaryKeys.get(i));
                id.append(value != null ? value.toString() : "null");
            }
            return id.toString();
        } else {
            // Use row number as fallback
            return String.valueOf(rs.getRow());
        }
    }

    @Override
    public int write(final Connection conn, final fURI furi, final String objJson) throws SQLException {
        // Parse the object from JSON
        final Obj obj = objJson == null ? noobj() : ObjSimpleJSONSerializer.parse(objJson);
        return write(conn, furi, obj);
    }

    /**
     * Write an Obj directly to the database without JSON serialization
     */
    public int write(final Connection conn, final fURI furi, final Obj obj) throws SQLException {
        final List<String> tablePath = parseTablePath(furi.asNode());
        if (tablePath == null) {
            throw new SQLException("invalid table path: " + furi);
        }

        final String tableName = tablePath.getFirst();
        final String rowId = tablePath.get(1);
        final TableMetadata metadata = tableSchemas.get(tableName.toLowerCase());

        if (metadata == null) {
            throw new SQLException("table not found: " + tableName);
        }

        if (rowId == null || rowId.equals("+")) {
            throw new SQLException("cannot write without specific row ID: " + furi);
        }

        if (metadata.primaryKeys.isEmpty()) {
            throw new SQLException("table " + tableName + " has no primary key, cannot write");
        }

        // Check if this is a field-level write (e.g., /table/123/name)
        final List<String> segments = furi.segments();
        if (segments.size() > 2) {
            // Field-level write: /table/rowId/fieldName
            final String fieldName = segments.get(2);
            return writeField(conn, metadata, rowId, fieldName, obj);
        } else {
            // Row-level write: /table/rowId
            if (obj.isRec()) {
                // Record with named fields (keys are column names)
                return writeRow(conn, metadata, rowId, obj.asRec());
            } else if (obj.isLst()) {
                // List with positional values (indices correspond to column order)
                return writeRowFromList(conn, metadata, rowId, obj.asLst());
            } else {
                throw new SQLException("expected record or list for row write, got: " + obj.tid());
            }
        }
    }

    /**
     * Write an entire row from a list (positional values matching column order).
     * <p>
     * The list values correspond to ALL columns in their natural order (as returned by the database).
     * This allows working with existing tables created by other programs.
     * <p>
     * If the list includes the primary key value(s), they will be used.
     * If the list is shorter than the number of columns, remaining columns will not be updated.
     */
    private int writeRowFromList(final Connection conn, final TableMetadata metadata, final String rowId,
                                 final studio.phaseshift.metatron.isa.m.type.Lst lst) throws SQLException {
        // Convert list to record using column names as keys
        final Map<Obj, Obj> recMap = new LinkedHashMap<>();
        final List<Obj> values = lst.jvm();

        // Map list values to columns by position (INCLUDING primary key)
        // This allows the list to specify all columns in their natural order
        for (int i = 0; i < Math.min(values.size(), metadata.columns.size()); i++) {
            final ColumnMetadata column = metadata.columns.get(i);
            recMap.put(uri(column.name), values.get(i));
        }

        if (values.size() > metadata.columns.size()) {
            this.space.logger().warn("list has more values (%d) than columns (%d) in table %s - extra values ignored",
                    values.size(), metadata.columns.size(), metadata.tableName);
        }

        // Create a record from the map and use existing writeRow logic
        final studio.phaseshift.metatron.isa.m.type.Rec rec = rec(recMap);

        // Determine the primary key value to check if row exists
        // Priority: 1) value from list, 2) value from URI
        final String pkColumn = metadata.primaryKeys.getFirst();
        final Obj pkValueFromList = recMap.get(uri(pkColumn));
        final String pkValue;

        if (pkValueFromList != null && !pkValueFromList.isNoObj()) {
            // Use primary key from the list
            pkValue = pkValueFromList.toString();
            this.space.logger().debug("using primary key from list: %s = %s", pkColumn, pkValue);
        } else {
            // Fall back to rowId from URI
            pkValue = rowId;
            this.space.logger().debug("using primary key from URI: %s = %s", pkColumn, pkValue);
        }

        // Check if row exists
        final String checkSql = String.format("SELECT COUNT(*) FROM %s WHERE %s = ?", metadata.tableName, pkColumn);

        final boolean exists;
        try (final PreparedStatement stmt = conn.prepareStatement(checkSql)) {
            final ColumnMetadata pkColMeta = metadata.columns.stream()
                    .filter(c -> c.name.equals(pkColumn))
                    .findFirst()
                    .orElseThrow();
            if (pkColMeta.sqlType == Types.INTEGER || pkColMeta.sqlType == Types.BIGINT ||
                    pkColMeta.sqlType == Types.SMALLINT || pkColMeta.sqlType == Types.TINYINT) {
                stmt.setLong(1, Long.parseLong(pkValue));
            } else {
                stmt.setString(1, pkValue);
            }
            try (final ResultSet rs = stmt.executeQuery()) {
                exists = rs.next() && rs.getInt(1) > 0;
            }
        }

        if (exists) {
            return updateRow(conn, metadata, pkValue, rec);
        } else {
            return insertRow(conn, metadata, pkValue, rec);
        }
    }

    /**
     * Write a single field value to a row
     */
    private int writeField(final Connection conn, final TableMetadata metadata, final String rowId,
                           final String fieldName, final Obj value) throws SQLException {
        // Verify the field exists in the table
        final ColumnMetadata column = metadata.columns.stream()
                .filter(c -> c.name.equalsIgnoreCase(fieldName))
                .findFirst()
                .orElseThrow(() -> new SQLException("Column not found: " + fieldName));

        final String pkColumn = metadata.primaryKeys.getFirst();
        final String sql = String.format("UPDATE %s SET %s = ? WHERE %s = ?",
                metadata.tableName, column.name, pkColumn);

        try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
            writeParameter(stmt, 1, value, column.sqlType);
            stmt.setString(2, rowId);
            final int updated = stmt.executeUpdate();
            this.space.logger().debug("updated field %s.%s for row %s: %s rows affected",
                    metadata.tableName, fieldName, rowId, updated);
            return updated;
        }
    }

    /**
     * Write an entire row (update existing or insert new)
     */
    private int writeRow(final Connection conn, final TableMetadata metadata, final String rowId,
                         final studio.phaseshift.metatron.isa.m.type.Rec rec) throws SQLException {
        // Check if row exists
        final String pkColumn = metadata.primaryKeys.getFirst();
        final String checkSql = String.format("SELECT COUNT(*) FROM %s WHERE %s = ?", metadata.tableName, pkColumn);

        final boolean exists;
        try (final PreparedStatement stmt = conn.prepareStatement(checkSql)) {
            // Use proper type for primary key parameter
            final ColumnMetadata pkColMeta = metadata.columns.stream()
                    .filter(c -> c.name.equals(pkColumn))
                    .findFirst()
                    .orElseThrow();
            if (pkColMeta.sqlType == Types.INTEGER || pkColMeta.sqlType == Types.BIGINT ||
                    pkColMeta.sqlType == Types.SMALLINT || pkColMeta.sqlType == Types.TINYINT) {
                stmt.setLong(1, Long.parseLong(rowId));
            } else {
                stmt.setString(1, rowId);
            }
            try (final ResultSet rs = stmt.executeQuery()) {
                exists = rs.next() && rs.getInt(1) > 0;
            }
        }

        if (exists) {
            return updateRow(conn, metadata, rowId, rec);
        } else {
            return insertRow(conn, metadata, rowId, rec);
        }
    }

    /**
     * Update an existing row
     */
    private int updateRow(final Connection conn, final TableMetadata metadata, final String rowId,
                          final studio.phaseshift.metatron.isa.m.type.Rec rec) throws SQLException {
        final List<String> setClauses = new ArrayList<>();
        final List<Tuple.Pair<Obj, ColumnMetadata>> values = new ArrayList<>();

        // Build SET clauses for each field in the record
        for (final Map.Entry<Obj, Obj> entry : rec.recValue().entrySet()) {
            // Skip non-Uri keys
            if (!entry.getKey().isUri()) {
                this.space.logger().warn("ignoring non-uri key in rec: %s", entry.getKey());
                continue;
            }

            final String fieldName = entry.getKey().asUri().uriValue().name();

            // Skip empty field names
            if (fieldName == null || fieldName.isEmpty()) {
                this.space.logger().warn("ignoring empty field name for key: %s", entry.getKey());
                continue;
            }

            final ColumnMetadata column = metadata.columns.stream()
                    .filter(c -> c.name.equalsIgnoreCase(fieldName))
                    .findFirst()
                    .orElse(null);

            if (column != null) {
                setClauses.add(column.name + " = ?");
                values.add(Tuple.Pair.with(entry.getValue(), column));
            } else {
                this.space.logger().warn("ignoring update as column %s not found in table %s", fieldName, metadata.tableName);
            }
        }

        if (setClauses.isEmpty()) {
            this.space.logger().warn("no valid columns to update for table %s", metadata.tableName);
            return 0;
        }

        final String pkColumn = metadata.primaryKeys.getFirst();
        final String sql = String.format("UPDATE %s SET %s WHERE %s = ?",
                metadata.tableName, String.join(", ", setClauses), pkColumn);

        try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                final Tuple.Pair<Obj, ColumnMetadata> pair = values.get(i);
                writeParameter(stmt, i + 1, pair.get0(), pair.get1().sqlType);
            }
            stmt.setString(values.size() + 1, rowId);

            final int updated = stmt.executeUpdate();
            this.space.logger().debug("updated row in %s with id %s: %s rows affected",
                    metadata.tableName, rowId, updated);
            return updated;
        }
    }

    /**
     * Insert a new row
     */
    private int insertRow(final Connection conn, final TableMetadata metadata, final String rowId,
                          final studio.phaseshift.metatron.isa.m.type.Rec rec) throws SQLException {
        final List<String> columnNames = new ArrayList<>();
        final List<Tuple.Pair<Obj, ColumnMetadata>> values = new ArrayList<>();

        // Add primary key first
        final String pkColumn = metadata.primaryKeys.getFirst();
        columnNames.add(pkColumn);
        final ColumnMetadata pkColMeta = metadata.columns.stream()
                .filter(c -> c.name.equals(pkColumn))
                .findFirst()
                .orElseThrow();
        // Convert rowId string to appropriate type based on column type
        final Obj pkValue;
        if (pkColMeta.sqlType == Types.INTEGER || pkColMeta.sqlType == Types.BIGINT ||
                pkColMeta.sqlType == Types.SMALLINT || pkColMeta.sqlType == Types.TINYINT) {
            pkValue = jnt(Long.parseLong(rowId));
        } else {
            pkValue = str(rowId);
        }
        values.add(Tuple.Pair.with(pkValue, pkColMeta));

        // Add other fields from the record
        for (final Map.Entry<Obj, Obj> entry : rec.recValue().entrySet()) {
            // Skip non-Uri keys
            if (!entry.getKey().isUri()) {
                this.space.logger().warn("ignoring non-uri key in rec: %s", entry.getKey());
                continue;
            }

            final String fieldName = entry.getKey().asUri().uriValue().name();

            // Skip empty field names
            if (fieldName == null || fieldName.isEmpty()) {
                this.space.logger().warn("ignoring empty field name for key: %s", entry.getKey());
                continue;
            }

            // Skip if this is the primary key (already added)
            if (fieldName.equalsIgnoreCase(pkColumn)) {
                continue;
            }

            final ColumnMetadata column = metadata.columns.stream()
                    .filter(c -> c.name.equalsIgnoreCase(fieldName))
                    .findFirst()
                    .orElse(null);

            if (column != null) {
                columnNames.add(column.name);
                values.add(Tuple.Pair.with(entry.getValue(), column));
            } else {
                this.space.logger().warn("ignoring insert as column %s not found in table %s", fieldName, metadata.tableName);
            }
        }

        final String placeholders = String.join(", ", Collections.nCopies(columnNames.size(), "?"));
        final String sql = String.format("INSERT INTO %s (%s) VALUES (%s)",
                metadata.tableName, String.join(", ", columnNames), placeholders);

        try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                final Tuple.Pair<Obj, ColumnMetadata> pair = values.get(i);
                writeParameter(stmt, i + 1, pair.get0(), pair.get1().sqlType);
            }

            final int inserted = stmt.executeUpdate();
            this.space.logger().debug("inserted row into %s with id %s: %s rows affected",
                    metadata.tableName, rowId, inserted);
            return inserted;
        }
    }

    @Override
    public Iterator<Space.IdObj> read(final Connection conn, final fURI pattern) throws SQLException {
        final List<String> tablePath = parseTablePath(pattern);
        if (tablePath == null)
            return Collections.emptyIterator();
        final String tableName = tablePath.getFirst();
        final String rowId = tablePath.get(1);
        final TableMetadata metadata = tableSchemas.get(tableName.toLowerCase());
        if (metadata == null)
            return Collections.emptyIterator();
        final List<Space.IdObj> results = new ArrayList<>();
        if (rowId.equals("+") || rowId.equals("#")) {
            if (tablePath.size() > 2 && !tablePath.get(2).equals("+")) {
                // Read all rows with specific field - need to include primary keys for buildRowId
                final String pkColumns = String.join(", ", metadata.primaryKeys);
                final String fieldName = tablePath.get(2);
                final String sql = String.format("SELECT %s, %s FROM %s", pkColumns, fieldName, metadata.tableName);
                try (final Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        // Build row identifier from primary keys or use row number
                        final String id = buildRowId(rs, metadata);
                        fURI rowFuri = f(tableName).extend(id).extend(fieldName);
                        final Obj obj = readTableRow(rs, metadata, fieldName);
                        results.add(Space.IdObj.of(rowFuri, obj));
                    }
                }
            } else {
                // Read all rows
                final String sql = String.format("SELECT * FROM %s", metadata.tableName);
                try (final Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        // Build row identifier from primary keys or use row number
                        final String id = buildRowId(rs, metadata);
                        fURI rowFuri = f(tableName).extend(id);
                        final Obj obj = readTableRow(rs, metadata);
                        results.add(Space.IdObj.of(rowFuri, obj));
                    }
                }
            }
        } else {
            // Read specific row by primary key
            if (metadata.primaryKeys.isEmpty()) {
                this.space.logger().warn("table %s has no primary key, cannot read specific row", tableName);
                return Collections.emptyIterator();
            }
            if (tablePath.size() > 2 && !tablePath.get(2).equals("+")) {
                final String pkColumn = metadata.primaryKeys.getFirst();
                final String pkColumns = String.join(", ", metadata.primaryKeys);
                final String fieldName = tablePath.get(2);
                final String sql = String.format("SELECT %s, %s FROM %s WHERE %s = ?", pkColumns, fieldName, metadata.tableName, pkColumn);
                try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
                    // Set the parameter with the correct type based on the primary key column type
                    final ColumnMetadata pkColMeta = metadata.columns.stream()
                            .filter(c -> c.name.equals(pkColumn))
                            .findFirst()
                            .orElseThrow();
                    if (pkColMeta.sqlType == Types.INTEGER || pkColMeta.sqlType == Types.BIGINT ||
                            pkColMeta.sqlType == Types.SMALLINT || pkColMeta.sqlType == Types.TINYINT) {
                        if (!CommonUtil.isInt(rowId))
                            return IteratorUtil.of();
                        stmt.setLong(1, Long.parseLong(rowId));
                    } else {
                        stmt.setString(1, rowId);
                    }
                    try (final ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            final fURI rowFuri = f(tableName).extend(rowId).extend(fieldName);
                            final Obj row = readTableRow(rs, metadata, fieldName);
                            results.add(Space.IdObj.of(rowFuri, row));
                        }
                    }
                }
            } else {
                final String pkColumn = metadata.primaryKeys.getFirst();
                final String sql = String.format("SELECT * FROM %s WHERE %s = ?", metadata.tableName, pkColumn);
                try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
                    // Set the parameter with the correct type based on the primary key column type
                    final ColumnMetadata pkColMeta = metadata.columns.stream()
                            .filter(c -> c.name.equals(pkColumn))
                            .findFirst()
                            .orElseThrow();
                    if (pkColMeta.sqlType == Types.INTEGER || pkColMeta.sqlType == Types.BIGINT ||
                            pkColMeta.sqlType == Types.SMALLINT || pkColMeta.sqlType == Types.TINYINT) {
                        if (!CommonUtil.isInt(rowId))
                            return IteratorUtil.of();
                        stmt.setLong(1, Long.parseLong(rowId));
                    } else {
                        stmt.setString(1, rowId);
                    }
                    try (final ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            final fURI rowFuri = f(tableName).extend(rowId);
                            final Obj row = readTableRow(rs, metadata);
                            results.add(Space.IdObj.of(rowFuri, row));
                        }
                    }
                }
            }
        }
        return results.iterator();
    }

    @Override
    public int delete(final Connection conn, final fURI furi) throws SQLException {
        // ExistingTableSchema is read-only
        throw new UnsupportedOperationException("ExistingTableSchema is read-only. Cannot delete from existing tables.");
    }

    @Override
    public boolean supportsfURIPatterns() {
        return false; // Basic pattern support only
    }

    @Override
    public String version() {
        return "1.0-existing";
    }

    /**
     * Check if a fURI path refers to a table managed by this schema
     */
    public boolean isTablePath(final fURI furi) {
        return parseTablePath(furi.asNode()) != null;
    }

    /**
     * Get all discovered table names
     */
    public Set<String> getTableNames() {
        return tableSchemas.keySet();
    }

    public List<TableMetadata> getTableMetadata() {
        return new ArrayList<>(tableSchemas.values());
    }

    /**
     * Get foreign key metadata for a specific column in a table
     * Returns null if the column is not a foreign key
     */
    public ForeignKeyMetadata getForeignKeyForColumn(final String tableName, final String columnName) {
        final TableMetadata metadata = tableSchemas.get(tableName.toLowerCase());
        if (metadata == null) {
            return null;
        }
        return metadata.foreignKeys().stream()
                .filter(fk -> fk.fromColumn().equalsIgnoreCase(columnName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get all foreign keys for a table
     */
    public List<ForeignKeyMetadata> getForeignKeysForTable(final String tableName) {
        final TableMetadata metadata = tableSchemas.get(tableName.toLowerCase());
        if (metadata == null) {
            return Collections.emptyList();
        }
        return metadata.foreignKeys();
    }

    /**
     * Get all foreign keys across all tables
     */
    public List<ForeignKeyMetadata> getAllForeignKeys() {
        return tableSchemas.values().stream()
                .flatMap(table -> table.foreignKeys().stream())
                .toList();
    }
}
