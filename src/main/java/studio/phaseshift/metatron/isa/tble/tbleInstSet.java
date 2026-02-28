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
import studio.phaseshift.metatron.isa.m.type.Type;

import java.util.Set;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class tbleInstSet extends AbstractInstSet {

    public static tbleInstSet create() {
        return new tbleInstSet(fURI.fnull);
    }

    public static final fURI TABL_INSTSET_TID = f("/tabl");
    public static final fURI INST_TID = TABL_INSTSET_TID.extend("inst");
    public static final fURI TABLE_TID = TABL_INSTSET_TID.extend("table");

    public static final Type TABLE_TYPE = T(TABLE_TID, isa_(lst(rec(), lst())));


    public tbleInstSet(final fURI vid) {
        super(TABL_INSTSET_TID, vid);
    }

    @Override
    public Set<Type> types() {
        return Set.of(tbleSpace.TABL_TYPE, TABLE_TYPE);
    }

    @Override
    public Set<Inst> insts() {
        return Set.of();
    }
}
