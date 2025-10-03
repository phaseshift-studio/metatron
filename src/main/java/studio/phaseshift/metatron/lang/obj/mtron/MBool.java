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
import studio.phaseshift.metatron.lang.obj.Bool;

import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.BOOL_TID;


public class MBool extends MObj implements Bool {

    public static Bool bool(final Boolean value) {
        return MBool.of(value);
    }

    public MBool(final Boolean value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    public MBool(final Boolean value) {
        this(value, BOOL_TID, fURI.NULL);
    }

    @Override
    public Bool clone(final Object value, final fURI tid, final fURI vid) {
        return super.clone(value, tid, vid, (a, b, c) -> new MBool((Boolean) a, b, c));
    }

    @Override
    public Boolean value() {
        return (Boolean) this.value;
    }

    public static Bool of(final boolean value) {
        return new MBool(value);
    }
}