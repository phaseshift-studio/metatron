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

import java.io.Closeable;
import java.util.function.Function;
import java.util.function.Supplier;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class Common {

    private Common() {
        // do nothing
    }

    public static void close(final Object object) {
        try {
            if (object instanceof Closeable)
                ((Closeable) object).close();
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    public static <A,B> B nullOrElse(final A object, final Supplier<B> ifNull, final Function<A, B> ifNotNull) {
        if (null == object)
            return ifNull.get();
        return ifNotNull.apply(object);
    }
}
