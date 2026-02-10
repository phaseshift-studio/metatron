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
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.Map;

import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.mach.io.ioInstSet.OBJ_SERIALIZER_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractObjSerializer<T> implements ObjSerializer<T> {

    @Override
    public Map<Obj, Obj> jvm() {
        return Map.of();
    }

    
    @Override
    public ObjSerializer<T> clone() {
        return this;
    }

    @Override
    public String toString() {
        return CommonUtil.snakeCase(this.getClass().getSimpleName());
    }

    @Override
    public fURI tid() {
        return REC_TID;
    }

    @Override
    public fURI vid() {
        return OBJ_SERIALIZER_TID;
    }

    @Override
    public <O extends Obj> O clone(Object jvm, fURI tid, fURI vid) {
        return (O) this;
    }

    @Override
    public <O extends Obj> O self(Object jvm, fURI tid, fURI vid) {
        return (O) this;
    }

}
