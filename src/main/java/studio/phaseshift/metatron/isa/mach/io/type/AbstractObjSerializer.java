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

package studio.phaseshift.metatron.isa.mach.io.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractObjSerializer<T> extends MRec implements ObjSerializer<T> {

    protected AbstractObjSerializer(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    protected AbstractObjSerializer(final fURI tid, final fURI vid) {
        super(new LinkedHashMap<>(), tid, vid);
    }

    @Override
    public ByteBuffer outputBytes(final Obj obj) throws MTronException {
        final T t = this.write(obj);
        return ByteBuffer.wrap(t.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public ObjSerializer<T> clone() {
        return this;
    }

    public String toString() {
        return "!*" + this.vid();
    }

    public boolean equals(final Object other) {
        if (other == null)
            return false;
        return this.getClass().equals(other.getClass());
    }
}
