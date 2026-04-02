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
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Rel;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.util.MTronException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.TABLE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.tble.tbleSpace.TABL_SPACE_TYPE;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(tid = "/m/tble")
public class tbleInstSet extends AbstractInstSet {

    public static final fURI TBLE_ISA_TID = M_ISA_TID.extend("tble");
    public static final fURI TBLE_ISA_INST_TID = TBLE_ISA_TID.extend("inst");
    public static final fURI TBLE_ISA_REWRITE_TID = TBLE_ISA_INST_TID.extend("rewrite");
    public static final fURI LST_ROW_TID = TBLE_ISA_TID.extend("lrow");
    public static final fURI REC_ROW_TID = TBLE_ISA_TID.extend("rrow");
    public static final fURI TABLE_TID = TBLE_ISA_TID.extend("table");

    static final Set<Inst> TBLE_ISA_INSTS = new LinkedHashSet<>();
    static final Set<Type> TBLE_ISA_TYPES = new LinkedHashSet<>();

    public static final Type LST_ROW_TYPE = Type.Builder.build()
            .tid(LST_TID)
            .vid(LST_ROW_TID)
            .create(TBLE_ISA_TYPES, TBLE_ISA_INSTS);

    public static final Type REC_ROW_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(REC_ROW_TID)
            .isaPredicate(rec(URI_TYPE, T(ALL)))
            .create(TBLE_ISA_TYPES, TBLE_ISA_INSTS);

    public static final Type TABLE_TYPE = Type.Builder.build()
            .tid(LST_TID.maybeSome())
            .vid(TABLE_TID)
            .predicate(isa_(T(LST_ROW_TID.maybeSome())).tryToInst())
            .create(TBLE_ISA_TYPES, TBLE_ISA_INSTS);


    public tbleInstSet() {
        super(TBLE_ISA_TID, TBLE_ISA_TID);
    }

    @Override
    public Set<Type> types() {
        TBLE_ISA_TYPES.add(TABL_SPACE_TYPE);
        return TBLE_ISA_TYPES;
    }

    @Override
    public Set<Inst> insts() {
        TBLE_ISA_INSTS.add(instC(AS_INST_TID.dom(LST_ROW_TID).rng(REC_ROW_TID), lst(REC_ROW_TYPE), (lhs, inst) -> lhs.asRec().at(uri(TABLE))));
        TBLE_ISA_INSTS.add(instC(AS_INST_TID.dom(REC_ROW_TID).rng(LST_ROW_TID), lst(LST_ROW_TYPE), (lhs, inst) -> lst(lhs.asRec().elements().map(Rel::second).toList(), LST_ROW_TID, null)));
        return TBLE_ISA_INSTS;
    }

    @Override
    public Set<Inst> rewrites() {
        return new LinkedHashSet<>(List.of(
                // Optimize: *table.count() → SELECT COUNT(*)
                CommonRewrites.countRewrite(
                        tbleSpace.class,
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
                ),

                // Optimize: *table.sum() → SELECT SUM(*)
                CommonRewrites.sumRewrite(
                        tbleSpace.class,
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
                ),

                // Optimize: *table.mean() → SELECT AVG(*)
                CommonRewrites.meanRewrite(
                        tbleSpace.class,
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
                )
        ));
    }
}
