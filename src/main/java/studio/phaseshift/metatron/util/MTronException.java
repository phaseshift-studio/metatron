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

package studio.phaseshift.metatron.util;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Fail;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Highlighter;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;

import java.util.Arrays;
import java.util.Objects;

import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;

public class MTronException extends RuntimeException {


    private MTronException(final String message, final Throwable cause) {
        super(null == cause ? Graphitty.string(message) : Graphitty.string(message + "[%s:%d]",
                cause.getStackTrace()[0].getClassName(),
                cause.getStackTrace()[0].getLineNumber()), cause);
    }

    private MTronException(final String message) {
        this(Graphitty.string(message), null);
    }

    public static MTronException of(final Throwable cause) {
        final MTronException m = cause instanceof MTronException ? (MTronException) cause : convert(cause);
        return cause.getCause() != null ? m.cause(convert(cause.getCause())) : m;
    }

    public static MTronException of(final Throwable cause, final String format, final Object... args) {
        return new MTronException(Graphitty.string(format.formatted(args)), convert(cause));
    }

    public static MTronException of(final String format, final Object... args) {
        return new MTronException(Graphitty.string(format.formatted(args)));
    }

    public static MTronException of(final fURI source, final String format, final Object... args) {
        return new MTronException("[%s] %s".formatted(source, Graphitty.string(format.formatted(args))));
    }

    public static MTronException of(final Object throwableOrformat, final Object... args) {
        //if (throwableOrformat instanceof Throwable)
        //   ((Throwable) throwableOrformat).printStackTrace();
        return throwableOrformat instanceof Throwable ?
                new MTronException(Graphitty.string(((String) args[0]).formatted(Arrays.copyOfRange(args, 1, args.length))),
                        convert((Throwable) throwableOrformat)) :
                new MTronException(Graphitty.string(throwableOrformat.toString().formatted(args)));
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
            return new MTronException("unable to convert " + convertName(leftClass.substring(leftClass.lastIndexOf('.') + 1)) + " to " + convertName(rightClass.substring(rightClass.lastIndexOf('.') + 1)), throwable);
        } else {
            final StringBuilder stack = new StringBuilder();
            for (int i = 0; i < throwable.getStackTrace().length; i++)
                stack.append("\t")
                        .append(throwable.getStackTrace()[i].getClassName())
                        .append(" [line ").append(throwable.getStackTrace()[i].getLineNumber()).append("]\n");
            return new MTronException(Highlighter.unformat("%s: %s\n%s".formatted(
                    null == throwable.getCause() ?
                            throwable.getClass().getSimpleName().toLowerCase() :
                            throwable.getCause().toString(),
                    throwable.getMessage(),
                    CommonUtil.indent(stack.toString(), 2))));
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
        return fail(this, null);
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
