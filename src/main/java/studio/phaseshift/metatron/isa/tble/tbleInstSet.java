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
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Type;

import java.util.Set;

import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(tid = "/m/tble")
public class tbleInstSet extends AbstractInstSet {

    public static final fURI TBLE_ISA_TID = M_ISA_TID.extend("tble");
    public static final fURI INST_TID = TBLE_ISA_TID.extend("inst");
    public static final fURI ROW_TID = TBLE_ISA_TID.extend("row");
    public static final fURI TABLE_TID = TBLE_ISA_TID.extend("table");


    public static final Type ROW_TYPE = Type.Builder.build()
            .tid(REC_TID)
            .vid(ROW_TID)
            .create();

    public static final Type TABLE_TYPE = Type.Builder.build()
            .tid(REC_TID.maybeSome())
            .vid(TABLE_TID)
            .predicate(isa_(T(ROW_TID.maybeSome())).tryToInst())
            .create();


    public tbleInstSet() {
        super(TBLE_ISA_TID, TBLE_ISA_TID);
    }

    @Override
    public Set<Type> types() {
        return Set.of(tbleSpace.TABL_SPACE_TYPE, ROW_TYPE, TABLE_TYPE);
    }

    @Override
    public Set<Inst> insts() {
        return Set.of();
    }
}
