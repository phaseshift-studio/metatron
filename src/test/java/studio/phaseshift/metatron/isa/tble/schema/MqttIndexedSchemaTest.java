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

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.f;

/**
 * Test suite for MqttIndexedSchema.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MqttIndexedSchemaTest extends AbstractMetatronTest {

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
        // For testing, we'll use SimpleSchema instead
        schema = new SimpleSchema();
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
        final Iterator<TableSchema.FuriObjPair> results = schema.read(conn, f("/sensor/kitchen/temperature"));
        assertTrue(results.hasNext());

        final TableSchema.FuriObjPair pair = results.next();
        assertEquals("/sensor/kitchen/temperature", pair.furi().toString());
        assertEquals("{\"value\": 22.5}", pair.objJson());
        assertFalse(results.hasNext());
    }

    @Test
    public void testUpdate() throws SQLException {
        // Write initial value
        schema.write(conn, f("/test/value"), "{\"v\": 1}");

        // Update with new value
        schema.write(conn, f("/test/value"), "{\"v\": 2}");

        // Read back - should have updated value
        final Iterator<TableSchema.FuriObjPair> results = schema.read(conn, f("/test/value"));
        assertTrue(results.hasNext());

        final TableSchema.FuriObjPair pair = results.next();
        assertEquals("{\"v\": 2}", pair.objJson());
        assertFalse(results.hasNext());
    }

    @Test
    public void testDelete() throws SQLException {
        // Write an object
        schema.write(conn, f("/test/delete"), "{\"value\": 123}");

        // Verify it exists
        Iterator<TableSchema.FuriObjPair> results = schema.read(conn, f("/test/delete"));
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
        Iterator<TableSchema.FuriObjPair> results = schema.read(conn, f("/test/null"));
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
        Iterator<TableSchema.FuriObjPair> results = schema.read(conn, f("/sensor/kitchen/temperature"));
        assertTrue(results.hasNext());
        assertEquals(22.5, extractValue(results.next().objJson()));

        results = schema.read(conn, f("/sensor/bedroom/temperature"));
        assertTrue(results.hasNext());
        assertEquals(20.1, extractValue(results.next().objJson()));

        results = schema.read(conn, f("/sensor/kitchen/humidity"));
        assertTrue(results.hasNext());
        assertEquals(45.0, extractValue(results.next().objJson()));
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
        final boolean matches = MqttIndexedSchema.matchesMqttPattern(topic.trim(), pattern.trim());
        LOG.debug("pattern: %s, topic: %s, matches: %s (expected: %s)", pattern.trim(), topic.trim(), matches, shouldMatch);
        assertEquals(shouldMatch, matches);
    }

    @Test
    public void testMqttPatternEdgeCases() {
        // Empty segments
        assertTrue(MqttIndexedSchema.matchesMqttPattern("/a/b", "/a/b"));
        assertFalse(MqttIndexedSchema.matchesMqttPattern("/a/b", "/a/b/c"));

        // Multi-level wildcard at end
        assertTrue(MqttIndexedSchema.matchesMqttPattern("/a/b/c", "/a/#"));
        assertTrue(MqttIndexedSchema.matchesMqttPattern("/a", "/a/#"));

        // Single-level wildcard
        assertTrue(MqttIndexedSchema.matchesMqttPattern("/a/b/c", "/a/+/c"));
        assertFalse(MqttIndexedSchema.matchesMqttPattern("/a/b/c/d", "/a/+/c"));

        // Multiple single-level wildcards
        assertTrue(MqttIndexedSchema.matchesMqttPattern("/a/b/c/d", "/+/+/+/+"));
        assertFalse(MqttIndexedSchema.matchesMqttPattern("/a/b/c", "/+/+/+/+"));

        // Combination
        assertTrue(MqttIndexedSchema.matchesMqttPattern("/a/b/c/d/e", "/a/+/c/#"));
        assertFalse(MqttIndexedSchema.matchesMqttPattern("/a/b/d/e", "/a/+/c/#"));
    }

    private double extractValue(final String json) {
        // Simple JSON value extraction for testing
        final String valueStr = json.substring(json.indexOf(":") + 1, json.indexOf("}")).trim();
        return Double.parseDouble(valueStr);
    }
}
