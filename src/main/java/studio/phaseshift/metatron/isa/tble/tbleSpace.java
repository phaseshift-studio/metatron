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

package studio.phaseshift.metatron.isa.tble;

import studio.phaseshift.metatron.furi.DataPath;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.SchemaSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.tble.schema.storage.TableSchema;
import studio.phaseshift.metatron.isa.tble.schema.storage.TypedKeyValueSchema;
import studio.phaseshift.metatron.isa.tble.schema.storage.fURIAwareIndexedSchema;
import studio.phaseshift.metatron.isa.tble.space.ExistingTableSchema;
import studio.phaseshift.metatron.isa.tble.space.SQLSchemaGenerator;
import studio.phaseshift.metatron.isa.tble.space.SQLSchemaInstSet;
import studio.phaseshift.metatron.util.MTronException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.INST_CTOR_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SPACE_TID;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.tble.tbleInstSet.TBLE_ISA_INST_TID;
import static studio.phaseshift.metatron.isa.tble.tbleInstSet.TBLE_ISA_TID;

/**
 * A dual-mode JDBC-backed {@link Space} that provides both a key-value store and
 * automatic SQL-table mapping.
 *
 * <h3>Modes</h3>
 * <dl>
 *   <dt>Key-Value</dt>
 *   <dd>Paths that don't match a known SQL table are stored as typed key-value
 *       pairs.  On MariaDB/MySQL this uses an indexed MQTT-pattern schema; on
 *       PostgreSQL, SQLite, and others a {@link TypedKeyValueSchema} preserves
 *       full type fidelity.</dd>
 *   <dt>Table Mapping</dt>
 *   <dd>When the {@code TABLE} config key is present, the space auto-discovers
 *       existing SQL tables via JDBC metadata and routes reads/writes for those
 *       table paths to {@link ExistingTableSchema}.  Tables listed in
 *       {@code TABLE} but not yet in the database are created on first write
 *       ("create-on-first-write").</dd>
 * </dl>
 *
 * <h3>Foreign keys and {@code auto_from}</h3>
 * <p>When a record field is an {@code auto_from} instruction, tbleSpace stores
 * only the raw FK value in an {@code INTEGER} column and records the pointer
 * metadata in {@code _mtron_meta}.  On read the pointer is reconstructed and
 * lazily resolved — see {@link ExistingTableSchema)}.
 *
 * <h3>Configuration</h3>
 * <pre>{@code
 * tbleSpace.of(rec(
 *     uri(PATTERN), uri("db:#"),
 *     uri(HOST),    uri("postgresql://localhost:5432/mydb"),
 *     uri(DRIVER),  uri("org.postgresql.Driver"),
 *     uri(TABLE),   lst(uri("users"), uri("orders")),  // optional whitelist
 *     uri(ROUTE),   rec(uri("db:"), uri(""))
 * ).jvm(), f("/sys/space/tble"));
 * }</pre>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class tbleSpace extends AbstractSpace<Connection> implements SchemaSpace {

    // ---- Database product-name fragments (matched case-insensitively) -----------

    public static final String MARIADB = "mariadb";
    public static final String MYSQL = "mysql";
    public static final String POSTGRESQL = "postgresql";
    //public static final String SQLITE = "sqlite";

    // ---- Type system -----------------------------------------------------------

    public static fURI SQL_INST_TID = TBLE_ISA_INST_TID.extend(SQL);
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
                            uri(TABLE).maybe(), LST_TYPE,
                            uri(ROOT).maybe(), REC_TYPE,
                            uri(SCHEMA).maybe(), SCHEMA_CONFIG))
                    .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(TBLE_SPACE_TID),
                            lst(REC_TYPE),
                            (lhs, inst) -> tbleSpace.of(inst.arg(0).asRec().jvm(), inst.arg(0).vid())))
                    .create();

    // ---- Instance state --------------------------------------------------------

    protected ObjSerializer<?> serializer;
    protected TableSchema schema;
    protected ExistingTableSchema existingTableSchema;
    protected SQLSchemaGenerator schemaGenerator;
    protected String databaseName;

    // =========================================================================
    //  Factory
    // =========================================================================

    /**
     * Creates and returns a new {@code tbleSpace} from a configuration map.
     * The {@code HOST} key must be a JDBC-connectable URI <em>without</em> the
     * {@code jdbc:} prefix — it is prepended automatically.
     */
    public static tbleSpace of(final Map<Obj, Obj> config, final fURI vid) {
        // Ensure vid is never null — generate a default from tid if missing
        final fURI effectiveVid = null != vid ? vid : TBLE_SPACE_TID.extend("default");
        MTronException.wrap(() -> Class.forName(config.get(uri(DRIVER)).uriValue().toString()));
        try {
            final Connection conn = DriverManager.getConnection(
                    JDBC + config.get(uri(HOST)).uriValue().toString());
            // Defensive copy: the constructor mutates the config map (adds TABLE/SCHEMA
            // entries).  The original map is the shared jvm() of the caller's Rec —
            // modifying it concurrently while another thread iterates it causes
            // ConcurrentModificationException.
            return new tbleSpace(conn, new LinkedHashMap<>(config), TBLE_SPACE_TID, effectiveVid);
        } catch (final SQLException ex) {
            throw MTronException.of(ex);
        }
    }

    // =========================================================================
    //  Constructor
    // =========================================================================

    protected tbleSpace(final Connection sjvm, final Map<Obj, Obj> config,
                        final fURI tid, final fURI vid) {
        super(sjvm, config, tid, vid);
        LOG.info("connected {{b}}%s{{X}}", config.get(uri(HOST)));
        try {
            this.databaseName = sjvm.getCatalog() != null ? sjvm.getCatalog() : "db";
            initializeSchema(sjvm);
            initializeTableMapping(sjvm);
        } catch (final SQLException ex) {
            throw MTronException.of(ex);
        }
    }

    public String getDatabaseName() {
        return this.databaseName;
    }

    @Override
    public void close() {
        try {
            this.sjvm().close();
            SchemaSpace.super.close();
        } catch (final Exception e) {
            LOG.error(e);
        } finally {
            super.close();
        }
    }

    // =========================================================================
    //  Schema / table-mapping initialization
    // =========================================================================

    /**
     * Auto-detects the database product and installs the matching KV schema.
     */
    private void initializeSchema(final Connection conn) throws SQLException {
        final String dbProductName = conn.getMetaData().getDatabaseProductName().toLowerCase();
        if (dbProductName.contains(MARIADB) || dbProductName.contains(MYSQL)) {
            this.schema = new fURIAwareIndexedSchema();
            this.serializer = this.at(SERIALIZER).orElse(new ObjmtronSerializer());
            LOG.info("detected {{b}}mariadb/mysql{{X}} - using {{g}}mqtt schema with clean string serializer");
        } else {
            this.schema = new TypedKeyValueSchema();
            this.serializer = this.at(SERIALIZER).orElse(new ObjmtronSerializer());
            LOG.info("detected {{b}}%s{{X}} - using {{g}}typed schema", dbProductName);
        }
        this.schema.initialize(conn);
        LOG.info("initialized schema {{b}}%s{{X}} (version: %s)",
                this.schema.getClass().getSimpleName(), this.schema.version());
    }

    /**
     * Sets up auto-discovery of existing SQL tables and the schema generator.
     *
     * <p>When the {@code TABLE} config key is absent the space operates in pure
     * key-value mode.  When present (even as an empty list) table mapping is
     * enabled: JDBC metadata is scanned for existing tables, user-configured
     * tables are merged in, and a {@link SQLSchemaInstSet} is registered with
     * the Router in the {@code /m/} namespace for type resolution.
     */
    private void initializeTableMapping(final Connection conn) throws SQLException {
        final boolean enableTableMapping = this.at(uri(TABLE)) != null;
        if (!enableTableMapping) {
            this.existingTableSchema = null;
            this.schemaGenerator = null;
            LOG.info("table mapping {{y}}disabled{{X}}");
            return;
        }

        this.existingTableSchema = new ExistingTableSchema(this, "objs");
        this.existingTableSchema.initialize(conn);
        LOG.info("initialized {{g}}existing table schema{{X}} - discovered %s tables for database %s",
                this.existingTableSchema.getTableNames().size(), conn.getCatalog());

        // Merge user-configured tables (whitelist for create-on-first-write)
        // with JDBC-discovered tables (actual schema).  Order: user first,
        // then discovered — user intent preserved, discovery appended.
        syncTableConfig(new ArrayList<>());

        // Schema VID is in /m/ namespace — backed by system memSpace, not this
        // tbleSpace.  A VID under the tbleSpace pattern would cause the Router
        // to route schema reads/writes back into this space → recursion.
        final fURI schemaVid = this.vid().extend(SCHEMA).extend(INSTSET);
        this.schemaGenerator = new SQLSchemaGenerator(
                this.existingTableSchema.getTableMetadata(), schemaVid);

        // Register schema instset as a Router space for type resolution (e.g.
        // /m/tble/space/schema/db/type/users → users::T).  Stored at the
        // SCHEMA config key as a human-readable rec for introspection.
        final SQLSchemaInstSet schemaInstset = this.schemaGenerator.generateSchemaInstset(schemaVid);
        Router.global().addSpace(schemaInstset);
        schemaInstset.setup();
        this.at(f(SCHEMA).extend(NATIVE), this.schemaGenerator.generateNativeSchema(), MUTABLE);

        LOG.info("initialized {{g}}SQL schema{{X}} with %s table types",
                this.existingTableSchema.getTableNames().size());
    }

    // =========================================================================
    //  Table-config helpers
    // =========================================================================

    /**
     * Merges the given table names into the live {@code TABLE} config list,
     * preserving any names already present.  Used both during initialisation
     * (JDBC-discovered tables) and create-on-first-write (new tables).
     */
    private void syncTableConfig(final Collection<String> additional) {
        final Obj current = this.at(uri(TABLE));
        final List<Obj> tableList = new ArrayList<>();
        if (current != null && !current.isNoObj() && current.isLst())
            tableList.addAll(current.asLst().jvm());
        for (final String name : additional) {
            if (tableList.stream().noneMatch(
                    o -> o.isUri() && name.equalsIgnoreCase(o.asUri().uriValue().name())))
                tableList.add(uri(name));
        }
        this.at(uri(TABLE), lst(tableList), MUTABLE);
    }

    /**
     * Returns {@code true} when {@code tableName} appears in the {@code TABLE}
     * config list.  Only whitelisted names are eligible for create-on-first-write
     * — this prevents KV paths (e.g. {@code db:kv/test}) from accidentally
     * becoming SQL tables.
     */
    protected boolean isConfiguredTable(final String tableName) {
        final Obj tableConfig = this.at(uri(TABLE));
        if (tableConfig == null || tableConfig.isNoObj() || !tableConfig.isLst())
            return false;
        return tableConfig.asLst().lstValue().stream()
                .anyMatch(o -> o.isUri()
                        && tableName.equalsIgnoreCase(o.asUri().uriValue().name()));
    }

    // =========================================================================
    //  Lazy table-schema initialisation
    // =========================================================================

    /**
     * Creates the {@link ExistingTableSchema} on first access when the space was
     * mounted without a {@code TABLE} config key and one is added at runtime via
     * {@code /sys/space/.../table -> [person,score]}.
     */
    protected synchronized void lazyInitExistingTableSchema() {
        if (this.existingTableSchema == null) {
            try {
                this.existingTableSchema = new ExistingTableSchema(this, "objs");
                this.existingTableSchema.initialize(this.sjvm());
                LOG.info("lazy-initialized {{g}}existing table schema{{X}} - discovered %s tables",
                        this.existingTableSchema.getTableNames().size());
            } catch (final SQLException ex) {
                throw MTronException.of(ex);
            }
        }
    }

    // =========================================================================
    //  Path resolution
    // =========================================================================
    // =========================================================================
    //  I/O — Writer
    // =========================================================================

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (pattern, obj) -> {
            try {
                if (pattern.hasPattern()) {
                    // Wildcard write: expand to matching keys, write to each
                    this.directReader().apply(pattern)
                            .forEachRemaining(kv -> this.write(kv.furi(), obj));
                } else {
                    final fURI aligned = Space.Helper.routeFromSpace(pattern, this.routes());

                    if (this.existingTableSchema != null
                            && this.existingTableSchema.isTablePath(aligned)) {
                        // ── table-mapped path (existing table) ──
                        this.existingTableSchema.write(this.sjvm(), aligned, obj);

                    } else if (obj.isRec()) {
                        final DataPath dp = DataPath.ofSpaceRelative(aligned, this.databaseName);
                        if (dp.hasEntry() && isConfiguredTable(dp.collection())
                                && !dp.entryIsWildcard()) {
                            lazyInitExistingTableSchema();
                            if (!this.existingTableSchema.getTableNames()
                                    .contains(dp.collection().toLowerCase())) {
                                this.existingTableSchema.createTableFromRecord(
                                        this.sjvm(), dp.collection(), obj.asRec());
                                syncTableConfig(List.of(dp.collection()));
                            }
                            this.existingTableSchema.write(this.sjvm(), aligned, obj);
                        } else {
                            writeKV(aligned, obj);
                        }

                    } else {
                        // ── key-value path ──
                        writeKV(aligned, obj);
                    }
                }
            } catch (final SQLException e) {
                throw MTronException.of(e);
            }
            return obj;
        };
    }

    /**
     * Dispatches a key-value write to the appropriate schema backend.
     */
    private void writeKV(final fURI pattern, final Obj obj) throws SQLException {
        if (this.schema instanceof TypedKeyValueSchema typed) {
            typed.write(this.sjvm(), pattern, obj);
        } else {
            final String json = obj.isNoObj() ? null : this.serializer.write(obj).toString();
            this.schema.write(this.sjvm(), pattern, json);
        }
    }

    // =========================================================================
    //  I/O — Reader
    // =========================================================================

    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        return (pattern) -> {
            try {
                LOG.debug("looking for table vid: %s", pattern);
                final fURI aligned = Space.Helper.routeFromSpace(pattern, this.routes());

                // ── table-mapped path ──
                if (this.existingTableSchema != null
                        && this.existingTableSchema.isTablePath(aligned)) {
                    final Iterator<IdObj> raw = this.existingTableSchema.read(
                            this.sjvm(), aligned);
                    final List<IdObj> all = new ArrayList<>();
                    raw.forEachRemaining(kv -> {
                        // Use pattern for exact node queries so the fURI matches what
                        // locateBasePoly/unrollPoly produce — prevents dupes in resolveRead
                        final fURI external = pattern.isNode()
                                ? pattern
                                : Space.Helper.routeToSpace(kv.furi(), this.routes());
                        all.add(IdObj.of(Space.Helper.routeToSpace(kv.furi(), this.routes()), kv.obj()));
                        if (pattern.hasPattern() && kv.obj().isPoly())
                            all.addAll(Space.Helper.unrollPoly(
                                    external, kv.obj().as(), pattern.asNode()));
                    });
                    return all.iterator();
                }

                // ── key-value path ──
                return collectResults(this.schema.read(this.sjvm(), aligned), pattern);

            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        };
    }

    /**
     * Wraps a raw result iterator, adding poly-unrolling for wildcard patterns.
     * Shared by both the table-mapped and key-value read paths.
     */
    private Iterator<IdObj> collectResults(final Iterator<IdObj> raw,
                                           final fURI pattern) {
        final List<IdObj> all = new ArrayList<>();
        raw.forEachRemaining(kv -> {
            // Convert internal URI (without scheme prefix) back to external URI
            // so that fURI.test() and unrollPoly() work against the original pattern.
            final fURI external = Space.Helper.routeToSpace(kv.furi(), this.routes());
            if (pattern.hasPattern()) {
                // Wildcard query: include the base if it matches the pattern,
                // then independently unroll any poly children (they may match
                // even when the parent doesn't).
                if (external.test(pattern.asNode()))
                    all.add(IdObj.of(external, kv.obj()));
                if (kv.obj().isPoly())
                    all.addAll(Space.Helper.unrollPoly(
                            external, kv.obj().as(), pattern.asNode()));
            } else {
                // Exact match — convert and add
                all.add(IdObj.of(external, kv.obj()));
            }
        });
        return all.iterator();
    }
}
