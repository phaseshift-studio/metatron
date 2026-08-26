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

package studio.phaseshift.metatron.isa.mach.io.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.web.parser.ObjJSONSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.LST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.DATETIME_TYPE;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_DATETIME_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs0;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Serializer for converting between SQL types and Metatron objects.
 * <p>
 * Implements the full {@link ObjSerializer} contract with {@code T=Object}:
 * {@link #write(Obj)} produces a SQL-safe String representation, and
 * {@link #read(Object)} parses a String (or reads a {@link ResultSet} row)
 * back to an {@link Obj}.
 * <p>
 * JDBC bridge methods ({@link #readColumn}, {@link #writeParameter},
 * {@link #readColumnWithType}) handle direct {@link ResultSet} /
 * {@link PreparedStatement} operations.  When constructed with a
 * {@code logicalTypes} map (from {@code _mtron_meta}), type-aware
 * deserialization is used instead of heuristic guessing.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjSQLSerializer extends AbstractObjSerializer<Object> {

    public static final fURI OBJ_SQL_SERIALIZER_TID = f("/m/mach/io/serializer/sql");
    public static final fURI OBJ_SQL_SERIALIZER_VID = OBJ_SQL_SERIALIZER_TID;

    // ---- logical type metadata (_mtron_meta) --------------------------------

    /**
     * Column-level type overrides from {@code _mtron_meta}: tableName → (columnName → mtronTID).
     * When non-null, {@link #readColumnWithType} uses this to reconstruct typed values
     * (e.g. {@code uri::T} from a TEXT column) without heuristic guessing.
     */
    protected final Map<String, Map<String, fURI>> logicalTypes;

    // ---- constructors -------------------------------------------------------

    public ObjSQLSerializer() {
        super(OBJ_SQL_SERIALIZER_TID, OBJ_SQL_SERIALIZER_VID);
        this.logicalTypes = null;
    }

    public ObjSQLSerializer(final Map<String, Map<String, fURI>> logicalTypes) {
        super(OBJ_SQL_SERIALIZER_TID, OBJ_SQL_SERIALIZER_VID);
        this.logicalTypes = logicalTypes;
    }

    // ---- ObjSerializer core -------------------------------------------------

    @Override
    public fURI vid() {
        return OBJ_SQL_SERIALIZER_VID;
    }

    private static final ObjmtronSerializer MTRON = ObjmtronSerializer.compact();

    @Override
    public ByteBuffer outputBytes(final Obj obj) throws MTronException {
        return MTRON.outputBytes(obj);
    }

    @Override
    public Obj inputBytes(final ByteBuffer bytes) throws MTronException {
        return MTRON.inputBytes(bytes);
    }

    /**
     * Read a value back from its serialized form.
     * <p>
     * Accepts:
     * <ul>
     *   <li>{@link String} — parsed via {@link #readMaybeJSON(String)}</li>
     *   <li>{@link ResultSet} — read as a full row (column-name-keyed {@link Rec})</li>
     * </ul>
     */
    @Override
    public Obj read(final Object data) throws MTronException {
        if (data instanceof ResultSet rs) {
            return readResultSetRow(rs);
        }
        if (data instanceof String s) {
            return MTRON.inputBytes(s.getBytes(StandardCharsets.UTF_8));
        }
        throw MTronException.of("ObjSQLSerializer.read: unsupported type %s", data.getClass().getName());
    }

    // ---- write(Obj) → String (type-dispatched) ------------------------------

    /**
     * Write an Obj to a SQL-safe String representation.
     * Delegates to the type-specific {@code writeXxx} methods via the
     * default {@link ObjSerializer#write(Obj)} dispatcher.
     */
    /**
     * Write an Obj to its canonical mtron String representation.
     * Delegates to {@link ObjmtronSerializer} — single format to maintain.
     */
    @Override
    public Object write(final Obj obj) throws MTronException {
        return MTRON.write(obj);
    }

    // ---- read helpers -------------------------------------------------------

    /**
     * Read the current row from a ResultSet as a Rec (column-name keys).
     */
    private Obj readResultSetRow(final ResultSet rs) throws MTronException {
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

    // ---- JDBC bridge: read --------------------------------------------------

    /**
     * Parse a {@code datetime::T} URI ({@code //yyyy.MM:dd/HH/mm/ss/SSS?tz=±HHmm})
     * into a {@link ZonedDateTime}, or return {@code null} if the value
     * is not a datetime URI.
     */
    private static ZonedDateTime toZonedDateTime(final Obj value) {
        if (!value.isUri() || !"datetime".equals(value.tid().name())) {
            return null;
        }
        final fURI furi = value.uriValue();
        // host = "yyyy.MM", port = dd, path = [HH, mm, ss, SSS]
        final String host = furi.host();
        if (host == null || host.length() < 7) return null;
        final int year = Integer.parseInt(host.substring(0, 4));
        final int month = Integer.parseInt(host.substring(5, 7));
        final int day = furi.port();
        final List<String> path = furi.path();
        if (path.size() < 4) return null;
        final int hour = Integer.parseInt(path.get(path.size() - 4));
        final int minute = Integer.parseInt(path.get(path.size() - 3));
        final int second = Integer.parseInt(path.get(path.size() - 2));
        final int millis = Integer.parseInt(path.getLast());
        final String tzStr = furi.qMap().getOrDefault("tz", "+0000");
        final ZoneId zone = ZoneId.of(tzStr.replaceAll("([+-]\\d{2})(\\d{2})", "$1:$2"));
        return ZonedDateTime.of(year, month, day, hour, minute, second, millis * 1_000_000, zone);
    }

    private static java.sql.Date toSqlDate(final Obj value) {
        final ZonedDateTime zdt = toZonedDateTime(value);
        if (zdt != null)
            return java.sql.Date.valueOf(zdt.toLocalDate());
        if (value.isStr())
            return java.sql.Date.valueOf(value.asStr().jvm());
        return java.sql.Date.valueOf(value.toString());
    }

    private static java.sql.Time toSqlTime(final Obj value) {
        final ZonedDateTime zdt = toZonedDateTime(value);
        if (zdt != null)
            return java.sql.Time.valueOf(zdt.toLocalTime());
        if (value.isStr())
            return java.sql.Time.valueOf(value.asStr().jvm());
        return java.sql.Time.valueOf(value.toString());
    }

    private static java.sql.Timestamp toSqlTimestamp(final Obj value) {
        final ZonedDateTime zdt = toZonedDateTime(value);
        if (zdt != null)
            return java.sql.Timestamp.valueOf(zdt.toLocalDateTime());
        if (value.isStr())
            return java.sql.Timestamp.valueOf(value.asStr().jvm());
        return java.sql.Timestamp.valueOf(value.toString());
    }

    /**
     * Convert a {@code datetime::T} URI directly to an ISO-8601 string
     * suitable for SQLite TEXT/TIMESTAMP columns.  Avoids JDBC
     * {@code setTimestamp} round-trip issues in SQLite.
     */
    private static String toSqlDatetimeString(final Obj value) {
        final ZonedDateTime zdt = toZonedDateTime(value);
        if (zdt != null)
            return zdt.toLocalDateTime().toString().replace('T', ' ');
        if (value.isStr())
            return value.asStr().jvm();
        return value.toString();
    }

    /**
     * Read a SQL {@link java.sql.Timestamp} as a {@code datetime::T} URI.
     */
    private static Obj readDateTime(final ResultSet rs, final String columnName) throws SQLException {
        final Timestamp ts = rs.getTimestamp(columnName);
        if (ts == null) return noobj();
        final ZonedDateTime zdt = ts.toInstant().atZone(ZoneId.systemDefault());
        return mathInstSet.buildDatetimeUri(zdt);
    }

    private static Obj readDateTime(final ResultSet rs, final int columnIndex) throws SQLException {
        final Timestamp ts = rs.getTimestamp(columnIndex);
        if (ts == null) return noobj();
        final ZonedDateTime zdt = ts.toInstant().atZone(ZoneId.systemDefault());
        return mathInstSet.buildDatetimeUri(zdt);
    }

    /**
     * Read a metatron object from a SQL ResultSet column by name.
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
                    readMaybeJSON(rs.getString(columnName));
            case Types.DATE, Types.TIME, Types.TIMESTAMP -> readDateTime(rs, columnName);
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> str(rs.getString(columnName));
            default -> readMaybeJSON(value.toString());
        };
    }

    /**
     * Read a Metatron object from a SQL ResultSet column by index.
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
                    readMaybeJSON(rs.getString(columnIndex));
            case Types.DATE, Types.TIME, Types.TIMESTAMP -> readDateTime(rs, columnIndex);
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> str(rs.getString(columnIndex));
            default -> readMaybeJSON(value.toString());
        };
    }

    // ---- _mtron_meta type-aware read ----------------------------------------

    /**
     * Look up the logical mtron TID for a column from {@link #logicalTypes}
     * (populated from {@code _mtron_meta}).  Returns {@code null} when no type
     * metadata is available.
     */
    public fURI getLogicalType(final String tableName, final String columnName) {
        if (logicalTypes == null) return null;
        final Map<String, fURI> tableTypes = logicalTypes.get(tableName.toLowerCase());
        if (tableTypes == null) return null;
        return tableTypes.get(columnName.toLowerCase());
    }

    /**
     * Read a column with type awareness from {@link #logicalTypes}.
     * <p>
     * When {@code _mtron_meta} declares this column as {@code uri::T}, the raw
     * TEXT value is reconstructed as a {@link Uri} instead of relying on the
     * legacy {@code <>} wrapper heuristic.  Similarly, {@code bool::T} stored
     * in an INTEGER column is coerced correctly.
     * <p>
     * Falls back to {@link #readColumn(ResultSet, String, int)} when no logical
     * type is registered for the column.
     */
    protected Obj readColumnWithType(final ResultSet rs, final String columnName,
                                     final int sqlType, final String tableName)
            throws SQLException {
        final fURI logicalType = getLogicalType(tableName, columnName);
        if (logicalType != null) {
            final Object value = rs.getObject(columnName);
            if (value == null || rs.wasNull()) return noobj();
            final String typeName = logicalType.name();

            if ("uri".equals(typeName)) {
                final String raw = rs.getString(columnName);
                if (raw == null || raw.isBlank()) return noobj();
                final String clean = (raw.startsWith("<") && raw.endsWith(">"))
                        ? raw.substring(1, raw.length() - 1)
                        : raw;
                final Obj uriObj = uri(f(clean));
                if (uriObj.test(DATETIME_TYPE))
                    return uri(f(clean), MATH_DATETIME_TID, null);
                return uriObj;
            }

            if ("bool".equals(typeName)) {
                if (sqlType == Types.BOOLEAN || sqlType == Types.BIT)
                    return bool(rs.getBoolean(columnName));
                return bool(rs.getInt(columnName) != 0);
            }

            // Structured types serialized as mtron strings in TEXT columns.
            // Use the canonical parser — no heuristic guessing needed.
            if ("lst".equals(typeName) || "rec".equals(typeName)) {
                final String raw = rs.getString(columnName);
                if (raw == null || raw.isBlank()) return noobj();
                return MTRON.inputBytes(raw.getBytes(StandardCharsets.UTF_8));
            }

            // datetime::T — use readDateTime for TIMESTAMP columns, or
            // parse a datetime URI string from a VARCHAR column.  The VARCHAR
            // value was written as an ISO-8601 string via toSqlDatetimeString,
            // so parse it back to a canonical datetime URI — wrapping the raw
            // ISO string in uri(f(raw)) would produce an invalid datetime
            // (e.g. datetime::<2026-08-25 22:34:11.533> with no host/port).
            if ("datetime".equals(typeName)) {
                if (sqlType == Types.DATE || sqlType == Types.TIME || sqlType == Types.TIMESTAMP)
                    return readDateTime(rs, columnName);
                final String raw = rs.getString(columnName);
                if (raw == null || raw.isBlank()) return noobj();
                try {
                    return mathInstSet.parseDatetime(raw);
                } catch (final Exception ignored) { /* fall through */ }
                return readMaybeJSON(raw);
            }

            // Plain string — don't guess at JSON/mtron/numbers/bools.
            if ("str".equals(typeName)) {
                final String raw = rs.getString(columnName);
                return raw != null ? str(raw) : noobj();
            }

            // Unknown subtype (e.g. chat_result::T whose base is rec::T).
            // Try mtron parsing only when the raw string looks structured
            // (e.g. "chat_result::[...]", "[...]", "{...}") — primitive
            // values like "29" or "true" stay on the JDBC code path below.
            {
                final String raw = rs.getString(columnName);
                if (raw != null && !raw.isBlank()) {
                    final String trimmed = raw.stripLeading();
                    if (trimmed.indexOf("::[") > 0 || trimmed.startsWith("[") || trimmed.startsWith("{")) {
                        try {
                            return MTRON.inputBytes(raw.getBytes(StandardCharsets.UTF_8));
                        } catch (final Exception ignored) { /* fall through to readColumn */ }
                    }
                }
            }
        }
        // No _mtron_meta entry — fall back to JDBC-type-based reading
        // (which uses readMaybeJSON heuristics for VARCHAR columns).
        return readColumn(rs, columnName, sqlType);
    }

    // ---- heuristic string parser --------------------------------------------

    /**
     * Probe a string value for structured data.  If it starts with {@code [} or {@code \{},
     * try JSON parsing first, then fall back to mtron parsing.  If wrapped in {@code <>},
     * treat as a URI (legacy format).  Otherwise return as a plain {@link Str}.
     */
    public static Obj readMaybeJSON(final String value) {
        if (value == null || value.isBlank()) return str(value);
        final String trimmed = value.stripLeading();
        if (trimmed.isEmpty()) return str(value);
        final char first = trimmed.charAt(0);
        if (first == '<' && trimmed.endsWith(">")) {
            return uri(trimmed.substring(1, trimmed.length() - 1));
        }
        // datetime::T stored as ISO-8601 string: yyyy-MM-dd HH:mm:ss[.SSS]
        if (first >= '0' && first <= '9' && trimmed.length() >= 19
                && trimmed.charAt(4) == '-' && trimmed.charAt(7) == '-') {
            try {
                return mathInstSet.parseDatetime(trimmed);
            } catch (final Exception ignored) { /* fall through */ }
        }
        // datetime::T URIs (legacy): //yyyy.MM:dd/HH/mm/ss/SSS?tz=±HHmm
        if (first == '/' && trimmed.startsWith("//") && trimmed.length() > 20) {
            try {
                return uri(f(trimmed), MATH_DATETIME_TID, null);
            } catch (final Exception ignored) { /* fall through */ }
        }
        if (first == '[' || first == '{') {
            try {
                return ObjJSONSerializer.simple().inputBytes(value);
            } catch (final Exception jsonEx) {
                try {
                    return ObjmtronSerializer.compact().inputBytes(value.getBytes());
                } catch (final Exception mtronEx) {
                    return str(value);
                }
            }
        }
        // Plain numbers stored in VARCHAR columns (e.g. mono written over a Rec)
        try {
            return jnt(Long.parseLong(value));
        } catch (NumberFormatException e) {
        }
        try {
            return real(Double.parseDouble(value));
        } catch (NumberFormatException e) {
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))
            return bool(Boolean.parseBoolean(value));
        return str(value);
    }

    // ---- JDBC bridge: write -------------------------------------------------

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
                    if ("datetime".equals(value.tid().name())) {
                        stmt.setString(paramIndex, toSqlDatetimeString(value));
                    } else {
                        stmt.setString(paramIndex, value.asUri().uriValue().toString());
                    }
                } else if (value.isPoly()) {
                    // Poly values (Lst, Rec, Inst, Code) — use canonical
                    // no-clip mtron format so they round-trip cleanly.
                    stmt.setString(paramIndex, MTRON.write(value));
                } else {
                    stmt.setString(paramIndex, value.toString());
                }
            }
            case Types.DATE -> {
                stmt.setDate(paramIndex, toSqlDate(value));
            }
            case Types.TIME -> {
                stmt.setTime(paramIndex, toSqlTime(value));
            }
            case Types.TIMESTAMP -> {
                stmt.setTimestamp(paramIndex, toSqlTimestamp(value));
            }
            default -> stmt.setString(paramIndex, value.toString());
        }
    }

    // ---- bulk ResultSet conversion helpers ----------------------------------

    public static List<Rec> readAllAsRec(final ResultSet rs) throws SQLException {
        return readLimitedAsRec(rs, Integer.MAX_VALUE);
    }

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
        return rec(rowData, REC_TID, null);
    }

    public static Objs readAllAsRecObjs(final ResultSet rs) throws SQLException {
        Obj result = objs0();
        for (final Rec row : readLimitedAsRec(rs, Integer.MAX_VALUE)) {
            result = result.append(row);
        }
        return result.asObjs();
    }

    public static List<Lst> readAllAsLst(final ResultSet rs) throws SQLException {
        return readLimitedAsLst(rs, Integer.MAX_VALUE);
    }

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
            rows.add(rec(rowData, REC_TID, null));
            count++;
        }
        return rows;
    }

    public static Obj readLimitedAsRecObjs(final ResultSet rs, final int limit) throws SQLException {
        Obj result = objs0();
        for (final Rec row : readLimitedAsRec(rs, limit)) {
            result = result.append(row);
        }
        return result;
    }

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
            rows.add(lst(rowData, LST_TID, null));
            count++;
        }
        return rows;
    }

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
                    readMaybeJSON(rs.getString(columnIndex));
            case Types.DATE, Types.TIME, Types.TIMESTAMP -> readDateTime(rs, columnIndex);
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> str(rs.getString(columnIndex));
            default -> readMaybeJSON(value.toString());
        };
    }
}
