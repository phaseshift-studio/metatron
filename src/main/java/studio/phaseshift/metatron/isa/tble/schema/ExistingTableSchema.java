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

package studio.phaseshift.metatron.isa.tble.schema;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.tble.tbleSpace;
import studio.phaseshift.metatron.util.Tuple;

import java.sql.*;
import java.util.*;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.tble.tbleInstSet.REC_ROW_TID;

/**
 * Schema for mapping existing SQL tables to Metatron objects.
 * Discovers tables in the database and makes them accessible via fURIs.
 * <p>
 * Path format: /table_name/row_id
 * - /users/123 → reads row with primary key 123 from users table
 * - /users/+ → reads all rows from users table
 * <p>
 * SQL rows are converted to Metatron lists with values in column order.
 * This schema is read-only and does not support writes.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ExistingTableSchema implements TableSchema {
    private final tbleSpace space;
    private final Map<String, TableMetadata> tableSchemas = new LinkedHashMap<>();
    private final String excludeTableName;

    /**
     * Metadata about a SQL table
     */
    public record TableMetadata(String dbName, String tableName, List<ColumnMetadata> columns,
                                List<String> primaryKeys) {
    }

    /**
     * Metadata about a SQL column
     */
    public record ColumnMetadata(String name, int sqlType, String typeName) {
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
        // Get all tables (excluding system tables)
        try (final ResultSet tables = metaData.getTables(conn.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                final String tableName = tables.getString("TABLE_NAME");
                // Skip the key-value store table
                if (tableName.equals(this.excludeTableName)) {
                    continue;
                }
                // Get columns for this table
                final List<ColumnMetadata> columns = new ArrayList<>();
                try (final ResultSet cols = metaData.getColumns(null, null, tableName, "%")) {
                    while (cols.next()) {
                        String columnName = cols.getString("COLUMN_NAME");
                        int sqlType = cols.getInt("DATA_TYPE");
                        String typeName = cols.getString("TYPE_NAME");
                        columns.add(new ColumnMetadata(columnName, sqlType, typeName));
                    }
                }
                // Get primary keys for this table
                final List<String> primaryKeys = new ArrayList<>();
                try (final ResultSet pks = metaData.getPrimaryKeys(null, null, tableName)) {
                    while (pks.next()) {
                        primaryKeys.add(pks.getString("COLUMN_NAME"));
                    }
                }
                this.tableSchemas.put(tableName.toLowerCase(), new TableMetadata(conn.getCatalog(), tableName, columns, primaryKeys));
                this.space.logger().debug("discovered table: %s with %s columns and %s primary keys",
                        tableName, columns.size(), primaryKeys.size());
            }
        }
        this.space.logger().info("discovered {{b}}%s{{X}} tables: %s", tableSchemas.size(), tableSchemas.keySet());
    }

    /**
     * Parse a fURI to extract table name and row identifier
     * Format: /table_name/row_id or /table_name/+ for all rows
     * Returns null if not a table path
     */
    private Tuple.Pair<String, String> parseTablePath(final fURI furi) {
        // Use segments() to get only the named segments (no empty strings from slashes)
        final List<String> segments = furi.segments();
        if (segments.isEmpty())
            return null;
        // First segment should be the table name
        final String tableName = segments.get(0);
        if (!this.tableSchemas.containsKey(tableName.toLowerCase()))
            return null;
        // Get row identifier (if present)
        final String rowId = segments.size() > 1 ? segments.get(1) : null;
        return Tuple.Pair.with(tableName, rowId);
    }

    /**
     * Convert a SQL value to a Metatron object
     */
    private Obj sqlValueToObj(final Object value, final int sqlType) {
        if (value == null) {
            return noobj();
        }

        return switch (sqlType) {
            case Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT -> jnt(((Number) value).longValue());
            case Types.REAL, Types.FLOAT, Types.DOUBLE, Types.DECIMAL, Types.NUMERIC ->
                    real(((Number) value).doubleValue());
            case Types.BOOLEAN, Types.BIT -> bool((Boolean) value);
            case Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR, Types.NVARCHAR, Types.NCHAR -> str(value.toString());
            default -> str(value.toString());
        };
    }

    /**
     * Read a row from a SQL table and convert it to a Metatron list
     */
    private Obj readTableRow(final ResultSet rs, final TableMetadata metadata) throws SQLException {
        //    List<Obj> values = new ArrayList<>();
       final Map<Obj, Obj> labeledValues = new LinkedHashMap<>();
        for (final ColumnMetadata col : metadata.columns) {
            final Object value = rs.getObject(col.name);
            //  values.add(sqlValueToObj(value, col.sqlType));
            labeledValues.put(uri(col.name), sqlValueToObj(value, col.sqlType));
            if (null != value)
                Router.global().stats().ioStats().incrBytesRecv(value.toString().getBytes().length);
        }
        // Convert to JSON string for consistency with TableSchema interface
        // return rec(Map.of(uri(TABLE),uri(metadata.tableName),uri(VALUE),lst(values)),ROW_TID,null);
        return rec(labeledValues, REC_ROW_TID, null);
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
        // ExistingTableSchema is read-only
        throw new UnsupportedOperationException("ExistingTableSchema is read-only. Cannot write to existing tables.");
    }

    @Override
    public Iterator<Space.IdObj> read(final Connection conn, final fURI pattern) throws SQLException {
        final Tuple.Pair<String, String> tablePath = parseTablePath(pattern.asNode());
        if (tablePath == null)
            return Collections.emptyIterator();
        final String tableName = tablePath.get0();
        final String rowId = tablePath.get1();
        final TableMetadata metadata = tableSchemas.get(tableName.toLowerCase());
        if (metadata == null)
            return Collections.emptyIterator();
        final List<Space.IdObj> results = new ArrayList<>();
        if (rowId == null || rowId.equals("+") || pattern.hasPattern()) {
            // Read all rows
            final String sql = String.format("SELECT * FROM %s", metadata.tableName);
            try (final Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    // Build row identifier from primary keys or use row number
                    final String id = buildRowId(rs, metadata);
                    fURI rowFuri = fURI.Singleton.f("/" + tableName + "/" + id);
                    final Obj obj = readTableRow(rs, metadata);
                    results.add(Space.IdObj.of(rowFuri, obj));
                }
            }
        } else {
            // Read specific row by primary key
            if (metadata.primaryKeys.isEmpty()) {
                this.space.logger().warn("table %s has no primary key, cannot read specific row", tableName);
                return Collections.emptyIterator();
            }

            final String pkColumn = metadata.primaryKeys.getFirst();
            final String sql = String.format("SELECT * FROM %s WHERE %s = ?", metadata.tableName, pkColumn);
            try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, rowId);
                try (final ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        final fURI rowFuri = fURI.Singleton.f("/" + tableName + "/" + rowId);
                        final Obj row = readTableRow(rs, metadata);
                        results.add(Space.IdObj.of(rowFuri, row));
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
}
