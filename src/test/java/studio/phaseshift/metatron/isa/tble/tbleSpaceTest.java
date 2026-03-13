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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.m.type.Bool;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Test suite for tbleSpace with MQTT-indexed schema.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class tbleSpaceTest extends AbstractSpaceTest {

    private static final String DB_PATH = "target/test-tble-space.db";
    private static final fURI SPACE_VID = f("/sys/space/tble/test");

    public tbleSpaceTest() {
        super( () -> tbleSpace.of(
                rec(
                        uri(PATTERN), uri("/tble/#"),
                        uri(HOST), uri("sqlite:" + DB_PATH),
                        uri(DRIVER), uri("org.sqlite.JDBC")
                ).jvm(),
                SPACE_VID
        ));
    }

    @BeforeAll
    public static void setupDatabase() throws Exception {
        // Load SQLite JDBC driver
        Class.forName("org.sqlite.JDBC");

        // Delete existing test database
        final File dbFile = new File(DB_PATH);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }

    @AfterAll
    public static void cleanupDatabase() {
        final File dbFile = new File(DB_PATH);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }

    @Test
    public void testTableMapping() throws Exception {
        LOG.info("Testing table mapping feature");

        // Create a test table with some data directly in the database
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, age INTEGER)");
            stmt.executeUpdate("INSERT INTO users (id, name, age) VALUES (1, 'Alice', 30)");
            stmt.executeUpdate("INSERT INTO users (id, name, age) VALUES (2, 'Bob', 25)");
            stmt.executeUpdate("INSERT INTO users (id, name, age) VALUES (3, 'Charlie', 35)");
        }

        // Create a new space instance to pick up the new table
        final tbleSpace testSpace = tbleSpace.of(
                rec(
                        uri(PATTERN), uri("/t/#"),
                        uri(HOST), uri("sqlite:" + DB_PATH),
                        uri(DRIVER), uri("org.sqlite.JDBC"),
                        uri(ROUTE), rec(uri(""), uri("")),
                        uri(TABLE), lst()
                ).jvm(),
                f("/sys/space/tble/test2")
        );

        try {
            // Check if table was discovered
            if (testSpace.existingTableSchema != null) {
                LOG.info("Discovered tables: {}", testSpace.existingTableSchema.getTableNames());
            } else {
                LOG.warn("ExistingTableSchema is null!");
            }

            // Use directReader to test table mapping (avoids poly resolution)
            // Note: directReader receives the rewritten path (without /t/ prefix)
            final var row1Iter = testSpace.directReader().apply(f("/users/1"));
            assertTrue(row1Iter.hasNext(), "Should read row 1");
            final var row1 = row1Iter.next();
            LOG.info("Read row 1: {}", row1.obj());
            assertFalse(row1.obj().isNoObj(), "Row 1 should not be noobj");

            // Read all rows
            final var allRowsIter = testSpace.directReader().apply(f("/users/+"));
            final var allRows = new java.util.ArrayList<>();
            allRowsIter.forEachRemaining(allRows::add);
            LOG.info("Read {} rows", allRows.size());
            assertEquals(3, allRows.size(), "Should read 3 rows");
        } finally {
            testSpace.close();
        }

        // Clean up
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP TABLE users");
        }
    }

    @Test
    public void testComprehensiveTableOperations() throws Exception {
        LOG.info("Testing comprehensive table operations with multiple data types");

        // Create synthetic dataset with two tables and various data types
        try (final Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             final Statement stmt = conn.createStatement()) {

            // Table 1: users - testing int, text, real, boolean
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

            // Insert 10 rows into users table
            stmt.executeUpdate("INSERT INTO users VALUES (1, 'Alice', 30, 75000.50, 1, 'alice@example.com')");
            stmt.executeUpdate("INSERT INTO users VALUES (2, 'Bob', 25, 60000.00, 1, 'bob@example.com')");
            stmt.executeUpdate("INSERT INTO users VALUES (3, 'Charlie', 35, 85000.75, 0, 'charlie@example.com')");
            stmt.executeUpdate("INSERT INTO users VALUES (4, 'Diana', 28, 70000.25, 1, 'diana@example.com')");
            stmt.executeUpdate("INSERT INTO users VALUES (5, 'Eve', 42, 95000.00, 1, 'eve@example.com')");
            stmt.executeUpdate("INSERT INTO users VALUES (6, 'Frank', 31, 68000.50, 0, 'frank@example.com')");
            stmt.executeUpdate("INSERT INTO users VALUES (7, 'Grace', 27, 72000.00, 1, 'grace@example.com')");
            stmt.executeUpdate("INSERT INTO users VALUES (8, 'Henry', 38, 88000.75, 1, 'henry@example.com')");
            stmt.executeUpdate("INSERT INTO users VALUES (9, 'Iris', 33, 79000.25, 0, 'iris@example.com')");
            stmt.executeUpdate("INSERT INTO users VALUES (10, 'Jack', 29, 65000.00, 1, 'jack@example.com')");

            // Table 2: products - testing different column types
            stmt.executeUpdate("""
                CREATE TABLE products (
                    product_id INTEGER PRIMARY KEY,
                    product_name TEXT NOT NULL,
                    price REAL,
                    in_stock BOOLEAN,
                    quantity INTEGER,
                    category TEXT
                )
                """);

            // Insert 10 rows into products table
            stmt.executeUpdate("INSERT INTO products VALUES (101, 'Laptop', 1299.99, 1, 15, 'Electronics')");
            stmt.executeUpdate("INSERT INTO products VALUES (102, 'Mouse', 29.99, 1, 50, 'Electronics')");
            stmt.executeUpdate("INSERT INTO products VALUES (103, 'Keyboard', 79.99, 1, 30, 'Electronics')");
            stmt.executeUpdate("INSERT INTO products VALUES (1, 'Monitor', 399.99, 0, 0, 'Electronics')");
            stmt.executeUpdate("INSERT INTO products VALUES (105, 'Desk Chair', 249.99, 1, 20, 'Furniture')");
            stmt.executeUpdate("INSERT INTO products VALUES (106, 'Desk', 499.99, 1, 10, 'Furniture')");
            stmt.executeUpdate("INSERT INTO products VALUES (107, 'Notebook', 4.99, 1, 100, 'Stationery')");
            stmt.executeUpdate("INSERT INTO products VALUES (108, 'Pen Set', 12.99, 1, 75, 'Stationery')");
            stmt.executeUpdate("INSERT INTO products VALUES (109, 'Webcam', 89.99, 0, 0, 'Electronics')");
            stmt.executeUpdate("INSERT INTO products VALUES (110, 'Headphones', 149.99, 1, 25, 'Electronics')");
        }

        // Create space instance with table mapping enabled - will be picked up by Router
        final tbleSpace testSpace = tbleSpace.of(
                rec(
                        uri(PATTERN), uri("db:#"),
                        uri(HOST), uri("sqlite:" + DB_PATH),
                        uri(DRIVER), uri("org.sqlite.JDBC"),
                        uri(ROUTE), rec(uri("db:"), uri("/tble/")),
                        uri(TABLE), lst()
                ).jvm(),
                f("/sys/space/tble/comprehensive")
        );

        try {
            LOG.info("Discovered tables: %s", testSpace.existingTableSchema.getTableNames());

            // TEST 1: Read specific rows as records using Router
            LOG.info("TEST 1: Reading specific rows");
            final Obj user1 = Router.readFromSpace(f("db:users/1"));
            LOG.info("User 1: %s", user1);
            assertTrue(user1.isRec(), "Should return a record");
            final Rec user1Rec = user1.asRec();
            assertEquals(str("Alice"), user1Rec.at(uri("name")), "Name should be Alice");
            assertEquals(jnt(30), user1Rec.at(uri("age")), "Age should be 30");

            final Obj product101 = Router.readFromSpace(f("db:products/101"));
            LOG.info("Product 101: %s", product101);
            assertTrue(product101.isRec(), "Should return a record");
            final Rec product101Rec = product101.asRec();
            assertEquals(str("Laptop"), product101Rec.at(uri("product_name")), "Product name should be Laptop");

            // TEST 2: Write entire row (update existing)
            LOG.info("TEST 2: Updating entire row");
            Router.writeToSpace(f("db:users/1"), rec(
                    uri("name"), str("Alice Smith"),
                    uri("age"), jnt(31),
                    uri("salary"), real(80000.00),
                    uri("active"), bool(true),
                    uri("email"), str("alice.smith@example.com")
            ));

            final Obj updatedUser1 = Router.readFromSpace(f("db:users/1"));
            LOG.info("Updated User 1: %s", updatedUser1);
            final Rec updatedUser1Rec = updatedUser1.asRec();
            assertEquals(str("Alice Smith"), updatedUser1Rec.at(uri("name")), "Name should be updated");
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

            /*// TEST 4: Insert new row
            LOG.info("TEST 4: Inserting new row");
            Router.writeToSpace(f("db:users/11"), rec(
                    uri("name"), str("Karen"),
                    uri("age"), jnt(34),
                    uri("salary"), real(82000.00),
                    uri("active"), bool(true),
                    uri("email"), str("karen@example.com")
            ));

            final Obj newUser = Router.readFromSpace(f("db:users/11"));
            LOG.info("New User 11: %s", newUser);
            assertTrue(newUser.isRec(), "Should return a record");
            assertEquals(str("Karen"), newUser.asRec().at(uri("name")), "Name should be Karen");*/

            // TEST 5: Update product with different types
            LOG.info("TEST 5: Updating product fields");
            Router.writeToSpace(f("db:products/1/in_stock"), bool(true));
            Router.writeToSpace(f("db:products/1/quantity"), jnt(5));

            final Obj updatedProduct = Router.readFromSpace(f("db:products/1"));
            LOG.info("Updated Product 1: %s", updatedProduct);
            final Rec updatedProductRec = updatedProduct.asRec();
            assertEquals(bool(true), updatedProductRec.at(uri("in_stock")), "Should be in stock");
            assertEquals(jnt(5), updatedProductRec.at(uri("quantity")), "Quantity should be 5");

            // TEST 6: Verify data in database directly
            LOG.info("TEST 6: Verifying data in database");
            try (final Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
                 final Statement stmt = conn.createStatement();
                 final ResultSet rs = stmt.executeQuery("SELECT name, age FROM users WHERE id = 1")) {
                if (rs.next()) {
                    assertEquals("Alice Smith", rs.getString("name"), "DB should have updated name");
                    assertEquals(31, rs.getInt("age"), "DB should have updated age");
                }
            }

            LOG.info("All comprehensive tests passed!");

        } finally {
            Router.global().removeSpace(testSpace.vid());
            testSpace.close();
        }

        // Clean up
        try (final Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             final Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP TABLE IF EXISTS users");
            stmt.executeUpdate("DROP TABLE IF EXISTS products");
        }
    }
}
