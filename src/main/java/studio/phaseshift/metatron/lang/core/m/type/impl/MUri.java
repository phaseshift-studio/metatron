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
import studio.phaseshift.metatron.lang.core.m.type.Uri;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.URI_TID;

public class MUri extends MObj implements Uri {

    public MUri(final fURI jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
        if (jvm.isZero())
            this.tid = this.tid.zero();
    }

    public static Uri uri(final String jvm) {
        return new MUri(f(jvm), URI_TID, fURI.NULL);
    }

    public static Uri uri(final fURI jvm) {
        return new MUri(jvm, URI_TID, fURI.NULL);
    }

    public static Uri uri(final fURI jvm, final fURI tid) {
        return new MUri(jvm, tid, fURI.NULL);
    }

    public static Uri uri(final String jvm, final fURI tid) {
        return new MUri(f(jvm), tid, fURI.NULL);
    }

    @Override
    public Uri clone(final Object jvm, final fURI tid, final fURI vid) {
        MUri clone = super.clone(jvm, tid, vid);
        if (clone.jvm().isZero())
            clone.tid = clone.tid.zero();
        return clone;
    }

    @Override
    public fURI jvm() {
        return (fURI) this.jvm;
    }
}


