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

package studio.phaseshift.metatron.isa.math;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;
import studio.phaseshift.metatron.isa.m.math.mathInstSet;

import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mathInstSetTest extends AbstractInstSetTest {

    public mathInstSetTest() {
        super(mathInstSet::new);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "1024.as(kB::T)                                                                    % kB::1024",
            "kB::1024.as(kB::T)                                                                % kB::1024",
            "bB::1024.as(kB::T)                                                                % kB::1",
            "kB::1024.as(mB::T)                                                                % mB::1",
            "mB::1024.as(gB::T)                                                                % gB::1",
            "gB::1024.as(tB::T)                                                                % tB::1",
            "tB::1024.as(pB::T)                                                                % pB::1",
            "pB::1.as(tB::T)                                                                   % tB::1024",
            "tB::1.as(gB::T)                                                                   % gB::1024",
            "gB::1.as(mB::T)                                                                   % mB::1024",
            "mB::1.as(kB::T)                                                                   % kB::1024",
            "kB::1.as(bB::T)                                                                   % bB::1024",
            "mB::1.as(bB::T)                                                                   % bB::1024.mult(1024)",
            "gB::1.as(bB::T)                                                                   % bB::1024.mult(1024).mult(1024)",
            "tB::1.as(bB::T)                                                                   % bB::1024.mult(1024).mult(1024).mult(1024)",
            "pB::1.as(bB::T)                                                                   % bB::1024.mult(1024).mult(1024).mult(1024).mult(1024)",
            "mB::1.as(kB::T)                                                                   % kB::1024",
            "gB::1.as(mB::T)                                                                   % mB::1024",
            "tB::1.as(gB::T)                                                                   % gB::1024",
            "pB::1.as(tB::T)                                                                   % tB::1024",
            "pB::1.as(gB::T)                                                                   % gB::1024.mult(1024)",
            "pB::1.as(mB::T)                                                                   % mB::1024.mult(1024).mult(1024)",
            "gB::1.as(gB::T)                                                                   % gB::1",
            "tB::1.as(gB::T)                                                                   % gB::1024",
            "pB::1.as(gB::T)                                                                   % gB::1024.mult(1024)",
            "tB::1.as(tB::T)                                                                   % tB::1",
            "pB::1.as(tB::T)                                                                   % tB::1024",
            "pB::1.as(pB::T)                                                                   % pB::1",
    }, delimiter = '%', quoteCharacter = '~')
    public void testConversions(final String code, final String expected) {
        AbstractMetatronTest.testCode(LOG, code, expected);
    }

}
