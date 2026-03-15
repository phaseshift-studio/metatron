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

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.MTronException;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.TABLE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.fURI.manyMatches;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(tid = "/m/tble")
public class tbleInstSet extends AbstractInstSet {

    public static final fURI TBLE_ISA_TID = M_ISA_TID.extend("tble");
    public static final fURI INST_TID = TBLE_ISA_TID.extend("inst");
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
            .tid(LST_TID.maybeSome())
            .vid(TABLE_TID)
            .predicate(isa_(T(LST_ROW_TID.maybeSome())).tryToInst())
            .create();


    public tbleInstSet() {
        super(TBLE_ISA_TID, TBLE_ISA_TID);
    }

    @Override
    public Set<Type> types() {
        return Set.of(tbleSpace.TABL_SPACE_TYPE, REC_ROW_TYPE, LST_ROW_TYPE, TABLE_TYPE);
    }

    @Override
    public Set<Inst> insts() {
        return new LinkedHashSet<>(List.of(
                instC(AS_INST_TID.dom(LST_ROW_TID).rng(REC_ROW_TID), lst(REC_ROW_TYPE), (lhs, inst) -> lhs.asRec().at(uri(TABLE))),
                instC(AS_INST_TID.dom(REC_ROW_TID).rng(LST_ROW_TID), lst(LST_ROW_TYPE), (lhs, inst) -> lst(lhs.asRec().elements().map(Rel::second).toList(), LST_ROW_TID, null)
                )));
    }

    @Override
    public Set<Inst> rewrites() {
        return new LinkedHashSet<>(List.of(
                InstSet.Helper.rewriter(f("sql_native_count_rewrite"), code -> {
                    final List<fURI> instTIDs = code.insts().stream().map(Obj::tid).toList();
                    if (manyMatches(instTIDs, List.of(FROM_INST_TID, COUNT_INST_TID))) {
                        final fURI oldfURI = code.codeValue().getFirst().arg(0).asUri().uriValue();
                        final Space sqlSpace = Router.global().getSpace(oldfURI);
                        if (sqlSpace instanceof tbleSpace) {
                            return MCode.of(List.of(instC(f("sql_native_count").dom(ALL.zero()).rng(INT_TID), lst(), (_, _) -> {
                                final fURI expandedfURI = sqlSpace.rewrite(oldfURI, true);
                                LOG.debug("evaluating native sql query on table %s in space %s", expandedfURI, sqlSpace);
                                try (final Statement stmt = ((tbleSpace) sqlSpace).sjvm().createStatement()) {
                                    final ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + expandedfURI.segments().getFirst());
                                    if (rs.next()) {
                                        return jnt(rs.getInt(1)).c(c -> c.mult(code.codeValue().getLast().c()));
                                    } else {
                                        throw MTronException.of("failed to evaluate native SQL query: %s", stmt);
                                    }
                                } catch (final Exception e) {
                                    throw MTronException.of(e);
                                }
                            })));
                        }
                    }
                    return code;
                })));
    }
}
