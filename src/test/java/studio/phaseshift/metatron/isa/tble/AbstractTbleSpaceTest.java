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

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import studio.phaseshift.metatron.AbstractDataPathSpaceTest;
import studio.phaseshift.metatron.SkipRegexTest;
import studio.phaseshift.metatron.algebra.rewrite.CommonRewritesTestContract;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.IncrQTest;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.llm.llmInstSet.AI_MESSAGE_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.INT_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.*;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.tble.tbleInstSet.TBLE_ISA_TID;

/**
 * Abstract base test suite for tbleSpace with database-agnostic tests.
 * Subclasses provide database-specific configuration via {@link DatabaseConfig}.
 * <p>
 * See {@link #parseObj(String)}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SkipRegexTest(value = {
        // Remaining testUpdateWrite skips are schema-fixed incompatibilities, NOT the
        // objs serialization bug (tble already round-trips objs losslessly as mtron
        // strings in TEXT columns — see M48, which passes).  M12b/M43/M44/M44b: a "+"
        // merge produces an Objs that cannot be stored in the seeded INTEGER column.
        // M46: a mono cannot overwrite a schema-fixed table row.  M32: >>= through a
        // !* cross-ref does not deref the target (core semantics, shared with dcmnt).
        // M44c/M45/M48 were stale skips and now pass.
        @SkipRegexTest.Skip(method = "testUpdateWrite", params = {"M12b", "M43", "M44:", "M44b", "M46", "M32"})
})
public abstract class AbstractTbleSpaceTest extends AbstractDataPathSpaceTest implements CommonRewritesTestContract, IncrQTest {

    /**
     * tbleIncQ is the native incrQ qproc implemented as AUTO INCREMENT
     *
     * @return
     */
    @Override
    public fURI incrQBaseURI() {
        return f(getSpace().pattern().scheme() + ":incrq");
    }

    @Override
    protected fURI deducedBaseUri() {
        return f("db:scratch");
    }

    @Override
    protected boolean supportsLosslessFlatMigration() {
        // Tables are schema-fixed: a mono flat entry can't be promoted to a row.
        return false;
    }

    @Override
    protected void dropDeducedCollection(final String collectionName) {
        // Drop the table so the shared DataPathSpace tests stay order-independent.
        if (this.space instanceof tbleSpace ts) {
            try {
                ts.sql("DROP TABLE IF EXISTS " + collectionName);
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
        }
    }

    protected static final fURI SPACE_VID = f("/sys/space/tabledb/test");
    protected static DatabaseConfig staticDbConfig;
    protected final DatabaseConfig dbConfig;
    private static final AtomicInteger testSpaceCounter = new AtomicInteger(0);
    protected static final GraphittyLogger LOG = Graphitty.log(AbstractTbleSpaceTest.class);

    public AbstractTbleSpaceTest(final DatabaseConfig dbConfig) {
        super(f("db:kv/test"), () -> {
            if (staticDbConfig == null) {
                throw new IllegalStateException(
                        "staticDbConfig not initialized. @BeforeAll method must run first.");
            }
            return tbleSpace.of(
                    rec(
                            uri(PATTERN), uri("db:#"),
                            uri(HOST), uri(staticDbConfig.getJdbcHost()),
                            uri(DRIVER), uri(staticDbConfig.getDriverClass()),
                            uri(TABLE), lst(),
                            uri(ROUTE), rec(uri("db:"), uri(""))
                    ).jvm(),
                    SPACE_VID
            );
        });
        this.dbConfig = dbConfig;
    }

    // =========================================================================
    //  AbstractDataPathTest — collection→Type contract for scheme-based URIs
    // =========================================================================

    /**
     * Override the inherited DataPath contract test with tbleSpace-specific
     * scheme-based URIs ({@code db:+}, {@code db:+/+}) instead of the
     * path-segment {@code $$/+} form used by grphSpace.  This avoids
     * conflicting with {@code make()}'s {@code $$} substitution used by
     * {@code testMonoReadWrite} and other inherited parameterized tests.
     */
    @Override
    @ParameterizedTest
    @CsvSource(value = {
            "*<$$/instset/+>       % collection",    // wildcard collection → every result is a Type
            "*<db:users/+>.take(1) % entry",         // specific collection + wildcard entry → first is instance
            "*<db:users/+>.take(2) % entry",         // second entry also an instance (not just first)
    }, delimiter = '%')
    public void testDataPathSegmentTypes(final String code, final String segmentType) {
        super.testDataPathSegmentTypes(code.replace("$$", SPACE_VID.toString()), segmentType);
    }

    @Override
    @Disabled("Rootless container aggregation only works in memSpace's trie — " +
            "database spaces store discrete rows/documents with no implicit parent container")
    public void testMonoRootlessReadWrites() {
        super.testMonoRootlessReadWrites();
    }

    // =========================================================================
    //  Lifecycle
    // =========================================================================

    @BeforeAll
    public static void setupInstSet() throws Exception {
        InstSet.importInstSet(TBLE_ISA_TID);
    }

    /**
     * Called by subclass {@code @BeforeAll} methods after setting {@code staticDbConfig}.
     */
    protected static void setupDatabase() throws Exception {
        if (staticDbConfig == null)
            throw new IllegalStateException(
                    "Database config not initialized. Ensure constructor is called.");

        staticDbConfig.setup();
        try (Connection conn = staticDbConfig.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(staticDbConfig.getUsersTableDDL());
            stmt.executeUpdate(String.format(
                    "INSERT INTO users VALUES (1, 'Alice', 30, 75000.0, %d, 'alice@example.com')",
                    staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO users VALUES (2, 'Bob', 25, 60000.0, %d, 'bob@example.com')",
                    staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO users VALUES (3, 'Charlie', 35, 85000.0, %d, 'charlie@example.com')",
                    staticDbConfig.getBooleanFalse()));

            stmt.executeUpdate(staticDbConfig.getProductsTableDDL());
            stmt.executeUpdate(String.format(
                    "INSERT INTO products VALUES (101, 'Laptop', 1299.99, %d, 15, 'Electronics')",
                    staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO products VALUES (102, 'Mouse', 29.99, %d, 50, 'Electronics')",
                    staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO products VALUES (103, 'Keyboard', 79.99, %d, 30, 'Electronics')",
                    staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO products VALUES (1, 'Monitor', 399.99, %d, 0, 'Electronics')",
                    staticDbConfig.getBooleanFalse()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO products VALUES (105, 'Desk Chair', 249.99, %d, 20, 'Furniture')",
                    staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO products VALUES (106, 'Desk', 499.99, %d, 10, 'Furniture')",
                    staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO products VALUES (107, 'Notebook', 4.99, %d, 100, 'Stationery')",
                    staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO products VALUES (108, 'Pen Set', 12.99, %d, 75, 'Stationery')",
                    staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO products VALUES (109, 'Webcam', 89.99, %d, 0, 'Electronics')",
                    staticDbConfig.getBooleanFalse()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO products VALUES (110, 'Headphones', 149.99, %d, 25, 'Electronics')",
                    staticDbConfig.getBooleanTrue()));

            stmt.executeUpdate(staticDbConfig.getRewriteTestTableDDL());
            for (int i = 1; i <= 10; i++) {
                final int active = (i % 2 == 1)
                        ? staticDbConfig.getBooleanTrue()
                        : staticDbConfig.getBooleanFalse();
                stmt.executeUpdate(String.format(
                        "INSERT INTO rewrite_test (id, value, name, active) VALUES (%d, %d, 'item%d', %d)",
                        i, i, i, active));
            }

            // testMonoUpdate schema — companies first (FK target), then people (FK source)
            stmt.executeUpdate(staticDbConfig.getCompaniesTableDDL());
            stmt.executeUpdate(String.format(
                    "INSERT INTO companies VALUES (101, 'Acme Corp', 'NYC', 50, %d)",
                    staticDbConfig.getBooleanFalse()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO companies VALUES (102, 'Globex Inc', 'LA', 200, %d)",
                    staticDbConfig.getBooleanTrue()));

            stmt.executeUpdate(staticDbConfig.getPeopleTableDDL());
            stmt.executeUpdate(String.format(
                    "INSERT INTO people VALUES (1, 'Alice', 30, 'Engineer', 75000.0, 101, %d)",
                    staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO people VALUES (2, 'Bob', 25, 'Designer', 60000.0, 101, %d)",
                    staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO people VALUES (3, 'Charlie', 35, 'Manager', 85000.0, 102, %d)",
                    staticDbConfig.getBooleanFalse()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO people VALUES (4, 'Diana', 28, 'Engineer', 70000.0, 102, %d)",
                    staticDbConfig.getBooleanTrue()));
        }
    }

    /**
     * Called by subclass {@code @AfterAll} methods.
     */
    protected static void cleanupDatabase() throws Exception {
        if (staticDbConfig != null)
            staticDbConfig.teardown();
    }

    // =========================================================================
    //  Per-test helpers
    // =========================================================================

    /**
     * Creates/inserts test data for parameterized tests that need fresh state.
     */
    protected void setupTestDatabase() throws Exception {
        try (final Connection conn = staticDbConfig.getConnection();
             final Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP TABLE IF EXISTS users");
            stmt.executeUpdate("DROP TABLE IF EXISTS products");

            stmt.executeUpdate(staticDbConfig.getUsersTableDDL());
            stmt.executeUpdate(String.format(
                    "INSERT INTO users VALUES (1, 'Alice', 30, 75000.50, %d, 'alice@example.com')",
                    staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO users VALUES (2, 'Bob', 25, 60000.00, %d, 'bob@example.com')",
                    staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO users VALUES (3, 'Charlie', 35, 85000.75, %d, 'charlie@example.com')",
                    staticDbConfig.getBooleanFalse()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO users VALUES (4, 'Diana', 28, 70000.25, %d, 'diana@example.com')",
                    staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO users VALUES (5, 'Eve', 42, 95000.00, %d, 'eve@example.com')",
                    staticDbConfig.getBooleanTrue()));

            stmt.executeUpdate(staticDbConfig.getProductsTableDDL());
            stmt.executeUpdate(String.format(
                    "INSERT INTO products VALUES (101, 'Laptop', 1299.99, %d, 15, 'Electronics')",
                    staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO products VALUES (102, 'Mouse', 29.99, %d, 50, 'Electronics')",
                    staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO products VALUES (103, 'Keyboard', 79.99, %d, 30, 'Electronics')",
                    staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO products VALUES (1, 'Monitor', 399.99, %d, 0, 'Electronics')",
                    staticDbConfig.getBooleanFalse()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO products VALUES (105, 'Desk Chair', 249.99, %d, 20, 'Furniture')",
                    staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO products VALUES (106, 'Desk', 499.99, %d, 10, 'Furniture')",
                    staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO products VALUES (107, 'Notebook', 4.99, %d, 100, 'Stationery')",
                    staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO products VALUES (108, 'Pen Set', 12.99, %d, 75, 'Stationery')",
                    staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO products VALUES (109, 'Webcam', 89.99, %d, 0, 'Electronics')",
                    staticDbConfig.getBooleanFalse()));
            stmt.executeUpdate(String.format(
                    "INSERT INTO products VALUES (110, 'Headphones', 149.99, %d, 25, 'Electronics')",
                    staticDbConfig.getBooleanTrue()));
        }
    }

    protected void cleanupTestDatabase() throws Exception {
        try (final Connection conn = staticDbConfig.getConnection();
             final Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP TABLE IF EXISTS users");
            stmt.executeUpdate("DROP TABLE IF EXISTS products");
        }
    }

    protected tbleSpace createTestSpace() {
        // Evict the lazily-constructed parent space (SPACE_VID, pattern db:#) so it
        // doesn't block the fresh space from registering.
        Router.global().removeSpace(SPACE_VID);
        return tbleSpace.of(
                rec(
                        uri(PATTERN), uri("db:#"),
                        uri(HOST), uri(staticDbConfig.getJdbcHost()),
                        uri(DRIVER), uri(staticDbConfig.getDriverClass()),
                        uri(ROUTE), rec(uri("db:"), uri("")),
                        uri(TABLE), lst()
                ).jvm(),
                f("/sys/space/tble/test/" + testSpaceCounter.getAndIncrement())
        );
    }

    @Override
    public fURI getTestDataUriPrefix() {
        return f("db:rewrite_test");
    }

    /**
     * Route inherited KV tests through the {@code kv/} collection so they
     * bypass table mapping entirely.  Rewrite tests use the parent prefix.
     */
    @Override
    protected fURI testUri(String suffix) {
        return f("db:kv/test/" + suffix);
    }

    @Override
    public String make(final String expression, final Method testMethod) {
        // For testMonoUpdate, $$ → db: so seed data writes to db:<collection>/<docId>
        // and update/read expressions resolve to the same two-segment document paths.
        if (testMethod != null && ("testMonoUpdate".equals(testMethod.getName()) ||
                "testUpdateWrite".equals(testMethod.getName()))) {
            if (!expression.contains("$$")) return expression;
            // a/b/c URI scheme: strip $$/, strip b-prefix from numeric entries,
            // keep a (table) and c (field) prefixes as part of the name.
            return expression
                    .replace("$$/a", "db:a")
                    .replaceAll("/b(\\d+)", "/$1");
        }
        if (testMethod != null && "testMonoDepth".equals(testMethod.getName())) {
            return expression.contains("$$") ? expression.replace("$$/", "db:kv/") : expression;
        }
        return super.make(expression, testMethod);
    }

    @Override
    public String getNativeInstructionPrefix() {
        return "sql_";
    }

    @Override
    public fURI getRewriteInstUri() {
        return f("/m/tble");
    }

    // =========================================================================
    //  Type-prefix parser for @CsvSource test data
    // =========================================================================

    protected static Obj parseObj(final String encoded) {
        return ObjmtronSerializer.singleNoClip().read(encoded);
    }

    // =========================================================================
    //  Rewrite tests — generated from CommonRewritesTestContract with db:rewrite_test prefix
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideAllRewriteTestCases")
    public void testRewrites(String description, String code, Obj expected) throws Exception {
        runRewriteTest(description, code, expected);
    }

    public static Stream<Arguments> provideAllRewriteTestCases() {
        // Static bridge — getTestDataUriPrefix() is instance-bound but returns the
        // same prefix for all tble backends.  Use a lightweight anonymous impl
        // rather than coupling to a specific subclass.
        return new CommonRewritesTestContract() {
            @Override
            public fURI getTestDataUriPrefix() {
                return f("db:rewrite_test");
            }
        }.generateAllRewriteTestCases();
    }

    // =========================================================================
    //  testReadIndividualFields
    // =========================================================================

    /**
     * Reads a single field from a row and compares it to the expected value.
     * <br>
     * CSV columns: {@code description, rowUri, fieldName, expectedValue}
     * <br>
     * {@code expectedValue} uses type-prefix encoding (see {@link #parseObj}).
     * <br>
     * Note: primary keys are encoded in the VID, not in the row body.
     * Use {@code table/id} path traversal to read PK values, not a full-row read.
     */
    @ParameterizedTest(name = "[{index}] Read {0}")
    @CsvSource(delimiter = '|', textBlock = """
                                            String field from users             | db:users/1       | name         | str::"Alice"
                                            String field from products          | db:products/101  | product_name | str::"Laptop"
                                            Email field                         | db:users/2       | email        | "bob@example.com"
                                            Category field                      | db:products/105  | category     | str::"Furniture"
                                            Category stationery                 | db:products/107  | category     | str::"Stationery"
                                            Integer age field                   | db:users/1       | age          | int::30
                                            Integer quantity field              | db:products/102  | quantity     | int::50
                                            Integer quantity zero               | db:products/101  | quantity     | int::15
                                            Integer age field user 2            | db:users/2       | age          | int::25
                                            Integer age field user 3            | db:users/3       | age          | int::35
                                            Real salary field                   | db:users/1       | salary       | real::75000.50
                                            Real price field                    | db:products/101  | price        | real::1299.99
                                            Small price value                   | db:products/102  | price        | real::29.99
                                            Real salary Diana                   | db:users/4       | salary       | real::70000.25
                                            Real price furniture                | db:products/105  | price        | real::249.99
                                            """)
    public void testReadIndividualFields(String description, String rowUri,
                                         String fieldName, String expectedEncoded) throws Exception {
        final Obj expectedValue = parseObj(expectedEncoded);
        setupTestDatabase();
        final tbleSpace testSpace = createTestSpace();
        try {
            final Obj row = Router.readFromSpace(f(rowUri));
            assertFalse(row.isNoObj(), "should not be a noobj");
            assertTrue(row.isRec(), "should return a rec");
            assertEquals(expectedValue, row.asRec().at(uri(fieldName)), description);
        } finally {
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }
        cleanupTestDatabase();
    }

    // =========================================================================
    //  testWriteIndividualFields
    // =========================================================================

    /**
     * Writes a value to a single field and reads it back to verify round-trip.
     * <br>
     * CSV columns: {@code table, rowId, field, newValue, expectedValue}
     */
    @ParameterizedTest(name = "[{index}] Write {2} to {0}/{1}")
    @CsvSource(delimiter = '|', quoteCharacter = '\'', textBlock = """     
                                                                   users    | 1   | name         | "Alice Updated"        | "Alice Updated"
                                                                   products | 101 | product_name | str::"Gaming Laptop"   | str::"Gaming Laptop"
                                                                   users    | 1   | age          | 31                     | int::31
                                                                   users    | 2   | age          | 26                     | int::26
                                                                   products | 102 | quantity     | 100                    | int::100
                                                                   products | 103 | quantity     | 0                      | int::0
                                                                   users    | 1   | salary       | 80000.00               | real::80000.00
                                                                   products | 101 | price        | real::999.00           | real::999.00
                                                                   users    | 3   | salary       | real::100000.50        | real::100000.50
                                                                   """)
    public void testWriteIndividualFields(String table, String rowId, String field,
                                          String newValueEncoded, String expectedEncoded) throws Exception {
        final Obj newValue = parseObj(newValueEncoded);
        final Obj expectedValue = parseObj(expectedEncoded);
        setupTestDatabase();
        final tbleSpace testSpace = createTestSpace();
        try {
            Router.writeToSpace(f("db:%s/%s/%s".formatted(table, rowId, field)), newValue);
            final Obj row = Router.readFromSpace(f("db:%s/%s".formatted(table, rowId)));
            assertFalse(row.isNoObj(), "should not be a noobj");
            assertTrue(row.isRec(), "should return a rec");
            assertEquals(expectedValue, row.asRec().at(uri(field)),
                    "field " + field + " should be updated");
        } finally {
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }
        cleanupTestDatabase();
    }

    // =========================================================================
    //  testReadEntireRow
    // =========================================================================

    @ParameterizedTest(name = "[{index}] Read row {0}")
    @CsvSource(delimiter = '|', textBlock = """
                                            db:users/1       | name         | Alice
                                            db:users/2       | name         | Bob
                                            db:users/3       | name         | Charlie
                                            db:users/4       | name         | Diana
                                            db:users/5       | name         | Eve
                                            db:products/101  | product_name | Laptop
                                            db:products/102  | product_name | Mouse
                                            db:products/103  | product_name | Keyboard
                                            db:products/105  | product_name | Desk Chair
                                            """)
    public void testReadEntireRow(String uri, String fieldName, String expectedFieldValue)
            throws Exception {
        setupTestDatabase();
        final tbleSpace testSpace = createTestSpace();
        try {
            final Obj row = Router.readFromSpace(f(uri));
            assertTrue(row.isRec(), "Should return a record");
            assertEquals(str(expectedFieldValue), row.asRec().at(uri(fieldName)),
                    "Field " + fieldName + " should match");
        } finally {
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }
        cleanupTestDatabase();
    }

    // =========================================================================
    //  testInsertNewRows
    // =========================================================================

    /**
     * Inserts a new row via a full-record write and reads it back.
     * <br>
     * CSV columns: {@code table, rowId, verifyField, expectedValue, col1, val1, ... colN, valN}
     * <br>
     * The trailing pairs define the record body.  All values use type-prefix encoding.
     */
    @ParameterizedTest(name = "[{index}] Insert new row into {0}")
    @CsvSource(delimiter = '|', quoteCharacter = '\'', textBlock = """
                                                                   users    | 100 | name | str::"Test User" | name | "Test User" | age | int::25 | salary | real::50000.00 | active | bool::true | email | "test@example.com"
                                                                   products | 200 | product_name | str::"New Product" | product_name | "New Product" | price | real::199.99 | in_stock | bool::true | quantity | int::10 | category | str::"Test Category"
                                                                   users    | 101 | age  | int::0  | name | str::"Zero Age" | age | int::0 | salary | real::0.0 | active | bool::false | email | str::"zero@example.com"
                                                                   users    | 102 | name | str::"Max Val" | name | str::"Max Val" | age | int::999 | salary | real::999999.99 | active | true | email | str::"max@example.com"
                                                                   products | 201 | price | real::0.0 | product_name | str::"Free Item" | price | real::0.0 | in_stock | bool::true | quantity | 0 | category | str::"Free"
                                                                   """)
    public void testInsertNewRows(String table, String rowId, String verifyField,
                                  String expectedEncoded,
                                  String col1, String val1,
                                  String col2, String val2,
                                  String col3, String val3,
                                  String col4, String val4,
                                  String col5, String val5) throws Exception {
        final Obj expectedValue = parseObj(expectedEncoded);
        final Obj rowData = rec(
                uri(col1), parseObj(val1),
                uri(col2), parseObj(val2),
                uri(col3), parseObj(val3),
                uri(col4), parseObj(val4),
                uri(col5), parseObj(val5)
        );
        setupTestDatabase();
        final tbleSpace testSpace = createTestSpace();
        try {
            Router.writeToSpace(f("db:%s/%s".formatted(table, rowId)), rowData);
            final Obj insertedRow = Router.readFromSpace(
                    f("db:%s/%s".formatted(table, rowId)));
            assertTrue(insertedRow.isRec(), "Should return a record");
            assertEquals(expectedValue, insertedRow.asRec().at(uri(verifyField)),
                    "Field " + verifyField + " should match");
        } finally {
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }
        cleanupTestDatabase();
    }

    // =========================================================================
    //  testTypeConversions
    // =========================================================================

    /**
     * Writes a value and reads it back, verifying type-preserving round-trip
     * behavior.
     * <br>
     * CSV columns: {@code description, table, rowId, field, writeValue, expectedReadValue}
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', textBlock = """
                                            boolean true converts and back   | users | 1 | active | bool::true     | bool::true
                                            boolean false converts and back  | users | 1 | active | bool::false    | bool::false
                                            real number with decimals        | users | 1 | salary | real::12345.00 | real::12345.00
                                            real number zero                 | users | 1 | salary | real::0.0      | real::0.0
                                            real negative                    | users | 1 | salary | real::-500.25  | real::-500.25
                                            real large                       | users | 1 | salary | real::9999999.99 | real::9999999.99
                                            integer zero                     | users | 1 | age    | 0                | int::0
                                            integer large value              | users | 1 | age    | int::999         | int::999
                                            integer negative                 | users | 1 | age    | int::-1          | -1
                                            integer max long                 | users | 1 | age    | 2147483647       | 2147483647
                                            empty string                     | users | 1 | name   | str::""          | ""
                                            string with spaces               | users | 1 | name   | "   Test"        | str::"   Test"
                                            string with special chars        | users | 1 | email  | str::"test+tag@example.com" | str::"test+tag@example.com"
                                            string unicode                   | users | 1 | name   | str::"José María"           | "José María"
                                            """)
    public void testTypeConversions(String description, String table, String rowId,
                                    String field, String writeEncoded,
                                    String expectedEncoded) throws Exception {
        final Obj writeValue = parseObj(writeEncoded);
        final Obj expectedReadValue = parseObj(expectedEncoded);
        setupTestDatabase();
        final tbleSpace testSpace = createTestSpace();
        try {
            Router.writeToSpace(
                    f("db:%s/%s/%s".formatted(table, rowId, field)), writeValue);
            final Obj row = Router.readFromSpace(
                    f("db:%s/%s".formatted(table, rowId)));
            assertTrue(row.isRec() || row.isStr(), "should return a rec or str");
            if (expectedReadValue.isReal())
                assertEquals(expectedReadValue.asReal().realValue(), row.asRec().at(uri(field)).asReal().realValue(), 0.01f);
            else
                assertEquals(expectedReadValue, row.asRec().at(uri(field)), description);
        } finally {
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }
        cleanupTestDatabase();
    }

    // =========================================================================
    //  testSpecialStringValues (override)
    // =========================================================================

    @Override
    @ParameterizedTest(name = "[{index}] Special string: {0}")
    @CsvSource(value = {
            "newline              | 'line1\\nline2'",
            "tab                  | 'col1\\tcol2'",
            "carriage return      | 'line1\\rline2'",
            "rtl text             | 'مرحبا'",
            "mixed scripts        | 'Hello世界مرحبا'"
    }, delimiter = '|', ignoreLeadingAndTrailingWhitespace = false)
    public void testSpecialStringValues(String description, String value) {
        final fURI uri = testUri("special_string/" + description.replaceAll("\\s+", "_"));
        String unescaped = value
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\r")
                .replace("\\0", "\0");
        if (unescaped.startsWith("'") && unescaped.endsWith("'"))
            unescaped = unescaped.substring(1, unescaped.length() - 1);
        this.space.write(uri, str(unescaped));
        assertEquals(str(unescaped), this.space.read(uri).selfVID(null), description);
    }

    // =========================================================================
    //  testComprehensiveTableOperations
    // =========================================================================

    @Test
    public void testComprehensiveTableOperations() throws Exception {
        LOG.info("Testing comprehensive table operations with multiple data types on {}",
                staticDbConfig.getDatabaseName());
        setupTestDatabase();
        final tbleSpace testSpace = createTestSpace();
        try {
            LOG.info("Discovered tables: %s", testSpace.existingTableSchema.getTableNames());

            // Read specific row
            final Obj user1 = Router.readFromSpace(f("db:users/1"));
            assertTrue(user1.isRec(), "Should return a record");
            assertEquals(str("Alice"), user1.asRec().at(uri(NAME)), "Name should be Alice");
            assertEquals(jnt(30), user1.asRec().at(uri("age")), "Age should be 30");

            // Update entire row
            Router.writeToSpace(f("db:users/1"), rec(
                    uri(NAME), str("Alice Smith"),
                    uri("age"), jnt(31),
                    uri("salary"), real(80000.00),
                    uri("active"), bool(true),
                    uri("email"), str("alice.smith@example.com")
            ));
            final Obj updatedUser1 = Router.readFromSpace(f("db:users/1"));
            assertEquals(str("Alice Smith"), updatedUser1.asRec().at(uri(NAME)));
            assertEquals(jnt(31), updatedUser1.asRec().at(uri("age")));

            // Update single fields
            Router.writeToSpace(f("db:users/2/age"), jnt(26));
            Router.writeToSpace(f("db:users/2/salary"), real(62000.00));
            final Obj updatedUser2 = Router.readFromSpace(f("db:users/2"));
            assertEquals(jnt(26), updatedUser2.asRec().at(uri("age")));
            assertEquals(real(62000.00), updatedUser2.asRec().at(uri("salary")));

            // Verify directly in DB via space.sql()
            final Obj verifyRows = testSpace.sql(
                    "SELECT name, age FROM users WHERE id = 1");
            final Rec verifyRow = verifyRows.stream().toList().get(0).asRec();
            assertEquals(str("Alice Smith"), verifyRow.at(uri("name")));
            assertEquals(jnt(31), verifyRow.at(uri("age")));

            LOG.info("All comprehensive tests passed for {}!",
                    staticDbConfig.getDatabaseName());
        } finally {
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }
        cleanupTestDatabase();
    }

    // =========================================================================
    //  auto_from FK pointer tests
    // =========================================================================

    /**
     * Verifies FK pointer round-trip: writes {@code auto_from} insts, reads back
     * via {@code rec.at()} (which eagerly resolves), and verifies the dereferenced
     * record is correct.
     */
    @Test
    public void testAutoFromWriteReadRoundTrip() throws Exception {
        final fURI spaceVid = f("/sys/space/tble/autofrom_test");
        final tbleSpace testSpace = tbleSpace.of(
                rec(
                        uri(PATTERN), uri("pfk:#"),
                        uri(HOST), uri(staticDbConfig.getJdbcHost()),
                        uri(DRIVER), uri(staticDbConfig.getDriverClass()),
                        uri(TABLE), lst(uri("person"), uri("award")),
                        uri(ROUTE), rec(uri("pfk:"), uri(""))
                ).jvm(),
                spaceVid
        );
        try {
            Router.writeToSpace(f("pfk:person/1"), rec(uri("name"), str("Alice")));
            Router.writeToSpace(f("pfk:person/2"), rec(uri("name"), str("Bob")));

            Router.writeToSpace(f("pfk:award/1"), rec(
                    uri("trophy"), str("gold"),
                    uri("recipient"), auto_from_(f("pfk:person/1")).tryToInst()));
            Router.writeToSpace(f("pfk:award/2"), rec(
                    uri("trophy"), str("silver"),
                    uri("recipient"), auto_from_(f("pfk:person/2")).tryToInst()));

            // rec.at() eagerly resolves auto_from → the person record
            final Obj award1 = Router.readFromSpace(f("pfk:award/1"));
            assertTrue(award1.isRec(), "award/1 should be a record");
            final Obj recipient = award1.asRec().at(uri("recipient"));
            assertTrue(recipient.isRec(), "at(recipient) should resolve to person record");
            assertEquals(str("Alice"), recipient.asRec().at(uri("name")));

            // Path traversal pfk:award/1/recipient → also resolves to person
            final Obj aliceRec = Router.readFromSpace(f("pfk:award/1/recipient"));
            assertTrue(aliceRec.isRec(), "dereferenced recipient should be a record");
            assertEquals(str("Alice"), aliceRec.asRec().at(uri("name")));

            // Second row → Bob
            final Obj award2 = Router.readFromSpace(f("pfk:award/2"));
            final Obj recipient2 = award2.asRec().at(uri("recipient"));
            assertTrue(recipient2.isRec());
            assertEquals(str("Bob"), recipient2.asRec().at(uri("name")));

            LOG.info("auto_from round-trip test passed on {}",
                    staticDbConfig.getDatabaseName());
        } finally {
            try {
                testSpace.sql("DROP TABLE IF EXISTS award; DROP TABLE IF EXISTS person");
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }
    }

    /**
     * Verifies the {@code _mtron_meta} table records auto_from column mappings
     * and that those mappings survive a space restart.
     */
    @Test
    public void testMtronMetaTableTracksAutoFromColumns() throws Exception {
        // Ensure _mtron_meta is recreated with current schema (old table may
        // lack base_vid/obj_tid columns from a prior run).
        try (final Connection c = staticDbConfig.getConnection();
             final Statement s = c.createStatement()) {
            s.executeUpdate("DROP TABLE IF EXISTS _mtron_meta");
        }
        final fURI spaceVid = f("/sys/space/tble/metacheck_test");
        final tbleSpace testSpace = tbleSpace.of(
                rec(
                        uri(PATTERN), uri("pmk:#"),
                        uri(HOST), uri(staticDbConfig.getJdbcHost()),
                        uri(DRIVER), uri(staticDbConfig.getDriverClass()),
                        uri(TABLE), lst(uri("category"), uri("item")),
                        uri(ROUTE), rec(uri("pmk:"), uri(""))
                ).jvm(),
                spaceVid
        );
        try {
            Router.writeToSpace(f("pmk:category/10"),
                    rec(uri("label"), str("Books")));
            Router.writeToSpace(f("pmk:item/1"), rec(
                    uri("title"), str("Dune"),
                    uri("category"), auto_from_(f("pmk:category/10")).tryToInst()));

            // _mtron_meta should have one FK row for item.category → category
            // (filter ref_table IS NOT NULL — non-FK columns also have rows now)
            final Obj metaRows = testSpace.sql(
                    "SELECT column_name, ref_table FROM _mtron_meta WHERE table_name = 'item' AND ref_table IS NOT NULL");
            final List<Obj> metaList = metaRows.stream().toList();
            assertEquals(1, metaList.size(),
                    "_mtron_meta should have exactly one FK row for item");
            assertEquals(str("category"), metaList.get(0).asRec().at(uri("column_name")));
            assertEquals(str("category"), metaList.get(0).asRec().at(uri("ref_table")));

            // Restart: close space, re-open → FK must survive
            testSpace.close();
            Router.global().removeSpace(testSpace.vid());

            final tbleSpace testSpace2 = tbleSpace.of(
                    rec(
                            uri(PATTERN), uri("pmk:#"),
                            uri(HOST), uri(staticDbConfig.getJdbcHost()),
                            uri(DRIVER), uri(staticDbConfig.getDriverClass()),
                            uri(TABLE), lst(uri("category"), uri("item")),
                            uri(ROUTE), rec(uri("pmk:"), uri(""))
                    ).jvm(),
                    spaceVid
            );
            try {
                // at() eagerly resolves auto_from → the category record
                final Obj item = Router.readFromSpace(f("pmk:item/1"));
                final Obj catPtr = item.asRec().at(uri("category"));
                assertTrue(catPtr.isRec(),
                        "after restart, category should resolve to the category record, got: "
                                + catPtr.tid());
                assertEquals(str("Books"), catPtr.asRec().at(uri("label")));
            } finally {
                Router.global().removeSpace(testSpace2.vid());
                testSpace2.close();
            }
        } finally {
            try {
                testSpace.sql("DROP TABLE IF EXISTS item; DROP TABLE IF EXISTS category");
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
            try {
                Router.global().removeSpace(testSpace.vid());
                testSpace.close();
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
        }
    }

    /**
     * Verifies that cross-space auto_from references (e.g. {@code !*g:V/1} in a
     * {@code play:#} space) are stored with their full scheme:segment in
     * {@code _mtron_meta.ref_table} and reconstructed to point at the original
     * cross-space URI, not the space's own pattern.
     */
    @Test
    public void testCrossSpaceAutoFromReference() throws Exception {
        // Ensure _mtron_meta is recreated with current schema
        try (final Connection c = staticDbConfig.getConnection();
             final Statement s = c.createStatement()) {
            s.executeUpdate("DROP TABLE IF EXISTS _mtron_meta");
        }
        final fURI spaceVid = f("/sys/space/tble/xspace_test");
        final tbleSpace testSpace = tbleSpace.of(
                rec(
                        uri(PATTERN), uri("play:#"),
                        uri(HOST), uri(staticDbConfig.getJdbcHost()),
                        uri(DRIVER), uri(staticDbConfig.getDriverClass()),
                        uri(TABLE), lst(uri("place"), uri("venue")),
                        uri(ROUTE), rec(uri("play:"), uri(""))
                ).jvm(),
                spaceVid
        );
        try {
            // --- cross-space: addr → g:V/1 ---
            Router.writeToSpace(f("play:place/1"), rec(
                    uri("name"), str("fun_area"),
                    uri("addr"), auto_from_(f("g:V/1")).tryToInst()));

            // Verify _mtron_meta stores scheme:segment for cross-space ref
            final Obj placeMetaRows = testSpace.sql(
                    "SELECT column_name, ref_table FROM _mtron_meta WHERE table_name = 'place' AND ref_table IS NOT NULL");
            final List<Obj> placeMetaList = placeMetaRows.stream().toList();
            assertEquals(1, placeMetaList.size(),
                    "_mtron_meta should have exactly one row for place");
            assertEquals(str("addr"), placeMetaList.get(0).asRec().at(uri("column_name")));
            assertEquals(str("g:V"), placeMetaList.get(0).asRec().at(uri("ref_table")),
                    "cross-space ref should store scheme:segment, not bare table name");

            // Read back: auto_from inst points to g:V/1 (not play:V/1)
            final Obj row = Router.readFromSpace(f("play:place/1"));
            final Obj addr = row.recValue().get(uri("addr"));
            assertTrue(addr.isAutoFrom(), "addr should be an auto_from inst");
            final fURI targetURI = addr.asInst().arg(0).uriValue();
            assertEquals(f("g:V/1"), targetURI,
                    "cross-space auto_from should point at original URI, not space-pattern URI");
            // --- internal FK: venue.parent → play:place/1 (separate table, single create) ---
            Router.writeToSpace(f("play:venue/1"), rec(
                    uri("name"), str("indoor_zone"),
                    uri("parent"), auto_from_(f("play:place/1")).tryToInst()));

            final Obj venueMetaRows = testSpace.sql(
                    "SELECT column_name, ref_table FROM _mtron_meta WHERE table_name = 'venue' AND ref_table IS NOT NULL");
            final List<Obj> venueMetaList = venueMetaRows.stream().toList();
            assertEquals(1, venueMetaList.size(),
                    "_mtron_meta should have exactly one row for venue");
            assertEquals(str("parent"), venueMetaList.get(0).asRec().at(uri("column_name")));
            assertEquals(str("place"), venueMetaList.get(0).asRec().at(uri("ref_table")),
                    "internal FK should store bare table name");

            // Read back: internal FK uses space pattern
            final Obj row2 = Router.readFromSpace(f("play:venue/1"));
            final Obj parent = row2.recValue().get(uri("parent"));
            assertTrue(parent.isAutoFrom());
            final fURI parentURI = parent.asInst().arg(0).uriValue();
            assertEquals(f("play:place/1"), parentURI,
                    "internal FK should use space pattern");

            LOG.info("cross-space auto_from test passed on {}",
                    staticDbConfig.getDatabaseName());
        } finally {
            try {
                testSpace.sql("DROP TABLE IF EXISTS venue; DROP TABLE IF EXISTS place");
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }
    }

    /**
     * Verifies that a cross-space auto_from reference resolves through the router
     * to an actual record in another space. Sets up a memSpace as the target and
     * a tbleSpace as the source to test end-to-end cross-space FK resolution.
     */
    @Test
    public void testCrossSpaceAutoFromResolution() throws Exception {
        // Ensure _mtron_meta is recreated with current schema
        try (final Connection c = staticDbConfig.getConnection();
             final Statement s = c.createStatement()) {
            s.executeUpdate("DROP TABLE IF EXISTS _mtron_meta");
        }
        final fURI memSpaceVid = f("/sys/space/mem/xspace_target");
        final fURI tbleSpaceVid = f("/sys/space/tble/xspace_source");

        // Target space: memSpace with pattern grph:#
        final memSpace targetSpace = memSpace.of(f("grph:#"), memSpaceVid);
        Router.global().addSpace(targetSpace);

        final tbleSpace sourceSpace = tbleSpace.of(
                rec(
                        uri(PATTERN), uri("play:#"),
                        uri(HOST), uri(staticDbConfig.getJdbcHost()),
                        uri(DRIVER), uri(staticDbConfig.getDriverClass()),
                        uri(TABLE), lst(uri("arena")),
                        uri(ROUTE), rec(uri("play:"), uri(""))
                ).jvm(),
                tbleSpaceVid
        );
        try {
            // Write the target record into memSpace
            Router.writeToSpace(f("grph:vertices/42"),
                    rec(uri("label"), str("downtown"),
                            uri("capacity"), jnt(5000)));

            // Write a tbleSpace record with cross-space auto_from pointing at grph:vertices/42
            Router.writeToSpace(f("play:arena/1"), rec(
                    uri("name"), str("main_stage"),
                    uri("location"), auto_from_(f("grph:vertices/42")).tryToInst()));

            // Verify storage: _mtron_meta records scheme:segment for FK
            final Obj arenaMetaRows = sourceSpace.sql(
                    "SELECT ref_table FROM _mtron_meta WHERE table_name = 'arena' AND column_name = 'location' AND ref_table IS NOT NULL");
            final List<Obj> arenaMetaList = arenaMetaRows.stream().toList();
            assertEquals(1, arenaMetaList.size(),
                    "_mtron_meta should have exactly one row for arena.location");
            assertEquals(str("grph:vertices"), arenaMetaList.get(0).asRec().at(uri("ref_table")),
                    "multi-segment cross-space ref stores scheme:firstSegment");

            // Verify the instruction is properly reconstructed
            final Obj row = Router.readFromSpace(f("play:arena/1"));
            final Obj locInst = row.recValue().get(uri("location"));
            assertTrue(locInst.isAutoFrom());
            assertEquals(f("grph:vertices/42"), locInst.asInst().arg(0).uriValue());

            // Verify resolution through the router: rec.at() eagerly resolves
            // the auto_from, fetching the memSpace record
            final Obj resolved = row.asRec().at(uri("location"));
            assertTrue(resolved.isRec(),
                    "cross-space auto_from should resolve to a record, got: " + resolved.tid());
            assertEquals(str("downtown"), resolved.asRec().at(uri("label")),
                    "resolved record should be the memSpace target");
            assertEquals(jnt(5000), resolved.asRec().at(uri("capacity")));

            LOG.info("cross-space auto_from resolution test passed on {}",
                    staticDbConfig.getDatabaseName());
        } finally {
            try {
                sourceSpace.sql("DROP TABLE IF EXISTS arena");
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
            Router.global().removeSpace(sourceSpace.vid());
            sourceSpace.close();
            Router.global().removeSpace(targetSpace.vid());
            targetSpace.close();
        }
    }

    /**
     * Verifies intra-space cross-table FK resolution: a record in one table
     * referencing a record in another table within the same space. This is the
     * standard internal FK use case. Also tests self-referencing FKs.
     */
    @Test
    public void testIntraSpaceCrossTableAutoFromResolution() throws Exception {
        // Ensure _mtron_meta is recreated with current schema
        try (final Connection c = staticDbConfig.getConnection();
             final Statement s = c.createStatement()) {
            s.executeUpdate("DROP TABLE IF EXISTS _mtron_meta");
        }
        final fURI spaceVid = f("/sys/space/tble/intraspace_test");
        final tbleSpace testSpace = tbleSpace.of(
                rec(
                        uri(PATTERN), uri("net:#"),
                        uri(HOST), uri(staticDbConfig.getJdbcHost()),
                        uri(DRIVER), uri(staticDbConfig.getDriverClass()),
                        uri(TABLE), lst(uri("org"), uri("employee")),
                        uri(ROUTE), rec(uri("net:"), uri(""))
                ).jvm(),
                spaceVid
        );
        try {
            // --- cross-table FK: employee.org_id → org ---
            Router.writeToSpace(f("net:org/1"),
                    rec(uri("label"), str("PhaseShift Studio")));
            // Write the first employee with ALL FK columns in one shot (cross-table
            // org_id + self-referencing manager_id) so the table is created with
            // every FK column. self-ref points at its own row — the raw INTEGER
            // value stores fine before the row exists.
            Router.writeToSpace(f("net:employee/1"), rec(
                    uri("name"), str("Marko"),
                    uri("org_id"), auto_from_(f("net:org/1")).tryToInst(),
                    uri("manager_id"), auto_from_(f("net:employee/1")).tryToInst()));

            // _mtron_meta has FK rows for both FK columns (filter: only FK rows)
            final Obj empMetaRows = testSpace.sql(
                    "SELECT column_name, ref_table FROM _mtron_meta WHERE table_name = 'employee' AND ref_table IS NOT NULL ORDER BY column_name");
            final List<Obj> empMetaList = empMetaRows.stream().toList();
            assertEquals(2, empMetaList.size(),
                    "_mtron_meta should have two rows for employee");
            // ORDER BY column_name → manager_id comes first
            assertEquals(str("manager_id"), empMetaList.get(0).asRec().at(uri("column_name")));
            assertEquals(str("employee"), empMetaList.get(0).asRec().at(uri("ref_table")),
                    "self-referencing FK stores own table name");
            assertEquals(str("org_id"), empMetaList.get(1).asRec().at(uri("column_name")));
            assertEquals(str("org"), empMetaList.get(1).asRec().at(uri("ref_table")),
                    "cross-table internal FK stores bare target table name");

            // Read back: org_id instruction points within same space
            final Obj emp = Router.readFromSpace(f("net:employee/1"));
            final Obj orgInst = emp.recValue().get(uri("org_id"));
            assertTrue(orgInst.isAutoFrom());
            assertEquals(f("net:org/1"), orgInst.asInst().arg(0).uriValue());

            // Resolution via rec.at() fetches the target org record
            final Obj resolved = emp.asRec().at(uri("org_id"));
            assertTrue(resolved.isRec());
            assertEquals(str("PhaseShift Studio"), resolved.asRec().at(uri("label")));

            // Self-referencing instruction
            final Obj mgrInst = emp.recValue().get(uri("manager_id"));
            assertTrue(mgrInst.isAutoFrom());
            assertEquals(f("net:employee/1"), mgrInst.asInst().arg(0).uriValue());

            // Self-referencing resolution: rec.at() resolves to the own row
            final Obj mgr = emp.asRec().at(uri("manager_id"));
            assertTrue(mgr.isRec());
            assertEquals(str("Marko"), mgr.asRec().at(uri("name")));

            LOG.info("intra-space auto_from resolution test passed on {}",
                    staticDbConfig.getDatabaseName());
        } finally {
            try {
                testSpace.sql("DROP TABLE IF EXISTS employee; DROP TABLE IF EXISTS org");
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }
    }

    /**
     * Verifies that writing {@code none} to a NOT NULL column throws a clear
     * {@link studio.phaseshift.metatron.util.MTronException} rather than
     * silently ignoring the constraint violation.
     */
    @Test
    public void testNoneWriteToNotNullColumnThrowsException() throws Exception {
        final String tableName = "notnull_test";
        final fURI spaceVid = f("/sys/space/tble/nn_test");

        // Create table with explicit NOT NULL constraint
        try (final Connection conn = staticDbConfig.getConnection();
             final Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP TABLE IF EXISTS " + tableName);
            stmt.executeUpdate("CREATE TABLE " + tableName +
                    " (id INTEGER PRIMARY KEY, required_field TEXT NOT NULL)");
            stmt.executeUpdate("INSERT INTO " + tableName + " VALUES (1, 'hello')");
        }

        final tbleSpace testSpace = tbleSpace.of(
                rec(
                        uri(PATTERN), uri("nn:#"),
                        uri(HOST), uri(staticDbConfig.getJdbcHost()),
                        uri(DRIVER), uri(staticDbConfig.getDriverClass()),
                        uri(TABLE), lst(uri(tableName)),
                        uri(ROUTE), rec(uri("nn:"), uri(""))
                ).jvm(),
                spaceVid
        );

        try {
            final MTronException ex = assertThrows(MTronException.class, () ->
                    Router.writeToSpace(f("nn:" + tableName + "/1"),
                            rec(uri("required_field"), Obj.none()))
            );
            assertTrue(ex.getMessage().contains("NOT NULL"),
                    "exception should mention NOT NULL constraint, got: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("required_field"),
                    "exception should name the column, got: " + ex.getMessage());
            assertTrue(ex.getMessage().contains(tableName),
                    "exception should name the table, got: " + ex.getMessage());
        } finally {
            try {
                testSpace.sql("DROP TABLE IF EXISTS " + tableName);
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }
    }

    /**
     * Verifies that when a rec with new fields is written to a table that was
     * created by a prior first-write (with fewer columns), the missing columns
     * are added on the fly via ALTER TABLE rather than silently dropping the data.
     */
    @Test
    public void testAlterTableOnTheFly() throws Exception {
        final String tableName = "onthefly_test";

        final tbleSpace space = (tbleSpace) getSpace();
        space.sql("DROP TABLE IF EXISTS " + tableName);
        try (final Connection conn = staticDbConfig.getConnection()) {
            // -- Write 1: creates the table with only name + age columns ------
            final Obj rec1 = rec(uri("name"), str("Alice"), uri("age"), jnt(30));
            space.write(f("db:" + tableName + "/1"), rec1);
            // -- Verify: name, age columns exist in the DB --------------------
            final List<String> columnNames = new java.util.ArrayList<>();
            try (final java.sql.ResultSet cols = conn.getMetaData().getColumns(
                    null, null, tableName, null)) {
                while (cols.next())
                    columnNames.add(cols.getString("COLUMN_NAME").toLowerCase());
            }
            assertTrue(columnNames.contains("name"));
            assertTrue(columnNames.contains("age"));
            assertFalse(columnNames.contains("email"));
            // -- Write 2: same table, but now with an email field -------------
            final Obj rec2 = rec(uri("name"), str("Bob"), uri("age"), jnt(25),
                    uri("email"), str("bob@example.com"));
            space.write(f("db:" + tableName + "/2"), rec2);

            // -- Verify: email column now exists in the DB --------------------
            columnNames.clear();
            try (final java.sql.ResultSet cols = conn.getMetaData().getColumns(
                    null, null, tableName, null)) {
                while (cols.next())
                    columnNames.add(cols.getString("COLUMN_NAME").toLowerCase());
            }
            assertTrue(columnNames.contains("email"),
                    "email column should have been added on the fly, but found: " + columnNames);
            assertTrue(columnNames.contains("name"));
            assertTrue(columnNames.contains("age"));

            // -- Verify: both rows are readable with correct data -------------
            // Read by auto-generated row IDs (1 and 2) to avoid IdObj wrapping
            // that wildcard reads (+/) produce.

            // Row 1: Alice, 30, no email (NULL since column was added later)
            final Obj row1 = space.read(f("db:" + tableName + "/1"));
            assertTrue(row1.isRec(), "row 1 should be a Rec, got: " + row1.getClass());
            assertEquals(str("Alice"), row1.asRec().at(uri("name")));
            assertEquals(jnt(30), row1.asRec().at(uri("age")));
            // email should be absent or NULL — not the "none" sentinel
            final Obj email1 = row1.asRec().at(uri("email"));
            assertTrue(email1.isNoObj() || email1.isNone(),
                    "Alice's email should be absent/NULL, got: " + email1);

            // Row 2: Bob, 25, bob@example.com
            final Obj row2 = space.read(f("db:" + tableName + "/2"));
            assertTrue(row2.isRec(), "row 2 should be a Rec, got: " + row2.getClass());
            assertEquals(str("Bob"), row2.asRec().at(uri("name")));
            assertEquals(jnt(25), row2.asRec().at(uri("age")));
            assertEquals(str("bob@example.com"), row2.asRec().at(uri("email")));

            LOG.info("on-the-fly ALTER TABLE test passed on {}", staticDbConfig.getDatabaseName());
        } finally {
            try {
                space.sql("DROP TABLE IF EXISTS " + tableName);
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
        }
    }

    /**
     * Verifies that user-defined types are faithfully preserved in the instset
     * schema and that type constraints are enforced on read-back.
     *
     * <p>Scenario matrix (each row mutates state cumulatively):
     * <ol>
     *   <li>Create table by writing {@code nat::29} → schema shows {@code nat::T}</li>
     *   <li>Read back row → value is typed {@code nat::29}</li>
     *   <li>Write {@code -50} → fails (not a valid nat)</li>
     *   <li>Raw SQL insert {@code -25} → succeeds (SQL bypasses types)</li>
     *   <li>Read raw-SQL row → fails (type constraint violated)</li>
     * </ol>
     */
    @Test
    public void testUserDefinedTypeInSchema() throws Exception {
        final String tableName = "person";
        final tbleSpace space = (tbleSpace) getSpace();
        space.at("table", lst(uri(tableName)), MUTABLE);

        // -- Register nat type (positive integer) — create() writes to Router
        final fURI natVID = f("nat");
        Type.Builder.build()
                .tid(INT_TID)
                .vid(natVID)
                .isaPredicate(is_(gt_(jnt(0))).tryToInst())
                .create();

        space.sql("DROP TABLE IF EXISTS " + tableName);
        try (final Connection conn = staticDbConfig.getConnection();
             final Statement stmt = conn.createStatement()) {

            // === Step 1: write nat::29, verify schema + read-back ==============
            final Obj nat29 = jnt(29, natVID, null);
            space.write(f("db:" + tableName + "/1"),
                    rec(uri("name"), str("marko"), uri("age"), nat29));

            // Verify instset schema shows nat::T (not int::T)
            final Type personType = space.schemaInstset.types().stream()
                    .filter(t -> t.vid().name().equalsIgnoreCase(tableName))
                    .findFirst().orElse(null);
            assertNotNull(personType, "person type should exist in schema instset");
            final Obj fields = personType.isPredicateObj();
            assertNotNull(fields, "person type should have an isa predicate");
            final Obj ageField = fields.asRec().at(uri("age"));
            assertFalse(ageField.isNoObj(), "age field should exist in schema");
            assertTrue(ageField.isType(), "age should be a Type, got: " + ageField);
            assertEquals(natVID, ageField.asType().vid(),
                    "age should be nat::T, got: " + ageField);

            // Read back: raw read returns int (TID not set in SQL deserialization)
            final Obj row1 = space.read(f("db:" + tableName + "/1"));
            assertTrue(row1.isRec());
            final Obj age1 = row1.asRec().at(uri("age"));
            assertTrue(age1.isInt(), "age should be an integer, got: " + age1);
            assertEquals(29, age1.asInt().jvm().intValue(), "age value should be 29");
            LOG.info("step 1 passed: schema shows nat::T, row data intact");

            // === Step 2: write -50 → stored as plain int in DB =================
            // The DB stores -50 as a plain INTEGER.  Type enforcement happens
            // at the mtron eval level (*db:person/2), not at SQL write time.
            final Obj invalidAge = jnt(-50);
            space.write(f("db:" + tableName + "/2"),
                    rec(uri("name"), str("grant"), uri("age"), invalidAge));

            // Read back: -50 comes back as a plain int (no type check on read)
            final Obj row2 = space.read(f("db:" + tableName + "/2"));
            assertTrue(row2.isRec());
            final Obj age2 = row2.asRec().at(uri("age"));
            assertTrue(age2.isInt(), "age should be stored as plain int");
            assertEquals(-50, age2.asInt().jvm().intValue());

            LOG.info("step 2 passed: -50 stored as plain int, mtron eval enforces nat constraint on read");

            // === Step 3: raw SQL bypass → write + read succeed (no typeRow) ====
            // Raw SQL INSERT skips typeRow entirely, so -25 is stored as a plain
            // INTEGER.  Read-back returns a plain int — no constraint violation
            // because type checking only happens on write via typeRow.
            stmt.executeUpdate("INSERT INTO " + tableName + " (id, name, age) VALUES (3, 'alex', -25)");

            // Verify via space.sql(): -25 is in the DB as a plain integer
            final Obj verifyRows = space.sql(
                    "SELECT age FROM " + tableName + " WHERE id = 3");
            final Rec verifyRow = verifyRows.stream().toList().get(0).asRec();
            assertEquals(jnt(-25), verifyRow.at(uri("age")));

            // mtron read-back succeeds (plain int, no type check on read)
            final Obj row3 = space.read(f("db:" + tableName + "/3"));
            assertTrue(row3.isRec());
            final Obj age3 = row3.asRec().at(uri("age"));
            assertTrue(age3.isInt(), "raw-SQL row comes back as plain int");
            assertEquals(-25, age3.asInt().jvm().intValue());

            LOG.info("step 3 passed: raw SQL bypass succeeds at both write and read");
        } finally {
            try {
                space.sql("DROP TABLE IF EXISTS " + tableName);
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
        }
    }

    /**
     * Edge cases for type preservation in the instset schema.
     *
     * <ol>
     *   <li>URI values → {@code uri::T} in schema (not {@code str::T})</li>
     *   <li>Already-typed rec → re-typing is a no-op</li>
     *   <li>ALTER TABLE adds a typed column on the fly</li>
     * </ol>
     */
    @Test
    public void testTypePreservationEdgeCases() throws Exception {
        final String tableName = "edge_test";
        final tbleSpace space = (tbleSpace) getSpace();
        space.at("table", lst(uri(tableName)), MUTABLE);

        // -- Register nat type -----------------------------------------------
        final fURI natVID = f("nat");
        Type.Builder.build()
                .tid(INT_TID)
                .vid(natVID)
                .isaPredicate(is_(gt_(jnt(0)).tryToInst()))
                .create();

        space.sql("DROP TABLE IF EXISTS " + tableName);
        try (final Connection conn = staticDbConfig.getConnection()) {

            // === Edge case 1: URI value → uri::T in schema ==================
            space.write(f("db:" + tableName + "/1"),
                    rec(uri("name"), str("alice"), uri("homepage"), uri("http://example.com")));

            // Verify homepage is uri::T (not str::T) in the instset
            final Type type1 = space.schemaInstset.types().stream()
                    .filter(t -> t.vid().name().equalsIgnoreCase(tableName))
                    .findFirst().orElse(null);
            assertNotNull(type1, "table type should exist");
            final Obj hpField = type1.isPredicateObj().asRec().at(uri("homepage"));
            assertTrue(hpField.isType());
            assertEquals("uri", hpField.asType().vid().name(),
                    "homepage should be uri::T, got: " + hpField);

            LOG.info("edge 1 passed: URI column → uri::T in schema");

            // === Edge case 2: already-typed rec → re-type is no-op ==========
            // typeRow calls rec.tid(tableTypeVid).  If the rec is already
            // typed to the same type, tid() returns 'this' — zero cost.
            final Type personType = space.schemaInstset.types().stream()
                    .filter(t -> t.vid().name().equalsIgnoreCase(tableName))
                    .findFirst().orElseThrow();
            final Rec alreadyTyped = rec(
                    uri("name"), str("bob"),
                    uri("homepage"), uri("http://bob.com"))
                    .tid(personType.vid()).asRec();        // type it
            // Writing an already-typed rec: typeRow is a no-op
            space.write(f("db:" + tableName + "/2"), alreadyTyped);
            final Obj row2 = space.read(f("db:" + tableName + "/2"));
            assertEquals("bob", row2.asRec().at(uri("name")).strValue());

            LOG.info("edge 2 passed: already-typed rec → tid() returns this");

            // === Edge case 3: ALTER TABLE adds typed column on the fly ======
            // Second record has a new field 'email' with URI type
            space.write(f("db:" + tableName + "/3"),
                    rec(uri("name"), str("carol"),
                            uri("homepage"), uri("http://carol.com"),
                            uri("email"), uri("mailto:carol@example.com")));

            // Verify email column exists and is uri::T
            final List<String> cols = new java.util.ArrayList<>();
            try (final ResultSet rs = conn.getMetaData().getColumns(
                    null, null, tableName, null)) {
                while (rs.next()) cols.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
            assertTrue(cols.contains("email"), "email column should have been added on the fly");

            final Type type3 = space.schemaInstset.types().stream()
                    .filter(t -> t.vid().name().equalsIgnoreCase(tableName))
                    .findFirst().orElseThrow();
            final Obj emailField = type3.isPredicateObj().asRec().at(uri("email"));
            assertTrue(emailField.isType());
            assertEquals("uri", emailField.asType().vid().name(),
                    "email should be uri::T after alter, got: " + emailField);

            // Read back row 3
            final Obj row3 = space.read(f("db:" + tableName + "/3"));
            assertEquals("carol", row3.asRec().at(uri("name")).strValue());

            LOG.info("edge 3 passed: ALTER TABLE adds uri column, schema updated");
        } finally {
            try {
                space.sql("DROP TABLE IF EXISTS " + tableName);
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
        }
    }

    /**
     * Verifies that writing a Type to a collection path registers it in the
     * schema instset, making it discoverable via {@code *schema:+} and
     * enabling typed-table writes.
     */
    @Test
    public void testCollectionPathTypeDeclaration() throws Exception {
        final String tableName = "decl_test";
        final tbleSpace space = (tbleSpace) getSpace();

        space.sql("DROP TABLE IF EXISTS " + tableName);

        // -- Write a locked type (no wildcard) to the collection path --
        final Type lockedType = Type.Builder.build()
                .tid(INT_TID)  // rrow type
                .vid(f(tableName))
                .isaPredicate(rec(
                        uri("name"), str("str::T"),  // placeholder — real type would use T()
                        uri("age"), str("int::T")
                ))
                .create();
        space.write(f("db:" + tableName), lockedType);

        // Verify it appears in the schema instset
        final Type registered = space.schemaInstset.types().stream()
                .filter(t -> t.vid().name().equalsIgnoreCase(tableName))
                .findFirst().orElse(null);
        assertNotNull(registered, "locked type should be in schema instset");
        assertNotNull(registered.isPredicateObj(), "should have isa predicate");

        // -- Write an open type (with wildcard) to another collection --
        final String openName = "open_test";
        final Type openType = Type.Builder.build()
                .tid(INT_TID)
                .vid(f(openName))
                .isaPredicate(rec(
                        uri("title"), str("str::T"),
                        uri("uri{?}::T"), str("#")  // wildcard entry
                ))
                .create();
        space.write(f("db:" + openName), openType);

        final Type registeredOpen = space.schemaInstset.types().stream()
                .filter(t -> t.vid().name().equalsIgnoreCase(openName))
                .findFirst().orElse(null);
        assertNotNull(registeredOpen, "open type should be in schema instset");

        // -- Verify isTableDeclared plumbing works ---------------
        assertTrue(space.isTableDeclared(tableName),
                "isTableDeclared should see the locked type");
        assertTrue(space.isTableDeclared(openName),
                "isTableDeclared should see the open type");
        assertFalse(space.isTableDeclared("nonexistent"),
                "isTableDeclared should return false for unknown collections");

        // Clean up
        space.sql("DROP TABLE IF EXISTS " + openName + "; DROP TABLE IF EXISTS " + tableName);

        LOG.info("collection-path type declaration test passed on {}",
                staticDbConfig.getDatabaseName());
    }

    /**
     * Comprehensive schema behavior matrix — covers all permutations of
     * auto-inferred, locked, and open schemas.
     *
     * <table>
     * <tr><th>#</th><th>Mode</th><th>Action</th><th>Expected</th></tr>
     * <tr><td>A1</td><td>Auto</td><td>Write {name, age}</td><td>Table created, wildcard added</td></tr>
     * <tr><td>A2</td><td>Auto</td><td>Write {name, age, skill}</td><td>skill column added via ALTER</td></tr>
     * <tr><td>A3</td><td>Auto</td><td>Write {name, age=>string}</td><td>Fails — type mismatch</td></tr>
     * <tr><td>A4</td><td>Auto</td><td>Write {name}</td><td>Succeeds — columns nullable</td></tr>
     * <tr><td>L1</td><td>Locked</td><td>Write {name, age=>29}</td><td>Table created from declared columns</td></tr>
     * <tr><td>L2</td><td>Locked</td><td>Write {name, age, skill}</td><td>Fails — locked, no wildcard</td></tr>
     * <tr><td>L3</td><td>Locked</td><td>Write {name, age=>string}</td><td>Fails — type mismatch</td></tr>
     * <tr><td>O1</td><td>Open</td><td>Write {name, age}</td><td>Table created from declared columns</td></tr>
     * <tr><td>O2</td><td>Open</td><td>Write {name, age, skill}</td><td>skill added via ALTER</td></tr>
     * <tr><td>O3</td><td>Open</td><td>Write {name, age=>string}</td><td>Fails — type mismatch</td></tr>
     * </table>
     */
    @Test
    public void testSchemaBehaviorMatrix() throws Exception {
        final tbleSpace space = (tbleSpace) getSpace();

        // ===== A: Auto-inferred (no declared type) ============================
        final String autoTable = "person_auto";
        space.sql("DROP TABLE IF EXISTS " + autoTable);

        // A1: first write defines the schema; wildcard auto-added
        space.write(f("db:" + autoTable + "/1"),
                rec(uri("name"), str("alice"), uri("age"), jnt(30)));
        // Verify schema exists with wildcard
        final Type autoType = space.schemaInstset().types().stream()
                .filter(t -> t.vid().name().equalsIgnoreCase(autoTable))
                .findFirst().orElse(null);
        assertNotNull(autoType, "A1: auto type should exist");
        assertNotNull(autoType.isPredicateObj(), "A1: should have predicate");
        final Obj autoPred = autoType.isPredicateObj();
        assertTrue(autoPred.isRec());
        // Has declared columns
        assertFalse(autoPred.asRec().at(uri("name")).isNoObj(), "A1: name should be declared");
        assertFalse(autoPred.asRec().at(uri("age")).isNoObj(), "A1: age should be declared");
        // Has wildcard
        final boolean autoHasWC = autoPred.asRec().recValue().keySet().stream()
                .anyMatch(k -> k.isType() && k.asType().vid() != null
                        && k.asType().vid().name().equals("uri")
                        && k.toString().contains("{?}"));
        assertTrue(autoHasWC, "A1: auto type should have uri{?} wildcard");

        // A2: write with extra column — ALTER TABLE adds it
        space.write(f("db:" + autoTable + "/2"),
                rec(uri("name"), str("bob"), uri("age"), jnt(25),
                        uri("skill"), str("coding")));
        final Type autoType2 = space.schemaInstset().types().stream()
                .filter(t -> t.vid().name().equalsIgnoreCase(autoTable))
                .findFirst().orElseThrow();
        final Obj pred2 = autoType2.isPredicateObj();
        assertFalse(pred2.asRec().at(uri("skill")).isNoObj(),
                "A2: skill should be added to schema");

        // A2b: data read-back
        final Obj row2 = space.read(f("db:" + autoTable + "/2"));
        assertEquals("bob", row2.asRec().at(uri("name")).strValue());
        assertEquals(25L, row2.asRec().at(uri("age")).asInt().jvm().longValue());

        // A3: write wrong type — validateColumnWrite rejects
        // non-numeric string in an INTEGER column at the DB level
        assertThrows(Exception.class, () ->
                space.write(f("db:" + autoTable + "/3"),
                        rec(uri("name"), str("charlie"), uri("age"), str("old")))
        );

        // A4: write without optional column — succeeds, age is NULL
        space.write(f("db:" + autoTable + "/4"),
                rec(uri("name"), str("diana")));
        final Obj row4 = space.read(f("db:" + autoTable + "/4"));
        assertEquals("diana", row4.asRec().at(uri("name")).strValue());

        LOG.info("A: auto-inferred matrix passed");
        try {
            space.sql("DROP TABLE IF EXISTS " + autoTable);
        } catch (final Exception ignored) {
            LOG.warn("[ignored] %s", ignored);
        }

        // ===== D: Declared (user-declared type at collection path) =============
        final String declTable = "person_declared";
        space.sql("DROP TABLE IF EXISTS " + declTable);

        // Declare type
        final Type declType = Type.Builder.build()
                .tid(INT_TID).vid(f(declTable))
                .isaPredicate(rec(uri("name"), STR_TYPE, uri("age"), INT_TYPE))
                .create();
        space.write(f("db:" + declTable), declType);

        // D1: first write matches declared type — succeeds
        space.write(f("db:" + declTable + "/1"),
                rec(uri("name"), str("alice"), uri("age"), jnt(30)));
        final Obj rowD1 = space.read(f("db:" + declTable + "/1"));
        assertEquals("alice", rowD1.asRec().at(uri("name")).strValue());

        // D2: extra column — metatron structural typing accepts it.
        // addColumnOnTheFly adds it, schema refreshed.
        space.write(f("db:" + declTable + "/2"),
                rec(uri("name"), str("bob"), uri("age"), jnt(25),
                        uri("skill"), str("coding")));
        final Obj rowD2 = space.read(f("db:" + declTable + "/2"));
        assertEquals("bob", rowD2.asRec().at(uri("name")).strValue());
        // Schema should include the new column
        final Type d2type = space.schemaInstset().types().stream()
                .filter(t -> t.vid().name().equalsIgnoreCase(declTable))
                .findFirst().orElseThrow();
        assertFalse(d2type.isPredicateObj().asRec().at(uri("skill")).isNoObj(),
                "D2: schema should be refreshed with skill column");

        // D3: wrong type for declared column — typeAndValidate rejects
        assertThrows(Exception.class, () ->
                space.write(f("db:" + declTable + "/3"),
                        rec(uri("name"), str("charlie"), uri("age"), str("old")))
        );

        // D4: write without optional column — succeeds, age is NULL
        space.write(f("db:" + declTable + "/4"),
                rec(uri("name"), str("diana")));
        final Obj rowD4 = space.read(f("db:" + declTable + "/4"));
        assertEquals("diana", rowD4.asRec().at(uri("name")).strValue());

        LOG.info("D: declared matrix passed");
        try {
            space.sql("DROP TABLE IF EXISTS " + declTable);
        } catch (final Exception ignored) {
            LOG.warn("[ignored] %s", ignored);
        }

        LOG.info("schema behavior matrix test passed on {}",
                staticDbConfig.getDatabaseName());
    }

    // =========================================================================
    //  testMultiLineSqlWithComments
    // =========================================================================

    /**
     * Verifies that {@link tbleSpace#sql(String)} handles multi-line SQL with
     * comments ({@code --}) and blank lines, executing DDL/DML statements and
     * returning the result of the final SELECT.
     */
    @Test
    public void testMultiLineSqlWithComments() throws Exception {
        // Use an isolated space (like testWriteIndividualFields) so no shared
        // Router state leaks to other tests.
        final tbleSpace space = createTestSpace();
        try {
            // == Multi-line SQL with comments and blank lines ==
            // CREATE + INSERTs run as update; SELECT runs as query — only its
            // rows are returned.
            final String tableName = "multi_test";
            final Obj result = space.sql("""
                                         -- Create a temp table for this test
                                         CREATE TABLE %s (id INTEGER PRIMARY KEY, label TEXT);
                                         
                                         -- Insert some rows
                                         INSERT INTO %s VALUES (1, 'alpha');
                                         INSERT INTO %s VALUES (2, 'beta');
                                         
                                         -- Blank line above this comment should be ignored
                                         
                                         -- Final query: should be the result returned
                                         SELECT * FROM %s ORDER BY id;
                                         """.formatted(tableName, tableName, tableName, tableName));
            final List<Obj> rows = result.stream().toList();
            assertEquals(2, rows.size(), "should have 2 rows");
            assertEquals(str("alpha"), rows.get(0).asRec().at(uri("label")),
                    "first row label should be alpha");
            assertEquals(str("beta"), rows.get(1).asRec().at(uri("label")),
                    "second row label should be beta");

            // == Single-statement degenerate case ==
            final List<Obj> singleRows = space.sql(
                    "SELECT COUNT(*) AS cnt FROM " + tableName).stream().toList();
            assertEquals(1, singleRows.size(), "COUNT should return one row");
            final Obj cntRow = singleRows.get(0);
            assertEquals(jnt(2), cntRow.asRec().at(uri("cnt")),
                    "count should be 2");

            // == All-comment / empty input → noobj ==
            assertEquals(noobj(), space.sql("-- just a comment\n\n  -- another comment"),
                    "all-comment input should return noobj");

            // Cleanup
            space.sql("DROP TABLE IF EXISTS " + tableName);

            LOG.info("multi-line SQL with comments test passed on {}",
                    staticDbConfig.getDatabaseName());
        } finally {
            Router.global().removeSpace(space.vid());
            space.close();
        }
    }

    // =========================================================================
    //  Schema sanity — catches instset loading regressions
    // =========================================================================

    /**
     * Verifies {@code *db:+} returns table schema Types.  If the instset
     * fails to load (e.g. a rewrite builder throws during {@code setup()}),
     * the Router cannot resolve this pattern and this test fails first.
     */
    @Test
    public void testWildcardCollectionReturnsSchemaTypes() throws Exception {
        final Obj result = ObjmtronSerializer.parse("*" + SPACE_VID + "/instset/+").apply();
        assertTrue(result.isObjs() || result.isLst(),
                "*" + SPACE_VID + "/instset/+ should return a stream of Types, got: " + result);
        final List<Obj> types = result.stream().toList();
        assertFalse(types.isEmpty(),
                "*db:+ should return at least one schema Type");
        for (final Obj t : types) {
            assertTrue(t.isType() || t.isRec(),
                    "each result from *db:+ should be a Type or rrow, got: " + t);
        }
    }

    // =========================================================================
    //  KV store rewrites
    // =========================================================================

    private static final String KV_PREFIX = "db:kvtest";
    private static final String[][] KV_SEED = {
            {"a", "str('alpha')"}, {"b", "str('beta')"}, {"c", "str('gamma')"},
            {"d", "str('delta')"}, {"e", "str('epsilon')"},
            {"sub/x", "str('xray')"}, {"sub/y", "str('yankee')"},
            {"deep/1/a", "str('deep_a')"}, {"deep/1/b", "str('deep_b')"},
            {"deep/2/a", "str('deep_c')"},
    };

    private static void seedKVData() {
        for (final String[] kv : KV_SEED)
            ObjmtronSerializer.parse(KV_PREFIX + "/" + kv[0] + " -> " + kv[1]).apply();
    }

    private static void cleanKVData() {
        for (final String[] kv : KV_SEED)
            ObjmtronSerializer.parse(KV_PREFIX + "/" + kv[0] + " -> none").apply();
    }

    static Stream<Arguments> provideKVStoreRewriteTestCases() {
        return Stream.of(
                // kv_count
                Arguments.of("kv: single +", "*db:kvtest/+.count()", jnt(5)),
                Arguments.of("kv: nested +", "*db:kvtest/sub/+.count()", jnt(2)),
                Arguments.of("kv: double +", "*db:kvtest/+/+.count()", jnt(2)),
                Arguments.of("kv: deep ++", "*db:kvtest/deep/+/+.count()", jnt(3)),
                Arguments.of("kv: exact path", "*db:kvtest/b.count()", jnt(1)),
                Arguments.of("kv: empty path", "*db:kvtest/nonexistent/+.count()", jnt(0)),
                // kv_limit
                Arguments.of("kv: take 3", "*db:kvtest/+.take(3).count()", jnt(3)),
                Arguments.of("kv: take 10", "*db:kvtest/+.take(10).count()", jnt(5)),
                Arguments.of("kv: take 0", "*db:kvtest/+.take(0).count()", jnt(0))
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideKVStoreRewriteTestCases")
    public void testKVStoreRewrites(String description, String code, Obj expected) throws Exception {
        seedKVData();
        try {
            runRewriteTest(description, code, expected);
        } finally {
            cleanKVData();
        }
    }

    // =========================================================================
    //  Non-INTEGER primary key — auto-create table with VARCHAR PK
    // =========================================================================

    /**
     * Verifies that writing a Rec to a URI with a non-numeric rowId
     * auto-creates the table with a {@code VARCHAR(255)} PK, while numeric
     * rowIds still use {@code INTEGER}.  This test runs against every
     * database backend (SQLite, PostgreSQL, MariaDB, MySQL).
     */
    @Test
    public void testTextPrimaryKeyAutoCreate() throws Exception {
        final tbleSpace space = createTestSpace();
        try {
            // ── 1. Write with a string rowId → should create VARCHAR PK ──
            final fURI textRowURI = f("db:textpk_people/marko");
            Router.writeToSpace(textRowURI, rec(
                    uri("name"), str("Marko"),
                    uri("role"), str("engineer")
            ));
            // Verify the table exists in the schema
            assertTrue(space.existingTableSchema().getTableNames()
                            .contains("textpk_people"),
                    "table should be auto-created");

            // Verify the PK column type is VARCHAR (not INTEGER) via JDBC metadata
            // Use the space's own connection to avoid cross-connection visibility issues
            final java.sql.DatabaseMetaData md = space.sjvm().getMetaData();
            try (final java.sql.ResultSet cols = md.getColumns(
                    space.sjvm().getCatalog(), null, "textpk_people", "id")) {
                assertTrue(cols.next(), "PK column 'id' should exist");
                final int sqlType = cols.getInt("DATA_TYPE");
                assertTrue(
                        sqlType == java.sql.Types.VARCHAR
                                || sqlType == java.sql.Types.NVARCHAR
                                || sqlType == java.sql.Types.LONGVARCHAR,
                        "PK should be VARCHAR for text rowId, got sqlType=" + sqlType);
            }

            // Read the row back
            final Obj row1 = Router.readFromSpace(textRowURI);
            assertFalse(row1.isNoObj(), "row should exist");
            assertTrue(row1.isRec(), "row should be a Rec");
            assertEquals(str("Marko"), row1.asRec().at(uri("name")));
            assertEquals(str("engineer"), row1.asRec().at(uri("role")));

            // ── 2. Write another row to the same table ──
            final fURI textRow2URI = f("db:textpk_people/josh");
            Router.writeToSpace(textRow2URI, rec(
                    uri("name"), str("Josh"),
                    uri("role"), str("designer")
            ));
            final Obj row2 = Router.readFromSpace(textRow2URI);
            assertEquals(str("Josh"), row2.asRec().at(uri("name")));

            // ── 3. Update a field on the string-PK row ──
            Router.writeToSpace(f("db:textpk_people/marko/role"), str("architect"));
            final Obj row1Updated = Router.readFromSpace(textRowURI);
            assertEquals(str("architect"), row1Updated.asRec().at(uri("role")));
            // Name should be unchanged
            assertEquals(str("Marko"), row1Updated.asRec().at(uri("name")));

            // ── 4. Write with a numeric rowId → should create INTEGER PK ──
            final fURI intRowURI = f("db:intpk_items/42");
            Router.writeToSpace(intRowURI, rec(
                    uri("label"), str("widget"),
                    uri("price"), real(9.99)
            ));
            assertTrue(space.existingTableSchema().getTableNames()
                            .contains("intpk_items"),
                    "integer-keyed table should be auto-created");

            // Verify the PK column type is INTEGER via JDBC metadata
            final java.sql.DatabaseMetaData md2 = space.sjvm().getMetaData();
            try (final java.sql.ResultSet cols = md2.getColumns(
                    space.sjvm().getCatalog(), null, "intpk_items", "id")) {
                assertTrue(cols.next(), "PK column 'id' should exist");
                final int sqlType = cols.getInt("DATA_TYPE");
                assertTrue(
                        sqlType == java.sql.Types.INTEGER
                                || sqlType == java.sql.Types.BIGINT
                                || sqlType == java.sql.Types.SMALLINT
                                || sqlType == java.sql.Types.TINYINT,
                        "PK should be INTEGER for numeric rowId, got sqlType=" + sqlType);
            }

            // Read the integer-PK row back
            final Obj intRow = Router.readFromSpace(intRowURI);
            assertFalse(intRow.isNoObj(), "int-PK row should exist");
            assertEquals(str("widget"), intRow.asRec().at(uri("label")));
            assertEquals(9.99, intRow.asRec().at(uri("price")).asReal().realValue(), 0.001,
                    "price should round-trip as 9.99");

            // ── 5. Write another integer-PK row ──
            Router.writeToSpace(f("db:intpk_items/99"), rec(
                    uri("label"), str("gadget"),
                    uri("price"), real(4.50)
            ));
            final Obj intRow2 = Router.readFromSpace(f("db:intpk_items/99"));
            assertEquals(str("gadget"), intRow2.asRec().at(uri("label")));

            LOG.info("text PK auto-create test passed on {}",
                    staticDbConfig.getDatabaseName());

            // Cleanup: drop the test tables
            try (final Connection conn = staticDbConfig.getConnection();
                 final java.sql.Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DROP TABLE IF EXISTS textpk_people");
                stmt.executeUpdate("DROP TABLE IF EXISTS intpk_items");
            }
        } finally {
            Router.global().removeSpace(space.vid());
            space.close();
        }
    }

    // =========================================================================
    //  _tid column round-trip
    // =========================================================================

    /**
     * Verifies that the {@code _tid} column is created with every rec table,
     * stored as a plain URI string, and restored as the rec's TID on read.
     */
    @Test
    public void testTidColumnRoundTrip() throws Exception {
        final tbleSpace space = createTestSpace();
        final String tableName = "tid_test";
        try {
            // -- Write a rec with a specific, non-trivial TID --
            final fURI knownTid = AI_MESSAGE_TID;  // /m/llm/message/ai
            final Rec rec = rec(
                    uri("text"), str("hello"),
                    uri("name"), str("assistant")
            ).tid(knownTid).asRec();
            Router.writeToSpace(f("db:" + tableName + "/1"), rec);

            // -- Verify _tid column exists in the database --
            final java.sql.DatabaseMetaData md = space.sjvm().getMetaData();
            boolean hasTidCol = false;
            try (final java.sql.ResultSet cols = md.getColumns(
                    space.sjvm().getCatalog(), null, tableName, "_tid")) {
                hasTidCol = cols.next();
            }
            assertTrue(hasTidCol, "_tid column should exist in the table");

            // -- Verify raw value: plain URI string (no <> wrapper needed) --
            final Obj raw = space.sql("SELECT _tid FROM " + tableName + " WHERE id = 1");
            final List<Obj> rawRows = raw.stream().toList();
            assertEquals(1, rawRows.size(), "should have one row");
            final Obj tidField = rawRows.get(0).asRec().at(uri("_tid"));
            assertTrue(tidField.isStr(),
                    "_tid should be stored as a plain string, got: " + tidField);
            assertEquals(knownTid, f(tidField.strValue()),
                    "_tid value should be " + knownTid);

            // -- Read back via space: rec.tid() restored from _tid --
            final Obj row = space.read(f("db:" + tableName + "/1"));
            assertTrue(row.isRec(), "row should be a Rec");
            assertEquals(knownTid, row.asRec().tid(),
                    "rec.tid() should be restored from _tid column");
            // _tid must NOT appear as a field in the rec — it's system metadata
            assertFalse(row.asRec().has(uri("_tid")),
                    "_tid column should not appear as a field in the rec");

            // -- Verify user fields are intact --
            assertEquals(str("hello"), row.asRec().at(uri("text")));
            assertEquals(str("assistant"), row.asRec().at(uri("name")));

            LOG.info("_tid round-trip test passed on {}",
                    staticDbConfig.getDatabaseName());
        } finally {
            try {
                space.sql("DROP TABLE IF EXISTS " + tableName);
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
            Router.global().removeSpace(space.vid());
            space.close();
        }
    }

    // =========================================================================
    //  KvStore TID column — primitive sub-type round-trip
    // =========================================================================

    /**
     * Verifies that primitive sub-types (e.g., {@code usd_currency}) survive
     * a write/read cycle through the kv_store table.  Each CSV row writes
     * a typed value, reads it back, and asserts the TID matches.
     *
     * <pre>
     * CSV columns: {@code type_uri, value, expectedType}
     * </pre>
     */
    @ParameterizedTest(name = "[{index}] {0} {2} round-trip")
    @CsvSource(delimiter = '%', value = {
            /* Rows are: db:kv/<entry> % <value expression> % <expected tid> */
            "db:kv/cost_in    % usd_currency::0.0008 % usd_currency",
            "db:kv/cost_out   % usd_currency::0.0005 % usd_currency",
            "db:kv/cost_total % usd_currency::0.0012 % usd_currency",
    })
    public void testKvStoreTidPreserved(String uri, String valueEncoded, String expectedTid) throws Exception {
        // Ensure math ISA is loaded so usd_currency type is registered
        InstSet.importInstSet(f("/m/math"));
        final Obj value = parseObj(valueEncoded);
        final fURI expectedTidURI = f(expectedTid);

        final tbleSpace space = createTestSpace();
        try {
            // Write a typed real value to kv_store
            Router.writeToSpace(f(uri), value);

            // Read it back
            final Obj roundTripped = Router.readFromSpace(f(uri));
            assertFalse(roundTripped.isNoObj(), "should read back non-noobj");
            assertTrue(roundTripped.isReal(), "should be a real");
            assertEquals(value.realValue(), roundTripped.realValue(), 0.0,
                    "numeric value should be preserved");

            // The key assertion: subtype TID must survive the write/read cycle
            assertEquals(expectedTidURI, roundTripped.tid(),
                    "TID should be " + expectedTid + " — kv_store.tid column must preserve sub-type");

            LOG.info("kv_store tid round-trip OK: %s → %s", value, roundTripped);
        } finally {
            Router.global().removeSpace(space.vid());
            space.close();
        }
    }

    // =========================================================================
    //  _mtron_meta column-type persistence — on-first-write
    // =========================================================================

    /**
     * Verifies that {@code _mtron_meta} records column types on first write:
     * <ul>
     *   <li>A {@code $table} sentinel row captures the table-level TID</li>
     *   <li>Every column gets a row with {@code base_vid} and {@code obj_tid}</li>
     *   <li>FK columns also record {@code ref_table}</li>
     * </ul>
     */
    @Test
    public void testMtronMetaPersistsColumnTypesOnFirstWrite() throws Exception {
        final tbleSpace space = createTestSpace();
        final String tableName = "meta_firstwrite";
        try {
            // Write a rec with diverse types to trigger table creation
            Router.writeToSpace(f("db:" + tableName + "/1"), rec(
                    uri("name"), str("Alice"),
                    uri("age"), jnt(30),
                    uri("salary"), real(75000.0),
                    uri("active"), bool(true),
                    uri("homepage"), uri("https://alice.example.com")
            ));

            // -- Verify _mtron_meta rows exist --------------------------------
            final Obj metaRows = space.sql(
                    "SELECT column_name, base_vid, obj_tid, ref_table FROM " +
                            "_mtron_meta WHERE table_name = '" + tableName +
                            "' ORDER BY column_name");
            final List<Obj> rows = metaRows.stream().toList();
            assertFalse(rows.isEmpty(), "_mtron_meta should have rows for the new table");

            // -- Verify $table sentinel ---------------------------------------
            final Obj sentinelRow = rows.stream()
                    .filter(r -> r.asRec().at(uri("column_name")).strValue().equals("$table"))
                    .findFirst().orElse(null);
            assertNotNull(sentinelRow, "$table sentinel row should exist");
            // base_vid for a rec is REC_TID (e.g. /m/rec)
            assertTrue(sentinelRow.asRec().at(uri("base_vid")).isStr(),
                    "$table base_vid should be a string");
            // obj_tid should be the rec's TID
            assertFalse(sentinelRow.asRec().at(uri("obj_tid")).isNoObj(),
                    "$table obj_tid should not be null");

            // -- Verify column rows have base_vid -----------------------------
            for (final Obj row : rows) {
                final String colName = row.asRec().at(uri("column_name")).strValue();
                if ("$table".equals(colName)) continue;
                final Obj baseVid = row.asRec().at(uri("base_vid"));
                assertFalse(baseVid.isNoObj(),
                        "column " + colName + " should have base_vid");
                assertTrue(baseVid.isStr(),
                        "base_vid for " + colName + " should be a string, got: " + baseVid);
            }

            // -- Verify specific types -----------------------------------------
            // name → str base
            final Obj nameRow = rows.stream()
                    .filter(r -> r.asRec().at(uri("column_name")).strValue().equals("name"))
                    .findFirst().orElseThrow();
            assertTrue(nameRow.asRec().at(uri("base_vid")).strValue().contains("str"),
                    "name base_vid should contain 'str', got: " +
                            nameRow.asRec().at(uri("base_vid")));
            // homepage → uri base
            final Obj hpRow = rows.stream()
                    .filter(r -> r.asRec().at(uri("column_name")).strValue().equals("homepage"))
                    .findFirst().orElseThrow();
            assertTrue(hpRow.asRec().at(uri("base_vid")).strValue().contains("uri"),
                    "homepage base_vid should contain 'uri', got: " +
                            hpRow.asRec().at(uri("base_vid")));
            // age → int base
            final Obj ageRow = rows.stream()
                    .filter(r -> r.asRec().at(uri("column_name")).strValue().equals("age"))
                    .findFirst().orElseThrow();
            assertTrue(ageRow.asRec().at(uri("base_vid")).strValue().contains("int"),
                    "age base_vid should contain 'int', got: " +
                            ageRow.asRec().at(uri("base_vid")));

            // -- Verify ref_table is NULL for non-FK columns ------------------
            for (final Obj row : rows) {
                final String colName = row.asRec().at(uri("column_name")).strValue();
                if ("$table".equals(colName)) continue;
                final Obj ref = row.asRec().at(uri("ref_table"));
                assertTrue(ref.isNoObj() || ref.isNone(),
                        "non-FK column " + colName + " should have NULL ref_table, got: " + ref);
            }

            LOG.info("_mtron_meta first-write persistence test passed on {}",
                    staticDbConfig.getDatabaseName());
        } finally {
            try {
                space.sql("DROP TABLE IF EXISTS " + tableName);
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
            Router.global().removeSpace(space.vid());
            space.close();
        }
    }

    // =========================================================================
    //  _mtron_meta column-type persistence — on-the-fly ALTER
    // =========================================================================

    /**
     * Verifies that when a new column is added via {@code ALTER TABLE} (on-the-fly),
     * its type metadata is persisted to {@code _mtron_meta}.
     */
    @Test
    public void testMtronMetaPersistsOnAlterTable() throws Exception {
        final tbleSpace space = createTestSpace();
        final String tableName = "meta_alter";
        try {
            // -- First write: creates table with name + age --------------------
            Router.writeToSpace(f("db:" + tableName + "/1"), rec(
                    uri("name"), str("Alice"),
                    uri("age"), jnt(30)
            ));

            // Verify initial _mtron_meta state
            final int initialCount = space.sql(
                            "SELECT COUNT(*) AS cnt FROM _mtron_meta WHERE table_name = '" +
                                    tableName + "'").stream().toList().get(0)
                    .asRec().at(uri("cnt")).asInt().jvm().intValue();
            // $table sentinel + name + age = 3 rows
            assertEquals(3, initialCount,
                    "should have $table + name + age rows in _mtron_meta");

            // -- Second write: adds email column (triggers addColumnOnTheFly) --
            Router.writeToSpace(f("db:" + tableName + "/2"), rec(
                    uri("name"), str("Bob"),
                    uri("age"), jnt(25),
                    uri("email"), uri("mailto:bob@example.com")
            ));

            // -- Verify _mtron_meta has the new column -------------------------
            final Obj metaRows = space.sql(
                    "SELECT column_name, base_vid FROM _mtron_meta WHERE table_name = '" +
                            tableName + "' AND column_name = 'email'");
            final List<Obj> emailRows = metaRows.stream().toList();
            assertEquals(1, emailRows.size(),
                    "_mtron_meta should have one row for email column");
            final Obj emailRow = emailRows.get(0);
            assertTrue(emailRow.asRec().at(uri("base_vid")).strValue().contains("uri"),
                    "email base_vid should contain 'uri', got: " +
                            emailRow.asRec().at(uri("base_vid")));

            // -- Verify total count increased ----------------------------------
            final int finalCount = space.sql(
                            "SELECT COUNT(*) AS cnt FROM _mtron_meta WHERE table_name = '" +
                                    tableName + "'").stream().toList().get(0)
                    .asRec().at(uri("cnt")).asInt().jvm().intValue();
            assertEquals(4, finalCount,
                    "should have $table + name + age + email = 4 rows after ALTER");

            // -- Verify column actually exists in DB schema --------------------
            final java.sql.DatabaseMetaData md = space.sjvm().getMetaData();
            try (final java.sql.ResultSet cols = md.getColumns(
                    space.sjvm().getCatalog(), null, tableName, "email")) {
                assertTrue(cols.next(), "email column should exist in the database");
            }

            LOG.info("_mtron_meta ALTER TABLE persistence test passed on {}",
                    staticDbConfig.getDatabaseName());
        } finally {
            try {
                space.sql("DROP TABLE IF EXISTS " + tableName);
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
            Router.global().removeSpace(space.vid());
            space.close();
        }
    }

    // =========================================================================
    //  _mtron_meta — type metadata survives restart
    // =========================================================================

    /**
     * Verifies that column type metadata in {@code _mtron_meta} survives a
     * space close/reopen cycle, and that the schema instset correctly renders
     * persisted types (e.g. {@code uri::T} not {@code str::T} for URI columns).
     */
    @Test
    public void testMtronMetaSurvivesRestart() throws Exception {
        final String tableName = "meta_restart";
        final fURI spaceVid = f("/sys/space/tble/metarestart_test");

        final tbleSpace space1 = tbleSpace.of(
                rec(
                        uri(PATTERN), uri("mr:#"),
                        uri(HOST), uri(staticDbConfig.getJdbcHost()),
                        uri(DRIVER), uri(staticDbConfig.getDriverClass()),
                        uri(TABLE), lst(uri(tableName)),
                        uri(ROUTE), rec(uri("mr:"), uri(""))
                ).jvm(),
                spaceVid
        );
        try {
            // Write data that creates the table with typed columns
            Router.writeToSpace(f("mr:" + tableName + "/1"), rec(
                    uri("name"), str("Alice"),
                    uri("website"), uri("https://alice.example.com"),
                    uri("score"), jnt(100)
            ));

            // Verify types are in _mtron_meta before restart
            final int beforeCount = space1.sql(
                            "SELECT COUNT(*) AS cnt FROM _mtron_meta WHERE table_name = '" +
                                    tableName + "'").stream().toList().get(0)
                    .asRec().at(uri("cnt")).asInt().jvm().intValue();
            assertTrue(beforeCount >= 3,
                    "should have at least $table + 2 columns before restart, got: " + beforeCount);

        } finally {
            Router.global().removeSpace(space1.vid());
            space1.close();
        }

        // -- Restart: re-open the same database -------------------------------
        final tbleSpace space2 = tbleSpace.of(
                rec(
                        uri(PATTERN), uri("mr:#"),
                        uri(HOST), uri(staticDbConfig.getJdbcHost()),
                        uri(DRIVER), uri(staticDbConfig.getDriverClass()),
                        uri(TABLE), lst(uri(tableName)),
                        uri(ROUTE), rec(uri("mr:"), uri(""))
                ).jvm(),
                spaceVid
        );
        try {
            // -- Verify _mtron_meta rows survived restart ---------------------
            final Obj metaRows = space2.sql(
                    "SELECT column_name, base_vid, obj_tid FROM _mtron_meta WHERE table_name = '" +
                            tableName + "' ORDER BY column_name");
            final List<Obj> rows = metaRows.stream().toList();
            assertFalse(rows.isEmpty(),
                    "_mtron_meta rows should survive restart");

            // -- Verify $table sentinel survived -------------------------------
            final boolean hasSentinel = rows.stream()
                    .anyMatch(r -> "$table".equals(
                            r.asRec().at(uri("column_name")).strValue()));
            assertTrue(hasSentinel, "$table sentinel should survive restart");

            // -- Verify website column is still recorded as uri base ----------
            final Obj websiteRow = rows.stream()
                    .filter(r -> "website".equals(
                            r.asRec().at(uri("column_name")).strValue()))
                    .findFirst().orElse(null);
            assertNotNull(websiteRow, "website column row should survive restart");
            assertTrue(websiteRow.asRec().at(uri("base_vid")).strValue().contains("uri"),
                    "website should still be uri base type after restart, got: " +
                            websiteRow.asRec().at(uri("base_vid")));

            // -- Verify schema instset renders website as uri::T --------------
            final Type tableType = space2.schemaInstset().types().stream()
                    .filter(t -> t.vid().name().equalsIgnoreCase(tableName))
                    .findFirst().orElse(null);
            assertNotNull(tableType,
                    "table type should exist in schema instset after restart");
            final Obj wsField = tableType.isPredicateObj().asRec().at(uri("website"));
            assertFalse(wsField.isNoObj(), "website field should exist in schema");
            assertTrue(wsField.isType(), "website should be a Type in schema");
            assertEquals("uri", wsField.asType().vid().name(),
                    "website should be uri::T in schema after restart, got: " + wsField);

            LOG.info("_mtron_meta restart survival test passed on {}",
                    staticDbConfig.getDatabaseName());
        } finally {
            try {
                space2.sql("DROP TABLE IF EXISTS " + tableName);
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
            Router.global().removeSpace(space2.vid());
            space2.close();
        }
    }

    // =========================================================================
    //  Schema instset — table-level TID from $table sentinel
    // =========================================================================

    /**
     * Verifies that the schema instset's table Type uses the TID from the
     * {@code $table} sentinel row (the rec's TID at write time) rather than
     * a generic fallback.
     */
    @Test
    public void testTableTidInSchemaInstset() throws Exception {
        final tbleSpace space = createTestSpace();
        final String tableName = "tid_instset_test";
        try {
            // Write a rec with a specific TID
            final fURI customTid = f("/m/test/person");  // non-standard TID
            final Rec rec = rec(
                    uri("name"), str("Marko"),
                    uri("role"), str("engineer")
            ).tid(customTid).asRec();
            Router.writeToSpace(f("db:" + tableName + "/1"), rec);

            // -- Verify $table sentinel stores the custom TID ------------------
            final Obj sentinelRow = space.sql(
                            "SELECT obj_tid FROM _mtron_meta WHERE table_name = '" +
                                    tableName + "' AND column_name = '$table'")
                    .stream().toList().get(0);
            final String storedTid = sentinelRow.asRec().at(uri("obj_tid")).strValue();
            assertEquals(customTid.toString(), storedTid,
                    "$table sentinel should store the rec's TID");

            // -- Verify schema instset Type uses the custom TID ----------------
            final Type tableType = space.schemaInstset().types().stream()
                    .filter(t -> t.vid().name().equalsIgnoreCase(tableName))
                    .findFirst().orElse(null);
            assertNotNull(tableType, "table type should exist in schema instset");
            assertEquals(customTid, tableType.tid(),
                    "schema instset type TID should match the rec's TID, got: " +
                            tableType.tid());

            LOG.info("table TID in schema instset test passed on {}",
                    staticDbConfig.getDatabaseName());
        } finally {
            try {
                space.sql("DROP TABLE IF EXISTS " + tableName);
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
            Router.global().removeSpace(space.vid());
            space.close();
        }
    }

    // =========================================================================
    //  auto_from non-resolution — tbleSpace must not eagerly resolve
    // =========================================================================

    /**
     * Verifies that tbleSpace never eagerly resolves {@code auto_from} values
     * during reads or writes.  auto_from should round-trip as an instruction,
     * and {@code !*} references stored inside strings must NOT be parsed into
     * auto_from by the table read path.
     *
     * <p>Scenarios:
     * <ol>
     *   <li>Explicit auto_from FK → stored/read as auto_from inst (not resolved)</li>
     *   <li>String containing {@code [!*...]} → stored/read as plain string</li>
     *   <li>Rec fields accessed via {@code recValue().get()} → no resolution</li>
     * </ol>
     */
    @Test
    public void testAutoFromNotEagerlyResolved() throws Exception {
        final tbleSpace space = createTestSpace();
        final String parentTable = "af_parent";
        final String childTable = "af_child";
        try {
            // -- S1: Write parent rows ------------------------------------------
            Router.writeToSpace(f("db:" + parentTable + "/1"),
                    rec(uri("label"), str("alpha")));
            Router.writeToSpace(f("db:" + parentTable + "/2"),
                    rec(uri("label"), str("beta")));

            // -- S2: Write child with explicit auto_from FK ---------------------
            Router.writeToSpace(f("db:" + childTable + "/1"), rec(
                    uri("name"), str("first"),
                    uri("parent_ref"), auto_from_(f("db:" + parentTable + "/1")).tryToInst()));

            // -- S3: Read back: auto_from FK must be an inst, not resolved ------
            final Obj childRow = Router.readFromSpace(f("db:" + childTable + "/1"));
            assertTrue(childRow.isRec(), "child row should be a Rec");

            // Access via recValue().get() — must NOT trigger resolution
            final Obj parentRef = childRow.asRec().recValue().get(uri("parent_ref"));
            assertNotNull(parentRef, "parent_ref should exist in the rec");
            assertTrue(parentRef.isInst(), "parent_ref should be an Inst (auto_from), got: " + parentRef);
            assertTrue(parentRef.isAutoFrom(),
                    "parent_ref should be auto_from, not resolved, got: " + parentRef);

            // -- S4: Write a row with a string that looks like mtron [!*...] ---
            final String embeddedRef = "[!*db:" + parentTable + "/2]";
            Router.writeToSpace(f("db:" + childTable + "/2"), rec(
                    uri("name"), str("second"),
                    uri("tags"), str(embeddedRef)));

            // -- S5: Read back: _mtron_meta says str::T → stays a string --------
            // No heuristic JSON/mtron parsing.  The column was written as a
            // string, so it comes back as a string.
            final Obj childRow2 = Router.readFromSpace(f("db:" + childTable + "/2"));
            assertTrue(childRow2.isRec(), "child row 2 should be a Rec");
            final Obj tagsField = childRow2.asRec().recValue().get(uri("tags"));
            assertTrue(tagsField.isStr(),
                    "tags should stay a string (column typed str::T by _mtron_meta), got: " + tagsField);
            assertEquals(embeddedRef, tagsField.strValue(),
                    "tags string should round-trip verbatim");

            // -- S6: Write a row with a string mimicking auto_from lst storage --
            // (ConceptFeature stores links as serialized strings like
            //  [!*/usr/dr/concept/x, !*/usr/dr/concept/y] in TEXT columns.)
            final String serializedLinks = "[!*db:" + parentTable + "/1,!*db:" + parentTable + "/2]";
            Router.writeToSpace(f("db:" + childTable + "/3"), rec(
                    uri("name"), str("third"),
                    uri("links"), str(serializedLinks)));

            // -- S7: Read back: _mtron_meta says str::T → stays a string --------
            final Obj childRow3 = Router.readFromSpace(f("db:" + childTable + "/3"));
            assertTrue(childRow3.isRec(), "child row 3 should be a Rec");
            final Obj linksField = childRow3.asRec().recValue().get(uri("links"));
            assertTrue(linksField.isStr(),
                    "links should stay a string (str::T from _mtron_meta), got: " + linksField);
            assertEquals(serializedLinks, linksField.strValue(),
                    "links string should round-trip verbatim");

            LOG.info("auto_from non-resolution test passed on {}",
                    staticDbConfig.getDatabaseName());
        } finally {
            try {
                space.sql("DROP TABLE IF EXISTS " + childTable);
                space.sql("DROP TABLE IF EXISTS " + parentTable);
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
            Router.global().removeSpace(space.vid());
            space.close();
        }
    }


    // =========================================================================
    // FK inference: column-value guards
    // =========================================================================
    // The URI prefix is arbitrary — SpaceHelper routeFromSpace/routeToSpace
    // strips it before FK code sees the collection/entry/field.  What matters
    // is the VALUE stored in the FK-named column.  Three cases:
    //
    //  1. Absolute URI  → plain Uri, no auto_from wrapping
    //  2. Bare PK       → auto_from_ with correct path, no doubling
    //  3. JSON list     → Lst, not a single FK reference

    /**
     * Absolute URI stored in an FK-named column is returned as plain Uri,
     * not wrapped in auto_from and not path-doubled.
     * <p>
     * Regression: store /x/session/1 in message.session — naming convention
     * matches table "session".  Without the '/' guard, the value gets wrapped
     * in auto_from_() and the space pattern is prepended, doubling the path.
     */
    @Test
    public void testFKAbsoluteURIStoredAsPlainUri() throws Exception {
        final fURI spaceVid = f("/sys/space/tble/fk_abs_uri_test");
        final tbleSpace testSpace = tbleSpace.of(
                rec(
                        uri(PATTERN), uri("x:#"),
                        uri(HOST), uri(staticDbConfig.getJdbcHost()),
                        uri(DRIVER), uri(staticDbConfig.getDriverClass()),
                        uri(ROUTE), rec(uri("x:"), uri(""))
                ).jvm(),
                spaceVid
        );
        try {
            // Create target table so FK naming convention fires
            Router.writeToSpace(f("x:session/1"),
                    rec(uri("label"), str("test-session")));
            // Write row with absolute URI in FK column
            // (simulating SpaceChatSessionStore.updateMessages())
            Router.writeToSpace(f("x:message/1"), rec(
                    uri("text"), str("hello"),
                    uri("session"), uri("x:session/1")));

            final Obj msg = Router.readFromSpace(f("x:message/1"));
            assertTrue(msg.isRec());
            final Obj sessionField = msg.asRec().at(uri("session"));
            assertTrue(sessionField.isUri(),
                    "absolute URI must come back as Uri, got: " + sessionField);
            assertFalse(sessionField.isInst(),
                    "absolute URI must NOT be wrapped in auto_from");
            assertEquals(f("x:session/1"), sessionField.uriValue());

            LOG.info("FK absolute-URI test passed on {}",
                    staticDbConfig.getDatabaseName());
        } finally {
            try {
                testSpace.sql("DROP TABLE IF EXISTS message; DROP TABLE IF EXISTS session");
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }
    }

    /**
     * Bare PK stored in an FK-named column is wrapped in auto_from with
     * the correct target path and resolves correctly via at().
     */
    @Test
    public void testFKBarePKProducesAutoFrom() throws Exception {
        final fURI spaceVid = f("/sys/space/tble/fk_bare_pk_test");
        final tbleSpace testSpace = tbleSpace.of(
                rec(
                        uri(PATTERN), uri("x:#"),
                        uri(HOST), uri(staticDbConfig.getJdbcHost()),
                        uri(DRIVER), uri(staticDbConfig.getDriverClass()),
                        uri(ROUTE), rec(uri("x:"), uri(""))
                ).jvm(),
                spaceVid
        );
        try {
            Router.writeToSpace(f("x:user/1"),
                    rec(uri("name"), str("Alice")));
            Router.writeToSpace(f("x:note/1"), rec(
                    uri("text"), str("hi"),
                    uri("user"), jnt(1)));

            final Obj note = Router.readFromSpace(f("x:note/1"));
            assertTrue(note.isRec());
            // recValue().get() returns the raw stored value — at() eagerly
            // resolves auto_from to the target record
            final Obj userField = note.asRec().recValue().get(uri("user"));
            assertTrue(userField.isAutoFrom(),
                    "bare PK should produce auto_from, got: " + userField);
            assertEquals(f("x:user/1"), userField.asInst().arg(0).uriValue(),
                    "FK target path must not be doubled");

            // at() resolves the auto_from to the target record
            final Obj resolved = note.asRec().at(uri("user"));
            assertTrue(resolved.isRec());
            assertEquals(str("Alice"), resolved.asRec().at(uri("name")));

            LOG.info("FK bare-PK test passed on {}",
                    staticDbConfig.getDatabaseName());
        } finally {
            try {
                testSpace.sql("DROP TABLE IF EXISTS note; DROP TABLE IF EXISTS user");
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }
    }

    /**
     * JSON list stored in an FK-named column is NOT treated as a FK.
     * <p>
     * Regression: column "message" storing [auto_from(...), auto_from(...)]
     * matched table "message" by naming convention.  Without the '[' guard,
     * the entire JSON string was treated as a single FK path segment,
     * producing x:message/[...].
     */
    @Test
    public void testFKColumnWithJSONListNotWrapped() throws Exception {
        final fURI spaceVid = f("/sys/space/tble/fk_json_list_test");
        final tbleSpace testSpace = tbleSpace.of(
                rec(
                        uri(PATTERN), uri("x:#"),
                        uri(HOST), uri(staticDbConfig.getJdbcHost()),
                        uri(DRIVER), uri(staticDbConfig.getDriverClass()),
                        uri(ROUTE), rec(uri("x:"), uri(""))
                ).jvm(),
                spaceVid
        );
        try {
            // Create target table so FK naming convention fires
            Router.writeToSpace(f("x:message/1"),
                    rec(uri("text"), str("hello")));

            // Write a row with a MESSAGE field containing a JSON list
            // (simulating ConceptFeature.addConceptsToSpace)
            final Obj msgLinks = lst(
                    auto_from_(f("x:message/4")).tryToInst(),
                    auto_from_(f("x:message/5")).tryToInst());
            Router.writeToSpace(f("x:concept/test"), rec(
                    uri("label"), str("test-concept"),
                    uri("message"), msgLinks));

            final Obj concept = Router.readFromSpace(f("x:concept/test"));
            assertTrue(concept.isRec());
            final Obj messageField = concept.asRec().at(uri("message"));
            assertTrue(messageField.isLst(),
                    "JSON-list must come back as Lst, got: " + messageField);
            assertFalse(messageField.isInst(),
                    "JSON-list must NOT be wrapped as auto_from Inst");
            assertEquals(2, messageField.asLst().lstValue().size());

            LOG.info("FK JSON-list guard test passed on {}",
                    staticDbConfig.getDatabaseName());
        } finally {
            try {
                testSpace.sql("DROP TABLE IF EXISTS concept; DROP TABLE IF EXISTS message");
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }
    }

    // =========================================================================
    //  Long-prefix count/where rewrite tests
    // =========================================================================

    /**
     * Verifies that {@code sql_count} and {@code sql_where_count} rewrites
     * activate correctly with longer, non-trivial route prefixes.
     *
     * <p>The {@code matchPredicate} in {@code countRewrite}
     * must resolve the URI through the space before
     * {@link studio.phaseshift.metatron.furi.DataPath#withoutDB DataPath.withoutDB}
     * decomposition — otherwise the space-prefix segments are misidentified as
     * collection / entry / field, and {@code !dp.hasField() && !dp.hasExtension()}
     * blocks the rewrite.
     *
     * <p>A single space with pattern {@code x:#} is created with three route
     * entries that strip different long-prefix shapes.  {@code getSpaceFor}
     * matches any {@code x:…} URI against the space, then the route strips
     * the long prefix before DataPath decomposition:
     * <ul>
     *   <li>{@code x:a:bb:ccc  → ""} — single colon-bearing segment</li>
     *   <li>{@code x:a/b/c      → ""} — three plain path segments</li>
     *   <li>{@code x:a:b/c      → ""} — scheme-like first part + two path segments</li>
     * </ul>
     */
    @Test
    public void testLongPrefixCountAndWhereRewrites() throws Exception {
        final fURI spaceVid = f("/sys/space/tble/long_prefix_test");

        // Single space, three route entries — each strips a different long prefix.
        final tbleSpace testSpace = tbleSpace.of(
                rec(
                        uri(PATTERN), uri("x:#"),
                        uri(HOST), uri(staticDbConfig.getJdbcHost()),
                        uri(DRIVER), uri(staticDbConfig.getDriverClass()),
                        uri(ROUTE), rec(
                                uri("x:a:bb:ccc"), uri(""),
                                uri("x:a/b/c"), uri(""),
                                uri("x:a:b/c"), uri("")),
                        uri(TABLE), lst()
                ).jvm(),
                spaceVid
        );

        // (URI base, label, table suffix)
        final String[][] variants = {
                {"x:a:bb:ccc", "colon_seg", "cs_items"},
                {"x:a/b/c", "path_seg", "ps_items"},
                {"x:a:b/c", "mixed_seg", "ms_items"},
        };

        try {
            for (final String[] v : variants) {
                final String base = v[0];
                final String label = v[1];
                final String table = v[2];
                final String fullBase = base + "/" + table;

                // Seed 5 rows: val = 10, 20, 30, 40, 50; tag = odd/even alternating
                for (int i = 1; i <= 5; i++) {
                    Router.writeToSpace(f(fullBase + "/" + i),
                            rec(uri("val"), jnt(i * 10),
                                    uri("tag"), str(i % 2 == 0 ? "even" : "odd")));
                }

                // --- count rewrite (* clone form) ---
                final Obj countResult = ObjmtronSerializer.parse(
                        "*" + fullBase + "/+.count()").apply();
                assertEquals(jnt(5), countResult,
                        label + ": * count() should return 5");

                // --- count rewrite (@ anchor form) ---
                final Obj atCountResult = ObjmtronSerializer.parse(
                        "@" + fullBase + "/+.count()").apply();
                assertEquals(jnt(5), atCountResult,
                        label + ": @ count() should return 5");

                // --- @ limit rewrite (returns rows, must have VIDs) ---
                final Obj atLimitResult = ObjmtronSerializer.parse(
                        "@" + fullBase + "/+.take(3)").apply();
                final List<Obj> atLimitRows = atLimitResult.stream().toList();
                assertEquals(3, atLimitRows.size(), label + ": @ take(3) should return 3 rows");
                for (final Obj row : atLimitRows) {
                    assertNotNull(row.vid(), label + ": @ take row should have a VID");
                    assertNotNull(row.vid().scheme(),
                            label + ": @ take row VID should be routable: " + row.vid());
                }

                // --- @ where rewrite (returns rows, must have VIDs) ---
                final Obj atWhereResult = ObjmtronSerializer.parse(
                        "@" + fullBase + "/+.where([val=>?<30])").apply();
                final List<Obj> atWhereRows = atWhereResult.stream().toList();
                assertEquals(2, atWhereRows.size(),
                        label + ": @ where val<30 should find 2 rows (10,20)");
                for (final Obj row : atWhereRows) {
                    assertNotNull(row.vid(), label + ": @ where row should have a VID");
                    assertNotNull(row.vid().scheme(),
                            label + ": @ where row VID should be routable: " + row.vid());
                }

                // --- where + count rewrite (integer predicate) ---
                final Obj whereCountResult = ObjmtronSerializer.parse(
                        "*" + fullBase + "/+.where([val=>?>20]).count()").apply();
                assertEquals(jnt(3), whereCountResult,
                        label + ": where val>20 should find 3 rows (30,40,50)");

                // --- where + count rewrite (string predicate) ---
                final Obj whereCountStr = ObjmtronSerializer.parse(
                        "*" + fullBase + "/+.where([tag=>\"even\"]).count()").apply();
                assertEquals(jnt(2), whereCountStr,
                        label + ": where tag=even should find 2 rows");

                // --- where + order rewrite (rows with VIDs, sorted) ---
                final Obj whereOrderResult = ObjmtronSerializer.parse(
                        "@" + fullBase + "/+.where([val=>?>20]).order(select(val))>-").apply();
                final List<Obj> whereOrderRows = whereOrderResult.stream().toList();
                assertEquals(3, whereOrderRows.size(),
                        label + ": @ where val>20 order by val should return 3 rows");
                for (final Obj row : whereOrderRows) {
                    assertNotNull(row.vid(), label + ": @ where+order row should have a VID");
                    assertNotNull(row.vid().scheme(),
                            label + ": @ where+order row VID should be routable: " + row.vid());
                }
                // Verify ascending order by val
                assertEquals(jnt(30), whereOrderRows.get(0).asRec().at(uri("val")),
                        label + ": first row val should be 30");
                assertEquals(jnt(50), whereOrderRows.get(2).asRec().at(uri("val")),
                        label + ": last row val should be 50");

                LOG.info("Long-prefix rewrite PASSED for '%s' on %s",
                        base, staticDbConfig.getDatabaseName());
            }
        } finally {
            for (final String[] v : variants) {
                try {
                    testSpace.sql("DROP TABLE IF EXISTS " + v[2]);
                } catch (final Exception ex) {
                    LOG.warn("[ignored] %s", ex);
                }
            }
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }
    }

    /**
     * Verifies that {@code @} (anchor / {@code AT_INST_TID}) form of
     * {@code count()} triggers the same native SQL rewrite as the
     * {@code *} (clone / {@code FROM_INST_TID}) form.
     *
     * <p>Uses a dedicated space with its own seeded data to avoid
     * contamination from other tests that drop/recreate tables.
     */
    @Test
    public void testAtAnchorCountRewrite() throws Exception {
        final tbleSpace space = createTestSpace();
        try {
            // Seed a table through the space so the schema tracks it
            for (int i = 1; i <= 7; i++) {
                Router.writeToSpace(f("db:at_count_test/" + i),
                        rec(uri("val"), jnt(i)));
            }

            // ── * count (baseline) ──────────────────────────────────────
            final Obj starCount = ObjmtronSerializer.parse("*db:at_count_test/+.count()").apply();
            assertEquals(jnt(7), starCount, "* count");

            // ── @ count ─────────────────────────────────────────────────
            final Obj atCount = ObjmtronSerializer.parse("@db:at_count_test/+.count()").apply();
            assertEquals(jnt(7), atCount, "@ count");

            LOG.info("@ anchor count rewrite test PASSED on {}",
                    staticDbConfig.getDatabaseName());
        } finally {
            try {
                space.sql("DROP TABLE IF EXISTS at_count_test");
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
            Router.global().removeSpace(space.vid());
            space.close();
        }
    }

    // =========================================================================
    //  _mtron_meta — base_vid vs obj_tid for structured subtypes
    // =========================================================================

    /**
     * Verifies that when a column stores a structured Rec subtype (e.g.
     * {@code chat_result::T} whose base type is {@code rec::T}), the
     * {@code _mtron_meta} row uses {@code base_vid} for deserialization
     * dispatch while {@code obj_tid} preserves the specific subtype.
     *
     * <p>Key invariant: {@code base_vid} is {@code /m/rec} so that
     * {@code readColumnWithType} triggers mtron parsing on read, while
     * {@code obj_tid} (e.g. {@code /m/llm/chat_result}) is the declared
     * subtype.  Without this, a column storing {@code chat_result::[...]}
     * would be returned as raw {@code str::T} instead of a navigable
     * {@code chat_result::T} Rec.</p>
     */
    @Test
    public void testMtronMetaBaseVidTriggersRecDeserialization() throws Exception {
        // Ensure _mtron_meta is recreated with current schema
        try (final Connection c = staticDbConfig.getConnection();
             final Statement s = c.createStatement()) {
            s.executeUpdate("DROP TABLE IF EXISTS _mtron_meta");
        }
        final fURI spaceVid = f("/sys/space/tble/subtype_deser_test");
        final tbleSpace space = tbleSpace.of(
                rec(
                        uri(PATTERN), uri("sst:#"),
                        uri(HOST), uri(staticDbConfig.getJdbcHost()),
                        uri(DRIVER), uri(staticDbConfig.getDriverClass()),
                        uri(TABLE), lst(),
                        uri(ROUTE), rec(uri("sst:"), uri(""))
                ).jvm(),
                spaceVid
        );
        final String tableName = "subtype_test";
        try {
            // --- Write a Rec with a non-trivial subtype TID as a column value ---
            // Simulates what happens when mTool.resultStash supplies a chat_result::T Rec
            final fURI subtypeTid = f("/m/llm/chat_result");  // base is /m/rec
            final Rec nestedRec = rec(
                    uri("response"), str("nested response text"),
                    uri("time"), str("1.234s")
            ).tid(subtypeTid).asRec();

            Router.writeToSpace(f("sst:" + tableName + "/1"), rec(
                    uri("label"), str("outer row"),
                    uri("result"), nestedRec
            ));

            // --- Verify _mtron_meta: base_vid vs obj_tid ---
            final Obj metaRows = space.sql(
                    "SELECT column_name, base_vid, obj_tid FROM _mtron_meta " +
                            "WHERE table_name = '" + tableName +
                            "' AND column_name = 'result'");
            final List<Obj> rows = metaRows.stream().toList();
            assertEquals(1, rows.size(), "_mtron_meta should have one row for 'result' column");

            final Rec metaRow = rows.get(0).asRec();
            final String baseVid = metaRow.at(uri("base_vid")).strValue();
            final String objTid = metaRow.at(uri("obj_tid")).strValue();

            // base_vid must be the structural type (/m/rec) — this is what
            // readColumnWithType checks to decide "mtron-parse this TEXT"
            assertTrue(baseVid.contains("rec"),
                    "base_vid should contain 'rec' (the structural base type), got: " + baseVid);

            // obj_tid must preserve the specific subtype
            assertEquals(subtypeTid.toString(), objTid,
                    "obj_tid should be the specific subtype TID");

            // --- Read back via space: result should be a proper Rec, not a Str ---
            final Obj row = space.read(f("sst:" + tableName + "/1"));
            assertTrue(row.isRec(), "row should be a Rec");
            final Obj resultField = row.asRec().at(uri("result"));
            assertTrue(resultField.isRec(),
                    "'result' field should be deserialized as a Rec, got: " + resultField.type());
            assertEquals(subtypeTid, resultField.asRec().tid(),
                    "nested Rec should preserve its subtype TID");
            assertEquals(str("nested response text"),
                    resultField.asRec().at(uri("response")),
                    "nested Rec fields should be accessible");

            LOG.info("_mtron_meta base_vid/obj_tid subtype deserialization test passed on {}",
                    staticDbConfig.getDatabaseName());
        } finally {
            try {
                space.sql("DROP TABLE IF EXISTS " + tableName);
            } catch (final Exception ex) {
                LOG.warn("[ignored] %s", ex);
            }
            Router.global().removeSpace(space.vid());
            space.close();
        }
    }
}
