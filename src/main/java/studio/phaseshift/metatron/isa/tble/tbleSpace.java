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
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.NoObj;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjCleanStringSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.tble.schema.domain.ExistingTableSchema;
import studio.phaseshift.metatron.isa.tble.schema.domain.SQLSchemaGenerator;
import studio.phaseshift.metatron.isa.tble.schema.storage.TableSchema;
import studio.phaseshift.metatron.isa.tble.schema.storage.TypedKeyValueSchema;
import studio.phaseshift.metatron.isa.tble.schema.storage.fURIAwareIndexedSchema;
import studio.phaseshift.metatron.util.MTronException;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.SPACE_TID;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs0;
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

    public static fURI SQL_INST_TID = TBLE_ISA_TID.extend(INST).extend(SQL);
    public static fURI TBLE_SPACE_TID = TBLE_ISA_TID.extend(SPACE).extend("tble");
    public static final Type TABL_SPACE_TYPE =
            Type.Builder.build()
                    .tid(SPACE_TID)
                    .vid(TBLE_SPACE_TID)
                    .isaPredicate(rec(
                            uri(PATTERN), URI_TYPE,
                            uri(HOST), URI_TYPE,
                            uri(DRIVER), URI_TYPE,
                            uri(ROUTE), rec(URI_TYPE, URI_TYPE),
                            uri(TABLE).maybe(), LST_TYPE))
                    .constructor(instC(mInstSet.M_ISA_INST_TID.dom(ALL.maybe()).rng(TBLE_SPACE_TID),
                            lst(REC_TYPE),
                            (lhs, inst) -> tbleSpace.of(inst.arg(0).asRec().jvm(), inst.arg(0).vid())))
                    .create();

    protected ObjSerializer<?> serializer;
    protected TableSchema schema;
    protected ExistingTableSchema existingTableSchema;
    protected String schemaPrefix;
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
                // Use ObjCleanStringSerializer for fURIAwareIndexedSchema to avoid JSON parsing issues
                this.serializer = this.at(uri(SERIALIZER)).orElse(new ObjCleanStringSerializer());
                LOG.info("detected {{b}}mariadb/mysql{{X}} - using {{g}}mqtt schema with clean string serializer");
            } else {
                // Use TypedKeyValueSchema for isomorphic type-preserving storage
                this.schema = new TypedKeyValueSchema();
                // TypedKeyValueSchema handles serialization internally, but set a default anyway
                this.serializer = this.at(uri(SERIALIZER)).orElse(new ObjCleanStringSerializer());
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

                // Initialize SQL schema generator for type definitions (lazy - will generate types on first access)
                this.schemaPrefix = this.pattern.retractPattern().extend("schema").toString();
                final String dbName = sjvm.getCatalog() != null ? sjvm.getCatalog() : "db";
                final fURI schemaPath = f(this.schemaPrefix).extend(dbName);

                this.schemaGenerator = new SQLSchemaGenerator(
                        this.existingTableSchema.getTableMetadata(),
                        schemaPath
                );

                LOG.info("initialized {{g}}SQL schema{{X}} at %s (lazy) with %s table types",
                        schemaPath, this.existingTableSchema.getTableNames().size());

            } else {
                this.existingTableSchema = null;
                this.schemaPrefix = null;
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
                    // Pattern write - write to all matching fURIs
                    this.directReader().apply(pattern).forEachRemaining(kv -> this.write(kv.furi(), obj));
                } else {
                    // Strip the space's pattern prefix to get the relative path
                    final fURI relativePath = stripPatternPrefix(pattern);

                    // Check if this is a table mapping path (existing table)
                    if (this.existingTableSchema != null && this.existingTableSchema.isTablePath(relativePath)) {
                        this.existingTableSchema.write(this.sjvm(), relativePath, obj);
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
                LOG.debug("looking for tble vid: %s", pattern);

                // Check if this is a schema path (e.g., */netflix/schema or */netflix/schema/movie)
                // Only if table mapping is enabled (schemaPrefix will be non-null)
                if (this.schemaPrefix != null && this.schemaGenerator != null) {
                    if (f(this.schemaPrefix).test(pattern)) {
                        // Return the schema object itself - includes tables and foreign keys
                        return IdObj.of(f(this.schemaPrefix), this.schemaGenerator.generateSchema()).iterator();
                    } else if (pattern.hasPrefix(this.schemaPrefix)) {
                        // Schema subpath - let the schema InstSet handle it
                        return Collections.emptyIterator();
                    }
                }

                // Check if this is a table mapping path (existing table)
                if (this.existingTableSchema != null && this.existingTableSchema.isTablePath(pattern)) {
                    // Use existing table schema - get raw results and add poly unrolling
                    final Iterator<IdObj> rawResults = this.existingTableSchema.read(this.sjvm(), pattern);
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
                // Get raw results and add poly unrolling
                final Iterator<IdObj> rawResults = this.schema.read(this.sjvm(), pattern);
                final List<IdObj> allResults = new ArrayList<>();
                rawResults.forEachRemaining(kv -> {
                    allResults.add(kv);  // Add the base object
                    if (pattern.hasPattern() && kv.obj().isPoly()) {
                        // Add unrolled nested paths (like memSpace does) - only when pattern has wildcards
                        allResults.addAll(Space.Helper.unrollPoly(kv.furi(), kv.obj().as(), pattern.asNode()));
                    }
                });
                return allResults.iterator();
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        };
    }

    /**
     * Strip the space's route prefix from a fURI to get the relative path.
     * For example, if route maps db: to /tble/ and fURI is /tble/users/1, returns /users/1
     */
    private fURI stripPatternPrefix(final fURI furi) {
        // If there are routes, use the route target as the prefix to strip
        if (!this.routes().isEmpty()) {
            // Get the first route's target (e.g., /tble/)
            final studio.phaseshift.metatron.isa.m.type.Uri routeTarget = this.routes().values().iterator().next();
            final fURI prefix = routeTarget.asUri().uriValue().asNode();
            // Only use the route if it's not empty (has actual path segments)
            if (!prefix.path().isEmpty() && prefix.path().stream().anyMatch(s -> !s.isEmpty())) {
                return furi.removePrefix(prefix);
            }
        }
        // Fallback to using the pattern if no routes or route is empty
        final fURI patternBase = this.pattern().asNode();
        return furi.removePrefix(patternBase);
    }

    /**
     * Add the space's route prefix to a relative fURI.
     * For example, if route maps db: to /tble/ and fURI is /users/1, returns /tble/users/1
     */
    private fURI addPatternPrefix(final fURI furi) {
        // If there are routes, use the route target as the prefix to add
        if (!this.routes().isEmpty()) {
            // Get the first route's target (e.g., /tble/)
            final studio.phaseshift.metatron.isa.m.type.Uri routeTarget = this.routes().values().iterator().next();
            final fURI prefix = routeTarget.asUri().uriValue().asNode();
            // Only use the route if it's not empty (has actual path segments)
            if (!prefix.path().isEmpty() && prefix.path().stream().anyMatch(s -> !s.isEmpty())) {
                return prefix.extend(furi);
            }
        }
        // Fallback to using the pattern if no routes or route is empty
        final fURI patternBase = this.pattern().asNode();
        return patternBase.extend(furi);
    }
}
