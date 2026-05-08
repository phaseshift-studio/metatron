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

package studio.phaseshift.metatron.isa.tble;

import studio.phaseshift.metatron.algebra.rewrite.CommonRewrites;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSQLSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Str.STR_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs0;
import static studio.phaseshift.metatron.isa.m.type.impl.MReal.real;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.tble.tbleSpace.*;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(vid = "/m/tble")
public class tbleInstSet extends AbstractInstSet {

    public static final fURI TBLE_ISA_TID = M_ISA_TID.extend("tble");
    public static final fURI TBLE_ISA_INST_TID = TBLE_ISA_TID.extend("inst");
    public static final fURI TBLE_ISA_REWRITE_TID = TBLE_ISA_INST_TID.extend("rewrite");
    public static final fURI LST_ROW_TID = TBLE_ISA_TID.extend("lrow");
    public static final fURI REC_ROW_TID = TBLE_ISA_TID.extend("rrow");
    public static final fURI TABLE_TID = TBLE_ISA_TID.extend("table");


    public static final Type LST_ROW_TYPE = Type.Builder.build()
            .tid(LST_TID)
            .vid(LST_ROW_TID)
            .create();

    public static final Type REC_ROW_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(REC_ROW_TID)
            .isaPredicate(rec(URI_TYPE, T(ALL)))
            .create();

    public static final Type TABLE_TYPE = Type.Builder.build()
            .tid(URI_TID)
            .vid(TABLE_TID)
            .predicate(isa_(T(LST_ROW_TID.maybeSome())).tryToInst())
            .create();


    public tbleInstSet() {
        super(mutableMap(uri(PATTERN), uri(TBLE_ISA_TID.extend(ALL))), INSTSET_TID, TBLE_ISA_TID);
    }

