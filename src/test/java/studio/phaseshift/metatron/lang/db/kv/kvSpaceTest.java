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

package studio.phaseshift.metatron.lang.db.kv;

import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.mSpaceTest;

import static studio.phaseshift.metatron.furi.fURI.f;

public class kvSpaceTest extends mSpaceTest {

    public kvSpaceTest() {
        super(() -> memSpace.of(f("/t/#"),f("/sys/space/kv")));
    }
}
