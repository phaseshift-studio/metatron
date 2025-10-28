/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC 
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

package studio.phaseshift.metatron.lang.mtron.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.c.cInt;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class CLessObj implements Obj {

    private final Obj base;
    private final fURI tid;
    private final fURI vid;

    public CLessObj(final Obj obj) {
        this.base = obj;
        this.tid = obj.tid();
        this.vid = obj.vid();
    }

    @Override
    public int hashCode() {
        return this.jvm().c(cInt.ONE()).hashCode();
    }

    @Override
    public boolean equals(final Object other) {
        return Obj.Helper.objcLessEquals(this.base, other);
    }

    @Override
    public Obj jvm() {
        return this.base;
    }

    @Override
    public fURI tid() {
        return this.base.isNoObj() ? this.base.tid() : this.base.tid().cLess();
    }

    @Override
    public fURI vid() {
        return this.base.vid();
    }

    @Override
    public CLessObj clone(Object jvm, fURI tid, fURI vid) {
        return new CLessObj((Obj) jvm);
    }

    @Override
    public Obj clone() {
        return new CLessObj(this.jvm());
    }
}
