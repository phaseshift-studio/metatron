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
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

package studio.phaseshift.metatron.lang.obj.mtron;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Int;

import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.INT_TID;

public class MInt extends MObj implements Int {

    public static Int jnt(final long i) {
        return MInt.of(i);
    }


    public MInt(final Long value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    public MInt(final Long value) {
        this(value, INT_TID, fURI.NULL);
    }

    @Override
    public Int clone(final Object value, final fURI tid, final fURI vid) {
        return super.clone(value, tid, vid, (a, b, c) -> new MInt((Long) a, b, c));
    }

    @Override
    public Long value() {
        return (Long) this.value;
    }

    public static Int of(final long value) {
        return new MInt(value);
    }
}
