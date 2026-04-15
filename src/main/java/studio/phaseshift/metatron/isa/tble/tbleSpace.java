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

package studio.phaseshift.metatron.isa.tble;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.tble.schema.domain.ExistingTableSchema;
import studio.phaseshift.metatron.isa.tble.schema.domain.SQLSchemaGenerator;
import studio.phaseshift.metatron.isa.tble.schema.storage.TableSchema;
import studio.phaseshift.metatron.isa.tble.schema.storage.TypedKeyValueSchema;
import studio.phaseshift.metatron.isa.tble.schema.storage.fURIAwareIndexedSchema;
import studio.phaseshift.metatron.util.MTronException;

import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.SPACE_TID;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.tble.tbleInstSet.*;

/**
 * tbleSpace - A dual-mode SQL database connector for Metatron with pluggable schema support
 *
 * <p>Provides two modes of operation:
 * <ol>
 *   <li><b>Key-Value Store Mode:</b> Stores arbitrary Metatron objects as JSON using pluggable schemas
 *       (fURIAwareIndexedSchema for MariaDB/MySQL, SimpleKeyValueSchema for others)</li>
 *   <li><b>Table Mapping Mode:</b> Maps existing SQL tables to Metatron objects automatically</li>
 * </ol>
 *
 * <p>Supports any JDBC-compatible database (PostgreSQL, MySQL, MariaDB, SQLite, etc.)
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Pluggable schema architecture for different database backends</li>
 *   <li>MQTT-style pattern matching with indexed segments (MariaDB/MySQL)</li>
 *   <li>Automatic discovery and mapping of existing SQL tables</li>
 *   <li>Read SQL table rows as Metatron lists</li>
 *   <li>Primary key-based row identification</li>
 *   <li>Automatic SQL type to Metatron type conversion</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * tbleSpace space = tbleSpace.of(
 *     rec(
 *         uri(PATTERN), uri("/tble/#"),
 *         uri(HOST), uri("postgresql://localhost:5432/mydb"),  // Note: no "jdbc:" prefix
 *         uri(DRIVER), uri("org.postgresql.Driver"),
 *         uri("table_mapping"), uri("true")      // optional, default: "true"
 *     ).jvm(),
 *     f("/sys/space/tble")
 * );
 * }</pre>
 *
 * <h2>Table Mapping Mode</h2>
 * <p>When table mapping is enabled (default), tbleSpace automatically discovers existing SQL tables
 * and makes them accessible via fURIs:
 *
 * <pre>{@code
 * // Read a specific row by primary key
 * Obj row = space.read(f("/users/123"));  // Returns a record: [name=>marko,age=>29]
 *
 * // Read all rows from a table
 * Obj allRows = space.read(f("/users/+"));  // Returns multiple records
 *
 * // Pattern matching
 * Obj rows = space.read(f("/users/#"));  // Returns all rows and nested data
 *
 * // Write an entire row (update or insert)
 * space.write(f("/users/123"), rec(uri("name"), str("marko"), uri("age"), jnt(29)));
 *
 * // Write a single field
 * space.write(f("/users/123/name"), str("marko"));
 * }</pre>
 *
 * <p>SQL rows are converted to Metatron records where column names are keys.
 * Primary keys are used to identify individual rows.
 * The Space.Helper.resolveWrite() method automatically handles poly unrolling for nested writes.
 *
 * <h2>Key-Value Store Mode</h2>
 * <p>For paths that don't match existing tables, tbleSpace uses its key-value store:
 *
 * <pre>{@code
 * // Store arbitrary objects
 * space.write(f("/my/data"), rec(uri("name"), str("Alice")));
 *
 * // Read them back
 * Obj data = space.read(f("/my/data"));
 * }</pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class tbleSpace extends AbstractSpace<Connection> {

    public static fURI SQL_INST_TID =TBLE_ISA_INST_TID.extend(SQL);
    public static fURI TBLE_SPACE_TID = TBLE_ISA_TID.extend(SPACE).extend("tblespace");
    public static final Type TBLE_SPACE_TYPE =
            Type.Builder.build()
                    .tid(SPACE_TID)
                    .vid(TBLE_SPACE_TID)
                    .isaPredicate(rec(
                            uri(PATTERN), URI_TYPE,
                            uri(HOST), URI_TYPE,
                            uri(DRIVER), URI_TYPE,
                            uri(ROUTE), rec(URI_TYPE, URI_TYPE),
                            uri(TABLE).maybe(), LST_TYPE))
                    .constructor(instC(TBLE_ISA_INST_TID.extend("tblespace/cons").dom(ALL.maybe()).rng(TBLE_SPACE_TID),
                            lst(REC_TYPE),
                            (lhs, inst) -> tbleSpace.of(inst.arg(0).asRec().jvm(), inst.arg(0).vid())))
                    .create();

    protected ObjSerializer<?> serializer;
    protected TableSchema schema;
    protected ExistingTableSchema existingTableSchema;
    protected SQLSchemaGenerator schemaGenerator;

    public static tbleSpace of(final Map<Obj, Obj> config, final fURI vid) {
        MTronException.wrap(() -> Class.forName(config.get(uri(DRIVER)).uriValue().toString()));
        try {
            final Connection conn = DriverManager.getConnection(JDBC + config.get(uri(HOST)).uriValue().toString());
            return new tbleSpace(conn, config, TBLE_SPACE_TID, vid);
        } catch (final SQLException ex) {
            throw MTronException.of(ex);
        }
    }

    protected tbleSpace(final Connection sjvm, final Map<Obj, Obj> config, final fURI tid, final fURI vid) {
        super(sjvm, config, tid, vid);
        LOG.info("connected {{b}}%s{{X}}", config.get(uri(HOST)));
        // Initialize schema - auto-detect based on database type
        try {
            final String dbProductName = sjvm.getMetaData().getDatabaseProductName().toLowerCase();
            if (dbProductName.contains("mariadb") || dbProductName.contains("mysql")) {
                this.schema = new fURIAwareIndexedSchema();
                // Use ObjmtronSerializer for fURIAwareIndexedSchema to avoid JSON parsing issues
                this.serializer = this.at(uri(SERIALIZER)).orElse(new ObjmtronSerializer());
                LOG.info("detected {{b}}mariadb/mysql{{X}} - using {{g}}mqtt schema with clean string serializer");
            } else {
                // Use TypedKeyValueSchema for isomorphic type-preserving storage
                this.schema = new TypedKeyValueSchema();
                // TypedKeyValueSchema handles serialization internally, but set a default anyway
                this.serializer = this.at(uri(SERIALIZER)).orElse(new ObjmtronSerializer());
                LOG.info("detected {{b}}%s{{X}} - using {{g}}typed schema", dbProductName);
            }
            this.schema.initialize(sjvm);
            LOG.info("initialized schema {{b}}%s{{X}} (version: %s)",
                    this.schema.getClass().getSimpleName(), this.schema.version());

            // Initialize existing table schema for table mapping
            // Check if table mapping is enabled (default: true)
            final boolean enableTableMapping = config.getOrDefault(uri(TABLE), null) != null;

            if (enableTableMapping) {
                this.existingTableSchema = new ExistingTableSchema(this, "objs");
                this.existingTableSchema.initialize(sjvm);
                LOG.info("initialized {{g}}existing table schema{{X}} - discovered %s tables for database %s",
                        this.existingTableSchema.getTableNames().size(), this.sjvm().getCatalog());
                this.at(uri(TABLE), lst(this.existingTableSchema.getTableMetadata().stream().map(t -> (Obj) uri(t.tableName())).toList()), MUTABLE);

                // Initialize SQL schema generator and store in configuration (not data namespace)
                final String dbName = sjvm.getCatalog() != null ? sjvm.getCatalog() : "db";
                final fURI schemaPath = this.pattern.retractPattern().extend("schema").extend(dbName);

                this.schemaGenerator = new SQLSchemaGenerator(
                        this.existingTableSchema.getTableMetadata(),
                        schemaPath
                );

                // Store schema in configuration so it doesn't interfere with pattern queries on data
                this.at(uri(SCHEMA), this.schemaGenerator.generateSchema(), MUTABLE);

                LOG.info("initialized {{g}}SQL schema{{X}} in config with %s table types",
                        this.existingTableSchema.getTableNames().size());

            } else {
                this.existingTableSchema = null;
                this.schemaGenerator = null;
                LOG.info("table mapping {{y}}disabled{{X}}");
            }
        } catch (final SQLException ex) {
            throw MTronException.of(ex);
        }
    }

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (pattern, obj) -> {
            try {
                if (pattern.hasPattern()) {
                    this.directReader().apply(pattern).forEachRemaining(kv -> this.write(kv.furi(), obj));
                } else {
                    final fURI alignedPattern = Space.Helper.routeFromSpace(pattern, this.routes());
                    // Check if this is a table mapping path (existing table)
                    if (this.existingTableSchema != null && this.existingTableSchema.isTablePath(alignedPattern)) {
                        this.existingTableSchema.write(this.sjvm(), alignedPattern, obj);
                    } else {
                        // Use key-value schema
                        if (this.schema instanceof TypedKeyValueSchema) {
                            // TypedKeyValueSchema can write Obj directly without JSON serialization
                            ((TypedKeyValueSchema) this.schema).write(this.sjvm(), pattern, obj);
                        } else {
                            // Other schemas need JSON serialization
                            final String objJson = obj.isNoObj() ? null : this.serializer.write(obj).toString();
                            this.schema.write(this.sjvm(), pattern, objJson);
                        }
                    }
                }
            } catch (final SQLException e) {
                throw MTronException.of(e);
            }
            return obj;
        };
    }

    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        return (pattern) -> {
            try {
                LOG.debug("looking for table vid: %s", pattern);
                final fURI alignedPattern = Space.Helper.routeFromSpace(pattern, this.routes());

                // Check if this is a table mapping path (existing table)
                if (this.existingTableSchema != null && this.existingTableSchema.isTablePath(alignedPattern)) {
                    // Known table from schema - use existing table schema
                    final Iterator<IdObj> rawResults = this.existingTableSchema.read(this.sjvm(), alignedPattern);
                    final List<IdObj> allResults = new ArrayList<>();
                    rawResults.forEachRemaining(kv -> {
                        allResults.add(kv);  // Add the base object
                        if (pattern.hasPattern() && kv.obj().isPoly()) {
                            // Add unrolled nested paths (like memSpace does) - only when pattern has wildcards
                            allResults.addAll(Space.Helper.unrollPoly(kv.furi(), kv.obj().as(), pattern.asNode()));
                        }
                    });
                    return allResults.iterator();
                }

                // Use key-value schema (TypedKeyValueSchema or SimpleKeyValueSchema)
                // Get raw results and add poly unrolling (matching memSpace pattern)
                final Iterator<IdObj> rawResults = this.schema.read(this.sjvm(), pattern);
                final List<IdObj> allResults = new ArrayList<>();
                rawResults.forEachRemaining(kv -> {
                    if (pattern.hasPattern()) {
                        // For pattern queries: add base if matches, AND unroll poly independently
                        if (kv.furi().test(pattern.asNode())) {
                            allResults.add(kv);  // Add the base object if it matches the pattern
                        }
                        if (kv.obj().isPoly()) {
                            // Unroll poly independently - children might match even if base doesn't
                            allResults.addAll(Space.Helper.unrollPoly(kv.furi(), kv.obj().as(), pattern.asNode()));
                        }
                    } else {
                        allResults.add(kv);  // Exact match - add the result
                    }
                });
                return allResults.iterator();
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        };
    }

}
