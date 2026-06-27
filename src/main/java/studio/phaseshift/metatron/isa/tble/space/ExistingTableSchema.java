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
import studio.phaseshift.metatron.isa.m.type.Type;
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
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;


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
    private SQLSchemaGenerator schemaGenerator;

    /**
     * Inject the schema generator so that table dereferences return instset-encoded
     * Types instead of SQL-specific TABLE_TID URIs.
     */
    public void setSchemaGenerator(final SQLSchemaGenerator schemaGenerator) {
        this.schemaGenerator = schemaGenerator;
        // Register any FKs discovered before the generator was set
        for (final FKInfo fk : pendingFKs) {
            schemaGenerator.registerFK(fk.fromTable(), fk.fromColumn(), fk.toTable(), fk.toColumn());
        }
        pendingFKs.clear();
    }

    private final String excludeTableName;

    /**
     * Logical type overrides: tableName → (columnName → mtronTypeTID).
     */
    private final Map<String, Map<String, fURI>> logicalTypes = new LinkedHashMap<>();

    /**
     * Exposed for {@link SQLSchemaGenerator} so it can produce correct instset types.
     */
    public Map<String, Map<String, fURI>> getLogicalTypes() {
        return logicalTypes;
    }

    /**
     * Single-column lookup for the schema generator.
     */
    public fURI getLogicalType(final String tableName, final String columnName) {
        final Map<String, fURI> tableTypes = logicalTypes.get(tableName.toLowerCase());
        return tableTypes != null ? tableTypes.get(columnName.toLowerCase()) : null;
    }

    public record TableMetadata(String dbName, String tableName, List<ColumnMetadata> columns,
                                List<String> primaryKeys) {
    }

    public record ColumnMetadata(String name, int sqlType, String typeName, boolean nullable, String columnDefault) {
        public ColumnMetadata(String name, int sqlType, String typeName) {
            this(name, sqlType, typeName, true, null);
        }

        /**
         * true when the column default looks like a JSON array ({@code [...]}, {@code '[...]'}, or {@code json_array()}).
         */
        public boolean isDefaultJSONArray() {
            if (columnDefault == null) return false;
            final String s = columnDefault.strip().toLowerCase();
            return s.startsWith("[") || s.startsWith("'[") || s.startsWith("json_array");
        }

        /**
         * true when the column default looks like a JSON object ({@code \{...\}}, {@code '\{...\}'}, or {@code json_object()}).
         */
        public boolean isDefaultJSONObject() {
            if (columnDefault == null) return false;
            final String s = columnDefault.strip().toLowerCase();
            return s.startsWith("{") || s.startsWith("'{") || s.startsWith("json_object");
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
                    "cannot write %s to column '%s.%s': column type is %s (numeric). "
                            + "numbers, strings, and booleans are supported.",
                    value.tid().name(), tableName, column.name(), column.typeName());
        }
        // Non-numeric string into a numeric column
        if (value.isStr() && column.isNumeric()) {
            try {
                Double.parseDouble(value.asStr().jvm());
            } catch (final NumberFormatException e) {
                throw MTronException.of(
                        "cannot write string '%s' to column '%s.%s': column type is %s (numeric).",
                        value.asStr().jvm(), tableName, column.name(), column.typeName());
            }
        }
    }

    /**
     * Temporary FK storage for FKs discovered before the schema generator is set.
     * Cleared once all FKs are registered with the generator.
     */
    private final List<FKInfo> pendingFKs = new ArrayList<>();

    /**
     * Simple FK info holder used during discovery before the generator is available.
     */
    private record FKInfo(String fromTable, String fromColumn, String toTable, String toColumn) {
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
                        final boolean nullable = !"NO".equalsIgnoreCase(cols.getString("IS_NULLABLE"));
                        final String columnDefault = cols.getString("COLUMN_DEF");
                        columns.add(new ColumnMetadata(
                                cols.getString("COLUMN_NAME"),
                                cols.getInt("DATA_TYPE"),
                                cols.getString("TYPE_NAME"),
                                nullable, columnDefault));
                    }
                }
                final List<String> primaryKeys = new ArrayList<>();
                try (final ResultSet pks = metaData.getPrimaryKeys(catalog, null, tableName)) {
                    while (pks.next()) {
                        primaryKeys.add(pks.getString("COLUMN_NAME"));
                    }
                }

                // Discover FKs and store temporarily — will register with schemaGenerator later
                discoverReferencesAndRegister(conn, catalog, tableName);

                this.tableSchemas.put(tableName.toLowerCase(),
                        new TableMetadata(catalog, tableName, columns, primaryKeys));
                this.space.logger().debug("discovered table: %s with %s columns and %s primary keys",
                        tableName, columns.size(), primaryKeys.size());
            }
        }
        this.space.logger().info("discovered {{b}}%s{{X}} tables: %s", tableSchemas.size(), tableSchemas.keySet());
        ensureMetaTable(conn);
        loadMetaForeignKeys(conn);
        // Discover FKs by naming convention: a column named after another table
        // (e.g. company in people → companies) is inferred as an FK.
        discoverFKByConvention(conn);
    }

    /**
     * Infer FK relationships by naming convention.
     * A column whose name matches another table name (case-insensitive,
     * with or without trailing "s" or "_id") is registered as a foreign key
     * pointing to that table.  This catches patterns like
     * {@code people.company → companies} without requiring SQL-level
     * REFERENCES constraints or {@link #MTRON_META_TABLE} entries.
     */
    private void discoverFKByConvention(final Connection conn) {
        final Set<String> tableNames = new HashSet<>();
        for (final String key : this.tableSchemas.keySet()) {
            final String tn = this.tableSchemas.get(key).tableName().toLowerCase();
            tableNames.add(tn);
        }
        for (final TableMetadata meta : this.tableSchemas.values()) {
            final String srcTbl = meta.tableName().toLowerCase();
            for (final ColumnMetadata col : meta.columns()) {
                final String colName = col.name().toLowerCase();
                if (meta.primaryKeys().stream().anyMatch(pk -> pk.equalsIgnoreCase(colName)))
                    continue;
                // Already registered (from JDBC metadata or _mtron_meta)
                if (isFKAlreadyRegistered(srcTbl, colName))
                    continue;
                // Try matching column name to table name (with convention variants)
                final String refTable = findReferencedTable(colName, tableNames);
                if (refTable != null) {
                    if (schemaGenerator != null) {
                        schemaGenerator.registerFK(meta.tableName(), col.name(), refTable, "id");
                    } else {
                        pendingFKs.add(new FKInfo(meta.tableName(), col.name(), refTable, "id"));
                    }
                    this.space.logger().info("inferred FK by convention: {{b}}%s.%s{{X}} → {{b}}%s{{X}}",
                            meta.tableName(), col.name(), refTable);
                }
            }
        }
    }

    /**
     * Match a column name against known table names using naming conventions:
     * exact match, column+ "s", column with trailing "_id" stripped, or column
     * without trailing "id".
     */
    private static String findReferencedTable(final String columnName, final Set<String> tableNames) {
        // Exact match
        if (tableNames.contains(columnName))
            return columnName;
        // column + "s": company + s → companies?  No, "companys" != "companies".
        // But try it for regular plurals (e.g. user + s → users).
        if (tableNames.contains(columnName + "s"))
            return columnName + "s";
        // y → ies: company → companies, category → categories
        if (columnName.endsWith("y") && columnName.length() > 1) {
            final String iesForm = columnName.substring(0, columnName.length() - 1) + "ies";
            if (tableNames.contains(iesForm))
                return iesForm;
        }
        // column without trailing "id": categoryid → category/categories
        if (columnName.endsWith("id") && columnName.length() > 2) {
            final String stem = columnName.substring(0, columnName.length() - 2);
            if (!stem.isEmpty() && tableNames.contains(stem))
                return stem;
            if (!stem.isEmpty() && tableNames.contains(stem + "s"))
                return stem + "s";
            if (stem.endsWith("y") && stem.length() > 1) {
                final String iesStem = stem.substring(0, stem.length() - 1) + "ies";
                if (tableNames.contains(iesStem))
                    return iesStem;
            }
        }
        // column without trailing "_id": company_id → company/companies
        if (columnName.endsWith("_id") && columnName.length() > 3) {
            final String stem = columnName.substring(0, columnName.length() - 3);
            if (tableNames.contains(stem))
                return stem;
            if (tableNames.contains(stem + "s"))
                return stem + "s";
            if (stem.endsWith("y") && stem.length() > 1) {
                final String iesStem = stem.substring(0, stem.length() - 1) + "ies";
                if (tableNames.contains(iesStem))
                    return iesStem;
            }
        }
        return null;
    }

    /**
     * Discover FK references from JDBC metadata and register them with the schema
     * generator (or store temporarily if the generator isn't set yet).
     */
    private void discoverReferencesAndRegister(final Connection conn, final String catalog,
                                               final String tableName) throws SQLException {
        final DatabaseMetaData metaData = conn.getMetaData();
        try (final ResultSet fks = metaData.getImportedKeys(catalog, null, tableName)) {
            while (fks.next()) {
                final String fromCol = fks.getString("FKCOLUMN_NAME");
                final String toTable = fks.getString("PKTABLE_NAME");
                final String toCol = fks.getString("PKCOLUMN_NAME");
                if (schemaGenerator != null) {
                    schemaGenerator.registerFK(tableName, fromCol, toTable, toCol);
                } else {
                    // Store temporarily; will register when generator is set
                    pendingFKs.add(new FKInfo(tableName, fromCol, toTable, toCol));
                }
            }
        }
    }

    /**
     * Load FK mappings from the _mtron_meta table and register them with the
     * schema generator (or store temporarily if the generator isn't set yet).
     */
    private void loadMetaForeignKeys(final Connection conn) {
        try (final Statement stmt = conn.createStatement();
             final ResultSet rs = stmt.executeQuery(
                     "SELECT table_name, column_name, ref_table FROM " + MTRON_META_TABLE)) {
            while (rs.next()) {
                final String tbl = rs.getString("table_name");
                final String col = rs.getString("column_name");
                final String ref = rs.getString("ref_table");
                if (!this.tableSchemas.containsKey(tbl.toLowerCase())) continue;
                // Check if already discovered from JDBC metadata
                if (isFKAlreadyRegistered(tbl, col)) continue;
                if (schemaGenerator != null) {
                    schemaGenerator.registerFK(tbl, col, ref, "id");
                } else {
                    pendingFKs.add(new FKInfo(tbl, col, ref, "id"));
                }
            }
        } catch (final SQLException ignored) {
        }
    }

    /**
     * Lazy FK inference by naming convention, triggered at read time.
     * If a column name matches another table (e.g. "company" in "people"
     * matches table "companies"), registers the FK on the fly and returns
     * the FKTarget.  This catches tables created after init.
     */
    private SQLSchemaGenerator.FKTarget inferFKByConvention(final String tableName, final String columnName) {
        if (schemaGenerator == null)
            return null;
        final Set<String> knownTables = this.tableSchemas.keySet();
        final String colName = columnName.toLowerCase();
        final String refTable = findReferencedTable(colName, knownTables);
        if (refTable != null) {
            schemaGenerator.registerFK(tableName, columnName, refTable, "id");
            this.space.logger().info("lazy foreign key by convention: {{b}}%s.%s{{X}} → {{b}}%s{{X}}",
                    tableName, columnName, refTable);
            return schemaGenerator.getFKTarget(tableName, columnName);
        }
        return null;
    }

    /**
     * Check if an FK is already registered (either in generator or pending list).
     */
    private boolean isFKAlreadyRegistered(final String tbl, final String col) {
        if (schemaGenerator != null && schemaGenerator.getFKTarget(tbl, col) != null) {
            return true;
        }
        return pendingFKs.stream().anyMatch(fk ->
                fk.fromTable.equalsIgnoreCase(tbl) && fk.fromColumn.equalsIgnoreCase(col));
    }

    /**
     * Resolve a space-relative fURI into a {@link DataPath} when the
     * table is known to this schema.  Returns {@code null} when the
     * table name is not a recognized table.
     */
    private DataPath resolveDataPath(final fURI furi) {
        final DataPath dp = DataPath.withoutDB(furi);
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
        // Check registered FKs (from JDBC metadata, _mtron_meta, or naming convention)
        SQLSchemaGenerator.FKTarget fk = getFKTarget(tableName, col.name);
        // Lazy naming-convention check: tables may have been added after init
        if (fk == null) {
            fk = inferFKByConvention(tableName, col.name);
        }
        if (fk != null) {
            final Object fkValue = rs.getObject(col.name);
            if (fkValue != null && !rs.wasNull()) {
                final fURI referencedPath = buildFKReferencePath(fk.targetPath(), fkValue.toString());
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
                    return bool(rs.getInt(col.name) != 0);
                }
            }
        }

        if ("BOOLEAN".equalsIgnoreCase(col.typeName) &&
                (col.sqlType == Types.INTEGER || col.sqlType == Types.TINYINT ||
                        col.sqlType == Types.SMALLINT || col.sqlType == Types.BIT)) {
            final Object value = rs.getObject(col.name);
            if (value == null || rs.wasNull()) return noobj();
            return bool(rs.getInt(col.name) != 0);
        }
        return readColumn(rs, col.name, col.sqlType);
    }

    /**
     * Build the reference path for an FK column value.
     * The targetPath is like "office/+/officeCode" or "db:office/+/officeCode".
     * We extract the table part (before /+) and append the row ID.
     */
    private fURI buildFKReferencePath(final String targetPath, final String rowId) {
        final int sepIdx = targetPath.indexOf("/+");
        if (sepIdx > 0) {
            final String refTable = targetPath.substring(0, sepIdx);
            if (refTable.indexOf(':') >= 0) {
                return f(refTable).extend(rowId);
            } else {
                return this.space.pattern().retractPattern()
                        .extend(refTable)
                        .extend(rowId);
            }
        }
        // Fallback: use full path
        return f(targetPath).extend(rowId);
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
            return 0; // collection-level write with no entry — no-op
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
        if (rowId != null && rowId.startsWith("+")) {
            final Map<Obj, Obj> recMap = new LinkedHashMap<>();
            for (int i = 0; i < Math.min(lst.jvm().size(), metadata.columns.size()); i++)
                recMap.put(uri(metadata.columns.get(i).name), lst.jvm().get(i));
            return insertRowAuto(conn, metadata, rec(recMap));
        }
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
            this.space.logger().debug("using primary key from uri: %s = %s", pkColumn, pkValue);
        }

        final Rec current = readCurrentRow(conn, metadata, pkColumn, pkValue);
        if (current == null)
            return insertRow(conn, metadata, pkValue, rec);

        // Diff: only write columns that changed from the current row
        final Map<String, Obj> changed = new LinkedHashMap<>();
        final Map<Obj, Obj> currentMap = current.recValue();
        for (final Map.Entry<Obj, Obj> entry : rec.recValue().entrySet()) {
            final String fieldName = entry.getKey().asUri().uriValue().name();
            ColumnMetadata col = metadata.columns.stream()
                    .filter(c -> c.name.equalsIgnoreCase(fieldName)).findFirst().orElse(null);
            if (col == null) {
                // On-the-fly column addition via UPDATE (same as INSERT path)
                col = addColumnOnTheFly(conn, metadata, fieldName, entry.getValue());
            }
            final Obj currentVal = currentMap.get(entry.getKey());
            if (!entry.getValue().equals(currentVal))
                changed.put(col.name, entry.getValue());
        }

        if (changed.isEmpty()) return 0;
        return updateRowDiffed(conn, metadata, pkColumn, pkValue, changed);
    }

    /**
     * Record the exact metatron TID of every value written so the instset
     * schema can preserve user-defined type refinements (e.g. {@code nat::T}
     * instead of plain {@code int::T}, {@code uri::T} instead of {@code str::T}).
     */
    private void trackLogicalType(final TableMetadata metadata, final String columnName,
                                  final Obj value, final int sqlType) {
        this.logicalTypes
                .computeIfAbsent(metadata.tableName.toLowerCase(), k -> new LinkedHashMap<>())
                .put(columnName.toLowerCase(), value.tid());
    }

    private int writeField(final Connection conn, final TableMetadata metadata, final String rowId,
                           final String fieldName, final Obj value) throws SQLException {
        final ColumnMetadata column = metadata.columns.stream()
                .filter(c -> c.name.equalsIgnoreCase(fieldName))
                .findFirst()
                .orElseThrow(() -> new SQLException("column not found: " + fieldName));

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
        // +?incrq and similar wildcard entry patterns tell the DB to
        // auto-generate the primary key (AUTO_INCREMENT / SERIAL).
        if (rowId != null && rowId.startsWith("+"))
            return insertRowAuto(conn, metadata, rec);

        final String pkColumn = metadata.primaryKeys.getFirst();

        // Read the current row to diff against
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

        // Also detect columns that exist in the current row but are absent from
        // the incoming rec — these were explicitly unset via [field=>none] (which
        // Rec.at() converts to a map-remove, dropping the key entirely).
        // Such fields should be set to SQL NULL in the database.
        for (final Map.Entry<Obj, Obj> entry : currentMap.entrySet()) {
            if (!entry.getKey().isUri()) continue;
            final String fieldName = entry.getKey().asUri().uriValue().name();
            if (fieldName == null || fieldName.isEmpty()) continue;
            if (metadata.primaryKeys.stream().anyMatch(pk -> pk.equalsIgnoreCase(fieldName)))
                continue;

            final boolean presentInNewRec = rec.recValue().keySet().stream()
                    .anyMatch(k -> k.isUri() && k.asUri().uriValue().name().equalsIgnoreCase(fieldName));
            if (!presentInNewRec) {
                // Column was present in the current row but was removed from the incoming
                // rec — write SQL NULL to clear it.
                final ColumnMetadata column = metadata.columns.stream()
                        .filter(c -> c.name.equalsIgnoreCase(fieldName)).findFirst().orElse(null);
                if (column != null && !column.nullable()) {
                    this.space.logger().warn("cannot set %s.%s to NULL: column has NOT NULL constraint",
                            metadata.tableName, fieldName);
                    continue;
                }
                changed.put(fieldName, noobj());
            }
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

    /**
     * ALTER TABLE on the fly — adds a missing column so writes never lose data
     * just because the first-written record didn't include this field.
     * <p>
     * The column is always nullable (existing rows get NULL) and the SQL type
     * is inferred from the metatron value being written, using the same rules
     * as {@link #createTableFromRecord}.
     */
    private ColumnMetadata addColumnOnTheFly(final Connection conn, final TableMetadata metadata,
                                             final String columnName, final Obj value) throws SQLException {
        final String sqlType;
        final int jdbcType;
        if (value.isBool()) {
            sqlType = "BOOLEAN";
            jdbcType = Types.BOOLEAN;
        } else if (value.isInt()) {
            sqlType = "INTEGER";
            jdbcType = Types.INTEGER;
        } else if (value.isReal()) {
            sqlType = "REAL";
            jdbcType = Types.REAL;
        } else {
            sqlType = "TEXT";
            jdbcType = Types.VARCHAR;
        }

        final String ddl = String.format("ALTER TABLE %s ADD COLUMN %s %s",
                metadata.tableName, columnName, sqlType);
        this.space.logger().info("adding column on the fly: %s", ddl);
        try (final Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(ddl);
        } catch (final SQLException e) {
            // Race: another writer added the same column between our metadata
            // check and this DDL.  The column now exists — re-read its info
            // from the DB so we can proceed with the insert.
            try (final java.sql.ResultSet cols = conn.getMetaData().getColumns(
                    metadata.dbName, null, metadata.tableName, columnName)) {
                if (cols.next()) {
                    final int sqlType2 = cols.getInt("DATA_TYPE");
                    final String typeName2 = cols.getString("TYPE_NAME");
                    final ColumnMetadata raced = new ColumnMetadata(columnName, sqlType2, typeName2);
                    metadata.columns.add(raced);
                    this.space.logger().info("column {{b}}%s{{X}}.%s already exists (race); using DB-reported type %s",
                            metadata.tableName, columnName, typeName2);
                    return raced;
                }
            }
            throw e;
        }

        final ColumnMetadata newCol = new ColumnMetadata(columnName, jdbcType, sqlType);
        metadata.columns.add(newCol);
        trackLogicalType(metadata, columnName, value, jdbcType);
        this.space.onTableChanged(metadata.tableName);
        return newCol;
    }

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
                final ColumnMetadata newCol = addColumnOnTheFly(conn, metadata, fieldName, entry.getValue());
                columnNames.add(newCol.name);
                values.add(Tuple.Pair.with(entry.getValue(), newCol));
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

    /**
     * INSERT a new row letting the database assign the primary key value
     * (AUTO_INCREMENT, SERIAL, etc.).  The PK column is omitted from the
     * INSERT statement entirely — the DB fills it in.
     */
    private int insertRowAuto(final Connection conn, final TableMetadata metadata,
                              final Rec rec) throws SQLException {
        final List<String> columnNames = new ArrayList<>();
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
            // Skip PK columns and user-provided 'id' — DB auto-assigns
            if (metadata.primaryKeys.contains(fieldName) || "id".equalsIgnoreCase(fieldName))
                continue;

            final ColumnMetadata column = metadata.columns.stream()
                    .filter(c -> c.name.equalsIgnoreCase(fieldName)).findFirst().orElse(null);
            if (column != null) {
                trackLogicalType(metadata, column.name, entry.getValue(), column.sqlType);
                columnNames.add(column.name);
                values.add(Tuple.Pair.with(entry.getValue(), column));
            } else {
                final ColumnMetadata newCol = addColumnOnTheFly(conn, metadata, fieldName, entry.getValue());
                columnNames.add(newCol.name);
                values.add(Tuple.Pair.with(entry.getValue(), newCol));
            }
        }

        if (columnNames.isEmpty()) {
            this.space.logger().warn("no valid columns for auto-insert into table %s", metadata.tableName);
            return 0;
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
            this.space.logger().debug("auto-inserted row into %s: %s rows affected",
                    metadata.tableName, inserted);
            return inserted;
        }
    }

    /**
     * Ensure the table exists (creating it if needed via
     * {@code createTableFromRecord}), then do an auto-increment INSERT.
     * Called from {@link tbleIncrQ#onPreWrite} — the QProc handles the
     * write so the normal {@code directWriter} path is never reached.
     */
    public void ensureTableAndInsert(final Connection conn, final String tableName,
                                     final Rec rec) throws java.sql.SQLException {
        final boolean isNew = !tableSchemas.containsKey(tableName.toLowerCase());
        if (isNew)
            createTableFromRecord(conn, tableName, rec);
        final TableMetadata metadata = tableSchemas.get(tableName.toLowerCase());
        if (metadata == null)
            throw new java.sql.SQLException("failed to create table: " + tableName);
        insertRowAuto(conn, metadata, rec);
        // Update the instset type AFTER the first insert so logical type
        // tracking (e.g. URI stored in TEXT) has already fired.
        if (isNew)
            this.space.onTableChanged(tableName);
    }

    @Override
    public Iterator<Space.IdObj> read(final Connection conn, final fURI pattern) throws SQLException {
        final DataPath dp = resolveDataPath(pattern);
        if (dp == null)
            return Collections.emptyIterator();
        final String tableName = dp.collection();
        if (!dp.hasEntry()) {
            if (dp.collectionIsWildcard()) {
                /*
                 * Wildcard table query — return the instset-encoded Type for each
                 * discovered table.  This makes the instset schema the single source
                 * of truth (no separate SQL-specific TABLE_TID encoding).
                 */
                // Merge auto-generated types with user-declared instset types
                final Set<Type> all = new LinkedHashSet<>(
                        this.schemaGenerator.getTableTypes());
                if (space.schemaInstset() != null)
                    all.addAll(space.schemaInstset().types());
                return all.stream()
                        .map(t -> Space.IdObj.of(t.vid(), t))
                        .iterator();
            } else {
                /*
                 * Exact table dereference — check the generator first, then
                 * fall back to the schema instset for user-declared types.
                 */
                final String tn = dp.collection().toLowerCase();
                final Type tableType = this.schemaGenerator.getTableType(tn);
                if (tableType != null) {
                    return IteratorUtil.of(Space.IdObj.of(tableType.vid(), tableType));
                }
                if (space.schemaInstset() != null) {
                    final Type declared = space.schemaInstset().types().stream()
                            .filter(t -> t.vid().name().equalsIgnoreCase(tn))
                            .findFirst().orElse(null);
                    if (declared != null)
                        return IteratorUtil.of(Space.IdObj.of(declared.vid(), declared));
                }
                return Collections.emptyIterator();
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
        final DataPath dp = DataPath.withoutDB(furi);
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
                final boolean nullable_ = !"NO".equalsIgnoreCase(cols.getString("IS_NULLABLE"));
                final String columnDefault_ = cols.getString("COLUMN_DEF");
                columns.add(new ColumnMetadata(
                        cols.getString("COLUMN_NAME"),
                        cols.getInt("DATA_TYPE"),
                        cols.getString("TYPE_NAME"),
                        nullable_, columnDefault_));
            }
        }

        final List<String> primaryKeys = new ArrayList<>();
        try (final ResultSet pks = metaData.getPrimaryKeys(catalog, null, tableName)) {
            while (pks.next()) {
                primaryKeys.add(pks.getString("COLUMN_NAME"));
            }
        }

        // Discover FKs and register with generator
        discoverReferencesAndRegister(conn, catalog, tableName);

        // Also check _mtron_meta for additional FK mappings
        try (final PreparedStatement ps = conn.prepareStatement(
                "SELECT column_name, ref_table FROM " + MTRON_META_TABLE + " WHERE table_name = ?")) {
            ps.setString(1, tableName);
            try (final ResultSet metaRs = ps.executeQuery()) {
                while (metaRs.next()) {
                    final String col = metaRs.getString("column_name");
                    final String ref = metaRs.getString("ref_table");
                    if (!isFKAlreadyRegistered(tableName, col)) {
                        if (schemaGenerator != null) {
                            schemaGenerator.registerFK(tableName, col, ref, "id");
                        } else {
                            pendingFKs.add(new FKInfo(tableName, col, ref, "id"));
                        }
                    }
                }
            }
        } catch (final SQLException ignored) {
        }

        this.tableSchemas.put(tableName.toLowerCase(),
                new TableMetadata(catalog, tableName, columns, primaryKeys));
        this.space.logger().info("created table {{b}}%s{{X}} with %s columns and primary keys %s",
                tableName, columns.size(), primaryKeys);
    }

    public void createTableFromRecord(final Connection conn, final String tableName,
                                      final studio.phaseshift.metatron.isa.m.type.Rec rec) throws SQLException {
        final StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                .append(tableName).append(" (");

        // Always use auto-increment integer PK — never let the user's
        // Rec dictate the PK type (avoids TEXT-in-key errors on MariaDB).
        // Dialect: SERIAL for PostgreSQL, AUTO_INCREMENT for MariaDB/MySQL,
        // plain INTEGER PRIMARY KEY for SQLite (which auto-increments it).
        if (this.space instanceof studio.phaseshift.metatron.isa.tble.tbleSpace tble) {
            final String b = tble.backend();
            if (b != null && b.contains("postgresql"))
                ddl.append("id SERIAL PRIMARY KEY");
            else if (b != null && (b.contains("mariadb") || b.contains("mysql")))
                ddl.append("id INTEGER PRIMARY KEY AUTO_INCREMENT");
            else
                ddl.append("id INTEGER PRIMARY KEY");
        } else {
            ddl.append("id INTEGER PRIMARY KEY");
        }

        boolean first = false; // id column already added

        final Map<String, String> autoFromColumns = new LinkedHashMap<>();

        for (final Map.Entry<Obj, Obj> entry : rec.recValue().entrySet()) {
            if (!entry.getKey().isUri()) continue;
            final String colName = entry.getKey().asUri().uriValue().name();
            if (colName == null || colName.isEmpty()) continue;
            if ("id".equalsIgnoreCase(colName)) continue;
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

        this.space.logger().debug("creating table {{b}}%s{{X}}: %s", tableName, ddl);
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

    /**
     * Query the schema generator for FK info for the given table+column.
     * Returns the FK target (e.g., "office/+/officeCode") or null if not an FK.
     */
    public SQLSchemaGenerator.FKTarget getFKTarget(final String tableName, final String columnName) {
        if (schemaGenerator == null) return null;
        return schemaGenerator.getFKTarget(tableName, columnName);
    }
}
