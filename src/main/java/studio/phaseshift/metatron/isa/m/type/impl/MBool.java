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
import studio.phaseshift.metatron.isa.m.type.Bool;

import static studio.phaseshift.metatron.isa.m.mInstSet.BOOL_TID;


public class MBool extends MObj implements Bool {

    public MBool(final Boolean jvm, final fURI tid, final fURI vid) {
        super(jvm, null == tid ? BOOL_TID : tid, vid);
    }

    public static Bool bool(final Boolean jvm, final fURI tid, final fURI vid) {
        return new MBool(jvm, tid, vid);
    }

    public static Bool bool(final Boolean jvm) {
        return bool(jvm, BOOL_TID, fURI.fnull);
    }

    @Override
    public Bool clone(final Object jvm, final fURI tid, final fURI vid) {
        return super.clone(jvm, tid, vid);
    }

    @Override
    public Boolean jvm() {
        return (Boolean) this.jvm;
    }

}