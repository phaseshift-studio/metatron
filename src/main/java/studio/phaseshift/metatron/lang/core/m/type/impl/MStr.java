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
import studio.phaseshift.metatron.lang.core.m.type.Str;

import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.STR_TID;


public class MStr extends MObj implements Str {

    public static Str str(final String jvm) {
        return str(jvm, STR_TID, fURI.fnull);
    }

    public static Str str(final String jvm, final fURI tid, final fURI vid) {
        return new MStr(jvm, null == tid ? STR_TID : tid, vid);
    }

    public MStr(final String value, final fURI tid, final fURI vid) {
        super(value, tid, vid);
    }

    public MStr(final String value) {
        this(value, STR_TID, fURI.fnull);
    }

    @Override
    public Str clone(final Object jvm, final fURI tid, final fURI vid) {
        return (Str) super.clone(jvm, tid, vid);
    }

    @Override
    public String jvm() {
        return (String) this.jvm;
    }
}
