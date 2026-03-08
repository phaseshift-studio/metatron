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
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.util.FastNoSuchElementException;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import static studio.phaseshift.metatron.isa.m.mInstSet.FAIL_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MFail extends MObj implements Fail {

    protected MFail(Tuple.Pair<Throwable, Fail> jvm, final fURI tid, final fURI vid) {
        super(jvm, null == tid ? FAIL_TID : tid, vid);
    }

    public static Fail fail(final String message, final Object... args) {
        return fail(MTronException.of(message, args), null);
    }

    public static Fail fail(final Throwable t, final Fail cause) {
        return new MFail(Tuple.Pair.with(MTronException.of(t), cause), FAIL_TID,null);
    }

    public static Fail fail(final Throwable t) {
        if(t.getCause() == null || (t.getCause() instanceof MTronException))
            return new MFail(Tuple.Pair.with(MTronException.of(t), null), FAIL_TID,null);
        else
            return new MFail(Tuple.Pair.with(MTronException.of(t.getCause()), fail(MTronException.of(t.getMessage()))), FAIL_TID,null);
    }

    public static Fail fail(final Throwable t, final String format, final Object... args) {
        return fail(MTronException.of(t, format, args));
    }

    @Override
    public Fail caught() {
        return this instanceof MCaughtFail ? this : new MCaughtFail(this);
    }

    @Override
    public Fail clone(Object jvm, fURI tid, fURI vid) {
        return super.clone(jvm, tid, vid);
    }

    @Override
    public Tuple.Pair<Throwable, Fail> jvm() {
        return super.jvm();
    }

    @Override
    public Fail plus(final Fail rhs) {
        return fail(this.jvm().get0(), rhs);
    }

    @Override
    public Fail zero() {
        return fail(FastNoSuchElementException.instance(), null);
    }

    public static class MCaughtFail extends MFail implements CaughtFail {

        protected MCaughtFail(Fail jvm) {
            super(Tuple.Pair.with(jvm.message(), jvm.cause().map(MCaughtFail::new).orElse(null)), jvm.tid(), null);
        }

        public boolean isFail() {
            return false;
        }
    }
}
