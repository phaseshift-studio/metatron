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
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.sql.*;
import java.util.*;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

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

    private static final GraphittyLogger LOG = Graphitty.log(ExistingTableSchema.class);

    private final Map<String, TableMetadata> tableSchemas = new LinkedHashMap<>();
    private final String excludeTableName;

    /**
     * Metadata about a SQL table
     */
    public static class TableMetadata {
        public final String tableName;
        public final List<ColumnMetadata> columns;
        public final List<String> primaryKeys;

        public TableMetadata(String tableName, List<ColumnMetadata> columns, List<String> primaryKeys) {
            this.tableName = tableName;
            this.columns = columns;
            this.primaryKeys = primaryKeys;
        }
    }

    /**
     * Metadata about a SQL column
     */
    public static class ColumnMetadata {
        public final String name;
        public final int sqlType;
        public final String typeName;

        public ColumnMetadata(String name, int sqlType, String typeName) {
            this.name = name;
            this.sqlType = sqlType;
            this.typeName = typeName;
        }
    }

    /**
     * Create a new ExistingTableSchema
     *
     * @param excludeTableName name of table to exclude from discovery (e.g., the key-value store table)
     */
    public ExistingTableSchema(String excludeTableName) {
        this.excludeTableName = excludeTableName;
    }

    @Override
    public void initialize(Connection conn) throws SQLException {
        discoverTableSchemas(conn);
    }

    /**
     * Discover all tables in the database and their schemas
     */
    private void discoverTableSchemas(Connection conn) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();

        // Get all tables (excluding system tables)
        try (ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");

                // Skip the key-value store table
                if (tableName.equals(excludeTableName)) {
                    continue;
                }

                // Get columns for this table
                List<ColumnMetadata> columns = new ArrayList<>();
                try (ResultSet cols = metaData.getColumns(null, null, tableName, "%")) {
                    while (cols.next()) {
                        String columnName = cols.getString("COLUMN_NAME");
                        int sqlType = cols.getInt("DATA_TYPE");
                        String typeName = cols.getString("TYPE_NAME");
                        columns.add(new ColumnMetadata(columnName, sqlType, typeName));
                    }
                }

                // Get primary keys for this table
                List<String> primaryKeys = new ArrayList<>();
                try (ResultSet pks = metaData.getPrimaryKeys(null, null, tableName)) {
                    while (pks.next()) {
                        primaryKeys.add(pks.getString("COLUMN_NAME"));
                    }
                }

                tableSchemas.put(tableName.toLowerCase(), new TableMetadata(tableName, columns, primaryKeys));
                LOG.debug("Discovered table: {} with {} columns and {} primary keys",
                        tableName, columns.size(), primaryKeys.size());
            }
        }
        LOG.info("Discovered {{b}}{}{{X}} tables: {}", tableSchemas.size(), tableSchemas.keySet());
    }

    /**
     * Parse a fURI to extract table name and row identifier
     * Format: /table_name/row_id or /table_name/+ for all rows
     * Returns null if not a table path
     */
    private org.javatuples.Pair<String, String> parseTablePath(final fURI furi) {
        // Use segments() to get only the named segments (no empty strings from slashes)
        List<String> segments = furi.segments();

        if (segments.isEmpty()) {
            return null;
        }

        // First segment should be the table name
        String tableName = segments.get(0);

        if (!tableSchemas.containsKey(tableName.toLowerCase())) {
            return null;
        }

        // Get row identifier (if present)
        String rowId = segments.size() > 1 ? segments.get(1) : null;
        return org.javatuples.Pair.with(tableName, rowId);
    }

    /**
     * Convert a SQL value to a Metatron object
     */
    private Obj sqlValueToObj(Object value, int sqlType) {
        if (value == null) {
            return noobj();
        }

        return switch (sqlType) {
            case Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT ->
                    jnt(((Number) value).longValue());
            case Types.REAL, Types.FLOAT, Types.DOUBLE, Types.DECIMAL, Types.NUMERIC ->
                    real(((Number) value).doubleValue());
            case Types.BOOLEAN, Types.BIT ->
                    bool((Boolean) value);
            case Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR, Types.NVARCHAR, Types.NCHAR ->
                    str(value.toString());
            default ->
                    str(value.toString());
        };
    }

    /**
     * Read a row from a SQL table and convert it to a Metatron list
     */
    private String readTableRow(ResultSet rs, TableMetadata metadata) throws SQLException {
        List<Obj> values = new ArrayList<>();
        for (ColumnMetadata col : metadata.columns) {
            Object value = rs.getObject(col.name);
            values.add(sqlValueToObj(value, col.sqlType));
        }
        // Convert to JSON string for consistency with TableSchema interface
        return lst(values).toString();
    }

    /**
     * Build a row identifier from primary keys or row number
     */
    private String buildRowId(ResultSet rs, TableMetadata metadata) throws SQLException {
        if (!metadata.primaryKeys.isEmpty()) {
            // Use primary key value(s)
            StringBuilder id = new StringBuilder();
            for (int i = 0; i < metadata.primaryKeys.size(); i++) {
                if (i > 0) id.append("_");
                Object value = rs.getObject(metadata.primaryKeys.get(i));
                id.append(value != null ? value.toString() : "null");
            }
            return id.toString();
        } else {
            // Use row number as fallback
            return String.valueOf(rs.getRow());
        }
    }

    @Override
    public int write(Connection conn, fURI furi, String objJson) throws SQLException {
        // ExistingTableSchema is read-only
        throw new UnsupportedOperationException("ExistingTableSchema is read-only. Cannot write to existing tables.");
    }

    @Override
    public Iterator<FuriObjPair> read(Connection conn, fURI pattern) throws SQLException {
        org.javatuples.Pair<String, String> tablePath = parseTablePath(pattern.asNode());
        if (tablePath == null) {
            // Not a table path
            return Collections.emptyIterator();
        }

        String tableName = tablePath.getValue0();
        String rowId = tablePath.getValue1();
        TableMetadata metadata = tableSchemas.get(tableName.toLowerCase());

        if (metadata == null) {
            return Collections.emptyIterator();
        }

        List<FuriObjPair> results = new ArrayList<>();

        if (rowId == null || rowId.equals("+") || pattern.hasPattern()) {
            // Read all rows
            String sql = String.format("SELECT * FROM %s", metadata.tableName);
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    // Build row identifier from primary keys or use row number
                    String id = buildRowId(rs, metadata);
                    fURI rowFuri = fURI.Singleton.f("/" + tableName + "/" + id);
                    String rowJson = readTableRow(rs, metadata);
                    results.add(new FuriObjPair(rowFuri, rowJson));
                }
            }
        } else {
            // Read specific row by primary key
            if (metadata.primaryKeys.isEmpty()) {
                LOG.warn("Table {} has no primary key, cannot read specific row", tableName);
                return Collections.emptyIterator();
            }

            String pkColumn = metadata.primaryKeys.get(0);
            String sql = String.format("SELECT * FROM %s WHERE %s = ?", metadata.tableName, pkColumn);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, rowId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        fURI rowFuri = fURI.Singleton.f("/" + tableName + "/" + rowId);
                        String rowJson = readTableRow(rs, metadata);
                        results.add(new FuriObjPair(rowFuri, rowJson));
                    }
                }
            }
        }

        return results.iterator();
    }

    @Override
    public int delete(Connection conn, fURI furi) throws SQLException {
        // ExistingTableSchema is read-only
        throw new UnsupportedOperationException("ExistingTableSchema is read-only. Cannot delete from existing tables.");
    }

    @Override
    public boolean supportsMqttPatterns() {
        return false; // Basic pattern support only
    }

    @Override
    public String version() {
        return "1.0-existing";
    }

    /**
     * Check if a fURI path refers to a table managed by this schema
     */
    public boolean isTablePath(fURI furi) {
        return parseTablePath(furi.asNode()) != null;
    }

    /**
     * Get all discovered table names
     */
    public Set<String> getTableNames() {
        return tableSchemas.keySet();
    }
}
