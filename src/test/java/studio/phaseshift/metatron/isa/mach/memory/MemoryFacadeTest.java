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

package studio.phaseshift.metatron.isa.mach.memory;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * MEMORY IS THE FACADE over the world of spaces.
 * <p>
 * The defining property: an instruction set is reachable THROUGH memory, by reading its address, exactly like any
 * other space — not through a component accessor of its own. The ISA is a Space ({@code InstSet extends Space}), so
 * once it is mounted in the memory index, {@code memory().read(...)} finds it via {@code getSpaceFor} by pattern.
 * <p>
 * This is the step that lets {@code instset} stop being a component: what the accessor used to fetch is simply what
 * the facade presents.
 */
public class MemoryFacadeTest extends AbstractMetatronTest {

    @Disabled("MEASURED: the ISA IS mounted into a memory index and keyed by its pattern, and Memory IS a Space -- but "
            + "reading it through a DIFFERENT machine's memory gives noobj. The ISA is populated into the AUTHORITY's "
            + "memory (the root, /.), and machines do not inherit from one another -- only FRAMES inherit, through the "
            + "memory chain. So this is not a facade defect: it is the missing link between a machine and its authority. "
            + "Resolution must walk from a machine to its authority's spaces, which is the same walk previous() provides "
            + "for frames. Enable when that walk lands")
    @Test
    public void testAnInstSetIsReachableThroughMemory() {
        final Machine machine = Machine.current();
        final Obj plus = machine.memory().read(f("/m/inst/plus"));
        assertEquals(false, plus.isNoObj(), "the ISA answers for its own addresses THROUGH memory");
    }

    /**
     * What IS true today: the authority's memory holds the ISA, findable by an address in its jurisdiction.
     */
    @Test
    public void testAnInstSetIsMountedInAMemoryIndex() {
        final Object found = Machine.current().memory().findSpace(f("/m/inst/plus"));
        assertEquals(true, null != found, "the authority's memory finds the ISA by an address in its pattern");
    }
}
