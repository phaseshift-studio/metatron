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

package studio.phaseshift.metatron.util;

import studio.phaseshift.metatron.Tracer;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.impl.MFail;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.sys.type.ExecutionStack;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class MTronException extends RuntimeException {

    /**
     * Back-pointer to the mtron-level {@link Fail} wrapping this exception. Set-once.
     */
    private volatile Fail failRef;

    /**
     * Snapshot of the mtron execution stack captured at creation time —
     * {@code null} when no frame was on the stack then. The deepest
     * (earliest captured, still most complete) non-blank capture in the chain
     * is what gets emitted; the live stack is already unraveling by the time
     * an outer re-wrap sees it.
     */
    private volatile String mtronTrace;
    private volatile boolean mtronTraceEmitted;
    private volatile boolean javaTraceEmitted;

    // ------------------------------------------------------------------
    // diagnostic / test hooks — how many traces each tracer emitted, and
    // what the last mtron one said
    // ------------------------------------------------------------------

    private static final AtomicInteger mtronTracesEmitted = new AtomicInteger();
    private static final AtomicInteger javaTracesEmitted = new AtomicInteger();
    private static volatile String lastMtronTrace;

    /**
     * @return the execution stack snapshot captured on this exception, or
     *         {@code null} when no frame was in flight at creation
     */
    public String mtronTrace() {
        return this.mtronTrace;
    }

    /**
     * @return how many mtron stack traces have been emitted so far
     */
    public static int mtronTracesEmitted() {
        return mtronTracesEmitted.get();
    }

    /**
     * @return how many java stack traces have been emitted so far
     */
    public static int javaTracesEmitted() {
        return javaTracesEmitted.get();
    }

    /**
     * @return the last emitted mtron stack trace, or {@code null}
     */
    public static String lastMtronTrace() {
        return lastMtronTrace;
    }

    /**
     * @return the mtron {@link Fail} that wraps this exception, or {@code null}
     */
    public Fail fail() {
        return this.failRef;
    }

    /**
     * Set the back-pointer.  First writer wins — subsequent calls are ignored
     * so the original (uncaught) Fail is always preferred.
     */
    public void setFailRef(final Fail f) {
        if (null == this.failRef)
            this.failRef = f;
    }

    /**
     * Find the most useful stack frame: walk to the deepest cause
     * (its origin is usually closest to the actual problem), then
     * return the first stack frame.  No more filtering out
     * MTronException frames — those ARE the call sites.
     */
    public static StackTraceElement originOf(final Throwable t) {
        if (null == t)
            return null;
        Throwable target = t;
        while (target.getCause() != null
                && target.getCause().getStackTrace().length > 0
                && !(target.getCause() instanceof StackOverflowError))
            target = target.getCause();
        if (target.getStackTrace().length > 0)
            return target.getStackTrace()[0];
        return t.getStackTrace().length > 0 ? t.getStackTrace()[0] : null;
    }

    /**
     * The origin suffix: WHERE the failure happened — for a raw-java cause,
     * the java class+line of the deepest frame (class only, no re-quoted
     * summary — that text lives in the message or the cause chain;
     * pointing at a MTronException factory line carries no information).
     * <p>
     * mtron chains (the tail is an MTronException) get no suffix at all:
     * they are self-describing, the instruction origin rides in the message
     * where it is generated (the inst funnel), and the fail text must
     * round-trip byte-stable through the parser (a parse-time frame is not
     * the failing instruction's).
     */
    private static String originSuffix(final Throwable cause) {
        Throwable owner = cause;
        while (null != owner.getCause())
            owner = owner.getCause();
        if (owner instanceof MTronException)
            return "";
        final StackTraceElement origin = originOf(cause);
        final String name = Optional.ofNullable(origin).map(o -> o.getClassName().substring(o.getClassName().lastIndexOf('.') + 1)).orElse("");
        if (name.isEmpty() || MTronException.class.getSimpleName().equals(name))
            return "";
        return " [" + name + "<" + origin.getLineNumber() + ">]";
    }

    private MTronException(final String message, final Throwable cause) {
        // a constructor must start with super() (statements before the
        // invocation are the JEP 513 flexible bodies — preview only here),
        // so the suffix is composed inline
        super(null == cause ? Graphitty.string(message) :
                    Graphitty.string((message != null ? message : "(null)").replace("%", "%%") + originSuffix(cause)),
               null == cause ? null : cause);
    }

    private MTronException(final String message) {
        super(Graphitty.string(message));
    }

    /**
     * Verbatim-message constructor — the caller's text stays exactly as
     * formatted (no origin suffix appended); a non-null cause is still linked.
     * (TypeMismatchException is the current user.)
     */
    protected MTronException(final String message, final Throwable cause, final boolean verbatim) {
        super(Graphitty.string(null == message ? "(null)" : message), cause);
    }

    private static MTronException tracerThrow(final MTronException e) {
        // creation time: capture the live execution stack on this exception
        // (the deepest capture in the chain is the most complete — the live
        // stack is unraveling as the exception unwinds). Emission happens
        // when the failure is reported (see emitStackTrace) — discarded
        // retries of a failing pipeline must not each print their own
        // shrink-wrapped trace.
        if (Tracer.mtron_stack.enabled())
            captureIfAbsent(e);
        return e;
    }

    private static void captureIfAbsent(final MTronException e) {
        if (null != e.mtronTrace)
            return;
        final String trace = ExecutionStack.generateStackTrace();
        if (!trace.isBlank())
            e.mtronTrace = trace;
    }

    /**
     * Emit the mtron and java stack traces for a failure — each exactly
     * once. Call this at the reporting boundary: when a fail is serialized
     * for display (console result line, headless -e print, MCP/WS reply,
     * log line — {@code writeFail} does this) or an exception escapes
     * uncaught. Never at exception creation: one logical failure passes
     * through several MTronExceptions (the deepest throw, then each outer
     * re-wrap), the live execution stack is already shrunk by the time
     * each later wrap is born (frames unwind as the exception unwinds),
     * and a failing pipeline may retry-and-die several times before the
     * failure that is actually reported.
     * <p>
     * So the mtron trace emitted is always the deepest non-blank capture
     * in the chain — the most complete one — and a chain whose trace
     * already went out stays quiet. A blank capture (no frame was alive
     * at any point) emits nothing, rather than leaving a bare "[Tracer]"
     * log line behind.
     */
    public static void emitStackTrace(final Throwable head) {
        if (null == head)
            return;
        if (Tracer.mtron_stack.enabled())
            emitMtronTrace(head);
        if (Tracer.java_stack.enabled())
            emitJavaTrace(head);
    }

    private static void emitMtronTrace(final Throwable head) {
        MTronException carrier = null;   // walk to the tail — the deepest non-blank capture wins
        for (Throwable c = head; null != c; c = c.getCause())
            if (c instanceof MTronException m && null != m.mtronTrace)
                carrier = m;
        if (null == carrier || carrier.mtronTraceEmitted)
            return;
        carrier.mtronTraceEmitted = true;
        lastMtronTrace = carrier.mtronTrace;
        mtronTracesEmitted.incrementAndGet();
        Graphitty.log(Tracer.class).error(carrier.mtronTrace);
    }

    /**
     * The outermost exception in the chain carries the most complete java
     * frames, so head's trace is printed — the first time the chain is seen.
     */
    private static void emitJavaTrace(final Throwable head) {
        for (Throwable c = head; null != c; c = c.getCause())
            if (c instanceof MTronException m && m.javaTraceEmitted)
                return;
        for (Throwable c = head; c instanceof MTronException m; c = c.getCause())
            m.javaTraceEmitted = true;
        javaTracesEmitted.incrementAndGet();
        head.printStackTrace();
    }

    public static MTronException of(final Throwable cause) {
        if (cause instanceof MTronException)
            return (MTronException) cause;
        // convert() already preserves the original java cause chain — the
        // convert-cause round trip here was always a no-op (cause() returns
        // early for MTronException arguments) but still fired a stray trace
        // into a discarded, unlinked chain
        return convert(cause);
    }

    public static MTronException of(final Throwable cause, final String format, final Object... args) {
        // links cause as the java cause (the tracer's once-per-chain dedup,
        // cause-chain walks, and fail serialization all see the whole chain)
        // and embeds the inner text in the outer message (the flat
        // failure-text contract)
        return tracerThrow(new MTronException(Graphitty.string(args.length == 0 ? format : format.formatted(args)), convert(cause)));
    }

    public static MTronException of(final String format, final Object... args) {
        return tracerThrow(new MTronException(Graphitty.string(args.length == 0 ? format : format.formatted(args))));
    }

    public static MTronException of(final fURI source, final String format, final Object... args) {
        return tracerThrow(new MTronException(args.length == 0
                ? "[%s] %s".formatted(source, format)
                : "[%s] %s".formatted(source, Graphitty.string(format.formatted(args)))));
    }

    public static MTronException of(final Object throwableOrformat, final Object... args) {
        //if (throwableOrformat instanceof Throwable)
        //   ((Throwable) throwableOrformat).printStackTrace();
        return tracerThrow(throwableOrformat instanceof Throwable ?
                new MTronException(Graphitty.string(args.length <= 1 ? (String) args[0] : ((String) args[0]).formatted(Arrays.copyOfRange(args, 1, args.length))),
                        convert((Throwable) throwableOrformat)) :
                new MTronException(Graphitty.string(args.length == 0 ? throwableOrformat.toString() : throwableOrformat.toString().formatted(args))));
    }

    private static MTronException convert(final Throwable throwable) {
        if (throwable == null)
            return null;
        if (throwable instanceof MTronException)
            return (MTronException) throwable;
        else if (throwable.toString().contains("cannot be cast to class")) {
            final String[] message = throwable.getMessage().split(" cannot be cast to class ");
            final String leftClass = message[0].trim();
            final String rightClass = message[1].trim().split("\\(")[0].trim();
            return tracerThrow(new MTronException("unable to convert " + convertName(leftClass.substring(leftClass.lastIndexOf('.') + 1)) + " to " + convertName(rightClass.substring(rightClass.lastIndexOf('.') + 1)), throwable));
        } else {
            // Preserve the original throwable as the Java cause — do NOT
            // embed the full stack trace in the message string.  The cause
            // chain is available through getCause() and the stack trace
            // through getStackTrace().  Embedding them in the message
            // buries the signal and discards structured cause data.
            // throwable.printStackTrace();
            return tracerThrow(new MTronException(null == throwable.getMessage() ? "fail" : throwable.getMessage(), throwable));
        }
    }

    private static String convertName(final String name) {
        final String lname = name.toLowerCase();
        if (lname.contains("boolean"))
            return "bool::T";
        if (lname.contains("int"))
            return "int::T";
        if (lname.contains("real"))
            return "real::T";
        if (lname.contains("str"))
            return "str::T";
        if (lname.contains("uri"))
            return "uri::T";
        if (lname.contains("lst"))
            return "lst::T";
        if (lname.contains("rec"))
            return "rec::T";
        if (lname.contains("rel"))
            return "rel::T";
        if (lname.contains("type"))
            return "type";
        else
            return lname;
    }

    public static <T> T wrap(final ThrowingSupplier<T> function) {
        try {
            return function.get();
        } catch (final Exception e) {
            throw MTronException.of(convert(e));
        }
    }

    public static void wrap(final ThrowingRunnable function) {
        wrap(function, false);
    }

    public static void wrap(final ThrowingRunnable function, final boolean ignore) {
        try {
            function.run();
        } catch (final Exception e) {
            if (!ignore) throw MTronException.of(convert(e));
        }
    }

    public static <T> T wrap(final ThrowingSupplier<T> function, final T onException) {
        try {
            return function.get();
        } catch (final Exception e) {
            return onException;
        }
    }

    public MTronException cause(final Throwable cause) {
        if (cause instanceof MTronException)
            return this;
        final MTronException m = convert(cause);
        if (null != m) this.initCause(m);
        return this;
    }

    public Fail asFail() {
        return MFail.fail(this, null);
    }

    public String toString() {
        return this.getMessage();
    }

    @Override
    public boolean equals(final Object other) {
        // chain-aware: a bare message no longer distinguishes funnel wraps
        // ("inst apply failure" with different inners)
        if (!(other instanceof MTronException o) || !Objects.equals(this.getMessage(), o.getMessage()))
            return false;
        return Objects.equals(this.getCause(), o.getCause());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getMessage(), this.getCause());
    }


    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
