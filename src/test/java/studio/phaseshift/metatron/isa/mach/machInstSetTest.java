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

package studio.phaseshift.metatron.isa.mach;

import org.junit.jupiter.api.BeforeAll;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.space.memSpace;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.QPROC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class machInstSetTest extends AbstractInstSetTest {

    /**
     * THE SUITE SUPPLIES ITS BACKING. Memory here because it needs nothing installed; a space with an external
     * dependency supplies that instead, and this class names no space at all.
     */
    @BeforeAll
    public static void registerCompute() {
        AbstractMachineTest.registerComputeSpace(() -> memSpace.of(rec(
                uri(PATTERN), uri("/usr/compute/#"),
                uri(QPROC), lst(QCollection.subQ())), AbstractMachineTest.COMPUTE_VID));
    }

    /**
     * This class tests the ISA, not a machine, so it does not inherit AbstractMachineTest -- but the rewrite it is
     * asserting against mints into compute:, and SOMETHING has to cover that namespace. That is the point of
     * distributed state living in a user namespace rather than under /sys: a suite says where the state lives.
     */
    public machInstSetTest() {
        super(machInstSet::new);
    }
}
