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

package studio.phaseshift.metatron.isa.mach.space;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.AbstractRouterTest;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.NoObj;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicMachine;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BasicRouterTest extends AbstractRouterTest {

    public BasicRouterTest() {
        super(new BasicMachine(f("/m/test")));
    }

    @Test
    public void testCloseSpace() {
        memSpace test = memSpace.of(f("/m/test/#"), f("/m/test")).as();
        Assertions.assertTrue(Machine.current().hasSpaceFor(f("/m/test/a")));
        assertTrue(Machine.current().hasSpaceFor(f("/m/test/a")));
        Machine.current().write("/m/test/a", jnt(10));
        assertEquals(jnt(10), Machine.current().read("/m/test/a"));
        assertTrue(Machine.current().hasSpaceFor(f("/m/test/a")));
        Machine.current().write("/m/test/a", NoObj.noobj());
        test.close();
        assertFalse(Machine.current().hasSpaceFor(f("/test/a")));
    }
}
