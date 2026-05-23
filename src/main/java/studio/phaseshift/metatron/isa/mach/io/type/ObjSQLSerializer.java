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

package studio.phaseshift.metatron.isa.mach.io.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs0;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.tble.tbleInstSet.LST_ROW_TID;
import static studio.phaseshift.metatron.isa.tble.tbleInstSet.REC_ROW_TID;

/**
 * Serializer for converting between SQL types and Metatron objects.
 * Handles reading from ResultSet and writing to PreparedStatement.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjSQLSerializer extends AbstractObjSerializer<ResultSet> {

    public static final fURI OBJ_SQL_SERIALIZER_VID = f("/m/mach/io/serializer/sql");

    @Override
    public fURI vid() {
        return OBJ_SQL_SERIALIZER_VID;
    }

    @Override
    public fURI jvm() {
        return OBJ_SQL_SERIALIZER_VID;
    }

    @Override
    public ByteBuffer outputBytes(final Obj obj) throws MTronException {
        throw new UnsupportedOperationException("SQL serializer does not support byte output");
    }

    @Override
    public Obj inputBytes(final ByteBuffer bytes) throws MTronException {
        throw new UnsupportedOperationException("SQL serializer does not support byte input");
    }

    /**
     * Read the current row from a ResultSet as a Metatron Rec object.
     * The Rec will contain column names as Uri keys and column values as Obj values.
     *
     * @param rs the ResultSet positioned at a row
     * @return a Rec containing the row data
     * @throws MTronException if reading fails
     */
    @Override
    public Obj read(final ResultSet rs) throws MTronException {
        try {
            final ResultSetMetaData metaData = rs.getMetaData();
            final int columnCount = metaData.getColumnCount();
            final Map<Obj, Obj> rowData = new LinkedHashMap<>();

            for (int i = 1; i <= columnCount; i++) {
                final String columnName = metaData.getColumnName(i);
                final int sqlType = metaData.getColumnType(i);
                final Obj value = readColumn(rs, i, sqlType);
                rowData.put(uri(columnName), value);
            }

            return rec(rowData);
        } catch (final SQLException e) {
            throw MTronException.of(e, "Failed to read ResultSet row");
        }
    }

    /**
     * Writing to ResultSet is not supported. Use writeParameter() to write to PreparedStatement.
     *
     * @param obj the object to write
     * @return never returns
     * @throws MTronException always
     */
    @Override
    public ResultSet write(final Obj obj) throws MTronException {
        throw new UnsupportedOperationException("SQL serializer does not support writing to ResultSet. Use writeParameter() to write to PreparedStatement.");
    }

    /**
     * Parse a string representation of SQL data.
     * This is a convenience method that delegates to the standard read() method.
     *
     * @param data the string to parse
     * @return the parsed object
     * @throws MTronException if parsing fails
     */
    public static Obj parse(final String data) throws MTronException {
        throw new UnsupportedOperationException("SQL serializer does not support string parsing. Use read(ResultSet) instead.");
    }

    /**
     * Read a metatron object from a SQL ResultSet column.
     *
     * @param rs         the ResultSet
     * @param columnName the column name to read
     * @param sqlType    the SQL type (from java.sql.Types)
     * @return the Metatron object
     * @throws SQLException if reading fails
     */
    protected Obj readColumn(final ResultSet rs, final String columnName, final int sqlType) throws SQLException {
        final Object value = rs.getObject(columnName);
        if (value == null || rs.wasNull()) {
            return noobj();
        }

        return switch (sqlType) {
            case Types.BOOLEAN, Types.BIT -> bool(rs.getBoolean(columnName));
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> jnt(rs.getLong(columnName));
            case Types.REAL, Types.FLOAT, Types.DOUBLE, Types.DECIMAL, Types.NUMERIC -> real(rs.getDouble(columnName));
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR ->
                    str(rs.getString(columnName));
            case Types.DATE, Types.TIME, Types.TIMESTAMP -> str(rs.getString(columnName));
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> str(rs.getString(columnName));
            default -> str(value.toString());
        };
    }

    /**
     * Read a Metatron object from a SQL ResultSet column by index.
     *
     * @param rs          the ResultSet
     * @param columnIndex the column index (1-based)
     * @param sqlType     the SQL type (from java.sql.Types)
     * @return the Metatron object
     * @throws SQLException if reading fails
     */
    protected Obj readColumn(final ResultSet rs, final int columnIndex, final int sqlType) throws SQLException {
        final Object value = rs.getObject(columnIndex);
        if (value == null || rs.wasNull()) {
            return noobj();
        }

        return switch (sqlType) {
            case Types.BOOLEAN, Types.BIT -> bool(rs.getBoolean(columnIndex));
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> jnt(rs.getLong(columnIndex));
            case Types.REAL, Types.FLOAT, Types.DOUBLE, Types.DECIMAL, Types.NUMERIC -> real(rs.getDouble(columnIndex));
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR ->
                    str(rs.getString(columnIndex));
            case Types.DATE, Types.TIME, Types.TIMESTAMP -> str(rs.getString(columnIndex));
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> str(rs.getString(columnIndex));
            default -> str(value.toString());
        };
    }

    // ==================== Bulk ResultSet Conversion Helpers ====================

    /**
     * Read all rows from a ResultSet as a list of Rec (rrows).
     * Each row becomes a Rec with column names as Uri keys.
     * The ResultSet cursor is advanced until exhausted.
     *
     * @param rs the ResultSet to read from (cursor should be before first row)
     * @return a list of Rec objects, one per row
     * @throws SQLException if reading fails
     */
    public static List<Rec> readAllAsRec(final ResultSet rs) throws SQLException {
        final List<Rec> rows = new ArrayList<>();
        final ResultSetMetaData metaData = rs.getMetaData();
        final int columnCount = metaData.getColumnCount();

        while (rs.next()) {
            final Map<Obj, Obj> rowData = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                final String columnName = metaData.getColumnName(i);
                final int sqlType = metaData.getColumnType(i);
                final Obj value = readColumnStatic(rs, i, sqlType);
                rowData.put(uri(columnName), value);
            }
            rows.add(rec(rowData, REC_ROW_TID, null));
        }
        return rows;
    }

    /**
     * Read the current row from a ResultSet as a Rec without advancing the cursor.
     *
     * @param rs the ResultSet positioned at a valid row
     * @return a Rec containing the current row's data
     * @throws SQLException if reading fails
     */
    public static Rec readCurrentAsRec(final ResultSet rs) throws SQLException {
        final ResultSetMetaData metaData = rs.getMetaData();
        final int columnCount = metaData.getColumnCount();
        final Map<Obj, Obj> rowData = new LinkedHashMap<>();
        for (int i = 1; i <= columnCount; i++) {
            final String columnName = metaData.getColumnName(i);
            final int sqlType = metaData.getColumnType(i);
            final Obj value = readColumnStatic(rs, i, sqlType);
            rowData.put(uri(columnName), value);
        }
        return rec(rowData, REC_ROW_TID, null);
    }

    /**
     * Read all rows from a ResultSet as an Objs stream of Rec (rrows).
     * This is useful for returning from instruction implementations.
     *
     * @param rs the ResultSet to read from
     * @return an Objs containing all rows as Rec objects
     * @throws SQLException if reading fails
     */
    public static Objs readAllAsRecObjs(final ResultSet rs) throws SQLException {
        Obj result = objs0();
        for (final Rec row : readAllAsRec(rs)) {
            result = result.append(row);
        }
        return result.asObjs();
    }

    /**
     * Read all rows from a ResultSet as a list of Lst (lrows).
     * Each row becomes a Lst with values in column order.
     * The ResultSet cursor is advanced until exhausted.
     *
     * @param rs the ResultSet to read from (cursor should be before first row)
     * @return a list of Lst objects, one per row
     * @throws SQLException if reading fails
     */
    public static List<Lst> readAllAsLst(final ResultSet rs) throws SQLException {
        final List<Lst> rows = new ArrayList<>();
        final ResultSetMetaData metaData = rs.getMetaData();
        final int columnCount = metaData.getColumnCount();

        while (rs.next()) {
            final List<Obj> rowData = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                final int sqlType = metaData.getColumnType(i);
                rowData.add(readColumnStatic(rs, i, sqlType));
            }
            rows.add(lst(rowData, LST_ROW_TID, null));
        }
        return rows;
    }

    /**
     * Read all rows from a ResultSet as an Objs stream of Lst (lrows).
     * This is useful for returning from instruction implementations.
     *
     * @param rs the ResultSet to read from
     * @return an Objs containing all rows as Lst objects
     * @throws SQLException if reading fails
     */
    public static Obj readAllAsLstObjs(final ResultSet rs) throws SQLException {
        Obj result = objs0();
        for (final Lst row : readAllAsLst(rs)) {
            result = result.append(row);
        }
        return result;
    }

    /**
     * Read up to 'limit' rows from a ResultSet as a list of Rec (rrows).
     * Each row becomes a Rec with column names as Uri keys.
     *
     * @param rs    the ResultSet to read from
     * @param limit maximum number of rows to read
     * @return a list of Rec objects, up to 'limit' rows
     * @throws SQLException if reading fails
     */
    public static List<Rec> readLimitedAsRec(final ResultSet rs, final int limit) throws SQLException {
        final List<Rec> rows = new ArrayList<>();
        final ResultSetMetaData metaData = rs.getMetaData();
        final int columnCount = metaData.getColumnCount();
        int count = 0;

        while (rs.next() && count < limit) {
            final Map<Obj, Obj> rowData = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                final String columnName = metaData.getColumnName(i);
                final int sqlType = metaData.getColumnType(i);
                final Obj value = readColumnStatic(rs, i, sqlType);
                rowData.put(uri(columnName), value);
            }
            rows.add(rec(rowData, REC_ROW_TID, null));
            count++;
        }
        return rows;
    }

    /**
     * Read up to 'limit' rows from a ResultSet as an Objs stream of Rec (rrows).
     *
     * @param rs    the ResultSet to read from
     * @param limit maximum number of rows to read
     * @return an Objs containing up to 'limit' rows as Rec objects
     * @throws SQLException if reading fails
     */
    public static Obj readLimitedAsRecObjs(final ResultSet rs, final int limit) throws SQLException {
        Obj result = objs0();
        for (final Rec row : readLimitedAsRec(rs, limit)) {
            result = result.append(row);
        }
        return result;
    }

    /**
     * Read up to 'limit' rows from a ResultSet as a list of Lst (lrows).
     * Each row becomes a Lst with values in column order.
     *
     * @param rs    the ResultSet to read from
     * @param limit maximum number of rows to read
     * @return a list of Lst objects, up to 'limit' rows
     * @throws SQLException if reading fails
     */
    public static List<Lst> readLimitedAsLst(final ResultSet rs, final int limit) throws SQLException {
        final List<Lst> rows = new ArrayList<>();
        final ResultSetMetaData metaData = rs.getMetaData();
        final int columnCount = metaData.getColumnCount();
        int count = 0;

        while (rs.next() && count < limit) {
            final List<Obj> rowData = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                final int sqlType = metaData.getColumnType(i);
                rowData.add(readColumnStatic(rs, i, sqlType));
            }
            rows.add(lst(rowData, LST_ROW_TID, null));
            count++;
        }
        return rows;
    }

    /**
     * Read up to 'limit' rows from a ResultSet as an Objs stream of Lst (lrows).
     *
     * @param rs    the ResultSet to read from
     * @param limit maximum number of rows to read
     * @return an Objs containing up to 'limit' rows as Lst objects
     * @throws SQLException if reading fails
     */
    public static Obj readLimitedAsLstObjs(final ResultSet rs, final int limit) throws SQLException {
        Obj result = objs0();
        for (final Lst row : readLimitedAsLst(rs, limit)) {
            result = result.append(row);
        }
        return result;
    }

    /**
     * Static helper to read a column value by index.
     * This is used by the static bulk conversion methods.
     */
    private static Obj readColumnStatic(final ResultSet rs, final int columnIndex, final int sqlType) throws SQLException {
        final Object value = rs.getObject(columnIndex);
        if (value == null || rs.wasNull()) {
            return noobj();
        }

        return switch (sqlType) {
            case Types.BOOLEAN, Types.BIT -> bool(rs.getBoolean(columnIndex));
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> jnt(rs.getLong(columnIndex));
            case Types.REAL, Types.FLOAT, Types.DOUBLE, Types.DECIMAL, Types.NUMERIC -> real(rs.getDouble(columnIndex));
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR ->
                    str(rs.getString(columnIndex));
            case Types.DATE, Types.TIME, Types.TIMESTAMP -> str(rs.getString(columnIndex));
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> str(rs.getString(columnIndex));
            default -> str(value.toString());
        };
    }

    // ==================== End Bulk ResultSet Conversion Helpers ====================

    /**
     * Write a Metatron object to a PreparedStatement parameter.
     *
     * @param stmt       the PreparedStatement
     * @param paramIndex the parameter index (1-based)
     * @param value      the Metatron object to write
     * @param sqlType    the target SQL type (from java.sql.Types)
     * @throws SQLException if writing fails
     */
    protected void writeParameter(final PreparedStatement stmt, final int paramIndex,
                                  final Obj value, final int sqlType) throws SQLException {
        if (value.isNoObj() || value.isNone()) {
            stmt.setNull(paramIndex, sqlType);
            return;
        }

        // FK pointer — store only the raw PK value (last URI segment) so the column
        // holds a plain integer/string the database can enforce as a real FK.
        // The auto_from inst is reconstructed on read by readColumnWithMetadata()
        // via getForeignKeyForColumn() when the REFERENCES constraint is present.
        if (value.isAutoFrom()) {
            final String pkStr = value.asInst().arg(0).uriValue().name();
            if (sqlType == Types.INTEGER || sqlType == Types.BIGINT ||
                    sqlType == Types.SMALLINT || sqlType == Types.TINYINT) {
                stmt.setLong(paramIndex, Long.parseLong(pkStr));
            } else {
                stmt.setString(paramIndex, pkStr);
            }
            return;
        }

        switch (sqlType) {
            case Types.BOOLEAN, Types.BIT -> {
                if (value.isBool()) {
                    stmt.setBoolean(paramIndex, value.asBool().jvm());
                } else if (value.isStr()) {
                    stmt.setBoolean(paramIndex, Boolean.parseBoolean(value.asStr().jvm()));
                } else if (value.isInt()) {
                    stmt.setBoolean(paramIndex, value.asInt().jvm() != 0);
                } else {
                    stmt.setBoolean(paramIndex, Boolean.parseBoolean(value.toString()));
                }
            }
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER -> {
                if (value.isInt()) {
                    stmt.setInt(paramIndex, Math.toIntExact(value.asInt().jvm()));
                } else if (value.isReal()) {
                    stmt.setInt(paramIndex, Double.valueOf(value.asReal().jvm()).intValue());
                } else if (value.isBool()) {
                    stmt.setInt(paramIndex, value.asBool().jvm() ? 1 : 0);
                } else if (value.isStr()) {
                    stmt.setInt(paramIndex, Integer.parseInt(value.asStr().jvm()));
                } else {
                    stmt.setInt(paramIndex, Integer.parseInt(value.toString()));
                }
            }
            case Types.BIGINT -> {
                if (value.isInt()) {
                    stmt.setLong(paramIndex, value.asInt().jvm());
                } else if (value.isReal()) {
                    stmt.setLong(paramIndex, Double.valueOf(value.asReal().jvm()).longValue());
                } else if (value.isBool()) {
                    stmt.setLong(paramIndex, value.asBool().jvm() ? 1L : 0L);
                } else if (value.isStr()) {
                    stmt.setLong(paramIndex, Long.parseLong(value.asStr().jvm()));
                } else {
                    stmt.setLong(paramIndex, Long.parseLong(value.toString()));
                }
            }
            case Types.REAL, Types.FLOAT -> {
                if (value.isReal()) {
                    stmt.setFloat(paramIndex, Double.valueOf(value.asReal().jvm()).floatValue());
                } else if (value.isInt()) {
                    stmt.setFloat(paramIndex, Long.valueOf(value.asInt().jvm()).floatValue());
                } else if (value.isStr()) {
                    stmt.setFloat(paramIndex, Float.parseFloat(value.asStr().jvm()));
                } else {
                    stmt.setFloat(paramIndex, Float.parseFloat(value.toString()));
                }
            }
            case Types.DOUBLE, Types.DECIMAL, Types.NUMERIC -> {
                if (value.isReal()) {
                    stmt.setDouble(paramIndex, value.asReal().jvm());
                } else if (value.isInt()) {
                    stmt.setDouble(paramIndex, (double) value.asInt().jvm());
                } else if (value.isStr()) {
                    stmt.setDouble(paramIndex, Double.parseDouble(value.asStr().jvm()));
                } else {
                    stmt.setDouble(paramIndex, Double.parseDouble(value.toString()));
                }
            }
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR -> {
                if (value.isStr()) {
                    stmt.setString(paramIndex, value.asStr().jvm());
                } else if (value.isUri()) {
                    stmt.setString(paramIndex, value.asUri().uriValue().toString());
                } else {
                    stmt.setString(paramIndex, value.toString());
                }
            }
            case Types.DATE -> {
                if (value.isStr()) {
                    stmt.setDate(paramIndex, java.sql.Date.valueOf(value.asStr().jvm()));
                } else {
                    stmt.setDate(paramIndex, java.sql.Date.valueOf(value.toString()));
                }
            }
            case Types.TIME -> {
                if (value.isStr()) {
                    stmt.setTime(paramIndex, java.sql.Time.valueOf(value.asStr().jvm()));
                } else {
                    stmt.setTime(paramIndex, java.sql.Time.valueOf(value.toString()));
                }
            }
            case Types.TIMESTAMP -> {
                if (value.isStr()) {
                    stmt.setTimestamp(paramIndex, java.sql.Timestamp.valueOf(value.asStr().jvm()));
                } else {
                    stmt.setTimestamp(paramIndex, java.sql.Timestamp.valueOf(value.toString()));
                }
            }
            default -> stmt.setString(paramIndex, value.toString());
        }
    }

    @Override
    public ResultSet writeNoObj(final NoObj noobj) {
        throw new UnsupportedOperationException("SQL serializer does not support writing to ResultSet");
    }

    @Override
    public ResultSet writeBool(final Bool dool) {
        throw new UnsupportedOperationException("SQL serializer does not support writing to ResultSet");
    }

    @Override
    public ResultSet writeFail(final Fail fail) {
        throw new UnsupportedOperationException("SQL serializer does not support writing to ResultSet");
    }

    @Override
    public ResultSet writeStr(final Str str) {
        throw new UnsupportedOperationException("SQL serializer does not support writing to ResultSet");
    }

    @Override
    public ResultSet writeInt(final Int jnt) {
        throw new UnsupportedOperationException("SQL serializer does not support writing to ResultSet");
    }

    @Override
    public ResultSet writeReal(final Real real) {
        throw new UnsupportedOperationException("SQL serializer does not support writing to ResultSet");
    }

    @Override
    public ResultSet writeUri(final Uri uri) {
        throw new UnsupportedOperationException("SQL serializer does not support writing to ResultSet");
    }

    @Override
    public ResultSet writeLst(final Lst lst) {
        throw new UnsupportedOperationException("SQL serializer does not support writing to ResultSet");
    }

    @Override
    public ResultSet writeRel(final Rel rel) {
        throw new UnsupportedOperationException("SQL serializer does not support writing to ResultSet");
    }

    @Override
    public ResultSet writeRec(final Rec rec) {
        throw new UnsupportedOperationException("SQL serializer does not support writing to ResultSet");
    }

    @Override
    public ResultSet writeInst(final Inst inst) {
        throw new UnsupportedOperationException("SQL serializer does not support writing to ResultSet");
    }

    @Override
    public ResultSet writeCode(final Code code) {
        throw new UnsupportedOperationException("SQL serializer does not support writing to ResultSet");
    }

    @Override
    public ResultSet writeObjs(final Objs objs) {
        throw new UnsupportedOperationException("SQL serializer does not support writing to ResultSet");
    }

    @Override
    public ResultSet writeType(final Type type) {
        throw new UnsupportedOperationException("SQL serializer does not support writing to ResultSet");
    }

    @Override
    public ResultSet writeBytes(final Bytes bytes) {
        throw new UnsupportedOperationException("SQL serializer does not support writing to ResultSet");
    }
}
