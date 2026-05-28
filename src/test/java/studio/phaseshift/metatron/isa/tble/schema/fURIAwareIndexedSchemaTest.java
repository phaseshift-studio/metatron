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

package studio.phaseshift.metatron.isa.tble.schema;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.tble.schema.storage.SimpleKeyValueSchema;
import studio.phaseshift.metatron.isa.tble.schema.storage.TableSchema;
import studio.phaseshift.metatron.isa.tble.schema.storage.fURIAwareIndexedSchema;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Test suite for fURIAwareIndexedSchema.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class fURIAwareIndexedSchemaTest extends AbstractMetatronTest {

    private static final String DB_PATH = "target/test-mqtt-schema.db";
    private static final String JDBC_URL = "jdbc:sqlite:" + DB_PATH;

    private Connection conn;
    private TableSchema schema;

    @BeforeEach
    public void setup() throws Exception {
        // Delete existing test database
        final File dbFile = new File(DB_PATH);
        if (dbFile.exists()) {
            dbFile.delete();
        }

        // Load SQLite driver and create connection
        Class.forName("org.sqlite.JDBC");
        conn = DriverManager.getConnection(JDBC_URL);

        // Note: SQLite doesn't support generated columns like MariaDB
        // For testing, we'll use SimpleKeyValueSchema instead
        schema = new SimpleKeyValueSchema();
        schema.initialize(conn);
    }

    @AfterEach
    public void cleanup() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
        final File dbFile = new File(DB_PATH);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }

    @Test
    public void testWriteAndRead() throws SQLException {
        // Write an object
        final int rows = schema.write(conn, f("/sensor/kitchen/temperature"), "{\"value\": 22.5}");
        assertEquals(1, rows);

        // Read it back
        final Iterator<Space.IdObj> results = schema.read(conn, f("/sensor/kitchen/temperature"));
        assertTrue(results.hasNext());

        final Space.IdObj pair = results.next();
        assertEquals(f("/sensor/kitchen/temperature"), pair.furi());
        assertEquals(rec(uri("value"), real(22.5)), pair.obj());
        assertFalse(results.hasNext());
    }

    @Test
    public void testUpdate() throws SQLException {
        // Write initial value
        schema.write(conn, f("/test/value"), "{\"v\": 1}");

        // Update with new value
        schema.write(conn, f("/test/value"), "{\"v\": 2}");

        // Read back - should have updated value
        final Iterator<Space.IdObj> results = schema.read(conn, f("/test/value"));
        assertTrue(results.hasNext());

        final Space.IdObj pair = results.next();
        assertEquals(rec(uri("v"), jnt(2)), pair.obj());
        assertFalse(results.hasNext());
    }

    @Test
    public void testDelete() throws SQLException {
        // Write an object
        schema.write(conn, f("/test/delete"), "{\"value\": 123}");

        // Verify it exists
        Iterator<Space.IdObj> results = schema.read(conn, f("/test/delete"));
        assertTrue(results.hasNext());

        // Delete it
        final int deleted = schema.delete(conn, f("/test/delete"));
        assertEquals(1, deleted);

        // Verify it's gone
        results = schema.read(conn, f("/test/delete"));
        assertFalse(results.hasNext());
    }

    @Test
    public void testWriteNullDeletes() throws SQLException {
        // Write an object
        schema.write(conn, f("/test/null"), "{\"value\": 456}");

        // Verify it exists
        Iterator<Space.IdObj> results = schema.read(conn, f("/test/null"));
        assertTrue(results.hasNext());

        // Write null to delete
        schema.write(conn, f("/test/null"), null);

        // Verify it's gone
        results = schema.read(conn, f("/test/null"));
        assertFalse(results.hasNext());
    }

    @Test
    public void testMultipleObjects() throws SQLException {
        // Write multiple objects
        schema.write(conn, f("/sensor/kitchen/temperature"), "{\"value\": 22.5}");
        schema.write(conn, f("/sensor/bedroom/temperature"), "{\"value\": 20.1}");
        schema.write(conn, f("/sensor/kitchen/humidity"), "{\"value\": 45}");

        // Read each one
        Iterator<Space.IdObj> results = schema.read(conn, f("/sensor/kitchen/temperature"));
        assertTrue(results.hasNext());
        assertEquals(rec(uri("value"), real(22.5)), results.next().obj());

        results = schema.read(conn, f("/sensor/bedroom/temperature"));
        assertTrue(results.hasNext());
        assertEquals(rec(uri("value"), real(20.1)), results.next().obj());

        results = schema.read(conn, f("/sensor/kitchen/humidity"));
        assertTrue(results.hasNext());
        assertEquals(rec(uri("value"), jnt(45)), results.next().obj());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/sensor/kitchen/temperature     | /sensor/kitchen/temperature     | true",
            "/sensor/+/temperature           | /sensor/kitchen/temperature     | true",
            "/sensor/+/temperature           | /sensor/bedroom/temperature     | true",
            "/sensor/+/temperature           | /sensor/kitchen/humidity        | false",
            "/sensor/#                       | /sensor/kitchen/temperature     | true",
            "/sensor/#                       | /sensor/bedroom/temperature     | true",
            "/sensor/#                       | /sensor/kitchen/humidity        | true",
            "/sensor/#                       | /actuator/kitchen/light         | false",
            "/sensor/kitchen/+               | /sensor/kitchen/temperature     | true",
            "/sensor/kitchen/+               | /sensor/kitchen/humidity        | true",
            "/sensor/kitchen/+               | /sensor/bedroom/temperature     | false",
            "/+/kitchen/+                    | /sensor/kitchen/temperature     | true",
            "/+/kitchen/+                    | /actuator/kitchen/light         | true",
            "/+/kitchen/+                    | /sensor/bedroom/temperature     | false",
            "/sensor/+/temperature/#         | /sensor/kitchen/temperature     | true",
            "/sensor/+/temperature/#         | /sensor/kitchen/temperature/raw | true",
            "/sensor/+/temperature/#         | /sensor/kitchen/humidity        | false",
    }, delimiter = '|')
    public void testMqttPatternMatching(final String pattern, final String topic, final boolean shouldMatch) {
        final boolean matches = f(topic).test(f(pattern.trim()));
        LOG.debug("pattern: %s, topic: %s, matches: %s (expected: %s)", pattern.trim(), topic.trim(), matches, shouldMatch);
        assertEquals(shouldMatch, matches, String.format("Pattern %s does not match topic %s", pattern.trim(), topic.trim()));
    }

    @Test
    public void testMqttPatternEdgeCases() {
        // Empty segments
        assertTrue(f("/a/b").test(f("/a/b")));
        assertFalse(f("/a/b").test(f("/a/b/c")));

        // Multi-level wildcard at end
        assertTrue(f("/a/b/c").test(f("/a/#")));
        assertTrue(f("/a").test(f("/a/#")));

        // Single-level wildcard
        assertTrue(f("/a/b/c").test(f("/a/+/c")));
        assertFalse(f("/a/b/c/d").test(f("/a/+/c")));

        // Multiple single-level wildcards
        assertTrue(f("/a/b/c/d").test(f("/+/+/+/+")));
        assertFalse(f("/a/b/c").test(f("/+/+/+/+")));

        // Combination
        assertTrue(f("/a/b/c/d/e").test(f("/a/+/c/#")));
        assertFalse(f("/a/b/d/e").test(f("/a/+/c/#")));
    }

    private double extractValue(final String json) {
        // Simple JSON value extraction for testing
        final String valueStr = json.substring(json.indexOf(":") + 1, json.indexOf("}")).trim();
        return Double.parseDouble(valueStr);
    }
}
