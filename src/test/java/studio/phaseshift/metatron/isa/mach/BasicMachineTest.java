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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.NoObj;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.machine.BasicMachine;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;

/**
 * Inherits the machine-universal contract from {@link AbstractMachineTest}; this class only supplies
 * the concrete machine. Future machine implementations add one class and inherit the same tests.
 */
public class BasicMachineTest extends AbstractMachineTest {

    private static final AtomicLong COUNTER = new AtomicLong(0);

    public BasicMachineTest() {
        super(mInstSet::new);
    }

    @Override
    protected Machine newMachine() {
        return BasicMachine.of(MACH_MACHINE_TID, null);
    }

    @Test
    public void testCloseSpace() {
        final Machine mach = Machine.current();
        memSpace test = memSpace.of(f("/m/test/#"), f("/m/test")).as();
        Assertions.assertTrue(mach.hasSpaceFor(f("/m/test/a")));
        assertTrue(mach.hasSpaceFor(f("/m/test/a")));
        mach.memory().write(f("/m/test/a"), jnt(10));
        assertEquals(jnt(10), mach.memory().read(f("/m/test/a")));
        assertTrue(mach.memory().hasSpaceFor(f("/m/test/a")));
        mach.memory().write(f("/m/test/a"), NoObj.noobj());
        test.close();
        assertFalse(mach.hasSpaceFor(f("/test/a")));
        mach.close();
        // assertTrue(mach.memory().isZero());
    }
}
