/*
 * metatron: a distributed virtual machine and language
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

package studio.phaseshift.metatron.isa.tble.space;

import studio.phaseshift.metatron.furi.DataPath;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSQLSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.tble.schema.storage.TableSchema;
import studio.phaseshift.metatron.isa.tble.tbleSpace;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;
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
import static studio.phaseshift.metatron.isa.tble.tbleInstSet.TABLE_TID;

/**
 * Schema for mapping existing SQL tables to metatron objects.
 * Discovers tables in the database and makes them accessible via fURIs.
 * <p>
 * Path format: /table_name/row_id[/field_name]
 * <p>
 * <b>Read operations:</b>
 * <ul>
 *   <li>{@code /users/123} → {@code SELECT * FROM users WHERE pk = ?}
 *       — full row as a record</li>
 *   <li>{@code /users/123/name} → {@code SELECT pk, name FROM users WHERE pk = ?}
 *       — single-column read, no full-row fetch</li>
 *   <li>{@code /users/+} → {@code SELECT * FROM users} — all rows</li>
 *   <li>{@code /users/+/name} → {@code SELECT pk, name FROM users}
 *       — single-column projection across all rows</li>
 * </ul>
 * <p>
 * <b>Write operations:</b>
 * <ul>
 *   <li>{@code /users/123 → [name=>marko,age=>29]}
 *       — reads the current row, diffs against the incoming Rec, and
 *       UPDATEs only the columns that actually changed (INSERT if new)</li>
 *   <li>{@code /users/123/name → marko}
 *       — {@code UPDATE users SET name = ? WHERE pk = ?} (single field)</li>
 *   <li>{@code /users/123 → noobj}
 *       — {@code DELETE FROM users WHERE pk = ?}</li>
 * </ul>
 * <p>
 * SQL rows are converted to metatron records where column names are keys.
 * The diff-based write optimization avoids rewriting unchanged columns,
 * reducing write amplification for partial Rec updates.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ExistingTableSchema extends ObjSQLSerializer implements TableSchema {

    static final String MTRON_META_TABLE = "_mtron_meta";

    private final tbleSpace space;
    private final Map<String, TableMetadata> tableSchemas = new LinkedHashMap<>();
    private final String excludeTableName;

    /**
     * Logical type overrides: tableName → (columnName → mtronTypeTID).
     */
    private final Map<String, Map<String, fURI>> logicalTypes = new LinkedHashMap<>();

    public record TableMetadata(String dbName, String tableName, List<ColumnMetadata> columns,
                                List<String> primaryKeys, List<ForeignKeyMetadata> foreignKeys) {
    }

    public record ColumnMetadata(String name, int sqlType, String typeName, boolean nullable) {
        public ColumnMetadata(String name, int sqlType, String typeName) {
            this(name, sqlType, typeName, true);
        }

        public boolean isNumeric() {
            return sqlType == Types.INTEGER || sqlType == Types.BIGINT ||
                   sqlType == Types.SMALLINT || sqlType == Types.TINYINT ||
                   sqlType == Types.REAL || sqlType == Types.FLOAT ||
                   sqlType == Types.DOUBLE || sqlType == Types.DECIMAL ||
                   sqlType == Types.NUMERIC;
        }
    }

    /**
     * Validates that {@code value} is compatible with the column's schema before writing.
     * Throws {@link MTronException} with a clear message for constraint violations
     * and type mismatches that would otherwise fail with cryptic JDBC errors.
     */
    private void validateColumnWrite(final Obj value, final ColumnMetadata column,
                                     final String tableName) {
        // NULL into a NOT NULL column
        if (value.isNone() && !column.nullable()) {
            throw MTronException.of(
                    "Cannot set column '%s.%s' to NULL: column has a NOT NULL constraint",
                    tableName, column.name());
        }
        // Complex types (Rec, Lst, Rel) into scalar columns — toString() fallback
        // produces garbage like "[field=>'val',...]" that can't parse as a number
        if (value.isPoly() && column.isNumeric()) {
            throw MTronException.of(
                    "Cannot write %s to column '%s.%s': column type is %s (numeric). "
                    + "Numbers, strings, and booleans are supported.",
                    value.tid().name(), tableName, column.name(), column.typeName());
        }
        // Non-numeric string into a numeric column
        if (value.isStr() && column.isNumeric()) {
            try {
                Double.parseDouble(value.asStr().jvm());
            } catch (final NumberFormatException e) {
                throw MTronException.of(
                        "Cannot write string '%s' to column '%s.%s': column type is %s (numeric).",
                        value.asStr().jvm(), tableName, column.name(), column.typeName());
            }
        }
    }

    public record ForeignKeyMetadata(String fromTable, String fromColumn,
                                     String toTable, String toColumn, String fkName) {
    }

    public ExistingTableSchema(final tbleSpace space, final String excludeTableName) {
        this.excludeTableName = excludeTableName;
        this.space = space;
    }

    @Override
    public void initialize(final Connection conn) throws SQLException {
        discoverEntities(conn);
    }

    private void ensureMetaTable(final Connection conn) throws SQLException {
        try (final Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + MTRON_META_TABLE + " (" +
                            "  table_name  VARCHAR(255) NOT NULL, " +
                            "  column_name VARCHAR(255) NOT NULL, " +
                            "  ref_table   VARCHAR(512) NOT NULL, " +
                            "  PRIMARY KEY (table_name, column_name)" +
                            ")"
            );
        }
    }

    private void discoverEntities(final Connection conn) throws SQLException {
        final DatabaseMetaData metaData = conn.getMetaData();
        final String catalog = conn.getCatalog();

        try (final ResultSet tables = metaData.getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                final String tableName = tables.getString("TABLE_NAME");
                if (tableName.equals(this.excludeTableName) || tableName.equals(MTRON_META_TABLE)) {
                    continue;
                }
                final List<ColumnMetadata> columns = new ArrayList<>();
                try (final ResultSet cols = metaData.getColumns(catalog, null, tableName, "%")) {
                    while (cols.next()) {
                        columns.add(new ColumnMetadata(
                                cols.getString("COLUMN_NAME"),
                                cols.getInt("DATA_TYPE"),
                                cols.getString("TYPE_NAME"),
                                !"NO".equalsIgnoreCase(cols.getString("IS_NULLABLE"))));
                    }
                }
                final List<String> primaryKeys = new ArrayList<>();
                try (final ResultSet pks = metaData.getPrimaryKeys(catalog, null, tableName)) {
                    while (pks.next()) {
                        primaryKeys.add(pks.getString("COLUMN_NAME"));
                    }
                }

                final List<ForeignKeyMetadata> foreignKeys = discoverReferences(conn, catalog, tableName);

                this.tableSchemas.put(tableName.toLowerCase(),
                        new TableMetadata(catalog, tableName, columns, primaryKeys, foreignKeys));
                this.space.logger().debug("discovered table: %s with %s columns, %s primary keys, and %s foreign keys",
                        tableName, columns.size(), primaryKeys.size(), foreignKeys.size());
            }
        }
        this.space.logger().info("discovered {{b}}%s{{X}} tables: %s", tableSchemas.size(), tableSchemas.keySet());
        ensureMetaTable(conn);
        loadMetaForeignKeys(conn);
    }

    private List<ForeignKeyMetadata> discoverReferences(final Connection conn, final String catalog,
                                                         final String tableName) throws SQLException {
        final List<ForeignKeyMetadata> foreignKeys = new ArrayList<>();
        final DatabaseMetaData metaData = conn.getMetaData();
        try (final ResultSet fks = metaData.getImportedKeys(catalog, null, tableName)) {
            while (fks.next()) {
                foreignKeys.add(new ForeignKeyMetadata(
                        tableName, fks.getString("FKCOLUMN_NAME"),
                        fks.getString("PKTABLE_NAME"), fks.getString("PKCOLUMN_NAME"),
                        fks.getString("FK_NAME")));
            }
        }
        return foreignKeys;
    }

    private void loadMetaForeignKeys(final Connection conn) {
        try (final Statement stmt = conn.createStatement();
             final ResultSet rs = stmt.executeQuery(
                     "SELECT table_name, column_name, ref_table FROM " + MTRON_META_TABLE)) {
            while (rs.next()) {
                final String tbl = rs.getString("table_name");
                final String col = rs.getString("column_name");
                final String ref = rs.getString("ref_table");
                final TableMetadata meta = this.tableSchemas.get(tbl.toLowerCase());
                if (meta == null) continue;
                final boolean alreadyMapped = meta.foreignKeys().stream()
                        .anyMatch(fk -> fk.fromColumn().equalsIgnoreCase(col));
                if (!alreadyMapped)
                    meta.foreignKeys().add(new ForeignKeyMetadata(tbl, col, ref, "id", null));
            }
        } catch (final SQLException ignored) {
        }
    }

    /**
     * Resolve a space-relative fURI into a {@link DataPath} when the
     * table is known to this schema.  Returns {@code null} when the
     * table name is not a recognized table.
     */
    private DataPath resolveDataPath(final fURI furi) {
        final DataPath dp = DataPath.ofSpaceRelative(furi, null);
        if (!dp.hasCollection())
            return null;
        if (!dp.collectionIsWildcard()
                && !this.tableSchemas.containsKey(dp.collection().toLowerCase()))
            return null;
        return dp;
    }

    private Obj readTableRow(final ResultSet rs, final TableMetadata metadata, final String... rowNames) throws SQLException {
        final Map<Obj, Obj> labeledValues = new LinkedHashMap<>();
        for (final ColumnMetadata col : metadata.columns) {
            if (rowNames.length == 0 && metadata.primaryKeys.contains(col.name)) continue;
            if (rowNames.length == 0 || Arrays.asList(rowNames).contains(col.name)) {
                final Obj value = readColumnWithMetadata(rs, col, metadata.tableName);
                labeledValues.put(uri(col.name), value);
                if (!value.isNoObj())
                    Router.global().stats().ioStats().incrBytesRecv(value.toString().getBytes().length);
            }
        }
        return rowNames.length == 1 ? objs(labeledValues.values()) : rec(labeledValues, REC_TID, null);
    }

    private Obj readColumnWithMetadata(final ResultSet rs, final ColumnMetadata col,
                                       final String tableName) throws SQLException {
        final ForeignKeyMetadata fk = getForeignKeyForColumn(tableName, col.name);
        if (fk != null) {
            final Object fkValue = rs.getObject(col.name);
            if (fkValue != null && !rs.wasNull()) {
                final fURI referencedPath;
                final String refTable = fk.toTable();
                if (refTable.indexOf(':') >= 0) {
                    referencedPath = f(refTable).extend(fkValue.toString());
                } else {
                    referencedPath = this.space.pattern().retractPattern()
                            .extend(refTable)
                            .extend(fkValue.toString());
                }
                return auto_from_(referencedPath).tryToInst();
            }
            return noobj();
        }

        final Map<String, fURI> tableTypes = this.logicalTypes.get(tableName.toLowerCase());
        if (tableTypes != null) {
            final fURI logicalType = tableTypes.get(col.name.toLowerCase());
            if (logicalType != null) {
                final Object value = rs.getObject(col.name);
                if (value == null || rs.wasNull()) return noobj();
                if (logicalType.name().equals("bool")) {
                    return studio.phaseshift.metatron.isa.m.type.impl.MBool.bool(rs.getInt(col.name) != 0);
                }
            }
        }

        if ("BOOLEAN".equalsIgnoreCase(col.typeName) &&
                (col.sqlType == Types.INTEGER || col.sqlType == Types.TINYINT ||
                        col.sqlType == Types.SMALLINT || col.sqlType == Types.BIT)) {
            final Object value = rs.getObject(col.name);
            if (value == null || rs.wasNull()) return noobj();
            return studio.phaseshift.metatron.isa.m.type.impl.MBool.bool(rs.getInt(col.name) != 0);
        }
        return readColumn(rs, col.name, col.sqlType);
    }

    private String buildRowId(final ResultSet rs, final TableMetadata metadata) throws SQLException {
        if (!metadata.primaryKeys.isEmpty()) {
            final StringBuilder id = new StringBuilder();
            for (int i = 0; i < metadata.primaryKeys.size(); i++) {
                if (i > 0) id.append("_");
                final Object value = rs.getObject(metadata.primaryKeys.get(i));
                id.append(value != null ? value.toString() : "null");
            }
            return id.toString();
        } else {
            return String.valueOf(rs.getRow());
        }
    }

    @Override
    public int write(final Connection conn, final fURI furi, final String objJson) throws SQLException {
        final Obj obj = objJson == null ? noobj() : ObjSimpleJSONSerializer.parse(objJson);
        return write(conn, furi, obj);
    }

    public int write(final Connection conn, final fURI furi, final Obj obj) throws SQLException {
        final DataPath dp = resolveDataPath(furi.asNode());
        if (dp == null) {
            throw new SQLException("invalid table path: " + furi);
        }

        final String tableName = dp.collection();
        final String rowId = dp.entry();
        final TableMetadata metadata = tableSchemas.get(tableName.toLowerCase());

        if (metadata == null) {
            throw new SQLException("table not found: " + tableName);
        }
        if (rowId == null || dp.entryIsWildcard()) {
            throw new SQLException("cannot write without specific row ID: " + furi);
        }
        if (metadata.primaryKeys.isEmpty()) {
            throw new SQLException("table " + tableName + " has no primary key, cannot write");
        }

        if (dp.hasField()) {
            return writeField(conn, metadata, rowId, dp.field(), obj);
        }

        if (obj.isNoObj() || obj.isNone()) {
            return delete(conn, furi);
        }
        if (obj.isRec()) {
            return writeRow(conn, metadata, rowId, obj.asRec());
        } else if (obj.isLst()) {
            return writeRowFromList(conn, metadata, rowId, obj.asLst());
        } else {
            throw new SQLException("expected rec or lst for row write: " + obj.tid());
        }
    }

    private int writeRowFromList(final Connection conn, final TableMetadata metadata, final String rowId,
                                 final studio.phaseshift.metatron.isa.m.type.Lst lst) throws SQLException {
        final Map<Obj, Obj> recMap = new LinkedHashMap<>();
        final List<Obj> values = lst.jvm();

        for (int i = 0; i < Math.min(values.size(), metadata.columns.size()); i++) {
            final ColumnMetadata column = metadata.columns.get(i);
            recMap.put(uri(column.name), values.get(i));
        }

        if (values.size() > metadata.columns.size()) {
            this.space.logger().warn("list has more values (%d) than columns (%d) in table %s - extra values ignored",
                    values.size(), metadata.columns.size(), metadata.tableName);
        }

        final studio.phaseshift.metatron.isa.m.type.Rec rec = rec(recMap);
        final String pkColumn = metadata.primaryKeys.getFirst();
        final Obj pkValueFromList = recMap.get(uri(pkColumn));
        final String pkValue;

        if (pkValueFromList != null && !pkValueFromList.isNoObj()) {
            pkValue = pkValueFromList.toString();
            this.space.logger().debug("using primary key from list: %s = %s", pkColumn, pkValue);
        } else {
            pkValue = rowId;
            this.space.logger().debug("using primary key from URI: %s = %s", pkColumn, pkValue);
        }

        final Rec current = readCurrentRow(conn, metadata, pkColumn, pkValue);
        if (current == null)
            return insertRow(conn, metadata, pkValue, rec);

        // Diff: only write columns that changed from the current row
        final Map<String, Obj> changed = new LinkedHashMap<>();
        final Map<Obj, Obj> currentMap = current.recValue();
        for (final Map.Entry<Obj, Obj> entry : rec.recValue().entrySet()) {
            final String fieldName = entry.getKey().asUri().uriValue().name();
            final ColumnMetadata col = metadata.columns.stream()
                    .filter(c -> c.name.equalsIgnoreCase(fieldName)).findFirst().orElse(null);
            if (col == null) continue;
            final Obj currentVal = currentMap.get(entry.getKey());
            if (!entry.getValue().equals(currentVal))
                changed.put(col.name, entry.getValue());
        }

        if (changed.isEmpty()) return 0;
        return updateRowDiffed(conn, metadata, pkColumn, pkValue, changed);
    }

    private void trackLogicalType(final TableMetadata metadata, final String columnName,
                                  final Obj value, final int sqlType) {
        if (sqlType == Types.INTEGER && value.isBool()) {
            this.logicalTypes
                    .computeIfAbsent(metadata.tableName.toLowerCase(), k -> new LinkedHashMap<>())
                    .put(columnName.toLowerCase(), value.tid());
        }
    }

    private int writeField(final Connection conn, final TableMetadata metadata, final String rowId,
                           final String fieldName, final Obj value) throws SQLException {
        final ColumnMetadata column = metadata.columns.stream()
                .filter(c -> c.name.equalsIgnoreCase(fieldName))
                .findFirst()
                .orElseThrow(() -> new SQLException("Column not found: " + fieldName));

        final String pkColumn = metadata.primaryKeys.getFirst();
        final String sql = String.format("UPDATE %s SET %s = ? WHERE %s = ?",
                metadata.tableName, column.name, pkColumn);

        try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
            trackLogicalType(metadata, column.name, value, column.sqlType);
            validateColumnWrite(value, column, metadata.tableName);
            writeParameter(stmt, 1, value, column.sqlType);

            final ColumnMetadata pkColMeta = metadata.columns.stream()
                    .filter(c -> c.name.equals(pkColumn)).findFirst().orElseThrow();
            if (pkColMeta.sqlType == Types.INTEGER || pkColMeta.sqlType == Types.BIGINT ||
                    pkColMeta.sqlType == Types.SMALLINT || pkColMeta.sqlType == Types.TINYINT) {
                stmt.setLong(2, Long.parseLong(rowId));
            } else {
                stmt.setString(2, rowId);
            }

            final int updated = stmt.executeUpdate();
            this.space.logger().debug("updated field %s.%s for row %s: %s rows affected",
                    metadata.tableName, fieldName, rowId, updated);
            return updated;
        }
    }

    private int writeRow(final Connection conn, final TableMetadata metadata, final String rowId, final Rec rec) throws SQLException {
        final String pkColumn = metadata.primaryKeys.getFirst();

        // Read the current row to diff against — replaces the old SELECT COUNT(*)
        // and enables writing only columns that actually changed.
        final Rec current = readCurrentRow(conn, metadata, pkColumn, rowId);
        if (current == null)
            return insertRow(conn, metadata, rowId, rec);

        // Diff: only include fields whose values differ from the current row
        final Map<String, Obj> changed = new LinkedHashMap<>();
        final Map<Obj, Obj> currentMap = current.recValue();
        for (final Map.Entry<Obj, Obj> entry : rec.recValue().entrySet()) {
            if (!entry.getKey().isUri()) continue;
            final String fieldName = entry.getKey().asUri().uriValue().name();
            if (fieldName == null || fieldName.isEmpty()) continue;

            final ColumnMetadata column = metadata.columns.stream()
                    .filter(c -> c.name.equalsIgnoreCase(fieldName)).findFirst().orElse(null);
            if (column == null) continue;

            final Obj newValue = entry.getValue();
            final Obj currentValue = currentMap.get(entry.getKey());
            if (!newValue.equals(currentValue))
                changed.put(column.name, newValue);
        }

        if (changed.isEmpty()) {
            this.space.logger().debug("no changes for row %s in %s — skipping UPDATE", rowId, metadata.tableName);
            return 0;
        }

        return updateRowDiffed(conn, metadata, pkColumn, rowId, changed);
    }

    /**
     * Read the current row as a metatron {@link Rec}, or {@code null} if the row
     * does not exist.
     */
    private Rec readCurrentRow(final Connection conn, final TableMetadata metadata,
                               final String pkColumn, final String rowId) throws SQLException {
        final String sql = String.format("SELECT * FROM %s WHERE %s = ?",
                metadata.tableName, pkColumn);
        try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
            final ColumnMetadata pkColMeta = metadata.columns.stream()
                    .filter(c -> c.name.equals(pkColumn)).findFirst().orElseThrow();
            if (pkColMeta.sqlType == Types.INTEGER || pkColMeta.sqlType == Types.BIGINT ||
                    pkColMeta.sqlType == Types.SMALLINT || pkColMeta.sqlType == Types.TINYINT) {
                stmt.setLong(1, Long.parseLong(rowId));
            } else {
                stmt.setString(1, rowId);
            }
            try (final ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? readTableRow(rs, metadata).asRec() : null;
            }
        }
    }

    /**
     * Issue an {@code UPDATE} that {@code SET}s only the columns present in {@code changed}.
     * Called after a diff against the current row determined which fields actually differ.
     */
    private int updateRowDiffed(final Connection conn, final TableMetadata metadata,
                                final String pkColumn, final String rowId,
                                final Map<String, Obj> changed) throws SQLException {
        final List<String> setClauses = new ArrayList<>();
        final List<Tuple.Pair<Obj, ColumnMetadata>> values = new ArrayList<>();

        for (final Map.Entry<String, Obj> entry : changed.entrySet()) {
            final String colName = entry.getKey();
            final Obj value = entry.getValue();
            final ColumnMetadata column = metadata.columns.stream()
                    .filter(c -> c.name.equalsIgnoreCase(colName)).findFirst().orElseThrow();
            trackLogicalType(metadata, column.name, value, column.sqlType);
            setClauses.add(column.name + " = ?");
            values.add(Tuple.Pair.with(value, column));
        }

        final String sql = String.format("UPDATE %s SET %s WHERE %s = ?",
                metadata.tableName, String.join(", ", setClauses), pkColumn);

        try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                final Tuple.Pair<Obj, ColumnMetadata> pair = values.get(i);
                final Obj value = pair.get0();
                final ColumnMetadata column = pair.get1();
                validateColumnWrite(value, column, metadata.tableName);
                writeParameter(stmt, i + 1, value, column.sqlType);
            }

            final ColumnMetadata pkColMeta = metadata.columns.stream()
                    .filter(c -> c.name.equals(pkColumn)).findFirst().orElseThrow();
            if (pkColMeta.sqlType == Types.INTEGER || pkColMeta.sqlType == Types.BIGINT ||
                    pkColMeta.sqlType == Types.SMALLINT || pkColMeta.sqlType == Types.TINYINT) {
                stmt.setLong(values.size() + 1, Long.parseLong(rowId));
            } else {
                stmt.setString(values.size() + 1, rowId);
            }

            final int updated = stmt.executeUpdate();
            this.space.logger().debug("updated row %s in %s: %d of %d columns changed — %d rows affected",
                    rowId, metadata.tableName, changed.size(), metadata.columns.size(), updated);
            return updated;
        }
    }

   /* private int updateRow(final Connection conn, final TableMetadata metadata, final String rowId, final Rec rec) throws SQLException {
        final List<String> setClauses = new ArrayList<>();
        final List<Tuple.Pair<Obj, ColumnMetadata>> values = new ArrayList<>();

        for (final Map.Entry<Obj, Obj> entry : rec.recValue().entrySet()) {
            if (!entry.getKey().isUri()) {
                this.space.logger().warn("ignoring non-uri key in rec: %s", entry.getKey());
                continue;
            }
            final String fieldName = entry.getKey().asUri().uriValue().name();
            if (fieldName == null || fieldName.isEmpty()) {
                this.space.logger().warn("ignoring empty field name for key: %s", entry.getKey());
                continue;
            }

            final ColumnMetadata column = metadata.columns.stream()
                    .filter(c -> c.name.equalsIgnoreCase(fieldName)).findFirst().orElse(null);

            if (column != null) {
                trackLogicalType(metadata, column.name, entry.getValue(), column.sqlType);
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
                final Obj value = pair.get0();
                final ColumnMetadata column = pair.get1();
                validateColumnWrite(value, column, metadata.tableName);
                writeParameter(stmt, i + 1, value, column.sqlType);
            }

            final ColumnMetadata pkColMeta = metadata.columns.stream()
                    .filter(c -> c.name.equals(pkColumn)).findFirst().orElseThrow();
            if (pkColMeta.sqlType == Types.INTEGER || pkColMeta.sqlType == Types.BIGINT ||
                    pkColMeta.sqlType == Types.SMALLINT || pkColMeta.sqlType == Types.TINYINT) {
                stmt.setLong(values.size() + 1, Long.parseLong(rowId));
            } else {
                stmt.setString(values.size() + 1, rowId);
            }

            final int updated = stmt.executeUpdate();
            this.space.logger().debug("updated row in %s with id %s: %s rows affected",
                    metadata.tableName, rowId, updated);
            return updated;
        }
    }*/

    private int insertRow(final Connection conn, final TableMetadata metadata, final String rowId, final Rec rec) throws SQLException {
        final List<String> columnNames = new ArrayList<>();
        final List<Tuple.Pair<Obj, ColumnMetadata>> values = new ArrayList<>();

        final String pkColumn = metadata.primaryKeys.getFirst();
        columnNames.add(pkColumn);
        final ColumnMetadata pkColMeta = metadata.columns.stream()
                .filter(c -> c.name.equals(pkColumn)).findFirst().orElseThrow();
        final Obj pkValue;
        if (pkColMeta.sqlType == Types.INTEGER || pkColMeta.sqlType == Types.BIGINT ||
                pkColMeta.sqlType == Types.SMALLINT || pkColMeta.sqlType == Types.TINYINT) {
            pkValue = jnt(Long.parseLong(rowId));
        } else {
            pkValue = str(rowId);
        }
        values.add(Tuple.Pair.with(pkValue, pkColMeta));

        for (final Map.Entry<Obj, Obj> entry : rec.recValue().entrySet()) {
            if (!entry.getKey().isUri()) {
                this.space.logger().warn("ignoring non-uri key in rec: %s", entry.getKey());
                continue;
            }
            final String fieldName = entry.getKey().asUri().uriValue().name();
            if (fieldName == null || fieldName.isEmpty()) {
                this.space.logger().warn("ignoring empty field name for key: %s", entry.getKey());
                continue;
            }
            if (fieldName.equalsIgnoreCase(pkColumn)) continue;

            final ColumnMetadata column = metadata.columns.stream()
                    .filter(c -> c.name.equalsIgnoreCase(fieldName)).findFirst().orElse(null);

            if (column != null) {
                trackLogicalType(metadata, column.name, entry.getValue(), column.sqlType);
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
                final Obj value = pair.get0();
                final ColumnMetadata column = pair.get1();
                validateColumnWrite(value, column, metadata.tableName);
                writeParameter(stmt, i + 1, value, column.sqlType);
            }
            final int inserted = stmt.executeUpdate();
            this.space.logger().debug("inserted row into %s with id %s: %s rows affected",
                    metadata.tableName, rowId, inserted);
            return inserted;
        }
    }

    @Override
    public Iterator<Space.IdObj> read(final Connection conn, final fURI pattern) throws SQLException {
        final DataPath dp = resolveDataPath(pattern);
        if (dp == null)
            return Collections.emptyIterator();
        final String tableName = dp.collection();
        if (!dp.hasEntry()) {
            if (dp.collectionIsWildcard()) {
                return this.tableSchemas.keySet().stream()
                        .map(s -> {
                            final fURI tableVID = Space.Helper.routeToSpace(pattern.retractPattern().extend(s), this.space.routes());
                            return Space.IdObj.of(tableVID, uri(tableVID, TABLE_TID, null).selfVID(tableVID));
                        }).iterator();
            } else {
                final fURI tableVID = Space.Helper.routeToSpace(pattern, this.space.routes());
                return IteratorUtil.of(Space.IdObj.of(tableVID, uri(tableVID, TABLE_TID, null).selfVID(tableVID)));
            }
        } else {
            final TableMetadata metadata = tableSchemas.get(tableName.toLowerCase());
            if (metadata == null)
                return Collections.emptyIterator();
            final List<Space.IdObj> results = new ArrayList<>();
            if (dp.entryIsWildcard()) {
                if (dp.hasField() && !dp.fieldIsWildcard()) {
                    final String pkColumns = String.join(", ", metadata.primaryKeys);
                    final String fieldName = dp.field();
                    final String sql = String.format("SELECT %s, %s FROM %s", pkColumns, fieldName, metadata.tableName);
                    try (final Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                        while (rs.next()) {
                            final String id = buildRowId(rs, metadata);
                            fURI rowFuri = f(tableName).extend(id).extend(fieldName);
                            final Obj obj = readTableRow(rs, metadata, fieldName);
                            results.add(Space.IdObj.of(rowFuri, obj));
                        }
                    } catch (final SQLException e) {
                        if (e.getErrorCode() == 1054) return IteratorUtil.of();
                        throw MTronException.of(e, "SQL failed: %s", sql);
                    }
                } else {
                    final String sql = String.format("SELECT * FROM %s", metadata.tableName);
                    try (final Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                        while (rs.next()) {
                            final String id = buildRowId(rs, metadata);
                            fURI rowFuri = f(tableName).extend(id);
                            final Obj obj = readTableRow(rs, metadata);
                            results.add(Space.IdObj.of(rowFuri, obj));
                        }
                    } catch (final SQLException e) {
                        if (e.getErrorCode() == 1054) return IteratorUtil.of();
                        throw MTronException.of(e, "SQL failed: %s", sql);
                    }
                }
            } else {
                final String rowId = dp.entry();
                if (metadata.primaryKeys.isEmpty()) {
                    this.space.logger().warn("table %s has no primary key, cannot read specific row", tableName);
                    return Collections.emptyIterator();
                }
                if (dp.hasField()) {
                    final String pkColumn = metadata.primaryKeys.getFirst();
                    final String pkColumns = String.join(", ", metadata.primaryKeys);
                    final String fieldName = dp.field();
                    final String sql = String.format("SELECT %s, %s FROM %s WHERE %s = ?", pkColumns, fieldName, metadata.tableName, pkColumn);
                    try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
                        final ColumnMetadata pkColMeta = metadata.columns.stream()
                                .filter(c -> c.name.equals(pkColumn)).findFirst().orElseThrow();
                        if (pkColMeta.sqlType == Types.INTEGER || pkColMeta.sqlType == Types.BIGINT ||
                                pkColMeta.sqlType == Types.SMALLINT || pkColMeta.sqlType == Types.TINYINT) {
                            if (!CommonUtil.isInt(rowId)) return IteratorUtil.of();
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
                        } catch (final SQLException e) {
                            if (e.getErrorCode() == 1054) return IteratorUtil.of();
                            throw MTronException.of(e, "SQL failed: %s", sql);
                        }
                    }
                } else {
                    final String pkColumn = metadata.primaryKeys.getFirst();
                    final String sql = String.format("SELECT * FROM %s WHERE %s = ?", metadata.tableName, pkColumn);
                    try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
                        final ColumnMetadata pkColMeta = metadata.columns.stream()
                                .filter(c -> c.name.equals(pkColumn)).findFirst().orElseThrow();
                        if (pkColMeta.sqlType == Types.INTEGER || pkColMeta.sqlType == Types.BIGINT ||
                                pkColMeta.sqlType == Types.SMALLINT || pkColMeta.sqlType == Types.TINYINT) {
                            if (!CommonUtil.isInt(rowId)) return IteratorUtil.of();
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
                        } catch (final SQLException e) {
                            if (e.getErrorCode() == 1054) return IteratorUtil.of();
                            throw MTronException.of(e, "SQL failed: %s", sql);
                        }
                    }
                }
            }
            return results.iterator();
        }
    }

    @Override
    public int delete(final Connection conn, final fURI furi) throws SQLException {
        final DataPath dp = DataPath.ofSpaceRelative(furi, null);
        if (!dp.hasEntry()) return 0;

        final String tableName = dp.collection();
        final String rowId = dp.entry();

        if (dp.hasField()) {
            final String column = dp.field();
            final String pkCol = getPrimaryKeyColumn(conn, tableName);
            final String sql = "UPDATE \"" + tableName + "\" SET \"" + column
                    + "\" = NULL WHERE \"" + pkCol + "\" = ?";
            try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, rowId);
                return stmt.executeUpdate();
            }
        }

        final String pkCol = getPrimaryKeyColumn(conn, tableName);
        final String sql = "DELETE FROM \"" + tableName + "\" WHERE \"" + pkCol + "\" = ?";
        try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, rowId);
            return stmt.executeUpdate();
        }
    }

    private String getPrimaryKeyColumn(final Connection conn, final String tableName) throws SQLException {
        try (final ResultSet pkRs = conn.getMetaData().getPrimaryKeys(null, null, tableName)) {
            if (pkRs.next()) {
                return pkRs.getString("COLUMN_NAME");
            }
        }
        return "id";
    }

    @Override
    public String version() {
        return "1.0-existing";
    }

    public void registerTable(final Connection conn, final String tableName) throws SQLException {
        final DatabaseMetaData metaData = conn.getMetaData();
        final String catalog = conn.getCatalog();

        final List<ColumnMetadata> columns = new ArrayList<>();
        try (final ResultSet cols = metaData.getColumns(catalog, null, tableName, "%")) {
            while (cols.next()) {
                columns.add(new ColumnMetadata(
                        cols.getString("COLUMN_NAME"),
                        cols.getInt("DATA_TYPE"),
                        cols.getString("TYPE_NAME"),
                        !"NO".equalsIgnoreCase(cols.getString("IS_NULLABLE"))));
            }
        }

        final List<String> primaryKeys = new ArrayList<>();
        try (final ResultSet pks = metaData.getPrimaryKeys(catalog, null, tableName)) {
            while (pks.next()) {
                primaryKeys.add(pks.getString("COLUMN_NAME"));
            }
        }

        final List<ForeignKeyMetadata> foreignKeys = new ArrayList<>();
        try (final ResultSet fks = metaData.getImportedKeys(catalog, null, tableName)) {
            while (fks.next()) {
                foreignKeys.add(new ForeignKeyMetadata(
                        tableName, fks.getString("FKCOLUMN_NAME"),
                        fks.getString("PKTABLE_NAME"), fks.getString("PKCOLUMN_NAME"),
                        fks.getString("FK_NAME")));
            }
        }

        try (final PreparedStatement ps = conn.prepareStatement(
                "SELECT column_name, ref_table FROM " + MTRON_META_TABLE + " WHERE table_name = ?")) {
            ps.setString(1, tableName);
            try (final ResultSet metaRs = ps.executeQuery()) {
                while (metaRs.next()) {
                    final String col = metaRs.getString("column_name");
                    final String ref = metaRs.getString("ref_table");
                    final boolean alreadyMapped = foreignKeys.stream()
                            .anyMatch(fk -> fk.fromColumn().equalsIgnoreCase(col));
                    if (!alreadyMapped)
                        foreignKeys.add(new ForeignKeyMetadata(tableName, col, ref, "id", null));
                }
            }
        } catch (final SQLException ignored) {
        }

        this.tableSchemas.put(tableName.toLowerCase(),
                new TableMetadata(catalog, tableName, columns, primaryKeys, foreignKeys));
        this.space.logger().info("registered table {{b}}%s{{X}} with %s columns and primary keys %s",
                tableName, columns.size(), primaryKeys);
    }

    public void createTableFromRecord(final Connection conn, final String tableName,
                                      final studio.phaseshift.metatron.isa.m.type.Rec rec) throws SQLException {
        final StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                .append(tableName).append(" (");

        final boolean hasIdField = rec.recValue().keySet().stream()
                .anyMatch(k -> k.isUri() && "id".equalsIgnoreCase(k.asUri().uriValue().name()));

        boolean first = true;
        if (!hasIdField) {
            ddl.append("id INTEGER PRIMARY KEY");
            first = false;
        }

        final Map<String, String> autoFromColumns = new LinkedHashMap<>();

        for (final Map.Entry<Obj, Obj> entry : rec.recValue().entrySet()) {
            if (!entry.getKey().isUri()) continue;
            final String colName = entry.getKey().asUri().uriValue().name();
            if (colName == null || colName.isEmpty()) continue;
            if (!first) ddl.append(", ");
            first = false;

            final Obj val = entry.getValue();
            if (val.isAutoFrom()) {
                final fURI refURI = val.asInst().arg(0).uriValue();
                final String refTable;
                if (refURI.test(space.pattern())) {
                    refTable = refURI.segments().getFirst();
                } else {
                    refTable = refURI.segments(List.of(refURI.segments().getFirst())).toString();
                }
                autoFromColumns.put(colName, refTable);
                ddl.append(colName).append(" INTEGER");
            } else {
                final String sqlType;
                if (val.isBool()) sqlType = "BOOLEAN";
                else if (val.isInt()) sqlType = "INTEGER";
                else if (val.isReal()) sqlType = "REAL";
                else sqlType = "TEXT";
                ddl.append(colName).append(" ").append(sqlType);
                if ("id".equalsIgnoreCase(colName)) {
                    ddl.append(" PRIMARY KEY");
                }
            }
        }
        ddl.append(")");

        this.space.logger().info("creating table {{b}}%s{{X}}: %s", tableName, ddl);
        try (final Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(ddl.toString());
        }

        if (!autoFromColumns.isEmpty()) {
            ensureMetaTable(conn);
        }
        for (final Map.Entry<String, String> af : autoFromColumns.entrySet()) {
            try (final PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM " + MTRON_META_TABLE + " WHERE table_name = ? AND column_name = ?")) {
                del.setString(1, tableName);
                del.setString(2, af.getKey());
                del.executeUpdate();
            }
            try (final PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO " + MTRON_META_TABLE + " (table_name, column_name, ref_table) VALUES (?, ?, ?)")) {
                ins.setString(1, tableName);
                ins.setString(2, af.getKey());
                ins.setString(3, af.getValue());
                ins.executeUpdate();
            }
        }

        registerTable(conn, tableName);
    }

    public boolean isTablePath(final fURI furi) {
        return resolveDataPath(furi.asNode()) != null;
    }

    public Set<String> getTableNames() {
        return tableSchemas.keySet();
    }

    public List<TableMetadata> getTableMetadata() {
        return new ArrayList<>(tableSchemas.values());
    }

    public ForeignKeyMetadata getForeignKeyForColumn(final String tableName, final String columnName) {
        final TableMetadata metadata = tableSchemas.get(tableName.toLowerCase());
        if (metadata == null) return null;
        return metadata.foreignKeys().stream()
                .filter(fk -> fk.fromColumn().equalsIgnoreCase(columnName))
                .findFirst().orElse(null);
    }

    /*public List<ForeignKeyMetadata> getForeignKeysForTable(final String tableName) {
        final TableMetadata metadata = tableSchemas.get(tableName.toLowerCase());
        if (metadata == null) return Collections.emptyList();
        return metadata.foreignKeys();
    }

    public List<ForeignKeyMetadata> getAllForeignKeys() {
        return tableSchemas.values().stream()
                .flatMap(table -> table.foreignKeys().stream())
                .toList();
    }*/
}
