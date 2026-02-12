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

package studio.phaseshift.metatron.isa.mach.io.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Uri;

import static studio.phaseshift.metatron.isa.mach.io.ioInstSet.OBJ_SERIALIZER_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractObjSerializer<T> implements ObjSerializer<T> {

    @Override
    public ObjSerializer<T> clone() {
        return this;
    }

    @Override
    public Uri clone(final Object jvm, final fURI tid, final fURI vid) {
        Obj.Helper.logLockedObj(this);
        return this;
    }

    @Override
    public Uri self(final Object jvm, final fURI tid, final fURI vid) {
        Obj.Helper.logLockedObj(this);
        return this;
    }
    
    @Override
    public fURI tid() {
        return OBJ_SERIALIZER_TID;
    }

    @Override
    public fURI jvm() {
        return OBJ_SERIALIZER_TID;
    }
}
