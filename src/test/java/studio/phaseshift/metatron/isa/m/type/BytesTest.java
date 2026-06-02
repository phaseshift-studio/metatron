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

package studio.phaseshift.metatron.isa.m.type;

import studio.phaseshift.metatron.algebra.AbstractAlgebraTest;

import java.nio.ByteBuffer;
import java.util.Set;

import static studio.phaseshift.metatron.algebra.Form.PLUS_MONOID;
import static studio.phaseshift.metatron.isa.m.type.impl.MBytes.bytes;

public class BytesTest extends AbstractAlgebraTest<Bytes> {
    public BytesTest() {
        super(bytes(ByteBuffer.wrap(new byte[]{1, 2, 3})), Set.of(PLUS_MONOID));
    }
}
