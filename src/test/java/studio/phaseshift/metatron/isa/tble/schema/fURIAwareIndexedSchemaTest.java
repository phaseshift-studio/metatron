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

package studio.phaseshift.metatron.isa.tble.schema;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.tble.schema.storage.SimpleKeyValueSchema;
import studio.phaseshift.metatron.isa.tble.schema.storage.TableSchema;
import studio.phaseshift.metatron.isa.tble.schema.storage.fURIAwareIndexedSchema;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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

    // =========================================================================
    //  MQTT-indexed pattern reads — the schema MariaDB/MySQL use
    // =========================================================================

    private static final int SEGMENT_COLUMNS = 7;

    /**
     * A pattern that descends PAST the row holding the value
     * ({@code kv/test/people/+/name}, where the rec lives at {@code kv/test/people/1}) must
     * return the stored parent rows, so the caller's Java-side {@code unrollPoly} can
     * decompose the rec. The read used to compare the post-wildcard literal against the
     * wrong {@code segN} column, and its ancestor fallback built its prefix with
     * {@code retractPattern()} — which only strips <em>trailing</em> wildcards, so the
     * literal tail stayed in the prefix. The parent rows never came back and the field read
     * collapsed to a single value on MariaDB/MySQL.
     * <p>
     * SQLite has no {@code SUBSTRING_INDEX}, so the generated columns this schema creates on
     * MariaDB/MySQL cannot be created here. The table is built by hand with the {@code segN}
     * values those generated columns hold, which is what makes the SQL testable on the host.
     */
    @Test
    public void testPatternDescendingPastStoredRowReturnsParents() throws SQLException {
        createMqttStore();
        for (int i = 1; i <= 4; i++)
            insertMqttRow("kv/test/people/" + i, "[name=>'Optimus Prime',meta=>[city=>'NYC']]");

        final TableSchema mqtt = new fURIAwareIndexedSchema();

        final List<fURI> descent = furis(mqtt.read(conn, f("kv/test/people/+/name")));
        assertEquals(4, descent.size(),
                "a descending pattern must return the parent rows for unrollPoly: " + descent);
        assertTrue(descent.stream().allMatch(u -> u.toString().startsWith("kv/test/people/")),
                "expected the stored parent rows, got: " + descent);

        // the non-descending fast path (segN equality) is unchanged
        assertEquals(4, furis(mqtt.read(conn, f("kv/test/people/+"))).size(),
                "a trailing wildcard should still match through the segN columns");
    }

    /** Builds {@code kv_store} the way {@link fURIAwareIndexedSchema#initialize} would, minus SUBSTRING_INDEX. */
    private void createMqttStore() throws SQLException {
        final StringBuilder ddl = new StringBuilder(
                "CREATE TABLE kv_store (furi VARCHAR(512) NOT NULL PRIMARY KEY, obj TEXT NOT NULL");
        for (int n = 1; n <= SEGMENT_COLUMNS; n++)
            ddl.append(", seg").append(n).append(" VARCHAR(128)");
        ddl.append(")");
        try (final Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP TABLE IF EXISTS kv_store");
            stmt.executeUpdate(ddl.toString());
        }
    }

    /**
     * Inserts a row with the {@code segN} values the generated columns derive from
     * {@code furi}: {@code segN = SUBSTRING_INDEX(SUBSTRING_INDEX(furi,'/',N+1),'/',-1)} —
     * element N (0-based), or the last element when the furi is shorter, with an empty
     * element as NULL. The furi is stored verbatim, as the space stores it (it routes the
     * scheme away before handing the path to the schema).
     */
    private void insertMqttRow(final String furi, final String obj) throws SQLException {
        final String[] parts = furi.split("/");
        final StringBuilder columns = new StringBuilder("furi, obj");
        final StringBuilder values = new StringBuilder("?, ?");
        final List<String> params = new ArrayList<>();
        params.add(furi);
        params.add(obj);
        for (int n = 1; n <= SEGMENT_COLUMNS; n++) {
            columns.append(", seg").append(n);
            values.append(", ?");
            final String element = parts[Math.min(n, parts.length - 1)];
            params.add(element.isEmpty() ? null : element);
        }
        try (final PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO kv_store (" + columns + ") VALUES (" + values + ")")) {
            for (int i = 0; i < params.size(); i++)
                stmt.setString(i + 1, params.get(i));
            stmt.executeUpdate();
        }
    }

    private static List<fURI> furis(final Iterator<Space.IdObj> rows) {
        final List<fURI> result = new ArrayList<>();
        rows.forEachRemaining(row -> result.add(row.furi()));
        return result;
    }

    private double extractValue(final String json) {
        // Simple JSON value extraction for testing
        final String valueStr = json.substring(json.indexOf(":") + 1, json.indexOf("}")).trim();
        return Double.parseDouble(valueStr);
    }
}
