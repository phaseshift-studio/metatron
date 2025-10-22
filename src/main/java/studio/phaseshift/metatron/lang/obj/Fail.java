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

package studio.phaseshift.metatron.lang.obj;

import studio.phaseshift.metatron.lang.fURI;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Fail extends Obj {

    @Override
    Fail clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    Throwable jvm();

    default Fail jvm(final Throwable value) {
        return this.clone(value, this.tid(), this.vid());
    }

    default Fail tid(final fURI tid) {
        return this.clone(this.jvm(), tid, this.vid());
    }
}
