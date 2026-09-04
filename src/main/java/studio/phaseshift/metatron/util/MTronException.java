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

public class MTronException extends RuntimeException {

    /**
     * Back-pointer to the mtron-level {@link Fail} wrapping this exception. Set-once.
     */
    private volatile Fail failRef;

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
     * Compact summary of the throwable and its cause chain.  Multi-line
     * messages are truncated at the first newline; nested causes are
     * separated by {@code ←}.
     */
    private static String causeSummary(final Throwable cause) {
        final StringBuilder sb = new StringBuilder();
        Throwable c = cause;
        int depth = 0;
        while (c != null && depth < 4) {
            if (depth > 0) sb.append(" ← ");
            final String msg = c.getMessage();
            if (msg != null) {
                final int nl = msg.indexOf('\n');
                sb.append(nl < 0 ? msg : msg.substring(0, nl) + "...");
            } else {
                sb.append('(').append(c.getClass().getSimpleName()).append(')');
            }
            c = c.getCause();
            depth++;
        }
        if (c != null) sb.append(" ← ...");
        return sb.toString();
    }

    private MTronException(final String message, final Throwable cause) {
        super(null == cause ? Graphitty.string(message) : Graphitty.string((message != null ? message : "(null)").replace("%", "%%") + "[%s<%d>:%s]",
                Optional.ofNullable(originOf(cause)).map(o -> o.getClassName().substring(o.getClassName().lastIndexOf('.') + 1)).orElse("no stack element"),
                Optional.ofNullable(originOf(cause)).map(StackTraceElement::getLineNumber).orElse(0),
                causeSummary(cause)), cause);
    }

    private MTronException(final String message) {
        this(Graphitty.string(message), null);
    }

    protected MTronException(final String message, final Throwable cause, final boolean dummy) {
        super(null == cause ? Graphitty.string(message) : Graphitty.string((message != null ? message : "(null)").replace("%", "%%") + "[%s<%d>:%s]",
                originOf(cause).getClassName().substring(originOf(cause).getClassName().lastIndexOf('.') + 1),
                originOf(cause).getLineNumber(),
                causeSummary(cause)), cause);
    }

    private static MTronException tracerThrow(final MTronException e) {
        if (Tracer.mtron_stack.enabled())
            Graphitty.log(Tracer.class).error(ExecutionStack.generateStackTrace());
        if (Tracer.java_stack.enabled())
            e.printStackTrace();
        return e;
    }

    public static MTronException of(final Throwable cause) {
        if (cause instanceof MTronException)
            return (MTronException) cause;
        final MTronException m = convert(cause);
        return cause.getCause() != null ? m.cause(convert(cause.getCause())) : m;
    }

    public static MTronException of(final Throwable cause, final String format, final Object... args) {
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
        return other instanceof MTronException && this.getMessage().equals(((MTronException) other).getMessage());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getMessage());
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