    @Override
    public void setup() {
        this.jvm().putAll(mutableMap(
                uri(TYPE), lst(
                        docWrap(LST_ROW_TYPE, "a table row indexed by column number"),
                        docWrap(REC_ROW_TYPE, "a table row indexed by column name"),
                        docWrap(TABLE_TYPE, "a stream of equally wide rows"),
                        docWrap(TBLE_SPACE_TYPE, "a metatron realization of a relational database")),
                uri(INST), lst(Stream.of(
                        docWrap(instC(AS_INST_TID.dom(LST_ROW_TID).rng(REC_ROW_TID), lst(REC_ROW_TYPE), (lhs, inst) -> lhs.asRec().at(uri(TABLE))),
                                "a table row indexed by column number",
                                "a table row indexed by column name",
                                Map.of(),
                                "maps a lst row to a rec row"),
                        docWrap(instC(AS_INST_TID.dom(REC_ROW_TID).rng(LST_ROW_TID), lst(LST_ROW_TYPE), (lhs, inst) -> lst(lhs.asRec().elements().map(Rel::second).toList(), LST_ROW_TID, null)),
                                "a table row indexed by column name",
                                "a table row indexed by column number",
                                Map.of(),
                                "maps a rec row to a lst row"),
                        docWrap(instC(SQL_INST_TID.dom(TBLE_SPACE_TID).rng(REC_TID.maybeSome()), lst(STR_TYPE), (lhs, inst) -> {
                                    try {
                                        final Statement statement = lhs.<tbleSpace>as().sjvm().createStatement();
                                        final ResultSet result = statement.executeQuery(inst.arg(0).strValue());
                                        final ResultSetMetaData metadata = result.getMetaData();
                                        Obj objs = objs0();
                                        while (result.next()) {
                                            final Rec row = rec();
                                            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                                                final int sqlType = metadata.getColumnType(i);
                                                final String columnName = metadata.getColumnName(i);
                                                // Use typed getters based on SQL type to avoid database-specific objects
                                                final Obj value = switch (sqlType) {
                                                    case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> {
                                                        final long val = result.getLong(i);
                                                        yield result.wasNull() ? noobj() : jnt(val);
                                                    }
                                                    case Types.FLOAT, Types.REAL, Types.DOUBLE, Types.DECIMAL, Types.NUMERIC -> {
                                                        final double val = result.getDouble(i);
                                                        yield result.wasNull() ? noobj() : real(val);
                                                    }
                                                    case Types.BOOLEAN, Types.BIT -> {
                                                        final boolean val = result.getBoolean(i);
                                                        yield result.wasNull() ? noobj() : bool(val);
                                                    }
                                                    default -> {
                                                        // String types, dates, binary, etc.
                                                        final String val = result.getString(i);
                                                        yield val == null ? noobj() : str(val);
                                                    }
                                                };
                                                row.at(uri(columnName), value, MUTABLE);
                                            }
                                            objs = objs.append(row);
                                        }
                                        return objs;

                                    } catch (final Exception e) {
                                        throw MTronException.of(e);
                                    }
                                }), "a table space typically backed by an sql-compliant relational database",
                                "a result set as a stream of rows in mtron",
                                Map.of(jnt(0), "an sql query"),
                                "query a relational database in native sql and yield an mtron mapped result set",
                                "*/sys/space/netflix.sql('SELECT * FROM movie WHERE runtime < ${*next_event - time(now)') [-- str templates are useful --]"))),
                uri(REWRITE), lst(
                        // Optimize: *table.count() → SELECT COUNT(*)
                        docWrap(CommonRewrites.countRewrite(
                                tbleSpace.class,
                                TBLE_ISA_REWRITE_TID.extend("sql_count"),
                                (space, furi) -> {
                                    final String tableName = furi.segments().getFirst();
                                    try (final Statement stmt = space.sjvm().createStatement();
                                         final ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
                                        return rs.next() ? (long) rs.getInt(1) : 0L;
                                    } catch (SQLException e) {
                                        if (e.getErrorCode() == 1054)
                                            return 0L;
                                        throw MTronException.of(e);
                                    }
                                }
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages native SELECT COUNT(*) to count rows in a table"),

                        // Optimize: *table.sum() → SELECT SUM(*)
                        docWrap(CommonRewrites.sumRewrite(
                                tbleSpace.class,
                                TBLE_ISA_REWRITE_TID.extend("sql_sum"),
                                (space, furi) -> {
                                    final String tableName = furi.segments().getFirst();
                                    final String columnName = furi.segments().get(2);
                                    final String query = "SELECT SUM(" + columnName + ") FROM " + tableName;
                                    LOG.debug("sql_sum query %s", query);
                                    try (final Statement stmt = space.sjvm().createStatement();
                                         final ResultSet rs = stmt.executeQuery(query)) {
                                        return rs.next() ?
                                                (rs.getMetaData().getColumnType(1) == JDBCType.DOUBLE.getVendorTypeNumber() ?
                                                        rs.getDouble(1) :
                                                        rs.getLong(1)) :
                                                0L;
                                    } catch (SQLException e) {
                                        if (e.getErrorCode() == 1054)
                                            return 0L;
                                        throw MTronException.of(e);
                                    }
                                }
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages native SELECT SUM(column) to sum entries in a table column"),
                        // Optimize: *table.mean() → SELECT AVG(*)
                        docWrap(CommonRewrites.meanRewrite(
                                tbleSpace.class,
                                TBLE_ISA_REWRITE_TID.extend("sql_mean"),
                                (space, furi) -> {
                                    final String tableName = furi.segments().getFirst();
                                    final String columnName = furi.segments().get(2);
                                    final String query = "SELECT AVG(" + columnName + ") FROM " + tableName;
                                    LOG.debug("sql_mean %s", query);
                                    try (final Statement stmt = space.sjvm().createStatement();
                                         final ResultSet rs = stmt.executeQuery(query)) {
                                        return rs.next() ? rs.getDouble(1) : 0.0;
                                    } catch (SQLException e) {
                                        throw MTronException.of(e);
                                    }
                                }
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages native SELECT AVG(column) to average entries in a table column"),
                        docWrap(CommonRewrites.limitRewrite(
                                tbleSpace.class,
                                TBLE_ISA_REWRITE_TID.extend("sql_limit"),
                                (space, furi, limit) -> {
                                    final String tableName = furi.segments().getFirst();
                                    final String sql = "SELECT * FROM " + tableName + " LIMIT " + limit;
                                    try (final Statement stmt = space.sjvm().createStatement();
                                         final ResultSet rs = stmt.executeQuery(sql)) {
                                        return ObjSQLSerializer.readLimitedAsRecObjs(rs, (int) limit);
                                    } catch (SQLException e) {
                                        if (e.getErrorCode() == 1054)
                                            return noobj();
                                        throw MTronException.of(e, "%s", sql);
                                    } catch (final Exception e) {
                                        throw MTronException.of(e, "%s", sql);
                                    }
                                }
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages native SELECT ... LIMIT to take first n rows from a table"),

                        // Optimize: *table.has() → SELECT EXISTS(SELECT 1 FROM table LIMIT 1)
                        docWrap(CommonRewrites.hasRewrite(
                                tbleSpace.class,
                                TBLE_ISA_REWRITE_TID.extend("sql_has"),
                                (space, furi) -> {
                                    final String tableName = furi.segments().getFirst();
                                    try (final Statement stmt = space.sjvm().createStatement();
                                         final ResultSet rs = stmt.executeQuery("SELECT EXISTS(SELECT 1 FROM " + tableName + " LIMIT 1)")) {
                                        return rs.next() && rs.getBoolean(1);
                                    } catch (SQLException e) {
                                        if (e.getErrorCode() == 1054)
                                            return false;
                                        throw MTronException.of(e);
                                    }
                                }
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages native SELECT EXISTS to check if table has any rows"),

                        // Optimize: *table.where([col=>val]) → SELECT * FROM table WHERE col = val
                        docWrap(CommonRewrites.whereRewrite(
                                tbleSpace.class,
                                TBLE_ISA_REWRITE_TID.extend("sql_where"),
                                (space, furi, sqlWhere) -> {
                                    if (furi.segments().isEmpty())
                                        throw MTronException.of("uri must contain a table reference: $s", furi);
                                    final String tableName = furi.segments().getFirst();
                                    final String sql = "SELECT * FROM " + tableName + " WHERE " + sqlWhere;
                                    try (final Statement stmt = space.sjvm().createStatement();
                                         final ResultSet rs = stmt.executeQuery(sql)) {
                                        return ObjSQLSerializer.readAllAsRecObjs(rs);
                                    } catch (SQLException e) {
                                        if (e.getErrorCode() == 1054)
                                            return noobj();
                                        throw MTronException.of(e, "%s", sql);
                                    } catch (final Exception e) {
                                        throw MTronException.of(e, "%s", sql);
                                    }
                                }
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages native SELECT ... WHERE to filter rows in a table"),

                        // Optimize: sql_where.count() → SELECT COUNT(*) FROM table WHERE ...
                        docWrap(CommonRewrites.whereCountRewrite(
                                tbleSpace.class,
                                TBLE_ISA_REWRITE_TID.extend("sql_where"),
                                TBLE_ISA_REWRITE_TID.extend("sql_where_count"),
                                (space, furi, sqlWhere) -> {
                                    final String tableName = furi.segments().getFirst();
                                    final String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE " + sqlWhere;
                                    try (final Statement stmt = space.sjvm().createStatement();
                                         final ResultSet rs = stmt.executeQuery(sql)) {
                                        return rs.next() ? rs.getLong(1) : 0L;
                                    } catch (SQLException e) {
                                        if (e.getErrorCode() == 1054)
                                            return 0L;
                                        throw MTronException.of(e, "%s", sql);
                                    }
                                }
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages native SELECT COUNT(*) ... WHERE to count filtered rows"),

                        // Optimize: from(table/+).>>{col1,col2} → SELECT col1, col2 FROM table
                        docWrap(CommonRewrites.selectRewrite(
                                tbleSpace.class,
                                TBLE_ISA_REWRITE_TID.extend("sql_select"),
                                (space, furi, columns) -> {
                                    final String tableName = furi.segments().getFirst();
                                    final String columnList = String.join(", ", columns);
                                    final String sql = "SELECT " + columnList + " FROM " + tableName;
                                    try (final Statement stmt = space.sjvm().createStatement();
                                         final ResultSet rs = stmt.executeQuery(sql)) {
                                        final java.sql.ResultSetMetaData metaData = rs.getMetaData();
                                        Obj result = objs0();
                                        while (rs.next()) {
                                            final Map<Obj, Obj> rowMap = new LinkedHashMap<>();
                                            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                                                final String colName = metaData.getColumnName(i);
                                                final Object value = rs.getObject(i);
                                                if (value != null) {
                                                    final Obj objValue = switch (metaData.getColumnType(i)) {
                                                        case java.sql.Types.BOOLEAN, java.sql.Types.BIT ->
                                                                bool(rs.getBoolean(i));
                                                        case java.sql.Types.TINYINT, java.sql.Types.SMALLINT, java.sql.Types.INTEGER, java.sql.Types.BIGINT ->
                                                                jnt(rs.getLong(i));
                                                        case java.sql.Types.REAL, java.sql.Types.FLOAT, java.sql.Types.DOUBLE, java.sql.Types.DECIMAL, java.sql.Types.NUMERIC ->
                                                                real(rs.getDouble(i));
                                                        default -> str(value.toString());
                                                    };
                                                    rowMap.put(uri(colName), objValue);
                                                }
                                            }
                                            result = result.append(rec(rowMap));
                                        }
                                        return result.asObjs();
                                    } catch (final SQLException e) {
                                        if (e.getErrorCode() == 1054)
                                            return noobj();
                                        throw MTronException.of(e, "%s", sql);
                                    }
                                }
                        ), "*table/+>>{name,age}", "sql_select(table, [name,age])", Map.of(), "leverages native SELECT col1, col2 FROM table for projections")

                )));
        docWrap(this,
                "relational tables, typed rows, and SQL rewrites within the metatron",
                "*acme:customer.where[person=>[name=>_=>age=>?>29]]");
        super.setup();
    }
}
