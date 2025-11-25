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
import studio.phaseshift.metatron.lang.core.m.type.Fail;
import studio.phaseshift.metatron.util.MTronException;

import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.FAIL_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MFail extends MObj implements Fail {

    public MFail(final Throwable t, final fURI tid, final fURI vid) {
        super(t, null == tid ? FAIL_TID : tid, vid);
        //t.printStackTrace();
    }

    public static Fail fail(final Throwable t) {
        return new MFail(t, FAIL_TID, fURI.fnull);
    }

    public static Fail fail(final Throwable t, final String format, final Object... args) {
        return fail(MTronException.of(t, format, args));
    }

    @Override
    public Fail clone(Object jvm, fURI tid, fURI vid) {
        return super.clone(jvm, tid, vid);
    }

    @Override
    public Throwable jvm() {
        return super.jvm();
    }

    @Override
    public Fail plus(final Fail rhs) {
        return fail(rhs.jvm().initCause(this.jvm()));
    }

    @Override
    public Fail zero() {
        return null;
    }
}
