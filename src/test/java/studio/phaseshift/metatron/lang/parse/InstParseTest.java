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

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.lang.obj.SObj.Obj;

import static studio.phaseshift.metatron.MetatronTest.assertMEquals;
import static studio.phaseshift.metatron.lang.parse.ObjParser.parse;

public class InstParseTest {

    @Test
    public void testPlusInst() {
        assertMEquals(3, parse("1 => plus(2)"));
    }

}
