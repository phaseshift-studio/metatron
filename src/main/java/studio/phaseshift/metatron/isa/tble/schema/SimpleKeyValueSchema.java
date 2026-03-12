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
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;

import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * Simple schema with basic furi/obj table and no special indexing.
 * Good for small datasets or when MQTT pattern matching is not needed.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class SimpleKeyValueSchema implements TableSchema {

    private static final String TABLE_NAME = "objs";

    @Override
    public void initialize(final Connection conn) throws SQLException {
        // Create table (compatible with SQLite, MariaDB, MySQL, PostgreSQL)
        final String createTable = """
                CREATE TABLE IF NOT EXISTS objs (
                    furi VARCHAR(512) NOT NULL PRIMARY KEY,
                    obj TEXT NOT NULL
                );
                """;

        // Create index separately (SQLite doesn't support inline INDEX in CREATE TABLE)
        final String createIndex = """
                CREATE INDEX IF NOT EXISTS idx_furi ON objs(furi);
                """;

        try (final Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createTable);
            stmt.executeUpdate(createIndex);
        }
    }

    @Override
    public int write(final Connection conn, final fURI furi, final String objJson) throws SQLException {
        if (objJson == null || objJson.isEmpty()) {
            return delete(conn, furi);
        }

        // Use REPLACE which works on SQLite, MariaDB, MySQL
        // REPLACE = DELETE + INSERT (atomic operation)
        final String sql = "REPLACE INTO " + TABLE_NAME + " (furi, obj) VALUES (?, ?);";

        try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, furi.toString());
            stmt.setString(2, objJson);
            return stmt.executeUpdate();
        }
    }

    @Override
    public Iterator<Space.IdObj> read(final Connection conn, final fURI pattern) throws SQLException {
        final String sql;
        final PreparedStatement stmt;

        if (pattern.hasPattern()) {
            // Pattern query - return all objects
            sql = "SELECT furi, obj FROM " + TABLE_NAME + ";";
            stmt = conn.prepareStatement(sql);
        } else {
            // Exact match query
            sql = "SELECT furi, obj FROM " + TABLE_NAME + " WHERE furi = ?;";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, pattern.toString());
        }

        final ResultSet rs = stmt.executeQuery();
        final List<Space.IdObj> results = new ArrayList<>();

        while (rs.next()) {
            results.add(Space.IdObj.of(f(rs.getString("furi")), ObjSimpleJSONSerializer.parse(rs.getString("obj"))));
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
    public boolean supportsfURIPatterns() {
        return false;
    }

    @Override
    public String version() {
        return "1.0-simple";
    }
}
