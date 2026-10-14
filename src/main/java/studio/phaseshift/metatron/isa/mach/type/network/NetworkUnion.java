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

package studio.phaseshift.metatron.isa.mach.type.network;

/**
 * NetworkUnion — one machine's OWN level of reach in a frame stack: the network's seat in the same
 * parade as {@code MemoryUnion} and the child's {@code BasicInstSet} in {@code Machine.push}.
 * Deliberately THINNER than its siblings, because the network's two laws leave nothing to union:
 * <ol>
 *     <li><b>knowledge is shared</b> — every level reads the ONE roster at the root address (like
 *     Memory's base frame, but machine-global rather than per variable stack), so sharing needs no
 *     plumbing here: it is a property of addressing.</li>
 *     <li><b>reach is not</b> — peers are neither inherited from the parent (not even shadowed) nor
 *     shared with children. A connection belongs to exactly the machine that dialed it: a pop
 *     retracts this machine's dials and nothing else, and a sibling never sees them.</li>
 * </ol>
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class NetworkUnion extends BasicNetwork {

    public NetworkUnion() {
        super();
    }
}
