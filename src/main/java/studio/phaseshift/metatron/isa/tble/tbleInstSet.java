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
import studio.phaseshift.metatron.util.MTronException;

import java.sql.*;
import java.util.Map;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
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
import static studio.phaseshift.metatron.isa.tble.tabledbSpace.*;
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
            .tid(LST_TID.maybeSome()) // TODO: stream not lst
            .vid(TABLE_TID)
            .predicate(isa_(T(LST_ROW_TID.maybeSome())).tryToInst())
            .create();


    public tbleInstSet() {
        super(mutableMap(uri(PATTERN), uri(TBLE_ISA_TID.extend(ALL))), TBLE_ISA_TID, TBLE_ISA_TID);
    }

    @Override
    public void setup() {
        this.jvm().putAll(mutableMap(
                uri(PATTERN), uri(TBLE_ISA_TID.extend(ALL)),
                uri(TYPE), lst(
                        docWrap(LST_ROW_TYPE, "a table row indexed by column number"),
                        docWrap(REC_ROW_TYPE, "a table row indexed by column name"),
                        docWrap(TABLE_TYPE, "a stream of equally sized rows"),
                        docWrap(TABLE_SPACE_TYPE, "a metatron realization of a relational database")),
                uri(INST), lst(
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
                        instC(SQL_INST_TID.dom(TABLEDB_SPACE_TID).rng(REC_ROW_TID.maybeSome()), lst(STR_TYPE), (lhs, inst) -> {
                            try {
                                final Statement statement = lhs.<tabledbSpace>as().sjvm().createStatement();
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
                                                yield result.wasNull() ? NoObj.noobj() : jnt(val);
                                            }
                                            case Types.FLOAT, Types.REAL, Types.DOUBLE, Types.DECIMAL, Types.NUMERIC -> {
                                                final double val = result.getDouble(i);
                                                yield result.wasNull() ? NoObj.noobj() : real(val);
                                            }
                                            case Types.BOOLEAN, Types.BIT -> {
                                                final boolean val = result.getBoolean(i);
                                                yield result.wasNull() ? NoObj.noobj() : bool(val);
                                            }
                                            default -> {
                                                // String types, dates, binary, etc.
                                                final String val = result.getString(i);
                                                yield val == null ? NoObj.noobj() : str(val);
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
                        })),
                uri(REWRITE), lst(
                        // Optimize: *table.count() → SELECT COUNT(*)
                        docWrap(CommonRewrites.countRewrite(
                                tabledbSpace.class,
                                TBLE_ISA_REWRITE_TID.extend("sql_native_count"),
                                (space, furi) -> {
                                    final String tableName = furi.segments().getFirst();
                                    try (final Statement stmt = space.sjvm().createStatement();
                                         final ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
                                        return rs.next() ? (long) rs.getInt(1) : 0L;
                                    } catch (SQLException e) {
                                        throw MTronException.of(e);
                                    }
                                }
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages native SELECT COUNT(*) to count rows in a table"),

                        // Optimize: *table.sum() → SELECT SUM(*)
                        docWrap(CommonRewrites.sumRewrite(
                                tabledbSpace.class,
                                TBLE_ISA_REWRITE_TID.extend("sql_native_sum"),
                                (space, furi) -> {
                                    final String tableName = furi.segments().getFirst();
                                    try (final Statement stmt = space.sjvm().createStatement();
                                         final ResultSet rs = stmt.executeQuery("SELECT SUM(1) FROM " + tableName)) {
                                        return rs.next() ? rs.getLong(1) : 0L;
                                    } catch (SQLException e) {
                                        throw MTronException.of(e);
                                    }
                                }
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages native SELECT SUM(*) to sum entries in a table column"),
                        // Optimize: *table.mean() → SELECT AVG(*)
                        docWrap(CommonRewrites.meanRewrite(
                                tabledbSpace.class,
                                TBLE_ISA_REWRITE_TID.extend("sql_native_mean"),
                                (space, furi) -> {
                                    final String tableName = furi.segments().getFirst();
                                    try (final Statement stmt = space.sjvm().createStatement();
                                         final ResultSet rs = stmt.executeQuery("SELECT AVG(1.0) FROM " + tableName)) {
                                        return rs.next() ? rs.getDouble(1) : 0.0;
                                    } catch (SQLException e) {
                                        throw MTronException.of(e);
                                    }
                                }
                        ), "pre-rewrite code", "post-rewrite code", Map.of(), "leverages native SELECT AVG(*) to average entries in a table column")
                )));
        docWrap(this,
                "the columns, rows, and entries of the table join the metatron",
                "*acme:customer.where[person=>[name=>_=>age=>?>29]]");
        super.setup();
    }
}
