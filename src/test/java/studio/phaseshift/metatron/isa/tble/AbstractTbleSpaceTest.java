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

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import studio.phaseshift.metatron.algebra.rewrite.CommonRewritesTestContract;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;

import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
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
 *
 * <h3>Adding test cases</h3>
 * Most parameterized tests use {@code @CsvSource} with a simple type-prefix
 * convention for expected values:
 * <pre>
 *   str:Alice    →   str("Alice")
 *   jnt:42       →   jnt(42)
 *   real:99.99   →   real(99.99)
 *   bool:true    →   bool(true)
 * </pre>
 * See {@link #parseObj(String)}.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractTbleSpaceTest extends AbstractSpaceTest implements CommonRewritesTestContract {

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

    @Override
    public String make(final String expression, final Method testMethod) {
        // For table-mapped tests, $$ → db: (table path: db:people/1, db:companies/101)
        // For key-value tests, $$ → db:kv/test (kv path: db:kv/test/key)
        if (testMethod != null && "testMonoUpdate".equals(testMethod.getName())) {
            return expression.contains("$$") ? expression.replace("$$/", "db:").replace("$$", "db:") : expression;
        }
        return super.make(expression, testMethod);
    }

    @Override
    public String getNativeInstructionPrefix() {
        return "sql_";
    }

    // =========================================================================
    //  Type-prefix parser for @CsvSource test data
    // =========================================================================

    /**
     * Parses a type-prefixed string into its corresponding {@link Obj}.
     * <pre>
     *   str:Alice    → str("Alice")
     *   jnt:42       → jnt(42)
     *   real:99.99   → real(99.99)
     *   bool:true    → bool(true)
     *   bool:false   → bool(false)
     * </pre>
     */
    protected static Obj parseObj(final String encoded) {
        if (encoded == null) return str("");
        final int colon = encoded.indexOf(':');
        if (colon < 0) return str(encoded);
        final String type = encoded.substring(0, colon);
        final String value = encoded.substring(colon + 1);
        return switch (type) {
            case "str" -> str(value);
            case "jnt" -> jnt(Long.parseLong(value));
            case "real" -> real(Double.parseDouble(value));
            case "bool" -> bool(Boolean.parseBoolean(value));
            default -> str(encoded);
        };
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
        final Code rewritten = (Code) parsed.rewrite();
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
                                            String field from users             | db:users/1    | name         | str:Alice
                                            String field from products          | db:products/101 | product_name | str:Laptop
                                            Email field                         | db:users/2    | email        | str:bob@example.com
                                            Category field                      | db:products/105 | category     | str:Furniture
                                            Category stationery                  | db:products/107 | category     | str:Stationery
                                            Integer age field                   | db:users/1    | age          | jnt:30
                                            Integer quantity field              | db:products/102 | quantity     | jnt:50
                                            Integer quantity zero               | db:products/101 | quantity     | jnt:15
                                            Integer age field user 2            | db:users/2    | age          | jnt:25
                                            Integer age field user 3            | db:users/3    | age          | jnt:35
                                            Real salary field                   | db:users/1    | salary       | real:75000.50
                                            Real price field                    | db:products/101 | price        | real:1299.99
                                            Small price value                   | db:products/102 | price        | real:29.99
                                            Real salary Diana                   | db:users/4    | salary       | real:70000.25
                                            Real price furniture                 | db:products/105 | price        | real:249.99
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
    @CsvSource(delimiter = '|', textBlock = """     
                                            users | 1   | name         | str:Alice Updated     | str:Alice Updated
                                            users | 2   | email        | str:bob.new@example.com | str:bob.new@example.com
                                            products | 101 | product_name | str:Gaming Laptop   | str:Gaming Laptop
                                            products | 105 | category   | str:Office            | str:Office
                                            users | 1   | age          | jnt:31                | jnt:31
                                            users | 2   | age          | jnt:26                | jnt:26
                                            products | 102 | quantity     | jnt:100              | jnt:100
                                            products | 103 | quantity     | jnt:0                | jnt:0
                                            users | 1   | salary       | real:80000.00         | real:80000.00
                                            products | 101 | price        | real:999.00          | real:999.00
                                            users | 3   | salary       | real:100000.50        | real:100000.50
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
    @CsvSource(delimiter = '|', textBlock = """
                                            users    | 100 | name | str:Test User | name | str:Test User | age | jnt:25 | salary | real:50000.00 | active | bool:true | email | str:test@example.com
                                            products | 200 | product_name | str:New Product | product_name | str:New Product | price | real:199.99 | in_stock | bool:true | quantity | jnt:10 | category | str:Test Category
                                            users    | 101 | age  | jnt:0  | name | str:Zero Age | age | jnt:0 | salary | real:0.0 | active | bool:false | email | str:zero@example.com
                                            users    | 102 | name | str:Max Val | name | str:Max Val | age | jnt:999 | salary | real:999999.99 | active | bool:true | email | str:max@example.com
                                            products | 201 | price | real:0.0 | product_name | str:Free Item | price | real:0.0 | in_stock | bool:true | quantity | jnt:0 | category | str:Free
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
     * behaviour.  (PostgreSQL uses INTEGER columns for booleans, so bool writes
     * come back as jnt.)
     * <br>
     * CSV columns: {@code description, table, rowId, field, writeValue, expectedReadValue}
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', textBlock = """
                                            boolean true converts and back   | users | 1 | active | bool:true  | bool:true
                                            boolean false converts and back  | users | 1 | active | bool:false | bool:false
                                            real number with decimals        | users | 1 | salary | real:12345.00 | real:12345.00
                                            real number zero                 | users | 1 | salary | real:0.0     | real:0.0
                                            real negative                    | users | 1 | salary | real:-500.25  | real:-500.25
                                            real large                       | users | 1 | salary | real:9999999.99 | real:9999999.99
                                            integer zero                     | users | 1 | age    | jnt:0       | jnt:0
                                            integer large value              | users | 1 | age    | jnt:999     | jnt:999
                                            integer negative                 | users | 1 | age    | jnt:-1      | jnt:-1
                                            integer max long                 | users | 1 | age    | jnt:2147483647 | jnt:2147483647
                                            empty string                     | users | 1 | name   | str:        | str:
                                            string with spaces               | users | 1 | name   | str:  Test  | str:  Test
                                            string with special chars        | users | 1 | email  | str:test+tag@example.com | str:test+tag@example.com
                                            string unicode                   | users | 1 | name   | str:José María | str:José María
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

            // Verify directly in DB
            try (final Connection conn = staticDbConfig.getConnection();
                 final Statement stmt = conn.createStatement();
                 final ResultSet rs = stmt.executeQuery(
                         "SELECT name, age FROM users WHERE id = 1")) {
                if (rs.next()) {
                    assertEquals("Alice Smith", rs.getString("name"));
                    assertEquals(31, rs.getInt("age"));
                }
            }

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
            try (final Connection conn = staticDbConfig.getConnection();
                 final Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DROP TABLE IF EXISTS award");
                stmt.executeUpdate("DROP TABLE IF EXISTS person");
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
            try (final Connection conn = staticDbConfig.getConnection();
                 final Statement stmt = conn.createStatement();
                 final ResultSet rs = stmt.executeQuery(
                         "SELECT column_name, ref_table FROM _mtron_meta " +
                                 "WHERE table_name = 'item'")) {
                assertTrue(rs.next(), "_mtron_meta should have a row for item.category");
                assertEquals("category", rs.getString("column_name"));
                assertEquals("category", rs.getString("ref_table"));
                assertFalse(rs.next(), "_mtron_meta should have exactly one row for item");
            }

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
            try (final Connection conn = staticDbConfig.getConnection();
                 final Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DROP TABLE IF EXISTS item");
                stmt.executeUpdate("DROP TABLE IF EXISTS category");
            }
            try {
                Router.global().removeSpace(testSpace.vid());
                testSpace.close();
            } catch (final Exception ignored) {
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
            try (final Connection conn = staticDbConfig.getConnection();
                 final Statement stmt = conn.createStatement();
                 final ResultSet rs = stmt.executeQuery(
                         "SELECT column_name, ref_table FROM _mtron_meta " +
                                 "WHERE table_name = 'place'")) {
                assertTrue(rs.next(), "_mtron_meta should have a row for place.addr");
                assertEquals("addr", rs.getString("column_name"));
                assertEquals("g:V", rs.getString("ref_table"),
                        "cross-space ref should store scheme:segment, not bare table name");
                assertFalse(rs.next(), "should have exactly one row for place");
            }

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

            try (final Connection conn = staticDbConfig.getConnection();
                 final Statement stmt = conn.createStatement();
                 final ResultSet rs = stmt.executeQuery(
                         "SELECT column_name, ref_table FROM _mtron_meta " +
                                 "WHERE table_name = 'venue'")) {
                assertTrue(rs.next(), "_mtron_meta should have a row for venue.parent");
                assertEquals("parent", rs.getString("column_name"));
                assertEquals("place", rs.getString("ref_table"),
                        "internal FK should store bare table name");
            }

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
            try (final Connection conn = staticDbConfig.getConnection();
                 final Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DROP TABLE IF EXISTS venue");
                stmt.executeUpdate("DROP TABLE IF EXISTS place");
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
            try (final Connection conn = staticDbConfig.getConnection();
                 final Statement stmt = conn.createStatement();
                 final ResultSet rs = stmt.executeQuery(
                         "SELECT ref_table FROM _mtron_meta " +
                                 "WHERE table_name = 'arena' AND column_name = 'location'")) {
                assertTrue(rs.next());
                assertEquals("grph:vertices", rs.getString("ref_table"),
                        "multi-segment cross-space ref stores scheme:firstSegment");
            }

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
            try (final Connection conn = staticDbConfig.getConnection();
                 final Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DROP TABLE IF EXISTS arena");
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
            try (final Connection conn = staticDbConfig.getConnection();
                 final Statement stmt = conn.createStatement();
                 final ResultSet rs = stmt.executeQuery(
                         "SELECT column_name, ref_table FROM _mtron_meta " +
                                 "WHERE table_name = 'employee' ORDER BY column_name")) {
                assertTrue(rs.next());
                assertEquals("manager_id", rs.getString("column_name"));
                assertEquals("employee", rs.getString("ref_table"),
                        "self-referencing FK stores own table name");
                assertTrue(rs.next());
                assertEquals("org_id", rs.getString("column_name"));
                assertEquals("org", rs.getString("ref_table"),
                        "cross-table internal FK stores bare target table name");
                assertFalse(rs.next());
            }

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
            try (final Connection conn = staticDbConfig.getConnection();
                 final Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DROP TABLE IF EXISTS employee");
                stmt.executeUpdate("DROP TABLE IF EXISTS org");
            }
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }
    }
}
