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
import studio.phaseshift.metatron.AbstractDataPathTest;
import studio.phaseshift.metatron.algebra.rewrite.CommonRewritesTestContract;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.IncrQTest;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Code;
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
import static studio.phaseshift.metatron.isa.m.mInstSet.ALL_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.INT_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.Int.INT_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.gt_;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.is_;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.tble.tbleInstSet.TBLE_ISA_TID;

/**
 * Abstract base test suite for tbleSpace with database-agnostic tests.
 * Subclasses provide database-specific configuration via {@link DatabaseConfig}.
 * 
 * See {@link #parseObj(String)}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractTbleSpaceTest extends AbstractDataPathTest implements CommonRewritesTestContract, IncrQTest {

    /**
     * tbleIncQ is the native incrQ qproc implemented as AUTO INCREMENT
     * @return
     */
    @Override
    public fURI incrQBaseURI() {
        return f(getSpace().pattern().scheme() + ":incrq");
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
            "*<db:+>               % collection",    // wildcard collection → every result is a Type
            "*<db:users/+>.take(1) % entry",         // specific collection + wildcard entry → first is instance
            "*<db:users/+>.take(2) % entry",         // second entry also an instance (not just first)
    }, delimiter = '%')
    public void testDataPathSegmentTypes(final String code, final String segmentType) {
        super.testDataPathSegmentTypes(code, segmentType);
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

    /** Route inherited KV tests through the {@code kv/} collection so they
     *  bypass table mapping entirely.  Rewrite tests use the parent prefix. */
    @Override
    protected fURI testUri(String suffix) {
        return f("db:kv/test/" + suffix);
    }

    @Override
    public String make(final String expression, final Method testMethod) {
        // For testMonoUpdate, $$ → db: so seed data writes to db:<collection>/<docId>
        // and update/read expressions resolve to the same two-segment document paths.
        if (testMethod != null && "testMonoUpdate".equals(testMethod.getName())) {
            return expression.contains("$$") ? expression.replace("$$/", "db:") : expression;
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
    //  Rewrite tests (delegated to PostgreSQLTbleSpaceTest for test data)
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideAllRewriteTestCases")
    public void testRewrites(String description, String code, Obj expected) throws Exception {
        runRewriteTest(description, code, expected);
    }

    public static Stream<Arguments> provideAllRewriteTestCases() {
        return new PostgreSQLTbleSpaceTest().generateAllRewriteTestCases();
    }

    @Disabled
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("providePlanVerificationTestCases")
    public void testRewritePlans(String description, String code, String nativeInstName) throws Exception {
        final Code parsed = ObjmtronSerializer.parse(code);
        final Code rewritten = parsed.rewrite();
        LOG.info("Testing Rewrite Plan: %s", description);
        LOG.info("  Code: %s", code);
        LOG.info("  Plan: %s", rewritten);

        final String fullNativeInstName = getNativeInstructionPrefix() + nativeInstName;
        assertTrue(rewritten.stream().anyMatch(
                        obj -> obj.isInst()
                                && obj.asInst().tid().name().equals(fullNativeInstName)),
                "Plan should contain " + fullNativeInstName);
    }

    public static Stream<Arguments> providePlanVerificationTestCases() {
        return new PostgreSQLTbleSpaceTest().generatePlanVerificationTestCases();
    }

    // verify/firing tests: now enabled via getRewriteInstUri() which fetches
    // rewriters directly from the InstSet (Router.readFromSpace("/m/tble").at("rewrite"))

    @Disabled("parse().rewrite() does not trigger tbleSpace rewrites")
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideRewriteVerificationTestCases")
    public void testRewriteVerification(String description, String code, String nativeInstName) throws Exception {
        runRewriteVerificationTest(description, code, nativeInstName);
    }

    static Stream<Arguments> provideRewriteVerificationTestCases() {
        return Stream.empty();
    }

    @Disabled("parse().rewrite() does not trigger tbleSpace rewrites")
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideRewriteFiringTestCases")
    public void testRewriteFiring(String description, String code, String nativeInstName, boolean shouldRewrite) throws Exception {
        runRewriteFiringTest(description, code, nativeInstName, shouldRewrite);
    }

    static Stream<Arguments> provideRewriteFiringTestCases() {
        return Stream.empty();
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

            // _mtron_meta should have one row for item.category → category
            final Obj metaRows = testSpace.sql(
                    "SELECT column_name, ref_table FROM _mtron_meta WHERE table_name = 'item'");
            final List<Obj> metaList = metaRows.stream().toList();
            assertEquals(1, metaList.size(),
                    "_mtron_meta should have exactly one row for item");
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
                    "SELECT column_name, ref_table FROM _mtron_meta WHERE table_name = 'place'");
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
                    "SELECT column_name, ref_table FROM _mtron_meta WHERE table_name = 'venue'");
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

            // Verify storage: _mtron_meta records scheme:segment
            final Obj arenaMetaRows = sourceSpace.sql(
                    "SELECT ref_table FROM _mtron_meta WHERE table_name = 'arena' AND column_name = 'location'");
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

            // _mtron_meta has rows for both FK columns
            final Obj empMetaRows = testSpace.sql(
                    "SELECT column_name, ref_table FROM _mtron_meta WHERE table_name = 'employee' ORDER BY column_name");
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
        final Obj result = ObjmtronSerializer.parse("*db:+").apply();
        assertTrue(result.isObjs() || result.isLst(),
                "*db:+ should return a stream of Types, got: " + result);
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
}