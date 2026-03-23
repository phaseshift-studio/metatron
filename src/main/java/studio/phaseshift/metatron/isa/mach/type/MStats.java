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

package studio.phaseshift.metatron.isa.mach.type;

import studio.phaseshift.metatron.isa.m.type.impl.MRec;

import java.util.Map;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MStats extends MRec implements Stats {

    private final MIOStats ioStats = new MIOStats();
    private final MMonadicStats monadicStats = new MMonadicStats();

    public MStats() {
        super(Map.of(), Stats.STATS_TID, null);
    }

    public class MIOStats extends MRec implements Stats.IOStats {

        public MIOStats() {
            super(Map.of(), Stats.IOStats.STATS_IO_TID, null);
        }
    }

    public class MMonadicStats extends MRec implements Stats.MonadicStats {

        public MMonadicStats() {
            super(Map.of(), Stats.MonadicStats.MACH_ISA_STATS_MONADIC_TID, null);
        }
    }

    @Override
    public Stats.IOStats ioStats() {
        return this.ioStats;
    }

    @Override
    public Stats.MonadicStats monadicStats() {
        return this.monadicStats;
    }
}
