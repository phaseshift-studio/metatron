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

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
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
        super(f("/tble"), () -> tbleSpace.of(
                rec(
                        uri(PATTERN), uri("/t/#"),
                        uri(HOST), uri("sqlite:" + DB_PATH),
                        uri(DRIVER), uri("org.sqlite.JDBC"),
                        uri(ROUTE), rec(uri(""), uri(""))
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
}
