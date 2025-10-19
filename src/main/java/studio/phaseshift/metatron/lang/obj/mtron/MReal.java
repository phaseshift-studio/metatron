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

package studio.phaseshift.metatron.lang.obj.mtron;

import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Real;

import static studio.phaseshift.metatron.lang.obj.mtron.mtronInstSet.REAL_TID;


public class MReal extends MObj implements Real {

    public MReal(final Double value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    public MReal(final Double value) {
        this(value, REAL_TID, fURI.NULL);
    }

    public static Real real(final Double r) {
        return MReal.of(r);
    }

    public static Real real(final Double r, final fURI tid, final fURI vid) {
        return new MReal(r, tid, vid);
    }

    public static Real of(final double value) {
        return new MReal(value);
    }

    @Override
    public Real clone(final Object jvm, final fURI tid, final fURI vid) {
        return (Real) super.clone(jvm, tid, vid);
    }

    @Override
    public Double jvm() {
        return (Double) this.jvm;
    }
}
