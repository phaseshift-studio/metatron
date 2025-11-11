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

package studio.phaseshift.metatron.lang.core.m.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.Real;

import static studio.phaseshift.metatron.lang.core.m.mInstSet.REAL_TID;


public class MReal extends MObj implements Real {

    public MReal(final Double value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    public static Real real(final Double jvm) {
        return new MReal(jvm, REAL_TID, fURI.NULL);
    }

    public static Real real(final Float jvm) {
        return real(jvm.doubleValue());
    }

    public static Real real(final Double jvm, final fURI tid, final fURI vid) {
        return new MReal(jvm, tid, vid);
    }

    public static Real of(final double jvm) {
        return new MReal(jvm, REAL_TID, fURI.NULL);
    }

    @Override
    public Real clone(final Object jvm, final fURI tid, final fURI vid) {
        return super.clone(jvm, tid, vid);
    }

    @Override
    public Double jvm() {
        return (Double) this.jvm;
    }
}
