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

package studio.phaseshift.metatron.isa.tble;

import studio.phaseshift.metatron.algebra.rewrite.CommonRewrites;
import studio.phaseshift.metatron.algebra.rewrite.RewriteBuilder;
import studio.phaseshift.metatron.furi.DataPath;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSimpleJSONSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjSQLSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.web.webHelper;
import studio.phaseshift.metatron.util.MTronException;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
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
import static studio.phaseshift.metatron.isa.mach.machInstSet.FILE_TID;
import static studio.phaseshift.metatron.isa.mach.machInstSet.FILE_TYPE;
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
        final BiPredicate<tbleSpace, List<Inst>> tableGuard = (space, matches) -> {
            final Obj ref = matches.getFirst().arg(0);
            if (!ref.isUri()) return true;
            final DataPath dp = DataPath.of(ref.uriValue());
            if (!dp.hasCollection() || dp.collectionIsWildcard()) return false;
            return TableStoreUtil.isTableCollection(space, dp.collection());
        };

        final BiPredicate<tbleSpace, List<Inst>> kvGuard = (space, matches) -> {
            final Obj ref = matches.getFirst().arg(0);
            if (!ref.isUri()) return false;
            final DataPath dp = DataPath.of(f("-").extend(ref.uriValue()));
            if (!dp.hasCollection() || dp.collectionIsWildcard()) return false;
            if (TableStoreUtil.isTableCollection(space, dp.collection()))
                return false;
            return KVStoreUtil.translateKVPatternToSQL(ref.uriValue()) != null;
        };

        this.jvm().putAll(mutableMap(
                uri(CONST), lst(
                        docWrap(rec(mutableMap(
                                                uri("llm_chat_schema"),
                                                instC(M_ISA_INST_TID.dom(ALL_STAR).rng(ALL_STAR), lst(), (lhs, inst) -> MTronException.wrap(() -> str(new String(new BufferedInputStream(Objects.requireNonNull(tbleInstSet.class.getResourceAsStream("llm_messages_schema.sql"))).readAllBytes()))))),
                                        REC_TID, TBLE_ISA_TID.extend("helper")),
                                "a collection of tble related utilities")),
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
                        docWrap(instC(SQL_INST_TID.dom(TBLE_SPACE_TID).rng(REC_TID.maybeSome()), lst(STR_TYPE), (lhs, inst) ->
                                    MTronException.wrap(() -> lhs.<tbleSpace>as().sql(inst.arg(0).strValue()))
                                ), "a table space typically backed by an sql-compliant relational database",
                                "a result set as a stream of rows in mtron",
                                mutableMap(jnt(0), "an sql query"),
                                "query a relational database in native sql and yield an mtron mapped result set",
                                "*/sys/space/netflix.sql('SELECT * FROM movie WHERE runtime < ${*next_event} - time(now)') [-- str templates are useful --]"))),
                uri(REWRITE), lst(
                        // Optimize: *table.count() → SELECT COUNT(*)
                        docWrap(CommonRewrites.countRewrite(
                                tbleSpace.class,
                                TBLE_ISA_REWRITE_TID.extend("sql_count"),
                                (space, dp) -> {
                                    final String tableName = dp.collection();
                                    try (final Statement stmt = space.sjvm().createStatement();
                                         final ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
                                        return rs.next() ? (long) rs.getInt(1) : 0L;
                                    } catch (SQLException e) {
                                        if (e.getErrorCode() == 1054)
                                            return 0L;
                                        throw MTronException.of(e);
                                    }
                                },
                                tableGuard
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages native SELECT COUNT(*) to count rows in a table"),

                        // Optimize: *table.sum() → SELECT SUM(*)
                        docWrap(CommonRewrites.sumRewrite(
                                tbleSpace.class,
                                TBLE_ISA_REWRITE_TID.extend("sql_sum"),
                                (space, dp) -> {
                                    final String tableName = dp.collection();
                                    if (!dp.hasField()) return 0L;
                                    final String columnName = dp.field();
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
                                },
                                tableGuard
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages native SELECT SUM(column) to sum entries in a table column"),
                        // Optimize: *table.mean() → SELECT AVG(*)
                        docWrap(CommonRewrites.meanRewrite(
                                tbleSpace.class,
                                TBLE_ISA_REWRITE_TID.extend("sql_mean"),
                                (space, dp) -> {
                                    final String tableName = dp.collection();
                                    if (!dp.hasField()) return 0.0;
                                    final String columnName = dp.field();
                                    final String query = "SELECT AVG(" + columnName + ") FROM " + tableName;
                                    LOG.debug("sql_mean %s", query);
                                    try (final Statement stmt = space.sjvm().createStatement();
                                         final ResultSet rs = stmt.executeQuery(query)) {
                                        return rs.next() ? rs.getDouble(1) : 0.0;
                                    } catch (SQLException e) {
                                        throw MTronException.of(e);
                                    }
                                },
                                tableGuard
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages native SELECT AVG(column) to average entries in a table column"),
                        docWrap(CommonRewrites.limitRewrite(
                                tbleSpace.class,
                                TBLE_ISA_REWRITE_TID.extend("sql_limit"),
                                (space, dp, limit) -> {
                                    final String tableName = dp.collection();
                                    final String sql = "SELECT * FROM " + tableName + " LIMIT " + limit;
                                    try (final Statement stmt = space.sjvm().createStatement();
                                         final ResultSet rs = stmt.executeQuery(sql)) {

                                        // Discover primary key columns for VID construction
                                        final DatabaseMetaData dbMeta = space.sjvm().getMetaData();
                                        final List<String> pkColumns = new ArrayList<>();
                                        try (final ResultSet pkRs = dbMeta.getPrimaryKeys(null, null, tableName)) {
                                            while (pkRs.next()) {
                                                pkColumns.add(pkRs.getString("COLUMN_NAME"));
                                            }
                                        }

                                        // Read rows and stamp routable VIDs for space routing
                                        final Objs rows = objs0();
                                        while (rs.next()) {
                                            final Rec rawRow = ObjSQLSerializer.readCurrentAsRec(rs);
                                            final Rec coerced = rawRow;
                                            /*final Rec coerced = space.existingTableSchema != null
                                                    ? space.existingTableSchema.coerceRow(tableName, rawRow)
                                                    : rawRow;*/
                                            final fURI rowVID = Space.Helper.routeToSpace(
                                                    pkColumns.isEmpty()
                                                            ? space.vid().extend(tableName).extend(coerced.at(uri("id")).toString())
                                                            : pkColumns.stream()
                                                            .map(col -> coerced.at(uri(col)).toString())
                                                            .reduce(space.vid().extend(tableName),
                                                                    (vid, seg) -> vid.extend(seg),
                                                                    (a, b) -> b),
                                                    space.routes());
                                            rows.append(coerced.selfVID(rowVID));
                                        }
                                        return rows.asObjs();
                                    } catch (SQLException e) {
                                        if (e.getErrorCode() == 1054)
                                            return noobj();
                                        throw MTronException.of(e, "%s", sql);
                                    } catch (final Exception e) {
                                        throw MTronException.of(e, "%s", sql);
                                    }
                                },
                                tableGuard
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages native SELECT ... LIMIT to take first n rows from a table"),

                        // Optimize: *table.has() → SELECT EXISTS(SELECT 1 FROM table LIMIT 1)
                        docWrap(CommonRewrites.hasRewrite(
                                tbleSpace.class,
                                TBLE_ISA_REWRITE_TID.extend("sql_has"),
                                (space, dp) -> {
                                    final String tableName = dp.collection();
                                    try (final Statement stmt = space.sjvm().createStatement();
                                         final ResultSet rs = stmt.executeQuery("SELECT EXISTS(SELECT 1 FROM " + tableName + " LIMIT 1)")) {
                                        return rs.next() && rs.getBoolean(1);
                                    } catch (SQLException e) {
                                        if (e.getErrorCode() == 1054)
                                            return false;
                                        throw MTronException.of(e);
                                    }
                                },
                                tableGuard
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages native SELECT EXISTS to check if table has any rows"),

                        // Optimize: *table.where([col=>val]) → SELECT * FROM table WHERE col = val
                        docWrap(CommonRewrites.whereRewrite(
                                tbleSpace.class,
                                TBLE_ISA_REWRITE_TID.extend("sql_where"),
                                (space, dp, sqlWhere) -> {
                                    if (!dp.hasCollection())
                                        throw MTronException.of("uri must contain a table reference: $s", dp);
                                    final String tableName = dp.collection();
                                    final String sql = "SELECT * FROM " + tableName + " WHERE " + sqlWhere;
                                    try (final Statement stmt = space.sjvm().createStatement();
                                         final ResultSet rs = stmt.executeQuery(sql)) {

                                        // Discover primary key columns for VID construction
                                        final DatabaseMetaData dbMeta = space.sjvm().getMetaData();
                                        final List<String> pkColumns = new ArrayList<>();
                                        try (final ResultSet pkRs = dbMeta.getPrimaryKeys(null, null, tableName)) {
                                            while (pkRs.next()) {
                                                pkColumns.add(pkRs.getString("COLUMN_NAME"));
                                            }
                                        }

                                        // Read rows and stamp routable VIDs for space routing
                                        final Objs rows = objs0();
                                        while (rs.next()) {
                                            final Rec rawRow = ObjSQLSerializer.readCurrentAsRec(rs);
                                            final Rec coerced = rawRow; /*space.existingTableSchema != null
                                                    ? space.existingTableSchema.coerceRow(tableName, rawRow)
                                                    : rawRow;*/
                                            final fURI rowVID = Space.Helper.routeToSpace(
                                                    pkColumns.isEmpty()
                                                            ? space.vid().extend(tableName).extend(coerced.at(uri("id")).toString())
                                                            : pkColumns.stream()
                                                            .map(col -> coerced.at(uri(col)).toString())
                                                            .reduce(space.vid().extend(tableName),
                                                                    (vid, seg) -> vid.extend(seg),
                                                                    (a, b) -> b),
                                                    space.routes());
                                            rows.append(coerced.selfVID(rowVID));
                                        }
                                        return rows.asObjs();
                                    } catch (SQLException e) {
                                        if (e.getErrorCode() == 1054)
                                            return noobj();
                                        throw MTronException.of(e, "%s", sql);
                                    } catch (final Exception e) {
                                        throw MTronException.of(e, "%s", sql);
                                    }
                                },
                                tableGuard
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages native SELECT ... WHERE to filter rows in a table"),

                        // Optimize: sql_where.count() → SELECT COUNT(*) FROM table WHERE ...
                        docWrap(CommonRewrites.whereCountRewrite(
                                tbleSpace.class,
                                TBLE_ISA_REWRITE_TID.extend("sql_where"),
                                TBLE_ISA_REWRITE_TID.extend("sql_where_count"),
                                (space, dp, sqlWhere) -> {
                                    final String tableName = dp.collection();
                                    final String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE " + sqlWhere;
                                    try (final Statement stmt = space.sjvm().createStatement();
                                         final ResultSet rs = stmt.executeQuery(sql)) {
                                        return rs.next() ? rs.getLong(1) : 0L;
                                    } catch (SQLException e) {
                                        if (e.getErrorCode() == 1054)
                                            return 0L;
                                        throw MTronException.of(e, "%s", sql);
                                    }
                                },
                                tableGuard
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages native SELECT COUNT(*) ... WHERE to count filtered rows"),

                        // Optimize: sql_where.take(n) → SELECT * FROM table WHERE ... LIMIT n
                        docWrap(CommonRewrites.whereLimitRewrite(
                                tbleSpace.class,
                                TBLE_ISA_REWRITE_TID.extend("sql_where"),
                                TBLE_ISA_REWRITE_TID.extend("sql_where_limit"),
                                (space, dp, sqlWhere, limit) -> {
                                    final String tableName = dp.collection();
                                    final String sql = "SELECT * FROM " + tableName + " WHERE " + sqlWhere + " LIMIT " + limit;
                                    try (final Statement stmt = space.sjvm().createStatement();
                                         final ResultSet rs = stmt.executeQuery(sql)) {

                                        // Discover primary key columns for VID construction
                                        final DatabaseMetaData dbMeta = space.sjvm().getMetaData();
                                        final List<String> pkColumns = new ArrayList<>();
                                        try (final ResultSet pkRs = dbMeta.getPrimaryKeys(null, null, tableName)) {
                                            while (pkRs.next()) {
                                                pkColumns.add(pkRs.getString("COLUMN_NAME"));
                                            }
                                        }

                                        // Read rows and stamp routable VIDs for space routing
                                        final Objs rows = objs0();
                                        while (rs.next()) {
                                            final Rec rawRow = ObjSQLSerializer.readCurrentAsRec(rs);
                                            final Rec coerced = rawRow;
                                            final fURI rowVID = Space.Helper.routeToSpace(
                                                    pkColumns.isEmpty()
                                                            ? space.vid().extend(tableName).extend(coerced.at(uri("id")).toString())
                                                            : pkColumns.stream()
                                                            .map(col -> coerced.at(uri(col)).toString())
                                                            .reduce(space.vid().extend(tableName),
                                                                    (vid, seg) -> vid.extend(seg),
                                                                    (a, b) -> b),
                                                    space.routes());
                                            rows.append(coerced.selfVID(rowVID));
                                        }
                                        return rows.asObjs();
                                    } catch (SQLException e) {
                                        if (e.getErrorCode() == 1054)
                                            return noobj();
                                        throw MTronException.of(e, "%s", sql);
                                    } catch (final Exception e) {
                                        throw MTronException.of(e, "%s", sql);
                                    }
                                },
                                tableGuard
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages native SELECT ... WHERE ... LIMIT to filter and limit rows in a table"),

                        // Optimize: from(table/+).>>{col1,col2} → SELECT col1, col2 FROM table
                        docWrap(CommonRewrites.selectRewrite(
                                tbleSpace.class,
                                TBLE_ISA_REWRITE_TID.extend("sql_select"),
                                (space, dp, columns) -> {
                                    final String tableName = dp.collection();
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
                                },
                                tableGuard
                        ), "*table/+>>{name,age}", "sql_select(table, [name,age])", Map.of(), "leverages native SELECT col1, col2 FROM table for projections"),

                        // Optimize: *kvPath/+.count() → SELECT COUNT(*) FROM kv_store WHERE furi LIKE ...
                        docWrap(RewriteBuilder.forDatabase(tbleSpace.class)
                                .tid(TBLE_ISA_REWRITE_TID.extend("kv_count"))
                                .rng(INT_TID)
                                .match(FROM_INST_TID, COUNT_INST_TID)
                                .matchSpacePredicate(kvGuard)
                                .optimizeWithURI("kv_from_count", (space, dp, expandedURI, coeff) -> {
                                    final fURI stored = Space.Helper.routeFromSpace(expandedURI, space.routes());
                                    final String whereClause = KVStoreUtil.translateKVPatternToSQL(stored);
                                    if (whereClause == null) throw MTronException.of("untranslatable KV pattern: %s", stored);
                                    final String sql = "SELECT COUNT(*) FROM kv_store WHERE " + whereClause;
                                    try (final Statement stmt = space.sjvm().createStatement();
                                         final ResultSet rs = stmt.executeQuery(sql)) {
                                        final long count = rs.next() ? rs.getLong(1) : 0L;
                                        return jnt(count).c(c -> c.mult((cInt) coeff));
                                    } catch (SQLException e) {
                                        throw MTronException.of(e, "%s", sql);
                                    }
                                })
                                .build(),
                                "/col/+, /col/+/+, /col/*",
                                "kv_count(kv_store, WHERE furi LIKE ...)",
                                Map.of(),
                                "leverages native SELECT COUNT(*) FROM kv_store with LIKE-based pattern matching"),

                        // Optimize: *kvPath/+.take(n) → SELECT * FROM kv_store WHERE furi LIKE ... LIMIT n
                        docWrap(new KVLimitRewriteBuilder(TBLE_ISA_REWRITE_TID.extend("kv_limit"))
                                .match(FROM_INST_TID, TAKE_INST_TID)
                                .matchSpacePredicate(kvGuard)
                                .build(),
                                "/col/+.take(5)",
                                "kv_limit(kv_store, WHERE furi LIKE ..., LIMIT n)",
                                Map.of(),
                                "leverages native SELECT ... FROM kv_store ... LIMIT n to avoid loading all KV entries")

                )));
        docWrap(this,
                "relational tables, typed rows, and SQL rewrites within the metatron",
                "*acme:customer.where[person=>[name=>_=>age=>?>29]]");
        super.setup();
    }

    // =========================================================================
    //  Inner builder: kv_limit rewrite
    // =========================================================================

    private static final class KVLimitRewriteBuilder extends RewriteBuilder<tbleSpace> {
        KVLimitRewriteBuilder(final fURI rewriteTid) {
            super(tbleSpace.class);
            this.rewriteTid = rewriteTid;
            this.resultTid = ALL_STAR;
            this.rewriteName = "kv_from_limit";
            this.optimization = (space, dp, coeff) -> null; // dummy — overrides createRewriteFunction
        }

        @Override
        protected java.util.function.Function<java.util.Map<Inst, Inst>, java.util.List<Inst>> createRewriteFunction() {
            return map -> {
                final java.util.List<Inst> matchedInsts = new java.util.ArrayList<>(map.values());
                final Inst fromInst = matchedInsts.get(0);
                final Inst takeInst = matchedInsts.get(1);
                final long limitValue = takeInst.arg(0).asInt().jvm();

                final fURI oldfURI = fromInst.arg(0).asUri().uriValue();
                final Space space = Router.global().getSpaceFor(oldfURI);

                if (!this.spaceType.isInstance(space))
                    return matchedInsts.stream().map(Obj::asInst).toList();

                final tbleSpace typedSpace = this.spaceType.cast(space);

                if (this.matchSpacePredicate != null
                        && !this.matchSpacePredicate.test(typedSpace, matchedInsts))
                    return matchedInsts.stream().map(Obj::asInst).toList();

                final fURI expandedfURI = space.redirect(oldfURI, true);
                final fURI stored = Space.Helper.routeFromSpace(expandedfURI, typedSpace.routes());
                final String whereClause = KVStoreUtil.translateKVPatternToSQL(stored);
                if (whereClause == null)
                    return matchedInsts.stream().map(Obj::asInst).toList();

                final String sql = "SELECT furi, obj FROM kv_store WHERE "
                        + whereClause + " LIMIT " + limitValue;

                return java.util.List.of(instC(
                        this.rewriteTid.dom(ALL.zero()).rng(this.resultTid),
                        lst(uri(expandedfURI), jnt(limitValue)),
                        (lhs, inst) -> {
                            try (final Statement stmt = typedSpace.sjvm().createStatement();
                                 final ResultSet rs = stmt.executeQuery(sql)) {
                                final Objs rows = objs0();
                                while (rs.next()) {
                                    final fURI rowFuri = f(rs.getString("furi"));
                                    final Obj deserialized = ObjSimpleJSONSerializer.parse(rs.getString("obj"));
                                    final fURI rowVID = Space.Helper.routeToSpace(rowFuri, typedSpace.routes());
                                    rows.append(deserialized.selfVID(rowVID));
                                }
                                return rows.asObjs();
                            } catch (SQLException e) {
                                throw MTronException.of(e, "%s", sql);
                            }
                        }
                ));
            };
        }
    }
}
