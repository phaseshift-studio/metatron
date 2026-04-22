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
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import java.util.ArrayList;

import java.sql.Types;
import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Real.REAL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
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
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SQLSchemaGenerator {

    private final List<ExistingTableSchema.TableMetadata> tableMetadata;
    private final fURI schemaBasePath;
    private final String databaseName;
    private Map<String, Type> tableTypes;

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
        return tableTypes.get(tableName.toLowerCase());
    }



    /**
     * Generate a mtron type definition for a SQL table
     */
    private Type generateTableType(final ExistingTableSchema.TableMetadata table) {
        final LinkedHashMap<Obj, Obj> fields = new LinkedHashMap<>();

        // Add each column as a field in the record type
        for (final ExistingTableSchema.ColumnMetadata column : table.columns()) {
            final Type columnType = sqlTypeToMtronType(column);
            fields.put(uri(column.name()), columnType);
        }

        // Build the type: rec::T[isa([column1=>type1, column2=>type2, ...])]
        final fURI tableTypePath = schemaBasePath.extend(table.tableName().toLowerCase());

        return Type.Builder.build()
                .tid(REC_ROW_TID)
                .vid(tableTypePath)
                .isaPredicate(studio.phaseshift.metatron.isa.m.type.impl.MRec.rec(fields))
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
        final fURI typeBase = schemaVid.extend("type");
        final List<Type> types = new ArrayList<>();
        for (final ExistingTableSchema.TableMetadata table : tableMetadata) {
            final fURI typeVid = typeBase.extend(table.tableName().toLowerCase());
            types.add(generateTableTypeAt(table, typeVid));
        }
        return new SQLSchemaInstSet(schemaVid, types);
    }

    /**
     * Generate a table Type with a specific VID (for use within a schema instset).
     * The VID must fall under the instset's pattern so it is stored locally.
     */
    private Type generateTableTypeAt(final ExistingTableSchema.TableMetadata table, final fURI typeVid) {
        final LinkedHashMap<Obj, Obj> fields = new LinkedHashMap<>();
        for (final ExistingTableSchema.ColumnMetadata column : table.columns()) {
            fields.put(uri(column.name()), sqlTypeToMtronType(column));
        }
        return Type.Builder.build()
                .tid(REC_ROW_TID)
                .vid(typeVid)
                .isaPredicate(studio.phaseshift.metatron.isa.m.type.impl.MRec.rec(fields))
                .create();
    }

    /**
     * Generate a complete schema object including tables and references.
     * <p>
     * Returns a rec with unified structure (aligned with dcmntSpace schema):
     * <ul>
     *   <li>pattern: base pattern for table types</li>
     *   <li>name: database name</li>
     *   <li>tables: list of table definitions with name, uri, schema, type</li>
     *   <li>references: list of foreign key relationships</li>
     * </ul>
     */
    public Obj generateSchema() {
        final Map<Obj, Obj> schemaMap = new LinkedHashMap<>();

        // Add pattern (aligned with docdb)
        schemaMap.put(uri(PATTERN), uri(this.schemaBasePath.extend("#")));

        // Add database name (aligned with docdb)
        schemaMap.put(uri(NAME), str(this.databaseName));

        // Add table definitions with unified structure
        schemaMap.put(uri(TABLES), lst(generateTableList()));

        // Add references (unified name, was foreign_keys)
        schemaMap.put(uri(REFERENCES), generateReferenceList());

        return rec(schemaMap);
    }

    /**
     * Generate a list of table definitions with unified structure.
     * Each table entry contains: name, uri, schema, type
     */
    private List<Obj> generateTableList() {
        final List<Obj> tableList = new ArrayList<>();

        for (final ExistingTableSchema.TableMetadata table : tableMetadata) {
            final Type tableType = generateTableType(table);
            final fURI tableUri = schemaBasePath.extend(table.tableName().toLowerCase());

            // Build schema rec (field => type mappings)
            final Map<Obj, Obj> schemaRec = new LinkedHashMap<>();
            for (final ExistingTableSchema.ColumnMetadata column : table.columns()) {
                schemaRec.put(uri(column.name()), sqlTypeToMtronType(column));
            }

            // Build table entry with unified structure
            final Map<Obj, Obj> tableEntry = new LinkedHashMap<>();
            tableEntry.put(uri(NAME), str(table.tableName()));
            tableEntry.put(uri(URI), uri(tableUri));
            tableEntry.put(uri(SCHEMA), rec(schemaRec));
            tableEntry.put(uri(TYPE), tableType);

            tableList.add(rec(tableEntry));

            // Also cache the type
            if (tableTypes == null) {
                tableTypes = new LinkedHashMap<>();
            }
            tableTypes.put(table.tableName().toLowerCase(), tableType);
        }

        return tableList;
    }

    /**
     * Generate a list of reference relationships as mtron objects.
     * Uses unified field names aligned with docdb schema.
     */
    private Obj generateReferenceList() {
        final List<Obj> refList = new ArrayList<>();

        for (final ExistingTableSchema.TableMetadata table : tableMetadata) {
            for (final ExistingTableSchema.ForeignKeyMetadata fk : table.foreignKeys()) {
                final Map<Obj, Obj> refRec = new LinkedHashMap<>();
                refRec.put(uri(FROM_TABLE), str(fk.fromTable()));
                refRec.put(uri(FROM_COLUMN), str(fk.fromColumn()));
                refRec.put(uri(TO_TABLE), str(fk.toTable()));
                refRec.put(uri(TO_COLUMN), str(fk.toColumn()));
                if (fk.fkName() != null) {
                    refRec.put(uri(NAME), str(fk.fkName()));
                }
                refRec.put(uri(TYPE), str("FOREIGN_KEY"));
                refList.add(rec(refRec));
            }
        }

        return lst(refList);
    }
}
