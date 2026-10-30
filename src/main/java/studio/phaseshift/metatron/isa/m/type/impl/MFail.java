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
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.sys.type.ExecutionStack;
import studio.phaseshift.metatron.util.MTronException;

import java.util.Objects;

import static studio.phaseshift.metatron.Tokens.FAIL_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.INCRQ;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MFail extends MObj implements Fail {

    /**
     * The per-machine fail stack: a fail lands under the machine it executes in.
     */
    public static fURI failStackPattern() {
        // ~/sys/fail: resolved to the current machine's vid (<vid>/sys/fail/_) when written.
        return f("~/sys/fail").extend("_").addQ(INCRQ);
    }

    /**
     * The JVM is the Java {@link Throwable} that carries the message and stack trace.
     * The cause chain is threaded through {@link Throwable#getCause()}.
     */
    protected MFail(final Throwable jvm, final fURI tid, final fURI vid) {
        super(jvm, null == tid ? FAIL_TID : tid, vid);
        if (jvm instanceof MTronException e)
            e.setFailRef(this);
    }

    /**
     * THE FAIL-STACK ENTRY POINT: address an unaddressed fail at {@code pattern} — the machine's
     * {@code ~/sys/fail/_?incrq} ({@link #failStackPattern()}) — and answer it with that address. A fail that
     * already has a vid is answered as it stands (it is already filed).
     */
    public static Fail incrStackWrap(final Fail fail, final fURI pattern) {
        if (null != fail.vid() && !fail.isNoObj())
            return fail;
        // write AT the pattern: `fail.vid(pattern)` is a tagged clone, which writes itself to that vid on
        // construction, so stamping first consumed a second counter value per fail (the stack held only evens)
        final Obj o = Machine.write(pattern, fail);
        // a CAUGHT fail is a value (isFail() is false by design): it is filed just like an uncaught one
        if (o.isFail() || o.isCaughtFail())
            return o.as();
        else
            return new MFail(new RuntimeException("expected fail: %s".formatted(o.toCleanString())), FAIL_TID, null);
    }

    /**
     * ADDRESS A FAIL FOR REPORTING — the other half of minting unfiled ({@link #transientFail}). Called by the
     * boundaries that SHOW a failure to someone: the console's result line, a ws/http/mcp reply, the headless
     * {@code -e} output. A fail that already carries an address keeps it (one re-homed off a wire, one written into a
     * space, or one asked twice — {@link #incrStackWrap} answers an addressed fail unchanged), so this is idempotent;
     * anything that is not a fail, and every noobj, is answered as it stands.
     */
    public static Obj reported(final Obj obj) {
        return null != obj && (obj.isFail() || obj.isCaughtFail()) && !obj.hasVID()
                ? incrStackWrap(obj.asFail(), failStackPattern())
                : obj;
    }

    @Override
    public int hashCode() {
        return this.isNoObj() ? Obj.Helper.objHashCode(this) : Objects.hash(this.tid, this.jvm().getMessage(), this.jvm().getCause() != null);
    }

    @Override
    public boolean equals(final Object other) {
        // a zero-coefficient fail is no object at all: it is the one zero, like every {0}
        if (this.isNoObj() && other instanceof Obj otherObj && otherObj.isNoObj())
            return true;
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
    /**
     * MINT A FAIL AND FILE NOTHING. The failure is an ordinary {@code fail::T} value — same tid, message, cause chain
     * and back-links — it simply has no address, because an address is what REPORTING gives it ({@code
     * incrStackWrap} at the console, the wire reply, the http/mcp reply). Minting used to file as it minted, so every
     * question the engine asks — a type test applying a predicate, a rewrite matcher trying a rule, a parser probing
     * text — left a frame in {@code ~/sys/fail} for a failure that never happened to anyone.
     */
    public static Fail transientFail(final Throwable t) {
        // NOTHING BUT THE MINT. No filing (see the doc above) and no chain walk: `ensureFailRefs` follows
        // Throwable.getCause() with no cycle guard, and a fail chain CAN be cyclic — `fail(t, cause)` threads a
        // throwable at the tail of another chain, and a throwable already in that chain closes the loop
        // (testFailPlusSameFailDoesNotCycle pins that the merged chain must not revisit a node). A walker here
        // would spin forever on exactly that input.
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
        return incrStackWrap(mfail, failStackPattern());
    }

    /**
     * Create a fail from a Throwable.
     * If the Throwable already has a cause chain, the cause is preserved.
     */
    public static Fail fail(final Throwable t) {
        final MTronException mte = MTronException.of(t);
        final MFail mfail = new MFail(mte, FAIL_TID, null);
        ensureFailRefs(mte);
        return incrStackWrap(mfail, failStackPattern());
    }

    /**
     * Create a fail with a nested mtron cause. The cause fail's Throwable becomes
     * the Java {@link Throwable#getCause()} of the outer fail's Throwable.
     * <p>
     * When {@code t} has its own cause chain, that chain is preserved by threading
     * it at the tail of {@code cause.jvm()}'s chain.  Without this, wrapping
     * an exception that already carries causal information would silently drop
     * the original cause chain — e.g. {@code IOException → FileNotFoundException}
     * wrapped with a mtron cause would lose the {@code FileNotFoundException}.
     */
    public static Fail fail(final Throwable t, final Fail cause) {
        final Throwable jvm;
        if (null != cause) {
            // Preserve t's original cause chain by threading it at the tail
            // of the mtron cause's chain, so the full causal history survives.
            // JDK 25+: Throwable(msg, null) sets this.cause = null which
            // blocks initCause(), so we reconstruct the chain from
            // constructors bottom-up rather than using initCause().
            Throwable mtronCauseHead = cause.jvm();
            final Throwable tCause = t.getCause();
            if (tCause != null) {
                mtronCauseHead = rebuildMtronChainWithCause(mtronCauseHead, tCause);
            }
            jvm = new RuntimeException(t.getMessage(), mtronCauseHead);
            jvm.setStackTrace(t.getStackTrace());
        } else {
            jvm = t instanceof MTronException ? t : MTronException.of(t.getMessage());
            if (jvm instanceof MTronException)
                ensureFailRefs(jvm);
        }
        return incrStackWrap(new MFail(jvm, FAIL_TID, null), failStackPattern());
    }

    /**
     * Attach the mtron execution path (from {@link ExecutionStack}) and the
     * java origin frame to the fail's message when the stack is non-empty.
     * This is what makes {@code catch(cause())} show *where in the pipeline*
     * the fail happened — the clipped in-flow message stays, and the full
     * history is present on the fail itself and in fail space.
     */
    /**
     * The mtron execution path at the moment of access — the stable *query*
     * form of the stack (the in-flow fail string itself is always rendered
     * without it, keeping fail equality byte-stable).
     */
    public static String path() {
        return ExecutionStack.generateStackTrace();
    }

    /**
     * Thread {@code tCause} at the tail of {@code mtronCauseHead}'s chain
     * while preserving {@code tCause}'s original stack frames intact.
     * Uses {@link Throwable#initCause} where possible; falls back to
     * constructor wrapping on JDK 25+ (initCause blocked after null-cause).
     */
    private static Throwable rebuildMtronChainWithCause(final Throwable mtronCauseHead,
                                                        final Throwable tCause) {
        if (null == tCause)
            return mtronCauseHead;
        // a cause already reachable from the head would close a getCause() cycle —
        // the cause-walk in writeFail has no cycle guard and would spin forever
        for (Throwable c = mtronCauseHead; c != null; c = c.getCause())
            if (c == tCause)
                return mtronCauseHead;
        // Walk to the tail of the mtron chain
        Throwable tail = mtronCauseHead;
        while (tail.getCause() != null)
            tail = tail.getCause();
        // Try to thread tCause directly — preserves its original stack frames
        try {
            tail.initCause(tCause);
            return mtronCauseHead;
        } catch (final IllegalStateException e) {
            // JDK 25+: initCause blocked on null-cause construction.
            // Fall back to wrapping the tail with a new throwable that
            // carries tCause as its cause.  The rest of the mtron chain
            // above tail is untouched.
            final RuntimeException leaf = new RuntimeException(
                    tail.getMessage() != null ? tail.getMessage() : "", tCause);
            leaf.setStackTrace(tail.getStackTrace());
            if (mtronCauseHead == tail)
                return leaf;
            // Replace tail with leaf in the chain
            Throwable parent = mtronCauseHead;
            while (parent.getCause() != tail)
                parent = parent.getCause();
            try {
                parent.initCause(leaf);
            } catch (final Exception ignored) {
                // best effort — chain above leaf is intact, just missing one link
            }
            return mtronCauseHead;
        }
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
        return this.caughtTransient();
    }

    @Override
    public Fail caughtTransient() {
        if (this instanceof MCaughtFail)
            return this;
        final MCaughtFail caught = new MCaughtFail(this);
        // THE ADDRESS IS STILL THE FAIL'S IDENTITY IN MEMORY, SO IT IS STAMPED — WITHOUT A WRITE. `vid()` clones
        // and SAVES (objCheckAndSave), which would re-file the caught wrapper at the uncaught fail's address:
        // caught() deletes that entry and the contract test says it must stay deleted. selfVID only re-labels this
        // wrapper, so a fail leaving a machine for a wire keeps the tag it was answered under.
        return null == this.vid() ? caught : (Fail) caught.selfVID(this.vid());
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
        // Walk to the end of the lhs cause chain and attach rhs there.
        // Uses rebuildMtronChainWithCause to avoid JDK 25 initCause rejection.
        final Throwable merged = rebuildMtronChainWithCause(this.jvm(), rhs.jvm());
        return transientFail(merged);
    }
    
    public Fail zero() {
        return fail(MTronException.of(""));
    }

    public static class MCaughtFail extends MFail implements CaughtFail {

        protected MCaughtFail(final Fail other) {
            // Cause chain is already embedded in other.message().getCause() —
            // no need to re-thread it; this.message() IS other.message()
            // CONSTRUCTED WITH NO ADDRESS. Constructing an obj at a vid WRITES it there (objCheckAndSave), and a
            // caught fail is not filed at the uncaught fail's address: caught() deletes that entry, so constructing
            // at the address resurrected it. The wrapper is stamped IN MEMORY by caughtTransient() with selfVID
            // instead — which keeps the tag a fail is answered under (the wire needs it) without a write.
            super(other.jvm(), other.tid(), null);
        }

        public boolean isFail() {
            return false;
        }
    }
}
