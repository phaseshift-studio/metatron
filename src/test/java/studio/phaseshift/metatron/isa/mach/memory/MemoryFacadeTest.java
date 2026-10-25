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

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.mach.type.Machine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * MEMORY HOLDS DATA SPACES — and only those (Marko, 2026-10-25: "Instsets shouldn't be in Memory as spaces (though they
 * are spaces). They should be in InstSet only. This will really clean up Memory and put items where they should
 * logically be -- Network related objs in Network, InstSets in InstSet, and data spaces in Memory.").
 * <p>
 * An instruction set lives in the machine's instset and is not in memory's index. The addresses it holds stay
 * routable all the same: memory's lookup weighs the libraries the machine mounted against its data spaces, and the most
 * specific claimant answers. (This class used to assert that the ISA was mounted in memory's index.)
 */
public class MemoryFacadeTest extends AbstractMetatronTest {

    @Test
    public void testAnInstSetIsNotInMemorysIndex() {
        final Machine machine = Machine.current();
        assertTrue(machine.memory().spaces().elements().map(e -> e.isRel() ? e.asRel().second() : e).noneMatch(s -> s instanceof InstSet),
                "memory's index holds data spaces only");
    }

    @Test
    public void testMemorysLookupStillRoutesAnInstructionsAddress() {
        final Machine machine = Machine.current();
        assertTrue(machine.memory().findSpace(f("/m/inst/plus")) instanceof InstSet isa && f("/m").equals(isa.vid()),
                "memory's lookup names /m for /m/inst/plus: the instset's library outranks the data space there");
        assertFalse(machine.memory().read(f("/m/inst/plus")).isNoObj(), "and memory reads the instruction from it");
        assertFalse(Machine.read(f("/m/inst/plus")).isNoObj(), "as the machine does");
    }
}
