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

package studio.phaseshift.metatron.isa.m.db.tble;

import studio.phaseshift.metatron.furi.DataPath;
import studio.phaseshift.metatron.furi.QProc;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.AbstractDataPathSpace;
import studio.phaseshift.metatron.isa.SchemaSpace;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.db.tble.schema.storage.TableSchema;
import studio.phaseshift.metatron.isa.m.db.tble.schema.storage.TypedKeyValueSchema;
import studio.phaseshift.metatron.isa.m.db.tble.schema.storage.fURIAwareIndexedSchema;
import studio.phaseshift.metatron.isa.m.db.tble.space.ExistingTableSchema;
import studio.phaseshift.metatron.isa.m.db.tble.space.tbleIncrQ;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.web.type.ObjSQLSerializer;
import studio.phaseshift.metatron.isa.m.web.type.ObjSerializer;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.IteratorUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.sql.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.db.tble.tbleInstSet.TBLE_ISA_INST_TID;
import static studio.phaseshift.metatron.isa.m.db.tble.tbleInstSet.TBLE_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs0;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

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
 * ).jvm(), f("~/memory/space/tble"));
 * }</pre>
 * The {@code TABLE} config key is deprecated and ignored — table mapping is
 * always on.  All collection paths except {@code "kv_store"}, {@code "_mtron_meta"},
 * and {@code "kv"} are candidates for auto table creation.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class tbleSpace extends AbstractDataPathSpace<Connection> implements SchemaSpace {

    // ---- Database product-name fragments (matched case-insensitively) -----------

    public static final String MARIADB = "mariadb";
    public static final String MYSQL = "mysql";
    public static final String POSTGRESQL = "postgresql";
    public static final String SQLITE = "sqlite";

    // ---- Type system -----------------------------------------------------------

    public static fURI SQL_INST_TID = TBLE_ISA_INST_TID.extend(SQL);
    public static fURI TBLE_SPACE_TID = TBLE_ISA_TID.extend(SPACE).extend("tblespace");

    // ---- Instance state --------------------------------------------------------

    protected ObjSerializer<?> serializer;
    protected TableSchema schema;
    protected ExistingTableSchema existingTableSchema;
    protected boolean tablesDiscovered = false;
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
            try {
                // schema initialization runs after the space has mounted: a failure must not leave it there
                return Space.Helper.construct(() -> new tbleSpace(conn, new LinkedHashMap<>(config), TBLE_SPACE_TID, vid));
            } catch (final RuntimeException e) {
                try {
                    conn.close();
                } catch (final SQLException closing) {
                    e.addSuppressed(closing);
                }
                throw e;
            }
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
        SchemaSpace.Helper.mount(this);
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
            if (null != this.sjvm() && !this.sjvm().isClosed())
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
            this.serializer = this.at(SERIALIZER).orElse(ObjmtronSerializer.single());
            LOG.info("detected {{b}}mariadb/mysql{{X}} using {{y}}mqtt{{X}} schema {{g}}[{{b}}serializer: {{y}}%s{{g}}]", this.serializer.typeId());
        } else {
            this.schema = new TypedKeyValueSchema();
            this.serializer = this.at(SERIALIZER).orElse(ObjmtronSerializer.single());
            LOG.info("detected {{b}}%s{{X}} using {{y}}typed{{X}} schema {{g}}[{{b}}serializer: {{y}}%s{{g}}]", dbProductName, this.serializer.typeId());
        }
        this.schema.initialize(conn);
        LOG.info("initialized schema {{b}}%s{{X}} (version: %s)",
                this.schema.getClass().getSimpleName(), this.schema.version());
    }

    /**
     * Sets up table-mapping support without running the expensive catalog walk.
     *
     * <p>The existing-table discovery ({@link #ensureTables()}) is deferred
     * until a collection that could map to a SQL table is first accessed.  Pure
     * key-value usage (collection {@code kv}/{@code msg}) never needs it, so this
     * keeps per-space construction cheap for the common KV path.
     */
    private void initializeTableMapping(final Connection conn) {
        this.existingTableSchema = new ExistingTableSchema(this, "kv_store");
    }

    /**
     * Lazily discovers the existing tables (populating {@link #existingTableSchema}) on the first table-mapped access:
     * the catalog walk SQL reads, writes and rewrite guards need to see the real tables. Idempotent. A table's type is
     * the {@link SchemaSpace.Helper}'s, inferred when it is first read.
     */
    public void ensureTables() {
        if (this.tablesDiscovered)
            return;
        this.tablesDiscovered = true;
        try {
            this.existingTableSchema.initialize(this.sjvm());
        } catch (final SQLException e) {
            this.tablesDiscovered = false; // allow a later retry
            throw MTronException.of(e);
        }
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
     * The tables the catalog holds (the key/value and metadata tables are not collections), lowercased as a table's
     * type is addressed.
     */
    @Override
    public Stream<String> collections() {
        return MTronException.wrap(() -> {
            final List<String> tables = new ArrayList<>();
            try (final ResultSet catalog = this.sjvm().getMetaData().getTables(this.sjvm().getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (catalog.next())
                    tables.add(catalog.getString("TABLE_NAME").toLowerCase());
            }
            return tables;
        }).stream().filter(this::isTableCandidate);
    }

    @Override
    public Rec fields(final String table) {
        return this.collections().anyMatch(table::equalsIgnoreCase)
                ? MTronException.wrap(() -> this.existingTableSchema.fields(this.sjvm(), table))
                : rec0();
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
            LOG.status(DEBUG, "sql update: %s", valid.get(i));
            try (final Statement stmt = this.sjvm().createStatement()) {
                stmt.executeUpdate(valid.get(i));
            }
        }

        // 3. Execute the final statement and return its result
        final String lastStmt = valid.getLast();
        LOG.status(DEBUG, "sql query: %s", lastStmt);
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
            fURI aligned = null;
            try {
                if (pattern.hasPattern()) {
                    // Wildcard write: expand to matching keys, write to each -- a level's interior node (noobj: a
                    // deeper key passes through it, nothing is stored there) is no key to write
                    IteratorUtil.stream(this.directReader().apply(pattern))
                            .filter(kv -> !kv.obj().isNoObj())
                            .toList()
                            .forEach(kv -> this.write(kv.furi(), obj));
                } else {
                    aligned = Space.Helper.routeFromSpace(pattern, this.routes());

                    // Lazy table discovery — the table paths below need the real tables.
                    // Collections that can't map to SQL tables (kv/msg) skip the catalog walk entirely.
                    final DataPath dp = DataPath.of(f(this.databaseName).extend(aligned));
                    if (dp.hasCollection() && isTableCandidate(dp.collection().toLowerCase()))
                        this.ensureTables();

                    // a type written to a collection's path declares the collection (SchemaSpace.Helper)
                    if (obj.isType() && dp.hasCollection() && !dp.hasEntry()) {
                        Machine.write(this.schema().vid().extend(dp.collection().toLowerCase()), obj);
                        return obj;
                    }

                    if (this.existingTableSchema.isTablePath(aligned)) {
                        // ── table-mapped path (existing table) ──
                        // WHAT THE TABLE IS ASKED TO HOLD: the row a rec write replaces, or the row a field write leaves
                        final String coll = dp.collection().toLowerCase();
                        if (dp.hasEntry() && !dp.entryIsWildcard() && dp.hasField()) {
                            final Iterator<IdObj> rows = this.existingTableSchema.read(this.sjvm(), f(dp.collection()).extend(dp.entry()));
                            final Obj row = rows.hasNext() ? rows.next().obj() : noobj();
                            if (row.isRec())
                                SchemaSpace.Helper.admit(this, coll, row.asRec().at(uri(dp.hasExtension()
                                        ? f(dp.field()).extend(dp.extension()) : f(dp.field())), obj, IMMUTABLE));
                        } else if (dp.hasEntry() && !dp.entryIsWildcard() && obj.isRec())
                            SchemaSpace.Helper.admit(this, coll, obj);
                        this.existingTableSchema.write(this.sjvm(), aligned, obj);

                    } else if (obj.isRec()) {
                        final String coll = dp.collection().toLowerCase();
                        if (dp.hasEntry() && !dp.entryIsWildcard()
                                && isTableCandidate(coll)) {
                            // before the table exists: a new table's first record is its type
                            SchemaSpace.Helper.admit(this, coll, obj);
                            if (!this.existingTableSchema.getTableNames().contains(coll)) {
                                this.existingTableSchema.createTableFromRecord(
                                        this.sjvm(), coll, obj.asRec(), dp.entry());
                                // Promote flat kv entries parked under this prefix
                                // into the new table (standard, schema-enforcing writes).
                                this.migrateFlatToStructured(coll);
                            }
                            this.existingTableSchema.write(this.sjvm(), aligned, obj.asRec());
                        } else {
                            writeFlat(aligned, obj);
                        }

                    } else {
                        // ── key-value path ──
                        writeFlat(aligned, obj);
                    }
                }
            } catch (final SQLException e) {
                throw MTronException.of(e);
            }
            // Attach the actual write location as the object's VID so the
            // caller can discover where the object was stored (important
            // for auto-increment and routed writes).
            return obj.vid(pattern.hasPattern() ? obj.vid() : aligned);
        };
    }

    /**
     * Dispatches a key-value write to the appropriate schema backend.
     */
    @Override
    protected void writeFlat(final fURI pattern, final Obj obj) {
        try {
            if (this.schema instanceof TypedKeyValueSchema typed) {
                typed.write(this.sjvm(), pattern, obj);
            } else {
                final String json = obj.isNoObj() ? null : this.serializer.write(obj).toString();
                this.schema.write(this.sjvm(), pattern, json);
            }
        } catch (final SQLException e) {
            throw MTronException.of(e);
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
                    return Machine.read(aligned).stream().map(o -> new IdObj(aligned, o)).iterator();
                }
                // Lazy table discovery: the table paths below need the real tables.
                final DataPath dp = DataPath.of(f(this.databaseName).extend(aligned));
                if (dp.hasCollection() && isTableCandidate(dp.collection().toLowerCase()))
                    this.ensureTables();

                // ── collection-level schema resolution ──
                // Shared across all SchemaSpaces: /db/collection → type from schema InstSet.
                // A collection with no declared schema falls through to the table /
                // key-value paths below: a one-segment URI (db:a01) is indistinguishable
                // from a collection here, but it is really a flat kv entry.
                if (dp.hasCollection() && !dp.hasEntry()) {
                    final Iterator<IdObj> schemaResults =
                            resolveCollectionSchema(dp.collection().toLowerCase());
                    if (schemaResults.hasNext())
                        return schemaResults;
                }

                // ── table-mapped path (entry-level) ──
                if (this.existingTableSchema != null
                        && this.existingTableSchema.isTablePath(aligned)) {
                    final Iterator<IdObj> raw = this.existingTableSchema.read(
                            this.sjvm(), aligned);
                    final List<IdObj> all = new ArrayList<>();
                    raw.forEachRemaining(kv -> {
                        // Use the value's base uri as the polyvid whenever the pattern carries a
                        // wildcard, so unrollPoly expands against the stored value's location
                        // rather than the pattern itself (a node pattern like field/+ would
                        // otherwise fabricate keys under the wildcard segment).
                        final fURI external = pattern.hasPattern()
                                ? Space.Helper.routeToSpace(kv.furi(), this.routes())
                                : pattern;
                        final fURI rawUri = Space.Helper.routeToSpace(kv.furi(), this.routes());
                        // Only surface the raw value when it sits at the pattern's depth; on a
                        // wildcard read the unrollPoly below emits the matching children instead
                        // (mirrors dcmntSpace's docVID.test(nodePattern) gate).  Compare in the
                        // space-internal form: routeToSpace can reconstruct an external via a
                        // different route entry in multi-route spaces.
                        if (!pattern.hasPattern() || kv.furi().test(aligned.asNode()))
                            all.add(IdObj.of(rawUri, kv.obj()));
                        if (pattern.hasPattern() && kv.obj().isPoly())
                            all.addAll(Space.Helper.unrollPoly(
                                    external, kv.obj().as(), pattern.asNode()));
                    });
                    return all.iterator();
                }

                // ── key-value path ──
                return this.flat(aligned, pattern);

            } catch (final Error | Exception e) {
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

    // =======================================================================
    //  AbstractDataPathSpace hooks — flat kv_store fallback + migration
    // =======================================================================

    @Override
    protected boolean isReservedFlatName(final String firstSegment) {
        return !isTableCandidate(firstSegment);
    }

    @Override
    protected boolean isStructuredCollection(final fURI relativePath) {
        return this.existingTableSchema != null
                && this.existingTableSchema.isTablePath(relativePath);
    }

    @Override
    protected Iterator<IdObj> readFlat(final fURI relativePath, final fURI pattern) {
        try {
            return collectResults(this.schema.read(this.sjvm(), relativePath), pattern);
        } catch (final SQLException e) {
            throw MTronException.of(e);
        }
    }

    @Override
    protected void writeStructured(final fURI relativePath, final Obj obj) {
        final DataPath dp = DataPath.withoutDB(relativePath);
        if (obj.isRec() && dp.hasEntry() && !dp.hasField())
            SchemaSpace.Helper.admit(this, dp.collection().toLowerCase(), obj);
        try {
            this.existingTableSchema.write(this.sjvm(), relativePath, obj);
        } catch (final SQLException e) {
            throw MTronException.of(e);
        }
    }

    @Override
    protected Stream<FlatEntry> scanFlat(final String collectionPrefix) {
        final String prefix = collectionPrefix + "/";
        try {
            return IteratorUtil.stream(this.schema.read(this.sjvm(), f(collectionPrefix + "/#")))
                    .filter(kv -> kv.furi().toString().startsWith(prefix))
                    .map(kv -> new FlatEntry(kv.furi().toString(), kv.obj()));
        } catch (final SQLException e) {
            throw MTronException.of(e);
        }
    }

    @Override
    protected void removeFlat(final String fullKey) {
        try {
            this.schema.delete(this.sjvm(), f(fullKey));
        } catch (final SQLException e) {
            throw MTronException.of(e);
        }
    }
}
