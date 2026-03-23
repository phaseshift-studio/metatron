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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

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
     * Read a Metatron object from a SQL ResultSet column.
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
        if (value.isNoObj()) {
            stmt.setNull(paramIndex, sqlType);
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
