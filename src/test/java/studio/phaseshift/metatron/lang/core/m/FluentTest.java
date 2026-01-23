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

package studio.phaseshift.metatron.lang.core.m;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.mTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.start_;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInt.jnt;

public class FluentTest extends mTest {

    @Test
    public void testSimpleFluency() {
        assertEquals(jnt(11), start_(jnt(1)).plus_(jnt(10)).stream().iterator().next());
        //  assertEquals(jnt(110), start(jnt(10)).p1us(mult(jnt(10))).iterator().next());
        //  assertEquals(List.of(jnt(110), jnt(125)), start(jnt(10)).p1us(mult(jnt(10))).split(lst(List.<Obj>of(id(), plus(jnt(15))))).merge().merge().merge().toList()); // TODO: merging and toList with quantifies requires more reasoning on iteration
    }
}

