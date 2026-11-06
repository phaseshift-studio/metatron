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

package studio.phaseshift.metatron.isa.m.math.geo;

import studio.phaseshift.metatron.algebra.LawTable;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;

import static studio.phaseshift.metatron.isa.m.math.geo.geoInstSet.GEO_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;

/**
 * The law table of {@code /m/math/geo} — an EMPTY SHELL. The geo operations declare their laws beside themselves
 * ({@code InstBuilder} in {@code geoInstSet}); a geometry models no carrier algebra. The shell stays only as
 * {@link LawTable}'s dispatch and memo owner for {@code /m/math/geo} until that layer is re-homed.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class geoInstSetLawTable extends LawTable {

    public static final geoInstSetLawTable INSTANCE = new geoInstSetLawTable();

    private geoInstSetLawTable() {
        super(GEO_ISA_TID);
    }

    @Override
    protected Rec typeLawsUncached(final Type type) {
        return rec0();
    }
}
