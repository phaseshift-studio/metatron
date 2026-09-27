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

package studio.phaseshift.metatron.isa.mach.type.machine;

import studio.phaseshift.metatron.furi.fURI;

import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_MACHINE_TID;

/*
 * BasicMachine — the concrete machine: a Router that binds an ISA to its lowering and execution
 * axes. It holds its instset, compiler and processor as rec entries (the {@code machine::T} shape),
 * and its own address space through the inherited Router surface. The default machine lives at
 * {@code /sys/mach} and is bootstrapped by {@code machInstSet.setup()}; this class is the stable
 * Java instance that lives there.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BasicMachine extends AbstractMachine {

    public BasicMachine(final fURI vid) {
        super(vid);
    }

    @Override
    public fURI tid() {
        return MACH_MACHINE_TID;
    }
}
