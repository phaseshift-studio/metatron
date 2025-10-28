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

package studio.phaseshift.metatron.util;

import org.apache.tinkerpop.gremlin.util.function.ThrowingSupplier;
import studio.phaseshift.metatron.lang.mtron.type.Fail;
import studio.phaseshift.metatron.ui.Graphitty;

import java.util.Arrays;

import static studio.phaseshift.metatron.lang.mtron.type.impl.MFail.fail;

public class MTronException extends RuntimeException {

    private MTronException(final String message) {
        super(Graphitty.string(message));
    }

    private MTronException(final String message, final Throwable cause) {
        super(Graphitty.string(message), cause);
    }

    private MTronException(final Throwable cause) {
        super(cause);
    }

    public static MTronException of(final Throwable cause) {
        return new MTronException(cause);
    }

    public static MTronException of(final Throwable cause, final String format, final Object... args) {
        return new MTronException(Graphitty.string(format.formatted(args)), cause);
    }

    public static MTronException of(final String format, final Object... args) {
        return new MTronException(Graphitty.string(format.formatted(args)));
    }

    public static MTronException of(final Object throwableOrformat, final Object... args) {
        if (throwableOrformat instanceof Throwable)
            ((Throwable) throwableOrformat).printStackTrace();
        return throwableOrformat instanceof Throwable ?
                new MTronException(Graphitty.string(((String) args[0]).formatted(Arrays.copyOfRange(args, 1, args.length))),
                        (Throwable) throwableOrformat) :
                new MTronException(Graphitty.string(throwableOrformat.toString().formatted(args)));
    }

    public static <T> T wrap(final ThrowingSupplier<T> function) {
        try {
            return function.get();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    public static <T> T wrap(final ThrowingSupplier<T> function, final T onException) {
        try {
            return function.get();
        } catch (final Exception e) {
            return onException;
        }
    }
    
    public static MTronException mexcept(final Object throwableOrformat, final Object... args) {
        return throwableOrformat instanceof Throwable ?
                new MTronException(Graphitty.string(((String) args[0]).formatted(Arrays.copyOfRange(args, 1, args.length))),
                        (Throwable) throwableOrformat) :
                new MTronException(Graphitty.string(throwableOrformat.toString().formatted(args)));
    }

    public MTronException cause(final Exception cause) {
        this.initCause(cause);
        return this;
    }

    public Fail asFail() {
        return fail(this);
    }

    public String toString() {
        return this.getMessage();
    }
}
