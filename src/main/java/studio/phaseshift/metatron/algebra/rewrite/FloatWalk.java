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

package studio.phaseshift.metatron.algebra.rewrite;

import studio.phaseshift.metatron.isa.m.math.cat.LawTable;
import studio.phaseshift.metatron.isa.m.math.cat.Law;
import studio.phaseshift.metatron.isa.m.type.Inst;

import java.util.List;

/**
 * FloatWalk — the shard cut, COMPUTED rather than pattern-matched: push a coefficient rightward
 * through the chain while each instruction declares {@link Law#floatable} (Thm 6,
 * universal functional commutativity) and answer the index of the first instruction that declares
 * {@link Law#blocked} (Thm 17: a reduce anchors the coefficient). The walk starts after
 * the chain's seed: position 0 is the initial inst and is never asked to float — a start has no
 * coefficient to commute past.
 * <p>
 * The answer is where a distribution rule splits master from workers; {@link #NO_CUT} means the
 * chain has no admissible anchor (no shard rewrite fires — fail closed).
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class FloatWalk {

    public static final int NO_CUT = -1;

    private FloatWalk() {
    }

    /**
     * The anchor index, or {@link #NO_CUT} when a prefix inst neither floats nor blocks — the
     * float dies before an anchor, so no cut exists (a non-floatable mapper pins the chain local).
     */
    public static int cut(final List<Inst> chain) {
        for (int i = 1; i < chain.size(); i++) {
            final Inst inst = chain.get(i);
            if (LawTable.declares(inst.tid(), Law.blocked))
                return i;
            if (!LawTable.declares(inst.tid(), Law.floatable))
                return NO_CUT;
        }
        return NO_CUT;
    }
}
