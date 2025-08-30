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

package studio.phaseshift.metatron.lang.inst;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.lang.obj.SObj.Inst;

import static studio.phaseshift.metatron.MetatronTest.assertMEquals;
import static studio.phaseshift.metatron.lang.inst.SInst.PLUS_URI;
import static studio.phaseshift.metatron.lang.obj.SObj.Int;

public class STest {

    @BeforeAll
    public static void setUp() {
        BootLoader.load();
    }

    @Test
    public void testPlusInst() {
        assertMEquals(35, new Inst(PLUS_URI, Int.of(25)).apply(Int.of(10)));
    }
}
