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

package studio.phaseshift.metatron.algebra;

import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;

import static studio.phaseshift.metatron.Tokens.M_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;

/**
 * The law table of {@code /m} — an EMPTY SHELL. Every process law is declared by the instruction that obeys it
 * ({@code ?decq}, through {@code InstBuilder}) and every carrier's algebra by its type ({@code mTypes.declareAlgebras}).
 * The shell stays only as {@link LawTable}'s dispatch and memo owner for {@code /m} until that layer is re-homed.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class mInstSetLawTable extends LawTable {

    public static final mInstSetLawTable INSTANCE = new mInstSetLawTable();

    private mInstSetLawTable() {
        super(M_ISA_TID);
    }

    @Override
    public Rec typeLawsUncached(final Type type) {
        return rec0();
    }
}
