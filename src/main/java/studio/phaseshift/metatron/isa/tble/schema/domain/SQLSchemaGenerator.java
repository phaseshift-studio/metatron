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

import java.sql.Types;
import java.util.*;

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
    private Map<String, Type> tableTypes;

    /**
     * Create a schema generator for a SQL database
     *
     * @param tableMetadata metadata for all tables in the database
     * @param schemaBasePath base path for schema types (e.g., /m/tble/inst/schema/db)
     */
    public SQLSchemaGenerator(final List<ExistingTableSchema.TableMetadata> tableMetadata,
                             final fURI schemaBasePath) {
        this.tableMetadata = tableMetadata;
        this.schemaBasePath = schemaBasePath;
        this.tableTypes = null; // Lazy initialization
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
     * Generate a complete schema object including tables and foreign keys
     * Returns a rec with:
     * - pattern: base pattern for table types
     * - tables: list of table type definitions
     * - foreign_keys: list of foreign key relationships
     */
    public Obj generateSchema() {
        final Map<Obj, Obj> schemaMap = new LinkedHashMap<>();

        // Add pattern
        schemaMap.put(uri("pattern"), uri(this.schemaBasePath.extend("#")));

        // Add table types
        schemaMap.put(uri("tables"), lst(getTableTypes().stream()
            .map(t -> (Obj) t).toList()));

        // Add foreign key metadata
        schemaMap.put(uri("foreign_keys"), generateForeignKeyList());

        return rec(schemaMap);
    }

    /**
     * Generate a list of foreign key relationships as mtron objects
     */
    private Obj generateForeignKeyList() {
        final List<Obj> fkList = new ArrayList<>();

        for (final ExistingTableSchema.TableMetadata table : tableMetadata) {
            for (final ExistingTableSchema.ForeignKeyMetadata fk : table.foreignKeys()) {
                final Map<Obj, Obj> fkRec = new LinkedHashMap<>();
                fkRec.put(uri("table"), str(fk.fromTable()));
                fkRec.put(uri("column"), str(fk.fromColumn()));
                fkRec.put(uri("references"), str(fk.toTable()));
                fkRec.put(uri("ref_column"), str(fk.toColumn()));
                if (fk.fkName() != null) {
                    fkRec.put(uri("name"), str(fk.fkName()));
                }
                fkList.add(rec(fkRec));
            }
        }

        return lst(fkList);
    }
}
