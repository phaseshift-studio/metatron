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

import java.io.Serial;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

// 1. Define a private Exception for signaling project failure
public class ProjectionFailureException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 233338654157697L;
    private static final ProjectionFailureException INSTANCE = new ProjectionFailureException();

    private ProjectionFailureException() {
    }

    public static ProjectionFailureException instance() {
        return INSTANCE;
    }

    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    public static <A> A predicateThrow(final A lhs, final Consumer<A> function) {
        try {
            function.accept(lhs);
            return lhs;
        } catch (final ProjectionFailureException e) {
            return (A) noobj();
        }
    }
}
