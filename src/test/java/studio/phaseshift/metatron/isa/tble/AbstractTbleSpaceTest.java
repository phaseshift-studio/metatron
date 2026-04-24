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
import studio.phaseshift.metatron.isa.m.type.Code;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
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
 * Subclasses provide database-specific configuration via DatabaseConfig.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractTbleSpaceTest extends AbstractSpaceTest implements CommonRewritesTestContract {

    protected static final fURI SPACE_VID = f("/sys/space/tabledb/test");
    protected static DatabaseConfig staticDbConfig;
    protected final DatabaseConfig dbConfig;

    public AbstractTbleSpaceTest(final DatabaseConfig dbConfig) {
        // Use scheme-based baseURI like dcmntSpaceTest does (mongo:test_collection/rewrite_test)
        // This ensures the parent memSpace has a specific pattern (tble:kv/#)
        // which is MORE SPECIFIC than the tbleSpace pattern (tble:#)
        // so that tble:users routes to tbleSpace, not the parent memSpace
        super(f("db:kv/test"), () -> {
            // This lambda is called lazily, so staticDbConfig will be set by @BeforeAll
            if (staticDbConfig == null) {
                throw new IllegalStateException("staticDbConfig not initialized. @BeforeAll method must run first.");
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
        // Don't set staticDbConfig here - let @BeforeAll do it after starting the container
    }

    @BeforeAll
    public static void setupInstSet() throws Exception {
        InstSet.importInstSet(TBLE_ISA_TID);
    }

    /**
     * Setup database - called once before all tests.
     * Note: This is NOT annotated with @BeforeAll because it needs to be called
     * by subclasses after they set staticDbConfig in their @BeforeAll method.
     */
    protected static void setupDatabase() throws Exception {
        if (staticDbConfig == null) {
            throw new IllegalStateException("Database config not initialized. Ensure constructor is called.");
        }
        staticDbConfig.setup();

        // Create test tables for parameterized tests
        try (Connection conn = staticDbConfig.getConnection();
             Statement stmt = conn.createStatement()) {

            // Create users table
            stmt.executeUpdate(staticDbConfig.getUsersTableDDL());

            // Insert test data into users
            stmt.executeUpdate(String.format("INSERT INTO users VALUES (1, 'Alice', 30, 75000.0, %d, 'alice@example.com')", staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format("INSERT INTO users VALUES (2, 'Bob', 25, 60000.0, %d, 'bob@example.com')", staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format("INSERT INTO users VALUES (3, 'Charlie', 35, 85000.0, %d, 'charlie@example.com')", staticDbConfig.getBooleanFalse()));

            // Create products table
            stmt.executeUpdate(staticDbConfig.getProductsTableDDL());

            // Insert test data into products
            stmt.executeUpdate(String.format("INSERT INTO products VALUES (101, 'Laptop', 1299.99, %d, 15, 'Electronics')", staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format("INSERT INTO products VALUES (102, 'Mouse', 29.99, %d, 50, 'Electronics')", staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format("INSERT INTO products VALUES (103, 'Keyboard', 79.99, %d, 30, 'Electronics')", staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format("INSERT INTO products VALUES (1, 'Monitor', 399.99, %d, 0, 'Electronics')", staticDbConfig.getBooleanFalse()));
            stmt.executeUpdate(String.format("INSERT INTO products VALUES (105, 'Desk Chair', 249.99, %d, 20, 'Furniture')", staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format("INSERT INTO products VALUES (106, 'Desk', 499.99, %d, 10, 'Furniture')", staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format("INSERT INTO products VALUES (107, 'Notebook', 4.99, %d, 100, 'Stationery')", staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format("INSERT INTO products VALUES (108, 'Pen Set', 12.99, %d, 75, 'Stationery')", staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format("INSERT INTO products VALUES (109, 'Webcam', 89.99, %d, 0, 'Electronics')", staticDbConfig.getBooleanFalse()));
            stmt.executeUpdate(String.format("INSERT INTO products VALUES (110, 'Headphones', 149.99, %d, 25, 'Electronics')", staticDbConfig.getBooleanTrue()));

            // Create rewrite_test table for CommonRewritesTestContract
            stmt.executeUpdate(staticDbConfig.getRewriteTestTableDDL());

            // Insert 10 rows of test data for rewrite tests
            for (int i = 1; i <= 10; i++) {
                final int active = (i % 2 == 1) ? staticDbConfig.getBooleanTrue() : staticDbConfig.getBooleanFalse();
                stmt.executeUpdate(String.format(
                    "INSERT INTO rewrite_test (id, value, name, active) VALUES (%d, %d, 'item%d', %d)",
                    i, i, i, active
                ));
            }
        }
    }

    /**
     * Cleanup database - called once after all tests.
     * Note: This is NOT annotated with @AfterAll because it needs to be called
     * by subclasses in their @AfterAll method.
     */
    protected static void cleanupDatabase() throws Exception {
        if (staticDbConfig != null) {
            staticDbConfig.teardown();
        }
    }

    // ========== Helper Methods ==========

    /**
     * Setup test database with users and products tables.
     * Used by parameterized tests that need a fresh database state.
     */
    protected void setupTestDatabase() throws Exception {
        try (final Connection conn = staticDbConfig.getConnection();
             final Statement stmt = conn.createStatement()) {

            // Drop tables if they exist
            stmt.executeUpdate("DROP TABLE IF EXISTS users");
            stmt.executeUpdate("DROP TABLE IF EXISTS products");

            // Create users table
            stmt.executeUpdate(staticDbConfig.getUsersTableDDL());

            // Insert test data
            stmt.executeUpdate(String.format("INSERT INTO users VALUES (1, 'Alice', 30, 75000.50, %d, 'alice@example.com')", staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format("INSERT INTO users VALUES (2, 'Bob', 25, 60000.00, %d, 'bob@example.com')", staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format("INSERT INTO users VALUES (3, 'Charlie', 35, 85000.75, %d, 'charlie@example.com')", staticDbConfig.getBooleanFalse()));
            stmt.executeUpdate(String.format("INSERT INTO users VALUES (4, 'Diana', 28, 70000.25, %d, 'diana@example.com')", staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format("INSERT INTO users VALUES (5, 'Eve', 42, 95000.00, %d, 'eve@example.com')", staticDbConfig.getBooleanTrue()));

            // Create products table
            stmt.executeUpdate(staticDbConfig.getProductsTableDDL());

            stmt.executeUpdate(String.format("INSERT INTO products VALUES (101, 'Laptop', 1299.99, %d, 15, 'Electronics')", staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format("INSERT INTO products VALUES (102, 'Mouse', 29.99, %d, 50, 'Electronics')", staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format("INSERT INTO products VALUES (103, 'Keyboard', 79.99, %d, 30, 'Electronics')", staticDbConfig.getBooleanTrue()));
            stmt.executeUpdate(String.format("INSERT INTO products VALUES (1, 'Monitor', 399.99, %d, 0, 'Electronics')", staticDbConfig.getBooleanFalse()));
            stmt.executeUpdate(String.format("INSERT INTO products VALUES (105, 'Desk Chair', 249.99, %d, 20, 'Furniture')", staticDbConfig.getBooleanTrue()));
        }
    }

    /**
     * Cleanup test database.
     */
    protected void cleanupTestDatabase() throws Exception {
        try (final Connection conn = staticDbConfig.getConnection();
             final Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP TABLE IF EXISTS users");
            stmt.executeUpdate("DROP TABLE IF EXISTS products");
        }
    }

    /**
     * Create a test space instance.
     */
    protected tbleSpace createTestSpace() {
        return tbleSpace.of(
                rec(
                        uri(PATTERN), uri("db:#"),
                        uri(HOST), uri(staticDbConfig.getJdbcHost()),
                        uri(DRIVER), uri(staticDbConfig.getDriverClass()),
                        uri(ROUTE), rec(uri("db:"), uri("")),
                        uri(TABLE), lst()
                ).jvm(),
                f("/sys/space/tble/test")
        );
    }

    @Override
    public fURI getTestDataUriPrefix() {
        return f("db:rewrite_test");
    }

    @Override
    public String getNativeInstructionPrefix() {
        return "sql_";
    }

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
        assertTrue(rewritten.stream().anyMatch(obj -> obj.isInst() && obj.asInst().tid().name().equals(fullNativeInstName)),
                "Plan should contain " + fullNativeInstName);
    }

    public static Stream<Arguments> providePlanVerificationTestCases() {
        return new PostgreSQLTbleSpaceTest().generatePlanVerificationTestCases();
    }

    // ========== Parameterized Tests ==========

    /**
     * Test reading individual fields from database rows.
     */
    @ParameterizedTest(name = "[{index}] Read {0}")
    @MethodSource("provideFieldReadTestCases")
    public void testReadIndividualFields(String description, String tableRowUri, String fieldName, Obj expectedValue) throws Exception {
        setupTestDatabase();

        final tbleSpace testSpace = createTestSpace();
        try {
            final Obj row = Router.readFromSpace(f(tableRowUri));
            assertFalse(row.isNoObj(), "should not be a noobj");
            assertTrue(row.isRec(), "should return a rec");

            final Obj actualValue = row.asRec().at(uri(fieldName));
            assertEquals(expectedValue, actualValue, description);
        } finally {
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }

        cleanupTestDatabase();
    }

    protected static Stream<Arguments> provideFieldReadTestCases() {
        return Stream.of(
                // String fields
                Arguments.of("String field from users", "db:users/1", "name", str("Alice")),
                Arguments.of("String field from products", "db:products/101", "product_name", str("Laptop")),
                Arguments.of("Email field", "db:users/2", "email", str("bob@example.com")),
                Arguments.of("Category field", "db:products/105", "category", str("Furniture")),

                // Integer fields
                Arguments.of("Integer age field", "db:users/1", "age", jnt(30)),
                Arguments.of("Integer quantity field", "db:products/102", "quantity", jnt(50)),
                // Note: primary key (id) is encoded in the VID, not in the row body.
                // To read the pk value use the path db:users/3/id, not a full-row read.

                // Real/Double fields
                Arguments.of("Real salary field", "db:users/1", "salary", real(75000.50)),
                Arguments.of("Real price field", "db:products/101", "price", real(1299.99)),
                Arguments.of("Small price value", "db:products/102", "price", real(29.99))

                // Boolean fields - REMOVED because PostgreSQL uses INTEGER columns
                // Different databases return different types (bool vs int)
                // Arguments.of("Boolean true value", "db:users/1", "active", bool(true)),
                // Arguments.of("Boolean false value", "db:users/3", "active", bool(false)),
                // Arguments.of("Product in stock true", "db:products/101", "in_stock", bool(true)),
                // Arguments.of("Product in stock false", "db:products/1", "in_stock", bool(false))
        );
    }

    /**
     * Test writing individual fields to database rows.
     */
    @ParameterizedTest(name = "[{index}] Write {2} to {0}/{1}")
    @MethodSource("provideFieldWriteTestCases")
    public void testWriteIndividualFields(String table, String rowId, String field, Obj newValue, Obj expectedValue) throws Exception {
        setupTestDatabase();

        final tbleSpace testSpace = createTestSpace();
        try {
            final String writeUri = String.format("db:%s/%s/%s", table, rowId, field);
            Router.writeToSpace(f(writeUri), newValue);

            final String rowUri = String.format("db:%s/%s", table, rowId);
            final Obj row = Router.readFromSpace(f(rowUri));
            assertFalse(row.isNoObj(), "should not be a noobj");
            assertTrue(row.isRec(), "should return a rec");

            final Obj actualValue = row.asRec().at(uri(field));
            assertEquals(expectedValue, actualValue, String.format("field %s should be updated", field));
        } finally {
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }

        cleanupTestDatabase();
    }

    protected static Stream<Arguments> provideFieldWriteTestCases() {
        return Stream.of(
                // String updates
                Arguments.of("users", "1", "name", str("Alice Updated"), str("Alice Updated")),
                Arguments.of("users", "2", "email", str("bob.new@example.com"), str("bob.new@example.com")),
                Arguments.of("products", "101", "product_name", str("Gaming Laptop"), str("Gaming Laptop")),

                // Integer updates
                Arguments.of("users", "1", "age", jnt(31), jnt(31)),
                Arguments.of("users", "2", "age", jnt(26), jnt(26)),
                Arguments.of("products", "102", "quantity", jnt(100), jnt(100)),

                // Real updates
                Arguments.of("users", "1", "salary", real(80000.00), real(80000.00)),
                Arguments.of("products", "101", "price", real(999.00), real(999.00))

                // Boolean updates - REMOVED (PostgreSQL uses INTEGER)
                // Arguments.of("users", "3", "active", bool(true), bool(true)),
                // Arguments.of("users", "1", "active", bool(false), bool(false)),
                // Arguments.of("products", "1", "in_stock", bool(true), bool(true)),
                // Arguments.of("products", "103", "in_stock", bool(false), bool(false))
        );
    }

    /**
     * Test reading entire rows as records.
     */
    @ParameterizedTest(name = "[{index}] Read row {0}")
    @CsvSource({
            "db:users/1, name, Alice",
            "db:users/2, name, Bob",
            "db:users/5, name, Eve",
            "db:products/101, product_name, Laptop",
            "db:products/105, product_name, Desk Chair"
    })
    public void testReadEntireRow(String uri, String fieldName, String expectedFieldValue) throws Exception {
        setupTestDatabase();

        final tbleSpace testSpace = createTestSpace();
        try {
            final Obj row = Router.readFromSpace(f(uri));
            assertTrue(row.isRec(), "Should return a record");

            final Rec rec = row.asRec();
            final Obj fieldValue = rec.at(uri(fieldName));
            assertEquals(str(expectedFieldValue), fieldValue, String.format("Field %s should match", fieldName));
        } finally {
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }

        cleanupTestDatabase();
    }

    /**
     * Test inserting new rows with various data types.
     */
    @ParameterizedTest(name = "[{index}] Insert new row into {0}")
    @MethodSource("provideRowInsertTestCases")
    public void testInsertNewRows(String table, String rowId, Rec rowData, String verifyField, Obj expectedValue) throws Exception {
        setupTestDatabase();

        final tbleSpace testSpace = createTestSpace();
        try {
            final String writeUri = String.format("db:%s/%s", table, rowId);
            Router.writeToSpace(f(writeUri), rowData);

            final Obj insertedRow = Router.readFromSpace(f(writeUri));
            assertTrue(insertedRow.isRec(), "Should return a record");

            final Obj fieldValue = insertedRow.asRec().at(uri(verifyField));
            assertEquals(expectedValue, fieldValue, String.format("Field %s should match", verifyField));
        } finally {
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }

        cleanupTestDatabase();
    }

    protected static Stream<Arguments> provideRowInsertTestCases() {
        return Stream.of(
                Arguments.of(
                        "users", "100",
                        rec(
                                uri(NAME), str("Test User"),
                                uri("age"), jnt(25),
                                uri("salary"), real(50000.00),
                                uri("active"), bool(true),
                                uri("email"), str("test@example.com")
                        ),
                        "name", str("Test User")
                ),
                Arguments.of(
                        "products", "200",
                        rec(
                                uri("product_name"), str("New Product"),
                                uri("price"), real(199.99),
                                uri("in_stock"), bool(true),
                                uri("quantity"), jnt(10),
                                uri("category"), str("Test Category")
                        ),
                        "product_name", str("New Product")
                )
        );
    }

    /**
     * Test type conversions and edge cases.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideTypeConversionTestCases")
    public void testTypeConversions(String description, String table, String rowId, String field, Obj writeValue, Obj expectedReadValue) throws Exception {
        setupTestDatabase();

        final tbleSpace testSpace = createTestSpace();
        try {
            final String writeUri = String.format("db:%s/%s/%s", table, rowId, field);
            Router.writeToSpace(f(writeUri), writeValue);

            final String rowUri = String.format("db:%s/%s", table, rowId);
            final Obj row = Router.readFromSpace(f(rowUri));
            assertTrue(row.isRec() || row.isStr(), "should return a rec or str");

            final Obj actualValue = row.asRec().at(uri(field));
            assertEquals(expectedReadValue, actualValue, description);
        } finally {
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }

        cleanupTestDatabase();
    }

    protected static Stream<Arguments> provideTypeConversionTestCases() {
        return Stream.of(
                // Boolean conversions - PostgreSQL uses INTEGER columns, so booleans are stored/read as 0/1
                Arguments.of("boolean true converts and back", "users", "1", "active", bool(true), jnt(1)),
                Arguments.of("boolean false converts and back", "users", "1", "active", bool(false), jnt(0)),

                // Real number precision
                Arguments.of("real number with decimals", "users", "1", "salary", real(12345.00), real(12345.00)),
                Arguments.of("real number zero", "users", "1", "salary", real(0.0), real(0.0)),

                // Integer boundaries
                Arguments.of("integer zero", "users", "1", "age", jnt(0), jnt(0)),
                Arguments.of("integer large value", "users", "1", "age", jnt(999), jnt(999)),

                // String edge cases
                Arguments.of("empty string", "users", "1", "name", str(""), str("")),
                Arguments.of("string with spaces", "users", "1", "name", str("  Test  "), str("  Test  ")),
                Arguments.of("string with special chars", "users", "1", "email", str("test+tag@example.com"), str("test+tag@example.com"))
        );
    }

    /**
     * Override parent's testSpecialStringValues to skip null character test for PostgreSQL.
     * PostgreSQL doesn't support null bytes (\0) in strings.
     */
    @Override
    @ParameterizedTest(name = "[{index}] Special string: {0}")
    @CsvSource(value = {
            "newline              | 'line1\\nline2'",
            "tab                  | 'col1\\tcol2'",
            "carriage return      | 'line1\\rline2'",
            // "null character       | 'before\\0after'",  // PostgreSQL doesn't support null bytes
            "rtl text             | 'مرحبا'",
            "mixed scripts        | 'Hello世界مرحبا'"
    }, delimiter = '|', ignoreLeadingAndTrailingWhitespace = false)
    public void testSpecialStringValues(String description, String value) {
        final fURI uri = testUri("special_string/" + description.replaceAll("\\s+", "_"));

        // Unescape special characters
        String unescaped = value
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\r")
                .replace("\\0", "\0");

        // Remove surrounding quotes if present
        if (unescaped.startsWith("'") && unescaped.endsWith("'")) {
            unescaped = unescaped.substring(1, unescaped.length() - 1);
        }

        // Write and read back
        this.space.write(uri, str(unescaped));
        final Obj result = this.space.read(uri);
        assertEquals(str(unescaped), result, description);
    }

    @Test
    public void testComprehensiveTableOperations() throws Exception {
        LOG.info("Testing comprehensive table operations with multiple data types on {}", staticDbConfig.getDatabaseName());

        setupTestDatabase();

        final tbleSpace testSpace = createTestSpace();

        try {
            LOG.info("Discovered tables: %s", testSpace.existingTableSchema.getTableNames());

            // TEST 1: Read specific rows
            LOG.info("TEST 1: Reading specific rows");
            final Obj user1 = Router.readFromSpace(f("db:users/1"));
            LOG.info("User 1: %s", user1);
            assertTrue(user1.isRec(), "Should return a record");
            final Rec user1Rec = user1.asRec();
            assertEquals(str("Alice"), user1Rec.at(uri(NAME)), "Name should be Alice");
            assertEquals(jnt(30), user1Rec.at(uri("age")), "Age should be 30");

            // TEST 2: Write entire row (update existing)
            LOG.info("TEST 2: Updating entire row");
            Router.writeToSpace(f("db:users/1"), rec(
                    uri(NAME), str("Alice Smith"),
                    uri("age"), jnt(31),
                    uri("salary"), real(80000.00),
                    uri("active"), bool(true),
                    uri("email"), str("alice.smith@example.com")
            ));

            final Obj updatedUser1 = Router.readFromSpace(f("db:users/1"));
            LOG.info("Updated User 1: %s", updatedUser1);
            final Rec updatedUser1Rec = updatedUser1.asRec();
            assertEquals(str("Alice Smith"), updatedUser1Rec.at(uri(NAME)), "Name should be updated");
            assertEquals(jnt(31), updatedUser1Rec.at(uri("age")), "Age should be updated");

            // TEST 3: Write single field
            LOG.info("TEST 3: Updating single field");
            Router.writeToSpace(f("db:users/2/age"), jnt(26));
            Router.writeToSpace(f("db:users/2/salary"), real(62000.00));

            final Obj updatedUser2 = Router.readFromSpace(f("db:users/2"));
            LOG.info("Updated User 2: %s", updatedUser2);
            final Rec updatedUser2Rec = updatedUser2.asRec();
            assertEquals(jnt(26), updatedUser2Rec.at(uri("age")), "Age should be updated to 26");
            assertEquals(real(62000.00), updatedUser2Rec.at(uri("salary")), "Salary should be updated");

            // TEST 4: Verify data in database directly
            LOG.info("TEST 4: Verifying data in database");
            try (final Connection conn = staticDbConfig.getConnection();
                 final Statement stmt = conn.createStatement();
                 final ResultSet rs = stmt.executeQuery("SELECT name, age FROM users WHERE id = 1")) {
                if (rs.next()) {
                    assertEquals("Alice Smith", rs.getString("name"), "DB should have updated name");
                    assertEquals(31, rs.getInt("age"), "DB should have updated age");
                }
            }

            LOG.info("All comprehensive tests passed for {}!", staticDbConfig.getDatabaseName());

        } finally {
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }

        cleanupTestDatabase();
    }

    // ========== auto_from FK Pointer Tests ==========

    /**
     * Creates {@code person} and {@code award} tables via auto-creation (first-write DDL),
     * verifying that {@code auto_from} pointer values round-trip correctly through SQL:
     * <ol>
     *   <li>written as the raw FK integer PK</li>
     *   <li>read back as a live {@code auto_from} inst pointing to the correct URI</li>
     *   <li>dereferenced via the fURI path to the full referenced record</li>
     * </ol>
     * The test intentionally uses no pre-existing schema — tables are created on
     * the first write, and FK tracking is via {@code _mtron_meta} so it is
     * portable across SQLite, PostgreSQL, MySQL, and MariaDB.
     */
    @Test
    public void testAutoFromWriteReadRoundTrip() throws Exception {
        final fURI spaceVid = f("/sys/space/tble/autofrom_test");
        // Use a unique scheme "pfk:" so this space has no overlap with the main "db:#" space.
        final tbleSpace testSpace = tbleSpace.of(
                rec(
                        uri(PATTERN), uri("pfk:#"),
                        uri(HOST),    uri(staticDbConfig.getJdbcHost()),
                        uri(DRIVER),  uri(staticDbConfig.getDriverClass()),
                        uri(TABLE),   lst(uri("person"), uri("award")),
                        uri(ROUTE),   rec(uri("pfk:"), uri(""))
                ).jvm(),
                spaceVid
        );

        try {
            // --- WRITE: auto-create person table, then award table with FK pointer ---
            Router.writeToSpace(f("pfk:person/1"), rec(uri("name"), str("Alice")));
            Router.writeToSpace(f("pfk:person/2"), rec(uri("name"), str("Bob")));

            final Obj alicePointer = auto_from_(f("pfk:person/1")).tryToInst();
            Router.writeToSpace(f("pfk:award/1"), rec(
                    uri("trophy"),    str("gold"),
                    uri("recipient"), alicePointer
            ));
            Router.writeToSpace(f("pfk:award/2"), rec(
                    uri("trophy"),    str("silver"),
                    uri("recipient"), auto_from_(f("pfk:person/2")).tryToInst()
            ));

            // --- READ: recipient must come back as an auto_from inst, not a plain str ---
            final Obj award1 = Router.readFromSpace(f("pfk:award/1"));
            assertTrue(award1.isRec(), "award/1 should be a record");
            final Obj recipient = award1.asRec().at(uri("recipient"));
            assertTrue(recipient.isAutoFrom(),
                    "recipient should be an auto_from inst, got: " + recipient.tid());

            // The pointer should reference pfk:person/1
            assertEquals(f("pfk:person/1"), recipient.asInst().arg(0).uriValue(),
                    "auto_from should point to pfk:person/1");

            // --- DEREFERENCE: traversing the pointer should yield Alice's record ---
            final Obj aliceRec = Router.readFromSpace(f("pfk:award/1/recipient"));
            assertTrue(aliceRec.isRec(), "dereferenced recipient should be a record");
            assertEquals(str("Alice"), aliceRec.asRec().at(uri("name")),
                    "dereferenced recipient name should be Alice");

            // --- SECOND ROW: verify Bob's pointer too ---
            final Obj award2 = Router.readFromSpace(f("pfk:award/2"));
            final Obj recipient2 = award2.asRec().at(uri("recipient"));
            assertTrue(recipient2.isAutoFrom(), "award/2 recipient should also be auto_from");
            assertEquals(f("pfk:person/2"), recipient2.asInst().arg(0).uriValue(),
                    "auto_from should point to pfk:person/2");

            LOG.info("auto_from round-trip test passed on {}", staticDbConfig.getDatabaseName());

        } finally {
            // Drop the auto-created tables to leave db clean for other tests
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
     * Verifies that the {@code _mtron_meta} table is created on space init and
     * that it correctly records auto_from column mappings after a write.
     */
    @Test
    public void testMtronMetaTableTracksAutoFromColumns() throws Exception {
        final fURI spaceVid = f("/sys/space/tble/metacheck_test");
        // Use a unique scheme "pmk:" so this space has no overlap with the main "db:#" space.
        final tbleSpace testSpace = tbleSpace.of(
                rec(
                        uri(PATTERN), uri("pmk:#"),
                        uri(HOST),    uri(staticDbConfig.getJdbcHost()),
                        uri(DRIVER),  uri(staticDbConfig.getDriverClass()),
                        uri(TABLE),   lst(uri("category"), uri("item")),
                        uri(ROUTE),   rec(uri("pmk:"), uri(""))
                ).jvm(),
                spaceVid
        );

        try {
            Router.writeToSpace(f("pmk:category/10"), rec(uri("label"), str("Books")));
            Router.writeToSpace(f("pmk:item/1"), rec(
                    uri("title"),    str("Dune"),
                    uri("category"), auto_from_(f("pmk:category/10")).tryToInst()
            ));

            // _mtron_meta should have one row for item.category -> category
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

            // And a subsequent space restart (re-init) must still reconstruct the auto_from
            testSpace.close();
            Router.global().removeSpace(testSpace.vid());

            final tbleSpace testSpace2 = tbleSpace.of(
                    rec(
                            uri(PATTERN), uri("pmk:#"),
                            uri(HOST),    uri(staticDbConfig.getJdbcHost()),
                            uri(DRIVER),  uri(staticDbConfig.getDriverClass()),
                            uri(TABLE),   lst(uri("category"), uri("item")),
                            uri(ROUTE),   rec(uri("pmk:"), uri(""))
                    ).jvm(),
                    spaceVid
            );
            try {
                final Obj item = Router.readFromSpace(f("pmk:item/1"));
                final Obj catPtr = item.asRec().at(uri("category"));
                assertTrue(catPtr.isAutoFrom(),
                        "after restart, category should still be auto_from, got: " + catPtr.tid());
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
}
