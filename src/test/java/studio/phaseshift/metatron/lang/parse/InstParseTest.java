/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang.parse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.lang.inst.SInst;
import studio.phaseshift.metatron.lang.obj.SObj;
import studio.phaseshift.metatron.lang.obj.mtron.MInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.MetatronTest.assertMEquals;
import static studio.phaseshift.metatron.lang.parse.ObjParser.eval;

public class InstParseTest {

    @BeforeAll
    public static void begin() {
        SInst.load();
    }

    @Test
    public void testPlusInst() {
        assertEquals(MInt.of(3), eval("1.plus(2)").next());
    }

    @Test
    @Disabled
    public void testCountInst() {
        assertEquals(SObj.Int.of(3), eval("{1,2,3}.count()").next());
    }

    @Test
    @Disabled
    public void testSumInst() {
        assertEquals(SObj.Int.of(6), eval("{1,2,3}.sum()").next());
    }

}
