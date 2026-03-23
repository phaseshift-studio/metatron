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

package studio.phaseshift.metatron.isa.tble.schema.storage;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjCleanStringSerializer;

import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;

/**
 * Typed key-value schema that stores primitive types (Bool, Int, Real, Str) natively
 * and complex types (Inst, Code, Rec, Lst, etc.) as strings using ObjCleanStringSerializer.
 *
 * This provides an isomorphic, error-free mapping between Metatron objects and SQL,
 * avoiding JSON conversion peculiarities.
 *
 * Table structure:
 * - furi: VARCHAR(512) PRIMARY KEY - the object's URI
 * - type: VARCHAR(32) NOT NULL - the Metatron type (bool, int, real, str, complex)
 * - bool_val: BOOLEAN - for Bool objects
 * - int_val: BIGINT - for Int objects
 * - real_val: DOUBLE PRECISION - for Real objects
 * - str_val: TEXT - for Str objects
 * - complex_val: TEXT - for complex objects (Inst, Code, Rec, Lst, etc.)
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TypedKeyValueSchema implements TableSchema {

    private static final String TABLE_NAME = "objs";
    private static final ObjCleanStringSerializer SERIALIZER = new ObjCleanStringSerializer();

    @Override
    public void initialize(final Connection conn) throws SQLException {
        // Create table with typed columns for isomorphic mapping
        final String createTable = """
                CREATE TABLE IF NOT EXISTS objs (
                    furi VARCHAR(512) NOT NULL PRIMARY KEY,
                    type VARCHAR(32) NOT NULL,
                    bool_val BOOLEAN,
                    int_val BIGINT,
                    real_val DOUBLE PRECISION,
                    str_val TEXT,
                    complex_val TEXT
                );
                """;

        // Create index on furi for fast lookups
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
        // Parse the JSON string back to Obj (this is a temporary bridge until we refactor the interface)
        final Obj obj = objJson == null || objJson.isEmpty() ? noobj() :
                studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer.parse(objJson);

        return write(conn, furi, obj);
    }

    /**
     * Write an Obj directly to the database with proper type mapping.
     */
    public int write(final Connection conn, final fURI furi, final Obj obj) throws SQLException {
        if (obj.isNoObj()) {
            return delete(conn, furi);
        }

        // Determine the type and appropriate column
        final String type;
        final String sql;

        if (obj.isBool()) {
            type = "bool";
            sql = "REPLACE INTO " + TABLE_NAME + " (furi, type, bool_val) VALUES (?, ?, ?);";
        } else if (obj.isInt()) {
            type = "int";
            sql = "REPLACE INTO " + TABLE_NAME + " (furi, type, int_val) VALUES (?, ?, ?);";
        } else if (obj.isReal()) {
            type = "real";
            sql = "REPLACE INTO " + TABLE_NAME + " (furi, type, real_val) VALUES (?, ?, ?);";
        } else if (obj.isStr()) {
            type = "str";
            sql = "REPLACE INTO " + TABLE_NAME + " (furi, type, str_val) VALUES (?, ?, ?);";
        } else {
            // Complex types: Inst, Code, Rec, Lst, Poly, etc.
            type = "complex";
            sql = "REPLACE INTO " + TABLE_NAME + " (furi, type, complex_val) VALUES (?, ?, ?);";
        }

        try (final PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, furi.toString());
            stmt.setString(2, type);

            // Set the appropriate value based on type
            if (obj.isBool()) {
                stmt.setBoolean(3, obj.asBool().boolValue());
            } else if (obj.isInt()) {
                stmt.setLong(3, obj.asInt().intValue());
            } else if (obj.isReal()) {
                stmt.setDouble(3, obj.asReal().realValue());
            } else if (obj.isStr()) {
                stmt.setString(3, obj.asStr().strValue());
            } else {
                // Use ObjCleanStringSerializer for complex types
                stmt.setString(3, SERIALIZER.write(obj));
            }

            return stmt.executeUpdate();
        }
    }

    @Override
    public Iterator<Space.IdObj> read(final Connection conn, final fURI pattern) throws SQLException {
        final String sql;
        final PreparedStatement stmt;

        if (pattern.hasPattern()) {
            // Pattern query - return all objects
            sql = "SELECT furi, type, bool_val, int_val, real_val, str_val, complex_val FROM " + TABLE_NAME + ";";
            stmt = conn.prepareStatement(sql);
        } else {
            // Exact match query
            sql = "SELECT furi, type, bool_val, int_val, real_val, str_val, complex_val FROM " + TABLE_NAME + " WHERE furi = ?;";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, pattern.toString());
        }

        final ResultSet rs = stmt.executeQuery();
        final List<Space.IdObj> results = new ArrayList<>();

        while (rs.next()) {
            final fURI furi = f(rs.getString("furi"));
            final String type = rs.getString("type");
            final Obj obj;

            // Reconstruct the Obj based on its type
            switch (type) {
                case "bool":
                    obj = bool(rs.getBoolean("bool_val"));
                    break;
                case "int":
                    obj = jnt(rs.getLong("int_val"));
                    break;
                case "real":
                    obj = real(rs.getDouble("real_val"));
                    break;
                case "str":
                    obj = str(rs.getString("str_val"));
                    break;
                case "complex":
                    // Use ObjCleanStringSerializer to parse complex types
                    obj = SERIALIZER.read(rs.getString("complex_val"));
                    break;
                default:
                    throw new SQLException("Unknown type: " + type);
            }

            results.add(Space.IdObj.of(furi, obj));
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
        return "2.0-typed";
    }
}
