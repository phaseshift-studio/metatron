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

package studio.phaseshift.metatron.isa.m.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Bytes;

import java.nio.ByteBuffer;

import static studio.phaseshift.metatron.isa.m.mInstSet.BYTES_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MBytes extends MObj implements Bytes {

    public MBytes(final ByteBuffer jvm, final fURI tid, final fURI vid) {
        super(jvm, null == tid ? BYTES_TID : tid, vid);
    }

    public static Bytes bytes(final ByteBuffer jvm) {
        return bytes(jvm, BYTES_TID, fURI.fnull);
    }

    public static Bytes bytes(final ByteBuffer jvm, final fURI tid, final fURI vid) {
        return new MBytes(jvm, tid, vid);
    }

    @Override
    public Bytes clone(final Object jvm, final fURI tid, final fURI vid) {
        return super.clone(jvm, tid, vid);
    }

    @Override
    public ByteBuffer jvm() {
        return (ByteBuffer) this.jvm;
    }

}