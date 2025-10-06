/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

package studio.phaseshift.metatron.lang.obj.mtron;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.lang.obj.Obj;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.lang.obj.mtron.MInt.jnt;
import static studio.phaseshift.metatron.lang.obj.mtron.MLst.lst;
import static studio.phaseshift.metatron.lang.obj.mtron.mtronFluent.StartLess.*;

public class FluentTest extends MetatronTest {

    @Test
    public void testSimpleFluency() {
        assertEquals(jnt(11), start(jnt(1)).plus(jnt(10)).iterator().next());
        assertEquals(jnt(110), start(jnt(10)).plus(mult(jnt(10))).iterator().next());
        assertEquals(List.of(jnt(110), jnt(125)), start(jnt(10)).plus(mult(jnt(10))).split(lst(List.<Obj>of(id(), plus(jnt(15))))).merge().merge().merge().toList()); // TODO: merging and toList with quantifies requires more reasoning on iteration
    }
}

