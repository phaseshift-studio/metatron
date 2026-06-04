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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;

import java.sql.Types;
import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.id_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.tble.tbleInstSet.REC_ROW_TID;

/**
 * Generates mtron type definitions from SQL table metadata.
 * Simple utility class that creates type definitions for each discovered table.
 *
 * <p>This allows SQL schemas to be accessible via fURIs like:
 * <pre>
 * /netflix/schema              → the schema rec
 * /netflix/schema/db/movie     → the movie table type
 * </pre>
 *
 * <p>This class serves as the single source of truth for FK relationships.
 * FK info is stored in {@link #fkLookup} and queryable via
 * {@link #getFKTarget(String, String)}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SQLSchemaGenerator {

    /**
     * Holds the target path for a foreign key reference.
     * {@code targetPath} is the full path to the referenced column,
     * e.g., {@code "office/+/officeCode"} or {@code "db:office/+/officeCode"}.
     */
    public record FKTarget(String targetPath) {}

    private final List<ExistingTableSchema.TableMetadata> tableMetadata;
    private final fURI schemaBasePath;
    private final String databaseName;
    private Map<String, Type> tableTypes;

    /**
     * FK lookup map: "tableName.columnName" (lowercase) → FKTarget.
     * Single source of truth for FK relationships — no duplicate SQL-specific
     * FK encoding needed in {@link ExistingTableSchema}.
     */
    private final Map<String, FKTarget> fkLookup = new LinkedHashMap<>();

    /**
     * Create a schema generator for a SQL database
     *
     * @param tableMetadata metadata for all tables in the database
     * @param schemaBasePath base path for schema types (e.g., /m/tble/inst/schema/db)
     * @param databaseName the name of the database (for alignment with docdb schema)
     */
    public SQLSchemaGenerator(final List<ExistingTableSchema.TableMetadata> tableMetadata,
                             final fURI schemaBasePath,
                             final String databaseName) {
        this.tableMetadata = tableMetadata;
        this.schemaBasePath = schemaBasePath;
        this.databaseName = databaseName;
        this.tableTypes = null; // Lazy initialization
    }

    /**
     * Create a schema generator for a SQL database (without explicit database name)
     *
     * @param tableMetadata metadata for all tables in the database
     * @param schemaBasePath base path for schema types (e.g., /m/tble/inst/schema/db)
     */
    public SQLSchemaGenerator(final List<ExistingTableSchema.TableMetadata> tableMetadata,
                             final fURI schemaBasePath) {
        this(tableMetadata, schemaBasePath, schemaBasePath.name());
    }

    /**
     * Get all table types as a collection (generates them lazily on first access)
     */
    public Collection<Type> getTableTypes() {
        if (tableTypes == null) {
            tableTypes = new LinkedHashMap<>();
            // Generate all table types
            for (final ExistingTableSchema.TableMetadata table : tableMetadata) {
                final Type tableType = generateTableType(table);
                tableTypes.put(table.tableName().toLowerCase(), tableType);
            }
        }
        return tableTypes.values();
    }

    /**
     * Get a specific table type by name
     */
    public Type getTableType(final String tableName) {
        return tableTypes != null ? tableTypes.get(tableName.toLowerCase()) : null;
    }

    /**
     * Returns the FK target path for the given table+column, or {@code null}
     * if the column is not a foreign key.
     *
     * <p>The returned path is the full target path, e.g.
     * {@code "office/+/officeCode"} or {@code "db:office/+/officeCode"}.
     *
     * @param tableName  the source table name
     * @param columnName the source column name
     * @return the FK target, or {@code null} if not an FK column
     */
    public FKTarget getFKTarget(final String tableName, final String columnName) {
        final String key = tableName.toLowerCase() + "." + columnName.toLowerCase();
        return fkLookup.get(key);
    }

    /**
     * Register FK info discovered from SQL metadata.
     * Called by {@link ExistingTableSchema} during initialization so that the
     * generator — not the table schema — is the single source of truth.
     *
     * @param tableName  source table
     * @param columnName source column
     * @param toTable    referenced table
     * @param toColumn   referenced column
     */
    public void registerFK(final String tableName, final String columnName,
                           final String toTable, final String toColumn) {
        final String key = tableName.toLowerCase() + "." + columnName.toLowerCase();
        final String targetPath = toTable.contains(":")
                ? toTable + "/+/" + toColumn
                : toTable + "/+/" + toColumn;
        fkLookup.put(key, new FKTarget(targetPath));
    }

    /**
     * Returns the FK lookup map (read-only view).
     */
    public Map<String, FKTarget> getFKLookup() {
        return Collections.unmodifiableMap(fkLookup);
    }



    /**
     * Generate a mtron type definition for a SQL table.
     * <p>
     * Foreign key columns are encoded as isa predicates on the column type,
     * pointing to the target table's primary key path via auto_from.
     * This eliminates the need for a separate "references" block — the instset
     * Type itself is the single source of truth.
     */
    private Type generateTableType(final ExistingTableSchema.TableMetadata table) {
        final LinkedHashMap<Obj, Obj> fields = new LinkedHashMap<>();
        final String tbl = table.tableName().toLowerCase();

        // Add each column as a field in the record type
        for (final ExistingTableSchema.ColumnMetadata column : table.columns()) {
            final FKTarget fkTarget = getFKTarget(tbl, column.name());
            if (fkTarget != null) {
                // Encode FK as an isa predicate on the column type
                // e.g., isa_(f("office/+/officeCode")).auto_from_(id_()).tryToInst()
                final Obj fkPredicate = isa_(f(fkTarget.targetPath()))
                        .auto_from_(id_())
                        .tryToInst();
                fields.put(uri(column.name()), fkPredicate);
            } else {
                final Type columnType = sqlTypeToMtronType(column);
                fields.put(uri(column.name()), columnType);
            }
        }

        // Build the type: rec::T[isa([column1=>type1, column2=>isa_predicate, ...])]
        final fURI tableTypePath = schemaBasePath.extend(tbl);

        return Type.Builder.build()
                .tid(REC_ROW_TID)
                .vid(tableTypePath)
                .isaPredicate(rec(fields))
                .create();
    }

    /**
     * Map SQL types to mtron types
     */
    private Type sqlTypeToMtronType(final ExistingTableSchema.ColumnMetadata column) {
        // Handle BOOLEAN specially - SQLite reports it as INTEGER but with BOOLEAN type name
        if ("BOOLEAN".equalsIgnoreCase(column.typeName())) {
            return BOOL_TYPE;
        }

        return switch (column.sqlType()) {
            // Integer types
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> INT_TYPE;

            // Floating point types
            case Types.FLOAT, Types.REAL, Types.DOUBLE, Types.DECIMAL, Types.NUMERIC -> REAL_TYPE;

            // Boolean
            case Types.BOOLEAN, Types.BIT -> BOOL_TYPE;

            // String types
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR,
                 Types.NVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> STR_TYPE;

            // Date/Time types - represent as strings for now
            case Types.DATE, Types.TIME, Types.TIMESTAMP,
                 Types.TIME_WITH_TIMEZONE, Types.TIMESTAMP_WITH_TIMEZONE -> STR_TYPE;

            // Binary types - represent as strings (base64 encoded)
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> STR_TYPE;

            // Other types - default to string
            default -> STR_TYPE;
        };
    }

    /**
     * Get the schema base path
     */
    public fURI getSchemaBasePath() {
        return schemaBasePath;
    }

    /**
     * Generate a {@link SQLSchemaInstSet} for the discovered tables.
     *
     * <p>The instset VID is {@code schemaVid} (must be in the {@code /m/} namespace so it is
     * backed by memSpace). Each table Type's VID is placed under
     * {@code schemaVid/type/{tableName}} so that {@code checkPattern()} in
     * {@link studio.phaseshift.metatron.isa.AbstractInstSet} stores them locally in
     * {@code TYPE_TABLE} rather than routing them elsewhere.
     *
     * <p>Register the returned instset via {@code Router.global().addSpace(instset)} —
     * safe because its VID is in {@code /m/}, not in the tbleSpace's data namespace.
     *
     * @param schemaVid VID for the schema instset, e.g. {@code f("/m/tble/space/schema/mydb")}
     * @return a fully-populated {@link SQLSchemaInstSet}
     */
    public SQLSchemaInstSet generateSchemaInstset(final fURI schemaVid) {
        final List<Type> types = new ArrayList<>();
        for (final ExistingTableSchema.TableMetadata table : tableMetadata) {
            final fURI typeVid = f(table.tableName().toLowerCase());
            types.add(generateTableTypeAt(table, typeVid));
        }
        return new SQLSchemaInstSet(schemaVid, types);
    }

    /**
     * Generate a table Type with a specific VID (for use within a schema instset).
     * <p>
     * FK columns are encoded as isa predicates pointing to the target table path.
     */
    private Type generateTableTypeAt(final ExistingTableSchema.TableMetadata table, final fURI typeVid) {
        final LinkedHashMap<Obj, Obj> fields = new LinkedHashMap<>();
        final String tbl = table.tableName().toLowerCase();

        for (final ExistingTableSchema.ColumnMetadata column : table.columns()) {
            final FKTarget fkTarget = getFKTarget(tbl, column.name());
            if (fkTarget != null) {
                final Obj fkPredicate = isa_(f(fkTarget.targetPath()))
                        .auto_from_(id_())
                        .tryToInst();
                fields.put(uri(column.name()), fkPredicate);
            } else {
                fields.put(uri(column.name()), sqlTypeToMtronType(column));
            }
        }

        return Type.Builder.build()
                .tid(REC_ROW_TID)
                .vid(typeVid)
                .isaPredicate(rec(fields))
                .create();
    }
}
