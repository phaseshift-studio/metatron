/*
 * metatron: a distributed virtual machine and language
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
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.FastNoSuchElementException;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Objects;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.FAIL_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MFail extends MObj implements Fail {

    public static fURI FAIL_STACK_PATTERN = f("/sys/fail/_?incrq");

    /**
     * The JVM is the Java {@link Throwable} that carries the message and stack trace.
     * The cause chain is threaded through {@link Throwable#getCause()}.
     */
    protected MFail(final Throwable jvm, final fURI tid, final fURI vid) {
        super(jvm, null == tid ? FAIL_TID : tid, vid);
        if (jvm instanceof MTronException e)
            e.setFailRef(this);
    }

    protected static Fail incrStackWrap(final Fail fail, final fURI pattern) {
        if (null != fail.vid())
            return fail;
        return Router.writeToSpace(fail.vid(pattern)).as();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.tid, this.jvm().getMessage(), this.jvm().getCause() != null);
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof Fail that))
            return false;
        if (!Objects.equals(this.tid(), that.tid()))
            return false;
        if (!Objects.equals(this.jvm().getMessage(), that.jvm().getMessage()))
            return false;
        // Compare cause chain recursively (structural equality)
        final Throwable thisCause = this.jvm().getCause();
        final Throwable thatCause = that.jvm().getCause();
        if (thisCause == null)
            return thatCause == null;
        if (thatCause == null)
            return false;
        return Objects.equals(thisCause.getMessage(), thatCause.getMessage());
    }

    /**
     * Create a transient (no VID) fail wrapping the given Throwable.
     * Used by {@link Fail#cause()} to construct on-the-fly cause Fails
     * without writing to the fail space.
     * Preserves the Throwable as-is — no conversion through MTronException.of().
     */
    public static Fail transientFail(final Throwable t) {
        return new MFail(t, FAIL_TID, null);
    }

    /**
     * Walk the cause chain of {@code jvm} and create transient {@link MFail}
     * back-links for any {@link MTronException} that doesn't already have one.
     * This ensures {@code walkFailChain} can traverse the full mtron-level nesting.
     */
    private static void ensureFailRefs(final Throwable jvm) {
        for (Throwable cause = jvm.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof MTronException e && e.fail() == null)
                transientFail(e); // constructs MFail, which calls e.setFailRef(this)
        }
    }

    /**
     * Create a fail from a message string (wrapped in {@link MTronException}).
     */
    public static Fail fail(final String message, final Object... args) {
        final MTronException mte = MTronException.of(message, args);
        final MFail mfail = new MFail(mte, FAIL_TID, null);
        ensureFailRefs(mte);
        return incrStackWrap(mfail, FAIL_STACK_PATTERN);
    }

    /**
     * Create a fail from a Throwable.
     * If the Throwable already has a cause chain, the cause is preserved.
     */
    public static Fail fail(final Throwable t) {
        final MTronException mte = MTronException.of(t);
        final MFail mfail = new MFail(mte, FAIL_TID, null);
        ensureFailRefs(mte);
        return incrStackWrap(mfail, FAIL_STACK_PATTERN);
    }

    /**
     * Create a fail with a nested mtron cause. The cause fail's Throwable becomes
     * the Java {@link Throwable#getCause()} of the outer fail's Throwable.
     * Uses RuntimeException with cause via constructor so the chain survives space writes.
     */
    public static Fail fail(final Throwable t, final Fail cause) {
        final Throwable jvm;
        if (null != cause) {
            jvm = new RuntimeException(t.getMessage(), cause.jvm());
            jvm.setStackTrace(t.getStackTrace());
        } else {
            jvm = t instanceof MTronException ? t : MTronException.of(t.getMessage());
            if (jvm instanceof MTronException)
                ensureFailRefs(jvm);
        }
        return incrStackWrap(new MFail(jvm, FAIL_TID, null), FAIL_STACK_PATTERN);
    }

    /**
     * Create a fail from a Throwable with a formatted message.
     */
    public static Fail fail(final Throwable t, final String format, final Object... args) {
        return fail(MTronException.of(t, format, args));
    }

    @Override
    public Fail caught() {
        this.delete();
        return this instanceof MCaughtFail ? this : new MCaughtFail(this);
    }

    @Override
    public Fail clone(Object jvm, fURI tid, fURI vid) {
        return super.clone(jvm, tid, vid);
    }

    @Override
    public Throwable jvm() {
        return (Throwable) super.jvm();
    }

    @Override
    public Fail plus(final Fail rhs) {
        // Walk to the end of the lhs cause chain and attach rhs there
        Throwable tail = this.jvm();
        while (tail.getCause() != null)
            tail = tail.getCause();
        try { tail.initCause(rhs.jvm()); } catch (final IllegalStateException ignored) {}
        return transientFail(this.jvm());
    }

    @Override
    public Fail zero() {
        return fail(FastNoSuchElementException.instance());
    }

    public static class MCaughtFail extends MFail implements CaughtFail {

        protected MCaughtFail(final Fail other) {
            // Cause chain is already embedded in other.message().getCause() —
            // no need to re-thread it; this.message() IS other.message()
            super(other.jvm(), other.tid(), null);
        }

        public boolean isFail() {
            return false;
        }
    }
}
