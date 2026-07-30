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
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
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
import static studio.phaseshift.metatron.isa.m.mInstSet.BOOL_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.CODE_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.INT_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.LST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REAL_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REL_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.STR_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
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
    static final String TID_COLUMN = "_tid";

    private final tbleSpace space;
    private final Map<String, TableMetadata> tableSchemas = new LinkedHashMap<>();
    private SQLSchemaGenerator schemaGenerator;

    /**
     * Inject the schema generator so that table dereferences return instset-encoded
     * Types instead of SQL-specific TABLE_TID URIs.
     */
    public void setSchemaGenerator(final SQLSchemaGenerator schemaGenerator) {
        this.schemaGenerator = schemaGenerator;
        for (final FKInfo fk : pendingFKs)
            schemaGenerator.registerFK(fk.fromTable(), fk.fromColumn(), fk.toTable(), fk.toColumn());
        pendingFKs.clear();
    }

    private final String excludeTableName;

    /**
     * Table-level TIDs discovered from the {@code $table} sentinel row in
     * {@link #_mtron_meta}.  Keyed by lowercase table name.
     */
    private final Map<String, fURI> tableTids = new LinkedHashMap<>();

    /**
     * Exposed for {@link SQLSchemaGenerator} so it can produce correct instset types.
     */
    public Map<String, Map<String, fURI>> getLogicalTypes() {
        return logicalTypes;
    }

    /**
     * Table-level TIDs discovered from the {@code $table} sentinel in
     * {@link #MTRON_META_TABLE}.  Exposed for {@link SQLSchemaGenerator}.
     */
    public Map<String, fURI> getTableTids() {
        return tableTids;
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
            return isIntegerType() ||
                    sqlType == Types.REAL || sqlType == Types.FLOAT ||
                    sqlType == Types.DOUBLE || sqlType == Types.DECIMAL ||
                    sqlType == Types.NUMERIC;
        }

        public boolean isIntegerType() {
            return sqlType == Types.INTEGER || sqlType == Types.BIGINT ||
                    sqlType == Types.SMALLINT || sqlType == Types.TINYINT;
        }
    }

    /**
     * Validates that {@code value} is compatible with the column's schema before writing.
     * Throws {@link MTronException} with a clear message for constraint violations
     * and type mismatches that would otherwise fail with cryptic JDBC errors.
     */
    private String q(final String identifier) {
        if (identifier == null) return null;
        final String backend = this.space.backend();
        final char quote = (backend != null && (backend.contains("mysql") || backend.contains("mariadb"))) ? '`' : '"';
        return quote + identifier + quote;
    }

    private void validateColumnWrite(final Obj value, final ColumnMetadata column,
                                     final String tableName) {
        // NULL into a NOT NULL column
        if (value.isNone() && !column.nullable()) {
            throw MTronException.of(
                    "Cannot set column '%s.%s' to NULL: column has a NOT NULL constraint",
                    tableName, column.name());
        }
        // Complex types (Rec, Lst, Objs) into scalar columns: coax to TEXT.
        // The writeParameter call site handles serialization to VARCHAR.
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
        super(new LinkedHashMap<>());  // logicalTypes shared with parent ObjSQLSerializer
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
                            "  base_vid    VARCHAR(128) NOT NULL, " +
                            "  obj_tid     VARCHAR(512), " +
                            "  ref_table   VARCHAR(512), " +
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
        loadMetaTable(conn);
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
                // Try matching column name to table name (with convention variants).
                // Skip when the matched table is the same table — a column named
                // after its own table (e.g. 'concept' in 'concept') is a data
                // column, not a self-referencing FK.
                final String refTable = findReferencedTable(colName, tableNames);
                if (refTable != null && !refTable.equalsIgnoreCase(srcTbl)) {
                    registerOrPendingFK(meta.tableName(), col.name(), refTable, "id");
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
                registerOrPendingFK(tableName,
                        fks.getString("FKCOLUMN_NAME"),
                        fks.getString("PKTABLE_NAME"),
                        fks.getString("PKCOLUMN_NAME"));
            }
        }
    }

    /**
     * Load column type metadata and FK mappings from the {@link #MTRON_META_TABLE}
     * and register them with the schema generator (or store temporarily if the
     * generator isn't set yet).
     * <p>
     * Also populates {@link #logicalTypes} and {@link #tableTids} from the
     * persisted metadata so types survive restarts.
     */
    private void loadMetaTable(final Connection conn) {
        try (final Statement stmt = conn.createStatement();
             final ResultSet rs = stmt.executeQuery(
                     "SELECT table_name, column_name, base_vid, obj_tid, ref_table FROM " + MTRON_META_TABLE)) {
            while (rs.next()) {
                final String tbl = rs.getString("table_name");
                // Only load metadata for tables we've already discovered.
                if (!this.tableSchemas.containsKey(tbl.toLowerCase())) continue;
                processMetaRow(tbl, rs.getString("column_name"),
                        rs.getString("obj_tid"), rs.getString("ref_table"));
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
        if (refTable != null && !refTable.equalsIgnoreCase(tableName)) {
            schemaGenerator.registerFK(tableName, columnName, refTable, "id");
            this.space.logger().info("lazy foreign key by convention: {{b}}%s.%s{{X}} → {{b}}%s{{X}}",
                    tableName, columnName, refTable);
            return schemaGenerator.getFKTarget(tableName, columnName);
        }
        return null;
    }

    /**
     * Process a single row from {@link #MTRON_META_TABLE}, populating
     * {@link #tableTids}, {@link #logicalTypes}, and FK registrations.
     * Shared by {@link #loadMetaTable} and {@link #registerTable}.
     */
    private void processMetaRow(final String tbl, final String col,
                                final String objTidStr, final String ref) {
        // $table sentinel — table-level TID
        if ("$table".equalsIgnoreCase(col)) {
            if (objTidStr != null && !objTidStr.isBlank())
                this.tableTids.put(tbl.toLowerCase(), f(objTidStr));
            return;
        }

        // FK registration — skip rows where column, table, and ref are all
        // identical (e.g. concept.concept→concept — a naming collision, not
        // a real FK).  Legitimate self-refs like manager_id→employee have
        // different column names and are allowed through.
        if (ref != null && !ref.isBlank() && !isFKAlreadyRegistered(tbl, col)
                && !(ref.equalsIgnoreCase(tbl) && col.equalsIgnoreCase(tbl)))
            registerOrPendingFK(tbl, col, ref, "id");

        // Populate logical type overrides
        if (objTidStr != null && !objTidStr.isBlank()) {
            this.logicalTypes
                    .computeIfAbsent(tbl.toLowerCase(), k -> new LinkedHashMap<>())
                    .put(col.toLowerCase(), f(objTidStr));
        }
    }

    /**
     * Register an FK with the schema generator, or store temporarily in
     * {@link #pendingFKs} if the generator hasn't been set yet.
     */
    private void registerOrPendingFK(final String tableName, final String columnName,
                                     final String toTable, final String toColumn) {
        if (schemaGenerator != null)
            schemaGenerator.registerFK(tableName, columnName, toTable, toColumn);
        else
            pendingFKs.add(new FKInfo(tableName, columnName, toTable, toColumn));
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
        fURI storedTid = null;
        for (final ColumnMetadata col : metadata.columns) {
            if (rowNames.length == 0 && metadata.primaryKeys.stream()
                    .anyMatch(pk -> pk.equalsIgnoreCase(col.name))) continue;
            if (TID_COLUMN.equalsIgnoreCase(col.name)) {
                // _tid is system metadata — extract for rec TID, skip from fields.
                try {
                    final String tidStr = rs.getString(col.name);
                    if (tidStr != null && !tidStr.isBlank()) {
                        final Obj parsed = readMaybeJSON(tidStr);
                        storedTid = parsed.isUri() ? parsed.uriValue() : f(tidStr);
                    }
                } catch (final SQLException ignored) {
                    // column may not exist in older tables — use REC_TID fallback
                }
                continue;
            }
            if (rowNames.length == 0 || Arrays.asList(rowNames).contains(col.name)) {
                final Obj value = readColumnWithMetadata(rs, col, metadata.tableName);
                labeledValues.put(uri(col.name), value);
                if (!value.isNoObj())
                    Router.global().stats().ioStats().incrBytesRecv(value.toString().getBytes().length);
            }
        }
        final fURI tid = storedTid != null ? storedTid : REC_TID;
        return rowNames.length == 1 ? objs(labeledValues.values()) : rec(labeledValues, tid, null);
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
                final String fkStr = fkValue.toString();
                // If the stored value is a JSON list (e.g. "[!*/usr/dr/message/4,...]"),
                // skip FK wrapping entirely — the column stores a list, not a single
                // reference.  Fall through to the type-aware reader.
                if (!fkStr.startsWith("[")) {
                    // If the value is already an absolute path, return it as a plain URI
                    // without auto_from wrapping (e.g. "/usr/dr/session/1" stored by caller).
                    if (fkStr.startsWith("/") || fkStr.indexOf(':') >= 0) {
                        return uri(fkStr);
                    }
                    final fURI referencedPath = buildFKReferencePath(fk.targetPath(), fkStr);
                    return auto_from_(referencedPath).tryToInst();
                }
                // JSON list: fall through to type-aware reader below
            } else {
                return noobj();
            }
        }

        // BOOLEAN stored as INTEGER (SQLite): handle before delegating to the
        // type-aware read, which only activates when _mtron_meta has a row.
        if ("BOOLEAN".equalsIgnoreCase(col.typeName) &&
                (col.sqlType == Types.INTEGER || col.sqlType == Types.TINYINT ||
                        col.sqlType == Types.SMALLINT)) {
            final Object value = rs.getObject(col.name);
            if (value == null || rs.wasNull()) return noobj();
            return bool(rs.getInt(col.name) != 0);
        }

        // Delegate to the parent's type-aware reader — uses _mtron_meta logical
        // types (uri::T, lst::T, rec::T, str::T, bool::T, etc.) to reconstruct
        // typed values.  No heuristic guessing needed.
        return readColumnWithType(rs, col.name, col.sqlType, tableName);
    }

    /**
     * Build the reference path for an FK column value.
     * The targetPath is like "office/+/officeCode" or "db:office/+/officeCode".
     * We extract the table part (before /+) and append the row ID.
     */
    private fURI buildFKReferencePath(final String targetPath, final String rowId) {
        // If the rowId is already an absolute URI (e.g. "/usr/dr/session/1"),
        // use it directly — don't prepend the space pattern and table name.
        if (rowId.startsWith("/") || rowId.indexOf(':') >= 0) {
            return f(rowId);
        }
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
        final Obj obj = objJson == null ? noobj() : ObjJSONSerializer.parse(objJson);
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
            if (dp.extension() != null) {
                final String pkColumn = metadata.primaryKeys.getFirst();
                final Rec current = readCurrentRow(conn, metadata, pkColumn, rowId);
                if (current == null) return 0;
                final Obj colValue = current.at(dp.field()).selfVID(null);
                final Obj updated = colValue.isRec()
                        ? colValue.asRec().at(dp.extension(), obj, MUTABLE)
                        : obj;
                return writeField(conn, metadata, rowId, dp.field(), updated);
            }
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
     * <p>
     * Persisted to {@link #MTRON_META_TABLE} so type metadata survives restarts.
     */
    private void trackLogicalType(final Connection conn, final TableMetadata metadata,
                                  final String columnName, final Obj value, final int sqlType) {
        final String tbl = metadata.tableName.toLowerCase();
        final String col = columnName.toLowerCase();
        final fURI tid = value.tid();
        this.logicalTypes
                .computeIfAbsent(tbl, k -> new LinkedHashMap<>())
                .put(col, tid);
        persistColumnType(conn, tbl, col, baseVidForValue(value), tid.toString(), null);
    }

    /**
     * Derive the base-type VID {@link fURI} string for {@code value}.
     * Uses the actual TID constants from {@code mInstSet} so the stored
     * string round-trips correctly through {@link fURI.Singleton#f(String)}.
     */
    private static String baseVidForValue(final Obj value) {
        if (value.isBool()) return BOOL_TID.toString();
        if (value.isInt()) return INT_TID.toString();
        if (value.isReal()) return REAL_TID.toString();
        if (value.isStr()) return STR_TID.toString();
        if (value.isUri()) return URI_TID.toString();
        if (value.isRec()) return REC_TID.toString();
        if (value.isLst()) return LST_TID.toString();
        if (value.isInst()) return M_ISA_INST_TID.toString();
        if (value.isCode()) return CODE_TID.toString();
        if (value.isRel()) return REL_TID.toString();
        return REC_TID.toString();
    }

    /**
     * Upsert a row into {@link #MTRON_META_TABLE}.
     *
     * @param conn      the JDBC connection
     * @param tableName lowercase table name
     * @param columnName lowercase column name
     * @param baseVid   base type VID string (e.g. {@code "int::T"})
     * @param objTid    full type ID string (e.g. {@code "int::T[nat]"}), may be null
     * @param refTable  FK target table, may be null
     */
    private void persistColumnType(final Connection conn, final String tableName,
                                   final String columnName, final String baseVid,
                                   final String objTid, final String refTable) {
        try {
            // Preserve existing ref_table when the incoming write doesn't
            // carry one — prevents trackLogicalType from overwriting FK info
            // that createTableFromRecord already persisted.
            String effectiveRef = refTable;
            if (effectiveRef == null) {
                try (final PreparedStatement sel = conn.prepareStatement(
                        "SELECT ref_table FROM " + MTRON_META_TABLE +
                                " WHERE table_name = ? AND column_name = ?")) {
                    sel.setString(1, tableName);
                    sel.setString(2, columnName);
                    try (final ResultSet rs = sel.executeQuery()) {
                        if (rs.next()) {
                            final String existing = rs.getString("ref_table");
                            if (existing != null && !existing.isBlank())
                                effectiveRef = existing;
                        }
                    }
                } catch (final SQLException ignored) { /* table may not exist yet */ }
            }

            // Delete-then-insert: portable across SQLite/PostgreSQL/MariaDB/MySQL
            try (final PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM " + MTRON_META_TABLE +
                            " WHERE table_name = ? AND column_name = ?")) {
                del.setString(1, tableName);
                del.setString(2, columnName);
                del.executeUpdate();
            }
            try (final PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO " + MTRON_META_TABLE +
                            " (table_name, column_name, base_vid, obj_tid, ref_table) VALUES (?, ?, ?, ?, ?)")) {
                ins.setString(1, tableName);
                ins.setString(2, columnName);
                ins.setString(3, baseVid);
                if (objTid != null) ins.setString(4, objTid);
                else ins.setNull(4, Types.VARCHAR);
                if (effectiveRef != null) ins.setString(5, effectiveRef);
                else ins.setNull(5, Types.VARCHAR);
                ins.executeUpdate();
            }
        } catch (final SQLException e) {
            this.space.logger().warn("failed to persist column type to %s: %s", MTRON_META_TABLE, e.getMessage());
        }
    }

    private static int sqlTypeForMono(final Obj value) {
        if (value.isBool()) return Types.BOOLEAN;
        if (value.isInt()) return Types.INTEGER;
        if (value.isReal()) return Types.REAL;
        return Types.VARCHAR;
    }

    private int writeField(final Connection conn, final TableMetadata metadata, final String rowId,
                           final String fieldName, final Obj value) throws SQLException {
        final ColumnMetadata column = metadata.columns.stream()
                .filter(c -> c.name.equalsIgnoreCase(fieldName))
                .findFirst()
                .orElseThrow(() -> new SQLException("column not found: " + fieldName));

        final String pkColumn = metadata.primaryKeys.getFirst();
        final String sql = String.format("UPDATE %s SET %s = ? WHERE %s = ?",
                q(metadata.tableName), q(column.name), q(pkColumn));

        try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
            trackLogicalType(conn, metadata, column.name, value, column.sqlType);
            validateColumnWrite(value, column, metadata.tableName);
            final int sqlType = (!value.isPoly() && !value.isNoObj() && column.sqlType == Types.VARCHAR)
                    ? sqlTypeForMono(value) : column.sqlType;
            writeParameter(stmt, 1, value, sqlType);

            final ColumnMetadata pkColMeta = metadata.columns.stream()
                    .filter(c -> c.name.equals(pkColumn)).findFirst().orElseThrow();
            if (pkColMeta.isIntegerType()) {
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
        if (rowId != null && rowId.startsWith("_"))
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

            ColumnMetadata column = metadata.columns.stream()
                    .filter(c -> c.name.equalsIgnoreCase(fieldName)).findFirst().orElse(null);
            if (column == null) {
                column = addColumnOnTheFly(conn, metadata, fieldName, entry.getValue());
            }

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
                q(metadata.tableName), q(pkColumn));
        try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
            final ColumnMetadata pkColMeta = metadata.columns.stream()
                    .filter(c -> c.name.equals(pkColumn)).findFirst().orElseThrow();
            if (pkColMeta.isIntegerType()) {
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
            trackLogicalType(conn, metadata, column.name, value, column.sqlType);
            setClauses.add(q(column.name) + " = ?");
            values.add(Tuple.Pair.with(value, column));
        }

        final String sql = String.format("UPDATE %s SET %s WHERE %s = ?",
                q(metadata.tableName), String.join(", ", setClauses), q(pkColumn));

        try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                final Tuple.Pair<Obj, ColumnMetadata> pair = values.get(i);
                final Obj value = pair.get0();
                final ColumnMetadata column = pair.get1();
                validateColumnWrite(value, column, metadata.tableName);
                final int sqlType = value.isPoly() ? Types.VARCHAR : column.sqlType;
                writeParameter(stmt, i + 1, value, sqlType);
            }

            final ColumnMetadata pkColMeta = metadata.columns.stream()
                    .filter(c -> c.name.equals(pkColumn)).findFirst().orElseThrow();
            if (pkColMeta.isIntegerType()) {
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
                q(metadata.tableName), q(columnName), sqlType);
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
        trackLogicalType(conn, metadata, columnName, value, jdbcType);
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
        if (pkColMeta.isIntegerType()) {
            pkValue = jnt(Long.parseLong(rowId));
        } else {
            pkValue = str(rowId);
        }
        values.add(Tuple.Pair.with(pkValue, pkColMeta));

        // Ensure _tid column exists and include rec type identity
        final ColumnMetadata tidColumn = metadata.columns.stream()
                .filter(c -> c.name.equals(TID_COLUMN)).findFirst().orElse(null);
        final ColumnMetadata resolvedTidCol;
        if (tidColumn != null) {
            resolvedTidCol = tidColumn;
        } else {
            resolvedTidCol = addColumnOnTheFly(conn, metadata, TID_COLUMN, str(rec.tid().toString()));
        }
        columnNames.add(TID_COLUMN);
        values.add(Tuple.Pair.with(str(rec.tid().toString()), resolvedTidCol));

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
            if (TID_COLUMN.equalsIgnoreCase(fieldName)) continue;

            final ColumnMetadata column = metadata.columns.stream()
                    .filter(c -> c.name.equalsIgnoreCase(fieldName)).findFirst().orElse(null);

            if (column != null) {
                trackLogicalType(conn, metadata, column.name, entry.getValue(), column.sqlType);
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
                q(metadata.tableName), columnNames.stream().map(this::q).reduce((a, b) -> a + ", " + b).orElse(""),
                placeholders);

        try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                final Tuple.Pair<Obj, ColumnMetadata> pair = values.get(i);
                final Obj value = pair.get0();
                final ColumnMetadata column = pair.get1();
                validateColumnWrite(value, column, metadata.tableName);
                final int sqlType = value.isPoly() ? Types.VARCHAR : column.sqlType;
                writeParameter(stmt, i + 1, value, sqlType);
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

        // Ensure _tid column exists and include rec type identity
        final ColumnMetadata tidColumn = metadata.columns.stream()
                .filter(c -> c.name.equals(TID_COLUMN)).findFirst().orElse(null);
        final ColumnMetadata resolvedTidCol;
        if (tidColumn != null) {
            resolvedTidCol = tidColumn;
        } else {
            resolvedTidCol = addColumnOnTheFly(conn, metadata, TID_COLUMN, str(rec.tid().toString()));
        }
        columnNames.add(TID_COLUMN);
        values.add(Tuple.Pair.with(str(rec.tid().toString()), resolvedTidCol));

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
            // Skip PK columns, user-provided 'id', and system _tid column
            if (metadata.primaryKeys.contains(fieldName) || "id".equalsIgnoreCase(fieldName)
                    || TID_COLUMN.equalsIgnoreCase(fieldName))
                continue;

            final ColumnMetadata column = metadata.columns.stream()
                    .filter(c -> c.name.equalsIgnoreCase(fieldName)).findFirst().orElse(null);
            if (column != null) {
                trackLogicalType(conn, metadata, column.name, entry.getValue(), column.sqlType);
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
                q(metadata.tableName), columnNames.stream().map(this::q).reduce((a, b) -> a + ", " + b).orElse(""),
                placeholders);

        try (final PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.size(); i++) {
                final Tuple.Pair<Obj, ColumnMetadata> pair = values.get(i);
                final Obj value = pair.get0();
                final ColumnMetadata column = pair.get1();
                validateColumnWrite(value, column, metadata.tableName);
                final int sqlType = value.isPoly() ? Types.VARCHAR : column.sqlType;
                writeParameter(stmt, i + 1, value, sqlType);
            }
            stmt.executeUpdate();
            try (final ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    final int key = generatedKeys.getInt(1);
                    this.space.logger().debug("auto-inserted row into %s: generated key %s",
                            metadata.tableName, key);
                    return key;
                }
            }
            this.space.logger().debug("auto-inserted row into %s: no generated key returned",
                    metadata.tableName);
            return 0;
        }
    }

    /**
     * Ensure the table exists (creating it if needed via
     * {@code createTableFromRecord}), then do an auto-increment INSERT.
     * Called from {@link tbleIncrQ#onPreWrite} — the QProc handles the
     * write so the normal {@code directWriter} path is never reached.
     */
    public int ensureTableAndInsert(final Connection conn, final String tableName,
                                     final Rec rec) throws java.sql.SQLException {
        final boolean isNew = !tableSchemas.containsKey(tableName.toLowerCase());
        if (isNew)
            createTableFromRecord(conn, tableName, rec, null);
        final TableMetadata metadata = tableSchemas.get(tableName.toLowerCase());
        if (metadata == null)
            throw new java.sql.SQLException("failed to create table: " + tableName);
        final int generatedKey = insertRowAuto(conn, metadata, rec);
        // Update the instset type AFTER the first insert so logical type
        // tracking (e.g. URI stored in TEXT) has already fired.
        if (isNew)
            this.space.onTableChanged(tableName);
        return generatedKey;
    }

    @Override
    public Iterator<Space.IdObj> read(final Connection conn, final fURI pattern) throws SQLException {
        final DataPath dp = resolveDataPath(pattern);
        if (dp == null)
            return Collections.emptyIterator();
        final String tableName = dp.collection();
        if (!dp.hasEntry()) {
            // Collection-level type resolution is now handled by
            // SchemaSpace.resolveCollectionSchema() in the space's directReader.
            return Collections.emptyIterator();
        }
        {
            final TableMetadata metadata = tableSchemas.get(tableName.toLowerCase());
            if (metadata == null)
                return Collections.emptyIterator();
            final List<Space.IdObj> results = new ArrayList<>();
            if (dp.entryIsWildcard()) {
                if (dp.hasField() && !dp.fieldIsWildcard()) {
                    final String pkColumns = metadata.primaryKeys.stream()
                            .map(this::q).reduce((a, b) -> a + ", " + b).orElse("");
                    final String fieldName = dp.field();
                    final String sql = String.format("SELECT %s, %s FROM %s", pkColumns, q(fieldName), q(metadata.tableName));
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
                    final String sql = String.format("SELECT * FROM %s", q(metadata.tableName));
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
                    final String pkColumns = metadata.primaryKeys.stream()
                            .map(this::q).reduce((a, b) -> a + ", " + b).orElse("");
                    final String fieldName = dp.field();
                    final String sql = String.format("SELECT %s, %s FROM %s WHERE %s = ?", pkColumns, q(fieldName), q(metadata.tableName), q(pkColumn));
                    try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
                        final ColumnMetadata pkColMeta = metadata.columns.stream()
                                .filter(c -> c.name.equals(pkColumn)).findFirst().orElseThrow();
                        if (pkColMeta.isIntegerType()) {
                            if (!CommonUtil.isInt(rowId)) return IteratorUtil.of();
                            stmt.setLong(1, Long.parseLong(rowId));
                        } else {
                            stmt.setString(1, rowId);
                        }
                        try (final ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                final fURI rowFuri = f(tableName).extend(rowId).extend(fieldName);
                                Obj row = readTableRow(rs, metadata, fieldName);
                                if (dp.extension() != null && row.isRec())
                                    row = row.asRec().at(dp.extension());
                                results.add(Space.IdObj.of(rowFuri, row));
                            }
                        } catch (final SQLException e) {
                            if (e.getErrorCode() == 1054) return IteratorUtil.of();
                            throw MTronException.of(e, "SQL failed: %s", sql);
                        }
                    }
                } else {
                    final String pkColumn = metadata.primaryKeys.getFirst();
                    final String sql = String.format("SELECT * FROM %s WHERE %s = ?", q(metadata.tableName), q(pkColumn));
                    try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
                        final ColumnMetadata pkColMeta = metadata.columns.stream()
                                .filter(c -> c.name.equals(pkColumn)).findFirst().orElseThrow();
                        if (pkColMeta.isIntegerType()) {
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
            final String sql = "UPDATE " + q(tableName) + " SET " + q(column)
                    + " = NULL WHERE " + q(pkCol) + " = ?";
            try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, rowId);
                return stmt.executeUpdate();
            }
        }

        final String pkCol = getPrimaryKeyColumn(conn, tableName);
        final String sql = "DELETE FROM " + q(tableName) + " WHERE " + q(pkCol) + " = ?";
        try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
            setRowIdParam(stmt, 1, conn, tableName, pkCol, rowId);
            return stmt.executeUpdate();
        }
    }

    private void setRowIdParam(final PreparedStatement stmt, final int idx,
                               final Connection conn, final String tableName,
                               final String pkCol, final String rowId) throws SQLException {
        final TableMetadata metadata = tableSchemas.get(tableName.toLowerCase());
        if (metadata != null) {
            final ColumnMetadata pkMeta = metadata.columns.stream()
                    .filter(c -> c.name.equalsIgnoreCase(pkCol)).findFirst().orElse(null);
            if (pkMeta != null && (pkMeta.isIntegerType())) {
                stmt.setLong(idx, Long.parseLong(rowId));
                return;
            }
        }
        stmt.setString(idx, rowId);
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

        // Also check _mtron_meta for type metadata and FK mappings
        try (final PreparedStatement ps = conn.prepareStatement(
                "SELECT column_name, base_vid, obj_tid, ref_table FROM " + MTRON_META_TABLE +
                        " WHERE table_name = ?")) {
            ps.setString(1, tableName);
            try (final ResultSet metaRs = ps.executeQuery()) {
                while (metaRs.next()) {
                    processMetaRow(tableName, metaRs.getString("column_name"),
                            metaRs.getString("obj_tid"), metaRs.getString("ref_table"));
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
                                      final studio.phaseshift.metatron.isa.m.type.Rec rec,
                                      final String rowId) throws SQLException {
        final StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                .append(q(tableName)).append(" (");

        // Infer PK type from the rowId: numeric → INTEGER (with dialect-specific
        // auto-increment), non-numeric text → VARCHAR(255).  A null rowId means
        // the caller wants auto-increment (incrQ path), so default to INTEGER.
        // VARCHAR is used instead of TEXT to avoid TEXT-in-key errors on MariaDB.
        final boolean textPK = rowId != null && !studio.phaseshift.metatron.util.CommonUtil.isInt(rowId);
        if (textPK) {
            ddl.append(q("id")).append(" VARCHAR(255) PRIMARY KEY");
        } else if (this.space instanceof studio.phaseshift.metatron.isa.tble.tbleSpace tble) {
            final String b = tble.backend();
            if (b != null && b.contains("postgresql"))
                ddl.append(q("id")).append(" SERIAL PRIMARY KEY");
            else if (b != null && (b.contains("mariadb") || b.contains("mysql")))
                ddl.append(q("id")).append(" INTEGER PRIMARY KEY AUTO_INCREMENT");
            else
                ddl.append(q("id")).append(" INTEGER PRIMARY KEY");
        } else {
            ddl.append(q("id")).append(" INTEGER PRIMARY KEY");
        }

        // _tid column — preserves rec type identity across write/read.
        // Nullable: write paths that bypass insertRow/insertRowAuto (e.g. raw SQL,
        // writeRow field-level diffs, writeRowFromList) won't populate it.
        ddl.append(", ").append(q(TID_COLUMN)).append(" TEXT");

        for (final Map.Entry<Obj, Obj> entry : rec.recValue().entrySet()) {
            if (!entry.getKey().isUri()) continue;
            final String colName = entry.getKey().asUri().uriValue().name();
            if (colName == null || colName.isEmpty()) continue;
            if ("id".equalsIgnoreCase(colName)) continue;
            if (TID_COLUMN.equalsIgnoreCase(colName)) continue;
            ddl.append(", ");

            final Obj val = entry.getValue();
            if (val.isAutoFrom()) {
                ddl.append(q(colName)).append(" INTEGER");
            } else {
                final String sqlType;
                if (val.isBool()) sqlType = "BOOLEAN";
                else if (val.isInt()) sqlType = "INTEGER";
                else if (val.isReal()) sqlType = "REAL";
                else sqlType = "TEXT";
                ddl.append(q(colName)).append(" ").append(sqlType);
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

        // Persist column types and FK references to _mtron_meta.
        ensureMetaTable(conn);
        final String tbl = tableName.toLowerCase();

        // $table sentinel — records the table-level TID (e.g. person::T)
        persistColumnType(conn, tbl, "$table", REC_TID.toString(), rec.tid().toString(), null);

        // Column-level type metadata
        for (final Map.Entry<Obj, Obj> entry : rec.recValue().entrySet()) {
            if (!entry.getKey().isUri()) continue;
            final String colName = entry.getKey().asUri().uriValue().name();
            if (colName == null || colName.isEmpty()) continue;
            if ("id".equalsIgnoreCase(colName)) continue;
            if (TID_COLUMN.equalsIgnoreCase(colName)) continue;

            final Obj val = entry.getValue();
            if (val.isAutoFrom()) {
                final fURI refURI = val.asInst().arg(0).uriValue();
                final String refTable;
                if (refURI.test(space.pattern())) {
                    refTable = refURI.segments().getFirst();
                } else {
                    refTable = refURI.segments(List.of(refURI.segments().getFirst())).toString();
                }
                // FK column: INTEGER base, ref_table populated
                persistColumnType(conn, tbl, colName.toLowerCase(),
                        INT_TID.toString(), val.tid().toString(), refTable);
            } else {
                persistColumnType(conn, tbl, colName.toLowerCase(),
                        baseVidForValue(val), val.tid().toString(), null);
            }
        }

        registerTable(conn, tableName);
    }

    public boolean isTablePath(final fURI furi) {
        return resolveDataPath(furi) != null;
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
