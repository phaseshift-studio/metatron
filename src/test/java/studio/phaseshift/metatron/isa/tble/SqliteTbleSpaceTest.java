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
import org.junit.jupiter.params.provider.MethodSource;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.sql.Connection;
import java.sql.Statement;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Test suite for tbleSpace with SQLite database.
 * Extends AbstractTbleSpaceTest to inherit all common database tests.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SqliteTbleSpaceTest extends AbstractTbleSpaceTest {

    private static final String DB_PATH = "target/test-tabledb-space.db";

    public SqliteTbleSpaceTest() {
        super(new SqliteDatabaseConfig(DB_PATH));
    }

    @BeforeAll
    public static void setupSqliteDatabase() throws Exception {
        // Initialize the static config before calling setupDatabase
        staticDbConfig = new SqliteDatabaseConfig(DB_PATH);
        setupDatabase();
    }

    @AfterAll
    public static void cleanupSqliteDatabase() throws Exception {
        cleanupDatabase();
    }

    // All common tests are inherited from AbstractTbleSpaceTest
    // Add SQLite-specific tests below if needed

    // SQLite-specific test - not inherited by other databases
    @Test
    public void testTableMapping() throws Exception {
        LOG.info("Testing table mapping feature");

        // Create a test table with some data directly in the database
        try (Connection conn = staticDbConfig.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP TABLE IF EXISTS users");
            stmt.executeUpdate("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, age INTEGER)");
            stmt.executeUpdate("INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30)");
            stmt.executeUpdate("INSERT INTO users (id, name, age) VALUES (2, 'Bob', 25)");
            stmt.executeUpdate("INSERT INTO users (id, name, age) VALUES (3, 'Charlie', 35)");
        }

        // Create a new space instance to pick up the new table
        final tbleSpace testSpace = tbleSpace.of(
                rec(
                        uri(PATTERN), uri("db:#"),
                        uri(HOST), uri(staticDbConfig.getJdbcHost()),
                        uri(DRIVER), uri(staticDbConfig.getDriverClass()),
                        uri(ROUTE), rec(uri("db:"), uri("")),
                        uri(TABLE), lst()
                ).jvm(),
                f("/sys/space/tabledb/test2")
        );

        try {
            // Check if table was discovered
            if (testSpace.existingTableSchema != null) {
                LOG.info("Discovered tables: {}", testSpace.existingTableSchema.getTableNames());
            } else {
                LOG.warn("ExistingTableSchema is null!");
            }

            // Use Router.readFromSpace() to test table mapping
            final Obj row1 = Router.readFromSpace(f("db:users/1"));
            assertFalse(row1.isNoObj(), "Row 1 should not be noobj");
            assertTrue(row1.isRec(), "Row 1 should be a record");
            final Rec row1Rec = row1.asRec();
            assertEquals(str("Alice"), row1Rec.at(uri(NAME)), "Name should be Alice");
            assertEquals(jnt(30), row1Rec.at(uri("age")), "Age should be 30");

            // Read all rows using pattern
            final Obj allRows = Router.readFromSpace(f("db:users/+"));
            assertFalse(allRows.isNoObj(), "Should return results");
        } finally {
            testSpace.close();

            // Clean up - ensure this happens even if test fails
            try (Connection conn = staticDbConfig.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DROP TABLE IF EXISTS users");
            }
        }
    }

    /**
     * Test that TypedKeyValueSchema preserves types correctly (isomorphic mapping).
     * This verifies we're not losing type information through JSON conversion.
     * SQLite-specific test - uses hardcoded SQLite path.
     */
    @ParameterizedTest(name = "[{index}] Type preservation: {0}")
    @MethodSource("provideTypedStorageTestCases")
    public void testTypedStoragePreservation(String description, String uri, Obj writeValue, Obj expectedValue) throws Exception {
        // SQLite-specific: hardcoded path
        final tbleSpace testSpace = tbleSpace.of(
                rec(
                        uri(PATTERN), uri("/tble/#"),
                        uri(HOST), uri("sqlite:target/test-typed-storage.db"),
                        uri(DRIVER), uri("org.sqlite.JDBC"),
                        uri(TABLE), lst(),
                        uri(ROUTE), rec(uri("/tble/"), uri(""))
                ).jvm(),
                f("/sys/space/tble/typed")
        );

        try {
            // Write the value
            Router.writeToSpace(f(uri), writeValue);

            // Read it back
            final Obj actualValue = Router.readFromSpace(f(uri));

            // Verify exact type preservation
            assertEquals(expectedValue, actualValue, description);
            assertEquals(expectedValue.getClass(), actualValue.getClass(),
                    "Type class should be preserved: " + description);
        } finally {
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }
    }

    private static Stream<Arguments> provideTypedStorageTestCases() {
        return Stream.of(
                // Primitive types should be stored natively, not as JSON
                Arguments.of("Boolean true", "/tble/test/bool1", bool(true), bool(true)),
                Arguments.of("Boolean false", "/tble/test/bool2", bool(false), bool(false)),
                Arguments.of("Integer zero", "/tble/test/int1", jnt(0), jnt(0)),
                Arguments.of("Integer positive", "/tble/test/int2", jnt(42), jnt(42)),
                Arguments.of("Integer negative", "/tble/test/int3", jnt(-999), jnt(-999)),
                Arguments.of("Real zero", "/tble/test/real1", real(0.0), real(0.0)),
                Arguments.of("Real positive", "/tble/test/real2", real(3.14159), real(3.14159)),
                Arguments.of("String empty", "/tble/test/str1", str(""), str("")),
                Arguments.of("String simple", "/tble/test/str2", str("hello"), str("hello")),

                // Complex types should use ObjmtronSerializer
                Arguments.of("Record", "/tble/test/rec1",
                        rec(uri(NAME), str("Alice"), uri("age"), jnt(30)),
                        rec(uri(NAME), str("Alice"), uri("age"), jnt(30))),
                Arguments.of("List", "/tble/test/lst1",
                        lst(jnt(1), jnt(2), jnt(3)),
                        lst(jnt(1), jnt(2), jnt(3)))
        );
    }

    /**
     * Test that poly unrolling works for existing table schemas.
     * SQLite-specific test.
     */
    @Test
    @Disabled
    public void testPolyUnrollingExistingTable() throws Exception {
        // Create test database with users table
        try (final Connection conn = staticDbConfig.getConnection();
             final Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP TABLE IF EXISTS users");
            stmt.executeUpdate("""
                               CREATE TABLE users (
                                   id INTEGER PRIMARY KEY,
                                   name TEXT NOT NULL,
                                   age INTEGER,
                                   salary REAL,
                                   active BOOLEAN,
                                   email TEXT
                               )
                               """);
            stmt.executeUpdate("INSERT INTO users VALUES (1, 'Alice', 30, 75000.50, 1, 'alice@example.com')");
            stmt.executeUpdate("INSERT INTO users VALUES (2, 'Bob', 25, 60000.00, 1, 'bob@example.com')");
            stmt.executeUpdate("INSERT INTO users VALUES (3, 'Charlie', 35, 85000.75, 0, 'charlie@example.com')");
        }

        // Create space with table mapping
        final tbleSpace testSpace = tbleSpace.of(
                rec(
                        uri(PATTERN), uri("db:#"),
                        uri(HOST), uri(staticDbConfig.getJdbcHost()),
                        uri(DRIVER), uri(staticDbConfig.getDriverClass()),
                        uri(ROUTE), rec(uri("db:"), uri("")),
                        uri(TABLE), lst()
                ).jvm(),
                f("/sys/space/tble/polytest")
        );

        try {
            // Read the entire row first to verify it's a Record
            final Obj entireRow = Router.readFromSpace(f("db:users/1"));
            LOG.info("Read entire row: {} (type: {})", entireRow, entireRow.getClass().getSimpleName());
            assertTrue(entireRow.isRec(), "Should return a Record for the entire row");

            // Now read individual fields using poly unrolling
            final Obj nameField = Router.readFromSpace(f("db:users/1/name"));
            assertEquals(str("Alice"), nameField, "Should return just the name field value");

            final Obj ageField = Router.readFromSpace(f("db:users/1/age"));
            assertEquals(jnt(30), ageField, "Should return just the age field value");

            final Obj salaryField = Router.readFromSpace(f("db:users/1/salary"));
            assertEquals(real(75000.50), salaryField, "Should return just the salary field value");

            final Obj activeField = Router.readFromSpace(f("db:users/1/active"));
            try {
                assertEquals(bool(true), activeField, "Should return just the active field value");
            } catch (AssertionError e) {
                assertEquals(jnt(1), activeField, "Should return just the active field value");
            }

            final Obj emailField = Router.readFromSpace(f("db:users/1/email"));
            assertEquals(str("alice@example.com"), emailField, "Should return just the email field value");
        } finally {
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();

            // Clean up database
            try (final Connection conn = staticDbConfig.getConnection();
                 final Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DROP TABLE IF EXISTS users");
            }
        }
    }

    /**
     * Test that poly unrolling works for key-value schemas (TypedKeyValueSchema).
     * This test uses the main test space, so it works for all databases.
     */
    @Test
    @Disabled
    public void testPolyUnrollingKeyValueSchema() throws Exception {
        // Store a Record in the key-value schema
        final Obj testRecord = rec(
                uri(NAME), str("Bob"),
                uri("age"), jnt(25),
                uri("city"), str("New York")
        );
        Router.writeToSpace(f("tble:person/123"), testRecord);

        // Read the entire record first
        final Obj entireRecord = Router.readFromSpace(f("tble:person/123"));
        assertEquals(testRecord, entireRecord, "Should return the entire record");

        // Now read individual fields using poly unrolling
        final Obj nameField = Router.readFromSpace(f("tble:person/123/name"));
        assertEquals(str("Bob"), nameField, "Should return just the name field value");

        final Obj ageField = Router.readFromSpace(f("tble:person/123/age"));
        assertEquals(jnt(25), ageField, "Should return just the age field value");

        final Obj cityField = Router.readFromSpace(f("tble:person/123/city"));
        assertEquals(str("New York"), cityField, "Should return just the city field value");
    }

    /**
     * Test that poly unrolling works with nested Records.
     */
    @Test
    @Disabled
    public void testPolyUnrollingNestedRecords() throws Exception {
        // Store a nested Record
        final Obj nestedRecord = rec(
                uri(USER), rec(
                        uri(NAME), str("Charlie"),
                        uri("age"), jnt(35)
                ),
                uri(STATUS), str("active")
        );
        Router.writeToSpace( f("tble:data/789"), nestedRecord);

        // Access nested field
        final Obj userName = Router.readFromSpace(f("tble:data/789/user/name"));
        assertEquals(str("Charlie"), userName, "Should return nested field value");

        final Obj userAge = Router.readFromSpace(f("tble:data/789/user/age"));
        assertEquals(jnt(35), userAge, "Should return nested field value");

        final Obj status = Router.readFromSpace(f("tble:data/789/status"));
        assertEquals(str("active"), status, "Should return top-level field value");
    }
}
