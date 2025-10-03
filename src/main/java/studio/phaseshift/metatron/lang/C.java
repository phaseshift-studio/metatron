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
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */

package studio.phaseshift.metatron.lang;

import java.util.Objects;

public interface C<T extends Comparable<T>, D extends C<T, D>> extends Comparable<D> {

    T min();

    T max();

    D neg();

    D plus(final D rhs);

    D mult(final D rhs);

    default D minus(final D rhs) {
        return this.plus(rhs.neg());
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    boolean within(final D rhs);

    default boolean lt(final D rhs) {
        return this.compareTo(rhs) < 0;
    }

    default boolean gt(final D rhs) {
        return this.compareTo(rhs) > 0;
    }

    default boolean lte(final D rhs) {
        return this.compareTo(rhs) <= 0;
    }

    default boolean gte(final D rhs) {
        return this.compareTo(rhs) >= 0;
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////

    D any();

    D some();

    D zero();

    D one();

    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    boolean isNeg();

    default boolean isPos() {
        return !this.isNeg();
    }

    boolean isZeroOrNeg();

    boolean isOne();

    boolean isAny();

    boolean isSome();

    boolean isMaybe();

    boolean isZero();

    boolean isNoObjable();

    default boolean isExact() {
        return Objects.equals(this.min(), this.max());
    }
}
