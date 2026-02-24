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

    default boolean signeq(final D rhs) {
        return this.gt(zero()) == rhs.gt(zero());
    }

    default D abs() {
        return this.lt(zero()) ? this.neg() : (D)this;
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////

    D any();

    D maybeSome();

    default D inverse() {
        return this.clone(this.most().neg().min(), this.least().neg().max());
    }

    default D mirror() {
        return this.clone(
                this.most().gt(this.zero()) ? this.inverse().min() : this.min(),
                this.most().lte(this.zero()) ? this.inverse().max() : this.max());
    }

    default D antiMaybe() {
        return this.maybe().inverse();
    }

    default D antiSome() {
        return this.some().inverse();
    }

    default D antiMaybeSome() {
        return this.maybeSome().inverse();
    }

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

    default boolean isOne() {
        return Objects.equals(this, this.one());
    }

    default boolean isAny() {
        return Objects.equals(this, this.any());
    }
    
    default boolean isAntiMaybeSome() {
        return Objects.equals(this,this.antiMaybeSome());
    }

    default boolean isAntiSome() {
        return Objects.equals(this,this.antiSome());
    }

    default boolean isAntiMaybe() {
        return Objects.equals(this,this.antiMaybe());
    }
    
    default boolean isAbsMaybe() {
        return Objects.equals(this,this.abs().maybe());
    }

    default boolean isAbsSome() {
        return Objects.equals(this,this.abs().some());
    }

    default boolean isAbsMaybeSome() {
        return Objects.equals(this,this.abs().maybeSome());
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

    default boolean isZeroable() {
        return this.zero().within((D) this);
    }

    default boolean isZero() {
        return Objects.equals(this, this.zero());
    }

    default boolean isExact() {
        return Objects.equals(this.min(), this.max());
    }

    default boolean isRange() {
        return !this.isExact();
    }
    
    static <D extends C<T, D>, T extends Comparable<T>> T[] balance(final T min, final T max) {
       return min.compareTo(max) > 0 ? (T[])new Object[]{max, min} : (T[])new Object[]{min, max};
    }
}
