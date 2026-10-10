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

package studio.phaseshift.metatron.isa.tble.schema.storage;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
public class fURIAwareIndexedSchema implements TableSchema {

    private static final int MAX_SEGMENTS = 7;
    private static final String TABLE_NAME = "kv_store";
    private static final ObjmtronSerializer SERIALIZER = ObjmtronSerializer.single();

    @Override
    public void initialize(final Connection conn) throws SQLException {
        final String createTable = """
                                   CREATE TABLE IF NOT EXISTS kv_store (
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
                                        seg6 VARCHAR(128) AS (
                                           CASE
                                               WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 7), '/', -1) = '' THEN NULL
                                               WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 7), '/', -1) = SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 6), '/', -1) THEN NULL
                                               ELSE SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 7), '/', -1)
                                           END
                                       ) VIRTUAL,
                                        seg7 VARCHAR(128) AS (
                                           CASE
                                               WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 8), '/', -1) = '' THEN NULL
                                               WHEN SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 8), '/', -1) = SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 7), '/', -1) THEN NULL
                                               ELSE SUBSTRING_INDEX(SUBSTRING_INDEX(furi, '/', 8), '/', -1)
                                           END
                                       ) VIRTUAL,
                                       -- Indexes on virtual columns for fast pattern matching
                                       INDEX idx_seg1 (seg1),
                                       INDEX idx_seg2 (seg2),
                                       INDEX idx_seg3 (seg3),
                                       INDEX idx_seg4 (seg4),
                                       INDEX idx_seg5 (seg5),
                                       INDEX idx_seg6 (seg6),
                                       INDEX idx_seg7 (seg7),
                                       -- Composite indexes for common multi-segment patterns
                                       INDEX idx_seg1_seg2 (seg1, seg2),
                                       INDEX idx_seg1_seg2_seg3 (seg1, seg2, seg3),
                                       INDEX idx_seg1_seg2_seg3_seg4 (seg1, seg2, seg3, seg4)
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
    public Iterator<Space.IdObj> read(final Connection conn, final fURI pattern) throws SQLException {
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

        final List<Space.IdObj> results = new ArrayList<>();
        while (rs.next()) {
            results.add(new Space.IdObj(f(rs.getString("furi")), SERIALIZER.read(rs.getString("obj"))));
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
     * <p>
     * A pattern that descends PAST the row holding the value (e.g. {@code kv/test/people/+/name},
     * where the rec is stored at {@code kv/test/people/1}) cannot be narrowed in SQL — the deeper
     * segments exist only inside the stored rec.  Those return every row at the concrete prefix and
     * let the caller's Java-side {@code unrollPoly} decompose them.
     */
    private Iterator<Space.IdObj> readMqttPattern(final Connection conn, final fURI pattern) throws SQLException {
        // Build WHERE clause based on pattern segments
        final StringBuilder whereClause = new StringBuilder();
        final List<String> params = new ArrayList<>();
        boolean hasMultiLevelWildcard = false;
        boolean hasWildcard = false;
        int segmentIndex = 1; // Database columns are seg1, seg2, etc.

        // DB generated columns use SUBSTRING_INDEX(furi, '/', N) where N = segmentIndex+1.
        // For seg1, N=2 extracts the SECOND slash-delimited element, skipping element 0
        // (the scheme+namespace prefix or leading empty for absolute URIs).
        // Therefore we start from path index 1 to align with DB seg1.
        final List<String> pathString = pattern.asRelativeNode().path();

        // A '+' wildcard followed by more segments (kv/test/people/+/name) is a DESCENT
        // past the row that stores the value: the whole rec lives at kv/test/people/1, so
        // the deeper segments are not DB segments at any fixed position and no segN
        // equality can express them.  The ancestor fallback below cannot either — it
        // builds its prefix with retractPattern(), which only strips TRAILING wildcards,
        // so the literal tail (…/+/name) stays in the prefix and the parent rows are never
        // returned; Java-level unrollPoly then has nothing to decompose.  Match the
        // concrete prefix instead and let collectResults/unrollPoly pick the real matches
        // — the same contract TypedKeyValueSchema honours by returning every row for a
        // pattern read.  It returns a superset, so correctness is unchanged.
        final int firstWildcard = firstWildcardIndex(pathString);
        if (firstWildcard > 0 && hasExactSegmentAfter(pathString, firstWildcard)) {
            final String concretePath = String.join("/", pathString.subList(0, firstWildcard));
            return readBelowPath(conn, concretePath);
        }

        for (int i = 1; i < Math.min(pathString.size(), MAX_SEGMENTS + 2); i++) {
            final String seg = pathString.get(i);

            if (seg.isEmpty()) {
                continue;
            }

            if (seg.equals("#")) {
                // Multi-level wildcard - matches everything from here on
                hasMultiLevelWildcard = true;
                hasWildcard = true;
                break;
            } else if (seg.equals("+")) {
                // Single-level wildcard — don't enforce IS NOT NULL in SQL.
                // The KV store stores whole polys at parent URIs (e.g. kv/test/a
                // contains [x=>1,y=>2,z=>3]), so sub-field paths (kv/test/a/x)
                // don't exist as separate DB rows.  Java-level unrollPoly
                // decomposes polys after the SQL returns the parent row.
                hasWildcard = true;
            } else {
                // Exact segment match
                if (!whereClause.isEmpty()) {
                    whereClause.append(" AND ");
                }
                whereClause.append("seg").append(segmentIndex).append(" = ?");
                params.add(seg);
                segmentIndex++;
            }
        }

        // Only enforce no-extra-segments when there are no wildcards.
        // Wildcards signal "match anything deeper" so we must not restrict
        // beyond the last exact segment.
        if (!hasMultiLevelWildcard && !hasWildcard && segmentIndex <= MAX_SEGMENTS) {
            if (!whereClause.isEmpty()) {
                whereClause.append(" AND ");
            }
            whereClause.append("seg").append(segmentIndex).append(" IS NULL");
        }

        // Ancestor rows. A pattern read can descend PAST the row that holds the value: the KV store
        // keeps a whole poly at its parent URI (kv/test/a holds [x=>1,y=>2]), so kv/test/a/x has no
        // row of its own. The '+' branch above documents that Java-level unrollPoly decomposes that
        // parent — which requires the parent row to come back from THIS query. The segment equality
        // cannot return it, because the parent has no segment for the deeper path, so match ancestors
        // of the concrete (non-wildcard) prefix explicitly. TypedKeyValueSchema returns every row for
        // a pattern read and therefore never needed this; without it a nested value (a lst inside the
        // stored rec) reads back as noobj on MySQL/MariaDB while working on PostgreSQL.
        // The OR defeats the seg indexes — the price of correctness for a key-value table.
        // The prefix test is spelled with LENGTH/SUBSTRING rather than CONCAT so it also holds on
        // SQLite, which fURIAwareIndexedSchemaTest runs this class against.
        final String concretePrefix = pattern.retractPattern().toString();
        final String sql = "SELECT furi, obj FROM " + TABLE_NAME +
                (whereClause.length() > 0
                        ? " WHERE (" + whereClause + ")"
                        + " OR (LENGTH(furi) < LENGTH(?)"
                        + " AND SUBSTRING(?, 1, LENGTH(furi)) = furi"
                        + " AND SUBSTRING(?, LENGTH(furi) + 1, 1) = '/')"
                        : "") + ";";

        final PreparedStatement stmt = conn.prepareStatement(sql);
        for (int i = 0; i < params.size(); i++) {
            stmt.setString(i + 1, params.get(i));
        }
        if (whereClause.length() > 0) {
            stmt.setString(params.size() + 1, concretePrefix);
            stmt.setString(params.size() + 2, concretePrefix);
            stmt.setString(params.size() + 3, concretePrefix);
        }

        final ResultSet rs = stmt.executeQuery();
        final List<Space.IdObj> results = new ArrayList<>();

        while (rs.next()) {
            final fURI furiStr = f(rs.getString("furi"));
            // Double-check pattern match (for patterns beyond MAX_SEGMENTS)
            //if (f(furiStr.pathString()).test(f(pattern.asNode().pathString()))) {
            results.add(Space.IdObj.of(furiStr, SERIALIZER.read(rs.getString("obj"))));
            //}
        }

        rs.close();
        stmt.close();

        return results.iterator();
    }

    /**
     * Index of the first single- or multi-level wildcard segment, or {@code -1} when the
     * pattern has none.  Index 0 is the namespace element (see the seg1 alignment note in
     * {@link #readMqttPattern}), so the scan starts at 1.
     */
    private static int firstWildcardIndex(final List<String> path) {
        for (int i = 1; i < path.size(); i++) {
            final String seg = path.get(i);
            if (seg.equals("+") || seg.equals("#"))
                return i;
        }
        return -1;
    }

    /**
     * Whether a concrete (non-wildcard, non-empty) segment follows the wildcard — i.e. the
     * pattern descends past the row that stores the value, which only Java-level
     * {@code unrollPoly} can resolve.
     */
    private static boolean hasExactSegmentAfter(final List<String> path, final int wildcardIndex) {
        for (int i = wildcardIndex + 1; i < path.size(); i++) {
            final String seg = path.get(i);
            if (!seg.isEmpty() && !seg.equals("+") && !seg.equals("#"))
                return true;
        }
        return false;
    }

    /**
     * Read the row at {@code path} and every row beneath it.  A descent pattern cannot be
     * narrowed in SQL, so this returns the superset the caller filters (and unrolls) in
     * Java; an empty path degenerates to every row.
     */
    private Iterator<Space.IdObj> readBelowPath(final Connection conn, final String path) throws SQLException {
        final boolean bounded = !path.isEmpty();
        final String sql = "SELECT furi, obj FROM " + TABLE_NAME
                + (bounded ? " WHERE furi = ? OR furi LIKE ?;" : ";");
        try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (bounded) {
                stmt.setString(1, path);
                stmt.setString(2, path + "/%");
            }
            try (final ResultSet rs = stmt.executeQuery()) {
                final List<Space.IdObj> results = new ArrayList<>();
                while (rs.next())
                    results.add(Space.IdObj.of(f(rs.getString("furi")), SERIALIZER.read(rs.getString("obj"))));
                return results.iterator();
            }
        }
    }

    @Override
    public Iterator<Space.IdObj> readWhere(final Connection conn, final String whereClause,
                                           final long limit) throws SQLException {
        final String sql = "SELECT furi, obj FROM " + TABLE_NAME
                + " WHERE " + whereClause + " LIMIT " + limit + ";";
        try (final PreparedStatement stmt = conn.prepareStatement(sql);
             final ResultSet rs = stmt.executeQuery()) {
            final List<Space.IdObj> results = new ArrayList<>();
            while (rs.next())
                results.add(Space.IdObj.of(f(rs.getString("furi")), SERIALIZER.read(rs.getString("obj"))));
            return results.iterator();
        }
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
    public String version() {
        return "1.0-mqtt";
    }
}
