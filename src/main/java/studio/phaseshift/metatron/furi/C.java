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

package studio.phaseshift.metatron.furi;

import studio.phaseshift.metatron.algebra.Ring;

import java.util.Objects;

public interface C<T extends Comparable<T>, D extends C<T, D>> extends Comparable<D>, Ring<D> {

    T min();

    T max();

    D neg();

    D plus(final D rhs);

    D mult(final D rhs);

    default D minus(final D rhs) {
        return this.plus(rhs.neg());
    }

    D clone(final T min, final T max);

    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    boolean within(final D rhs);

    default boolean contains(final D rhs) {
        return this.lte(rhs.least()) && this.gte(rhs.most());
    }

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

    D maybeSome();

    D some();

    D maybe();

    D zero();

    D one();

    default D least() {
        return this.clone(this.min(), this.min());
    }

    default D most() {
        return this.clone(this.max(), this.max());
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    boolean isNeg();

    default boolean isPos() {
        return !this.isNeg();
    }

    default boolean isZeroOrNeg() {
        return this.isZero() || this.isNeg();
    }

    default boolean isOne() {
        return Objects.equals(this, this.one());
    }

    default boolean isAny() {
        return Objects.equals(this, this.any());
    }

    default boolean isMaybeSome() {
        return Objects.equals(this, this.maybeSome());
    }

    default boolean isSome() {
        return Objects.equals(this, this.some());
    }

    default boolean isMaybe() {
        return Objects.equals(this, this.maybe());
    }

    default boolean isZero() {
        return Objects.equals(this, this.zero());
    }

    default boolean isNoObjable() {
        return this.contains(this.zero());
    }

    default boolean isExact() {
        return Objects.equals(this.min(), this.max());
    }

    default boolean isRange() {
        return !this.isExact();
    }
}
