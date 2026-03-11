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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.util.MTronException;

import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * MQTT-indexed schema using MariaDB/MySQL generated columns for efficient pattern matching.
 * Decomposes fURIs into path segments (seg1-seg5) with indexes for fast MQTT-style queries.
 * <p>
 * Supports MQTT wildcards:
 * - '+' matches exactly one path segment
 * - '#' matches zero or more path segments (must be last segment)
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MqttIndexedSchema implements TableSchema {

    private static final int MAX_SEGMENTS = 5;
    private static final String TABLE_NAME = "objs";

    @Override
    public void initialize(final Connection conn) throws SQLException {
        final String createTable = """
                CREATE TABLE IF NOT EXISTS objs (
                    furi VARCHAR(512) NOT NULL PRIMARY KEY,
                    obj TEXT NOT NULL,
                    -- Virtual generated columns for path segments
                    seg1 VARCHAR(128) AS (
                        CASE
                            WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 2), '/', -1) = '' THEN NULL
                            ELSE SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 2), '/', -1)
                        END
                    ) VIRTUAL,
                    seg2 VARCHAR(128) AS (
                        CASE
                            WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 3), '/', -1) = '' THEN NULL
                            WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 3), '/', -1) = SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 2), '/', -1) THEN NULL
                            ELSE SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 3), '/', -1)
                        END
                    ) VIRTUAL,
                    seg3 VARCHAR(128) AS (
                        CASE
                            WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 4), '/', -1) = '' THEN NULL
                            WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 4), '/', -1) = SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 3), '/', -1) THEN NULL
                            ELSE SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 4), '/', -1)
                        END
                    ) VIRTUAL,
                    seg4 VARCHAR(128) AS (
                        CASE
                            WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 5), '/', -1) = '' THEN NULL
                            WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 5), '/', -1) = SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 4), '/', -1) THEN NULL
                            ELSE SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 5), '/', -1)
                        END
                    ) VIRTUAL,
                    seg5 VARCHAR(128) AS (
                        CASE
                            WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 6), '/', -1) = '' THEN NULL
                            WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 6), '/', -1) = SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 5), '/', -1) THEN NULL
                            ELSE SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 6), '/', -1)
                        END
                    ) VIRTUAL,
                    -- Indexes on virtual columns for fast pattern matching
                    INDEX idx_seg1 (seg1),
                    INDEX idx_seg2 (seg2),
                    INDEX idx_seg3 (seg3),
                    INDEX idx_seg4 (seg4),
                    INDEX idx_seg5 (seg5),
                    -- Composite indexes for common multi-segment patterns
                    INDEX idx_seg1_seg2 (seg1, seg2),
                    INDEX idx_seg1_seg2_seg3 (seg1, seg2, seg3)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                """;

        try (final Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createTable);
        }
    }

    @Override
    public int write(final Connection conn, final fURI furi, final String objJson) throws SQLException {
        if (objJson == null || objJson.isEmpty()) {
            return delete(conn, furi);
        }

        final String sql = "INSERT INTO " + TABLE_NAME + " (furi, obj) VALUES (?, ?) " +
                           "ON DUPLICATE KEY UPDATE obj = VALUES(obj);";

        try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, furi.toString());
            stmt.setString(2, objJson);
            return stmt.executeUpdate();
        }
    }

    @Override
    public Iterator<FuriObjPair> read(final Connection conn, final fURI pattern) throws SQLException {
        final String patternStr = pattern.toString();

        // Check if this is an MQTT pattern
        if (pattern.hasPattern()) {
            return readMqttPattern(conn, pattern);
        }

        // Exact match query
        final String sql = "SELECT furi, obj FROM " + TABLE_NAME + " WHERE furi = ?;";
        final PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, patternStr);
        final ResultSet rs = stmt.executeQuery();

        final List<FuriObjPair> results = new ArrayList<>();
        while (rs.next()) {
            results.add(new FuriObjPair(f(rs.getString("furi")), rs.getString("obj")));
        }
        rs.close();
        stmt.close();

        return results.iterator();
    }

    /**
     * Read objects matching MQTT-style pattern using indexed segments.
     * Examples:
     * - /sensor/+/temperature -> matches /sensor/kitchen/temperature, /sensor/bedroom/temperature
     * - /sensor/# -> matches /sensor/kitchen, /sensor/kitchen/temperature, etc.
     * - /sensor/+/# -> matches /sensor/kitchen/temperature, /sensor/bedroom/humidity/current
     */
    private Iterator<FuriObjPair> readMqttPattern(final Connection conn, final fURI pattern) throws SQLException {
        final String patternStr = pattern.toString();

        // Build WHERE clause based on pattern segments
        final StringBuilder whereClause = new StringBuilder();
        final List<String> params = new ArrayList<>();
        boolean hasMultiLevelWildcard = false;
        int segmentIndex = 1; // Database columns are seg1, seg2, etc.

        for (int i = 0; i < Math.min(pattern.segmentLength(), MAX_SEGMENTS + 1); i++) {
            final String seg = pattern.asRelativeNode().path().get(i);

            if (seg.isEmpty()) {
                continue; // Skip empty segments (leading slash)
            }

            if (seg.equals("#")) {
                // Multi-level wildcard - matches everything from here on
                hasMultiLevelWildcard = true;
                break;
            } else if (seg.equals("+")) {
                // Single-level wildcard - segment must exist but can be anything
                if (whereClause.length() > 0) {
                    whereClause.append(" AND ");
                }
                whereClause.append("seg").append(segmentIndex).append(" IS NOT NULL");
                segmentIndex++;
            } else {
                // Exact segment match
                if (whereClause.length() > 0) {
                    whereClause.append(" AND ");
                }
                whereClause.append("seg").append(segmentIndex).append(" = ?");
                params.add(seg);
                segmentIndex++;
            }
        }

        // If no multi-level wildcard, ensure no extra segments exist
        if (!hasMultiLevelWildcard && segmentIndex <= MAX_SEGMENTS) {
            if (whereClause.length() > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append("seg").append(segmentIndex).append(" IS NULL");
        }

        final String sql = "SELECT furi, obj FROM " + TABLE_NAME +
                           (whereClause.length() > 0 ? " WHERE " + whereClause : "") + ";";

        final PreparedStatement stmt = conn.prepareStatement(sql);
        for (int i = 0; i < params.size(); i++) {
            stmt.setString(i + 1, params.get(i));
        }

        final ResultSet rs = stmt.executeQuery();
        final List<FuriObjPair> results = new ArrayList<>();

        while (rs.next()) {
            final String furiStr = rs.getString("furi");
            // Double-check pattern match (for patterns beyond MAX_SEGMENTS)
            if (matchesMqttPattern(furiStr, patternStr)) {
                results.add(new FuriObjPair(f(furiStr), rs.getString("obj")));
            }
        }

        rs.close();
        stmt.close();

        return results.iterator();
    }

    @Override
    public int delete(final Connection conn, final fURI furi) throws SQLException {
        final String sql = "DELETE FROM " + TABLE_NAME + " WHERE furi = ?;";
        try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, furi.toString());
            return stmt.executeUpdate();
        }
    }

    @Override
    public boolean supportsMqttPatterns() {
        return true;
    }

    @Override
    public String version() {
        return "1.0-mqtt";
    }

    /**
     * Check if a topic matches an MQTT pattern.
     * Supports + (single-level wildcard) and # (multi-level wildcard).
     *
     * @param topic   the topic to test
     * @param pattern the MQTT pattern
     * @return true if topic matches pattern
     */
    public static boolean matchesMqttPattern(final String topic, final String pattern) {
        if(Objects.equals(topic, pattern))
            return true;
        final fURI topicfURI = f(topic);
        final fURI patternfURI = f(pattern);

        int ti = 0, pi = 0;

        while (ti < topicfURI.pathLength() && pi < patternfURI.pathLength()) {
            final String patternSeg = patternfURI.path().get(pi);
            if (patternSeg.equals("#")) {
                // Multi-level wildcard matches everything remaining
                return true;
            } else if (patternSeg.equals("+")) {
                // Single-level wildcard matches one segment
                ti++;
                pi++;
            } else if (topicfURI.path().get(ti).equals(patternSeg)) {
                // Exact match
                ti++;
                pi++;
            } else {
                // No match
                return false;
            }
        }

        // Check if we consumed both topic and pattern
        // Special case: pattern ending with # can match shorter topics
        if (pi < patternfURI.pathLength() && patternfURI.path().get(pi).equals("#")) {
            return true;
        }

        return ti == topicfURI.pathLength() && pi == patternfURI.pathLength();
    }
}
