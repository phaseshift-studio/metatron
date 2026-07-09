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
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSQLSerializer;
import studio.phaseshift.metatron.isa.tble.space.tbleIncrQ;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.SchemaSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.INST_CTOR_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SPACE_TID;
import static studio.phaseshift.metatron.isa.m.type.InstSet.INSTSET_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs0;
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
 *   <dd>Paths matching the reserved collection names {@code "kv_store"} (KV schema
 *       internal table), {@code "_mtron_meta"} (FK metadata), and {@code "kv"}
 *       (conventional KV namespace) are stored as typed key-value pairs.  On
 *       MariaDB/MySQL this uses an indexed MQTT-pattern schema; on PostgreSQL,
 *       SQLite, and others a {@link TypedKeyValueSchema} preserves full type
 *       fidelity.</dd>
 *   <dt>Table Mapping</dt>
 *   <dd>All other collection paths are automatically treated as SQL tables.
 *       The space auto-discovers existing SQL tables via JDBC metadata and
 *       creates new tables on first Rec write ("create-on-first-write").
 *       Table schemas are published as Types in the schema instset so wildcard
 *       queries ({@code *db:+}) resolve immediately.</dd>
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
 *     uri(ROUTE),   rec(uri("db:"), uri(""))
 * ).jvm(), f("/sys/space/tble"));
 * }</pre>
 * The {@code TABLE} config key is deprecated and ignored — table mapping is
 * always on.  All collection paths except {@code "kv_store"}, {@code "_mtron_meta"},
 * and {@code "kv"} are candidates for auto table creation.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class tbleSpace extends AbstractSpace<Connection> implements SchemaSpace {

    // ---- Database product-name fragments (matched case-insensitively) -----------

    public static final String MARIADB = "mariadb";
    public static final String MYSQL = "mysql";
    public static final String POSTGRESQL = "postgresql";
    public static final String SQLITE = "sqlite";

    // ---- Type system -----------------------------------------------------------

    public static fURI SQL_INST_TID = TBLE_ISA_INST_TID.extend(SQL);
    public static fURI TBLE_SPACE_TID = TBLE_ISA_TID.extend(SPACE).extend("tblespace");
    public static final Type TBLE_SPACE_TYPE =
            Type.Builder.build()
                    .tid(SPACE_TID)
                    .vid(TBLE_SPACE_TID)
                    .isaPredicate(rec(
                            uri(HOST), URI_TYPE,
                            uri(DRIVER), URI_TYPE))
                    .constructor(instC(INST_CTOR_TID.dom(ALL.maybe()).rng(TBLE_SPACE_TID),
                            lst(REC_TYPE),
                            (lhs, inst) -> tbleSpace.of(inst.arg(0).asRec().jvm(), inst.arg(0).vid())))
                    .create();

    // ---- Instance state --------------------------------------------------------

    protected ObjSerializer<?> serializer;
    protected TableSchema schema;
    protected ExistingTableSchema existingTableSchema;
    protected SQLSchemaGenerator schemaGenerator;
    protected SQLSchemaInstSet schemaInstset;
    protected String databaseName;
    protected String backend;

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
        //final fURI effectiveVid = null != vid ? vid : TBLE_SPACE_TID.extend("default");
        MTronException.wrap(() -> Class.forName(config.get(uri(DRIVER)).uriValue().toString()));
        try {
            final Connection conn = DriverManager.getConnection(
                    JDBC + config.get(uri(HOST)).autoResolve(noobj()).uriValue().toString());
            // MariaDB/MySQL: allow explicit zero PK values (avoid AUTO_INCREMENT trigger).
            try (final Statement stmt = conn.createStatement()) {
                stmt.execute("SET sql_mode = 'NO_AUTO_VALUE_ON_ZERO'");
            } catch (final SQLException ignored) { /* not MariaDB/MySQL, or no permission */ }
            // Defensive copy: the constructor mutates the config map (adds TABLE/SCHEMA
            // entries).  The original map is the shared jvm() of the caller's Rec —
            // modifying it concurrently while another thread iterates it causes
            // ConcurrentModificationException.
            return new tbleSpace(conn, new LinkedHashMap<>(config), TBLE_SPACE_TID, vid);
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
        // Re-route QPROC entries through addQ() so the override can substitute
        // tbleIncrQ for the default AtomicLong-based incrQ.
        final Lst qprocs = this.at(uri(QPROC)).orElse(lst()).asLst();
        if (!qprocs.isEmpty()) {
            final List<QProc> snapshot = new ArrayList<>(qprocs.<QProc>elements().toList());
            this.at(uri(QPROC), lst(), MUTABLE);
            for (final QProc q : snapshot)
                this.addQ(q);
        }
        LOG.info("connected {{b}}%s{{X}}", this.at(HOST));
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

    /**
     * The detected database product name (e.g. {@code sqlite}, {@code postgresql}, {@code mariadb}).
     */
    public String backend() {
        return this.backend;
    }

    /**
     * Convenience: true when the backend is SQLite.
     */
    public boolean isSqlite() {
        return this.backend != null && this.backend.contains(SQLITE);
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
        this.backend = dbProductName;
        if (dbProductName.contains(MARIADB) || dbProductName.contains(MYSQL)) {
            this.schema = new fURIAwareIndexedSchema();
            this.serializer = this.at(SERIALIZER).orElse(ObjmtronSerializer.singleNoClip());
            LOG.info("detected {{b}}mariadb/mysql{{X}} using {{y}}mqtt{{X}} schema {{g}}[{{b}}serializer: {{y}}%s{{g}}]", this.serializer.typeId());
        } else {
            this.schema = new TypedKeyValueSchema();
            this.serializer = this.at(SERIALIZER).orElse(ObjmtronSerializer.singleNoClip());
            LOG.info("detected {{b}}%s{{X}} using {{y}}typed{{X}} schema {{g}}[{{b}}serializer: {{y}}%s{{g}}]", dbProductName, this.serializer.typeId());
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
        this.existingTableSchema = new ExistingTableSchema(this, "kv_store");
        this.existingTableSchema.initialize(conn);
        LOG.info("initialized {{g}}existing table schema{{X}} - discovered %s tables for database %s",
                this.existingTableSchema.getTableNames().size(), null == conn.getCatalog() ? "" : conn.getCatalog());

        // Schema VID is in /m/ namespace — backed by system memSpace, not this
        // tbleSpace.  A VID under the tbleSpace pattern would cause the Router
        // to route schema reads/writes back into this space → recursion.
        final fURI schemaVid = this.vid().extend(INSTSET);
        this.schemaGenerator = new SQLSchemaGenerator(
                this.existingTableSchema.getTableMetadata(), schemaVid,
                this.databaseName,
                this.existingTableSchema.getLogicalTypes());

        // Wire schema generator into existingTableSchema so that table
        // dereferences return instset-encoded Types (single source of truth)
        // instead of SQL-specific TABLE_TID URIs.
        this.existingTableSchema.setSchemaGenerator(this.schemaGenerator);

        // Register schema instset as a Router space for type resolution (e.g.
        // /m/tble/space/schema/db/type/users → users::T).  The instset Types
        // are the single source of truth — column types AND FK references are
        // embedded in the isaPredicate; no separate native schema needed.
        this.schemaInstset = this.schemaGenerator.generateSchemaInstset(schemaVid);
        Router.global().addSpace(this.schemaInstset);
        this.schemaInstset.setup();

        // Wire schema into the space's own Rec so SchemaSpace.schema()
        // (and resolveCollectionSchema) can find it at SCHEMA.
        this.at(uri(SCHEMA), this.schemaInstset, MUTABLE);

        LOG.info("initialized {{g}}SQL schema{{X}} with %s table types",
                this.existingTableSchema.getTableNames().size());
    }

    @Override
    public Space addQ(final QProc qProc) {
        // Intercept incrQ: replace the default in-memory counter with our
        // DB-backed version that has a reference to this space.
        final QProc toAdd = qProc.pattern().equals(QCollection.INCRQ_PATTERN)
                ? new tbleIncrQ(this) : qProc;
        final Obj key = uri(QPROC);
        if (this.at(key).isNoObj())
            this.at(key, lst(), MUTABLE);
        this.at(key).asLst().add(toAdd, MUTABLE);
        return this;
    }

    /**
     * Exposed for {@link tbleIncrQ} — access to table schema.
     */
    public ExistingTableSchema existingTableSchema() {
        return this.existingTableSchema;
    }

    /**
     * The schema instset — single source of truth for table types.
     */
    public SQLSchemaInstSet schemaInstset() {
        return this.schemaInstset;
    }

    // =========================================================================
    //  Table-config helpers
    // =========================================================================

    /**
     * Returns {@code true} when {@code collectionName} is eligible for
     * create-on-first-write table creation.  Excludes the KV schema table
     * ({@code "kv_store"}) and the FK meta table ({@code "_mtron_meta"}).
     */
    protected boolean isTableCandidate(final String collectionName) {
        final String n = collectionName.toLowerCase();
        return !n.equals("kv_store")
                && !n.equals("_mtron_meta")
                && !n.equals("kv")
                && !n.equals("msg");
    }

    /**
     * Returns {@code true} when a type exists in the schema instset for the
     * given collection — the instset is the single source of truth for which
     * collections are typed tables.
     */
    boolean isTableDeclared(final String collectionName) {
        if (this.schemaInstset == null) return false;
        return this.schemaInstset.types().stream()
                .anyMatch(t -> t.vid().name().equalsIgnoreCase(collectionName));
    }

    /**
     * Surgically add or refresh the Type for a single table after on-the-fly
     * creation or alteration.  Writes through {@link Router} so the type lands
     * in the schema instset that owns it — no full rebuild, no direct coupling
     * to the instset instance.
     */
    public void onTableChanged(final String tableName) {
        if (this.schemaInstset == null)
            return;
        final ExistingTableSchema.TableMetadata metadata =
                this.existingTableSchema.getTableMetadata().stream()
                        .filter(t -> t.tableName().equalsIgnoreCase(tableName))
                        .findFirst().orElse(null);
        if (metadata == null) return;
        final Type type = this.schemaGenerator.refreshTableType(metadata);
        Router.writeToSpace(type.vid(), type);
    }

    // =========================================================================
    //  SQL execution
    // =========================================================================

    /**
     * Executes a multi-line SQL expression against this space's JDBC connection.
     * <p>
     * SQL comments ({@code --}) and blank lines are ignored.  The expression is
     * split on {@code ;} into individual statements.  All statements except the
     * last are executed as updates (DDL/DML).  The final statement is executed
     * and its result set — if any — is converted to mtron {@link Rec} rows via
     * {@link ObjSQLSerializer#readCurrentAsRec(ResultSet)} and returned as an
     * {@code Objs} stream.  When the final statement produces no result set
     * (e.g. a terminal INSERT or CREATE), {@code noobj} is returned.
     *
     * @param sqlExpression one or more SQL statements separated by semicolons
     * @return the result of the last statement as a stream of rows, or noobj
     * @throws SQLException if a database error occurs
     */
    public Obj sql(final String sqlExpression) throws SQLException {
        // 1. Normalize: strip comments, blank lines, split into statements
        final List<String> lines = sqlExpression
                .lines()
                .map(String::trim)
                .filter(l -> !l.isEmpty() && !l.startsWith("--"))
                .toList();

        if (lines.isEmpty())
            return noobj();

        final String[] statements = String.join(" ", lines).split(";");

        // Filter out blank fragments after the split
        final List<String> valid = new ArrayList<>();
        for (final String s : statements) {
            final String trimmed = s.trim();
            if (!trimmed.isBlank())
                valid.add(trimmed);
        }

        if (valid.isEmpty())
            return noobj();

        // 2. Execute all but the last as updates
        for (int i = 0; i < valid.size() - 1; i++) {
            LOG.info("sql update: %s", valid.get(i));
            try (final Statement stmt = this.sjvm().createStatement()) {
                stmt.executeUpdate(valid.get(i));
            }
        }

        // 3. Execute the final statement and return its result
        final String lastStmt = valid.getLast();
        LOG.info("sql query: %s", lastStmt);
        try (final Statement stmt = this.sjvm().createStatement()) {
            final boolean hasResultSet = stmt.execute(lastStmt);
            if (hasResultSet) {
                try (final ResultSet rs = stmt.getResultSet()) {
                    Obj objs = objs0();
                    while (rs.next()) {
                        objs = objs.append(ObjSQLSerializer.readCurrentAsRec(rs));
                    }
                    return objs;
                }
            }
            return noobj();
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

                    // -- Collection-path type declaration ----------------
                    // Writing a Type to a collection path (no entry) is a
                    // schema declaration: place it under the schema instset
                    // namespace and write it directly.  (auto-save skips
                    // Types, so we can't rely on selfVID+objCheckAndSave.)
                    if (obj.isType() && this.schemaInstset != null) {
                        final DataPath dp = DataPath.of(f(this.databaseName).extend(aligned));
                        if (dp.hasCollection() && !dp.hasEntry()) {
                            final fURI typeVID = this.schemaInstset.pattern()
                                    .retractPattern().extend(dp.collection());
                            final Type typed = (Type) obj.asType().vid(typeVID);
                            Router.writeToSpace(typeVID, typed);
                            LOG.info("registered type {{b}}%s{{X}} at collection path %s", typeVID, dp.collection());
                            return typed;
                        }
                    }

                    if (this.existingTableSchema != null
                            && this.existingTableSchema.isTablePath(aligned)) {
                        // ── table-mapped path (existing table) ──
                        this.existingTableSchema.write(this.sjvm(), aligned, obj);

                    } else if (obj.isRec()) {
                        final DataPath dp = DataPath.of(f(this.databaseName).extend(aligned));
                        final String coll = dp.collection().toLowerCase();
                        if (dp.hasEntry() && !dp.entryIsWildcard()
                                && isTableCandidate(coll)) {
                            final boolean isNew = !this.existingTableSchema.getTableNames().contains(coll);
                            if (isNew)
                                this.existingTableSchema.createTableFromRecord(
                                        this.sjvm(), coll, obj.asRec());
                            this.existingTableSchema.write(this.sjvm(), aligned, obj.asRec());
                            if (isNew)
                                onTableChanged(coll);
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
                if (pattern.hasScheme() && aligned.hasScheme() && !pattern.scheme().equals(aligned.scheme())) {
                    LOG.warn("interspace reroute: %s => %s", pattern, aligned);
                    return Router.readFromSpace(aligned).stream().map(o -> new IdObj(aligned, o)).iterator();
                }
                // ── collection-level schema resolution ──
                // Shared across all SchemaSpaces: /db/collection → type from schema InstSet.
                // Returns empty iterator when collection is unknown, falling through
                // to the key-value path below.
                final DataPath dp = DataPath.of(f(this.databaseName).extend(aligned));
                if (dp.hasCollection() && !dp.hasEntry()) {
                    final Iterator<IdObj> schemaResults =
                            resolveCollectionSchema(dp.collection());
                    if (schemaResults.hasNext())
                        return collectResults(schemaResults, pattern);
                }

                // ── table-mapped path (entry-level) ──
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
