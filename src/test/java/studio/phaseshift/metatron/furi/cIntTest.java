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

package studio.phaseshift.metatron.furi;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.util.MTronException;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class cIntTest {

    @Test
    public void testExactOps() {
        assertEquals(cInt.ZERO(), cInt.ZERO().plus(cInt.ZERO()));
        assertEquals(cInt.ONE(), cInt.ZERO().plus(cInt.ONE()));
        assertEquals(cInt.of(100), cInt.ZERO().plus(cInt.of(100)));
        assertEquals(cInt.of(100), cInt.of(44).plus(cInt.of(56)));
        assertEquals(cInt.of(2), cInt.ONE().plus(cInt.ONE()));
    }

    @Test
    public void testRangeOps() {
        assertEquals(cInt.of(22, 44), cInt.of(11, 22).plus(cInt.of(11, 22)));
        assertEquals(cInt.of(22, 81), cInt.of(21, 24).plus(cInt.of(1, 57)));
        assertEquals(cInt.of(-20, 81), cInt.of(-21, 24).plus(cInt.of(1, 57)));
        assertThrows(MTronException.class, () -> cInt.of(-24, -46));
        assertThrows(MTronException.class, () -> cInt.of(2, 0).plus(cInt.of(0, 2)));
    }

    @Test
    public void testComparators() {
        assertFalse(cInt.of(11, 22).gt(cInt.of(11, 22)));
        assertTrue(cInt.of(11, 22).gte(cInt.of(11, 22)));
        assertTrue(cInt.of(11, 22).equals(cInt.of(11, 22)));
        assertFalse(cInt.of(11, 22).lt(cInt.of(11, 22)));
        assertTrue(cInt.of(11, 22).lte(cInt.of(11, 22)));
        /// //
        assertTrue(cInt.of(22, 81).signeq(cInt.of(21, 24)));
        assertFalse(cInt.of(-81, -22).signeq(cInt.of(21, 24)));
    }


}
